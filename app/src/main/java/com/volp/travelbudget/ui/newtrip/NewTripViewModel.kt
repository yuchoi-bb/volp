package com.volp.travelbudget.ui.newtrip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.exchange.ExchangeRateRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.budget.BudgetPredictor
import com.volp.travelbudget.domain.budget.CurrencyRates
import com.volp.travelbudget.domain.budget.DestinationCatalog
import com.volp.travelbudget.domain.budget.SpendingProfile
import com.volp.travelbudget.domain.budget.SpendingProfiles
import com.volp.travelbudget.domain.model.Destination
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class NewTripUiState(
    val title: String = "",
    val titleEditedByUser: Boolean = false,
    val region: Region = Region.JAPAN,
    val destinationKey: String = "tokyo",
    val customDestinationName: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusDays(3),
    val travelers: String = "2",
    val style: TravelStyle = TravelStyle.STANDARD,
    val includeFlight: Boolean = true,
    val currencyCode: String = "JPY",
    val exchangeRate: String = "9.3",
    /** 지난 여행에서 배운 내 씀씀이. 아직 배운 것이 없으면 비어 있다. */
    val profile: SpendingProfile = SpendingProfile(),
) {
    val isCustomDestination: Boolean
        get() = destinationKey.startsWith(DestinationCatalog.CUSTOM_KEY_PREFIX)

    val destination: Destination
        get() = DestinationCatalog.find(destinationKey) ?: DestinationCatalog.regionAverage(region)

    val destinationName: String
        get() = if (isCustomDestination) customDestinationName.ifBlank { region.label } else destination.name

    val travelerCount: Int
        get() = travelers.toIntOrNull()?.coerceAtLeast(1) ?: 1

    val nights: Int
        get() = ChronoUnit.DAYS.between(startDate, endDate).toInt().coerceAtLeast(0)

    /** 도시·스타일 표만 보고 낸 값. 보정 전후를 견주어 보여 주려고 남긴다. */
    val basePrediction: Map<ExpenseCategory, Long>
        get() = BudgetPredictor.predict(
            BudgetPredictor.Input(
                destination = destination,
                nights = nights,
                travelers = travelerCount,
                style = style,
                includeFlight = includeFlight,
            ),
        )

    val prediction: Map<ExpenseCategory, Long>
        get() = profile.apply(basePrediction)

    /** 보정으로 달라진 금액. 0이면 보정이 없었다는 뜻이다. */
    val profileDelta: Long
        get() = prediction.values.sum() - basePrediction.values.sum()

    val predictedTotal: Long get() = prediction.values.sum()

    val canSave: Boolean
        get() = title.isNotBlank() && !endDate.isBefore(startDate)
}

class NewTripViewModel(
    private val repository: TripRepository,
    private val exchangeRates: ExchangeRateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewTripUiState().withSuggestedTitle())
    val state: StateFlow<NewTripUiState> = _state.asStateFlow()

    init {
        // 기본값 대신 받아 둔 환율을 채워 넣는다.
        refreshRate(_state.value.currencyCode)
        learnFromPastTrips()
    }

    /** 지난 여행이 있으면 예측을 내 쪽으로 당긴다. */
    private fun learnFromPastTrips() {
        viewModelScope.launch {
            val profile = SpendingProfiles.learn(repository.finishedOutcomes())
            if (!profile.isEmpty) _state.update { it.copy(profile = profile) }
        }
    }

    private val _createdTripId = MutableStateFlow<Long?>(null)
    val createdTripId: StateFlow<Long?> = _createdTripId.asStateFlow()

    fun setTitle(value: String) = _state.update { it.copy(title = value, titleEditedByUser = true) }

    /** 통화가 바뀌면 그 통화의 최신 환율로 갈아 끼운다. */
    private fun refreshRate(code: String) {
        viewModelScope.launch {
            val rate = exchangeRates.rateFor(code)
            _state.update { current ->
                if (current.currencyCode == code) current.copy(exchangeRate = rate.toString()) else current
            }
        }
    }

    fun setRegion(region: Region) = _state.updateAndRefreshRate { current ->
        val first = DestinationCatalog.byRegion()[region]?.firstOrNull()
        current.copy(
            region = region,
            destinationKey = first?.key ?: DestinationCatalog.customKey(region),
            currencyCode = first?.currencyCode ?: "USD",
            exchangeRate = CurrencyRates.defaultRate(first?.currencyCode ?: "USD").toString(),
        ).withSuggestedTitle()
    }

    fun setDestination(key: String) = _state.updateAndRefreshRate { current ->
        val destination = DestinationCatalog.find(key)
        current.copy(
            destinationKey = key,
            currencyCode = destination?.currencyCode ?: current.currencyCode,
            exchangeRate = destination?.currencyCode
                ?.let { CurrencyRates.defaultRate(it).toString() }
                ?: current.exchangeRate,
        ).withSuggestedTitle()
    }

    fun setCustomDestinationName(value: String) =
        _state.update { it.copy(customDestinationName = value).withSuggestedTitle() }

    fun setStartDate(date: LocalDate) = _state.update { current ->
        // 시작일을 종료일 뒤로 옮기면 기간을 그대로 유지한 채 종료일도 따라 움직인다.
        if (date.isAfter(current.endDate)) {
            current.copy(startDate = date, endDate = date.plusDays(current.nights.toLong()))
        } else {
            current.copy(startDate = date)
        }
    }

    fun setEndDate(date: LocalDate) = _state.update { current ->
        if (date.isBefore(current.startDate)) current.copy(endDate = current.startDate) else current.copy(endDate = date)
    }

    fun setTravelers(value: String) = _state.update { it.copy(travelers = value) }

    fun setStyle(style: TravelStyle) = _state.update { it.copy(style = style) }

    fun setIncludeFlight(value: Boolean) = _state.update { it.copy(includeFlight = value) }

    fun setCurrency(code: String) {
        _state.update { it.copy(currencyCode = code, exchangeRate = CurrencyRates.defaultRate(code).toString()) }
        refreshRate(code)
    }

    fun setExchangeRate(value: String) = _state.update { it.copy(exchangeRate = value) }

    /** 상태를 바꾼 뒤 통화가 달라졌으면 환율도 새로 받아 온다. */
    private fun MutableStateFlow<NewTripUiState>.updateAndRefreshRate(
        transform: (NewTripUiState) -> NewTripUiState,
    ) {
        val before = value.currencyCode
        update(transform)
        val after = value.currencyCode
        if (before != after) refreshRate(after)
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val prediction = current.prediction
            val trip = Trip(
                title = current.title.trim(),
                destinationKey = current.destinationKey,
                destinationName = current.destinationName,
                region = current.region,
                startDate = current.startDate,
                endDate = current.endDate,
                travelers = current.travelerCount,
                style = current.style,
                includeFlight = current.includeFlight,
                currencyCode = current.currencyCode,
                exchangeRate = current.exchangeRate.toDoubleOrNull()
                    ?: CurrencyRates.defaultRate(current.currencyCode),
                predictedBudget = prediction,
                // 처음에는 예측값을 그대로 예산으로 쓰고, 예산 조정 화면에서 고칠 수 있다.
                plannedBudget = prediction,
            )
            _createdTripId.value = repository.createTrip(trip)
        }
    }
}

/** 사용자가 제목을 직접 고치기 전까지는 목적지 이름을 따라간다. */
private fun NewTripUiState.withSuggestedTitle(): NewTripUiState =
    if (titleEditedByUser) this else copy(title = "$destinationName 여행")
