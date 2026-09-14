package com.volp.travelbudget.domain.budget

import com.volp.travelbudget.domain.model.ExpenseCategory
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToLong

/** 끝난 여행 하나의 예측과 실제. 보정 계수는 이 둘의 차이에서 나온다. */
data class TripOutcome(
    val endDate: LocalDate,
    val predicted: Map<ExpenseCategory, Long>,
    val actual: Map<ExpenseCategory, Long>,
)

/**
 * 지난 여행에서 배운 내 씀씀이.
 *
 * [factors]는 항목별로 "예측에 곱할 값"이다. 1.2면 이 사람은 그 항목에서 늘 예측보다 20% 더 쓴다는 뜻이다.
 */
data class SpendingProfile(
    val factors: Map<ExpenseCategory, Double> = emptyMap(),
    /** 계수를 뽑는 데 쓴 여행 수. 적을수록 계수를 조심해서 쓴다. */
    val tripCount: Int = 0,
) {
    val isEmpty: Boolean get() = factors.isEmpty()

    fun factorFor(category: ExpenseCategory): Double = factors[category] ?: 1.0

    /** 예측에 내 씀씀이를 입힌다. */
    fun apply(prediction: Map<ExpenseCategory, Long>): Map<ExpenseCategory, Long> {
        if (isEmpty) return prediction
        return prediction.mapValues { (category, amount) ->
            SpendingProfiles.round(amount * factorFor(category))
        }
    }

    /** 눈에 띄게 어긋났던 항목만. 화면에서 "왜 이 금액인지" 설명하는 데 쓴다. */
    fun notableAdjustments(): List<Pair<ExpenseCategory, Double>> =
        factors.filter { (_, factor) -> kotlin.math.abs(factor - 1.0) >= NOTABLE }
            .toList()
            .sortedByDescending { kotlin.math.abs(it.second - 1.0) }

    private companion object {
        const val NOTABLE = 0.1
    }
}

/**
 * 끝난 여행들을 보고 다음 예측을 내 쪽으로 당긴다.
 *
 * 도시·스타일 표만으로 낸 예측은 "보통 사람"의 값이다. 같은 도쿄를 가도 누구는 밥에 두 배를 쓰고
 * 누구는 절반만 쓴다. 이미 다녀온 여행이 그 답을 들고 있으므로 굳이 매번 같은 오차를 반복할 이유가 없다.
 *
 * 다만 한 번의 유별난 여행이 다음 예측을 망치면 안 되므로 세 가지 제동을 건다.
 * 최근 여행에 더 큰 무게를 주고([DECAY]), 여행 수가 적으면 1.0 쪽으로 당기며([DAMPING]),
 * 계수 자체를 [MIN_FACTOR]~[MAX_FACTOR] 안에 가둔다.
 */
object SpendingProfiles {

    /** 한 여행 전으로 갈 때마다 곱해지는 무게. 최근 여행이 더 많이 반영된다. */
    private const val DECAY = 0.7

    /** 여행 수가 적을 때 계수를 1.0 쪽으로 당기는 세기. */
    private const val DAMPING = 2.0

    private const val MIN_FACTOR = 0.5
    private const val MAX_FACTOR = 2.0

    /** 한 여행에서 나온 비율의 허용 범위. 이보다 벗어난 것은 그만큼만 인정한다. */
    private const val MIN_RATIO = 0.3
    private const val MAX_RATIO = 3.0

    /**
     * 이보다 적게 예측한 항목은 보지 않는다.
     *
     * 예측 3천 원짜리 항목에 만 원을 쓰면 비율이 3.3이 되지만, 그것은 씀씀이가 아니라 반올림의 문제다.
     */
    private const val MIN_PREDICTED = 30_000L

    /** 최근 이 만큼의 여행만 본다. 너무 오래된 여행은 지금의 나와 다르다. */
    private const val MAX_HISTORY = 8

    private const val ROUNDING_UNIT = 1_000L

    /**
     * 항공은 배우지 않는다.
     *
     * 항공권 값은 목적지와 예매 시점이 정하는 것이라, 지난 여행에서 싸게 샀다는 사실이 다음 여행
     * 항공권에 대해 알려 주는 것이 거의 없다. 여기서 계수를 잡으면 배우는 게 아니라 착각하는 것이다.
     */
    private val SKIPPED = setOf(ExpenseCategory.FLIGHT)

    fun learn(history: List<TripOutcome>): SpendingProfile {
        val recent = history.sortedByDescending { it.endDate }.take(MAX_HISTORY)
        if (recent.isEmpty()) return SpendingProfile()

        val factors = ExpenseCategory.entries.mapNotNull { category ->
            if (category in SKIPPED) return@mapNotNull null
            factorFor(category, recent)?.let { category to it }
        }.toMap()

        return SpendingProfile(factors = factors, tripCount = recent.size)
    }

    private fun factorFor(category: ExpenseCategory, history: List<TripOutcome>): Double? {
        var weightedSum = 0.0
        var weightTotal = 0.0
        var samples = 0

        history.forEachIndexed { index, outcome ->
            val predicted = outcome.predicted[category] ?: 0L
            val actual = outcome.actual[category] ?: 0L
            if (predicted < MIN_PREDICTED || actual <= 0L) return@forEachIndexed

            val ratio = (actual.toDouble() / predicted.toDouble()).coerceIn(MIN_RATIO, MAX_RATIO)
            val weight = Math.pow(DECAY, index.toDouble())
            weightedSum += ratio * weight
            weightTotal += weight
            samples++
        }

        if (samples == 0) return null

        val average = weightedSum / weightTotal
        // 표본이 적을수록 1.0에 가깝게 둔다. 한 번 다녀온 것으로 단정하지 않는다.
        val confidence = samples / (samples + DAMPING)
        return (1.0 + (average - 1.0) * confidence).coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    internal fun round(value: Double): Long {
        if (value <= 0.0) return 0L
        val rounded = (value / ROUNDING_UNIT).roundToLong() * ROUNDING_UNIT
        return max(ROUNDING_UNIT, rounded)
    }
}
