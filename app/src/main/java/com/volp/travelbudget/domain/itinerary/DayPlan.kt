package com.volp.travelbudget.domain.itinerary

import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.booking.BookingType
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.sync.Syncable
import com.volp.travelbudget.domain.travel.Geo
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.RouteAdvisor
import com.volp.travelbudget.domain.travel.TransportSuggestion
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 일정표에 넣은 장소 한 곳. */
data class ItineraryStop(
    val id: Long = 0L,
    override val uid: String = "",
    override val updatedAt: Long = 0L,
    val tripId: Long,
    val date: LocalDate,
    val sortOrder: Int,
    val name: String,
    val address: String = "",
    val point: GeoPoint? = null,
    /** `HH:mm`. 정하지 않았으면 null. */
    val startTime: String? = null,
    val memo: String = "",
    /** 날짜를 옮길 수 있는 일정인지. 투어나 항공편은 잡으면 못 옮긴다. */
    val fixity: PlanFixity = PlanFixity.FLEXIBLE,
) : Syncable {
    val time: LocalTime?
        get() = startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}

/** 숙소처럼 시작과 끝이 다른 날인 예약은 시간표에 두 번 놓인다. */
enum class BookingPhase(val label: String) {
    START("시작"),
    END("끝"),
}

/**
 * 하루 시간표에 놓이는 한 줄. 둘러볼 장소와 예약이 같은 줄 위에 섞인다.
 */
sealed interface PlanEntry {
    val date: LocalDate
    val time: LocalTime?
    val title: String
    val point: GeoPoint?

    /** 시간표에서의 자리. 시각이 없는 장소는 정해 둔 순서대로 뒤에 붙는다. */
    val order: Int

    data class Stop(val stop: ItineraryStop) : PlanEntry {
        override val date: LocalDate get() = stop.date
        override val time: LocalTime? get() = stop.time
        override val title: String get() = stop.name
        override val point: GeoPoint? get() = stop.point
        override val order: Int get() = stop.sortOrder
    }

    data class Reservation(val booking: Booking, val phase: BookingPhase) : PlanEntry {
        private val at: LocalDateTime
            get() = if (phase == BookingPhase.START) booking.startAt else booking.endAt ?: booking.startAt

        override val date: LocalDate get() = at.toLocalDate()
        override val time: LocalTime get() = at.toLocalTime()
        override val title: String get() = booking.title
        override val point: GeoPoint? get() = booking.point
        override val order: Int get() = -1
    }
}

/** 한 줄에서 다음 줄로 넘어가는 구간. */
data class RouteLeg(
    val from: PlanEntry,
    val to: PlanEntry,
    /** 좌표를 모르는 쪽이 끼어 있으면 null. */
    val suggestion: TransportSuggestion?,
)

/** 하루치 시간표와 그날의 동선. */
data class DayTimeline(
    val date: LocalDate,
    val entries: List<PlanEntry>,
    val legs: List<RouteLeg>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    val stops: List<ItineraryStop>
        get() = entries.filterIsInstance<PlanEntry.Stop>().map { it.stop }

    /** 그날 이동에 드는 예상 시간(분). 좌표를 아는 구간만 더한다. */
    val totalTravelMinutes: Int get() = legs.sumOf { it.suggestion?.minutes ?: 0 }

    /** 그날 이동에 드는 예상 교통비(원). */
    val totalTravelCostKrw: Long get() = legs.sumOf { it.suggestion?.estimatedCostKrw ?: 0L }
}

object TimelineBuilder {

    /**
     * 장소와 예약을 날짜별로 묶어 시간표를 만든다.
     *
     * 시각이 있는 줄이 먼저 시간순으로 놓이고, 시각을 정하지 않은 장소는 정해 둔 순서대로
     * 그 뒤에 붙는다. 규칙을 단순하게 둬야 사용자가 순서를 예측할 수 있다.
     *
     * @param dates 여행 기간. 비어 있는 날도 자리를 남겨 무엇을 채워야 할지 보이게 한다.
     */
    fun build(
        stops: List<ItineraryStop>,
        bookings: List<Booking>,
        dates: List<LocalDate>,
        region: Region,
    ): List<DayTimeline> {
        val entries = buildList {
            stops.forEach { add(PlanEntry.Stop(it)) }
            bookings.forEach { booking ->
                add(PlanEntry.Reservation(booking, BookingPhase.START))
                // 숙소는 체크아웃 날에도 한 번 더 놓여야 마지막 날 아침에 보인다.
                if (booking.spansNights) {
                    add(PlanEntry.Reservation(booking, BookingPhase.END))
                }
            }
        }

        val byDate = entries.groupBy { it.date }
        val allDates = (dates + byDate.keys).distinct().sorted()

        return allDates.map { date ->
            val dayEntries = byDate[date].orEmpty().sortedWith(entryOrder)
            DayTimeline(
                date = date,
                entries = dayEntries,
                legs = dayEntries.zipWithNext { from, to ->
                    RouteLeg(from, to, legSuggestion(from.point, to.point, region))
                },
            )
        }
    }

    /** 시각이 있는 줄이 먼저, 그다음 정해 둔 순서대로. */
    private val entryOrder = compareBy<PlanEntry>(
        { it.time == null },
        { it.time ?: LocalTime.MIDNIGHT },
        { it.order },
    )

    private fun legSuggestion(
        from: GeoPoint?,
        to: GeoPoint?,
        region: Region,
    ): TransportSuggestion? {
        if (from == null || to == null) return null
        return RouteAdvisor.suggest(Geo.distanceMeters(from, to), region)
    }
}

/** 숙소 예약은 체크인·체크아웃 줄에서 서로 다른 말을 보여 준다. */
fun PlanEntry.Reservation.phaseLabel(): String = when {
    booking.type == BookingType.LODGING && phase == BookingPhase.START -> "체크인"
    booking.type == BookingType.LODGING -> "체크아웃"
    phase == BookingPhase.START -> booking.type.label
    else -> "도착"
}
