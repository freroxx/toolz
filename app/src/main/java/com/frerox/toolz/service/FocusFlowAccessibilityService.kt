package com.frerox.toolz.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import com.frerox.toolz.R
import com.frerox.toolz.data.focus.AppLimitRepository
import com.frerox.toolz.data.focus.CaffeinateRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject

/**
 * FocusFlowAccessibilityService provides app locking functionality by monitoring
 * window state changes and usage stats. It overlays a lock screen when an app's
 * daily limit is reached.
 *
 * It also handles 'Caffeinate' auto-enable logic when a designated app is launched.
 */
@AndroidEntryPoint
class FocusFlowAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appLimitRepository: AppLimitRepository
    
    @Inject
    lateinit var caffeinateRepository: CaffeinateRepository

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var overlayShowingForPackage: String? = null
    private var currentPackage: String? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var validationJob: Job? = null
    private var cachedHomePackage: String? = null

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
        private const val TOOLZ_PACKAGE = "com.frerox.toolz"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshHomePackage()
        startPeriodicValidation()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // 1. Stability: Ignore events from the overlay itself to prevent flicker loops.
        if (packageName == TOOLZ_PACKAGE && !className.contains("Activity") && !className.contains("MainActivity")) {
            return
        }

        Log.d(TAG, "onAccessibilityEvent: $packageName / $className")

        // 2. Handle "Safe" contexts where the lock must be hidden immediately.
        if (packageName == getHomePackage() || (packageName == TOOLZ_PACKAGE && (className.contains("Activity") || className.contains("MainActivity")))) {
            hideOverlay()
            currentPackage = packageName
            return
        }

        // 3. Handle System UI (Notifications, Recents, Dialogs).
        if (SYSTEM_UI_PACKAGES.contains(packageName)) {
            return
        }

        // 4. It's a standard app. Update currentPackage and run validation.
        currentPackage = packageName
        validateAndLock(packageName)
        checkCaffeinate(packageName)
        triggerClipboardCheck()
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
                delay(1500) // Validate every 1.5 seconds for responsiveness
                val pkg = currentPackage
                if (pkg != null && pkg != getHomePackage() && pkg != TOOLZ_PACKAGE && !SYSTEM_UI_PACKAGES.contains(pkg)) {
                    validateAndLock(pkg)
                }
            }
        }
    }

    private fun validateAndLock(packageName: String) {
        serviceScope.launch {
            val limit = withContext(Dispatchers.IO) {
                appLimitRepository.getLimitForApp(packageName)
            }
            
            if (currentPackage != packageName) return@launch

            val shouldLock = if (limit != null && limit.isEnabled) {
                val usageTime = withContext(Dispatchers.IO) { getTodayUsage(packageName) }
                usageTime >= limit.limitMillis
            } else {
                false
            }

            withContext(Dispatchers.Main) {
                if (shouldLock) {
                    showOverlay(packageName)
                } else if (overlayShowingForPackage == packageName) {
                    hideOverlay()
                }
            }
        }
    }

    private fun checkCaffeinate(packageName: String) {
        serviceScope.launch {
            val autoEnabledPackages = withContext(Dispatchers.IO) {
                caffeinateRepository.getAutoEnabledPackages()
            }
            if (autoEnabledPackages.contains(packageName)) {
                val intent = Intent(this@FocusFlowAccessibilityService, CaffeinateService::class.java).apply {
                    action = CaffeinateService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
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
            val stats = statsManager.queryAndAggregateUsageStats(startTime, endTime)
            stats[packageName]?.totalTimeInForeground ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error querying usage stats", e)
            0L
        }
    }

    private fun showOverlay(packageName: String) {
        if (isOverlayShowing && overlayShowingForPackage == packageName) return
        
        Log.d(TAG, "Showing lock screen for: $packageName")

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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
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
        view.findViewById<TextView>(R.id.tv_lock_message)?.text = "Time's up for $appName."
        
        view.isClickable = true
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                Log.d(TAG, "Back button intercepted")
                true // Blocked
            } else {
                false
            }
        }

        view.findViewById<Button>(R.id.btn_exit)?.setOnClickListener {
            Log.d(TAG, "User choosing to exit locked app")
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        validationJob?.cancel()
        serviceScope.cancel()
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}
