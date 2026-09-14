package com.volp.travelbudget.data.backup

import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.sync.SyncCodec
import com.volp.travelbudget.data.sync.SyncEngine
import com.volp.travelbudget.data.sync.SyncSnapshot
import com.volp.travelbudget.data.update.UpdateChecker
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * 여행 기록을 Google Drive의 `볼프` 폴더에 넣고 꺼낸다.
 *
 * 복원용 JSON과 PC에서 바로 열어 볼 수 있는 CSV를 함께 올린다. 기기끼리 맞추는 일은
 * Firestore가 맡고, 이쪽은 **계정이 통째로 날아갔을 때를 대비한 사본**이다.
 *
 * 가져오기는 덮어쓰지 않고 합친다. 두 기기가 각자 넣은 기록이 사라지면 안 되기 때문이다.
 */
class BackupManager(
    private val engine: SyncEngine,
    private val settings: AppSettings,
    client: OkHttpClient = UpdateChecker.defaultClient(),
    private val drive: DriveClient = DriveClient(client),
) {

    /** @return 백업한 여행 수 */
    suspend fun backup(accessToken: String): Int {
        val folderId = resolveFolder(accessToken)
        val snapshot = engine.snapshot()
        upload(accessToken, folderId, snapshot)

        settings.setLastBackupAt(snapshot.exportedAt)
        return snapshot.trips.size
    }

    /**
     * 드라이브에 있던 기록을 이 기기에 합친다.
     *
     * @return 받아 온 기록 수. 백업 파일이 없으면 null.
     */
    suspend fun restore(accessToken: String): Int? {
        val folderId = resolveFolder(accessToken)
        val fileId = drive.findFile(accessToken, folderId, SyncSnapshot.FILE_NAME) ?: return null

        val remote = SyncCodec.fromJson(drive.downloadText(accessToken, fileId))
        val result = engine.mergeIn(remote)
        // 합친 결과를 도로 올려 둬야 다음에 받을 때도 두 쪽이 같다.
        upload(accessToken, folderId, result.snapshot)

        return result.pulled
    }

    private suspend fun upload(accessToken: String, folderId: String, snapshot: SyncSnapshot) {
        drive.uploadText(
            token = accessToken,
            folderId = folderId,
            name = SyncSnapshot.FILE_NAME,
            mimeType = "application/json",
            content = SyncCodec.toJson(snapshot),
        )
        drive.uploadText(
            token = accessToken,
            folderId = folderId,
            name = SyncSnapshot.CSV_FILE_NAME,
            mimeType = "text/csv",
            content = SyncCodec.toCsv(snapshot),
        )
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
