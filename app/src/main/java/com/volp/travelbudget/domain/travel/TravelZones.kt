package com.volp.travelbudget.domain.travel

import com.volp.travelbudget.domain.model.Region
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** 지금 두 곳의 시각. [offsetMinutes]는 현지가 한국보다 얼마나 앞선지. */
data class LocalClock(
    val local: LocalDateTime,
    val home: LocalDateTime,
    val offsetMinutes: Long,
) {
    val sameAsHome: Boolean get() = offsetMinutes == 0L

    /** `+1시간`, `-7시간`, `+8시간 30분`. */
    val offsetLabel: String
        get() {
            if (sameAsHome) return "한국과 같음"
            val sign = if (offsetMinutes > 0) "+" else "-"
            val absolute = kotlin.math.abs(offsetMinutes)
            val hours = absolute / 60
            val minutes = absolute % 60
            return if (minutes == 0L) "$sign${hours}시간" else "$sign${hours}시간 ${minutes}분"
        }
}

/**
 * 목적지의 시간대.
 *
 * 여행 중에 시각을 잘못 읽는 일은 대개 시차에서 온다. 항공편 시각이 현지 시각인지 한국 시각인지
 * 헷갈리면 비행기를 놓친다. 그래서 여행지 시각과 한국 시각을 나란히 보여 주는 데만 쓴다.
 */
object TravelZones {

    private const val HOME = "Asia/Seoul"

    private val byDestination = mapOf(
        "jeju" to HOME,
        "busan" to HOME,
        "gangneung" to HOME,
        "tokyo" to "Asia/Tokyo",
        "osaka" to "Asia/Tokyo",
        "fukuoka" to "Asia/Tokyo",
        "sapporo" to "Asia/Tokyo",
        "bangkok" to "Asia/Bangkok",
        "danang" to "Asia/Ho_Chi_Minh",
        "singapore" to "Asia/Singapore",
        "bali" to "Asia/Makassar",
        "cebu" to "Asia/Manila",
        "taipei" to "Asia/Taipei",
        "hongkong" to "Asia/Hong_Kong",
        "shanghai" to "Asia/Shanghai",
        "guam" to "Pacific/Guam",
        "saipan" to "Pacific/Saipan",
        "sydney" to "Australia/Sydney",
        "paris" to "Europe/Paris",
        "rome" to "Europe/Rome",
        "barcelona" to "Europe/Madrid",
        "london" to "Europe/London",
        "prague" to "Europe/Prague",
        "newyork" to "America/New_York",
        "losangeles" to "America/Los_Angeles",
        "honolulu" to "Pacific/Honolulu",
        "vancouver" to "America/Vancouver",
    )

    private val byRegion = mapOf(
        Region.DOMESTIC to HOME,
        Region.JAPAN to "Asia/Tokyo",
        Region.SOUTHEAST_ASIA to "Asia/Bangkok",
        Region.GREATER_CHINA to "Asia/Shanghai",
        Region.EUROPE to "Europe/Paris",
        Region.AMERICAS to "America/Los_Angeles",
        Region.OCEANIA_PACIFIC to "Australia/Sydney",
    )

    fun zoneOf(destinationKey: String, region: Region): ZoneId? {
        val id = byDestination[destinationKey] ?: byRegion[region] ?: return null
        return runCatching { ZoneId.of(id) }.getOrNull()
    }

    /**
     * 지금 현지 시각과 한국 시각.
     *
     * 시차가 없으면 굳이 두 시각을 늘어놓을 이유가 없으므로 화면에서 [LocalClock.sameAsHome]을 본다.
     */
    fun clock(destinationKey: String, region: Region, now: ZonedDateTime): LocalClock? {
        val zone = zoneOf(destinationKey, region) ?: return null
        val home = ZoneId.of(HOME)

        val localNow = now.withZoneSameInstant(zone)
        val homeNow = now.withZoneSameInstant(home)
        val offset = Duration.between(
            homeNow.toLocalDateTime(),
            localNow.toLocalDateTime(),
        ).toMinutes()

        return LocalClock(
            local = localNow.toLocalDateTime(),
            home = homeNow.toLocalDateTime(),
            offsetMinutes = offset,
        )
    }
}
