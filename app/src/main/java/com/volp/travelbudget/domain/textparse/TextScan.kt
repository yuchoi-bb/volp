package com.volp.travelbudget.domain.textparse

import java.time.LocalDate
import java.time.LocalTime

/**
 * 문자나 복사해 온 글에서 값을 줍는 연장.
 *
 * 쇼핑몰과 항공사마다 문구가 제각각이라 서식을 외우는 대신 **열쇳말 옆의 값**을 집는 방식으로 읽는다.
 * 구매와 예약 두 갈래가 같은 연장을 쓰므로 여기에 모아 둔다.
 */
object TextScan {

    /** 열쇳말에서 값을 찾아 볼 거리. */
    const val WINDOW = 40

    /** 앞머리의 발신 꼬리표를 떼고 다듬는다. */
    fun clean(text: String): String = text
        .replace("[Web발신]", " ")
        .replace("[국제발신]", " ")
        .replace(' ', ' ')
        .trim()

    /** 문자 앞머리의 `[쿠팡]` 같은 꼬리표. */
    fun leadingTag(text: String): String =
        Regex("^\\s*\\[([^\\]]{1,20})\\]").find(text)?.groupValues?.get(1)?.trim().orEmpty()

    fun stripLeadingTag(line: String): String =
        line.replace(Regex("^\\s*\\[[^\\]]{1,20}\\]\\s*"), "")

    /** `열쇳말 : 값` 꼴에서 값 쪽을 잘라 온다. */
    fun valueAfter(text: String, keyword: String, window: Int = WINDOW): String? {
        val index = text.indexOf(keyword)
        if (index < 0) return null
        return text.substring(index + keyword.length)
            .trimStart(' ', ':', '=', '-', '\t')
            .lineSequence()
            .firstOrNull()
            ?.take(window)
    }

    fun valueAfterAny(text: String, keywords: List<String>, window: Int = WINDOW): String? {
        keywords.forEach { keyword ->
            valueAfter(text, keyword, window)?.let { if (it.isNotBlank()) return it }
        }
        return null
    }

    // ---- 날짜 ----

    private val FULL_DATE = Regex("(20[0-9]{2})[-./년]\\s*([0-9]{1,2})[-./월]\\s*([0-9]{1,2})")
    private val MONTH_DAY =
        Regex("(?<![0-9])([0-9]{1,2})\\s*(?:월\\s*([0-9]{1,2})\\s*일|[/.]\\s*([0-9]{1,2}))(?![0-9])")

    /** 해가 없는 날짜를 오늘 기준 가까운 쪽으로 볼 때, 이만큼 지난 것까지는 올해로 본다. */
    private const val PAST_TOLERANCE_DAYS = 30L

    /**
     * 열쇳말 가까이에 있는 날짜.
     *
     * 열쇳말 뒤를 먼저 보고 없으면 앞을 본다. `도착 예정 10/12`와 `10/12 도착 예정`을 모두 읽기 위해서다.
     */
    fun dateNear(
        text: String,
        keywords: List<String>,
        today: LocalDate,
        window: Int = WINDOW,
    ): LocalDate? {
        keywords.forEach { keyword ->
            var from = text.indexOf(keyword)
            while (from >= 0) {
                val tail = text.substring(from + keyword.length).take(window)
                dateIn(tail, today)?.let { return it }

                val head = text.substring(maxOf(0, from - window), from)
                dateIn(head, today)?.let { return it }

                from = text.indexOf(keyword, from + keyword.length)
            }
        }
        return null
    }

    fun dateIn(text: String, today: LocalDate): LocalDate? {
        FULL_DATE.findAll(text).forEach { match ->
            val (year, month, day) = match.destructured
            safeDate(year.toInt(), month.toInt(), day.toInt())?.let { return it }
        }
        MONTH_DAY.findAll(text).forEach { match ->
            val month = match.groupValues[1].toIntOrNull()
            val day = (match.groupValues[2].ifBlank { match.groupValues[3] }).toIntOrNull()
            // `12.500` 같은 금액이 12월 500일로 읽히지 않게 달과 날의 범위를 먼저 본다.
            val candidate = if (month == null || day == null) null else safeDate(today.year, month, day)
            if (candidate != null) {
                return if (candidate.isBefore(today.minusDays(PAST_TOLERANCE_DAYS))) {
                    candidate.plusYears(1)
                } else {
                    candidate
                }
            }
        }
        return null
    }

    fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    // ---- 시각 ----

    private val CLOCK = Regex("(?<![0-9])([0-9]{1,2})\\s*:\\s*([0-9]{2})(?![0-9])")
    private val KOREAN_CLOCK = Regex("(오전|오후)?\\s*([0-9]{1,2})\\s*시\\s*(?:([0-9]{1,2})\\s*분)?")

    /** `09:05`, `오후 3시 20분` 같은 시각. */
    fun timeIn(text: String): LocalTime? {
        CLOCK.findAll(text).forEach { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[2].toIntOrNull() ?: return@forEach
            safeTime(hour, minute)?.let { return it }
        }
        KOREAN_CLOCK.findAll(text).forEach { match ->
            val meridiem = match.groupValues[1]
            val rawHour = match.groupValues[2].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[3].toIntOrNull() ?: 0
            val hour = when {
                meridiem == "오후" && rawHour < 12 -> rawHour + 12
                meridiem == "오전" && rawHour == 12 -> 0
                else -> rawHour
            }
            safeTime(hour, minute)?.let { return it }
        }
        return null
    }

    /** 열쇳말 가까이에 있는 시각. */
    fun timeNear(text: String, keywords: List<String>, window: Int = WINDOW): LocalTime? {
        keywords.forEach { keyword ->
            val index = text.indexOf(keyword)
            if (index >= 0) {
                timeIn(text.substring(index + keyword.length).take(window))?.let { return it }
            }
        }
        return null
    }

    private fun safeTime(hour: Int, minute: Int): LocalTime? =
        runCatching { LocalTime.of(hour, minute) }.getOrNull()

    // ---- 번호 ----

    val CODE = Regex("[A-Za-z0-9][A-Za-z0-9-]{5,25}")

    fun codeNear(text: String, keywords: List<String>, pattern: Regex = CODE): String {
        keywords.forEach { keyword ->
            valueAfter(text, keyword)?.let { tail ->
                pattern.find(tail)?.value?.let { return it }
            }
        }
        return ""
    }

    // ---- 금액 ----

    private val KRW = Regex("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})\\s*(?:원|KRW|₩)")
    private val KRW_SYMBOL = Regex("₩\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,})")
    private val FOREIGN = Regex(
        "(USD|EUR|JPY|CNY|GBP|AUD|CAD|CHF|HKD|SGD|THB|VND|TWD)\\s*([0-9,]+(?:\\.[0-9]+)?)" +
            "|([0-9,]+(?:\\.[0-9]+)?)\\s*(USD|EUR|JPY|CNY|GBP|AUD|CAD|CHF|HKD|SGD|THB|VND|TWD)" +
            "|\\$\\s*([0-9,]+(?:\\.[0-9]+)?)",
    )

    /** 글 안에서 가장 큰 원화 금액. 할인 전 가격과 적립금에 섞여 있어도 결제액이 대개 가장 크다. */
    fun krwIn(text: String): Long? =
        (KRW.findAll(text) + KRW_SYMBOL.findAll(text))
            .mapNotNull { match -> match.groupValues.drop(1).firstOrNull { it.isNotBlank() } }
            .mapNotNull { it.replace(",", "").toLongOrNull() }
            .toList()
            .maxOrNull()

    /** 외화 금액. 통화 코드와 금액을 함께 돌려준다. */
    fun foreignIn(text: String): Pair<String, Double>? {
        val match = FOREIGN.find(text) ?: return null
        val groups = match.groupValues
        val currency = when {
            groups[1].isNotBlank() -> groups[1]
            groups[4].isNotBlank() -> groups[4]
            else -> "USD"
        }
        val raw = listOf(groups[2], groups[3], groups[5]).firstOrNull { it.isNotBlank() } ?: return null
        val amount = raw.replace(",", "").toDoubleOrNull() ?: return null
        return currency.uppercase() to amount
    }
}
