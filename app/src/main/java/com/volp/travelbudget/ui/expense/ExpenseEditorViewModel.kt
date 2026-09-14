package com.volp.travelbudget.ui.expense

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.photos.PhotoStore
import com.volp.travelbudget.data.photos.TripPhoto
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.budget.CurrencyRates
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.summary.TripSummaries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpenseEditorUiState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val category: ExpenseCategory = ExpenseCategory.FOOD,
    val date: LocalDate = LocalDate.now(),
    val memo: String = "",
    /** 현지 통화로 입력하는 중인지. 끄면 원화로 바로 입력한다. */
    val useLocalCurrency: Boolean = false,
    val currencyCode: String = "KRW",
    val exchangeRate: Double = 1.0,
    val amountInput: String = "",
    val saved: Boolean = false,
) {
    private val amount: Double get() = amountInput.replace(",", "").toDoubleOrNull() ?: 0.0

    /** 화면에 보여 주고 저장할 원화 금액. */
    val amountKrw: Long
        get() = if (useLocalCurrency) TripSummaries.toKrw(amount, exchangeRate) else amount.toLong()

    val originalAmount: Double? get() = if (useLocalCurrency) amount else null

    val canSave: Boolean get() = amount != 0.0
}

class ExpenseEditorViewModel(
    private val repository: TripRepository,
    private val photoStore: PhotoStore,
    private val tripId: Long,
    private val expenseId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseEditorUiState())
    val state: StateFlow<ExpenseEditorUiState> = _state.asStateFlow()

    /** 이미 저장된 지출에 붙어 있는 영수증 사진. */
    val receipts: StateFlow<List<TripPhoto>> =
        if (expenseId > 0L) {
            photoStore.observeReceipts(expenseId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())
        } else {
            MutableStateFlow(emptyList())
        }

    /** 아직 저장 전이라 붙일 곳이 없는 사진. 저장할 때 함께 붙인다. */
    private val _queuedReceipts = MutableStateFlow<List<Uri>>(emptyList())
    val queuedReceipts: StateFlow<List<Uri>> = _queuedReceipts.asStateFlow()

    init {
        viewModelScope.launch {
            val trip = repository.getTrip(tripId)
            val existing = if (expenseId > 0L) repository.getExpense(expenseId) else null
            val tripCurrency = trip?.currencyCode ?: "KRW"
            val tripRate = trip?.exchangeRate ?: 1.0

            _state.value = if (existing != null) {
                ExpenseEditorUiState(
                    loading = false,
                    isEditing = true,
                    category = existing.category,
                    date = existing.date,
                    memo = existing.memo,
                    useLocalCurrency = existing.enteredInForeignCurrency,
                    currencyCode = existing.currencyCode,
                    exchangeRate = tripRate,
                    amountInput = if (existing.enteredInForeignCurrency) {
                        (existing.originalAmount ?: 0.0).toString()
                    } else {
                        existing.amountKrw.toString()
                    },
                )
            } else {
                ExpenseEditorUiState(
                    loading = false,
                    date = defaultDateFor(trip?.startDate, trip?.endDate),
                    // 해외 여행이면 현지 통화 입력을 기본으로 켜 둔다.
                    useLocalCurrency = !CurrencyRates.isKrw(tripCurrency),
                    currencyCode = tripCurrency,
                    exchangeRate = tripRate,
                )
            }
        }
    }

    fun setCategory(value: ExpenseCategory) = _state.update { it.copy(category = value) }

    fun setDate(value: LocalDate) = _state.update { it.copy(date = value) }

    fun setMemo(value: String) = _state.update { it.copy(memo = value) }

    fun setAmountInput(value: String) = _state.update { it.copy(amountInput = value) }

    fun setUseLocalCurrency(value: Boolean) = _state.update { it.copy(useLocalCurrency = value) }

    fun setExchangeRate(value: String) = _state.update {
        it.copy(exchangeRate = value.toDoubleOrNull() ?: it.exchangeRate)
    }

    /** 영수증 사진을 붙인다. 아직 저장하지 않은 지출이면 저장할 때까지 들고 있는다. */
    fun addReceipt(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (expenseId > 0L) {
            viewModelScope.launch {
                uris.forEach { photoStore.attach(tripId, expenseId, it) }
            }
        } else {
            _queuedReceipts.update { it + uris }
        }
    }

    fun removeReceipt(photoId: Long) {
        viewModelScope.launch { photoStore.remove(photoId) }
    }

    fun removeQueuedReceipt(uri: Uri) {
        _queuedReceipts.update { list -> list.filterNot { it == uri } }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val expense = Expense(
                id = expenseId,
                tripId = tripId,
                category = current.category,
                amountKrw = current.amountKrw,
                originalAmount = current.originalAmount,
                currencyCode = if (current.useLocalCurrency) current.currencyCode else "KRW",
                date = current.date,
                memo = current.memo.trim(),
            )
            if (current.isEditing) {
                repository.updateExpense(expense)
            } else {
                val newId = repository.addExpense(expense)
                _queuedReceipts.value.forEach { photoStore.attach(tripId, newId, it) }
                _queuedReceipts.value = emptyList()
            }
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        if (expenseId <= 0L) return
        viewModelScope.launch {
            repository.deleteExpense(expenseId)
            _state.update { it.copy(saved = true) }
        }
    }
}

private const val STOP_TIMEOUT_MS = 5_000L

/** 여행 기간 안이면 오늘을, 아니면 여행 시작일을 기본 날짜로 쓴다. */
private fun defaultDateFor(start: LocalDate?, end: LocalDate?): LocalDate {
    val today = LocalDate.now()
    if (start == null || end == null) return today
    return if (today.isBefore(start) || today.isAfter(end)) start else today
}