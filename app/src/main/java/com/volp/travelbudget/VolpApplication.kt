package com.volp.travelbudget

import android.app.Application
import com.volp.travelbudget.data.alert.BudgetAlertNotifier
import com.volp.travelbudget.data.alert.BookingAlertWorker
import com.volp.travelbudget.data.alert.DailyReminderWorker
import com.volp.travelbudget.data.alert.TripReminderNotifier
import com.volp.travelbudget.data.alert.RainAlertWorker
import com.volp.travelbudget.data.backup.BackupManager
import com.volp.travelbudget.data.backup.BackupWorker
import com.volp.travelbudget.data.capture.CardCaptureHandler
import com.volp.travelbudget.data.exchange.ExchangeRateRepository
import com.volp.travelbudget.data.local.VolpDatabase
import com.volp.travelbudget.data.photos.PhotoStore
import com.volp.travelbudget.data.repository.BookingRepository
import com.volp.travelbudget.data.repository.ItineraryRepository
import com.volp.travelbudget.data.repository.CashRepository
import com.volp.travelbudget.data.repository.DocumentRepository
import com.volp.travelbudget.data.repository.PurchaseRepository
import com.volp.travelbudget.data.repository.TripRepository
import com.volp.travelbudget.data.travel.LocationProvider
import com.volp.travelbudget.data.travel.PlaceLookup
import com.volp.travelbudget.data.weather.WeatherRepository
import com.volp.travelbudget.data.settings.AppSettings
import com.volp.travelbudget.data.sync.FirestoreSync
import com.volp.travelbudget.data.sync.SyncEngine
import com.volp.travelbudget.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
            syncDao = database.syncDao(),
            exchangeRates = exchangeRates,
        )
    }

    val settings: AppSettings by lazy { AppSettings(this) }

    val photoStore: PhotoStore by lazy { PhotoStore(this, database.tripPhotoDao()) }

    val itineraryRepository: ItineraryRepository by lazy {
        ItineraryRepository(database.itineraryDao(), database.syncDao())
    }

    val bookingRepository: BookingRepository by lazy {
        BookingRepository(database.bookingDao(), database.syncDao())
    }

    val purchaseRepository: PurchaseRepository by lazy {
        PurchaseRepository(
            dao = database.purchaseDao(),
            syncDao = database.syncDao(),
            trips = repository,
            exchangeRates = exchangeRates,
        )
    }

    /** 기기끼리 기록을 맞출 때 쓰는 합치기 엔진. 드라이브 백업도 같은 것을 쓴다. */
    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            tripDao = database.tripDao(),
            expenseDao = database.expenseDao(),
            bookingDao = database.bookingDao(),
            purchaseDao = database.purchaseDao(),
            syncDao = database.syncDao(),
        )
    }

    val backupManager: BackupManager by lazy { BackupManager(syncEngine, settings) }

    /** 다른 안드로이드 기기와 기록을 맞춘다. */
    val firestoreSync: FirestoreSync by lazy {
        FirestoreSync(this, syncEngine, database.syncDao(), settings)
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(this, database.documentDao())
    }

    val cashRepository: CashRepository by lazy {
        CashRepository(database.cashTopUpDao(), database.syncDao())
    }

    val placeLookup: PlaceLookup by lazy { PlaceLookup(this) }

    val locationProvider: LocationProvider by lazy { LocationProvider(this) }

    val weatherRepository: WeatherRepository by lazy { WeatherRepository() }

    val budgetAlertNotifier: BudgetAlertNotifier by lazy {
        BudgetAlertNotifier(this, repository, database.budgetAlertDao(), settings)
    }

    val tripReminderNotifier: TripReminderNotifier by lazy {
        TripReminderNotifier(
            context = this,
            trips = repository,
            purchaseDao = database.purchaseDao(),
            bookingDao = database.bookingDao(),
            syncDao = database.syncDao(),
            sentDao = database.budgetAlertDao(),
            settings = settings,
        )
    }

    val captureHandler: CardCaptureHandler by lazy {
        CardCaptureHandler(this, repository, settings, budgetAlertNotifier)
    }

    override fun onCreate() {
        super.onCreate()
        BackupWorker.schedule(this)
        RainAlertWorker.schedule(this)
        SyncWorker.schedule(this)
        DailyReminderWorker.schedule(this)
        BookingAlertWorker.schedule(this)
        // 해외 결제 문자에는 원화 환산액이 없어 환율이 곧 금액 정확도다.
        applicationScope.launch { exchangeRates.refreshIfStale() }
        // 다른 기기에서 넣은 기록을 앱을 여는 순간 따라잡는다.
        applicationScope.launch {
            if (settings.settings.first().autoSyncEnabled) firestoreSync.sync()
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
