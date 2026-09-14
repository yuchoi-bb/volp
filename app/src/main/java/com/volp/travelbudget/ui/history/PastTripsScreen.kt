@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.history

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.volp.travelbudget.domain.stats.PastTrip
import com.volp.travelbudget.domain.stats.PastTripsSummary
import com.volp.travelbudget.ui.common.BudgetBar
import com.volp.travelbudget.ui.common.LabeledRow
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.theme.BudgetColors
import com.volp.travelbudget.util.formatDateRange
import com.volp.travelbudget.util.formatKrw
import com.volp.travelbudget.util.formatKrwShort
import com.volp.travelbudget.util.formatSignedKrw
import kotlin.math.roundToInt

/**
 * 다녀온 여행에서 실제로 쓴 돈.
 *
 * 총액은 기간과 인원에 좌우되어 여행끼리 견주기 어렵다. 그래서 **하루 얼마**와 **한 사람 하루
 * 얼마**를 함께 놓는다. 다음 여행 예산을 잡을 때 사람이 실제로 쓰는 값이 그것이다.
 */
@Composable
fun PastTripsScreen(onBack: () -> Unit, onOpenTrip: (Long) -> Unit) {
    val viewModel: PastTripsViewModel = viewModel(
        factory = com.volp.travelbudget.ui.common.volpViewModelFactory {
            PastTripsViewModel(it.repository)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summary = state.summary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("지난 여행 경비") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold

        if (summary.isEmpty) {
            EmptyHistory(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TotalCard(summary) }
            item { CategoryCard(summary) }

            item {
                Text(
                    "여행별 ${summary.tripCount}건",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            items(summary.trips, key = { it.tripId }) { trip ->
                PastTripCard(trip = trip, onClick = { onOpenTrip(trip.tripId) })
            }

            if (!state.profile.isEmpty) {
                item { ProfileCard(state) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("아직 끝난 여행이 없다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "여행이 끝나면 실제로 쓴 돈을 여기서 모아 보여 준다. 그 값이 다음 여행 예측에도 쓰인다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalCard(summary: PastTripsSummary) {
    SectionCard("모두 합쳐") {
        Text(
            formatKrw(summary.totalActual),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${summary.tripCount}번의 여행 · ${summary.totalDays}일",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        LabeledRow("하루 평균", formatKrw(summary.perDay))
        Spacer(Modifier.height(6.dp))
        LabeledRow("한 사람 하루 평균", formatKrw(summary.perPersonPerDay))

        Spacer(Modifier.height(6.dp))
        LabeledRow(
            label = "예측 대비",
            value = formatSignedKrw(summary.difference),
            valueColor = if (summary.difference > 0L) BudgetColors.over else BudgetColors.under,
        )

        summary.accuracy?.let { accuracy ->
            Spacer(Modifier.height(10.dp))
            BudgetBar(ratio = accuracy.toFloat(), overColor = BudgetColors.over)
            Spacer(Modifier.height(4.dp))
            Text(
                "예측이 ${(accuracy * 100).roundToInt()}% 맞았다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryCard(summary: PastTripsSummary) {
    SectionCard("어디에 썼나") {
        val share = summary.categoryShare
        summary.byCategory.entries
            .sortedByDescending { it.value }
            .forEach { (category, amount) ->
                val percent = ((share[category] ?: 0.0) * 100).roundToInt()
                LabeledRow(
                    label = "${category.emoji} ${category.label}",
                    value = "${formatKrw(amount)} · ${percent}%",
                )
                Spacer(Modifier.height(6.dp))
            }
    }
}

@Composable
private fun PastTripCard(trip: PastTrip, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trip.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(formatKrw(trip.actualTotal), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "${formatDateRange(trip.startDate, trip.endDate, trip.days - 1)} · ${trip.travelers}명",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            LabeledRow("하루", formatKrwShort(trip.perDay))
            Spacer(Modifier.height(6.dp))
            LabeledRow("한 사람 하루", formatKrwShort(trip.perPersonPerDay))
            Spacer(Modifier.height(6.dp))
            // 항공·숙박을 뺀 값이라야 "현지에서 얼마 쓰는 사람인지"가 보인다.
            LabeledRow("현지에서 하루", formatKrwShort(trip.onSitePerDay))

            if (trip.predictedTotal > 0L) {
                Spacer(Modifier.height(6.dp))
                LabeledRow(
                    label = "예측 ${formatKrwShort(trip.predictedTotal)} 대비",
                    value = formatSignedKrw(trip.difference),
                    valueColor = if (trip.overPredicted) BudgetColors.over else BudgetColors.under,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(state: PastTripsState) {
    SectionCard("다음 여행 예측에 반영된 것") {
        val notable = state.profile.notableAdjustments()
        if (notable.isEmpty()) {
            Text(
                "지난 여행 ${state.profile.tripCount}건을 보니 예측과 실제가 크게 어긋나지 않았다. " +
                    "예측을 그대로 쓴다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }

        Text(
            "지난 여행 ${state.profile.tripCount}건을 보고 다음 예측을 이렇게 고쳐 잡는다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        notable.forEach { (category, factor) ->
            val percent = ((factor - 1.0) * 100).roundToInt()
            LabeledRow(
                label = "${category.emoji} ${category.label}",
                value = if (percent > 0) "+${percent}%" else "${percent}%",
                valueColor = if (percent > 0) BudgetColors.over else BudgetColors.under,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
