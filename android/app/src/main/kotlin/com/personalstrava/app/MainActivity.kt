package com.personalstrava.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.personalstrava.app.sync.SyncWorker
import com.personalstrava.app.ui.PersonalStravaNavHost
import io.github.jan.supabase.auth.handleDeeplinks

/**
 * Single-activity Compose app. Opening PersonalStrava lands directly on
 * HomeScreen when signed in — today's steps plus the record buttons front
 * and center, per spec section 38 ("never navigate through multiple
 * screens to start a ride") — or on SignInScreen when it isn't; the
 * sign-in gate lives in [PersonalStravaNavHost], not here, so this class
 * stays focused on the two things only an Activity can do: forwarding the
 * OAuth redirect intent to supabase-kt, and enqueuing the periodic sync
 * worker once a session exists.
 *
 * launchMode="singleTask" (AndroidManifest) matters here: without it, the
 * personalstrava://auth-callback redirect after Google sign-in would spin
 * up a *second* MainActivity instance on top of the first instead of
 * delivering onNewIntent to the existing one, and handleDeeplinks would
 * never see it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PersonalStravaApp
        app.supabase.handleDeeplinks(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PersonalStravaNavHost(onSignedIn = { SyncWorker.enqueuePeriodic(applicationContext) })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val app = application as PersonalStravaApp
        app.supabase.handleDeeplinks(intent)
    }
}
