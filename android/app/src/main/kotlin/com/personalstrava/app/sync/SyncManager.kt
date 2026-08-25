package com.personalstrava.app.sync

import com.personalstrava.app.data.local.dao.ActivityDao
import com.personalstrava.app.data.local.dao.SyncQueueDao
import com.personalstrava.app.data.local.entity.ActivityEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

/**
 * Drives local -> pending_sync -> syncing -> synced|sync_failed (spec
 * section 19). Called from a WorkManager periodic job and immediately after
 * "Stop recording" when the network is up.
 *
 * Idempotency: every upsert is keyed on the activity's client-generated
 * UUID with Postgres `on_conflict=id` semantics (`upsert()` below), so a
 * retried sync after a timed-out-but-actually-succeeded request never
 * creates a duplicate row — it just overwrites itself with the same data
 * (spec sections 19, 44 "test duplicate sync").
 */
class SyncManager(
    private val supabase: SupabaseClient,
    private val activityDao: ActivityDao,
    private val syncQueueDao: SyncQueueDao,
) {
    private val maxAttempts = 6

    suspend fun syncPending(): SyncResult {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
            ?: return SyncResult(succeeded = 0, failed = 0) // not signed in — nothing to do yet

        val pending = activityDao.getPendingSync()
        var succeeded = 0
        var failed = 0

        for (activity in pending) {
            activityDao.updateSyncStatus(activity.id, "syncing", System.currentTimeMillis())
            try {
                supabase.postgrest["activities"].upsert(activity.toRemoteDto(userId)) {
                    // Conflict target is the primary key (id) — an upsert on
                    // the same UUID overwrites in place rather than erroring
                    // or duplicating.
                }
                activityDao.updateSyncStatus(activity.id, "synced", System.currentTimeMillis())
                syncQueueDao.remove(activity.id)
                succeeded++
            } catch (e: Exception) {
                failed++
                syncQueueDao.recordAttempt(activity.id, System.currentTimeMillis(), e.message)
                val queueEntry = syncQueueDao.getAll().firstOrNull { it.activityId == activity.id }
                val shouldGiveUp = (queueEntry?.attemptCount ?: 0) >= maxAttempts
                activityDao.updateSyncStatus(
                    activity.id,
                    if (shouldGiveUp) "sync_failed" else "pending_sync",
                    System.currentTimeMillis(),
                )
                // WorkManager's own exponential backoff policy (configured on
                // the enqueued request, see docs/sync.md) handles the retry
                // delay between attempts — this method just does one pass.
            }
        }

        return SyncResult(succeeded, failed)
    }

    data class SyncResult(val succeeded: Int, val failed: Int)
}

@Serializable
private data class RemoteActivityDto(
    val id: String,
    val user_id: String,
    val activity_type: String,
    val start_time: String, // ISO-8601
    val end_time: String,
    val elapsed_seconds: Long,
    val moving_seconds: Long,
    val distance_meters: Double,
    val elevation_gain_meters: Double,
    val elevation_loss_meters: Double,
    val average_speed_mps: Double?,
    val moving_average_speed_mps: Double?,
    val max_speed_mps: Double?,
    val start_latitude: Double?,
    val start_longitude: Double?,
    val end_latitude: Double?,
    val end_longitude: Double?,
    val route_polyline: String?,
    val title: String?,
    val notes: String?,
)

private fun ActivityEntity.toRemoteDto(userId: String) = RemoteActivityDto(
    id = id,
    user_id = userId,
    activity_type = activityType,
    start_time = java.time.Instant.ofEpochMilli(startTime).toString(),
    end_time = java.time.Instant.ofEpochMilli(endTime).toString(),
    elapsed_seconds = elapsedSeconds,
    moving_seconds = movingSeconds,
    distance_meters = distanceMeters,
    elevation_gain_meters = elevationGainMeters,
    elevation_loss_meters = elevationLossMeters,
    average_speed_mps = averageSpeedMps,
    moving_average_speed_mps = movingAverageSpeedMps,
    max_speed_mps = maxSpeedMps,
    start_latitude = startLatitude,
    start_longitude = startLongitude,
    end_latitude = endLatitude,
    end_longitude = endLongitude,
    route_polyline = routePolyline,
    title = title,
    notes = notes,
)
