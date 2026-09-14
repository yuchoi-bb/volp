package com.volp.travelbudget.data.photos

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.volp.travelbudget.data.local.TripPhotoDao
import com.volp.travelbudget.data.local.TripPhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** 화면에 보여 줄 사진 한 장. 갤러리에서 읽은 것과 앱에 저장한 것을 같은 모양으로 다룬다. */
data class TripPhoto(
    val id: Long,
    val uri: Uri,
    val takenAt: Long,
    /** 앱 저장소에 복사해 둔 사진인지. 갤러리에서 읽어 온 사진은 지울 수 없다. */
    val saved: Boolean,
)

/**
 * 여행 사진을 다룬다.
 *
 * 여행 기간에 찍은 기기 사진은 갤러리에서 그때그때 읽어 오고, 사용자가 직접 고른 사진과
 * 영수증 사진은 앱 저장소로 복사해 보관한다.
 */
class PhotoStore(
    private val context: Context,
    private val dao: TripPhotoDao,
) {

    fun observeSaved(tripId: Long): Flow<List<TripPhoto>> =
        dao.observeByTrip(tripId).map { list -> list.map { it.toPhoto() } }

    fun observeReceipts(expenseId: Long): Flow<List<TripPhoto>> =
        dao.observeByExpense(expenseId).map { list -> list.map { it.toPhoto() } }

    /**
     * 여행 기간에 찍은 기기 사진을 찾는다.
     *
     * Google Photos API로는 사용자의 전체 사진을 읽을 수 없어(2025년부터 막혔다) 기기에
     * 남아 있는 사진을 날짜로 추린다. 기기에서 지운 사진은 사진 선택기로 따로 넣으면 된다.
     */
    suspend fun findDevicePhotos(
        startDate: LocalDate,
        endDate: LocalDate,
        limit: Int = 300,
    ): List<TripPhoto> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val from = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        // 촬영 시각이 비어 있는 사진이 있어 추가된 시각으로 대신한다.
        val takenAt = "COALESCE(${MediaStore.Images.Media.DATE_TAKEN}, " +
            "${MediaStore.Images.Media.DATE_ADDED} * 1000)"

        val photos = mutableListOf<TripPhoto>()
        context.contentResolver.query(
            collection,
            projection,
            "$takenAt >= ? AND $takenAt < ?",
            arrayOf(from.toString(), to.toString()),
            "$takenAt DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext() && photos.size < limit) {
                val id = cursor.getLong(idColumn)
                val taken = takenColumn.takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { cursor.getLong(it) }
                    ?: addedColumn.takeIf { it >= 0 }?.let { cursor.getLong(it) * 1000 }
                    ?: 0L
                photos += TripPhoto(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    takenAt = taken,
                    saved = false,
                )
            }
        }
        photos
    }

    /** 고른 사진을 앱 저장소로 복사해 여행에 붙인다. */
    suspend fun attach(
        tripId: Long,
        expenseId: Long?,
        source: Uri,
        note: String = "",
    ): TripPhoto? = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "photos/$tripId").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}-${source.lastPathSegment?.takeLast(16) ?: "photo"}.jpg")

        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) return@withContext null

        val id = dao.insert(
            TripPhotoEntity(
                tripId = tripId,
                expenseId = expenseId,
                filePath = target.absolutePath,
                takenAt = System.currentTimeMillis(),
                note = note,
            ),
        )
        TripPhoto(id = id, uri = Uri.fromFile(target), takenAt = target.lastModified(), saved = true)
    }

    suspend fun remove(photoId: Long) = withContext(Dispatchers.IO) {
        dao.findById(photoId)?.let { File(it.filePath).delete() }
        dao.deleteById(photoId)
    }

    private fun TripPhotoEntity.toPhoto() = TripPhoto(
        id = id,
        uri = Uri.fromFile(File(filePath)),
        takenAt = takenAt,
        saved = true,
    )
}
