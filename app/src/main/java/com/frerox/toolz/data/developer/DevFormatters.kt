/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Pure formatting helpers for the developer console. No Android APIs beyond
 * org.json (available in unit tests through the app classpath) so the report
 * format stays stable and testable.
 */

package com.frerox.toolz.data.developer

import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val KIB = 1024.0
private const val MIB = KIB * 1024.0
private const val GIB = MIB * 1024.0

/** Human-readable byte size using binary units: "1.5 MB". */
fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe B"
        safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / KIB)
        safe < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", safe / MIB)
        else -> String.format(Locale.US, "%.2f GB", safe / GIB)
    }
}

/** Compact duration for uptime readouts: "4m 12s", "2h 05m", "3d 4h". */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> if (minutes > 0L) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "${hours}h"
        minutes > 0L -> if (seconds > 0L) "${minutes}m ${seconds.toString().padStart(2, '0')}s" else "${minutes}m"
        else -> "${seconds}s"
    }
}

/** Relative timestamp for crash rows: "just now", "4m ago", then an absolute date. */
fun formatRelativeTime(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (thenMs <= 0L) return "unknown"
    val delta = (nowMs - thenMs).coerceAtLeast(0L)
    return when {
        delta < 60_000L -> "just now"
        delta < 3_600_000L -> "${delta / 60_000L}m ago"
        delta < 86_400_000L -> "${delta / 3_600_000L}h ago"
        delta < 7L * 86_400_000L -> "${delta / 86_400_000L}d ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(thenMs))
    }
}

/** Absolute timestamp used in reports and the DataStore inspector. */
fun formatAbsoluteTime(ms: Long): String =
    if (ms <= 0L) "unknown" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

fun parseCrashHistory(raw: String?): List<CrashLabEvent> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val type = item.optString("type").takeIf { it.isNotBlank() } ?: continue
                add(
                    CrashLabEvent(
                        type = type,
                        atMs = item.optLong("atMs", 0L),
                        note = item.optString("note"),
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Text report offered through the clipboard / system share sheet. It contains
 * process metadata and sandbox names only — no logs, accounts or messages.
 */
fun buildDiagnosticReport(
    info: DevAppInfo,
    activeSandboxes: List<String>,
    crashes: List<CrashLabEvent>,
    generatedAtMs: Long,
    themeMode: String,
    languageTag: String,
): String {
    val heapPercent = (info.heapUsedFraction * 100f).toInt().coerceIn(0, 100)
    val sandboxBlock = if (activeSandboxes.isEmpty()) {
        "Sandboxes: none"
    } else {
        buildString {
            appendLine("Sandboxes (${activeSandboxes.size} active):")
            activeSandboxes.sorted().forEach { appendLine("- $it") }
        }.trimEnd()
    }
    val crashBlock = if (crashes.isEmpty()) {
        "Crash-lab history: none"
    } else {
        buildString {
            appendLine("Crash-lab history (${crashes.size}):")
            crashes.sortedByDescending { it.atMs }.forEach { event ->
                val note = event.note.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                appendLine("- ${formatAbsoluteTime(event.atMs)} · ${event.type}$note")
            }
        }.trimEnd()
    }

    return buildString {
        appendLine("Toolz diagnostic report")
        appendLine("Generated: ${formatAbsoluteTime(generatedAtMs)}")
        appendLine()
        appendLine("App: Toolz ${info.versionName.ifBlank { "unknown" }} (${info.versionCode}) · ${info.buildType.ifBlank { "unknown" }}")
        appendLine("Package: ${info.packageName.ifBlank { "unknown" }}")
        appendLine("Process: PID ${info.pid} · uptime ${formatDuration(info.processUptimeMs)}")
        appendLine("Device: ${info.deviceLabel.ifBlank { "unknown" }}")
        appendLine("Android: ${info.androidLabel.ifBlank { "unknown" }} · ${info.abi.ifBlank { "unknown ABI" }}")
        appendLine("Display: ${info.densityDpi} dpi")
        appendLine(
            "Runtime: heap ${formatBytes(info.heapUsedBytes)} / ${formatBytes(info.heapMaxBytes)} " +
                "($heapPercent%) · native ${formatBytes(info.nativeHeapBytes)} · ${info.threads} threads",
        )
        appendLine("Appearance: theme ${themeMode.lowercase(Locale.US)} · language $languageTag")
        appendLine()
        appendLine(sandboxBlock)
        appendLine()
        appendLine(crashBlock)
        appendLine()
        appendLine("Generated locally by Developer Mode. Nothing is uploaded automatically.")
    }.trimEnd()
}
