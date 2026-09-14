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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.volp.travelbudget.MainActivity
import com.volp.travelbudget.R
import com.volp.travelbudget.VolpApplication
import com.volp.travelbudget.domain.travel.CityCoordinates
import com.volp.travelbudget.domain.travel.GeoPoint
import com.volp.travelbudget.domain.weather.RainWatch
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 여행 중에 15분마다 지금 있는 곳의 강수 예보를 보고, 한 시간 안에 비가 오면 알린다.
 *
 * 하루 예보로는 우산을 챙길 시점을 놓친다. 여행이 없는 날에는 위치도 예보도 건드리지 않는다.
 */
class RainAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? VolpApplication ?: return Result.success()
        val settings = application.settings.settings.first()
        if (!settings.rainAlertsEnabled) return Result.success()

        val today = LocalDate.now()
        val trip = application.repository.tripsOnce().firstOrNull {
            !today.isBefore(it.startDate) && !today.isAfter(it.endDate)
        } ?: return Result.success()

        // 지금 있는 곳을 모르면 여행지 좌표라도 쓴다. 아무것도 없으면 볼 것이 없다.
        val point = application.locationProvider.current()
            ?: trip.latitude?.let { lat -> trip.longitude?.let { lon -> GeoPoint(lat, lon) } }
            ?: CityCoordinates.find(trip.destinationKey)
            ?: return Result.success()

        val slots = application.weatherRepository.precipitationSlots(point)
        val alert = RainWatch.evaluate(slots, LocalDateTime.now()) ?: return Result.success()

        // 같은 비로 두 번 울리지 않는다.
        val key = alert.startsAt.toString()
        if (settings.lastRainAlertFor == key) return Result.success()

        notify(
            title = if (alert.minutesAway <= 5) "곧 비가 옵니다" else "${alert.minutesAway}분 뒤 비가 옵니다",
            message = buildString {
                append(trip.destinationName)
                append(" · ")
                append(alert.startsAt.format(TIME_FORMAT))
                append("부터")
                alert.probability?.let { append(" · 강수 확률 ${it}%") }
                if (alert.millimeters > 0) {
                    append(" · ")
                    append(String.format(java.util.Locale.KOREA, "%.1fmm", alert.millimeters))
                }
            },
            tripId = trip.id,
        )
        application.settings.setLastRainAlertFor(key)
        return Result.success()
    }

    private fun notify(title: String, message: String, tripId: Long) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "비 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "여행 중 한 시간 안에 비가 오면 알려 준다"
            },
        )

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_TRIP_ID, tripId)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            RAIN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "rain_alerts"
        private const val NOTIFICATION_ID = 2001
        private const val RAIN_REQUEST_CODE = 2001
        private const val UNIQUE_NAME = "volp-rain-watch"
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RainAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
