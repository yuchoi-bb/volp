package com.volp.travelbudget.domain.settlement

import com.volp.travelbudget.domain.model.Expense
import kotlin.math.roundToLong

/**
 * 카드 명세서에 찍힌 실제 청구액에 맞춰 해외 결제 금액을 보정한다.
 *
 * 카드 문자에 오는 것은 *승인* 금액이고, 실제 청구는 해외이용수수료와 확정 환율 때문에
 * 조금 달라진다. 여행이 끝난 뒤 명세서의 총액을 한 번 넣으면 그 비율만큼 건별로 나눠 붙인다.
 *
 * 보정은 항상 원본 금액에서 다시 계산한다. 두 번 넣어도 값이 겹쳐 커지지 않는다.
 */
object Settlement {

    /**
     * 실제 청구 총액으로 보정 계수를 구한다.
     *
     * @param billedTotalKrw 명세서에 찍힌 해외 결제 총액(원)
     * @param expenses 이 여행의 지출 전체
     * @return 승인액 합계 대비 청구액 비율. 해외 결제가 없으면 1.0.
     */
    fun factorFor(billedTotalKrw: Long, expenses: List<Expense>): Double {
        val approved = approvedForeignTotal(expenses)
        if (approved <= 0L || billedTotalKrw <= 0L) return 1.0
        return billedTotalKrw.toDouble() / approved.toDouble()
    }

    /** 보정 전(계수 1.0 기준) 해외 결제 승인액 합계. */
    fun approvedForeignTotal(expenses: List<Expense>): Long =
        expenses.filter { it.isForeign }.sumOf { baseKrwOf(it) }

    /**
     * 보정 계수를 적용한 지출 목록을 돌려준다. 국내 결제는 건드리지 않는다.
     */
    fun apply(expenses: List<Expense>, factor: Double): List<Expense> =
        expenses.map { expense ->
            if (!expense.isForeign) {
                expense
            } else {
                expense.copy(amountKrw = (baseKrwOf(expense) * factor).roundToLong())
            }
        }

    /**
     * 보정을 빼고 본 원화 금액.
     *
     * 건별 환율을 알고 있으면 현지 금액 × 환율로 되돌리고, 모르면 지금 저장된 금액을 그대로 쓴다.
     */
    private fun baseKrwOf(expense: Expense): Long {
        val original = expense.originalAmount
        val rate = expense.exchangeRate
        return if (original != null && rate != null && rate > 0.0) {
            (original * rate).roundToLong()
        } else {
            expense.amountKrw
        }
    }
}
