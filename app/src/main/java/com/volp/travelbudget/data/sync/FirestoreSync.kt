package com.volp.travelbudget.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.volp.travelbudget.data.local.DeletionEntity
import com.volp.travelbudget.data.local.SyncDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.domain.sync.SyncCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/** 한 번 맞춰 본 결과. */
sealed interface SyncOutcome {
    /** 동기화 코드를 아직 안 넣었다. */
    data object NoCode : SyncOutcome

    /** 이 빌드에 Firebase 설정이 들어 있지 않다. */
    data object NotConfigured : SyncOutcome

    data class Done(val pulled: Int, val pushed: Int) : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * 안드로이드 기기끼리 기록을 맞춘다.
 *
 * 같은 **동기화 코드**를 넣은 기기들이 Firestore의 같은 자리를 본다. 기록 하나가 문서 하나이므로
 * 두 기기가 서로 다른 기록을 고쳤으면 둘 다 남고, 같은 기록을 고쳤으면 나중에 고친 쪽이 남는다.
 *
 * 마지막으로 맞춘 시각 뒤에 바뀐 것만 주고받는다. 기기 시계가 조금씩 다를 수 있어 [SLACK_MILLIS]
 * 만큼 앞에서부터 다시 본다. 같은 것을 두 번 받아도 합치는 쪽이 알아서 걸러 낸다.
 */
class FirestoreSync(
    private val context: Context,
    private val engine: SyncEngine,
    private val syncDao: SyncDao,
    private val settings: AppSettings,
) {

    /** 이 빌드가 Firebase 설정을 갖고 있는지. 없으면 동기화 자리 자체가 없다. */
    val isConfigured: Boolean
        get() = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun sync(): SyncOutcome = withContext(Dispatchers.IO) {
        val prefs = settings.settings.first()
        val code = prefs.syncCode
        if (code.isBlank() || !SyncCode.isValid(code)) return@withContext SyncOutcome.NoCode
        if (!isConfigured) return@withContext SyncOutcome.NotConfigured

        runCatching { runSync(code, prefs.lastSyncAt) }
            .getOrElse { SyncOutcome.Failed(it.message ?: "동기화에 실패했다") }
    }

    private suspend fun runSync(code: String, lastSyncAt: Long): SyncOutcome {
        signInIfNeeded()

        val startedAt = System.currentTimeMillis()
        val since = (lastSyncAt - SLACK_MILLIS).coerceAtLeast(0L)
        val space = FirebaseFirestore.getInstance().collection(SPACES).document(code)

        // ---- 받아오기 ----
        val recordDocs = space.collection(RECORDS)
            .whereGreaterThan(FIELD_UPDATED_AT, since)
            .orderBy(FIELD_UPDATED_AT, Query.Direction.ASCENDING)
            .get()
            .await()
            .documents

        val deletionDocs = space.collection(DELETIONS)
            .whereGreaterThan(FIELD_DELETED_AT, since)
            .get()
            .await()
            .documents

        val incoming = SyncRecords.snapshotOf(
            records = recordDocs.mapNotNull { it.toRecord() },
            deletions = deletionDocs.mapNotNull { it.toDeletion() },
            tripOf = { uid -> syncDao.tripByUid(uid)?.toDomain() },
        )
        val merged = engine.mergeIn(incoming.snapshot)

        // ---- 올리기 ----
        // 합친 뒤의 상태가 곧 이 기기가 알고 있는 전부다. 그 가운데 바뀐 것만 올린다.
        val mine = SyncRecords.flatten(merged.snapshot).filter { it.updatedAt > since }
        writeRecords(space, mine)

        val myDeletions = syncDao.deletions().filter { it.deletedAt > since }
        writeDeletions(space, myDeletions)

        // 붙일 여행을 못 찾은 기록이 있으면 다음에 다시 받아 봐야 한다.
        val advanced = if (incoming.unresolved == 0) startedAt else lastSyncAt
        settings.setLastSyncAt(advanced)

        return SyncOutcome.Done(pulled = merged.pulled, pushed = mine.size + myDeletions.size)
    }

    private suspend fun writeRecords(
        space: com.google.firebase.firestore.DocumentReference,
        records: List<SyncRecord>,
    ) {
        records.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = FirebaseFirestore.getInstance().batch()
            chunk.forEach { record ->
                val doc = space.collection(RECORDS).document(documentId(record.entity, record.uid))
                batch.set(
                    doc,
                    mapOf(
                        "entity" to record.entity,
                        "uid" to record.uid,
                        "tripUid" to record.tripUid,
                        FIELD_UPDATED_AT to record.updatedAt,
                        "payload" to record.payload,
                    ),
                )
            }
            batch.commit().await()
        }
    }

