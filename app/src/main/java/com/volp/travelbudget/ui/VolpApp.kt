package com.volp.travelbudget.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.update.UpdateChecker
import com.volp.travelbudget.ui.booking.BookingEditorScreen
import com.volp.travelbudget.ui.budget.BudgetEditScreen
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.ui.documents.DocumentVaultScreen
import com.volp.travelbudget.ui.expense.ExpenseEditorScreen
import com.volp.travelbudget.ui.history.PastTripsScreen
import com.volp.travelbudget.ui.inbox.InboxScreen
import com.volp.travelbudget.ui.newtrip.NewTripScreen
import com.volp.travelbudget.ui.purchase.PurchaseEditorScreen
import com.volp.travelbudget.ui.purchase.PurchaseListScreen
import com.volp.travelbudget.ui.quick.QuickExpenseScreen
import com.volp.travelbudget.ui.settings.SettingsScreen
import com.volp.travelbudget.ui.smsimport.SmsImportScreen
import com.volp.travelbudget.ui.stats.TripStatsScreen
import com.volp.travelbudget.ui.trip.TripScreen
import com.volp.travelbudget.ui.trips.TripListScreen
import com.volp.travelbudget.ui.update.UpdatePrompt
import com.volp.travelbudget.ui.update.UpdateViewModel
import java.time.LocalDate

object Routes {
    const val TRIPS = "trips"
    const val NEW_TRIP = "trips/new"
    const val SETTINGS = "settings"
    const val INBOX = "inbox"
    const val PURCHASES = "purchases"
    const val DOCUMENTS = "documents"
    const val PAST_TRIPS = "history"

    fun tripDetail(tripId: Long) = "trips/$tripId"
    fun budgetEdit(tripId: Long) = "trips/$tripId/budget"
    fun stats(tripId: Long) = "trips/$tripId/stats"
    fun expenseEditor(tripId: Long, expenseId: Long = 0L) = "trips/$tripId/expense?expenseId=$expenseId"
    fun quickExpense(tripId: Long = 0L) = "quick?tripId=$tripId"
    fun smsImport(tripId: Long) = "trips/$tripId/sms-import"
    fun purchaseEditor(purchaseId: Long = 0L, tripId: Long = 0L) =
        "purchases/editor?purchaseId=$purchaseId&tripId=$tripId"
    fun bookingEditor(tripId: Long, bookingId: Long = 0L, date: LocalDate? = null) =
        "trips/$tripId/booking?bookingId=$bookingId&date=${date?.toString().orEmpty()}"

    const val TRIP_DETAIL_PATTERN = "trips/{tripId}"
    const val BUDGET_EDIT_PATTERN = "trips/{tripId}/budget"
    const val STATS_PATTERN = "trips/{tripId}/stats"
    const val EXPENSE_EDITOR_PATTERN = "trips/{tripId}/expense?expenseId={expenseId}"
    const val QUICK_EXPENSE_PATTERN = "quick?tripId={tripId}"
    const val BOOKING_EDITOR_PATTERN = "trips/{tripId}/booking?bookingId={bookingId}&date={date}"
    const val PURCHASE_EDITOR_PATTERN = "purchases/editor?purchaseId={purchaseId}&tripId={tripId}"
    const val SMS_IMPORT_PATTERN = "trips/{tripId}/sms-import"
}

