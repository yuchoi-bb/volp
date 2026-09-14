package com.volp.travelbudget.domain.cardsms

import java.time.LocalDateTime

/** 문자를 보내는 카드사. 발신번호는 광고 문자를 걸러내는 데 쓴다. */
enum class CardIssuer(val label: String, val senderNumbers: Set<String>) {
    SAMSUNG("삼성카드", setOf("15888900", "0220008100")),
    SHINHAN("신한카드", setOf("15447000", "15447200")),
    HANA("하나카드", setOf("18001111")),
    ;

    companion object {
        /** 발신번호로 카드사를 찾는다. 국가번호(+82)와 하이픈은 무시한다. */
        fun fromSender(sender: String?): CardIssuer? {
            val digits = sender?.filter(Char::isDigit)?.let {
                if (it.startsWith("82")) "0" + it.removePrefix("82") else it
            } ?: return null
            return entries.firstOrNull { issuer -> issuer.senderNumbers.any { digits.endsWith(it) } }
        }
    }
}

enum class TransactionKind(val label: String) {
    /** 결제 승인. 가계부에 더한다. */
    APPROVAL("승인"),

    /** 매출취소·환불. 가계부에서 뺀다. */
    CANCEL("취소"),

    /** 승인거절. 실제로 돈이 나가지 않았으므로 기록하지 않는다. */
    DECLINED("승인거절"),
}

/**
 * 카드 문자 한 건에서 뽑아낸 결제 내역.
 *
 * 금액([amount])은 항상 양수이고, 취소 여부는 [kind]로 구분한다.
 * 해외 결제는 원화 환산액이 문자에 없으므로 [currencyCode]가 KRW가 아니면 앱에서 환율로 환산해야 한다.
 */
data class CardTransaction(
    val issuer: CardIssuer,
    /** 카드 식별값. 삼성·신한은 끝 네 자리, 하나는 `MG+ 하나7*4*` 같은 표기. */
    val cardLabel: String,
    val holderName: String?,
    val kind: TransactionKind,
    val amount: Double,
    val currencyCode: String,
    /** 해외 결제 문자에 들어 있는 국가코드(예: JP). 여행지를 자동으로 맞추는 데 쓴다. */
    val countryCode: String?,
    val merchant: String,
    val occurredAt: LocalDateTime,
    /** 일시불, 할부, 자동결제 등. */
    val paymentPlan: String?,
    /** 문자에 찍힌 이 카드의 누적 사용액(원). */
    val accumulatedKrw: Long?,
    val rawBody: String,
) {
    val isOverseas: Boolean get() = currencyCode != "KRW"

    /** 가계부에 반영할 부호 있는 금액. 취소는 음수다. */
    val signedAmount: Double get() = if (kind == TransactionKind.CANCEL) -amount else amount

    val isRecordable: Boolean get() = kind != TransactionKind.DECLINED
}
