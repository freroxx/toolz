/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Pure fast-report models and Markdown formatting. No Android framework calls
 * live here so the exact report shape stays unit-testable: the crash recorder
 * and the sharing layer feed data in, and this file decides how it reads.
 */

package com.frerox.toolz.data.developer

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Everything the crash handler managed to save before the process died. */
data class CapturedCrash(
    val atMs: Long,
    val threadName: String,
    val throwableName: String,
    val message: String,
    val stackTrace: String,
    val logs: List<String> = emptyList(),
    val appVersionName: String = "",
    val appVersionCode: Long = 0L,
    val deviceLabel: String = "",
    val androidLabel: String = "",
)

fun CapturedCrash.toJson(): String = JSONObject()
    .put("atMs", atMs)
    .put("threadName", threadName)
    .put("throwableName", throwableName)
    .put("message", message)
    .put("stackTrace", stackTrace)
    .put("appVersionName", appVersionName)
    .put("appVersionCode", appVersionCode)
    .put("deviceLabel", deviceLabel)
    .put("androidLabel", androidLabel)
    .put("logs", JSONArray(logs))
    .toString()

fun parseCapturedCrash(raw: String?): CapturedCrash? {
    if (raw.isNullOrBlank()) return null
    return try {
        val json = JSONObject(raw)
        val stackTrace = json.optString("stackTrace")
        if (stackTrace.isBlank()) return null
        val logs = mutableListOf<String>()
        val array = json.optJSONArray("logs")
        if (array != null) {
            for (index in 0 until array.length()) {
                val line = array.optString(index)
                if (line.isNotBlank()) logs += line
            }
        }
        CapturedCrash(
            atMs = json.optLong("atMs", 0L),
            threadName = json.optString("threadName").ifBlank { "unknown" },
            throwableName = json.optString("throwableName").ifBlank { "Unknown error" },
            message = json.optString("message"),
            stackTrace = stackTrace,
            logs = logs,
            appVersionName = json.optString("appVersionName"),
            appVersionCode = json.optLong("appVersionCode", 0L),
            deviceLabel = json.optString("deviceLabel"),
            androidLabel = json.optString("androidLabel"),
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Markdown fast report: short enough to read on a phone, complete enough for
 * a maintainer to understand the crash without asking follow-up questions.
 *
 * Log and stack content is wrapped in four-tilde fences so a stray ``` inside
 * a stack trace can never break the document.
 */
fun buildFastReportMarkdown(
    info: DevAppInfo,
    crash: CapturedCrash?,
    warnErrorLogs: List<String>,
    activeSandboxes: List<String> = emptyList(),
    generatedAtMs: Long,
    discordUrl: String,
    languageTag: String = Locale.getDefault().toLanguageTag(),
): String {
    val heapPercent = (info.heapUsedFraction * 100f).toInt().coerceIn(0, 100)
    val crashVersion = crash?.appVersionName?.takeIf { it.isNotBlank() } ?: info.versionName
    val crashCode = crash?.appVersionCode?.takeIf { it > 0L } ?: info.versionCode
    val crashDevice = crash?.deviceLabel?.takeIf { it.isNotBlank() } ?: info.deviceLabel
    val crashAndroid = crash?.androidLabel?.takeIf { it.isNotBlank() } ?: info.androidLabel

    val sandboxBlock = if (activeSandboxes.isEmpty()) {
        "_No sandboxes were active._"
    } else {
        activeSandboxes.sorted().joinToString("\n") { "- $it" }
    }

    val logBlock = if (warnErrorLogs.isEmpty()) {
        "_No warning or error lines were captured._"
    } else {
        fenced(warnErrorLogs.takeLast(MAX_REPORT_LOG_LINES).joinToString("\n"))
    }

    return buildString {
        appendLine("# Toolz fast report")
        appendLine()
        appendLine("> Generated on device at ${formatAbsoluteTime(generatedAtMs)}.")
        appendLine("> This file was created locally by Toolz. Nothing has been uploaded automatically.")
        appendLine()
        appendLine("## What happened")
        appendLine()
        if (crash == null) {
            appendLine("- **Crash:** no crash was recorded for this report")
            appendLine("- **Report reason:** manual diagnostic snapshot")
        } else {
            appendLine("- **Crash detected:** ${formatAbsoluteTime(crash.atMs)}")
            appendLine("- **Exception:** `${crash.throwableName}`")
            if (crash.message.isNotBlank()) {
                appendLine("- **Message:** ${crash.message.lineSequence().joinToString(" ") { it.trim() }.take(600)}")
            } else {
                appendLine("- **Message:** _none_")
            }
            appendLine("- **Thread:** `${crash.threadName}`")
        }
        appendLine()
        appendLine("## App")
        appendLine()
        appendLine("- **Version:** Toolz $crashVersion ($crashCode)")
        appendLine("- **Build type:** ${info.buildType.ifBlank { "unknown" }}")
        appendLine("- **Package:** `${info.packageName.ifBlank { "com.frerox.toolz" }}`")
        appendLine("- **Process:** PID ${info.pid} · uptime ${formatDuration(info.processUptimeMs)}")
        appendLine("- **Build:** ${if (info.debuggable) "debuggable" else "release"} · language `$languageTag`")
        appendLine()
        appendLine("## Device")
        appendLine()
        appendLine("- **Model:** $crashDevice")
        appendLine("- **Android:** $crashAndroid")
        appendLine("- **ABI:** ${info.abi.ifBlank { "unknown" }}")
        appendLine("- **Display:** ${info.densityDpi} dpi")
        appendLine(
            "- **Memory:** heap ${formatBytes(info.heapUsedBytes)} / ${formatBytes(info.heapMaxBytes)} " +
                "($heapPercent%) · native ${formatBytes(info.nativeHeapBytes)} · ${info.threads} threads",
        )
        appendLine()
        appendLine("## Active sandboxes")
        appendLine()
        appendLine(sandboxBlock)
        appendLine()
        appendLine("## Recent warnings and errors")
        appendLine()
        appendLine(logBlock)
        appendLine()
        if (crash != null) {
            appendLine("## Stack trace")
            appendLine()
            appendLine(fenced(crash.stackTrace.trim().take(MAX_REPORT_STACK_CHARS)))
            appendLine()
        }
        appendLine("## How to send this report")
        appendLine()
        appendLine("1. Join the Toolz Discord server (also linked in Settings → About): $discordUrl")
        appendLine("2. Open the **bug-reports** channel.")
        appendLine("3. Drop this `.md` file into the channel with a short description of what you were doing.")
        appendLine()
        appendLine("## Privacy")
        appendLine()
        appendLine(
            "This report contains technical diagnostics only: app and device versions, active sandbox " +
                "names, warning/error logs, and the crash stack trace. Toolz does not read your messages, " +
                "files, photos, passwords, or account details to build it. You choose where the file is sent.",
        )
    }.trimEnd()
}

internal const val MAX_REPORT_LOG_LINES = 300
private const val MAX_REPORT_STACK_CHARS = 24_000

/**
 * Keeps crash-time evidence even when the current session has already logged
 * enough warnings to fill the report on its own. Crash lines come first, but
 * up to a third of the budget is reserved for the newest live lines so the
 * report still describes what happened after the restart.
 */
internal fun mergeFastReportLogs(
    liveLogs: List<String>,
    crashLogs: List<String>,
    maxLines: Int = MAX_REPORT_LOG_LINES,
): List<String> {
    if (maxLines <= 0) return emptyList()
    val crash = stableDistinctLogs(crashLogs)
    val live = stableDistinctLogs(liveLogs)
    if (crash.isEmpty()) return live.takeLast(maxLines)
    if (live.isEmpty()) return crash.takeLast(maxLines)

    val liveBudget = minOf(live.size, maxLines / 3)
    val crashBudget = maxLines - liveBudget
    val crashPart = crash.takeLast(crashBudget)
    return stableDistinctLogs(crashPart + live.takeLast(maxLines - crashPart.size))
}

internal fun stableDistinctLogs(lines: List<String>): List<String> {
    if (lines.isEmpty()) return emptyList()
    val seen = LinkedHashSet<String>(lines.size)
    lines.forEach { line ->
        val trimmed = line.trimEnd()
        if (trimmed.isNotBlank()) seen += trimmed
    }
    return seen.toList()
}

/** Four-tilde fence; logs and traces may contain any number of backticks. */
private fun fenced(block: String): String {
    val safe = block
        .replace("\r\n", "\n")
        .replace("~~~~", "~ ~ ~ ~")
    return "~~~~text\n$safe\n~~~~"
}
