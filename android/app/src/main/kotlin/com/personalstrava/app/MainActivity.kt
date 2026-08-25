package com.personalstrava.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.personalstrava.app.ui.home.HomeScreen

/**
 * Single-activity Compose app. Opening PersonalStrava lands directly on
 * HomeScreen — today's steps plus the two record buttons front and center,
 * per spec section 38 ("never navigate through multiple screens to start a
 * ride"). Auth-gating and navigation between Home/History/Settings live in
 * ui/PersonalStravaNavHost (Phase 2, once more than one screen exists).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}
