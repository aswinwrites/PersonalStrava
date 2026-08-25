package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Flexible key/value records — new record types are new recordKey values, no migration (spec section 23). */
@Entity(tableName = "personal_records")
data class PersonalRecordEntity(
    @PrimaryKey val recordKey: String,
    val activityType: String?,
    val valueNumeric: Double,
    val valueUnit: String,
    val activityId: String?,
    val achievedOn: String?, // yyyy-MM-dd
    val updatedAt: Long,
)
