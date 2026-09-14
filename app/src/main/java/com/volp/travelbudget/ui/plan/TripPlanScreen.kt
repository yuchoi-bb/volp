@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.plan

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.itinerary.DayPlan
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.packing.PackingGroup
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.TransportSuggestion
import com.volp.travelbudget.domain.weather.DailyForecast
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDateWithDay
import com.volp.travelbudget.util.formatKrw
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun TripPlanScreen(
    tripId: Long,
    onBack: () -> Unit,
) {
    val viewModel: TripPlanViewModel = viewModel(
        key = "plan-$tripId",
        factory = volpViewModelFactory { app ->
            TripPlanViewModel(
                tripRepository = app.repository,
                itineraryRepository = app.itineraryRepository,
                placeLookup = app.placeLookup,
                locationProvider = app.locationProvider,
                weatherRepository = app.weatherRepository,
                tripId = tripId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var addingFor by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.trip?.title ?: "일정") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refreshLocation) {
                            Icon(Icons.Default.MyLocation, contentDescription = "현재 위치 확인")
                        }
                    },
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("일정·동선") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("날씨·준비물") })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab == 0) {
                ItineraryTab(
                    state = state,
                    onAddStop = { addingFor = it },
                    onDelete = viewModel::deleteStop,
                    onMove = viewModel::move,
                )
            } else {
                PackingTab(state = state, onToggle = viewModel::togglePacking)
            }
        }
    }

    addingFor?.let { date ->
        AddStopDialog(
            date = date,
            searching = state.searching,
            onDismiss = { addingFor = null },
            onAdd = { name, time, memo ->
                viewModel.addStop(date, name, time, memo)
                addingFor = null
            },
        )
    }
}

