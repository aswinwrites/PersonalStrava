package com.personalstrava.app.photo

import android.content.Context
import android.net.Uri
import com.personalstrava.app.data.local.AppDatabase
import com.personalstrava.app.data.local.entity.PhotoEntity
import com.personalstrava.app.domain.IdGenerator
import com.personalstrava.app.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the "picked photo -> permanent app-private copy -> Room row" path.
 * The copy step matters: a content:// URI from the system photo picker only
 * grants this app read access for a short-lived window (effectively until
 * the picker activity's result is consumed), so holding onto that URI and
 * reading it later — e.g. from a WorkManager sync job — would intermittently
 * fail with a SecurityException. Copying the bytes into
 * filesDir/photos/<activityId>/<photoId>.jpg immediately after pick sidesteps
 * that entirely; PhotoSyncManager reads from this permanent copy.
 */
class PhotoRepository(private val context: Context, private val database: AppDatabase) {
    private val photoDao = database.photoDao()

    fun observeForActivity(activityId: String): Flow<List<PhotoEntity>> = photoDao.observeForActivity(activityId)

    /** Copies each picked URI into permanent storage and inserts a pending-sync Room row per photo. */
    suspend fun addPhotos(activityId: String, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "photos/$activityId").apply { mkdirs() }
        var nextPosition = photoDao.nextPosition(activityId)

        for (uri in uris) {
            val photoId = IdGenerator.newPhotoId()
            val dest = File(dir, "$photoId.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: continue // unreadable URI (permission revoked, picker glitch) — skip rather than crash the batch

            photoDao.insert(
                PhotoEntity(
                    id = photoId,
                    activityId = activityId,
                    localUri = Uri.fromFile(dest).toString(),
                    caption = null,
                    position = nextPosition++,
                    storagePath = null,
                    syncStatus = SyncStatus.PENDING_SYNC.dbValue,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Deletes a photo locally. If it had already synced, the orphaned remote object is left in
     *  place for now — deliberate scope cut; a "delete photo" remote-cleanup pass is a fast-follow
     *  rather than blocking the core feature on wiring up storage-object deletion + retry semantics. */
    suspend fun deletePhoto(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        runCatching { Uri.parse(photo.localUri).path?.let { File(it).delete() } }
        photoDao.delete(photo.id)
    }
}
