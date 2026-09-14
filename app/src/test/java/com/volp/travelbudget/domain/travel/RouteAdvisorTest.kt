package com.volp.travelbudget.domain.travel

import com.volp.travelbudget.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAdvisorTest {

    @Test
    fun `가까우면 걸어가라고 한다`() {
        val suggestion = RouteAdvisor.suggest(500.0, Region.JAPAN)

        assertEquals(TransportMode.WALK, suggestion.mode)
        assertNull(suggestion.estimatedCostKrw)
        assertTrue(suggestion.minutes in 1..20)
    }

    @Test
    fun `시내 거리는 대중교통을 권한다`() {
        val suggestion = RouteAdvisor.suggest(5_000.0, Region.JAPAN)

        assertEquals(TransportMode.TRANSIT, suggestion.mode)
        assertNotNull(suggestion.estimatedCostKrw)
    }

    @Test
    fun `대중교통으로 감당 안 되는 거리는 택시로 넘어간다`() {
        val suggestion = RouteAdvisor.suggest(40_000.0, Region.JAPAN)

        assertEquals(TransportMode.TAXI, suggestion.mode)
        assertTrue(suggestion.estimatedCostKrw!! > 0L)
    }

    @Test
    fun `도시를 넘는 거리는 시외 이동으로 본다`() {
        val suggestion = RouteAdvisor.suggest(300_000.0, Region.JAPAN)

        assertEquals(TransportMode.INTERCITY, suggestion.mode)
    }

    @Test
    fun `택시 요금은 권역에 따라 다르다`() {
        val japan = RouteAdvisor.suggest(40_000.0, Region.JAPAN).estimatedCostKrw!!
        val southeastAsia = RouteAdvisor.suggest(40_000.0, Region.SOUTHEAST_ASIA).estimatedCostKrw!!

        assertTrue("일본이 동남아보다 비싸야 한다", japan > southeastAsia)
    }

    @Test
    fun `길을 따라가는 거리는 직선보다 길게 잡는다`() {
        val suggestion = RouteAdvisor.suggest(1_000.0, Region.DOMESTIC)
        assertTrue(suggestion.distanceMeters > 1_000.0)
    }
}
