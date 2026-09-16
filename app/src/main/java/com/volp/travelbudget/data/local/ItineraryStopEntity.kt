package com.volp.travelbudget.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** 일정표의 장소 한 곳. */
@Entity(
    tableName = "itinerary_stops",
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
data class ItineraryStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(defaultValue = "") val uid: String,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long,
    val tripId: Long,
    val date: LocalDate,
    /** 같은 날 안에서의 순서. 동선은 이 순서대로 이어진다. */
    val sortOrder: Int,
    val name: String,
    val address: String,
    /** 지오코딩으로 찾은 좌표. 못 찾았으면 null이고 거리 안내가 빠진다. */
    val latitude: Double?,
    val longitude: Double?,
    /** `HH:mm`. 정하지 않았으면 null. */
    val startTime: String?,
    val memo: String,
    /** 날짜를 옮길 수 있는 일정인지. [com.volp.travelbudget.domain.itinerary.PlanFixity]의 이름. */
    @ColumnInfo(defaultValue = "FLEXIBLE") val fixity: String = "FLEXIBLE",
)

/** 준비물 체크 상태. 목록 자체는 규칙으로 만들고 체크만 저장한다. */
@Entity(tableName = "packing_checks", primaryKeys = ["tripId", "itemName"])
data class PackingCheckEntity(
    val tripId: Long,
    val itemName: String,
    val checked: Boolean,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)

@Dao
interface ItineraryDao {

    @Query("SELECT * FROM itinerary_stops WHERE tripId = :tripId ORDER BY date, sortOrder")
    fun observeStops(tripId: Long): Flow<List<ItineraryStopEntity>>

    @Query("SELECT * FROM itinerary_stops WHERE id = :id")
    suspend fun findStop(id: Long): ItineraryStopEntity?

    @Query("SELECT * FROM itinerary_stops WHERE tripId = :tripId AND date = :date ORDER BY sortOrder")
    suspend fun observeStopsOnce(tripId: Long, date: LocalDate): List<ItineraryStopEntity>

    @Query("SELECT * FROM itinerary_stops WHERE tripId = :tripId ORDER BY date, sortOrder")
    suspend fun stopsOf(tripId: Long): List<ItineraryStopEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM itinerary_stops WHERE tripId = :tripId AND date = :date")
    suspend fun nextSortOrder(tripId: Long, date: LocalDate): Int

    @Insert
    suspend fun insert(stop: ItineraryStopEntity): Long

    @Update
    suspend fun update(stop: ItineraryStopEntity)

    @Delete
    suspend fun delete(stop: ItineraryStopEntity)

    @Query("DELETE FROM itinerary_stops WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM packing_checks WHERE tripId = :tripId")
    fun observePackingChecks(tripId: Long): Flow<List<PackingCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackingCheck(check: PackingCheckEntity)
}
