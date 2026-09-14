package com.volp.travelbudget.domain.stats

import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import java.time.LocalDate

/** 하루치 지출 합계. 지출이 없는 날도 0으로 채워 그래프가 끊기지 않게 한다. */
data class DailyTotal(
    val date: LocalDate,
    val amountKrw: Long,
)

/** 항목 하나가 전체에서 차지하는 몫. */
data class CategoryShare(
    val category: ExpenseCategory,
    val amountKrw: Long,
    val ratio: Float,
)

/** 통화별로 실제로 얼마를 썼는지. 현지에서 얼마를 썼는지 감을 잡는 데 쓴다. */
data class CurrencyTotal(
    val currencyCode: String,
    val amount: Double,
    val amountKrw: Long,
)

data class TripStatistics(
    val dailyTotals: List<DailyTotal>,
    val categoryShares: List<CategoryShare>,
    val currencyTotals: List<CurrencyTotal>,
    val total: Long,
    val dailyAverage: Long,
    val perPerson: Long,
    val busiestDay: DailyTotal?,
    val topCategory: CategoryShare?,
    val expenseCount: Int,
) {
    val maxDailyAmount: Long get() = dailyTotals.maxOfOrNull { it.amountKrw } ?: 0L
}

object TripStatisticsCalculator {

    /** 하루 단위로 보기에는 튀어서 그래프를 망치는 항목. 필요하면 빼고 볼 수 있다. */
    val UPFRONT_CATEGORIES = setOf(ExpenseCategory.FLIGHT, ExpenseCategory.LODGING)

    /**
     * @param includeUpfront 항공·숙박을 포함할지. 끄면 하루 씀씀이가 잘 보인다.
     */
    fun of(
        trip: Trip,
        expenses: List<Expense>,
        includeUpfront: Boolean = true,
    ): TripStatistics {
        val target = if (includeUpfront) {
            expenses
        } else {
            expenses.filterNot { it.category in UPFRONT_CATEGORIES }
        }

        val total = target.sumOf { it.amountKrw }
        val byDate = target.groupBy { it.date }

        // 여행 기간은 빠짐없이 채우고, 기간 밖에 찍힌 지출(사전 결제 등)도 빠뜨리지 않는다.
        val dates = buildSet {
            var day = trip.startDate
            while (!day.isAfter(trip.endDate)) {
                add(day)
                day = day.plusDays(1)
            }
            addAll(byDate.keys)
        }.sorted()

        val dailyTotals = dates.map { date ->
            DailyTotal(date, byDate[date]?.sumOf { it.amountKrw } ?: 0L)
        }

        val categoryShares = target
            .groupBy { it.category }
            .map { (category, items) ->
                val amount = items.sumOf { it.amountKrw }
                CategoryShare(
                    category = category,
                    amountKrw = amount,
                    ratio = if (total > 0L) amount.toFloat() / total.toFloat() else 0f,
                )
            }
            .sortedByDescending { it.amountKrw }

        val currencyTotals = target
            .groupBy { if (it.enteredInForeignCurrency) it.currencyCode else "KRW" }
            .map { (code, items) ->
                CurrencyTotal(
                    currencyCode = code,
                    amount = if (code == "KRW") {
                        items.sumOf { it.amountKrw }.toDouble()
                    } else {
                        items.sumOf { it.originalAmount ?: 0.0 }
                    },
                    amountKrw = items.sumOf { it.amountKrw },
                )
            }
            .sortedByDescending { it.amountKrw }

        val spentDays = dailyTotals.count { it.amountKrw != 0L }

        return TripStatistics(
            dailyTotals = dailyTotals,
            categoryShares = categoryShares,
            currencyTotals = currencyTotals,
            total = total,
            dailyAverage = if (spentDays > 0) total / spentDays else 0L,
            perPerson = if (trip.travelers > 0) total / trip.travelers else total,
            busiestDay = dailyTotals.filter { it.amountKrw > 0L }.maxByOrNull { it.amountKrw },
            topCategory = categoryShares.firstOrNull { it.amountKrw > 0L },
            expenseCount = target.size,
        )
    }
}