    private suspend fun writeDeletions(
        space: com.google.firebase.firestore.DocumentReference,
        deletions: List<DeletionEntity>,
    ) {
        deletions.chunked(BATCH_LIMIT).forEach { chunk ->
            val batch = FirebaseFirestore.getInstance().batch()
            chunk.forEach { entry ->
                val doc = space.collection(DELETIONS).document(documentId(entry.entity, entry.uid))
                batch.set(
                    doc,
                    mapOf(
                        "entity" to entry.entity,
                        "uid" to entry.uid,
                        FIELD_DELETED_AT to entry.deletedAt,
                    ),
                )
            }
            batch.commit().await()
        }
    }

    /**
     * 익명 계정으로 들어간다.
     *
     * 누구인지는 중요하지 않다. Firestore 규칙에서 **앱을 거쳐 들어왔는지**만 가리는 용도다.
     * 어느 기록을 보느냐는 동기화 코드가 정한다.
     */
    private suspend fun signInIfNeeded() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    private fun DocumentSnapshot.toRecord(): SyncRecord? {
        val entity = getString("entity") ?: return null
        val uid = getString("uid") ?: return null
        val payload = getString("payload") ?: return null
        return SyncRecord(
            entity = entity,
            uid = uid,
            tripUid = getString("tripUid"),
            updatedAt = getLong(FIELD_UPDATED_AT) ?: 0L,
            payload = payload,
        )
    }

    private fun DocumentSnapshot.toDeletion(): DeletionEntity? {
        val entity = getString("entity") ?: return null
        val uid = getString("uid") ?: return null
        return DeletionEntity(entity, uid, getLong(FIELD_DELETED_AT) ?: 0L)
    }

    /**
     * 문서 이름.
     *
     * uid에는 날짜나 준비물 이름처럼 문서 이름에 못 쓰는 글자가 섞일 수 있어 해시로 바꾼다.
     */
    private fun documentId(entity: String, uid: String): String {
        val safe = uid.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (safe && uid.length <= MAX_ID_LENGTH) "${entity}_$uid" else "${entity}_${hash(uid)}"
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SPACES = "spaces"
        const val RECORDS = "records"
        const val DELETIONS = "deletions"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_DELETED_AT = "deletedAt"

        /** 기기 시계 차이를 견디는 여유. 이만큼 앞에서부터 다시 본다. */
        const val SLACK_MILLIS = 5 * 60 * 1000L
        const val BATCH_LIMIT = 400
        const val MAX_ID_LENGTH = 100
    }
}

/** Firestore 문서 하나에 해당하는 기록. [payload]는 [SyncCodec]이 만든 JSON이다. */
data class SyncRecord(
    val entity: String,
    val uid: String,
    val tripUid: String?,
    val updatedAt: Long,
    val payload: String,
)

/** 받아 온 기록을 합치기 좋은 꼴로 모은 결과. */
data class IncomingSnapshot(
    val snapshot: SyncSnapshot,
    /** 붙을 여행을 못 찾아 이번에는 넘긴 기록 수. */
    val unresolved: Int,
)

/**
 * 기록 한 벌과 문서 목록 사이를 오간다.
 *
 * Firestore에는 기록 하나가 문서 하나로 들어간다. 여행 단위로 묶인 [SyncSnapshot]과 달리
 * 평평한 목록이라, 받아 올 때 다시 여행별로 모아 준다.
 */
object SyncRecords {

    const val TRIP = "trip"
    const val EXPENSE = "expense"
    const val BOOKING = "booking"
    const val STOP = "stop"
    const val NOTE = "note"
    const val PACKING = "packing"
    const val CASH = "cash"
    const val PURCHASE = "purchase"

