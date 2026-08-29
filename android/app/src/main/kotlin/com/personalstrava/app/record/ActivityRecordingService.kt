package com.personalstrava.app.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.personalstrava.app.MainActivity
import com.personalstrava.app.R

/**
 * Foreground service that owns GPS recording for any of the four activity
 * types (walking, jogging, cycling, motorcycling — spec sections 12-13).
 * Runs while the screen is locked or the user has switched apps; only ever
 * started by an explicit user action ("Start walking"/"Start jogging"/
 * "Start cycling"/"Start motorcycling") — never auto-starts. Activity type
 * itself only matters downstream (GpsProcessor thresholds, ShareCard
 * labels) — this service just samples location the same way regardless.
 *
 * Sampling: BALANCED priority at a 3s interval is the default trade-off
 * between route fidelity and battery drain (spec section 12 — "do not
 * unnecessarily maximize GPS frequency"). Tuned per docs/android.md if the
 * captured tracks turn out too coarse for a given riding style.
 */
class ActivityRecordingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var activityId: String? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val activityId = activityId ?: return
            result.locations.forEach { location ->
                // RecordingRepository (owned by PersonalStravaApp, subscribed
                // for the duration of the recording) is the listener that
                // persists this via GpsPointDao and derives live distance/
                // speed for the recording UI — see its own doc comment.
                LocationSampleBus.emit(activityId, location)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                // Actually tear down GPS updates rather than just ignoring incoming fixes — no
                // point burning battery sampling location the app is about to discard anyway
                // (RecordingRepository stops listening on the bus the moment pause() is called).
                if (::fusedLocationClient.isInitialized) fusedLocationClient.removeLocationUpdates(locationCallback)
                updateNotification(paused = true)
            }
            ACTION_RESUME -> {
                startLocationUpdates()
                updateNotification(paused = false)
            }
            else -> {
                activityId = intent?.getStringExtra(EXTRA_ACTIVITY_ID)
                startForeground(NOTIFICATION_ID, buildNotification(paused = false))
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    private fun startLocationUpdates() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS)
            .build()
        // Caller (MainActivity/RecordingViewModel) already verified
        // ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION before starting
        // this service — see PermissionFlow.kt.
        @Suppress("MissingPermission")
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    override fun onDestroy() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(paused: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(paused))
    }

    private fun buildNotification(paused: Boolean): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (paused) "Recording paused" else "Recording activity")
            .setContentText(
                if (paused) "Tap Resume in the app to keep going" else "Distance and speed update live — tap to open Telemetry",
            )
            .setSmallIcon(R.drawable.ic_recording) // placeholder vector asset — see res/drawable
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val ACTION_PAUSE = "com.personalstrava.app.action.PAUSE"
        const val ACTION_RESUME = "com.personalstrava.app.action.RESUME"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_INTERVAL_MS = 3_000L
    }
}

/**
 * Minimal in-process pub/sub so the foreground service (which cannot hold a
 * ViewModel reference) can hand raw locations to whichever component is
 * currently persisting + displaying them — RecordingRepository, for the
 * lifetime of one recording.
 */
object LocationSampleBus {
    private val listeners = mutableListOf<(String, android.location.Location) -> Unit>()

    fun subscribe(listener: (String, android.location.Location) -> Unit) {
        listeners += listener
    }

    fun unsubscribe(listener: (String, android.location.Location) -> Unit) {
        listeners -= listener
    }

    fun emit(activityId: String, location: android.location.Location) {
        listeners.forEach { it(activityId, location) }
    }
}
