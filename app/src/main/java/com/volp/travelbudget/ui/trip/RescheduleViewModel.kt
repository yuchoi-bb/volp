package com.volp.travelbudget.ui.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.RescheduleRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.budget.BudgetPredictor
import com.volp.travelbudget.domain.budget.DestinationCatalog
import com.volp.travelbudget.domain.budget.SpendingProfiles
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.trip.ReschedulePlan
import com.volp.travelbudget.domain.trip.RescheduleItem
import com.volp.travelbudget.domain.trip.TripReschedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RescheduleState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val items: List<RescheduleItem> = emptyList(),
    val newStart: LocalDate = LocalDate.now(),
    val newEnd: LocalDate = LocalDate.now(),
    /** 고정된 것도 함께 밀지. 예약을 이미 다시 잡았을 때만 켠다. */
    val moveFixed: Boolean = false,
    /** 새 기간 밖으로 나가는 것을 안으로 당길지. */
    val pullInside: Boolean = true,
    val recalculateBudget: Boolean = false,
    val saved: Boolean = false,
) {
    val plan: ReschedulePlan?
        get() = trip?.let {
            TripReschedule.plan(
                items = items,
                oldStart = it.startDate,
                oldEnd = it.endDate,
                newStart = newStart,
                newEnd = newEnd,
                moveFixed = moveFixed,
                pullInside = pullInside,
            )
        }

    val changed: Boolean
        get() = trip != null && (trip.startDate != newStart || trip.endDate != newEnd)

    val validRange: Boolean get() = !newEnd.isBefore(newStart)

    val canSave: Boolean get() = changed && validRange
}

/**
 * 여행 날짜가 바뀌었을 때 그 안의 일정을 함께 옮기는 화면의 상태.
 *
 * 날짜만 고쳐 놓으면 일정표는 옛 날짜에 그대로 남는다. 그렇다고 앱이 전부 밀어 버리면 이미
 * 잡아 둔 투어와 항공편이 장부에서만 옮겨진다. 무엇이 어디로 가는지 먼저 보여 주고 사람이 정한다.
 */
class RescheduleViewModel(
    private val repository: TripRepository,
    private val reschedule: RescheduleRepository,
    private val tripId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(RescheduleState())
    val state: StateFlow<RescheduleState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = repository.getTrip(tripId)
            _state.value = RescheduleState(
                loading = false,
                trip = trip,
                items = reschedule.itemsOf(tripId),
                newStart = trip?.startDate ?: LocalDate.now(),
                newEnd = trip?.endDate ?: LocalDate.now(),
            )
        }
    }

    fun setStart(value: LocalDate) = _state.update { state ->
        // 시작이 끝을 넘으면 기간이 뒤집힌다. 끝도 같은 폭으로 민다.
        val end = if (value.isAfter(state.newEnd)) value else state.newEnd
        state.copy(newStart = value, newEnd = end).withBudgetDefault()
    }

    fun setEnd(value: LocalDate) = _state.update { state ->
        state.copy(newEnd = value).withBudgetDefault()
    }

    fun setMoveFixed(value: Boolean) = _state.update { it.copy(moveFixed = value) }

    fun setPullInside(value: Boolean) = _state.update { it.copy(pullInside = value) }

    fun setRecalculateBudget(value: Boolean) = _state.update { it.copy(recalculateBudget = value) }

    /**
     * 기간이 달라지면 예산도 달라져야 한다. 다만 사람이 손으로 조정해 둔 예산은 지운다는 뜻이라
     * 그때는 켜지 않는다.
     */
    private fun RescheduleState.withBudgetDefault(): RescheduleState {
        val trip = trip ?: return this
        val untouched = trip.plannedBudget == trip.predictedBudget
        val lengthChanged = plan?.lengthChanged == true
        return copy(recalculateBudget = lengthChanged && untouched)
    }

    fun save() {
        val current = _state.value
        val trip = current.trip ?: return
        if (!current.canSave) return

        viewModelScope.launch {
            val budget = if (current.recalculateBudget) newBudget(trip, current) else null
            reschedule.apply(
                tripId = tripId,
                newStart = current.newStart,
                newEnd = current.newEnd,
                moveFixed = current.moveFixed,
                pullInside = current.pullInside,
                budget = budget,
            )
            _state.update { it.copy(saved = true) }
        }
    }

    /** 여행을 만들 때와 같은 방식으로 다시 계산한다. 지난 여행에서 배운 보정도 그대로 쓴다. */
    private suspend fun newBudget(trip: Trip, state: RescheduleState): Map<ExpenseCategory, Long>? {
        val destination = DestinationCatalog.find(trip.destinationKey) ?: return null
        val base = BudgetPredictor.predict(
            BudgetPredictor.Input(
                destination = destination,
                nights = TripReschedule.nights(state.newStart, state.newEnd),
                travelers = trip.travelers,
                style = trip.style,
                includeFlight = trip.includeFlight,
            ),
        )
        return SpendingProfiles.learn(repository.finishedOutcomes()).apply(base)
    }
}
