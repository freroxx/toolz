package com.frerox.toolz.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
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

    // Settings Cache
    private var isFocusSessionActive = false
    private var categoryMappings = emptyMap<String, String>()
    private var appLimits = emptyMap<String, Long>()

    companion object {
        private const val TAG = "FocusFlowService"
        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui", 
            "android", 
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.google.android.gms",
            "com.android.settings"
        )
    }

    private val toolzPackage: String get() = packageName

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshHomePackage()
        startPeriodicValidation()
        observeSettings()
    }

    private fun observeSettings() {
        serviceScope.launch {
            settingsRepository.focusFlowSessionActive.collect { active ->
                isFocusSessionActive = active
                currentPackage?.let { validateAndLock(it) }
            }
        }
        serviceScope.launch {
            settingsRepository.appCategoryMappings.collect { mappings ->
                categoryMappings = mappings
                currentPackage?.let { validateAndLock(it) }
            }
        }
        serviceScope.launch {
            appLimitRepository.allLimits.collect { limits ->
                appLimits = limits.associate { it.packageName to it.limitMillis }
                currentPackage?.let { validateAndLock(it) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // Handle package change for precise tracking
        if (packageName != currentPackage) {
            currentPackage = packageName
            currentPackageResumedTime = System.currentTimeMillis()
        }

        // Ignore events from the overlay itself or Toolz UI
        if (packageName == toolzPackage && !className.contains("Activity") && !className.contains("MainActivity")) {
            return
        }

        Log.d(TAG, "onAccessibilityEvent: $packageName / $className")

        // Handle "Safe" contexts
        if (packageName == getHomePackage() || (packageName == toolzPackage && (className.contains("Activity") || className.contains("MainActivity")))) {
            if (packageName == getHomePackage() && shouldKeepOverlayVisibleOnHome()) {
                return
            }
            hideOverlay()
            return
        }

        if (SYSTEM_UI_PACKAGES.contains(packageName)) {
            return
        }

        validateAndLock(packageName)
        checkCaffeinate(packageName)
        
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

    private fun refreshHomePackage() {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val res = packageManager.resolveActivity(intent, 0)
        cachedHomePackage = res?.activityInfo?.packageName
    }

    private fun getHomePackage(): String? {
        if (cachedHomePackage == null) refreshHomePackage()
        return cachedHomePackage
    }

    private fun startPeriodicValidation() {
        validationJob?.cancel()
        validationJob = serviceScope.launch {
            while (isActive) {
                delay(1500)
                val pkg = currentPackage
                if (pkg != null && pkg != getHomePackage() && pkg != toolzPackage && !SYSTEM_UI_PACKAGES.contains(pkg)) {
                    validateAndLock(pkg)
                }
            }
        }
    }

    private fun validateAndLock(packageName: String) {
        if (packageName == toolzPackage) return // Security: Toolz is immune

        val limitMillis = appLimits[packageName]
        val isSessionActive = isFocusSessionActive
        
        val isDistraction = categoryMappings[packageName] == "Distraction" || 
                            (categoryMappings[packageName] == null && isLikelyDistraction(packageName))

        val shouldLock = if (isSessionActive && isDistraction) {
            true // Global block during focus session
        } else if (limitMillis != null && limitMillis > 0) {
            val usageTime = getTodayUsage(packageName)
            usageTime >= limitMillis
        } else {
            false
        }

        if (shouldLock) {
            showOverlay(packageName, isSessionActive && isDistraction)
            enforceLockedApp(packageName)
        } else if (overlayShowingForPackage == packageName) {
            hideOverlay()
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

    private fun checkCaffeinate(packageName: String) {
        serviceScope.launch {
            val autoAllApps = settingsRepository.caffeinateAutoAllApps.first()
            val autoEnabledPackages = withContext(Dispatchers.IO) {
                caffeinateRepository.getAutoEnabledPackages()
            }
            
            val isTargetApp = autoAllApps || autoEnabledPackages.contains(packageName)
            
            if (isTargetApp) {
                // Should be running
                if (!CaffeinateService.isRunning) {
                    val intent = Intent(this@FocusFlowAccessibilityService, CaffeinateService::class.java).apply {
                        action = CaffeinateService.ACTION_AUTO_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            } else {
                // Should not be running (if it was started automatically)
                if (CaffeinateService.isRunning) {
                    val intent = Intent(this@FocusFlowAccessibilityService, CaffeinateService::class.java).apply {
                        action = CaffeinateService.ACTION_AUTO_STOP
                    }
                    startService(intent)
                }
            }
        }
    }

    private fun getTodayUsage(packageName: String): Long {
        val statsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        return try {
            // Use queryUsageStats for potentially more recent data than aggregated if available
            val stats = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            var usage = stats.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
            
            // Critical Fix: Add time for the current ongoing session if it's the package we're checking
            if (packageName == currentPackage && currentPackageResumedTime > 0) {
                val sessionDuration = endTime - currentPackageResumedTime
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
        if (isOverlayShowing && overlayShowingForPackage == packageName) return
        
        Log.d(TAG, "Showing lock screen for: $packageName (Session block: $isSessionBlock)")

        if (isOverlayShowing) {
            removeOverlayInternal()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val inflater = LayoutInflater.from(this)
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
                true // Blocked
            } else {
                false
            }
        }

        view.findViewById<Button>(R.id.btn_exit)?.setOnClickListener {
            hideOverlay()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Dashboard.route)
            }
            startActivity(intent)
        }

        try {
            windowManager?.addView(view, params)
            overlayView = view
            isOverlayShowing = true
            overlayShowingForPackage = packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add window overlay", e)
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing) {
            Log.d(TAG, "Hiding overlay")
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
        blockedPackagePendingDismissal = packageName
        muteLockedAppAudio(packageName)

        backgroundStopJob?.cancel()
        backgroundStopJob = serviceScope.launch {
            performGlobalAction(GLOBAL_ACTION_HOME)
            delay(250)
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
        serviceScope.cancel()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}
