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
import android.os.Bundle
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
import kotlinx.coroutines.withTimeoutOrNull

/** Reason an app could not be auto-cleared. */
data class FailedApp(
    val pkg: String,
    val label: String,
    val reason: String
)

/** Observable auto-clear run state for the UI. */
data class AutoClearState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val current: String = "",
    val currentPkg: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val freedBytes: Long = 0L,
    val log: List<String> = emptyList(),
    val cleared: List<String> = emptyList(),
    val failedApps: List<FailedApp> = emptyList(),
    val done: Boolean = false
)

@AndroidEntryPoint
class CleanerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var overlay: CleanerExpressiveOverlay? = null

    override fun onCreate() {
        super.onCreate()
        overlay = CleanerExpressiveOverlay(this, object : CleanerExpressiveOverlay.Listener {
            override fun onExitRequested() {
                stopAutoClear()
                overlay?.hide()
                returnToToolz()
            }
        })
    }

    override fun onDestroy() {
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        overlay?.hide()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        overlay?.hide()
        stopAutoClear()
    }

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
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            context.startActivity(intent)
        }

        /** Direct-storage fast path: opens the Storage page for [pkg] without going through App info. */
        fun openAppStorageDirect(context: Context, pkg: String, fragment: String) {
            val args = Bundle().apply { putString("package", pkg) }
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                putExtra(":settings:show_fragment", fragment)
                putExtra(":settings:show_fragment_args", args)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            context.startActivity(intent)
        }

        /** Storage fragments to try in order for the direct fast path. */
        val DIRECT_STORAGE_FRAGMENTS = listOf(
            "com.android.settings.applications.appinfo.AppStorageSettings",
            "com.android.settings.applications.AppStorageSettings"
        )

        /** AC-v2 tuning: fast switching + reliable taps. */
        const val WINDOW_READY_TIMEOUT_MS = 1_200L
        const val WINDOW_POLL_MS = 100L
        const val TAP_RETRY_ROUNDS = 3
        const val ATTEMPTS_PER_ROUND = 8
        const val VERIFY_READS = 3

        /**
         * AC-v2 direct-storage intent: best-effort deep-link straight to the
         * App Storage page (skips App Info → Storage drill when the ROM honors it).
         * Returns true when an intent was launched; false when all attempts failed.
         */
        fun openAppStorageDirect(context: Context, packageName: String): Boolean {
            val pkgUri = Uri.fromParts("package", packageName, null)
            // 1) App-Info intent with show_fragment extra (honored by AOSP + some OEMs).
            try {
                val deep = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = pkgUri
                    putExtra(":settings:show_fragment", "com.android.settings.applications.AppStorageSettings")
                    putExtra(":settings:show_fragment_title", "Storage")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                }
                context.startActivity(deep)
                return true
            } catch (_: Exception) {}
            // 2) Explicit AppStorageSettings activity (AOSP).
            try {
                val explicit = Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings\$AppStorageSettingsActivity"
                    )
                    data = pkgUri
                    putExtra("package", packageName)
                    putExtra(":settings:show_fragment", "com.android.settings.applications.AppStorageSettings")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                }
                context.startActivity(explicit)
                return true
            } catch (_: Exception) {}
            // 3) Fallback: plain App Info (drill-down path handles the rest).
            return try {
                openAppStorage(context, packageName)
                true
            } catch (_: Exception) { false }
        }

        fun isSettingsPackage(pkg: CharSequence?): Boolean {
            if (pkg == null) return false
            val s = pkg.toString()
            return s.contains("settings", ignoreCase = true) ||
                s.contains("sec.android", ignoreCase = true) ||
                s.contains("miui.security", ignoreCase = true) ||
                s == "com.android.settings"
        }

        /** Clear-cache button labels across locales (best-effort; view-id match is tried first). */
        val CLEAR_CACHE_TEXTS = listOf(
            "clear cache", "vider le cache", "effacer le cache", "borrar caché",
            "borrar memoria caché", "limpiar caché", "limpar cache", "cache leeren",
            "cache löschen", "cancella cache", "svuota cache", "cache wissen",
            "cache leegmaken", "önbelleği temizle", "очистить кэш", "清除缓存",
            "清理缓存", "キャッシュを消去", "キャッシュを削除", "캐시 삭제", "캐시 지우기",
            "مسح ذاكرة التخزين المؤقت", "कैश साफ़ करें"
        )
        val STORAGE_TEXTS = listOf(
            "storage & cache", "storage and cache", "storage", "stockage", "almacenamiento",
            "speicher", "armazenamento", "spazio di archiviazione", "opslag", "depolama",
            "önbellek", "память", "хранилище", "存储", "ストレージ", "저장"
        )
        val DANGEROUS_ACTION_TEXTS = listOf(
            "clear storage", "clear data", "manage space", "borrar datos",
            "borrar almacenamiento", "vider le stockage", "effacer les données",
            "daten löschen", "tüm verileri temizle", "очистить хранилище",
            "清除存储", "清除数据", "ストレージを消去", "저장용량 지우기", "데이터 삭제"
        )
        /** AOSP Settings view-ids for the clear-cache button (OEMs vary — text match is the fallback). */
        val CLEAR_CACHE_VIEW_IDS = listOf(
            "com.android.settings:id/clear_cache_btn",
            "com.android.settings:id/clearCacheButton",
            "com.android.settings:id/button2",
            "com.android.settings:id/right_button",
            "com.android.settings:id/btn_clear_cache",
            "com.android.settings:id/clear_cache",
            "com.android.settings:id/button_clear_cache"
        )
        val STORAGE_VIEW_IDS = listOf(
            "com.android.settings:id/storage_settings",
            "com.android.settings:id/storage_size",
            "com.android.settings:id/storage",
            "com.android.settings:id/widget_frame"
        )
    }

    /** Legacy overload: label falls back to the package name. */
    @Deprecated("Use pairs overload with labels")
    fun startAutoClear(packages: List<String>): Boolean =
        startAutoClear(packages.map { it to it })

    /** Starts a guided run. Returns false when a run is already active. */
    /** Starts a guided run. Returns false when a run is already active. */
    @JvmName("startAutoClearWithLabels")
    fun startAutoClear(apps: List<Pair<String, String>>): Boolean {
        if (job?.isActive == true) return false
        val queue = apps.distinctBy { it.first }.take(50)
        _autoState.value = AutoClearState(
            running = true, paused = false, current = "", currentPkg = "", index = 0, total = queue.size, freedBytes = 0L,
            log = listOf("Starting auto-clear for ${queue.size} app(s)…")
        )
        job?.cancel()

        // Attach M3 Expressive Overlay to screen (Exit button only, touch-intercepting)
        overlay?.show(queue.size)

        job = scope.launch {
            // S1.1.1: seed baseline window id (our own window) so the first app's
            // first awaitWindow() waits for a FRESH window instead of returning ours.
            var baselineWin: Int? = try { rootInActiveWindow?.windowId } catch (_: Exception) { null }
            val ok = mutableListOf<String>()
            val bad = mutableListOf<FailedApp>()
            val visited = mutableSetOf<String>()
            var totalFreed = 0L
            // Learn-once direct-storage fast path: null = undecided, decided on the first app.
            var directOk: Boolean? = null
            var directFragment: String? = null
            try {
                for ((i, entry) in queue.withIndex()) {
                    if (!isActive) break

                    val (pkg, label) = entry
                    if (pkg in visited) continue
                    visited.add(pkg)

                    _autoState.value = _autoState.value.copy(
                        current = label, currentPkg = pkg, index = i, total = queue.size, freedBytes = totalFreed
                    )
                    overlay?.updateApp(pkg, label, i, queue.size, totalFreed)

                    if (!isEnabled(this@CleanerAccessibilityService)) { appendLog("Service disabled — stopping"); break }
                    // S1.1.1 measure-first skip: stale entries cost no open+poll cycle.
                    val pre = try { cacheBytes(pkg) } catch (_: Exception) { null }
                    if ((pre ?: 0L) <= 0L) {
                        ok.add(label)
                        appendLog("[${i + 1}/${queue.size}] $label — already clean")
                        _autoState.value = _autoState.value.copy(index = i + 1, current = label, currentPkg = pkg, freedBytes = totalFreed)
                        continue
                    }
                    // Pop any previous Settings activity before opening the next app
                    if (i > 0) {
                        try {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            delay(100)
                        } catch (_: Exception) {}
                    }

                    // S1.1.1: seed lastWin with our own window id so the first
                    // awaitWindow() blocks for the Settings window (null would return instantly).
                    var lastWin: Int? = baselineWin
                    var drilledAny = false
                    var tappedAny = false
                    var sawButtonAny = false
                    var clearedThis = false
                    var alreadyClean = false
                    var unverified = false
                    var freedThis = 0L
                    var buttonConfirmed = false
                    var lastAfter: Long? = null
                    var roundsUsed = 0
                    var openFailed = false
                    var appTimedOut = false

                    // S1.1.1 per-app 45s timebox: stuck apps fail as timeout, never stall the run.
                    // withTimeoutOrNull returns null on timeout (no CancellationException propagation).
                    val timedOut = withTimeoutOrNull(45_000) {
                    // Retry rounds per app (max 2, fail-fast): reopen page → gate window → tap → quick verify.
                    // Round 1 with no tap + no button seen gets ONE grace round; round 2 verdict is FINAL.
                    for (round in 1..2) {
                        if (!isActive) break
                        if (!isEnabled(this@CleanerAccessibilityService)) { appendLog("Service disabled — stopping"); break }
                        roundsUsed = round
                        val before = cacheBytes(pkg)
                        if (round == 1) {
                            appendLog("[${i + 1}/${queue.size}] Opening $label…", current = label)
                        } else {
                            appendLog("[${i + 1}/${queue.size}] $label: retry $round/2…")
                        }

                        // ---- Open page (direct-storage fast path with learn-once, else App info) ----
                        var opened = false
                        try {
                            when {
                                directOk == false -> {
                                    openAppStorage(this@CleanerAccessibilityService, pkg)
                                    opened = true
                                    lastWin = awaitWindow(lastWin).second
                                }
                                directOk == true && directFragment != null -> {
                                    try {
                                        openAppStorageDirect(this@CleanerAccessibilityService, pkg, directFragment!!)
                                    } catch (_: Exception) {
                                        openAppStorage(this@CleanerAccessibilityService, pkg)
                                    }
                                    opened = true
                                    lastWin = awaitWindow(lastWin).second
                                }
                                else -> {
                                    // Learn once on the first app: probe direct fragments (no click).
                                    var learned = false
                                    for (frag in DIRECT_STORAGE_FRAGMENTS) {
                                        if (!isActive) break
                                        try {
                                            openAppStorageDirect(this@CleanerAccessibilityService, pkg, frag)
                                        } catch (_: Exception) { continue }
                                        lastWin = awaitWindow(lastWin).second
                                        if (probeClearNode(2500L)) {
                                            directOk = true
                                            directFragment = frag
                                            learned = true
                                            opened = true
                                            break
                                        }
                                    }
                                    if (!learned) {
                                        directOk = false
                                        openAppStorage(this@CleanerAccessibilityService, pkg)
                                        opened = true
                                        lastWin = awaitWindow(lastWin).second
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            opened = false
                        }
                        if (!opened) {
                            openFailed = true
                            break
                        }

                        // ---- Tap phase (adaptive budget: 10×300ms direct, 20×350ms App-info) ----
                        var tapped = false
                        var alreadyZero = false
                        var navigatedStorage = false
                        var sawButton = false
                        val directMode = directOk == true
                        val maxAttempts = if (directMode) 10 else 20
                        val attemptDelayMs = if (directMode) 300L else 350L
                        for (attempt in 0 until maxAttempts) {
                            if (!isActive) break
                            delay(attemptDelayMs)
                            val root = getTargetRoot()
                            val result = processWindow(root, navigatedStorage)
                            if (result == TapResult.TAPPED) {
                                tapped = true
                                sawButton = true
                                break
                            } else if (result == TapResult.ALREADY_ZERO) {
                                alreadyZero = true
                                tapped = true
                                sawButton = true
                                break
                            } else if (result == TapResult.BUTTON_SEEN) {
                                sawButton = true
                                continue
                            } else if (result == TapResult.DRILLED_STORAGE) {
                                navigatedStorage = true
                                drilledAny = true
                                continue
                            }
                            if (attempt % 2 == 1) scrollFirstScrollable(root)
                        }
                        if (tapped) tappedAny = true
                        if (sawButton) sawButtonAny = true
                        if (navigatedStorage) drilledAny = true

                        // ---- Verify (quick: 1s settle + up to 2 extra reads ~900ms apart, ≤3s total) ----
                        delay(1000)
                        if (alreadyZero) {
                            alreadyClean = true
                            clearedThis = true
                            break
                        }
                        if (before != null && before <= 0L) {
                            alreadyClean = true
                            clearedThis = true
                            break
                        }
                        if (before != null && before > 0L) {
                            for (read in 0 until 3) {
                                if (read > 0) delay(900)
                                val after = cacheBytes(pkg)
                                if (after != null) lastAfter = after
                                if (after != null && after < before) {
                                    freedThis = before - after
                                    lastAfter = after
                                    clearedThis = true
                                    break
                                }
                                // S1.1.1 button-state fallback (stats lag reality): on every
                                // poll iteration, a fresh root showing the Storage screen
                                // (no clear node, no storage row) with the Clear-cache
                                // button disabled proves the cache hit zero. Stats drop
                                // above stays primary; this only fires when stats lag.
                                // Gated on tapped==true so a fresh page with zero tap
                                // evidence never clears here (ALREADY_ZERO owns that).
                                if (tapped) {
                                    val freshRoot = try { getTargetRoot() } catch (_: Exception) { null }
                                    val onStorageScreen = try {
                                        freshRoot != null &&
                                            findClearNode(freshRoot) == null &&
                                            findStorageNode(freshRoot) == null
                                    } catch (_: Exception) { false }
                                    val btnEmpty = try { isClearDisabled(freshRoot) } catch (_: Exception) { false }
                                    if (onStorageScreen && btnEmpty) {
                                        // Button proves true cache is zero: freed = before - min(floor, before);
                                        // stale stats (after null or >= before) collapse to freed = before.
                                        val floor = after?.takeIf { it < before } ?: 0L
                                        freedThis = before - minOf(floor, before)
                                        buttonConfirmed = true
                                        clearedThis = true
                                        break
                                    }
                                }
                            }
                            if (clearedThis) break
                        } else {
                            // Stats unavailable: observe once for the failure message.
                            for (read in 0 until 3) {
                                if (read > 0) delay(900)
                                val after = cacheBytes(pkg)
                                if (after != null) { lastAfter = after; break }
                            }
                            if (tapped) {
                                unverified = true
                                clearedThis = true
                                break
                            }
                        }
                        // Fail-fast: round 2 verdict is FINAL — never a round 3.
                        if (round >= 2) break
                    }
                    } == null
                    if (timedOut) {
                        appTimedOut = true
                        appendLog("[${i + 1}/${queue.size}] $label: timed out after 45s — needs a manual tap")
                    }

                    if (openFailed) {
                        bad.add(FailedApp(pkg, label, "couldn't open settings"))
                        appendLog("[${i + 1}/${queue.size}] $label: couldn't open settings")
                        _autoState.value = _autoState.value.copy(index = i + 1)
                        try { backNav(false) } catch (_: Exception) {}
                        delay(150)
                        continue
                    }
                    if (!isEnabled(this@CleanerAccessibilityService)) { appendLog("Service disabled — stopping"); break }
                    if (!isActive) break
                    if (appTimedOut) {
                        bad.add(FailedApp(pkg, label, "timed out after 45s — needs a manual tap"))
                    } else if (clearedThis) {
                        if (alreadyClean) {
                            ok.add(label)
                            appendLog("[${i + 1}/${queue.size}] $label: already clean ✓")
                        } else if (unverified) {
                            ok.add(label)
                            appendLog("[${i + 1}/${queue.size}] $label: cache cleared ✓")
                        } else {
                            totalFreed += freedThis
                            ok.add(label)
                            val suffix = if (buttonConfirmed) " (button confirms empty)" else ""
                            if (freedThis > 0L) {
                                val freed = Formatter.formatShortFileSize(this@CleanerAccessibilityService, freedThis)
                                appendLog("[${i + 1}/${queue.size}] $label: cache cleared ✓ (freed $freed)$suffix")
                            } else {
                                appendLog("[${i + 1}/${queue.size}] $label: cache cleared ✓$suffix")
                            }
                        }
                    } else {
                        if (tappedAny || sawButtonAny) {
                            val still = if (lastAfter != null) Formatter.formatShortFileSize(this@CleanerAccessibilityService, lastAfter!!) else "cache"
                            bad.add(FailedApp(pkg, label, "still $still after $roundsUsed tries — needs a manual tap"))
                            appendLog("[${i + 1}/${queue.size}] $label: couldn't verify clear")
                        } else {
                            bad.add(FailedApp(pkg, label, "button not found after $roundsUsed tries"))
                            appendLog("[${i + 1}/${queue.size}] $label: couldn't tap Clear cache")
                        }
                    }
                    _autoState.value = _autoState.value.copy(index = i + 1, current = label, currentPkg = pkg, freedBytes = totalFreed)
                    // Pop the Settings stack so activities don't pile up.
                    try { backNav(drilledAny) } catch (_: Exception) {}
                    delay(100)
                }

                if (isActive) {
                    overlay?.showDone(ok.size, totalFreed)
                    delay(550)
                }
            } finally {
                overlay?.hide()
                returnToToolz()
                _autoState.value = _autoState.value.copy(
                    running = false, paused = false, current = "", currentPkg = "", done = true,
                    freedBytes = totalFreed,
                    cleared = ok, failedApps = bad,
                    log = _autoState.value.log + "Done — ${ok.size} cleared, ${bad.size} need attention"
                )
            }
        }
        return true
    }

    fun stopAutoClear() {
        job?.cancel()
        overlay?.hide()
        returnToToolz()
        _autoState.value = _autoState.value.copy(running = false, paused = false, done = true,
            log = _autoState.value.log + "Stopped by user")
    }

    fun resetAutoClear() {
        _autoState.value = AutoClearState()
    }

    private fun returnToToolz() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (launchIntent != null) startActivity(launchIntent)
        } catch (_: Exception) {}
    }

    private fun getTargetRoot(): AccessibilityNodeInfo? {
        try {
            val active = rootInActiveWindow
            if (active != null && active.packageName != packageName && isSettingsPackage(active.packageName)) {
                return active
            }
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName != packageName && isSettingsPackage(r.packageName)) {
                    return r
                }
            }
            for (w in windows) {
                val r = w.root ?: continue
                if (r.packageName != packageName && w.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    return r
                }
            }
            if (active != null && active.packageName != packageName) {
                return active
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

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

    private suspend fun backNav(navigatedStorage: Boolean) {
        // WindowId unwind (package-agnostic): BACK up to 3×, 400ms apart, until
        // windowId differs from the pre-back id or the root goes null.
        // navigatedStorage kept for call-site compat; depth is id-driven.
        try {
            val startId = try { (getTargetRoot() ?: rootInActiveWindow)?.windowId } catch (_: Exception) { null } ?: return
            if (navigatedStorage) { /* depth handled by the id check below */ }
            repeat(3) {
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (_: Exception) { return }
                delay(400)
                val cur = try { getTargetRoot() ?: rootInActiveWindow } catch (_: Exception) { null } ?: return
                if (cur.windowId != startId) return
            }
        } catch (_: Exception) {}
    }

    /** AC-v2 window-ready gating: poll until a Settings window with content is front. */
    private suspend fun awaitSettingsWindow(timeoutMs: Long = WINDOW_READY_TIMEOUT_MS): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: AccessibilityNodeInfo? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val root = getTargetRoot()
                if (root != null) {
                    last = root
                    val pkgOk = isSettingsWindow(root)
                    val hasContent = try { root.childCount > 0 } catch (_: Exception) { false }
                    if (pkgOk && hasContent) return root
                }
            } catch (_: Exception) {}
            delay(WINDOW_POLL_MS)
        }
        return last
    }

    private fun isSettingsWindow(root: AccessibilityNodeInfo): Boolean {
        return try {
            val pkg = root.packageName
            if (isSettingsPackage(pkg)) return true
            // Fallback: window list contains a Settings root even if active window lags.
            for (w in windows) {
                val r = try { w.root } catch (_: Exception) { null } ?: continue
                if (isSettingsPackage(r.packageName)) return true
            }
            false
        } catch (_: Exception) { false }
    }

    /** AC-v2 multi-verify: re-query cache up to [VERIFY_READS] times, keep the minimum. */
    private suspend fun multiVerifyCache(pkg: String, before: Long?, tapped: Boolean = false): VerifyOutcome {
        if (before == null || before <= 0L) return VerifyOutcome(alreadyClean = true, freed = 0L, after = before)
        var best: Long? = null
        repeat(VERIFY_READS) { i ->
            if (i == 1) delay(250)
            if (i == 2) delay(500)
            val cur = try { cacheBytes(pkg) } catch (_: Exception) { null }
            if (cur != null && (best == null || cur < (best ?: Long.MAX_VALUE))) best = cur
            val snap = best
            if (snap != null && snap < before) {
                // Early exit once a drop is confirmed; still cheap (≤3 reads).
                if (i >= 1) return VerifyOutcome(cleared = true, freed = before - snap, after = snap)
            }
            // S1.1.1 button-state fallback (stats lag reality): on every poll iteration,
            // a fresh root showing the Storage screen (no clear node, no storage row)
            // with the Clear-cache button disabled proves the cache hit zero.
            // Gated on tapped==true so a fresh page with zero tap evidence never
            // clears here (that case belongs to the ALREADY_ZERO path).
            if (tapped) {
                val freshRoot = try { getTargetRoot() } catch (_: Exception) { null }
                val onStorageScreen = try {
                    freshRoot != null &&
                        findClearNode(freshRoot) == null &&
                        findStorageNode(freshRoot) == null
                } catch (_: Exception) { false }
                val btnEmpty = try { isClearDisabled(freshRoot) } catch (_: Exception) { false }
                if (onStorageScreen && btnEmpty) {
                    // Button proves true cache is zero: freed = before - min(floor, before)
                    // where floor is the clamped stats floor (0 when stats are stale at/above before).
                    val floor = snap?.takeIf { it < before } ?: 0L
                    return VerifyOutcome(
                        cleared = true, freed = before - minOf(floor, before),
                        after = snap, buttonConfirmed = true
                    )
                }
            }
        }
        val after = best ?: try { cacheBytes(pkg) } catch (_: Exception) { null }
        // One last button-state check after the final read (same gating: tapped only).
        if (tapped && (after == null || after >= before)) {
            val freshRoot = try { getTargetRoot() } catch (_: Exception) { null }
            val onStorageScreen = try {
                freshRoot != null &&
                    findClearNode(freshRoot) == null &&
                    findStorageNode(freshRoot) == null
            } catch (_: Exception) { false }
            val btnEmpty = try { isClearDisabled(freshRoot) } catch (_: Exception) { false }
            if (onStorageScreen && btnEmpty) {
                val floor = after?.takeIf { it < before } ?: 0L
                return VerifyOutcome(
                    cleared = true, freed = before - minOf(floor, before),
                    after = after, buttonConfirmed = true
                )
            }
        }
        return if (after != null && after < before) {
            VerifyOutcome(cleared = true, freed = before - after, after = after)
        } else {
            VerifyOutcome(cleared = false, freed = 0L, after = after)
        }
    }

    private data class VerifyOutcome(
        val cleared: Boolean = false,
        val alreadyClean: Boolean = false,
        val freed: Long = 0L,
        val after: Long? = null,
        val buttonConfirmed: Boolean = false
    )

    private fun isButtonNode(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        return try {
            val cls = (n.className?.toString() ?: "")
            cls.contains("Button", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    /**
     * Window-ready gating: poll [rootInActiveWindow] every 250ms until a fresh
     * window appears (stale-window taps land on the previous app's page).
     * Settles 500ms, then returns the root with its [AccessibilityNodeInfo.getWindowId].
     * Callers track the id in `lastWin` across opens within the app.
     */
    private suspend fun awaitWindow(lastId: Int?, timeoutMs: Long = 5000L): Pair<AccessibilityNodeInfo?, Int> {
        val start = android.os.SystemClock.uptimeMillis()
        var latest: AccessibilityNodeInfo? = null
        var latestId = lastId ?: -1
        while (android.os.SystemClock.uptimeMillis() - start < timeoutMs) {
            val root = try { getTargetRoot() ?: rootInActiveWindow } catch (_: Exception) { null }
            if (root != null) {
                latest = root
                latestId = root.windowId
                if (lastId == null || root.windowId != lastId) {
                    delay(500)
                    return root to root.windowId
                }
            }
            delay(250)
        }
        val fallback = try { getTargetRoot() ?: rootInActiveWindow } catch (_: Exception) { null }
        if (fallback != null) return fallback to fallback.windowId
        return latest to latestId
    }

    /** Probe for a clear-cache node without clicking (learn-once direct-path detection). */
    private suspend fun probeClearNode(timeoutMs: Long = 4000L): Boolean {
        val start = android.os.SystemClock.uptimeMillis()
        while (android.os.SystemClock.uptimeMillis() - start < timeoutMs) {
            val root = getTargetRoot()
            if (root != null) {
                try { if (findClearNode(root) != null) return true } catch (_: Exception) {}
            }
            delay(250)
        }
        return false
    }

    /**
     * Shared clear-cache matcher: (a) view-id list first (clickable + enabled only),
     * (b) text match collecting ALL clickable + enabled matches, preferring the
     * `android.widget.Button`, else the first match. Non-chosen id nodes are recycled.
     */
    private fun findClearNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        // (a) View-id match first.
        var buttonChoice: AccessibilityNodeInfo? = null
        var fallbackChoice: AccessibilityNodeInfo? = null
        for (id in CLEAR_CACHE_VIEW_IDS) {
            val nodes = try { root.findAccessibilityNodeInfosByViewId(id) } catch (_: Exception) { emptyList() }
            for (n in nodes) {
                val target = try { findClickableTarget(n) } catch (_: Exception) { null }
                if (target != null) {
                    if (isButtonNode(target) || isButtonNode(n)) {
                        if (buttonChoice == null) buttonChoice = target
                    } else {
                        if (fallbackChoice == null) fallbackChoice = target
                    }
                }
            }
            for (n in nodes) {
                if (n !== buttonChoice && n !== fallbackChoice) {
                    try { n.recycle() } catch (_: Exception) {}
                }
            }
            if (buttonChoice != null) break
        }
        if (buttonChoice != null) {
            if (fallbackChoice != null && fallbackChoice !== buttonChoice) {
                try { fallbackChoice.recycle() } catch (_: Exception) {}
            }
            return buttonChoice
        }
        if (fallbackChoice != null) return fallbackChoice
        // (b) Text match: collect ALL clickable + enabled matches (600 steps), prefer the Button.
        val matches = mutableListOf<AccessibilityNodeInfo>()
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var steps = 0
            while (queue.isNotEmpty() && steps++ < 600) {
                val n = queue.removeFirst()
                val text = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()
                if (text.isNotEmpty()) {
                    val isDangerous = DANGEROUS_ACTION_TEXTS.any { text.contains(it) }
                    if (!isDangerous && CLEAR_CACHE_TEXTS.any { text.contains(it) }) {
                        if (n.isEnabled) {
                            val target = findClickableTarget(n)
                            if (target != null) matches.add(target)
                        }
                    }
                }
                for (i in 0 until n.childCount) {
                    try { n.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        if (matches.isEmpty()) return null
        return matches.firstOrNull { it.className?.toString() == "android.widget.Button" }
            ?: matches.firstOrNull { isButtonNode(it) }
            ?: matches[0]
    }

    /** True when a clear-cache node exists but has no clickable target (cache already zero). */
    private fun isClearDisabled(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        for (id in CLEAR_CACHE_VIEW_IDS) {
            val nodes = try { root.findAccessibilityNodeInfosByViewId(id) } catch (_: Exception) { continue }
            for (n in nodes) {
                val target = try { findClickableTarget(n) } catch (_: Exception) { null }
                val disabled = !n.isEnabled && target == null
                try { n.recycle() } catch (_: Exception) {}
                if (disabled) return true
            }
        }
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var steps = 0
            while (queue.isNotEmpty() && steps++ < 600) {
                val n = queue.removeFirst()
                val text = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()
                if (text.isNotEmpty()) {
                    val isDangerous = DANGEROUS_ACTION_TEXTS.any { text.contains(it) }
                    if (!isDangerous && CLEAR_CACHE_TEXTS.any { text.contains(it) }) {
                        if (!n.isEnabled && findClickableTarget(n) == null) return true
                    }
                }
                for (i in 0 until n.childCount) {
                    try { n.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /** App info → Storage row candidate (null on the Storage screen itself). Single 600-step pass. */
    private fun findStorageNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        var candidate: AccessibilityNodeInfo? = null
        var isStorageScreen = false
        try {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var steps = 0
            while (queue.isNotEmpty() && steps++ < 600 && candidate == null) {
                val n = queue.removeFirst()
                val text = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()
                if (text.isNotEmpty()) {
                    val isDangerous = DANGEROUS_ACTION_TEXTS.any { text.contains(it) }
                    if (!isDangerous) {
                        if (CLEAR_CACHE_TEXTS.any { text.contains(it) } ||
                            text.contains("clear storage") || text.contains("clear data") ||
                            text.contains("user data") || text.contains("données utilisateur") || text.contains("datos de usuario")) {
                            isStorageScreen = true
                        } else if (!isStorageScreen &&
                            STORAGE_TEXTS.any { text == it || text.startsWith("storage &") || text.startsWith("storage and") || text.startsWith("stockage &") }) {
                            val target = findClickableTarget(n)
                            if (target != null) candidate = target
                        }
                    }
                }
                for (i in 0 until n.childCount) {
                    try { n.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return if (isStorageScreen) null else candidate
    }

    /** Clicks the clear-cache button when present, else drills into the Storage row. */
    private fun tapClearCache(root: AccessibilityNodeInfo?, alreadyDrilledStorage: Boolean = false): Boolean {
        val clear = try { findClearNode(root) } catch (_: Exception) { null }
        if (clear != null) {
            return try { clear.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
        }
        if (root == null || alreadyDrilledStorage) return false
        val storage = try { findStorageNode(root) } catch (_: Exception) { null }
        if (storage != null) {
            return try { storage.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
        }
        return false
    }

    private enum class TapResult { NONE, DRILLED_STORAGE, TAPPED, ALREADY_ZERO, BUTTON_SEEN }

    private fun findClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var curr: AccessibilityNodeInfo? = node
        for (depth in 0..4) {
            if (curr == null) break
            if (curr.isClickable && curr.isEnabled) return curr
            curr = curr.parent
        }
        return null
    }

    private fun processWindow(root: AccessibilityNodeInfo?, alreadyDrilledStorage: Boolean): TapResult {
        if (root == null) return TapResult.NONE

        // 1) Shared matcher (view-id first, then text preferring the Button).
        val clear = try { findClearNode(root) } catch (_: Exception) { null }
        if (clear != null) {
            val ok = try { clear.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
            if (ok) return TapResult.TAPPED
            // Button found on screen but click failed: counts as seen (sawButton), retry next attempt.
            return TapResult.BUTTON_SEEN
        }

        // 2) Disabled clear button (no clickable target) means the cache already reads zero.
        try { if (isClearDisabled(root)) return TapResult.ALREADY_ZERO } catch (_: Exception) {}

        // 3) App info → Storage drill (as before).
        if (!alreadyDrilledStorage) {
            val storage = try { findStorageNode(root) } catch (_: Exception) { null }
            if (storage != null) {
                val ok = try { storage.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
                if (ok) return TapResult.DRILLED_STORAGE
            }
        }

        return TapResult.NONE
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
