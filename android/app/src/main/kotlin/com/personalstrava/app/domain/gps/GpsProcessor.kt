package com.personalstrava.app.domain.gps

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cleans a raw GPS track and derives activity metrics. Formulas are
 * documented inline and mirrored in docs/architecture.md ("GPS Processing")
 * — keep the two in sync if either changes.
 *
 * Pipeline (spec section 17):
 *   raw points -> filter -> sort/dedupe -> [distance, moving time, speed, elevation]
 */
object GpsProcessor {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    // --- Filtering thresholds -------------------------------------------------
    /** GPS fixes worse than this accuracy radius are dropped outright. */
    const val MAX_ACCEPTABLE_ACCURACY_METERS = 30f

    /** A point implying faster than this instantaneous speed vs. the previous
     *  accepted point is treated as a GPS jump and dropped, not just capped —
     *  capping would silently understate max speed instead of rejecting bad data. */
    const val MAX_PLAUSIBLE_SPEED_MPS = 55.0 // ~198 km/h; generous ceiling for motorcycling

    /** Below this speed the point counts as "stopped" for moving-time purposes. */
    const val MOVING_SPEED_THRESHOLD_MPS = 0.5

    /** Elevation smoothing window (simple moving average) — see [smoothElevation]. */
    private const val ELEVATION_SMOOTHING_WINDOW = 5

    /**
     * Filters raw points for: impossible coordinates, poor accuracy, duplicate
     * timestamps, and GPS jumps (speed spikes between consecutive accepted
     * points). Returns points sorted by timestamp.
     */
    fun cleanPoints(rawPoints: List<GpsPoint>): List<GpsPoint> {
        val sorted = rawPoints
            .filter { isPlausibleCoordinate(it.latitude, it.longitude) }
            .filter { it.accuracyMeters == null || it.accuracyMeters <= MAX_ACCEPTABLE_ACCURACY_METERS }
            .sortedBy { it.timestampMs }

        val deduped = mutableListOf<GpsPoint>()
        for (point in sorted) {
            if (deduped.isNotEmpty() && deduped.last().timestampMs == point.timestampMs) continue // duplicate timestamp
            deduped += point
        }

        val cleaned = mutableListOf<GpsPoint>()
        for (point in deduped) {
            val previous = cleaned.lastOrNull()
            if (previous == null) {
                cleaned += point
                continue
            }
            val dtSeconds = (point.timestampMs - previous.timestampMs) / 1000.0
            if (dtSeconds <= 0) continue
            val distance = haversineMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
            val impliedSpeed = distance / dtSeconds
            if (impliedSpeed > MAX_PLAUSIBLE_SPEED_MPS) continue // GPS jump — drop, don't cap
            cleaned += point
        }

        return cleaned
    }

    private fun isPlausibleCoordinate(lat: Double, lng: Double): Boolean =
        lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0) // (0,0) is a common GPS-fault sentinel

    /**
     * Great-circle distance between two coordinates via the Haversine formula:
     *   a = sin²(Δφ/2) + cos(φ1)·cos(φ2)·sin²(Δλ/2)
     *   c = 2·atan2(√a, √(1−a))
     *   d = R·c
     * Good enough at activity-track scale (sub-meter error is irrelevant once
     * summed over a multi-km route); we don't need Vincenty's ellipsoid model.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)

        val a = sin(dPhi / 2).let { it * it } +
            cos(phi1) * cos(phi2) * sin(dLambda / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /** Sum of consecutive haversine segment distances across a cleaned track. */
    fun totalDistanceMeters(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
        }
        return total
    }

    /**
     * Moving time = sum of the time deltas between consecutive points where
     * the segment's implied speed is >= [MOVING_SPEED_THRESHOLD_MPS]. This is
     * a distance/time-derived speed, not the (noisier) instantaneous GPS
     * speed field, so a genuinely stationary phone with GPS drift doesn't get
     * counted as "moving."
     */
    fun movingSeconds(points: List<GpsPoint>): Long {
        if (points.size < 2) return 0L
        var seconds = 0L
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dtSeconds = (curr.timestampMs - prev.timestampMs) / 1000.0
            if (dtSeconds <= 0) continue
            val segmentDistance = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
            val impliedSpeed = segmentDistance / dtSeconds
            if (impliedSpeed >= MOVING_SPEED_THRESHOLD_MPS) {
                seconds += dtSeconds.toLong()
            }
        }
        return seconds
    }

    data class SpeedStats(val averageMps: Double, val movingAverageMps: Double, val maxMps: Double)

    fun speedStats(points: List<GpsPoint>, totalDistance: Double, elapsedSeconds: Long, movingSeconds: Long): SpeedStats {
        val average = if (elapsedSeconds > 0) totalDistance / elapsedSeconds else 0.0
        val movingAverage = if (movingSeconds > 0) totalDistance / movingSeconds else 0.0
        // Max speed from the GPS speed field where present (device fused speed
        // is typically smoother/more accurate than a derived per-segment
        // speed), falling back to derived segment speed otherwise.
        var max = 0.0
        for (i in points.indices) {
            val reported = points[i].speedMps?.toDouble()
            if (reported != null && reported <= MAX_PLAUSIBLE_SPEED_MPS) {
                max = maxOf(max, reported)
            } else if (i > 0) {
                val dt = (points[i].timestampMs - points[i - 1].timestampMs) / 1000.0
                if (dt > 0) {
                    val d = haversineMeters(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
                    max = maxOf(max, d / dt)
                }
            }
        }
        return SpeedStats(average, movingAverage, max)
    }

    /**
     * Elevation gain/loss with a simple moving-average smoothing pass first
     * (window = [ELEVATION_SMOOTHING_WINDOW]) to reduce barometer/GPS altitude
     * noise, then sums only the positive/negative deltas between consecutive
     * smoothed samples. A noise floor of 1m per step avoids counting sensor
     * jitter as real elevation change.
     */
    data class ElevationStats(val gainMeters: Double, val lossMeters: Double)

    fun elevationStats(points: List<GpsPoint>): ElevationStats {
        val altitudes = points.mapNotNull { it.altitudeMeters }
        if (altitudes.size < 2) return ElevationStats(0.0, 0.0)

        val smoothed = smoothElevation(altitudes)
        var gain = 0.0
        var loss = 0.0
        val noiseFloorMeters = 1.0
        for (i in 1 until smoothed.size) {
            val delta = smoothed[i] - smoothed[i - 1]
            if (abs(delta) < noiseFloorMeters) continue
            if (delta > 0) gain += delta else loss += -delta
        }
        return ElevationStats(gain, loss)
    }

    private fun smoothElevation(altitudes: List<Double>): List<Double> {
        val half = ELEVATION_SMOOTHING_WINDOW / 2
        return altitudes.indices.map { i ->
            val from = maxOf(0, i - half)
            val to = minOf(altitudes.lastIndex, i + half)
            altitudes.subList(from, to + 1).average()
        }
    }
}
