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

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.TextView
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.data.focus.AppLimitRepository
import com.frerox.toolz.data.focus.CaffeinateRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.shizuku.ShizukuHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * FocusFlowAccessibilityService provides app locking functionality by monitoring
 * window state changes and usage stats. It overlays a lock screen when an app's
 * daily limit is reached.
 */
@AndroidEntryPoint
class FocusFlowAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appLimitRepository: AppLimitRepository
    
    @Inject
    lateinit var caffeinateRepository: CaffeinateRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var usageRepository: com.frerox.toolz.data.focus.UsageStatsRepository

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var overlayShowingForPackage: String? = null
    private var currentPackage: String? = null
    private var currentPackageResumedTime: Long = 0
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var validationJob: Job? = null
    private var backgroundStopJob: Job? = null
    private var cachedHomePackage: String? = null
    private var blockedPackagePendingDismissal: String? = null
    private var previousMusicVolume: Int? = null
    private var mutedPackage: String? = null

    private var lastDismissedTime: Long = 0
    private var lastDismissedPackage: String? = null
    // Per-package dismiss times: single-slot grace let a second dismissal
    // orphan the first app's grace (re-lock while the user was still leaving).
    private val dismissedAtMs = ConcurrentHashMap<String, Long>()
    // Incremented on every user-initiated dismiss (RETURN / EXIT). Stale
    // validateAndLock coroutines capture the epoch at start and abort if it
    // changed while they were doing IO — this stops the "tap button, overlay
    // instantly reappears" stuck loop.
    private var dismissEpoch: Long = 0L

    // Full-coverage session blocklist: ALL installed distraction apps, not just
    // today's used ones. Warmed async; single-app fallback covers uncached pkgs.
    private var sessionDistractionSet = emptySet<String>()
    private var sessionBlocklistRefreshJob: Job? = null
    private var lastBlocklistRefreshMs: Long = 0L

    // Settings Cache
    private var isFocusSessionActive = false
    private var categoryMappings = emptyMap<String, String>()
    private var appLimits = emptyMap<String, Long>()
    private var aiCategoryMappings = emptyMap<String, String>()

    // Caffeinate Cache
    private var caffeinateEverything = false
    private var caffeinateAutoPkgs = emptySet<String>()
    private var caffeinateDebounceJob: Job? = null
    private var caffeinatePendingStopJob: Job? = null

    companion object {
        private const val TAG = "FocusFlowService"
        private const val CAFFEINATE_EXIT_GRACE_MS = 2500L
        private const val CAFFEINATE_START_DEBOUNCE_MS = 300L
        private const val DISMISS_GRACE_PERIOD_MS = 8000L
        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.google.android.gms",
            "com.android.settings",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            // Samsung OneUI lock screen & AOD packages
            "com.samsung.android.app.aodservice",
            "com.samsung.android.dynamiclock",
            "com.samsung.android.homemode",
            "com.samsung.android.lockscreen",
            "com.samsung.systemui.lockscreen",
            "com.samsung.android.app.cocktailbarservice", // Edge panel
            "com.samsung.android.biometrics.app.setting",
            "com.samsung.android.brightnesscontroller",
            "com.samsung.android.app.smartcapture",
            "com.samsung.android.Bixby",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.bixby.wakeup",
            "com.samsung.android.app.routines",
            // Other OEM system packages
            "com.miui.aod",
            "com.oplus.aod",
            "com.coloros.lockscreen",
            "com.oppo.lockscreen"
        )
        private val KNOWN_LAUNCHERS = setOf(
            "com.android.launcher3",
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.launcher",
            // Samsung OneUI launchers
            "com.sec.android.app.launcher",
            "com.samsung.android.app.launcher",
            "com.samsung.android.app.homescreen",   // OneUI 6+
            // MIUI
            "com.miui.home",
            // Huawei / Honor
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher",
            // Oppo / Realme / OnePlus
            "com.oppo.launcher",
            "com.coloros.home",
            "com.realme.launcher",
            "com.oneplus.launcher",
            "net.oneplus.launcher",
            "net.oneplus.h2launcher",
            // Vivo / BBK / Transsion
            "com.vivo.launcher",
            "com.bbk.launcher2",
            "com.transsion.hilauncher",
            "com.transsion.XOSLauncher",
            // Motorola / LG / Asus / Sony
            "com.motorola.launcher3",
            "com.asus.launcher",
            "com.sonyericsson.home",
            "com.lge.launcher2",
            "com.lge.launcher3",
            // Third-party launchers
            "com.teslacoilsw.launcher",
            "ch.deletescape.lawnchair.plah",
            "app.lawnchair",
            "app.lawnchair.playstore",
            "com.actionlauncher.playstore",
            "com.microsoft.launcher",
            "com.nothing.launcher",
            "com.smartlauncher.smartlauncher5",
            "ginlemon.flowerfree",
            "com.niagara.launcher",
            "bitpit.launcher"
        )
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen off detected -> stopping auto caffeinate immediately")
                    caffeinateDebounceJob?.cancel()
                    stopAutoCaffeinate(immediate = true)
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen on detected -> verifying lock screen")
                    if (isLockScreen()) {
                        stopAutoCaffeinate()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "User unlocked screen -> rechecking caffeinate (with retry)")
                    serviceScope.launch {
                        // Right after unlock the foreground window is often still the
                        // launcher/locker remnant. Retry so AUTO resumes when
                        // unlocking straight into a target app.
                        repeat(3) { attempt ->
                            delay(if (attempt == 0) 600 else 1000)
                            try {
                                evaluateForegroundAppForCaffeinate()
                                if (CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value) return@launch
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    private val toolzPackage: String get() = packageName

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FocusFlowAccessibilityService created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshHomePackage()
        startPeriodicValidation()
        observeSettings()

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register screenReceiver", e)
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.focusFlowSessionActive.collect { active ->
                Log.d(TAG, "Settings: focusFlowSessionActive = $active")
                isFocusSessionActive = active
                if (active) scheduleSessionBlocklistRefresh(force = true)
                currentPackage?.let { validateAndLock(it) }
            }
        }
        serviceScope.launch {
            settingsRepository.appCategoryMappings.collect { mappings ->
                Log.d(TAG, "Settings: categoryMappings size = ${mappings.size}")
                categoryMappings = mappings
                if (isFocusSessionActive) scheduleSessionBlocklistRefresh()
                currentPackage?.let { validateAndLock(it) }
            }
        }
        serviceScope.launch {
            appLimitRepository.allLimits.collect { limits ->
                Log.d(TAG, "Settings: appLimits size = ${limits.size}")
                appLimits = limits.associate { it.packageName to it.limitMillis }
                currentPackage?.let { validateAndLock(it) }
            }
        }
        // Load AI categories from SharedPreferences
        serviceScope.launch {
            while(isActive) {
                try {
                    val prefs = getSharedPreferences("focus_ai_category_cache", Context.MODE_PRIVATE)
                    val jsonStr = prefs.getString("categories_json", null)
                    if (jsonStr != null) {
                        val json = org.json.JSONObject(jsonStr)
                        val newAiMappings = mutableMapOf<String, String>()
                        json.keys().forEach { pkg ->
                            newAiMappings[pkg] = json.getString(pkg)
                        }
                        if (newAiMappings != aiCategoryMappings) {
                            Log.d(TAG, "Settings: aiCategoryMappings updated, size = ${newAiMappings.size}")
                            aiCategoryMappings = newAiMappings
                            if (isFocusSessionActive) scheduleSessionBlocklistRefresh()
                            currentPackage?.let { validateAndLock(it) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading AI categories", e)
                }
                delay(30000) // Every 30s is enough for cache
            }
        }
        serviceScope.launch {
            settingsRepository.caffeinateEverything.collect { everything ->
                caffeinateEverything = everything
                currentPackage?.let { checkCaffeinate(it) }
            }
        }
        serviceScope.launch {
            settingsRepository.caffeinateAutoPkgs.collect { pkgs ->
                caffeinateAutoPkgs = pkgs
                currentPackage?.let { checkCaffeinate(it) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                val className = event.className?.toString() ?: ""

                Log.d(TAG, "onAccessibilityEvent TYPE_WINDOW_STATE_CHANGED: $packageName / $className")

                // CRITICAL: Always check Caffeinate first on EVERY window state change!
                checkCaffeinate(packageName, className)

                // Handle package change for precise tracking
                if (packageName != currentPackage) {
                    Log.d(TAG, "Package changed: $currentPackage -> $packageName")
                    currentPackage = packageName
                    currentPackageResumedTime = System.currentTimeMillis()
                }

                // Ignore events from the overlay itself or Toolz UI
                if (packageName == toolzPackage && !className.contains("Activity") && !className.contains("MainActivity")) {
                    return
                }

                // Handle "Safe" contexts
                if (isHomePackage(packageName) || (packageName == toolzPackage && (className.contains("Activity") || className.contains("MainActivity")))) {
                    if (isHomePackage(packageName) && shouldKeepOverlayVisibleOnHome()) {
                        Log.d(TAG, "Keeping overlay visible on Home")
                        return
                    }
                    Log.d(TAG, "Hiding overlay because of safe context: $packageName")
                    hideOverlay()
                    return
                }

                if (SYSTEM_UI_PACKAGES.contains(packageName)) {
                    return
                }

                validateAndLock(packageName)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Window layer changes (e.g. gesture navigation to home, recents, notification shade)
                if (CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value) {
                    evaluateForegroundAppForCaffeinate()
                }
                // Gesture nav often emits ONLY this (no STATE_CHANGED): track it
                // or currentPackage goes stale and the blocker either misses the
                // new app or re-locks a ghost of the old one (stuck loop).
                try {
                    val detected = getDetectedForegroundPackage()
                    if (detected != null && detected != currentPackage) {
                        Log.d(TAG, "WINDOWS_CHANGED: foreground now $detected (was $currentPackage)")
                        currentPackage = detected
                        currentPackageResumedTime = System.currentTimeMillis()
                        if (!isHomePackage(detected) && detected != toolzPackage &&
                            !SYSTEM_UI_PACKAGES.contains(detected)
                        ) {
                            validateAndLock(detected)
                        } else if (isHomePackage(detected) && !shouldKeepOverlayVisibleOnHome()) {
                            hideOverlay()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        
        // Only trigger clipboard check if WE are the ones becoming focused
        if (packageName == toolzPackage) {
            // Check if standard access is needed. If Shizuku is authorized, 
            // the service handles it automatically without needing focus.
            if (!ShizukuHelper.isAuthorized()) {
                serviceScope.launch {
                    delay(250) // Small delay to let MainActivity register focus
                    triggerClipboardCheck()
                }
            }
        }
    }

    private fun triggerClipboardCheck() {
        // PAUSED — clipboard tool is under development. No-op until re-enabled.
    }

    private var homePackages = setOf<String>()

    private fun refreshHomePackage() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0
            val resolveInfos = packageManager.queryIntentActivities(intent, flags)
            val pkgs = resolveInfos.mapNotNull { it.activityInfo?.packageName }.toMutableSet()
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let {
                pkgs.add(it)
            }
            pkgs.addAll(KNOWN_LAUNCHERS)
            homePackages = pkgs
            Log.d(TAG, "Refreshed home packages: $homePackages")
            cachedHomePackage = resolveInfos.firstOrNull()?.activityInfo?.packageName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh home packages", e)
            homePackages = KNOWN_LAUNCHERS
        }
    }

    private fun isHomePackage(packageName: String): Boolean {
        if (homePackages.isEmpty()) refreshHomePackage()
        return homePackages.contains(packageName)
    }

    private fun startPeriodicValidation() {
        validationJob?.cancel()
        // Default dispatcher: the combined detector does UsageStats + PM IPC
        // that must never run on Main (it janked blocking + buttons before).
        validationJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                // Combined detector: usage-events truth keeps currentPackage
                // honest when a11y goes blind (secure windows) or events are
                // missing (gesture nav) — the stale-relock stuck loop lived here.
                val detected = try { getCombinedForegroundPackage() } catch (_: Exception) { null }
                val pkg = detected ?: currentPackage
                if (detected != null && detected != currentPackage) {
                    Log.d(TAG, "Periodic: foreground now $detected (was $currentPackage)")
                    currentPackage = detected
                    currentPackageResumedTime = System.currentTimeMillis()
                }

                if (pkg != null && !isHomePackage(pkg) && pkg != toolzPackage && !SYSTEM_UI_PACKAGES.contains(pkg)) {
                    validateAndLock(pkg)
                }

                // Auto-Caffeinate watchdog:
                // If auto-caffeinate is currently active, ensure the active window is STILL a valid target app
                if (CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value) {
                    if (pkg == null || isOutsideApp(pkg, "")) {
                        Log.d(TAG, "Watchdog: outside app ($pkg) while AUTO caffeinate running -> stopping")
                        stopAutoCaffeinate()
                    } else {
                        val isTarget = caffeinateEverything || caffeinateAutoPkgs.contains(pkg)
                        if (!isTarget) {
                            Log.d(TAG, "Watchdog: app $pkg is no longer a target -> stopping")
                            stopAutoCaffeinate()
                        }
                    }
                }
            }
        }
    }

    private fun validateAndLock(packageName: String) {
        if (packageName == toolzPackage) {
            hideOverlay()
            return
        }

        if (isInGrace(packageName)) {
            return
        }

        // Capture epoch so a user tap on RETURN/EXIT while we do IO invalidates us.
        val startEpoch = dismissEpoch

        // Default dispatcher: freshness checks query UsageStats + PM (binder
        // IPC) that must never run on Main. show/hideOverlay self-hop to Main.
        serviceScope.launch(Dispatchers.Default) {
            // Freshness first: drop stale requests before any work. Combined
            // detector (a11y + UsageEvents) sees secure windows + gesture nav
            // that the old a11y-only check missed (block never fired / ghost
            // re-locks of an app the user already left).
            val fg0 = try { getCombinedForegroundPackage() } catch (_: Exception) { null } ?: currentPackage
            if (fg0 != packageName) return@launch
            if (startEpoch != dismissEpoch) return@launch
            if (isInGrace(packageName)) return@launch

            // ── Fast path: focus session blocks ALL distraction apps (even with
            // zero usage today). Cache-only here (Main-safe); unknown pkgs fall
            // through to a single IO lookup below — never PM on Main.
            var sessionDistr: Boolean? = null
            if (isFocusSessionActive) {
                when (isDistractionForSessionCached(packageName)) {
                    true -> {
                        Log.d(TAG, "validateAndLock: Blocking $packageName due to focus session")
                        showOverlay(packageName, true)
                        enforceLockedApp(packageName)
                        return@launch
                    }
                    false -> sessionDistr = false
                    null -> {
                        scheduleSessionBlocklistRefresh()
                        val resolved = withContext(Dispatchers.IO) { isUnknownAppDistraction(packageName) }
                        if (startEpoch != dismissEpoch) return@launch
                        if (isInGrace(packageName)) return@launch
                        val fg1 = try { getCombinedForegroundPackage() } catch (_: Exception) { null } ?: currentPackage
                        if (fg1 != packageName) return@launch
                        if (fg1 == toolzPackage || isHomePackage(fg1!!) || SYSTEM_UI_PACKAGES.contains(fg1)) return@launch
                        sessionDistr = resolved
                        if (resolved) {
                            Log.d(TAG, "validateAndLock: Blocking $packageName due to focus session")
                            showOverlay(packageName, true)
                            enforceLockedApp(packageName)
                            return@launch
                        }
                    }
                }
            }

            val limitMillis = appLimits[packageName]
            val usageTime = withContext(Dispatchers.IO) { getTodayUsage(packageName) }

            // ── Stale guards: abort if user dismissed or navigated away mid-IO.
            // Without these, an in-flight check re-shows the overlay right after
            // RETURN/EXIT hides it — the "button does nothing, stuck" loop.
            if (startEpoch != dismissEpoch) return@launch
            if (isInGrace(packageName)) return@launch
            val foreground = try { getCombinedForegroundPackage() } catch (_: Exception) { null } ?: currentPackage
            if (foreground != packageName) return@launch
            if (foreground == toolzPackage || isHomePackage(foreground!!) || SYSTEM_UI_PACKAGES.contains(foreground)) {
                if (isOverlayShowing && overlayShowingForPackage == packageName) hideOverlay()
                return@launch
            }

            val isSessionActive = isFocusSessionActive
            // PM-free: reuse the session resolution above when active; otherwise
            // maps + pkg-keywords only (no PM on Main). If the session toggled
            // on mid-IO and the pkg is still uncached, resolve once via IO.
            var isDistraction = if (isSessionActive) {
                sessionDistr ?: (isDistractionForSessionCached(packageName) ?: false)
            } else (
                categoryMappings[packageName] == "Distraction" ||
                    aiCategoryMappings[packageName] == "DISTRACTION" ||
                    (categoryMappings[packageName] == null && aiCategoryMappings[packageName] == null && isLikelyDistraction(packageName))
                )
            if (isSessionActive && sessionDistr == null && isDistractionForSessionCached(packageName) == null) {
                scheduleSessionBlocklistRefresh()
                isDistraction = withContext(Dispatchers.IO) { isUnknownAppDistraction(packageName) }
                if (startEpoch != dismissEpoch) return@launch
                if (isInGrace(packageName)) return@launch
            }

            val shouldLock = if (isSessionActive && isDistraction) {
                Log.d(TAG, "validateAndLock: Blocking $packageName due to focus session")
                true
            } else if (limitMillis != null && limitMillis > 0) {
                val locked = usageTime >= limitMillis
                if (locked) Log.d(TAG, "validateAndLock: Blocking $packageName due to limit ($usageTime >= $limitMillis)")
                locked
            } else {
                false
            }

            if (startEpoch != dismissEpoch) return@launch
            // Re-verify foreground right before showing — paranoid but cheap.
            val fgNow = try { getCombinedForegroundPackage() } catch (_: Exception) { null } ?: currentPackage
            if (shouldLock && fgNow != packageName) return@launch

            if (shouldLock) {
                showOverlay(packageName, isSessionActive && isDistraction)
                enforceLockedApp(packageName)
            } else {
                if (isOverlayShowing && overlayShowingForPackage == packageName) {
                    Log.d(TAG, "hiding overlay for $packageName because shouldLock is false (usage: $usageTime)")
                    hideOverlay()
                }
            }
        }
    }

    /**
     * Main-thread-safe session lookup: explicit mappings + warmed blocklist
     * only. Returns null when unknown — caller must resolve via IO, never do
     * PackageManager IPC on Main (binder calls there jank the service and make
     * the block feel dead / buttons unresponsive).
     */
    private fun isDistractionForSessionCached(packageName: String): Boolean? {
        if (packageName == toolzPackage) return false
        categoryMappings[packageName]?.let { return it == "Distraction" }
        aiCategoryMappings[packageName]?.let { return it == "DISTRACTION" }
        if (sessionDistractionSet.contains(packageName)) return true
        if (isLikelyDistraction(packageName)) return true
        return null
    }

    /**
     * Session-blocking decision covering ALL installed apps, not just today's
     * usage. Priority: explicit user mapping → AI cache → warmed blocklist →
     * single-app fallback (Play category + pkg/label keywords).
     * May do PackageManager IPC — call on IO only, never on Main.
     */
    private fun isDistractionForSession(packageName: String): Boolean {
        if (packageName == toolzPackage) return false
        categoryMappings[packageName]?.let { return it == "Distraction" }
        aiCategoryMappings[packageName]?.let { return it == "DISTRACTION" }
        if (sessionDistractionSet.contains(packageName)) return true
        // Opportunistically warm the full list (throttled) so future checks hit cache.
        if (isFocusSessionActive) scheduleSessionBlocklistRefresh()
        return isUnknownAppDistraction(packageName)
    }

    private fun scheduleSessionBlocklistRefresh(force: Boolean = false) {
        if (!isFocusSessionActive) return
        if (sessionBlocklistRefreshJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (!force && now - lastBlocklistRefreshMs < 60_000L) return
        sessionBlocklistRefreshJob = serviceScope.launch(Dispatchers.IO) {
            try {
                refreshSessionBlocklist()
            } catch (e: Exception) {
                Log.w(TAG, "Session blocklist refresh failed", e)
            }
        }
    }

    private fun refreshSessionBlocklist() {
        val pm = packageManager
        val apps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getInstalledApplications failed", e)
            return
        }
        val result = HashSet<String>(apps.size / 4)
        for (app in apps) {
            try {
                val pkg = app.packageName
                if (pkg == toolzPackage) continue
                if (pkg.isBlank()) continue
                // Skip launchers / system UI early (cheap set checks, no PM calls).
                if (homePackages.contains(pkg) || SYSTEM_UI_PACKAGES.contains(pkg)) continue
                if (isSystemUi(pkg)) continue
                if (pm.getLaunchIntentForPackage(pkg) == null) continue
                if (isSessionDistraction(pkg, app)) result.add(pkg)
            } catch (_: Exception) { }
        }
        sessionDistractionSet = result
        lastBlocklistRefreshMs = System.currentTimeMillis()
        Log.d(TAG, "Session blocklist refreshed: ${result.size} distraction apps")
    }

    private fun isSessionDistraction(packageName: String, appInfo: android.content.pm.ApplicationInfo?): Boolean {
        categoryMappings[packageName]?.let { return it == "Distraction" }
        aiCategoryMappings[packageName]?.let { return it == "DISTRACTION" }
        // Productive overrides (user or AI) never blocked by heuristics below.
        if (categoryMappings[packageName] == "Productive") return false
        // AI cache stores TOOLZ for productive.
        try {
            val pm = packageManager
            val info = appInfo ?: pm.getApplicationInfo(packageName, 0)
            val cat = info.category
            if (cat == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                cat == android.content.pm.ApplicationInfo.CATEGORY_SOCIAL
            ) return true
            // ENTERTAINMENT / VIDEO handled via keywords+label to avoid
            // false-positives on camera/gallery.
            val label = try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { "" }
            if (isLikelyDistraction(packageName) || isLikelyDistractionLabel(label) || isLikelyBrowserLabel(label)) return true
        } catch (_: Exception) {
            return isLikelyDistraction(packageName)
        }
        return false
    }

    /** Single-app fallback used when the warmed blocklist hasn't covered [pkg] yet. */
    private fun isUnknownAppDistraction(packageName: String): Boolean {
        if (packageName == toolzPackage) return false
        // Non-launchable entries (keyboards, live wallpapers) are never session-blocked.
        if (!isLaunchableApp(packageName)) return false
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            isSessionDistraction(packageName, info)
        } catch (_: Exception) {
            isLikelyDistraction(packageName)
        }
    }

    private fun isLikelyDistractionLabel(label: String): Boolean {
        if (label.isBlank()) return false
        val lower = label.lowercase()
        val keywords = setOf(
            "instagram", "tiktok", "facebook", "youtube", "netflix", "twitch",
            "snapchat", "reddit", "pinterest", "disney", "prime video", "hotstar",
            "candy crush", "clash", "pubg", "free fire", "fortnite", "minecraft",
            "roblox", "brawl stars", "ludo", "dream11", "casino", "poker", "slots",
            "tinder", "bumble", "dating",
            // Browsers (session-blocked unless user marks Productive)
            "firefox", "brave", "opera", "vivaldi", "duckduckgo", "ecosia",
            "samsung internet",
            // More social / short-video
            "threads", "bluesky", "twitter",
        )
        if (lower.trim() == "x") return true // X (Twitter rebrand) — exact match only
        return keywords.any { lower.contains(it) }
    }

    private fun isLikelyDistraction(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val keywords = setOf(
            "facebook", "instagram", "tiktok", "youtube", "twitter", "x.android",
            "snapchat", "netflix", "disney", "game", "pubg", "freefire", "reels",
            "shorts", "twitch", "reddit", "pinterest", "tumblr", "spotify",
            "soundcloud", "clash", "candy", "minecraft", "roblox", "brawl",
            "among", "fortnite", "garena", "mlbb", "mobilelegends", "likee",
            "kwai", "vigo", "helo", "moj", "roposo", "josh", "ludo", "carrom",
            // Messengers / social / dating (session-blocked unless marked productive)
            "whatsapp", "telegram", "discord", "messenger", "viber", "wechat", "line.",
            "tinder", "bumble", "dating", "badoo", "hinge",
            // Streaming / video
            "hotstar", "primevideo", "hbomax", "crunchyroll", "kick.", "rumble",
            "dailymotion", "vlive", "plex", "stream",
            // Games publishers / genres missed by generic "game"
            "supercell", "playrix", "zynga", "niantic", "pokem", "riot", "activision",
            "easports", "king.", "outfit7", "talkingtom", "subwaysurf", "templerun",
            "dream11", "casino", "poker", "slots", "betting", "lotto", "chess",
            // Browsers (session-blocked unless user marks Productive)
            "firefox", "mozilla", "brave", "opera", "vivaldi", "duckduckgo",
            "ecosia", "samsungbrowser", "naver", "whale", "kiwi", "stargon",
            "yandex.browser", "miuibrowser", "heytapbrowser",
            // More social / short-video (obscure pkgs)
            "musically", "trill", "likee", "mxsharing", "threads", "bluesky",
            "chrome", "chromium",
        )
        return keywords.any { lower.contains(it) }
    }

    /** Browsers are a top distraction vector: label-based catch-all. */
    private fun isLikelyBrowserLabel(label: String): Boolean {
        if (label.isBlank()) return false
        val t = label.lowercase().trim()
        if (t == "internet" || t == "browser") return true
        val keywords = setOf(
            "chrome", "firefox", "brave", "edge", "opera", "vivaldi",
            "duckduckgo", "ecosia", "samsung internet", "mi browser",
        )
        return keywords.any { t.contains(it) }
    }

    private val launchableCache = ConcurrentHashMap<String, Boolean>()

    private fun isLaunchableApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return launchableCache.computeIfAbsent(packageName) { pkg ->
            try {
                packageManager.getLaunchIntentForPackage(pkg) != null
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: true
    }

    private fun isLockScreen(): Boolean {
        if (!isScreenInteractive()) return true
        // Primary check: KeyguardManager (works on AOSP, unreliable on OneUI)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true ||
            keyguardManager?.isDeviceLocked == true ||
            keyguardManager?.inKeyguardRestrictedInputMode() == true) return true
        // Secondary check: scan active accessibility windows for lock screen packages.
        // On Samsung OneUI, the lock screen appears as a TYPE_SYSTEM window whose root
        // package is com.samsung.android.dynamiclock, com.android.systemui, etc.
        return isOneUiLockScreenVisible()
    }

    /**
     * Scans active accessibility windows for genuine lock-screen windows.
     *
     * Strict version: a bare `com.android.systemui` TYPE_SYSTEM window (status
     * bar / nav bar / notification shade) must NEVER count as locked — those
     * windows exist permanently, even inside normal apps. That was the root
     * cause of "auto-caffeinate disabled everywhere": the old
     * `isFocused || type == TYPE_SYSTEM` check matched the status bar and
     * reported lockscreen while inside the target app.
     */
    private fun isOneUiLockScreenVisible(): Boolean {
        val activeWindows = try { windows } catch (e: Exception) {
            Log.w(TAG, "isOneUiLockScreenVisible error", e)
            return false
        } ?: return false
        // Dedicated lock/AOD packages: their mere visible presence is a strong signal.
        // Generic systemui/android: only counts when the window is FOCUSED and its
        // title says keyguard/lockscreen/bouncer (i.e. the actual lock UI, not the shade).
        val dedicatedLockPkgs = setOf(
            "com.samsung.android.dynamiclock",
            "com.samsung.android.app.aodservice",
            "com.samsung.android.lockscreen",
            "com.samsung.systemui.lockscreen",
            "com.miui.aod",
            "com.oplus.aod",
            "com.coloros.lockscreen",
            "com.oppo.lockscreen"
        )
        try {
            for (window in activeWindows) {
                val pkg = try { window.root?.packageName?.toString() } catch (_: Exception) { null } ?: continue
                val title = try { window.title?.toString()?.lowercase() } catch (_: Exception) { null } ?: ""
                val isLockTitle = title.contains("keyguard") || title.contains("lockscreen") ||
                    title.contains("bouncer")
                if (pkg in dedicatedLockPkgs) {
                    // Dedicated AOD/lock windows are only alive on the lock screen.
                    // Require focus for application windows to avoid matching cached entries,
                    // but accept unfocused TYPE_SYSTEM AOD windows (AOD rarely takes focus).
                    if (window.isFocused || window.type == AccessibilityWindowInfo.TYPE_SYSTEM || isLockTitle) {
                        Log.d(TAG, "Lock detected via dedicated pkg=$pkg title='$title' focused=${window.isFocused} type=${window.type}")
                        return true
                    }
                    continue
                }
                if (pkg == "com.android.systemui" || pkg == "android") {
                    // Status bar / nav bar / QS shade are systemui TYPE_SYSTEM but NOT lock.
                    // Only the focused keyguard/bouncer window counts.
                    if (window.isFocused && isLockTitle) {
                        Log.d(TAG, "Lock detected via systemui focused lock title='$title'")
                        return true
                    }
                    continue
                }
                // Any other window whose focused title names the lock UI.
                if (window.isFocused && isLockTitle) {
                    Log.d(TAG, "Lock detected via pkg=$pkg title='$title'")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "isOneUiLockScreenVisible error", e)
        }
        return false
    }

    private fun isKeyguardOrDream(className: String): Boolean {
        if (className.isBlank()) return false
        val lower = className.lowercase()
        return lower.contains("keyguard") ||
                lower.contains("lockscreen") ||
                lower.contains("bouncer") ||
                lower.contains("dream") ||
                lower.contains("screensaver") ||
                lower.contains("aod") ||
                lower.contains("ambient")
    }

    private fun isLauncherHeuristic(packageName: String, className: String): Boolean {
        val lowerPkg = packageName.lowercase()
        val lowerCls = className.lowercase()
        return lowerPkg.contains("launcher") ||
                lowerPkg.contains("homescreen") ||
                lowerPkg.endsWith(".home") ||
                lowerPkg.contains(".quickstep") ||
                lowerCls.contains("launcher") ||
                lowerCls.contains("homescreen") ||
                lowerCls.contains("quickstep") ||
                lowerCls.contains("recentsactivity") ||
                lowerCls.contains("fallbackhome")
    }

    private fun isSystemUi(packageName: String): Boolean {
        return SYSTEM_UI_PACKAGES.contains(packageName) ||
                packageName == "android" ||
                packageName.startsWith("com.android.systemui") ||
                packageName.startsWith("com.google.android.permissioncontroller") ||
                packageName.startsWith("com.android.permissioncontroller") ||
                packageName.startsWith("com.samsung.android.app.aodservice") ||
                packageName.startsWith("com.samsung.android.dynamiclock") ||
                packageName.startsWith("com.miui.aod") ||
                packageName.startsWith("com.oplus.aod")
    }

    private fun isOutsideApp(packageName: String, className: String = ""): Boolean {
        if (isLockScreen()) return true
        if (isKeyguardOrDream(className)) return true
        if (isHomePackage(packageName) || isLauncherHeuristic(packageName, className)) return true
        if (isSystemUi(packageName)) return true
        if (packageName == toolzPackage) return true
        if (!isLaunchableApp(packageName)) return true
        return false
    }

    private fun markDismissed(packageName: String) {
        val now = System.currentTimeMillis()
        lastDismissedTime = now
        lastDismissedPackage = packageName
        dismissedAtMs[packageName] = now
    }

    private fun isInGrace(packageName: String): Boolean {
        if (packageName == lastDismissedPackage && System.currentTimeMillis() - lastDismissedTime < DISMISS_GRACE_PERIOD_MS) return true
        val t = dismissedAtMs[packageName] ?: return false
        return System.currentTimeMillis() - t < DISMISS_GRACE_PERIOD_MS
    }

    /**
     * Ground-truth foreground via UsageEvents. Accessibility APIs go blind for
     * FLAG_SECURE windows (banking, DRM video) and often miss gesture-nav
     * transitions; UsageEvents covers both. Null when the permission is off.
     */
    private fun getForegroundViaUsageEvents(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return null
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 6000L, now) ?: return null
            var lastPkg: String? = null
            val ev = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    ev.packageName?.takeIf { it.isNotBlank() }?.let { lastPkg = it }
                }
            }
            lastPkg
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Accessibility-only foreground, no stale fallback (null when undetectable). */
    private fun getDetectedForegroundPackage(): String? {
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                val pkg = activeRoot.packageName?.toString()
                if (!pkg.isNullOrBlank()) return pkg
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val appWindow = windows?.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                val pkg = appWindow?.root?.packageName?.toString()
                if (!pkg.isNullOrBlank()) return pkg
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect foreground package", e)
        }
        return null
    }

    /**
     * Combined foreground: accessibility (real-time) reconciled with
     * UsageEvents (blind-spot-proof). When they disagree, UsageEvents wins —
     * it is the OS-reported foreground; the a11y root is often a keyboard,
     * shade, or null (secure windows).
     * @param allowPm false on Main thread: skips the launchable tiebreak
     * (binder IPC) and lets usage truth win any disagreement.
     */
    private fun getCombinedForegroundPackage(): String? {
        val a11y = getDetectedForegroundPackage()
        // Fast path: no session/limits work needed for our own UI.
        if (a11y == toolzPackage) return a11y
        val usage = try { getForegroundViaUsageEvents() } catch (_: Exception) { null }
        if (usage == null) return a11y ?: currentPackage
        if (a11y == null) return usage
        if (a11y == usage) return a11y
        // a11y blind spots (secure windows → null handled above; keyboard /
        // shade / transient system) defer to usage truth…
        if (isSystemUi(a11y) || SYSTEM_UI_PACKAGES.contains(a11y)) return usage
        // …but for app-vs-app disagreement the live window (a11y) is fresher
        // than the usage log, which can lag a second behind fast switches.
        return a11y
    }

    private fun getActiveForegroundPackage(): String? {
        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                val pkg = activeRoot.packageName?.toString()
                if (!pkg.isNullOrBlank()) return pkg
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val appWindow = windows?.firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                val pkg = appWindow?.root?.packageName?.toString()
                if (!pkg.isNullOrBlank()) return pkg
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get active foreground package", e)
        }
        return currentPackage
    }

    private fun evaluateForegroundAppForCaffeinate() {
        val pkg = getActiveForegroundPackage()
        if (pkg != null) {
            checkCaffeinate(pkg, "")
        } else {
            stopAutoCaffeinate()
        }
    }

    private fun stopAutoCaffeinate(immediate: Boolean = false) {
        if (CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value) {
            if (!immediate) {
                // Grace period: transient system windows (shade, keyboard, recents,
                // share sheet, rotation) must not kill AUTO instantly. Schedule the
                // stop and let a returning target app cancel it.
                if (caffeinatePendingStopJob?.isActive == true) return
                Log.d(TAG, "Scheduling auto-caffeinate stop in $CAFFEINATE_EXIT_GRACE_MS ms (grace)")
                caffeinatePendingStopJob = serviceScope.launch {
                    delay(CAFFEINATE_EXIT_GRACE_MS)
                    if (!isActive) return@launch
                    if (!(CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value)) return@launch
                    // Re-verify before actually stopping: the user may be back inside.
                    val pkg = try { getActiveForegroundPackage() } catch (_: Exception) { null }
                    if (pkg != null && !isOutsideApp(pkg, "")) {
                        val stillTarget = caffeinateEverything || caffeinateAutoPkgs.contains(pkg)
                        if (stillTarget) {
                            Log.d(TAG, "Grace stop cancelled, back inside target=$pkg")
                            return@launch
                        }
                    } else if (pkg != null && !isOutsideApp(pkg, "")) {
                        return@launch
                    }
                    Log.d(TAG, "Grace expired -> stopping auto caffeinate")
                    sendAutoStop()
                }
                return
            }
            Log.d(TAG, "Stopping auto caffeinate (immediate)")
            caffeinatePendingStopJob?.cancel()
            caffeinatePendingStopJob = null
            sendAutoStop()
        } else {
            if (immediate) {
                caffeinatePendingStopJob?.cancel()
                caffeinatePendingStopJob = null
            }
        }
    }

    private fun sendAutoStop() {
        try {
            val intent = Intent(this@FocusFlowAccessibilityService, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_AUTO_STOP
            }
            startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "sendAutoStop failed", e)
        }
    }

    private fun cancelPendingAutoStop() {
        caffeinatePendingStopJob?.cancel()
        caffeinatePendingStopJob = null
    }

    private fun checkCaffeinate(packageName: String, className: String = "") {
        caffeinateDebounceJob?.cancel()

        if (isOutsideApp(packageName, className)) {
            // User is outside apps: home screen, lockscreen, system UI, or Toolz.
            // Grace-stop if AUTO is running so transient windows don't kill it.
            stopAutoCaffeinate()
            return
        }

        // Inside a regular application: any pending grace-stop is now stale.
        cancelPendingAutoStop()
        caffeinateDebounceJob = serviceScope.launch {
            delay(CAFFEINATE_START_DEBOUNCE_MS)

            if (isOutsideApp(packageName, className)) {
                stopAutoCaffeinate()
                return@launch
            }

            val isTargetApp = caffeinateEverything || caffeinateAutoPkgs.contains(packageName)
            Log.d(TAG, "Inside app: $packageName, isTarget=$isTargetApp (everything=$caffeinateEverything, autoPkgs=${caffeinateAutoPkgs.size})")

            if (isTargetApp) {
                // If service is running manually in INFINITE mode, do not downgrade or override
                if (CaffeinateService.isRunning && !CaffeinateService.isAutoRunningFlow.value) {
                    return@launch
                }

                val appLabel = try {
                    val ai = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(ai).toString()
                } catch (e: Exception) {
                    packageName
                }
                val intent = Intent(this@FocusFlowAccessibilityService, CaffeinateService::class.java).apply {
                    action = CaffeinateService.ACTION_AUTO_START
                    putExtra(CaffeinateService.EXTRA_TARGET_APP, appLabel)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                stopAutoCaffeinate()
            }
        }
    }

    private fun getTodayUsage(packageName: String): Long {
        return try {
            var usage = usageRepository.queryPackageUsageToday(packageName)
            // Add current session if it's the package we're checking
            if (packageName == currentPackage && currentPackageResumedTime > 0) {
                val sessionDuration = System.currentTimeMillis() - currentPackageResumedTime
                if (sessionDuration > 0) {
                    usage += sessionDuration
                }
            }
            usage
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage stats", e)
            0L
        }
    }

    private fun showOverlay(packageName: String, isSessionBlock: Boolean = false) {
        // All callers are already on Main (accessibility callbacks + Main-scoped
        // validate). If somehow called off-thread, hop to Main and re-enter —
        // this keeps WindowManager adds single-threaded, so duplicate async
        // adds can never leak a second view that hideOverlay can't remove.
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            serviceScope.launch(Dispatchers.Main) { showOverlay(packageName, isSessionBlock) }
            return
        }
        // Single-flight sync guard — no async gap, so no leaked duplicates.
        if (isOverlayShowing && overlayShowingForPackage == packageName && overlayView?.isAttachedToWindow == true) {
            Log.d(TAG, "showOverlay: Already showing for $packageName")
            return
        }

        Log.d(TAG, "Showing lock screen for: $packageName (Session block: $isSessionBlock)")

        try {
            // Clean up any stale overlay (single view only — no leaks)
            removeOverlayInternal()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // Allow key events
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val inflater = LayoutInflater.from(this@FocusFlowAccessibilityService)
            val view = inflater.inflate(R.layout.layout_focus_lock, null)

            val appName = try {
                val ai = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(ai).toString()
            } catch (e: Exception) {
                packageName
            }
            val message = if (isSessionBlock) {
                "Focus session in progress. $appName is restricted."
            } else {
                "Time's up for $appName."
            }
            view.findViewById<TextView>(R.id.tv_lock_message)?.text = message

            view.isClickable = true
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setOnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    Log.d(TAG, "Overlay: Back button blocked")
                    true // Blocked — use on-screen buttons to exit
                } else {
                    false
                }
            }

            view.findViewById<Button>(R.id.btn_exit)?.setOnClickListener {
                Log.d(TAG, "Overlay: Return-to-Focus-Flow clicked for $packageName")
                onReturnToFocusFlowClicked(packageName)
            }

            view.findViewById<Button>(R.id.btn_dismiss)?.setOnClickListener {
                Log.d(TAG, "Overlay: Exit-to-Home clicked for $packageName")
                onExitToHomeClicked(packageName)
            }

            windowManager?.addView(view, params)
            overlayView = view
            isOverlayShowing = true
            overlayShowingForPackage = packageName
            Log.d(TAG, "Overlay successfully added for $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add window overlay", e)
            // Don't mark showing if add failed — otherwise hideOverlay would
            // no-op and a half-added state could look "stuck".
            overlayView = null
            isOverlayShowing = false
            overlayShowingForPackage = null
        }
    }

    /**
     * Primary escape hatch: hide the blocker and reliably bring Toolz
     * Focus Flow to the front so the user can pause the session / edit limits.
     */
    private fun onReturnToFocusFlowClicked(blockedPackage: String) {
        markDismissed(blockedPackage)
        dismissEpoch++
        val clickEpoch = dismissEpoch
        currentPackage = toolzPackage
        hideOverlay()
        // HOME first: guaranteed exit from the blocked app even if the Toolz
        // launch below is silently denied by background-activity (BAL) rules,
        // which throw no exception — the old code then looked dead/stuck.
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (_: Exception) {}
        fun launchToolz(): Boolean {
            return try {
                val intent = Intent(this@FocusFlowAccessibilityService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.FocusFlow.route)
                }
                startActivity(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Return-to-Focus-Flow startActivity failed", e)
                false
            }
        }
        launchToolz()
        // Retry: first launch often loses the race with HOME; second lands.
        serviceScope.launch {
            delay(700)
            if (clickEpoch != dismissEpoch) return@launch
            launchToolz()
            // Watchdog: if we're somehow still inside the blocked app (BAL
            // denial is silent), yank out to Home rather than strand the user.
            delay(1500)
            if (clickEpoch != dismissEpoch) return@launch
            try {
                val fg = getCombinedForegroundPackage() ?: currentPackage
                if (fg == blockedPackage) {
                    Log.w(TAG, "Return watchdog: still in $blockedPackage, forcing HOME")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Secondary escape hatch (small button): just exit the blocked app to the
     * launcher without opening Toolz. Grace period prevents instant re-lock.
     */
    private fun onExitToHomeClicked(blockedPackage: String) {
        markDismissed(blockedPackage)
        dismissEpoch++
        currentPackage = toolzPackage
        hideOverlay()
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.w(TAG, "Exit-to-Home global action failed", e)
        }
        serviceScope.launch {
            delay(500)
            stopLockedAppInBackground(blockedPackage)
        }
    }

    private fun hideOverlay() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            serviceScope.launch(Dispatchers.Main) { hideOverlay() }
            return
        }
        if (isOverlayShowing) {
            Log.d(TAG, "Hiding overlay for $overlayShowingForPackage")
        }
        // Always attempt removal — guarantees no leaked WindowManager view
        // can leave the user stuck on a stale blocker.
        removeOverlayInternal()
        isOverlayShowing = false
        overlayShowingForPackage = null
        clearBlockEnforcement()
    }

    private fun removeOverlayInternal() {
        overlayView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager?.removeView(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing view from window manager", e)
            }
            overlayView = null
        }
    }

    private fun enforceLockedApp(packageName: String) {
        if (blockedPackagePendingDismissal == packageName) return // Already enforcing
        Log.d(TAG, "Enforcing block for $packageName")
        blockedPackagePendingDismissal = packageName
        muteLockedAppAudio(packageName)

        backgroundStopJob?.cancel()
        backgroundStopJob = serviceScope.launch {
            Log.d(TAG, "Performing Global Action HOME")
            performGlobalAction(GLOBAL_ACTION_HOME)
            delay(500)
            stopLockedAppInBackground(packageName)
        }
    }

    private fun shouldKeepOverlayVisibleOnHome(): Boolean {
        return isOverlayShowing &&
            overlayShowingForPackage != null &&
            blockedPackagePendingDismissal == overlayShowingForPackage
    }

    private fun muteLockedAppAudio(packageName: String) {
        if (mutedPackage == packageName) return

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (previousMusicVolume == null) {
            previousMusicVolume = currentVolume
        }
        if (currentVolume > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }
        mutedPackage = packageName
    }

    private fun restoreAudioAfterBlock() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val volumeToRestore = previousMusicVolume ?: return
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToRestore, 0)
        }
        previousMusicVolume = null
        mutedPackage = null
    }

    private fun stopLockedAppInBackground(packageName: String) {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to request background stop for $packageName", e)
        }
    }

    private fun clearBlockEnforcement() {
        backgroundStopJob?.cancel()
        backgroundStopJob = null
        blockedPackagePendingDismissal = null
        restoreAudioAfterBlock()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove synchronously: hideOverlay() posts via the scope when
        // off-thread, but the scope is cancelled below — a posted hide would
        // never run and leak the window (stuck overlay across restarts).
        try {
            overlayView?.let {
                try {
                    if (it.isAttachedToWindow) windowManager?.removeView(it)
                } catch (_: Exception) {}
                overlayView = null
            }
        } catch (_: Exception) {}
        isOverlayShowing = false
        overlayShowingForPackage = null
        validationJob?.cancel()
        backgroundStopJob?.cancel()
        backgroundStopJob = null
        blockedPackagePendingDismissal = null
        try { restoreAudioAfterBlock() } catch (_: Exception) {}
        caffeinateDebounceJob?.cancel()
        caffeinatePendingStopJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}
