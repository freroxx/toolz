package com.frerox.toolz.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        
        // 1. Core Logic: If moving to Home or Toolz, kill the lock immediately.
        if (packageName == "com.frerox.toolz" || isHomeApp(packageName)) {
            if (isOverlayShowing) hideOverlay()
            currentPackage = packageName
            return
        }

        // 2. Flicker Mitigation: 
        // If the overlay is already blocking this specific package, ignore sub-window events 
        // (keyboards, system UI transients, volume sliders) while we are in the locked app.
        if (isOverlayShowing && (packageName == overlayShowingForPackage || 
            packageName == "com.android.systemui" || 
            packageName == "android" || 
            packageName == "com.google.android.inputmethod.latin")) {
            return
        }

        // 3. Update tracking state if package changed
        if (currentPackage != packageName) {
            currentPackage = packageName
            checkAppLimit(packageName)
        }
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
                currentPackage?.let { pkg ->
                    // Persistence Heartbeat: Ensure distraction apps stay locked even if events are missed
                    if (pkg != "com.frerox.toolz" && !isHomeApp(pkg)) {
                        validateAndLock(pkg)
                    }
                }
                delay(1000) 
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
                    if (!isOverlayShowing || overlayShowingForPackage != packageName) {
                        showOverlay(packageName)
                    }
                } else {
                    // Usage is below limit (e.g. user increased limit or reset stats)
                    if (overlayShowingForPackage == packageName) hideOverlay()
                }
            } else {
                // App is no longer limited
                if (overlayShowingForPackage == packageName) hideOverlay()
            }
        }
    }

    private fun checkAppLimit(packageName: String) {
        validateAndLock(packageName)
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

        val stats = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        return stats?.filter { it.packageName == packageName }?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    private fun showOverlay(packageName: String) {
        if (isOverlayShowing && overlayShowingForPackage == packageName) return

        serviceScope.launch {
            // Precise cleanup to avoid view leaks or double layering
            if (isOverlayShowing) hideOverlay()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            )

            val inflater = LayoutInflater.from(this@FocusFlowAccessibilityService)
            val view = inflater.inflate(R.layout.layout_focus_lock, null)
            
            val pm = packageManager
            val appName = try {
                val ai = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (e: Exception) {
                packageName
            }

            view.findViewById<TextView>(R.id.tv_lock_message)?.text = 
                "Focus flow active for $appName. Toolz has secured your productivity session."
            
            view.findViewById<Button>(R.id.btn_exit)?.setOnClickListener {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                // Home detection in onAccessibilityEvent will handle the hideOverlay()
            }

            try {
                windowManager?.addView(view, params)
                overlayView = view
                isOverlayShowing = true
                overlayShowingForPackage = packageName
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                // System might have already detached the view
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
