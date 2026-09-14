package com.volp.travelbudget.domain.weather

import java.time.LocalDate

/** 하루치 날씨 예보. */
data class DailyForecast(
    val date: LocalDate,
    val tempMaxCelsius: Double,
    val tempMinCelsius: Double,
    /** 비나 눈이 올 확률(%). 모르면 null. */
    val precipitationProbability: Int?,
    /** 강수량 합계(mm). */
    val precipitationMm: Double,
    /** WMO 날씨 코드. */
    val weatherCode: Int,
) {
    val isRainy: Boolean
        get() = precipitationMm >= 1.0 || (precipitationProbability ?: 0) >= 40

    val isSnowy: Boolean
        get() = weatherCode in SNOW_CODES

    val description: String get() = describe(weatherCode)

    val emoji: String
        get() = when {
            weatherCode == 0 -> "☀️"
            weatherCode in 1..2 -> "🌤️"
            weatherCode == 3 -> "☁️"
            weatherCode in 45..48 -> "🌫️"
            isSnowy -> "🌨️"
            weatherCode in 95..99 -> "⛈️"
            weatherCode in 51..82 -> "🌧️"
            else -> "🌤️"
        }

    companion object {
        private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)

        /** WMO 날씨 코드를 사람이 읽는 말로 바꾼다. */
        fun describe(code: Int): String = when (code) {
            0 -> "맑음"
            1, 2 -> "구름 조금"
            3 -> "흐림"
            45, 48 -> "안개"
            51, 53, 55 -> "이슬비"
            56, 57 -> "어는 이슬비"
            61, 63, 65 -> "비"
            66, 67 -> "어는 비"
            71, 73, 75 -> "눈"
            77 -> "싸락눈"
            80, 81, 82 -> "소나기"
            85, 86 -> "소낙눈"
            95 -> "뇌우"
            96, 99 -> "우박을 동반한 뇌우"
            else -> "알 수 없음"
        }
    }
}
