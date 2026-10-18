/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Developer console ViewModel. Everything here stays LOCAL: logs are read
 * from local logcat, the bug report is built as text and only leaves the
 * device via the system share sheet / clipboard when the user chooses.
 * There is no telemetry of any kind.
 */

package com.frerox.toolz.ui.screens.developer

import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.data.developer.DevLogHub
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.DeveloperSpoofController
import com.frerox.toolz.data.developer.SpoofGrid
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject

data class DevAppInfo(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: String = BuildConfig.VERSION_CODE.toString(),
    val packageName: String = "com.frerox.toolz",
    val uptimeMs: Long = 0L,
    val heapMb: String = "",
    val nativeHeapMb: String = "",
    val threads: Int = 0,
    val heapUsedFraction: Float = 0f,
)

data class DevTableCount(val name: String, val rows: Int)

/** One crash-lab event. Diagnostics only — stored in DataStore, never uploaded. */
data class CrashLabEvent(
    val type: String,
    val atMs: Long,
    val note: String = "",
)

data class DevStorageInfo(
    val cacheBytes: Long = 0L,
    val filesBytes: Long = 0L,
) {
    fun format(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
    }
}

enum class DemoPreset(val title: String, val subtitle: String) {
    SHOWCASE("Showcase Demo", "Photogenic values for presentations & UI demos"),
    EDGE_CASES("Edge Cases & Stress", "Critical battery, zero network speed, loud noise"),
    TRAVEL("Travel & Offline", "Airplane mode, cruising altitude, offline sensors"),
}

enum class LogcatScope(val title: String) {
    APP("App (PID)"),
    SYSTEM("Full System"),
}

