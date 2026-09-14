package com.volp.travelbudget.data.sync

import com.volp.travelbudget.data.local.BookingDao
import com.volp.travelbudget.data.local.DayNoteEntity
import com.volp.travelbudget.data.local.DeletionEntity
import com.volp.travelbudget.data.local.ExpenseDao
import com.volp.travelbudget.data.local.PackingCheckEntity
import com.volp.travelbudget.data.local.PurchaseDao
import com.volp.travelbudget.data.local.SyncDao
import com.volp.travelbudget.data.local.TripDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.sync.SyncIds
import com.volp.travelbudget.domain.sync.SyncMerge
import com.volp.travelbudget.domain.sync.Tombstone
import com.volp.travelbudget.domain.travel.GeoPoint

/**
 * 기기 두 대의 기록을 맞춘다.
 *
 * 한쪽으로 덮어쓰면 다른 폰에서 넣은 것이 사라지므로, 기록 하나하나를 uid로 짝지어 합친다.
 * 합친 뒤에는 이 기기가 곧 합친 결과이므로 그대로 다시 올리면 된다.
 */
/** 동기화 결과. [pulled]는 저쪽에서 받아 온 기록 수다. */
data class SyncResult(
    val snapshot: SyncSnapshot,
    val pulled: Int,
)

