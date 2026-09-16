@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.inbox

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.cardsms.TransactionKind
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.BuildConfig
import com.volp.travelbudget.ui.common.CapturePermissionButtons
import com.volp.travelbudget.ui.common.rememberCaptureAccess
import com.volp.travelbudget.ui.common.DropdownField
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDateTime
import com.volp.travelbudget.util.formatForeign
import com.volp.travelbudget.util.formatKrw

@Composable
fun InboxScreen(onBack: () -> Unit) {
    val viewModel: InboxViewModel = viewModel(
        factory = volpViewModelFactory { InboxViewModel(it.repository, it.budgetAlertNotifier) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<InboxRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("미확인 결제") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionCard() }

            if (state.isEmpty) {
                item {
                    Text(
                        "새로 들어온 결제가 없다. 카드 문자나 카드사 앱 알림이 오면 여기에 쌓인다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.mine.isNotEmpty()) {
                item { SectionTitle("내 결제 ${state.mine.size}건") }
                items(state.mine, key = { it.transaction.id }) { row ->
                    PendingCard(
                        row = row,
                        trips = state.trips,
                        onAssign = { editing = row },
                        onIgnore = { viewModel.ignore(row.transaction.id) },
                    )
                }
            }

            if (state.others.isNotEmpty()) {
                item { SectionTitle("미분류 (다른 명의) ${state.others.size}건") }
                items(state.others, key = { it.transaction.id }) { row ->
                    PendingCard(
                        row = row,
                        trips = state.trips,
                        onAssign = { editing = row },
                        onIgnore = { viewModel.ignore(row.transaction.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editing?.let { row ->
        AssignDialog(
            row = row,
            trips = state.trips,
            onDismiss = { editing = null },
            onConfirm = { tripId, category, displayName, keepAlias ->
                viewModel.accept(
                    pendingId = row.transaction.id,
                    tripId = tripId,
                    category = category,
                    displayName = displayName,
                    rawMerchant = row.transaction.merchant,
                    rememberAlias = keepAlias,
                )
                editing = null
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

/** 문자·알림 권한이 없으면 수집이 되지 않으므로 맨 위에서 안내한다. */
@Composable
private fun PermissionCard() {
    // 권한이 아예 없는 빌드에서 권한을 달라고 하면 거절만 돌아온다. 무엇을 하면 되는지 말해 준다.
    if (!BuildConfig.CAN_CAPTURE) {
        ShareInsteadCard()
        return
    }

    val access = rememberCaptureAccess()
    if (access.allGranted) return

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("자동 수집을 켜려면", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            CapturePermissionButtons(access)
        }
    }
}

/**
 * 자동 수집이 없는 빌드에서 대신 무엇을 하면 되는지.
 *
 * 문자·알림 읽기 권한을 함께 요구하는 앱은 보이스피싱 앱과 지문이 같아 기기가 설치를 막는다.
 * 그래서 이 빌드에는 그 권한이 없고, 사람이 문자를 공유해서 넣는다.
 */
@Composable
private fun ShareInsteadCard() {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("카드 결제 넣는 법", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "이 빌드에는 문자·알림 읽기 권한이 없다. 결제 문자를 길게 눌러 Volp로 공유하면 " +
                    "금액과 가맹점을 읽어 넣어 준다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "저절로 모으려면 자동 수집판(volp-full)을 PC에서 adb로 설치해야 한다. " +
                    "두 빌드는 서명과 패키지가 같아 기록은 그대로 이어진다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PendingCard(
    row: InboxRow,
    trips: List<Trip>,
    onAssign: () -> Unit,
    onIgnore: () -> Unit,
) {
    val transaction = row.transaction
    val suggestedTrip = trips.firstOrNull { it.id == row.suggestedTripId }

    Card(Modifier.fillMaxWidth().clickable(onClick = onAssign)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.suggestedName.ifBlank { "가맹점 미상" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (transaction.isOverseas) {
                        formatForeign(transaction.amount, transaction.currencyCode)
                    } else {
                        formatKrw(transaction.amount.toLong())
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(formatDateTime(transaction.occurredAt))
                    append(" · ")
                    append(transaction.issuer.label)
                    append(" ")
                    append(transaction.cardLabel)
                    append(" · ")
                    append(transaction.source.label)
                    if (transaction.kind == TransactionKind.CANCEL) append(" · 취소")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = onAssign,
                    label = {
                        Text(suggestedTrip?.title ?: "여행 고르기")
                    },
                )
                AssistChip(
                    onClick = onAssign,
                    label = { Text(row.suggestedCategory.label) },
                )
                Box(Modifier.weight(1f))
                TextButton(onClick = onIgnore) { Text("무시") }
            }
        }
    }
}

@Composable
private fun AssignDialog(
    row: InboxRow,
    trips: List<Trip>,
    onDismiss: () -> Unit,
    onConfirm: (Long, ExpenseCategory, String, Boolean) -> Unit,
) {
    var tripId by remember(row.transaction.id) {
        mutableStateOf(row.suggestedTripId ?: trips.firstOrNull()?.id)
    }
    var category by remember(row.transaction.id) { mutableStateOf(row.suggestedCategory) }
    var displayName by remember(row.transaction.id) { mutableStateOf(row.suggestedName) }
    var rememberAlias by remember(row.transaction.id) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어느 여행의 지출인가요") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (trips.isEmpty()) {
                    Text("먼저 여행을 만들어야 지출을 옮길 수 있다.")
                } else {
                    DropdownField(
                        label = "여행",
                        selected = tripId ?: trips.first().id,
                        options = trips.map { it.id },
                        optionLabel = { id -> trips.firstOrNull { it.id == id }?.title ?: "여행" },
                        onSelect = { tripId = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    DropdownField(
                        label = "항목",
                        selected = category,
                        options = ExpenseCategory.entries,
                        optionLabel = { "${it.emoji} ${it.label}" },
                        onSelect = { category = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("가맹점 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "문자에 찍힌 이름: ${row.transaction.merchant}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("다음부터 이 가맹점 기억하기", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = rememberAlias, onCheckedChange = { rememberAlias = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tripId != null,
                onClick = { tripId?.let { onConfirm(it, category, displayName.trim(), rememberAlias) } },
            ) { Text("지출로 추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
