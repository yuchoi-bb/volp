package com.volp.travelbudget.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.repository.TripWithSpending
import com.volp.travelbudget.data.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripListViewModel(
    repository: TripRepository,
    private val settings: AppSettings,
) : ViewModel() {

    /** 고른 차례는 기억해 둔다. 앱을 다시 열어도 같은 순서로 보인다. */
    val sort: StateFlow<TripSort> = settings.settings
        .map { TripSort.fromName(it.tripSort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripSort.DEFAULT)

    val trips: StateFlow<List<TripWithSpending>> =
        combine(repository.observeTripsWithSpending(), sort) { trips, order -> order.sort(trips) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** 아직 여행에 넣지 않은 카드 결제 건수. 목록 화면 배지에 쓴다. */
    val pendingCount: StateFlow<Int> = repository.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    fun changeSort(value: TripSort) {
        viewModelScope.launch { settings.setTripSort(value.name) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
