package com.volp.travelbudget.util

import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val amountFormat = DecimalFormat("#,###")
private val foreignFormat = DecimalFormat("#,##0.##")
private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val shortDateFormat = DateTimeFormatter.ofPattern("M.d")

/** 1234000 -> "1,234,000원" */
fun formatKrw(amount: Long): String = "${amountFormat.format(amount)}원"

/** 부호를 함께 보여 준다. 예산 대비 차액처럼 방향이 중요한 값에 쓴다. */
fun formatSignedKrw(amount: Long): String = when {
    amount > 0L -> "+${formatKrw(amount)}"
    amount < 0L -> "-${formatKrw(abs(amount))}"
    else -> formatKrw(0L)
}

/** 큰 금액을 카드에 짧게 넣을 때 쓴다. 1234000 -> "123.4만원" */
fun formatKrwShort(amount: Long): String {
    val absolute = abs(amount)
    val sign = if (amount < 0L) "-" else ""
    return when {
        absolute >= 100_000_000L -> "$sign${trimZero(absolute / 100_000_000.0)}억원"
        absolute >= 10_000L -> "$sign${trimZero(absolute / 10_000.0)}만원"
        else -> formatKrw(amount)
    }
}

private fun trimZero(value: Double): String = foreignFormat.format(value)

fun formatForeign(amount: Double, currencyCode: String): String =
    "${foreignFormat.format(amount)} $currencyCode"

fun formatDate(date: LocalDate): String = date.format(dateFormat)

/** "9.14 (월)" */
fun formatDateWithDay(date: LocalDate): String {
    val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    return "${date.format(shortDateFormat)} ($day)"
}

/** "2026.09.14 - 2026.09.18 · 4박 5일" */
fun formatDateRange(start: LocalDate, end: LocalDate, nights: Int): String =
    "${formatDate(start)} - ${formatDate(end)} · ${nights}박 ${nights + 1}일"

/** 파일 크기를 사람이 읽는 단위로. 업데이트 안내에 쓴다. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.KOREA, "%.1fMB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.KOREA, "%.0fKB", bytes / 1024.0)
    else -> "${bytes}B"
}
