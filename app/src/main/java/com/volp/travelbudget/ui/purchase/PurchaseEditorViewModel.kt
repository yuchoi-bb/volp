package com.volp.travelbudget.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.repository.PurchaseRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.purchase.Purchase
import com.volp.travelbudget.domain.purchase.PurchaseKind
import com.volp.travelbudget.domain.purchase.PurchaseStatus
import com.volp.travelbudget.domain.purchase.PurchaseTextParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PurchaseEditorState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val trips: List<Trip> = emptyList(),
    val tripId: Long? = null,
    val kind: PurchaseKind = PurchaseKind.OTHER,
    val title: String = "",
    val merchant: String = "",
    val amountText: String = "",
    val currencyCode: String = "KRW",
    val orderedOn: LocalDate = LocalDate.now(),
    val hasEta: Boolean = false,
    val eta: LocalDate = LocalDate.now(),
    val status: PurchaseStatus = PurchaseStatus.ORDERED,
    val orderNumber: String = "",
    val trackingNumber: String = "",
    val carrier: String = "",
    val memo: String = "",
    val sourceText: String = "",
    /** 공유로 들어온 글에서 읽어 낸 값인지. 화면에 안내를 띄우는 데 쓴다. */
    val fromShare: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()

    /** 출발 전에 못 받을 것 같은 여행. 고르는 순간 알려 준다. */
    val lateForTrip: Trip?
        get() = trips.firstOrNull { it.id == tripId }
            ?.takeIf { hasEta && !eta.isBefore(it.startDate) }
}

/**
 * 여행 전에 산 것을 넣고 고치는 화면의 상태.
 *
 * 문자를 공유해서 들어온 경우에는 읽어 낸 값을 미리 채워 둔다. 잘못 읽었을 수 있으므로
 * 원문을 함께 보여 주고, 저장은 사람이 누른다.
 */
class PurchaseEditorViewModel(
    private val purchases: PurchaseRepository,
    private val trips: TripRepository,
    private val purchaseId: Long,
    private val sharedText: String?,
    private val presetTripId: Long?,
) : ViewModel() {

    private val _state = MutableStateFlow(PurchaseEditorState())
    val state: StateFlow<PurchaseEditorState> = _state.asStateFlow()

    /** 고칠 때 건드리지 않는 값들. 저장할 때 그대로 돌려 놓는다. */
    private var loaded: Purchase? = null

    init {
        viewModelScope.launch {
            val allTrips = trips.tripsOnce()
            when {
                purchaseId > 0L -> purchases.find(purchaseId)?.let { fill(it, allTrips, fromShare = false) }
                !sharedText.isNullOrBlank() -> {
                    val parsed = PurchaseTextParser.parse(sharedText)
                    val tripId = presetTripId ?: guessTrip(allTrips, parsed.eta)?.id
                    fill(purchases.fromParsed(parsed, tripId), allTrips, fromShare = true)
                }
                else -> _state.update {
                    it.copy(
                        loading = false,
                        trips = allTrips,
                        tripId = presetTripId ?: guessTrip(allTrips, null)?.id,
                    )
                }
            }
        }
    }

    /**
     * 어느 여행 것인지 어림한다.
     *
     * 도착 예정일 뒤에 시작하는 여행 가운데 가장 가까운 것을 고른다. 여행 전에 사 두는 물건이니
     * 그 물건을 들고 갈 여행은 보통 그 다음 여행이다.
     */
    private fun guessTrip(trips: List<Trip>, eta: LocalDate?): Trip? {
        val today = LocalDate.now()
        val from = eta ?: today
        return trips.filter { !it.endDate.isBefore(from) }.minByOrNull { it.startDate }
            ?: trips.filter { !it.endDate.isBefore(today) }.minByOrNull { it.startDate }
    }

    private fun fill(purchase: Purchase, allTrips: List<Trip>, fromShare: Boolean) {
        loaded = purchase
        _state.update {
            PurchaseEditorState(
                loading = false,
                isEditing = purchase.id > 0L,
                trips = allTrips,
                tripId = purchase.tripId,
                kind = purchase.kind,
                title = purchase.title,
                merchant = purchase.merchant,
                amountText = if (purchase.amountKrw > 0L) purchase.amountKrw.toString() else "",
                currencyCode = purchase.currencyCode,
                orderedOn = purchase.orderedOn ?: LocalDate.now(),
                hasEta = purchase.eta != null,
                eta = purchase.eta ?: LocalDate.now().plusDays(DEFAULT_ETA_DAYS),
                status = purchase.status,
                orderNumber = purchase.orderNumber,
                trackingNumber = purchase.trackingNumber,
                carrier = purchase.carrier,
                memo = purchase.memo,
                sourceText = purchase.sourceText,
                fromShare = fromShare,
            )
        }
    }

    fun setTrip(tripId: Long?) = _state.update { it.copy(tripId = tripId) }

    fun setKind(kind: PurchaseKind) = _state.update { it.copy(kind = kind) }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun setMerchant(value: String) = _state.update { it.copy(merchant = value) }

    fun setAmount(value: String) =
        _state.update { it.copy(amountText = value.filter { ch -> ch.isDigit() }) }

    fun setOrderedOn(date: LocalDate) = _state.update { it.copy(orderedOn = date) }

    fun setHasEta(value: Boolean) = _state.update { it.copy(hasEta = value) }

    fun setEta(date: LocalDate) = _state.update { it.copy(eta = date) }

    fun setStatus(status: PurchaseStatus) = _state.update { it.copy(status = status) }

    fun setOrderNumber(value: String) = _state.update { it.copy(orderNumber = value) }

    fun setTrackingNumber(value: String) = _state.update { it.copy(trackingNumber = value) }

    fun setCarrier(value: String) = _state.update { it.copy(carrier = value) }

    fun setMemo(value: String) = _state.update { it.copy(memo = value) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            val base = loaded ?: Purchase(title = current.title)
            purchases.save(
                base.copy(
                    tripId = current.tripId,
                    kind = current.kind,
                    title = current.title.trim(),
                    merchant = current.merchant.trim(),
                    amountKrw = current.amountText.toLongOrNull() ?: 0L,
                    currencyCode = current.currencyCode,
                    orderedOn = current.orderedOn,
                    eta = current.eta.takeIf { current.hasEta },
                    status = current.status,
                    orderNumber = current.orderNumber.trim(),
                    trackingNumber = current.trackingNumber.trim(),
                    carrier = current.carrier.trim(),
                    memo = current.memo.trim(),
                    sourceText = current.sourceText,
                ),
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun delete() {
        val id = loaded?.id ?: return
        viewModelScope.launch {
            purchases.delete(id)
            _state.update { it.copy(saved = true) }
        }
    }

    private companion object {
        const val DEFAULT_ETA_DAYS = 3L
    }
}
