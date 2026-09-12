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

    // Settings Cache
    private var isFocusSessionActive = false
    private var categoryMappings = emptyMap<String, String>()
    private var appLimits = emptyMap<String, Long>()
    private var aiCategoryMappings = emptyMap<String, String>()

    // Caffeinate Cache
    private var caffeinateEverything = false
    private var caffeinateAutoPkgs = emptySet<String>()
    private var caffeinateDebounceJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen off detected -> stopping auto caffeinate immediately")
                    caffeinateDebounceJob?.cancel()
                    stopAutoCaffeinate()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen on detected -> verifying lock screen")
                    if (isLockScreen()) {
                        stopAutoCaffeinate()
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    Log.d(TAG, "User unlocked screen -> rechecking caffeinate")
                    evaluateForegroundAppForCaffeinate()
                }
            }
        }
    }

    companion object {
        private const val TAG = "FocusFlowService"
        private const val DISMISS_GRACE_PERIOD_MS = 3000L
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
                currentPackage?.let { validateAndLock(it) }
            }
        }
        serviceScope.launch {
            settingsRepository.appCategoryMappings.collect { mappings ->
                Log.d(TAG, "Settings: categoryMappings size = ${mappings.size}")
                categoryMappings = mappings
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
        val intent = Intent(this, ClipboardService::class.java).apply {
            action = ClipboardService.ACTION_CHECK_CLIPBOARD
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
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
        validationJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val pkg = getActiveForegroundPackage() ?: currentPackage

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

        if (packageName == lastDismissedPackage && System.currentTimeMillis() - lastDismissedTime < DISMISS_GRACE_PERIOD_MS) {
            return
        }

        serviceScope.launch(Dispatchers.Main) {
            val limitMillis = appLimits[packageName]
            val isSessionActive = isFocusSessionActive

            val isDistraction = categoryMappings[packageName] == "Distraction" ||
                    aiCategoryMappings[packageName] == "DISTRACTION" ||
                    (categoryMappings[packageName] == null && aiCategoryMappings[packageName] == null && isLikelyDistraction(packageName))

            val usageTime = withContext(Dispatchers.IO) { getTodayUsage(packageName) }

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

    private fun isLikelyDistraction(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val keywords = setOf(
            "facebook", "instagram", "tiktok", "youtube", "twitter", "x.android",
            "snapchat", "netflix", "disney", "game", "pubg", "freefire", "reels",
            "shorts", "twitch", "reddit", "pinterest", "tumblr", "spotify",
            "soundcloud", "clash", "candy", "minecraft", "roblox", "brawl",
            "among", "fortnite", "garena", "mlbb", "mobilelegends", "likee",
            "kwai", "vigo", "helo", "moj", "roposo", "josh", "ludo", "carrom",
        )
        return keywords.any { lower.contains(it) }
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
     * Scans all active accessibility windows for known Samsung/OEM lock-screen packages.
     * This catches cases where [KeyguardManager.isKeyguardLocked] incorrectly returns false
     * on OneUI (Galaxy devices), MIUI, ColorOS, etc.
     */
    private fun isOneUiLockScreenVisible(): Boolean {
        try {
            val activeWindows = windows ?: return false
            for (window in activeWindows) {
                val pkg = window.root?.packageName?.toString() ?: continue
                // Anything from the known lock-screen packages is a dead giveaway
                if (pkg == "com.samsung.android.dynamiclock" ||
                    pkg == "com.samsung.android.app.aodservice" ||
                    pkg == "com.samsung.android.lockscreen" ||
                    pkg == "com.samsung.systemui.lockscreen" ||
                    pkg == "com.android.systemui" ||
                    pkg == "com.miui.aod" ||
                    pkg == "com.coloros.lockscreen" ||
                    pkg == "com.oppo.lockscreen") {
                    // Extra: make sure it's not just a quick-settings overlay on top of an app
                    // by checking if this system window has focus or covers the screen
                    if (window.isFocused || window.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                        return true
                    }
                }
                // Also catch any window whose class/title contains lock-screen keywords
                val title = window.title?.toString()?.lowercase() ?: ""
                if (title.contains("keyguard") || title.contains("lockscreen") ||
                    title.contains("bouncer")) return true
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

    private fun stopAutoCaffeinate() {
        if (CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value) {
            Log.d(TAG, "Stopping auto caffeinate")
            val intent = Intent(this@FocusFlowAccessibilityService, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_AUTO_STOP
            }
            startService(intent)
        }
    }

    private fun checkCaffeinate(packageName: String, className: String = "") {
        caffeinateDebounceJob?.cancel()

        if (isOutsideApp(packageName, className)) {
            // User is outside apps: home screen, lockscreen, system UI, or Toolz:
            // Stop immediately if running in auto mode!
            stopAutoCaffeinate()
            return
        }

        // Inside a regular application!
        caffeinateDebounceJob = serviceScope.launch {
            delay(200)

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
        // Only skip if already showing for THIS package AND the view is actually there
        if (isOverlayShowing && overlayShowingForPackage == packageName && overlayView?.isAttachedToWindow == true) {
            Log.d(TAG, "showOverlay: Already showing for $packageName")
            return
        }
        
        Log.d(TAG, "Showing lock screen for: $packageName (Session block: $isSessionBlock)")
        
        // Ensure we're on the main thread for UI
        serviceScope.launch(Dispatchers.Main) {
            try {
                // Clean up any stale overlay
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
                        true // Blocked
                    } else {
                        false
                    }
                }

                view.findViewById<Button>(R.id.btn_exit)?.setOnClickListener {
                    Log.d(TAG, "Overlay: Exit button clicked")
                    lastDismissedTime = System.currentTimeMillis()
                    lastDismissedPackage = packageName
                    currentPackage = toolzPackage
                    hideOverlay()
                    val intent = Intent(this@FocusFlowAccessibilityService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.FocusFlow.route)
                    }
                    startActivity(intent)
                }

                windowManager?.addView(view, params)
                overlayView = view
                isOverlayShowing = true
                overlayShowingForPackage = packageName
                Log.d(TAG, "Overlay successfully added for $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add window overlay", e)
            }
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            Log.d(TAG, "Hiding overlay for $overlayShowingForPackage")
            removeOverlayInternal()
            isOverlayShowing = false
            overlayShowingForPackage = null
        }
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
        hideOverlay()
        validationJob?.cancel()
        backgroundStopJob?.cancel()
        caffeinateDebounceJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}
