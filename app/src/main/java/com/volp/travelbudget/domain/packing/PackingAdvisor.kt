package com.volp.travelbudget.domain.packing

import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.travel.CountryDirectory
import com.volp.travelbudget.domain.weather.DailyForecast
import kotlin.math.roundToInt

enum class PackingGroup(val label: String) {
    DOCUMENT("서류·돈"),
    CLOTHING("옷"),
    WEATHER("날씨 대비"),
    ELECTRONICS("전자기기"),
    HEALTH("건강·위생"),
    ETC("그 밖에"),
}

/**
 * 챙길 것 한 가지.
 *
 * @param reason 왜 필요한지. 날씨처럼 근거가 있는 항목은 이유를 함께 보여 줘야 납득이 된다.
 */
data class PackingItem(
    val name: String,
    val group: PackingGroup,
    val reason: String,
)

/** 예보를 요약한 값. 준비물 판단과 화면 표시에 함께 쓴다. */
data class WeatherOutlook(
    val maxTemp: Double,
    val minTemp: Double,
    val rainyDays: Int,
    val snowyDays: Int,
    val totalDays: Int,
) {
    /** 하루 안에서 기온이 크게 흔들리는지. */
    val swings: Boolean get() = maxTemp - minTemp >= 12.0

    companion object {
        fun of(forecasts: List<DailyForecast>): WeatherOutlook? {
            if (forecasts.isEmpty()) return null
            return WeatherOutlook(
                maxTemp = forecasts.maxOf { it.tempMaxCelsius },
                minTemp = forecasts.minOf { it.tempMinCelsius },
                rainyDays = forecasts.count { it.isRainy },
                snowyDays = forecasts.count { it.isSnowy },
                totalDays = forecasts.size,
            )
        }
    }
}

/**
 * 여행 조건과 날씨 예보로 챙길 것을 뽑는다.
 *
 * 예보가 없으면(출발이 멀거나 받아 오지 못했으면) 날씨와 무관한 것만 알려 준다.
 * 없는 예보를 지어내 엉뚱한 옷을 챙기게 하는 것보다 낫다.
 */
object PackingAdvisor {

    fun suggest(trip: Trip, forecasts: List<DailyForecast>): List<PackingItem> {
        val items = mutableListOf<PackingItem>()
        val country = CountryDirectory.find(trip.destinationKey, trip.region)
        val overseas = trip.region != Region.DOMESTIC
        val outlook = WeatherOutlook.of(forecasts)

        // 서류·돈
        if (overseas) {
            items += PackingItem("여권", PackingGroup.DOCUMENT, "해외 여행")
            items += PackingItem("여행자보험 증서", PackingGroup.DOCUMENT, "해외 병원비는 비싸다")
            if (country.cashHeavy) {
                items += PackingItem(
                    "현지 현금",
                    PackingGroup.DOCUMENT,
                    "${country.name}은 카드가 안 되는 가게가 많다",
                )
            }
        } else {
            items += PackingItem("신분증", PackingGroup.DOCUMENT, "국내 숙소 체크인")
        }
        items += PackingItem("카드 두 장", PackingGroup.DOCUMENT, "한 장이 막히면 쓸 것이 필요하다")

        // 전자기기
        items += PackingItem("충전기", PackingGroup.ELECTRONICS, "기본")
        items += PackingItem("보조배터리", PackingGroup.ELECTRONICS, "지도와 사진으로 배터리가 빨리 준다")
        if (overseas) {
            if (!country.plugCompatibleWithKorea) {
                items += PackingItem(
                    "${country.plugTypes} 변환 어댑터",
                    PackingGroup.ELECTRONICS,
                    "${country.name}은 ${country.voltage}V ${country.plugTypes}이라 그냥 꽂을 수 없다",
                )
            }
            items += PackingItem("eSIM 또는 로밍", PackingGroup.ELECTRONICS, "현지에서 지도를 쓰려면 필요하다")
        }

        // 옷
        val nights = trip.nights
        items += PackingItem(
            "속옷·양말 ${(nights + 1).coerceAtMost(7)}벌",
            PackingGroup.CLOTHING,
            "${nights}박 ${trip.days}일",
        )
        if (nights >= 4) {
            items += PackingItem("휴대용 세탁 세제", PackingGroup.CLOTHING, "4박이 넘어가면 빨래가 는다")
        }

        if (outlook != null) {
            items += weatherItems(outlook)
        }

        // 건강·위생
        items += PackingItem("상비약", PackingGroup.HEALTH, "두통약·소화제·밴드")
        if (!country.tapWaterSafe && overseas) {
            items += PackingItem(
                "생수 사 먹기",
                PackingGroup.HEALTH,
                "${country.name}은 수돗물을 그냥 마시기 어렵다",
            )
        }

        // 그 밖에
        items += PackingItem("접이식 가방", PackingGroup.ETC, "돌아올 때 짐이 늘어난다")
        if (trip.includeFlight && overseas) {
            items += PackingItem("기내용 목베개", PackingGroup.ETC, "장거리 비행")
        }

        return items
    }

    private fun weatherItems(outlook: WeatherOutlook): List<PackingItem> = buildList {
        val max = outlook.maxTemp.roundToInt()
        val min = outlook.minTemp.roundToInt()
        val range = "예보 최저 ${min}도 최고 ${max}도"

        when {
            outlook.minTemp <= 0 -> {
                add(PackingItem("두꺼운 패딩", PackingGroup.WEATHER, range))
                add(PackingItem("장갑·목도리·모자", PackingGroup.WEATHER, range))
            }

            outlook.minTemp <= 8 -> {
                add(PackingItem("겨울 외투", PackingGroup.WEATHER, range))
                add(PackingItem("목도리", PackingGroup.WEATHER, range))
            }

            outlook.minTemp <= 15 -> add(PackingItem("얇은 외투", PackingGroup.WEATHER, range))
        }

        if (outlook.maxTemp >= 28) {
            add(PackingItem("반팔·반바지", PackingGroup.WEATHER, range))
            add(PackingItem("자외선 차단제", PackingGroup.WEATHER, "햇볕이 강하다"))
        }
        if (outlook.maxTemp >= 33) {
            add(PackingItem("휴대용 선풍기", PackingGroup.WEATHER, "최고 ${max}도"))
        }
        if (outlook.swings) {
            add(
                PackingItem(
                    "겹쳐 입을 옷 한 장",
                    PackingGroup.WEATHER,
                    "하루 안에서 기온이 ${(outlook.maxTemp - outlook.minTemp).roundToInt()}도 움직인다",
                ),
            )
        }
        if (outlook.rainyDays > 0) {
            add(
                PackingItem(
                    "우산 또는 우비",
                    PackingGroup.WEATHER,
                    "${outlook.totalDays}일 중 ${outlook.rainyDays}일 비 예보",
                ),
            )
            if (outlook.rainyDays * 2 >= outlook.totalDays) {
                add(PackingItem("방수 신발", PackingGroup.WEATHER, "비 오는 날이 절반을 넘는다"))
            }
        }
        if (outlook.snowyDays > 0) {
            add(
                PackingItem(
                    "미끄럼 방지 신발",
                    PackingGroup.WEATHER,
                    "${outlook.snowyDays}일 눈 예보",
                ),
            )
        }
    }
}
