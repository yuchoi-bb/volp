package com.volp.travelbudget.ui.trip

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.photos.PhotoStore
import com.volp.travelbudget.data.photos.TripPhoto
import com.volp.travelbudget.data.repository.BookingRepository
import com.volp.travelbudget.data.repository.ItineraryRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.travel.LocationProvider
import com.volp.travelbudget.data.travel.PlaceLookup
import com.volp.travelbudget.data.weather.WeatherRepository
import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.itinerary.DayTimeline
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.itinerary.TimelineBuilder
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.packing.PackingAdvisor
import com.volp.travelbudget.domain.packing.PackingItem
import com.volp.travelbudget.domain.settlement.Settlement
import com.volp.travelbudget.domain.summary.TripSummary
import com.volp.travelbudget.domain.travel.CityCoordinates
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.weather.DailyForecast
import com.volp.travelbudget.domain.weather.RainAlert
import com.volp.travelbudget.domain.weather.RainWatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class TripUiState(
    val trip: Trip? = null,
    val summary: TripSummary? = null,
    val expenses: List<Expense> = emptyList(),
    val foreignApproved: Long = 0L,
    val timelines: List<DayTimeline> = emptyList(),
    val bookings: List<Booking> = emptyList(),
    val notes: Map<LocalDate, String> = emptyMap(),
    val forecasts: List<DailyForecast> = emptyList(),
    val savedPhotos: List<TripPhoto> = emptyList(),
    val devicePhotos: List<TripPhoto> = emptyList(),
    val packingItems: List<PackingItem> = emptyList(),
    val checkedItems: Set<String> = emptySet(),
    val currentLocation: GeoPoint? = null,
    val rainAlert: RainAlert? = null,
    val loadingWeather: Boolean = false,
    val searchingPlace: Boolean = false,
) {
    fun timelineFor(date: LocalDate): DayTimeline? = timelines.firstOrNull { it.date == date }

    fun forecastFor(date: LocalDate): DailyForecast? = forecasts.firstOrNull { it.date == date }

    /** 오늘 쓴 하루 경비. 항공·숙박은 하루 씀씀이로 보지 않는다. */
    fun spentToday(today: LocalDate): Long = expenses
        .filter { it.date == today && it.category !in UPFRONT }
        .sumOf { it.amountKrw }

    fun expensesOn(date: LocalDate): List<Expense> = expenses.filter { it.date == date }

    private companion object {
        val UPFRONT = setOf(ExpenseCategory.FLIGHT, ExpenseCategory.LODGING)
    }
}

/**
 * 여행 화면 네 갈래(오늘·전체일정·가계부·기록)가 함께 쓰는 상태.
 *
 * 탭마다 따로 읽으면 같은 것을 네 번 읽게 되고 탭을 옮길 때마다 화면이 잠깐 비어 보인다.
 * 그래서 한 자리에서 모아 두고 탭은 필요한 부분만 꺼내 쓴다.
 */
