package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.volp.travelbudget.domain.cash.CashTopUp
import com.volp.travelbudget.domain.cash.TopUpKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 지갑에 현금을 채운 기록. 여행을 지우면 함께 사라진다. */
@Entity(
    tableName = "cash_top_ups",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("uid")],
)
data class CashTopUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val updatedAt: Long,
    val tripId: Long,
    val kind: String,
    val currencyCode: String,
    val amount: Double,
    val krwPaid: Long,
    val date: LocalDate,
    val memo: String,
    val createdAt: Long,
)

@Dao
interface CashTopUpDao {

    @Query("SELECT * FROM cash_top_ups WHERE tripId = :tripId ORDER BY date DESC, id DESC")
    fun observeByTrip(tripId: Long): Flow<List<CashTopUpEntity>>

    @Query("SELECT * FROM cash_top_ups WHERE tripId = :tripId ORDER BY date, id")
    suspend fun findByTrip(tripId: Long): List<CashTopUpEntity>

    @Query("SELECT * FROM cash_top_ups WHERE id = :id")
    suspend fun findById(id: Long): CashTopUpEntity?

    @Query("SELECT * FROM cash_top_ups WHERE uid = :uid")
    suspend fun findByUid(uid: String): CashTopUpEntity?

    @Insert
    suspend fun insert(entry: CashTopUpEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CashTopUpEntity)

    @Update
    suspend fun update(entry: CashTopUpEntity)

    @Query("DELETE FROM cash_top_ups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cash_top_ups WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("SELECT uid FROM cash_top_ups WHERE id = :id")
    suspend fun uidOf(id: Long): String?
}

fun CashTopUpEntity.toDomain(): CashTopUp = CashTopUp(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    kind = TopUpKind.fromName(kind),
    currencyCode = currencyCode,
    amount = amount,
    krwPaid = krwPaid,
    date = date,
    memo = memo,
    createdAt = createdAt,
)

fun CashTopUp.toEntity(): CashTopUpEntity = CashTopUpEntity(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    kind = kind.name,
    currencyCode = currencyCode,
    amount = amount,
    krwPaid = krwPaid,
    date = date,
    memo = memo,
    createdAt = createdAt,
)
