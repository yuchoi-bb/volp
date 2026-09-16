@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.trip.tabs

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.volp.travelbudget.domain.booking.Booking
import com.volp.travelbudget.domain.booking.BookingType
import com.volp.travelbudget.domain.itinerary.DayArranger
import com.volp.travelbudget.domain.itinerary.ItineraryStop
import com.volp.travelbudget.domain.itinerary.PlanFixity
import com.volp.travelbudget.domain.itinerary.PlanEntry
import com.volp.travelbudget.domain.itinerary.phaseLabel
import com.volp.travelbudget.domain.travel.TransportSuggestion
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.map.TripMap
import com.volp.travelbudget.ui.trip.TripUiState
import com.volp.travelbudget.util.formatDateWithDay
import com.volp.travelbudget.util.formatKrw
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 여행 전체 시간표. 항공·숙소 같은 예약이 둘러볼 장소와 같은 줄 위에 놓인다.
 */
@Composable
fun ScheduleTab(
    state: TripUiState,
    onAddStop: (LocalDate, String, String?, String) -> Unit,
    onDeleteStop: (Long) -> Unit,
    onMoveStop: (ItineraryStop, Boolean) -> Unit,
    onAddBooking: (LocalDate) -> Unit,
    onEditBooking: (Long) -> Unit,
    onImportPlan: () -> Unit,
    onMoveStopToDate: (ItineraryStop, LocalDate) -> Unit,
    onApplyDayOrder: (LocalDate, List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip ?: return
    var selectedDate by remember(trip.id) { mutableStateOf(initialDate(state)) }
    var addingStop by remember { mutableStateOf(false) }
    // 다른 날로 옮길 일정. 고르면 날짜를 묻는다.
    var movingStop by remember { mutableStateOf<ItineraryStop?>(null) }
    var arrangingDay by remember { mutableStateOf(false) }

    val timeline = state.timelineFor(selectedDate)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.timelines.forEach { day ->
                    FilterChip(
                        selected = day.date == selectedDate,
                        onClick = { selectedDate = day.date },
                        label = { Text(formatDateWithDay(day.date)) },
                    )
                }
            }
        }

        item {
            TripMap(
                entries = timeline?.entries.orEmpty(),
                current = state.currentLocation,
                modifier = Modifier.fillMaxWidth().height(210.dp),
            )
        }

        if (timeline == null || timeline.isEmpty) {
            item {
                SectionCard {
                    Text(
                        "이 날에는 아직 넣은 것이 없다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(timeline.entries.size) { index ->
                val entry = timeline.entries[index]
                when (entry) {
                    is PlanEntry.Reservation -> BookingCard(
                        entry = entry,
                        onEdit = { onEditBooking(entry.booking.id) },
                    )

                    is PlanEntry.Stop -> StopCard(
                        stop = entry.stop,
                        canMoveUp = index > 0,
                        canMoveDown = index < timeline.entries.lastIndex,
                        onDelete = { onDeleteStop(entry.stop.id) },
                        onMove = { up -> onMoveStop(entry.stop, up) },
                        onMoveToDate = { movingStop = entry.stop },
                    )
                }

                timeline.legs.getOrNull(index)?.let { leg -> LegLine(leg.suggestion) }
            }

            if (timeline.totalTravelMinutes > 0) {
                item {
                    Text(
                        "이동 예상 ${timeline.totalTravelMinutes}분" +
                            if (timeline.totalTravelCostKrw > 0L) {
                                " · 교통비 약 ${formatKrw(timeline.totalTravelCostKrw)}"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { addingStop = true }, modifier = Modifier.weight(1f)) {
                    Text("갈 곳 넣기")
                }
                OutlinedButton(onClick = { onAddBooking(selectedDate) }, modifier = Modifier.weight(1f)) {
                    Text("예약 넣기")
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportPlan, modifier = Modifier.weight(1f)) {
                    Text("AI 일정 붙여넣기")
                }
                OutlinedButton(
                    onClick = { arrangingDay = true },
                    enabled = (timeline?.entries?.count { it is PlanEntry.Stop } ?: 0) > 1,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("하루 정리")
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    movingStop?.let { stop ->
        MoveToDateDialog(
            stop = stop,
            dates = state.timelines.map { it.date },
            onPick = { date ->
                onMoveStopToDate(stop, date)
                movingStop = null
            },
            onDismiss = { movingStop = null },
        )
    }

    if (arrangingDay) {
        val stops = timeline?.entries.orEmpty()
            .filterIsInstance<PlanEntry.Stop>()
            .map { it.stop }
        ArrangeDayDialog(
            date = selectedDate,
            stops = stops,
            onApply = { orderedIds ->
                onApplyDayOrder(selectedDate, orderedIds)
                arrangingDay = false
            },
            onDismiss = { arrangingDay = false },
        )
    }

    if (addingStop) {
        AddStopDialog(
            date = selectedDate,
            searching = state.searchingPlace,
            onDismiss = { addingStop = false },
            onAdd = { name, time, memo ->
                onAddStop(selectedDate, name, time, memo)
                addingStop = false
            },
        )
    }
}

/** 오늘이 여행 기간 안이면 오늘을, 아니면 첫날을 연다. */
private fun initialDate(state: TripUiState): LocalDate {
    val today = LocalDate.now()
    return state.timelines.firstOrNull { it.date == today }?.date
        ?: state.timelines.firstOrNull()?.date
        ?: today
}

@Composable
private fun BookingCard(entry: PlanEntry.Reservation, onEdit: () -> Unit) {
    val booking = entry.booking

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${booking.type.emoji} ${entry.phaseLabel()}" +
                        if (booking.provider.isNotBlank()) " · ${booking.provider}" else "",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (booking.confirmationCode.isNotBlank()) {
                        Text(booking.confirmationCode, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "예약 고치기")
                    }
                }
            }

            HorizontalDivider()

            Column(Modifier.padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            booking.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (booking.routeLabel.isNotBlank()) {
                            Text(booking.routeLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (booking.address.isNotBlank()) {
                            Text(
                                booking.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            entry.time.format(clockFormat),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        booking.endAt?.takeIf { booking.type != BookingType.LODGING }?.let {
                            Text(
                                "도착 ${it.toLocalTime().format(clockFormat)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val details = buildList {
                    booking.terminal.takeIf { it.isNotBlank() }?.let { add("터미널" to it) }
                    booking.gate.takeIf { it.isNotBlank() }?.let { add("탑승구" to it) }
                    booking.seat.takeIf { it.isNotBlank() }?.let { add("좌석" to it) }
                    if (booking.type == BookingType.LODGING) {
                        booking.endAt?.let {
                            add("체크아웃" to "${it.toLocalDate()} ${it.toLocalTime().format(clockFormat)}")
                        }
                    }
                }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        details.forEach { (label, value) ->
                            Column {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                if (booking.memo.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(booking.memo, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StopCard(
    stop: ItineraryStop,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDelete: () -> Unit,
    onMove: (Boolean) -> Unit,
    onMoveToDate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
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
                // 투어나 항공편은 날짜를 못 옮긴다. 일정을 손볼 때 이것부터 자리를 잡는다.
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
            IconButton(onClick = onMoveToDate) {
                Icon(Icons.Default.EditCalendar, contentDescription = "다른 날로 옮기기")
            }
            IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "위로")
            }
            IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "아래로")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "빼기")
            }
        }
    }
}

@Composable
private fun LegLine(suggestion: TransportSuggestion?) {
    Text(
        if (suggestion == null) {
            "↓  거리를 몰라 안내를 못 한다"
        } else {
            buildString {
                append("↓  ")
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
            }
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 10.dp),
    )
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1_000) {
        String.format(java.util.Locale.KOREA, "%.1fkm", meters / 1_000)
    } else {
        "${meters.roundToInt()}m"
    }

/**
 * 일정을 다른 날로 옮긴다.
 *
 * 날짜를 고르면 그 날의 맨 뒤에 붙는다. 하루 안의 자리는 그다음에 정하면 된다.
 */
@Composable
private fun MoveToDateDialog(
    stop: ItineraryStop,
    dates: List<LocalDate>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어느 날로 옮길까요") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stop.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                dates.forEach { date ->
                    TextButton(
                        onClick = { onPick(date) },
                        enabled = date != stop.date,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            formatDateWithDay(date) + if (date == stop.date) " · 지금 이 날" else "",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/**
 * 하루 안의 차례를 정리해 보여 준다.
 *
 * 바꾸기 전에 무엇이 어디로 가는지 먼저 보인다. 시각이 정해진 것은 그대로 두고 그 사이만 가까운
 * 순으로 다시 늘어놓는다.
 */
@Composable
private fun ArrangeDayDialog(
    date: LocalDate,
    stops: List<ItineraryStop>,
    onApply: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val arranged = remember(stops) { DayArranger.arrange(stops) }
    val changed = remember(arranged) { DayArranger.changed(arranged) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${formatDateWithDay(date)} 정리") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (changed) {
                        "시각이 정해진 것은 그대로 두고, 그 사이를 가까운 곳부터 가도록 다시 놓는다."
                    } else {
                        "이미 잘 놓여 있다. 바꿀 것이 없다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                arranged.forEach { item ->
                    Text(
                        "${item.order + 1}. " + item.stop.startTime?.let { "$it  " }.orEmpty() + item.stop.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (item.moved) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        item.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.moved) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(arranged.map { it.stop.id }) },
                enabled = changed,
            ) { Text("이대로 놓기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } },
    )
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
