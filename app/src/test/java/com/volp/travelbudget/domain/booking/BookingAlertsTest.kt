package com.volp.travelbudget.domain.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BookingAlertsTest {

    private fun flight(startAt: LocalDateTime) = Booking(
        id = 1L,
        tripId = 7L,
        type = BookingType.FLIGHT,
        title = "KE723",
        startAt = startAt,
        fromCode = "ICN",
        toCode = "KIX",
        terminal = "제2여객터미널",
        seat = "32A",
    )

    private fun lodging(start: LocalDateTime, end: LocalDateTime) = Booking(
        id = 2L,
        tripId = 7L,
        type = BookingType.LODGING,
        title = "도미인 난바",
        startAt = start,
        endAt = end,
        address = "오사카시 주오구",
    )

    @Test
    fun `출발 24시간 전에 온라인 체크인을 알린다`() {
        val departure = LocalDateTime.of(2026, 10, 3, 9, 5)
        val booking = flight(departure)

        val opened = BookingAlerts.evaluate(listOf(booking), departure.minusHours(24))
        val later = BookingAlerts.evaluate(listOf(booking), departure.minusHours(22))
        val tooEarly = BookingAlerts.evaluate(listOf(booking), departure.minusHours(30))
        val tooLate = BookingAlerts.evaluate(listOf(booking), departure.minusHours(20))

        assertTrue(opened.any { it.key == "checkin:1" })
        assertTrue(later.any { it.key == "checkin:1" })
        assertTrue(tooEarly.none { it.key == "checkin:1" })
        assertTrue(tooLate.none { it.key == "checkin:1" })
    }

    @Test
    fun `세 시간 전에 나서라고 알린다`() {
        val departure = LocalDateTime.of(2026, 10, 3, 9, 5)
        val booking = flight(departure)

        val alerts = BookingAlerts.evaluate(listOf(booking), departure.minusHours(3))

        val alert = alerts.first { it.key == "departure:1" }
        assertTrue(alert.message.contains("ICN → KIX"))
        assertTrue(alert.message.contains("제2여객터미널"))
        assertTrue(alert.message.contains("32A"))
    }

    @Test
    fun `숙소는 체크인 날 아침에 알린다`() {
        val booking = lodging(
            LocalDateTime.of(2026, 10, 3, 15, 0),
            LocalDateTime.of(2026, 10, 6, 11, 0),
        )

        val morning = BookingAlerts.evaluate(listOf(booking), LocalDateTime.of(2026, 10, 3, 9, 0))
        val noon = BookingAlerts.evaluate(listOf(booking), LocalDateTime.of(2026, 10, 3, 13, 0))

        assertTrue(morning.any { it.key == "lodging-in:2" })
        assertTrue(noon.none { it.key == "lodging-in:2" })
    }

    @Test
    fun `체크아웃 날 아침에도 알린다`() {
        val booking = lodging(
            LocalDateTime.of(2026, 10, 3, 15, 0),
            LocalDateTime.of(2026, 10, 6, 11, 0),
        )

        val alerts = BookingAlerts.evaluate(listOf(booking), LocalDateTime.of(2026, 10, 6, 9, 30))

        val alert = alerts.first { it.key == "lodging-out:2" }
        assertTrue(alert.title.contains("체크아웃"))
    }

    @Test
    fun `입장권 같은 예약은 알리지 않는다`() {
        val booking = Booking(
            id = 3L,
            tripId = 7L,
            type = BookingType.TICKET,
            title = "유니버설 스튜디오",
            startAt = LocalDateTime.of(2026, 10, 4, 10, 0),
        )

        val alerts = BookingAlerts.evaluate(
            listOf(booking),
            LocalDateTime.of(2026, 10, 4, 7, 0),
        )

        assertEquals(0, alerts.size)
    }

    @Test
    fun `기차는 체크인 없이 나설 시각만 알린다`() {
        val booking = Booking(
            id = 4L,
            tripId = 7L,
            type = BookingType.TRAIN,
            title = "KTX 101",
            startAt = LocalDateTime.of(2026, 10, 3, 9, 0),
        )

        val checkInTime = BookingAlerts.evaluate(listOf(booking), LocalDateTime.of(2026, 10, 2, 9, 0))
        val leaveTime = BookingAlerts.evaluate(listOf(booking), LocalDateTime.of(2026, 10, 3, 6, 0))

        assertEquals(0, checkInTime.size)
        assertEquals(1, leaveTime.size)
    }
}
