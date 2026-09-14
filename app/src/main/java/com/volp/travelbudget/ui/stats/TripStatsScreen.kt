@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.stats

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.stats.TripStatistics
import com.volp.travelbudget.ui.common.BudgetBar
import com.volp.travelbudget.ui.common.LabeledRow
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.StatTile
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.ui.theme.BudgetColors
import com.volp.travelbudget.util.formatForeign
import com.volp.travelbudget.util.formatKrw
import com.volp.travelbudget.util.formatKrwShort
import java.time.format.DateTimeFormatter

private val axisFormat = DateTimeFormatter.ofPattern("M.d")

@Composable
fun TripStatsScreen(
    tripId: Long,
    onBack: () -> Unit,
) {
    val viewModel: TripStatsViewModel = viewModel(
        key = "stats-$tripId",
        factory = volpViewModelFactory { TripStatsViewModel(it.repository, tripId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("통계") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.report.isNotBlank(),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, state.report)
                            }
                            context.startActivity(Intent.createChooser(intent, "여행 리포트 보내기"))
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "리포트 공유")
                    }
                },
            )
        },
    ) { padding ->
        val stats = state.stats
        if (stats == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("불러오는 중…")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("항공·숙박 포함", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "끄면 하루 씀씀이가 잘 보인다",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.includeUpfront,
                        onCheckedChange = viewModel::setIncludeUpfront,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatTile("총 지출", formatKrwShort(stats.total))
                    StatTile("하루 평균", formatKrwShort(stats.dailyAverage))
                    StatTile("1인당", formatKrwShort(stats.perPerson))
                }
            }

            SectionCard("일자별 지출") {
                if (stats.total == 0L) {
                    Text(
                        "아직 기록한 지출이 없다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    DailyChart(stats)
                    Spacer(Modifier.height(12.dp))
                    stats.busiestDay?.let { day ->
                        LabeledRow(
                            label = "가장 많이 쓴 날",
                            value = "${day.date.format(axisFormat)} · ${formatKrw(day.amountKrw)}",
                        )
                    }
                }
            }

            SectionCard("항목별 비중") {
                if (stats.categoryShares.none { it.amountKrw > 0L }) {
                    Text(
                        "아직 기록한 지출이 없다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    stats.categoryShares
                        .filter { it.amountKrw > 0L }
                        .forEach { share ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${share.category.emoji} ${share.category.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${formatKrwShort(share.amountKrw)} · ${(share.ratio * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            BudgetBar(ratio = share.ratio, overColor = BudgetColors.over)
                            Spacer(Modifier.height(14.dp))
                        }
                }
            }

            if (stats.currencyTotals.size > 1 || stats.currencyTotals.any { it.currencyCode != "KRW" }) {
                SectionCard("통화별") {
                    stats.currencyTotals.forEach { currency ->
                        LabeledRow(
                            label = if (currency.currencyCode == "KRW") {
                                "원화 결제"
                            } else {
                                formatForeign(currency.amount, currency.currencyCode)
                            },
                            value = formatKrw(currency.amountKrw),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            SectionCard("여행 리포트") {
                Text(state.report, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 일자별 지출 막대그래프.
 *
 * 막대 개수가 여행 일수만큼으로 많지 않아 라이브러리 없이 직접 그린다.
 */
@Composable
private fun DailyChart(stats: TripStatistics) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = stats.maxDailyAmount.coerceAtLeast(1L)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp),
    ) {
        val count = stats.dailyTotals.size
        if (count == 0) return@Canvas

        val gap = if (count > 1) 6.dp.toPx() else 0f
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)

        stats.dailyTotals.forEachIndexed { index, daily ->
            val ratio = daily.amountKrw.toFloat() / max.toFloat()
            val barHeight = (size.height * ratio).coerceAtLeast(2f)
            val left = index * (barWidth + gap)

            drawRoundRect(
                color = if (daily.amountKrw > 0L) barColor else emptyColor,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        stats.dailyTotals.firstOrNull()?.let {
            Text(
                it.date.format(axisFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        stats.dailyTotals.lastOrNull()?.let {
            Text(
                it.date.format(axisFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

