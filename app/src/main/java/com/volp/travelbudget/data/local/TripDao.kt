package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun observeById(tripId: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun findById(tripId: Long): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    suspend fun findAll(): List<TripEntity>

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Update
    suspend fun update(trip: TripEntity)

    @Delete
    suspend fun delete(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteById(tripId: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()

    // ---- 겹치는 여행 합치기 ----
    //
    // 같은 여행이 두 줄로 남았을 때 한쪽에 붙은 기록을 다른 쪽으로 옮긴다. 옮긴 뒤 빈 여행을
    // 지우면 붙어 있던 기록이 함께 지워지므로, 지우기 전에 반드시 다 옮겨야 한다.
    //
    // 하루 메모와 준비물은 여행과 날짜(또는 이름)가 열쇠라서 양쪽에 같은 날이 있으면 부딪힌다.
    // OR REPLACE를 붙여 옮겨 온 쪽을 남긴다.

    @Query("UPDATE expenses SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun moveExpenses(dropId: Long, keepId: Long)

    @Query("UPDATE bookings SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun moveBookings(dropId: Long, keepId: Long)

    @Query("UPDATE itinerary_stops SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun moveStops(dropId: Long, keepId: Long)

    @Query("UPDATE cash_top_ups SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun moveCashTopUps(dropId: Long, keepId: Long)

    @Query("UPDATE trip_photos SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun movePhotos(dropId: Long, keepId: Long)

    @Query("UPDATE purchases SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun movePurchases(dropId: Long, keepId: Long)

    @Query("UPDATE documents SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun moveDocuments(dropId: Long, keepId: Long)

    @Query("UPDATE OR REPLACE day_notes SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun moveDayNotes(dropId: Long, keepId: Long)

    @Query("UPDATE OR REPLACE packing_checks SET tripId = :keepId WHERE tripId = :dropId")
    suspend fun movePackingChecks(dropId: Long, keepId: Long)
}
