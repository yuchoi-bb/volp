@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.smsimport

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.BuildConfig
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDateWithDay
import com.volp.travelbudget.util.formatForeign
import com.volp.travelbudget.util.formatKrw

/**
 * 문자에서 모은 지출을 확인하고 넣는 화면.
 *
 * 앱이 혼자 넣지 않는다. 카드 문자는 취소·승인거절·다른 사람 명의가 섞여 있고, 여행과 무관한
 * 결제도 그 기간에 얼마든지 있다. 사람이 목록을 보고 고른 것만 들어간다.
 */
@Composable
fun SmsImportScreen(
    tripId: Long? = null,
    sharedText: String? = null,
    onDone: () -> Unit,
) {
    val viewModel: SmsImportViewModel = viewModel(
        key = "sms-import-${tripId ?: 0L}-${sharedText?.hashCode() ?: 0}",
        factory = volpViewModelFactory { app ->
            SmsImportViewModel(app.repository, app.smsInbox, tripId, sharedText)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.READ_SMS] == true) viewModel.load()
    }

    LaunchedEffect(state.savedCount) {
        if (state.savedCount != null) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("문자에서 지출 모으기") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (state.rows.isNotEmpty()) {
                        TextButton(onClick = { viewModel.checkAll(state.selected.size != state.rows.size) }) {
                            Text(if (state.selected.size == state.rows.size) "모두 끄기" else "모두 켜기")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.rows.isNotEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${state.selected.size}건 넣기 · ${formatKrw(state.selectedTotalKrw)}")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "해외 결제는 오늘 환율로 환산해 넣는다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        if (state.loading) return@Scaffold

        if (state.rows.isEmpty()) {
            EmptyImport(
                needsPermission = state.needsPermission,
                onAskPermission = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                    )
                },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.trips.size > 1) {
                item(key = "trip") {
                    SectionCard("어느 여행에 넣을까요") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.trips.forEach { trip ->
                                FilterChip(
                                    selected = state.tripId == trip.id,
                                    onClick = { viewModel.setTrip(trip.id) },
                                    label = { Text(trip.title) },
                                )
                            }
                        }
                    }
                }
            }

            items(state.rows, key = { it.key }) { row ->
                ImportRow(
                    row = row,
                    onToggle = { viewModel.toggle(row.key) },
                    onCategory = { viewModel.setCategory(row.key, it) },
                )
            }

            item(key = "space") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EmptyImport(
    needsPermission: Boolean,
    onAskPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (needsPermission && BuildConfig.CAN_CAPTURE) {
                Text("문자를 읽어야 합니다", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "여행 기간에 받은 문자만 읽어 카드 결제를 찾는다. 그 밖의 문자는 건드리지 않는다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onAskPermission) { Text("문자 권한 허용") }
                return@Column
            }

            Text("찾은 결제가 없습니다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (BuildConfig.CAN_CAPTURE) {
                    "이 여행 기간에 카드 결제 문자가 없다."
                } else {
                    "문자 앱에서 결제 문자를 여러 건 골라 Volp로 공유하면 여기로 들어온다."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportRow(
    row: SmsImportRow,
    onToggle: () -> Unit,
    onCategory: (ExpenseCategory) -> Unit,
) {
    var pickingCategory by remember { mutableStateOf(false) }
    val transaction = row.transaction

    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = row.checked, onCheckedChange = { onToggle() })
                Spacer(Modifier.height(0.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        row.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${transaction.issuer.label} · ${formatDateWithDay(transaction.occurredAt.toLocalDate())}" +
                            if (transaction.kind.label != "승인") " · ${transaction.kind.label}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    if (transaction.isOverseas) {
                        formatForeign(transaction.signedAmount, transaction.currencyCode)
                    } else {
                        formatKrw(transaction.signedAmount.toLong())
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (row.alreadyThere) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "같은 날 같은 금액이 이미 있다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            if (pickingCategory) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.entries.forEach { category ->
                        FilterChip(
                            selected = row.category == category,
                            onClick = {
                                onCategory(category)
                                pickingCategory = false
                            },
                            label = { Text("${category.emoji} ${category.label}") },
                        )
                    }
                }
            } else {
                TextButton(onClick = { pickingCategory = true }) {
                    Text("${row.category.emoji} ${row.category.label}")
                }
            }
        }
    }
}
