package com.volp.travelbudget.domain.weather

import java.time.Duration
import java.time.LocalDateTime

/** 15분 단위 강수 예보 한 칸. */
data class PrecipitationSlot(
    val at: LocalDateTime,
    /** 강수 확률(%). 모르면 null. */
    val probability: Int?,
    val millimeters: Double,
)

/** 곧 내릴 비 한 건. */
data class RainAlert(
    val startsAt: LocalDateTime,
    val probability: Int?,
    val millimeters: Double,
    val minutesAway: Int,
)

/**
 * 지금 있는 곳에 곧 비가 오는지 본다.
 *
 * 하루 예보로는 "오늘 비"까지만 알 수 있어 우산을 챙길 시점을 놓친다. 그래서 15분 단위
 * 예보를 받아 한 시간 앞만 본다.
 */
object RainWatch {

    /** 이 확률을 넘으면 알린다. */
    const val DEFAULT_PROBABILITY_THRESHOLD = 60

    /** 확률이 없어도 이만큼 내린다는 예보면 알린다. */
    private const val MILLIMETER_THRESHOLD = 0.3

    /**
     * @param withinMinutes 얼마 앞까지 볼지
     * @return 알릴 만한 비. 없으면 null.
     */
    fun evaluate(
        slots: List<PrecipitationSlot>,
        now: LocalDateTime,
        withinMinutes: Long = 60,
        probabilityThreshold: Int = DEFAULT_PROBABILITY_THRESHOLD,
    ): RainAlert? {
        val limit = now.plusMinutes(withinMinutes)

        val slot = slots
            .filter { !it.at.isBefore(now) && !it.at.isAfter(limit) }
            .sortedBy { it.at }
            .firstOrNull { rains(it, probabilityThreshold) }
            ?: return null

        return RainAlert(
            startsAt = slot.at,
            probability = slot.probability,
            millimeters = slot.millimeters,
            minutesAway = Duration.between(now, slot.at).toMinutes().toInt().coerceAtLeast(0),
        )
    }

    private fun rains(slot: PrecipitationSlot, probabilityThreshold: Int): Boolean =
        (slot.probability ?: 0) >= probabilityThreshold || slot.millimeters >= MILLIMETER_THRESHOLD
}
