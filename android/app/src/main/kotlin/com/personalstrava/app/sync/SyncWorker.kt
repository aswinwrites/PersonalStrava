package com.personalstrava.app.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.personalstrava.app.PersonalStravaApp
import java.util.concurrent.TimeUnit

/**
 * Runs SyncManager.syncPending() in the background. Two triggers enqueue
 * this, both by name so re-enqueuing never stacks duplicate jobs:
 *  - a periodic job (every 15 min, the WorkManager minimum), started once on
 *    first successful sign-in (see MainActivity) and left running — a
 *    signed-out SyncManager.syncPending() is just a cheap no-op, so there's
 *    no need to cancel it on sign-out;
 *  - a one-off job fired immediately after RecordingViewModel.stop(), so a
 *    finished ride shows up on the web dashboard within seconds on a good
 *    connection instead of waiting for the next periodic tick.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: starting")
        val app = applicationContext as PersonalStravaApp
        return try {
            val result = app.syncManager.syncPending()
            // Photos piggyback on the same worker/trigger points as activity
            // sync rather than getting their own WorkManager job — one fewer
            // moving part, and there's no ordering requirement (an activity
            // row and its photos sync independently; the foreign key only
            // matters once both sides exist, which activities.id being
            // client-generated already guarantees).
            app.photoSyncManager.syncPending()
            Log.d(TAG, "doWork: finished, activities succeeded=${result.succeeded} failed=${result.failed}")
            Result.success()
        } catch (e: Exception) {
            // Unexpected failure (not a per-activity failure, which
            // SyncManager already handles internally) — let WorkManager's
            // backoff policy retry.
            Log.e(TAG, "doWork: unexpected failure, will retry: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val PERIODIC_WORK_NAME = "sync_periodic"
        private const val ONE_TIME_WORK_NAME = "sync_one_time"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
