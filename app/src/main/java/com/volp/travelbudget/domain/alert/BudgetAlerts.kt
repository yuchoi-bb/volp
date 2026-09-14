package com.volp.travelbudget.domain.alert

import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.summary.TripSummary
import java.time.LocalDate

enum class BudgetAlertLevel {
    /** 오늘 하루 배정액을 넘겼다. */
    DAILY_OVER,

    /** 지금 속도로 가면 여행이 끝날 때 예산을 넘긴다. */
    PROJECTED_OVER,

    /** 이미 전체 예산을 넘겼다. */
    TOTAL_OVER,
}

/**
 * 알림 한 건.
 *
 * @param key 같은 알림을 두 번 보내지 않으려고 쓰는 열쇠. 하루 단위 알림은 날짜를 포함한다.
 */
data class BudgetAlert(
    val level: BudgetAlertLevel,
    val key: String,
    val title: String,
    val message: String,
)

/**
 * 지출을 기록할 때마다 예산 상태를 보고 알릴 만한 일이 생겼는지 판단한다.
 *
 * 알림은 적게 울려야 쓸모가 있다. 그래서 같은 알림은 한 번만 보내고(전체 예산 초과는 여행당
 * 한 번, 하루 관련 알림은 하루에 한 번), 조금 넘긴 정도로는 울리지 않게 여유를 둔다.
 */
object BudgetAlerts {

    /** 하루 배정액을 이만큼 넘겨야 알린다. 조금 넘길 때마다 울리면 금방 무시하게 된다. */
    private const val DAILY_TOLERANCE = 1.2

    /** 예상 최종 지출이 예산을 이만큼 넘길 때 알린다. */
    private const val PROJECTED_TOLERANCE = 1.05

    private val UPFRONT_CATEGORIES = setOf(ExpenseCategory.FLIGHT, ExpenseCategory.LODGING)

    /**
     * @param spentTodayDaily 오늘 쓴 하루 경비(항공·숙박 제외)
     */
    fun evaluate(
        summary: TripSummary,
        today: LocalDate,
        spentTodayDaily: Long,
    ): List<BudgetAlert> {
        if (summary.totalPlanned <= 0L) return emptyList()

        val alerts = mutableListOf<BudgetAlert>()

        if (summary.isOverBudget) {
            alerts += BudgetAlert(
                level = BudgetAlertLevel.TOTAL_OVER,
                key = "total",
                title = "예산을 넘겼다",
                message = "${summary.trip.title} · 예산보다 " +
                    "${formatMan(summary.totalSpent - summary.totalPlanned)} 더 썼다",
            )
        }

        // 여행이 끝난 뒤에는 앞으로의 이야기를 할 필요가 없다.
        if (summary.remainingDays > 0) {
            val dailyBudget = dailyBudgetOf(summary)
            if (dailyBudget > 0L && spentTodayDaily > dailyBudget * DAILY_TOLERANCE) {
                alerts += BudgetAlert(
                    level = BudgetAlertLevel.DAILY_OVER,
                    key = "daily:$today",
                    title = "오늘 좀 많이 썼다",
                    message = "오늘 ${formatMan(spentTodayDaily)} · 하루 몫 ${formatMan(dailyBudget)}",
                )
            }

            if (!summary.isOverBudget &&
                summary.projectedTotal > summary.totalPlanned * PROJECTED_TOLERANCE
            ) {
                alerts += BudgetAlert(
                    level = BudgetAlertLevel.PROJECTED_OVER,
                    key = "projected:$today",
                    title = "이 속도면 예산을 넘긴다",
                    message = "예상 ${formatMan(summary.projectedTotal)} · " +
                        "예산 ${formatMan(summary.totalPlanned)}. " +
                        "남은 ${summary.remainingDays}일 동안 하루 " +
                        "${formatMan(summary.dailyAllowance ?: 0L)}까지 쓸 수 있다",
                )
            }
        }

        return alerts
    }

    /** 항공·숙박을 뺀 예산을 여행 일수로 나눈 하루 몫. */
    private fun dailyBudgetOf(summary: TripSummary): Long {
        val dailyPlanned = summary.categories
            .filterNot { it.category in UPFRONT_CATEGORIES }
            .sumOf { it.planned }
        val days = summary.trip.days.coerceAtLeast(1)
        return dailyPlanned / days
    }

    private fun formatMan(amount: Long): String {
        val absolute = kotlin.math.abs(amount)
        return when {
            absolute >= 10_000L -> {
                val man = absolute / 10_000.0
                val text = if (man >= 10) man.toInt().toString() else String.format("%.1f", man)
                "${text}만원"
            }

            else -> "${absolute}원"
        }
    }
}
