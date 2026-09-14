package com.volp.travelbudget.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.model.ExpenseCategory
import java.time.LocalDate

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tripId: Long,
    val category: String,
    val amountKrw: Long,
    val originalAmount: Double?,
    val currencyCode: String,
    val date: LocalDate,
    val memo: String,
    val exchangeRate: Double?,
    val createdAt: Long,
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    tripId = tripId,
    category = ExpenseCategory.fromName(category),
    amountKrw = amountKrw,
    originalAmount = originalAmount,
    currencyCode = currencyCode,
    date = date,
    memo = memo,
    exchangeRate = exchangeRate,
    createdAt = createdAt,
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    tripId = tripId,
    category = category.name,
    amountKrw = amountKrw,
    originalAmount = originalAmount,
    currencyCode = currencyCode,
    date = date,
    memo = memo,
    exchangeRate = exchangeRate,
    createdAt = createdAt,
)
