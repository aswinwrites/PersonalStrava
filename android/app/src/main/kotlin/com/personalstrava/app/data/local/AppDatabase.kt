package com.personalstrava.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.personalstrava.app.data.local.dao.ActivityDao
import com.personalstrava.app.data.local.dao.DailyStatsDao
import com.personalstrava.app.data.local.dao.ExportHistoryDao
import com.personalstrava.app.data.local.dao.GpsPointDao
import com.personalstrava.app.data.local.dao.MonthlyStatsDao
import com.personalstrava.app.data.local.dao.PersonalRecordDao
import com.personalstrava.app.data.local.dao.PhotoDao
import com.personalstrava.app.data.local.dao.SyncQueueDao
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.data.local.entity.DailyStatsEntity
import com.personalstrava.app.data.local.entity.ExportHistoryEntity
import com.personalstrava.app.data.local.entity.GpsPointEntity
import com.personalstrava.app.data.local.entity.MonthlyStatsEntity
import com.personalstrava.app.data.local.entity.PersonalRecordEntity
import com.personalstrava.app.data.local.entity.PhotoEntity
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
        PhotoEntity::class,
    ],
    version = 3,
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
    abstract fun photoDao(): PhotoDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // Purely additive (new table, nothing existing changes), so this is
        // a plain CREATE TABLE rather than a destructive fallback — the
        // point of a real migration here is that recorded-but-not-yet-synced
        // rides in `activities`/`sync_queue` survive the app update instead
        // of being wiped, which fallbackToDestructiveMigration() would do.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_photos` (
                        `id` TEXT NOT NULL,
                        `activityId` TEXT NOT NULL,
                        `localUri` TEXT NOT NULL,
                        `caption` TEXT,
                        `position` INTEGER NOT NULL,
                        `storagePath` TEXT,
                        `syncStatus` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_photos_activityId` ON `activity_photos` (`activityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_photos_syncStatus` ON `activity_photos` (`syncStatus`)")
            }
        }

        // jogging's per-day distance/time columns were added to DailyStatsEntity
        // without a matching migration at the time (predates this fix) — devices
        // that already had a v2 daily_stats table crash on open because Room's
        // schema-validation check (not just the version number) finds the two
        // missing columns. This backfills them; existing rows get 0/0.0 same as
        // any other day with no jogging recorded yet.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `daily_stats` ADD COLUMN `joggingDistanceMeters` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `daily_stats` ADD COLUMN `joggingSeconds` INTEGER NOT NULL DEFAULT 0")
                // monthly_stats has the exact same gap — MonthlyStatsEntity picked up
                // the same two jogging columns at the same time daily_stats did.
                db.execSQL("ALTER TABLE `monthly_stats` ADD COLUMN `joggingDistanceMeters` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `monthly_stats` ADD COLUMN `joggingSeconds` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "personalstrava.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
