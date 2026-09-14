package com.volp.travelbudget.domain.alert

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.summary.TripSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetAlertsTest {

    // 하루 경비 예산 900,000원(3일 × 300,000원), 항공·숙박 별도 1,100,000원
    private val budget = mapOf(
        ExpenseCategory.FLIGHT to 600_000L,
        ExpenseCategory.LODGING to 500_000L,
        ExpenseCategory.FOOD to 450_000L,
        ExpenseCategory.TRANSPORT to 150_000L,
        ExpenseCategory.ACTIVITY to 300_000L,
    )

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
        predictedBudget = budget,
        plannedBudget = budget,
    )

    private fun alerts(
        spent: Map<ExpenseCategory, Long>,
        today: LocalDate,
        spentTodayDaily: Long,
    ) = BudgetAlerts.evaluate(TripSummaries.summarize(trip, spent, today), today, spentTodayDaily)

    private fun levels(list: List<BudgetAlert>) = list.map { it.level }.toSet()

    @Test
    fun `예산 안에서 쓰면 아무 알림도 없다`() {
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 100_000L),
            today = LocalDate.of(2026, 4, 18),
            spentTodayDaily = 100_000L,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `전체 예산을 넘기면 알린다`() {
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 2_100_000L),
            today = LocalDate.of(2026, 4, 19),
            spentTodayDaily = 100_000L,
        )
        assertTrue(BudgetAlertLevel.TOTAL_OVER in levels(result))
    }

    @Test
    fun `하루 몫을 훌쩍 넘기면 알린다`() {
        // 하루 몫 300,000원. 400,000원이면 넘긴 것으로 본다.
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 400_000L),
            today = LocalDate.of(2026, 4, 18),
            spentTodayDaily = 400_000L,
        )
        assertTrue(BudgetAlertLevel.DAILY_OVER in levels(result))
    }

    @Test
    fun `조금 넘긴 정도로는 울리지 않는다`() {
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 320_000L),
            today = LocalDate.of(2026, 4, 18),
            spentTodayDaily = 320_000L,
        )
        assertTrue(BudgetAlertLevel.DAILY_OVER !in levels(result))
    }

    @Test
    fun `지금 속도면 넘길 것 같을 때 미리 알린다`() {
        // 이틀째에 하루 경비로 900,000원을 썼다. 하루 450,000원 속도면 남은 하루에 더 쓴다.
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 900_000L),
            today = LocalDate.of(2026, 4, 19),
            spentTodayDaily = 200_000L,
        )
        assertTrue(BudgetAlertLevel.PROJECTED_OVER in levels(result))
    }

    @Test
    fun `이미 넘겼으면 예상 초과는 따로 알리지 않는다`() {
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 2_100_000L),
            today = LocalDate.of(2026, 4, 19),
            spentTodayDaily = 100_000L,
        )
        assertTrue(BudgetAlertLevel.PROJECTED_OVER !in levels(result))
    }

    @Test
    fun `여행이 끝난 뒤에는 앞일을 말하지 않는다`() {
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 900_000L),
            today = LocalDate.of(2026, 5, 1),
            spentTodayDaily = 0L,
        )
        assertTrue(BudgetAlertLevel.PROJECTED_OVER !in levels(result))
        assertTrue(BudgetAlertLevel.DAILY_OVER !in levels(result))
    }

    @Test
    fun `하루 알림 열쇠에는 날짜가 들어가 하루에 한 번만 울린다`() {
        val result = alerts(
            spent = mapOf(ExpenseCategory.FOOD to 400_000L),
            today = LocalDate.of(2026, 4, 18),
            spentTodayDaily = 400_000L,
        )
        val daily = result.first { it.level == BudgetAlertLevel.DAILY_OVER }

        assertEquals("daily:2026-04-18", daily.key)
    }

    @Test
    fun `예산을 세우지 않은 여행은 알리지 않는다`() {
        val noBudget = trip.copy(plannedBudget = emptyMap(), predictedBudget = emptyMap())
        val summary = TripSummaries.summarize(
            noBudget,
            mapOf(ExpenseCategory.FOOD to 500_000L),
            LocalDate.of(2026, 4, 18),
        )

        assertTrue(BudgetAlerts.evaluate(summary, LocalDate.of(2026, 4, 18), 500_000L).isEmpty())
    }
}
