package com.volp.travelbudget.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.local.PendingTransaction
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.classify.RuleBasedMerchantClassifier
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 미확인함의 한 줄. 어느 여행·항목에 넣을지 미리 골라 둔 값을 함께 들고 있다. */
data class InboxRow(
    val transaction: PendingTransaction,
    val suggestedTripId: Long?,
    val suggestedCategory: ExpenseCategory,
)

data class InboxUiState(
    val mine: List<InboxRow> = emptyList(),
    /** 내 명의가 아닌 결제. 여행과 무관한 경우가 많아 따로 모아 둔다. */
    val others: List<InboxRow> = emptyList(),
    val trips: List<Trip> = emptyList(),
) {
    val isEmpty: Boolean get() = mine.isEmpty() && others.isEmpty()
}

class InboxViewModel(private val repository: TripRepository) : ViewModel() {

    val state: StateFlow<InboxUiState> = combine(
        repository.observePendingTransactions(),
        repository.observeTrips(),
    ) { pending, trips ->
        val rows = pending.map { transaction ->
            InboxRow(
                transaction = transaction,
                suggestedTripId = suggestTrip(transaction, trips)?.id,
                suggestedCategory = RuleBasedMerchantClassifier.classify(transaction.merchant)
                    ?: ExpenseCategory.ETC,
            )
        }
        InboxUiState(
            mine = rows.filterNot { it.transaction.foreignHolder },
            others = rows.filter { it.transaction.foreignHolder },
            trips = trips,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InboxUiState())

    fun accept(pendingId: Long, tripId: Long, category: ExpenseCategory) {
        viewModelScope.launch { repository.acceptPending(pendingId, tripId, category) }
    }

    fun ignore(pendingId: Long) {
        viewModelScope.launch { repository.ignorePending(pendingId) }
    }

    /** 결제 시각이 여행 기간 안에 들어가는 여행을 고른다. */
    private fun suggestTrip(transaction: PendingTransaction, trips: List<Trip>): Trip? {
        val date = transaction.occurredAt.toLocalDate()
        return trips.firstOrNull { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
