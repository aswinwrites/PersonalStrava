package com.personalstrava.app.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.domain.model.ActivityType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The shareable end-of-ride card (the "similar to Strava" ask). Deliberately
 * a stats-only card, no route map: rendering a static map image would need
 * either a maps SDK snapshot API (a paid/quota'd dependency for a
 * single-user app) or hand-drawing the polyline ourselves — real work
 * that's a natural fast-follow once routePolyline is actually populated
 * (see RecordingRepository's own note — it's null today, that gap predates
 * this feature). This card is intentionally the fast, ships-today version.
 */
private val ACTIVITY_COLOR = mapOf(
    ActivityType.WALKING to Color(0xFF2F9E6F),
    ActivityType.JOGGING to Color(0xFFF2B134),
    ActivityType.CYCLING to Color(0xFFFF6A3D),
    ActivityType.MOTORCYCLING to Color(0xFF3D6BFF),
)

private val ACTIVITY_LABEL = mapOf(
    ActivityType.WALKING to "Walk",
    ActivityType.JOGGING to "Jog",
    ActivityType.CYCLING to "Ride",
    ActivityType.MOTORCYCLING to "Ride",
)

@Composable
fun ShareCard(activity: ActivityEntity, width: androidx.compose.ui.unit.Dp = 360.dp) {
    val type = ActivityType.fromDbValue(activity.activityType)
    val accent = ACTIVITY_COLOR[type] ?: Color(0xFF7C3AED)

    Column(
        modifier = Modifier
            .width(width)
            .background(Color(0xFF0B0B0C))
            .padding(28.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column {
                Text(
                    text = activity.title?.takeIf { it.isNotBlank() } ?: "${ACTIVITY_LABEL[type]} · ${formatDate(activity.startTime)}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatDate(activity.startTime),
                    color = Color(0xFF9A9793),
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBlock(label = "DISTANCE", value = "%.2f km".format(activity.distanceMeters / 1000), accent = accent)
            StatBlock(label = "MOVING TIME", value = formatElapsed(activity.movingSeconds), accent = accent)
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val avgSpeed = activity.movingAverageSpeedMps
            StatBlock(
                label = if (type == ActivityType.WALKING || type == ActivityType.JOGGING) "AVG PACE" else "AVG SPEED",
                value = if (avgSpeed != null && avgSpeed > 0) formatSpeedOrPace(avgSpeed, type) else "—",
                accent = accent,
            )
            StatBlock(
                label = "ELEVATION",
                value = "+${activity.elevationGainMeters.toInt()} m",
                accent = accent,
            )
        }

        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(text = "Telemetry", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, accent: Color) {
    Column {
        Text(text = value, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Color(0xFF9A9793), fontSize = 11.sp, letterSpacing = 1.sp)
    }
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(epochMs))

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Walking/jogging read better as pace (min/km); cycling/motorcycling as speed (km/h) — same convention RecordingScreen already uses for live speed. */
private fun formatSpeedOrPace(mps: Double, type: ActivityType): String {
    if (type == ActivityType.WALKING || type == ActivityType.JOGGING) {
        val secPerKm = 1000.0 / mps
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).toInt()
        return "%d:%02d /km".format(min, sec)
    }
    return "%.1f km/h".format(mps * 3.6)
}
