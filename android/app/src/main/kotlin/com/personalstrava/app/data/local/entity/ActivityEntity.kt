package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Mirrors supabase/migrations/0001_init_schema.sql `activities` (summary
 * columns) plus local-only bookkeeping. `id` is a client-generated UUID
 * (see [com.personalstrava.app.domain.IdGenerator]) so sync upserts are
 * naturally idempotent — retrying a sync never creates a duplicate row,
 * locally or in Supabase (spec sections 15, 19).
 */
@Entity(
    tableName = "activities",
    indices = [Index("activityType"), Index("startTime"), Index("syncStatus")],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val activityType: String, // ActivityType.dbValue
    val startTime: Long, // epoch millis
    val endTime: Long,
    val elapsedSeconds: Long,
    val movingSeconds: Long,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val averageSpeedMps: Double?,
    val movingAverageSpeedMps: Double?,
    val maxSpeedMps: Double?,
    val startLatitude: Double?,
    val startLongitude: Double?,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val routePolyline: String?, // simplified (RDP) encoded polyline, uploaded to Supabase
    val title: String?,
    val notes: String?,
    val syncStatus: String, // SyncStatus.dbValue
    val createdAt: Long,
    val updatedAt: Long,
)
