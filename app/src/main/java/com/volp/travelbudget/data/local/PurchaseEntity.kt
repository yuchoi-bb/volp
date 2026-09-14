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
import com.volp.travelbudget.domain.purchase.Purchase
import com.volp.travelbudget.domain.purchase.PurchaseKind
import com.volp.travelbudget.domain.purchase.PurchaseStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 여행 전에 산 것.
 *
 * 여행이 지워져도 물건은 남으므로 여행과의 연결만 끊는다([ForeignKey.SET_NULL]).
 */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tripId"), Index("uid")],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val updatedAt: Long,
    val tripId: Long?,
    val kind: String,
    val title: String,
    val merchant: String,
    val amountKrw: Long,
    val originalAmount: Double?,
    val currencyCode: String,
    val orderedOn: LocalDate?,
    val eta: LocalDate?,
    val status: String,
    val orderNumber: String,
    val trackingNumber: String,
    val carrier: String,
    val memo: String,
    val sourceText: String,
    val expenseId: Long?,
    val createdAt: Long,
)

@Dao
interface PurchaseDao {

    @Query("SELECT * FROM purchases ORDER BY eta IS NULL, eta, createdAt DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE tripId = :tripId ORDER BY eta IS NULL, eta, createdAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun findById(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchases")
    suspend fun findAll(): List<PurchaseEntity>

    @Query("SELECT * FROM purchases WHERE tripId = :tripId")
    suspend fun findByTrip(tripId: Long): List<PurchaseEntity>

    @Query("SELECT * FROM purchases WHERE uid = :uid")
    suspend fun findByUid(uid: String): PurchaseEntity?

    /** 아직 오지 않았고 예정일이 지난 것. 알림에서 쓴다. */
    @Query(
        "SELECT * FROM purchases WHERE status IN ('ORDERED', 'SHIPPED') " +
            "AND eta IS NOT NULL AND eta <= :through ORDER BY eta",
    )
    suspend fun findDueThrough(through: LocalDate): List<PurchaseEntity>

    @Insert
    suspend fun insert(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(purchase: PurchaseEntity)

    @Update
    suspend fun update(purchase: PurchaseEntity)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM purchases WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("SELECT uid FROM purchases WHERE id = :id")
    suspend fun uidOf(id: Long): String?
}

fun PurchaseEntity.toDomain(): Purchase = Purchase(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    kind = PurchaseKind.fromName(kind),
    title = title,
    merchant = merchant,
    amountKrw = amountKrw,
    originalAmount = originalAmount,
    currencyCode = currencyCode,
    orderedOn = orderedOn,
    eta = eta,
    status = PurchaseStatus.fromName(status),
    orderNumber = orderNumber,
    trackingNumber = trackingNumber,
    carrier = carrier,
    memo = memo,
    sourceText = sourceText,
    expenseId = expenseId,
    createdAt = createdAt,
)

fun Purchase.toEntity(): PurchaseEntity = PurchaseEntity(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    kind = kind.name,
    title = title,
    merchant = merchant,
    amountKrw = amountKrw,
    originalAmount = originalAmount,
    currencyCode = currencyCode,
    orderedOn = orderedOn,
    eta = eta,
    status = status.name,
    orderNumber = orderNumber,
    trackingNumber = trackingNumber,
    carrier = carrier,
    memo = memo,
    sourceText = sourceText,
    expenseId = expenseId,
    createdAt = createdAt,
)
