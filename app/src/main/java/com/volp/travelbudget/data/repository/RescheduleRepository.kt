package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.local.BookingDao
import com.volp.travelbudget.data.local.ItineraryDao
import com.volp.travelbudget.data.local.TripDao
import com.volp.travelbudget.domain.itinerary.PlanFixity
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.sync.SyncIds
import com.volp.travelbudget.domain.trip.RescheduleItem
import com.volp.travelbudget.domain.trip.TripReschedule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 여행 날짜가 바뀌었을 때 그 안의 일정과 예약을 함께 옮긴다.
 *
 * 무엇을 어디로 옮길지는 도메인([TripReschedule])이 정하고, 여기서는 그대로 쓴다. 예약은 실제로
 * 잡혀 있는 것이라 고정으로 본다 — 여행 날짜를 바꿨다고 항공편이 따라 옮겨지지는 않는다.
 */
class RescheduleRepository(
    private val tripDao: TripDao,
    private val itineraryDao: ItineraryDao,
    private val bookingDao: BookingDao,
) {

    /** 지금 이 여행에 붙어 있는, 날짜를 가진 것들. */
    suspend fun itemsOf(tripId: Long): List<RescheduleItem> {
        val stops = itineraryDao.stopsOf(tripId).map { stop ->
            RescheduleItem(
                key = "stop:${stop.id}",
                label = stop.name,
                date = stop.date,
                fixed = PlanFixity.fromName(stop.fixity) == PlanFixity.FIXED,
            )
        }
        val bookings = bookingDao.findByTrip(tripId).map { booking ->
            RescheduleItem(
                key = "booking:${booking.id}",
                label = booking.title,
                date = booking.startAt.toLocalDate(),
                // 예약은 실제로 잡혀 있다. 앱이 날짜만 바꾸면 장부와 예약이 어긋난다.
                fixed = true,
            )
        }
        return (stops + bookings).sortedBy { it.date }
    }

    /**
     * 여행 날짜를 바꾸고 일정을 옮긴다.
     *
     * @param budget null이 아니면 예산도 새 기간으로 다시 계산해 넣는다.
     * @return 자리를 옮긴 것의 수.
     */
    suspend fun apply(
        tripId: Long,
        newStart: LocalDate,
        newEnd: LocalDate,
        moveFixed: Boolean,
        pullInside: Boolean,
        budget: Map<ExpenseCategory, Long>? = null,
    ): Int {
        val trip = tripDao.findById(tripId) ?: return 0
        val plan = TripReschedule.plan(
            items = itemsOf(tripId),
            oldStart = trip.startDate,
            oldEnd = trip.endDate,
            newStart = newStart,
            newEnd = newEnd,
            moveFixed = moveFixed,
            pullInside = pullInside,
        )

        plan.moved.forEach { change ->
            val (kind, rawId) = change.item.key.split(":")
            val id = rawId.toLongOrNull() ?: return@forEach

            when (kind) {
                "stop" -> itineraryDao.findStop(id)?.let { stop ->
                    itineraryDao.update(
                        stop.copy(date = change.newDate, updatedAt = SyncIds.now()),
                    )
                }

                "booking" -> bookingDao.findById(id)?.let { booking ->
                    // 예약은 시각까지 들고 있다. 날짜만 옮기고 시각은 그대로 둔다.
                    val shift = ChronoUnit.DAYS.between(booking.startAt.toLocalDate(), change.newDate)
                    bookingDao.update(
                        booking.copy(
                            startAt = booking.startAt.plusDays(shift),
                            endAt = booking.endAt?.plusDays(shift),
                            updatedAt = SyncIds.now(),
                        ),
                    )
                }
            }
        }

        tripDao.update(
            trip.copy(
                startDate = newStart,
                endDate = newEnd,
                predictedBudget = budget ?: trip.predictedBudget,
                plannedBudget = budget ?: trip.plannedBudget,
                updatedAt = SyncIds.now(),
            ),
        )

        return plan.moved.size
    }
}