@HiltViewModel
class DeveloperModeViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val spoofController: DeveloperSpoofController,
    private val database: AppDatabase,
    private val shizukuExecutor: ShizukuShellExecutor,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val devModeEnabled = repository.developerModeEnabled
    val allSpoofFlags = repository.allSpoofFlags

    /** Effective sandbox state per tool (master ON + flag ON), hot. */
    val spoofSnapshot: StateFlow<Map<DevToolId, Boolean>> = combine(
        repository.developerModeEnabled,
        repository.allSpoofFlags,
    ) { master, flags -> SpoofGrid(master, flags) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val activeSandboxCount = spoofController.activeSandboxCount
    val devShakeToOpen = repository.devShakeToOpen
    val devCrashOnNextLaunch = repository.devCrashOnNextLaunch
    val spoofParamsJson = repository.spoofParamsJson

    val themeMode: StateFlow<String> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val appLanguage: StateFlow<String> = repository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val spoofParams: StateFlow<SpoofParams> = repository.spoofParamsJson
        .map { SpoofParams.parse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpoofParams())

    // ---- Crash lab history (local DataStore, auto-cleared on version bump) ----
    val crashHistory: StateFlow<List<CrashLabEvent>> = repository.devCrashHistoryJson
        .map(::parseCrashHistory)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun parseCrashHistory(raw: String): List<CrashLabEvent> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CrashLabEvent(
                type = o.optString("type", "?"),
                atMs = o.optLong("atMs", 0L),
                note = o.optString("note", ""),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    private val _lastAction = MutableStateFlow<String?>(null)
    val lastAction = _lastAction.asStateFlow()

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines = _logLines.asStateFlow()

    private val _logcatTail = MutableStateFlow(true)
    val logcatTail = _logcatTail.asStateFlow()

    private val _shizukuActive = MutableStateFlow(false)
    val shizukuActive = _shizukuActive.asStateFlow()

    private val _forceShizukuLogcat = MutableStateFlow(true)
    val forceShizukuLogcat = _forceShizukuLogcat.asStateFlow()

    private val _logcatScope = MutableStateFlow(LogcatScope.APP)
    val logcatScope = _logcatScope.asStateFlow()

    fun setForceShizukuLogcat(enabled: Boolean) {
        _forceShizukuLogcat.value = enabled
        refreshLogcat()
        _lastAction.value = if (enabled) "Shizuku privileged logcat enabled" else "Standard logcat enabled"
    }

    fun setLogcatScope(scope: LogcatScope) {
        _logcatScope.value = scope
        refreshLogcat()
        _lastAction.value = "Logcat scope: ${scope.title}"
    }

    fun requestShizukuPermission() {
        try {
            ShizukuHelper.requestPermission(1001)
            _lastAction.value = "Requesting Shizuku authorization"
        } catch (e: Exception) {
            _lastAction.value = "Shizuku service unreachable: ${e.message}"
        }
    }

    fun checkShizukuStatus(): Boolean {
        val active = try {
            ShizukuHelper.isAuthorized()
        } catch (_: Exception) {
            false
        }
        _shizukuActive.value = active
        return active
    }

    init {
        checkShizukuStatus()
        refreshLogcat()
        startTail()
    }

    private var tailJob: Job? = null

    private fun startTail() {
        tailJob?.cancel()
        tailJob = viewModelScope.launch {
            while (isActive) {
                withContext(Dispatchers.IO) { fetchLogcat() }
                delay(1000)
            }
        }
    }

    fun setLogcatTail(enabled: Boolean) {
        _logcatTail.value = enabled
        if (enabled) {
            refreshLogcat()
            startTail()
        } else {
            tailJob?.cancel()
        }
    }

    private val _tableCounts = MutableStateFlow<List<DevTableCount>>(emptyList())
    val tableCounts = _tableCounts.asStateFlow()

    private val _storageInfo = MutableStateFlow(DevStorageInfo())
    val storageInfo = _storageInfo.asStateFlow()

    private val _prefsSnapshot = MutableStateFlow<Map<String, String>>(emptyMap())
    val prefsSnapshot = _prefsSnapshot.asStateFlow()

    // ---- Live telemetry: only runs while a collector is actually watching ----
    private val _heapHistory = MutableStateFlow<List<Float>>(emptyList())
    val heapHistory: StateFlow<List<Float>> = _heapHistory.asStateFlow()

    val appInfo: StateFlow<DevAppInfo> = flow {
        while (true) {
            val info = collectAppInfo()
            _heapHistory.value = (_heapHistory.value + info.heapUsedFraction).takeLast(48)
            emit(info)
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), collectAppInfo())

    private fun collectAppInfo(): DevAppInfo {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory()
        return DevAppInfo(
            uptimeMs = android.os.SystemClock.elapsedRealtime(),
            heapMb = "${used / (1024 * 1024)} MB / ${max / (1024 * 1024)} MB",
            nativeHeapMb = "${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)} MB",
            threads = Thread.activeCount(),
            heapUsedFraction = (used.toFloat() / max.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
        )
    }

    fun setDevMode(enabled: Boolean) = viewModelScope.launch {
        repository.setDeveloperModeEnabled(enabled)
        DevLogHub.i("ToolzDev", "Developer Mode master switch -> $enabled")
        _lastAction.value = if (enabled) "Developer Mode enabled" else "Developer Mode disabled"
    }

    fun setSpoof(tool: DevToolId, enabled: Boolean) = viewModelScope.launch {
        repository.setSpoofFlag(tool.prefKey, enabled)
        DevLogHub.i("ToolzDev", "Sandbox for ${tool.title} -> ${if (enabled) "ENABLED" else "DISABLED"}")
        _lastAction.value = if (enabled) "${tool.title} sandbox enabled" else "${tool.title} sandbox disabled"
    }

    fun clearAllSpoofs() = viewModelScope.launch {
        repository.clearAllSpoofs()
        DevLogHub.i("ToolzDev", "All tool sandboxes reset to live hardware")
        _lastAction.value = "All tool sandboxes reset to live hardware"
    }

    fun spoofAll() = viewModelScope.launch {
        repository.setDeveloperModeEnabled(true)
        repository.setAllSpoofs(true)
        DevLogHub.i("ToolzDev", "All 18 tool sandboxes enabled")
        _lastAction.value = "All 18 tool sandboxes enabled"
    }

    fun applyPreset(preset: DemoPreset) = viewModelScope.launch {
        repository.setDeveloperModeEnabled(true)
        when (preset) {
            DemoPreset.SHOWCASE -> {
                repository.clearAllSpoofs()
                listOf(
                    DevToolId.BATTERY,
                    DevToolId.STEPS,
                    DevToolId.NETWORK,
                    DevToolId.SENSORS,
                    DevToolId.MUSIC,
                    DevToolId.TIMERS,
                    DevToolId.DEVICE,
                ).forEach { repository.setSpoofFlag(it.prefKey, true) }

                val params = SpoofParams(
                    batteryLevel = 94,
                    batteryTempC = 28.5f,
                    batteryCharging = true,
                    stepsToday = 8420,
                    networkPingMs = 14,
                    networkDownMbps = 450.0f,
                    networkSsid = "Toolz-Fiber-WiFi",
                    compassDeg = 45,
                    soundDb = 38,
                    lightLux = 420,
                    speedKmh = 0,
                    altitudeM = 125,
                    deviceModel = "Pixel 9 Pro",
                    musicTrack = "Midnight Symphony",
                    musicArtist = "Toolz Studio",
                    timerSecs = 120,
                )
                repository.setSpoofParamsJson(params.toJson())
                DevLogHub.i("ToolzDev", "Applied SHOWCASE demo preset: core tools active with clean data")
                _lastAction.value = "Applied Showcase Demo preset"
            }
            DemoPreset.EDGE_CASES -> {
                repository.clearAllSpoofs()
                listOf(
                    DevToolId.BATTERY,
                    DevToolId.NETWORK,
                    DevToolId.SENSORS,
                    DevToolId.DEVICE,
                    DevToolId.CLEANER,
                ).forEach { repository.setSpoofFlag(it.prefKey, true) }

                val params = SpoofParams(
                    batteryLevel = 2,
                    batteryTempC = 52.0f,
                    batteryCharging = false,
                    stepsToday = 99999,
                    networkPingMs = 4500,
                    networkDownMbps = 0.05f,
                    networkSsid = "Weak-Public-WiFi",
                    compassDeg = 358,
                    soundDb = 125,
                    lightLux = 150000,
                    speedKmh = 380,
                    altitudeM = 8848,
                    storageFullPct = 99,
                    deviceModel = "Edge Test Device",
                )
                repository.setSpoofParamsJson(params.toJson())
                DevLogHub.w("ToolzDev", "Applied EDGE_CASES stress preset (2% battery, 125dB, 8848m)")
                _lastAction.value = "Applied Edge Cases & Stress preset"
            }
            DemoPreset.TRAVEL -> {
                repository.clearAllSpoofs()
                listOf(
                    DevToolId.NETWORK,
                    DevToolId.BATTERY,
                    DevToolId.SENSORS,
                ).forEach { repository.setSpoofFlag(it.prefKey, true) }

                val params = SpoofParams(
                    batteryLevel = 68,
                    batteryTempC = 25.0f,
                    batteryCharging = false,
                    networkPingMs = 9999,
                    networkDownMbps = 0.0f,
                    networkSsid = "In-Flight-Offline",
                    compassDeg = 180,
                    soundDb = 72,
                    altitudeM = 10500,
                    speedKmh = 860,
                )
                repository.setSpoofParamsJson(params.toJson())
                DevLogHub.i("ToolzDev", "Applied TRAVEL preset (10,500m cruising altitude, 860km/h)")
                _lastAction.value = "Applied Travel & Offline preset"
            }
        }
    }

    fun resetAll() = viewModelScope.launch {
        repository.resetDeveloperMode()
        DevLogHub.i("ToolzDev", "Developer mode reset to defaults")
        _lastAction.value = "Developer mode reset to defaults"
    }

    fun setCrashOnNextLaunch(enabled: Boolean) = viewModelScope.launch {
        repository.setDevCrashOnNextLaunch(enabled)
        _lastAction.value = if (enabled) "Crash-on-launch enabled" else "Crash-on-launch disabled"
    }

    fun setShakeToOpen(enabled: Boolean) = viewModelScope.launch {
        repository.setDevShakeToOpen(enabled)
        _lastAction.value = if (enabled) "Shake-to-open enabled" else "Shake-to-open disabled"
    }

    fun setThemeMode(mode: String) = viewModelScope.launch {
        repository.setThemeMode(mode)
    }

    fun updateSpoofParams(transform: (SpoofParams) -> SpoofParams) = viewModelScope.launch {
        val current = SpoofParams.parse(try {
            spoofParamsJson.first()
        } catch (_: Exception) { "{}" })
        repository.setSpoofParamsJson(transform(current).toJson())
    }

    fun copyText(label: String, text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            _lastAction.value = "$label copied to clipboard"
        } catch (_: Exception) { }
    }

    fun buildBugReport(info: DevAppInfo): String {
        val active = try {
            spoofSnapshot.value.filter { it.value }.keys.joinToString(", ") { it.title }.ifBlank { "none" }
        } catch (_: Exception) { "unknown" }
        val history = runCatching { crashHistory.value }.getOrDefault(emptyList())
        val crashSection = if (history.isEmpty()) "none" else history.joinToString("\n") { "- ${it.type} @ ${it.atMs}" }
        return "## Toolz Diagnostic Report\n" +
            "- App: Toolz ${info.versionName} (${info.versionCode})\n" +
            "- Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE}, API ${android.os.Build.VERSION.SDK_INT})\n" +
            "- Active Sandboxes: $active\n" +
            "- Memory: Heap ${info.heapMb} | Native ${info.nativeHeapMb} | Threads: ${info.threads}\n" +
            "- Uptime: ${info.uptimeMs / 1000}s\n" +
            "- Crash Test History: \n$crashSection\n"
    }

    fun copyBugReport(info: DevAppInfo) = copyText("Diagnostic report", buildBugReport(info))

    fun shareBugReport(info: DevAppInfo) {
        com.frerox.toolz.data.developer.DevUiOverrider.shareText(context, "Toolz Diagnostic Report", buildBugReport(info))
        _lastAction.value = "Sharing diagnostic report"
    }

    fun applyLanguage(code: String) {
        viewModelScope.launch {
            try { repository.setAppLanguage(code) } catch (_: Exception) { }
            try {
                val tags = if (code == "system") "" else code
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
                _lastAction.value = if (code == "system") "Language reset to system" else "Language set to $code"
            } catch (_: Exception) { }
        }
    }

    // ---- Notification testing ----
    private val devPostedIds = mutableSetOf<Int>()

    fun areNotificationsEnabled(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.areNotificationsEnabled()
        } else true
    }

    fun sendSpoofNotification(channelId: String, title: String, body: String, id: Int) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) {
                _lastAction.value = "Notifications are disabled in system settings"
                return
            }
            NotificationHelper.createAllChannels(context)
            val notif = NotificationHelper.baseBuilder(context, channelId)
                .setContentTitle("[DEV TEST] $title")
                .setContentText(body)
                .setAutoCancel(true)
                .build()
            manager.notify(id, notif)
            synchronized(devPostedIds) { devPostedIds.add(id) }
            _lastAction.value = "Notification posted: $title"
        } catch (e: Exception) {
            _lastAction.value = "Notify failed: ${e.message}"
        }
    }

    fun cancelDevNotifications() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            synchronized(devPostedIds) {
                devPostedIds.forEach { runCatching { manager.cancel(it) } }
                devPostedIds.clear()
            }
            _lastAction.value = "Test notifications cleared"
        } catch (_: Exception) { }
    }

    // ---- Database & Storage ----
    fun refreshTableCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val out = mutableListOf<DevTableCount>()
            try {
                val db = database.openHelper.readableDatabase
                db.query("SELECT name FROM sqlite_master WHERE type='table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' " +
                    "AND name NOT LIKE 'android_%' ORDER BY name").use { c ->
                    while (c.moveToNext()) {
                        val table = c.getString(0)
                        val rows = try {
                            db.query("SELECT COUNT(*) FROM \"$table\"").use { cc ->
                                if (cc.moveToFirst()) cc.getInt(0) else 0
                            }
                        } catch (_: Exception) { -1 }
                        val lower = table.lowercase()
                        if (lower.contains("password") || lower.contains("whisper") || lower.contains("key")) {
                            out.add(DevTableCount("$table (redacted)", rows))
                        } else {
                            out.add(DevTableCount(table, rows))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _lastAction.value = "DB inspect error: ${e.message}" }
                return@launch
            }
            val snapshot = out.toList()
            withContext(Dispatchers.Main) {
                _tableCounts.value = snapshot
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.clearAllTables()
                refreshTableCounts()
                withContext(Dispatchers.Main) {
                    _lastAction.value = "All database tables cleared"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _lastAction.value = "Failed to clear database: ${e.message}"
                }
            }
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            repository.resetOnboarding()
            _lastAction.value = "Onboarding reset — app will show welcome screen on next start"
        }
    }

    private fun dirSize(dir: java.io.File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) { 0L }
    }

    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = DevStorageInfo(
                cacheBytes = dirSize(context.cacheDir),
                filesBytes = dirSize(context.filesDir),
            )
            withContext(Dispatchers.Main) { _storageInfo.value = info }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir?.listFiles()?.forEach {
                    runCatching { if (it.isDirectory) it.deleteRecursively() else it.delete() }
                }
                refreshStorageInfo()
                withContext(Dispatchers.Main) {
                    _lastAction.value = "Cache cleared"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _lastAction.value = "Clear cache failed: ${e.message}" }
            }
        }
    }

    fun refreshPrefs() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = try { repository.snapshotPrefs() } catch (_: Exception) { emptyMap() }
            withContext(Dispatchers.Main) {
                _prefsSnapshot.value = snap
            }
        }
    }

    // ---- Crash Testing (Executed on main thread for real crashes) ----
    fun triggerCrash(type: String) {
        _lastAction.value = "Triggering $type crash..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.appendDevCrashHistoryEvent(type, System.currentTimeMillis(), note = "Manual test trigger")
            } catch (_: Exception) { }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            when (type) {
                "NPE" -> throw NullPointerException("[DEV TEST] Simulated NullPointerException on main thread")
                "IllegalState" -> throw IllegalStateException("[DEV TEST] Simulated IllegalStateException on main thread")
                "ANR" -> {
                    // Block main thread for 10 seconds to cause real ANR
                    Thread.sleep(10000)
                }
                "OOM" -> {
                    val hog = mutableListOf<ByteArray>()
                    while (true) {
                        hog.add(ByteArray(32 * 1024 * 1024))
                    }
                }
                else -> throw RuntimeException("[DEV TEST] Simulated $type crash on main thread")
            }
        }, 200)
    }

    fun clearCrashHistory() = viewModelScope.launch {
        repository.clearDevCrashHistory()
        _lastAction.value = "Crash history cleared"
    }

    fun logNonFatal() {
        android.util.Log.e("ToolzDev", "[DEV TEST] Non-fatal test exception logged", IllegalStateException("Sample non-fatal issue"))
        _lastAction.value = "Logged non-fatal exception to logcat"
    }

    // ---- Logcat & Diagnostics Log Stream ----
    fun refreshLogcat() {
        viewModelScope.launch(Dispatchers.IO) { fetchLogcat() }
    }

    fun sendTestLog(level: String = "INFO") {
        val now = System.currentTimeMillis()
        when (level.uppercase()) {
            "VERBOSE", "V" -> DevLogHub.v("ToolzTest", "Test VERBOSE diagnostic trace event at $now")
            "DEBUG", "D" -> DevLogHub.d("ToolzTest", "Test DEBUG state checkpoint at $now")
            "INFO", "I" -> DevLogHub.i("ToolzTest", "Test INFO event emitted at $now")
            "WARN", "W" -> DevLogHub.w("ToolzTest", "Test WARN: potential latency simulated at $now")
            "ERROR", "E" -> DevLogHub.e("ToolzTest", "Test ERROR: simulated handled exception at $now", RuntimeException("Sample developer exception"))
            else -> DevLogHub.i("ToolzTest", "Test log entry at $now")
        }
        refreshLogcat()
        _lastAction.value = "Added $level test log entry"
    }

    fun copyWarningsAndErrors() {
        val lines = _logLines.value
        val regex = Regex("""(?:\s|\b)([WEF])[/ ]""")
        val filtered = lines.filter { line ->
            regex.containsMatchIn(line) || line.contains(" E/") || line.contains(" W/") || line.contains(" F/")
        }
        if (filtered.isEmpty()) {
            _lastAction.value = "No warnings or errors found in logcat"
        } else {
            copyText("Warnings & Errors", filtered.joinToString("\n"))
            _lastAction.value = "Copied ${filtered.size} warnings/errors to clipboard"
        }
    }

    fun copyAllLogs() {
        val lines = _logLines.value
        if (lines.isEmpty()) {
            _lastAction.value = "Logcat is empty"
        } else {
            copyText("Logcat", lines.joinToString("\n"))
            _lastAction.value = "Copied ${lines.size} log lines to clipboard"
        }
    }

    private suspend fun fetchLogcat() {
        val isShizuku = checkShizukuStatus()
        val preferShizuku = _forceShizukuLogcat.value
        val scope = _logcatScope.value
        val myPid = Process.myPid()

        var lines: List<String> = emptyList()
        var shizukuUsed = false

        if (preferShizuku && isShizuku) {
            val cmd = if (scope == LogcatScope.SYSTEM) {
                "logcat -d -v time -t 300"
            } else {
                "logcat -d -v time -t 300 --pid=$myPid"
            }
            try {
                val res = shizukuExecutor.executeForResult(cmd, timeoutMs = 2000)
                if (res.exitCode == 0 && res.stdout.isNotBlank()) {
                    lines = res.stdout.lines().filter { it.isNotBlank() }
                    shizukuUsed = true
                }
            } catch (e: Exception) {
                DevLogHub.w("ToolzDev", "Shizuku logcat failed: ${e.message}")
            }
        }

        if (!shizukuUsed) {
            val hubLines = DevLogHub.getLines()
            val systemLines = try {
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "200", "--pid=$myPid"))
                val l = proc.inputStream.bufferedReader().readLines()
                if (l.isEmpty()) {
                    val fallbackProc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", "200"))
                    fallbackProc.inputStream.bufferedReader().readLines().filter { it.contains("Toolz") || it.contains("$myPid") }
                } else l
            } catch (_: Exception) {
                emptyList()
            }
            lines = (hubLines + systemLines).distinct().takeLast(350)
        }

        withContext(Dispatchers.Main) {
            if (lines.isNotEmpty()) {
                _logLines.value = lines.takeLast(350)
            }
        }
    }

    fun clearLogcat() {
        DevLogHub.clear()
        viewModelScope.launch(Dispatchers.IO) {
            if (_forceShizukuLogcat.value && checkShizukuStatus()) {
                try { shizukuExecutor.executeForResult("logcat -c", timeoutMs = 2000) } catch (_: Exception) { }
            }
            try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")) } catch (_: Exception) { }
            withContext(Dispatchers.Main) { _logLines.value = emptyList() }
        }
        _lastAction.value = "Log stream cleared"
    }

    fun restartApp(activity: Activity?) {
        activity?.let { act ->
            val intent = act.packageManager.getLaunchIntentForPackage(act.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            act.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }

    fun consumeAction() { _lastAction.value = null }

    fun appContext(): Context = context
}