package com.volp.travelbudget.domain.settlement

import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SettlementTest {

    private fun expense(
        id: Long,
        amountKrw: Long,
        original: Double? = null,
        currency: String = "KRW",
        rate: Double? = null,
    ) = Expense(
        id = id,
        tripId = 1L,
        category = ExpenseCategory.FOOD,
        amountKrw = amountKrw,
        originalAmount = original,
        currencyCode = currency,
        date = LocalDate.of(2026, 4, 19),
        memo = "",
        exchangeRate = rate,
    )

    private val expenses = listOf(
        expense(1, 11_160, original = 1_200.0, currency = "JPY", rate = 9.3),
        expense(2, 22_320, original = 2_400.0, currency = "JPY", rate = 9.3),
        expense(3, 50_000), // 국내 결제
    )

    @Test
    fun `해외 결제 승인액만 합친다`() {
        assertEquals(33_480L, Settlement.approvedForeignTotal(expenses))
    }

    @Test
    fun `실제 청구액 비율로 건별 금액을 맞춘다`() {
        // 수수료가 붙어 승인액보다 2% 더 청구됐다.
        val billed = 34_150L
        val factor = Settlement.factorFor(billed, expenses)
        val adjusted = Settlement.apply(expenses, factor)

        assertEquals(billed, adjusted.filter { it.isForeign }.sumOf { it.amountKrw })
        assertEquals(50_000L, adjusted.first { !it.isForeign }.amountKrw)
    }

    @Test
    fun `두 번 적용해도 값이 겹쳐 커지지 않는다`() {
        val billed = 34_150L
        val once = Settlement.apply(expenses, Settlement.factorFor(billed, expenses))
        val twice = Settlement.apply(once, Settlement.factorFor(billed, once))

        assertEquals(
            once.map { it.amountKrw },
            twice.map { it.amountKrw },
        )
    }

    @Test
    fun `보정을 풀면 승인액으로 돌아온다`() {
        val adjusted = Settlement.apply(expenses, Settlement.factorFor(40_000L, expenses))
        val restored = Settlement.apply(adjusted, 1.0)

        assertEquals(expenses.map { it.amountKrw }, restored.map { it.amountKrw })
    }

    @Test
    fun `해외 결제가 없으면 계수는 1이다`() {
        val onlyDomestic = listOf(expense(1, 10_000))
        assertEquals(1.0, Settlement.factorFor(20_000L, onlyDomestic), 0.0001)
    }

    @Test
    fun `건별 환율을 모르면 저장된 금액을 기준으로 쓴다`() {
        val legacy = listOf(expense(1, 10_000, original = 1_000.0, currency = "JPY", rate = null))
        assertEquals(10_000L, Settlement.approvedForeignTotal(legacy))

        val adjusted = Settlement.apply(legacy, Settlement.factorFor(11_000L, legacy))
        assertEquals(11_000L, adjusted.single().amountKrw)
    }
}
