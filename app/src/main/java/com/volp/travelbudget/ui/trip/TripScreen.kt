@file:OptIn(ExperimentalMaterial3Api::class)

package com.volp.travelbudget.ui.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.volp.travelbudget.ui.common.ReadableContent
import com.volp.travelbudget.ui.common.isWideScreen
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.ui.trip.tabs.LedgerTab
import com.volp.travelbudget.ui.trip.tabs.RecordTab
import com.volp.travelbudget.ui.trip.tabs.ScheduleTab
import com.volp.travelbudget.ui.trip.tabs.TodayTab
import java.time.LocalDate

private data class TripTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TripTab("오늘", Icons.Default.Today),
    TripTab("전체일정", Icons.AutoMirrored.Filled.List),
    TripTab("가계부", Icons.Default.Payments),
    TripTab("기록", Icons.Default.PhotoLibrary),
)

/**
 * 여행 하나를 여는 화면. 네 갈래가 하단 탭으로 붙는다.
 *
 * 가계부는 그중 하나다. 여행에서 남기는 것 가운데 돈이 한 갈래일 뿐이라는 것을 구조로 보인다.
 */
@Composable
fun TripScreen(
    tripId: Long,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onQuickExpense: () -> Unit,
    onEditBudget: () -> Unit,
    onOpenStats: () -> Unit,
    onImportSms: () -> Unit,
    onAddBooking: (LocalDate) -> Unit,
    onEditBooking: (Long) -> Unit,
    onDeleted: () -> Unit,
    startTab: Int = 0,
) {
    val viewModel: TripViewModel = viewModel(
        key = "trip-$tripId",
        factory = volpViewModelFactory { app ->
            TripViewModel(
                repository = app.repository,
                itineraryRepository = app.itineraryRepository,
                bookingRepository = app.bookingRepository,
                cashRepository = app.cashRepository,
                photoStore = app.photoStore,
                placeLookup = app.placeLookup,
                locationProvider = app.locationProvider,
                weatherRepository = app.weatherRepository,
                tripId = tripId,
            )
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 지출을 고치러 갔다 돌아왔을 때 보던 탭 그대로 열린다.
    var selectedTab by rememberSaveable(tripId) { mutableIntStateOf(startTab.coerceIn(0, tabs.lastIndex)) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 탭마다 제 상태를 들고 있게 한다. 가계부에 걸어 둔 조건이 다른 탭에 다녀왔다고 풀리면
    // 탭이 아니라 다시 여는 화면이 된다.
    val tabStates = rememberSaveableStateHolder()

    // 태블릿에서는 탭을 아래가 아니라 옆에 세운다. 넓은 화면에서 아래 끝은 손이 멀다.
    val wide = isWideScreen()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.trip?.title ?: "여행") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshLocation) {
                        Icon(Icons.Default.MyLocation, contentDescription = "현재 위치 확인")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "여행 삭제")
                    }
                },
            )
        },
        bottomBar = {
            if (!wide) {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (state.trip == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("불러오는 중…")
            }
            return@Scaffold
        }

        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide) {
                NavigationRail {
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }

            ReadableContent {
                tabStates.SaveableStateProvider(selectedTab) {
                    when (selectedTab) {
                        0 -> TodayTab(
                            state = state,
                            onRefreshLocation = viewModel::refreshLocation,
                            onQuickExpense = onQuickExpense,
                        )

                        1 -> ScheduleTab(
                            state = state,
                            onAddStop = viewModel::addStop,
                            onDeleteStop = viewModel::deleteStop,
                            onMoveStop = viewModel::moveStop,
                            onAddBooking = onAddBooking,
                            onEditBooking = onEditBooking,
                        )

                        2 -> LedgerTab(
                            state = state,
                            onAddExpense = onAddExpense,
                            onEditExpense = onEditExpense,
                            onDeleteExpense = viewModel::deleteExpense,
                            onEditBudget = onEditBudget,
                            onOpenStats = onOpenStats,
                            onApplySettlement = viewModel::applySettlement,
                            onImportSms = onImportSms,
                            onAddTopUp = viewModel::addTopUp,
                            onDeleteTopUp = viewModel::deleteTopUp,
                        )

                        else -> RecordTab(
                            state = state,
                            onLoadDevicePhotos = viewModel::loadDevicePhotos,
                            onAttachPhotos = viewModel::attachPhotos,
                            onSaveNote = viewModel::saveNote,
                            onTogglePacking = viewModel::togglePacking,
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("여행을 삭제할까요") },
            text = { Text("이 여행에 남긴 일정·지출·사진이 함께 지워진다. 되돌릴 수 없다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteTrip(onDeleted)
                    },
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            },
        )
    }
}
