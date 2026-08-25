package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local record of GPX/CSV exports generated on-device (spec section 18, 31). */
@Entity(tableName = "export_history")
data class ExportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodLabel: String,
    val format: String, // "gpx" | "csv" | "json" | "zip"
    val filePath: String,
    val activityCount: Int,
    val createdAt: Long,
)
