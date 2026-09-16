package com.volp.travelbudget.domain.trip

import com.volp.travelbudget.domain.model.Trip
import java.time.LocalDate

/**
 * 여행을 늘어놓는 규칙.
 *
 * 날짜 하나로 줄 세우면 목록이 쓸모없어진다. 내림차순은 몇 해 뒤 여행을 맨 위에 올리고,
 * 오름차순은 몇 해 전 여행을 맨 위에 올린다. 사람이 보고 싶은 것은 **지금 가까운 여행**이다.
 */
object TripOrder {

    /** 목록에서 묶이는 갈래. 숫자가 작을수록 위에 온다. */
    private const val ONGOING = 0
    private const val UPCOMING = 1
    private const val FINISHED = 2

    /**
     * 여행 중인 것이 맨 위, 그다음이 곧 떠날 순서, 지난 여행은 최근 것부터.
     *
     * 오늘을 가운데 두고 양쪽으로 멀어지는 차례다.
     */
    fun upcomingFirst(today: LocalDate): Comparator<Trip> =
        compareBy<Trip> { group(it, today) }
            .thenBy { trip ->
                // 다가오는 여행은 가까운 것부터, 지난 여행은 최근 것부터.
                if (group(trip, today) == FINISHED) -trip.endDate.toEpochDay() else trip.startDate.toEpochDay()
            }
            .thenBy { it.title }

    /**
     * 손으로 정한 차례.
     *
     * 아직 손대지 않은 여행(자리 번호 0)은 뒤로 밀지 않고 [upcomingFirst] 규칙을 따른다.
     * 그래야 '내 순서'로 바꾸는 순간 목록이 뒤집히지 않는다.
     */
    fun manual(today: LocalDate): Comparator<Trip> =
        compareBy<Trip> { if (it.sortOrder > 0) it.sortOrder else Int.MAX_VALUE }
            .then(upcomingFirst(today))

    private fun group(trip: Trip, today: LocalDate): Int = when {
        today.isBefore(trip.startDate) -> UPCOMING
        today.isAfter(trip.endDate) -> FINISHED
        else -> ONGOING
    }
}
