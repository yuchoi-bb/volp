package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.exchange.ExchangeRateRepository
import com.volp.travelbudget.data.local.DeletionEntity
import com.volp.travelbudget.data.local.PurchaseDao
import com.volp.travelbudget.data.local.SyncDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.purchase.ParsedPurchase
import com.volp.travelbudget.domain.purchase.Purchase
import com.volp.travelbudget.domain.purchase.PurchaseKind
import com.volp.travelbudget.domain.purchase.Purchases
import com.volp.travelbudget.domain.sync.SyncIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * 여행 전에 사 두는 것들을 맡는다.
 *
 * 가계부와 따로 두되, 물건이 도착하면 그대로 지출로 넘길 수 있게 이어 둔다.
 */
class PurchaseRepository(
    private val dao: PurchaseDao,
    private val syncDao: SyncDao,
    private val trips: TripRepository,
    private val exchangeRates: ExchangeRateRepository,
) {

    fun observeAll(): Flow<List<Purchase>> =
        dao.observeAll().map { list -> Purchases.sortByUrgency(list.map { it.toDomain() }) }

    fun observeByTrip(tripId: Long): Flow<List<Purchase>> =
        dao.observeByTrip(tripId).map { list -> Purchases.sortByUrgency(list.map { it.toDomain() }) }

    suspend fun find(id: Long): Purchase? = dao.findById(id)?.toDomain()

    suspend fun save(purchase: Purchase): Long {
        val stamped = purchase.copy(
            uid = purchase.uid.ifBlank { SyncIds.newUid() },
            updatedAt = SyncIds.now(),
            createdAt = if (purchase.createdAt > 0L) purchase.createdAt else SyncIds.now(),
        )
        return if (stamped.id > 0L) {
            dao.update(stamped.toEntity())
            stamped.id
        } else {
            dao.insert(stamped.toEntity())
        }
    }

    suspend fun delete(id: Long) {
        dao.uidOf(id)?.let { uid ->
            syncDao.recordDeletion(DeletionEntity(ENTITY, uid, SyncIds.now()))
        }
        dao.deleteById(id)
    }

    /**
     * 읽어 낸 값을 저장할 수 있는 구매로 바꾼다.
     *
     * 외화로 산 것은 받아 둔 환율로 원화를 채워 둔다. 화면에서 고칠 수 있으므로 어림값이어도 된다.
     */
    suspend fun fromParsed(parsed: ParsedPurchase, tripId: Long?): Purchase {
        val krw = when {
            parsed.amountKrw > 0L -> parsed.amountKrw
            parsed.originalAmount != null -> convert(parsed.originalAmount, parsed.currencyCode)
            else -> 0L
        }

        return Purchase(
            tripId = tripId,
            kind = parsed.kind,
            title = parsed.title,
            merchant = parsed.merchant,
            amountKrw = krw,
            originalAmount = parsed.originalAmount,
            currencyCode = parsed.currencyCode,
            orderedOn = parsed.orderedOn ?: LocalDate.now(),
            eta = parsed.eta,
            status = parsed.status,
            orderNumber = parsed.orderNumber,
            trackingNumber = parsed.trackingNumber,
            carrier = parsed.carrier,
            sourceText = parsed.sourceText,
        )
    }

    private suspend fun convert(amount: Double, currencyCode: String): Long {
        if (currencyCode == "KRW") return amount.roundToLong()
        return (amount * exchangeRates.rateFor(currencyCode)).roundToLong()
    }

    /**
     * 이 구매를 여행 가계부의 지출로 넣는다.
     *
     * 여행 전에 미리 낸 돈도 그 여행에 쓴 돈이다. 날짜는 산 날을 쓴다.
     */
    suspend fun pushToLedger(purchase: Purchase): Long? {
        val tripId = purchase.tripId ?: return null
        if (!purchase.canBecomeExpense) return null

        val expenseId = trips.addExpense(
            Expense(
                tripId = tripId,
                category = purchase.kind.toCategory(),
                amountKrw = purchase.amountKrw,
                originalAmount = purchase.originalAmount,
                currencyCode = purchase.currencyCode,
                date = purchase.orderedOn ?: LocalDate.now(),
                memo = listOf(purchase.merchant, purchase.title)
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
            ),
        )
        save(purchase.copy(expenseId = expenseId))
        return expenseId
    }

    private companion object {
        const val ENTITY = "purchase"
    }
}

/** 구매 갈래를 가계부 항목으로 옮긴다. 미리 산 항공권은 여행에서도 교통비다. */
fun PurchaseKind.toCategory(): ExpenseCategory = when (this) {
    PurchaseKind.FLIGHT -> ExpenseCategory.FLIGHT
    PurchaseKind.TRANSPORT -> ExpenseCategory.TRANSPORT
    PurchaseKind.LODGING -> ExpenseCategory.LODGING
    PurchaseKind.TICKET -> ExpenseCategory.ACTIVITY
    PurchaseKind.GEAR, PurchaseKind.CLOTHING, PurchaseKind.ELECTRONICS -> ExpenseCategory.SHOPPING
    PurchaseKind.INSURANCE, PurchaseKind.OTHER -> ExpenseCategory.ETC
}
