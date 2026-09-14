package com.volp.travelbudget.domain.stats

import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripStatisticsTest {

    private val trip = Trip(
        id = 1,
        title = "오사카",
        destinationKey = "osaka",
        destinationName = "오사카",
        region = Region.JAPAN,
        startDate = LocalDate.of(2026, 4, 18),
        endDate = LocalDate.of(2026, 4, 20),
        travelers = 2,
        style = TravelStyle.STANDARD,
        includeFlight = true,
        currencyCode = "JPY",
        exchangeRate = 9.3,
        predictedBudget = emptyMap(),
        plannedBudget = emptyMap(),
    )

    private fun expense(
        day: Int,
        amount: Long,
        category: ExpenseCategory = ExpenseCategory.FOOD,
        original: Double? = null,
        currency: String = "KRW",
    ) = Expense(
        tripId = 1L,
        category = category,
        amountKrw = amount,
        originalAmount = original,
        currencyCode = currency,
        date = LocalDate.of(2026, 4, day),
        memo = "",
    )

    private val expenses = listOf(
        expense(18, 600_000, ExpenseCategory.FLIGHT),
        expense(18, 50_000, original = 5_000.0, currency = "JPY"),
        expense(19, 120_000, original = 12_000.0, currency = "JPY"),
        expense(19, 30_000, ExpenseCategory.SHOPPING),
        expense(20, 40_000),
    )

    @Test
    fun `여행 기간의 모든 날짜가 그래프에 들어간다`() {
        val stats = TripStatisticsCalculator.of(trip, listOf(expense(19, 10_000)))

        assertEquals(3, stats.dailyTotals.size)
        assertEquals(listOf(0L, 10_000L, 0L), stats.dailyTotals.map { it.amountKrw })
    }

    @Test
    fun `기간 밖에 찍힌 지출도 빠뜨리지 않는다`() {
        // 출발 전에 미리 결제한 항공권
        val stats = TripStatisticsCalculator.of(trip, listOf(expense(1, 600_000, ExpenseCategory.FLIGHT)))

        assertEquals(600_000L, stats.total)
        assertTrue(stats.dailyTotals.any { it.date == LocalDate.of(2026, 4, 1) })
    }

    @Test
    fun `항목별 비중을 큰 것부터 돌려준다`() {
        val stats = TripStatisticsCalculator.of(trip, expenses)

        assertEquals(ExpenseCategory.FLIGHT, stats.categoryShares.first().category)
        assertEquals(600_000L, stats.categoryShares.first().amountKrw)
        assertEquals(0.71f, stats.categoryShares.first().ratio, 0.01f)
        assertEquals(1f, stats.categoryShares.sumOf { it.ratio.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun `항공과 숙박을 빼고 볼 수 있다`() {
        val stats = TripStatisticsCalculator.of(trip, expenses, includeUpfront = false)

        assertEquals(240_000L, stats.total)
        assertTrue(stats.categoryShares.none { it.category == ExpenseCategory.FLIGHT })
    }

    @Test
    fun `가장 많이 쓴 날과 항목을 찾는다`() {
        val stats = TripStatisticsCalculator.of(trip, expenses, includeUpfront = false)

        assertEquals(LocalDate.of(2026, 4, 19), stats.busiestDay?.date)
        assertEquals(150_000L, stats.busiestDay?.amountKrw)
        assertEquals(ExpenseCategory.FOOD, stats.topCategory?.category)
    }

    @Test
    fun `하루 평균은 실제로 쓴 날로만 나눈다`() {
        val stats = TripStatisticsCalculator.of(trip, expenses, includeUpfront = false)

        // 3일 중 3일 모두 지출이 있다. 240,000 / 3
        assertEquals(80_000L, stats.dailyAverage)
    }

    @Test
    fun `통화별로 현지에서 쓴 금액을 모은다`() {
        val stats = TripStatisticsCalculator.of(trip, expenses)
        val jpy = stats.currencyTotals.first { it.currencyCode == "JPY" }

        assertEquals(17_000.0, jpy.amount, 0.001)
        assertEquals(170_000L, jpy.amountKrw)
    }

    @Test
    fun `1인당 금액을 계산한다`() {
        val stats = TripStatisticsCalculator.of(trip, expenses)
        assertEquals(420_000L, stats.perPerson)
    }

    @Test
    fun `지출이 없어도 계산이 깨지지 않는다`() {
        val stats = TripStatisticsCalculator.of(trip, emptyList())

        assertEquals(0L, stats.total)
        assertEquals(0L, stats.dailyAverage)
        assertEquals(null, stats.busiestDay)
        assertEquals(null, stats.topCategory)
    }
}
