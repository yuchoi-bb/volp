package com.volp.travelbudget.data.alert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.volp.travelbudget.VolpApplication
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 시간마다 예약을 살펴 지금 말해 줄 것이 있는지 본다.
 *
 * 온라인 체크인이 열리는 순간과 공항으로 나설 시각은 하루 한 번으로는 못 맞춘다. 같은 알림을
 * 두 번 보내지 않으므로 자주 본다고 자주 울리지는 않는다.
 */
class BookingAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? VolpApplication ?: return Result.success()

        return runCatching { application.tripReminderNotifier.checkBookings(LocalDateTime.now()) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val UNIQUE_NAME = "volp-booking-alerts"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BookingAlertWorker>(1, TimeUnit.HOURS).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
