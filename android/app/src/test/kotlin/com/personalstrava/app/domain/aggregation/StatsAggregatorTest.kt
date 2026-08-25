package com.personalstrava.app.domain.aggregation

import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.data.local.entity.DailyStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsAggregatorTest {

    private fun activity(type: String, distance: Double, movingSeconds: Long, elevation: Double = 0.0) = ActivityEntity(
        id = "id-$type-$distance",
        activityType = type,
        startTime = 0,
        endTime = 0,
        elapsedSeconds = movingSeconds,
        movingSeconds = movingSeconds,
        distanceMeters = distance,
        elevationGainMeters = elevation,
        elevationLossMeters = 0.0,
        averageSpeedMps = null,
        movingAverageSpeedMps = null,
        maxSpeedMps = null,
        startLatitude = null,
        startLongitude = null,
        endLatitude = null,
        endLongitude = null,
        routePolyline = null,
        title = null,
        notes = null,
        syncStatus = "synced",
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `aggregateDaily sums distance and time per activity type`() {
        val activities = listOf(
            activity("cycling", 10_000.0, 1_800, elevation = 100.0),
            activity("cycling", 5_000.0, 900),
            activity("motorcycling", 40_000.0, 2_400),
        )
        val result = StatsAggregator.aggregateDaily("2026-08-25", steps = 8421, activities = activities, now = 123L)

        assertEquals(8421, result.steps)
        assertEquals(15_000.0, result.cyclingDistanceMeters, 0.001)
        assertEquals(40_000.0, result.motorcyclingDistanceMeters, 0.001)
        assertEquals(0.0, result.walkingDistanceMeters, 0.001)
        assertEquals(2_700L, result.cyclingSeconds)
        assertEquals(100.0, result.elevationGainMeters, 0.001)
        assertEquals(3, result.activityCount)
    }

    @Test
    fun `aggregateDaily with no activities still records steps`() {
        val result = StatsAggregator.aggregateDaily("2026-08-25", steps = 1200, activities = emptyList(), now = 0)
        assertEquals(1200, result.steps)
        assertEquals(0, result.activityCount)
    }

    @Test
    fun `aggregateMonthly rolls up daily rows without re-deriving from activities`() {
        val days = listOf(
            DailyStatsEntity(date = "2026-08-01", steps = 1000, cyclingDistanceMeters = 5000.0, cyclingSeconds = 600, activityCount = 1),
            DailyStatsEntity(date = "2026-08-02", steps = 2000, cyclingDistanceMeters = 3000.0, cyclingSeconds = 400, activityCount = 1),
        )
        val month = StatsAggregator.aggregateMonthly("2026-08", days, now = 0)

        assertEquals(3000, month.steps)
        assertEquals(8000.0, month.cyclingDistanceMeters, 0.001)
        assertEquals(1000L, month.cyclingSeconds)
        assertEquals(2, month.activityCount)
    }

    @Test
    fun `percentChange computes signed percentage`() {
        assertEquals(34.0, StatsAggregator.percentChange(current = 134.0, previous = 100.0)!!, 0.001)
        assertEquals(-18.0, StatsAggregator.percentChange(current = 82.0, previous = 100.0)!!, 0.001)
    }

    @Test
    fun `percentChange is undefined when previous period was zero`() {
        assertNull(StatsAggregator.percentChange(current = 10.0, previous = 0.0))
    }
}
