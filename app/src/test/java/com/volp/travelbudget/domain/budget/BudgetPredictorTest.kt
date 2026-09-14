package com.volp.travelbudget.domain.budget

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.TravelStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPredictorTest {

    private val osaka = DestinationCatalog.find("osaka")!!

    @Test
    fun `모든 항목이 결과에 들어간다`() {
        val result = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = 3, travelers = 2, style = TravelStyle.STANDARD),
        )
        assertEquals(ExpenseCategory.entries.toSet(), result.keys)
    }

    @Test
    fun `항공권은 인원수에 비례한다`() {
        fun flightFor(travelers: Int) = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = 3, travelers = travelers, style = TravelStyle.STANDARD),
        ).getValue(ExpenseCategory.FLIGHT)

        assertEquals(osaka.flightPerPerson, flightFor(1))
        assertEquals(osaka.flightPerPerson * 3, flightFor(3))
    }

    @Test
    fun `항공권을 빼면 0원이 된다`() {
        val result = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, 3, 2, TravelStyle.STANDARD, includeFlight = false),
        )
        assertEquals(0L, result.getValue(ExpenseCategory.FLIGHT))
    }

    @Test
    fun `숙박은 두 명당 객실 하나로 계산한다`() {
        fun lodgingFor(travelers: Int) = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = 2, travelers = travelers, style = TravelStyle.STANDARD),
        ).getValue(ExpenseCategory.LODGING)

        val oneRoom = osaka.lodgingPerNight * 2
        assertEquals(oneRoom, lodgingFor(1))
        assertEquals(oneRoom, lodgingFor(2))
        assertEquals(oneRoom * 2, lodgingFor(3))
        assertEquals(oneRoom * 2, lodgingFor(4))
    }

    @Test
    fun `당일치기는 숙박비가 없고 하루치 경비만 잡힌다`() {
        val result = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = 0, travelers = 1, style = TravelStyle.STANDARD),
        )
        assertEquals(0L, result.getValue(ExpenseCategory.LODGING))
        assertEquals(osaka.foodPerDay, result.getValue(ExpenseCategory.FOOD))
    }

    @Test
    fun `여행 스타일이 올라가면 총액도 올라간다`() {
        fun totalFor(style: TravelStyle) = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = 3, travelers = 2, style = style),
        ).values.sum()

        assertTrue(totalFor(TravelStyle.BUDGET) < totalFor(TravelStyle.STANDARD))
        assertTrue(totalFor(TravelStyle.STANDARD) < totalFor(TravelStyle.LUXURY))
    }

    @Test
    fun `예비비는 나머지 항목 합계의 8퍼센트다`() {
        val result = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = 3, travelers = 2, style = TravelStyle.STANDARD),
        )
        val core = result.filterKeys { it != ExpenseCategory.ETC }.values.sum()
        val expected = Math.round(core * 0.08 / 1_000.0) * 1_000L
        assertEquals(expected, result.getValue(ExpenseCategory.ETC))
    }

    @Test
    fun `금액은 천원 단위로 떨어진다`() {
        val result = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = 4, travelers = 3, style = TravelStyle.LUXURY),
        )
        result.values.forEach { assertEquals(0L, it % 1_000L) }
    }

    @Test
    fun `목록에 없는 도시는 권역 평균 단가를 쓴다`() {
        val custom = DestinationCatalog.find(DestinationCatalog.customKey(com.volp.travelbudget.domain.model.Region.EUROPE))!!
        val europeans = DestinationCatalog.destinations.filter {
            it.region == com.volp.travelbudget.domain.model.Region.EUROPE
        }
        assertEquals(europeans.sumOf { it.foodPerDay } / europeans.size, custom.foodPerDay)
    }

    @Test
    fun `잘못된 입력도 계산이 깨지지 않는다`() {
        val result = BudgetPredictor.predict(
            BudgetPredictor.Input(osaka, nights = -3, travelers = 0, style = TravelStyle.STANDARD),
        )
        assertEquals(0L, result.getValue(ExpenseCategory.LODGING))
        assertTrue(result.getValue(ExpenseCategory.FOOD) > 0L)
    }
}
