package com.volp.travelbudget.domain.cash

import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.PaymentMethod
import com.volp.travelbudget.domain.sync.Syncable
import java.time.LocalDate
import kotlin.math.roundToLong

enum class TopUpKind(val label: String, val emoji: String) {
    EXCHANGE("환전", "💱"),
    WITHDRAW("ATM 인출", "🏧"),
    LEFTOVER("지난 여행에서 남은 돈", "👛"),
    OTHER("그 밖에", "💵"),
    ;

    companion object {
        fun fromName(name: String?): TopUpKind = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * 지갑에 현금을 채운 한 건.
 *
 * 환전은 고시환율대로 되지 않는다. 수수료와 우대율이 붙어 실제로 낸 원화([krwPaid])와 받은 현지
 * 통화([amount])의 비가 곧 **내가 산 환율**이다. 그 값을 남겨 두면 다음 환전을 가늠할 수 있다.
 */
data class CashTopUp(
    val id: Long = 0L,
    override val uid: String = "",
    override val updatedAt: Long = 0L,
    val tripId: Long,
    val kind: TopUpKind = TopUpKind.EXCHANGE,
    val currencyCode: String,
    /** 받은 현지 통화 금액. */
    val amount: Double,
    /** 그 대가로 낸 원화. */
    val krwPaid: Long,
    val date: LocalDate,
    val memo: String = "",
    val createdAt: Long = 0L,
) : Syncable {
    /** 이 건의 실효 환율(1 [currencyCode] 당 원). 금액이 0이면 알 수 없다. */
    val effectiveRate: Double?
        get() = if (amount > 0.0) krwPaid.toDouble() / amount else null
}

/** 지금 지갑에 무엇이 얼마나 남았는지. */
data class WalletSummary(
    val currencyCode: String,
    /** 채운 현지 통화 합계. */
    val toppedUp: Double,
    /** 채우느라 낸 원화 합계. */
    val toppedUpKrw: Long,
    /** 현금으로 쓴 현지 통화 합계. */
    val spent: Double,
    val effectiveRate: Double?,
) {
    val remaining: Double get() = toppedUp - spent

    /** 남은 현금을 원화로 환산한 값. 실효 환율을 모르면 0. */
    val remainingKrw: Long
        get() = effectiveRate?.let { (remaining * it).roundToLong() } ?: 0L

    val isEmpty: Boolean get() = toppedUp <= 0.0 && spent <= 0.0

    /** 채운 것보다 더 썼는지. 어딘가 빠뜨렸다는 뜻이므로 화면에서 알려 준다. */
    val overspent: Boolean get() = remaining < 0.0
}

/**
 * 현금 지갑.
 *
 * 카드는 문자가 알아서 들어오지만 현금은 쓰는 순간 기록이 없으면 사라진다. 환전한 돈에서 현금
 * 지출을 빼 두면 **지금 주머니에 얼마 남았는지**가 나오고, 그것이 여행 중에 제일 자주 궁금한 값이다.
 */
object CashWallets {

    /**
     * @param expenses 이 여행의 모든 지출. 현금으로 낸 것만 골라 쓴다.
     */
    fun summarize(
        topUps: List<CashTopUp>,
        expenses: List<Expense>,
        currencyCode: String,
    ): WalletSummary {
        val mine = topUps.filter { it.currencyCode == currencyCode }
        val toppedUp = mine.sumOf { it.amount }
        val toppedUpKrw = mine.sumOf { it.krwPaid }

        val spent = expenses
            .filter { it.method == PaymentMethod.CASH }
            .sumOf { localAmountOf(it, currencyCode) }

        return WalletSummary(
            currencyCode = currencyCode,
            toppedUp = toppedUp,
            toppedUpKrw = toppedUpKrw,
            spent = spent,
            effectiveRate = if (toppedUp > 0.0) toppedUpKrw.toDouble() / toppedUp else null,
        )
    }

    /**
     * 지출 한 건이 이 지갑에서 나간 현지 통화 금액.
     *
     * 원화 지갑이면 원화 금액을 그대로 쓴다. 통화가 다른 지출은 이 지갑과 무관하므로 0이다.
     */
    private fun localAmountOf(expense: Expense, currencyCode: String): Double = when {
        expense.currencyCode == currencyCode ->
            expense.originalAmount ?: convert(expense, currencyCode)
        else -> 0.0
    }

    private fun convert(expense: Expense, currencyCode: String): Double = when {
        currencyCode == "KRW" -> expense.amountKrw.toDouble()
        // 현지 금액을 안 남긴 건은 그 건에 적용한 환율로 되돌린다.
        expense.exchangeRate != null && expense.exchangeRate > 0.0 ->
            expense.amountKrw / expense.exchangeRate
        else -> 0.0
    }

    /**
     * 남은 현금으로 하루에 쓸 수 있는 돈.
     *
     * 여행 마지막 날에 현금이 남으면 그만큼 다시 바꿔 와야 하고, 모자라면 수수료를 물고 인출해야 한다.
     * 둘 다 손해라 남은 날에 고르게 펴 보는 것이 낫다.
     */
    fun dailyAllowance(summary: WalletSummary, daysLeft: Int): Double? {
        if (daysLeft <= 0 || summary.remaining <= 0.0) return null
        return summary.remaining / daysLeft
    }
}
