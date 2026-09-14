package com.volp.travelbudget.data.weather

import com.volp.travelbudget.data.update.UpdateChecker
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.weather.DailyForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate

/**
 * 날씨 예보를 받아 온다.
 *
 * 키가 필요 없는 공개 API(Open-Meteo)를 쓴다. 예보는 보름 남짓까지만 나오므로 그보다 먼
 * 여행은 예보가 비어 있을 수 있다. 그때는 준비물 안내도 날씨 없이 만든다.
 */
class WeatherRepository(
    private val client: OkHttpClient = UpdateChecker.defaultClient(),
) {

    suspend fun forecast(
        point: GeoPoint,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyForecast> = withContext(Dispatchers.IO) {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", point.latitude.toString())
            .addQueryParameter("longitude", point.longitude.toString())
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min," +
                    "precipitation_sum,precipitation_probability_max",
            )
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("start_date", startDate.toString())
            .addQueryParameter("end_date", endDate.toString())
            .build()

        val request = Request.Builder().url(url).build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parse(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(body: String): List<DailyForecast> {
        if (body.isBlank()) return emptyList()
        val daily = JSONObject(body).optJSONObject("daily") ?: return emptyList()

        val dates = daily.optJSONArray("time") ?: return emptyList()
        val codes = daily.optJSONArray("weather_code")
        val maxTemps = daily.optJSONArray("temperature_2m_max")
        val minTemps = daily.optJSONArray("temperature_2m_min")
        val precipitation = daily.optJSONArray("precipitation_sum")
        val probability = daily.optJSONArray("precipitation_probability_max")

        return (0 until dates.length()).mapNotNull { index ->
            val date = runCatching { LocalDate.parse(dates.getString(index)) }.getOrNull()
                ?: return@mapNotNull null
            DailyForecast(
                date = date,
                tempMaxCelsius = maxTemps?.optDouble(index) ?: 0.0,
                tempMinCelsius = minTemps?.optDouble(index) ?: 0.0,
                precipitationProbability = probability
                    ?.optDouble(index)
                    ?.takeIf { !it.isNaN() }
                    ?.toInt(),
                precipitationMm = precipitation?.optDouble(index)?.takeIf { !it.isNaN() } ?: 0.0,
                weatherCode = codes?.optInt(index) ?: 0,
            )
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    }
}
