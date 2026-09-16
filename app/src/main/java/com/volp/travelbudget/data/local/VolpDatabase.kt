package com.volp.travelbudget.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        ExpenseEntity::class,
        PendingTransactionEntity::class,
        TripPhotoEntity::class,
        ExchangeRateEntity::class,
        MerchantAliasEntity::class,
        ItineraryStopEntity::class,
        PackingCheckEntity::class,
        BudgetAlertEntity::class,
        BookingEntity::class,
        DayNoteEntity::class,
        DeletionEntity::class,
        PurchaseEntity::class,
        CashTopUpEntity::class,
        DocumentEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VolpDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun pendingTransactionDao(): PendingTransactionDao

    abstract fun tripPhotoDao(): TripPhotoDao

    abstract fun exchangeRateDao(): ExchangeRateDao

    abstract fun merchantAliasDao(): MerchantAliasDao

    abstract fun itineraryDao(): ItineraryDao

    abstract fun budgetAlertDao(): BudgetAlertDao

    abstract fun bookingDao(): BookingDao

    abstract fun syncDao(): SyncDao

    abstract fun purchaseDao(): PurchaseDao

    abstract fun cashTopUpDao(): CashTopUpDao

    abstract fun documentDao(): DocumentDao

    companion object {
        private const val DATABASE_NAME = "volp.db"

        /** 카드 문자에서 모은 결제를 담을 미확인함을 추가한 버전. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_transactions` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `issuer` TEXT NOT NULL,
                        `cardLabel` TEXT NOT NULL,
                        `holderName` TEXT,
                        `kind` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `currencyCode` TEXT NOT NULL,
                        `countryCode` TEXT,
                        `merchant` TEXT NOT NULL,
                        `occurredAt` TEXT NOT NULL,
                        `paymentPlan` TEXT,
                        `source` TEXT NOT NULL,
                        `receivedAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `foreignHolder` INTEGER NOT NULL,
                        `tripId` INTEGER,
                        `expenseId` INTEGER,
                        `rawBody` TEXT NOT NULL,
                        `fingerprint` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_transactions_fingerprint` " +
                        "ON `pending_transactions` (`fingerprint`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pending_transactions_status` " +
                        "ON `pending_transactions` (`status`)",
                )
            }
        }

        /** 여행 사진을 붙일 수 있게 한 버전. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `trip_photos` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `tripId` INTEGER NOT NULL,
                        `expenseId` INTEGER,
                        `filePath` TEXT NOT NULL,
                        `takenAt` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_photos_tripId` ON `trip_photos` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_photos_expenseId` ON `trip_photos` (`expenseId`)")
            }
        }

        /** 건별 환율과 실제 청구액 보정, 그리고 받아 둔 환율표를 더한 버전. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `exchangeRate` REAL")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `billedTotalKrw` INTEGER")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `settlementFactor` REAL NOT NULL DEFAULT 1.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exchange_rates` (
                        `code` TEXT NOT NULL PRIMARY KEY,
                        `krwPerUnit` REAL NOT NULL,
                        `fetchedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** 가맹점 별칭 사전을 더한 버전. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_aliases` (
                        `rawKey` TEXT NOT NULL PRIMARY KEY,
                        `displayName` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /** 일정표와 준비물 체크, 그리고 여행의 대표 좌표를 더한 버전. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `longitude` REAL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `itinerary_stops` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `tripId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `startTime` TEXT,
                        `memo` TEXT NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_itinerary_stops_tripId` ON `itinerary_stops` (`tripId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `packing_checks` (
                        `tripId` INTEGER NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `checked` INTEGER NOT NULL,
                        PRIMARY KEY(`tripId`, `itemName`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** 이미 보낸 예산 알림을 기억하는 표를 더한 버전. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budget_alerts` (
                        `tripId` INTEGER NOT NULL,
                        `alertKey` TEXT NOT NULL,
                        `notifiedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tripId`, `alertKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** 항공·숙소 같은 예약과 하루 메모를 더한 버전. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookings` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `tripId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `provider` TEXT NOT NULL,
                        `confirmationCode` TEXT NOT NULL,
                        `startAt` TEXT NOT NULL,
                        `endAt` TEXT,
                        `fromName` TEXT NOT NULL,
                        `fromCode` TEXT NOT NULL,
                        `toName` TEXT NOT NULL,
                        `toCode` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `seat` TEXT NOT NULL,
                        `gate` TEXT NOT NULL,
                        `terminal` TEXT NOT NULL,
                        `memo` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookings_tripId` ON `bookings` (`tripId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `day_notes` (
                        `tripId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tripId`, `date`),
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * 기기 두 대에서 같은 기록을 쓰기 위한 준비.
         *
         * 로컬 행 번호는 기기마다 다르게 매겨져 두 대를 맞출 때 쓸 수 없다. 기록마다 기기와
         * 무관한 uid를 붙이고, 지운 기록은 흔적을 남겨 다음 동기화에서 되살아나지 않게 한다.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                listOf("trips", "expenses", "bookings", "itinerary_stops").forEach { table ->
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                    // 이미 있던 기록에도 uid를 붙여 준다.
                    db.execSQL("UPDATE `$table` SET `uid` = lower(hex(randomblob(16))), `updatedAt` = $now")
                }
                db.execSQL("ALTER TABLE `packing_checks` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `packing_checks` SET `updatedAt` = $now")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `deletions` (
                        `entity` TEXT NOT NULL,
                        `uid` TEXT NOT NULL,
                        `deletedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`entity`, `uid`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** 여행 전에 사 두는 것들(항공권·캐리어 같은)과 그 도착 예정일을 더한 버전. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchases` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `uid` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `tripId` INTEGER,
                        `kind` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `amountKrw` INTEGER NOT NULL,
                        `originalAmount` REAL,
                        `currencyCode` TEXT NOT NULL,
                        `orderedOn` TEXT,
                        `eta` TEXT,
                        `status` TEXT NOT NULL,
                        `orderNumber` TEXT NOT NULL,
                        `trackingNumber` TEXT NOT NULL,
                        `carrier` TEXT NOT NULL,
                        `memo` TEXT NOT NULL,
                        `sourceText` TEXT NOT NULL,
                        `expenseId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_tripId` ON `purchases` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_uid` ON `purchases` (`uid`)")
            }
        }

        /**
         * 현금 지갑을 더한 버전.
         *
         * 카드는 문자로 저절로 들어오지만 현금은 쓰는 순간 기록이 없으면 사라진다. 지출마다
         * 무엇으로 냈는지를 남기고, 환전해 온 돈을 따로 적어 두면 지갑에 남은 현금이 나온다.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 예전에 넣은 지출은 무엇으로 냈는지 알 수 없다. 넘겨짚지 않고 미상으로 둔다.
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `method` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cash_top_ups` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `uid` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `tripId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `currencyCode` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `krwPaid` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `memo` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_top_ups_tripId` ON `cash_top_ups` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_top_ups_uid` ON `cash_top_ups` (`uid`)")
            }
        }

        /**
         * 문서 보관함을 더한 버전.
         *
         * 여권·보험증서·바우처는 해외에서 데이터가 없을 때 꺼내 봐야 한다. 사진은 기기 안에 두고
         * 표에는 그 자리만 적어 둔다.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `documents` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `uid` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `tripId` INTEGER,
                        `kind` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `number` TEXT NOT NULL,
                        `expiresOn` TEXT,
                        `memo` TEXT NOT NULL,
                        `filePath` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_tripId` ON `documents` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_uid` ON `documents` (`uid`)")
            }
        }

        /** 목록에서 여행을 끌어 옮길 수 있게 자리 번호를 더한 버전. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 0은 아직 손대지 않았다는 뜻이다. 그때는 날짜 차례로 보여 준다.
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 예전에 넣은 일정은 모두 옮길 수 있는 것으로 본다.
                db.execSQL(
                    "ALTER TABLE `itinerary_stops` ADD COLUMN `fixity` TEXT NOT NULL DEFAULT 'FLEXIBLE'",
                )
            }
        }

        @Volatile
        private var instance: VolpDatabase? = null

        fun get(context: Context): VolpDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): VolpDatabase =
            Room.databaseBuilder(context, VolpDatabase::class.java, DATABASE_NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                )
                .build()
    }
}
