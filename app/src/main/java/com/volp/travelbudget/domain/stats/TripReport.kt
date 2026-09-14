package com.volp.travelbudget.domain.stats

import com.volp.travelbudget.domain.model.Trip
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 여행이 끝난 뒤 한 화면에 담아 보거나 남에게 보낼 요약을 만든다.
 */
object TripReport {

    private val amountFormat = DecimalFormat("#,###")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val shortDateFormat = DateTimeFormatter.ofPattern("M.d")

    fun compose(trip: Trip, stats: TripStatistics, plannedTotal: Long): String = buildString {
        appendLine("${trip.title} (${trip.destinationName})")
        appendLine(
            "${trip.startDate.format(dateFormat)} - ${trip.endDate.format(dateFormat)} · " +
                "${trip.nights}박 ${trip.days}일 · ${trip.travelers}명",
        )
        appendLine()
        appendLine("총 지출 ${won(stats.total)}")

        if (plannedTotal > 0L) {
            val diff = stats.total - plannedTotal
            val percent = diff.toDouble() / plannedTotal * 100
            appendLine(
                "예산 ${won(plannedTotal)} 대비 " +
                    String.format(Locale.KOREA, "%+.1f%%", percent) +
                    " (${signedWon(diff)})",
            )
        }

        appendLine("하루 평균 ${won(stats.dailyAverage)} · 1인당 ${won(stats.perPerson)}")

        stats.topCategory?.let { top ->
            appendLine(
                "가장 많이 쓴 항목: ${top.category.label} ${won(top.amountKrw)} " +
                    "(${(top.ratio * 100).toInt()}%)",
            )
        }
        stats.busiestDay?.let { day ->
            val weekday = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
            appendLine(
                "가장 많이 쓴 날: ${day.date.format(shortDateFormat)} ($weekday) ${won(day.amountKrw)}",
            )
        }

        stats.currencyTotals
            .filter { it.currencyCode != "KRW" }
            .forEach { currency ->
                appendLine(
                    "현지 결제: ${amountFormat.format(currency.amount)} ${currency.currencyCode} " +
                        "(${won(currency.amountKrw)})",
                )
            }

        appendLine("기록한 지출 ${stats.expenseCount}건")
    }.trimEnd()

    private fun won(amount: Long) = "${amountFormat.format(amount)}원"

    private fun signedWon(amount: Long) =
        if (amount >= 0) "+${won(amount)}" else "-${won(-amount)}"
}
