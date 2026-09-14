package com.volp.travelbudget.domain.cash

import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CashWalletTest {

    private val date = LocalDate.of(2026, 10, 3)

    private fun topUp(amount: Double, krw: Long, code: String = "JPY") = CashTopUp(
        tripId = 1L,
        currencyCode = code,
        amount = amount,
        krwPaid = krw,
        date = date,
    )

    private fun expense(
        amountKrw: Long,
        original: Double?,
        code: String,
        method: PaymentMethod,
        rate: Double? = null,
    ) = Expense(
        tripId = 1L,
        category = ExpenseCategory.FOOD,
        amountKrw = amountKrw,
        originalAmount = original,
        currencyCode = code,
        date = date,
        memo = "",
        exchangeRate = rate,
        method = method,
    )

    @Test
    fun `환전한 돈에서 현금 지출을 뺀 것이 남은 돈이다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(50_000.0, 470_000L)),
            expenses = listOf(
                expense(28_200L, 3_000.0, "JPY", PaymentMethod.CASH),
                expense(18_800L, 2_000.0, "JPY", PaymentMethod.CASH),
            ),
            currencyCode = "JPY",
        )

        assertEquals(50_000.0, summary.toppedUp, 0.001)
        assertEquals(5_000.0, summary.spent, 0.001)
        assertEquals(45_000.0, summary.remaining, 0.001)
    }

    @Test
    fun `카드로 낸 것은 지갑에서 빠지지 않는다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(50_000.0, 470_000L)),
            expenses = listOf(
                expense(94_000L, 10_000.0, "JPY", PaymentMethod.CARD),
                expense(9_400L, 1_000.0, "JPY", PaymentMethod.UNKNOWN),
            ),
            currencyCode = "JPY",
        )

        assertEquals(0.0, summary.spent, 0.001)
        assertEquals(50_000.0, summary.remaining, 0.001)
    }

    @Test
    fun `실효 환율은 낸 원화를 받은 돈으로 나눈 값이다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(50_000.0, 470_000L), topUp(30_000.0, 288_000L)),
            expenses = emptyList(),
            currencyCode = "JPY",
        )

        // (470,000 + 288,000) / 80,000 = 9.475
        assertEquals(9.475, summary.effectiveRate!!, 0.0001)
        assertEquals(758_000L, summary.remainingKrw)
    }

    @Test
    fun `현지 금액을 안 남긴 현금 지출은 그 건의 환율로 되돌린다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(50_000.0, 470_000L)),
            expenses = listOf(expense(9_400L, null, "JPY", PaymentMethod.CASH, rate = 9.4)),
            currencyCode = "JPY",
        )

        assertEquals(1_000.0, summary.spent, 0.001)
    }

    @Test
    fun `다른 통화 지출은 이 지갑과 무관하다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(50_000.0, 470_000L)),
            expenses = listOf(expense(20_000L, 20_000.0, "KRW", PaymentMethod.CASH)),
            currencyCode = "JPY",
        )

        assertEquals(0.0, summary.spent, 0.001)
    }

    @Test
    fun `원화 지갑은 원화 금액을 그대로 뺀다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(300_000.0, 300_000L, code = "KRW")),
            expenses = listOf(expense(45_000L, null, "KRW", PaymentMethod.CASH)),
            currencyCode = "KRW",
        )

        assertEquals(255_000.0, summary.remaining, 0.001)
        assertEquals(1.0, summary.effectiveRate!!, 0.0001)
    }

    @Test
    fun `채운 것보다 더 썼으면 알려 준다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(10_000.0, 94_000L)),
            expenses = listOf(expense(141_000L, 15_000.0, "JPY", PaymentMethod.CASH)),
            currencyCode = "JPY",
        )

        assertTrue(summary.overspent)
        assertEquals(-5_000.0, summary.remaining, 0.001)
    }

    @Test
    fun `남은 현금을 남은 날로 나눈다`() {
        val summary = CashWallets.summarize(
            topUps = listOf(topUp(60_000.0, 564_000L)),
            expenses = emptyList(),
            currencyCode = "JPY",
        )

        assertEquals(20_000.0, CashWallets.dailyAllowance(summary, 3)!!, 0.001)
        assertNull(CashWallets.dailyAllowance(summary, 0))
    }

    @Test
    fun `환전 기록이 없으면 빈 지갑이다`() {
        val summary = CashWallets.summarize(emptyList(), emptyList(), "JPY")

        assertTrue(summary.isEmpty)
        assertNull(summary.effectiveRate)
        assertEquals(0L, summary.remainingKrw)
    }
}
