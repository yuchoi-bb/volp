package com.volp.travelbudget.domain.travel

import com.volp.travelbudget.domain.model.Region
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class TransportMode(val label: String, val emoji: String) {
    WALK("도보", "🚶"),
    TRANSIT("대중교통", "🚇"),
    TAXI("택시", "🚕"),
    INTERCITY("시외 이동", "🚄"),
}

/**
 * 다음 장소까지 어떻게 갈지에 대한 안내 한 건.
 *
 * @param minutes 예상 소요 시간(분)
 * @param estimatedCostKrw 예상 요금(원). 도보처럼 돈이 들지 않으면 null.
 */
data class TransportSuggestion(
    val mode: TransportMode,
    val distanceMeters: Double,
    val minutes: Int,
    val estimatedCostKrw: Long?,
    val note: String,
)

/**
 * 두 장소 사이의 거리를 보고 이동수단을 고른다.
 *
 * 길찾기 API를 쓰지 않고 거리와 도시별 요금 수준만으로 어림한다. 정확한 경로와 시간표는
 * 지도 앱이 훨씬 잘하므로, 여기서는 "걸어갈까 택시 탈까"를 판단할 정도만 알려 주고
 * 자세한 것은 지도 앱으로 넘긴다.
 */
object RouteAdvisor {

    /** 도보 속도(m/분). 관광 중에는 느리게 걷는다. */
    private const val WALK_SPEED = 70.0

    /** 대중교통 평균 속도(m/분). 환승과 대기를 포함한 값. */
    private const val TRANSIT_SPEED = 280.0

    /** 시내 택시 평균 속도(m/분). */
    private const val TAXI_SPEED = 350.0

    /** 기차·버스 등 도시 간 이동 평균 속도(m/분). */
    private const val INTERCITY_SPEED = 1_100.0

    private const val WALKABLE_METERS = 1_200.0
    private const val TRANSIT_LIMIT_METERS = 30_000.0

    /** 권역별 택시 요금 수준(원). 정확한 금액이 아니라 규모를 가늠하는 값이다. */
    private data class TaxiRate(val base: Long, val perKm: Long)

    private val taxiRates = mapOf(
        Region.DOMESTIC to TaxiRate(4_800, 1_200),
        Region.JAPAN to TaxiRate(5_000, 3_300),
        Region.SOUTHEAST_ASIA to TaxiRate(1_500, 800),
        Region.GREATER_CHINA to TaxiRate(2_500, 1_200),
        Region.OCEANIA_PACIFIC to TaxiRate(5_000, 3_000),
        Region.EUROPE to TaxiRate(5_500, 2_800),
        Region.AMERICAS to TaxiRate(5_000, 2_600),
    )

    /** 권역별 대중교통 1회 요금(원). */
    private val transitFares = mapOf(
        Region.DOMESTIC to 1_500L,
        Region.JAPAN to 2_200L,
        Region.SOUTHEAST_ASIA to 900L,
        Region.GREATER_CHINA to 1_200L,
        Region.OCEANIA_PACIFIC to 3_500L,
        Region.EUROPE to 3_000L,
        Region.AMERICAS to 3_500L,
    )

    /**
     * @param straightMeters 두 장소 사이의 직선 거리
     */
    fun suggest(straightMeters: Double, region: Region): TransportSuggestion {
        // 길을 따라가면 직선보다 멀다.
        val meters = straightMeters * Geo.ROAD_DETOUR_FACTOR

        return when {
            meters <= WALKABLE_METERS -> TransportSuggestion(
                mode = TransportMode.WALK,
                distanceMeters = meters,
                minutes = (meters / WALK_SPEED).roundToInt().coerceAtLeast(1),
                estimatedCostKrw = null,
                note = "걸어갈 만한 거리다",
            )

            meters <= TRANSIT_LIMIT_METERS -> TransportSuggestion(
                mode = TransportMode.TRANSIT,
                distanceMeters = meters,
                // 역까지 걷고 기다리는 시간을 더한다.
                minutes = (meters / TRANSIT_SPEED).roundToInt() + 10,
                estimatedCostKrw = transitFares[region] ?: 2_000L,
                note = "택시로 가면 ${taxiFare(meters, region)?.let { "약 ${it / 1_000}천원" } ?: "요금 미상"}",
            )

            meters <= 80_000.0 -> TransportSuggestion(
                mode = TransportMode.TAXI,
                distanceMeters = meters,
                minutes = (meters / TAXI_SPEED).roundToInt(),
                estimatedCostKrw = taxiFare(meters, region),
                note = "대중교통이 있으면 더 싸다",
            )

            else -> TransportSuggestion(
                mode = TransportMode.INTERCITY,
                distanceMeters = meters,
                minutes = (meters / INTERCITY_SPEED).roundToInt(),
                estimatedCostKrw = null,
                note = "기차나 시외버스를 미리 알아보는 게 좋다",
            )
        }
    }

    private fun taxiFare(meters: Double, region: Region): Long? {
        val rate = taxiRates[region] ?: return null
        val km = meters / 1_000.0
        // 기본요금에 포함된 거리는 뺀다.
        val billableKm = (km - 1.5).coerceAtLeast(0.0)
        return (rate.base + rate.perKm * billableKm).roundToLong()
    }
}
