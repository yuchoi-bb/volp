package com.volp.travelbudget.ui.trip.tabs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.volp.travelbudget.data.photos.TripPhoto
import com.volp.travelbudget.domain.packing.PackingGroup
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.trip.TripUiState
import com.volp.travelbudget.util.formatDateWithDay
import com.volp.travelbudget.util.formatKrw
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val galleryPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * 돈이 아닌 것을 남기는 자리. 하루를 사진과 메모 한 장으로 묶는다.
 */
@Composable
fun RecordTab(
    state: TripUiState,
    onLoadDevicePhotos: () -> Unit,
    onAttachPhotos: (List<android.net.Uri>) -> Unit,
    onSaveNote: (LocalDate, String) -> Unit,
    onTogglePacking: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip ?: return
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, galleryPermission) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (result) onLoadDevicePhotos()
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20),
    ) { uris -> onAttachPhotos(uris) }

    LaunchedEffect(granted) {
        if (granted) onLoadDevicePhotos()
    }

    val photosByDate = (state.savedPhotos + state.devicePhotos).groupBy { photo ->
        Instant.ofEpochMilli(photo.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!granted) {
            item {
                SectionCard {
                    Text(
                        "여행 기간에 찍은 사진을 자동으로 모으려면 사진 권한이 필요하다.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(galleryPermission) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("사진 권한 허용") }
                }
            }
        }

        items(state.timelines.size) { index ->
            val date = state.timelines[index].date
            DayRecordCard(
                date = date,
                dayNumber = index + 1,
                weather = state.forecastFor(date)?.let {
                    "${it.emoji} ${it.tempMinCelsius.roundToInt()}~${it.tempMaxCelsius.roundToInt()}도"
                },
                note = state.notes[date].orEmpty(),
                photos = photosByDate[date].orEmpty(),
                expenseCount = state.expensesOn(date).size,
                expenseTotal = state.expensesOn(date).sumOf { it.amountKrw },
                onSaveNote = { onSaveNote(date, it) },
            )
        }

        item {
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("사진 직접 넣기") }
        }

        val grouped = state.packingItems.groupBy { it.group }
        PackingGroup.entries.forEach { group ->
            val items = grouped[group].orEmpty()
            if (items.isEmpty()) return@forEach

            item {
                SectionCard("준비물 · ${group.label}") {
                    items.forEach { packing ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = packing.name in state.checkedItems,
                                onCheckedChange = { onTogglePacking(packing.name, it) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(packing.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    packing.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun DayRecordCard(
    date: LocalDate,
    dayNumber: Int,
    weather: String?,
    note: String,
    photos: List<TripPhoto>,
    expenseCount: Int,
    expenseTotal: Long,
    onSaveNote: (String) -> Unit,
) {
    var draft by remember(date, note) { mutableStateOf(note) }
    var editing by remember(date) { mutableStateOf(false) }

    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${dayNumber}일차 · ${formatDateWithDay(date)}",
                style = MaterialTheme.typography.titleMedium,
            )
            weather?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("그날의 기록") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onSaveNote(draft)
                        editing = false
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("저장") }
                OutlinedButton(
                    onClick = {
                        draft = note
                        editing = false
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("취소") }
            }
        } else {
            Text(
                note.ifBlank { "이날 기록이 비어 있다." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { editing = true }) {
                Text(if (note.isBlank()) "기록 쓰기" else "고치기")
            }
        }

        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                photos.take(4).forEach { photo ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp)),
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                repeat(4 - photos.take(4).size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (photos.size > 4) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "사진 ${photos.size}장",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (expenseCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "지출 ${expenseCount}건 · ${formatKrw(expenseTotal)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
