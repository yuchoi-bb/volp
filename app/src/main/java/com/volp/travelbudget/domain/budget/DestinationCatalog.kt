package com.volp.travelbudget.domain.budget

import com.volp.travelbudget.domain.model.Destination
import com.volp.travelbudget.domain.model.Region

/**
 * 자주 가는 목적지의 기준 단가표.
 *
 * 숫자는 한국 출발 기준의 대략적인 시세이며 [com.volp.travelbudget.domain.model.TravelStyle.STANDARD]
 * 여행을 가정한다. 예측값은 어디까지나 출발점이고, 사용자가 여행별로 예산을 조정할 수 있다.
 */
object DestinationCatalog {

    const val CUSTOM_KEY_PREFIX = "custom:"

    val destinations: List<Destination> = listOf(
        // 국내
        Destination("jeju", "제주", Region.DOMESTIC, "KRW", 120_000, 130_000, 55_000, 20_000, 30_000, 25_000),
        Destination("busan", "부산", Region.DOMESTIC, "KRW", 60_000, 110_000, 50_000, 12_000, 25_000, 25_000),
        Destination("gangneung", "강릉", Region.DOMESTIC, "KRW", 40_000, 120_000, 50_000, 15_000, 25_000, 20_000),
        // 일본
        Destination("tokyo", "도쿄", Region.JAPAN, "JPY", 350_000, 160_000, 60_000, 15_000, 30_000, 30_000),
        Destination("osaka", "오사카", Region.JAPAN, "JPY", 320_000, 140_000, 55_000, 13_000, 28_000, 30_000),
        Destination("fukuoka", "후쿠오카", Region.JAPAN, "JPY", 250_000, 130_000, 50_000, 10_000, 25_000, 25_000),
        Destination("sapporo", "삿포로", Region.JAPAN, "JPY", 330_000, 150_000, 55_000, 14_000, 32_000, 25_000),
        // 동남아
        Destination("bangkok", "방콕", Region.SOUTHEAST_ASIA, "THB", 400_000, 90_000, 35_000, 10_000, 25_000, 20_000),
        Destination("danang", "다낭", Region.SOUTHEAST_ASIA, "VND", 380_000, 80_000, 30_000, 9_000, 22_000, 18_000),
        Destination("singapore", "싱가포르", Region.SOUTHEAST_ASIA, "SGD", 550_000, 200_000, 60_000, 15_000, 40_000, 30_000),
        Destination("bali", "발리", Region.SOUTHEAST_ASIA, "IDR", 700_000, 120_000, 40_000, 15_000, 30_000, 25_000),
        Destination("cebu", "세부", Region.SOUTHEAST_ASIA, "PHP", 420_000, 110_000, 35_000, 12_000, 28_000, 18_000),
        // 중화권
        Destination("taipei", "타이베이", Region.GREATER_CHINA, "TWD", 300_000, 110_000, 40_000, 10_000, 22_000, 22_000),
        Destination("hongkong", "홍콩", Region.GREATER_CHINA, "HKD", 380_000, 180_000, 55_000, 14_000, 35_000, 35_000),
        Destination("shanghai", "상하이", Region.GREATER_CHINA, "CNY", 350_000, 140_000, 45_000, 12_000, 28_000, 25_000),
        // 대양주·태평양
        Destination("guam", "괌", Region.OCEANIA_PACIFIC, "USD", 600_000, 220_000, 70_000, 18_000, 45_000, 30_000),
        Destination("saipan", "사이판", Region.OCEANIA_PACIFIC, "USD", 620_000, 200_000, 65_000, 18_000, 42_000, 25_000),
        Destination("sydney", "시드니", Region.OCEANIA_PACIFIC, "AUD", 1_300_000, 250_000, 80_000, 20_000, 45_000, 35_000),
        // 유럽
        Destination("paris", "파리", Region.EUROPE, "EUR", 1_300_000, 250_000, 80_000, 20_000, 45_000, 40_000),
        Destination("rome", "로마", Region.EUROPE, "EUR", 1_250_000, 220_000, 70_000, 18_000, 40_000, 35_000),
        Destination("barcelona", "바르셀로나", Region.EUROPE, "EUR", 1_300_000, 210_000, 70_000, 18_000, 40_000, 35_000),
        Destination("london", "런던", Region.EUROPE, "GBP", 1_400_000, 280_000, 85_000, 25_000, 50_000, 45_000),
        Destination("prague", "프라하", Region.EUROPE, "CZK", 1_250_000, 170_000, 55_000, 14_000, 32_000, 28_000),
        // 미주
        Destination("newyork", "뉴욕", Region.AMERICAS, "USD", 1_500_000, 350_000, 95_000, 25_000, 55_000, 50_000),
        Destination("losangeles", "로스앤젤레스", Region.AMERICAS, "USD", 1_400_000, 300_000, 85_000, 28_000, 50_000, 45_000),
        Destination("honolulu", "하와이", Region.AMERICAS, "USD", 1_400_000, 300_000, 85_000, 22_000, 50_000, 40_000),
        Destination("vancouver", "밴쿠버", Region.AMERICAS, "CAD", 1_350_000, 260_000, 80_000, 20_000, 45_000, 38_000),
    )

    private val byKey: Map<String, Destination> = destinations.associateBy { it.key }

    fun byRegion(): Map<Region, List<Destination>> =
        destinations.groupBy { it.region }

    /**
     * 목적지 키로 단가표를 찾는다. `custom:EUROPE` 형태의 키는 해당 권역 평균 단가표를 돌려준다.
     */
    fun find(key: String): Destination? = when {
        key.startsWith(CUSTOM_KEY_PREFIX) -> regionAverage(Region.fromName(key.removePrefix(CUSTOM_KEY_PREFIX)))
        else -> byKey[key]
    }

    fun customKey(region: Region): String = CUSTOM_KEY_PREFIX + region.name

    /**
     * 목록에 없는 도시를 위한 권역 평균 단가표.
     */
    fun regionAverage(region: Region): Destination {
        val members = destinations.filter { it.region == region }
        require(members.isNotEmpty()) { "권역 ${region.name}에 등록된 목적지가 없다" }
        fun avg(selector: (Destination) -> Long): Long =
            members.sumOf(selector) / members.size
        return Destination(
            key = customKey(region),
            name = region.label,
            region = region,
            currencyCode = members.groupingBy { it.currencyCode }.eachCount()
                .maxByOrNull { it.value }?.key ?: "USD",
            flightPerPerson = avg { it.flightPerPerson },
            lodgingPerNight = avg { it.lodgingPerNight },
            foodPerDay = avg { it.foodPerDay },
            transportPerDay = avg { it.transportPerDay },
            activityPerDay = avg { it.activityPerDay },
            shoppingPerDay = avg { it.shoppingPerDay },
        )
    }
}
