@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.trip

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.domain.trip.RescheduleChange
import com.volp.travelbudget.ui.common.DateField
import com.volp.travelbudget.ui.common.ReadableContent
import com.volp.travelbudget.ui.common.SectionCard
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.util.formatDateWithDay

/**
 * 여행 날짜를 바꾸고 그 안의 일정을 함께 옮기는 화면.
 *
 * "16일까지인 줄 알았는데 14일까지였다"는 일은 흔하다. 날짜만 고치면 일정표는 사라진 날에 그대로
 * 남고, 앱이 전부 밀어 버리면 이미 잡아 둔 투어와 항공편이 장부에서만 옮겨진다. 무엇이 어디로
 * 가는지 먼저 보여 주고 사람이 정한다.
 */
@Composable
fun RescheduleScreen(tripId: Long, onDone: () -> Unit) {
    val viewModel: RescheduleViewModel = viewModel(
        key = "reschedule-$tripId",
        factory = volpViewModelFactory { app ->
            RescheduleViewModel(app.repository, app.rescheduleRepository, tripId)
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("여행 날짜 바꾸기") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        val trip = state.trip ?: return@Scaffold
        val plan = state.plan

        ReadableContent(Modifier.padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard("언제로 바꿀까요") {
                    DateField(
                        label = "시작일",
                        date = state.newStart,
                        onDateChange = viewModel::setStart,
                    )
                    Spacer(Modifier.height(12.dp))
                    DateField(
                        label = "종료일",
                        date = state.newEnd,
                        onDateChange = viewModel::setEnd,
                    )
                    Spacer(Modifier.height(12.dp))

                    if (!state.validRange) {
                        Text(
                            "끝나는 날이 시작하는 날보다 앞이다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (plan != null) {
                        Text(
                            "${plan.nightsBefore}박 → ${plan.nightsAfter}박" +
                                when {
                                    plan.shiftDays > 0 -> " · ${plan.shiftDays}일 뒤로"
                                    plan.shiftDays < 0 -> " · ${-plan.shiftDays}일 앞으로"
                                    else -> ""
                                },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (plan != null && state.changed) {
                    SectionCard("일정은 어떻게 할까요") {
                        ToggleLine(
                            label = "기간 밖으로 나가는 일정은 마지막 날로 당기기",
                            checked = state.pullInside,
                            onCheckedChange = viewModel::setPullInside,
                        )
                        Spacer(Modifier.height(8.dp))
                        ToggleLine(
                            label = "🔒 날짜가 고정된 것도 함께 밀기",
                            checked = state.moveFixed,
                            onCheckedChange = viewModel::setMoveFixed,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "투어·항공권·예약은 그 날짜로 이미 잡혀 있다. 앱에서만 옮기면 실제 " +
                                "예약과 어긋난다. 예약을 다시 잡았을 때만 켜세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (plan.lengthChanged) {
                            Spacer(Modifier.height(12.dp))
                            ToggleLine(
                                label = "예산도 새 기간으로 다시 계산",
                                checked = state.recalculateBudget,
                                onCheckedChange = viewModel::setRecalculateBudget,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (trip.plannedBudget == trip.predictedBudget) {
                                    "${plan.nightsAfter}박 기준으로 다시 계산한다."
                                } else {
                                    "손으로 조정해 둔 예산이 있다. 켜면 그 값은 사라진다."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SectionCard("이렇게 바뀐다") {
                        if (plan.changes.isEmpty()) {
                            Text(
                                "이 여행에 날짜를 가진 일정이 아직 없다. 날짜만 바뀐다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "일정 ${plan.changes.size}개 중 ${plan.moved.size}개가 옮겨진다.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (plan.outside.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${plan.outside.size}개는 새 기간 밖에 남는다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (plan.needsRebooking.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "🔒 ${plan.needsRebooking.size}개는 예약을 다시 잡아야 한다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            plan.changes.forEach { change ->
                                ChangeLine(change)
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("이대로 바꾸기") }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChangeLine(change: RescheduleChange) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            buildString {
                if (change.item.fixed) append("🔒 ")
                append(change.item.label.ifBlank { "이름 없는 일정" })
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (change.moved) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            if (change.moved) {
                "${formatDateWithDay(change.item.date)} → ${formatDateWithDay(change.newDate)}"
            } else {
                "${formatDateWithDay(change.newDate)} · 그대로"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                change.outside -> MaterialTheme.colorScheme.error
                change.moved -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (change.outside) {
            Text(
                "새 기간 밖",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
