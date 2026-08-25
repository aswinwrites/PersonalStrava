package com.personalstrava.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalstrava.app.data.local.entity.ExportHistoryEntity
import com.personalstrava.app.data.local.entity.SyncQueueEntity

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue ORDER BY createdAt")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query("DELETE FROM sync_queue WHERE activityId = :activityId")
    suspend fun remove(activityId: String)

    @Query("UPDATE sync_queue SET attemptCount = attemptCount + 1, lastAttemptAt = :now, lastError = :error WHERE activityId = :activityId")
    suspend fun recordAttempt(activityId: String, now: Long, error: String?)
}

@Dao
interface ExportHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExportHistoryEntity)

    @Query("SELECT * FROM export_history ORDER BY createdAt DESC")
    suspend fun getAll(): List<ExportHistoryEntity>
}
