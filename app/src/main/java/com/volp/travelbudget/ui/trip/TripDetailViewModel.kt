package com.volp.travelbudget.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.settlement.Settlement
import com.volp.travelbudget.domain.summary.TripSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripDetailViewModel(
    private val repository: TripRepository,
    private val tripId: Long,
) : ViewModel() {

    val summary: StateFlow<TripSummary?> = repository.observeSummary(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val expenses: StateFlow<List<Expense>> = repository.observeExpenses(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** 보정 전 기준으로 본 해외 결제 승인액 합계. 0이면 보정할 것이 없다. */
    val foreignApproved: StateFlow<Long> = repository.observeExpenses(tripId)
        .map { Settlement.approvedForeignTotal(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0L)

    /**
     * 카드 명세서의 실제 청구 총액에 맞춰 해외 결제 금액을 다시 계산한다.
     * null을 넣으면 보정을 푼다.
     */
    fun applySettlement(billedTotalKrw: Long?) {
        viewModelScope.launch { repository.applySettlement(tripId, billedTotalKrw) }
    }

    fun deleteTrip(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
            onDone()
        }
    }

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch { repository.deleteExpense(expenseId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
