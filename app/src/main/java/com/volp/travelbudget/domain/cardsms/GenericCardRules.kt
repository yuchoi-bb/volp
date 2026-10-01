package com.volp.travelbudget.domain.cardsms

import java.time.LocalDateTime

/**
 * 전용 규칙이 없는 카드사의 국내 결제 문자를 읽는다.
 *
 * 카드사마다 문구가 다르지만 국내 승인 문자는 거의 같은 뼈대를 갖는다.
 * ```
 * [Web발신]
 * KB국민카드 승인 홍*동
 * 12,345원 일시불
 * 09/30 14:23
 * 스타벅스강남점
 * ```
 * 카드사 이름, 승인·취소, `12,345원`, `09/30 14:23`, 그리고 마지막에 가맹점이다. 줄바꿈 자리와
 * 괄호는 제각각이라 줄 단위로 외우는 대신 이 다섯 조각을 각각 집는다.
 *
 * 아무 문자나 읽지는 않는다. **카드사 이름과 승인·취소 말이 둘 다 있을 때만** 읽는다. 금액이 적힌
 * 문자는 세상에 널려 있고, 그것까지 지출로 올리면 미확인함이 쓸모없어진다.
 *
 * 해외 결제는 건드리지 않는다. 카드사마다 통화 표기가 너무 달라 잘못 읽으면 금액이 엉뚱해진다.
 * 그때는 전용 규칙을 따로 쓰는 편이 낫다.
 */
internal object GenericCardRules {

    /** 문자에 적히는 이름과 카드사. 긴 이름을 먼저 본다 — 'NH농협'이 '농협'보다 앞이다. */
    private val issuerNames: List<Pair<String, CardIssuer>> = listOf(
        "KB국민" to CardIssuer.KOOKMIN,
        "국민카드" to CardIssuer.KOOKMIN,
        "KB카드" to CardIssuer.KOOKMIN,
        "현대카드" to CardIssuer.HYUNDAI,
        "롯데카드" to CardIssuer.LOTTE,
        "우리카드" to CardIssuer.WOORI,
        "BC카드" to CardIssuer.BC,
        "비씨카드" to CardIssuer.BC,
        "NH농협" to CardIssuer.NONGHYUP,
        "NH카드" to CardIssuer.NONGHYUP,
        "농협카드" to CardIssuer.NONGHYUP,
        "카카오뱅크" to CardIssuer.KAKAO,
        "카카오페이" to CardIssuer.KAKAO,
        "토스뱅크" to CardIssuer.TOSS,
        "토스카드" to CardIssuer.TOSS,
        "IBK기업" to CardIssuer.IBK,
        "기업은행" to CardIssuer.IBK,
        "씨티카드" to CardIssuer.CITI,
        "수협카드" to CardIssuer.SUHYUP,
        "삼성카드" to CardIssuer.SAMSUNG,
        "신한카드" to CardIssuer.SHINHAN,
        "하나카드" to CardIssuer.HANA,
    )

    private val kindWord = Regex("""(승인취소|매출취소|승인거절|거절|취소|승인)""")
    private val amount = Regex("""(-?[\d,]+)\s*원""")
    private val cardLabel = Regex("""[(（]\s*(\d{3,4})\s*[)）]|카드\s*(\d{4})""")
    private val holder = Regex("""([가-힣]\*+[가-힣]?)""")
    private val date = Regex("""(?<![\d:])(\d{1,2})/(\d{1,2})(?![\d/])""")
    private val time = Regex("""(?<![\d])(\d{1,2}):(\d{2})(?![\d])""")
    private val plan = Regex("""(일시불|할부|\d{1,2}개월|자동결제|정기결제)""")
    private val accumulated = Regex("""누적\s*([\d,]+)\s*원""")

    fun parse(
        lines: List<String>,
        flat: String,
        raw: String,
        receivedAt: LocalDateTime,
    ): CardTransaction? {
        // 해외 결제는 전용 규칙에 맡긴다.
        if (flat.contains("해외")) return null

        val issuer = issuerNames.firstOrNull { flat.contains(it.first, ignoreCase = true) }?.second
            ?: return null
        val kind = kindWord.find(flat)?.value ?: return null

        val amountMatch = amount.find(flat) ?: return null
        val paid = ParseSupport.amountOf(amountMatch.groupValues[1]) ?: return null
        if (paid == 0.0) return null

        val dateMatch = date.find(flat) ?: return null
        val timeMatch = time.find(flat, startIndex = dateMatch.range.last)

        val occurredAt = ParseSupport.dateTimeOf(
            month = dateMatch.groupValues[1].toInt(),
            day = dateMatch.groupValues[2].toInt(),
            hour = timeMatch?.groupValues?.get(1)?.toInt(),
            minute = timeMatch?.groupValues?.get(2)?.toInt(),
            receivedAt = receivedAt,
        ) ?: return null

        return CardTransaction(
            issuer = issuer,
            cardLabel = cardLabel.find(flat)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }.orEmpty(),
            holderName = holder.find(flat)?.value,
            kind = ParseSupport.kindOf(kind),
            amount = kotlin.math.abs(paid),
            currencyCode = "KRW",
            countryCode = null,
            merchant = merchantOf(lines, flat, timeMatch?.range?.last ?: dateMatch.range.last),
            occurredAt = occurredAt,
            paymentPlan = plan.find(flat)?.value,
            accumulatedKrw = ParseSupport.accumulatedOf(accumulated.find(flat)?.groupValues?.get(1)),
            rawBody = raw,
        )
    }

    /**
     * 가맹점은 날짜·시각 뒤에 온다.
     *
     * 그 자리가 비어 있으면(가맹점을 줄 맨 끝에 따로 적는 카드사가 있다) 마지막 줄을 쓴다.
     */
    private fun merchantOf(lines: List<String>, flat: String, after: Int): String {
        val tail = flat.substring((after + 1).coerceAtMost(flat.length))
            .replace(accumulated, " ")
            .replace(plan, " ")
            .trim(' ', '-', '·', ',', '/')
            .trim()

        if (tail.isNotBlank()) return tail

        return lines.lastOrNull { line ->
            line.isNotBlank() &&
                !kindWord.containsMatchIn(line) &&
                !amount.containsMatchIn(line) &&
                !date.containsMatchIn(line)
        }.orEmpty().trim()
    }
}
