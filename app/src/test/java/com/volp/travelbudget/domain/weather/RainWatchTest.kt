package com.volp.travelbudget.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RainWatchTest {

    private val now = LocalDateTime.of(2026, 4, 19, 14, 5)

    private fun slot(minutes: Long, probability: Int?, mm: Double = 0.0) =
        PrecipitationSlot(now.plusMinutes(minutes), probability, mm)

    @Test
    fun `한 시간 안에 비가 오면 알린다`() {
        val alert = RainWatch.evaluate(
            listOf(slot(10, 10), slot(25, 30), slot(40, 80, 1.4), slot(55, 90, 2.0)),
            now,
        )!!

        assertEquals(40, alert.minutesAway)
        assertEquals(80, alert.probability)
        assertEquals(1.4, alert.millimeters, 0.001)
    }

    @Test
    fun `한 시간 밖의 비는 알리지 않는다`() {
        val alert = RainWatch.evaluate(listOf(slot(90, 90, 3.0)), now)
        assertNull(alert)
    }

    @Test
    fun `확률이 낮으면 알리지 않는다`() {
        val alert = RainWatch.evaluate(listOf(slot(30, 40), slot(45, 50)), now)
        assertNull(alert)
    }

    @Test
    fun `확률이 없어도 실제로 내린다는 예보면 알린다`() {
        val alert = RainWatch.evaluate(listOf(slot(30, null, 1.0)), now)
        assertNotNull(alert)
    }

    @Test
    fun `이미 지난 칸은 보지 않는다`() {
        val alert = RainWatch.evaluate(listOf(slot(-15, 100, 5.0), slot(50, 20)), now)
        assertNull(alert)
    }

    @Test
    fun `가장 먼저 오는 비를 알린다`() {
        val alert = RainWatch.evaluate(
            listOf(slot(50, 90, 2.0), slot(20, 70, 0.8)),
            now,
        )!!

        assertEquals(20, alert.minutesAway)
    }

    @Test
    fun `기준을 낮추면 더 민감해진다`() {
        val slots = listOf(slot(30, 45))

        assertNull(RainWatch.evaluate(slots, now))
        assertNotNull(RainWatch.evaluate(slots, now, probabilityThreshold = 40))
    }
}
