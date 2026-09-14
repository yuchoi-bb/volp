package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {

    @Query("SELECT * FROM pending_transactions WHERE status = 'PENDING' ORDER BY occurredAt DESC")
    fun observePending(): Flow<List<PendingTransactionEntity>>

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = 'PENDING' AND foreignHolder = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_transactions WHERE id = :id")
    suspend fun findById(id: Long): PendingTransactionEntity?

    /** 같은 결제가 문자와 알림으로 두 번 들어오면 뒤에 온 쪽을 버린다. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(transaction: PendingTransactionEntity): Long

    @Query("UPDATE pending_transactions SET status = :status, tripId = :tripId, expenseId = :expenseId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, tripId: Long?, expenseId: Long?)

    @Query("DELETE FROM pending_transactions WHERE status != 'PENDING' AND receivedAt < :before")
    suspend fun purgeHandledBefore(before: Long)
}
