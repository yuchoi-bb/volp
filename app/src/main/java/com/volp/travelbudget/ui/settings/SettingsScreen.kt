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
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.BuildConfig
import com.volp.travelbudget.data.backup.BackupManager
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.ui.common.CapturePermissionButtons
import com.volp.travelbudget.ui.common.DateField
import com.volp.travelbudget.ui.common.rememberCaptureAccess
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.BuildIdentity
import com.volp.travelbudget.util.formatTimestamp
import java.time.LocalDate

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
            BackupViewModel(app, app.backupManager)
        },
    )
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    var confirmRestore by remember { mutableStateOf(false) }

    val syncViewModel: DeviceSyncViewModel = viewModel(
        factory = volpViewModelFactory { app ->
            DeviceSyncViewModel(app.settings, app.firestoreSync)
        },
    )
    val syncState by syncViewModel.state.collectAsStateWithLifecycle()
    val syncStatus by syncViewModel.status.collectAsStateWithLifecycle()
    var codeInput by remember { mutableStateOf("") }

    val transferViewModel: RecordTransferViewModel = viewModel(
        factory = volpViewModelFactory { app -> RecordTransferViewModel(app.recordTransfer) },
    )
    val transferStatus by transferViewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(transferViewModel::exportTo) }

    // 다른 앱이 붙여 주는 이름이 제각각이라 json으로만 좁히면 고르지 못하는 파일이 생긴다.
    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(transferViewModel::importFrom) }

    // 파일이 만들어지면 곧바로 보내기 화면을 띄운다. 어디로 보낼지는 안드로이드가 묻는다.
    LaunchedEffect(transferStatus) {
        val ready = transferStatus as? TransferStatus.ReadyToSend ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, ready.uri)
            putExtra(Intent.EXTRA_SUBJECT, "볼프 기록")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "기록 보내기"))
        }
        transferViewModel.sent(ready.message)
    }

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
                if (BuildConfig.CAN_CAPTURE) {
                    val access = rememberCaptureAccess()
                    if (access.allGranted) {
                        Text(
                            "문자와 알림을 읽을 수 있다. 새 결제가 오면 저절로 들어온다.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            "아직 권한이 없어 한 통도 못 읽는다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        CapturePermissionButtons(access)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (!BuildConfig.CAN_CAPTURE) {
                    // 권한이 아예 없는 빌드다. 스위치를 켜 봐야 아무 일도 일어나지 않는다.
                    Text(
                        "이 빌드에는 문자·알림 읽기 권한이 없다. 카드 결제는 문자를 길게 눌러 " +
                            "Volp로 공유하면 들어온다.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "자동으로 모으려면 자동 수집판(volp-full)을 ADB로 설치해야 한다. " +
                            "두 빌드는 서명과 패키지가 같아 기록은 그대로 이어진다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }

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

            SectionCard("비 알림") {
                ToggleRow(
                    label = "한 시간 안에 비가 오면 알리기",
                    checked = state.rainAlertsEnabled,
                    onCheckedChange = viewModel::setRainAlertsEnabled,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "여행 기간에만 15분마다 지금 있는 곳의 강수 예보를 본다. 같은 비로 두 번 울리지 않는다. " +
                        "위치는 예보를 받는 데만 쓰고 기기 밖으로 나가지 않는다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("출발 준비") {
                ToggleRow(
                    label = "출발 전 준비와 배송 도착 알림",
                    checked = state.prepRemindersEnabled,
                    onCheckedChange = viewModel::setPrepRemindersEnabled,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "일주일 전과 하루 전에 한 번씩, 아직 안 챙긴 것과 못 받은 주문을 아침에 알려 준다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                val passport = remember(state.passportExpiry) {
                    runCatching { LocalDate.parse(state.passportExpiry) }.getOrNull()
                }
                if (passport == null) {
                    OutlinedButton(
                        onClick = { viewModel.setPassportExpiry(LocalDate.now().plusYears(5)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("여권 만료일 넣기") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "넣어 두면 출발 전에 잔여 유효기간이 모자라지 않은지 확인한다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DateField(
                        label = "여권 만료일",
                        date = passport,
                        onDateChange = { viewModel.setPassportExpiry(it) },
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { viewModel.setPassportExpiry(null) }) {
                        Text("여권 만료일 지우기")
                    }
                }
            }

            SectionCard("다른 기기와 함께 쓰기") {
                if (!BuildConfig.HAS_FIREBASE) {
                    Text(
                        "이 빌드에는 Firebase 설정이 없어 기기끼리 맞출 수 없다. " +
                            "GOOGLE_SERVICES_JSON 시크릿을 넣고 다시 빌드하면 켜진다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    "같은 동기화 코드를 넣은 안드로이드 기기끼리 같은 기록을 본다. " +
                        "코드는 비밀번호와 같으니 남에게 알리지 않는다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                if (syncState.hasCode) {
                    Text("내 동기화 코드", style = MaterialTheme.typography.labelMedium)
                    Text(syncState.code, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "마지막 동기화 ${formatTimestamp(syncState.lastSyncAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "아직 코드가 없다. 첫 기기에서 코드를 만들고, 다른 기기에서는 그 코드를 넣는다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    label = "앱을 열 때와 몇 시간마다 자동으로 맞추기",
                    checked = syncState.autoEnabled,
                    onCheckedChange = syncViewModel::setAutoEnabled,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = syncViewModel::syncNow,
                        enabled = syncState.hasCode && syncStatus !is DeviceSyncStatus.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text("지금 맞추기") }
                    OutlinedButton(
                        onClick = syncViewModel::createCode,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (syncState.hasCode) "새 코드" else "코드 만들기") }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("다른 기기의 코드 넣기") },
                    placeholder = { Text("예: A3F9-K2MP-7XQR-5TWB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        syncViewModel.applyCode(codeInput)
                        codeInput = ""
                    },
                    enabled = codeInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("이 코드로 맞추기") }

                when (val status = syncStatus) {
                    is DeviceSyncStatus.Working -> {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.height(18.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("  맞추는 중", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is DeviceSyncStatus.Done -> {
                        Spacer(Modifier.height(12.dp))
                        Text(status.message, style = MaterialTheme.typography.bodySmall)
                    }
                    is DeviceSyncStatus.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    DeviceSyncStatus.Idle -> Unit
                }
            }

            SectionCard("파일로 주고받기") {
                Text(
                    "기록을 파일 하나로 내보내 다른 기기에서 가져온다. 계정도 인터넷도 필요 없어서 " +
                        "해외에서 데이터가 안 되거나 기기를 바꿀 때 쓴다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "받은 파일은 덮어쓰지 않고 합친다. 두 기기에서 따로 넣은 것이 둘 다 남고, " +
                        "같은 기록은 나중에 고친 쪽이 남는다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = transferViewModel::prepareSend,
                        enabled = transferStatus !is TransferStatus.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text("보내기") }
                    OutlinedButton(
                        onClick = { saveFileLauncher.launch(transferViewModel.suggestedFileName()) },
                        enabled = transferStatus !is TransferStatus.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text("파일로 저장") }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    enabled = transferStatus !is TransferStatus.Working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("받은 파일 가져오기") }

                when (val status = transferStatus) {
                    is TransferStatus.Working -> {
                        Spacer(Modifier.height(12.dp))
                        Text(status.message, style = MaterialTheme.typography.bodySmall)
                    }
                    is TransferStatus.Done -> {
                        Spacer(Modifier.height(12.dp))
                        Text(status.message, style = MaterialTheme.typography.bodySmall)
                    }
                    is TransferStatus.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is TransferStatus.ReadyToSend, TransferStatus.Idle -> Unit
                }
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
                Text(
                    "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · " +
                        if (BuildConfig.CAN_CAPTURE) "자동 수집판" else "안전판",
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    if (BuildConfig.HAS_MAPS_KEY) "지도 키 있음" else "지도 키 없음 · 지도가 뜨지 않는다",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (BuildConfig.HAS_MAPS_KEY) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )

                val sha1 = remember { BuildIdentity.signingSha1(context) }
                if (sha1 != null) {
                    Spacer(Modifier.height(10.dp))
                    Text("서명 지문 (SHA-1)", style = MaterialTheme.typography.labelMedium)
                    Text(
                        sha1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (BuildIdentity.isDebugSigned(sha1)) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "디버그 키로 서명된 빌드다. 이대로는 릴리스 빌드로 업데이트할 수 없고 " +
                                "Google 로그인과 지도도 동작하지 않는다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "지도 키와 Google 로그인 제한을 이 지문으로 걸어야 한다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

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
