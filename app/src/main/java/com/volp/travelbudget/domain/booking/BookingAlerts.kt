package com.volp.travelbudget.domain.booking

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** 예약 때문에 지금 알릴 만한 일. */
data class BookingAlert(
    val bookingId: Long,
    val tripId: Long,
    /** 같은 알림을 두 번 보내지 않으려고 쓰는 열쇠. */
    val key: String,
    val title: String,
    val message: String,
)

/**
 * 예약을 보고 지금 말해 줄 것이 있는지 정한다.
 *
 * 예약 화면을 열어 보는 사람은 이미 알고 있다. 알림이 쓸모 있으려면 **잊고 있을 때** 와야 한다.
 * 온라인 체크인이 열리는 순간과 나설 시각, 그리고 체크아웃 아침이 그런 때다.
 */
object BookingAlerts {

    /** 온라인 체크인은 대개 출발 24시간 전에 열린다. */
    private val CHECK_IN_OPENS = Duration.ofHours(24)

    /** 열린 뒤 이만큼 안에 한 번 알린다. 이보다 늦게 알리면 이미 했을 가능성이 크다. */
    private val CHECK_IN_WINDOW = Duration.ofHours(3)

    /** 공항·역으로 나설 즈음. */
    private val DEPARTURE_AHEAD = Duration.ofHours(3)
    private val DEPARTURE_WINDOW = Duration.ofHours(1)

    /** 숙소 알림을 보내는 아침 시각. */
    private val MORNING = LocalTime.of(9, 0)
    private val MORNING_WINDOW = Duration.ofHours(2)

    fun evaluate(bookings: List<Booking>, now: LocalDateTime): List<BookingAlert> =
        bookings.flatMap { booking -> alertsFor(booking, now) }

    private fun alertsFor(booking: Booking, now: LocalDateTime): List<BookingAlert> = buildList {
        when (booking.type) {
            BookingType.FLIGHT -> {
                checkInAlert(booking, now)?.let { add(it) }
                departureAlert(booking, now)?.let { add(it) }
            }
            BookingType.TRAIN, BookingType.BUS -> departureAlert(booking, now)?.let { add(it) }
            BookingType.LODGING -> {
                lodgingCheckIn(booking, now)?.let { add(it) }
                lodgingCheckOut(booking, now)?.let { add(it) }
            }
            else -> Unit
        }
    }

    /** 온라인 체크인이 열렸다. */
    private fun checkInAlert(booking: Booking, now: LocalDateTime): BookingAlert? {
        val opensAt = booking.startAt.minus(CHECK_IN_OPENS)
        if (!inWindow(now, opensAt, CHECK_IN_WINDOW)) return null

        return BookingAlert(
            bookingId = booking.id,
            tripId = booking.tripId,
            key = "checkin:${booking.id}",
            title = "온라인 체크인이 열렸습니다",
            message = buildString {
                append(booking.title)
                if (booking.routeLabel.isNotBlank()) append(" · ${booking.routeLabel}")
                append(" · 내일 ${booking.startAt.toLocalTime()} 출발")
            },
        )
    }

    /** 이제 나서야 할 때. */
    private fun departureAlert(booking: Booking, now: LocalDateTime): BookingAlert? {
        val leaveAt = booking.startAt.minus(DEPARTURE_AHEAD)
        if (!inWindow(now, leaveAt, DEPARTURE_WINDOW)) return null

        return BookingAlert(
            bookingId = booking.id,
            tripId = booking.tripId,
            key = "departure:${booking.id}",
            title = "${booking.startAt.toLocalTime()} 출발",
            message = buildString {
                append(booking.title)
                if (booking.routeLabel.isNotBlank()) append(" · ${booking.routeLabel}")
                if (booking.terminal.isNotBlank()) append(" · ${booking.terminal}")
                if (booking.seat.isNotBlank()) append(" · ${booking.seat}")
                append("\n세 시간 뒤 출발입니다.")
            },
        )
    }

    /** 오늘 들어갈 숙소. */
    private fun lodgingCheckIn(booking: Booking, now: LocalDateTime): BookingAlert? {
        if (now.toLocalDate() != booking.startAt.toLocalDate()) return null
        if (!inWindow(now, now.toLocalDate().atTime(MORNING), MORNING_WINDOW)) return null

        return BookingAlert(
            bookingId = booking.id,
            tripId = booking.tripId,
            key = "lodging-in:${booking.id}",
            title = "오늘 ${booking.startAt.toLocalTime()} 체크인",
            message = listOf(booking.title, booking.address)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        )
    }

    /** 오늘 나와야 하는 숙소. 체크아웃을 놓치면 추가 요금이 붙는다. */
    private fun lodgingCheckOut(booking: Booking, now: LocalDateTime): BookingAlert? {
        val endAt = booking.endAt ?: return null
        if (now.toLocalDate() != endAt.toLocalDate()) return null
        if (!inWindow(now, now.toLocalDate().atTime(MORNING), MORNING_WINDOW)) return null

        return BookingAlert(
            bookingId = booking.id,
            tripId = booking.tripId,
            key = "lodging-out:${booking.id}",
            title = "오늘 ${endAt.toLocalTime()} 체크아웃",
            message = "${booking.title}에서 오늘 나옵니다. 짐 맡길 곳을 미리 정해 두세요.",
        )
    }

    /** [at]부터 [window] 사이인지. 알림은 시간마다 도는 일꾼이 보내므로 구간으로 본다. */
    private fun inWindow(now: LocalDateTime, at: LocalDateTime, window: Duration): Boolean =
        !now.isBefore(at) && now.isBefore(at.plus(window))
}
