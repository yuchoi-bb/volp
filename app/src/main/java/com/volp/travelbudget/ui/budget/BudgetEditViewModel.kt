package com.volp.travelbudget.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.budget.BudgetPredictor
import com.volp.travelbudget.domain.budget.DestinationCatalog
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetEditUiState(
    val loading: Boolean = true,
    val tripTitle: String = "",
    val amounts: Map<ExpenseCategory, String> = emptyMap(),
    val predicted: Map<ExpenseCategory, Long> = emptyMap(),
    val saved: Boolean = false,
) {
    val total: Long
        get() = amounts.values.sumOf { it.toLongOrNull() ?: 0L }
}

class BudgetEditViewModel(
    private val repository: TripRepository,
    private val tripId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetEditUiState())
    val state: StateFlow<BudgetEditUiState> = _state.asStateFlow()

    private var trip: Trip? = null

    init {
        viewModelScope.launch {
            val loaded = repository.getTrip(tripId) ?: return@launch
            trip = loaded
            _state.value = BudgetEditUiState(
                loading = false,
                tripTitle = loaded.title,
                amounts = ExpenseCategory.entries.associateWith { loaded.plannedFor(it).toString() },
                predicted = loaded.predictedBudget,
            )
        }
    }

    fun setAmount(category: ExpenseCategory, value: String) = _state.update { current ->
        current.copy(amounts = current.amounts + (category to value))
    }

    /** 지금 여행 조건으로 다시 예측해 예산을 덮어쓴다. */
    fun resetToPrediction() {
        val loaded = trip ?: return
        val destination = DestinationCatalog.find(loaded.destinationKey)
            ?: DestinationCatalog.regionAverage(loaded.region)
        val prediction = BudgetPredictor.predict(
            BudgetPredictor.Input(
                destination = destination,
                nights = loaded.nights,
                travelers = loaded.travelers,
                style = loaded.style,
                includeFlight = loaded.includeFlight,
            ),
        )
        _state.update { current ->
            current.copy(
                amounts = ExpenseCategory.entries.associateWith { (prediction[it] ?: 0L).toString() },
                predicted = prediction,
            )
        }
    }

    fun save() {
        val loaded = trip ?: return
        val current = _state.value
        viewModelScope.launch {
            val planned = ExpenseCategory.entries.associateWith {
                current.amounts[it]?.toLongOrNull() ?: 0L
            }
            repository.updateTrip(
                loaded.copy(plannedBudget = planned, predictedBudget = current.predicted),
            )
            _state.update { it.copy(saved = true) }
        }
    }
}
