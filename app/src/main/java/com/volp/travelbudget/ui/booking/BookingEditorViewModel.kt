package com.volp.travelbudget.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.BookingRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.travel.PlaceLookup
import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.booking.BookingType
import com.volp.travelbudget.domain.travel.CityCoordinates
import com.volp.travelbudget.ui.trip.point
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class BookingEditorUiState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val type: BookingType = BookingType.FLIGHT,
    val title: String = "",
    val provider: String = "",
    val confirmationCode: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: String = "09:00",
    val endDate: LocalDate = LocalDate.now(),
    val endTime: String = "11:00",
    val useEnd: Boolean = true,
    val fromName: String = "",
    val fromCode: String = "",
    val toName: String = "",
    val toCode: String = "",
    val address: String = "",
    val seat: String = "",
    val gate: String = "",
    val terminal: String = "",
    val memo: String = "",
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank() && parseTime(startTime) != null
}

/** `HH:mm`을 읽는다. 틀린 값이면 null. */
internal fun parseTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value.trim()) }.getOrNull()

class BookingEditorViewModel(
    private val bookingRepository: BookingRepository,
    private val tripRepository: TripRepository,
    private val placeLookup: PlaceLookup,
    private val tripId: Long,
    private val bookingId: Long,
    private val defaultDate: LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(BookingEditorUiState())
    val state: StateFlow<BookingEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = if (bookingId > 0L) bookingRepository.find(bookingId) else null
            _state.value = existing?.toUiState() ?: BookingEditorUiState(
                loading = false,
                startDate = defaultDate,
                endDate = defaultDate,
            )
        }
    }

    fun setType(value: BookingType) = _state.update { it.copy(type = value) }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun setProvider(value: String) = _state.update { it.copy(provider = value) }

    fun setConfirmationCode(value: String) = _state.update { it.copy(confirmationCode = value) }

    fun setStartDate(value: LocalDate) = _state.update { current ->
        // 끝나는 날이 시작보다 앞서지 않게 같이 움직인다.
        if (current.endDate.isBefore(value)) {
            current.copy(startDate = value, endDate = value)
        } else {
            current.copy(startDate = value)
        }
    }

    fun setStartTime(value: String) = _state.update { it.copy(startTime = value) }

    fun setEndDate(value: LocalDate) = _state.update { current ->
        if (value.isBefore(current.startDate)) current else current.copy(endDate = value)
    }

    fun setEndTime(value: String) = _state.update { it.copy(endTime = value) }

    fun setUseEnd(value: Boolean) = _state.update { it.copy(useEnd = value) }

    fun setFromName(value: String) = _state.update { it.copy(fromName = value) }

    fun setFromCode(value: String) = _state.update { it.copy(fromCode = value.uppercase()) }

    fun setToName(value: String) = _state.update { it.copy(toName = value) }

    fun setToCode(value: String) = _state.update { it.copy(toCode = value.uppercase()) }

    fun setAddress(value: String) = _state.update { it.copy(address = value) }

    fun setSeat(value: String) = _state.update { it.copy(seat = value) }

    fun setGate(value: String) = _state.update { it.copy(gate = value) }

    fun setTerminal(value: String) = _state.update { it.copy(terminal = value) }

    fun setMemo(value: String) = _state.update { it.copy(memo = value) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            // 주소나 도착지 이름으로 좌표를 찾아 두면 지도와 길 안내에 함께 쓴다.
            val trip = tripRepository.getTrip(tripId)
            val near = trip?.let { it.point() ?: CityCoordinates.find(it.destinationKey) }
            val query = listOf(current.address, current.toName, current.title)
                .firstOrNull { it.isNotBlank() }
            val point = query?.let { placeLookup.find(it, near)?.point }

            bookingRepository.save(
                Booking(
                    id = bookingId,
                    tripId = tripId,
                    type = current.type,
                    title = current.title.trim(),
                    provider = current.provider.trim(),
                    confirmationCode = current.confirmationCode.trim(),
                    startAt = LocalDateTime.of(
                        current.startDate,
                        parseTime(current.startTime) ?: LocalTime.of(9, 0),
                    ),
                    endAt = if (current.useEnd) {
                        LocalDateTime.of(
                            current.endDate,
                            parseTime(current.endTime) ?: LocalTime.of(11, 0),
                        )
                    } else {
                        null
                    },
                    fromName = current.fromName.trim(),
                    fromCode = current.fromCode.trim(),
                    toName = current.toName.trim(),
                    toCode = current.toCode.trim(),
                    address = current.address.trim(),
                    seat = current.seat.trim(),
                    gate = current.gate.trim(),
                    terminal = current.terminal.trim(),
                    memo = current.memo.trim(),
                    point = point,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        if (bookingId <= 0L) return
        viewModelScope.launch {
            bookingRepository.delete(bookingId)
            _state.update { it.copy(saved = true) }
        }
    }
}

private fun Booking.toUiState() = BookingEditorUiState(
    loading = false,
    isEditing = true,
    type = type,
    title = title,
    provider = provider,
    confirmationCode = confirmationCode,
    startDate = startAt.toLocalDate(),
    startTime = startAt.toLocalTime().toString().take(5),
    endDate = endAt?.toLocalDate() ?: startAt.toLocalDate(),
    endTime = endAt?.toLocalTime()?.toString()?.take(5) ?: "11:00",
    useEnd = endAt != null,
    fromName = fromName,
    fromCode = fromCode,
    toName = toName,
    toCode = toCode,
    address = address,
    seat = seat,
    gate = gate,
    terminal = terminal,
    memo = memo,
)
