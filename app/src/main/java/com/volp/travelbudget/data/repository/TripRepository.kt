package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.backup.TripBackup
import com.volp.travelbudget.data.exchange.ExchangeRateRepository
import com.volp.travelbudget.data.local.CaptureSource
import com.volp.travelbudget.data.local.ExpenseDao
import com.volp.travelbudget.data.local.MerchantAliasDao
import com.volp.travelbudget.data.local.MerchantAliasEntity
import com.volp.travelbudget.data.local.PendingStatus
import com.volp.travelbudget.data.local.PendingTransaction
import com.volp.travelbudget.data.local.PendingTransactionDao
import com.volp.travelbudget.data.local.TripDao
import com.volp.travelbudget.data.local.normalizeMerchantKey
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.data.local.toPendingEntity
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.cardsms.CardTransaction
import com.volp.travelbudget.domain.cardsms.TransactionKind
import com.volp.travelbudget.domain.classify.RuleBasedMerchantClassifier
import com.volp.travelbudget.domain.settlement.Settlement
import com.volp.travelbudget.domain.summary.TripSummaries
import com.volp.travelbudget.domain.summary.TripSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** 목록 화면에서 쓰는, 여행과 그 여행의 총지출을 묶은 값. */
/** 별칭 사전과 규칙을 거쳐 정리한 가맹점. 항목을 못 정했으면 [category]가 null이다. */
data class ResolvedMerchant(
    val displayName: String,
    val category: ExpenseCategory?,
)

data class TripWithSpending(
    val trip: Trip,
    val totalSpent: Long,
) {
    val remaining: Long get() = trip.totalPlanned - totalSpent
    val progressRatio: Float
        get() = if (trip.totalPlanned > 0L) totalSpent.toFloat() / trip.totalPlanned.toFloat() else 0f
}

