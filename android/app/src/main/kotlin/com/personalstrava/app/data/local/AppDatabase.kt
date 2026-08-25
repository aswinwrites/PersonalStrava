package com.personalstrava.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.personalstrava.app.data.local.dao.ActivityDao
import com.personalstrava.app.data.local.dao.DailyStatsDao
import com.personalstrava.app.data.local.dao.ExportHistoryDao
import com.personalstrava.app.data.local.dao.GpsPointDao
import com.personalstrava.app.data.local.dao.MonthlyStatsDao
import com.personalstrava.app.data.local.dao.PersonalRecordDao
import com.personalstrava.app.data.local.dao.SyncQueueDao
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.data.local.entity.DailyStatsEntity
import com.personalstrava.app.data.local.entity.ExportHistoryEntity
import com.personalstrava.app.data.local.entity.GpsPointEntity
import com.personalstrava.app.data.local.entity.MonthlyStatsEntity
import com.personalstrava.app.data.local.entity.PersonalRecordEntity
import com.personalstrava.app.data.local.entity.SyncQueueEntity

/**
 * The detailed, on-device data source (spec section 4/18). Seven focused
 * tables rather than one giant table, each with the indexes its query
 * patterns need. This is the database that survives with or without
 * Supabase ever being reachable.
 */
@Database(
    entities = [
        ActivityEntity::class,
        GpsPointEntity::class,
        DailyStatsEntity::class,
        MonthlyStatsEntity::class,
        PersonalRecordEntity::class,
        SyncQueueEntity::class,
        ExportHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun gpsPointDao(): GpsPointDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun monthlyStatsDao(): MonthlyStatsDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun exportHistoryDao(): ExportHistoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "personalstrava.db")
                    .build()
                    .also { instance = it }
            }
    }
}
