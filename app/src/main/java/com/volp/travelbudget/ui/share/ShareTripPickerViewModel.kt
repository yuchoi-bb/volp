package com.volp.travelbudget.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.model.Trip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ShareTripPickerState(val trips: List<Trip> = emptyList()) {

    /** 여행이 하나뿐이면 묻지 않는다. */
    val onlyTripId: Long? get() = trips.singleOrNull()?.id

    /**
     * 예약 편집 화면이 처음 보여 줄 날짜.
     *
     * 이미 시작한 여행이면 오늘이, 아직이면 출발일이 맞다.
     */
    fun defaultDateFor(tripId: Long): LocalDate {
        val trip = trips.firstOrNull { it.id == tripId } ?: return LocalDate.now()
        val today = LocalDate.now()
        return if (today.isBefore(trip.startDate)) trip.startDate else today
    }
}

class ShareTripPickerViewModel(repository: TripRepository) : ViewModel() {

    val state: StateFlow<ShareTripPickerState> = repository.observeTrips()
        .map { ShareTripPickerState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShareTripPickerState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
