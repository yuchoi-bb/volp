package com.volp.travelbudget.domain.cardsms

import java.time.LocalDateTime

/**
 * 삼성카드 문자 규칙.
 *
 * 국내 승인
 * ```
 * 삼성7414승인 최*업
 * 50,000원 일시불
 * 05/09 13:35 에이치디현대오일
 * 누적6,865,947원
 * ```
 * 해외 승인(원화 환산액이 없다)
 * ```
 * 삼성7414해외승인 최*업
 * JPY 1,200
 * 04/19 10:43 OSAKAMUSEUMOFHISTORYAI
 * ```
 * 해외 승인거절
 * ```
 * [삼성카드]해외7414 04/19 12:17
 * SAKAE-KOTSU JPY 2,100 승인거절
 * ```
 * 자동결제(시각이 없고, 취소는 금액이 음수로 온다)
 * ```
 * [삼성카드]7414 자동결제 6/12 접수 최*리
 * 한율초등학교/방과후수강 166,480원
 * ```
 */
internal object SamsungCardRules {

    private val overseasHead = Regex("""^삼성(\d{4})해외(승인|취소)\s*(.*)$""")
    private val overseasAmount = Regex("""^([A-Z]{3})\s*(-?[\d,]+(?:\.\d+)?)$""")
    private val domesticHead = Regex("""^삼성(\d{4})(승인|취소)\s*(.*)$""")
    private val domesticAmount = Regex("""^(-?[\d,]+)원\s*(.*)$""")
    private val whenLine = Regex("""^(\d{1,2})/(\d{1,2})\s+(\d{1,2}):(\d{2})\s+(.+)$""")
    private val accumulated = Regex("""누적\s*([\d,]+)원""")
    private val declined = Regex(
        """\[삼성카드]\s*해외(\d{4})\s+(\d{1,2})/(\d{1,2})\s+(\d{1,2}):(\d{2})\s+(.+?)\s+([A-Z]{3})\s+([\d,]+(?:\.\d+)?)\s*승인거절""",
    )
    private val autoPayment = Regex(
        """\[삼성카드]\s*(\d{4})\s*자동결제\s*(\d{1,2})/(\d{1,2})\s*(접수|취소)\s*(\S*)\s+(.+?)\s+(-?[\d,]+)원""",
    )

    fun parse(
        lines: List<String>,
        flat: String,
        raw: String,
        receivedAt: LocalDateTime,
    ): CardTransaction? =
        parseDeclined(flat, raw, receivedAt)
            ?: parseAutoPayment(flat, raw, receivedAt)
            ?: parseOverseas(lines, flat, raw, receivedAt)
            ?: parseDomestic(lines, flat, raw, receivedAt)

