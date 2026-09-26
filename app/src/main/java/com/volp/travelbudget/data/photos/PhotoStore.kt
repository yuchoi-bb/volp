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

    /** 예약 하나에 붙은 티켓 사진. 보여 줄 차례대로 온다. */
    fun observeBookingPhotos(bookingId: Long): Flow<List<TripPhoto>> =
        dao.observeByBooking(bookingId).map { list -> list.map { it.toPhoto() } }

    /** 예약마다 사진이 몇 장 붙어 있는지. 일정표에서 티켓이 있는지 알려 주려고 쓴다. */
    fun observeBookingPhotoCounts(tripId: Long): Flow<Map<Long, Int>> =
        dao.observeBookingCounts(tripId).map { list -> list.associate { it.bookingId to it.count } }

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

    /**
     * 예약에 티켓 사진을 붙인다.
     *
     * 가족 여행이면 한 예약에 표가 여러 장이다. 그래서 여러 장을 받되 [MAX_BOOKING_PHOTOS]장까지만
     * 둔다. 그보다 많이 쌓이면 개표대 앞에서 찾는 데 오히려 시간이 걸린다.
     *
     * @return 실제로 붙인 장수.
     */
    suspend fun attachToBooking(tripId: Long, bookingId: Long, sources: List<Uri>): Int {
        if (bookingId <= 0L || sources.isEmpty()) return 0
        val already = dao.countForBooking(bookingId)
        val room = (MAX_BOOKING_PHOTOS - already).coerceAtLeast(0)
        var order = already

        var added = 0
        sources.take(room).forEach { source ->
            val photo = attach(tripId, expenseId = null, source = source, bookingId = bookingId, sortOrder = order)
            if (photo != null) {
                order++
                added++
            }
        }
        return added
    }

    /** 보여 줄 차례를 한 칸 옮긴다. */
    suspend fun moveBookingPhoto(bookingId: Long, photoId: Long, up: Boolean) {
        val photos = dao.findByBooking(bookingId)
        val index = photos.indexOfFirst { it.id == photoId }
        if (index < 0) return
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in photos.indices) return

        // 예전 사진은 차례가 모두 0이라 자리를 바꿔도 티가 안 난다. 한 번에 다시 매긴다.
        val reordered = photos.toMutableList()
        reordered[index] = photos[swapWith]
        reordered[swapWith] = photos[index]
        reordered.forEachIndexed { order, photo ->
            if (photo.sortOrder != order) dao.update(photo.copy(sortOrder = order))
        }
    }

    /** 고른 사진을 앱 저장소로 복사해 여행에 붙인다. */
    suspend fun attach(
        tripId: Long,
        expenseId: Long?,
        source: Uri,
        note: String = "",
        bookingId: Long? = null,
        sortOrder: Int = 0,
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
                bookingId = bookingId,
                filePath = target.absolutePath,
                takenAt = System.currentTimeMillis(),
                note = note,
                sortOrder = sortOrder,
            ),
        )
        TripPhoto(id = id, uri = Uri.fromFile(target), takenAt = target.lastModified(), saved = true)
    }

    suspend fun remove(photoId: Long) = withContext(Dispatchers.IO) {
        dao.findById(photoId)?.let { File(it.filePath).delete() }
        dao.deleteById(photoId)
    }

    companion object {
        /** 한 예약에 둘 수 있는 티켓 사진 수. */
        const val MAX_BOOKING_PHOTOS = 20
    }

    private fun TripPhotoEntity.toPhoto() = TripPhoto(
        id = id,
        uri = Uri.fromFile(File(filePath)),
        takenAt = takenAt,
        saved = true,
    )
}
