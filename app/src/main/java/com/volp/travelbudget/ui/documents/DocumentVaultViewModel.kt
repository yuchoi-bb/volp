package com.volp.travelbudget.ui.documents

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.DocumentRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.document.DocumentKind
import com.volp.travelbudget.domain.document.TravelDocument
import com.volp.travelbudget.domain.document.TravelDocuments
import com.volp.travelbudget.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DocumentVaultState(
    val documents: List<TravelDocument> = emptyList(),
    val attention: List<TravelDocument> = emptyList(),
    val trips: List<Trip> = emptyList(),
) {
    val isEmpty: Boolean get() = documents.isEmpty()

    fun tripTitle(tripId: Long?): String? =
        tripId?.let { id -> trips.firstOrNull { it.id == id }?.title }
}

class DocumentVaultViewModel(
    private val documents: DocumentRepository,
    trips: TripRepository,
) : ViewModel() {

    val today: LocalDate = LocalDate.now()

    val state: StateFlow<DocumentVaultState> =
        combine(documents.observeAll(), trips.observeTrips()) { docs, tripList ->
            DocumentVaultState(
                documents = TravelDocuments.sort(docs, today),
                attention = TravelDocuments.needsAttention(docs, today),
                trips = tripList,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DocumentVaultState())

    /** 목록에서는 번호를 가려 둔다. 사용자가 누른 것만 잠깐 보여 준다. */
    private val _revealed = MutableStateFlow<Set<Long>>(emptySet())
    val revealed: StateFlow<Set<Long>> = _revealed

    fun toggleReveal(id: Long) {
        _revealed.value = if (id in _revealed.value) _revealed.value - id else _revealed.value + id
    }

    fun save(
        id: Long,
        kind: DocumentKind,
        title: String,
        number: String,
        expiresOn: LocalDate?,
        memo: String,
        tripId: Long?,
        image: Uri?,
        existingPath: String?,
    ) {
        viewModelScope.launch {
            val path = image?.let { documents.copyImage(it) } ?: existingPath
            documents.save(
                TravelDocument(
                    id = id,
                    kind = kind,
                    title = title.trim(),
                    number = number.trim(),
                    expiresOn = expiresOn,
                    memo = memo.trim(),
                    tripId = tripId,
                    filePath = path,
                ).let { fresh ->
                    // 고치는 중이면 uid와 만든 시각을 지킨다. 새로 만든 값으로 덮으면 다른 기록이 된다.
                    if (id > 0L) {
                        val existing = documents.find(id)
                        fresh.copy(
                            uid = existing?.uid.orEmpty(),
                            createdAt = existing?.createdAt ?: 0L,
                        )
                    } else {
                        fresh
                    }
                },
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { documents.delete(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
