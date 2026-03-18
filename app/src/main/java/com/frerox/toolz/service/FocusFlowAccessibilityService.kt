package com.frerox.toolz.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.frerox.toolz.R
import com.frerox.toolz.data.focus.AppLimitRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class FocusFlowAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appLimitRepository: AppLimitRepository

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var currentPackage: String? = null
    private var overlayShowingForPackage: String? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var validationJob: Job? = null
    private var lastEventTime = 0L
    private var lastValidPackage: String? = null

    companion object {
        private const val TAG = "FocusFlowService"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        val currentTime = System.currentTimeMillis()
        
        // System packages that shouldn't trigger locking
        val systemPackages = listOf(
            "com.android.systemui", 
            "android", 
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.google.android.gms",
            "com.android.settings"
        )
        
        if (systemPackages.contains(packageName)) return

        // If user goes home or to our app, hide overlay immediately
        if (packageName == "com.frerox.toolz" || isHomeApp(packageName)) {
            if (isOverlayShowing) hideOverlay()
            currentPackage = packageName
            lastValidPackage = packageName
            return
        }

        // Debounce to avoid flickering
        if (packageName == currentPackage && currentTime - lastEventTime < 200) return
        lastEventTime = currentTime

        currentPackage = packageName
        lastValidPackage = packageName
        validateAndLock(packageName)
    }

    private fun isHomeApp(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val res = packageManager.resolveActivity(intent, 0)
        return res?.activityInfo?.packageName == packageName
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startPeriodicValidation()
    }

    private fun startPeriodicValidation() {
        validationJob?.cancel()
        validationJob = serviceScope.launch {
            while (isActive) {
                lastValidPackage?.let { pkg ->
                    if (pkg != "com.frerox.toolz" && !isHomeApp(pkg)) {
                        validateAndLock(pkg)
                    }
                }
                delay(2000) 
            }
        }
    }

    private fun validateAndLock(packageName: String) {
        serviceScope.launch {
            val limit = withContext(Dispatchers.IO) {
                appLimitRepository.getLimitForApp(packageName)
            }
            
            if (limit != null && limit.isEnabled) {
                val usageTime = withContext(Dispatchers.IO) { getTodayUsage(packageName) }
                if (usageTime >= limit.limitMillis) {
                    withContext(Dispatchers.Main) {
                        showOverlay(packageName)
                    }
                } else if (overlayShowingForPackage == packageName) {
                    withContext(Dispatchers.Main) {
                        hideOverlay()
                    }
                }
            } else if (overlayShowingForPackage == packageName) {
                withContext(Dispatchers.Main) {
                    hideOverlay()
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

        // Using aggregate usage stats for better precision
        val stats = statsManager.queryAndAggregateUsageStats(startTime, endTime)
        return stats[packageName]?.totalTimeInForeground ?: 0L
    }

    private fun showOverlay(packageName: String) {
        if (isOverlayShowing && overlayShowingForPackage == packageName) return
        if (isOverlayShowing) hideOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
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
        
        view.findViewById<Button>(R.id.btn_exit)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            hideOverlay()
        }

        try {
            windowManager?.addView(view, params)
            overlayView = view
            isOverlayShowing = true
            overlayShowingForPackage = packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            overlayView = null
            isOverlayShowing = false
            overlayShowingForPackage = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        validationJob?.cancel()
        serviceScope.cancel()
    }

    override fun onInterrupt() {}
}
