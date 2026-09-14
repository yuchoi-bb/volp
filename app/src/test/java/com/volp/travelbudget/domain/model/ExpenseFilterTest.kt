package com.volp.travelbudget.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseFilterTest {

    private fun expense(
        category: ExpenseCategory = ExpenseCategory.FOOD,
        amount: Long = 10_000L,
        date: LocalDate = LocalDate.of(2026, 10, 3),
        memo: String = "",
        method: PaymentMethod = PaymentMethod.CARD,
        currency: String = "KRW",
    ) = Expense(
        tripId = 1L,
        category = category,
        amountKrw = amount,
        originalAmount = null,
        currencyCode = currency,
        date = date,
        memo = memo,
        method = method,
    )

    private val all = listOf(
        expense(memo = "이치란 라멘", amount = 18_000L),
        expense(
            category = ExpenseCategory.SHOPPING,
            memo = "돈키호테",
            amount = 52_000L,
            method = PaymentMethod.CASH,
        ),
        expense(
            category = ExpenseCategory.TRANSPORT,
            memo = "지하철",
            amount = 3_000L,
            date = LocalDate.of(2026, 10, 5),
            method = PaymentMethod.CASH,
        ),
    )

    @Test
    fun `조건이 없으면 그대로 둔다`() {
        val filter = ExpenseFilter()

        assertTrue(filter.isEmpty)
        assertEquals(all, filter.apply(all))
        assertEquals(0, filter.activeCount)
    }

    @Test
    fun `메모로 찾는다`() {
        val found = ExpenseFilter(query = "라멘").apply(all)

        assertEquals(1, found.size)
        assertEquals("이치란 라멘", found.first().memo)
    }

    @Test
    fun `항목 이름으로도 찾힌다`() {
        val found = ExpenseFilter(query = "쇼핑").apply(all)

        assertEquals(1, found.size)
        assertEquals(ExpenseCategory.SHOPPING, found.first().category)
    }

    @Test
    fun `항목과 결제 수단은 함께 걸린다`() {
        val filter = ExpenseFilter(
            categories = setOf(ExpenseCategory.SHOPPING, ExpenseCategory.TRANSPORT),
            methods = setOf(PaymentMethod.CASH),
        )

        assertEquals(2, filter.apply(all).size)
        assertEquals(2, filter.activeCount)
    }

    @Test
    fun `날짜 구간으로 자른다`() {
        val filter = ExpenseFilter(from = LocalDate.of(2026, 10, 4))

        val found = filter.apply(all)

        assertEquals(1, found.size)
        assertEquals("지하철", found.first().memo)
    }

    @Test
    fun `금액 아래는 걸러 낸다`() {
        val found = ExpenseFilter(minAmountKrw = 20_000L).apply(all)

        assertEquals(1, found.size)
        assertEquals(52_000L, found.first().amountKrw)
    }

    @Test
    fun `걸러 낸 것의 합을 함께 낸다`() {
        val result = ExpenseFilterResult.of(all, ExpenseFilter(methods = setOf(PaymentMethod.CASH)))

        assertEquals(2, result.expenses.size)
        assertEquals(55_000L, result.totalKrw)
    }

    @Test
    fun `대소문자는 가리지 않는다`() {
        val withForeign = all + expense(currency = "JPY", memo = "Convenience")

        assertEquals(1, ExpenseFilter(query = "jpy").apply(withForeign).size)
        assertEquals(1, ExpenseFilter(query = "CONVENIENCE").apply(withForeign).size)
    }
}
