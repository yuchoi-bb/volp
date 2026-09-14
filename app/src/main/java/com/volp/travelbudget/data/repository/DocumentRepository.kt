package com.volp.travelbudget.data.repository

import android.content.Context
import android.net.Uri
import com.volp.travelbudget.data.local.DocumentDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.local.toEntity
import com.volp.travelbudget.domain.document.TravelDocument
import com.volp.travelbudget.domain.sync.SyncIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 여권·보험증서 같은 문서를 맡는다.
 *
 * 이 기록은 다른 기기로 옮기지 않는다. 사진 자체가 민감하고, 기기끼리 맞추려면 그 사진을 밖으로
 * 내보내야 하기 때문이다. 해외에서 꺼내 보는 것이 목적이므로 기기 안에 두는 것으로 충분하다.
 */
class DocumentRepository(
    private val context: Context,
    private val dao: DocumentDao,
) {

    fun observeAll(): Flow<List<TravelDocument>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun find(id: Long): TravelDocument? = dao.findById(id)?.toDomain()

    suspend fun save(document: TravelDocument): Long {
        val stamped = document.copy(
            uid = document.uid.ifBlank { SyncIds.newUid() },
            updatedAt = SyncIds.now(),
            createdAt = if (document.createdAt > 0L) document.createdAt else SyncIds.now(),
        )
        return if (stamped.id > 0L) {
            dao.update(stamped.toEntity())
            stamped.id
        } else {
            dao.insert(stamped.toEntity())
        }
    }

    suspend fun delete(id: Long) {
        // 사진도 함께 지운다. 표에서만 지우면 지웠다고 생각한 사진이 기기에 남는다.
        dao.findById(id)?.filePath?.let { path -> runCatching { File(path).delete() } }
        dao.deleteById(id)
    }

    /**
     * 고른 사진을 앱 안으로 복사한다.
     *
     * 갤러리 쪽 사진은 사용자가 지우면 같이 사라지고, 다른 앱도 볼 수 있다. 보관함에 넣은 문서는
     * 앱 저장소에 따로 둔다.
     */
    suspend fun copyImage(source: Uri): String? = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}.jpg")

        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)

        if (copied) target.absolutePath else null
    }

    private companion object {
        /** 백업에서 빼는 경로와 같아야 한다. `backup_rules.xml`을 함께 고칠 것. */
        const val DIRECTORY = "documents"
    }
}