@Composable
fun VolpApp(
    startTarget: StartTarget = StartTarget.None,
    onStartTargetHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    val updateViewModel: UpdateViewModel = viewModel(
        factory = volpViewModelFactory { app ->
            UpdateViewModel(UpdateChecker(app), AppSettings(app))
        },
    )
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    val startupViewModel: StartupViewModel = viewModel(
        factory = volpViewModelFactory { StartupViewModel(it.repository) },
    )
    val startup by startupViewModel.state.collectAsStateWithLifecycle()
    var startupHandled by remember { mutableStateOf(false) }

    // 앱을 열 때마다 한 번씩(하루에 몇 번까지만) 새 빌드가 있는지 확인한다.
    LaunchedEffect(Unit) { updateViewModel.check() }

    LaunchedEffect(startTarget) {
        when (startTarget) {
            StartTarget.None -> return@LaunchedEffect
            StartTarget.Inbox -> navController.navigate(Routes.INBOX)
            StartTarget.QuickEntry -> navController.navigate(Routes.quickExpense())
            is StartTarget.Trip -> navController.navigate(Routes.tripDetail(startTarget.tripId))
        }
        startupHandled = true
        onStartTargetHandled()
    }

    // 여행 중이면 목록을 거치지 않고 오늘 화면을 연다. 여행이 없을 때만 목록이 첫 화면이다.
    LaunchedEffect(startup, startTarget) {
        if (startupHandled || !startup.resolved || startTarget != StartTarget.None) return@LaunchedEffect
        startupHandled = true
        startup.ongoingTripId?.let { navController.navigate(Routes.tripDetail(it)) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(navController = navController, startDestination = Routes.TRIPS) {
            composable(Routes.TRIPS) {
                TripListScreen(
                    onAddTrip = { navController.navigate(Routes.NEW_TRIP) },
                    onOpenTrip = { navController.navigate(Routes.tripDetail(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenInbox = { navController.navigate(Routes.INBOX) },
                    onQuickExpense = { navController.navigate(Routes.quickExpense()) },
                    onOpenPurchases = { navController.navigate(Routes.PURCHASES) },
                    onOpenDocuments = { navController.navigate(Routes.DOCUMENTS) },
                    onOpenPastTrips = { navController.navigate(Routes.PAST_TRIPS) },
                )
            }

            composable(Routes.PAST_TRIPS) {
                PastTripsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrip = { navController.navigate(Routes.tripDetail(it)) },
                )
            }

            composable(Routes.DOCUMENTS) {
                DocumentVaultScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PURCHASES) {
                PurchaseListScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.purchaseEditor()) },
                    onEdit = { navController.navigate(Routes.purchaseEditor(purchaseId = it)) },
                )
            }

            composable(
                route = Routes.PURCHASE_EDITOR_PATTERN,
                arguments = listOf(
                    navArgument("purchaseId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("tripId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                PurchaseEditorScreen(
                    purchaseId = entry.arguments?.getLong("purchaseId") ?: 0L,
                    tripId = entry.arguments?.getLong("tripId")?.takeIf { it > 0L },
                    onDone = { navController.popBackStack() },
                )
            }

            composable(Routes.NEW_TRIP) {
                NewTripScreen(
                    onBack = { navController.popBackStack() },
                    onCreated = { tripId ->
                        navController.popBackStack()
                        navController.navigate(Routes.tripDetail(tripId))
                    },
                )
            }

            composable(
                route = Routes.TRIP_DETAIL_PATTERN,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
            ) { entry ->
                val tripId = entry.arguments?.getLong("tripId") ?: 0L
                TripScreen(
                    tripId = tripId,
                    onBack = { navController.popBackStack() },
                    onAddExpense = { navController.navigate(Routes.expenseEditor(tripId)) },
                    onEditExpense = { navController.navigate(Routes.expenseEditor(tripId, it)) },
                    onQuickExpense = { navController.navigate(Routes.quickExpense(tripId)) },
                    onEditBudget = { navController.navigate(Routes.budgetEdit(tripId)) },
                    onOpenStats = { navController.navigate(Routes.stats(tripId)) },
                    onImportSms = { navController.navigate(Routes.smsImport(tripId)) },
                    onAddBooking = { date -> navController.navigate(Routes.bookingEditor(tripId, date = date)) },
                    onEditBooking = { navController.navigate(Routes.bookingEditor(tripId, bookingId = it)) },
                    onDeleted = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.BOOKING_EDITOR_PATTERN,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("bookingId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("date") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val raw = entry.arguments?.getString("date").orEmpty()
                BookingEditorScreen(
                    tripId = entry.arguments?.getLong("tripId") ?: 0L,
                    bookingId = entry.arguments?.getLong("bookingId") ?: 0L,
                    defaultDate = runCatching { LocalDate.parse(raw) }.getOrDefault(LocalDate.now()),
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.EXPENSE_EDITOR_PATTERN,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.LongType },
                    navArgument("expenseId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                ExpenseEditorScreen(
                    tripId = entry.arguments?.getLong("tripId") ?: 0L,
                    expenseId = entry.arguments?.getLong("expenseId") ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.BUDGET_EDIT_PATTERN,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
            ) { entry ->
                BudgetEditScreen(
                    tripId = entry.arguments?.getLong("tripId") ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.SMS_IMPORT_PATTERN,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
            ) { entry ->
                SmsImportScreen(
                    tripId = entry.arguments?.getLong("tripId") ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.STATS_PATTERN,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
            ) { entry ->
                TripStatsScreen(
                    tripId = entry.arguments?.getLong("tripId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.QUICK_EXPENSE_PATTERN,
                arguments = listOf(
                    navArgument("tripId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                QuickExpenseScreen(
                    tripId = entry.arguments?.getLong("tripId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.INBOX) {
                InboxScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onCheckUpdate = { updateViewModel.check(manual = true) },
                )
            }
        }

        UpdatePrompt(state = updateState, viewModel = updateViewModel)
    }
}