    private fun parseDeclined(flat: String, raw: String, receivedAt: LocalDateTime): CardTransaction? {
        val (card, month, day, hour, minute, merchant, currency, amount) =
            declined.find(flat)?.destructured ?: return null
        return CardTransaction(
            issuer = CardIssuer.SAMSUNG,
            cardLabel = card,
            holderName = null,
            kind = TransactionKind.DECLINED,
            amount = ParseSupport.amountOf(amount) ?: return null,
            currencyCode = ParseSupport.currencyOf(currency, null),
            countryCode = null,
            merchant = merchant.trim(),
            occurredAt = ParseSupport.dateTimeOf(
                month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), receivedAt,
            ) ?: return null,
            paymentPlan = null,
            accumulatedKrw = null,
            rawBody = raw,
        )
    }

    private fun parseAutoPayment(flat: String, raw: String, receivedAt: LocalDateTime): CardTransaction? {
        val match = autoPayment.find(flat) ?: return null
        val groups = match.groupValues
        val amount = ParseSupport.amountOf(groups[7]) ?: return null
        return CardTransaction(
            issuer = CardIssuer.SAMSUNG,
            cardLabel = groups[1],
            holderName = ParseSupport.nameOrNull(groups[5]),
            // 자동결제는 취소일 때 금액이 음수로 오기도 하고 문구로 오기도 한다.
            kind = if (groups[4] == "취소" || amount < 0) TransactionKind.CANCEL else TransactionKind.APPROVAL,
            amount = kotlin.math.abs(amount),
            currencyCode = "KRW",
            countryCode = "KR",
            merchant = groups[6].trim(),
            occurredAt = ParseSupport.dateTimeOf(
                groups[2].toInt(), groups[3].toInt(), null, null, receivedAt,
            ) ?: return null,
            paymentPlan = "자동결제",
            accumulatedKrw = null,
            rawBody = raw,
        )
    }

    private fun parseOverseas(
        lines: List<String>,
        flat: String,
        raw: String,
        receivedAt: LocalDateTime,
    ): CardTransaction? {
        val headIndex = lines.indexOfFirst { overseasHead.matches(it) }
        if (headIndex < 0) return null
        val head = overseasHead.find(lines[headIndex])!!.groupValues
        val rest = lines.drop(headIndex + 1)

        val amountMatch = rest.firstNotNullOfOrNull { overseasAmount.find(it) } ?: return null
        val whenMatch = rest.firstNotNullOfOrNull { whenLine.find(it) } ?: return null
        val amount = ParseSupport.amountOf(amountMatch.groupValues[2]) ?: return null

        return CardTransaction(
            issuer = CardIssuer.SAMSUNG,
            cardLabel = head[1],
            holderName = ParseSupport.nameOrNull(head[3]),
            kind = if (head[2] == "취소" || amount < 0) TransactionKind.CANCEL else TransactionKind.APPROVAL,
            amount = kotlin.math.abs(amount),
            currencyCode = ParseSupport.currencyOf(amountMatch.groupValues[1], null),
            countryCode = null,
            merchant = whenMatch.groupValues[5].trim(),
            occurredAt = ParseSupport.dateTimeOf(
                whenMatch.groupValues[1].toInt(),
                whenMatch.groupValues[2].toInt(),
                whenMatch.groupValues[3].toInt(),
                whenMatch.groupValues[4].toInt(),
                receivedAt,
            ) ?: return null,
            paymentPlan = null,
            accumulatedKrw = ParseSupport.accumulatedOf(accumulated.find(flat)?.groupValues?.get(1)),
            rawBody = raw,
        )
    }

    private fun parseDomestic(
        lines: List<String>,
        flat: String,
        raw: String,
        receivedAt: LocalDateTime,
    ): CardTransaction? {
        val headIndex = lines.indexOfFirst { domesticHead.matches(it) }
        if (headIndex < 0) return null
        val head = domesticHead.find(lines[headIndex])!!.groupValues
        val rest = lines.drop(headIndex + 1)

        val amountMatch = rest.firstNotNullOfOrNull { domesticAmount.find(it) } ?: return null
        val whenMatch = rest.firstNotNullOfOrNull { whenLine.find(it) } ?: return null
        val amount = ParseSupport.amountOf(amountMatch.groupValues[1]) ?: return null

        return CardTransaction(
            issuer = CardIssuer.SAMSUNG,
            cardLabel = head[1],
            holderName = ParseSupport.nameOrNull(head[3]),
            kind = if (head[2] == "취소" || amount < 0) TransactionKind.CANCEL else TransactionKind.APPROVAL,
            amount = kotlin.math.abs(amount),
            currencyCode = "KRW",
            countryCode = "KR",
            merchant = whenMatch.groupValues[5].trim(),
            occurredAt = ParseSupport.dateTimeOf(
                whenMatch.groupValues[1].toInt(),
                whenMatch.groupValues[2].toInt(),
                whenMatch.groupValues[3].toInt(),
                whenMatch.groupValues[4].toInt(),
                receivedAt,
            ) ?: return null,
            paymentPlan = ParseSupport.nameOrNull(amountMatch.groupValues[2]),
            accumulatedKrw = ParseSupport.accumulatedOf(accumulated.find(flat)?.groupValues?.get(1)),
            rawBody = raw,
        )
    }
}
