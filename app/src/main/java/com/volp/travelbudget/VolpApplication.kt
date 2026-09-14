package com.volp.travelbudget

import android.app.Application
import com.volp.travelbudget.data.backup.BackupWorker
import com.volp.travelbudget.data.capture.CardCaptureHandler
import com.volp.travelbudget.data.local.VolpDatabase
import com.volp.travelbudget.data.photos.PhotoStore
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.settings.AppSettings

/**
 * 앱 전체에서 하나만 쓰는 의존성을 들고 있는다. 규모가 작아 DI 라이브러리 대신 직접 조립한다.
 */
class VolpApplication : Application() {

    private val database: VolpDatabase by lazy { VolpDatabase.get(this) }

    val repository: TripRepository by lazy {
        TripRepository(
            tripDao = database.tripDao(),
            expenseDao = database.expenseDao(),
            pendingDao = database.pendingTransactionDao(),
        )
    }

    val settings: AppSettings by lazy { AppSettings(this) }

    val photoStore: PhotoStore by lazy { PhotoStore(this, database.tripPhotoDao()) }

    val captureHandler: CardCaptureHandler by lazy {
        CardCaptureHandler(this, repository, settings)
    }

    override fun onCreate() {
        super.onCreate()
        BackupWorker.schedule(this)
    }
}
