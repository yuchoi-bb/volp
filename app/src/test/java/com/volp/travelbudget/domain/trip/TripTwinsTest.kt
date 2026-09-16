package com.volp.travelbudget.domain.trip

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripTwinsTest {

    private fun trip(
        id: Long,
        uid: String,
        title: String = "제주 여행",
        start: LocalDate = LocalDate.of(2026, 10, 1),
        end: LocalDate = LocalDate.of(2026, 10, 6),
    ) = Trip(
        id = id,
        uid = uid,
        title = title,
        destinationKey = "jeju",
        destinationName = "제주",
        region = Region.DOMESTIC,
        startDate = start,
        endDate = end,
        travelers = 2,
        style = TravelStyle.STANDARD,
        includeFlight = true,
        currencyCode = "KRW",
        exchangeRate = 1.0,
        predictedBudget = mapOf(ExpenseCategory.FOOD to 1L),
        plannedBudget = mapOf(ExpenseCategory.FOOD to 1L),
    )

    @Test
    fun `제목과 기간이 같으면 같은 여행으로 본다`() {
        val incoming = trip(id = 0, uid = "phone-1")
        val local = listOf(trip(id = 7, uid = "tablet-1"))

        val twin = TripTwins.findTwin(incoming, local, remoteUids = setOf("phone-1"))

        assertEquals(7L, twin?.id)
    }

    @Test
    fun `앞뒤 공백과 대소문자는 가리지 않는다`() {
        val incoming = trip(id = 0, uid = "phone-1", title = " 제주 여행 ")
        val local = listOf(trip(id = 7, uid = "tablet-1", title = "제주 여행"))

        assertEquals(7L, TripTwins.findTwin(incoming, local, setOf("phone-1"))?.id)
    }

    @Test
    fun `기간이 다르면 다른 여행이다`() {
        val incoming = trip(id = 0, uid = "phone-1", start = LocalDate.of(2027, 2, 15))
        val local = listOf(trip(id = 7, uid = "tablet-1", start = LocalDate.of(2027, 2, 16)))

        assertNull(TripTwins.findTwin(incoming, local, setOf("phone-1")))
    }

    @Test
    fun `저쪽에도 있는 여행은 쌍둥이로 보지 않는다`() {
        // 이미 uid로 짝이 맞은 여행이다. 여기에 또 붙이면 엉뚱한 여행을 덮어쓴다.
        val incoming = trip(id = 0, uid = "phone-2")
        val local = listOf(trip(id = 7, uid = "phone-1"))

        assertNull(TripTwins.findTwin(incoming, local, remoteUids = setOf("phone-1", "phone-2")))
    }

    @Test
    fun `이미 두 벌로 쌓인 여행을 묶는다`() {
        val trips = listOf(
            trip(id = 1, uid = "a"),
            trip(id = 2, uid = "b"),
            trip(id = 3, uid = "c", title = "시드니 여행", start = LocalDate.of(2027, 2, 15)),
        )

        val groups = TripTwins.duplicates(trips)

        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups.first().map { it.id })
    }

    @Test
    fun `겹치는 것이 없으면 묶을 것도 없다`() {
        val trips = listOf(
            trip(id = 1, uid = "a"),
            trip(id = 2, uid = "b", title = "시드니 여행"),
        )

        assertTrue(TripTwins.duplicates(trips).isEmpty())
    }

    @Test
    fun `묶음 안에서는 먼저 만든 여행이 앞에 온다`() {
        val trips = listOf(trip(id = 9, uid = "c"), trip(id = 2, uid = "a"), trip(id = 5, uid = "b"))

        assertEquals(listOf(2L, 5L, 9L), TripTwins.duplicates(trips).first().map { it.id })
    }
}
