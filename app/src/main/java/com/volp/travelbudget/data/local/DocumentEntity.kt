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
import com.volp.travelbudget.domain.document.DocumentKind
import com.volp.travelbudget.domain.document.TravelDocument
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 보관함에 넣어 둔 문서.
 *
 * 여행을 지워도 여권은 남아야 하므로 연결만 끊는다.
 */
@Entity(
    tableName = "documents",
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
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uid: String,
    val updatedAt: Long,
    val tripId: Long?,
    val kind: String,
    val title: String,
    val number: String,
    val expiresOn: LocalDate?,
    val memo: String,
    val filePath: String?,
    val createdAt: Long,
)

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun findById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun findAll(): List<DocumentEntity>

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT uid FROM documents WHERE id = :id")
    suspend fun uidOf(id: Long): String?
}

fun DocumentEntity.toDomain(): TravelDocument = TravelDocument(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    kind = DocumentKind.fromName(kind),
    title = title,
    number = number,
    expiresOn = expiresOn,
    memo = memo,
    filePath = filePath,
    createdAt = createdAt,
)

fun TravelDocument.toEntity(): DocumentEntity = DocumentEntity(
    id = id,
    uid = uid,
    updatedAt = updatedAt,
    tripId = tripId,
    kind = kind.name,
    title = title,
    number = number,
    expiresOn = expiresOn,
    memo = memo,
    filePath = filePath,
    createdAt = createdAt,
)
