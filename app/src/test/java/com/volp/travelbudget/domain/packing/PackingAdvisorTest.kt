package com.volp.travelbudget.domain.packing

import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.weather.DailyForecast
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PackingAdvisorTest {

    private fun trip(
        destinationKey: String = "osaka",
        region: Region = Region.JAPAN,
        nights: Long = 3,
    ) = Trip(
        id = 1,
        title = "여행",
        destinationKey = destinationKey,
        destinationName = "오사카",
        region = region,
        startDate = LocalDate.of(2026, 4, 18),
        endDate = LocalDate.of(2026, 4, 18).plusDays(nights),
        travelers = 2,
        style = TravelStyle.STANDARD,
        includeFlight = true,
        currencyCode = "JPY",
        exchangeRate = 9.3,
        predictedBudget = emptyMap(),
        plannedBudget = emptyMap(),
    )

    private fun forecast(
        day: Int,
        max: Double,
        min: Double,
        rainMm: Double = 0.0,
        code: Int = 0,
    ) = DailyForecast(
        date = LocalDate.of(2026, 4, day),
        tempMaxCelsius = max,
        tempMinCelsius = min,
        precipitationProbability = if (rainMm > 0) 80 else 5,
        precipitationMm = rainMm,
        weatherCode = code,
    )

    private fun names(items: List<PackingItem>) = items.map { it.name }

    @Test
    fun `해외 여행에는 여권을 챙기라고 한다`() {
        val items = names(PackingAdvisor.suggest(trip(), emptyList()))

        assertTrue(items.contains("여권"))
        assertFalse(items.contains("신분증"))
    }

    @Test
    fun `국내 여행에는 여권 대신 신분증이다`() {
        val items = names(PackingAdvisor.suggest(trip("jeju", Region.DOMESTIC), emptyList()))

        assertTrue(items.contains("신분증"))
        assertFalse(items.contains("여권"))
    }

    @Test
    fun `플러그가 다른 나라는 어댑터를 알려 준다`() {
        val japan = names(PackingAdvisor.suggest(trip("osaka", Region.JAPAN), emptyList()))
        assertTrue(japan.any { it.contains("어댑터") })

        // 프랑스는 한국과 같은 C형이라 어댑터가 필요 없다.
        val france = names(PackingAdvisor.suggest(trip("paris", Region.EUROPE), emptyList()))
        assertFalse(france.any { it.contains("어댑터") })
    }

    @Test
    fun `추운 예보에는 외투를 챙기라고 한다`() {
        val items = names(
            PackingAdvisor.suggest(
                trip(),
                listOf(forecast(18, 2.0, -4.0), forecast(19, 1.0, -6.0)),
            ),
        )

        assertTrue(items.contains("두꺼운 패딩"))
        assertTrue(items.any { it.contains("장갑") })
    }

    @Test
    fun `더운 예보에는 자외선 차단제를 챙기라고 한다`() {
        val items = names(
            PackingAdvisor.suggest(trip(), listOf(forecast(18, 31.0, 24.0))),
        )

        assertTrue(items.contains("자외선 차단제"))
        assertTrue(items.contains("반팔·반바지"))
    }

    @Test
    fun `비 예보가 있으면 우산을 챙기라고 한다`() {
        val items = PackingAdvisor.suggest(
            trip(),
            listOf(forecast(18, 20.0, 14.0, rainMm = 8.0, code = 63)),
        )

        val umbrella = items.first { it.name == "우산 또는 우비" }
        assertTrue(umbrella.reason.contains("1일 비 예보"))
    }

    @Test
    fun `비 오는 날이 절반을 넘으면 방수 신발까지 챙긴다`() {
        val items = names(
            PackingAdvisor.suggest(
                trip(),
                listOf(
                    forecast(18, 20.0, 14.0, rainMm = 8.0, code = 63),
                    forecast(19, 20.0, 14.0, rainMm = 12.0, code = 63),
                    forecast(20, 21.0, 15.0),
                ),
            ),
        )

        assertTrue(items.contains("방수 신발"))
    }

    @Test
    fun `예보가 없으면 날씨와 무관한 것만 알려 준다`() {
        val items = PackingAdvisor.suggest(trip(), emptyList())

        assertTrue(items.none { it.group == PackingGroup.WEATHER })
        assertTrue(items.any { it.group == PackingGroup.DOCUMENT })
    }

    @Test
    fun `긴 여행에는 세탁 세제를 챙기라고 한다`() {
        val short = names(PackingAdvisor.suggest(trip(nights = 2), emptyList()))
        val long = names(PackingAdvisor.suggest(trip(nights = 6), emptyList()))

        assertFalse(short.contains("휴대용 세탁 세제"))
        assertTrue(long.contains("휴대용 세탁 세제"))
    }
}
