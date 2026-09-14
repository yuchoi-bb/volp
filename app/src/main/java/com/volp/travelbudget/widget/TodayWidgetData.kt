package com.volp.travelbudget.widget

import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.summary.TripSummaries
import java.time.LocalDate
import java.time.LocalDateTime

/** 위젯 한 장에 들어갈 값. 두 줄이 전부이므로 미리 글자로 만들어 둔다. */
data class TodayWidgetData(
    val tripId: Long?,
    val title: String,
    val money: String,
    val next: String,
    val overspent: Boolean = false,
) {
    companion object {
        /** 여행이 없을 때. 위젯을 눌러 앱을 열면 목록이 뜬다. */
        val IDLE = TodayWidgetData(
            tripId = null,
            title = "Volp",
            money = "여행 중이 아님",
            next = "누르면 앱이 열립니다",
        )
    }
}

/**
 * 위젯에 무엇을 쓸지 정한다.
 *
 * 홈 화면에서 한눈에 보고 싶은 것은 둘뿐이다. **오늘 더 쓸 수 있는 돈**과 **다음에 갈 곳**.
 * 그 밖의 것은 앱을 열어서 보면 된다.
 */
object TodayWidget {

    /** 항공·숙박은 하루 씀씀이로 보지 않는다. 미리 낸 돈이기 때문이다. */
    private val UPFRONT = setOf(ExpenseCategory.FLIGHT, ExpenseCategory.LODGING)

    suspend fun load(
        repository: TripRepository,
        stopsOf: suspend (Long) -> List<ItineraryStop>,
        bookingsOf: suspend (Long) -> List<Booking>,
        now: LocalDateTime = LocalDateTime.now(),
    ): TodayWidgetData {
        val today = now.toLocalDate()
        val trip = pickTrip(repository.tripsOnce(), today) ?: return TodayWidgetData.IDLE

        val expenses = repository.expensesOnce(trip.id)
        val summary = TripSummaries.summarize(
            trip = trip,
            spentByCategory = expenses.groupBy { it.category }
                .mapValues { (_, items) -> items.sumOf { it.amountKrw } },
            today = today,
        )
        val spentToday = expenses
            .filter { it.date == today && it.category !in UPFRONT }
            .sumOf { it.amountKrw }

        val allowance = summary.dailyAllowance
        val left = allowance?.minus(spentToday)

        return TodayWidgetData(
            tripId = trip.id,
            title = trip.title,
            money = when {
                left == null -> "오늘 ${formatShort(spentToday)} 씀"
                left >= 0L -> "오늘 ${formatShort(left)} 남음"
                else -> "오늘 ${formatShort(-left)} 초과"
            },
            next = nextLine(stopsOf(trip.id), bookingsOf(trip.id), now),
            overspent = left != null && left < 0L,
        )
    }

    /** 여행 중이면 그 여행, 아니면 가장 가까운 다음 여행. 지난 여행은 보여 줄 것이 없다. */
    private fun pickTrip(trips: List<Trip>, today: LocalDate): Trip? =
        trips.firstOrNull { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) }
            ?: trips.filter { it.startDate.isAfter(today) }.minByOrNull { it.startDate }

    /** 다음에 갈 곳 한 줄. 시각이 있는 것을 먼저 본다. */
    private fun nextLine(
        stops: List<ItineraryStop>,
        bookings: List<Booking>,
        now: LocalDateTime,
    ): String {
        val fromBookings = bookings
            .filter { it.startAt.isAfter(now) }
            .minByOrNull { it.startAt }
            ?.let { "${it.startAt.toLocalTime()} ${it.title}" }

        val fromStops = stops
            .filter { it.date == now.toLocalDate() }
            .mapNotNull { stop ->
                val time = stop.startTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
                if (time == null || !now.toLocalTime().isBefore(time)) null else time to stop.name
            }
            .minByOrNull { it.first }
            ?.let { (time, name) -> "$time $name" }

        return fromStops ?: fromBookings ?: "다음 일정 없음"
    }

    private fun formatShort(amount: Long): String = when {
        amount >= 10_000L -> "${amount / 10_000}만원"
        else -> "${amount}원"
    }
}
