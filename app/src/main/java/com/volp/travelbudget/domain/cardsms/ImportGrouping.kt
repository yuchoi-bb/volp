package com.volp.travelbudget.domain.cardsms

import com.volp.travelbudget.domain.budget.CurrencyRates

/** 문자에서 모은 결제를 사람이 가리기 쉽게 나눈 묶음. */
enum class ImportGroup(val title: String, val note: String) {
    PRETRIP(
        "여행 전 예약",
        "떠나기 전에 미리 낸 항공·숙소·렌터카다.",
    ),
    ONSITE(
        "여행 중 결제",
        "여행 기간에 쓴 돈이다.",
    ),
    UNCERTAIN(
        "넣을지 고민되는 결제",
        "여행 기간이지만 원화로 나갔다. 여행사·투어 잔금일 수도 있고, 집에서 빠져나간 자동결제일 수도 있다.",
    ),
}

/**
 * 모은 결제를 세 묶음으로 나눈다.
 *
 * 해외 여행 중에 원화로 나간 돈이 문제다. 여행사 잔금이나 투어 예약처럼 여행 것도 있고, 통신비나
 * 구독처럼 집에서 그냥 빠져나간 것도 있다. 둘은 문자만 보고 가릴 수 없다. 섞어서 기본으로 켜 두면
 * 장부가 부풀고, 통째로 빼면 여행사 잔금이 사라진다. 그래서 따로 모아 사람에게 묻는다.
 *
 * 국내 여행은 모두 원화라 가릴 것이 없다. 그때는 이 묶음을 만들지 않는다.
 */
object ImportGrouping {

    /** 가맹점 이름이 여행 것으로 읽히는지. 여행사·투어·숙소·렌터카·항공을 본다. */
    fun looksLikeTravel(merchant: String): Boolean = PretripBookings.kindOf(merchant) != null

    /** 해외 여행 중의 원화 결제인지. */
    fun isUncertain(tripCurrencyCode: String, isOverseas: Boolean): Boolean =
        !isOverseas && !CurrencyRates.isKrw(tripCurrencyCode)

    fun groupFor(tripCurrencyCode: String, isOverseas: Boolean, pretrip: Boolean): ImportGroup = when {
        pretrip -> ImportGroup.PRETRIP
        isUncertain(tripCurrencyCode, isOverseas) -> ImportGroup.UNCERTAIN
        else -> ImportGroup.ONSITE
    }

    /**
     * 처음부터 켜 둘지.
     *
     * 이미 같은 값이 장부에 있으면 켜지 않는다. 고민되는 묶음은 여행으로 읽히는 것만 켜 둔다 —
     * 나머지는 사람이 하나씩 보고 켠다.
     */
    fun checkedByDefault(group: ImportGroup, alreadyThere: Boolean, looksLikeTravel: Boolean): Boolean = when {
        alreadyThere -> false
        group == ImportGroup.UNCERTAIN -> looksLikeTravel
        else -> true
    }
}
