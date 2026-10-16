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

/**
 * A single update candidate resolved by [UpdateManager.check].
 *
 * @param source "github" when resolved from the GitHub release feed,
 *        "manifest" when enriched with the signed update manifest.
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
    val source: String
)

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
        val progress: Float
    ) : UpdateManagerState
    data class ReadyToInstall(val update: PendingUpdate, val apkFile: File) : UpdateManagerState
    data class Installing(val update: PendingUpdate) : UpdateManagerState
    data class Error(val message: String, val retryable: Boolean) : UpdateManagerState
    data object UpToDate : UpdateManagerState
    data object Installed : UpdateManagerState
}

/** Human-readable byte count: "512 B", "3.2 MB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

/** Human-readable throughput: "512 B/s", "3.2 MB/s". */
fun formatSpeed(bps: Long): String {
    if (bps <= 0) return "--"
    return "${formatBytes(bps)}/s"
}

/**
 * Human-readable ETA: "--" when unknown, "12s", "3m 12s", "1h 05m".
 */
fun formatEta(remainingBytes: Long, bps: Long): String {
    if (remainingBytes <= 0) return "0s"
    if (bps <= 0) return "--"
    val seconds = remainingBytes / bps
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    if (minutes < 60) return "%dm %02ds".format(minutes, seconds % 60)
    return "%dh %02dm".format(minutes / 60, minutes % 60)
}
