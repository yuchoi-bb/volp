@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.quick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.budget.CurrencyRates
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.ui.common.DropdownField
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatKrw
import java.text.DecimalFormat

private val digitFormat = DecimalFormat("#,###")

private const val BACKSPACE_KEY = "←"

/** 숫자판 배치. 0을 두 번 누르는 일이 잦아 00 키를 따로 뒀다. */
private val keypadRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("00", "0", BACKSPACE_KEY),
)

@Composable
fun QuickExpenseScreen(
    tripId: Long,
    onBack: () -> Unit,
) {
    val viewModel: QuickExpenseViewModel = viewModel(
        key = "quick-$tripId",
        factory = volpViewModelFactory { app ->
            QuickExpenseViewModel(
                repository = app.repository,
                exchangeRates = app.exchangeRates,
                alertNotifier = app.budgetAlertNotifier,
                requestedTripId = tripId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("현금 빠른 입력") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (state.savedCount > 0) {
                        Text(
                            "${state.savedCount}건 기록",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.trip == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (state.loading) "불러오는 중…" else "먼저 여행을 만들어야 지출을 넣을 수 있다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (state.trips.size > 1) {
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = "여행",
                    selected = state.trip!!.id,
                    options = state.trips.map { it.id },
                    optionLabel = { id -> state.trips.firstOrNull { it.id == id }?.title ?: "여행" },
                    onSelect = viewModel::selectTrip,
                )
            }

            AmountDisplay(state)

            if (!CurrencyRates.isKrw(state.currencyCode)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${state.currencyCode}로 입력", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.useLocalCurrency,
                        onCheckedChange = viewModel::setUseLocalCurrency,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.setCategory(category) },
                        label = { Text("${category.emoji} ${category.label}") },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.memo,
                onValueChange = viewModel::setMemo,
                label = { Text("메모 (선택)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Keypad(
                onDigit = viewModel::press,
                onBackspace = viewModel::backspace,
                modifier = Modifier.weight(1f),
            )

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("기록하기", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AmountDisplay(state: QuickExpenseUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            buildString {
                append(digitFormat.format(state.amount))
                append(if (state.useLocalCurrency) " ${state.currencyCode}" else "원")
            },
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        when {
            state.useLocalCurrency && state.amount > 0L -> Text(
                "원화로 약 ${formatKrw(state.amountKrw)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.lastSavedKrw != null && state.amount == 0L -> Text(
                "방금 ${formatKrw(state.lastSavedKrw)} 기록했다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            else -> Text(
                state.trip?.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keypadRows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { key ->
                    KeypadButton(
                        key = key,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onClick = { if (key == BACKSPACE_KEY) onBackspace() else onDigit(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    key: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        if (key == BACKSPACE_KEY) {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "한 자리 지우기")
        } else {
            Text(key, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }
}