class TripRepository(
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao,
    private val pendingDao: PendingTransactionDao,
    private val aliasDao: MerchantAliasDao,
    private val exchangeRates: ExchangeRateRepository,
) {

    fun observeTripsWithSpending(): Flow<List<TripWithSpending>> =
        combine(tripDao.observeAll(), expenseDao.observeTripTotals()) { trips, totals ->
            val spentByTrip = totals.associate { it.tripId to it.total }
            trips.map { entity ->
                TripWithSpending(
                    trip = entity.toDomain(),
                    totalSpent = spentByTrip[entity.id] ?: 0L,
                )
            }
        }

    fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTrip(tripId: Long): Flow<Trip?> =
        tripDao.observeById(tripId).map { it?.toDomain() }

    fun observeExpenses(tripId: Long): Flow<List<Expense>> =
        expenseDao.observeByTrip(tripId).map { list -> list.map { it.toDomain() } }

    /** 여행 상세 화면이 필요로 하는 계산을 한 번에 끝낸 값. 여행이 삭제되면 null을 내보낸다. */
    fun observeSummary(tripId: Long): Flow<TripSummary?> =
        combine(
            tripDao.observeById(tripId),
            expenseDao.observeCategoryTotals(tripId),
        ) { tripEntity, totals ->
            val trip = tripEntity?.toDomain() ?: return@combine null
            val spent = totals.associate { ExpenseCategory.fromName(it.category) to it.total }
            TripSummaries.summarize(trip, spent, LocalDate.now())
        }

    suspend fun getTrip(tripId: Long): Trip? = tripDao.findById(tripId)?.toDomain()

    /** 지금 시점의 여행 목록. 한 번만 읽으면 되는 곳에 쓴다. */
    suspend fun tripsOnce(): List<Trip> = tripDao.findAll().map { it.toDomain() }

    suspend fun createTrip(trip: Trip): Long = tripDao.insert(trip.toEntity())

    suspend fun updateTrip(trip: Trip) = tripDao.update(trip.toEntity())

    suspend fun deleteTrip(tripId: Long) = tripDao.deleteById(tripId)

    suspend fun getExpense(expenseId: Long): Expense? = expenseDao.findById(expenseId)?.toDomain()

    /** 지금 시점의 지출 목록. 알림 판단처럼 한 번만 읽으면 되는 곳에 쓴다. */
    suspend fun expensesOnce(tripId: Long): List<Expense> =
        expenseDao.findByTrip(tripId).map { it.toDomain() }

    suspend fun addExpense(expense: Expense): Long = expenseDao.insert(expense.toEntity())

    suspend fun updateExpense(expense: Expense) = expenseDao.update(expense.toEntity())

    suspend fun deleteExpense(expenseId: Long) = expenseDao.deleteById(expenseId)

    // ---- 카드 문자에서 모은 결제 ----

    fun observePendingTransactions(): Flow<List<PendingTransaction>> =
        pendingDao.observePending().map { list -> list.map { it.toDomain() } }

    /** 내 명의로 들어온, 아직 정리하지 않은 결제 건수. 화면 배지에 쓴다. */
    fun observePendingCount(): Flow<Int> = pendingDao.observePendingCount()

    /**
     * 문자·알림에서 읽은 결제를 미확인함에 넣는다.
     *
     * @return 새로 들어갔으면 true. 같은 결제가 이미 있으면 false.
     */
    /**
     * @return 새로 들어간 행의 아이디. 같은 결제가 이미 있으면 null.
     */
    suspend fun capture(
        transaction: CardTransaction,
        source: CaptureSource,
        ownerName: String,
    ): Long? {
        if (!transaction.isRecordable) return null
        val id = pendingDao.insertIfNew(transaction.toPendingEntity(source, ownerName))
        return id.takeIf { it > 0L }
    }

    /**
     * 확실한 결제는 확인을 기다리지 않고 바로 지출로 넣는다.
     *
     * 해외에서는 하루에도 여러 건이 들어오는데 매번 손으로 배정하면 앱을 켜는 일이 일이 된다.
     * 그래서 다음이 모두 맞을 때만 자동으로 넣는다.
     * - 내 명의 결제일 것
     * - 결제 시각이 딱 한 여행의 기간 안에 들어갈 것
     * - 항목을 알 수 있을 것(별칭 사전이나 가맹점 규칙에 걸릴 것)
     *
     * @return 자동으로 넣은 여행. 조건이 맞지 않으면 null.
     */
    suspend fun tryAutoAssign(pendingId: Long): Trip? {
        val pending = pendingDao.findById(pendingId)?.toDomain() ?: return null
        if (pending.foreignHolder) return null

        val date = pending.occurredAt.toLocalDate()
        val trip = tripDao.findAll()
            .map { it.toDomain() }
            .filter { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
            .singleOrNull() ?: return null

        val resolved = resolveMerchant(pending.merchant)
        if (resolved.category == null) return null

        acceptPending(
            pendingId = pendingId,
            tripId = trip.id,
            category = resolved.category,
            memoOverride = resolved.displayName,
        )
        return trip
    }

    // ---- 가맹점 별칭 ----

    /** 별칭 사전과 규칙을 차례로 보고 가맹점 이름과 항목을 정리한다. */
    suspend fun resolveMerchant(merchant: String): ResolvedMerchant {
        val alias = aliasDao.find(normalizeMerchantKey(merchant))
        if (alias != null) {
            return ResolvedMerchant(alias.displayName, ExpenseCategory.fromName(alias.category))
        }
        return ResolvedMerchant(merchant, RuleBasedMerchantClassifier.classify(merchant))
    }

    fun observeAliases(): Flow<List<MerchantAliasEntity>> = aliasDao.observeAll()

    suspend fun rememberAlias(rawMerchant: String, displayName: String, category: ExpenseCategory) {
        if (rawMerchant.isBlank()) return
        aliasDao.upsert(
            MerchantAliasEntity(
                rawKey = normalizeMerchantKey(rawMerchant),
                displayName = displayName.ifBlank { rawMerchant },
                category = category.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun forgetAlias(rawKey: String) = aliasDao.deleteByKey(rawKey)

    /** 결제 시각이 여행 기간 안에 들어가는 여행을 찾는다. */
    suspend fun findTripFor(transaction: PendingTransaction): Trip? {
        val date = transaction.occurredAt.toLocalDate()
        return tripDao.findAll()
            .map { it.toDomain() }
            .firstOrNull { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
    }

    /** 미확인 결제를 실제 여행 지출로 옮긴다. */
    suspend fun acceptPending(
        pendingId: Long,
        tripId: Long,
        category: ExpenseCategory,
        memoOverride: String? = null,
    ): Long? {
        val pending = pendingDao.findById(pendingId)?.toDomain() ?: return null
        val trip = getTrip(tripId) ?: return null
        val sign = if (pending.kind == TransactionKind.CANCEL) -1 else 1
        // 여행을 만들 때 넣은 값이 아니라 지금 받아 둔 환율을 쓴다.
        val rate = if (pending.isOverseas) exchangeRates.rateFor(pending.currencyCode) else 1.0

        val expense = Expense(
            tripId = tripId,
            category = category,
            amountKrw = if (pending.isOverseas) {
                TripSummaries.toKrw(pending.amount * sign, rate)
            } else {
                (pending.amount * sign).toLong()
            },
            originalAmount = if (pending.isOverseas) pending.amount * sign else null,
            currencyCode = if (pending.isOverseas) pending.currencyCode else "KRW",
            date = pending.occurredAt.toLocalDate(),
            memo = memoOverride?.takeIf { it.isNotBlank() } ?: pending.merchant,
            exchangeRate = if (pending.isOverseas) rate else null,
        )
        val expenseId = expenseDao.insert(expense.toEntity())
        pendingDao.updateStatus(pendingId, PendingStatus.ACCEPTED.name, tripId, expenseId)
        return expenseId
    }

    suspend fun ignorePending(pendingId: Long) {
        pendingDao.updateStatus(pendingId, PendingStatus.IGNORED.name, null, null)
    }

    // ---- 실제 청구액 보정 ----

    /** 보정 전 기준으로 본 이 여행의 해외 결제 승인액 합계. */
    suspend fun approvedForeignTotal(tripId: Long): Long =
        Settlement.approvedForeignTotal(expenseDao.findByTrip(tripId).map { it.toDomain() })

    /**
     * 카드 명세서의 실제 청구 총액에 맞춰 해외 결제 금액을 다시 계산한다.
     *
     * @param billedTotalKrw null이면 보정을 풀고 승인액 그대로 되돌린다.
     */
    suspend fun applySettlement(tripId: Long, billedTotalKrw: Long?) {
        val trip = getTrip(tripId) ?: return
        val expenses = expenseDao.findByTrip(tripId).map { it.toDomain() }
        val factor = if (billedTotalKrw == null) 1.0 else Settlement.factorFor(billedTotalKrw, expenses)

        Settlement.apply(expenses, factor).forEach { expenseDao.update(it.toEntity()) }
        tripDao.update(
            trip.copy(billedTotalKrw = billedTotalKrw, settlementFactor = factor).toEntity(),
        )
    }

    // ---- 백업 ----

    /** 백업에 담을 전체 기록. */
    suspend fun exportAll(): List<TripBackup> =
        tripDao.findAll().map { entity ->
            TripBackup(
                trip = entity.toDomain(),
                expenses = expenseDao.findByTrip(entity.id).map { it.toDomain() },
            )
        }

    /**
     * 백업으로 기존 기록을 덮어쓴다.
     *
     * 합치기가 아니라 통째로 교체한다. 혼자 쓰는 앱이라 기기를 바꿨을 때 되살리는 용도가
     * 대부분이고, 합치기는 같은 지출이 두 번 들어가기 쉽다.
     *
     * @return 복원한 여행 수
     */
    suspend fun importAll(backups: List<TripBackup>): Int {
        tripDao.deleteAll()
        backups.forEach { backup ->
            val tripId = tripDao.insert(backup.trip.copy(id = 0L).toEntity())
            backup.expenses.forEach { expense ->
                expenseDao.insert(expense.copy(id = 0L, tripId = tripId).toEntity())
            }
        }
        return backups.size
    }
}
