package com.volp.travelbudget.domain.itinerary

import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.travel.Geo
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.RouteAdvisor
import com.volp.travelbudget.domain.travel.TransportSuggestion
import java.time.LocalDate

/** 일정표에 넣은 장소 한 곳. */
data class ItineraryStop(
    val id: Long = 0L,
    val tripId: Long,
    val date: LocalDate,
    val sortOrder: Int,
    val name: String,
    val address: String = "",
    val point: GeoPoint? = null,
    /** `HH:mm`. 정하지 않았으면 null. */
    val startTime: String? = null,
    val memo: String = "",
)

/** 장소에서 다음 장소로 넘어가는 구간. */
data class RouteLeg(
    val from: ItineraryStop,
    val to: ItineraryStop,
    /** 좌표를 모르는 장소가 끼어 있으면 null. */
    val suggestion: TransportSuggestion?,
)

/** 하루치 일정과 그날의 동선. */
data class DayPlan(
    val date: LocalDate,
    val stops: List<ItineraryStop>,
    val legs: List<RouteLeg>,
) {
    val isEmpty: Boolean get() = stops.isEmpty()

    /** 그날 이동에 드는 예상 시간(분). 좌표를 아는 구간만 더한다. */
    val totalTravelMinutes: Int get() = legs.sumOf { it.suggestion?.minutes ?: 0 }

    /** 그날 이동에 드는 예상 교통비(원). */
    val totalTravelCostKrw: Long get() = legs.sumOf { it.suggestion?.estimatedCostKrw ?: 0L }
}

object DayPlanBuilder {

    /**
     * 장소 목록을 날짜별로 묶고 이어지는 구간마다 이동수단을 붙인다.
     *
     * @param dates 여행 기간. 일정이 비어 있는 날도 자리를 남겨 무엇을 채워야 할지 보이게 한다.
     */
    fun build(
        stops: List<ItineraryStop>,
        dates: List<LocalDate>,
        region: Region,
    ): List<DayPlan> {
        val byDate = stops.groupBy { it.date }
        val allDates = (dates + byDate.keys).distinct().sorted()

        return allDates.map { date ->
            val dayStops = byDate[date].orEmpty().sortedBy { it.sortOrder }
            DayPlan(
                date = date,
                stops = dayStops,
                legs = dayStops.zipWithNext { from, to ->
                    RouteLeg(
                        from = from,
                        to = to,
                        suggestion = legSuggestion(from.point, to.point, region),
                    )
                },
            )
        }
    }

    private fun legSuggestion(
        from: GeoPoint?,
        to: GeoPoint?,
        region: Region,
    ): TransportSuggestion? {
        if (from == null || to == null) return null
        return RouteAdvisor.suggest(Geo.distanceMeters(from, to), region)
    }
}
