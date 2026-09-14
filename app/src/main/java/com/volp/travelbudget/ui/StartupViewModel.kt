package com.volp.travelbudget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 앱을 열었을 때 어디로 보낼지 정하는 데 쓰는 최소한의 정보. */
data class Startup(
    val resolved: Boolean = false,
    /** 오늘이 여행 기간 안인 여행. 없으면 null. */
    val ongoingTripId: Long? = null,
)

class StartupViewModel(repository: TripRepository) : ViewModel() {

    private val _state = MutableStateFlow(Startup())
    val state: StateFlow<Startup> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val ongoing = repository.tripsOnce().firstOrNull {
                !today.isBefore(it.startDate) && !today.isAfter(it.endDate)
            }
            _state.value = Startup(resolved = true, ongoingTripId = ongoing?.id)
        }
    }
}
