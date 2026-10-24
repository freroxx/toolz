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
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Standalone accessibility service for Caffeinate's Auto mode.
 *
 * Kept separate from [FocusFlowAccessibilityService] so users who only want
 * Caffeinate can grant permission for just this service instead of enabling
 * the app-locking service. It only watches which package is in the foreground
 * and starts/stops Auto Caffeinate accordingly — it never reads screen content.
 */
@AndroidEntryPoint
class CaffeinateAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var caffeinateEverything = false
    private var caffeinateAutoPkgs = emptySet<String>()
    private var caffeinateDebounceJob: Job? = null
    private var caffeinatePendingStopJob: Job? = null

    private var homePackages = setOf<String>()
    private val launchableCache = mutableMapOf<String, Boolean>()

    private val toolzPackage: String get() = packageName

    companion object {
        private const val TAG = "CaffeinateA11y"
        private const val CAFFEINATE_EXIT_GRACE_MS = 2500L
        private const val CAFFEINATE_START_DEBOUNCE_MS = 300L
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
            "com.samsung.android.app.aodservice",
            "com.samsung.android.dynamiclock",
            "com.samsung.android.homemode",
            "com.samsung.android.lockscreen",
            "com.samsung.systemui.lockscreen",
            "com.samsung.android.app.cocktailbarservice",
            "com.samsung.android.biometrics.app.setting",
            "com.samsung.android.brightnesscontroller",
            "com.samsung.android.app.smartcapture",
            "com.samsung.android.Bixby",
            "com.samsung.android.bixby.agent",
            "com.samsung.android.bixby.wakeup",
            "com.samsung.android.app.routines",
            "com.miui.aod",
            "com.oplus.aod",
            "com.coloros.lockscreen",
            "com.oppo.lockscreen"
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
                    if (isLockScreen()) stopAutoCaffeinate()
                }
                Intent.ACTION_USER_PRESENT -> {
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CaffeinateAccessibilityService created")
        refreshHomePackage()
        observeSettings()

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register screenReceiver", e)
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.caffeinateEverything.collect { everything ->
                caffeinateEverything = everything
                evaluateForegroundAppForCaffeinate()
            }
        }
        serviceScope.launch {
            settingsRepository.caffeinateAutoPkgs.collect { pkgs ->
                caffeinateAutoPkgs = pkgs
                evaluateForegroundAppForCaffeinate()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                val className = event.className?.toString() ?: ""
                checkCaffeinate(packageName, className)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Gesture nav, recents and the notification shade can emit only
                // this event, so re-evaluate the live foreground window.
                if (CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value) {
                    evaluateForegroundAppForCaffeinate()
                }
            }
        }
    }

    private fun refreshHomePackage() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0
            val resolveInfos = packageManager.queryIntentActivities(intent, flags)
            val pkgs = resolveInfos.mapNotNull { it.activityInfo?.packageName }.toMutableSet()
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let {
                pkgs.add(it)
            }
            homePackages = pkgs
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve home packages", e)
        }
    }

    private fun isHomePackage(packageName: String): Boolean {
        if (homePackages.isEmpty()) refreshHomePackage()
        return homePackages.contains(packageName)
    }

    private fun isLaunchableApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return launchableCache.getOrPut(packageName) {
            try {
                packageManager.getLaunchIntentForPackage(packageName) != null
            } catch (_: Exception) {
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
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true ||
            keyguardManager?.isDeviceLocked == true ||
            keyguardManager?.inKeyguardRestrictedInputMode() == true
        ) return true
        return isOneUiLockScreenVisible()
    }

    /**
     * Strict lock-screen detection: a bare `com.android.systemui` TYPE_SYSTEM
     * window exists permanently (status/nav bars), so it must never count as
     * locked — only focused keyguard/bouncer windows or dedicated AOD windows do.
     */
    private fun isOneUiLockScreenVisible(): Boolean {
        val activeWindows = try { windows } catch (e: Exception) {
            Log.w(TAG, "isOneUiLockScreenVisible error", e)
            return false
        } ?: return false
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
                    if (window.isFocused || window.type == AccessibilityWindowInfo.TYPE_SYSTEM || isLockTitle) {
                        return true
                    }
                    continue
                }
                if (pkg == "com.android.systemui" || pkg == "android") {
                    if (window.isFocused && isLockTitle) return true
                    continue
                }
                if (window.isFocused && isLockTitle) return true
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
        if (SYSTEM_UI_PACKAGES.contains(packageName)) return true
        if (packageName == "android") return true
        val lower = packageName.lowercase()
        if (lower.contains("inputmethod") || lower.contains("keyboard") || lower.contains("swiftkey")) return true
        return packageName.startsWith("com.android.systemui") ||
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
                val appWindow = windows?.firstOrNull {
                    it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
                }
                val pkg = appWindow?.root?.packageName?.toString()
                if (!pkg.isNullOrBlank()) return pkg
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get active foreground package", e)
        }
        return null
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
                caffeinatePendingStopJob = serviceScope.launch {
                    delay(CAFFEINATE_EXIT_GRACE_MS)
                    if (!isActive) return@launch
                    if (!(CaffeinateService.isRunning && CaffeinateService.isAutoRunningFlow.value)) return@launch
                    val pkg = try { getActiveForegroundPackage() } catch (_: Exception) { null }
                    if (pkg != null && !isOutsideApp(pkg, "")) {
                        val stillTarget = caffeinateEverything || caffeinateAutoPkgs.contains(pkg)
                        if (stillTarget) return@launch
                    }
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
            val intent = Intent(this@CaffeinateAccessibilityService, CaffeinateService::class.java).apply {
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
            stopAutoCaffeinate()
            return
        }

        cancelPendingAutoStop()
        caffeinateDebounceJob = serviceScope.launch {
            delay(CAFFEINATE_START_DEBOUNCE_MS)

            if (isOutsideApp(packageName, className)) {
                stopAutoCaffeinate()
                return@launch
            }

            val isTargetApp = caffeinateEverything || caffeinateAutoPkgs.contains(packageName)
            if (isTargetApp) {
                // Never downgrade a manual INFINITE session into AUTO.
                if (CaffeinateService.isRunning && !CaffeinateService.isAutoRunningFlow.value) {
                    return@launch
                }

                val appLabel = try {
                    val ai = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(ai).toString()
                } catch (_: Exception) {
                    packageName
                }
                val intent = Intent(this@CaffeinateAccessibilityService, CaffeinateService::class.java).apply {
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

    override fun onDestroy() {
        caffeinateDebounceJob?.cancel()
        caffeinatePendingStopJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Caffeinate accessibility service interrupted")
    }
}
