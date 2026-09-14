package com.volp.travelbudget.domain.travel

import com.volp.travelbudget.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TravelZonesTest {

    private val now: ZonedDateTime =
        LocalDateTime.of(2026, 10, 3, 12, 0).atZone(ZoneId.of("Asia/Seoul"))

    @Test
    fun `일본은 한국과 시차가 없다`() {
        val clock = TravelZones.clock("tokyo", Region.JAPAN, now)!!

        assertTrue(clock.sameAsHome)
        assertEquals("한국과 같음", clock.offsetLabel)
        assertEquals(clock.home, clock.local)
    }

    @Test
    fun `방콕은 두 시간 느리다`() {
        val clock = TravelZones.clock("bangkok", Region.SOUTHEAST_ASIA, now)!!

        assertEquals(-120L, clock.offsetMinutes)
        assertEquals("-2시간", clock.offsetLabel)
        assertEquals(10, clock.local.hour)
    }

    @Test
    fun `도시를 몰라도 지역으로 어림한다`() {
        val clock = TravelZones.clock("unknown-city", Region.EUROPE, now)!!

        assertTrue(clock.offsetMinutes < 0L)
    }

    @Test
    fun `국내 여행은 시차가 없다`() {
        val clock = TravelZones.clock("jeju", Region.DOMESTIC, now)!!

        assertTrue(clock.sameAsHome)
    }

    @Test
    fun `삼십 분 단위 시차도 말이 되게 적는다`() {
        val clock = LocalClock(
            local = now.toLocalDateTime(),
            home = now.toLocalDateTime(),
            offsetMinutes = 510L,
        )

        assertEquals("+8시간 30분", clock.offsetLabel)
    }
}
