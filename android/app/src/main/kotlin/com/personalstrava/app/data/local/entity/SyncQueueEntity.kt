package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Explicit outbox for the sync manager (spec section 19). An activity moves
 * local -> pending_sync -> syncing -> synced|sync_failed. Rows here are the
 * work items WorkManager's sync job drains; retry count drives backoff.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val activityId: String,
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val createdAt: Long,
)
