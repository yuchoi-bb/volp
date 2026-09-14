package com.volp.travelbudget.domain.travel

/**
 * 목적지의 대표 좌표. 날씨 예보를 받고 첫 동선을 그리는 기준점으로 쓴다.
 *
 * 목록에 없는 도시는 기기의 지오코더로 찾는다.
 */
object CityCoordinates {

    private val coordinates = mapOf(
        "jeju" to GeoPoint(33.4996, 126.5312),
        "busan" to GeoPoint(35.1796, 129.0756),
        "gangneung" to GeoPoint(37.7519, 128.8761),
        "tokyo" to GeoPoint(35.6762, 139.6503),
        "osaka" to GeoPoint(34.6937, 135.5023),
        "fukuoka" to GeoPoint(33.5904, 130.4017),
        "sapporo" to GeoPoint(43.0618, 141.3545),
        "bangkok" to GeoPoint(13.7563, 100.5018),
        "danang" to GeoPoint(16.0544, 108.2022),
        "singapore" to GeoPoint(1.3521, 103.8198),
        "bali" to GeoPoint(-8.4095, 115.1889),
        "cebu" to GeoPoint(10.3157, 123.8854),
        "taipei" to GeoPoint(25.0330, 121.5654),
        "hongkong" to GeoPoint(22.3193, 114.1694),
        "shanghai" to GeoPoint(31.2304, 121.4737),
        "guam" to GeoPoint(13.4443, 144.7937),
        "saipan" to GeoPoint(15.1850, 145.7467),
        "sydney" to GeoPoint(-33.8688, 151.2093),
        "paris" to GeoPoint(48.8566, 2.3522),
        "rome" to GeoPoint(41.9028, 12.4964),
        "barcelona" to GeoPoint(41.3851, 2.1734),
        "london" to GeoPoint(51.5074, -0.1278),
        "prague" to GeoPoint(50.0755, 14.4378),
        "newyork" to GeoPoint(40.7128, -74.0060),
        "losangeles" to GeoPoint(34.0522, -118.2437),
        "honolulu" to GeoPoint(21.3069, -157.8583),
        "vancouver" to GeoPoint(49.2827, -123.1207),
    )

    fun find(destinationKey: String): GeoPoint? = coordinates[destinationKey]
}
