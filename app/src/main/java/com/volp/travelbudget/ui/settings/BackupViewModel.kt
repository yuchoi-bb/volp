package com.volp.travelbudget.ui.settings

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.volp.travelbudget.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** 백업 화면이 지금 무엇을 하고 있는지. */
sealed interface BackupState {
    data object Idle : BackupState
    data class Working(val message: String) : BackupState

    /** Google 계정 동의 화면을 띄워야 하는 상태. */
    data class NeedsConsent(val intentSender: IntentSender) : BackupState
    data class Done(val message: String) : BackupState
    data class Failed(val message: String) : BackupState
}

private enum class BackupAction { BACKUP, RESTORE }

class BackupViewModel(
    private val context: Context,
    private val manager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private var pendingAction: BackupAction? = null

    fun backupNow() = start(BackupAction.BACKUP)

    fun restoreNow() = start(BackupAction.RESTORE)

    /** 동의 화면에서 돌아왔을 때 이어서 실행한다. */
    fun onConsentResult(data: Intent?) {
        val action = pendingAction ?: return
        viewModelScope.launch {
            val token = runCatching {
                Identity.getAuthorizationClient(context)
                    .getAuthorizationResultFromIntent(data)
                    .accessToken
            }.getOrNull()

            if (token.isNullOrBlank()) {
                _state.value = BackupState.Failed("Google 계정 접근 권한을 받지 못했다")
                pendingAction = null
            } else {
                run(action, token)
            }
        }
    }

    fun dismiss() {
        _state.value = BackupState.Idle
    }

    private fun start(action: BackupAction) {
        pendingAction = action
        viewModelScope.launch {
            _state.value = BackupState.Working("Google 계정을 확인하는 중")
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(BackupManager.DRIVE_SCOPE)))
                .build()

            val result = runCatching {
                Identity.getAuthorizationClient(context).authorize(request).await()
            }.getOrElse {
                _state.value = BackupState.Failed(it.message ?: "Google 계정 확인에 실패했다")
                pendingAction = null
                return@launch
            }

            val sender = result.pendingIntent?.intentSender
            if (result.hasResolution() && sender != null) {
                // 처음 쓸 때는 사용자가 드라이브 접근에 동의해야 한다.
                _state.value = BackupState.NeedsConsent(sender)
            } else {
                val token = result.accessToken
                if (token.isNullOrBlank()) {
                    _state.value = BackupState.Failed("Google 계정 접근 권한을 받지 못했다")
                    pendingAction = null
                } else {
                    run(action, token)
                }
            }
        }
    }

    private suspend fun run(action: BackupAction, token: String) {
        pendingAction = null
        _state.value = BackupState.Working(
            if (action == BackupAction.BACKUP) "드라이브에 올리는 중" else "드라이브에서 가져오는 중",
        )
        _state.value = runCatching {
            when (action) {
                BackupAction.BACKUP -> {
                    val count = manager.backup(token)
                    BackupState.Done("여행 ${count}건을 '${BackupManager.FOLDER_NAME}' 폴더에 백업했다")
                }

                BackupAction.RESTORE -> {
                    val count = manager.restore(token)
                    if (count == null) {
                        BackupState.Failed("드라이브에 백업 파일이 없다")
                    } else {
                        BackupState.Done("기록 ${count}건을 가져왔다")
                    }
                }
            }
        }.getOrElse { BackupState.Failed(it.message ?: "백업에 실패했다") }
    }
}
