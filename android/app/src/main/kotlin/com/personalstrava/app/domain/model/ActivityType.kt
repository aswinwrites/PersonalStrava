package com.personalstrava.app.domain.model

/** Exactly three activity types — spec section 14. One normalized model, no per-type subclasses. */
enum class ActivityType {
    WALKING,
    CYCLING,
    MOTORCYCLING;

    val dbValue: String get() = name.lowercase()

    companion object {
        fun fromDbValue(value: String): ActivityType = valueOf(value.uppercase())
    }
}

enum class SyncStatus {
    LOCAL,
    PENDING_SYNC,
    SYNCING,
    SYNCED,
    SYNC_FAILED,
    ARCHIVED;

    val dbValue: String get() = name.lowercase()

    companion object {
        fun fromDbValue(value: String): SyncStatus = valueOf(value.uppercase())
    }
}
