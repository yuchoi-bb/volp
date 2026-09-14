@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.newtrip

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.budget.CurrencyRates
import com.volp.travelbudget.domain.budget.DestinationCatalog
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.model.Region
import com.volp.travelbudget.domain.model.TravelStyle
import com.volp.travelbudget.ui.common.DateField
import com.volp.travelbudget.ui.common.DropdownField
import com.volp.travelbudget.ui.common.LabeledRow
import com.volp.travelbudget.ui.common.NumberField
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatKrw

private const val CUSTOM_LABEL = "직접 입력"

@Composable
fun NewTripScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val viewModel: NewTripViewModel = viewModel(
        factory = volpViewModelFactory { NewTripViewModel(it.repository, it.exchangeRates) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createdTripId by viewModel.createdTripId.collectAsStateWithLifecycle()

    LaunchedEffect(createdTripId) {
        createdTripId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("새 여행") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
            SectionCard("어디로 가나요") {
                DropdownField(
                    label = "권역",
                    selected = state.region,
                    options = Region.entries,
                    optionLabel = { it.label },
                    onSelect = viewModel::setRegion,
                )
                Spacer(Modifier.height(12.dp))

                val cityKeys = buildList {
                    DestinationCatalog.byRegion()[state.region]?.forEach { add(it.key) }
                    add(DestinationCatalog.customKey(state.region))
                }
                DropdownField(
                    label = "도시",
                    selected = state.destinationKey,
                    options = cityKeys,
                    optionLabel = { key ->
                        if (key.startsWith(DestinationCatalog.CUSTOM_KEY_PREFIX)) {
                            CUSTOM_LABEL
                        } else {
                            DestinationCatalog.find(key)?.name ?: key
                        }
                    },
                    onSelect = viewModel::setDestination,
                )

                if (state.isCustomDestination) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.customDestinationName,
                        onValueChange = viewModel::setCustomDestinationName,
                        label = { Text("도시 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "목록에 없는 도시는 ${state.region.label} 평균 물가로 예측한다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard("언제 가나요") {
                DateField("시작일", state.startDate, viewModel::setStartDate)
                Spacer(Modifier.height(12.dp))
                DateField("종료일", state.endDate, viewModel::setEndDate)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.nights}박 ${state.nights + 1}일",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("어떻게 가나요") {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("여행 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                NumberField("인원", state.travelers, viewModel::setTravelers, suffix = "명")
                Spacer(Modifier.height(12.dp))

                Text("여행 스타일", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.style == style,
                            onClick = { viewModel.setStyle(style) },
                            label = { Text(style.label) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    state.style.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("항공권을 예산에 포함", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = state.includeFlight, onCheckedChange = viewModel::setIncludeFlight)
                }
            }

            SectionCard("현지 통화") {
                DropdownField(
                    label = "통화",
                    selected = state.currencyCode,
                    options = CurrencyRates.currencies.map { it.code },
                    optionLabel = { "$it (${CurrencyRates.label(it)})" },
                    onSelect = viewModel::setCurrency,
                )
                Spacer(Modifier.height(12.dp))
                NumberField(
                    label = "환율 (1 ${state.currencyCode} 당 원)",
                    value = state.exchangeRate,
                    onValueChange = viewModel::setExchangeRate,
                    allowDecimal = true,
                    suffix = "원",
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "실시간 시세가 아니라 입력을 돕는 기본값이다. 여행 중에 바꿀 수 있다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("예상 경비") {
                val prediction = state.prediction
                ExpenseCategory.entries.forEach { category ->
                    LabeledRow(
                        label = "${category.emoji} ${category.label}",
                        value = formatKrw(prediction[category] ?: 0L),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(6.dp))
                LabeledRow(
                    label = "합계",
                    value = formatKrw(state.predictedTotal),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("이 예산으로 여행 만들기")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
