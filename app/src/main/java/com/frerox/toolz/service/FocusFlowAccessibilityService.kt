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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            if (packageName == "com.frerox.toolz" || packageName == "com.android.systemui") {
                hideOverlay()
                currentPackage = null
                return
            }
            
            currentPackage = packageName
            checkAppLimit(packageName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Start a periodic check for the current package
        serviceScope.launch {
            while (isActive) {
                currentPackage?.let { checkAppLimit(it) }
                delay(30000) // Check every 30 seconds
            }
        }
    }

    private fun checkAppLimit(packageName: String) {
        serviceScope.launch {
            val limit = appLimitRepository.getLimitForApp(packageName)
            if (limit != null && limit.isEnabled) {
                val usageTime = getTodayUsage(packageName)
                if (usageTime >= limit.limitMillis) {
                    showOverlay(packageName)
                } else {
                    hideOverlay()
                }
            } else {
                hideOverlay()
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

        val stats = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats.filter { it.packageName == packageName }.sumOf { it.totalTimeInForeground }
    }

    private fun showOverlay(packageName: String) {
        if (isOverlayShowing) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.layout_focus_lock, null)
        
        val pm = packageManager
        val appName = try {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName
        }

        overlayView?.findViewById<TextView>(R.id.tv_lock_message)?.text = 
            "Limit reached for $appName. Time to refocus!"
        
        overlayView?.findViewById<Button>(R.id.btn_exit)?.setOnClickListener {
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(startMain)
            hideOverlay()
        }

        try {
            windowManager?.addView(overlayView, params)
            isOverlayShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideOverlay() {
        if (isOverlayShowing && overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
            isOverlayShowing = false
        }
    }

    override fun onInterrupt() {}
}
