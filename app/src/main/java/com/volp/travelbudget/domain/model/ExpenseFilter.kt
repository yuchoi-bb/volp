package com.volp.travelbudget.domain.model

import java.time.LocalDate

/**
 * 지출 목록을 좁히는 조건.
 *
 * 여행이 길어지면 지출이 수십 건이 되고, 그때부터 목록은 훑는 것이 아니라 **찾는** 것이 된다.
 * 조건은 모두 그리고(AND)로 걸린다.
 */
data class ExpenseFilter(
    /** 메모와 통화에서 찾을 말. 대소문자는 가리지 않는다. */
    val query: String = "",
    val categories: Set<ExpenseCategory> = emptySet(),
    val methods: Set<PaymentMethod> = emptySet(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** 이 금액 이상만. 0이면 보지 않는다. */
    val minAmountKrw: Long = 0L,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && categories.isEmpty() && methods.isEmpty() &&
            from == null && to == null && minAmountKrw <= 0L

    /** 지금 걸린 조건 수. 화면의 배지에 쓴다. */
    val activeCount: Int
        get() = listOf(
            query.isNotBlank(),
            categories.isNotEmpty(),
            methods.isNotEmpty(),
            from != null || to != null,
            minAmountKrw > 0L,
        ).count { it }

    fun matches(expense: Expense): Boolean {
        if (categories.isNotEmpty() && expense.category !in categories) return false
        if (methods.isNotEmpty() && expense.method !in methods) return false
        if (from != null && expense.date.isBefore(from)) return false
        if (to != null && expense.date.isAfter(to)) return false
        if (minAmountKrw > 0L && expense.amountKrw < minAmountKrw) return false

        if (query.isBlank()) return true
        val needle = query.trim()
        return expense.memo.contains(needle, ignoreCase = true) ||
            expense.currencyCode.contains(needle, ignoreCase = true) ||
            expense.category.label.contains(needle, ignoreCase = true)
    }

    fun apply(expenses: List<Expense>): List<Expense> =
        if (isEmpty) expenses else expenses.filter { matches(it) }
}

/** 걸러 낸 결과를 한눈에 보여 주는 값. */
data class ExpenseFilterResult(
    val expenses: List<Expense>,
    val totalKrw: Long,
) {
    companion object {
        fun of(expenses: List<Expense>, filter: ExpenseFilter): ExpenseFilterResult {
            val filtered = filter.apply(expenses)
            return ExpenseFilterResult(filtered, filtered.sumOf { it.amountKrw })
        }
    }
}
