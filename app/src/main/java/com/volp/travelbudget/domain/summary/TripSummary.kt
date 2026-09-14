package com.volp.travelbudget.domain.summary

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.model.TripStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** 항목 하나의 예산 대비 지출 현황. */
data class CategoryProgress(
    val category: ExpenseCategory,
    val predicted: Long,
    val planned: Long,
    val spent: Long,
) {
    /** 계획보다 더 쓴 금액. 음수면 아직 여유가 있다는 뜻이다. */
    val difference: Long get() = spent - planned

    val isOverBudget: Boolean get() = spent > planned

    /** 진행률(0.0 ~ 1.0 이상). 계획이 0원인 항목에서 지출이 생기면 1로 본다. */
    val ratio: Float
        get() = when {
            planned > 0L -> spent.toFloat() / planned.toFloat()
            spent > 0L -> 1f
            else -> 0f
        }
}

/** 여행 하나의 전체 현황. 화면에서 쓰는 숫자는 모두 여기서 계산한다. */
data class TripSummary(
    val trip: Trip,
    val status: TripStatus,
    val categories: List<CategoryProgress>,
    val totalPredicted: Long,
    val totalPlanned: Long,
    val totalSpent: Long,
    /** 남은 예산. 음수면 예산을 넘겼다. */
    val remaining: Long,
    val elapsedDays: Int,
    val remainingDays: Int,
    /** 항공·숙박을 뺀 하루 평균 지출. 아직 여행이 시작되지 않았으면 0. */
    val dailyAverage: Long,
    /** 남은 기간 동안 하루에 쓸 수 있는 금액. 남은 날이 없으면 null. */
    val dailyAllowance: Long?,
    /** 지금 속도대로 썼을 때의 예상 최종 지출. */
    val projectedTotal: Long,
) {
    val progressRatio: Float
        get() = if (totalPlanned > 0L) totalSpent.toFloat() / totalPlanned.toFloat() else 0f

    val isOverBudget: Boolean get() = totalSpent > totalPlanned

    /** 지금 속도라면 예산을 넘길 것으로 보이는지. */
    val projectedOverBudget: Boolean get() = projectedTotal > totalPlanned
}

object TripSummaries {

    /** 여행 전에 미리 결제하는 경우가 많아 하루 평균 계산에서 빼는 항목. */
    private val UPFRONT_CATEGORIES = setOf(ExpenseCategory.FLIGHT, ExpenseCategory.LODGING)

    fun summarize(
        trip: Trip,
        spentByCategory: Map<ExpenseCategory, Long>,
        today: LocalDate = LocalDate.now(),
    ): TripSummary {
        val categories = ExpenseCategory.entries.map { category ->
            CategoryProgress(
                category = category,
                predicted = trip.predictedFor(category),
                planned = trip.plannedFor(category),
                spent = spentByCategory[category] ?: 0L,
            )
        }

        val totalPlanned = categories.sumOf { it.planned }
        val totalSpent = categories.sumOf { it.spent }

        val elapsedDays = elapsedDays(trip, today)
        val remainingDays = (trip.days - elapsedDays).coerceAtLeast(0)

        val dailyCategories = categories.filterNot { it.category in UPFRONT_CATEGORIES }
        val dailySpent = dailyCategories.sumOf { it.spent }
        val dailyPlanned = dailyCategories.sumOf { it.planned }

        val dailyAverage = if (elapsedDays > 0) dailySpent / elapsedDays else 0L
        val dailyAllowance = if (remainingDays > 0) {
            ((dailyPlanned - dailySpent).coerceAtLeast(0L)) / remainingDays
        } else {
            null
        }

        // 앞으로 쓸 돈 = 하루 경비를 지금 속도대로 이어갈 때의 금액 + 아직 결제하지 않은 항공·숙박 예산
        val unpaidUpfront = categories
            .filter { it.category in UPFRONT_CATEGORIES }
            .sumOf { (it.planned - it.spent).coerceAtLeast(0L) }
        val projectedDaily = if (elapsedDays > 0) {
            dailyAverage * remainingDays
        } else {
            // 아직 시작 전이면 계획한 하루 경비를 그대로 쓴다고 본다.
            (dailyPlanned - dailySpent).coerceAtLeast(0L)
        }
        val projectedTotal = totalSpent + projectedDaily + unpaidUpfront

        return TripSummary(
            trip = trip,
            status = trip.statusOn(today),
            categories = categories,
            totalPredicted = trip.totalPredicted,
            totalPlanned = totalPlanned,
            totalSpent = totalSpent,
            remaining = totalPlanned - totalSpent,
            elapsedDays = elapsedDays,
            remainingDays = remainingDays,
            dailyAverage = dailyAverage,
            dailyAllowance = dailyAllowance,
            projectedTotal = projectedTotal,
        )
    }

    /** 여행이 며칠째인지. 출발 전이면 0, 끝난 뒤면 전체 일수. */
    private fun elapsedDays(trip: Trip, today: LocalDate): Int = when {
        today.isBefore(trip.startDate) -> 0
        today.isAfter(trip.endDate) -> trip.days
        else -> ChronoUnit.DAYS.between(trip.startDate, today).toInt() + 1
    }

    /** 현지 통화 금액을 원화로 바꾼다. */
    fun toKrw(amount: Double, rate: Double): Long = (amount * rate).roundToLong()
}
