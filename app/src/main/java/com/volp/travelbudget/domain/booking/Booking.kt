package com.volp.travelbudget.domain.booking

import com.volp.travelbudget.domain.sync.Syncable
import com.volp.travelbudget.domain.travel.GeoPoint
import java.time.LocalDateTime

enum class BookingType(val label: String, val emoji: String) {
    FLIGHT("항공", "✈️"),
    LODGING("숙소", "🏨"),
    TRAIN("기차", "🚄"),
    BUS("버스", "🚌"),
    CAR("렌터카", "🚗"),
    TICKET("입장권·공연", "🎫"),
    OTHER("그 밖에", "📌"),
    ;

    /** 출발지·도착지가 따로 있는 종류인지. 숙소나 입장권은 한 지점이다. */
    val hasRoute: Boolean get() = this == FLIGHT || this == TRAIN || this == BUS

    companion object {
        fun fromName(name: String): BookingType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * 미리 잡아 둔 항공편·숙소·기차 같은 예약 한 건.
 *
 * 일정과 따로 두지 않고 같은 시간표 위에 올린다. 공항에서 꺼내 보는 값(편명, 예약번호,
 * 터미널, 탑승구, 좌석)을 한 자리에 모아 두는 것이 이 화면의 목적이다.
 */
data class Booking(
    val id: Long = 0L,
    override val uid: String = "",
    override val updatedAt: Long = 0L,
    val tripId: Long,
    val type: BookingType,
    /** 편명이나 숙소 이름처럼 이 예약을 가리키는 말. */
    val title: String,
    /** 항공사·숙박 플랫폼 등. */
    val provider: String = "",
    val confirmationCode: String = "",
    val startAt: LocalDateTime,
    /** 도착 시각이나 체크아웃. 없으면 null. */
    val endAt: LocalDateTime? = null,
    val fromName: String = "",
    /** 공항·역 코드(ICN, KIX). 없으면 빈 값. */
    val fromCode: String = "",
    val toName: String = "",
    val toCode: String = "",
    val address: String = "",
    val seat: String = "",
    val gate: String = "",
    val terminal: String = "",
    val memo: String = "",
    val point: GeoPoint? = null,
) : Syncable {
    /** 숙소처럼 며칠에 걸친 예약인지. */
    val spansNights: Boolean
        get() = type == BookingType.LODGING && endAt != null &&
            endAt.toLocalDate().isAfter(startAt.toLocalDate())

    val routeLabel: String
        get() = when {
            !type.hasRoute -> ""
            fromCode.isNotBlank() && toCode.isNotBlank() -> "$fromCode → $toCode"
            fromName.isNotBlank() && toName.isNotBlank() -> "$fromName → $toName"
            else -> ""
        }
}
