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
    private var lastEventTime = 0L
    private var lastValidPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        val currentTime = System.currentTimeMillis()
        
        // Ignore system UI transients and keyboard while we are in a potentially locked state
        val systemPackages = listOf(
            "com.android.systemui", 
            "android", 
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.google.android.gms"
        )
        
        if (systemPackages.contains(packageName)) {
            return
        }

        // Core Logic: If moving to Home or Toolz, kill the lock immediately.
        if (packageName == "com.frerox.toolz" || isHomeApp(packageName)) {
            if (isOverlayShowing) hideOverlay()
            currentPackage = packageName
            lastValidPackage = packageName
            return
        }

        // Debounce events to prevent flickering during rapid window transitions
        if (packageName == currentPackage && currentTime - lastEventTime < 300) return
        lastEventTime = currentTime

        if (currentPackage != packageName) {
            currentPackage = packageName
            lastValidPackage = packageName
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
                lastValidPackage?.let { pkg ->
                    if (pkg != "com.frerox.toolz" && !isHomeApp(pkg)) {
                        validateAndLock(pkg)
                    } else if (isOverlayShowing) {
                        hideOverlay()
                    }
                }
                delay(1500) 
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
                        withContext(Dispatchers.Main) {
                            showOverlay(packageName)
                        }
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

        val stats = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats?.filter { it.packageName == packageName }?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    private fun showOverlay(packageName: String) {
        // Double check we are still on the same package and it's not a safe one
        if (currentPackage != packageName || packageName == "com.frerox.toolz" || isHomeApp(packageName)) return
        
        if (isOverlayShowing && overlayShowingForPackage == packageName) return

        if (isOverlayShowing) hideOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )

        val inflater = LayoutInflater.from(this)
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
            hideOverlay()
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

    private fun hideOverlay() {
        if (isOverlayShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                // Ignore if view already gone
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
