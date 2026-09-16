@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.data.repository.TripWithSpending
import com.volp.travelbudget.domain.model.TripStatus
import com.volp.travelbudget.ui.common.BudgetBar
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.ui.theme.BudgetColors
import com.volp.travelbudget.util.formatDateRange
import com.volp.travelbudget.util.formatKrw
import com.volp.travelbudget.util.formatKrwShort
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import java.time.LocalDate

@Composable
fun TripListScreen(
    onAddTrip: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInbox: () -> Unit,
    onQuickExpense: () -> Unit,
    onOpenPurchases: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenPastTrips: () -> Unit,
) {
    val viewModel: TripListViewModel = viewModel(
        factory = volpViewModelFactory { TripListViewModel(it.repository, it.settings) },
    )
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 여행") },
                actions = {
                    IconButton(onClick = onQuickExpense) {
                        Icon(Icons.Default.Bolt, contentDescription = "현금 빠른 입력")
                    }
                    IconButton(onClick = onOpenPurchases) {
                        Icon(Icons.Default.LocalShipping, contentDescription = "구매·배송")
                    }
                    IconButton(onClick = onOpenDocuments) {
                        Icon(Icons.Default.Folder, contentDescription = "문서 보관함")
                    }
                    BadgedBox(
                        badge = {
                            if (pendingCount > 0) {
                                Badge { Text(pendingCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = onOpenInbox) {
                            Icon(Icons.Default.Inbox, contentDescription = "미확인 결제")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTrip) {
                Icon(Icons.Default.Add, contentDescription = "여행 추가")
            }
        },
    ) { padding ->
        val listState = rememberLazyListState()
        val dragState = rememberDragReorderState(
            listState = listState,
            // 여행 카드만 끌어 옮긴다. 아래쪽 단추와 여백은 자리를 내주지 않는다.
            canDrag = { it.key is Long },
            onMove = viewModel::moveTrip,
            onDrop = viewModel::commitOrder,
        )

        // 화면 끝까지 끌고 갔을 때 목록이 저절로 밀려야 아래쪽 여행으로 옮길 수 있다.
        LaunchedEffect(dragState) {
            dragState.autoScrollRequests.collect { amount -> dragState.scrollBy(amount) }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 여행이 하나뿐이면 정렬 단추가 자리만 차지한다.
            if (trips.size > 1) {
                SortRow(
                    selected = sort,
                    onSelect = viewModel::changeSort,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (trips.isEmpty()) {
                EmptyTrips()
                return@Scaffold
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .dragReorder(dragState, enabled = sort.draggable),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(trips, key = { _, item -> item.trip.id }) { index, item ->
                    val dragging = dragState.isDragging(index)
                    TripCard(
                        item = item,
                        dragging = dragging,
                        showHandle = sort.draggable,
                        onClick = { onOpenTrip(item.trip.id) },
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (dragging) dragState.offset else 0f
                            },
                    )
                }

                // 다녀온 여행이 있어야 볼 것이 있다.
                if (trips.any { it.trip.statusOn(LocalDate.now()) == TripStatus.FINISHED }) {
                    item(key = "past") {
                        OutlinedButton(
                            onClick = onOpenPastTrips,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("지난 여행에서 쓴 돈 보기")
                        }
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun SortRow(
    selected: TripSort,
    onSelect: (TripSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TripSort.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option.label) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (selected.draggable) {
                "카드를 길게 눌러 끌면 자리를 바꿀 수 있다."
            } else {
                "'내 순서'를 고르면 카드를 끌어 자리를 정할 수 있다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyTrips(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("아직 기록한 여행이 없다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "오른쪽 아래 + 를 눌러 첫 여행을 만들어 보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TripCard(
    item: TripWithSpending,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    showHandle: Boolean = false,
) {
    val trip = item.trip
    val today = LocalDate.now()
    val status = trip.statusOn(today)
    val over = item.remaining < 0L

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        // 끌고 있는 카드는 살짝 띄워 어느 것을 쥐고 있는지 알려 준다.
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (showHandle) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        trip.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(statusLabel(trip, status, today), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                formatDateRange(trip.startDate, trip.endDate, trip.nights),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            BudgetBar(ratio = item.progressRatio, overColor = BudgetColors.over)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${formatKrw(item.totalSpent)} / ${formatKrwShort(trip.totalPlanned)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (over) "${formatKrwShort(-item.remaining)} 초과" else "${formatKrwShort(item.remaining)} 남음",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (over) BudgetColors.over else BudgetColors.under,
                )
            }
        }
    }
}

private fun statusLabel(
    trip: com.volp.travelbudget.domain.model.Trip,
    status: TripStatus,
    today: LocalDate,
): String = when (status) {
    TripStatus.UPCOMING -> "D-${trip.daysUntilStart(today)}"
    TripStatus.ONGOING -> "여행 중"
    TripStatus.FINISHED -> "완료"
}
