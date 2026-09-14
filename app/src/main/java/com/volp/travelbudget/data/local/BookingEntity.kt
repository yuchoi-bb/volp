package com.volp.travelbudget.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.booking.BookingType
import com.volp.travelbudget.domain.travel.GeoPoint
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "bookings",
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
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tripId: Long,
    val type: String,
    val title: String,
    val provider: String,
    val confirmationCode: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime?,
    val fromName: String,
    val fromCode: String,
    val toName: String,
    val toCode: String,
    val address: String,
    val seat: String,
    val gate: String,
    val terminal: String,
    val memo: String,
    val latitude: Double?,
    val longitude: Double?,
)

/** 하루치 여행 메모. 사진과 함께 그날을 남긴다. */
@Entity(
    tableName = "day_notes",
    primaryKeys = ["tripId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DayNoteEntity(
    val tripId: Long,
    val date: LocalDate,
    val text: String,
    val updatedAt: Long,
)

@Dao
interface BookingDao {

    @Query("SELECT * FROM bookings WHERE tripId = :tripId ORDER BY startAt")
    fun observeByTrip(tripId: Long): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE tripId = :tripId ORDER BY startAt")
    suspend fun findByTrip(tripId: Long): List<BookingEntity>

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun findById(id: Long): BookingEntity?

    @Insert
    suspend fun insert(booking: BookingEntity): Long

    @Update
    suspend fun update(booking: BookingEntity)

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM day_notes WHERE tripId = :tripId")
    fun observeNotes(tripId: Long): Flow<List<DayNoteEntity>>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: DayNoteEntity)
}

fun BookingEntity.toDomain(): Booking = Booking(
    id = id,
    tripId = tripId,
    type = BookingType.fromName(type),
    title = title,
    provider = provider,
    confirmationCode = confirmationCode,
    startAt = startAt,
    endAt = endAt,
    fromName = fromName,
    fromCode = fromCode,
    toName = toName,
    toCode = toCode,
    address = address,
    seat = seat,
    gate = gate,
    terminal = terminal,
    memo = memo,
    point = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
)

fun Booking.toEntity(): BookingEntity = BookingEntity(
    id = id,
    tripId = tripId,
    type = type.name,
    title = title,
    provider = provider,
    confirmationCode = confirmationCode,
    startAt = startAt,
    endAt = endAt,
    fromName = fromName,
    fromCode = fromCode,
    toName = toName,
    toCode = toCode,
    address = address,
    seat = seat,
    gate = gate,
    terminal = terminal,
    memo = memo,
    latitude = point?.latitude,
    longitude = point?.longitude,
)
