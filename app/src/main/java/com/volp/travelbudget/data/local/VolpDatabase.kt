package com.volp.travelbudget.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TripEntity::class, ExpenseEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VolpDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    abstract fun expenseDao(): ExpenseDao

    companion object {
        private const val DATABASE_NAME = "volp.db"

        @Volatile
        private var instance: VolpDatabase? = null

        fun get(context: Context): VolpDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): VolpDatabase =
            Room.databaseBuilder(context, VolpDatabase::class.java, DATABASE_NAME)
                // 외래 키 CASCADE가 동작해야 여행을 지울 때 지출도 함께 지워진다.
                .build()
    }
}
