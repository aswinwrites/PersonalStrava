package com.personalstrava.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personalstrava.app.PersonalStravaApp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    /** Still resolving the initial session on process start — show nothing (or a splash) rather than flashing sign-in. */
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data object SignedIn : AuthUiState
}

/**
 * Mirrors the web's AuthProvider (web/src/features/auth/AuthProvider.tsx):
 * same Supabase Google OAuth provider, same user_id on both clients (spec
 * section 9). The browser-based OAuth flow redirects back via
 * personalstrava://auth-callback (declared on MainActivity), which the
 * supabase-kt Auth plugin picks up once MainActivity forwards the intent to
 * `supabase.handleDeeplinks(intent)` — see MainActivity.onCreate/onNewIntent.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val supabase = (getApplication<Application>() as PersonalStravaApp).supabase

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            supabase.auth.sessionStatus.collect { status ->
                _uiState.value = when (status) {
                    is SessionStatus.Authenticated -> AuthUiState.SignedIn
                    is SessionStatus.NotAuthenticated -> AuthUiState.SignedOut
                    is SessionStatus.Initializing -> AuthUiState.Loading
                    is SessionStatus.RefreshFailure -> AuthUiState.SignedOut
                }
            }
        }
    }

    fun signInWithGoogle(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Google)
            } catch (e: Exception) {
                onError(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            supabase.auth.signOut()
        }
    }
}
