package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 항목별 지출 합계. */
data class CategoryTotal(val category: String, val total: Long)

/** 여행별 지출 합계. 목록 화면에서 한 번에 읽어 온다. */
data class TripTotal(val tripId: Long, val total: Long)

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun findById(expenseId: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date")
    suspend fun findByTrip(tripId: Long): List<ExpenseEntity>

    @Query(
        "SELECT category, SUM(amountKrw) AS total FROM expenses " +
            "WHERE tripId = :tripId GROUP BY category",
    )
    fun observeCategoryTotals(tripId: Long): Flow<List<CategoryTotal>>

    @Query("SELECT tripId, SUM(amountKrw) AS total FROM expenses GROUP BY tripId")
    fun observeTripTotals(): Flow<List<TripTotal>>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteById(expenseId: Long)
}
