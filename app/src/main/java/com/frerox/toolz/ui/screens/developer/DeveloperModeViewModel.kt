/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Developer console ViewModel. Everything here stays LOCAL: the app ring
 * buffer, PID-filtered logcat, Room counts, DataStore snapshot and the
 * diagnostic report. Nothing is uploaded; text leaves only through the
 * clipboard or the system share sheet when the user explicitly asks.
 *
 * Sandboxes are local fixtures. The master switch never touches real data;
 * turning it off makes every tool report live hardware immediately while
 * keeping the per-tool selection for the next session.
 */

package com.frerox.toolz.ui.screens.developer

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.data.developer.CrashLabEvent
import com.frerox.toolz.data.developer.DevAppInfo
import com.frerox.toolz.data.developer.DevLogHub
import com.frerox.toolz.data.developer.DevMessage
import com.frerox.toolz.data.developer.DevPrefsState
import com.frerox.toolz.data.developer.DevShizukuState
import com.frerox.toolz.data.developer.DevStorageInfo
import com.frerox.toolz.data.developer.DevStorageState
import com.frerox.toolz.data.developer.DevTableCount
import com.frerox.toolz.data.developer.DevTableState
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.DevUiOverrider
import com.frerox.toolz.data.developer.LogcatLevel
import com.frerox.toolz.data.developer.LogcatScope
import com.frerox.toolz.data.developer.LogcatSeverity
import com.frerox.toolz.data.developer.MAX_LOGCAT_LINES
import com.frerox.toolz.data.developer.SpoofGrid
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.data.developer.buildDiagnosticReport
import com.frerox.toolz.data.developer.filterLogcatLines
import com.frerox.toolz.data.developer.filterLogcatSince
import com.frerox.toolz.data.developer.formatBytes
import com.frerox.toolz.data.developer.mergeLogcatLines
import com.frerox.toolz.data.developer.parseCrashHistory
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Result wrapper for coroutine work: ordinary failures become a [Result],
 * while cancellation is rethrown so a cleared/recreated ViewModel never
 * reports a stale operation as failed.
 */
private inline fun <T> devCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

