@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.BuildConfig
import com.volp.travelbudget.data.backup.BackupManager
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatTimestamp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = volpViewModelFactory { SettingsViewModel(AppSettings(it)) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val backupViewModel: BackupViewModel = viewModel(
        factory = volpViewModelFactory { app ->
            BackupViewModel(app, BackupManager(app.repository, app.settings))
        },
    )
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        backupViewModel.onConsentResult(result.data)
    }

    LaunchedEffect(backupState) {
        val needsConsent = backupState as? BackupState.NeedsConsent ?: return@LaunchedEffect
        consentLauncher.launch(IntentSenderRequest.Builder(needsConsent.intentSender).build())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("카드 결제 자동 기록") {
                OutlinedTextField(
                    value = state.ownerName,
                    onValueChange = viewModel::setOwnerName,
                    label = { Text("카드 문자에 찍히는 내 이름") },
                    placeholder = { Text("예: 최*업") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "다른 사람 명의로 결제된 문자는 여기 이름과 비교해 '미분류'로 따로 모은다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    label = "문자·알림에서 결제 내역 모으기",
                    checked = state.captureEnabled,
                    onCheckedChange = viewModel::setCaptureEnabled,
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    label = "확실한 결제는 바로 기록",
                    checked = state.autoAssignEnabled,
                    onCheckedChange = viewModel::setAutoAssignEnabled,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "결제 시각이 딱 한 여행의 기간 안에 들어가고 항목까지 알 수 있으면 확인 없이 바로 넣는다. " +
                        "그 밖에는 미확인함에 쌓인다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("예산 경고") {
                ToggleRow(
                    label = "예산을 넘길 것 같으면 알리기",
                    checked = state.budgetAlertsEnabled,
                    onCheckedChange = viewModel::setBudgetAlertsEnabled,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "하루 몫을 크게 넘겼을 때, 지금 속도로 예산을 넘길 것 같을 때, 이미 넘겼을 때 알린다. " +
                        "같은 알림은 한 번만 울린다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("백업") {
                ToggleRow(
                    label = "Google Drive 자동 백업",
                    checked = state.autoBackupEnabled,
                    onCheckedChange = viewModel::setAutoBackupEnabled,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "내 드라이브의 '${BackupManager.FOLDER_NAME}' 폴더에 복원용 JSON과 열람용 CSV를 올린다. " +
                        "자동 백업은 하루에 한 번 돈다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "마지막 백업 ${formatTimestamp(state.lastBackupAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = backupViewModel::backupNow,
                        enabled = backupState !is BackupState.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text("지금 백업") }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = backupState !is BackupState.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text("복원") }
                }
            }

            SectionCard("앱 정보") {
                Text("버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) {
                    Text("업데이트 확인")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    BackupDialogs(
        state = backupState,
        confirmRestore = confirmRestore,
        onConfirmRestore = {
            confirmRestore = false
            backupViewModel.restoreNow()
        },
        onCancelRestore = { confirmRestore = false },
        onDismiss = backupViewModel::dismiss,
    )
}

@Composable
private fun BackupDialogs(
    state: BackupState,
    confirmRestore: Boolean,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("백업으로 되돌릴까요") },
            text = { Text("지금 기기에 있는 여행 기록을 드라이브 백업으로 덮어쓴다. 되돌릴 수 없다.") },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text("복원") } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("취소") } },
        )
        return
    }

    when (state) {
        is BackupState.Working -> AlertDialog(
            onDismissRequest = { },
            title = { Text(state.message) },
            text = { CircularProgressIndicator() },
            confirmButton = { },
        )

        is BackupState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("완료") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        )

        is BackupState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("실패") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        )

        is BackupState.NeedsConsent, BackupState.Idle -> Unit
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
