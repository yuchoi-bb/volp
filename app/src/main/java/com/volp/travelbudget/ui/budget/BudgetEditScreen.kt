@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.ui.common.LabeledRow
import com.volp.travelbudget.ui.common.NumberField
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatKrw

@Composable
fun BudgetEditScreen(
    tripId: Long,
    onDone: () -> Unit,
) {
    val viewModel: BudgetEditViewModel = viewModel(
        key = "budget-$tripId",
        factory = volpViewModelFactory { BudgetEditViewModel(it.repository, tripId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("예산 조정") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            Text(
                "앱이 예측한 금액을 그대로 써도 되고, 실제 계획에 맞게 고쳐도 된다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard("항목별 예산") {
                ExpenseCategory.entries.forEach { category ->
                    NumberField(
                        label = "${category.emoji} ${category.label}",
                        value = state.amounts[category].orEmpty(),
                        onValueChange = { viewModel.setAmount(category, it) },
                        suffix = "원",
                    )
                    val predicted = state.predicted[category] ?: 0L
                    Text(
                        "예측 ${formatKrw(predicted)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                LabeledRow(
                    label = "합계",
                    value = formatKrw(state.total),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedButton(
                onClick = viewModel::resetToPrediction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("예측값으로 되돌리기")
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("저장")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
