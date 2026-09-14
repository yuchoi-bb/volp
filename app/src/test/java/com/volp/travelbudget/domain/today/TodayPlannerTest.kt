package com.volp.travelbudget.domain.today

import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.itinerary.TimelineBuilder
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.travel.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TodayPlannerTest {

    private val today = LocalDate.of(2026, 4, 19)
    private val namba = GeoPoint(34.6620, 135.5020)
    private val castle = GeoPoint(34.6873, 135.5259)

    private fun stop(order: Int, name: String, time: String?, point: GeoPoint? = null) =
        ItineraryStop(
            id = order.toLong() + 1,
            tripId = 1L,
            date = today,
            sortOrder = order,
            name = name,
            point = point,
            startTime = time,
        )

    private fun timeline(vararg stops: ItineraryStop) =
        TimelineBuilder.build(stops.toList(), emptyList(), listOf(today), Region.JAPAN).first()

    @Test
    fun `지나간 일정은 건너뛰고 다음 것을 고른다`() {
        val plan = timeline(
            stop(0, "아침 식사", "08:00"),
            stop(1, "오사카성", "15:30"),
            stop(2, "구로몬시장", "18:00"),
        )
        val next = TodayPlanner.nextEntry(plan, LocalDateTime.of(today, java.time.LocalTime.of(14, 5)))

        assertEquals("오사카성", next?.title)
    }

    @Test
    fun `오늘 일정이 다 지났으면 다음 날 첫 줄을 고른다`() {
        val tomorrow = today.plusDays(1)
        val timelines = TimelineBuilder.build(
            listOf(
                stop(0, "아침 식사", "08:00"),
                ItineraryStop(id = 9, tripId = 1, date = tomorrow, sortOrder = 0, name = "공항", startTime = "10:00"),
            ),
            emptyList(),
            listOf(today, tomorrow),
            Region.JAPAN,
        )
        val next = TodayPlanner.upcomingEntry(timelines, LocalDateTime.of(today, java.time.LocalTime.of(20, 0)))

        assertEquals("공항", next?.title)
    }

    @Test
    fun `언제 나서야 하는지 계산한다`() {
        val plan = timeline(stop(0, "오사카성", "15:30", castle))
        val next = TodayPlanner.nextEntry(plan, LocalDateTime.of(today, java.time.LocalTime.of(14, 0)))!!

        val advice = TodayPlanner.advice(
            entry = next,
            from = namba,
            now = LocalDateTime.of(today, java.time.LocalTime.of(14, 0)),
            region = Region.JAPAN,
        )!!

        // 약속 시각보다 앞서야 하고, 이동 시간만큼은 떨어져 있어야 한다.
        assertTrue(advice.leaveBy.toLocalTime().isBefore(java.time.LocalTime.of(15, 30)))
        assertTrue(advice.travelMinutes > 0)
        assertFalse(advice.late)
    }

    @Test
    fun `출발 시각을 지났으면 늦은 것으로 본다`() {
        val plan = timeline(stop(0, "오사카성", "15:30", castle))
        val next = TodayPlanner.nextEntry(plan, LocalDateTime.of(today, java.time.LocalTime.of(15, 25)))!!

        val advice = TodayPlanner.advice(
            entry = next,
            from = namba,
            now = LocalDateTime.of(today, java.time.LocalTime.of(15, 25)),
            region = Region.JAPAN,
        )!!

        assertTrue(advice.late)
    }

    @Test
    fun `좌표나 시각을 모르면 억지로 안내하지 않는다`() {
        val plan = timeline(stop(0, "어딘가", "15:30"))
        val next = TodayPlanner.nextEntry(plan, LocalDateTime.of(today, java.time.LocalTime.of(14, 0)))!!

        assertNull(TodayPlanner.advice(next, namba, LocalDateTime.of(today, java.time.LocalTime.of(14, 0)), Region.JAPAN))
        assertNull(TodayPlanner.advice(next, null, LocalDateTime.of(today, java.time.LocalTime.of(14, 0)), Region.JAPAN))
    }

    @Test
    fun `시각을 정하지 않은 장소는 아직 남은 것으로 본다`() {
        val plan = timeline(stop(0, "정하지 않음", null))
        val next = TodayPlanner.nextEntry(plan, LocalDateTime.of(today, java.time.LocalTime.of(23, 0)))

        assertEquals("정하지 않음", next?.title)
    }
}
