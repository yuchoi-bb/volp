package com.volp.travelbudget.domain.cardsms

import java.time.LocalDateTime

/**
 * 카드사 결제 문자(및 같은 내용의 앱 알림)를 [CardTransaction]으로 바꾼다.
 *
 * 카드사마다 문구가 다르고 같은 카드사 안에서도 국내·해외·자동결제 형식이 달라, 하나의 큰 정규식 대신
 * 카드사별로 줄 단위 규칙을 나눠 두었다. 형식이 조금 바뀌어도 해당 규칙만 고치면 된다.
 *
 * 문자에는 연도가 없으므로 수신 시각을 기준으로 연도를 추정한다.
 */
object CardMessageParser {

    fun parse(body: String, receivedAt: LocalDateTime, sender: String? = null): CardTransaction? {
        val text = body.replace('\u00A0', ' ').trim()
        if (text.isEmpty()) return null

        // 통신사가 붙이는 머리말과 회신 표시를 떼어내야 이후 규칙이 줄 첫머리부터 맞는다.
        val lines = text.lines()
            .map { it.trim().removePrefix("[Web발신]").trim().removePrefix("RE:").trim() }
            .filter { it.isNotEmpty() }
        val flat = lines.joinToString(" ").replace(Regex("\\s+"), " ")

        // 발신번호를 알면 그 카드사 규칙만 시도하고, 모르면(앱 알림 등) 전부 시도한다.
        val candidates = CardIssuer.fromSender(sender)?.let { listOf(it) } ?: CardIssuer.entries
        for (issuer in candidates) {
            val parsed = when (issuer) {
                CardIssuer.SAMSUNG -> SamsungCardRules.parse(lines, flat, text, receivedAt)
                CardIssuer.SHINHAN -> ShinhanCardRules.parse(lines, flat, text, receivedAt)
                CardIssuer.HANA -> HanaCardRules.parse(lines, text, receivedAt)
            }
            if (parsed != null) return parsed
        }
        return null
    }
}

internal object ParseSupport {

    /** 통화 이름이 한글로 오는 카드사가 있어 코드로 바꿔 준다. */
    private val currencyByKoreanName = mapOf(
        "엔" to "JPY", "엔화" to "JPY",
        "달러" to "USD", "미화" to "USD", "미국달러" to "USD",
        "유로" to "EUR",
        "파운드" to "GBP",
        "위안" to "CNY", "위안화" to "CNY",
        "바트" to "THB",
        "동" to "VND",
        "페소" to "PHP",
        "링깃" to "MYR",
        "루피아" to "IDR",
        "홍콩달러" to "HKD",
        "대만달러" to "TWD",
        "싱가포르달러" to "SGD",
        "호주달러" to "AUD",
        "캐나다달러" to "CAD",
        "프랑" to "CHF",
    )

    /** 통화 표기가 빠진 문자를 대비한 국가코드별 통화. */
    private val currencyByCountry = mapOf(
        "KR" to "KRW", "JP" to "JPY", "US" to "USD", "GU" to "USD",
        "TH" to "THB", "VN" to "VND", "SG" to "SGD", "TW" to "TWD",
        "HK" to "HKD", "CN" to "CNY", "ID" to "IDR", "PH" to "PHP",
        "MY" to "MYR", "AU" to "AUD", "CA" to "CAD", "GB" to "GBP",
        "FR" to "EUR", "IT" to "EUR", "ES" to "EUR", "DE" to "EUR",
        "NL" to "EUR", "CZ" to "CZK", "CH" to "CHF",
    )

    fun currencyOf(token: String?, countryCode: String?): String {
        val name = token?.trim().orEmpty()
        if (name.length == 3 && name.all { it in 'A'..'Z' }) return name
        currencyByKoreanName[name]?.let { return it }
        countryCode?.uppercase()?.let { code -> currencyByCountry[code]?.let { return it } }
        return "KRW"
    }

    fun amountOf(raw: String): Double? =
        raw.replace(",", "").replace(" ", "").toDoubleOrNull()

    fun accumulatedOf(raw: String?): Long? =
        raw?.replace(",", "")?.trim()?.toLongOrNull()

    /**
     * 문자에는 `04/19` 처럼 연도가 없다. 수신 시각의 연도를 먼저 써 보고, 그 날짜가 수신 시각보다
     * 뚜렷하게 미래면(연말에 받은 연초 날짜 등) 한 해 앞으로 돌린다.
     */
    fun dateTimeOf(
        month: Int,
        day: Int,
        hour: Int?,
        minute: Int?,
        receivedAt: LocalDateTime,
    ): LocalDateTime? {
        fun attempt(year: Int): LocalDateTime? = runCatching {
            LocalDateTime.of(year, month, day, hour ?: 0, minute ?: 0)
        }.getOrNull()

        val sameYear = attempt(receivedAt.year)
        if (sameYear != null && !sameYear.isAfter(receivedAt.plusDays(2))) return sameYear
        return attempt(receivedAt.year - 1) ?: sameYear
    }

    fun kindOf(word: String): TransactionKind = when {
        word.contains("거절") -> TransactionKind.DECLINED
        word.contains("취소") -> TransactionKind.CANCEL
        else -> TransactionKind.APPROVAL
    }

    /** 카드사마다 이름 자리에 빈 값이 오기도 한다. */
    fun nameOrNull(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
}
