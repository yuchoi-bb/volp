package com.volp.travelbudget.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 여행 한 건. 예측 예산([predictedBudget])과 사용자가 조정한 계획 예산([plannedBudget])을 함께 들고 있다.
 * 실제 지출은 [Expense]로 따로 쌓인다.
 */
data class Trip(
    val id: Long = 0L,
    val title: String,
    val destinationKey: String,
    val destinationName: String,
    val region: Region,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travelers: Int,
    val style: TravelStyle,
    val includeFlight: Boolean,
    val currencyCode: String,
    val exchangeRate: Double,
    /** 앱이 계산한 예상 경비. 사용자가 예산을 고쳐도 비교를 위해 그대로 남는다. */
    val predictedBudget: Map<ExpenseCategory, Long>,
    /** 실제로 쓰기로 한 예산. 처음에는 [predictedBudget]과 같다. */
    val plannedBudget: Map<ExpenseCategory, Long>,
    /** 카드 명세서에 찍힌 해외 결제 실제 청구 총액(원). 아직 넣지 않았으면 null. */
    val billedTotalKrw: Long? = null,
    /** 승인액 대비 실제 청구액 비율. 보정 전에는 1.0. */
    val settlementFactor: Double = 1.0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val nights: Int
        get() = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(0)

    val days: Int
        get() = nights + 1

    val totalPredicted: Long
        get() = predictedBudget.values.sum()

    val totalPlanned: Long
        get() = plannedBudget.values.sum()

    fun plannedFor(category: ExpenseCategory): Long = plannedBudget[category] ?: 0L

    fun predictedFor(category: ExpenseCategory): Long = predictedBudget[category] ?: 0L

    /** 오늘 기준 여행 상태. */
    fun statusOn(today: LocalDate): TripStatus = when {
        today.isBefore(startDate) -> TripStatus.UPCOMING
        today.isAfter(endDate) -> TripStatus.FINISHED
        else -> TripStatus.ONGOING
    }

    /** 출발까지 남은 일수. 이미 시작했으면 0 이하. */
    fun daysUntilStart(today: LocalDate): Long = ChronoUnit.DAYS.between(today, startDate)
}

enum class TripStatus(val label: String) {
    UPCOMING("출발 전"),
    ONGOING("여행 중"),
    FINISHED("여행 완료"),
}
