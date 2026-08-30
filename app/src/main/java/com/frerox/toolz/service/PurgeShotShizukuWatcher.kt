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

package com.frerox.toolz.service

import android.content.Context
import android.util.Log
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import com.frerox.toolz.util.shizuku.ShellOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privileged file watcher via Shizuku.
 *
 * When Shizuku is authorized, this holds a long-running `inotifywait`
 * on the standard screenshot directories via the privileged shell.
 * inotify is kernel-level and fires instantly even when Toolz is dead,
 * unlike app-process ContentObserver which dies with the process.
 *
 * Fallback: if inotifywait binary is missing (some OEMs), poll `ls -t`.
 */
@Singleton
class PurgeShotShizukuWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val executor: ShizukuShellExecutor,
    private val repository: PurgeShotRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watcherJob: Job? = null
    private val _isWatching = MutableStateFlow(false)
    val isWatching: StateFlow<Boolean> = _isWatching

    private var lastTriggerMs = 0L
    private var lastSeenFile: String? = null

    companion object {
        private const val TAG = "PurgeShotShizuku"
        private const val DEBOUNCE_MS = 700L
        private val DIRS = listOf(
            // Standard paths (Pixel, AOSP)
            "/sdcard/Pictures/Screenshots",
            "/sdcard/DCIM/Screenshots",
            "/storage/emulated/0/Pictures/Screenshots",
            "/storage/emulated/0/DCIM/Screenshots",
            // Samsung (Galaxy): stores under DCIM/Pictures or DCIM/Screenshots
            "/storage/emulated/0/DCIM/Pictures",
            "/sdcard/DCIM/Pictures",
            // Xiaomi / MIUI: sometimes stores under Pictures or DCIM directly
            "/storage/emulated/0/Pictures",
            "/sdcard/Pictures",
            "/sdcard/DCIM"
        )
    }

    fun start() {
        if (watcherJob?.isActive == true) {
            Log.d(TAG, "already watching")
            return
        }
        watcherJob = scope.launch {
            runWatcherLoop()
        }
    }

    fun stop() {
        watcherJob?.cancel()
        watcherJob = null
        _isWatching.value = false
        Log.i(TAG, "stopped")
    }

    /**
     * Restarts the watcher if it is not currently active.
     * Called by [PurgeShotAccessibilityService] and [PurgeShotObserverJobService] when they
     * wake due to a cold process start — the watcher's coroutine died with the previous
     * process instance and must be re-launched before Shizuku file events flow again.
     */
    fun restartIfNeeded() {
        if (watcherJob?.isActive != true) {
            Log.i(TAG, "restartIfNeeded: starting watcher")
            start()
        }
    }

    private suspend fun runWatcherLoop() {
        try {
            // CRITICAL FIX: default to true on DataStore read failure (fail open).
            // When the process is cold-started by an accessibility event or JobScheduler,
            // DataStore may not be initialized yet and first() throws. Defaulting to false
            // caused the watcher to silently abort on every cold start.
            val enabled = try { settingsRepository.purgeShotEnabled.first() } catch (_: Exception) { true }
            if (!enabled) {
                Log.d(TAG, "purgeShot disabled, not starting shizuku watcher")
                _isWatching.value = false
                return
            }
            if (!ShizukuHelper.isAuthorized() && !executor.isShizukuAvailable()) {
                Log.w(TAG, "Shizuku not available/authorized")
                _isWatching.value = false
                return
            }
            if (!executor.ensureService()) {
                Log.w(TAG, "Shizuku service bind failed")
                _isWatching.value = false
                // fallback to poll even without service? try poll via ShizukuHelper? For now just return
                return
            }
            // Check inotifywait exists
            val check = try { executor.executeForResult("which inotifywait", 3000) } catch (e: Exception) { null }
            val hasInotify = check?.isSuccess == true && check.stdout.isNotBlank()
            Log.i(TAG, "hasInotify=$hasInotify check=${check?.stdout}")

            _isWatching.value = true
            if (hasInotify) {
                runInotify()
            } else {
                runPolling()
            }
        } catch (e: Exception) {
            Log.w(TAG, "runWatcherLoop failed", e)
            _isWatching.value = false
            // exponential backoff retry if still enabled
            delay(5000)
            if (watcherJob?.isActive == true) {
                try {
                val enabled = try { settingsRepository.purgeShotEnabled.first() } catch (_: Exception) { true }
                    if (enabled) runWatcherLoop()
                } catch (_: Exception) {}
            }
        }
    }

    private suspend fun runInotify() {
        val dirs = DIRS.joinToString(" ")
        val cmd = "inotifywait -m -e create -e close_write -e moved_to --format \"%w%f:%e\" $dirs 2>&1"
        Log.i(TAG, "starting inotifywait: $cmd")
        try {
            executor.execute(cmd).collect { output ->
                when (output) {
                    is ShellOutput.StdOut -> {
                        val line = output.line.trim()
                        if (line.isBlank()) return@collect
                        // format: /sdcard/Pictures/Screenshots/xxx.png:CREATE or CLOSE_WRITE
                        val path = line.substringBefore(":").trim()
                        val fileName = path.substringAfterLast("/")
                        if (fileName.isBlank()) return@collect
                        if (!looksLikeScreenshotName(fileName)) return@collect
                        val now = System.currentTimeMillis()
                        if (now - lastTriggerMs < DEBOUNCE_MS) return@collect
                        lastTriggerMs = now
                        Log.i(TAG, "inotify event $line")
                        handleTrigger("shizuku:inotify:$fileName")
                    }
                    is ShellOutput.StdErr -> {
                        val err = output.line
                        if (err.contains("inotifywait", ignoreCase = true) || err.contains("No such file")) {
                            Log.w(TAG, "inotify stderr $err -> fallback to polling")
                            // switch to polling
                            throw Exception("inotify failed: $err")
                        }
                        Log.d(TAG, "inotify stderr $err")
                    }
                    is ShellOutput.Exit -> {
                        Log.w(TAG, "inotify exit code ${output.code}, restarting as polling")
                        throw Exception("inotify exit ${output.code}")
                    }
                    is ShellOutput.Error -> {
                        Log.w(TAG, "inotify error ${output.message}")
                        throw Exception(output.message)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "inotify failed, fallback to polling", e)
            runPolling()
        }
    }

    private suspend fun runPolling() {
        Log.i(TAG, "starting polling fallback every 2s")
        while (watcherJob?.isActive == true) {
            try {
                val enabled = try { settingsRepository.purgeShotEnabled.first() } catch (_: Exception) { true }
                if (!enabled) {
                    Log.d(TAG, "polling: disabled, stopping")
                    break
                }
                // ls -t sorts by mtime newest first
                val cmd = "ls -t /sdcard/Pictures/Screenshots 2>/dev/null | head -n 1; ls -t /sdcard/DCIM/Screenshots 2>/dev/null | head -n 1; ls -t /storage/emulated/0/Pictures/Screenshots 2>/dev/null | head -n 1"
                val result = executor.executeForResult(cmd, 4000)
                val out = result.stdout
                val candidates = out.lines().map { it.trim() }.filter { it.isNotBlank() }
                for (name in candidates) {
                    if (!looksLikeScreenshotName(name)) continue
                    if (name == lastSeenFile) continue
                    // check freshness via stat if possible
                    val statCmd = "stat -c %Y \"/sdcard/Pictures/Screenshots/$name\" 2>/dev/null || stat -c %Y \"/sdcard/DCIM/Screenshots/$name\" 2>/dev/null || echo 0"
                    val stat = executor.executeForResult(statCmd, 3000)
                    val tsSec = stat.stdout.trim().toLongOrNull() ?: 0L
                    val ageMs = if (tsSec > 0) System.currentTimeMillis() - tsSec * 1000 else 0L
                    // Only trigger if fresh (within 10s for polling, more lenient than real-time 90s but avoid old)
                    if (tsSec > 0 && ageMs > 15000) continue
                    lastSeenFile = name
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerMs < DEBOUNCE_MS) continue
                    lastTriggerMs = now
                    Log.i(TAG, "polling detected $name ageMs=$ageMs")
                    handleTrigger("shizuku:poll:$name")
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "polling iteration failed", e)
            }
            delay(2000)
        }
        _isWatching.value = false
    }

    private suspend fun handleTrigger(source: String) {
        try {
            Log.i(TAG, "handleTrigger from $source")
            val ok = PurgeShotDetector.detectAndHandle(
                context = context,
                repository = repository,
                settingsRepository = settingsRepository,
                awaitSettle = true,
                isPoll = source.contains("poll")
            )
            Log.i(TAG, "detectAndHandle from $source result=$ok")
        } catch (e: Exception) {
            Log.w(TAG, "handleTrigger failed for $source", e)
        }
    }

    private fun looksLikeScreenshotName(name: String): Boolean {
        val lower = name.lowercase()
        // Explicit parentheses to avoid the && vs || precedence trap:
        // previously `contains("screen") && contains("shot")` was OR'd without parens,
        // which Kotlin evaluates differently from the intended grouping.
        return lower.contains("screenshot") ||
            lower.contains("screencap") ||
            lower.contains("screen_capture") ||
            lower.contains("screengrab") ||
            (lower.contains("screen") && lower.contains("shot")) ||
            lower.matches(Regex(".*screenshot.*\\.(png|jpg|jpeg|webp)"))
    }
}
