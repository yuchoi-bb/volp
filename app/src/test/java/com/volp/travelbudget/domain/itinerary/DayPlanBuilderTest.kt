package com.volp.travelbudget.domain.itinerary

import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayPlanBuilderTest {

    private val day1 = LocalDate.of(2026, 4, 18)
    private val day2 = LocalDate.of(2026, 4, 19)
    private val dates = listOf(day1, day2)

    private fun stop(
        id: Long,
        date: LocalDate,
        order: Int,
        name: String,
        point: GeoPoint? = null,
    ) = ItineraryStop(
        id = id,
        tripId = 1L,
        date = date,
        sortOrder = order,
        name = name,
        point = point,
    )

    @Test
    fun `일정이 없는 날도 자리를 남긴다`() {
        val plans = DayPlanBuilder.build(emptyList(), dates, Region.JAPAN)

        assertEquals(2, plans.size)
        assertTrue(plans.all { it.isEmpty })
    }

    @Test
    fun `같은 날 장소를 순서대로 잇는다`() {
        val stops = listOf(
            stop(2, day1, 1, "도톤보리", GeoPoint(34.6687, 135.5013)),
            stop(1, day1, 0, "난바파크스", GeoPoint(34.6620, 135.5020)),
        )
        val plan = DayPlanBuilder.build(stops, dates, Region.JAPAN).first()

        assertEquals(listOf("난바파크스", "도톤보리"), plan.stops.map { it.name })
        assertEquals(1, plan.legs.size)
        assertEquals(TransportMode.WALK, plan.legs.first().suggestion?.mode)
    }

    @Test
    fun `좌표를 모르는 장소가 끼면 그 구간만 안내가 빠진다`() {
        val stops = listOf(
            stop(1, day1, 0, "숙소", GeoPoint(34.6620, 135.5020)),
            stop(2, day1, 1, "어딘가"),
            stop(3, day1, 2, "오사카성", GeoPoint(34.6873, 135.5259)),
        )
        val plan = DayPlanBuilder.build(stops, dates, Region.JAPAN).first()

        assertEquals(2, plan.legs.size)
        assertNull(plan.legs[0].suggestion)
        assertNull(plan.legs[1].suggestion)
    }

    @Test
    fun `날짜별로 나눠 담는다`() {
        val stops = listOf(
            stop(1, day1, 0, "첫날 장소"),
            stop(2, day2, 0, "둘째날 장소"),
        )
        val plans = DayPlanBuilder.build(stops, dates, Region.JAPAN)

        assertEquals(listOf("첫날 장소"), plans[0].stops.map { it.name })
        assertEquals(listOf("둘째날 장소"), plans[1].stops.map { it.name })
    }

    @Test
    fun `여행 기간 밖의 일정도 빠뜨리지 않는다`() {
        val extra = LocalDate.of(2026, 4, 25)
        val plans = DayPlanBuilder.build(listOf(stop(1, extra, 0, "연장")), dates, Region.JAPAN)

        assertEquals(3, plans.size)
        assertEquals(extra, plans.last().date)
    }

    @Test
    fun `그날 이동 시간과 교통비를 더한다`() {
        val stops = listOf(
            stop(1, day1, 0, "난바", GeoPoint(34.6620, 135.5020)),
            stop(2, day1, 1, "오사카성", GeoPoint(34.6873, 135.5259)),
        )
        val plan = DayPlanBuilder.build(stops, dates, Region.JAPAN).first()

        assertTrue(plan.totalTravelMinutes > 0)
        assertTrue(plan.totalTravelCostKrw > 0L)
    }
}
