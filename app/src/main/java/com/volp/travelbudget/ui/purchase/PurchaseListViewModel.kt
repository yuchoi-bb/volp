package com.volp.travelbudget.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.PurchaseRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.purchase.Purchase
import com.volp.travelbudget.domain.purchase.PurchaseStatus
import com.volp.travelbudget.domain.purchase.Purchases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 한 줄에 보여 줄 구매와, 그 구매에 얽힌 여행. */
data class PurchaseRow(
    val purchase: Purchase,
    val trip: Trip?,
) {
    /** 출발 전에 못 받을 것 같은지. 여행을 안 정했으면 재촉할 것도 없다. */
    val late: Boolean get() = trip != null && purchase.arrivesLate(trip.startDate)
}

data class PurchaseListState(
    val open: List<PurchaseRow> = emptyList(),
    val done: List<PurchaseRow> = emptyList(),
) {
    val isEmpty: Boolean get() = open.isEmpty() && done.isEmpty()

    /** 아직 안 온 것들의 합. 이미 나간 돈이 얼마인지 한눈에 보여 준다. */
    val openTotalKrw: Long get() = Purchases.openTotalKrw(open.map { it.purchase })

    val lateCount: Int get() = open.count { it.late }
}

class PurchaseListViewModel(
    private val purchases: PurchaseRepository,
    trips: TripRepository,
    tripId: Long?,
) : ViewModel() {

    private val source = if (tripId == null) purchases.observeAll() else purchases.observeByTrip(tripId)

    val state: StateFlow<PurchaseListState> =
        combine(source, trips.observeTrips()) { items, allTrips ->
            val byId = allTrips.associateBy { it.id }
            val rows = items.map { PurchaseRow(it, it.tripId?.let(byId::get)) }
            PurchaseListState(
                open = rows.filter { it.purchase.status.isOpen },
                done = rows.filterNot { it.purchase.status.isOpen },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PurchaseListState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val today: LocalDate = LocalDate.now()

    fun setStatus(purchase: Purchase, status: PurchaseStatus) {
        viewModelScope.launch { purchases.save(purchase.copy(status = status)) }
    }

    fun delete(purchase: Purchase) {
        viewModelScope.launch { purchases.delete(purchase.id) }
    }

    /** 도착한 물건 값을 그 여행 가계부에 넣는다. */
    fun pushToLedger(purchase: Purchase) {
        viewModelScope.launch {
            val expenseId = purchases.pushToLedger(purchase)
            _message.value = when {
                expenseId != null -> "가계부에 넣었습니다"
                purchase.tripId == null -> "먼저 어느 여행 것인지 골라 주세요"
                purchase.expenseId != null -> "이미 가계부에 있습니다"
                else -> "금액을 넣어야 가계부로 보낼 수 있습니다"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
