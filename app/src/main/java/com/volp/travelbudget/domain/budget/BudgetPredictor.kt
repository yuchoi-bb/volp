package com.volp.travelbudget.domain.budget

import com.volp.travelbudget.domain.model.Destination
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.TravelStyle
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * 여행 조건을 받아 항목별 예상 경비를 계산한다.
 */
object BudgetPredictor {

    /** 예상하지 못한 지출에 대비해 다른 항목 합계에 얹는 예비비 비율. */
    private const val BUFFER_RATIO = 0.08

    /** 성인 몇 명이 객실 하나를 함께 쓰는지. */
    private const val GUESTS_PER_ROOM = 2

    /** 금액을 보기 좋게 다듬는 단위. */
    private const val ROUNDING_UNIT = 1_000L

    data class Input(
        val destination: Destination,
        val nights: Int,
        val travelers: Int,
        val style: TravelStyle,
        val includeFlight: Boolean = true,
    )

    /**
     * 항목별 예상 경비를 계산한다. 모든 항목이 키로 포함되며 금액 단위는 원이다.
     *
     * 숙박은 객실 단위(성인 2명당 1객실)로, 나머지 하루 경비는 인원 × 일수로 계산한다.
     * 여행 일수는 `숙박 수 + 1`이다. 당일치기(0박)도 하루로 친다.
     */
    fun predict(input: Input): Map<ExpenseCategory, Long> {
        val nights = max(0, input.nights)
        val travelers = max(1, input.travelers)
        val days = nights + 1
        val rooms = (travelers + GUESTS_PER_ROOM - 1) / GUESTS_PER_ROOM

        val destination = input.destination
        val daily = input.style.dailyMultiplier
        // 현지 교통비는 스타일에 따라 하루 경비만큼 벌어지지 않아 배율을 절반만 반영한다.
        val transportMultiplier = 1.0 + (daily - 1.0) * 0.5

        val flight = if (input.includeFlight) {
            destination.flightPerPerson * travelers * input.style.flightMultiplier
        } else {
            0.0
        }
        val lodging = destination.lodgingPerNight.toDouble() * rooms * nights * daily
        val food = destination.foodPerDay.toDouble() * travelers * days * daily
        val transport = destination.transportPerDay.toDouble() * travelers * days * transportMultiplier
        val activity = destination.activityPerDay.toDouble() * travelers * days * daily
        val shopping = destination.shoppingPerDay.toDouble() * travelers * days * daily

        val core = linkedMapOf(
            ExpenseCategory.FLIGHT to round(flight),
            ExpenseCategory.LODGING to round(lodging),
            ExpenseCategory.FOOD to round(food),
            ExpenseCategory.TRANSPORT to round(transport),
            ExpenseCategory.ACTIVITY to round(activity),
            ExpenseCategory.SHOPPING to round(shopping),
        )
        val buffer = round(core.values.sum() * BUFFER_RATIO)

        return core + (ExpenseCategory.ETC to buffer)
    }

    private fun round(value: Double): Long {
        if (value <= 0.0) return 0L
        val rounded = (value / ROUNDING_UNIT).roundToLong() * ROUNDING_UNIT
        // 0원이 아닌 항목이 반올림 때문에 0원이 되지는 않게 한다.
        return max(ROUNDING_UNIT, rounded)
    }
}