class SyncEngine(
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao,
    private val bookingDao: BookingDao,
    private val purchaseDao: PurchaseDao,
    private val syncDao: SyncDao,
) {

    /** 지금 이 기기에 있는 기록 한 벌. */
    suspend fun snapshot(): SyncSnapshot {
        val tripEntities = tripDao.findAll()
        // 여행을 가리킬 때는 행 번호 대신 uid를 쓴다. 기기마다 번호가 다르게 매겨지기 때문이다.
        val tripUidById = tripEntities.associate { it.id to it.uid }

        val trips = tripEntities.map { entity ->
            val trip = entity.toDomain()
            TripBundle(
                trip = trip,
                expenses = expenseDao.findByTrip(entity.id).map { it.toDomain() },
                bookings = bookingDao.findByTrip(entity.id).map { it.toDomain() },
                stops = syncDao.stopsOf(entity.id).map { it.toStop() },
                notes = syncDao.notesOf(entity.id).map { DayNote(it.date, it.text, it.updatedAt) },
                packing = syncDao.packingChecksOf(entity.id).map {
                    PackingCheck(it.itemName, it.checked, it.updatedAt)
                },
                cash = syncDao.cashTopUpsOf(entity.id).map { it.toDomain() },
            )
        }

        val purchases = purchaseDao.findAll().map { entity ->
            PurchaseRecord(entity.toDomain(), entity.tripId?.let { tripUidById[it] })
        }

        return SyncSnapshot(
            exportedAt = SyncIds.now(),
            trips = trips,
            purchases = purchases,
            deletions = syncDao.deletions(),
        )
    }

    /**
     * 드라이브에 있던 기록과 이 기기의 기록을 합쳐 이 기기에 반영한다.
     *
     * @return 합친 결과. 이대로 드라이브에 올리면 두 기기가 같아진다.
     */
    suspend fun mergeIn(remote: SyncSnapshot): SyncResult {
        pulled = 0
        val localTombstones = syncDao.deletions()
        // 저쪽이 지운 것도 이 기기에 남겨야 다음 동기화에서 되살아나지 않는다.
        val newRemoteTombstones = remote.deletions.filterNot { incoming ->
            localTombstones.any { it.entity == incoming.entity && it.uid == incoming.uid }
        }
        if (newRemoteTombstones.isNotEmpty()) syncDao.recordDeletions(newRemoteTombstones)

        val tombstones = (localTombstones + remote.deletions).groupBy { it.entity }

        mergeTrips(remote, tombstones)
        remote.trips.forEach { bundle -> mergeChildren(bundle, tombstones) }
        mergePurchases(remote.purchases, tombstones)

        // 합친 결과는 곧 지금 이 기기의 상태다.
        return SyncResult(snapshot(), pulled)
    }

    /** 이번 동기화에서 저쪽으로부터 받아 온 기록 수. 사용자에게 무엇이 일어났는지 알려 준다. */
    private var pulled = 0

    private suspend fun mergeTrips(
        remote: SyncSnapshot,
        tombstones: Map<String, List<DeletionEntity>>,
    ) {
        val outcome = SyncMerge.merge(
            local = tripDao.findAll().map { it.toDomain() },
            remote = remote.trips.map { it.trip },
            localTombstones = tombstones.stones("trip"),
        )

        outcome.incoming.forEach { trip ->
            val existing = syncDao.tripByUid(trip.uid)
            syncDao.upsertTrip(trip.copy(id = existing?.id ?: 0L).toEntity())
            pulled++
        }
        outcome.removedUids.forEach { syncDao.deleteTripByUid(it) }
    }

    private suspend fun mergeChildren(
        bundle: TripBundle,
        tombstones: Map<String, List<DeletionEntity>>,
    ) {
        // 여행이 지워졌거나 아직 없으면 그 안의 기록도 넣을 곳이 없다.
        val trip = syncDao.tripByUid(bundle.trip.uid) ?: return
        val tripId = trip.id

        val expenses = SyncMerge.merge(
            local = expenseDao.findByTrip(tripId).map { it.toDomain() },
            remote = bundle.expenses,
            localTombstones = tombstones.stones("expense"),
        )
        expenses.incoming.forEach { expense ->
            val existing = syncDao.expenseByUid(expense.uid)
            syncDao.upsertExpense(
                expense.copy(id = existing?.id ?: 0L, tripId = tripId).toEntity(),
            )
            pulled++
        }
        expenses.removedUids.forEach { syncDao.deleteExpenseByUid(it) }

        val bookings = SyncMerge.merge(
            local = bookingDao.findByTrip(tripId).map { it.toDomain() },
            remote = bundle.bookings,
            localTombstones = tombstones.stones("booking"),
        )
        bookings.incoming.forEach { booking ->
            val existing = syncDao.bookingByUid(booking.uid)
            syncDao.upsertBooking(
                booking.copy(id = existing?.id ?: 0L, tripId = tripId).toEntity(),
            )
            pulled++
        }
        bookings.removedUids.forEach { syncDao.deleteBookingByUid(it) }

        val stops = SyncMerge.merge(
            local = syncDao.stopsOf(tripId).map { it.toStop() },
            remote = bundle.stops,
            localTombstones = tombstones.stones("stop"),
        )
        stops.incoming.forEach { stop ->
            val existing = syncDao.stopByUid(stop.uid)
            syncDao.upsertStop(stop.copy(id = existing?.id ?: 0L, tripId = tripId).toStopEntity())
            pulled++
        }
        stops.removedUids.forEach { syncDao.deleteStopByUid(it) }

        val cash = SyncMerge.merge(
            local = syncDao.cashTopUpsOf(tripId).map { it.toDomain() },
            remote = bundle.cash,
            localTombstones = tombstones.stones("cash"),
        )
        cash.incoming.forEach { topUp ->
            val existing = syncDao.cashTopUpByUid(topUp.uid)
            syncDao.upsertCashTopUp(
                topUp.copy(id = existing?.id ?: 0L, tripId = tripId).toEntity(),
            )
            pulled++
        }
        cash.removedUids.forEach { syncDao.deleteCashTopUpByUid(it) }

        mergeNotes(tripId, bundle.notes)
        mergePacking(tripId, bundle.packing)
    }

    /**
     * 구매는 여행에 안 붙어 있을 수도 있어 따로 합친다.
     *
     * 붙어 있더라도 그 여행이 이 기기에 아직 없을 수 있다. 그럴 때는 여행 없이 넣어 두고,
     * 다음 동기화에서 여행이 들어오면 그때 이어 붙는다.
     */
    private suspend fun mergePurchases(
        records: List<PurchaseRecord>,
        tombstones: Map<String, List<DeletionEntity>>,
    ) {
        val outcome = SyncMerge.merge(
            local = purchaseDao.findAll().map { it.toDomain() },
            remote = records.map { it.purchase },
            localTombstones = tombstones.stones("purchase"),
        )

        val tripUidByPurchase = records.associate { it.purchase.uid to it.tripUid }
        outcome.incoming.forEach { purchase ->
            val existing = purchaseDao.findByUid(purchase.uid)
            val tripId = tripUidByPurchase[purchase.uid]?.let { syncDao.tripByUid(it)?.id }
            purchaseDao.upsert(
                purchase.copy(id = existing?.id ?: 0L, tripId = tripId).toEntity(),
            )
            pulled++
        }
        outcome.removedUids.forEach { purchaseDao.deleteByUid(it) }
    }

    /** 메모와 준비물은 날짜와 이름이 곧 열쇠다. 나중에 고친 쪽을 남긴다. */
    private suspend fun mergeNotes(tripId: Long, remote: List<DayNote>) {
        val local = syncDao.notesOf(tripId).associateBy { it.date }
        remote.forEach { note ->
            val mine = local[note.date]
            if (mine == null || note.updatedAt > mine.updatedAt) {
                syncDao.upsertNote(DayNoteEntity(tripId, note.date, note.text, note.updatedAt))
            }
        }
    }

    private suspend fun mergePacking(tripId: Long, remote: List<PackingCheck>) {
        val local = syncDao.packingChecksOf(tripId).associateBy { it.itemName }
        remote.forEach { check ->
            val mine = local[check.itemName]
            if (mine == null || check.updatedAt > mine.updatedAt) {
                syncDao.upsertPackingCheck(
                    PackingCheckEntity(tripId, check.itemName, check.checked, check.updatedAt),
                )
            }
        }
    }

    private fun Map<String, List<DeletionEntity>>.stones(entity: String): List<Tombstone> =
        this[entity].orEmpty().map { Tombstone(it.uid, it.deletedAt) }
}

private fun com.volp.travelbudget.data.local.ItineraryStopEntity.toStop() = ItineraryStop(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    date = date,
    sortOrder = sortOrder,
    name = name,
    address = address,
    point = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
    startTime = startTime,
    memo = memo,
)

private fun ItineraryStop.toStopEntity() = com.volp.travelbudget.data.local.ItineraryStopEntity(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    date = date,
    sortOrder = sortOrder,
    name = name,
    address = address,
    latitude = point?.latitude,
    longitude = point?.longitude,
    startTime = startTime,
    memo = memo,
)
