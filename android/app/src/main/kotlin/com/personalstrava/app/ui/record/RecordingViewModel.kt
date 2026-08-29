package com.personalstrava.app.ui.record

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personalstrava.app.PersonalStravaApp
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.domain.model.ActivityType
import com.personalstrava.app.record.ActivityRecordingService
import com.personalstrava.app.record.RecordingRepository
import com.personalstrava.app.sync.SyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecordingUiState(
    val activityType: ActivityType? = null,
    val isRecording: Boolean = false,
    val elapsedSeconds: Long = 0,
    val distanceMeters: Double = 0.0,
    val currentSpeedMps: Double = 0.0,
    val isPaused: Boolean = false,
    /** Set for one emission right after stop() finalizes — RecordingScreen
     *  consumes it (navigates back) then clears it via consumedFinished(). */
    val justFinished: ActivityEntity? = null,
)

/**
 * Wires the recording screen to [RecordingRepository] (Room writes + live
 * distance/speed) and to starting/stopping [ActivityRecordingService]
 * itself. The repository is owned by the Application, not this ViewModel,
 * so a rotation never interrupts an in-progress recording — this class
 * only ever re-attaches to whatever's already running there.
 */
class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RecordingRepository
        get() = (getApplication<Application>() as PersonalStravaApp).recordingRepository

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.liveStats.collect { live ->
                _uiState.value = _uiState.value.copy(
                    activityType = live.activityType,
                    isRecording = live.isRecording,
                    distanceMeters = live.distanceMeters,
                    currentSpeedMps = live.currentSpeedMps,
                    isPaused = live.isPaused,
                )
            }
        }
        // Elapsed time is wall-clock, not GPS-point-derived, so it keeps
        // ticking even while stopped at a light with no new fixes coming in —
        // but excludes any time spent explicitly paused (both time already
        // accumulated across past pause/resume cycles, and time in the
        // *current* pause if one is in progress right now).
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val live = repository.liveStats.value
                val start = live.startTimeMs
                if (start != null) {
                    val pausedSoFar = live.pausedMs + (live.pauseStartedAtMs?.let { System.currentTimeMillis() - it } ?: 0L)
                    val elapsedMs = (System.currentTimeMillis() - start - pausedSoFar).coerceAtLeast(0)
                    _uiState.value = _uiState.value.copy(elapsedSeconds = elapsedMs / 1000, isPaused = live.isPaused)
                }
            }
        }
    }

    /** Call only after location (+ background location, + notifications on API 33+) are already granted — see RecordingScreen's permission gate. */
    fun start(activityType: ActivityType) {
        val activityId = repository.start(activityType)
        val context = getApplication<Application>()
        val intent = Intent(context, ActivityRecordingService::class.java)
            .putExtra(ActivityRecordingService.EXTRA_ACTIVITY_ID, activityId)
        context.startForegroundService(intent)
    }

    /** Pauses GPS sampling (service is told via ACTION_PAUSE) and the repository's own accounting together, so the two never drift. */
    fun pause() {
        val context = getApplication<Application>()
        context.startService(Intent(context, ActivityRecordingService::class.java).setAction(ActivityRecordingService.ACTION_PAUSE))
        repository.pause()
    }

    fun resume() {
        val context = getApplication<Application>()
        context.startService(Intent(context, ActivityRecordingService::class.java).setAction(ActivityRecordingService.ACTION_RESUME))
        repository.resume()
    }

    fun stop() {
        viewModelScope.launch {
            stopServiceInternal()
            val finalized = repository.stop()
            _uiState.value = RecordingUiState(justFinished = finalized)
            // Fire a one-off sync immediately so a finished ride reaches the
            // web dashboard within seconds rather than waiting for the next
            // 15-minute periodic tick — a no-op if there's no session or no
            // network right now (WorkManager just retries/queues it).
            SyncWorker.enqueueOneTime(getApplication())
        }
    }

    fun discard() {
        viewModelScope.launch {
            stopServiceInternal()
            repository.discard()
            _uiState.value = RecordingUiState()
        }
    }

    fun consumedFinished() {
        _uiState.value = _uiState.value.copy(justFinished = null)
    }

    private fun stopServiceInternal() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, ActivityRecordingService::class.java))
    }
}
