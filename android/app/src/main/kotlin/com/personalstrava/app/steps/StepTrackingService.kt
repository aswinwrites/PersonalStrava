package com.personalstrava.app.steps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.personalstrava.app.MainActivity
import com.personalstrava.app.R
import com.personalstrava.app.data.local.AppDatabase
import com.personalstrava.app.data.local.entity.DailyStatsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The "always on" step tracking ask: rather than relying on whatever (if
 * anything) is writing to Health Connect — on plenty of phones nothing is,
 * so HomeViewModel's Health Connect read silently shows nothing useful —
 * this reads the device's own TYPE_STEP_COUNTER hardware sensor directly
 * and persists today's count into daily_stats itself, independent of any
 * other app. Opt-in only (see ProfileScreen's toggle); never starts on its
 * own without the user turning it on, and re-starts after a reboot only
 * because the user asked for "always on" specifically (see BootReceiver).
 */
class StepTrackingService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var prefs: StepPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        prefs = StepPreferences(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor == null) {
            // Device has no step-counter hardware — nothing this service can do; stop cleanly
            // rather than sit in the foreground burning battery for no benefit.
            stopSelf()
            return START_NOT_STICKY
        }
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val totalSinceBoot = event.values.firstOrNull() ?: return
        val baseline = prefs.rebaselineIfNeeded(totalSinceBoot)
        val todaySteps = (totalSinceBoot - baseline).toInt().coerceAtLeast(0)

        scope.launch {
            val dao = AppDatabase.getInstance(applicationContext).dailyStatsDao()
            val date = LocalDate.now().toString()
            val existing = dao.getByDate(date) ?: DailyStatsEntity(date = date)
            dao.upsert(existing.copy(steps = todaySteps, updatedAt = System.currentTimeMillis()))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nothing to react to — TYPE_STEP_COUNTER's accuracy field isn't meaningful the way it is
        // for e.g. the magnetometer.
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Step tracking", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking your steps")
            .setContentText("Always-on step tracking is on — tap to open Telemetry")
            .setSmallIcon(R.drawable.ic_recording)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 1002
    }
}
