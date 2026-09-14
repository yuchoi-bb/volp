package com.volp.travelbudget.domain.travel

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 위경도 한 점. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

object Geo {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * 두 점 사이의 직선 거리(미터).
     *
     * 실제 이동 거리는 길을 따라가므로 이보다 길다. 이동수단을 고르는 데는 직선 거리로 충분하고,
     * 정확한 경로는 지도 앱에 넘긴다.
     */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h).coerceIn(0.0, 1.0))
    }

    /** 길을 따라 걷는 거리는 직선보다 길다. 안내용 추정에 쓰는 보정값. */
    const val ROAD_DETOUR_FACTOR = 1.3
}
