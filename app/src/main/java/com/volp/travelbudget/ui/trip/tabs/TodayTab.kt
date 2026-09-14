package com.volp.travelbudget.ui.trip.tabs

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.volp.travelbudget.domain.itinerary.PlanEntry
import com.volp.travelbudget.domain.today.DepartureAdvice
import com.volp.travelbudget.domain.today.TodayPlanner
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.travel.LocalClock
import com.volp.travelbudget.domain.travel.TravelZones
import com.volp.travelbudget.ui.common.BudgetBar
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.map.TripMap
import com.volp.travelbudget.ui.theme.BudgetColors
import com.volp.travelbudget.ui.trip.TripUiState
import com.volp.travelbudget.util.formatDateWithDay
import com.volp.travelbudget.util.formatKrw
import com.volp.travelbudget.util.formatKrwShort
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 여행 중에 앱을 열면 처음 보이는 화면. 지금 당장 필요한 것만 위에서부터 놓는다.
 */
@Composable
fun TodayTab(
    state: TripUiState,
    onRefreshLocation: () -> Unit,
    onQuickExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip ?: return
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    val todayTimeline = state.timelineFor(today)
    val next = TodayPlanner.upcomingEntry(state.timelines, now)
    val advice = next?.let {
        TodayPlanner.advice(it, state.currentLocation, now, trip.region)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.rainAlert?.let { alert ->
            item {
                RainBanner(
                    minutesAway = alert.minutesAway,
                    startsAt = alert.startsAt.format(clockFormat),
                    probability = alert.probability,
                    place = trip.destinationName,
                )
            }
        }

        // 시차가 있을 때만 보여 준다. 한국과 같은 곳에서 두 시각을 늘어놓아 봐야 눈만 어지럽다.
        TravelZones.clock(trip.destinationKey, trip.region, ZonedDateTime.now())
            ?.takeIf { !it.sameAsHome }
            ?.let { localClock ->
                item { ClockCard(place = trip.destinationName, clock = localClock) }
            }

        item {
            NextEntryCard(
                next = next,
                advice = advice,
                current = state.currentLocation,
                onRefreshLocation = onRefreshLocation,
            )
        }

        if (todayTimeline != null && !todayTimeline.isEmpty) {
            item {
                TripMap(
                    entries = todayTimeline.entries,
                    current = state.currentLocation,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
            }
        }

        item {
            TodaySpendCard(
                spentToday = state.spentToday(today),
                dailyAllowance = state.summary?.dailyAllowance,
                entriesToday = state.expensesOn(today).size,
                onQuickExpense = onQuickExpense,
            )
        }

        if (todayTimeline != null && !todayTimeline.isEmpty) {
            item {
                SectionCard("오늘 남은 일정") {
                    val remaining = todayTimeline.entries.filter { entry ->
                        val time = entry.time ?: return@filter true
                        !LocalDateTime.of(entry.date, time).isBefore(now)
                    }
                    if (remaining.isEmpty()) {
                        Text(
                            "오늘 일정은 다 지났다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        remaining.forEach { entry ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(
                                    entry.time?.format(clockFormat) ?: "--:--",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(entry.title, style = MaterialTheme.typography.bodyLarge)
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
private fun RainBanner(
    minutesAway: Int,
    startsAt: String,
    probability: Int?,
    place: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Text("🌧️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (minutesAway <= 5) "곧 비가 옵니다" else "${minutesAway}분 뒤 비",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        append(place)
                        append(" ")
                        append(startsAt)
                        append("부터")
                        probability?.let { append(" · 강수 확률 ${it}%") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun NextEntryCard(
    next: PlanEntry?,
    advice: DepartureAdvice?,
    current: GeoPoint?,
    onRefreshLocation: () -> Unit,
) {
    val context = LocalContext.current
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { onRefreshLocation() }

    SectionCard("다음 일정") {
        if (next == null) {
            Text(
                "남은 일정이 없다. 전체일정에서 갈 곳을 넣으면 여기에 길 안내가 뜬다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(
                    next.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        append(formatDateWithDay(next.date))
                        next.time?.let { append(" ${it.format(clockFormat)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            advice?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${it.suggestion.mode.emoji} ${it.travelMinutes}분",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    it.suggestion.estimatedCostKrw?.let { cost ->
                        Text(
                            formatKrw(cost),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        when {
            advice != null && advice.late -> Text(
                "${advice.leaveBy.format(clockFormat)}에 나섰어야 합니다",
                style = MaterialTheme.typography.bodyMedium,
                color = BudgetColors.over,
                fontWeight = FontWeight.SemiBold,
            )

            advice != null -> Text(
                "${advice.leaveBy.format(clockFormat)}에는 나서야 합니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )

            current == null -> Text(
                "현재 위치를 알면 언제 나서야 하는지 알려 준다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Text(
                "이 장소의 좌표나 시각이 없어 출발 시각을 계산하지 못한다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current == null) {
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
                ) { Text("위치 허용") }
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(directionsUri(current, next)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.weight(1f),
            ) { Text("길찾기") }
        }
    }
}

@Composable
private fun TodaySpendCard(
    spentToday: Long,
    dailyAllowance: Long?,
    entriesToday: Int,
    onQuickExpense: () -> Unit,
) {
    SectionCard("오늘 쓴 돈") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                formatKrw(spentToday),
                style = MaterialTheme.typography.headlineMedium,
            )
            dailyAllowance?.let {
                Text(
                    "하루 몫 ${formatKrwShort(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (dailyAllowance != null && dailyAllowance > 0L) {
            Spacer(Modifier.height(8.dp))
            BudgetBar(
                ratio = spentToday.toFloat() / dailyAllowance.toFloat(),
                overColor = BudgetColors.over,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "오늘 ${entriesToday}건 기록",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onQuickExpense, modifier = Modifier.fillMaxWidth()) {
            Text("현금 빠른 입력")
        }
    }
}

/** 지도 앱으로 넘길 길찾기 주소. 좌표를 모르면 이름으로 검색시킨다. */
private fun directionsUri(from: GeoPoint?, entry: PlanEntry): String {
    val target = entry.point
        ?: return "https://www.google.com/maps/search/?api=1&query=${Uri.encode(entry.title)}"

    val destination = "${target.latitude},${target.longitude}"
    val origin = from?.let { "&origin=${it.latitude},${it.longitude}" }.orEmpty()
    return "https://www.google.com/maps/dir/?api=1&destination=$destination$origin&travelmode=transit"
}

/**
 * 현지 시각과 한국 시각.
 *
 * 항공편 시각이 현지 시각인지 한국 시각인지 헷갈리면 비행기를 놓친다. 두 시각을 나란히 두면
 * 그 헷갈림이 사라진다.
 */
@Composable
private fun ClockCard(place: String, clock: LocalClock) {
    SectionCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(place, style = MaterialTheme.typography.labelMedium)
                Text(
                    clock.local.toLocalTime().format(clockFormat),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("한국 · ${clock.offsetLabel}", style = MaterialTheme.typography.labelMedium)
                Text(
                    clock.home.toLocalTime().format(clockFormat),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

