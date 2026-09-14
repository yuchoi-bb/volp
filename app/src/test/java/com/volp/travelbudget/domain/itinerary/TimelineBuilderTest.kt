package com.volp.travelbudget.domain.itinerary

import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.booking.BookingType
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TimelineBuilderTest {

    private val day1 = LocalDate.of(2026, 4, 18)
    private val day2 = LocalDate.of(2026, 4, 19)
    private val day3 = LocalDate.of(2026, 4, 20)
    private val dates = listOf(day1, day2, day3)

    private fun stop(
        id: Long,
        date: LocalDate,
        order: Int,
        name: String,
        time: String? = null,
        point: GeoPoint? = null,
    ) = ItineraryStop(
        id = id,
        tripId = 1L,
        date = date,
        sortOrder = order,
        name = name,
        point = point,
        startTime = time,
    )

    private fun flight() = Booking(
        id = 1,
        tripId = 1,
        type = BookingType.FLIGHT,
        title = "KE723",
        startAt = LocalDateTime.of(day1, java.time.LocalTime.of(9, 5)),
        endAt = LocalDateTime.of(day1, java.time.LocalTime.of(11, 0)),
        fromCode = "ICN",
        toCode = "KIX",
    )

    private fun lodging() = Booking(
        id = 2,
        tripId = 1,
        type = BookingType.LODGING,
        title = "도미인 난바",
        startAt = LocalDateTime.of(day1, java.time.LocalTime.of(15, 0)),
        endAt = LocalDateTime.of(day3, java.time.LocalTime.of(11, 0)),
    )

    private fun titles(timeline: DayTimeline) = timeline.entries.map { it.title }

    @Test
    fun `일정이 없는 날도 자리를 남긴다`() {
        val timelines = TimelineBuilder.build(emptyList(), emptyList(), dates, Region.JAPAN)

        assertEquals(3, timelines.size)
        assertTrue(timelines.all { it.isEmpty })
    }

    @Test
    fun `예약과 장소가 시간순으로 한 줄에 섞인다`() {
        val stops = listOf(
            stop(1, day1, 0, "난바파크스", time = "13:00"),
            stop(2, day1, 1, "구로몬시장", time = "18:00"),
        )
        val timeline = TimelineBuilder.build(stops, listOf(flight(), lodging()), dates, Region.JAPAN).first()

        assertEquals(
            listOf("KE723", "난바파크스", "도미인 난바", "구로몬시장"),
            titles(timeline),
        )
    }

    @Test
    fun `숙소는 체크아웃 날에도 한 번 더 놓인다`() {
        val timelines = TimelineBuilder.build(emptyList(), listOf(lodging()), dates, Region.JAPAN)

        assertEquals(listOf("도미인 난바"), titles(timelines[0]))
        assertTrue(timelines[1].isEmpty)
        assertEquals(listOf("도미인 난바"), titles(timelines[2]))

        val checkout = timelines[2].entries.first() as PlanEntry.Reservation
        assertEquals(BookingPhase.END, checkout.phase)
        assertEquals("체크아웃", checkout.phaseLabel())
    }

    @Test
    fun `시각을 정하지 않은 장소는 정해 둔 순서대로 뒤에 붙는다`() {
        val stops = listOf(
            stop(1, day1, 0, "어딘가"),
            stop(2, day1, 1, "또 어딘가"),
            stop(3, day1, 2, "점심", time = "12:00"),
        )
        val timeline = TimelineBuilder.build(stops, emptyList(), dates, Region.JAPAN).first()

        assertEquals(listOf("점심", "어딘가", "또 어딘가"), titles(timeline))
    }

    @Test
    fun `이어지는 줄마다 이동수단을 붙인다`() {
        val stops = listOf(
            stop(1, day1, 0, "난바파크스", time = "13:00", point = GeoPoint(34.6620, 135.5020)),
            stop(2, day1, 1, "도톤보리", time = "14:00", point = GeoPoint(34.6687, 135.5013)),
        )
        val timeline = TimelineBuilder.build(stops, emptyList(), dates, Region.JAPAN).first()

        assertEquals(1, timeline.legs.size)
        assertEquals(TransportMode.WALK, timeline.legs.first().suggestion?.mode)
        assertTrue(timeline.totalTravelMinutes > 0)
    }

    @Test
    fun `좌표를 모르는 줄이 끼면 그 구간만 안내가 빠진다`() {
        val stops = listOf(
            stop(1, day1, 0, "숙소", time = "09:00", point = GeoPoint(34.6620, 135.5020)),
            stop(2, day1, 1, "어딘가", time = "10:00"),
        )
        val timeline = TimelineBuilder.build(stops, emptyList(), dates, Region.JAPAN).first()

        assertNull(timeline.legs.first().suggestion)
    }

    @Test
    fun `여행 기간 밖의 예약도 빠뜨리지 않는다`() {
        val early = flight().copy(startAt = LocalDateTime.of(2026, 4, 10, 9, 0), endAt = null)
        val timelines = TimelineBuilder.build(emptyList(), listOf(early), dates, Region.JAPAN)

        assertEquals(4, timelines.size)
        assertEquals(LocalDate.of(2026, 4, 10), timelines.first().date)
    }
}
