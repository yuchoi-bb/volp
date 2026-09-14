package com.volp.travelbudget.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "volp_settings")

/** 앱 설정 한 벌. 화면에서는 이 값 하나만 구독하면 된다. */
data class VolpSettings(
    /** 카드 문자에 찍히는 내 이름(예: `최*업`). 다른 사람 명의 결제를 가려내는 데 쓴다. */
    val ownerName: String = "",
    /** 카드 문자·알림에서 결제 내역을 자동으로 모을지. */
    val captureEnabled: Boolean = true,
    /** Drive 자동 백업을 켤지. */
    val autoBackupEnabled: Boolean = true,
    /** 마지막으로 백업에 성공한 시각(epoch millis). 0이면 아직 없다. */
    val lastBackupAt: Long = 0L,
    /** 백업을 넣어 둔 Drive 폴더. 매번 찾지 않으려고 기억해 둔다. */
    val driveFolderId: String? = null,
    /** 마지막 업데이트 확인 시각. */
    val lastUpdateCheckAt: Long = 0L,
    /** 사용자가 건너뛴 버전. 같은 버전은 다시 묻지 않는다. */
    val skippedVersionCode: Int = 0,
)

class AppSettings(private val context: Context) {

    private object Keys {
        val ownerName = stringPreferencesKey("owner_name")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val autoBackupEnabled = booleanPreferencesKey("auto_backup_enabled")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val driveFolderId = stringPreferencesKey("drive_folder_id")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
        val skippedVersionCode = intPreferencesKey("skipped_version_code")
    }

    val settings: Flow<VolpSettings> = context.settingsStore.data.map { prefs ->
        VolpSettings(
            ownerName = prefs[Keys.ownerName].orEmpty(),
            captureEnabled = prefs[Keys.captureEnabled] ?: true,
            autoBackupEnabled = prefs[Keys.autoBackupEnabled] ?: true,
            lastBackupAt = prefs[Keys.lastBackupAt] ?: 0L,
            driveFolderId = prefs[Keys.driveFolderId],
            lastUpdateCheckAt = prefs[Keys.lastUpdateCheckAt] ?: 0L,
            skippedVersionCode = prefs[Keys.skippedVersionCode] ?: 0,
        )
    }

    suspend fun setOwnerName(value: String) = edit { it[Keys.ownerName] = value.trim() }

    suspend fun setCaptureEnabled(value: Boolean) = edit { it[Keys.captureEnabled] = value }

    suspend fun setAutoBackupEnabled(value: Boolean) = edit { it[Keys.autoBackupEnabled] = value }

    suspend fun setLastBackupAt(value: Long) = edit { it[Keys.lastBackupAt] = value }

    suspend fun setDriveFolderId(value: String) = edit { it[Keys.driveFolderId] = value }

    suspend fun setLastUpdateCheckAt(value: Long) = edit { it[Keys.lastUpdateCheckAt] = value }

    suspend fun setSkippedVersionCode(value: Int) = edit { it[Keys.skippedVersionCode] = value }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
