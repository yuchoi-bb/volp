package com.volp.travelbudget

import android.app.Application
import com.volp.travelbudget.data.alert.BudgetAlertNotifier
import com.volp.travelbudget.data.backup.BackupWorker
import com.volp.travelbudget.data.capture.CardCaptureHandler
import com.volp.travelbudget.data.exchange.ExchangeRateRepository
import com.volp.travelbudget.data.local.VolpDatabase
import com.volp.travelbudget.data.photos.PhotoStore
import com.volp.travelbudget.data.repository.ItineraryRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.travel.LocationProvider
import com.volp.travelbudget.data.travel.PlaceLookup
import com.volp.travelbudget.data.weather.WeatherRepository
import com.volp.travelbudget.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 앱 전체에서 하나만 쓰는 의존성을 들고 있는다. 규모가 작아 DI 라이브러리 대신 직접 조립한다.
 */
class VolpApplication : Application() {

    private val database: VolpDatabase by lazy { VolpDatabase.get(this) }

    val exchangeRates: ExchangeRateRepository by lazy {
        ExchangeRateRepository(database.exchangeRateDao())
    }

    val repository: TripRepository by lazy {
        TripRepository(
            tripDao = database.tripDao(),
            expenseDao = database.expenseDao(),
            pendingDao = database.pendingTransactionDao(),
            aliasDao = database.merchantAliasDao(),
            exchangeRates = exchangeRates,
        )
    }

    val settings: AppSettings by lazy { AppSettings(this) }

    val photoStore: PhotoStore by lazy { PhotoStore(this, database.tripPhotoDao()) }

    val itineraryRepository: ItineraryRepository by lazy {
        ItineraryRepository(database.itineraryDao())
    }

    val placeLookup: PlaceLookup by lazy { PlaceLookup(this) }

    val locationProvider: LocationProvider by lazy { LocationProvider(this) }

    val weatherRepository: WeatherRepository by lazy { WeatherRepository() }

    val budgetAlertNotifier: BudgetAlertNotifier by lazy {
        BudgetAlertNotifier(this, repository, database.budgetAlertDao(), settings)
    }

    val captureHandler: CardCaptureHandler by lazy {
        CardCaptureHandler(this, repository, settings, budgetAlertNotifier)
    }

    override fun onCreate() {
        super.onCreate()
        BackupWorker.schedule(this)
        // 해외 결제 문자에는 원화 환산액이 없어 환율이 곧 금액 정확도다.
        applicationScope.launch { exchangeRates.refreshIfStale() }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
