package com.personalstrava.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalstrava.app.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("SELECT * FROM activity_photos WHERE activityId = :activityId ORDER BY position ASC")
    fun observeForActivity(activityId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM activity_photos WHERE activityId = :activityId ORDER BY position ASC")
    suspend fun getForActivity(activityId: String): List<PhotoEntity>

    @Query("SELECT * FROM activity_photos WHERE syncStatus IN ('pending_sync', 'sync_failed') ORDER BY createdAt ASC")
    suspend fun getPendingSync(): List<PhotoEntity>

    @Query("UPDATE activity_photos SET syncStatus = :status, storagePath = :storagePath WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String, storagePath: String?)

    @Query("DELETE FROM activity_photos WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM activity_photos WHERE activityId = :activityId")
    suspend fun nextPosition(activityId: String): Int
}
