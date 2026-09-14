package com.volp.travelbudget.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.volp.travelbudget.BuildConfig
import com.volp.travelbudget.util.formatBytes

/**
 * 새 빌드가 올라오면 띄우는 안내. 사용자가 승낙해야만 내려받고 설치한다.
 */
@Composable
fun UpdatePrompt(
    state: UpdateState,
    viewModel: UpdateViewModel,
) {
    val context = LocalContext.current

    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("새 버전 ${state.release.versionName}") },
            text = {
                Column {
                    Text(
                        if (BuildConfig.CAN_SELF_INSTALL) {
                            "지금 내려받아 설치할까요?"
                        } else {
                            // 설치 권한이 없는 빌드다. 브라우저에서 받아 직접 설치해야 한다.
                            "릴리스 페이지를 열까요? 브라우저에서 내려받아 설치하면 된다."
                        },
                    )
                    if (state.release.sizeBytes > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            formatBytes(state.release.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.release.notes.take(300),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.fetch(state.release) }) {
                    Text(if (BuildConfig.CAN_SELF_INSTALL) "받아서 설치" else "받으러 가기")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skip(state.release) }) { Text("이 버전 건너뛰기") }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text("내려받는 중") },
            text = {
                Column {
                    Text("${(state.progress * 100).toInt()}%")
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { },
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("설치 준비 완료") },
            text = { Text("볼프 ${state.release.versionName}을 설치한다.") },
            confirmButton = {
                TextButton(onClick = { viewModel.install(state.file) }) { Text("설치") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text("나중에") }
            },
        )

        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("설치 권한이 필요하다") },
            text = { Text("이 앱이 APK를 설치할 수 있도록 '출처를 알 수 없는 앱' 권한을 켜 주세요. 켠 뒤 돌아오면 이어서 설치한다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.startActivity(viewModel.unknownSourcesIntent())
                        viewModel.retryInstall(state.release, state.file)
                    },
                ) { Text("설정 열기") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text("나중에") }
            },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("업데이트 실패") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("확인") } },
        )

        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text("최신 버전") },
            text = { Text("이미 최신 버전을 쓰고 있다.") },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("확인") } },
        )

        UpdateState.Checking, UpdateState.Idle -> Unit
    }
}
