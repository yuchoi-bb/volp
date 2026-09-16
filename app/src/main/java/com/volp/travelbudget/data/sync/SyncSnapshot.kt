package com.volp.travelbudget.data.sync

import com.volp.travelbudget.data.local.DeletionEntity
import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.cash.CashTopUp
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.purchase.Purchase
import java.time.LocalDate

/** 하루 메모. 여행과 날짜로 짝지어진다. */
data class DayNote(
    val date: LocalDate,
    val text: String,
    val updatedAt: Long,
)

/** 준비물 체크 한 줄. */
data class PackingCheck(
    val itemName: String,
    val checked: Boolean,
    val updatedAt: Long,
)

/** 여행 하나와 그 안의 모든 기록. 동기화는 이 덩어리 단위로 오간다. */
data class TripBundle(
    val trip: Trip,
    val expenses: List<Expense> = emptyList(),
    val bookings: List<Booking> = emptyList(),
    val stops: List<ItineraryStop> = emptyList(),
    val notes: List<DayNote> = emptyList(),
    val packing: List<PackingCheck> = emptyList(),
    val cash: List<CashTopUp> = emptyList(),
)

/**
 * 구매 하나와 그것이 붙은 여행.
 *
 * 아직 어느 여행 것인지 안 정한 구매도 있어 여행 꾸러미 안에 넣지 않는다. 기기마다 행 번호가
 * 다르므로 여행은 uid로 가리킨다.
 */
data class PurchaseRecord(
    val purchase: Purchase,
    val tripUid: String? = null,
)

/**
 * 드라이브에 올라가는 기록 한 벌.
 *
 * @param deletions 지운 기록의 흔적. 이것이 없으면 다른 기기에 남아 있던 기록이 되살아난다.
 */
data class SyncSnapshot(
    val version: Int = FORMAT_VERSION,
    val exportedAt: Long = 0L,
    val trips: List<TripBundle> = emptyList(),
    val purchases: List<PurchaseRecord> = emptyList(),
    val deletions: List<DeletionEntity> = emptyList(),
) {
    /** 이 한 벌에 담긴 기록 수. 파일을 주고받을 때 몇 건인지 말해 주려고 쓴다. */
    val recordCount: Int
        get() = trips.sumOf { bundle ->
            1 + bundle.expenses.size + bundle.bookings.size + bundle.stops.size +
                bundle.notes.size + bundle.packing.size + bundle.cash.size
        } + purchases.size

    companion object {
        const val FORMAT_VERSION = 4
        const val FILE_NAME = "volp-backup.json"
        const val CSV_FILE_NAME = "volp-expenses.csv"
    }
}
