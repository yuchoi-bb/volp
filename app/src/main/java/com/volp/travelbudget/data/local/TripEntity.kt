package com.volp.travelbudget.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.domain.model.Trip
import java.time.LocalDate

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val destinationKey: String,
    val destinationName: String,
    val region: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val travelers: Int,
    val style: String,
    val includeFlight: Boolean,
    val currencyCode: String,
    val exchangeRate: Double,
    val predictedBudget: Map<ExpenseCategory, Long>,
    val plannedBudget: Map<ExpenseCategory, Long>,
    val createdAt: Long,
)

fun TripEntity.toDomain(): Trip = Trip(
    id = id,
    title = title,
    destinationKey = destinationKey,
    destinationName = destinationName,
    region = Region.fromName(region),
    startDate = startDate,
    endDate = endDate,
    travelers = travelers,
    style = TravelStyle.fromName(style),
    includeFlight = includeFlight,
    currencyCode = currencyCode,
    exchangeRate = exchangeRate,
    predictedBudget = predictedBudget,
    plannedBudget = plannedBudget,
    createdAt = createdAt,
)

fun Trip.toEntity(): TripEntity = TripEntity(
    id = id,
    title = title,
    destinationKey = destinationKey,
    destinationName = destinationName,
    region = region.name,
    startDate = startDate,
    endDate = endDate,
    travelers = travelers,
    style = style.name,
    includeFlight = includeFlight,
    currencyCode = currencyCode,
    exchangeRate = exchangeRate,
    predictedBudget = predictedBudget,
    plannedBudget = plannedBudget,
    createdAt = createdAt,
)
