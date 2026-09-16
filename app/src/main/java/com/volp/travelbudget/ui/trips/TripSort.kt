package com.volp.travelbudget.ui.trips

import com.volp.travelbudget.data.repository.TripWithSpending
import com.volp.travelbudget.domain.trip.TripOrder
import java.time.LocalDate

/** 여행 목록을 늘어놓는 차례. 여행이 여러 개가 되면 어떻게 줄 세우느냐가 곧 쓸모다. */
enum class TripSort(val label: String) {
    /** 여행 중인 것이 맨 위, 그다음이 곧 떠날 순서. 지난 여행은 최근 것부터 아래에. */
    UPCOMING("다가오는 순"),

    /** 손으로 끌어 정한 차례. 마음이 가는 대로 놓는다. */
    MANUAL("내 순서"),

    /** 시작일이 늦은 것부터. 먼 미래 계획을 훑어볼 때 쓴다. */
    START_DESC("먼 날짜순"),

    /** 지난 여행부터 차례로. 기록을 처음부터 되짚을 때 쓴다. */
    START_ASC("오래된 날짜순"),

    /** 가나다순. 이름을 아는 여행을 찾을 때 쓴다. */
    NAME("이름순"),

    /** 만든 차례. 날짜를 아직 안 정한 여행을 찾을 때 쓴다. */
    CREATED_DESC("등록순"),
    ;

    /** 이 차례에서 손으로 끌어 옮길 수 있는지. 날짜순에서 끌어 봐야 다시 제자리로 간다. */
    val draggable: Boolean get() = this == MANUAL

    fun sort(trips: List<TripWithSpending>, today: LocalDate = LocalDate.now()): List<TripWithSpending> {
        val byTrip = when (this) {
            UPCOMING -> TripOrder.upcomingFirst(today)
            MANUAL -> TripOrder.manual(today)
            // 같은 날 시작하는 여행이 둘이면 끝나는 날로, 그래도 같으면 이름으로 가른다.
            START_DESC -> compareByDescending<com.volp.travelbudget.domain.model.Trip> { it.startDate }
                .thenByDescending { it.endDate }
                .thenBy { it.title }
            START_ASC -> compareBy<com.volp.travelbudget.domain.model.Trip> { it.startDate }
                .thenBy { it.endDate }
                .thenBy { it.title }
            NAME -> compareBy<com.volp.travelbudget.domain.model.Trip, String>(
                String.CASE_INSENSITIVE_ORDER,
            ) { it.title }.thenByDescending { it.startDate }
            CREATED_DESC -> compareByDescending { it.createdAt }
        }

        return trips.sortedWith { left, right -> byTrip.compare(left.trip, right.trip) }
    }

    companion object {
        /** 처음 쓰는 사람에게 가장 쓸모 있는 차례. */
        val DEFAULT = UPCOMING

        fun fromName(name: String?): TripSort =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
