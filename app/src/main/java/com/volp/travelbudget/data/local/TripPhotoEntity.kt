package com.volp.travelbudget.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 사용자가 여행에 직접 붙인 사진.
 *
 * 갤러리 원본을 그대로 가리키면 원본이 지워졌을 때 함께 사라지므로 앱 저장소로 복사해 둔다.
 * 여행 기간의 기기 사진은 저장하지 않고 그때그때 갤러리에서 읽어 보여 준다.
 */
@Entity(
    tableName = "trip_photos",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("expenseId")],
)
data class TripPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val tripId: Long,
    /** 영수증 사진이면 어떤 지출의 것인지. 여행 사진이면 null. */
    val expenseId: Long?,
    val filePath: String,
    val takenAt: Long,
    val note: String,
)
