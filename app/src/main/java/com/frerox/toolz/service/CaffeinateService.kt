package com.frerox.toolz.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.ui.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.service.quicksettings.TileService
import android.content.ComponentName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class CaffeinateService : Service() {

    private var screenWakeLock: PowerManager.WakeLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reminderJob: Job? = null
    private var notificationJob: Job? = null
    
    private var startTimeMillis: Long = 0
    private var reminderIntervalMinutes: Int = 30
    private var isInfinite: Boolean = false
    private var themeColor: Int = android.graphics.Color.BLUE

    companion object {
        private const val TAG = "CaffeinateService"
        private const val CHANNEL_ID = "caffeinate_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_KEEP_GOING = "ACTION_KEEP_GOING"
        
        const val EXTRA_INTERVAL = "EXTRA_INTERVAL"
        const val EXTRA_INFINITE = "EXTRA_INFINITE"
        const val EXTRA_COLOR = "EXTRA_COLOR"

        private val _elapsedTimeFlow = MutableStateFlow(0L)
        val elapsedTimeFlow = _elapsedTimeFlow.asStateFlow()

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isRunning = true
                reminderIntervalMinutes = intent.getIntExtra(EXTRA_INTERVAL, 30)
                isInfinite = intent.getBooleanExtra(EXTRA_INFINITE, false)
                themeColor = intent.getIntExtra(EXTRA_COLOR, android.graphics.Color.BLUE)
                startCaffeinate()
            }
            ACTION_STOP -> {
                stopCaffeinate()
            }
            ACTION_KEEP_GOING -> resetReminder()
            else -> {
                if (intent == null && isRunning) {
                     startCaffeinate()
                }
            }
        }
        return START_STICKY
    }

    private fun startCaffeinate() {
        acquireWakeLocks()
        showKeepScreenOnOverlay()
        
        if (startTimeMillis == 0L) {
            startTimeMillis = System.currentTimeMillis()
        }

        val notification = createNotification(currentNotificationText())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startReminderTimer()
        updateNotificationLoop()
        requestTileUpdate()
    }

    private fun stopCaffeinate() {
        isRunning = false
        startTimeMillis = 0
        _elapsedTimeFlow.value = 0
        reminderJob?.cancel()
        reminderJob = null
        notificationJob?.cancel()
        notificationJob = null
        releaseWakeLocks()
        removeKeepScreenOnOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        cancelNotifications()
        requestTileUpdate()
        stopSelf()
    }

    private fun acquireWakeLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        if (cpuWakeLock == null) {
            cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Toolz:CaffeinateCpuLock").apply {
                setReferenceCounted(false)
            }
        }
        if (cpuWakeLock?.isHeld == false) {
            cpuWakeLock?.acquire()
        }

        if (screenWakeLock == null) {
            @Suppress("DEPRECATION")
            screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Toolz:CaffeinateScreenLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (screenWakeLock?.isHeld == false) {
            screenWakeLock?.acquire()
        }
    }

    private fun showKeepScreenOnOverlay() {
        if (overlayView != null) return
        
        try {
            val params = WindowManager.LayoutParams(
                1, 1,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                else 
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            
            overlayView = View(this)
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "KeepScreenOn Overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add KeepScreenOn overlay", e)
        }
    }

    private fun removeKeepScreenOnOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
            overlayView = null
        }
    }

    private fun releaseWakeLocks() {
        if (screenWakeLock?.isHeld == true) {
            screenWakeLock?.release()
        }
        if (cpuWakeLock?.isHeld == true) {
            cpuWakeLock?.release()
        }
    }

    private fun createNotification(contentText: String = "Screen will stay awake"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Caffeinate is Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_coffee)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setColor(themeColor)
            .setColorized(true)
            .setContentIntent(createOpenCaffeinatePendingIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", createStopPendingIntent())
            .addAction(android.R.drawable.ic_menu_add, "Keep Going", createKeepGoingPendingIntent())
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotificationLoop() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive && isRunning) {
                val elapsed = System.currentTimeMillis() - startTimeMillis
                _elapsedTimeFlow.value = elapsed
                
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification(currentNotificationText()))
                delay(1000)
            }
        }
    }
    
    private fun startReminderTimer() {
        reminderJob?.cancel()
        reminderJob = null
        if (isInfinite) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID + 1)
            return
        }

        reminderJob = serviceScope.launch {
            delay(TimeUnit.MINUTES.toMillis(reminderIntervalMinutes.toLong()))
            showReminderAlert()
        }
    }

    private fun resetReminder() {
        startReminderTimer()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID + 1)
        
        if (screenWakeLock?.isHeld == false) {
            screenWakeLock?.acquire()
        }
        
        // Ensure overlay is still there
        if (overlayView == null) {
            showKeepScreenOnOverlay()
        }
    }

    private fun showReminderAlert() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Caffeinate Reminder")
            .setContentText("Still using the screen? Open Caffeinate or tap Keep Going to continue.")
            .setSmallIcon(R.drawable.ic_coffee)
            .setColor(themeColor)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(createOpenCaffeinatePendingIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", createStopPendingIntent())
            .addAction(android.R.drawable.ic_menu_add, "Keep Going", createKeepGoingPendingIntent())
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, alertNotification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Caffeinate Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun requestTileUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(this, ComponentName(this, CaffeinateTileService::class.java))
        }
    }

    private fun createStopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, CaffeinateService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createKeepGoingPendingIntent(): PendingIntent {
        val keepGoingIntent = Intent(this, CaffeinateService::class.java).apply {
            action = ACTION_KEEP_GOING
        }
        return PendingIntent.getService(
            this,
            1,
            keepGoingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createOpenCaffeinatePendingIntent(): PendingIntent {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Caffeinate.route)
        }
        return PendingIntent.getActivity(
            this,
            2,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun currentNotificationText(): String {
        val elapsed = System.currentTimeMillis() - startTimeMillis
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
        
        val timeStr = if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
        
        return if (isInfinite) {
            "Active for: $timeStr (Infinite)"
        } else {
            val remaining = TimeUnit.MINUTES.toMillis(reminderIntervalMinutes.toLong()) - elapsed
            if (remaining > 0) {
                val rMin = TimeUnit.MILLISECONDS.toMinutes(remaining)
                val rSec = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                "Active for: $timeStr • Ends in ${rMin}m ${rSec}s"
            } else {
                "Active for: $timeStr • Pending action"
            }
        }
    }

    private fun cancelNotifications() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.cancel(NOTIFICATION_ID + 1)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        reminderJob?.cancel()
        notificationJob?.cancel()
        serviceScope.cancel()
        releaseWakeLocks()
        removeKeepScreenOnOverlay()
        cancelNotifications()
    }
}
