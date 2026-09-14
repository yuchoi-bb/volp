package com.volp.travelbudget.ui.trips

import com.volp.travelbudget.data.repository.TripWithSpending

/** 여행 목록을 늘어놓는 차례. 여행이 여러 개가 되면 날짜로 찾는 편이 빠르다. */
enum class TripSort(val label: String) {
    /** 다가오는 여행과 방금 다녀온 여행이 위로. */
    START_DESC("최신 날짜순"),

    /** 지난 여행부터 차례로. 기록을 훑어볼 때 쓴다. */
    START_ASC("오래된 날짜순"),

    /** 만든 차례. 날짜를 아직 안 정한 여행을 찾을 때 쓴다. */
    CREATED_DESC("등록순"),
    ;

    fun sort(trips: List<TripWithSpending>): List<TripWithSpending> = when (this) {
        // 같은 날 시작하는 여행이 둘이면 끝나는 날로, 그래도 같으면 이름으로 가른다.
        START_DESC -> trips.sortedWith(
            compareByDescending<TripWithSpending> { it.trip.startDate }
                .thenByDescending { it.trip.endDate }
                .thenBy { it.trip.title },
        )
        START_ASC -> trips.sortedWith(
            compareBy<TripWithSpending> { it.trip.startDate }
                .thenBy { it.trip.endDate }
                .thenBy { it.trip.title },
        )
        CREATED_DESC -> trips.sortedByDescending { it.trip.createdAt }
    }

    companion object {
        val DEFAULT = START_DESC

        fun fromName(name: String?): TripSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
