package com.volp.travelbudget.data.backup

import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.update.UpdateChecker
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * 여행 기록을 Google Drive의 `볼프` 폴더에 넣고 꺼낸다.
 *
 * 복원용 JSON과 PC에서 바로 열어 볼 수 있는 CSV를 함께 올린다.
 */
class BackupManager(
    private val repository: TripRepository,
    private val settings: AppSettings,
    client: OkHttpClient = UpdateChecker.defaultClient(),
    private val drive: DriveClient = DriveClient(client),
) {

    /** @return 백업한 여행 수 */
    suspend fun backup(accessToken: String): Int {
        val folderId = resolveFolder(accessToken)
        val backups = repository.exportAll()
        val now = System.currentTimeMillis()

        drive.uploadText(
            token = accessToken,
            folderId = folderId,
            name = BackupPayload.JSON_FILE_NAME,
            mimeType = "application/json",
            content = BackupPayload.toJson(backups, now),
        )
        drive.uploadText(
            token = accessToken,
            folderId = folderId,
            name = BackupPayload.CSV_FILE_NAME,
            mimeType = "text/csv",
            content = BackupPayload.toCsv(backups),
        )

        settings.setLastBackupAt(now)
        return backups.size
    }

    /** @return 복원한 여행 수. 백업 파일이 없으면 null. */
    suspend fun restore(accessToken: String): Int? {
        val folderId = resolveFolder(accessToken)
        val fileId = drive.findFile(accessToken, folderId, BackupPayload.JSON_FILE_NAME) ?: return null
        val text = drive.downloadText(accessToken, fileId)
        return repository.importAll(BackupPayload.fromJson(text))
    }

    /** 폴더 아이디는 한 번 찾으면 설정에 기억해 둔다. */
    private suspend fun resolveFolder(accessToken: String): String {
        val remembered = settings.settings.first().driveFolderId
        if (remembered != null) return remembered

        val folderId = drive.findFolder(accessToken, FOLDER_NAME)
            ?: drive.createFolder(accessToken, FOLDER_NAME)
        settings.setDriveFolderId(folderId)
        return folderId
    }

    companion object {
        const val FOLDER_NAME = "볼프"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
