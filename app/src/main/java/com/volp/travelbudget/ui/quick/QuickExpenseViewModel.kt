package com.volp.travelbudget.ui.quick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.alert.BudgetAlertNotifier
import com.volp.travelbudget.data.exchange.ExchangeRateRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.budget.CurrencyRates
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.PaymentMethod
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.summary.TripSummaries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class QuickExpenseUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val trips: List<Trip> = emptyList(),
    /** 숫자판으로 눌러 넣은 금액. 소수점 없이 정수로만 받는다. */
    val amountDigits: String = "",
    val category: ExpenseCategory = ExpenseCategory.FOOD,
    val useLocalCurrency: Boolean = false,
    val currencyCode: String = "KRW",
    val exchangeRate: Double = 1.0,
    val memo: String = "",
    /** 이 화면에서 연달아 기록한 건수. 여러 건을 몰아 넣을 때 확인용으로 보여 준다. */
    val savedCount: Int = 0,
    val lastSavedKrw: Long? = null,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    val amountKrw: Long
        get() = if (useLocalCurrency) {
            TripSummaries.toKrw(amount.toDouble(), exchangeRate)
        } else {
            amount
        }

    val canSave: Boolean get() = trip != null && amount > 0L
}

/**
 * 현금 결제를 빠르게 남기기 위한 화면의 상태.
 *
 * 카드 결제는 문자로 저절로 들어오지만 현금과 동전은 그때그때 직접 넣어야 한다. 여행 중에
 * 가장 자주 하는 동작이라 금액을 누르고 항목만 고르면 바로 저장되게 했다. 저장한 뒤에도
 * 화면에 그대로 머물러 여러 건을 이어서 넣을 수 있다.
 */
class QuickExpenseViewModel(
    private val repository: TripRepository,
    private val exchangeRates: ExchangeRateRepository,
    private val alertNotifier: BudgetAlertNotifier,
    private val requestedTripId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickExpenseUiState())
    val state: StateFlow<QuickExpenseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val trips = repository.tripsOnce()
            val trip = trips.firstOrNull { it.id == requestedTripId } ?: pickCurrent(trips)
            _state.value = QuickExpenseUiState(
                loading = false,
                trip = trip,
                trips = trips,
            ).withTrip(trip)

            trip?.let { applyRate(it) }
        }
    }

    fun press(digit: String) = _state.update { current ->
        // 자릿수를 제한해 실수로 0을 여러 번 눌러도 엉뚱한 금액이 되지 않게 한다.
        val next = (current.amountDigits + digit).trimStart('0').take(MAX_DIGITS)
        current.copy(amountDigits = next)
    }

    fun backspace() = _state.update { it.copy(amountDigits = it.amountDigits.dropLast(1)) }

    fun clear() = _state.update { it.copy(amountDigits = "") }

    fun setCategory(category: ExpenseCategory) = _state.update { it.copy(category = category) }

    fun setMemo(memo: String) = _state.update { it.copy(memo = memo) }

    fun setUseLocalCurrency(value: Boolean) = _state.update { it.copy(useLocalCurrency = value) }

    fun selectTrip(tripId: Long) {
        val trip = _state.value.trips.firstOrNull { it.id == tripId } ?: return
        _state.update { it.withTrip(trip) }
        applyRate(trip)
    }

    fun save() {
        val current = _state.value
        val trip = current.trip ?: return
        if (!current.canSave) return

        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    tripId = trip.id,
                    category = current.category,
                    amountKrw = current.amountKrw,
                    originalAmount = if (current.useLocalCurrency) current.amount.toDouble() else null,
                    currencyCode = if (current.useLocalCurrency) current.currencyCode else "KRW",
                    date = defaultDateFor(trip),
                    memo = current.memo.trim(),
                    exchangeRate = if (current.useLocalCurrency) current.exchangeRate else null,
                    // 빠른 입력은 카드 문자가 못 잡는 현금을 넣으려고 만든 화면이다.
                    method = PaymentMethod.CASH,
                ),
            )

            _state.update {
                it.copy(
                    amountDigits = "",
                    memo = "",
                    savedCount = it.savedCount + 1,
                    lastSavedKrw = current.amountKrw,
                )
            }

            alertNotifier.check(trip.id)
        }
    }

    private fun applyRate(trip: Trip) {
        viewModelScope.launch {
            val rate = exchangeRates.rateFor(trip.currencyCode)
            _state.update { it.copy(exchangeRate = rate) }
        }
    }

    /** 오늘 진행 중인 여행을 먼저 고르고, 없으면 가장 가까운 여행을 쓴다. */
    private fun pickCurrent(trips: List<Trip>): Trip? {
        val today = LocalDate.now()
        return trips.firstOrNull { !today.isBefore(it.startDate) && !today.isAfter(it.endDate) }
            ?: trips.minByOrNull { trip ->
                kotlin.math.abs(
                    java.time.temporal.ChronoUnit.DAYS.between(today, trip.startDate),
                )
            }
    }

    private companion object {
        const val MAX_DIGITS = 9
    }
}

private fun QuickExpenseUiState.withTrip(trip: Trip?): QuickExpenseUiState = copy(
    trip = trip,
    currencyCode = trip?.currencyCode ?: "KRW",
    // 해외 여행이면 현지 통화로 넣는 일이 많다.
    useLocalCurrency = trip != null && !CurrencyRates.isKrw(trip.currencyCode),
    amountDigits = "",
)

/** 여행 기간 안이면 오늘, 아니면 여행 시작일. */
private fun defaultDateFor(trip: Trip): LocalDate {
    val today = LocalDate.now()
    return if (today.isBefore(trip.startDate) || today.isAfter(trip.endDate)) trip.startDate else today
}
