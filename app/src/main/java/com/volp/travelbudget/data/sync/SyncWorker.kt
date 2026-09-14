package com.volp.travelbudget.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.volp.travelbudget.VolpApplication
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 몇 시간마다 다른 기기와 기록을 맞춘다.
 *
 * 앱을 열 때도 한 번 맞추지만, 한쪽 폰만 오래 쓰면 다른 폰이 계속 뒤처진다. 그래서 앱을 열지
 * 않아도 조용히 따라잡게 해 둔다.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? VolpApplication ?: return Result.success()
        if (!application.settings.settings.first().autoSyncEnabled) return Result.success()

        return when (application.firestoreSync.sync()) {
            is SyncOutcome.Done -> Result.success()
            // 코드가 없거나 설정이 없는 것은 다시 해도 마찬가지다.
            SyncOutcome.NoCode, SyncOutcome.NotConfigured -> Result.success()
            is SyncOutcome.Failed -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "volp-device-sync"
        private const val INTERVAL_HOURS = 6L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
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
