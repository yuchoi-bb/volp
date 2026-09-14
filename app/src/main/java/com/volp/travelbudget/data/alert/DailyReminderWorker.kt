package com.volp.travelbudget.data.alert

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.volp.travelbudget.VolpApplication
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 하루에 한 번 출발 준비와 배송 도착을 살핀다.
 *
 * 아침에 울려야 그날 손을 쓸 수 있다. 밤에 "내일 출발입니다"라고 해 봐야 할 수 있는 일이 없다.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? VolpApplication ?: return Result.success()

        return runCatching { application.tripReminderNotifier.checkAll(LocalDate.now()) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val UNIQUE_NAME = "volp-daily-reminder"

        /** 알림을 울릴 시각. */
        private val RUN_AT: LocalTime = LocalTime.of(9, 0)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextRun(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** 다음 아침 9시까지 남은 분. */
        private fun delayUntilNextRun(): Long {
            val now = LocalDateTime.now()
            val today = now.toLocalDate().atTime(RUN_AT)
            val next = if (now.isBefore(today)) today else today.plusDays(1)
            return Duration.between(now, next).toMinutes().coerceAtLeast(1L)
        }
    }
}
