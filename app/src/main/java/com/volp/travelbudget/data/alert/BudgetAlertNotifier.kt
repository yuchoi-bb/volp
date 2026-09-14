package com.volp.travelbudget.data.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.volp.travelbudget.MainActivity
import com.volp.travelbudget.R
import com.volp.travelbudget.data.local.BudgetAlertDao
import com.volp.travelbudget.data.local.BudgetAlertEntity
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.domain.alert.BudgetAlert
import com.volp.travelbudget.domain.alert.BudgetAlerts
import com.volp.travelbudget.domain.model.ExpenseCategory
import com.volp.travelbudget.domain.summary.TripSummaries
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 지출이 들어올 때마다 예산 상태를 보고 알릴 만한 일이 생겼는지 확인한다.
 *
 * 알림은 적게 울려야 쓸모가 있다. 같은 알림은 한 번만 보내도록 보낸 기록을 남긴다.
 */
class BudgetAlertNotifier(
    private val context: Context,
    private val repository: TripRepository,
    private val dao: BudgetAlertDao,
    private val settings: AppSettings,
) {

    /** 항공·숙박은 하루 씀씀이로 보지 않는다. */
    private val upfront = setOf(ExpenseCategory.FLIGHT, ExpenseCategory.LODGING)

    suspend fun check(tripId: Long) {
        if (!settings.settings.first().budgetAlertsEnabled) return

        val trip = repository.getTrip(tripId) ?: return
        val expenses = repository.expensesOnce(tripId)
        val today = LocalDate.now()

        val summary = TripSummaries.summarize(
            trip = trip,
            spentByCategory = expenses.groupBy { it.category }.mapValues { (_, items) ->
                items.sumOf { it.amountKrw }
            },
            today = today,
        )
        val spentTodayDaily = expenses
            .filter { it.date == today && it.category !in upfront }
            .sumOf { it.amountKrw }

        BudgetAlerts.evaluate(summary, today, spentTodayDaily).forEach { alert ->
            if (dao.countSent(tripId, alert.key) > 0) return@forEach
            notify(tripId, alert)
            dao.markSent(BudgetAlertEntity(tripId, alert.key, System.currentTimeMillis()))
        }
    }

    private fun notify(tripId: Long, alert: BudgetAlert) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "예산 경고", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "예산을 넘겼거나 넘길 것 같을 때 알려 준다"
            },
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TRIP_ID, tripId)
        }
        val pending = PendingIntent.getActivity(
            context,
            tripId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // 여행별로 알림을 따로 쌓되 같은 종류는 덮어쓴다.
        NotificationManagerCompat.from(context)
            .notify("budget-$tripId", alert.level.ordinal, notification)
    }

    private companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}
