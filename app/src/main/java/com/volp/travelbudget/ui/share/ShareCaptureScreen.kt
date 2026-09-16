@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.volp.travelbudget.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.booking.BookingTextParser
import com.volp.travelbudget.domain.cardsms.CardMessageBatch
import com.volp.travelbudget.domain.cardsms.CardMessageParser
import com.volp.travelbudget.domain.itinerary.PlanTextParser
import com.volp.travelbudget.domain.purchase.PurchaseTextParser
import com.volp.travelbudget.ui.booking.BookingEditorScreen
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.ui.plan.PlanImportScreen
import com.volp.travelbudget.ui.purchase.PurchaseEditorScreen
import com.volp.travelbudget.ui.smsimport.SmsImportScreen
import com.volp.travelbudget.util.formatDate
import com.volp.travelbudget.util.formatForeign
import com.volp.travelbudget.util.formatKrw
import java.time.LocalDate
import java.time.LocalDateTime

/** 공유한 글을 무엇으로 넣을지. */
private enum class ShareChoice { EXPENSE, EXPENSES, PURCHASE, BOOKING, PLAN }

/**
 * 공유로 들어온 글을 받는 화면.
 *
 * 같은 문자가 구매일 수도 예약일 수도 있다. 앱이 혼자 단정하면 틀렸을 때 사용자가 왜 이렇게
 * 됐는지 알 수 없으므로, 양쪽으로 읽어 본 결과를 나란히 보여 주고 고르게 한다. 더 잘 읽힌 쪽에
 * 추천을 붙여 두면 대개 한 번에 고른다.
 */
@Composable
fun ShareCaptureScreen(text: String, onClose: () -> Unit) {
    var choice by remember { mutableStateOf<ShareChoice?>(null) }

    when (choice) {
        null -> ShareChooser(text = text, onPick = { choice = it }, onClose = onClose)
        ShareChoice.EXPENSE -> CardShareScreen(text = text, onClose = onClose)
        ShareChoice.EXPENSES -> SmsImportScreen(sharedText = text, onDone = onClose)
        ShareChoice.PURCHASE -> PurchaseEditorScreen(sharedText = text, onDone = onClose)
        ShareChoice.BOOKING -> BookingShareScreen(text = text, onClose = onClose)
        ShareChoice.PLAN -> PlanImportScreen(sharedText = text, onDone = onClose)
    }
}

