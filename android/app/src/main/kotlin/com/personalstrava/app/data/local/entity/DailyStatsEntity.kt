package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per calendar date (yyyy-MM-dd string, device-local timezone). */
@Entity(tableName = "daily_stats", indices = [Index(value = ["date"], unique = true)])
data class DailyStatsEntity(
    @PrimaryKey val date: String,
    val steps: Int = 0,
    val walkingDistanceMeters: Double = 0.0,
    val cyclingDistanceMeters: Double = 0.0,
    val motorcyclingDistanceMeters: Double = 0.0,
    val walkingSeconds: Long = 0,
    val cyclingSeconds: Long = 0,
    val motorcyclingSeconds: Long = 0,
    val elevationGainMeters: Double = 0.0,
    val activityCount: Int = 0,
    val updatedAt: Long = 0,
)
