package com.volp.travelbudget.domain.travel

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun `같은 점 사이의 거리는 0이다`() {
        val point = GeoPoint(35.6762, 139.6503)
        assertEquals(0.0, Geo.distanceMeters(point, point), 0.001)
    }

    @Test
    fun `도쿄와 오사카 사이는 약 400킬로미터다`() {
        val tokyo = GeoPoint(35.6762, 139.6503)
        val osaka = GeoPoint(34.6937, 135.5023)

        val km = Geo.distanceMeters(tokyo, osaka) / 1_000.0
        assertEquals(400.0, km, 15.0)
    }

    @Test
    fun `난바와 도톤보리 사이는 1킬로미터 안쪽이다`() {
        val namba = GeoPoint(34.6659, 135.5011)
        val dotonbori = GeoPoint(34.6687, 135.5013)

        assertEquals(310.0, Geo.distanceMeters(namba, dotonbori), 60.0)
    }
}
