package com.volp.travelbudget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.sync.FirestoreSync
import com.volp.travelbudget.data.sync.SyncOutcome
import com.volp.travelbudget.domain.sync.SyncCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DeviceSyncState(
    val code: String = "",
    val autoEnabled: Boolean = true,
    val lastSyncAt: Long = 0L,
) {
    val hasCode: Boolean get() = SyncCode.isValid(code)
}

/** 지금 동기화가 어디까지 갔는지. */
sealed interface DeviceSyncStatus {
    data object Idle : DeviceSyncStatus
    data object Working : DeviceSyncStatus
    data class Done(val message: String) : DeviceSyncStatus
    data class Failed(val message: String) : DeviceSyncStatus
}

/**
 * 다른 안드로이드 기기와 기록을 맞추는 설정.
 *
 * 코드를 만든 기기가 첫 번째 기기, 그 코드를 옮겨 적은 기기가 두 번째 기기다. 코드를 바꾸면
 * 보는 자리가 통째로 바뀌므로 처음부터 다시 받아 온다.
 */
class DeviceSyncViewModel(
    private val settings: AppSettings,
    private val sync: FirestoreSync,
) : ViewModel() {

    val state: StateFlow<DeviceSyncState> = settings.settings
        .map { DeviceSyncState(it.syncCode, it.autoSyncEnabled, it.lastSyncAt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DeviceSyncState())

    private val _status = MutableStateFlow<DeviceSyncStatus>(DeviceSyncStatus.Idle)
    val status: StateFlow<DeviceSyncStatus> = _status.asStateFlow()

    val isConfigured: Boolean get() = sync.isConfigured

    /** 첫 기기에서 한 번 누른다. 이 코드를 다른 기기에 옮겨 적으면 둘이 같은 기록을 본다. */
    fun createCode() {
        viewModelScope.launch {
            settings.setSyncCode(SyncCode.newCode())
            // 코드를 새로 만들면 아직 아무것도 주고받지 않은 상태다.
            settings.setLastSyncAt(0L)
            _status.value = DeviceSyncStatus.Idle
        }
    }

    /** 다른 기기에서 만든 코드를 옮겨 적었을 때. */
    fun applyCode(raw: String) {
        val normalized = SyncCode.normalize(raw)
        viewModelScope.launch {
            if (!SyncCode.isValid(normalized)) {
                _status.value = DeviceSyncStatus.Failed("코드가 짧거나 잘못됐다")
                return@launch
            }
            settings.setSyncCode(normalized)
            // 상대 기기에 있던 기록을 처음부터 받아 와야 한다.
            settings.setLastSyncAt(0L)
            syncNow()
        }
    }

    fun setAutoEnabled(value: Boolean) {
        viewModelScope.launch { settings.setAutoSyncEnabled(value) }
    }

    fun syncNow() {
        if (_status.value == DeviceSyncStatus.Working) return
        viewModelScope.launch {
            _status.value = DeviceSyncStatus.Working
            _status.value = when (val outcome = sync.sync()) {
                is SyncOutcome.Done -> DeviceSyncStatus.Done(
                    "받은 기록 ${outcome.pulled}건 · 올린 기록 ${outcome.pushed}건",
                )
                SyncOutcome.NoCode -> DeviceSyncStatus.Failed("먼저 동기화 코드를 만들거나 넣어야 한다")
                SyncOutcome.NotConfigured ->
                    DeviceSyncStatus.Failed("이 빌드에는 Firebase 설정이 없어 기기끼리 맞출 수 없다")
                is SyncOutcome.Failed -> DeviceSyncStatus.Failed(outcome.message)
            }
        }
    }

    fun clearStatus() {
        _status.value = DeviceSyncStatus.Idle
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
