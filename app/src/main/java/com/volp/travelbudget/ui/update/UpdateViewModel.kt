package com.volp.travelbudget.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.update.ReleaseInfo
import com.volp.travelbudget.data.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** 업데이트 안내 상태. 화면은 이 값만 보고 그린다. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateState
    data class NeedsPermission(val release: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
    data object UpToDate : UpdateState
}

class UpdateViewModel(
    private val checker: UpdateChecker,
    private val settings: AppSettings,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * 새 버전을 확인한다.
     *
     * @param manual 사용자가 직접 눌렀는지. 직접 누른 경우에는 건너뛴 버전도 다시 보여 주고,
     *   최신 상태라는 것도 알려 준다.
     */
    fun check(manual: Boolean = false) {
        if (_state.value is UpdateState.Downloading) return
        viewModelScope.launch {
            _state.value = if (manual) UpdateState.Checking else _state.value
            val current = settings.settings.first()
            if (!manual && System.currentTimeMillis() - current.lastUpdateCheckAt < CHECK_INTERVAL_MS) return@launch

            val release = runCatching { checker.fetchLatest() }.getOrNull()
            settings.setLastUpdateCheckAt(System.currentTimeMillis())

            _state.value = when {
                release == null -> if (manual) UpdateState.Failed("최신 버전 정보를 가져오지 못했다") else UpdateState.Idle
                !checker.isNewer(release) -> if (manual) UpdateState.UpToDate else UpdateState.Idle
                !manual && release.versionCode == current.skippedVersionCode -> UpdateState.Idle
                else -> UpdateState.Available(release)
            }
        }
    }

    fun download(release: ReleaseInfo) {
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            val result = runCatching {
                checker.download(release) { progress ->
                    _state.value = UpdateState.Downloading(release, progress)
                }
            }
            _state.value = result.fold(
                onSuccess = { file ->
                    if (checker.canInstallPackages()) {
                        UpdateState.ReadyToInstall(release, file)
                    } else {
                        UpdateState.NeedsPermission(release, file)
                    }
                },
                onFailure = { UpdateState.Failed(it.message ?: "다운로드에 실패했다") },
            )
        }
    }

    fun install(file: File) {
        runCatching { checker.install(file) }
            .onFailure { _state.value = UpdateState.Failed(it.message ?: "설치 화면을 열지 못했다") }
    }

    /** 권한을 켜고 돌아왔을 때 다시 설치를 시도한다. */
    fun retryInstall(release: ReleaseInfo, file: File) {
        _state.value = if (checker.canInstallPackages()) {
            UpdateState.ReadyToInstall(release, file)
        } else {
            UpdateState.NeedsPermission(release, file)
        }
    }

    fun unknownSourcesIntent() = checker.unknownSourcesSettingsIntent()

    fun skip(release: ReleaseInfo) {
        viewModelScope.launch {
            settings.setSkippedVersionCode(release.versionCode)
            _state.value = UpdateState.Idle
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
