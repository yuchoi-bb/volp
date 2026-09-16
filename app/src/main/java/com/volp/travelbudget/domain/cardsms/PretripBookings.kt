package com.volp.travelbudget.domain.cardsms

import com.volp.travelbudget.domain.model.ExpenseCategory
import java.time.LocalDate

/** 여행 전에 미리 결제해 두는 예약의 갈래. */
enum class PretripKind(val label: String, val category: ExpenseCategory) {
    LODGING("숙소", ExpenseCategory.LODGING),
    RENTAL("렌터카", ExpenseCategory.TRANSPORT),
    FLIGHT("항공권", ExpenseCategory.FLIGHT),
    AGENCY("여행사·예약", ExpenseCategory.ETC),
}

/**
 * 여행 전에 결제한 예약을 문자에서 찾는다.
 *
 * 항공권·숙소·렌터카는 떠나기 전에 낸다. 여행 기간만 훑으면 이 돈이 통째로 빠져서, 여행이 끝난
 * 뒤 "이 정도밖에 안 썼나" 하는 장부가 남는다. 보통 가장 큰 항목인데도 그렇다.
 *
 * 그렇다고 여행 전 몇 달을 통째로 가져올 수는 없다. 그 기간은 평소 생활비이기 때문이다. 그래서
 * 가맹점 이름이 예약으로 읽히는 것만 걸러 낸다. 걸러 낸 뒤에도 사람이 목록을 보고 고른다.
 */
object PretripBookings {

    /** 여행 시작일에서 며칠 전까지 훑을지. */
    val LOOKBACK_CHOICES = listOf(30, 60, 90, 180)

    const val DEFAULT_LOOKBACK_DAYS = 90

    /**
     * 훑을 날짜 구간. 여행 시작 전날까지다.
     *
     * 여행 기간은 이미 따로 훑으므로 여기서 겹치게 두면 같은 결제가 두 번 올라온다.
     */
    fun window(tripStart: LocalDate, days: Int): ClosedRange<LocalDate> {
        val span = days.coerceAtLeast(1)
        return tripStart.minusDays(span.toLong())..tripStart.minusDays(1)
    }

    /** 가맹점 이름이 예약으로 읽히면 그 갈래를, 아니면 null을 준다. */
    fun kindOf(merchant: String): PretripKind? {
        val normalized = normalize(merchant)
        if (normalized.isBlank()) return null
        return rules.firstOrNull { (_, keywords) ->
            keywords.any { normalized.contains(normalize(it)) }
        }?.first
    }

    private fun normalize(value: String): String =
        value.uppercase().filterNot { it == ' ' || it == '.' || it == '-' || it == '(' || it == ')' }

    /**
     * 위에서부터 본다. 에어비앤비처럼 항공으로 읽힐 수 있는 이름이 있어 숙소를 먼저 둔다.
     *
     * 항공에 'AIR' 한 조각만 두지 않는 것도 같은 이유다. 미용실(HAIR)이 딸려 온다.
     */
    private val rules: List<Pair<PretripKind, List<String>>> = listOf(
        PretripKind.LODGING to listOf(
            "AIRBNB", "에어비앤비", "AGODA", "아고다", "BOOKING.COM", "부킹닷컴",
            "HOTELS.COM", "호텔스컴바인", "TRIVAGO", "트리바고", "야놀자", "여기어때",
            "호텔", "HOTEL", "HOSTEL", "게스트하우스", "GUESTHOUSE", "리조트", "RESORT",
            "펜션", "PENSION", "료칸", "RYOKAN", "민박", "숙박", "데일리호텔",
        ),
        PretripKind.RENTAL to listOf(
            "렌터카", "렌트카", "RENTACAR", "RENTAL", "카렌탈", "쏘카", "그린카",
            "HERTZ", "AVIS", "SIXT", "EUROPCAR", "BUDGETRENT", "ENTERPRISERENT",
        ),
        PretripKind.FLIGHT to listOf(
            "항공", "AIRLINE", "AIRWAYS", "KOREANAIR", "ASIANA", "JEJUAIR", "JINAIR",
            "AIRBUSAN", "AIRSEOUL", "TWAY", "에어부산", "에어서울", "진에어", "티웨이",
            "PEACH", "JETSTAR", "SCOOT", "VIETJET", "EMIRATES", "LUFTHANSA", "공항",
        ),
        PretripKind.AGENCY to listOf(
            "투어", "TOUR", "여행사", "여행상품", "EXPEDIA", "익스피디아", "TRIP.COM",
            "트립닷컴", "KLOOK", "클룩", "MYREALTRIP", "마이리얼트립", "SKYSCANNER",
            "스카이스캐너", "KKDAY", "VIATOR", "GETYOURGUIDE", "WAUG", "노랑풍선",
            "투어비스", "여행자보험",
        ),
    )
}
