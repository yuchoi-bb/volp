package com.volp.travelbudget.data.repository

import com.volp.travelbudget.data.local.ExpenseDao
import com.volp.travelbudget.data.local.TripDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
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
}
