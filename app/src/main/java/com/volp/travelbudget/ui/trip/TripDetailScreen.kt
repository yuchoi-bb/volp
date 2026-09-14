@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.trip

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.summary.CategoryProgress
import com.volp.travelbudget.domain.summary.TripSummary
import com.volp.travelbudget.ui.common.BudgetBar
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.StatTile
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.ui.theme.BudgetColors
import com.volp.travelbudget.util.formatDateRange
import com.volp.travelbudget.util.formatDateWithDay
import com.volp.travelbudget.util.formatForeign
import com.volp.travelbudget.util.formatKrw
import com.volp.travelbudget.util.formatKrwShort
import com.volp.travelbudget.util.formatSignedKrw

@Composable
fun TripDetailScreen(
    tripId: Long,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditBudget: () -> Unit,
    onDeleted: () -> Unit,
) {
    val viewModel: TripDetailViewModel = viewModel(
        key = "trip-$tripId",
        factory = volpViewModelFactory { TripDetailViewModel(it.repository, tripId) },
    )
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    val current = summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.trip?.title ?: "여행") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onEditBudget) {
                        Icon(Icons.Default.Edit, contentDescription = "예산 조정")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "여행 삭제")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Default.Add, contentDescription = "지출 추가")
            }
        },
    ) { padding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("불러오는 중…")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SummaryCard(current) }
            item { CategoryCard(current) }

            item {
                Text(
                    "지출 내역 ${expenses.size}건",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (expenses.isEmpty()) {
                item {
                    Text(
                        "아직 입력한 지출이 없다. + 를 눌러 기록해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(expenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    onClick = { onEditExpense(expense.id) },
                    onDelete = { viewModel.deleteExpense(expense.id) },
                )
                HorizontalDivider()
            }

            item { Spacer(Modifier.height(64.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("여행을 삭제할까요") },
            text = { Text("이 여행에 기록한 지출도 함께 지워진다. 되돌릴 수 없다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteTrip(onDeleted)
                    },
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SummaryCard(summary: TripSummary) {
    val trip = summary.trip
    SectionCard {
        Text(
            formatDateRange(trip.startDate, trip.endDate, trip.nights),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Text(
            formatKrw(summary.totalSpent),
            style = MaterialTheme.typography.headlineMedium,
            color = if (summary.isOverBudget) BudgetColors.over else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "예산 ${formatKrw(summary.totalPlanned)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        BudgetBar(ratio = summary.progressRatio, overColor = BudgetColors.over)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile(
                label = if (summary.remaining >= 0) "남은 예산" else "예산 초과",
                value = formatKrwShort(kotlin.math.abs(summary.remaining)),
                valueColor = if (summary.remaining >= 0) BudgetColors.under else BudgetColors.over,
            )
            StatTile(
                label = "하루 평균",
                value = formatKrwShort(summary.dailyAverage),
                hint = "항공·숙박 제외",
            )
            StatTile(
                label = "하루 쓸 수 있는 돈",
                value = summary.dailyAllowance?.let { formatKrwShort(it) } ?: "-",
                hint = if (summary.remainingDays > 0) "${summary.remainingDays}일 남음" else "여행 종료",
            )
        }

        if (summary.projectedOverBudget && summary.remainingDays > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "지금 속도면 ${formatKrwShort(summary.projectedTotal)}까지 쓸 것 같다. " +
                    "예산보다 ${formatKrwShort(summary.projectedTotal - summary.totalPlanned)} 많다.",
                style = MaterialTheme.typography.bodySmall,
                color = BudgetColors.over,
            )
        }
    }
}

@Composable
private fun CategoryCard(summary: TripSummary) {
    SectionCard("항목별 예산 대비 지출") {
        summary.categories.forEach { progress ->
            CategoryRow(progress)
            Spacer(Modifier.height(14.dp))
        }
        Text(
            "예측 합계 ${formatKrwShort(summary.totalPredicted)} · 조정한 예산 ${formatKrwShort(summary.totalPlanned)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryRow(progress: CategoryProgress) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${progress.category.emoji} ${progress.category.label}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${formatKrwShort(progress.spent)} / ${formatKrwShort(progress.planned)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (progress.isOverBudget) BudgetColors.over else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        BudgetBar(ratio = progress.ratio, overColor = BudgetColors.over)
        if (progress.difference != 0L) {
            Spacer(Modifier.height(4.dp))
            Text(
                formatSignedKrw(progress.difference),
                style = MaterialTheme.typography.labelSmall,
                color = if (progress.isOverBudget) BudgetColors.over else BudgetColors.under,
            )
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                expense.memo.ifBlank { expense.category.label },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${formatDateWithDay(expense.date)} · ${expense.category.emoji} ${expense.category.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatKrw(expense.amountKrw),
                style = MaterialTheme.typography.bodyLarge,
                color = if (expense.amountKrw < 0L) BudgetColors.under else MaterialTheme.colorScheme.onSurface,
            )
            if (expense.enteredInForeignCurrency) {
                Text(
                    formatForeign(expense.originalAmount ?: 0.0, expense.currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "지출 삭제")
        }
    }
}
