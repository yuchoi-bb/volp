@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.purchase

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.purchase.PurchaseStatus
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDate
import com.volp.travelbudget.util.formatKrw

/**
 * 여행 전에 사 둔 것들의 목록.
 *
 * 위쪽은 아직 오지 않은 것, 아래쪽은 끝난 것이다. 출발 전에 못 받을 것 같은 물건은 빨갛게
 * 보여 준다. 여행 짐을 싸는 시점에 정작 물건이 없는 일을 막는 것이 이 화면의 목적이다.
 */
@Composable
fun PurchaseListScreen(
    tripId: Long? = null,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val viewModel: PurchaseListViewModel = viewModel(
        key = "purchases-${tripId ?: 0L}",
        factory = volpViewModelFactory { app ->
            PurchaseListViewModel(app.purchaseRepository, app.repository, tripId)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("구매·배송") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "구매 넣기")
            }
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyPurchases(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.open.isNotEmpty()) {
                item(key = "summary") {
                    SectionCard {
                        Text(
                            "아직 안 온 것 ${state.open.size}건 · ${formatKrw(state.openTotalKrw)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.lateCount > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "그중 ${state.lateCount}건은 출발 전에 못 받을 수 있습니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            items(state.open, key = { it.purchase.id }) { row ->
                PurchaseCard(row = row, viewModel = viewModel, onEdit = onEdit)
            }

            if (state.done.isNotEmpty()) {
                item(key = "done-header") {
                    Text(
                        "끝난 것",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.done, key = { it.purchase.id }) { row ->
                    PurchaseCard(row = row, viewModel = viewModel, onEdit = onEdit)
                }
            }
        }
    }
}

@Composable
private fun EmptyPurchases(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("여행 전에 산 것이 없다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "주문 문자를 길게 눌러 Volp로 공유하면 도착 예정일까지 알아서 채워집니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PurchaseCard(
    row: PurchaseRow,
    viewModel: PurchaseListViewModel,
    onEdit: (Long) -> Unit,
) {
    val purchase = row.purchase
    val today = viewModel.today

    Card(modifier = Modifier.fillMaxWidth().clickable { onEdit(purchase.id) }) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${purchase.kind.emoji} ${purchase.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (purchase.amountKrw > 0L) {
                    Text(formatKrw(purchase.amountKrw), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    purchase.merchant.takeIf { it.isNotBlank() },
                    row.trip?.title ?: "여행 안 정함",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Text(
                etaLabel(row, today),
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.late || purchase.isOverdue(today)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            if (purchase.trackingNumber.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(purchase.carrier, purchase.trackingNumber)
                        .filter { it.isNotBlank() }
                        .joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PurchaseStatus.entries.forEach { status ->
                    FilterChip(
                        selected = purchase.status == status,
                        onClick = { viewModel.setStatus(purchase, status) },
                        label = { Text(status.label) },
                    )
                }
                if (purchase.canBecomeExpense && purchase.tripId != null) {
                    AssistChip(
                        onClick = { viewModel.pushToLedger(purchase) },
                        label = { Text("가계부에 넣기") },
                    )
                }
            }
        }
    }
}

private fun etaLabel(row: PurchaseRow, today: java.time.LocalDate): String {
    val purchase = row.purchase
    val eta = purchase.eta ?: return "도착 예정일 모름"
    val days = purchase.daysUntilEta(today) ?: return "도착 예정일 모름"

    val base = when {
        !purchase.status.isOpen -> formatDate(eta)
        days < 0L -> "${formatDate(eta)} 예정이었는데 아직"
        days == 0L -> "오늘 도착 예정"
        days == 1L -> "내일 도착 예정"
        else -> "${formatDate(eta)} 도착 예정 (D-$days)"
    }

    return if (row.late && row.trip != null) "$base · ${row.trip.title} 출발 전에 못 받을 수 있음" else base
}
