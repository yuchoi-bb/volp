package com.volp.travelbudget.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.volp.travelbudget.VolpApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * 하루에 한 번 조용히 드라이브에 백업한다.
 *
 * 사용자가 아직 드라이브 접근에 동의하지 않았으면 아무것도 하지 않는다. 동의 화면은
 * 백그라운드에서 띄울 수 없고, 설정 화면에서 한 번 직접 백업하면 그 뒤로는 조용히 된다.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? VolpApplication ?: return Result.success()
        if (!application.settings.settings.first().autoBackupEnabled) return Result.success()

        val token = silentAccessToken() ?: return Result.success()
        return runCatching {
            BackupManager(application.repository, application.settings).backup(token)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private suspend fun silentAccessToken(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(BackupManager.DRIVE_SCOPE)))
            .build()
        val result = runCatching {
            Identity.getAuthorizationClient(applicationContext).authorize(request).await()
        }.getOrNull() ?: return null

        // 동의가 더 필요하면 사용자가 앱을 열었을 때 처리한다.
        return if (result.hasResolution()) null else result.accessToken
    }

    companion object {
        private const val UNIQUE_NAME = "volp-daily-backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
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
