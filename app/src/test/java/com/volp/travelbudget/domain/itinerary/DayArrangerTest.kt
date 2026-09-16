package com.volp.travelbudget.domain.itinerary

import com.volp.travelbudget.domain.travel.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayArrangerTest {

    private val date = LocalDate.of(2026, 10, 1)

    private fun stop(
        id: Long,
        name: String,
        order: Int,
        time: String? = null,
        point: GeoPoint? = null,
    ) = ItineraryStop(
        id = id,
        tripId = 1L,
        date = date,
        sortOrder = order,
        name = name,
        startTime = time,
        point = point,
    )

    // 서쪽에서 동쪽으로 늘어선 네 곳.
    private val west = GeoPoint(33.45, 126.30)
    private val mid = GeoPoint(33.45, 126.40)
    private val east = GeoPoint(33.45, 126.55)
    private val far = GeoPoint(33.45, 126.90)

    @Test
    fun `시각이 적힌 것은 자리를 지킨다`() {
        val stops = listOf(
            stop(1, "아침 식당", 0, time = "09:00", point = west),
            stop(2, "박물관", 1, point = far),
            stop(3, "점심 예약", 2, time = "12:00", point = mid),
        )

        val arranged = DayArranger.arrange(stops)

        assertEquals(listOf("아침 식당", "박물관", "점심 예약"), arranged.map { it.stop.name })
        assertEquals("시각이 정해져 있다", arranged[0].reason)
        assertEquals("시각이 정해져 있다", arranged[2].reason)
    }

    @Test
    fun `시각이 없는 것은 가까운 곳부터 간다`() {
        val stops = listOf(
            stop(1, "숙소", 0, time = "09:00", point = west),
            stop(2, "먼 곳", 1, point = far),
            stop(3, "가까운 곳", 2, point = mid),
            stop(4, "중간 곳", 3, point = east),
        )

        val arranged = DayArranger.arrange(stops)

        assertEquals(
            listOf("숙소", "가까운 곳", "중간 곳", "먼 곳"),
            arranged.map { it.stop.name },
        )
        assertTrue(DayArranger.changed(arranged))
    }

    @Test
    fun `뼈대를 넘어가지는 않는다`() {
        // 먼 곳이 더 가깝다고 열두 시 예약 앞으로 끌려오면 예약을 놓친다.
        val stops = listOf(
            stop(1, "숙소", 0, time = "09:00", point = west),
            stop(2, "가까운 곳", 1, point = mid),
            stop(3, "점심 예약", 2, time = "12:00", point = far),
            stop(4, "예약 뒤", 3, point = east),
        )

        val arranged = DayArranger.arrange(stops)

        assertEquals(
            listOf("숙소", "가까운 곳", "점심 예약", "예약 뒤"),
            arranged.map { it.stop.name },
        )
    }

    @Test
    fun `좌표를 모르는 곳은 아는 곳 뒤에 원래 차례대로 붙는다`() {
        val stops = listOf(
            stop(1, "숙소", 0, time = "09:00", point = west),
            stop(2, "어딘지 모름", 1),
            stop(3, "먼 곳", 2, point = far),
            stop(4, "가까운 곳", 3, point = mid),
        )

        val arranged = DayArranger.arrange(stops)

        assertEquals(
            listOf("숙소", "가까운 곳", "먼 곳", "어딘지 모름"),
            arranged.map { it.stop.name },
        )
    }

    @Test
    fun `아는 곳이 하나뿐이면 차례를 건드리지 않는다`() {
        // 견줄 상대가 없다. 이럴 때 자리를 바꾸면 왜 바뀌었는지 설명할 수 없다.
        val stops = listOf(
            stop(1, "숙소", 0, time = "09:00", point = west),
            stop(2, "어딘지 모름", 1),
            stop(3, "가까운 곳", 2, point = mid),
        )

        assertFalse(DayArranger.changed(DayArranger.arrange(stops)))
    }

    @Test
    fun `좌표가 하나도 없으면 손대지 않는다`() {
        val stops = listOf(
            stop(1, "가", 0),
            stop(2, "나", 1),
            stop(3, "다", 2),
        )

        val arranged = DayArranger.arrange(stops)

        assertEquals(listOf("가", "나", "다"), arranged.map { it.stop.name })
        assertFalse(DayArranger.changed(arranged))
    }

    @Test
    fun `한 곳뿐이면 정리할 것이 없다`() {
        val arranged = DayArranger.arrange(listOf(stop(1, "가", 0)))

        assertFalse(DayArranger.changed(arranged))
    }

    @Test
    fun `이미 가까운 순이면 바뀌지 않는다`() {
        val stops = listOf(
            stop(1, "숙소", 0, time = "09:00", point = west),
            stop(2, "가까운 곳", 1, point = mid),
            stop(3, "먼 곳", 2, point = far),
        )

        assertFalse(DayArranger.changed(DayArranger.arrange(stops)))
    }
}
