package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per calendar month (yyyy-MM string). Rolled up from DailyStatsEntity. */
@Entity(tableName = "monthly_stats", indices = [Index(value = ["month"], unique = true)])
data class MonthlyStatsEntity(
    @PrimaryKey val month: String,
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
