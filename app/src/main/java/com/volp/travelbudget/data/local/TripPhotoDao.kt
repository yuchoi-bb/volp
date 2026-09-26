package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripPhotoDao {

    /** 여행에 직접 붙인 사진. 예약 티켓은 예약 안에서만 보므로 여기 섞지 않는다. */
    @Query(
        "SELECT * FROM trip_photos WHERE tripId = :tripId AND bookingId IS NULL " +
            "ORDER BY takenAt DESC",
    )
    fun observeByTrip(tripId: Long): Flow<List<TripPhotoEntity>>

    @Query("SELECT * FROM trip_photos WHERE bookingId = :bookingId ORDER BY sortOrder, id")
    fun observeByBooking(bookingId: Long): Flow<List<TripPhotoEntity>>

    @Query("SELECT * FROM trip_photos WHERE bookingId = :bookingId ORDER BY sortOrder, id")
    suspend fun findByBooking(bookingId: Long): List<TripPhotoEntity>

    @Query("SELECT COUNT(*) FROM trip_photos WHERE bookingId = :bookingId")
    suspend fun countForBooking(bookingId: Long): Int

    @Query(
        "SELECT bookingId AS bookingId, COUNT(*) AS count FROM trip_photos " +
            "WHERE tripId = :tripId AND bookingId IS NOT NULL GROUP BY bookingId",
    )
    fun observeBookingCounts(tripId: Long): Flow<List<BookingPhotoCount>>

    @Update
    suspend fun update(photo: TripPhotoEntity)

    @Query("SELECT * FROM trip_photos WHERE expenseId = :expenseId ORDER BY takenAt")
    fun observeByExpense(expenseId: Long): Flow<List<TripPhotoEntity>>

    @Query("SELECT * FROM trip_photos WHERE id = :id")
    suspend fun findById(id: Long): TripPhotoEntity?

    @Insert
    suspend fun insert(photo: TripPhotoEntity): Long

    @Query("DELETE FROM trip_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** 예약 하나에 붙은 사진 수. */
data class BookingPhotoCount(val bookingId: Long, val count: Int)