@HiltViewModel
class DeveloperModeViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val database: AppDatabase,
    private val shizukuExecutor: ShizukuShellExecutor,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // ---- Sandboxes ---------------------------------------------------------

    /** Null until DataStore has emitted, so the lock screen never flashes. */
    val devModeEnabled: StateFlow<Boolean?> = repository.developerModeEnabled
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Effective state per tool (master ON + per-tool flag). */
    val spoofSnapshot: StateFlow<Map<DevToolId, Boolean>> = combine(
        repository.developerModeEnabled,
        repository.allSpoofFlags,
    ) { master, flags -> SpoofGrid(master, flags) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val spoofParams: StateFlow<SpoofParams> = repository.spoofParamsJson
        .map(SpoofParams::parse)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpoofParams())

    val devShakeToOpen: StateFlow<Boolean> = repository.devShakeToOpen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val devCrashOnNextLaunch: StateFlow<Boolean> = repository.devCrashOnNextLaunch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---- Appearance readout ------------------------------------------------

    val themeMode: StateFlow<String> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "SYSTEM")

    val appLanguage: StateFlow<String> = repository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "en")

    // ---- Crash lab ---------------------------------------------------------

    val crashHistory: StateFlow<List<CrashLabEvent>> = repository.devCrashHistoryJson
        .map(::parseCrashHistory)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Diagnostics -------------------------------------------------------

    private val _message = MutableStateFlow<DevMessage?>(null)
    val message: StateFlow<DevMessage?> = _message.asStateFlow()
    private var nextMessageId = 0L

    private fun postMessage(text: String) {
        nextMessageId += 1
        _message.value = DevMessage(nextMessageId, text)
    }

    fun consumeMessage() {
        _message.value = null
    }

    private val _heapHistory = MutableStateFlow<List<Float>>(emptyList())
    val heapHistory: StateFlow<List<Float>> = _heapHistory.asStateFlow()

    val appInfo: StateFlow<DevAppInfo> = flow {
        while (true) {
            val info = collectAppInfo()
            _heapHistory.value = (_heapHistory.value + info.heapUsedFraction).takeLast(48)
            emit(info)
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), collectAppInfo())

    private fun collectAppInfo(): DevAppInfo {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val max = runtime.maxMemory().coerceAtLeast(1L)
        val manufacturer = Build.MANUFACTURER
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            .trim()
        val model = Build.MODEL.trim()
        val device = when {
            manufacturer.isBlank() -> model
            model.isBlank() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        return DevAppInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            packageName = context.packageName,
            pid = Process.myPid(),
            processUptimeMs = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()).coerceAtLeast(0L),
            heapUsedBytes = used,
            heapMaxBytes = max,
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L),
            threads = Thread.activeCount(),
            heapUsedFraction = (used.toFloat() / max.toFloat()).coerceIn(0f, 1f),
            deviceLabel = device.ifBlank { "Unknown device" },
            androidLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            buildType = BuildConfig.BUILD_TYPE,
            densityDpi = context.resources.displayMetrics.densityDpi,
            debuggable = BuildConfig.DEBUG,
        )
    }

    // ---- Logcat ------------------------------------------------------------

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    private val _logcatTail = MutableStateFlow(true)
    val logcatTail: StateFlow<Boolean> = _logcatTail.asStateFlow()

    private val _logcatScope = MutableStateFlow(LogcatScope.APP)
    val logcatScope: StateFlow<LogcatScope> = _logcatScope.asStateFlow()

    private val _logcatClearedAtMs = MutableStateFlow<Long?>(null)
    val logcatClearedAtMs: StateFlow<Long?> = _logcatClearedAtMs.asStateFlow()

    private val _shizukuState = MutableStateFlow(DevShizukuState())
    val shizukuState: StateFlow<DevShizukuState> = _shizukuState.asStateFlow()

    @Volatile private var logClearCutoffMs: Long? = null
    @Volatile private var logGeneration = 0L
    private var logcatVisible = false
    private var tailJob: Job? = null
    private val logFetchMutex = Mutex()
    private val spoofParamsMutex = Mutex()

    init {
        checkShizukuStatus()
        refreshNotificationsEnabled()
        refreshLogcat()
    }

    fun checkShizukuStatus() {
        val installed = ShizukuHelper.isInstalled(context)
        val available = ShizukuHelper.isAvailable()
        val authorized = ShizukuHelper.isAuthorized()
        _shizukuState.value = DevShizukuState(
            installed = installed,
            available = available,
            authorized = authorized,
        )
        if (!authorized && _logcatScope.value == LogcatScope.SYSTEM) {
            _logcatScope.value = LogcatScope.APP
        }
    }

    fun requestShizukuPermission() {
        val state = _shizukuState.value
        if (!state.installed) {
            postMessage("Shizuku is not installed")
            return
        }
        if (!state.available) {
            postMessage("Start the Shizuku service, then try again")
            return
        }
        if (state.authorized) {
            postMessage("Shizuku is already authorized")
            return
        }
        try {
            ShizukuHelper.requestAuthorization { granted ->
                viewModelScope.launch(Dispatchers.Main) {
                    checkShizukuStatus()
                    postMessage(if (granted) "Shizuku authorized" else "Shizuku permission denied")
                }
            }
        } catch (e: Exception) {
            DevLogHub.w(DevLogHub.TAG, "Shizuku permission request failed", e)
            postMessage("Could not request Shizuku permission")
        }
    }

    /**
     * The tail is scoped to the visible Diagnostics tab. The screen calls this
     * on tab changes and lifecycle pause/resume; [setLogcatTail] alone can
     * never start a background stream from another tab.
     */
    fun setLogcatVisible(visible: Boolean) {
        logcatVisible = visible
        if (visible) startLogcat() else stopLogcat()
    }

    private fun startLogcat() {
        checkShizukuStatus()
        if (!logcatVisible || !_logcatTail.value) return
        if (tailJob?.isActive == true) return
        tailJob = viewModelScope.launch {
            while (isActive) {
                fetchLogcat()
                delay(1_000)
            }
        }
    }

    private fun stopLogcat() {
        tailJob?.cancel()
        tailJob = null
    }

    fun setLogcatTail(enabled: Boolean) {
        _logcatTail.value = enabled
        if (enabled) {
            startLogcat()
            postMessage("Live log tail resumed")
        } else {
            stopLogcat()
            postMessage("Live log tail paused")
        }
    }

    fun setLogcatScope(scope: LogcatScope) {
        if (scope == LogcatScope.SYSTEM && !_shizukuState.value.authorized) {
            postMessage("Authorize Shizuku to read the full system log")
            return
        }
        _logcatScope.value = scope
        refreshLogcat()
    }

    fun refreshLogcat() {
        viewModelScope.launch { fetchLogcat() }
    }

    fun clearLogcat() {
        val now = System.currentTimeMillis()
        // Keep lines produced after the tap (same millisecond included) while
        // dropping every line that existed in the system buffer beforehand.
        // Cutoff first, then the generation bump: a fetch that captures the new
        // generation is guaranteed to observe the cutoff.
        logClearCutoffMs = now - 1L
        logGeneration += 1
        _logcatClearedAtMs.value = now
        DevLogHub.clear()
        _logLines.value = emptyList()
        viewModelScope.launch {
            if (_shizukuState.value.authorized) {
                devCatching {
                    shizukuExecutor.executeForResult("logcat -c", timeoutMs = 2_000)
                }.onSuccess { result ->
                    if (result.exitCode != 0) {
                        DevLogHub.w(
                            DevLogHub.TAG,
                            "logcat -c exited ${result.exitCode}: ${result.stderr.take(160)}",
                        )
                    }
                }.onFailure {
                    DevLogHub.w(DevLogHub.TAG, "logcat -c failed", it)
                }
            }
            fetchLogcat()
        }
        postMessage("Log stream cleared")
    }

    fun sendTestLog(level: LogcatLevel = LogcatLevel.INFO) {
        val suffix = "test ${level.label.lowercase(Locale.US)} entry"
        when (level) {
            LogcatLevel.VERBOSE -> DevLogHub.v("ToolzTest", suffix)
            LogcatLevel.DEBUG -> DevLogHub.d("ToolzTest", suffix)
            LogcatLevel.INFO -> DevLogHub.i("ToolzTest", suffix)
            LogcatLevel.WARN -> DevLogHub.w("ToolzTest", suffix)
            LogcatLevel.ERROR -> DevLogHub.e("ToolzTest", suffix, IllegalStateException("Sample developer exception"))
            LogcatLevel.FATAL -> DevLogHub.e("ToolzTest", "$suffix (fatal test marker)", IllegalStateException("Fatal test marker"))
        }
        refreshLogcat()
        postMessage("Added ${level.label} test entry")
    }

    fun logNonFatal() {
        DevLogHub.e(
            "ToolzTest",
            "Handled non-fatal test exception",
            IllegalStateException("Sample non-fatal developer issue"),
        )
        refreshLogcat()
        postMessage("Logged a handled exception")
    }

    fun copyAllLogs() {
        val lines = _logLines.value
        if (lines.isEmpty()) {
            postMessage("The log stream is empty")
            return
        }
        copyText("Logcat", lines.joinToString("\n"))
    }

    fun copyWarningsAndErrors() {
        val lines = filterLogcatLines(_logLines.value, severity = LogcatSeverity.WARNINGS)
        if (lines.isEmpty()) {
            postMessage("No warnings or errors in the current stream")
            return
        }
        copyText("Warnings and errors", lines.joinToString("\n"))
    }

    private suspend fun fetchLogcat() = withContext(Dispatchers.IO) {
        logFetchMutex.withLock {
            val generation = logGeneration
            val shizuku = _shizukuState.value
            val scope = _logcatScope.value
            val groups = mutableListOf<List<String>>()
            groups += DevLogHub.getLines()

            var fetched = false
            if (shizuku.authorized) {
                val command = if (scope == LogcatScope.SYSTEM) {
                    "logcat -d -v epoch -t 350"
                } else {
                    "logcat -d -v epoch -t 250 --pid=${Process.myPid()}"
                }
                try {
                    val result = shizukuExecutor.executeForResult(command, timeoutMs = 2_500)
                    if (result.exitCode == 0) {
                        groups += result.stdout.lines()
                        fetched = true
                    } else {
                        val detail = result.stderr.take(160).ifBlank { "exit ${result.exitCode}" }
                        DevLogHub.w(DevLogHub.TAG, "Shizuku logcat: $detail")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DevLogHub.w(DevLogHub.TAG, "Shizuku logcat failed", e)
                }
            }

            if (!fetched) {
                groups += readProcessLogcat()
            }

            val merged = filterLogcatSince(
                mergeLogcatLines(groups, maxLines = MAX_LOGCAT_LINES),
                logClearCutoffMs,
            )
            withContext(Dispatchers.Main) {
                // A clear may have landed while this snapshot was in flight;
                // never let pre-clear lines reappear after the stream is cleared.
                if (generation == logGeneration) {
                    _logLines.value = merged
                }
            }
        }
    }

    private fun readProcessLogcat(): List<String> {
        var process: java.lang.Process? = null
        return try {
            val running = ProcessBuilder(
                "logcat",
                "-d",
                "-v",
                "epoch",
                "-t",
                "250",
                "--pid=${Process.myPid()}",
            )
                // Drain stderr into the same pipe so a failing logcat command
                // can never block on a full error buffer while we read stdout.
                .redirectErrorStream(true)
                .start()
            process = running
            val output = ConcurrentLinkedQueue<String>()
            val reader = Thread {
                try {
                    running.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { output.add(it) }
                    }
                } catch (_: Exception) {
                    // The process may be destroyed on timeout; partial output is
                    // discarded rather than shown as a truncated log.
                }
            }.apply {
                isDaemon = true
                start()
            }
            // `logcat -d` should finish immediately. A watchdog prevents a
            // wedged child process from holding the fetch mutex forever.
            if (!running.waitFor(5, TimeUnit.SECONDS)) {
                running.destroy()
                reader.join(500)
                return emptyList()
            }
            reader.join(500)
            if (reader.isAlive || running.exitValue() != 0) emptyList()
            else output.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        } finally {
            process?.destroy()
        }
    }

    // ---- Diagnostics report ------------------------------------------------

    private fun currentActiveTools(): List<String> =
        spoofSnapshot.value.filterValues { it }.keys.map { it.title }.sorted()

    fun buildBugReport(info: DevAppInfo = appInfo.value): String = buildDiagnosticReport(
        info = info,
        activeSandboxes = currentActiveTools(),
        crashes = crashHistory.value,
        generatedAtMs = System.currentTimeMillis(),
        themeMode = themeMode.value,
        languageTag = appLanguage.value,
    )

    fun copyBugReport(info: DevAppInfo = appInfo.value) {
        copyText("Diagnostic report", buildBugReport(info))
    }

    fun shareBugReport(info: DevAppInfo = appInfo.value) {
        val opened = DevUiOverrider.shareText(
            context,
            "Share Toolz diagnostic report",
            buildBugReport(info),
        )
        if (opened) {
            postMessage("Opened the share sheet")
        } else {
            postMessage("No app can open the share sheet")
        }
    }

    fun copyText(label: String, text: String) {
        try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            postMessage("$label copied")
        } catch (e: Exception) {
            DevLogHub.w(DevLogHub.TAG, "Clipboard write failed", e)
            postMessage("Could not copy to the clipboard")
        }
    }

    // ---- Notification test matrix -----------------------------------------

    private val _notificationsEnabled = MutableStateFlow(areNotificationsEnabledNow())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _postedNotificationIds = MutableStateFlow<Set<Int>>(emptySet())
    val postedNotificationIds: StateFlow<Set<Int>> = _postedNotificationIds.asStateFlow()

    fun refreshNotificationsEnabled() {
        _notificationsEnabled.value = areNotificationsEnabledNow()
        refreshPostedNotifications()
    }

    private fun refreshPostedNotifications() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val active = devCatching {
            manager?.activeNotifications
                ?.asSequence()
                ?.map { it.id }
                ?.filter(::isDevNotificationId)
                ?.toSet()
                .orEmpty()
        }.getOrElse {
            DevLogHub.w(DevLogHub.TAG, "Could not read active notifications", it)
            emptySet()
        }
        _postedNotificationIds.value = active
    }

    private fun areNotificationsEnabledNow(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return permissionGranted && (manager?.areNotificationsEnabled() ?: true)
    }

    fun sendTestNotification(test: DevNotificationTest) {
        refreshNotificationsEnabled()
        if (!_notificationsEnabled.value) {
            postMessage("Notifications are blocked; open system settings first")
            return
        }
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            NotificationHelper.createAllChannels(context)
            if (manager.getNotificationChannel(test.channelId)?.importance ==
                NotificationManager.IMPORTANCE_NONE
            ) {
                postMessage("${test.label} channel is blocked; open notification settings")
                return
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val contentIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    test.id,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val notification = NotificationHelper.baseBuilder(context, test.channelId)
                .setContentTitle(test.title)
                .setContentText(test.body)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            manager.notify(test.id, notification)
            refreshPostedNotifications()
            postMessage("Posted ${test.label} test notification")
        } catch (e: Exception) {
            DevLogHub.w(DevLogHub.TAG, "Test notification failed", e)
            postMessage("Could not post the test notification")
        }
    }

    fun cancelDevNotifications() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (id in DEV_NOTIFICATION_ID_START..DEV_NOTIFICATION_ID_END) {
                manager.cancel(id)
            }
            refreshPostedNotifications()
            postMessage("Test notifications cleared")
        } catch (e: Exception) {
            DevLogHub.w(DevLogHub.TAG, "Cancelling test notifications failed", e)
            postMessage("Could not clear test notifications")
        }
    }

    // ---- Sandbox actions ---------------------------------------------------

    /**
     * Master switch only. Per-tool flags are deliberately preserved so the
     * previous selection returns when Developer Mode is switched back on.
     */
    fun setDevMode(enabled: Boolean) = viewModelScope.launch {
        devCatching { repository.setDeveloperModeEnabled(enabled) }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "Developer Mode master -> $enabled")
                postMessage(
                    if (enabled) "Developer Mode enabled"
                    else "Developer Mode off — sandbox selections kept",
                )
            }
            .onFailure {
                DevLogHub.e(DevLogHub.TAG, "Developer Mode switch failed", it)
                postMessage("Could not update Developer Mode")
            }
    }

    fun setSpoof(tool: DevToolId, enabled: Boolean) = viewModelScope.launch {
        devCatching { repository.setSpoofFlag(tool.prefKey, enabled) }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "${tool.title} sandbox -> ${if (enabled) "ON" else "OFF"}")
                postMessage("${tool.title} sandbox ${if (enabled) "enabled" else "disabled"}")
            }
            .onFailure {
                DevLogHub.e(DevLogHub.TAG, "Sandbox toggle failed for ${tool.title}", it)
                postMessage("Could not update ${tool.title}")
            }
    }

    /** Stable action name used by the console and command palette. */
    fun spoofAll() = viewModelScope.launch {
        devCatching { repository.setDeveloperModeAndAllSpoofs(true) }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "All ${DevToolId.entries.size} sandboxes enabled")
                postMessage("All ${DevToolId.entries.size} sandboxes enabled")
            }
            .onFailure {
                DevLogHub.e(DevLogHub.TAG, "Enable-all failed", it)
                postMessage("Could not enable every sandbox")
            }
    }

    fun clearAllSpoofs() = viewModelScope.launch {
        devCatching { repository.clearAllSpoofs() }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "All sandboxes returned to live data")
                postMessage("All sandboxes returned to live data")
            }
            .onFailure {
                DevLogHub.e(DevLogHub.TAG, "Disable-all failed", it)
                postMessage("Could not disable the sandboxes")
            }
    }

    fun applyPreset(preset: DevPreset) = viewModelScope.launch {
        devCatching {
            spoofParamsMutex.withLock {
                repository.applySandboxSelection(preset.prefKeys, preset.params.toJson())
            }
        }.onSuccess {
            DevLogHub.i(DevLogHub.TAG, "Preset applied: ${preset.title} (${preset.toolCount} sandboxes)")
            postMessage("${preset.title} preset applied · ${preset.toolCount} sandboxes")
        }.onFailure {
            DevLogHub.e(DevLogHub.TAG, "Preset ${preset.title} failed", it)
            postMessage("Could not apply the ${preset.title} preset")
        }
    }

    fun resetAll() = viewModelScope.launch {
        devCatching { repository.resetDeveloperMode() }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "Developer Mode reset")
                postMessage("Developer Mode reset · shake-to-open and fixture values kept")
            }
            .onFailure {
                DevLogHub.e(DevLogHub.TAG, "Developer Mode reset failed", it)
                postMessage("Could not reset Developer Mode")
            }
    }

    fun resetSpoofParams() = viewModelScope.launch {
        devCatching {
            spoofParamsMutex.withLock {
                repository.setSpoofParamsJson(SpoofParams().toJson())
            }
        }.onSuccess {
            DevLogHub.i(DevLogHub.TAG, "Fixture values reset to defaults")
            postMessage("Fixture values reset to defaults")
        }.onFailure {
            DevLogHub.e(DevLogHub.TAG, "Fixture reset failed", it)
            postMessage("Could not reset fixture values")
        }
    }

    fun updateSpoofParams(transform: (SpoofParams) -> SpoofParams) = viewModelScope.launch {
        spoofParamsMutex.withLock {
            val raw = try {
                repository.spoofParamsJson.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevLogHub.e(DevLogHub.TAG, "Could not read fixture values", e)
                postMessage("Could not read fixture values")
                return@withLock
            }
            val current = SpoofParams.parse(raw)
            devCatching { repository.setSpoofParamsJson(transform(current).toJson()) }
                .onFailure {
                    DevLogHub.e(DevLogHub.TAG, "Fixture parameter write failed", it)
                    postMessage("Could not save fixture values")
                }
        }
    }

    fun setShakeToOpen(enabled: Boolean) = viewModelScope.launch {
        devCatching { repository.setDevShakeToOpen(enabled) }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "Shake-to-open -> $enabled")
                postMessage(if (enabled) "Shake-to-open enabled" else "Shake-to-open disabled")
            }
            .onFailure { postMessage("Could not update shake-to-open") }
    }

    fun setCrashOnNextLaunch(enabled: Boolean) = viewModelScope.launch {
        devCatching { repository.setDevCrashOnNextLaunch(enabled) }
            .onSuccess {
                DevLogHub.w(DevLogHub.TAG, "Crash-on-next-launch -> $enabled")
                postMessage(
                    if (enabled) "The app will crash on the next launch"
                    else "Crash-on-next-launch disabled",
                )
            }
            .onFailure { postMessage("Could not update crash-on-next-launch") }
    }

    // ---- Crash lab ---------------------------------------------------------

    private val oomHog = ArrayList<ByteArray>()

    fun triggerCrash(test: DevCrashTest) {
        postMessage("Recording ${test.title} test event…")
        viewModelScope.launch {
            val recorded = devCatching {
                repository.appendDevCrashHistoryEvent(
                    type = test.historyType,
                    atMs = System.currentTimeMillis(),
                    note = "Manual test trigger",
                )
            }
            if (recorded.isFailure) {
                DevLogHub.e(
                    DevLogHub.TAG,
                    "Could not record crash-lab event; test aborted",
                    recorded.exceptionOrNull(),
                )
                postMessage("Could not record the test event · crash aborted")
                return@launch
            }
            DevLogHub.e(DevLogHub.TAG, "Crash-lab test triggering: ${test.historyType}")
            withContext(Dispatchers.Main) {
                // Let DataStore commit and the snackbar render before the process dies.
                Handler(Looper.getMainLooper()).postDelayed({ performCrash(test) }, 350)
            }
        }
    }

    private fun performCrash(test: DevCrashTest): Nothing? {
        when (test) {
            DevCrashTest.NPE -> throw NullPointerException("[DEV TEST] Simulated NPE on the main thread")
            DevCrashTest.ILLEGAL_STATE ->
                throw IllegalStateException("[DEV TEST] Simulated IllegalStateException on the main thread")
            DevCrashTest.ANR -> {
                Thread.sleep(10_000)
                throw IllegalStateException("[DEV TEST] Main thread blocked for 10 seconds")
            }
            DevCrashTest.OOM -> {
                while (true) {
                    oomHog.add(ByteArray(8 * 1024 * 1024))
                }
            }
        }
    }

    fun clearCrashHistory() = viewModelScope.launch {
        devCatching { repository.clearDevCrashHistory() }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "Crash-lab history cleared")
                postMessage("Crash-lab history cleared")
            }
            .onFailure { postMessage("Could not clear crash history") }
    }

    // ---- Database & storage ------------------------------------------------

    private val _tableState = MutableStateFlow(DevTableState())
    val tableState: StateFlow<DevTableState> = _tableState.asStateFlow()

    private val _storageState = MutableStateFlow(DevStorageState())
    val storageState: StateFlow<DevStorageState> = _storageState.asStateFlow()

    private val _prefsState = MutableStateFlow(DevPrefsState())
    val prefsState: StateFlow<DevPrefsState> = _prefsState.asStateFlow()

    private var tableJob: Job? = null
    private var storageJob: Job? = null
    private var prefsJob: Job? = null

    fun refreshTableCounts() {
        tableJob?.cancel()
        _tableState.value = _tableState.value.copy(loading = true, error = null)
        tableJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val rows = readTableCounts()
                withContext(Dispatchers.Main) {
                    _tableState.value = DevTableState(tables = rows)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevLogHub.w(DevLogHub.TAG, "Database inspection failed", e)
                withContext(Dispatchers.Main) {
                    _tableState.value = DevTableState(
                        tables = emptyList(),
                        error = e.message ?: "Could not read the database",
                    )
                }
            }
        }
    }

    private suspend fun readTableCounts(): List<DevTableCount> {
        val db = database.openHelper.readableDatabase
        val counts = mutableListOf<DevTableCount>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' " +
                "AND name NOT LIKE 'android_%' ORDER BY name",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(0)
                val rows = try {
                    val identifier = "\"" + table.replace("\"", "\"\"") + "\""
                    db.query("SELECT COUNT(*) FROM $identifier").use { countCursor ->
                        if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
                    }
                } catch (_: Exception) {
                    -1
                }
                counts += DevTableCount(name = table, rows = rows)
            }
        }
        return counts
    }

    fun clearDatabase() {
        tableJob?.cancel()
        tableJob = null
        viewModelScope.launch(Dispatchers.IO) {
            _tableState.value = _tableState.value.copy(loading = true)
            try {
                database.clearAllTables()
                DevLogHub.w(DevLogHub.TAG, "All Room tables cleared from Developer Mode")
                withContext(Dispatchers.Main) {
                    postMessage("All database tables cleared")
                }
                refreshTableCounts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevLogHub.e(DevLogHub.TAG, "Database clear failed", e)
                withContext(Dispatchers.Main) {
                    _tableState.value = _tableState.value.copy(loading = false)
                    postMessage("Could not clear the database")
                }
            }
        }
    }

    fun refreshStorageInfo() {
        storageJob?.cancel()
        _storageState.value = _storageState.value.copy(loading = true, error = null)
        storageJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = DevStorageInfo(
                    cacheBytes = directorySize(context.cacheDir),
                    filesBytes = directorySize(context.filesDir),
                    databaseBytes = devCatching {
                        val path = database.openHelper.readableDatabase.path
                        if (path == null) 0L else directorySize(File(path).parentFile)
                    }.getOrDefault(0L),
                )
                withContext(Dispatchers.Main) {
                    _storageState.value = DevStorageState(info = info)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevLogHub.w(DevLogHub.TAG, "Storage inspection failed", e)
                withContext(Dispatchers.Main) {
                    _storageState.value = DevStorageState(
                        error = e.message ?: "Could not read storage",
                    )
                }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val before = directorySize(context.cacheDir)
            var failures = 0
            try {
                context.cacheDir.listFiles()?.forEach { child ->
                    ensureActive()
                    val deleted = devCatching {
                        if (child.isDirectory) child.deleteRecursively() else child.delete()
                    }.getOrDefault(false)
                    if (!deleted) failures += 1
                }
                ensureActive()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevLogHub.e(DevLogHub.TAG, "Cache clear failed", e)
                withContext(Dispatchers.Main) { postMessage("Could not clear the cache") }
                return@launch
            }
            val remaining = directorySize(context.cacheDir)
            val freed = (before - remaining).coerceAtLeast(0L)
            DevLogHub.i(
                DevLogHub.TAG,
                "Cache clear removed ${formatBytes(freed)} ($failures failures)",
            )
            refreshStorageInfo()
            withContext(Dispatchers.Main) {
                postMessage(
                    when {
                        freed > 0L -> "Cache cleared · ${formatBytes(freed)} removed"
                        failures > 0 -> "Some cached files could not be removed"
                        else -> "No cache files were removed"
                    },
                )
            }
        }
    }

    private fun directorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    fun refreshPrefs() {
        prefsJob?.cancel()
        _prefsState.value = _prefsState.value.copy(loading = true, error = null)
        prefsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = repository.snapshotPrefs()
                withContext(Dispatchers.Main) {
                    _prefsState.value = DevPrefsState(values = snapshot)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DevLogHub.w(DevLogHub.TAG, "Preference inspection failed", e)
                withContext(Dispatchers.Main) {
                    _prefsState.value = DevPrefsState(
                        error = e.message ?: "Could not read preferences",
                    )
                }
            }
        }
    }

    fun resetOnboarding() = viewModelScope.launch {
        devCatching { repository.resetOnboarding() }
            .onSuccess {
                DevLogHub.i(DevLogHub.TAG, "Onboarding reset")
                postMessage("Onboarding will appear on the next launch")
            }
            .onFailure { postMessage("Could not reset onboarding") }
    }

    // ---- System actions ----------------------------------------------------

    fun openNotificationSettings() {
        val opened = DevUiOverrider.openAppNotificationSettings(context)
        postMessage(
            if (opened) {
                "Opening notification settings"
            } else {
                "Notification settings are unavailable on this device"
            },
        )
    }

    fun openSystemDeveloperOptions() {
        if (!DevUiOverrider.openSystemDeveloperOptions(context)) {
            postMessage("System Developer options are unavailable on this device")
        }
    }

    fun openDiscord() {
        if (!DevUiOverrider.openToolzDiscord(context)) {
            postMessage("No app can open the Discord invite")
        }
    }

    fun restartApp(activity: Activity?) {
        if (activity == null) {
            postMessage("Restart unavailable: no foreground activity")
            return
        }
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        if (intent == null) {
            postMessage("Restart unavailable: launch intent missing")
            return
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            activity.startActivity(intent)
            activity.finishAffinity()
            Runtime.getRuntime().exit(0)
        } catch (e: Exception) {
            DevLogHub.e(DevLogHub.TAG, "Restart failed", e)
            postMessage("Could not restart the app")
        }
    }
}
