package com.volp.travelbudget.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.budget.SpendingProfile
import com.volp.travelbudget.domain.budget.SpendingProfiles
import com.volp.travelbudget.domain.stats.PastTripReports
import com.volp.travelbudget.domain.stats.PastTripsSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PastTripsState(
    val loading: Boolean = true,
    val summary: PastTripsSummary = PastTripsSummary(emptyList()),
    /** 이 결산에서 배운 계수. 다음 여행 예측에 그대로 쓰인다. */
    val profile: SpendingProfile = SpendingProfile(),
)

/**
 * 다녀온 여행의 실제 사용 경비를 모아 본다.
 *
 * 여행 하나만 보면 그 여행이 유별났는지 알 수 없다. 겹쳐 놓아야 "나는 하루에 얼마쯤 쓰는 사람인가"가
 * 나오고, 그 값이 다음 여행 예산의 출발점이 된다.
 */
class PastTripsViewModel(private val repository: TripRepository) : ViewModel() {

    val today: LocalDate = LocalDate.now()

    private val _state = MutableStateFlow(PastTripsState())
    val state: StateFlow<PastTripsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val summary = PastTripReports.finished(repository.pastTrips(), today)
            val profile = SpendingProfiles.learn(repository.finishedOutcomes(today))
            _state.value = PastTripsState(loading = false, summary = summary, profile = profile)
        }
    }
}
