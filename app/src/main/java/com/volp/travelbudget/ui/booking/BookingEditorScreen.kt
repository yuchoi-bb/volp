@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.booking

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.booking.BookingType
import com.volp.travelbudget.ui.common.DateField
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import java.time.LocalDate

/**
 * 항공편·숙소 같은 예약을 넣고 고치는 화면.
 *
 * 종류에 따라 물어보는 것이 달라진다. 항공에는 터미널과 탑승구를, 숙소에는 주소와 체크아웃을
 * 묻는다. 쓸 일 없는 칸까지 늘어놓으면 입력이 일이 된다.
 */
@Composable
fun BookingEditorScreen(
    tripId: Long,
    bookingId: Long,
    defaultDate: LocalDate,
    onDone: () -> Unit,
    sharedText: String? = null,
) {
    val viewModel: BookingEditorViewModel = viewModel(
        key = "booking-$tripId-$bookingId-${sharedText?.hashCode() ?: 0}",
        factory = volpViewModelFactory { app ->
            BookingEditorViewModel(
                bookingRepository = app.bookingRepository,
                tripRepository = app.repository,
                placeLookup = app.placeLookup,
                photoStore = app.photoStore,
                tripId = tripId,
                bookingId = bookingId,
                defaultDate = defaultDate,
                sharedText = sharedText,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val queuedPhotos by viewModel.queuedPhotos.collectAsStateWithLifecycle()
    // 크게 볼 사진. 개표대 앞에서는 이 화면을 그대로 보여 주면 된다.
    var viewing by remember { mutableStateOf<Any?>(null) }

    val total = photos.size + queuedPhotos.size
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(
            (viewModel.photoLimit - total).coerceAtLeast(1),
        ),
    ) { uris -> viewModel.addPhotos(uris) }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "예약 고치기" else "예약 넣기") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Default.Delete, contentDescription = "삭제")
                        }
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
            SectionCard("무엇을 예약했나요") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookingType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.setType(type) },
                            label = { Text("${type.emoji} ${type.label}") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(if (state.type == BookingType.FLIGHT) "편명" else "이름") },
                    placeholder = {
                        Text(if (state.type == BookingType.FLIGHT) "예: KE723" else "예: 도미인 난바")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.provider,
                    onValueChange = viewModel::setProvider,
                    label = { Text("항공사·숙박 플랫폼 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.confirmationCode,
                    onValueChange = viewModel::setConfirmationCode,
                    label = { Text("예약번호 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(if (state.type == BookingType.LODGING) "언제부터 언제까지" else "언제") {
                DateField(
                    label = if (state.type == BookingType.LODGING) "체크인" else "출발일",
                    date = state.startDate,
                    onDateChange = viewModel::setStartDate,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.startTime,
                    onValueChange = viewModel::setStartTime,
                    label = { Text(if (state.type == BookingType.LODGING) "체크인 시각" else "출발 시각") },
                    placeholder = { Text("예: 09:05") },
                    singleLine = true,
                    isError = parseTime(state.startTime) == null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (state.type == BookingType.LODGING) "체크아웃도 넣기" else "도착 시각도 넣기",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = state.useEnd, onCheckedChange = viewModel::setUseEnd)
                }

                if (state.useEnd) {
                    Spacer(Modifier.height(12.dp))
                    DateField(
                        label = if (state.type == BookingType.LODGING) "체크아웃" else "도착일",
                        date = state.endDate,
                        onDateChange = viewModel::setEndDate,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.endTime,
                        onValueChange = viewModel::setEndTime,
                        label = { Text(if (state.type == BookingType.LODGING) "체크아웃 시각" else "도착 시각") },
                        singleLine = true,
                        isError = parseTime(state.endTime) == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.type.hasRoute) {
                SectionCard("어디서 어디로") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.fromCode,
                            onValueChange = viewModel::setFromCode,
                            label = { Text("출발") },
                            placeholder = { Text("ICN") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.toCode,
                            onValueChange = viewModel::setToCode,
                            label = { Text("도착") },
                            placeholder = { Text("KIX") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.toName,
                        onValueChange = viewModel::setToName,
                        label = { Text("도착지 이름 (선택)") },
                        placeholder = { Text("간사이 국제공항") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionCard("공항에서 꺼내 볼 것") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.terminal,
                            onValueChange = viewModel::setTerminal,
                            label = { Text("터미널") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.gate,
                            onValueChange = viewModel::setGate,
                            label = { Text("탑승구") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = state.seat,
                            onValueChange = viewModel::setSeat,
                            label = { Text("좌석") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                SectionCard("어디인가요") {
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = viewModel::setAddress,
                        label = { Text("주소") },
                        placeholder = { Text("오사카 주오구 니혼바시 1-2-3") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "주소를 넣으면 지도와 길 안내에 함께 쓴다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("예약 티켓 사진") {
                Text(
                    "표나 바우처를 찍어 두면 개표대 앞에서 바로 꺼낼 수 있다. " +
                        "한 예약에 ${viewModel.photoLimit}장까지 넣는다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (total > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        photos.forEachIndexed { index, photo ->
                            TicketThumbnail(
                                model = photo.uri,
                                position = index + 1,
                                canMoveLeft = index > 0,
                                canMoveRight = index < photos.lastIndex,
                                onOpen = { viewing = photo.uri },
                                onMove = { up -> viewModel.movePhoto(photo.id, up) },
                                onRemove = { viewModel.removePhoto(photo.id) },
                            )
                        }
                        queuedPhotos.forEachIndexed { index, uri ->
                            TicketThumbnail(
                                model = uri,
                                position = photos.size + index + 1,
                                canMoveLeft = index > 0,
                                canMoveRight = index < queuedPhotos.lastIndex,
                                onOpen = { viewing = uri },
                                onMove = { up -> viewModel.moveQueuedPhoto(uri, up) },
                                onRemove = { viewModel.removeQueuedPhoto(uri) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$total / ${viewModel.photoLimit}장 · 화살표로 차례를 바꾸고, 누르면 크게 본다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = total < viewModel.photoLimit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (total < viewModel.photoLimit) "티켓 사진 넣기" else "${viewModel.photoLimit}장을 채웠다")
                }

                if (!state.isEditing && queuedPhotos.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "예약을 저장할 때 함께 붙는다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("메모") {
                OutlinedTextField(
                    value = state.memo,
                    onValueChange = viewModel::setMemo,
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isEditing) "수정 저장" else "넣기")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    viewing?.let { model ->
        TicketViewer(model = model, onDismiss = { viewing = null })
    }
}

/**
 * 티켓 사진 한 장.
 *
 * 몇 번째인지 숫자로 보인다. 가족 표가 여러 장이면 누구 것인지 차례로 기억하기 때문이다.
 */
@Composable
private fun TicketThumbnail(
    model: Any,
    position: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onOpen: () -> Unit,
    onMove: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen),
        ) {
            AsyncImage(
                model = model,
                contentDescription = "티켓 ${position}번",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(110.dp),
            )
            Text(
                position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
            IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "사진 빼기",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Row {
            IconButton(onClick = { onMove(true) }, enabled = canMoveLeft) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "앞으로")
            }
            IconButton(onClick = { onMove(false) }, enabled = canMoveRight) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "뒤로")
            }
        }
    }
}

/** 표를 크게 보여 준다. 개표대 앞에서는 이 화면을 그대로 내민다. */
@Composable
private fun TicketViewer(model: Any, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = "티켓",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            )
        }
    }
}
