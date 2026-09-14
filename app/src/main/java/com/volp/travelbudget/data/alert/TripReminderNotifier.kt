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
import com.volp.travelbudget.data.local.PurchaseDao
import com.volp.travelbudget.data.local.SyncDao
import com.volp.travelbudget.data.local.toDomain
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.domain.model.Trip
import com.volp.travelbudget.domain.packing.PackingAdvisor
import com.volp.travelbudget.domain.prep.PrepReminder
import com.volp.travelbudget.domain.prep.TripPrepAdvisor
import com.volp.travelbudget.domain.purchase.Purchases
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 하루에 한 번, 출발 전에 짚을 것과 도착할 때가 된 주문을 살핀다.
 *
 * 여행 준비의 실패는 대개 제때 생각나지 않아 벌어진다. 다만 알림이 잦으면 사람은 알림을 끄므로
 * 여행 하나에 대해 같은 말을 두 번 하지 않고, 배송 쪽도 하루 한 번으로 묶는다.
 */
class TripReminderNotifier(
    private val context: Context,
    private val trips: TripRepository,
    private val purchaseDao: PurchaseDao,
    private val syncDao: SyncDao,
    private val sentDao: BudgetAlertDao,
    private val settings: AppSettings,
) {

    suspend fun checkAll(today: LocalDate = LocalDate.now()) {
        if (!settings.settings.first().prepRemindersEnabled) return

        checkArrivals(today)
        checkPreparations(today)
    }

    /** 오늘까지 오기로 한 주문 가운데 아직 안 온 것. */
    private suspend fun checkArrivals(today: LocalDate) {
        val current = settings.settings.first()
        if (current.lastArrivalReminderOn == today.toString()) return

        val due = purchaseDao.findDueThrough(today).map { it.toDomain() }
        if (due.isEmpty()) return

        val overdue = due.filter { it.isOverdue(today) }
        val message = if (overdue.isNotEmpty()) {
            val first = overdue.first()
            if (overdue.size > 1) {
                "${first.title} 외 ${overdue.size - 1}건이 예정일이 지났는데 아직 안 왔습니다."
            } else {
                "${first.title} · 예정일 ${first.eta}이 지났는데 아직 안 왔습니다."
            }
        } else {
            val first = due.first()
            if (due.size > 1) {
                "${first.title} 외 ${due.size - 1}건이 오늘 도착할 예정입니다."
            } else {
                "${first.title}이(가) 오늘 도착할 예정입니다."
            }
        }

        notify(
            tag = "arrival",
            id = 1,
            title = if (overdue.isNotEmpty()) "안 온 주문이 있습니다" else "오늘 도착 예정",
            message = message,
            tripId = due.firstOrNull { it.tripId != null }?.tripId,
        )
        settings.setLastArrivalReminderOn(today.toString())
    }

    /** 일주일 전과 하루 전에 한 번씩. */
    private suspend fun checkPreparations(today: LocalDate) {
        val passportExpiry = settings.settings.first().passportExpiry
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        trips.tripsOnce()
            .filter { !it.startDate.isBefore(today) }
            .forEach { trip ->
                val stage = TripPrepAdvisor.stageOn(trip, today) ?: return@forEach
                val key = "prep:${stage.name}"
                if (sentDao.countSent(trip.id, key) > 0) return@forEach

                val purchases = purchaseDao.findByTrip(trip.id).map { it.toDomain() }
                val reminder = TripPrepAdvisor.build(
                    trip = trip,
                    today = today,
                    passportExpiry = passportExpiry,
                    uncheckedItems = uncheckedItemsOf(trip.id, trip),
                    lateArrivals = Purchases.risky(purchases, trip.startDate).size,
                    missingBookings = bookingsMissing(trip.id),
                ) ?: return@forEach

                notify(reminder)
                sentDao.markSent(BudgetAlertEntity(trip.id, key, System.currentTimeMillis()))
            }
    }

    /**
     * 아직 체크하지 않은 준비물.
     *
     * 예보 없이 뽑으므로 날씨 때문에 챙길 것은 빠진다. 며칠 뒤 날씨를 지금 단정해 "우산을 넣으라"고
     * 하는 것보다, 확실한 것만 말하는 쪽이 낫다.
     */
    private suspend fun uncheckedItemsOf(tripId: Long, trip: Trip): List<String> {
        val checked = syncDao.packingChecksOf(tripId).filter { it.checked }.map { it.itemName }.toSet()
        return PackingAdvisor.suggest(trip, emptyList())
            .map { it.name }
            .filterNot { it in checked }
    }

    private suspend fun bookingsMissing(tripId: Long): Boolean =
        syncDao.bookingCount(tripId) == 0

    private fun notify(reminder: PrepReminder) {
        notify(
            tag = "prep-${reminder.tripId}",
            id = reminder.stage.ordinal,
            title = "${reminder.tripTitle} · ${reminder.stage.label}",
            message = reminder.notes.joinToString("\n") { it.text },
            tripId = reminder.tripId,
        )
    }

    private fun notify(tag: String, id: Int, title: String, message: String, tripId: Long?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "출발 준비", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "출발 전 준비와 주문한 물건의 도착을 알려 준다"
            },
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (tripId != null) putExtra(MainActivity.EXTRA_OPEN_TRIP_ID, tripId)
        }
        val pending = PendingIntent.getActivity(
            context,
            (tripId ?: 0L).toInt() + id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(tag, id, notification)
    }

    private companion object {
        const val CHANNEL_ID = "trip_prep"
    }
}