class TripViewModel(
    private val repository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val bookingRepository: BookingRepository,
    private val photoStore: PhotoStore,
    private val placeLookup: PlaceLookup,
    private val locationProvider: LocationProvider,
    private val weatherRepository: WeatherRepository,
    private val tripId: Long,
) : ViewModel() {

    private val forecasts = MutableStateFlow<List<DailyForecast>>(emptyList())
    private val devicePhotos = MutableStateFlow<List<TripPhoto>>(emptyList())
    private val location = MutableStateFlow<GeoPoint?>(null)
    private val rain = MutableStateFlow<RainAlert?>(null)
    private val loadingWeather = MutableStateFlow(false)
    private val searchingPlace = MutableStateFlow(false)

    private val ledger = combine(
        repository.observeSummary(tripId),
        repository.observeExpenses(tripId),
    ) { summary, expenses ->
        Ledger(summary, expenses, Settlement.approvedForeignTotal(expenses))
    }

    private val plan = combine(
        itineraryRepository.observeStops(tripId),
        bookingRepository.observeBookings(tripId),
        bookingRepository.observeNotes(tripId),
    ) { stops, bookings, notes -> Plan(stops, bookings, notes) }

    private val record = combine(
        photoStore.observeSaved(tripId),
        itineraryRepository.observePackingChecks(tripId),
    ) { photos, checks -> Record(photos, checks) }

    private val live = combine(
        forecasts,
        devicePhotos,
        location,
        rain,
        combine(loadingWeather, searchingPlace) { a, b -> a to b },
    ) { forecastList, photos, point, rainAlert, flags ->
        Live(forecastList, photos, point, rainAlert, flags.first, flags.second)
    }

    val state: StateFlow<TripUiState> = combine(ledger, plan, record, live) { l, p, r, v ->
        val trip = l.summary?.trip
        TripUiState(
            trip = trip,
            summary = l.summary,
            expenses = l.expenses,
            foreignApproved = l.foreignApproved,
            timelines = if (trip == null) {
                emptyList()
            } else {
                TimelineBuilder.build(p.stops, p.bookings, trip.dates(), trip.region)
            },
            bookings = p.bookings,
            notes = p.notes,
            forecasts = v.forecasts,
            savedPhotos = r.photos,
            devicePhotos = v.devicePhotos,
            packingItems = if (trip == null) emptyList() else PackingAdvisor.suggest(trip, v.forecasts),
            checkedItems = r.checks,
            currentLocation = v.location,
            rainAlert = v.rain,
            loadingWeather = v.loadingWeather,
            searchingPlace = v.searching,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripUiState())

    init {
        refreshLocation()
        loadWeather()
    }

    // ---- 위치와 날씨 ----

    fun refreshLocation() {
        viewModelScope.launch {
            location.value = locationProvider.current()
            checkRain()
        }
    }

    private fun checkRain() {
        viewModelScope.launch {
            val point = location.value ?: tripPoint() ?: return@launch
            val slots = weatherRepository.precipitationSlots(point)
            rain.value = RainWatch.evaluate(slots, LocalDateTime.now())
        }
    }

    private fun loadWeather() {
        viewModelScope.launch {
            val trip = repository.getTrip(tripId) ?: return@launch
            val point = trip.point()
                ?: CityCoordinates.find(trip.destinationKey)
                ?: placeLookup.find(trip.destinationName)?.point
                ?: return@launch

            // 한 번 찾은 좌표는 여행에 남겨 다음부터 다시 찾지 않는다.
            if (trip.point() == null) {
                repository.updateTrip(trip.copy(latitude = point.latitude, longitude = point.longitude))
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

    private suspend fun tripPoint(): GeoPoint? {
        val trip = repository.getTrip(tripId) ?: return null
        return trip.point() ?: CityCoordinates.find(trip.destinationKey)
    }

    // ---- 일정 ----

    fun addStop(date: LocalDate, name: String, time: String?, memo: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            searchingPlace.value = true
            val trip = repository.getTrip(tripId)
            val near = trip?.let { it.point() ?: CityCoordinates.find(it.destinationKey) }
            // 도시 이름을 붙여 찾으면 같은 이름의 다른 가게로 새지 않는다.
            val place = placeLookup.find("${trip?.destinationName.orEmpty()} $name".trim(), near)
                ?: placeLookup.find(name, near)
            searchingPlace.value = false

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

    fun moveStop(stop: ItineraryStop, up: Boolean) {
        viewModelScope.launch { itineraryRepository.move(stop, up) }
    }

    fun deleteBooking(bookingId: Long) {
        viewModelScope.launch { bookingRepository.delete(bookingId) }
    }

    // ---- 기록 ----

    fun loadDevicePhotos() {
        viewModelScope.launch {
            val trip = repository.getTrip(tripId) ?: return@launch
            devicePhotos.value = runCatching {
                photoStore.findDevicePhotos(trip.startDate, trip.endDate)
            }.getOrDefault(emptyList())
        }
    }

    fun attachPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { photoStore.attach(tripId, expenseId = null, source = it) }
        }
    }

    fun removePhoto(photoId: Long) {
        viewModelScope.launch { photoStore.remove(photoId) }
    }

    fun saveNote(date: LocalDate, text: String) {
        viewModelScope.launch { bookingRepository.saveNote(tripId, date, text) }
    }

    fun togglePacking(itemName: String, checked: Boolean) {
        viewModelScope.launch { itineraryRepository.setPackingCheck(tripId, itemName, checked) }
    }

    // ---- 가계부 ----

    fun deleteExpense(expenseId: Long) {
        viewModelScope.launch { repository.deleteExpense(expenseId) }
    }

    fun applySettlement(billedTotalKrw: Long?) {
        viewModelScope.launch { repository.applySettlement(tripId, billedTotalKrw) }
    }

    fun deleteTrip(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
            onDone()
        }
    }

    private data class Ledger(
        val summary: TripSummary?,
        val expenses: List<Expense>,
        val foreignApproved: Long,
    )

    private data class Plan(
        val stops: List<ItineraryStop>,
        val bookings: List<Booking>,
        val notes: Map<LocalDate, String>,
    )

    private data class Record(
        val photos: List<TripPhoto>,
        val checks: Set<String>,
    )

    private data class Live(
        val forecasts: List<DailyForecast>,
        val devicePhotos: List<TripPhoto>,
        val location: GeoPoint?,
        val rain: RainAlert?,
        val loadingWeather: Boolean,
        val searching: Boolean,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FORECAST_LIMIT_DAYS = 15L
    }
}

fun Trip.point(): GeoPoint? {
    val lat = latitude
    val lon = longitude
    return if (lat != null && lon != null) GeoPoint(lat, lon) else null
}

fun Trip.dates(): List<LocalDate> {
    val result = mutableListOf<LocalDate>()
    var day = startDate
    while (!day.isAfter(endDate)) {
        result += day
        day = day.plusDays(1)
    }
    return result
}
