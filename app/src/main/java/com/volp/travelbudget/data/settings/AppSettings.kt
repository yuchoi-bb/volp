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
    /** 여행 기간과 항목이 확실한 결제는 확인 없이 바로 지출로 넣을지. */
    val autoAssignEnabled: Boolean = true,
    /** 예산을 넘겼거나 넘길 것 같을 때 알려 줄지. */
    val budgetAlertsEnabled: Boolean = true,
    /** 한 시간 안에 비가 오면 알려 줄지. */
    val rainAlertsEnabled: Boolean = true,
    /** 마지막으로 알린 비가 시작되는 시각. 같은 비로 두 번 울리지 않게 기억한다. */
    val lastRainAlertFor: String = "",
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
    /** 여행 목록을 늘어놓는 차례. [com.volp.travelbudget.ui.trips.TripSort]의 이름이다. */
    val tripSort: String = "",
    /** 기기끼리 기록을 맞출 때 쓰는 공유 코드. 비어 있으면 이 기기만 쓴다. */
    val syncCode: String = "",
    /** 기록이 바뀔 때마다 자동으로 맞출지. */
    val autoSyncEnabled: Boolean = true,
    /** 마지막으로 동기화에 성공한 시각(epoch millis). 이 뒤에 바뀐 것만 주고받는다. */
    val lastSyncAt: Long = 0L,
)

class AppSettings(private val context: Context) {

    private object Keys {
        val ownerName = stringPreferencesKey("owner_name")
        val captureEnabled = booleanPreferencesKey("capture_enabled")
        val autoAssignEnabled = booleanPreferencesKey("auto_assign_enabled")
        val budgetAlertsEnabled = booleanPreferencesKey("budget_alerts_enabled")
        val rainAlertsEnabled = booleanPreferencesKey("rain_alerts_enabled")
        val lastRainAlertFor = stringPreferencesKey("last_rain_alert_for")
        val autoBackupEnabled = booleanPreferencesKey("auto_backup_enabled")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val driveFolderId = stringPreferencesKey("drive_folder_id")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
        val skippedVersionCode = intPreferencesKey("skipped_version_code")
        val tripSort = stringPreferencesKey("trip_sort")
        val syncCode = stringPreferencesKey("sync_code")
        val autoSyncEnabled = booleanPreferencesKey("auto_sync_enabled")
        val lastSyncAt = longPreferencesKey("last_sync_at")
    }

    val settings: Flow<VolpSettings> = context.settingsStore.data.map { prefs ->
        VolpSettings(
            ownerName = prefs[Keys.ownerName].orEmpty(),
            captureEnabled = prefs[Keys.captureEnabled] ?: true,
            autoAssignEnabled = prefs[Keys.autoAssignEnabled] ?: true,
            budgetAlertsEnabled = prefs[Keys.budgetAlertsEnabled] ?: true,
            rainAlertsEnabled = prefs[Keys.rainAlertsEnabled] ?: true,
            lastRainAlertFor = prefs[Keys.lastRainAlertFor].orEmpty(),
            autoBackupEnabled = prefs[Keys.autoBackupEnabled] ?: true,
            lastBackupAt = prefs[Keys.lastBackupAt] ?: 0L,
            driveFolderId = prefs[Keys.driveFolderId],
            lastUpdateCheckAt = prefs[Keys.lastUpdateCheckAt] ?: 0L,
            skippedVersionCode = prefs[Keys.skippedVersionCode] ?: 0,
            tripSort = prefs[Keys.tripSort] ?: "",
            syncCode = prefs[Keys.syncCode] ?: "",
            autoSyncEnabled = prefs[Keys.autoSyncEnabled] ?: true,
            lastSyncAt = prefs[Keys.lastSyncAt] ?: 0L,
        )
    }

    suspend fun setOwnerName(value: String) = edit { it[Keys.ownerName] = value.trim() }

    suspend fun setCaptureEnabled(value: Boolean) = edit { it[Keys.captureEnabled] = value }

    suspend fun setAutoAssignEnabled(value: Boolean) = edit { it[Keys.autoAssignEnabled] = value }

    suspend fun setBudgetAlertsEnabled(value: Boolean) = edit { it[Keys.budgetAlertsEnabled] = value }

    suspend fun setRainAlertsEnabled(value: Boolean) = edit { it[Keys.rainAlertsEnabled] = value }

    suspend fun setLastRainAlertFor(value: String) = edit { it[Keys.lastRainAlertFor] = value }

    suspend fun setAutoBackupEnabled(value: Boolean) = edit { it[Keys.autoBackupEnabled] = value }

    suspend fun setLastBackupAt(value: Long) = edit { it[Keys.lastBackupAt] = value }

    suspend fun setDriveFolderId(value: String) = edit { it[Keys.driveFolderId] = value }

    suspend fun setLastUpdateCheckAt(value: Long) = edit { it[Keys.lastUpdateCheckAt] = value }

    suspend fun setSkippedVersionCode(value: Int) = edit { it[Keys.skippedVersionCode] = value }

    suspend fun setTripSort(value: String) = edit { it[Keys.tripSort] = value }

    suspend fun setSyncCode(value: String) = edit { it[Keys.syncCode] = value.trim() }

    suspend fun setAutoSyncEnabled(value: Boolean) = edit { it[Keys.autoSyncEnabled] = value }

    suspend fun setLastSyncAt(value: Long) = edit { it[Keys.lastSyncAt] = value }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}
