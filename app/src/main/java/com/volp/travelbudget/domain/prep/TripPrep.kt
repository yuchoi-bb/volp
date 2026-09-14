package com.volp.travelbudget.domain.prep

import com.volp.travelbudget.domain.model.Trip
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 출발 전에 한 번씩 짚고 넘어가는 시점. */
enum class PrepStage(val daysBefore: Long, val label: String) {
    /** 아직 고칠 수 있는 시점. 빠뜨린 것을 사 둘 시간이 있다. */
    WEEK(7, "일주일 전"),

    /** 가방을 싸는 시점. 이제는 챙기는 일만 남았다. */
    DAY(1, "하루 전"),
    ;

    companion object {
        fun of(days: Long): PrepStage? = entries.firstOrNull { it.daysBefore == days }
    }
}

/** 출발 전 점검 한 줄. [critical]은 그냥 넘기면 여행이 틀어지는 것이다. */
data class PrepNote(
    val text: String,
    val critical: Boolean = false,
)

data class PrepReminder(
    val tripId: Long,
    val tripTitle: String,
    val stage: PrepStage,
    val notes: List<PrepNote>,
) {
    val hasCritical: Boolean get() = notes.any { it.critical }

    /** 알림 한 줄로 줄인 말. */
    val headline: String
        get() = notes.firstOrNull { it.critical }?.text
            ?: notes.firstOrNull()?.text
            ?: "$tripTitle 출발 준비를 확인하세요"
}

/**
 * 출발 전에 무엇을 짚어 줄지 정한다.
 *
 * 여행 준비의 실패는 대부분 "몰라서"가 아니라 "제때 생각나지 않아서" 벌어진다. 짐을 싸는 전날에
 * 여권 만료를 알아차리면 이미 늦다. 그래서 아직 손쓸 수 있는 일주일 전에 한 번, 가방을 싸는
 * 하루 전에 한 번만 말을 건다. 더 자주 울리면 사람은 알림을 끈다.
 */
object TripPrepAdvisor {

    /**
     * 입국에 필요한 여권 잔여 유효기간.
     *
     * 나라마다 다르지만 여섯 달을 요구하는 곳이 가장 많다. 넉넉한 쪽으로 잡아 두면 손해가 없다.
     */
    const val PASSPORT_MONTHS_REQUIRED = 6L

    fun stageOn(trip: Trip, today: LocalDate): PrepStage? =
        PrepStage.of(ChronoUnit.DAYS.between(today, trip.startDate))

    /**
     * 여권이 이 여행에 쓸 수 있는지.
     *
     * 만료일이 여행 끝난 뒤로 여섯 달 남지 않으면 출국장에서 막힐 수 있다. 재발급에 시간이 걸리므로
     * 이것만은 일주일 전이 아니라 알게 된 순간 말해 주는 편이 낫다.
     */
    fun passportNote(expiry: LocalDate?, trip: Trip): PrepNote? {
        if (expiry == null) return null

        val required = trip.endDate.plusMonths(PASSPORT_MONTHS_REQUIRED)
        return when {
            expiry.isBefore(trip.endDate) ->
                PrepNote("여권이 여행 중에 만료됩니다. 재발급이 먼저입니다.", critical = true)
            expiry.isBefore(required) -> PrepNote(
                "여권 만료가 ${expiry}입니다. 잔여 유효기간 6개월을 요구하는 나라가 많아 막힐 수 있습니다.",
                critical = true,
            )
            else -> null
        }
    }

    /**
     * 이 여행에 지금 알릴 것이 있는지.
     *
     * @param uncheckedItems 아직 체크하지 않은 준비물 이름
     * @param lateArrivals 출발 전에 못 받을 것 같은 구매 건수
     * @param missingBookings 아직 넣지 않은 예약이 있는지(항공·숙소)
     */
    fun build(
        trip: Trip,
        today: LocalDate,
        passportExpiry: LocalDate? = null,
        uncheckedItems: List<String> = emptyList(),
        lateArrivals: Int = 0,
        missingBookings: Boolean = false,
    ): PrepReminder? {
        val stage = stageOn(trip, today) ?: return null

        val notes = buildList {
            passportNote(passportExpiry, trip)?.let { add(it) }

            if (lateArrivals > 0) {
                add(
                    PrepNote(
                        "주문한 ${lateArrivals}건이 출발 전에 안 올 수 있습니다.",
                        critical = stage == PrepStage.WEEK,
                    ),
                )
            }

            // 일주일 전이라면 아직 살 수 있다. 하루 전에 없는 물건을 말해 봐야 소용이 없다.
            if (uncheckedItems.isNotEmpty()) {
                add(PrepNote(checklistNote(stage, uncheckedItems)))
            }

            if (missingBookings && stage == PrepStage.WEEK) {
                add(PrepNote("항공·숙소 예약을 아직 넣지 않았습니다."))
            }

            if (isEmpty()) {
                add(PrepNote(readyNote(stage)))
            }
        }

        return PrepReminder(trip.id, trip.title, stage, notes)
    }

    private fun checklistNote(stage: PrepStage, items: List<String>): String {
        val head = items.take(MAX_LISTED).joinToString(", ")
        val rest = items.size - MAX_LISTED
        val listed = if (rest > 0) "$head 외 ${rest}개" else head
        return if (stage == PrepStage.WEEK) {
            "아직 준비 안 한 것: $listed. 지금이면 살 시간이 있습니다."
        } else {
            "내일 출발입니다. 가방에 넣을 것: $listed"
        }
    }

    private fun readyNote(stage: PrepStage): String = when (stage) {
        PrepStage.WEEK -> "일주일 뒤 출발입니다. 준비는 다 된 것으로 보입니다."
        PrepStage.DAY -> "내일 출발입니다. 준비물은 모두 챙겼습니다."
    }

    private const val MAX_LISTED = 3
}
