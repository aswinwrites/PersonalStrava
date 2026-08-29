package com.personalstrava.app.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalstrava.app.domain.model.ActivityType

/**
 * Permission sequencing follows docs/android.md "Permissions": fine
 * location first, then (API 29+) background location as a separate
 * request — Android refuses to bundle the two on modern versions — then
 * (API 33+) POST_NOTIFICATIONS for the foreground-service notification.
 * This covers exactly the one flow (starting a recording); the general
 * PermissionFlow orchestrator docs/android.md scopes for later (tying all
 * four permission triggers app-wide to their own screens) is still Phase 2.
 */
@Composable
fun RecordingScreen(
    activityType: ActivityType,
    onFinished: (activityId: String?) -> Unit,
    viewModel: RecordingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    var hasLocation by remember { mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var hasBackgroundLocation by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        )
    }
    var hasNotifications by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifications = granted
    }
    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasBackgroundLocation = granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocation = granted
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications) {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val allGranted = hasLocation && hasBackgroundLocation && hasNotifications

    LaunchedEffect(state.justFinished) {
        val finished = state.justFinished
        if (finished != null) {
            viewModel.consumedFinished()
            onFinished(finished.id)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!state.isRecording) {
            Text(
                text = readyLabel(activityType),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(16.dp))
            if (!allGranted) {
                Text(
                    text = "Location access is needed to record your route. You'll be asked for background access too, so tracking keeps running with the screen off.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Button(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                    Text("Grant permissions")
                }
            } else {
                Button(onClick = { viewModel.start(activityType) }) {
                    Text(startButtonLabel(activityType))
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { onFinished(null) }) { Text("Cancel") }
        } else {
            Text(text = formatElapsed(state.elapsedSeconds), fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(text = if (state.isPaused) "PAUSED" else "ELAPSED", fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            Text(text = "%.2f km".format(state.distanceMeters / 1000), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(text = "DISTANCE", fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            Text(text = "%.1f km/h".format(state.currentSpeedMps * 3.6), fontSize = 20.sp)
            Text(text = "CURRENT SPEED", fontSize = 12.sp)
            Spacer(Modifier.height(32.dp))
            if (state.isPaused) {
                Button(onClick = { viewModel.resume() }) { Text("RESUME") }
            } else {
                Button(onClick = { viewModel.pause() }) { Text("PAUSE") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewModel.stop() }) { Text("STOP") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.discard(); onFinished(null) }) { Text("Discard") }
        }
    }
}

private fun readyLabel(activityType: ActivityType): String = when (activityType) {
    ActivityType.WALKING -> "Ready to walk"
    ActivityType.JOGGING -> "Ready to jog"
    ActivityType.CYCLING -> "Ready to cycle"
    ActivityType.MOTORCYCLING -> "Ready to ride"
}

private fun startButtonLabel(activityType: ActivityType): String = when (activityType) {
    ActivityType.WALKING -> "\uD83D\uDEB6 START WALKING"
    ActivityType.JOGGING -> "\uD83C\uDFC3 START JOGGING"
    ActivityType.CYCLING -> "\uD83D\uDEB4 START CYCLING"
    ActivityType.MOTORCYCLING -> "\uD83C\uDFCD\uFE0F START MOTORCYCLE"
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
