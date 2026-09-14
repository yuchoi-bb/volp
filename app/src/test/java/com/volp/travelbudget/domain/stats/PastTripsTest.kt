package com.volp.travelbudget.domain.stats

import com.volp.travelbudget.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PastTripsTest {

    private val today = LocalDate.of(2026, 9, 14)

    private fun trip(
        id: Long = 1L,
        title: String = "오사카",
        start: LocalDate = LocalDate.of(2026, 5, 1),
        days: Int = 4,
        travelers: Int = 2,
        predicted: Long = 1_000_000L,
        byCategory: Map<ExpenseCategory, Long> = mapOf(
            ExpenseCategory.FLIGHT to 400_000L,
            ExpenseCategory.LODGING to 300_000L,
            ExpenseCategory.FOOD to 300_000L,
        ),
    ) = PastTrip(
        tripId = id,
        title = title,
        destinationName = title,
        startDate = start,
        endDate = start.plusDays((days - 1).toLong()),
        days = days,
        travelers = travelers,
        predictedTotal = predicted,
        actualTotal = byCategory.values.sum(),
        byCategory = byCategory,
    )

    @Test
    fun `하루 얼마와 한 사람 하루 얼마를 낸다`() {
        val past = trip(days = 4, travelers = 2)

        assertEquals(1_000_000L, past.actualTotal)
        assertEquals(250_000L, past.perDay)
        assertEquals(125_000L, past.perPersonPerDay)
    }

    @Test
    fun `항공과 숙박은 현지 지출에서 뺀다`() {
        val past = trip()

        assertEquals(300_000L, past.onSiteTotal)
        assertEquals(75_000L, past.onSitePerDay)
    }

    @Test
    fun `예측보다 더 썼는지 알려 준다`() {
        val over = trip(predicted = 800_000L)
        val under = trip(predicted = 1_200_000L)

        assertTrue(over.overPredicted)
        assertEquals(200_000L, over.difference)
        assertTrue(!under.overPredicted)
        assertEquals(1.25, over.ratio!!, 0.001)
    }

    @Test
    fun `아직 안 끝난 여행은 결산에 넣지 않는다`() {
        val finished = trip(id = 1L, start = LocalDate.of(2026, 5, 1))
        val ongoing = trip(id = 2L, start = today.minusDays(1))

        val summary = PastTripReports.finished(listOf(finished, ongoing), today)

        assertEquals(1, summary.tripCount)
        assertEquals(1L, summary.trips.first().tripId)
    }

    @Test
    fun `지출이 없는 여행도 넣지 않는다`() {
        val empty = trip(id = 3L, byCategory = emptyMap())

        assertTrue(PastTripReports.finished(listOf(empty), today).isEmpty)
    }

    @Test
    fun `여러 여행을 겹쳐 하루 씀씀이를 낸다`() {
        val summary = PastTripReports.finished(
            listOf(
                trip(id = 1L, days = 4, travelers = 2),
                trip(
                    id = 2L,
                    title = "도쿄",
                    start = LocalDate.of(2026, 7, 1),
                    days = 6,
                    travelers = 1,
                    byCategory = mapOf(ExpenseCategory.FOOD to 600_000L),
                ),
            ),
            today,
        )

        assertEquals(2, summary.tripCount)
        assertEquals(1_600_000L, summary.totalActual)
        assertEquals(10, summary.totalDays)
        assertEquals(160_000L, summary.perDay)
        // 사람-날은 4×2 + 6×1 = 14
        assertEquals(114_285L, summary.perPersonPerDay)
    }

    @Test
    fun `최근 여행이 앞에 온다`() {
        val summary = PastTripReports.finished(
            listOf(
                trip(id = 1L, start = LocalDate.of(2026, 5, 1)),
                trip(id = 2L, title = "도쿄", start = LocalDate.of(2026, 7, 1)),
            ),
            today,
        )

        assertEquals(2L, summary.trips.first().tripId)
    }

    @Test
    fun `예측이 얼마나 맞았는지 센다`() {
        val exact = PastTripReports.finished(listOf(trip(predicted = 1_000_000L)), today)
        val off = PastTripReports.finished(listOf(trip(predicted = 500_000L)), today)

        assertEquals(1.0, exact.accuracy!!, 0.001)
        assertEquals(0.0, off.accuracy!!, 0.001)
    }

    @Test
    fun `항목별 비중을 낸다`() {
        val summary = PastTripReports.finished(listOf(trip()), today)

        assertEquals(0.4, summary.categoryShare[ExpenseCategory.FLIGHT]!!, 0.001)
        assertEquals(0.3, summary.categoryShare[ExpenseCategory.FOOD]!!, 0.001)
        assertNull(summary.categoryShare[ExpenseCategory.SHOPPING])
    }

    @Test
    fun `지난 씀씀이로 다음 여행 현지 예산을 어림한다`() {
        val summary = PastTripReports.finished(listOf(trip(days = 4, travelers = 2)), today)

        // 현지 지출 300,000 ÷ (4일 × 2명) = 하루 한 사람 37,500
        assertEquals(225_000L, PastTripReports.suggestOnSiteBudget(summary, days = 3, travelers = 2))
        assertNull(PastTripReports.suggestOnSiteBudget(PastTripsSummary(emptyList()), 3, 2))
    }

    @Test
    fun `가장 비쌌던 여행과 하루 씀씀이가 컸던 여행을 가른다`() {
        val long = trip(id = 1L, days = 10, byCategory = mapOf(ExpenseCategory.FOOD to 1_000_000L))
        val short = trip(
            id = 2L,
            title = "홍콩",
            start = LocalDate.of(2026, 6, 1),
            days = 2,
            byCategory = mapOf(ExpenseCategory.FOOD to 600_000L),
        )

        val summary = PastTripReports.finished(listOf(long, short), today)

        assertEquals(1L, summary.mostExpensive!!.tripId)
        assertEquals(2L, summary.busiestPerDay!!.tripId)
    }
}
