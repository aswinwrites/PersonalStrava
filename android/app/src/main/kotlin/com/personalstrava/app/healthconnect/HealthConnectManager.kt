package com.personalstrava.app.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Reads (never writes/fabricates) step data from Health Connect and
 * normalizes it into the app's daily_stats model (spec section 11). Only
 * the permissions actually used are requested — READ_STEPS today; distance
 * and exercise-session reads are wired the same way when those features
 * land, not requested speculatively up front.
 */
class HealthConnectManager(context: Context) {
    private val client: HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null // Health Connect not installed/available — rest of the app keeps working (spec section 46)
        }

    val isAvailable: Boolean get() = client != null

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    suspend fun hasPermissions(): Boolean {
        val client = client ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    /** Total steps for [day] (device-local midnight to midnight), or null if unavailable/unauthorized. */
    suspend fun readStepsForDay(day: java.time.LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int? {
        val client = client ?: return null
        if (!hasPermissions()) return null

        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )
        // Sum rather than take a single record: multiple sources (phone +
        // watch, or overlapping app writes) can each contribute a StepsRecord
        // for overlapping windows; summing raw counts can double-count in
        // that case. Phase 1 accepts that edge case and sums naively — a
        // dedup-by-source pass is a documented follow-up in docs/android.md.
        return response.records.sumOf { it.count.toInt() }
    }

    private fun Instant.toEpochDay(zone: java.time.ZoneId) = java.time.LocalDate.ofInstant(this, zone)
}
