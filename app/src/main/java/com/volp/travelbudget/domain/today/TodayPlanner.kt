package com.volp.travelbudget.domain.today

import com.volp.travelbudget.domain.itinerary.DayTimeline
import com.volp.travelbudget.domain.itinerary.PlanEntry
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.travel.Geo
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.RouteAdvisor
import com.volp.travelbudget.domain.travel.TransportSuggestion
import java.time.LocalDateTime

/**
 * 다음 일정까지 언제 나서야 하는지.
 *
 * @param leaveBy 이 시각에는 출발해야 약속 시각에 닿는다
 * @param late 이미 그 시각을 지났는지
 */
data class DepartureAdvice(
    val leaveBy: LocalDateTime,
    val travelMinutes: Int,
    val suggestion: TransportSuggestion,
    val late: Boolean,
)

/**
 * 오늘 화면이 답해야 하는 것을 계산한다. "다음에 어디로, 언제 나서야, 어떻게 가는가."
 */
object TodayPlanner {

    /** 길에서 헤매거나 표를 끊는 데 드는 여유. */
    private const val BUFFER_MINUTES = 5

    /**
     * 지금 기준으로 다음에 갈 곳.
     *
     * 시각이 지난 줄은 지나간 것으로 보고, 시각이 없는 줄은 아직 남은 것으로 본다.
     */
    fun nextEntry(timeline: DayTimeline?, now: LocalDateTime): PlanEntry? {
        val entries = timeline?.entries ?: return null
        return entries.firstOrNull { entry ->
            val time = entry.time ?: return@firstOrNull true
            !LocalDateTime.of(entry.date, time).isBefore(now)
        }
    }

    /** 오늘 이후 가장 가까운 줄. 오늘 남은 일정이 없을 때 쓴다. */
    fun upcomingEntry(timelines: List<DayTimeline>, now: LocalDateTime): PlanEntry? {
        val today = now.toLocalDate()
        nextEntry(timelines.firstOrNull { it.date == today }, now)?.let { return it }

        return timelines
            .filter { it.date.isAfter(today) }
            .firstNotNullOfOrNull { it.entries.firstOrNull() }
    }

    /**
     * 지금 있는 곳에서 다음 일정까지의 안내.
     *
     * 약속 시각이나 좌표를 모르면 안내할 것이 없으므로 null을 돌려준다. 억지로 숫자를
     * 지어내면 그 숫자를 믿고 늦게 된다.
     */
    fun advice(
        entry: PlanEntry,
        from: GeoPoint?,
        now: LocalDateTime,
        region: Region,
    ): DepartureAdvice? {
        val time = entry.time ?: return null
        val target = entry.point ?: return null
        val origin = from ?: return null

        val suggestion = RouteAdvisor.suggest(Geo.distanceMeters(origin, target), region)
        val needed = suggestion.minutes + BUFFER_MINUTES
        val leaveBy = LocalDateTime.of(entry.date, time).minusMinutes(needed.toLong())

        return DepartureAdvice(
            leaveBy = leaveBy,
            travelMinutes = suggestion.minutes,
            suggestion = suggestion,
            late = now.isAfter(leaveBy),
        )
    }
}
