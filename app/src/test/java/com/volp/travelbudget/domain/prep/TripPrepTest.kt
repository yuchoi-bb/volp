package com.volp.travelbudget.domain.prep

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripPrepTest {

    private val today = LocalDate.of(2026, 9, 14)

    private fun trip(start: LocalDate, end: LocalDate = start.plusDays(4)) = Trip(
        id = 7L,
        title = "오사카",
        destinationKey = "osaka",
        destinationName = "오사카",
        region = Region.JAPAN,
        startDate = start,
        endDate = end,
        travelers = 2,
        style = TravelStyle.STANDARD,
        includeFlight = true,
        currencyCode = "JPY",
        exchangeRate = 9.4,
        predictedBudget = mapOf(ExpenseCategory.FOOD to 500_000L),
        plannedBudget = mapOf(ExpenseCategory.FOOD to 500_000L),
    )

    @Test
    fun `일주일 전과 하루 전에만 말을 건다`() {
        assertEquals(PrepStage.WEEK, TripPrepAdvisor.stageOn(trip(today.plusDays(7)), today))
        assertEquals(PrepStage.DAY, TripPrepAdvisor.stageOn(trip(today.plusDays(1)), today))
        assertNull(TripPrepAdvisor.stageOn(trip(today.plusDays(3)), today))
        assertNull(TripPrepAdvisor.stageOn(trip(today), today))
        assertNull(TripPrepAdvisor.stageOn(trip(today.minusDays(2)), today))
    }

    @Test
    fun `여권 잔여 유효기간이 여섯 달 안이면 경고한다`() {
        val trip = trip(today.plusDays(7))

        val note = TripPrepAdvisor.passportNote(trip.endDate.plusMonths(3), trip)

        assertTrue(note!!.critical)
        assertTrue(note.text.contains("6개월"))
    }

    @Test
    fun `여권이 여행 중에 만료되면 더 세게 말한다`() {
        val trip = trip(today.plusDays(7))

        val note = TripPrepAdvisor.passportNote(trip.startDate.plusDays(1), trip)

        assertTrue(note!!.critical)
        assertTrue(note.text.contains("재발급"))
    }

    @Test
    fun `여권이 넉넉하면 아무 말도 하지 않는다`() {
        val trip = trip(today.plusDays(7))

        assertNull(TripPrepAdvisor.passportNote(trip.endDate.plusMonths(12), trip))
        assertNull(TripPrepAdvisor.passportNote(null, trip))
    }

    @Test
    fun `일주일 전에는 아직 살 수 있다고 말한다`() {
        val reminder = TripPrepAdvisor.build(
            trip = trip(today.plusDays(7)),
            today = today,
            uncheckedItems = listOf("멀티 어댑터", "상비약"),
        )

        assertEquals(PrepStage.WEEK, reminder!!.stage)
        assertTrue(reminder.notes.any { it.text.contains("살 시간") })
    }

    @Test
    fun `하루 전에는 가방에 넣으라고 말한다`() {
        val reminder = TripPrepAdvisor.build(
            trip = trip(today.plusDays(1)),
            today = today,
            uncheckedItems = listOf("멀티 어댑터"),
        )

        assertTrue(reminder!!.notes.any { it.text.contains("가방") })
    }

    @Test
    fun `준비물이 많으면 앞의 몇 개만 말한다`() {
        val reminder = TripPrepAdvisor.build(
            trip = trip(today.plusDays(7)),
            today = today,
            uncheckedItems = listOf("어댑터", "상비약", "우산", "목베개", "선크림"),
        )

        assertTrue(reminder!!.notes.any { it.text.contains("외 2개") })
    }

    @Test
    fun `못 받을 것 같은 주문은 일주일 전에 중요하게 다룬다`() {
        val week = TripPrepAdvisor.build(trip(today.plusDays(7)), today, lateArrivals = 2)
        val day = TripPrepAdvisor.build(trip(today.plusDays(1)), today, lateArrivals = 2)

        assertTrue(week!!.hasCritical)
        assertTrue(!day!!.hasCritical)
    }

    @Test
    fun `짚을 것이 없으면 준비가 끝났다고 말한다`() {
        val reminder = TripPrepAdvisor.build(trip(today.plusDays(1)), today)

        assertEquals(1, reminder!!.notes.size)
        assertTrue(reminder.headline.contains("내일 출발"))
    }

    @Test
    fun `중요한 것이 있으면 그것을 앞세운다`() {
        val trip = trip(today.plusDays(7))

        val reminder = TripPrepAdvisor.build(
            trip = trip,
            today = today,
            passportExpiry = trip.endDate.plusMonths(2),
            uncheckedItems = listOf("우산"),
        )

        assertTrue(reminder!!.headline.contains("여권"))
    }

    @Test
    fun `해당 시점이 아니면 알리지 않는다`() {
        assertNull(TripPrepAdvisor.build(trip(today.plusDays(4)), today, lateArrivals = 3))
    }
}
