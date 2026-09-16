package com.volp.travelbudget.domain.trip

import com.volp.travelbudget.domain.model.Trip
import java.time.LocalDate

/**
 * 같은 여행이 두 벌로 들어오는 것을 막는다.
 *
 * 기록은 uid로 짝짓는다. 그런데 uid는 기록을 만든 기기에서 매기므로, 폰과 태블릿에서 같은 여행을
 * 따로 만들었으면 uid가 둘이다. 그 상태로 파일을 주고받으면 앱이 보기에는 서로 다른 여행 둘이라
 * 목록에 '제주 여행'이 두 줄로 남는다.
 *
 * 사람이 보기에 같은 여행이면 같은 것으로 본다. 제목과 기간이 같으면 같은 여행이다. 같은 이름의
 * 같은 날짜 여행을 일부러 둘 만드는 일은 없다고 보아도 된다.
 */
object TripTwins {

    fun key(title: String, startDate: LocalDate, endDate: LocalDate): String =
        listOf(title.trim().lowercase(), startDate.toString(), endDate.toString()).joinToString("|")

    fun key(trip: Trip): String = key(trip.title, trip.startDate, trip.endDate)

    /**
     * 들어온 여행과 같은 여행이 이 기기에 이미 있는지 찾는다.
     *
     * @param remoteUids 이번에 들어온 여행들의 uid. 저쪽에도 있는 여행은 이미 짝이 맞았으므로
     *   쌍둥이로 보지 않는다. 이것을 빼면 저쪽의 다른 여행에 잘못 붙는다.
     */
    fun findTwin(incoming: Trip, local: List<Trip>, remoteUids: Set<String>): Trip? {
        val wanted = key(incoming)
        return local.firstOrNull { it.uid !in remoteUids && key(it) == wanted }
    }

    /**
     * 이미 두 벌로 쌓인 여행을 묶는다.
     *
     * 한 묶음 안에서 맨 앞이 남길 여행이다. 나머지는 그 안으로 합친다.
     */
    fun duplicates(trips: List<Trip>): List<List<Trip>> =
        trips.groupBy { key(it) }
            .values
            .filter { it.size > 1 }
            // 먼저 만든 것을 남긴다. 그쪽에 붙은 기록이 더 많을 가능성이 높다.
            .map { group -> group.sortedBy { it.id } }
            .sortedBy { it.first().id }
}
