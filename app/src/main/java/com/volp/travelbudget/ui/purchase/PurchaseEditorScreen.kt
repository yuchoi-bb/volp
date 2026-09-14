@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.purchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.purchase.PurchaseKind
import com.volp.travelbudget.domain.purchase.PurchaseStatus
import com.volp.travelbudget.ui.common.DateField
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDate

/**
 * 여행 전에 산 것을 넣고 고치는 화면.
 *
 * 문자를 공유해 들어오면 값이 미리 채워진 채로 열린다. 그래도 저장은 사람이 한 번 보고 누른다.
 */
@Composable
fun PurchaseEditorScreen(
    purchaseId: Long = 0L,
    tripId: Long? = null,
    sharedText: String? = null,
    onDone: () -> Unit,
) {
    val viewModel: PurchaseEditorViewModel = viewModel(
        key = "purchase-$purchaseId-${sharedText?.hashCode() ?: 0}",
        factory = volpViewModelFactory { app ->
            PurchaseEditorViewModel(
                purchases = app.purchaseRepository,
                trips = app.repository,
                purchaseId = purchaseId,
                sharedText = sharedText,
                presetTripId = tripId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "구매 고치기" else "구매 넣기") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제")
                        }
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
            if (state.fromShare) {
                SectionCard {
                    Text("공유한 글에서 읽어 왔습니다", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "잘못 읽은 곳이 있으면 고친 뒤 저장하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("무엇을 샀나요") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PurchaseKind.entries.forEach { kind ->
                        FilterChip(
                            selected = state.kind == kind,
                            onClick = { viewModel.setKind(kind) },
                            label = { Text("${kind.emoji} ${kind.label}") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("이름") },
                    placeholder = { Text("예: 28인치 캐리어") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.merchant,
                    onValueChange = viewModel::setMerchant,
                    label = { Text("어디서 (선택)") },
                    placeholder = { Text("예: 쿠팡") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::setAmount,
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("어느 여행에 쓸 건가요") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.tripId == null,
                        onClick = { viewModel.setTrip(null) },
                        label = { Text("아직 안 정함") },
                    )
                    state.trips.forEach { trip ->
                        FilterChip(
                            selected = state.tripId == trip.id,
                            onClick = { viewModel.setTrip(trip.id) },
                            label = { Text(trip.title) },
                        )
                    }
                }
            }

            SectionCard("언제 오나요") {
                DateField(label = "주문일", date = state.orderedOn, onDateChange = viewModel::setOrderedOn)

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("도착 예정일 넣기", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.hasEta, onCheckedChange = viewModel::setHasEta)
                }

                if (state.hasEta) {
                    Spacer(Modifier.height(12.dp))
                    DateField(label = "도착 예정", date = state.eta, onDateChange = viewModel::setEta)
                }

                state.lateForTrip?.let { trip ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${trip.title} 출발일(${formatDate(trip.startDate)})까지 못 받을 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PurchaseStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.status == status,
                            onClick = { viewModel.setStatus(status) },
                            label = { Text(status.label) },
                        )
                    }
                }
            }

            SectionCard("배송 정보 (선택)") {
                OutlinedTextField(
                    value = state.orderNumber,
                    onValueChange = viewModel::setOrderNumber,
                    label = { Text("주문·예약번호") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.trackingNumber,
                    onValueChange = viewModel::setTrackingNumber,
                    label = { Text("송장번호") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.carrier,
                    onValueChange = viewModel::setCarrier,
                    label = { Text("택배사") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = viewModel::setMemo,
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.sourceText.isNotBlank()) {
                SectionCard("공유한 원문") {
                    Text(
                        state.sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("저장")
            }
        }
    }
}
