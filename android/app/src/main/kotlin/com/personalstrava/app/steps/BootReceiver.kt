package com.personalstrava.app.steps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts step tracking after a reboot, but only when the user has actually opted into
 *  "always on" (see ProfileScreen/StepPreferences) — a plain install never gets this receiver
 *  doing anything, since Android also won't deliver BOOT_COMPLETED to an app that has never
 *  been opened once anyway. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!StepPreferences(context).isAlwaysOnEnabled) return
        context.startForegroundService(Intent(context, StepTrackingService::class.java))
    }
}
