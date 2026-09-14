@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.expense

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.budget.CurrencyRates
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.ui.common.DateField
import com.volp.travelbudget.ui.common.NumberField
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatKrw

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorScreen(
    tripId: Long,
    expenseId: Long,
    onDone: () -> Unit,
) {
    val viewModel: ExpenseEditorViewModel = viewModel(
        key = "expense-$tripId-$expenseId",
        factory = volpViewModelFactory { app ->
            ExpenseEditorViewModel(
                repository = app.repository,
                photoStore = app.photoStore,
                exchangeRates = app.exchangeRates,
                tripId = tripId,
                expenseId = expenseId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val queuedReceipts by viewModel.queuedReceipts.collectAsStateWithLifecycle()

    val receiptPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris ->
        viewModel.addReceipt(uris)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "지출 수정" else "지출 입력") },
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
            SectionCard("얼마를 썼나요") {
                if (!CurrencyRates.isKrw(state.currencyCode)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.currencyCode}로 입력",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = state.useLocalCurrency,
                            onCheckedChange = viewModel::setUseLocalCurrency,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                NumberField(
                    label = "금액",
                    value = state.amountInput,
                    onValueChange = viewModel::setAmountInput,
                    allowDecimal = state.useLocalCurrency,
                    suffix = if (state.useLocalCurrency) state.currencyCode else "원",
                )

                if (state.useLocalCurrency) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "원화로 약 ${formatKrw(state.amountKrw)} (환율 ${state.exchangeRate})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    NumberField(
                        label = "이 건에 적용할 환율",
                        value = state.exchangeRate.toString(),
                        onValueChange = viewModel::setExchangeRate,
                        allowDecimal = true,
                        suffix = "원",
                    )
                }
            }

            SectionCard("어떤 지출인가요") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { viewModel.setCategory(category) },
                            label = { Text("${category.emoji} ${category.label}") },
                        )
                    }
                }
            }

            SectionCard("언제, 어디서") {
                DateField("날짜", state.date, viewModel::setDate)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = viewModel::setMemo,
                    label = { Text("가맹점 또는 메모") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("영수증 사진") {
                if (receipts.isEmpty() && queuedReceipts.isEmpty()) {
                    Text(
                        "영수증을 찍어 두면 나중에 금액을 확인하기 쉽다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        receipts.forEach { photo ->
                            ReceiptThumbnail(
                                model = photo.uri,
                                onRemove = { viewModel.removeReceipt(photo.id) },
                            )
                        }
                        queuedReceipts.forEach { uri ->
                            ReceiptThumbnail(
                                model = uri,
                                onRemove = { viewModel.removeQueuedReceipt(uri) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        receiptPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("영수증 사진 넣기") }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isEditing) "수정 저장" else "기록하기")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReceiptThumbnail(model: Any, onRemove: () -> Unit) {
    Box(
        Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "사진 빼기",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
