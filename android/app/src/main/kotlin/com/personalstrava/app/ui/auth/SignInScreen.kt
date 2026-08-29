package com.personalstrava.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Shown whenever there's no authenticated Supabase session. Signing in here
 * is what unlocks sync — without it, RecordingRepository still records
 * fine locally (Room only), but SyncManager.syncPending() is a no-op (it
 * bails out early with no session, see SyncManager) and nothing reaches the
 * web dashboard. That's an intentional degrade, not a crash: recording
 * never depends on being signed in.
 */
@Composable
fun SignInScreen(viewModel: AuthViewModel = viewModel()) {
    var isSigningIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Track Metrics", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Sign in with the same Google account you use on the web dashboard, so your rides sync there.",
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        if (isSigningIn) {
            CircularProgressIndicator()
        } else {
            Button(onClick = {
                isSigningIn = true
                error = null
                viewModel.signInWithGoogle { message ->
                    isSigningIn = false
                    error = message
                }
            }) {
                Text("Continue with Google")
            }
        }
        error?.let {
            Text(text = it, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
