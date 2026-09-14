package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripPhotoDao {

    @Query("SELECT * FROM trip_photos WHERE tripId = :tripId ORDER BY takenAt DESC")
    fun observeByTrip(tripId: Long): Flow<List<TripPhotoEntity>>

    @Query("SELECT * FROM trip_photos WHERE expenseId = :expenseId ORDER BY takenAt")
    fun observeByExpense(expenseId: Long): Flow<List<TripPhotoEntity>>

    @Query("SELECT * FROM trip_photos WHERE id = :id")
    suspend fun findById(id: Long): TripPhotoEntity?

    @Insert
    suspend fun insert(photo: TripPhotoEntity): Long

    @Query("DELETE FROM trip_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
