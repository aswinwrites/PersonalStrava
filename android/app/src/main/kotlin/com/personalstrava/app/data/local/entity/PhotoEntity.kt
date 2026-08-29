package com.personalstrava.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A personal-memory photo attached to one activity. `localUri` points at a
 * copy in this app's private storage (see PhotoRepository — never a
 * transient content:// picker URI, which can lose read access after the
 * picker activity finishes). `storagePath` + `syncStatus` mirror the
 * activities table's own sync fields once the photo is uploaded to the
 * `activity-photos` Supabase Storage bucket (see supabase/migrations/
 * 0006_photos_and_avatars.sql).
 */
@Entity(tableName = "activity_photos", indices = [Index("activityId"), Index("syncStatus")])
data class PhotoEntity(
    @PrimaryKey val id: String, // client-generated UUID, doubles as the remote file's basename
    val activityId: String,
    val localUri: String,
    val caption: String?,
    val position: Int,
    val storagePath: String?,
    val syncStatus: String, // SyncStatus.dbValue — local, pending_sync, syncing, synced, sync_failed
    val createdAt: Long,
)
