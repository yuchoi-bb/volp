@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.trips

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import java.time.LocalDate

@Composable
fun TripListScreen(
    onAddTrip: () -> Unit,
    onOpenTrip: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: TripListViewModel = viewModel(
        factory = volpViewModelFactory { TripListViewModel(it.repository) },
    )
    val trips by viewModel.trips.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 여행") },
                actions = {
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
        if (trips.isEmpty()) {
            EmptyTrips(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(trips, key = { it.trip.id }) { item ->
                    TripCard(item = item, onClick = { onOpenTrip(item.trip.id) })
                }
            }
        }
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
private fun TripCard(item: TripWithSpending, onClick: () -> Unit) {
    val trip = item.trip
    val today = LocalDate.now()
    val status = trip.statusOn(today)
    val over = item.remaining < 0L

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(trip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
