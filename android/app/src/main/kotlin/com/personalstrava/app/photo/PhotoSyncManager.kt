package com.personalstrava.app.photo

import android.content.Context
import android.net.Uri
import com.personalstrava.app.data.local.dao.PhotoDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage

/**
 * Mirrors SyncManager's shape (local -> pending_sync -> syncing ->
 * synced|sync_failed) but for photo bytes rather than activity summary
 * rows. Uploads to the private `activity-photos` bucket at
 * `{user_id}/{activity_id}/{photo_id}.jpg` — see supabase/migrations/
 * 0006_photos_and_avatars.sql for the bucket + RLS policies that path
 * layout is keyed to.
 */
class PhotoSyncManager(
    private val context: Context,
    private val supabase: SupabaseClient,
    private val photoDao: PhotoDao,
) {
    suspend fun syncPending() {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return // not signed in — nothing to do yet
        val pending = photoDao.getPendingSync()
        val bucket = supabase.storage.from("activity-photos")

        for (photo in pending) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(Uri.parse(photo.localUri))?.use { it.readBytes() }
            }.getOrNull()

            if (bytes == null) {
                // Local file is gone (e.g. app storage cleared) — nothing left to upload, drop the row
                // rather than retry forever against a file that will never come back.
                photoDao.delete(photo.id)
                continue
            }

            val storagePath = "$userId/${photo.activityId}/${photo.id}.jpg"
            try {
                bucket.upload(storagePath, bytes) { upsert = true }
                photoDao.updateSyncStatus(photo.id, "synced", storagePath)
            } catch (e: Exception) {
                photoDao.updateSyncStatus(photo.id, "sync_failed", null)
                // Same WorkManager backoff policy as SyncManager handles the retry timing —
                // this method just does one pass and lets the caller decide when to run again.
            }
        }
    }
}
