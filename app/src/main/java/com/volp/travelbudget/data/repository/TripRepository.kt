package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.backup.TripBackup
import com.volp.travelbudget.data.local.CaptureSource
import com.volp.travelbudget.data.local.ExpenseDao
import com.volp.travelbudget.data.local.PendingStatus
import com.volp.travelbudget.data.local.PendingTransaction
import com.volp.travelbudget.data.local.PendingTransactionDao
import com.volp.travelbudget.data.local.TripDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.data.local.toPendingEntity
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.cardsms.CardTransaction
import com.volp.travelbudget.domain.cardsms.TransactionKind
import com.volp.travelbudget.domain.classify.RuleBasedMerchantClassifier
import com.volp.travelbudget.domain.summary.TripSummaries
import com.volp.travelbudget.domain.summary.TripSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** 목록 화면에서 쓰는, 여행과 그 여행의 총지출을 묶은 값. */
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

    suspend fun createTrip(trip: Trip): Long = tripDao.insert(trip.toEntity())

    suspend fun updateTrip(trip: Trip) = tripDao.update(trip.toEntity())

    suspend fun deleteTrip(tripId: Long) = tripDao.deleteById(tripId)

    suspend fun getExpense(expenseId: Long): Expense? = expenseDao.findById(expenseId)?.toDomain()

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
    suspend fun capture(
        transaction: CardTransaction,
        source: CaptureSource,
        ownerName: String,
    ): Boolean {
        if (!transaction.isRecordable) return false
        val id = pendingDao.insertIfNew(transaction.toPendingEntity(source, ownerName))
        return id > 0L
    }

    /** 결제 시각이 여행 기간 안에 들어가는 여행을 찾는다. */
    suspend fun findTripFor(transaction: PendingTransaction): Trip? {
        val date = transaction.occurredAt.toLocalDate()
        return tripDao.findAll()
            .map { it.toDomain() }
            .firstOrNull { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
    }

    /** 가맹점 이름으로 항목을 추측한다. 모르겠으면 기타로 둔다. */
    fun guessCategory(transaction: PendingTransaction): ExpenseCategory =
        RuleBasedMerchantClassifier.classify(transaction.merchant) ?: ExpenseCategory.ETC

    /** 미확인 결제를 실제 여행 지출로 옮긴다. */
    suspend fun acceptPending(
        pendingId: Long,
        tripId: Long,
        category: ExpenseCategory,
    ): Long? {
        val pending = pendingDao.findById(pendingId)?.toDomain() ?: return null
        val trip = getTrip(tripId) ?: return null
        val sign = if (pending.kind == TransactionKind.CANCEL) -1 else 1

        val expense = Expense(
            tripId = tripId,
            category = category,
            amountKrw = if (pending.isOverseas) {
                TripSummaries.toKrw(pending.amount * sign, trip.exchangeRate)
            } else {
                (pending.amount * sign).toLong()
            },
            originalAmount = if (pending.isOverseas) pending.amount * sign else null,
            currencyCode = if (pending.isOverseas) pending.currencyCode else "KRW",
            date = pending.occurredAt.toLocalDate(),
            memo = pending.merchant,
        )
        val expenseId = expenseDao.insert(expense.toEntity())
        pendingDao.updateStatus(pendingId, PendingStatus.ACCEPTED.name, tripId, expenseId)
        return expenseId
    }

    suspend fun ignorePending(pendingId: Long) {
        pendingDao.updateStatus(pendingId, PendingStatus.IGNORED.name, null, null)
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
