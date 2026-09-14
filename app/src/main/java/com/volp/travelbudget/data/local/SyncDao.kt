package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 지운 기록의 흔적.
 *
 * 지움을 남기지 않으면 다른 기기에 남아 있던 기록이 다음 동기화에서 되살아난다.
 */
@Entity(tableName = "deletions", primaryKeys = ["entity", "uid"])
data class DeletionEntity(
    /** trip, expense, booking, stop 중 하나. */
    val entity: String,
    val uid: String,
    val deletedAt: Long,
)

@Dao
interface SyncDao {

    @Query("SELECT * FROM deletions")
    suspend fun deletions(): List<DeletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDeletions(entries: List<DeletionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDeletion(entry: DeletionEntity)

    /** 오래된 흔적은 지운다. 모든 기기가 이미 받아 갔을 만큼 지난 것만 정리한다. */
    @Query("DELETE FROM deletions WHERE deletedAt < :before")
    suspend fun purgeDeletionsBefore(before: Long)

    // ---- uid로 찾기 ----

    @Query("SELECT uid FROM trips WHERE id = :id")
    suspend fun tripUid(id: Long): String?

    @Query("SELECT uid FROM expenses WHERE id = :id")
    suspend fun expenseUid(id: Long): String?

    @Query("SELECT uid FROM bookings WHERE id = :id")
    suspend fun bookingUid(id: Long): String?

    @Query("SELECT uid FROM itinerary_stops WHERE id = :id")
    suspend fun stopUid(id: Long): String?

    @Query("SELECT * FROM trips WHERE uid = :uid")
    suspend fun tripByUid(uid: String): TripEntity?

    @Query("SELECT * FROM expenses WHERE uid = :uid")
    suspend fun expenseByUid(uid: String): ExpenseEntity?

    @Query("SELECT * FROM bookings WHERE uid = :uid")
    suspend fun bookingByUid(uid: String): BookingEntity?

    @Query("SELECT * FROM itinerary_stops WHERE uid = :uid")
    suspend fun stopByUid(uid: String): ItineraryStopEntity?

    // ---- uid로 지우기 ----

    @Query("DELETE FROM trips WHERE uid = :uid")
    suspend fun deleteTripByUid(uid: String)

    @Query("DELETE FROM expenses WHERE uid = :uid")
    suspend fun deleteExpenseByUid(uid: String)

    @Query("DELETE FROM bookings WHERE uid = :uid")
    suspend fun deleteBookingByUid(uid: String)

    @Query("DELETE FROM itinerary_stops WHERE uid = :uid")
    suspend fun deleteStopByUid(uid: String)

    // ---- 동기화로 받아온 것 넣기 ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooking(booking: BookingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStop(stop: ItineraryStopEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: DayNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackingCheck(check: PackingCheckEntity)

    @Query("SELECT * FROM day_notes WHERE tripId = :tripId")
    suspend fun notesOf(tripId: Long): List<DayNoteEntity>

    @Query("SELECT * FROM packing_checks WHERE tripId = :tripId")
    suspend fun packingChecksOf(tripId: Long): List<PackingCheckEntity>

    @Query("SELECT * FROM itinerary_stops WHERE tripId = :tripId")
    suspend fun stopsOf(tripId: Long): List<ItineraryStopEntity>
}
