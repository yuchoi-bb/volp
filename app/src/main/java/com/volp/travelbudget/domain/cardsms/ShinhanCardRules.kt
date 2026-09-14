package com.volp.travelbudget.domain.cardsms

import java.time.LocalDateTime

/**
 * 신한카드 문자 규칙.
 *
 * 국내 승인
 * ```
 * 신한카드(0088)승인 최*업
 * 5,900원(일시불)04/20 09:09 스타벅스코리
 * 누적456,975원
 * ```
 * 국내 매출취소(시각이 없다)
 * ```
 * 신한카드 (0088) 매출취소 최*업
 * 3,980원  04/11 (주)오아시스
 * ```
 * 해외 승인(통화가 한글로 오고 줄바꿈이 중간에 끼어든다)
 * ```
 * 신한(0088)해외승인 최*업 2,390
 * 엔      (JP)04/19 14:08 DiDi Mobil
 * 누적451,075원
 * ```
 */
internal object ShinhanCardRules {

    private val overseas = Regex(
        """신한(?:카드)?\s*\((\d{4})\)\s*해외(승인|취소|거절)\s*(\S*)\s+([\d,]+(?:\.\d+)?)\s*([가-힣A-Za-z]*)\s*\(([A-Za-z]{2})\)\s*(\d{1,2})/(\d{1,2})\s+(\d{1,2}):(\d{2})\s+(.+?)(?:\s*누적\s*([\d,]+)원)?\s*$""",
    )
    private val domesticHead = Regex("""^신한(?:카드)?\s*\((\d{4})\)\s*(승인|매출취소|취소|거절)\s*(.*)$""")
    private val domesticBody = Regex(
        """^(-?[\d,]+)원\s*(?:\(([^)]*)\))?\s*(\d{1,2})/(\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?\s*(.*)$""",
    )
    private val accumulated = Regex("""누적\s*([\d,]+)원""")

    fun parse(
        lines: List<String>,
        flat: String,
        raw: String,
        receivedAt: LocalDateTime,
    ): CardTransaction? = parseOverseas(flat, raw, receivedAt) ?: parseDomestic(lines, flat, raw, receivedAt)

    private fun parseOverseas(flat: String, raw: String, receivedAt: LocalDateTime): CardTransaction? {
        val groups = overseas.find(flat)?.groupValues ?: return null
        val amount = ParseSupport.amountOf(groups[4]) ?: return null
        val country = groups[6].uppercase()
        return CardTransaction(
            issuer = CardIssuer.SHINHAN,
            cardLabel = groups[1],
            holderName = ParseSupport.nameOrNull(groups[3]),
            kind = ParseSupport.kindOf(groups[2]),
            amount = kotlin.math.abs(amount),
            currencyCode = ParseSupport.currencyOf(groups[5], country),
            countryCode = country,
            merchant = groups[11].trim(),
            occurredAt = ParseSupport.dateTimeOf(
                groups[7].toInt(), groups[8].toInt(), groups[9].toInt(), groups[10].toInt(), receivedAt,
            ) ?: return null,
            paymentPlan = null,
            accumulatedKrw = ParseSupport.accumulatedOf(groups[12].takeIf { it.isNotBlank() }),
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
        val body = lines.drop(headIndex + 1).firstNotNullOfOrNull { domesticBody.find(it) } ?: return null
        val groups = body.groupValues
        val amount = ParseSupport.amountOf(groups[1]) ?: return null

        return CardTransaction(
            issuer = CardIssuer.SHINHAN,
            cardLabel = head[1],
            holderName = ParseSupport.nameOrNull(head[3]),
            kind = if (amount < 0) TransactionKind.CANCEL else ParseSupport.kindOf(head[2]),
            amount = kotlin.math.abs(amount),
            currencyCode = "KRW",
            countryCode = "KR",
            merchant = groups[7].trim(),
            occurredAt = ParseSupport.dateTimeOf(
                groups[3].toInt(),
                groups[4].toInt(),
                groups[5].toIntOrNull(),
                groups[6].toIntOrNull(),
                receivedAt,
            ) ?: return null,
            paymentPlan = ParseSupport.nameOrNull(groups[2]),
            accumulatedKrw = ParseSupport.accumulatedOf(accumulated.find(flat)?.groupValues?.get(1)),
            rawBody = raw,
        )
    }
}
