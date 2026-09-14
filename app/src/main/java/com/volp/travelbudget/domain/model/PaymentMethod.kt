package com.volp.travelbudget.domain.model

/**
 * 무엇으로 냈는지.
 *
 * 카드는 문자로 저절로 들어오고 현금은 손으로 넣는다. 이 둘을 갈라 둬야 지갑에 남은 현금을 셀 수 있다.
 */
enum class PaymentMethod(val label: String, val emoji: String) {
    CARD("카드", "💳"),
    CASH("현금", "💵"),
    /** 어느 쪽인지 기록하기 전의 지출. 예전에 넣은 건들이 여기 해당한다. */
    UNKNOWN("미상", "❔"),
    ;

    companion object {
        fun fromName(name: String?): PaymentMethod =
            entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}