    fun flatten(snapshot: SyncSnapshot): List<SyncRecord> = buildList {
        snapshot.trips.forEach { bundle ->
            val tripUid = bundle.trip.uid
            add(
                SyncRecord(
                    TRIP,
                    tripUid,
                    null,
                    bundle.trip.updatedAt,
                    SyncCodec.tripToJson(bundle.trip).toString(),
                ),
            )
            bundle.expenses.forEach {
                add(SyncRecord(EXPENSE, it.uid, tripUid, it.updatedAt, SyncCodec.expenseToJson(it).toString()))
            }
            bundle.bookings.forEach {
                add(SyncRecord(BOOKING, it.uid, tripUid, it.updatedAt, SyncCodec.bookingToJson(it).toString()))
            }
            bundle.stops.forEach {
                add(SyncRecord(STOP, it.uid, tripUid, it.updatedAt, SyncCodec.stopToJson(it).toString()))
            }
            // 메모와 준비물에는 uid가 없다. 여행과 날짜(또는 이름)가 곧 그 기록을 가리킨다.
            bundle.notes.forEach {
                add(
                    SyncRecord(
                        NOTE,
                        "$tripUid|${it.date}",
                        tripUid,
                        it.updatedAt,
                        SyncCodec.noteToJson(it).toString(),
                    ),
                )
            }
            bundle.packing.forEach {
                add(
                    SyncRecord(
                        PACKING,
                        "$tripUid|${it.itemName}",
                        tripUid,
                        it.updatedAt,
                        SyncCodec.packingToJson(it).toString(),
                    ),
                )
            }

            bundle.cash.forEach {
                add(SyncRecord(CASH, it.uid, tripUid, it.updatedAt, SyncCodec.cashToJson(it).toString()))
            }
        }

        snapshot.purchases.forEach { record ->
            add(
                SyncRecord(
                    PURCHASE,
                    record.purchase.uid,
                    record.tripUid,
                    record.purchase.updatedAt,
                    SyncCodec.purchaseToJson(record.purchase, record.tripUid).toString(),
                ),
            )
        }
    }

    /**
     * 받아 온 문서들을 기록 한 벌로 모은다.
     *
     * 여행에 딸린 기록인데 그 여행을 아직 모르면 넣을 곳이 없다. 그런 기록은 세어서 알려 주고
     * 다음 동기화에서 다시 받는다.
     */
    suspend fun snapshotOf(
        records: List<SyncRecord>,
        deletions: List<DeletionEntity>,
        tripOf: suspend (String) -> Trip?,
    ): IncomingSnapshot {
        val byEntity = records.groupBy { it.entity }

        val trips = byEntity[TRIP].orEmpty()
            .mapNotNull { runCatching { SyncCodec.tripFromJson(JSONObject(it.payload)) }.getOrNull() }
            .associateBy { it.uid }
            .toMutableMap()

        var unresolved = 0
        val childTripUids = records
            .filter { it.entity != TRIP && it.entity != PURCHASE }
            .mapNotNull { it.tripUid }
            .toSet()

        // 이번에 안 바뀐 여행이라도, 그 안의 기록이 바뀌었으면 여행을 알아야 붙일 수 있다.
        childTripUids.forEach { uid ->
            if (trips.containsKey(uid)) return@forEach
            val local = tripOf(uid)
            if (local == null) unresolved++ else trips[uid] = local
        }

        val bundles = trips.values.map { trip ->
            TripBundle(
                trip = trip,
                expenses = records.of(EXPENSE, trip.uid) { SyncCodec.expenseFromJson(it) },
                bookings = records.of(BOOKING, trip.uid) { SyncCodec.bookingFromJson(it) },
                stops = records.of(STOP, trip.uid) { SyncCodec.stopFromJson(it) },
                notes = records.of(NOTE, trip.uid) { SyncCodec.noteFromJson(it) },
                packing = records.of(PACKING, trip.uid) { SyncCodec.packingFromJson(it) },
                cash = records.of(CASH, trip.uid) { SyncCodec.cashFromJson(it) },
            )
        }

        val purchases = byEntity[PURCHASE].orEmpty().mapNotNull { record ->
            runCatching { SyncCodec.purchaseRecordFromJson(JSONObject(record.payload)) }.getOrNull()
        }

        return IncomingSnapshot(
            snapshot = SyncSnapshot(
                exportedAt = System.currentTimeMillis(),
                trips = bundles,
                purchases = purchases,
                deletions = deletions,
            ),
            unresolved = unresolved,
        )
    }

    private fun <T> List<SyncRecord>.of(
        entity: String,
        tripUid: String,
        parse: (JSONObject) -> T,
    ): List<T> = filter { it.entity == entity && it.tripUid == tripUid }
        .mapNotNull { runCatching { parse(JSONObject(it.payload)) }.getOrNull() }
}
