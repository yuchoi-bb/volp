package com.volp.travelbudget.domain.trip

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TripOrderTest {

    private val today = LocalDate.of(2026, 9, 16)

    private fun trip(
        title: String,
        start: LocalDate,
        end: LocalDate = start.plusDays(3),
        sortOrder: Int = 0,
    ) = Trip(
        title = title,
        destinationKey = "tokyo",
        destinationName = "도쿄",
        region = Region.JAPAN,
        startDate = start,
        endDate = end,
        travelers = 1,
        style = TravelStyle.STANDARD,
        includeFlight = true,
        currencyCode = "JPY",
        exchangeRate = 9.4,
        predictedBudget = mapOf(ExpenseCategory.FOOD to 1L),
        plannedBudget = mapOf(ExpenseCategory.FOOD to 1L),
        sortOrder = sortOrder,
    )

    private val ongoing = trip("여행 중", today.minusDays(1), today.plusDays(2))
    private val soon = trip("다음 달", today.plusDays(20))
    private val later = trip("내년", today.plusYears(1))
    private val lastMonth = trip("지난달", today.minusDays(40), today.minusDays(35))
    private val lastYear = trip("작년", today.minusYears(1), today.minusYears(1).plusDays(3))

    @Test
    fun `여행 중인 것이 맨 위에 온다`() {
        val sorted = listOf(later, lastMonth, ongoing, soon).sortedWith(TripOrder.upcomingFirst(today))

        assertEquals("여행 중", sorted.first().title)
    }

    @Test
    fun `다가오는 여행은 가까운 것부터`() {
        val sorted = listOf(later, soon).sortedWith(TripOrder.upcomingFirst(today))

        assertEquals(listOf("다음 달", "내년"), sorted.map { it.title })
    }

    @Test
    fun `지난 여행은 최근 것부터 아래에 붙는다`() {
        val sorted = listOf(lastYear, lastMonth, soon).sortedWith(TripOrder.upcomingFirst(today))

        assertEquals(listOf("다음 달", "지난달", "작년"), sorted.map { it.title })
    }

    @Test
    fun `오늘을 가운데 두고 양쪽으로 멀어진다`() {
        val sorted = listOf(lastYear, later, ongoing, lastMonth, soon)
            .sortedWith(TripOrder.upcomingFirst(today))

        assertEquals(
            listOf("여행 중", "다음 달", "내년", "지난달", "작년"),
            sorted.map { it.title },
        )
    }

    @Test
    fun `손으로 정한 자리가 먼저다`() {
        val first = trip("나중에 갈 곳", today.plusYears(1), sortOrder = 1)
        val second = trip("곧 갈 곳", today.plusDays(3), sortOrder = 2)

        val sorted = listOf(second, first).sortedWith(TripOrder.manual(today))

        assertEquals(listOf("나중에 갈 곳", "곧 갈 곳"), sorted.map { it.title })
    }

    @Test
    fun `자리를 정하지 않은 여행은 다가오는 차례를 따른다`() {
        val pinned = trip("맨 위에 둔 것", today.plusYears(1), sortOrder = 1)

        val sorted = listOf(lastMonth, later, soon, pinned).sortedWith(TripOrder.manual(today))

        assertEquals(
            listOf("맨 위에 둔 것", "다음 달", "내년", "지난달"),
            sorted.map { it.title },
        )
    }
}
