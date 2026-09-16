package com.volp.travelbudget.domain.itinerary

import com.volp.travelbudget.domain.travel.Geo
import com.volp.travelbudget.domain.travel.GeoPoint

/** 정리한 뒤의 자리 하나. */
data class ArrangedStop(
    val stop: ItineraryStop,
    val order: Int,
    /** 왜 이 자리인지. 사람이 보고 납득할 수 있어야 바꿔도 된다고 여긴다. */
    val reason: String,
) {
    val moved: Boolean get() = order != stop.sortOrder
}

/**
 * 하루 안의 자리를 정리한다.
 *
 * 일정은 손으로 넣다 보면 생각난 차례대로 쌓인다. 그러면 오전에 동쪽, 오후에 서쪽, 저녁에 다시
 * 동쪽으로 오가게 된다. 그렇다고 앱이 시각을 무시하고 거리만 보고 늘어놓으면 예약 시각을 놓친다.
 *
 * 그래서 두 가지를 지킨다.
 *
 * 1. **시각이 적힌 것은 그 차례 그대로다.** 열한 시 예약은 열한 시 자리에 있어야 한다. 이것이
 *    하루의 뼈대가 된다.
 * 2. **시각이 없는 것만 옮긴다.** 원래 놓여 있던 두 뼈대 사이에서만 자리를 바꾸고, 그 안에서는
 *    직전 자리에서 가까운 곳부터 간다. 뼈대를 넘어가면 예약 시각과 어긋나기 때문이다.
 *
 * 좌표를 모르는 곳은 가까운지 알 수 없으므로 원래 차례를 지킨다. 억지로 끼워 넣으면 왜 그 자리에
 * 갔는지 설명할 수 없다.
 */
object DayArranger {

    private const val ANCHOR_REASON = "시각이 정해져 있다"
    private const val NEAR_REASON = "바로 앞에서 가깝다"
    private const val KEEP_REASON = "그대로"

    fun arrange(stops: List<ItineraryStop>): List<ArrangedStop> {
        if (stops.size < 2) return stops.mapIndexed { index, stop -> ArrangedStop(stop, index, KEEP_REASON) }

        val original = stops.sortedBy { it.sortOrder }

        // 시각이 적힌 것이 뼈대다. 그 사이의 자유로운 것만 다시 늘어놓는다.
        val result = mutableListOf<Pair<ItineraryStop, String>>()
        val gap = mutableListOf<ItineraryStop>()
        var cursor: GeoPoint? = null

        fun flushGap() {
            val ordered = nearestFirst(gap, cursor)
            ordered.forEach { (stop, reason) ->
                result += stop to reason
                stop.point?.let { cursor = it }
            }
            gap.clear()
        }

        original.forEach { stop ->
            if (stop.time != null) {
                flushGap()
                result += stop to ANCHOR_REASON
                stop.point?.let { cursor = it }
            } else {
                gap += stop
            }
        }
        flushGap()

        return result.mapIndexed { index, (stop, reason) -> ArrangedStop(stop, index, reason) }
    }

    fun changed(arranged: List<ArrangedStop>): Boolean = arranged.any { it.moved }

    /**
     * 직전 자리에서 가까운 곳부터 늘어놓는다.
     *
     * 좌표를 아는 것끼리만 견준다. 모르는 것은 원래 차례대로 뒤에 붙는다 — 어디인지 모르는 곳을
     * 가까운 곳 사이에 끼워 넣으면 그 줄은 아무도 설명할 수 없는 차례가 된다.
     */
    private fun nearestFirst(
        stops: List<ItineraryStop>,
        from: GeoPoint?,
    ): List<Pair<ItineraryStop, String>> {
        val located = stops.filter { it.point != null }.toMutableList()
        val unknown = stops.filter { it.point == null }

        if (located.size < 2 || from == null) {
            return stops.map { it to KEEP_REASON }
        }

        val ordered = mutableListOf<Pair<ItineraryStop, String>>()
        var cursor: GeoPoint = from
        while (located.isNotEmpty()) {
            val next = located.minByOrNull { Geo.distanceMeters(cursor, it.point!!) } ?: break
            located -= next
            ordered += next to NEAR_REASON
            cursor = next.point!!
        }

        return ordered + unknown.map { it to KEEP_REASON }
    }
}
