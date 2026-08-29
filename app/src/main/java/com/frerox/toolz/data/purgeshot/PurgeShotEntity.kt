/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.purgeshot

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable queue entry for a screenshot pending timed deletion.
 *
 * Survivability strategy (triple-redundant):
 *  1. Room (primary, observable, transactional)
 *  2. External mirror JSON in Documents/Toolz/.purgeshot_queue.json — survives clear data / cache
 *  3. Android Auto-Backup via backup_rules.xml (db file + DataStore)
 *  4. Per-entry WorkManager + AlarmManager rescheduled on BOOT_COMPLETED / MY_PACKAGE_REPLACED
 *
 * Even if the user clears app data or updates, [PurgeShotExternalBackupHelper] restores
 * the queue on next launch and [PurgeShotBootReceiver] re-enqueues all pending deletions.
 */
@Entity(
    tableName = "purge_shot_queue",
    indices = [
        Index(value = ["scheduledDeleteAtMs"]),
        Index(value = ["status"]),
        Index(value = ["fileUriString"])
    ]
)
data class PurgeShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileUriString: String,              // content://media/...  (canonical, for MediaStore delete)
    val displayName: String,                // e.g. Screenshot_20260829_123456.png
    val filePath: String? = null,           // legacy DATA column fallback
    val createdAtMs: Long = System.currentTimeMillis(),
    val scheduledDeleteAtMs: Long,          // absolute epoch millis when file must be deleted
    val durationMillis: Long,               // original chosen duration (for UI display)
    val durationLabel: String,              // "1 min", "15 min", "30 min", "1 week", etc.
    val status: String = STATUS_PENDING,    // PENDING | DELETED | CANCELLED | FAILED | EXPIRED
    val sourcePackage: String? = null,      // optional: which app triggered screenshot (if known)
    val attempts: Int = 0,
    val lastError: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DELETED = "DELETED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_EXPIRED = "EXPIRED"

        const val TABLE_NAME = "purge_shot_queue"
    }

    val isPending: Boolean get() = status == STATUS_PENDING
    val isTerminal: Boolean get() = status in setOf(STATUS_DELETED, STATUS_CANCELLED)
    val remainingMs: Long get() = scheduledDeleteAtMs - System.currentTimeMillis()
}

data class PurgeShotPreset(
    val label: String,
    val durationMillis: Long,
    val iconName: String = "timer" // maps to Icons.Rounded.* in UI
) {
    companion object {
        const val AUTO_SENTINEL = -1L // means "use autoDuration from settings"
        fun defaults(): List<PurgeShotPreset> = listOf(
            PurgeShotPreset("1 min", 60_000L, "timer"),
            PurgeShotPreset("15 min", 15 * 60_000L, "schedule"),
            PurgeShotPreset("30 min", 30 * 60_000L, "hourglass_top"),
            PurgeShotPreset("1 hour", 60 * 60_000L, "hourglass_empty"),
            PurgeShotPreset("1 day", 24 * 60 * 60_000L, "today"),
            PurgeShotPreset("Auto", AUTO_SENTINEL, "auto_awesome")
        )

        // Extra presets available in picker (not shown by default) — includes Auto sentinel
        fun allOptions(): List<PurgeShotPreset> = listOf(
            PurgeShotPreset("30 sec", 30_000L, "timer"),
            PurgeShotPreset("1 min", 60_000L, "timer"),
            PurgeShotPreset("5 min", 5 * 60_000L, "schedule"),
            PurgeShotPreset("15 min", 15 * 60_000L, "schedule"),
            PurgeShotPreset("30 min", 30 * 60_000L, "hourglass_top"),
            PurgeShotPreset("1 hour", 60 * 60_000L, "hourglass_empty"),
            PurgeShotPreset("6 hours", 6 * 60 * 60_000L, "wb_sunny"),
            PurgeShotPreset("12 hours", 12 * 60 * 60_000L, "nights_stay"),
            PurgeShotPreset("1 day", 24 * 60 * 60_000L, "today"),
            PurgeShotPreset("3 days", 3 * 24 * 60 * 60_000L, "calendar_today"),
            PurgeShotPreset("1 week", 7 * 24 * 60 * 60_000L, "date_range"),
            PurgeShotPreset("2 weeks", 14 * 24 * 60 * 60_000L, "event_repeat"),
            PurgeShotPreset("1 month", 30L * 24 * 60 * 60_000L, "calendar_month"),
            PurgeShotPreset("Auto", AUTO_SENTINEL, "auto_awesome")
        )
    }
}
