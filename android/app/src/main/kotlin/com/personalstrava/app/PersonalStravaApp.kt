package com.personalstrava.app

import android.app.Application
import com.personalstrava.app.data.local.AppDatabase
import com.personalstrava.app.photo.PhotoRepository
import com.personalstrava.app.photo.PhotoSyncManager
import com.personalstrava.app.record.RecordingRepository
import com.personalstrava.app.sync.SupabaseClientProvider
import com.personalstrava.app.sync.SyncManager

class PersonalStravaApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val supabase by lazy { SupabaseClientProvider.create() }

    // Owns GPS-sample persistence + live stats for an in-progress recording.
    // Application-scoped (not ViewModel-scoped) so recording survives the
    // recording screen being destroyed — see RecordingRepository's own doc.
    val recordingRepository by lazy { RecordingRepository(database) }

    val syncManager by lazy { SyncManager(supabase, database.activityDao(), database.syncQueueDao()) }

    val photoRepository by lazy { PhotoRepository(this, database) }
    val photoSyncManager by lazy { PhotoSyncManager(this, supabase, database.photoDao()) }

    override fun onCreate() {
        super.onCreate()
        // The periodic SyncWorker is enqueued from MainActivity once a
        // session exists (see AuthViewModel/SyncWorker.enqueuePeriodic) —
        // not unconditionally here, since syncing with no session is a
        // guaranteed no-op (see SyncManager.syncPending).
    }
}
