package com.personalstrava.app.domain.gps

/**
 * A single raw GPS fix, as captured by FusedLocationProviderClient. Mirrors
 * the gps_points Room table columns 1:1 (spec section 16) — this is the
 * in-memory processing shape, [com.personalstrava.app.data.local.entity.GpsPointEntity]
 * is the persistence shape.
 */
data class GpsPoint(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMps: Float?,
    val accuracyMeters: Float?,
    val headingDegrees: Float?,
)
