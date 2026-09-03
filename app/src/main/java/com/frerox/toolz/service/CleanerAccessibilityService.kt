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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import kotlinx.coroutines.launch

/** Observable auto-clear run state for the UI. */
data class AutoClearState(
    val running: Boolean = false,
    val current: String = "",
    val log: List<String> = emptyList(),
    val cleared: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
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

    /** Starts a guided run. Returns false when a run is already active. */
    fun startAutoClear(packages: List<String>): Boolean {
        if (job?.isActive == true) return false
        val queue = packages.distinct().take(50)
        _autoState.value = AutoClearState(running = true, current = "", log = listOf("Starting auto-clear for ${queue.size} app(s)…"))
        job?.cancel()
        job = scope.launch {
            val ok = mutableListOf<String>()
            val bad = mutableListOf<String>()
            for (pkg in queue) {
                if (!isEnabled(this@CleanerAccessibilityService)) { appendLog("Service disabled — stopping"); break }
                appendLog("Opening $pkg…", current = pkg)
                try { openAppStorage(this@CleanerAccessibilityService, pkg) } catch (_: Exception) {
                    bad.add(pkg); appendLog("$pkg: couldn't open settings"); continue
                }
                var tapped = false
                // Bounded: ~15s per app, then move on and report honestly.
                repeat(30) {
                    delay(500)
                    val root = try { rootInActiveWindow } catch (_: Exception) { null }
                    if (tapClearCache(root)) { tapped = true; return@repeat }
                    if (it % 2 == 1) scrollFirstScrollable(root)
                }
                if (tapped) { ok.add(pkg); appendLog("$pkg: cache cleared ✓") }
                else { bad.add(pkg); appendLog("$pkg: needs a manual tap") }
            }
            _autoState.value = _autoState.value.copy(running = false, current = "", done = true, cleared = ok, failed = bad,
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
