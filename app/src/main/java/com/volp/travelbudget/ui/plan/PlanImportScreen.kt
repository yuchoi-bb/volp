@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.itinerary.PlanFixity
import com.volp.travelbudget.ui.common.ReadableContent
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDateWithDay

/**
 * AI가 짜 준 일정을 여행에 넣는 화면.
 *
 * Claude나 제미나이에 일정을 물으면 날짜별 목록이 돌아온다. 그것을 손으로 다시 치는 것은 헛일이다.
 * 붙여 넣으면 하루씩 갈라 보여 주고, 사람이 고른 것만 들어간다.
 *
 * 투어나 항공편처럼 잡으면 못 옮기는 것은 자물쇠를 달아 둔다. 일정을 손볼 때 무엇부터 자리를
 * 잡아야 하는지 그것으로 안다.
 */
@Composable
fun PlanImportScreen(
    tripId: Long? = null,
    sharedText: String? = null,
    onDone: () -> Unit,
) {
    val viewModel: PlanImportViewModel = viewModel(
        key = "plan-import-${tripId ?: 0L}-${sharedText?.hashCode() ?: 0}",
        factory = volpViewModelFactory { app ->
            PlanImportViewModel(app.repository, app.itineraryRepository, tripId, sharedText)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedCount) {
        if (state.savedCount != null) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 일정 넣기") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (state.days.isNotEmpty()) {
                        val all = state.days.sumOf { it.rows.size }
                        TextButton(onClick = { viewModel.checkAll(state.selected.size != all) }) {
                            Text(if (state.selected.size == all) "모두 끄기" else "모두 켜기")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.days.isNotEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${state.selected.size}개 넣기")
                    }
                    if (state.fixedCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "그중 ${state.fixedCount}개는 날짜 고정으로 들어간다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) return@Scaffold

        ReadableContent(Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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

                item(key = "input") {
                    SectionCard("AI가 짜 준 일정 붙여넣기") {
                        Text(
                            "Claude나 제미나이에 일정을 물어 나온 글을 그대로 붙여 넣으세요. " +
                                "'1일차', 'Day 1', 날짜 줄을 보고 하루씩 나눈다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = viewModel::setInput,
                            label = { Text("일정 글") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::read,
                            enabled = state.canRead,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("일정 읽기") }

                        if (state.read && state.days.isEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "일정으로 읽히는 줄을 못 찾았다. '1일차'나 'Day 1' 같은 " +
                                    "하루 머리글과 그 아래 목록이 있어야 읽는다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                if (state.outsideCount > 0) {
                    item(key = "outside") {
                        Text(
                            "여행 기간 밖으로 놓인 줄이 ${state.outsideCount}개 있다. 꺼 두었으니 " +
                                "날짜를 확인하고 필요하면 켜세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                state.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                formatDateWithDay(day.date),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!day.insideTrip) {
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    "  여행 기간 밖",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    items(day.rows, key = { it.key }) { row ->
                        PlanStopRow(row = row, onToggle = { viewModel.toggle(row.key) })
                    }
                }

                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PlanStopRow(row: PlanRow, onToggle: () -> Unit) {
    val stop = row.stop

    Card(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = row.checked, onCheckedChange = { onToggle() })

            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(stop.startTime?.toString(), stop.title).joinToString("  "),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (stop.memo.isNotBlank()) {
                    Text(
                        stop.memo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (stop.fixity != PlanFixity.FLEXIBLE) {
                Text(
                    "${stop.fixity.emoji} ${stop.fixity.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stop.fixity == PlanFixity.FIXED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
