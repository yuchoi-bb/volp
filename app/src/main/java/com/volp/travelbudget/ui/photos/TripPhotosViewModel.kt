package com.volp.travelbudget.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.photos.PhotoStore
import com.volp.travelbudget.data.photos.TripPhoto
import com.volp.travelbudget.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripPhotosUiState(
    val tripTitle: String = "",
    /** 여행 기간에 기기에서 찍은 사진. 권한이 없으면 비어 있다. */
    val devicePhotos: List<TripPhoto> = emptyList(),
    /** 사용자가 직접 붙인 사진. */
    val savedPhotos: List<TripPhoto> = emptyList(),
    val loadingDevicePhotos: Boolean = false,
)

class TripPhotosViewModel(
    private val repository: TripRepository,
    private val photoStore: PhotoStore,
    private val tripId: Long,
) : ViewModel() {

    private val devicePhotos = MutableStateFlow<List<TripPhoto>>(emptyList())
    private val loading = MutableStateFlow(false)

    val state: StateFlow<TripPhotosUiState> = combine(
        repository.observeTrip(tripId),
        photoStore.observeSaved(tripId),
        devicePhotos,
        loading,
    ) { trip, saved, device, isLoading ->
        TripPhotosUiState(
            tripTitle = trip?.title.orEmpty(),
            devicePhotos = device,
            savedPhotos = saved,
            loadingDevicePhotos = isLoading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TripPhotosUiState())

    /** 사진 권한을 받은 뒤에 부른다. */
    fun loadDevicePhotos() {
        viewModelScope.launch {
            val trip = repository.getTrip(tripId) ?: return@launch
            loading.value = true
            devicePhotos.value = runCatching {
                photoStore.findDevicePhotos(trip.startDate, trip.endDate)
            }.getOrDefault(emptyList())
            loading.value = false
        }
    }

    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { photoStore.attach(tripId, expenseId = null, source = it) }
        }
    }

    fun remove(photoId: Long) {
        viewModelScope.launch { photoStore.remove(photoId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
