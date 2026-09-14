package com.volp.travelbudget.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.ItineraryRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.travel.LocationProvider
import com.volp.travelbudget.data.travel.PlaceLookup
import com.volp.travelbudget.data.weather.WeatherRepository
import com.volp.travelbudget.domain.itinerary.DayPlan
import com.volp.travelbudget.domain.itinerary.DayPlanBuilder
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.packing.PackingAdvisor
import com.volp.travelbudget.domain.packing.PackingItem
import com.volp.travelbudget.domain.travel.CityCoordinates
import com.volp.travelbudget.domain.travel.Geo
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.RouteAdvisor
import com.volp.travelbudget.domain.travel.TransportSuggestion
import com.volp.travelbudget.domain.weather.DailyForecast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TripPlanUiState(
    val trip: Trip? = null,
    val days: List<DayPlan> = emptyList(),
    val forecasts: List<DailyForecast> = emptyList(),
    val packingItems: List<PackingItem> = emptyList(),
    val checkedItems: Set<String> = emptySet(),
    val loadingWeather: Boolean = false,
    /** 지금 내 위치. 권한이 없거나 못 받으면 null. */
    val currentLocation: GeoPoint? = null,
    /** 오늘 남은 일정 중 다음에 갈 곳. */
    val nextStop: ItineraryStop? = null,
    /** 지금 위치에서 다음 장소까지의 안내. */
    val toNextStop: TransportSuggestion? = null,
    val searching: Boolean = false,
)

class TripPlanViewModel(
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val placeLookup: PlaceLookup,
    private val locationProvider: LocationProvider,
    private val weatherRepository: WeatherRepository,
    private val tripId: Long,
) : ViewModel() {

    private val forecasts = MutableStateFlow<List<DailyForecast>>(emptyList())
    private val loadingWeather = MutableStateFlow(false)
    private val currentLocation = MutableStateFlow<GeoPoint?>(null)
    private val searching = MutableStateFlow(false)

    val state: StateFlow<TripPlanUiState> = combine(
        tripRepository.observeTrip(tripId),
        itineraryRepository.observeStops(tripId),
        itineraryRepository.observePackingChecks(tripId),
        combine(forecasts, loadingWeather, currentLocation, searching) { f, l, c, s -> Quad(f, l, c, s) },
    ) { trip, stops, checks, extras ->
        if (trip == null) return@combine TripPlanUiState()

        val days = DayPlanBuilder.build(stops, trip.dateRange(), trip.region)
        val next = nextStopOf(stops)

        TripPlanUiState(
            trip = trip,
            days = days,
            forecasts = extras.forecasts,
            packingItems = PackingAdvisor.suggest(trip, extras.forecasts),
            checkedItems = checks,
            loadingWeather = extras.loading,
            currentLocation = extras.location,
            nextStop = next,
            toNextStop = suggestionTo(next, extras.location, trip),
            searching = extras.searching,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripPlanUiState())

    init {
        loadWeather()
        refreshLocation()
    }

    /**
     * 장소를 일정에 넣는다. 이름으로 좌표를 찾아 두면 동선과 이동수단 안내가 붙는다.
     * 못 찾아도 이름만으로 넣는다.
     */
    fun addStop(date: LocalDate, name: String, time: String?, memo: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            searching.value = true
            val trip = tripRepository.getTrip(tripId)
            val near = trip?.let { it.point() ?: CityCoordinates.find(it.destinationKey) }
            // 도시 이름을 붙여 검색하면 같은 이름의 다른 가게로 새지 않는다.
            val place = placeLookup.find("${trip?.destinationName.orEmpty()} $name".trim(), near)
                ?: placeLookup.find(name, near)
            searching.value = false

            itineraryRepository.addStop(
                tripId = tripId,
                date = date,
                name = name,
                address = place?.address.orEmpty(),
                point = place?.point,
                startTime = time,
                memo = memo,
            )
        }
    }

    fun deleteStop(stopId: Long) {
        viewModelScope.launch { itineraryRepository.deleteStop(stopId) }
    }

    fun move(stop: ItineraryStop, up: Boolean) {
        viewModelScope.launch { itineraryRepository.move(stop, up) }
    }

    fun togglePacking(itemName: String, checked: Boolean) {
        viewModelScope.launch { itineraryRepository.setPackingCheck(tripId, itemName, checked) }
    }

    fun refreshLocation() {
        viewModelScope.launch { currentLocation.value = locationProvider.current() }
    }

    private fun loadWeather() {
        viewModelScope.launch {
            val trip = tripRepository.getTrip(tripId) ?: return@launch
            val point = trip.point()
                ?: CityCoordinates.find(trip.destinationKey)
                ?: placeLookup.find(trip.destinationName)?.point
                ?: return@launch

            // 한 번 찾은 좌표는 여행에 남겨 다음부터 다시 찾지 않는다.
            if (trip.point() == null) {
                tripRepository.updateTrip(
                    trip.copy(latitude = point.latitude, longitude = point.longitude),
                )
            }

            loadingWeather.value = true
            forecasts.value = weatherRepository.forecast(
                point = point,
                // 예보는 보름 남짓까지만 나온다. 그보다 먼 날짜는 비워 둔다.
                startDate = maxOf(trip.startDate, LocalDate.now()),
                endDate = minOf(trip.endDate, LocalDate.now().plusDays(FORECAST_LIMIT_DAYS)),
            )
            loadingWeather.value = false
        }
    }

    /** 오늘 일정 중 아직 가지 않은 첫 장소. 오늘 일정이 없으면 다음 날의 첫 장소. */
    private fun nextStopOf(stops: List<ItineraryStop>): ItineraryStop? {
        val today = LocalDate.now()
        val now = LocalTime.now()

        val todays = stops.filter { it.date == today }.sortedBy { it.sortOrder }
        val upcoming = todays.firstOrNull { stop ->
            val time = stop.startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            time == null || time.isAfter(now)
        }
        if (upcoming != null) return upcoming

        return stops.filter { it.date.isAfter(today) }
            .minWithOrNull(compareBy({ it.date }, { it.sortOrder }))
    }

    private fun suggestionTo(
        stop: ItineraryStop?,
        location: GeoPoint?,
        trip: Trip,
    ): TransportSuggestion? {
        val target = stop?.point ?: return null
        val from = location ?: return null
        return RouteAdvisor.suggest(Geo.distanceMeters(from, target), trip.region)
    }

    private data class Quad(
        val forecasts: List<DailyForecast>,
        val loading: Boolean,
        val location: GeoPoint?,
        val searching: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FORECAST_LIMIT_DAYS = 15L
    }
}

private fun Trip.point(): GeoPoint? {
    val lat = latitude
    val lon = longitude
    return if (lat != null && lon != null) GeoPoint(lat, lon) else null
}

private fun Trip.dateRange(): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    var day = startDate
    while (!day.isAfter(endDate)) {
        dates += day
        day = day.plusDays(1)
    }
    return dates
}
