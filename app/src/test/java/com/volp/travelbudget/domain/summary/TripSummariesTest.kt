package com.volp.travelbudget.domain.summary

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripSummariesTest {

    private val budget = mapOf(
        ExpenseCategory.FLIGHT to 600_000L,
        ExpenseCategory.LODGING to 400_000L,
        ExpenseCategory.FOOD to 400_000L,
        ExpenseCategory.TRANSPORT to 100_000L,
        ExpenseCategory.ACTIVITY to 200_000L,
        ExpenseCategory.SHOPPING to 200_000L,
        ExpenseCategory.ETC to 100_000L,
    )

    private val trip = Trip(
        id = 1,
        title = "오사카",
        destinationKey = "osaka",
        destinationName = "오사카",
        region = Region.JAPAN,
        startDate = LocalDate.of(2026, 4, 18),
        endDate = LocalDate.of(2026, 4, 21),
        travelers = 2,
        style = TravelStyle.STANDARD,
        includeFlight = true,
        currencyCode = "JPY",
        exchangeRate = 9.3,
        predictedBudget = budget,
        plannedBudget = budget,
        createdAt = 0L,
    )

    @Test
    fun `3박 4일은 4일로 센다`() {
        assertEquals(3, trip.nights)
        assertEquals(4, trip.days)
    }

    @Test
    fun `남은 예산과 진행률을 계산한다`() {
        val summary = TripSummaries.summarize(
            trip,
            mapOf(ExpenseCategory.FLIGHT to 600_000L, ExpenseCategory.FOOD to 200_000L),
            today = LocalDate.of(2026, 4, 19),
        )

        assertEquals(2_000_000L, summary.totalPlanned)
        assertEquals(800_000L, summary.totalSpent)
        assertEquals(1_200_000L, summary.remaining)
        assertFalse(summary.isOverBudget)
        assertEquals(TripStatus.ONGOING, summary.status)
    }

    @Test
    fun `여행 시작 전에는 하루 평균이 없다`() {
        val summary = TripSummaries.summarize(trip, emptyMap(), today = LocalDate.of(2026, 4, 1))

        assertEquals(TripStatus.UPCOMING, summary.status)
        assertEquals(0, summary.elapsedDays)
        assertEquals(4, summary.remainingDays)
        assertEquals(0L, summary.dailyAverage)
    }

    @Test
    fun `하루 평균은 항공과 숙박을 뺀 금액으로 계산한다`() {
        // 이틀째에 식비 200,000원을 썼다. 항공 600,000원은 평균에서 빠진다.
        val summary = TripSummaries.summarize(
            trip,
            mapOf(ExpenseCategory.FLIGHT to 600_000L, ExpenseCategory.FOOD to 200_000L),
            today = LocalDate.of(2026, 4, 19),
        )

        assertEquals(2, summary.elapsedDays)
        assertEquals(100_000L, summary.dailyAverage)
    }

    @Test
    fun `남은 기간에 하루 얼마 쓸 수 있는지 알려준다`() {
        val summary = TripSummaries.summarize(
            trip,
            mapOf(ExpenseCategory.FOOD to 200_000L),
            today = LocalDate.of(2026, 4, 19),
        )

        // 하루 경비 예산 1,000,000원 중 200,000원을 썼고 이틀 남았다.
        assertEquals(2, summary.remainingDays)
        assertEquals(400_000L, summary.dailyAllowance)
    }

    @Test
    fun `여행이 끝나면 하루 배정액이 없다`() {
        val summary = TripSummaries.summarize(trip, emptyMap(), today = LocalDate.of(2026, 5, 1))

        assertEquals(TripStatus.FINISHED, summary.status)
        assertEquals(4, summary.elapsedDays)
        assertEquals(0, summary.remainingDays)
        assertNull(summary.dailyAllowance)
    }

    @Test
    fun `지금 속도로 예산을 넘길지 알려준다`() {
        // 이틀 동안 하루 경비로 900,000원을 썼다. 하루 450,000원 속도면 남은 이틀에 900,000원을 더 쓴다.
        val summary = TripSummaries.summarize(
            trip,
            mapOf(ExpenseCategory.FOOD to 900_000L),
            today = LocalDate.of(2026, 4, 19),
        )

        assertEquals(900_000L + 900_000L + 1_000_000L, summary.projectedTotal)
        assertTrue(summary.projectedOverBudget)
    }

    @Test
    fun `항목별로 예산과 실제를 비교한다`() {
        val summary = TripSummaries.summarize(
            trip,
            mapOf(ExpenseCategory.FOOD to 500_000L),
            today = LocalDate.of(2026, 4, 19),
        )
        val food = summary.categories.first { it.category == ExpenseCategory.FOOD }

        assertEquals(400_000L, food.planned)
        assertEquals(500_000L, food.spent)
        assertEquals(100_000L, food.difference)
        assertTrue(food.isOverBudget)
        assertEquals(1.25f, food.ratio, 0.001f)
    }

    @Test
    fun `현지 통화를 원화로 환산한다`() {
        assertEquals(11_160L, TripSummaries.toKrw(1_200.0, 9.3))
    }
}
