package com.volp.travelbudget.domain.model

import com.volp.travelbudget.domain.sync.Syncable
import java.time.LocalDate

/**
 * 실제로 쓴 돈 한 건.
 *
 * 금액은 항상 원화([amountKrw])로 저장해 두고, 현지 통화로 입력한 경우 원래 금액을
 * [originalAmount]와 [currencyCode]에 함께 남겨 나중에 무엇을 입력했는지 알 수 있게 한다.
 */
data class Expense(
    val id: Long = 0L,
    override val uid: String = "",
    override val updatedAt: Long = 0L,
    val tripId: Long,
    val category: ExpenseCategory,
    val amountKrw: Long,
    val originalAmount: Double?,
    val currencyCode: String,
    val date: LocalDate,
    val memo: String,
    /** 이 건에 적용한 환율(1 [currencyCode] 당 원). 나중에 실제 청구액으로 보정할 때 쓴다. */
    val exchangeRate: Double? = null,
    /** 카드로 냈는지 현금으로 냈는지. 현금 지갑 잔액을 세는 데 쓴다. */
    val method: PaymentMethod = PaymentMethod.UNKNOWN,
    val createdAt: Long = System.currentTimeMillis(),
) : Syncable {
    val enteredInForeignCurrency: Boolean
        get() = originalAmount != null && currencyCode != "KRW"

    /** 해외 결제인지. 보정은 해외 건에만 적용한다. */
    val isForeign: Boolean get() = currencyCode != "KRW"
}
