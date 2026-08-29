package com.personalstrava.app.record

import android.location.Location
import com.personalstrava.app.data.local.AppDatabase
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.data.local.entity.GpsPointEntity
import com.personalstrava.app.domain.IdGenerator
import com.personalstrava.app.domain.gps.GpsPoint
import com.personalstrava.app.domain.gps.GpsProcessor
import com.personalstrava.app.domain.model.ActivityType
import com.personalstrava.app.domain.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Live state exposed while an activity is being recorded. [RecordingViewModel]
 * collects this to drive the recording screen; distance/speed here are
 * derived from whatever has been persisted to Room so far, not a separate
 * source of truth.
 */
data class RecordingLiveStats(
    val activityId: String? = null,
    val activityType: ActivityType? = null,
    val isRecording: Boolean = false,
    val startTimeMs: Long? = null,
    val distanceMeters: Double = 0.0,
    val currentSpeedMps: Double = 0.0,
    val pointCount: Int = 0,
    val isPaused: Boolean = false,
    /** Total paused duration accumulated across all pause/resume cycles so far, finalized on each resume(). */
    val pausedMs: Long = 0,
    /** Wall-clock timestamp the *current* pause began, or null when not paused. */
    val pauseStartedAtMs: Long? = null,
)

/**
 * The piece [ActivityRecordingService]'s own comment flagged as missing:
 * "Persisted via GpsPointDao on a background dispatcher — see
 * RecordingRepository (Phase 2: wires this callback to Room writes + the
 * live-metrics StateFlow the recording UI reads)." This is that wiring.
 *
 * Subscribes to [LocationSampleBus], persists every sample to Room, and
 * maintains live distance/speed by re-running [GpsProcessor] over an
 * in-memory buffer scoped to the *current* activity only (cleared on
 * stop() — never the full historical track; see GpsPointDao's own comment
 * on why that never gets loaded into memory wholesale).
 *
 * Owned by [com.personalstrava.app.PersonalStravaApp] as a singleton (like
 * [AppDatabase] itself), not by a ViewModel — recording must keep writing
 * to Room even if the recording screen is destroyed (rotation, navigating
 * back to Home while the foreground service keeps running in the
 * background). A ViewModel only ever re-attaches to whatever's already in
 * progress here.
 */
