@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.volp.travelbudget.domain.document.DocumentKind
import com.volp.travelbudget.domain.document.TravelDocument
import com.volp.travelbudget.ui.common.DateField
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDate
import java.io.File
import java.time.LocalDate

/**
 * 여권·보험증서·바우처를 모아 두는 곳.
 *
 * 정작 필요한 순간은 해외에서 데이터가 없을 때다. 그래서 사진을 기기 안에 두고, 번호는 목록에서
 * 가려 두었다가 누를 때만 보여 준다. 어깨 너머로 보이는 것을 막는 것이 이 화면의 절반이다.
 */
@Composable
fun DocumentVaultScreen(onBack: () -> Unit) {
    val viewModel: DocumentVaultViewModel = viewModel(
        factory = volpViewModelFactory { DocumentVaultViewModel(it.documentRepository, it.repository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val revealed by viewModel.revealed.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TravelDocument?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("문서 보관함") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = TravelDocument(title = "") }) {
                Icon(Icons.Default.Add, contentDescription = "문서 넣기")
            }
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyVault(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.attention.isNotEmpty()) {
                    item(key = "attention") {
                        SectionCard {
                            Text(
                                "곧 만료되는 문서 ${state.attention.size}건",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(4.dp))
                            state.attention.forEach { document ->
                                Text(
                                    "${document.title} · ${document.expiresOn?.let { formatDate(it) }}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                items(state.documents, key = { it.id }) { document ->
                    DocumentCard(
                        document = document,
                        tripTitle = state.tripTitle(document.tripId),
                        revealed = document.id in revealed,
                        today = viewModel.today,
                        onToggleReveal = { viewModel.toggleReveal(document.id) },
                        onEdit = { editing = document },
                    )
                }

                item(key = "note") {
                    Text(
                        "문서는 이 기기에만 둔다. 사진은 다른 기기와 맞추지도, 드라이브에 올리지도 않는다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item(key = "space") { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    editing?.let { document ->
        DocumentEditorDialog(
            document = document,
            trips = state.trips,
            onDismiss = { editing = null },
            onDelete = {
                viewModel.delete(document.id)
                editing = null
            },
            onSave = { kind, title, number, expiresOn, memo, tripId, image ->
                viewModel.save(
                    id = document.id,
                    kind = kind,
                    title = title,
                    number = number,
                    expiresOn = expiresOn,
                    memo = memo,
                    tripId = tripId,
                    image = image,
                    existingPath = document.filePath,
                )
                editing = null
            },
        )
    }
}

@Composable
private fun EmptyVault(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("보관함이 비어 있다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "여권·보험증서·바우처를 넣어 두면 데이터가 없는 곳에서도 꺼내 볼 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: TravelDocument,
    tripTitle: String?,
    revealed: Boolean,
    today: LocalDate,
    onToggleReveal: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${document.kind.emoji} ${document.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            listOfNotNull(tripTitle, document.kind.label)
                .joinToString(" · ")
                .let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            if (document.number.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onToggleReveal),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (revealed) document.number else document.maskedNumber,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (revealed) "가리기" else "보기",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            document.expiresOn?.let { expiry ->
                Spacer(Modifier.height(6.dp))
                val days = document.daysUntilExpiry(today)
                Text(
                    when {
                        document.expired(today) -> "${formatDate(expiry)} · 만료됨"
                        document.expiresSoon(today) -> "${formatDate(expiry)} 만료 · ${days}일 남음"
                        else -> "${formatDate(expiry)} 만료"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (document.expired(today) || document.expiresSoon(today)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (document.hasImage) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = File(document.filePath!!),
                    contentDescription = document.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }

            if (document.memo.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(document.memo, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DocumentEditorDialog(
    document: TravelDocument,
    trips: List<com.volp.travelbudget.domain.model.Trip>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (DocumentKind, String, String, LocalDate?, String, Long?, Uri?) -> Unit,
) {
    var kind by remember { mutableStateOf(document.kind) }
    var title by remember { mutableStateOf(document.title) }
    var number by remember { mutableStateOf(document.number) }
    var memo by remember { mutableStateOf(document.memo) }
    var tripId by remember { mutableStateOf(document.tripId) }
    var hasExpiry by remember { mutableStateOf(document.expiresOn != null) }
    var expiresOn by remember { mutableStateOf(document.expiresOn ?: LocalDate.now().plusYears(5)) }
    var image by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) image = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (document.id > 0L) "문서 고치기" else "문서 넣기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                if (title.isBlank()) title = option.label
                            },
                            label = { Text("${option.emoji} ${option.label}") },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("번호 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (kind.hasExpiry) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("만료일 넣기", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = hasExpiry, onCheckedChange = { hasExpiry = it })
                    }
                    if (hasExpiry) {
                        Spacer(Modifier.height(8.dp))
                        DateField(
                            label = "만료일",
                            date = expiresOn,
                            onDateChange = { expiresOn = it },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tripId == null,
                        onClick = { tripId = null },
                        label = { Text("모든 여행") },
                    )
                    trips.forEach { trip ->
                        FilterChip(
                            selected = tripId == trip.id,
                            onClick = { tripId = trip.id },
                            label = { Text(trip.title) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            image != null -> "사진 고름"
                            document.hasImage -> "사진 바꾸기"
                            else -> "사진 넣기"
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        kind,
                        title,
                        number,
                        expiresOn.takeIf { hasExpiry && kind.hasExpiry },
                        memo,
                        tripId,
                        image,
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (document.id > 0L) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "삭제")
                    }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
