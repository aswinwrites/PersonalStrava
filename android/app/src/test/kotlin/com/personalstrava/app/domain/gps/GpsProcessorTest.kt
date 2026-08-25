package com.personalstrava.app.domain.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsProcessorTest {

    private fun point(tMs: Long, lat: Double, lng: Double, alt: Double? = null, speed: Float? = null, accuracy: Float? = 5f) =
        GpsPoint(timestampMs = tMs, latitude = lat, longitude = lng, altitudeMeters = alt, speedMps = speed, accuracyMeters = accuracy, headingDegrees = null)

    @Test
    fun `haversine distance between two known points is approximately correct`() {
        // Roughly 1 degree of latitude ~= 111.2 km at the equator.
        val d = GpsProcessor.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("expected ~111200m, got $d", abs(d - 111_195.0) < 500)
    }

    @Test
    fun `total distance sums consecutive segments`() {
        val points = listOf(
            point(0, 12.9716, 77.5946),
            point(1000, 12.9726, 77.5946),
            point(2000, 12.9736, 77.5946),
        )
        val total = GpsProcessor.totalDistanceMeters(points)
        val expected = GpsProcessor.haversineMeters(12.9716, 77.5946, 12.9726, 77.5946) +
            GpsProcessor.haversineMeters(12.9726, 77.5946, 12.9736, 77.5946)
        assertEquals(expected, total, 0.01)
    }

    @Test
    fun `cleanPoints drops points with poor accuracy`() {
        val points = listOf(
            point(0, 12.97, 77.59, accuracy = 5f),
            point(1000, 12.971, 77.591, accuracy = 200f), // too imprecise
            point(2000, 12.972, 77.592, accuracy = 8f),
        )
        val cleaned = GpsProcessor.cleanPoints(points)
        assertEquals(2, cleaned.size)
    }

    @Test
    fun `cleanPoints drops GPS jumps that imply implausible speed`() {
        val points = listOf(
            point(0, 12.9716, 77.5946),
            // ~11km away one second later => implausible speed, should be dropped
            point(1000, 13.0716, 77.5946),
            point(2000, 12.9718, 77.5947),
        )
        val cleaned = GpsProcessor.cleanPoints(points)
        assertEquals(2, cleaned.size)
        assertEquals(12.9716, cleaned[0].latitude, 0.0001)
        assertEquals(12.9718, cleaned[1].latitude, 0.0001)
    }

    @Test
    fun `cleanPoints removes duplicate timestamps`() {
        val points = listOf(
            point(0, 12.9716, 77.5946),
            point(0, 12.9716, 77.5946), // duplicate timestamp
            point(1000, 12.9717, 77.5947),
        )
        assertEquals(2, GpsProcessor.cleanPoints(points).size)
    }

    @Test
    fun `movingSeconds excludes segments below the moving speed threshold`() {
        val points = listOf(
            point(0, 12.9716, 77.5946),
            // Same coordinates 10s later => 0 m/s => not moving
            point(10_000, 12.9716, 77.5946),
            // ~14m in 10s => 1.4 m/s => moving
            point(20_000, 12.97173, 77.5946),
        )
        val moving = GpsProcessor.movingSeconds(points)
        assertEquals(10L, moving)
    }

    @Test
    fun `elevation gain and loss ignore sub-1m noise`() {
        val points = listOf(
            point(0, 0.0, 0.0, alt = 100.0),
            point(1000, 0.0001, 0.0, alt = 100.3), // within noise floor
            point(2000, 0.0002, 0.0, alt = 105.0), // real gain
            point(3000, 0.0003, 0.0, alt = 102.0), // real loss
        )
        val stats = GpsProcessor.elevationStats(points)
        assertTrue("gain should be > 0, got ${stats.gainMeters}", stats.gainMeters > 0)
        assertTrue("loss should be > 0, got ${stats.lossMeters}", stats.lossMeters > 0)
    }

    @Test
    fun `speed stats compute average moving average and max`() {
        val points = listOf(
            point(0, 12.9716, 77.5946, speed = 5f),
            point(10_000, 12.97205, 77.5946, speed = 8f),
        )
        val distance = GpsProcessor.totalDistanceMeters(points)
        val moving = GpsProcessor.movingSeconds(points)
        val stats = GpsProcessor.speedStats(points, distance, elapsedSeconds = 10, movingSeconds = moving)
        assertEquals(8.0, stats.maxMps, 0.01)
        assertTrue(stats.averageMps > 0)
    }

    private fun abs(x: Double) = if (x < 0) -x else x
}
