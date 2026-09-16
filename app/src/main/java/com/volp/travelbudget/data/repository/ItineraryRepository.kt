package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.local.DeletionEntity
import com.volp.travelbudget.data.local.ItineraryDao
import com.volp.travelbudget.data.local.ItineraryStopEntity
import com.volp.travelbudget.data.local.PackingCheckEntity
import com.volp.travelbudget.data.local.SyncDao
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.itinerary.PlanFixity
import com.volp.travelbudget.domain.sync.SyncIds
import com.volp.travelbudget.domain.travel.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ItineraryRepository(
    private val dao: ItineraryDao,
    private val syncDao: SyncDao,
) {

    fun observeStops(tripId: Long): Flow<List<ItineraryStop>> =
        dao.observeStops(tripId).map { list -> list.map { it.toDomain() } }

    /** 지금 시점의 일정. 위젯처럼 한 번만 읽으면 되는 곳에 쓴다. */
    suspend fun stopsOnce(tripId: Long): List<ItineraryStop> =
        syncDao.stopsOf(tripId).map { it.toDomain() }

    suspend fun addStop(
        tripId: Long,
        date: LocalDate,
        name: String,
        address: String = "",
        point: GeoPoint? = null,
        startTime: String? = null,
        memo: String = "",
        fixity: PlanFixity = PlanFixity.FLEXIBLE,
    ): Long = dao.insert(
        ItineraryStopEntity(
            uid = SyncIds.newUid(),
            updatedAt = SyncIds.now(),
            tripId = tripId,
            date = date,
            sortOrder = dao.nextSortOrder(tripId, date),
            name = name.trim(),
            address = address,
            latitude = point?.latitude,
            longitude = point?.longitude,
            startTime = startTime,
            memo = memo,
            fixity = fixity.name,
        ),
    )

    suspend fun updateStop(stop: ItineraryStop) = dao.update(stop.stamped().toEntity())

    suspend fun deleteStop(stopId: Long) {
        syncDao.stopUid(stopId)?.let { uid ->
            syncDao.recordDeletion(DeletionEntity("stop", uid, SyncIds.now()))
        }
        dao.deleteById(stopId)
    }

    /**
     * 장소를 하루 안에서 위아래로 옮긴다.
     *
     * 순서를 바꾸면 동선과 이동수단 안내도 함께 바뀐다.
     */
    suspend fun move(stop: ItineraryStop, up: Boolean) {
        val sameDay = dao.observeStopsOnce(stop.tripId, stop.date).sortedBy { it.sortOrder }
        val index = sameDay.indexOfFirst { it.id == stop.id }
        if (index < 0) return

        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in sameDay.indices) return

        val current = sameDay[index]
        val other = sameDay[swapWith]
        val now = SyncIds.now()
        dao.update(current.copy(sortOrder = other.sortOrder, updatedAt = now))
        dao.update(other.copy(sortOrder = current.sortOrder, updatedAt = now))
    }

    /**
     * 다른 날로 옮긴다.
     *
     * 옮긴 날의 맨 뒤에 붙는다. 시각이 적힌 것은 그 시각 자리에 알아서 서므로 뒤에 붙여도 된다.
     */
    suspend fun moveToDate(stop: ItineraryStop, date: LocalDate) {
        if (stop.date == date) return
        dao.update(
            stop.copy(
                date = date,
                sortOrder = dao.nextSortOrder(stop.tripId, date),
            ).stamped().toEntity(),
        )
    }

    /** 정리한 차례를 그대로 저장한다. */
    suspend fun reorder(tripId: Long, date: LocalDate, orderedIds: List<Long>) {
        val byId = dao.observeStopsOnce(tripId, date).associateBy { it.id }
        val now = SyncIds.now()
        orderedIds.forEachIndexed { index, id ->
            val stop = byId[id] ?: return@forEachIndexed
            if (stop.sortOrder != index) dao.update(stop.copy(sortOrder = index, updatedAt = now))
        }
    }

    fun observePackingChecks(tripId: Long): Flow<Set<String>> =
        dao.observePackingChecks(tripId).map { checks ->
            checks.filter { it.checked }.map { it.itemName }.toSet()
        }

    suspend fun setPackingCheck(tripId: Long, itemName: String, checked: Boolean) =
        dao.upsertPackingCheck(PackingCheckEntity(tripId, itemName, checked, SyncIds.now()))
}

private fun ItineraryStopEntity.toDomain() = ItineraryStop(
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
    fixity = PlanFixity.fromName(fixity),
)

private fun ItineraryStop.toEntity() = ItineraryStopEntity(
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
    fixity = fixity.name,
)

private fun ItineraryStop.stamped(): ItineraryStop =
    copy(uid = uid.ifBlank { SyncIds.newUid() }, updatedAt = SyncIds.now())
