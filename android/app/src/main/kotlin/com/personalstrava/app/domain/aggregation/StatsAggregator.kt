package com.personalstrava.app.domain.aggregation

import com.personalstrava.app.data.local.entity.ActivityEntity
import com.personalstrava.app.data.local.entity.DailyStatsEntity
import com.personalstrava.app.data.local.entity.MonthlyStatsEntity
import com.personalstrava.app.domain.model.ActivityType

/**
 * Pure functions that fold a day's/month's activities (+ Health Connect
 * steps) into the daily_stats / monthly_stats rows. Kept side-effect free
 * and Android-independent on purpose so they're plain-JUnit testable (spec
 * section 44: "daily step aggregation", "monthly aggregation", "period
 * comparisons") — the calling repository is what actually reads/writes Room.
 */
object StatsAggregator {

    fun aggregateDaily(date: String, steps: Int, activities: List<ActivityEntity>, now: Long): DailyStatsEntity {
        var walking = 0.0
        var cycling = 0.0
        var motorcycling = 0.0
        var walkingSeconds = 0L
        var cyclingSeconds = 0L
        var motorcyclingSeconds = 0L
        var elevation = 0.0

        for (activity in activities) {
            when (ActivityType.fromDbValue(activity.activityType)) {
                ActivityType.WALKING -> {
                    walking += activity.distanceMeters
                    walkingSeconds += activity.movingSeconds
                }
                ActivityType.CYCLING -> {
                    cycling += activity.distanceMeters
                    cyclingSeconds += activity.movingSeconds
                }
                ActivityType.MOTORCYCLING -> {
                    motorcycling += activity.distanceMeters
                    motorcyclingSeconds += activity.movingSeconds
                }
            }
            elevation += activity.elevationGainMeters
        }

        return DailyStatsEntity(
            date = date,
            steps = steps,
            walkingDistanceMeters = walking,
            cyclingDistanceMeters = cycling,
            motorcyclingDistanceMeters = motorcycling,
            walkingSeconds = walkingSeconds,
            cyclingSeconds = cyclingSeconds,
            motorcyclingSeconds = motorcyclingSeconds,
            elevationGainMeters = elevation,
            activityCount = activities.size,
            updatedAt = now,
        )
    }

    /** Rolls up a month's worth of already-computed daily rows — never re-derives from raw activities/GPS. */
    fun aggregateMonthly(month: String, dailyRows: List<DailyStatsEntity>, now: Long): MonthlyStatsEntity = MonthlyStatsEntity(
        month = month,
        steps = dailyRows.sumOf { it.steps },
        walkingDistanceMeters = dailyRows.sumOf { it.walkingDistanceMeters },
        cyclingDistanceMeters = dailyRows.sumOf { it.cyclingDistanceMeters },
        motorcyclingDistanceMeters = dailyRows.sumOf { it.motorcyclingDistanceMeters },
        walkingSeconds = dailyRows.sumOf { it.walkingSeconds },
        cyclingSeconds = dailyRows.sumOf { it.cyclingSeconds },
        motorcyclingSeconds = dailyRows.sumOf { it.motorcyclingSeconds },
        elevationGainMeters = dailyRows.sumOf { it.elevationGainMeters },
        activityCount = dailyRows.sumOf { it.activityCount },
        updatedAt = now,
    )

    /** Percent change of `current` vs `previous`, matching the sign/format the web Analytics trend cards use. */
    fun percentChange(current: Double, previous: Double): Double? {
        if (previous == 0.0) return null // undefined — caller renders "New" rather than a bogus %
        return ((current - previous) / previous) * 100.0
    }
}
