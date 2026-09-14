package com.volp.travelbudget.ui.trips

import com.volp.travelbudget.data.repository.TripWithSpending

/** 여행 목록을 늘어놓는 차례. 여행이 여러 개가 되면 날짜로 찾는 편이 빠르다. */
enum class TripSort(val label: String) {
    /** 다가오는 여행과 방금 다녀온 여행이 위로. */
    START_DESC("최신 날짜순"),

    /** 지난 여행부터 차례로. 기록을 훑어볼 때 쓴다. */
    START_ASC("오래된 날짜순"),

    /** 가나다순. 이름을 아는 여행을 찾을 때 쓴다. */
    NAME("이름순"),

    /** 만든 차례. 날짜를 아직 안 정한 여행을 찾을 때 쓴다. */
    CREATED_DESC("등록순"),

    /** 손으로 끌어 정한 차례. 마음이 가는 대로 놓는다. */
    MANUAL("내 순서"),
    ;

    /** 이 차례에서 손으로 끌어 옮길 수 있는지. 날짜순에서 끌어 봐야 다시 제자리로 간다. */
    val draggable: Boolean get() = this == MANUAL

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
        NAME -> trips.sortedWith(
            compareBy<TripWithSpending, String>(String.CASE_INSENSITIVE_ORDER) { it.trip.title }
                .thenByDescending { it.trip.startDate },
        )
        CREATED_DESC -> trips.sortedByDescending { it.trip.createdAt }
        // 아직 손대지 않은 여행(0)은 뒤로 밀고, 그 안에서는 날짜가 가까운 것부터 보여 준다.
        MANUAL -> trips.sortedWith(
            compareBy<TripWithSpending> { if (it.trip.sortOrder > 0) it.trip.sortOrder else Int.MAX_VALUE }
                .thenByDescending { it.trip.startDate },
        )
    }

    companion object {
        val DEFAULT = START_DESC

        fun fromName(name: String?): TripSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
