package com.volp.travelbudget.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.ItineraryRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.itinerary.PlanFixity
import com.volp.travelbudget.domain.itinerary.PlanTextParser
import com.volp.travelbudget.domain.itinerary.PlannedStop
import com.volp.travelbudget.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 읽어 낸 일정 한 줄. 사람이 보고 넣을지 정한다. */
data class PlanRow(
    val key: String,
    val stop: PlannedStop,
    val checked: Boolean,
)

/** 하루치 묶음. */
data class PlanDayRow(
    val date: LocalDate,
    val insideTrip: Boolean,
    val rows: List<PlanRow>,
)

data class PlanImportState(
    val loading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val tripId: Long? = null,
    val input: String = "",
    val days: List<PlanDayRow> = emptyList(),
    /** 한 번 읽어 봤는지. 아무것도 못 읽었을 때 그렇게 말해 주려고 둔다. */
    val read: Boolean = false,
    val savedCount: Int? = null,
) {
    val trip: Trip? get() = trips.firstOrNull { it.id == tripId }

    val selected: List<PlanRow> get() = days.flatMap { it.rows }.filter { it.checked }

    val fixedCount: Int get() = selected.count { it.stop.fixity == PlanFixity.FIXED }

    val outsideCount: Int get() = days.filterNot { it.insideTrip }.sumOf { it.rows.size }

    val canRead: Boolean get() = input.isNotBlank()

    val canSave: Boolean get() = tripId != null && selected.isNotEmpty()
}

/**
 * AI가 짜 준 일정 글을 여행 일정으로 넣는다.
 *
 * 글을 읽는 일은 도메인이 하고, 여기서는 여행 날짜에 맞춰 놓고 사람이 고른 것만 저장한다.
 * 앱이 혼자 넣지 않는다 — AI가 지어낸 장소나 이미 정해 둔 것과 겹치는 줄이 섞이기 때문이다.
 */
class PlanImportViewModel(
    private val repository: TripRepository,
    private val itinerary: ItineraryRepository,
    private val tripId: Long?,
    private val sharedText: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanImportState())
    val state: StateFlow<PlanImportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val trips = repository.tripsOnce()
            val target = tripId ?: trips.firstOrNull()?.id
            _state.value = PlanImportState(
                loading = false,
                trips = trips,
                tripId = target,
                input = sharedText.orEmpty(),
            )
            // 공유로 들어온 글은 이미 사람이 골라 보낸 것이다. 한 번 더 누르게 하지 않는다.
            if (!sharedText.isNullOrBlank()) read()
        }
    }

    fun setInput(value: String) = _state.update { it.copy(input = value, read = false, days = emptyList()) }

    fun read() {
        val current = _state.value
        val trip = current.trip
        val days = PlanTextParser.parse(current.input, trip?.startDate ?: LocalDate.now())
        val dated = PlanTextParser.placeOn(
            days = days,
            tripStart = trip?.startDate ?: LocalDate.now(),
            tripEnd = trip?.endDate ?: LocalDate.now(),
        )

        _state.update { state ->
            state.copy(
                read = true,
                days = dated.map { day ->
                    PlanDayRow(
                        date = day.date,
                        insideTrip = day.insideTrip,
                        rows = day.stops.mapIndexed { index, stop ->
                            PlanRow(
                                key = "${day.date}-$index",
                                stop = stop,
                                // 여행 기간 밖의 날은 꺼 둔다. 일차를 잘못 읽었을 수 있다.
                                checked = day.insideTrip,
                            )
                        },
                    )
                },
            )
        }
    }

    fun toggle(key: String) = _state.update { state ->
        state.copy(
            days = state.days.map { day ->
                day.copy(rows = day.rows.map { if (it.key == key) it.copy(checked = !it.checked) else it })
            },
        )
    }

    fun checkAll(checked: Boolean) = _state.update { state ->
        state.copy(days = state.days.map { day -> day.copy(rows = day.rows.map { it.copy(checked = checked) }) })
    }

    /** 여행이 바뀌면 날짜도 다시 붙여야 한다. */
    fun setTrip(id: Long) {
        _state.update { it.copy(tripId = id) }
        if (_state.value.read) read()
    }

    fun save() {
        val current = _state.value
        val target = current.tripId ?: return

        viewModelScope.launch {
            var count = 0
            current.days.forEach { day ->
                day.rows.filter { it.checked }.forEach { row ->
                    itinerary.addStop(
                        tripId = target,
                        date = day.date,
                        name = row.stop.title,
                        startTime = row.stop.startTime?.toString(),
                        memo = row.stop.memo,
                        fixity = row.stop.fixity,
                    )
                    count++
                }
            }
            _state.update { it.copy(savedCount = count) }
        }
    }
}
