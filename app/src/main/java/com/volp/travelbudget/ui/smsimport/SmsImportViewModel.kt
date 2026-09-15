package com.volp.travelbudget.ui.smsimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.capture.SmsInbox
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.cardsms.CardMessageBatch
import com.volp.travelbudget.domain.cardsms.CardMessageParser
import com.volp.travelbudget.domain.cardsms.CardTransaction
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** 문자에서 읽어 낸 결제 한 줄. 사람이 보고 넣을지 정한다. */
data class SmsImportRow(
    val transaction: CardTransaction,
    val merchant: String,
    val category: ExpenseCategory,
    val checked: Boolean,
    /** 같은 날 같은 금액이 이미 들어와 있는지. 기본으로 꺼 둔다. */
    val alreadyThere: Boolean,
) {
    val key: String
        get() = listOf(
            transaction.issuer.name,
            transaction.cardLabel,
            transaction.occurredAt.toString(),
            transaction.amount.toString(),
            transaction.merchant,
        ).joinToString("|")
}

data class SmsImportState(
    val loading: Boolean = true,
    val rows: List<SmsImportRow> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val tripId: Long? = null,
    /** 문자함을 읽을 수 없는 빌드이거나 권한이 없을 때. */
    val needsPermission: Boolean = false,
    val savedCount: Int? = null,
) {
    val selected: List<SmsImportRow> get() = rows.filter { it.checked }

    val selectedTotalKrw: Long
        get() = selected.sumOf { row ->
            if (row.transaction.isOverseas) 0L else row.transaction.signedAmount.toLong()
        }

    val canSave: Boolean get() = tripId != null && selected.isNotEmpty()

    val trip: Trip? get() = trips.firstOrNull { it.id == tripId }
}

/**
 * 문자에서 지출을 모아 확인하고 넣는다.
 *
 * 들어오는 길이 둘이다. 문자 앱에서 여러 건을 골라 공유하거나, 문자 권한이 있는 빌드에서 여행
 * 기간의 문자함을 훑거나. 어느 쪽이든 사람이 목록을 보고 고른 것만 들어간다. 지난 여행을 나중에
 * 적을 때 이 길이 없으면 영수증 기억에만 기대야 한다.
 */
class SmsImportViewModel(
    private val repository: TripRepository,
    private val inbox: SmsInbox,
    private val tripId: Long?,
    private val sharedText: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(SmsImportState())
    val state: StateFlow<SmsImportState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            val trips = repository.tripsOnce()
            val target = tripId ?: trips.firstOrNull()?.id

            val transactions = when {
                !sharedText.isNullOrBlank() ->
                    CardMessageBatch.parseAll(sharedText, LocalDateTime.now())
                target != null -> scanInbox(trips.firstOrNull { it.id == target })
                else -> emptyList()
            }

            _state.value = SmsImportState(
                loading = false,
                rows = transactions.map { row(it, target) },
                trips = trips,
                tripId = target,
                needsPermission = sharedText.isNullOrBlank() && !inbox.canRead,
            )
        }
    }

    /** 여행 기간의 문자만 읽어 결제로 읽히는 것만 남긴다. */
    private suspend fun scanInbox(trip: Trip?): List<CardTransaction> {
        if (trip == null) return emptyList()

        return inbox.messagesBetween(trip.startDate, trip.endDate)
            .mapNotNull { message ->
                CardMessageParser.parse(message.body, message.receivedAt, message.sender)
            }
            .filter { it.isRecordable }
            .distinctBy { "${it.occurredAt}|${it.amount}|${it.merchant}" }
    }

    private suspend fun row(transaction: CardTransaction, tripId: Long?): SmsImportRow {
        val resolved = repository.resolveMerchant(transaction.merchant)
        val amountKrw = if (transaction.isOverseas) 0L else transaction.signedAmount.toLong()
        val already = tripId != null && amountKrw != 0L &&
            repository.hasExpenseLike(tripId, transaction.occurredAt.toLocalDate(), amountKrw)

        return SmsImportRow(
            transaction = transaction,
            merchant = resolved.displayName,
            category = resolved.category ?: ExpenseCategory.ETC,
            // 이미 있는 것은 꺼 둔다. 사람이 굳이 다시 넣겠다면 켜면 된다.
            checked = !already,
            alreadyThere = already,
        )
    }

    fun toggle(key: String) = _state.update { current ->
        current.copy(
            rows = current.rows.map { if (it.key == key) it.copy(checked = !it.checked) else it },
        )
    }

    fun setCategory(key: String, category: ExpenseCategory) = _state.update { current ->
        current.copy(
            rows = current.rows.map { if (it.key == key) it.copy(category = category) else it },
        )
    }

    fun checkAll(checked: Boolean) = _state.update { current ->
        current.copy(rows = current.rows.map { it.copy(checked = checked) })
    }

    fun setTrip(id: Long) {
        _state.update { it.copy(tripId = id) }
        // 여행이 바뀌면 '이미 있는 것'도 달라진다.
        viewModelScope.launch {
            val current = _state.value
            val rows = current.rows.map { existing ->
                val refreshed = row(existing.transaction, current.tripId)
                // 사람이 손으로 고른 항목과 체크는 지킨다.
                refreshed.copy(category = existing.category, checked = existing.checked)
            }
            _state.update { it.copy(rows = rows) }
        }
    }

    fun save() {
        val current = _state.value
        val target = current.tripId ?: return

        viewModelScope.launch {
            var count = 0
            current.selected.forEach { row ->
                repository.recordCardTransaction(
                    transaction = row.transaction,
                    tripId = target,
                    category = row.category,
                    memo = row.merchant,
                )
                count++
            }
            _state.update { it.copy(savedCount = count) }
        }
    }
}
