/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * V4 production auto-clear: guides Settings → App info → Storage → Clear cache.
 * Play-safe: disabled by default, manual trigger only from the cleaner UI,
 * in-app disclosure before enable, Stop button, bounded per-app waits,
 * full audit log. Never scrapes windows in the background.
 */

package com.frerox.toolz.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.StorageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.format.Formatter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Reason an app could not be auto-cleared. */
data class FailedApp(
    val pkg: String,
    val label: String,
    val reason: String
)

/** Observable auto-clear run state for the UI. */
data class AutoClearState(
    val running: Boolean = false,
    val current: String = "",
    val currentPkg: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val log: List<String> = emptyList(),
    val cleared: List<String> = emptyList(),
    val failedApps: List<FailedApp> = emptyList(),
    val done: Boolean = false
)

@AndroidEntryPoint
class CleanerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() { stopAutoClear() }

    companion object {
        @Volatile var instance: CleanerAccessibilityService? = null
            private set

        private val _autoState = MutableStateFlow(AutoClearState())
        val autoState: StateFlow<AutoClearState> = _autoState.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, CleanerAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun openAppStorage(context: Context, packageName: String) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /** Clear-cache button labels across locales (best-effort; view-id match is tried first). */
        val CLEAR_CACHE_TEXTS = listOf(
            "clear cache", "vider le cache", "borrar cach\u00e9", "cache leeren",
            "limpar cache", "cancella cache", "cache wissen",
            "borrar memoria cach\u00e9", "önbelleği temizle"
        )
        val STORAGE_TEXTS = listOf(
            "storage", "stockage", "almacenamiento", "speicher",
            "armazenamento", "spazio di archiviazione", "opslag", "depolama"
        )
        /** AOSP Settings view-ids for the clear-cache button (OEMs vary — text match is the fallback). */
        val CLEAR_CACHE_VIEW_IDS = listOf(
            "com.android.settings:id/clear_cache_btn",
            "com.android.settings:id/clearCacheButton"
        )
    }

    /** Legacy overload: label falls back to the package name. */
    @Deprecated("Use pairs overload with labels")
    fun startAutoClear(packages: List<String>): Boolean =
        startAutoClear(packages.map { it to it })

    /** Starts a guided run. Returns false when a run is already active. */
    @JvmName("startAutoClearWithLabels")
    fun startAutoClear(apps: List<Pair<String, String>>): Boolean {
        if (job?.isActive == true) return false
        val queue = apps.distinctBy { it.first }.take(50)
        _autoState.value = AutoClearState(
            running = true, current = "", currentPkg = "", index = 0, total = queue.size,
            log = listOf("Starting auto-clear for ${queue.size} app(s)…")
        )
        job?.cancel()
        job = scope.launch {
            val ok = mutableListOf<String>()
            val bad = mutableListOf<FailedApp>()
            for ((i, entry) in queue.withIndex()) {
                if (!isActive) break
                val (pkg, label) = entry
                _autoState.value = _autoState.value.copy(current = label, currentPkg = pkg, index = i, total = queue.size)
                if (!isEnabled(this@CleanerAccessibilityService)) { appendLog("Service disabled — stopping"); break }
                val before = cacheBytes(pkg)
                appendLog("[${i + 1}/${queue.size}] Opening $label…", current = label)
                _autoState.value = _autoState.value.copy(current = label, currentPkg = pkg, index = i, total = queue.size)
                try { openAppStorage(this@CleanerAccessibilityService, pkg) } catch (_: Exception) {
                    bad.add(FailedApp(pkg, label, "couldn't open settings"))
                    appendLog("[${i + 1}/${queue.size}] $label: couldn't open settings")
                    _autoState.value = _autoState.value.copy(index = i + 1)
                    try { backNav() } catch (_: Exception) {}
                    continue
                }
                var tapped = false
                // Bounded: ~15s per app, stop polling as soon as the tap lands.
                for (attempt in 0 until 30) {
                    delay(500)
                    val root = try { rootInActiveWindow } catch (_: Exception) { null }
                    if (tapClearCache(root)) { tapped = true; break }
                    if (attempt % 2 == 1) scrollFirstScrollable(root)
                }
                delay(1000)
                val after = cacheBytes(pkg)
                val b = before ?: 0L
                if (b <= 0L) {
                    ok.add(label)
                    appendLog("[${i + 1}/${queue.size}] $label: already clean")
                } else if (after != null && after < b) {
                    ok.add(label)
                    val freed = Formatter.formatShortFileSize(this@CleanerAccessibilityService, b - after)
                    appendLog("[${i + 1}/${queue.size}] $label: cache cleared ✓ (freed $freed)")
                } else if (after == null) {
                    if (tapped) {
                        ok.add(label)
                        appendLog("[${i + 1}/${queue.size}] $label: tap sent, couldn't verify")
                    } else {
                        bad.add(FailedApp(pkg, label, "no tap target found"))
                        appendLog("[${i + 1}/${queue.size}] $label: needs a manual tap")
                    }
                } else {
                    val still = Formatter.formatShortFileSize(this@CleanerAccessibilityService, after)
                    bad.add(FailedApp(pkg, label, "still $still — needs a manual tap"))
                    appendLog("[${i + 1}/${queue.size}] $label: still $still — needs a manual tap")
                }
                _autoState.value = _autoState.value.copy(index = i + 1, current = label, currentPkg = pkg)
                // Pop the Settings stack so activities don't pile up.
                try { backNav() } catch (_: Exception) {}
            }
            _autoState.value = _autoState.value.copy(running = false, current = "", currentPkg = "", done = true,
                cleared = ok, failedApps = bad,
                log = _autoState.value.log + "Done — ${ok.size} cleared, ${bad.size} need attention")
        }
        return true
    }

    fun stopAutoClear() {
        job?.cancel()
        _autoState.value = _autoState.value.copy(running = false, done = true,
            log = _autoState.value.log + "Stopped by user")
    }

    fun resetAutoClear() { _autoState.value = AutoClearState() }

    private fun appendLog(line: String, current: String? = null) {
        val s = _autoState.value
        _autoState.value = s.copy(current = current ?: s.current, log = (s.log + line).takeLast(200))
    }

    /** Legacy callback API kept for compat; mirrors progress into the state flow. */
    fun clearCaches(packages: List<String>, onStep: (String) -> Unit = {}) {
        if (!startAutoClear(packages)) return
        scope.launch {
            var last = 0
            while (true) {
                delay(400)
                val s = _autoState.value
                s.log.drop(last).forEach(onStep)
                last = s.log.size
                if (s.done) break
            }
        }
    }

    private fun cacheBytes(pkg: String): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            val ssm = getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager ?: return null
            ssm.queryStatsForPackage(ai.storageUuid, pkg, Process.myUserHandle()).cacheBytes
        } catch (_: Exception) { null }
    }

    private suspend fun backNav() {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(400)
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(400)
        } catch (_: Exception) {}
    }

    private fun tapClearCache(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        // 1) Stable view-id match (AOSP).
        for (id in CLEAR_CACHE_VIEW_IDS) {
            try {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                val btn = nodes.firstOrNull { it.isClickable && it.isEnabled }
                nodes.forEach { if (it !== btn) try { it.recycle() } catch (_: Exception) {} }
                if (btn != null) {
                    return try { btn.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
                }
            } catch (_: Exception) {}
        }
        // 2) Text match across locales.
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var storageNode: AccessibilityNodeInfo? = null
        var clearNode: AccessibilityNodeInfo? = null
        var steps = 0
        while (queue.isNotEmpty() && steps++ < 600) {
            val n = queue.removeFirst()
            val text = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).lowercase()
            if (clearNode == null && n.isClickable && n.isEnabled && CLEAR_CACHE_TEXTS.any { text.contains(it) }) clearNode = n
            if (storageNode == null && n.isClickable && STORAGE_TEXTS.any { text == it }) storageNode = n
            if (clearNode != null && storageNode != null) break
            for (i in 0 until n.childCount) { try { n.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {} }
        }
        if (clearNode != null) { try { return clearNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) {} }
        // 3) Drill one level into the Storage page, caller re-polls.
        if (storageNode != null) { try { storageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) {} }
        return false
    }

    private fun scrollFirstScrollable(root: AccessibilityNodeInfo?) {
        if (root == null) return
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var steps = 0
            while (queue.isNotEmpty() && steps++ < 300) {
                val n = queue.removeFirst()
                if (n.isScrollable) {
                    try { n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } catch (_: Exception) {}
                    return
                }
                for (i in 0 until n.childCount) { try { n.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {} }
            }
        } catch (_: Exception) {}
    }
}
