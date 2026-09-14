package com.volp.travelbudget.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.summary.TripSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
