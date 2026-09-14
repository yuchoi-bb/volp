package com.volp.travelbudget.domain.travel

import com.volp.travelbudget.domain.model.Region

/**
 * 나라별로 챙겨야 할 것이 달라지는 정보.
 *
 * 한국(220V, C·F형)에서 출발하는 것을 전제로 어댑터가 필요한지 판단한다.
 */
data class CountryInfo(
    val code: String,
    val name: String,
    val voltage: Int,
    val plugTypes: String,
    /** 한국에서 쓰던 기기를 그대로 꽂을 수 있는지. */
    val plugCompatibleWithKorea: Boolean,
    /** 수돗물을 그냥 마시기 어려운 곳인지. */
    val tapWaterSafe: Boolean,
    /** 현금을 많이 쓰는 곳인지. */
    val cashHeavy: Boolean,
)

object CountryDirectory {

    val KOREA = CountryInfo("KR", "한국", 220, "C·F형", true, tapWaterSafe = true, cashHeavy = false)

    private val countries = listOf(
        KOREA,
        CountryInfo("JP", "일본", 100, "A형", false, tapWaterSafe = true, cashHeavy = true),
        CountryInfo("TW", "대만", 110, "A·B형", false, tapWaterSafe = false, cashHeavy = true),
        CountryInfo("HK", "홍콩", 220, "G형", false, tapWaterSafe = false, cashHeavy = false),
        CountryInfo("CN", "중국", 220, "A·C·I형", false, tapWaterSafe = false, cashHeavy = false),
        CountryInfo("TH", "태국", 220, "A·B·C형", false, tapWaterSafe = false, cashHeavy = true),
        CountryInfo("VN", "베트남", 220, "A·C형", false, tapWaterSafe = false, cashHeavy = true),
        CountryInfo("SG", "싱가포르", 230, "G형", false, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("ID", "인도네시아", 230, "C·F형", true, tapWaterSafe = false, cashHeavy = true),
        CountryInfo("PH", "필리핀", 220, "A·B·C형", false, tapWaterSafe = false, cashHeavy = true),
        CountryInfo("US", "미국", 120, "A·B형", false, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("CA", "캐나다", 120, "A·B형", false, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("AU", "호주", 230, "I형", false, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("GB", "영국", 230, "G형", false, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("FR", "프랑스", 230, "C·E형", true, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("IT", "이탈리아", 230, "C·F·L형", true, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("ES", "스페인", 230, "C·F형", true, tapWaterSafe = true, cashHeavy = false),
        CountryInfo("CZ", "체코", 230, "C·E형", true, tapWaterSafe = true, cashHeavy = false),
    )

    private val byCode = countries.associateBy { it.code }

    /** 목적지 키로 나라를 찾는다. 목록에 없으면 권역을 대표하는 나라를 쓴다. */
    private val countryByDestination = mapOf(
        "jeju" to "KR", "busan" to "KR", "gangneung" to "KR",
        "tokyo" to "JP", "osaka" to "JP", "fukuoka" to "JP", "sapporo" to "JP",
        "bangkok" to "TH", "danang" to "VN", "singapore" to "SG", "bali" to "ID", "cebu" to "PH",
        "taipei" to "TW", "hongkong" to "HK", "shanghai" to "CN",
        "guam" to "US", "saipan" to "US", "sydney" to "AU",
        "paris" to "FR", "rome" to "IT", "barcelona" to "ES", "london" to "GB", "prague" to "CZ",
        "newyork" to "US", "losangeles" to "US", "honolulu" to "US", "vancouver" to "CA",
    )

    private val fallbackByRegion = mapOf(
        Region.DOMESTIC to "KR",
        Region.JAPAN to "JP",
        Region.SOUTHEAST_ASIA to "TH",
        Region.GREATER_CHINA to "TW",
        Region.OCEANIA_PACIFIC to "AU",
        Region.EUROPE to "FR",
        Region.AMERICAS to "US",
    )

    fun find(destinationKey: String, region: Region): CountryInfo {
        val code = countryByDestination[destinationKey] ?: fallbackByRegion[region] ?: "US"
        return byCode[code] ?: KOREA
    }

    fun byCode(code: String): CountryInfo? = byCode[code.uppercase()]
}