@Composable
private fun ItineraryTab(
    state: TripPlanUiState,
    onAddStop: (LocalDate) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (ItineraryStop, Boolean) -> Unit,
) {
    val forecastByDate = state.forecasts.associateBy { it.date }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { NextStopCard(state) }

        items(state.days.size) { index ->
            val day = state.days[index]
            DayCard(
                day = day,
                dayNumber = index + 1,
                forecast = forecastByDate[day.date],
                onAddStop = { onAddStop(day.date) },
                onDelete = onDelete,
                onMove = onMove,
            )
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** 지금 위치에서 다음 일정까지 어떻게 갈지. 여행 중에 가장 자주 보게 되는 카드다. */
@Composable
private fun NextStopCard(state: TripPlanUiState) {
    val context = LocalContext.current
    val next = state.nextStop

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    SectionCard("다음 일정") {
        if (next == null) {
            Text(
                "아직 남은 일정이 없다. 아래에서 갈 곳을 넣어 두면 여기에 길 안내가 뜬다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(next.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(formatDateWithDay(next.date))
                        next.startTime?.let { append(" $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        val suggestion = state.toNextStop
        when {
            suggestion != null -> Text(
                describe(suggestion),
                style = MaterialTheme.typography.bodyMedium,
            )

            state.currentLocation == null -> Text(
                "현재 위치를 알면 여기서 얼마나 걸리는지 알려 준다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Text(
                "이 장소의 좌표를 찾지 못했다. 지도에서 직접 검색해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.currentLocation == null) {
                OutlinedButton(
                    onClick = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("위치 권한 허용") }
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(directionsUri(state.currentLocation, next)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.weight(1f),
            ) { Text("지도로 길찾기") }
        }
    }
}

@Composable
private fun DayCard(
    day: DayPlan,
    dayNumber: Int,
    forecast: DailyForecast?,
    onAddStop: () -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (ItineraryStop, Boolean) -> Unit,
) {
    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "${dayNumber}일차 · ${formatDateWithDay(day.date)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (forecast != null) {
                    Text(
                        "${forecast.emoji} ${forecast.description} " +
                            "${forecast.tempMinCelsius.roundToInt()}~${forecast.tempMaxCelsius.roundToInt()}도",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onAddStop) {
                Icon(Icons.Default.Add, contentDescription = "장소 추가")
            }
        }

        if (day.isEmpty) {
            Spacer(Modifier.height(8.dp))
            Text(
                "아직 넣은 장소가 없다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Spacer(Modifier.height(8.dp))
        day.stops.forEachIndexed { index, stop ->
            StopRow(
                stop = stop,
                canMoveUp = index > 0,
                canMoveDown = index < day.stops.lastIndex,
                onDelete = { onDelete(stop.id) },
                onMove = { up -> onMove(stop, up) },
            )
            day.legs.getOrNull(index)?.let { leg ->
                LegRow(leg.suggestion)
            }
        }

        if (day.totalTravelMinutes > 0) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                "이동 예상 ${day.totalTravelMinutes}분" +
                    if (day.totalTravelCostKrw > 0L) " · 교통비 약 ${formatKrw(day.totalTravelCostKrw)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StopRow(
    stop: ItineraryStop,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDelete: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    stop.startTime?.let { append("$it  ") }
                    append(stop.name)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (stop.address.isNotBlank()) {
                Text(
                    stop.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stop.memo.isNotBlank()) {
                Text(stop.memo, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "위로")
        }
        IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "아래로")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "장소 빼기")
        }
    }
}

@Composable
private fun LegRow(suggestion: TransportSuggestion?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (suggestion == null) "↓  거리를 몰라 안내를 못 한다" else "↓  ${describe(suggestion)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PackingTab(
    state: TripPlanUiState,
    onToggle: (String, Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard("날씨 예보") {
                when {
                    state.loadingWeather -> LinearProgressIndicator(Modifier.fillMaxWidth())

                    state.forecasts.isEmpty() -> Text(
                        "예보를 받지 못했다. 출발이 보름 넘게 남았으면 아직 예보가 나오지 않는다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> state.forecasts.forEach { forecast ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${forecast.emoji} ${formatDateWithDay(forecast.date)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${forecast.tempMinCelsius.roundToInt()}~" +
                                    "${forecast.tempMaxCelsius.roundToInt()}도" +
                                    (forecast.precipitationProbability?.let { " · 비 ${it}%" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        val grouped = state.packingItems.groupBy { it.group }
        PackingGroup.entries.forEach { group ->
            val items = grouped[group].orEmpty()
            if (items.isEmpty()) return@forEach

            item {
                SectionCard(group.label) {
                    items.forEach { item ->
                        val checked = item.name in state.checkedItems
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(item.name, it) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    item.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AddStopDialog(
    date: LocalDate,
    searching: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, String?, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${formatDateWithDay(date)}에 갈 곳") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("장소 이름") },
                    placeholder = { Text("예: 오사카성") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("시각 (선택)") },
                    placeholder = { Text("예: 10:30") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searching) {
                    Spacer(Modifier.height(12.dp))
                    Text("위치를 찾는 중…", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onAdd(name.trim(), time.trim().takeIf { it.isNotBlank() }, memo.trim()) },
            ) { Text("넣기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun describe(suggestion: TransportSuggestion): String = buildString {
    append(suggestion.mode.emoji)
    append(" ")
    append(suggestion.mode.label)
    append(" ")
    append(formatDistance(suggestion.distanceMeters))
    append(" · 약 ")
    append(suggestion.minutes)
    append("분")
    suggestion.estimatedCostKrw?.let {
        append(" · ")
        append(formatKrw(it))
    }
    if (suggestion.note.isNotBlank()) {
        append("\n")
        append(suggestion.note)
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1_000) String.format(java.util.Locale.KOREA, "%.1fkm", meters / 1_000) else "${meters.roundToInt()}m"

/** 지도 앱으로 넘길 길찾기 주소. 좌표를 모르면 이름으로 검색시킨다. */
private fun directionsUri(from: GeoPoint?, stop: ItineraryStop): String {
    val target = stop.point
        ?: return "https://www.google.com/maps/search/?api=1&query=${Uri.encode(stop.name)}"

    val destination = "${target.latitude},${target.longitude}"
    val origin = from?.let { "&origin=${it.latitude},${it.longitude}" }.orEmpty()
    return "https://www.google.com/maps/dir/?api=1&destination=$destination$origin&travelmode=transit"
}
