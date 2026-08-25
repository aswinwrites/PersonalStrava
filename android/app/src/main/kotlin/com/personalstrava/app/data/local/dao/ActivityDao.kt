package com.personalstrava.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalstrava.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    // REPLACE on conflict by primary key (client-generated UUID) is what
    // makes local writes idempotent — re-inserting the same activity id
    // (e.g. after a crash-recovery retry) overwrites rather than duplicates.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: ActivityEntity)

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: String): ActivityEntity?

    @Query("SELECT * FROM activities ORDER BY startTime DESC LIMIT :limit OFFSET :offset")
    fun observeRecent(limit: Int = 20, offset: Int = 0): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE syncStatus IN ('pending_sync', 'sync_failed') ORDER BY startTime ASC")
    suspend fun getPendingSync(): List<ActivityEntity>

    @Query("UPDATE activities SET syncStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, updatedAt: Long)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM activities WHERE startTime BETWEEN :startMs AND :endMs ORDER BY startTime")
    suspend fun getInRange(startMs: Long, endMs: Long): List<ActivityEntity>
}
