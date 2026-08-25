package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Full-resolution GPS samples. This table is the detail that NEVER leaves
 * the device wholesale (spec section 4/16/20) — only a simplified polyline
 * derived from it is synced, as part of the parent ActivityEntity.
 */
@Entity(
    tableName = "gps_points",
    indices = [Index("activity_id"), Index(value = ["activity_id", "timestamp"])],
)
data class GpsPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Float?,
    val accuracy: Float?,
    val heading: Float?,
)
