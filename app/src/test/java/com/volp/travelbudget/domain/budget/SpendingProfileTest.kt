package com.volp.travelbudget.domain.budget

import com.volp.travelbudget.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpendingProfileTest {

    private fun outcome(
        endDate: LocalDate,
        food: Pair<Long, Long>? = null,
        flight: Pair<Long, Long>? = null,
        lodging: Pair<Long, Long>? = null,
    ) = TripOutcome(
        endDate = endDate,
        predicted = listOfNotNull(
            food?.let { ExpenseCategory.FOOD to it.first },
            flight?.let { ExpenseCategory.FLIGHT to it.first },
            lodging?.let { ExpenseCategory.LODGING to it.first },
        ).toMap(),
        actual = listOfNotNull(
            food?.let { ExpenseCategory.FOOD to it.second },
            flight?.let { ExpenseCategory.FLIGHT to it.second },
            lodging?.let { ExpenseCategory.LODGING to it.second },
        ).toMap(),
    )

    @Test
    fun `여행이 없으면 아무것도 배우지 않는다`() {
        val profile = SpendingProfiles.learn(emptyList())

        assertTrue(profile.isEmpty)
        assertEquals(1.0, profile.factorFor(ExpenseCategory.FOOD), 0.0)
    }

    @Test
    fun `늘 더 쓴 항목은 계수가 1보다 커진다`() {
        val history = listOf(
            outcome(LocalDate.of(2026, 3, 10), food = 500_000L to 750_000L),
            outcome(LocalDate.of(2026, 6, 10), food = 400_000L to 600_000L),
            outcome(LocalDate.of(2026, 8, 10), food = 300_000L to 450_000L),
        )

        val factor = SpendingProfiles.learn(history).factorFor(ExpenseCategory.FOOD)

        // 실제 비율은 1.5지만 표본이 셋뿐이라 그만큼 다 반영하지는 않는다.
        assertTrue("계수가 1보다 커야 한다: $factor", factor > 1.0)
        assertTrue("계수가 실제 비율을 넘으면 안 된다: $factor", factor < 1.5)
    }

    @Test
    fun `한 번 다녀온 것으로 단정하지 않는다`() {
        val once = SpendingProfiles.learn(
            listOf(outcome(LocalDate.of(2026, 8, 10), food = 500_000L to 1_000_000L)),
        ).factorFor(ExpenseCategory.FOOD)

        val many = SpendingProfiles.learn(
            (1..6).map { outcome(LocalDate.of(2026, it, 10), food = 500_000L to 1_000_000L) },
        ).factorFor(ExpenseCategory.FOOD)

        assertTrue("표본이 많을수록 계수가 실제에 가까워야 한다: $once vs $many", many > once)
    }

    @Test
    fun `유별난 한 번이 계수를 망치지 않는다`() {
        val history = listOf(
            outcome(LocalDate.of(2026, 8, 10), lodging = 500_000L to 9_000_000L),
        )

        val factor = SpendingProfiles.learn(history).factorFor(ExpenseCategory.LODGING)

        assertTrue("계수는 2.0을 넘지 않는다: $factor", factor <= 2.0)
    }

    @Test
    fun `항공은 배우지 않는다`() {
        val history = (1..5).map { outcome(LocalDate.of(2026, it, 10), flight = 600_000L to 1_200_000L) }

        val profile = SpendingProfiles.learn(history)

        assertEquals(1.0, profile.factorFor(ExpenseCategory.FLIGHT), 0.0)
    }

    @Test
    fun `예측이 너무 작은 항목은 보지 않는다`() {
        val history = (1..5).map { outcome(LocalDate.of(2026, it, 10), food = 3_000L to 30_000L) }

        assertEquals(1.0, SpendingProfiles.learn(history).factorFor(ExpenseCategory.FOOD), 0.0)
    }

    @Test
    fun `최근 여행에 더 큰 무게를 준다`() {
        val recentlyFrugal = listOf(
            outcome(LocalDate.of(2026, 8, 10), food = 500_000L to 400_000L),
            outcome(LocalDate.of(2025, 1, 10), food = 500_000L to 1_000_000L),
        )
        val recentlyLavish = listOf(
            outcome(LocalDate.of(2026, 8, 10), food = 500_000L to 1_000_000L),
            outcome(LocalDate.of(2025, 1, 10), food = 500_000L to 400_000L),
        )

        val frugal = SpendingProfiles.learn(recentlyFrugal).factorFor(ExpenseCategory.FOOD)
        val lavish = SpendingProfiles.learn(recentlyLavish).factorFor(ExpenseCategory.FOOD)

        assertTrue("최근에 아껴 썼으면 계수가 더 낮아야 한다: $frugal vs $lavish", frugal < lavish)
    }

    @Test
    fun `예측에 계수를 입히면 금액이 천 원 단위로 떨어진다`() {
        val profile = SpendingProfile(mapOf(ExpenseCategory.FOOD to 1.23), tripCount = 3)

        val applied = profile.apply(
            mapOf(ExpenseCategory.FOOD to 500_000L, ExpenseCategory.LODGING to 300_000L),
        )

        assertEquals(615_000L, applied[ExpenseCategory.FOOD])
        // 배운 적 없는 항목은 그대로 둔다.
        assertEquals(300_000L, applied[ExpenseCategory.LODGING])
    }

    @Test
    fun `눈에 띄게 어긋난 항목만 골라 낸다`() {
        val profile = SpendingProfile(
            mapOf(
                ExpenseCategory.FOOD to 1.45,
                ExpenseCategory.LODGING to 1.02,
                ExpenseCategory.SHOPPING to 0.7,
            ),
            tripCount = 4,
        )

        val notable = profile.notableAdjustments().map { it.first }

        assertEquals(listOf(ExpenseCategory.FOOD, ExpenseCategory.SHOPPING), notable)
    }
}
