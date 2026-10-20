/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.update

import java.io.File
import java.util.Locale

/**
 * A single update candidate resolved by [UpdateManager.check].
 *
 * @param source "github" when resolved from the GitHub release feed,
 *        "manifest" when resolved from the signed update manifest.
 */
data class PendingUpdate(
    val version: String,
    val versionCode: Int,
    val changelog: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
    val sha256: String?,
    val isCritical: Boolean,
    val abi: String,
    val source: String,
) {
    val hasReleaseNotes: Boolean get() = changelog.isNotBlank()
}

/** Single source of truth for the in-app update flow UI. */
sealed interface UpdateManagerState {
    data object Idle : UpdateManagerState
    data object Checking : UpdateManagerState

    data class Available(val update: PendingUpdate) : UpdateManagerState

    data class Downloading(
        val update: PendingUpdate,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBps: Long,
        /** 0..100 */
        val progress: Float,
    ) : UpdateManagerState

    data class Verifying(
        val update: PendingUpdate,
        val apkFile: File? = null,
    ) : UpdateManagerState

    data class ReadyToInstall(
        val update: PendingUpdate,
        val apkFile: File,
        val needsPermission: Boolean = false,
    ) : UpdateManagerState

    data class Installing(
        val update: PendingUpdate,
        val apkFile: File,
    ) : UpdateManagerState

    data class Error(
        val error: UpdateError,
        /** True when retrying should re-download instead of re-checking. */
        val retryDownload: Boolean = false,
    ) : UpdateManagerState {
        val retryable: Boolean get() = error.retryable
    }

    data object UpToDate : UpdateManagerState

    data class Installed(val version: String) : UpdateManagerState
}

/** The candidate owned by a live state, when there is one. */
fun UpdateManagerState.pendingUpdate(): PendingUpdate? = when (this) {
    is UpdateManagerState.Available -> update
    is UpdateManagerState.Downloading -> update
    is UpdateManagerState.Verifying -> update
    is UpdateManagerState.ReadyToInstall -> update
    is UpdateManagerState.Installing -> update
    else -> null
}

private fun formatUnit(bytes: Double, unit: String): String =
    String.format(Locale.US, "%.1f %s", bytes, unit)

/** Human-readable byte count: "512 B", "3.2 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return formatUnit(kb, "KB")
    val mb = kb / 1024.0
    if (mb < 1024) return formatUnit(mb, "MB")
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

/** Compact duration pieces the UI turns into a localized string. */
sealed interface Eta {
    data class Seconds(val seconds: Long) : Eta
    data class Minutes(val minutes: Long, val seconds: Long) : Eta
    data class Hours(val hours: Long, val minutes: Long) : Eta
}

/**
 * Splits the remaining download time into localized pieces, or null when
 * throughput is not known yet.
 */
fun etaOf(remainingBytes: Long, bps: Long): Eta? {
    if (bps <= 0) return null
    val seconds = if (remainingBytes <= 0) 0 else remainingBytes / bps
    if (seconds < 60) return Eta.Seconds(seconds)
    val minutes = seconds / 60
    if (minutes < 60) return Eta.Minutes(minutes, seconds % 60)
    return Eta.Hours(minutes / 60, minutes % 60)
}

/** Coarse relative age; the UI picks the matching plural string. */
sealed interface RelativeAge {
    data object JustNow : RelativeAge
    data class Minutes(val minutes: Long) : RelativeAge
    data class Hours(val hours: Long) : RelativeAge
    data class Days(val days: Long) : RelativeAge
}

fun relativeAge(ageMs: Long): RelativeAge {
    if (ageMs < 0) return RelativeAge.JustNow
    val minutes = ageMs / 60_000L
    return when {
        minutes < 1 -> RelativeAge.JustNow
        minutes < 60 -> RelativeAge.Minutes(minutes)
        minutes < 24 * 60 -> RelativeAge.Hours(minutes / 60)
        else -> RelativeAge.Days(minutes / (24 * 60))
    }
}
