package com.personalstrava.app.steps

import android.content.Context
import java.time.LocalDate

/**
 * Tiny SharedPreferences wrapper — the app's first use of raw prefs (everything
 * else so far is Room) because this is genuinely just two small persistent
 * values, not a table: whether the user has opted into always-on step
 * tracking, and the step-counter baseline StepTrackingService resets once a
 * day. Kept out of Room since it's device-local state about the sensor
 * itself, not data that ever needs to sync.
 */
class StepPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("step_tracking", Context.MODE_PRIVATE)

    var isAlwaysOnEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** TYPE_STEP_COUNTER reports steps cumulative since the device's last boot, never resetting
     *  itself at midnight — so "today's steps" is (current sensor value) - (value it read at the
     *  start of today), and that baseline is what this persists. */
    var baselineDate: String?
        get() = prefs.getString(KEY_BASELINE_DATE, null)
        set(value) = prefs.edit().putString(KEY_BASELINE_DATE, value).apply()

    var baselineCount: Float
        get() = prefs.getFloat(KEY_BASELINE_COUNT, -1f)
        set(value) = prefs.edit().putFloat(KEY_BASELINE_COUNT, value).apply()

    /** Resets the baseline to [totalSinceBoot] for today if the stored baseline is missing or stale
     *  (a new calendar day, or the device rebooted and the counter itself reset to a smaller value). */
    fun rebaselineIfNeeded(totalSinceBoot: Float): Float {
        val today = LocalDate.now().toString()
        val storedDate = baselineDate
        val storedBaseline = baselineCount
        val needsReset = storedDate != today || storedBaseline < 0f || totalSinceBoot < storedBaseline
        if (needsReset) {
            baselineDate = today
            baselineCount = totalSinceBoot
            return totalSinceBoot
        }
        return storedBaseline
    }

    companion object {
        private const val KEY_ENABLED = "always_on_enabled"
        private const val KEY_BASELINE_DATE = "baseline_date"
        private const val KEY_BASELINE_COUNT = "baseline_count"
    }
}
