package com.volp.travelbudget.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.repository.TripWithSpending
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.util.moved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripListViewModel(
    private val repository: TripRepository,
    private val settings: AppSettings,
) : ViewModel() {

    /** 고른 차례는 기억해 둔다. 앱을 다시 열어도 같은 순서로 보인다. */
    val sort: StateFlow<TripSort> = settings.settings
        .map { TripSort.fromName(it.tripSort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripSort.DEFAULT)

    /**
     * 끌어 옮기는 중의 차례.
     *
     * 손가락을 따라 화면이 바로 움직여야 하는데 저장한 값이 돌아오기를 기다리면 한 박자 늦는다.
     * 그동안은 이 값이 화면의 차례를 정한다.
     */
    private val dragging = MutableStateFlow<List<Long>?>(null)

    val trips: StateFlow<List<TripWithSpending>> =
        combine(repository.observeTripsWithSpending(), sort, dragging) { list, order, pending ->
            val sorted = order.sort(list)
            if (pending == null) sorted else sorted.sortedBy { pending.indexOf(it.trip.id) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /**
     * 같은 여행이 두 벌로 남아 있는 묶음.
     *
     * 두 기기에서 같은 여행을 따로 만든 뒤 기록을 주고받으면 이렇게 된다. 목록에서 바로 알려
     * 주고 한 번에 합칠 수 있게 한다.
     */
    val duplicates: StateFlow<List<List<Trip>>> = repository.observeDuplicateTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** 아직 여행에 넣지 않은 카드 결제 건수. 목록 화면 배지에 쓴다. */
    val pendingCount: StateFlow<Int> = repository.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** 겹치는 여행을 한 벌로 합친다. 붙어 있던 기록은 남는 여행으로 옮겨진다. */
    fun mergeDuplicates() {
        viewModelScope.launch { repository.mergeDuplicateTrips() }
    }

    fun changeSort(value: TripSort) {
        viewModelScope.launch { settings.setTripSort(value.name) }
    }

    /** 끌어 옮기는 동안 차례를 바꿔 둔다. 아직 저장하지 않는다. */
    fun moveTrip(from: Int, to: Int) {
        val current = dragging.value ?: trips.value.map { it.trip.id }
        dragging.value = current.moved(from, to)
    }

    /** 손가락을 뗐을 때 그 차례를 저장한다. */
    fun commitOrder() {
        val order = dragging.value ?: return
        viewModelScope.launch {
            repository.reorderTrips(order)
            // 저장한 차례가 목록으로 돌아오면 임시 차례는 필요 없다.
            dragging.value = null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
