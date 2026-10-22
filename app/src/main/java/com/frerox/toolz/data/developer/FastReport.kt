/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Always-on crash capture and the fast-report export path.
 *
 * The uncaught-exception handler is installed by ToolzApplication regardless
 * of Developer Mode. It writes one small JSON file (synchronously, before the
 * process dies) containing the exception, the current app/device identity and
 * the warning/error lines that were still in logcat. On the next launch the
 * UI offers to turn that file into a Markdown report and share it.
 *
 * Nothing is uploaded. The file leaves the device only when the user picks a
 * destination in the system share sheet.
 */

package com.frerox.toolz.data.developer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FastReportStore {

    private const val TAG = "FastReport"
    private const val DIR_NAME = "fast_reports"
    private const val PENDING_FILE = "pending_crash.json"
    private const val MAX_STORED_LOGS = 300
    private const val MAX_STACK_CHARS = 24_000

    @Volatile
    private var installed = false

    private fun reportDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun pendingFile(context: Context): File = File(reportDir(context), PENDING_FILE)

    /**
     * Installs the process-wide handler once. The previous handler (usually
     * Android's own) is always called afterwards so the system still shows
     * the crash dialog, reports to Play/other collectors and kills the app.
     */
    fun installCrashHandler(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { captureCrash(app, thread, throwable) }
                .onFailure { Log.w(TAG, "Could not save the crash snapshot", it) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** True when a crash was recorded and has not been acknowledged yet. */
    fun hasPendingCrash(context: Context): Boolean =
        pendingFile(context).let { it.isFile && it.length() > 0L }

    /**
     * Reads the saved crash without deleting it, so a process death while the
     * recovery popup is open never loses the report. Call [clearPendingCrash]
     * only after the user acts on it.
     */
    fun readPendingCrash(context: Context): CapturedCrash? {
        val file = pendingFile(context)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            parseCapturedCrash(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the pending crash snapshot", e)
            null
        }
    }

    fun clearPendingCrash(context: Context) {
        runCatching { pendingFile(context).delete() }
    }

    @Synchronized
    private fun captureCrash(context: Context, thread: Thread, throwable: Throwable) {
        val info = runCatching { DevAppInfoCollector.collect(context) }.getOrNull()
        val logs = runCatching { FastReportLogs.readWarnErrorLogs() }.getOrDefault(emptyList())
        val crash = CapturedCrash(
            atMs = System.currentTimeMillis(),
            threadName = thread.name,
            throwableName = throwable.javaClass.name,
            message = throwable.message.orEmpty().take(1_200),
            stackTrace = Log.getStackTraceString(throwable).take(MAX_STACK_CHARS),
            logs = logs.map { it.take(1_200) }.takeLast(MAX_STORED_LOGS),
            appVersionName = info?.versionName.orEmpty(),
            appVersionCode = info?.versionCode ?: 0L,
            deviceLabel = info?.deviceLabel.orEmpty(),
            androidLabel = info?.androidLabel.orEmpty(),
        )

        val target = pendingFile(context)
        val temp = File(target.parentFile, "$PENDING_FILE.tmp")
        try {
            temp.writeText(crash.toJson(), Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                // Same-directory rename should be atomic on every supported
                // filesystem; a copy keeps the report if it is not.
                target.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
                temp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist the crash snapshot", e)
        } finally {
            runCatching { temp.delete() }
        }
    }
}

/**
 * Reads warning/error records from the local ring buffer and the app's own
 * logcat, keeping stack traces attached to their parent line.
 */
object FastReportLogs {

    private const val MAX_FETCHED_LINES = 600
    private const val TAKEN_LINES = 300
    private const val LOGCAT_TIMEOUT_MS = 1_500L

    fun readWarnErrorLogs(): List<String> {
        val groups = mutableListOf<List<String>>()
        runCatching { groups += DevLogHub.getLines() }
        runCatching { groups += readLogcatDump() }
        if (groups.isEmpty()) return emptyList()
        val merged = runCatching {
            mergeLogcatLines(groups, maxLines = MAX_FETCHED_LINES)
        }.getOrDefault(groups.flatten())
        return filterLogcatLines(merged, severity = LogcatSeverity.WARNINGS)
            .takeLast(TAKEN_LINES)
    }

    private fun readLogcatDump(): List<String> {
        var process: Process? = null
        return try {
            val running = ProcessBuilder(
                "logcat",
                "-d",
                "-v",
                "epoch",
                "-t",
                "500",
                "--pid=${android.os.Process.myPid()}",
            )
                .redirectErrorStream(true)
                .start()
            process = running
            val output = java.util.Collections.synchronizedList(mutableListOf<String>())
            val reader = Thread {
                runCatching {
                    running.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { output.add(it) }
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
            if (!running.waitFor(LOGCAT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                running.destroy()
            }
            reader.join(300)
            synchronized(output) { output.toList() }
        } catch (e: Exception) {
            Log.w("FastReport", "Could not read logcat for the report", e)
            emptyList()
        } finally {
            runCatching { process?.destroy() }
        }
    }
}

data class FastReportExport(
    val shared: Boolean,
    val file: File?,
    val error: String? = null,
)

object FastReportExporter {

    private const val MAX_REPORT_FILES = 12

    /**
     * Builds the Markdown report for [crash] (or a manual snapshot when it is
     * null), writes it to the cache and opens the system share sheet.
     */
    fun exportAndShare(
        context: Context,
        crash: CapturedCrash?,
        activeSandboxes: List<String> = emptyList(),
        extraWarnErrorLogs: List<String> = emptyList(),
    ): FastReportExport {
        val file = try {
            createReport(context, crash, activeSandboxes, extraWarnErrorLogs)
        } catch (e: Exception) {
            Log.w("FastReport", "Could not write the fast report", e)
            return FastReportExport(shared = false, file = null, error = "write_failed")
        } ?: return FastReportExport(shared = false, file = null, error = "write_failed")

        val shared = shareReport(context, file)
        return FastReportExport(shared = shared, file = file)
    }

    fun createReport(
        context: Context,
        crash: CapturedCrash?,
        activeSandboxes: List<String> = emptyList(),
        extraWarnErrorLogs: List<String> = emptyList(),
    ): File? {
        val info = DevAppInfoCollector.collect(context)
        val logs = stableDistinct(
            extraWarnErrorLogs + (crash?.logs ?: emptyList()) + FastReportLogs.readWarnErrorLogs(),
        ).takeLast(300)
        val markdown = buildFastReportMarkdown(
            info = info,
            crash = crash,
            warnErrorLogs = logs,
            activeSandboxes = activeSandboxes,
            generatedAtMs = System.currentTimeMillis(),
            discordUrl = DevUiOverrider.TOOLZ_DISCORD_URL,
            languageTag = Locale.getDefault().toLanguageTag(),
        )
        val dir = File(context.cacheDir, "fast_reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date(System.currentTimeMillis()))
        val file = File(dir, "toolz-fast-report-$stamp.md")
        file.writeText(markdown, Charsets.UTF_8)
        prune(dir)
        return file
    }

    fun shareReport(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                // text/plain has the widest handler coverage; the shared file
                // itself still keeps its .md name and Markdown contents.
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Toolz fast report")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Toolz fast report\n\n" +
                        "Join the Toolz Discord and drop this .md file in the bug-reports channel:\n" +
                        DevUiOverrider.TOOLZ_DISCORD_URL,
                )
                clipData = ClipData.newRawUri("Toolz fast report", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, "Send Toolz fast report")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: Exception) {
            Log.w("FastReport", "No app accepted the fast report file", e)
            // Last resort: share the text only, so the diagnostic still has a
            // route off the device on unusual ROMs with broken file providers.
            runCatching {
                val markdown = file.readText(Charsets.UTF_8)
                DevUiOverrider.shareText(context, "Send Toolz fast report", markdown)
            }.getOrDefault(false)
        }
    }

    private fun stableDistinct(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>(lines.size)
        lines.forEach { line ->
            val trimmed = line.trimEnd()
            if (trimmed.isNotBlank()) seen += trimmed
        }
        return seen.toList()
    }

    private fun prune(dir: File) {
        runCatching {
            val reports = dir.listFiles { file -> file.isFile && file.name.endsWith(".md") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
            reports.drop(MAX_REPORT_FILES).forEach { it.delete() }
        }
    }
}
