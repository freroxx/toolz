/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.developer

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Debug
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.AppDatabase
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.DeveloperSpoofController
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DevAppInfo(
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: String = BuildConfig.VERSION_CODE.toString(),
    val packageName: String = "com.frerox.toolz",
    val uptimeMs: Long = 0L,
    val heapMb: String = "",
    val nativeHeapMb: String = "",
    val threads: Int = 0,
)

data class DevTableCount(val name: String, val rows: Int)

data class DevStorageInfo(
    val cacheBytes: Long = 0L,
    val filesBytes: Long = 0L,
) {
    fun format(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024f))}MB"
        else -> "${"%.2f".format(bytes / (1024f * 1024f * 1024f))}GB"
    }
}

@HiltViewModel
class DeveloperModeViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val spoofController: DeveloperSpoofController,
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val devModeEnabled = repository.developerModeEnabled
    val allSpoofFlags = repository.allSpoofFlags
    val spoofSnapshot: StateFlow<Map<String, Boolean>> = repository.allSpoofFlags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val activeSandboxCount = spoofController.activeSandboxCount
    val devVerboseLogging = repository.devVerboseLogging
    val devMockDelayMs = repository.devMockDelayMs
    val devPerfHud = repository.devPerfHud
    val devForceOffline = repository.devForceOffline
    val devCrashOnNextLaunch = repository.devCrashOnNextLaunch
    val devShakeToOpen = repository.devShakeToOpen
    val spoofParamsJson = repository.spoofParamsJson

    val spoofParams: StateFlow<SpoofParams> = repository.spoofParamsJson
        .map { SpoofParams.parse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpoofParams())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    private val _lastAction = MutableStateFlow<String?>(null)
    val lastAction = _lastAction.asStateFlow()

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines = _logLines.asStateFlow()

    private val _tableCounts = MutableStateFlow<List<DevTableCount>>(emptyList())
    val tableCounts = _tableCounts.asStateFlow()

    private val _storageInfo = MutableStateFlow(DevStorageInfo())
    val storageInfo = _storageInfo.asStateFlow()

    private val _prefsSnapshot = MutableStateFlow<Map<String, String>>(emptyMap())
    val prefsSnapshot = _prefsSnapshot.asStateFlow()

    private val _liveTick = MutableStateFlow(0L)
    val appInfo: StateFlow<DevAppInfo> = _liveTick
        .map { collectAppInfo() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), collectAppInfo())

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _liveTick.value = android.os.SystemClock.elapsedRealtime()
            }
        }
    }

    private fun collectAppInfo(): DevAppInfo {
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        return DevAppInfo(
            uptimeMs = android.os.SystemClock.elapsedRealtime(),
            heapMb = "${usedMb}MB / ${runtime.maxMemory() / (1024 * 1024)}MB",
            nativeHeapMb = "${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)}MB",
            threads = Thread.activeCount(),
        )
    }

    fun setDevMode(enabled: Boolean) = viewModelScope.launch { repository.setDeveloperModeEnabled(enabled) }
    fun setSpoof(tool: DevToolId, enabled: Boolean) = viewModelScope.launch {
        repository.setSpoofFlag(tool.prefKey, enabled)
        _lastAction.value = if (enabled) "${tool.title} sandbox ON (local only)" else "${tool.title} sandbox OFF"
    }
    fun clearAllSpoofs() = viewModelScope.launch {
        repository.clearAllSpoofs()
        _lastAction.value = "All spoofs OFF — real data restored"
    }
    fun resetAll() = viewModelScope.launch {
        repository.resetDeveloperMode()
        _lastAction.value = "Developer mode reset"
    }
    fun setVerbose(enabled: Boolean) = viewModelScope.launch { repository.setDevVerboseLogging(enabled) }
    fun setMockDelay(ms: Int) = viewModelScope.launch { repository.setDevMockDelayMs(ms) }
    fun setPerfHud(enabled: Boolean) = viewModelScope.launch { repository.setDevPerfHud(enabled) }
    fun setForceOffline(enabled: Boolean) = viewModelScope.launch { repository.setDevForceOffline(enabled) }
    fun setCrashOnNextLaunch(enabled: Boolean) = viewModelScope.launch { repository.setDevCrashOnNextLaunch(enabled) }
    fun setShakeToOpen(enabled: Boolean) = viewModelScope.launch { repository.setDevShakeToOpen(enabled) }
    fun setThemeMode(mode: String) = viewModelScope.launch { repository.setThemeMode(mode) }

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
            _lastAction.value = "$label copied"
        } catch (_: Exception) { }
    }

    fun copyAppInfo(info: DevAppInfo) {
        copyText(
            "App info",
            "Toolz ${info.versionName} (${info.versionCode})\n" +
                "pkg=${info.packageName}\nuptime=${info.uptimeMs}ms\n" +
                "heap=${info.heapMb}\nnative=${info.nativeHeapMb}\nthreads=${info.threads}"
        )
    }

    fun copyBugTemplate(info: DevAppInfo) {
        val active = try {
            spoofSnapshot.value.filter { it.value }.keys.joinToString(", ").ifBlank { "none" }
        } catch (_: Exception) { "unknown" }
        copyText(
            "Bug template",
            "## Bug report\n" +
                "- App: Toolz ${info.versionName} (${info.versionCode})\n" +
                "- Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (SDK ${android.os.Build.VERSION.SDK_INT})\n" +
                "- Dev mode spoofs active: $active\n" +
                "- Heap: ${info.heapMb} | Uptime: ${info.uptimeMs / 1000}s\n\n" +
                "### Steps\n1. \n2. \n3. \n\n### Expected\n\n### Actual\n"
        )
    }

    fun applyLanguage(code: String) {
        viewModelScope.launch {
            try { repository.setAppLanguage(code) } catch (_: Exception) { }
            try {
                val tags = if (code == "system") "" else code
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
                _lastAction.value = if (code == "system") "Language: system default" else "Language: $code"
            } catch (_: Exception) { }
        }
    }

    // ---- Notification lab: every post is tagged [SPOOF TEST] on private IDs ----
    private val devPostedIds = mutableSetOf<Int>()

    fun sendSpoofNotification(channelId: String, title: String, body: String, id: Int) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationHelper.baseBuilder(context, channelId)
                .setContentTitle("[SPOOF TEST] $title")
                .setContentText(body)
                .setAutoCancel(true)
                .build()
            manager.notify(id, notif)
            synchronized(devPostedIds) { devPostedIds.add(id) }
            _lastAction.value = "Posted test #$id to $channelId"
        } catch (e: Exception) {
            _lastAction.value = "Notify failed: ${e.message}"
        }
    }

    fun floodNotifications() {
        val channels = listOf(
            NotificationHelper.CHANNEL_TOOL_ALARM,
            NotificationHelper.CHANNEL_TOOL_ACTIVE,
            NotificationHelper.CHANNEL_STEP_COUNTER,
            NotificationHelper.CHANNEL_TASK_REMINDERS,
            NotificationHelper.CHANNEL_BACKUPS,
            NotificationHelper.CHANNEL_TOOLZ_NEWS,
        )
        channels.forEachIndexed { i, ch ->
            sendSpoofNotification(ch, "Flood ${i + 1}/${channels.size}", "Stress item — safe to swipe away", 60001 + i)
        }
        _lastAction.value = "Flooded ${channels.size} test notifications"
    }

    fun cancelDevNotifications() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            synchronized(devPostedIds) {
                devPostedIds.forEach { runCatching { manager.cancel(it) } }
                devPostedIds.clear()
            }
            _lastAction.value = "Dev test notifications cancelled"
        } catch (_: Exception) { }
    }

    // ---- Storage + DB inspectors (read-only; vault bodies never read) ----
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
                        // Never expose secret tables' contents — counts only, and
                        // vault/whisper tables are listed as redacted.
                        val lower = table.lowercase()
                        if (lower.contains("password") || lower.contains("whisper") || lower.contains("key")) {
                            out.add(DevTableCount("$table (redacted)", rows))
                        } else {
                            out.add(DevTableCount(table, rows))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _lastAction.value = "DB inspect failed: ${e.message}" }
                return@launch
            }
            val snapshot = out.toList()
            withContext(Dispatchers.Main) {
                _tableCounts.value = snapshot
                _lastAction.value = "${snapshot.size} tables counted"
            }
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
                context.cacheDir?.listFiles()?.forEach { runCatching { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
                withContext(Dispatchers.Main) {
                    refreshStorageInfo()
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
                _lastAction.value = "${snap.size} prefs dumped (sensitive redacted)"
            }
        }
    }

    fun triggerCrash(type: String) {
        _lastAction.value = "Crashing: $type"
        viewModelScope.launch {
            delay(300)
            when (type) {
                "NPE" -> throw NullPointerException("[SPOOF TEST] Developer crash lab NPE")
                "ANR" -> Thread.sleep(6000)
                "OOM" -> {
                    val hog = mutableListOf<ByteArray>()
                    repeat(40) { hog.add(ByteArray(16 * 1024 * 1024)) }
                }
                else -> throw IllegalStateException("[SPOOF TEST] Developer crash lab: $type")
            }
        }
    }

    fun logNonFatal() {
        android.util.Log.e("DevCrashLab", "[SPOOF TEST] non-fatal test exception", IllegalStateException("test"))
        _lastAction.value = "Non-fatal logged to logcat (DevCrashLab)"
    }

    fun refreshLogcat() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "300", "Toolz:D", "DevSpoof:D", "DevCrashLab:D", "*:S"))
                val lines = proc.inputStream.bufferedReader().readLines().takeLast(300)
                withContext(Dispatchers.Main) {
                    _logLines.value = if (lines.isEmpty()) listOf("(logcat empty — verbose logging may be off)") else lines
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _logLines.value = listOf("(logcat unavailable: ${e.message})")
                }
            }
        }
    }

    fun clearLogcat() {
        viewModelScope.launch(Dispatchers.IO) {
            try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")) } catch (_: Exception) { }
            withContext(Dispatchers.Main) { _logLines.value = emptyList() }
        }
    }

    fun consumeAction() { _lastAction.value = null }

    /** Exposed for UI-only helpers (system settings intents). Never ships data out. */
    fun appContext(): Context = context
}
