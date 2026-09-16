package com.volp.travelbudget.domain.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripRescheduleTest {

    private val start = LocalDate.of(2026, 10, 10)
    private val end = LocalDate.of(2026, 10, 16)

    private fun stop(day: Int, fixed: Boolean = false) = RescheduleItem(
        key = "d$day",
        label = "$day 일의 일정",
        date = LocalDate.of(2026, 10, day),
        fixed = fixed,
    )

    @Test
    fun `기간이 줄면 사라진 날의 일정을 마지막 날로 당긴다`() {
        val plan = TripReschedule.plan(
            items = listOf(stop(11), stop(15), stop(16)),
            oldStart = start,
            oldEnd = end,
            newStart = start,
            newEnd = LocalDate.of(2026, 10, 14),
        )

        assertEquals(LocalDate.of(2026, 10, 11), plan.changes[0].newDate)
        assertEquals(LocalDate.of(2026, 10, 14), plan.changes[1].newDate)
        assertEquals(LocalDate.of(2026, 10, 14), plan.changes[2].newDate)
        assertTrue(plan.outside.isEmpty())
        assertTrue(plan.shorter)
        assertEquals(6, plan.nightsBefore)
        assertEquals(4, plan.nightsAfter)
    }

    @Test
    fun `당기지 않기로 하면 기간 밖에 남고 그렇다고 알린다`() {
        val plan = TripReschedule.plan(
            items = listOf(stop(16)),
            oldStart = start,
            oldEnd = end,
            newStart = start,
            newEnd = LocalDate.of(2026, 10, 14),
            pullInside = false,
        )

        assertEquals(LocalDate.of(2026, 10, 16), plan.changes.first().newDate)
        assertEquals(1, plan.outside.size)
    }

    @Test
    fun `여행이 통째로 밀리면 모두 같은 폭으로 민다`() {
        val plan = TripReschedule.plan(
            items = listOf(stop(10), stop(12)),
            oldStart = start,
            oldEnd = end,
            newStart = start.plusDays(2),
            newEnd = end.plusDays(2),
        )

        assertEquals(2L, plan.shiftDays)
        assertEquals(LocalDate.of(2026, 10, 12), plan.changes[0].newDate)
        assertEquals(LocalDate.of(2026, 10, 14), plan.changes[1].newDate)
        assertFalse(plan.lengthChanged)
    }

    @Test
    fun `날짜가 고정된 것은 밀지 않는다`() {
        val plan = TripReschedule.plan(
            items = listOf(stop(12, fixed = true), stop(12)),
            oldStart = start,
            oldEnd = end,
            newStart = start.plusDays(2),
            newEnd = end.plusDays(2),
        )

        // 투어는 그 날짜로 잡혀 있다. 나머지만 민다.
        assertEquals(LocalDate.of(2026, 10, 12), plan.changes[0].newDate)
        assertEquals(LocalDate.of(2026, 10, 14), plan.changes[1].newDate)
        assertFalse(plan.changes[0].moved)
    }

    @Test
    fun `고정된 것이 새 기간 밖에 남으면 당기지 않고 알린다`() {
        val plan = TripReschedule.plan(
            items = listOf(stop(11, fixed = true)),
            oldStart = start,
            oldEnd = end,
            newStart = start.plusDays(3),
            newEnd = end.plusDays(3),
        )

        assertEquals(LocalDate.of(2026, 10, 11), plan.changes.first().newDate)
        assertEquals(1, plan.outside.size)
        assertTrue(plan.outside.first().item.fixed)
    }

    @Test
    fun `예약을 다시 잡았다면 고정된 것도 함께 민다`() {
        val plan = TripReschedule.plan(
            items = listOf(stop(12, fixed = true)),
            oldStart = start,
            oldEnd = end,
            newStart = start.plusDays(2),
            newEnd = end.plusDays(2),
            moveFixed = true,
        )

        assertEquals(LocalDate.of(2026, 10, 14), plan.changes.first().newDate)
        assertEquals(1, plan.needsRebooking.size)
    }

    @Test
    fun `밀지 않아도 되는 일정은 옮겼다고 세지 않는다`() {
        val plan = TripReschedule.plan(
            items = listOf(stop(11), stop(12)),
            oldStart = start,
            oldEnd = end,
            newStart = start,
            newEnd = end,
        )

        assertTrue(plan.moved.isEmpty())
        assertEquals(0L, plan.shiftDays)
    }
}
