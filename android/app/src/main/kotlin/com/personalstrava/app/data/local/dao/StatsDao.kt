package com.personalstrava.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalstrava.app.data.local.entity.DailyStatsEntity
import com.personalstrava.app.data.local.entity.MonthlyStatsEntity
import com.personalstrava.app.data.local.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getByDate(date: String): DailyStatsEntity?

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun observeByDate(date: String): Flow<DailyStatsEntity?>

    @Query("SELECT * FROM daily_stats WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getRange(start: String, end: String): List<DailyStatsEntity>
}

@Dao
interface MonthlyStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: MonthlyStatsEntity)

    @Query("SELECT * FROM monthly_stats WHERE month = :month")
    suspend fun getByMonth(month: String): MonthlyStatsEntity?

    @Query("SELECT * FROM monthly_stats ORDER BY month DESC")
    fun observeAll(): Flow<List<MonthlyStatsEntity>>
}

@Dao
interface PersonalRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PersonalRecordEntity)

    @Query("SELECT * FROM personal_records WHERE recordKey = :key")
    suspend fun getByKey(key: String): PersonalRecordEntity?

    @Query("SELECT * FROM personal_records")
    fun observeAll(): Flow<List<PersonalRecordEntity>>
}
