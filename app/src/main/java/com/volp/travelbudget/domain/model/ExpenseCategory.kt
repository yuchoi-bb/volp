package com.volp.travelbudget.domain.model

/**
 * 여행 경비를 나누는 기준이 되는 지출 항목.
 *
 * 예측 예산과 실제 지출 모두 같은 항목 체계를 쓰기 때문에 두 값을 항목별로 바로 비교할 수 있다.
 */
enum class ExpenseCategory(
    val label: String,
    val emoji: String,
) {
    FLIGHT("항공", "✈️"),
    LODGING("숙박", "🏨"),
    FOOD("식비", "🍽️"),
    TRANSPORT("현지 교통", "🚌"),
    ACTIVITY("관광·액티비티", "🎟️"),
    SHOPPING("쇼핑", "🛍️"),
    ETC("기타", "💳"),
    ;

    companion object {
        fun fromName(name: String): ExpenseCategory =
            entries.firstOrNull { it.name == name } ?: ETC
    }
}
