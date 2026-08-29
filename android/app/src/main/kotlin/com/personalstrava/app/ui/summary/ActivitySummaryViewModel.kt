package com.personalstrava.app.ui.summary

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personalstrava.app.PersonalStravaApp
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.data.local.entity.PhotoEntity
import com.personalstrava.app.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivitySummaryUiState(
    val activity: ActivityEntity? = null,
    val photos: List<PhotoEntity> = emptyList(),
    val titleDraft: String = "",
    val notesDraft: String = "",
)

/**
 * Backs the screen shown right after a recording finishes — the natural
 * place to title the ride, jot a note, and attach a couple of photos while
 * it's fresh, rather than a separate "edit activity" flow the user has to
 * remember to go find later.
 */
class ActivitySummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>() as PersonalStravaApp
    private val activityDao get() = app.database.activityDao()

    private val _uiState = MutableStateFlow(ActivitySummaryUiState())
    val uiState: StateFlow<ActivitySummaryUiState> = _uiState.asStateFlow()

    private var loadedActivityId: String? = null

    fun load(activityId: String) {
        if (loadedActivityId == activityId) return
        loadedActivityId = activityId

        viewModelScope.launch {
            val activity = activityDao.getById(activityId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                activity = activity,
                titleDraft = activity.title ?: "",
                notesDraft = activity.notes ?: "",
            )
        }
        viewModelScope.launch {
            app.photoRepository.observeForActivity(activityId).collect { photos ->
                _uiState.value = _uiState.value.copy(photos = photos)
            }
        }
    }

    fun updateTitleDraft(value: String) {
        _uiState.value = _uiState.value.copy(titleDraft = value)
    }

    fun updateNotesDraft(value: String) {
        _uiState.value = _uiState.value.copy(notesDraft = value)
    }

    /** Persists title/notes and kicks a one-off sync so the edit (and any new photos) reach the dashboard promptly. */
    fun save() {
        val activity = _uiState.value.activity ?: return
        viewModelScope.launch {
            val updated = activity.copy(
                title = _uiState.value.titleDraft.trim().ifBlank { null },
                notes = _uiState.value.notesDraft.trim().ifBlank { null },
                updatedAt = System.currentTimeMillis(),
            )
            activityDao.update(updated)
            _uiState.value = _uiState.value.copy(activity = updated)
            SyncWorker.enqueueOneTime(app)
        }
    }

    fun addPhotos(uris: List<Uri>) {
        val activityId = loadedActivityId ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            app.photoRepository.addPhotos(activityId, uris)
            SyncWorker.enqueueOneTime(app)
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            app.photoRepository.deletePhoto(photo)
        }
    }
}
