package com.volp.travelbudget.domain.stats

import com.volp.travelbudget.domain.model.ExpenseCategory
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 다녀온 여행 한 건의 결산.
 *
 * 예측이 얼마였고 실제로 얼마를 썼는지, 그리고 하루에 얼마꼴이었는지를 함께 들고 있다.
 * 다음 여행 예산을 잡을 때 사람이 실제로 참고하는 값은 총액보다 **하루 얼마**다.
 */
data class PastTrip(
    val tripId: Long,
    val title: String,
    val destinationName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val days: Int,
    val travelers: Int,
    val predictedTotal: Long,
    val actualTotal: Long,
    val byCategory: Map<ExpenseCategory, Long>,
) {
    /** 예측보다 더 썼으면 양수. */
    val difference: Long get() = actualTotal - predictedTotal

    val overPredicted: Boolean get() = difference > 0L

    /** 예측 대비 실제 비율. 예측이 없으면 null. */
    val ratio: Double?
        get() = if (predictedTotal > 0L) actualTotal.toDouble() / predictedTotal.toDouble() else null

    /** 하루에 쓴 돈. 항공·숙박처럼 미리 낸 것도 포함한 총액 기준이다. */
    val perDay: Long get() = if (days > 0) actualTotal / days else actualTotal

    /** 한 사람이 하루에 쓴 돈. 인원이 다른 여행끼리 견줄 때 쓰는 값이다. */
    val perPersonPerDay: Long
        get() = if (days > 0 && travelers > 0) actualTotal / (days * travelers) else 0L

    /** 현지에서 쓴 돈. 항공·숙박은 대개 미리 결제하므로 따로 본다. */
    val onSiteTotal: Long
        get() = byCategory.filterKeys { it !in UPFRONT }.values.sum()

    val onSitePerDay: Long get() = if (days > 0) onSiteTotal / days else onSiteTotal

    private companion object {
        val UPFRONT = setOf(ExpenseCategory.FLIGHT, ExpenseCategory.LODGING)
    }
}

/**
 * 다녀온 여행 전체의 결산.
 *
 * 여행 하나만 보면 그 여행이 유별났는지 알 수 없다. 여러 번을 겹쳐 놓아야 내 씀씀이의 모양이 보인다.
 */
data class PastTripsSummary(
    val trips: List<PastTrip>,
) {
    val isEmpty: Boolean get() = trips.isEmpty()

    val tripCount: Int get() = trips.size

    val totalActual: Long get() = trips.sumOf { it.actualTotal }

    val totalPredicted: Long get() = trips.sumOf { it.predictedTotal }

    val totalDays: Int get() = trips.sumOf { it.days }

    val difference: Long get() = totalActual - totalPredicted

    /** 모든 여행을 통틀어 하루에 쓴 돈. */
    val perDay: Long get() = if (totalDays > 0) totalActual / totalDays else 0L

    /** 한 사람이 하루에 쓴 돈의 평균. 인원이 다른 여행을 고르게 섞는다. */
    val perPersonPerDay: Long
        get() {
            val personDays = trips.sumOf { it.days.toLong() * it.travelers.coerceAtLeast(1) }
            return if (personDays > 0L) (totalActual / personDays) else 0L
        }

    /**
     * 예측이 얼마나 맞았는지(0~1).
     *
     * 1이면 예측과 실제가 같았다는 뜻이다. 넘치든 모자라든 똑같이 빗나간 것으로 센다.
     */
    val accuracy: Double?
        get() {
            if (totalPredicted <= 0L) return null
            val error = abs(difference).toDouble() / totalPredicted.toDouble()
            return (1.0 - error).coerceIn(0.0, 1.0)
        }

    /** 항목별 실제 합계. 무엇에 돈이 몰렸는지 본다. */
    val byCategory: Map<ExpenseCategory, Long>
        get() = ExpenseCategory.entries
            .associateWith { category -> trips.sumOf { it.byCategory[category] ?: 0L } }
            .filterValues { it > 0L }

    /** 항목별 비중(0~1). */
    val categoryShare: Map<ExpenseCategory, Double>
        get() {
            val total = totalActual
            if (total <= 0L) return emptyMap()
            return byCategory.mapValues { (_, amount) -> amount.toDouble() / total.toDouble() }
        }

    /** 가장 돈이 많이 든 여행. */
    val mostExpensive: PastTrip? get() = trips.maxByOrNull { it.actualTotal }

    /** 하루 씀씀이가 가장 컸던 여행. 총액은 기간에 좌우되므로 이쪽이 더 견줄 만하다. */
    val busiestPerDay: PastTrip? get() = trips.maxByOrNull { it.perDay }
}

object PastTripReports {

    /**
     * 끝난 여행만 추려 최근 순으로.
     *
     * 아직 진행 중인 여행은 지출이 덜 들어와 실제보다 적게 보인다. 그 값을 평균에 섞으면
     * 다음 여행 예산을 낮게 잡게 된다.
     */
    fun finished(trips: List<PastTrip>, today: LocalDate): PastTripsSummary =
        PastTripsSummary(
            trips.filter { it.endDate.isBefore(today) && it.actualTotal > 0L }
                .sortedByDescending { it.endDate },
        )

    /**
     * 다음 여행에 하루 얼마를 잡으면 되는지.
     *
     * 지난 여행의 한 사람 하루 씀씀이에 인원과 날수를 곱한다. 항공·숙박은 목적지가 정하는 값이라
     * 여기에 넣지 않는다.
     */
    fun suggestOnSiteBudget(summary: PastTripsSummary, days: Int, travelers: Int): Long? {
        if (summary.isEmpty) return null

        val personDays = summary.trips.sumOf { it.days.toLong() * it.travelers.coerceAtLeast(1) }
        if (personDays <= 0L) return null

        val onSite = summary.trips.sumOf { it.onSiteTotal }
        val perPersonPerDay = onSite.toDouble() / personDays.toDouble()

        return (perPersonPerDay * days.coerceAtLeast(1) * travelers.coerceAtLeast(1)).roundToLong()
    }
}
