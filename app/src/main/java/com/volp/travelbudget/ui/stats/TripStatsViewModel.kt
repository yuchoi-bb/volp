package com.volp.travelbudget.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.stats.TripReport
import com.volp.travelbudget.domain.stats.TripStatistics
import com.volp.travelbudget.domain.stats.TripStatisticsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TripStatsUiState(
    val trip: Trip? = null,
    val stats: TripStatistics? = null,
    /** 항공·숙박을 포함해서 볼지. 끄면 하루 씀씀이가 잘 보인다. */
    val includeUpfront: Boolean = true,
    val report: String = "",
)

class TripStatsViewModel(
    repository: TripRepository,
    tripId: Long,
) : ViewModel() {

    private val includeUpfront = MutableStateFlow(true)

    val state: StateFlow<TripStatsUiState> = combine(
        repository.observeTrip(tripId),
        repository.observeExpenses(tripId),
        includeUpfront,
    ) { trip, expenses, withUpfront ->
        if (trip == null) return@combine TripStatsUiState(includeUpfront = withUpfront)

        val stats = TripStatisticsCalculator.of(trip, expenses, withUpfront)
        TripStatsUiState(
            trip = trip,
            stats = stats,
            includeUpfront = withUpfront,
            // 리포트는 늘 전체 기준으로 만든다. 일부만 담긴 요약을 남에게 보내면 오해를 산다.
            report = TripReport.compose(
                trip = trip,
                stats = TripStatisticsCalculator.of(trip, expenses, includeUpfront = true),
                plannedTotal = trip.totalPlanned,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripStatsUiState())

    fun setIncludeUpfront(value: Boolean) {
        includeUpfront.value = value
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
