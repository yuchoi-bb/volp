package com.volp.travelbudget.domain.cardsms

import java.time.LocalDateTime

/**
 * 하나카드 문자 규칙. 다른 카드사와 달리 `항목 값` 표 형태로 온다.
 *
 * ```
 * 승인
 * 금액      30,000원
 * 카드      MG+ 하나7*4*
 * 손님명    최*업
 * 거래종류  신용
 * 거래구분  일시불
 * 사용처    구글페이먼트코리아
 * 거래시간  08/20 15:05
 * 누적금액  126,155원
 * ```
 */
internal object HanaCardRules {

    private val fieldKeys = listOf(
        "누적금액", "거래시간", "거래구분", "거래종류", "손님명", "사용처", "가맹점", "금액", "카드",
    )
    private val krwAmount = Regex("""^(-?[\d,]+)\s*원$""")
    private val foreignAmount = Regex("""^([A-Z]{3})\s*(-?[\d,]+(?:\.\d+)?)$""")
    private val occurredAt = Regex("""^(\d{1,2})/(\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?$""")

    fun parse(lines: List<String>, raw: String, receivedAt: LocalDateTime): CardTransaction? {
        val fields = readFields(lines)
        val amountText = fields["금액"] ?: return null
        val whenText = fields["거래시간"] ?: return null
        if (fields["카드"] == null && fields["사용처"] == null) return null

        val when_ = occurredAt.find(whenText)?.groupValues ?: return null
        val krw = krwAmount.find(amountText)?.groupValues
        val foreign = if (krw == null) foreignAmount.find(amountText)?.groupValues else null
        val amount = ParseSupport.amountOf(krw?.get(1) ?: foreign?.get(2) ?: return null) ?: return null

        val cancelled = lines.any { it.trim() in CANCEL_HEADERS } || amount < 0
        val declined = lines.any { it.contains("거절") }

        return CardTransaction(
            issuer = CardIssuer.HANA,
            cardLabel = fields["카드"].orEmpty(),
            holderName = ParseSupport.nameOrNull(fields["손님명"]),
            kind = when {
                declined -> TransactionKind.DECLINED
                cancelled -> TransactionKind.CANCEL
                else -> TransactionKind.APPROVAL
            },
            amount = kotlin.math.abs(amount),
            currencyCode = if (krw != null) "KRW" else ParseSupport.currencyOf(foreign?.get(1), null),
            countryCode = if (krw != null) "KR" else null,
            merchant = (fields["사용처"] ?: fields["가맹점"]).orEmpty(),
            occurredAt = ParseSupport.dateTimeOf(
                when_[1].toInt(),
                when_[2].toInt(),
                when_.getOrNull(3)?.toIntOrNull(),
                when_.getOrNull(4)?.toIntOrNull(),
                receivedAt,
            ) ?: return null,
            paymentPlan = ParseSupport.nameOrNull(fields["거래구분"]),
            accumulatedKrw = ParseSupport.accumulatedOf(
                fields["누적금액"]?.removeSuffix("원")?.trim(),
            ),
            rawBody = raw,
        )
    }

    private val CANCEL_HEADERS = setOf("취소", "승인취소", "매출취소")

    /** `항목  값` 줄을 항목별로 모은다. 값에 공백이 있어도 항목 이름만 떼어내면 된다. */
    private fun readFields(lines: List<String>): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        for (line in lines) {
            val key = fieldKeys.firstOrNull { line.startsWith(it) } ?: continue
            val value = line.removePrefix(key).trim()
            if (value.isNotEmpty()) fields.putIfAbsent(key, value)
        }
        return fields
    }
}
