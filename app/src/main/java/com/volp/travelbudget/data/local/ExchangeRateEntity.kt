package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 마지막으로 받아 둔 환율. 통화 하나당 한 줄. */
@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey val code: String,
    val krwPerUnit: Double,
    val fetchedAt: Long,
)

@Dao
interface ExchangeRateDao {

    @Query("SELECT * FROM exchange_rates")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Query("SELECT * FROM exchange_rates WHERE code = :code")
    suspend fun find(code: String): ExchangeRateEntity?

    @Query("SELECT MAX(fetchedAt) FROM exchange_rates")
    suspend fun lastFetchedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rates: List<ExchangeRateEntity>)
}
