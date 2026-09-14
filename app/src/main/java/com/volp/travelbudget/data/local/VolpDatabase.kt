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
    ],
    version = 5,
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

        @Volatile
        private var instance: VolpDatabase? = null

        fun get(context: Context): VolpDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): VolpDatabase =
            Room.databaseBuilder(context, VolpDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