class RecordingRepository(private val database: AppDatabase) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gpsPointDao = database.gpsPointDao()
    private val activityDao = database.activityDao()

    private val _liveStats = MutableStateFlow(RecordingLiveStats())
    val liveStats: StateFlow<RecordingLiveStats> = _liveStats.asStateFlow()

    /** In-memory buffer for the activity currently being recorded only. */
    private val currentPoints = mutableListOf<GpsPoint>()

    private val locationListener: (String, Location) -> Unit = { activityId, location ->
        onLocation(activityId, location)
    }

    /** Total paused ms already finalized by resume() calls this session — mirrors liveStats.pausedMs but kept here so pause()/resume() don't need to read-then-write the StateFlow to stay consistent. */
    private var accumulatedPausedMs: Long = 0
    private var pauseStartedAtMs: Long? = null

    /**
     * Creates the draft activity row and starts listening for GPS samples.
     * Does NOT start [ActivityRecordingService] itself — the caller
     * (RecordingViewModel) starts the service with the returned id so the
     * two stay in lockstep, and must have already verified location
     * permissions before calling this (see RecordingScreen's permission
     * gate).
     */
    fun start(activityType: ActivityType): String {
        val activityId = IdGenerator.newActivityId()
        val now = System.currentTimeMillis()
        currentPoints.clear()
        accumulatedPausedMs = 0
        pauseStartedAtMs = null

        _liveStats.value = RecordingLiveStats(
            activityId = activityId,
            activityType = activityType,
            isRecording = true,
            startTimeMs = now,
        )

        scope.launch {
            activityDao.upsert(
                ActivityEntity(
                    id = activityId,
                    activityType = activityType.dbValue,
                    startTime = now,
                    endTime = now, // provisional — overwritten by stop()
                    elapsedSeconds = 0,
                    movingSeconds = 0,
                    distanceMeters = 0.0,
                    elevationGainMeters = 0.0,
                    elevationLossMeters = 0.0,
                    averageSpeedMps = null,
                    movingAverageSpeedMps = null,
                    maxSpeedMps = null,
                    startLatitude = null,
                    startLongitude = null,
                    endLatitude = null,
                    endLongitude = null,
                    routePolyline = null,
                    title = null,
                    notes = null,
                    syncStatus = SyncStatus.LOCAL.dbValue,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        LocationSampleBus.subscribe(locationListener)
        return activityId
    }

    /**
     * Explicit pause: tears down GPS sampling (the service, told via
     * ACTION_PAUSE, actually stops requesting location updates — this just
     * stops listening on the bus and marks the pause start) so elapsed time
     * and the moving/stopped split both exclude it, unlike an implicit stop
     * while still recording (spec: pause is a deliberate "I'm done for now,
     * for a bit" signal, not the same thing as coasting to a stoplight).
     */
    fun pause() {
        val stats = _liveStats.value
        if (!stats.isRecording || stats.isPaused) return
        pauseStartedAtMs = System.currentTimeMillis()
        LocationSampleBus.unsubscribe(locationListener)
        _liveStats.value = stats.copy(isPaused = true, pauseStartedAtMs = pauseStartedAtMs)
    }

    fun resume() {
        val stats = _liveStats.value
        if (!stats.isRecording || !stats.isPaused) return
        val startedAt = pauseStartedAtMs ?: return
        accumulatedPausedMs += System.currentTimeMillis() - startedAt
        pauseStartedAtMs = null
        LocationSampleBus.subscribe(locationListener)
        _liveStats.value = stats.copy(isPaused = false, pausedMs = accumulatedPausedMs, pauseStartedAtMs = null)
    }

    private fun onLocation(activityId: String, location: Location) {
        if (activityId != _liveStats.value.activityId) return // stale callback from a just-stopped activity

        val point = location.toGpsPoint()
        currentPoints += point

        scope.launch {
            gpsPointDao.insert(location.toGpsPointEntity(activityId))
        }

        // Re-derive live distance/speed from the cleaned buffer rather than
        // keeping a running total by hand — cheap at recording-session scale
        // (a multi-hour ride at the service's 3s sampling interval is a few
        // thousand points), and guarantees the live number matches what
        // stop() will compute from the same pipeline.
        val cleaned = GpsProcessor.cleanPoints(currentPoints)
        val last2 = cleaned.takeLast(2)
        val derivedSpeed = if (last2.size == 2) {
            val dtSeconds = (last2[1].timestampMs - last2[0].timestampMs) / 1000.0
            if (dtSeconds > 0) {
                GpsProcessor.haversineMeters(last2[0].latitude, last2[0].longitude, last2[1].latitude, last2[1].longitude) / dtSeconds
            } else 0.0
        } else 0.0

        _liveStats.value = _liveStats.value.copy(
            distanceMeters = GpsProcessor.totalDistanceMeters(cleaned),
            currentSpeedMps = if (location.hasSpeed()) location.speed.toDouble() else derivedSpeed,
            pointCount = cleaned.size,
        )
    }

    /**
     * Stops listening and re-runs the full GPS pipeline over everything
     * persisted for this activity in Room (not just the in-memory buffer,
     * so a process death mid-ride still finalizes correctly on next
     * launch), then writes the final summary row. Returns the finalized
     * activity, or null if nothing was recording.
     */
    suspend fun stop(): ActivityEntity? {
        val stats = _liveStats.value
        val activityId = stats.activityId ?: return null
        val startTime = stats.startTimeMs ?: return null

        LocationSampleBus.unsubscribe(locationListener)
        // Total paused duration includes any pause still in progress at stop time (e.g. user hit
        // Stop while paused rather than Resume first) — finalize it here rather than requiring resume().
        val totalPausedMs = stats.pausedMs + (stats.pauseStartedAtMs?.let { System.currentTimeMillis() - it } ?: 0L)
        _liveStats.value = RecordingLiveStats() // reset immediately so late/stale callbacks are ignored
        accumulatedPausedMs = 0
        pauseStartedAtMs = null

        val persistedPoints = gpsPointDao.getForActivity(activityId).map { it.toGpsPoint() }
        val cleaned = GpsProcessor.cleanPoints(persistedPoints)
        val endTime = System.currentTimeMillis()
        val elapsedSeconds = ((endTime - startTime - totalPausedMs) / 1000).coerceAtLeast(0)
        val movingSeconds = GpsProcessor.movingSeconds(cleaned)
        val distance = GpsProcessor.totalDistanceMeters(cleaned)
        val speed = GpsProcessor.speedStats(cleaned, distance, elapsedSeconds, movingSeconds)
        val elevation = GpsProcessor.elevationStats(cleaned)

        val existing = activityDao.getById(activityId) ?: return null
        val finalized = existing.copy(
            endTime = endTime,
            elapsedSeconds = elapsedSeconds,
            movingSeconds = movingSeconds,
            distanceMeters = distance,
            elevationGainMeters = elevation.gainMeters,
            elevationLossMeters = elevation.lossMeters,
            averageSpeedMps = speed.averageMps,
            movingAverageSpeedMps = speed.movingAverageMps,
            maxSpeedMps = speed.maxMps,
            startLatitude = cleaned.firstOrNull()?.latitude,
            startLongitude = cleaned.firstOrNull()?.longitude,
            endLatitude = cleaned.lastOrNull()?.latitude,
            endLongitude = cleaned.lastOrNull()?.longitude,
            // routePolyline stays null here — RDP simplification is a
            // separate, already-documented Phase 2 gap (docs/android.md),
            // not part of getting recording persistence itself working.
            syncStatus = SyncStatus.PENDING_SYNC.dbValue,
            updatedAt = endTime,
        )
        activityDao.update(finalized)
        currentPoints.clear()
        return finalized
    }

    /** Discards the in-progress activity entirely (e.g. an accidental start) — deletes the draft row and its points. */
    suspend fun discard() {
        val activityId = _liveStats.value.activityId ?: return
        LocationSampleBus.unsubscribe(locationListener)
        _liveStats.value = RecordingLiveStats()
        accumulatedPausedMs = 0
        pauseStartedAtMs = null
        gpsPointDao.deleteForActivity(activityId)
        activityDao.delete(activityId)
        currentPoints.clear()
    }
}

private fun Location.toGpsPoint() = GpsPoint(
    timestampMs = time,
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = if (hasAltitude()) altitude else null,
    speedMps = if (hasSpeed()) speed else null,
    accuracyMeters = if (hasAccuracy()) accuracy else null,
    headingDegrees = if (hasBearing()) bearing else null,
)

private fun Location.toGpsPointEntity(activityId: String) = GpsPointEntity(
    activityId = activityId,
    timestamp = time,
    latitude = latitude,
    longitude = longitude,
    altitude = if (hasAltitude()) altitude else null,
    speed = if (hasSpeed()) speed else null,
    accuracy = if (hasAccuracy()) accuracy else null,
    heading = if (hasBearing()) bearing else null,
)

private fun GpsPointEntity.toGpsPoint() = GpsPoint(
    timestampMs = timestamp,
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = altitude,
    speedMps = speed,
    accuracyMeters = accuracy,
    headingDegrees = heading,
)