@Composable
private fun ShareChooser(text: String, onPick: (ShareChoice) -> Unit, onClose: () -> Unit) {
    val today = LocalDate.now()
    val purchase = remember(text) { PurchaseTextParser.parse(text, today) }
    val booking = remember(text) { BookingTextParser.parse(text, today) }
    // 카드 결제 문자는 서식이 뚜렷해 읽혔다면 거의 틀림없다. 읽혔으면 그쪽을 앞에 놓는다.
    val card = remember(text) {
        CardMessageParser.parse(text, LocalDateTime.now(), null)
    }
    // 여러 건을 한꺼번에 공유했으면 한 줄씩 확인하는 목록으로 보낸다.
    val cards = remember(text) { CardMessageBatch.parseAll(text, LocalDateTime.now()) }
    // AI가 짜 준 일정은 하루 머리글이 여럿이라 결제 문자와 헷갈릴 일이 없다.
    val plan = remember(text) { PlanTextParser.parse(text, today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volp에 넣기") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "이 글을 무엇으로 넣을까요?",
                style = MaterialTheme.typography.titleMedium,
            )

            val ordered = buildList {
                // 하루가 둘 이상 읽혔으면 일정 글이 거의 틀림없다.
                if (plan.size > 1) add(ShareChoice.PLAN)
                if (cards.size > 1) add(ShareChoice.EXPENSES)
                if (card != null && cards.size <= 1) add(ShareChoice.EXPENSE)
                if (booking.confidence > purchase.confidence) {
                    add(ShareChoice.BOOKING)
                    add(ShareChoice.PURCHASE)
                } else {
                    add(ShareChoice.PURCHASE)
                    add(ShareChoice.BOOKING)
                }
                if (plan.size == 1) add(ShareChoice.PLAN)
            }

            ordered.forEachIndexed { index, option ->
                ChoiceCard(
                    title = when (option) {
                        ShareChoice.EXPENSES -> "💳 지출 ${cards.size}건 넣기"
                        ShareChoice.EXPENSE -> "💳 지출로 넣기"
                        ShareChoice.PURCHASE -> "🛍️ 구매로 넣기"
                        ShareChoice.BOOKING -> "🎫 예약으로 넣기"
                        ShareChoice.PLAN -> "🗺️ 여행 일정으로 넣기"
                    },
                    recommended = index == 0,
                    lines = when (option) {
                        ShareChoice.EXPENSES -> cards.take(3).map { "${it.merchant} ${it.amount.toLong()}" }
                        ShareChoice.EXPENSE -> cardLines(card)
                        ShareChoice.PURCHASE -> purchaseLines(purchase)
                        ShareChoice.BOOKING -> bookingLines(booking)
                        ShareChoice.PLAN -> plan.take(3).map { day ->
                            val name = day.heading.ifBlank { "${day.dayNumber ?: 1}일차" }
                            "$name · ${day.stops.size}곳"
                        }
                    },
                    onClick = { onPick(option) },
                )
            }

            SectionCard("공유한 원문") {
                Text(
                    text.take(MAX_PREVIEW),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    recommended: Boolean,
    lines: List<String>,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = if (recommended) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "추천",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (lines.isEmpty()) {
                Text(
                    "읽어 낸 것이 거의 없습니다. 직접 채워 넣어야 합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                lines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun cardLines(card: com.volp.travelbudget.domain.cardsms.CardTransaction?): List<String> {
    if (card == null) return emptyList()
    return buildList {
        add("${card.issuer.label} · ${card.kind.label}")
        add(
            if (card.isOverseas) {
                formatForeign(card.amount, card.currencyCode)
            } else {
                formatKrw(card.amount.toLong())
            },
        )
        if (card.merchant.isNotBlank()) add(card.merchant)
    }
}

private fun purchaseLines(parsed: com.volp.travelbudget.domain.purchase.ParsedPurchase): List<String> =
    buildList {
        if (parsed.title.isNotBlank()) add(parsed.title)
        if (parsed.amountKrw > 0L) add(formatKrw(parsed.amountKrw))
        parsed.originalAmount?.let { add("$it ${parsed.currencyCode}") }
        parsed.eta?.let { add("도착 예정 ${formatDate(it)}") }
        if (parsed.trackingNumber.isNotBlank()) add("송장 ${parsed.trackingNumber}")
    }

private fun bookingLines(parsed: com.volp.travelbudget.domain.booking.ParsedBooking): List<String> =
    buildList {
        if (parsed.title.isNotBlank()) add("${parsed.type.emoji} ${parsed.title}")
        val route = listOf(parsed.fromCode, parsed.toCode).filter { it.isNotBlank() }
        if (route.size == 2) add("${route[0]} → ${route[1]}")
        parsed.startDate?.let { date ->
            val time = parsed.startTime?.toString().orEmpty()
            add("${formatDate(date)} $time".trim())
        }
        if (parsed.confirmationCode.isNotBlank()) add("예약번호 ${parsed.confirmationCode}")
    }

/**
 * 예약은 어느 여행 것인지부터 정해야 한다.
 *
 * 여행이 하나뿐이면 묻지 않고 그 여행으로 간다. 고를 것이 하나인 질문은 질문이 아니다.
 */
@Composable
private fun BookingShareScreen(text: String, onClose: () -> Unit) {
    val viewModel: ShareTripPickerViewModel = viewModel(
        factory = volpViewModelFactory { ShareTripPickerViewModel(it.repository) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tripId by remember { mutableStateOf<Long?>(null) }

    val chosen = tripId ?: state.onlyTripId
    if (chosen != null) {
        BookingEditorScreen(
            tripId = chosen,
            bookingId = 0L,
            defaultDate = state.defaultDateFor(chosen),
            onDone = onClose,
            sharedText = text,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("어느 여행 예약인가요") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.trips.isEmpty()) {
                Text(
                    "예약을 넣으려면 여행이 먼저 있어야 합니다. Volp에서 여행을 만든 뒤 다시 공유해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.trips.forEach { trip ->
                    FilterChip(
                        selected = false,
                        onClick = { tripId = trip.id },
                        label = { Text("${trip.title} · ${formatDate(trip.startDate)}") },
                    )
                }
            }
        }
    }
}

private const val MAX_PREVIEW = 400

/**
 * 공유한 카드 결제 문자를 미확인함에 넣는다.
 *
 * 문자를 저절로 읽는 빌드와 같은 길을 탄다. 여행 기간과 항목이 확실하면 바로 그 여행 지출이
 * 되고, 아니면 미확인함에 쌓여 나중에 어느 여행 것인지 고르게 된다.
 */
@Composable
private fun CardShareScreen(text: String, onClose: () -> Unit) {
    val viewModel: CardShareViewModel = viewModel(
        key = "card-${text.hashCode()}",
        factory = volpViewModelFactory { CardShareViewModel(it.captureHandler, text) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("지출로 넣기") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                CardShareState.Working -> Text("읽는 중", style = MaterialTheme.typography.bodyLarge)

                CardShareState.Saved -> {
                    Text("넣었습니다", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "여행 기간과 항목이 확실하면 그 여행 지출로 들어가고, 아니면 미확인함에서 " +
                            "어느 여행 것인지 고르면 된다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CardShareState.Duplicate -> {
                    Text("이미 들어와 있습니다", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "같은 결제가 이미 기록돼 있어 다시 넣지 않았다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                CardShareState.Unreadable -> {
                    Text("읽지 못했습니다", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "카드 결제 문자로 보이지 않는다. 구매나 예약으로 넣어 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
