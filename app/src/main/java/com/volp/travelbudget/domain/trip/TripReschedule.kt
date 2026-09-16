package com.volp.travelbudget.domain.trip

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 날짜가 바뀔 때 함께 움직여야 하는 것 하나. 일정표의 갈 곳과 예약이 여기 들어온다. */
data class RescheduleItem(
    val key: String,
    val label: String,
    val date: LocalDate,
    /** 투어·항공권처럼 그 날짜로 이미 잡혀 있는 것. 앱이 마음대로 옮기면 안 된다. */
    val fixed: Boolean = false,
)

data class RescheduleChange(
    val item: RescheduleItem,
    val newDate: LocalDate,
    /** 새 기간 밖에 남는지. */
    val outside: Boolean,
) {
    val moved: Boolean get() = newDate != item.date

    /** 날짜가 고정인데 옮겨야 하는 것. 예약을 다시 잡아야 한다는 뜻이다. */
    val needsRebooking: Boolean get() = item.fixed && moved
}

data class ReschedulePlan(
    val shiftDays: Long,
    val nightsBefore: Int,
    val nightsAfter: Int,
    val changes: List<RescheduleChange>,
) {
    val moved: List<RescheduleChange> get() = changes.filter { it.moved }
    val outside: List<RescheduleChange> get() = changes.filter { it.outside }
    val needsRebooking: List<RescheduleChange> get() = changes.filter { it.needsRebooking }

    val shorter: Boolean get() = nightsAfter < nightsBefore
    val longer: Boolean get() = nightsAfter > nightsBefore
    val lengthChanged: Boolean get() = nightsAfter != nightsBefore
}

/**
 * 여행 날짜가 바뀌었을 때 그 안의 일정을 어떻게 옮길지 정한다.
 *
 * 일정은 두 가지로 바뀐다. 여행이 통째로 밀리거나(출발이 하루 늦어졌다), 기간이 줄고 늘거나
 * (16일까지인 줄 알았는데 14일까지였다) 한다. 앞의 경우는 모두 같은 폭으로 밀면 되지만, 뒤의
 * 경우는 사라진 날에 놓인 일정이 갈 곳을 잃는다.
 *
 * 그리고 무엇이든 다 밀 수는 없다. 투어와 항공권은 그 날짜로 이미 잡혀 있다. 앱이 날짜만 바꿔
 * 놓으면 장부에는 옮겨졌는데 실제 예약은 그대로인, 가장 나쁜 상태가 된다. 그래서 고정된 것은
 * 기본으로 두고, 옮겨야 한다면 **예약을 다시 잡아야 한다**고 따로 알린다.
 */
object TripReschedule {

    fun shiftDays(oldStart: LocalDate, newStart: LocalDate): Long =
        ChronoUnit.DAYS.between(oldStart, newStart)

    fun nights(start: LocalDate, end: LocalDate): Int =
        ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(0)

    /**
     * @param moveFixed 고정된 것도 함께 밀지. 예약을 이미 다시 잡았을 때만 켠다.
     * @param pullInside 새 기간 밖으로 나가는 것을 기간 안으로 당길지. 끄면 그 자리에 남는다.
     */
    fun plan(
        items: List<RescheduleItem>,
        oldStart: LocalDate,
        oldEnd: LocalDate,
        newStart: LocalDate,
        newEnd: LocalDate,
        moveFixed: Boolean = false,
        pullInside: Boolean = true,
    ): ReschedulePlan {
        val shift = shiftDays(oldStart, newStart)

        val changes = items.map { item ->
            val shifted = if (item.fixed && !moveFixed) item.date else item.date.plusDays(shift)
            val pulled = when {
                !pullInside -> shifted
                // 고정된 것을 당기면 예약과 어긋난다. 밖에 남겨 두고 알린다.
                item.fixed && !moveFixed -> shifted
                shifted.isBefore(newStart) -> newStart
                shifted.isAfter(newEnd) -> newEnd
                else -> shifted
            }
            RescheduleChange(
                item = item,
                newDate = pulled,
                outside = pulled.isBefore(newStart) || pulled.isAfter(newEnd),
            )
        }

        return ReschedulePlan(
            shiftDays = shift,
            nightsBefore = nights(oldStart, oldEnd),
            nightsAfter = nights(newStart, newEnd),
            changes = changes,
        )
    }
}
