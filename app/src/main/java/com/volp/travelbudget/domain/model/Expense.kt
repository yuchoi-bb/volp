package com.volp.travelbudget.domain.model

import java.time.LocalDate

/**
 * 실제로 쓴 돈 한 건.
 *
 * 금액은 항상 원화([amountKrw])로 저장해 두고, 현지 통화로 입력한 경우 원래 금액을
 * [originalAmount]와 [currencyCode]에 함께 남겨 나중에 무엇을 입력했는지 알 수 있게 한다.
 */
data class Expense(
    val id: Long = 0L,
    val tripId: Long,
    val category: ExpenseCategory,
    val amountKrw: Long,
    val originalAmount: Double?,
    val currencyCode: String,
    val date: LocalDate,
    val memo: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val enteredInForeignCurrency: Boolean
        get() = originalAmount != null && currencyCode != "KRW"
}
