package com.personalstrava.app.ui.profile

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personalstrava.app.PersonalStravaApp
import com.personalstrava.app.steps.StepPreferences
import com.personalstrava.app.steps.StepTrackingService
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ProfileUiState(
    val loading: Boolean = true,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val uploadingAvatar: Boolean = false,
    val saved: Boolean = false,
    val alwaysTrackSteps: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the account/profile screen — spec's "proper profile" ask. The
 * `profiles` row this reads/writes already exists per-user (created by the
 * `handle_new_user` trigger on first Google sign-in, see
 * supabase/migrations/0001_init_schema.sql), pre-filled from the Google
 * account's name/photo — this screen is what lets the user override that
 * (a display name they'd rather use, a custom avatar) rather than being
 * stuck with whatever Google reported.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>() as PersonalStravaApp
    private val supabase get() = app.supabase
    private val stepPrefs = StepPreferences(app)

    private val _uiState = MutableStateFlow(ProfileUiState(alwaysTrackSteps = stepPrefs.isAlwaysOnEnabled))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Called once the caller has confirmed ACTIVITY_RECOGNITION is granted (API 29+) — see
     *  ProfileScreen's permission launcher, which requests it before ever calling this with true. */
    fun setAlwaysTrackSteps(enabled: Boolean) {
        stepPrefs.isAlwaysOnEnabled = enabled
        _uiState.value = _uiState.value.copy(alwaysTrackSteps = enabled)
        val intent = Intent(app, StepTrackingService::class.java)
        if (enabled) app.startForegroundService(intent) else app.stopService(intent)
    }

    fun load() {
        viewModelScope.launch {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val profile = withContext(Dispatchers.IO) {
                    supabase.postgrest["profiles"]
                        .select(columns = Columns.list("id, display_name, avatar_url")) {
                            filter { eq("id", userId) }
                        }
                        .decodeSingleOrNull<ProfileDto>()
                }
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    displayName = profile?.displayName.orEmpty(),
                    avatarUrl = profile?.avatarUrl,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loading = false, error = e.message ?: "Couldn't load profile")
            }
        }
    }

    fun updateDisplayNameDraft(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value, saved = false)
    }

    fun save() {
        viewModelScope.launch {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
            val name = _uiState.value.displayName.trim().ifBlank { null }
            try {
                withContext(Dispatchers.IO) {
                    supabase.postgrest["profiles"].update(
                        { set("display_name", name) },
                    ) {
                        filter { eq("id", userId) }
                    }
                }
                _uiState.value = _uiState.value.copy(saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Couldn't save")
            }
        }
    }

    /** Uploads the picked image to the (public-read) `avatars` bucket at `{userId}/avatar.jpg`
     *  (overwriting any previous avatar via upsert), then points the profile row at its public URL. */
    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
            _uiState.value = _uiState.value.copy(uploadingAvatar = true, error = null)
            try {
                val bytes = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: throw IllegalStateException("Couldn't read the selected image")

                val path = "$userId/avatar.jpg"
                withContext(Dispatchers.IO) {
                    supabase.storage.from("avatars").upload(path, bytes) { upsert = true }
                }
                val publicUrl = supabase.storage.from("avatars").publicUrl(path)
                // Cache-bust so the new avatar shows immediately instead of an old cached image at the same URL.
                val bustedUrl = "$publicUrl?t=${System.currentTimeMillis()}"

                withContext(Dispatchers.IO) {
                    supabase.postgrest["profiles"].update(
                        { set("avatar_url", bustedUrl) },
                    ) {
                        filter { eq("id", userId) }
                    }
                }
                _uiState.value = _uiState.value.copy(uploadingAvatar = false, avatarUrl = bustedUrl)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(uploadingAvatar = false, error = e.message ?: "Couldn't upload photo")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { supabase.auth.signOut() }
    }
}

@Serializable
private data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
