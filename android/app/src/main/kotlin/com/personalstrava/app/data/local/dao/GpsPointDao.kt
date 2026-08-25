package com.personalstrava.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalstrava.app.data.local.entity.GpsPointEntity

@Dao
interface GpsPointDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<GpsPointEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: GpsPointEntity)

    // Never load a full track into memory at once for anything but the owning
    // activity's own processing/export — spec section 45 (years of data).
    @Query("SELECT * FROM gps_points WHERE activityId = :activityId ORDER BY timestamp")
    suspend fun getForActivity(activityId: String): List<GpsPointEntity>

    @Query("SELECT COUNT(*) FROM gps_points WHERE activityId = :activityId")
    suspend fun countForActivity(activityId: String): Int

    @Query("DELETE FROM gps_points WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: String)
}
