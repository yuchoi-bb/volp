package com.volp.travelbudget.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.volp.travelbudget.ui.budget.BudgetEditScreen
import com.volp.travelbudget.ui.common.volpViewModelFactory
import com.volp.travelbudget.ui.expense.ExpenseEditorScreen
import com.volp.travelbudget.ui.inbox.InboxScreen
import com.volp.travelbudget.ui.newtrip.NewTripScreen
import com.volp.travelbudget.ui.photos.TripPhotosScreen
import com.volp.travelbudget.ui.plan.TripPlanScreen
import com.volp.travelbudget.ui.settings.SettingsScreen
import com.volp.travelbudget.ui.stats.TripStatsScreen
import com.volp.travelbudget.ui.trip.TripDetailScreen
import com.volp.travelbudget.ui.trips.TripListScreen
import com.volp.travelbudget.ui.update.UpdatePrompt
import com.volp.travelbudget.ui.update.UpdateViewModel

object Routes {
    const val TRIPS = "trips"
    const val NEW_TRIP = "trips/new"
    const val SETTINGS = "settings"
    const val INBOX = "inbox"

    fun tripDetail(tripId: Long) = "trips/$tripId"
    fun budgetEdit(tripId: Long) = "trips/$tripId/budget"
    fun photos(tripId: Long) = "trips/$tripId/photos"
    fun stats(tripId: Long) = "trips/$tripId/stats"
    fun plan(tripId: Long) = "trips/$tripId/plan"
    fun expenseEditor(tripId: Long, expenseId: Long = 0L) = "trips/$tripId/expense?expenseId=$expenseId"

    const val TRIP_DETAIL_PATTERN = "trips/{tripId}"
    const val BUDGET_EDIT_PATTERN = "trips/{tripId}/budget"
    const val PHOTOS_PATTERN = "trips/{tripId}/photos"
    const val STATS_PATTERN = "trips/{tripId}/stats"
    const val PLAN_PATTERN = "trips/{tripId}/plan"
    const val EXPENSE_EDITOR_PATTERN = "trips/{tripId}/expense?expenseId={expenseId}"
}

@Composable
fun VolpApp(
    openInbox: Boolean = false,
    onInboxOpened: () -> Unit = {},
) {
    val navController = rememberNavController()

    val updateViewModel: UpdateViewModel = viewModel(
        factory = volpViewModelFactory { app ->
            UpdateViewModel(UpdateChecker(app), AppSettings(app))
        },
    )
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    // 앱을 열 때마다 한 번씩(하루에 몇 번까지만) 새 빌드가 있는지 확인한다.
    LaunchedEffect(Unit) { updateViewModel.check() }

    LaunchedEffect(openInbox) {
        if (openInbox) {
            navController.navigate(Routes.INBOX)
            onInboxOpened()
        }
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
                TripDetailScreen(
                    tripId = tripId,
                    onBack = { navController.popBackStack() },
                    onAddExpense = { navController.navigate(Routes.expenseEditor(tripId)) },
                    onEditExpense = { expenseId ->
                        navController.navigate(Routes.expenseEditor(tripId, expenseId))
                    },
                    onEditBudget = { navController.navigate(Routes.budgetEdit(tripId)) },
                    onOpenPhotos = { navController.navigate(Routes.photos(tripId)) },
                    onOpenStats = { navController.navigate(Routes.stats(tripId)) },
                    onOpenPlan = { navController.navigate(Routes.plan(tripId)) },
                    onDeleted = { navController.popBackStack() },
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
                route = Routes.PHOTOS_PATTERN,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
            ) { entry ->
                TripPhotosScreen(
                    tripId = entry.arguments?.getLong("tripId") ?: 0L,
                    onBack = { navController.popBackStack() },
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
                route = Routes.PLAN_PATTERN,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
            ) { entry ->
                TripPlanScreen(
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
