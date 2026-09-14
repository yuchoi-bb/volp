package com.volp.travelbudget.ui.trip.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.volp.travelbudget.domain.model.Expense
import com.volp.travelbudget.domain.summary.CategoryProgress
import com.volp.travelbudget.domain.summary.TripSummary
import com.volp.travelbudget.ui.common.BudgetBar
import com.volp.travelbudget.ui.common.LabeledRow
import com.volp.travelbudget.ui.common.NumberField
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.StatTile
import com.volp.travelbudget.ui.theme.BudgetColors
import com.volp.travelbudget.ui.trip.TripUiState
import com.volp.travelbudget.util.formatDateWithDay
import com.volp.travelbudget.util.formatForeign
import com.volp.travelbudget.util.formatKrw
import com.volp.travelbudget.util.formatKrwShort
import com.volp.travelbudget.util.formatSignedKrw

/**
 * 예산과 지출. 여행 기록의 한 갈래로 들어온다.
 */
@Composable
fun LedgerTab(
    state: TripUiState,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onDeleteExpense: (Long) -> Unit,
    onEditBudget: () -> Unit,
    onOpenStats: () -> Unit,
    onApplySettlement: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = state.summary ?: return
    var editingSettlement by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SummaryCard(summary) }
        item { CategoryCard(summary) }

        if (state.foreignApproved > 0L) {
            item {
                SettlementCard(
                    billedTotalKrw = summary.trip.billedTotalKrw,
                    settlementFactor = summary.trip.settlementFactor,
                    approvedForeign = state.foreignApproved,
                    onEdit = { editingSettlement = true },
                    onClear = { onApplySettlement(null) },
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEditBudget, modifier = Modifier.weight(1f)) {
                    Text("예산 조정")
                }
                OutlinedButton(onClick = onOpenStats, modifier = Modifier.weight(1f)) {
                    Text("통계")
                }
            }
        }

        item {
            Text("지출 내역 ${state.expenses.size}건", style = MaterialTheme.typography.titleMedium)
        }

        if (state.expenses.isEmpty()) {
            item {
                Text(
                    "아직 입력한 지출이 없다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.expenses, key = { it.id }) { expense ->
            ExpenseRow(
                expense = expense,
                onClick = { onEditExpense(expense.id) },
                onDelete = { onDeleteExpense(expense.id) },
            )
            HorizontalDivider()
        }

        item {
            OutlinedButton(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) {
                Text("지출 자세히 입력")
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (editingSettlement) {
        SettlementDialog(
            approvedForeign = state.foreignApproved,
            current = summary.trip.billedTotalKrw,
            onDismiss = { editingSettlement = false },
            onApply = {
                onApplySettlement(it)
                editingSettlement = false
            },
        )
    }
}

@Composable
private fun SummaryCard(summary: TripSummary) {
    SectionCard {
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
                label = "오늘 쓸 수 있는 돈",
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
private fun SettlementCard(
    billedTotalKrw: Long?,
    settlementFactor: Double,
    approvedForeign: Long,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    SectionCard("해외 결제 실제 청구액") {
        LabeledRow("승인액 합계", formatKrw(approvedForeign))
        Spacer(Modifier.height(6.dp))

        if (billedTotalKrw == null) {
            Text(
                "명세서에 찍힌 해외 결제 총액을 넣으면 수수료와 확정 환율만큼 건별로 맞춰 준다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("실제 청구액 넣기")
            }
        } else {
            LabeledRow("실제 청구액", formatKrw(billedTotalKrw))
            Spacer(Modifier.height(6.dp))
            val percent = (settlementFactor - 1.0) * 100
            LabeledRow(
                label = "차이",
                value = String.format(java.util.Locale.KOREA, "%+.1f%%", percent),
                valueColor = if (percent > 0) BudgetColors.over else BudgetColors.under,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("고치기") }
                TextButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("보정 풀기") }
            }
        }
    }
}

@Composable
private fun SettlementDialog(
    approvedForeign: Long,
    current: Long?,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
) {
    var input by remember { mutableStateOf((current ?: approvedForeign).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("실제 청구액") },
        text = {
            Column {
                Text(
                    "카드 명세서의 해외 결제 합계를 원화로 넣으세요. 승인액 ${formatKrw(approvedForeign)} 기준으로 " +
                        "건별 금액을 비율에 맞춰 다시 계산한다.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                NumberField(
                    label = "실제 청구 총액",
                    value = input,
                    onValueChange = { input = it },
                    suffix = "원",
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.toLongOrNull()?.let { it > 0L } == true,
                onClick = { input.toLongOrNull()?.let(onApply) },
            ) { Text("적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
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
