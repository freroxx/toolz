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

import android.app.*
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class CaffeinateService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var screenWakeLock: PowerManager.WakeLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var savedScreenTimeout: Int = -1  // stores original timeout to restore on stop

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationJob: Job? = null
    private var reminderJob: Job? = null

    private var startTimeMillis: Long = 0
    private var currentMode: String = "OFF" // "OFF" | "INFINITE" | "AUTO"
    private var targetAppName: String? = null
    private var notificationsEnabled: Boolean = true

    companion object {
        private const val TAG = "CaffeinateService"
        const val CHANNEL_STATUS_ID = "caffeinate_status"
        const val CHANNEL_ALERTS_ID = "caffeinate_alerts"
        private const val NOTIFICATION_STATUS_ID = 1001
        private const val NOTIFICATION_REMINDER_ID = 1002
        private const val NOTIFICATION_AUTOSTOP_ID = 1003

        const val ACTION_START = "ACTION_START"
        const val ACTION_START_INFINITE = "ACTION_START_INFINITE"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_AUTO_START = "ACTION_AUTO_START"
        const val ACTION_AUTO_STOP = "ACTION_AUTO_STOP"
        const val ACTION_TIMED_STOP = "ACTION_TIMED_STOP"
        const val ACTION_KEEP_GOING = "ACTION_KEEP_GOING"

        const val EXTRA_TARGET_APP = "EXTRA_TARGET_APP"
        const val EXTRA_INFINITE = "EXTRA_INFINITE"
        const val EXTRA_INTERVAL = "EXTRA_INTERVAL"
        const val EXTRA_COLOR = "EXTRA_COLOR"

        private val _elapsedTimeFlow = MutableStateFlow(0L)
        val elapsedTimeFlow = _elapsedTimeFlow.asStateFlow()

        private val _isAutoRunningFlow = MutableStateFlow(false)
        val isAutoRunningFlow = _isAutoRunningFlow.asStateFlow()

        var isRunning = false
            private set
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                if (currentMode == "AUTO" || _isAutoRunningFlow.value) {
                    Log.d(TAG, "Screen turned off in AUTO mode -> auto stopping Caffeinate")
                    stopSession()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register screenReceiver", e)
        }
    }

    private fun ensureForeground(notificationText: String = "Starting Caffeinate…") {
        val initialNotification = createStatusNotification(notificationText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_STATUS_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_STATUS_ID, initialNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START, ACTION_START_INFINITE -> {
                ensureForeground()
                currentMode = "INFINITE"
                _isAutoRunningFlow.value = false
                targetAppName = null
                startSession(isAuto = false)
            }
            ACTION_AUTO_START -> {
                // If user manually started INFINITE, don't downgrade or override mode
                if (isRunning && currentMode == "INFINITE") {
                    // Already running manually in infinite mode, ignore auto start
                } else {
                    targetAppName = intent.getStringExtra(EXTRA_TARGET_APP)
                    if (!isRunning) {
                        ensureForeground()
                        currentMode = "AUTO"
                        _isAutoRunningFlow.value = true
                        startSession(isAuto = true)
                    } else {
                        currentMode = "AUTO"
                        _isAutoRunningFlow.value = true
                        updateStatusNotification()
                    }
                }
            }
            ACTION_STOP -> {
                stopSession()
            }
            ACTION_AUTO_STOP -> {
                if (currentMode == "AUTO" || _isAutoRunningFlow.value || !isRunning) {
                    stopSession()
                }
            }
            ACTION_TIMED_STOP -> {
                serviceScope.launch {
                    val autostopMins = try { settingsRepository.caffeinateAutoStopMins.first() } catch (e: Exception) { 60 }
                    stopSession()
                    if (notificationsEnabled) {
                        postAlertNotification(
                            NOTIFICATION_AUTOSTOP_ID,
                            "Caffeinate Stopped",
                            "Screen awake ended after $autostopMins min."
                        )
                    }
                }
            }
            ACTION_KEEP_GOING -> {
                // Dismiss reminder notification
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_REMINDER_ID)
            }
            else -> {
                if (intent == null && isRunning) {
                    // Resurrected by START_STICKY
                    serviceScope.launch {
                        val savedMode = settingsRepository.caffeinateMode.first()
                        val savedStart = settingsRepository.caffeinateStartTime.first()
                        if (savedMode != "OFF") {
                            currentMode = savedMode
                            startTimeMillis = if (savedStart > 0) savedStart else System.currentTimeMillis()
                            _isAutoRunningFlow.value = (savedMode == "AUTO")
                            acquireWakeLocks()
                            showKeepScreenOnOverlay()
                            startLoops()
                        } else {
                            stopSession()
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startSession(isAuto: Boolean) {
        isRunning = true
        acquireWakeLocks()
        showKeepScreenOnOverlay()
        overrideScreenTimeout()  // prevent dimming

        if (startTimeMillis == 0L) {
            startTimeMillis = System.currentTimeMillis()
        }

        serviceScope.launch {
            notificationsEnabled = try { settingsRepository.caffeinateNotificationsEnabled.first() } catch (e: Exception) { true }
            settingsRepository.setCaffeinateMode(currentMode)
            settingsRepository.setCaffeinateStartTime(startTimeMillis)

            // Setup Auto-stop Alarm if applicable (only in manual infinite mode)
            if (!isAuto) {
                val autostopEnabled = try { settingsRepository.caffeinateAutoStopEnabled.first() } catch (e: Exception) { false }
                if (autostopEnabled) {
                    val autostopMins = try { settingsRepository.caffeinateAutoStopMins.first() } catch (e: Exception) { 60 }
                    scheduleAutoStopAlarm(autostopMins)
                } else {
                    cancelAutoStopAlarm()
                }

                // Setup Reminder loop if applicable
                val reminderEnabled = try { settingsRepository.caffeinateReminderEnabled.first() } catch (e: Exception) { false }
                if (reminderEnabled && notificationsEnabled) {
                    val reminderMins = try { settingsRepository.caffeinateReminderMins.first() } catch (e: Exception) { 30 }
                    startReminderLoop(reminderMins)
                } else {
                    reminderJob?.cancel()
                }
            } else {
                cancelAutoStopAlarm()
                reminderJob?.cancel()
            }

            updateStatusNotification()
            requestTileUpdate()
        }

        startLoops()
    }

    private fun stopSession() {
        isRunning = false
        currentMode = "OFF"
        _isAutoRunningFlow.value = false
        startTimeMillis = 0
        _elapsedTimeFlow.value = 0

        cancelAutoStopAlarm()
        reminderJob?.cancel()
        reminderJob = null
        notificationJob?.cancel()
        notificationJob = null

        releaseWakeLocks()
        removeKeepScreenOnOverlay()
        restoreScreenTimeout()  // undo our dimming-prevention override

        serviceScope.launch {
            try {
                settingsRepository.setCaffeinateMode("OFF")
                settingsRepository.setCaffeinateStartTime(0L)
            } catch (_: Exception) {}
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        requestTileUpdate()
        stopSelf()
    }

    private fun startLoops() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            var lastText: String? = null
            while (isActive && isRunning) {
                val elapsed = System.currentTimeMillis() - startTimeMillis
                _elapsedTimeFlow.value = elapsed

                val text = currentStatusText()
                if (text != lastText) {
                    lastText = text
                    updateStatusNotification()
                }
                delay(1000)
            }
        }
    }

    private fun startReminderLoop(intervalMins: Int) {
        reminderJob?.cancel()
        reminderJob = serviceScope.launch {
            val intervalMillis = intervalMins * 60_000L
            while (isActive && isRunning) {
                delay(intervalMillis)
                if (isActive && isRunning && notificationsEnabled) {
                    val elapsed = System.currentTimeMillis() - startTimeMillis
                    val elapsedMins = TimeUnit.MILLISECONDS.toMinutes(elapsed)
                    postReminderNotification(elapsedMins)
                }
            }
        }
    }

    private fun scheduleAutoStopAlarm(minutes: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = startTimeMillis + (minutes * 60_000L)
        val intent = Intent(this, CaffeinateService::class.java).apply {
            action = ACTION_TIMED_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "Scheduled auto-stop alarm in $minutes minutes")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule exact auto-stop alarm", e)
        }
    }

    private fun cancelAutoStopAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, CaffeinateService::class.java).apply {
            action = ACTION_TIMED_STOP
        }
        val pendingIntent = PendingIntent.getService(
            this,
            9001,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
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
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                "Toolz:CaffeinateScreenLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (screenWakeLock?.isHeld == false) {
            screenWakeLock?.acquire()
        }
    }

    private fun releaseWakeLocks() {
        if (screenWakeLock?.isHeld == true) {
            try { screenWakeLock?.release() } catch (_: Exception) {}
        }
        if (cpuWakeLock?.isHeld == true) {
            try { cpuWakeLock?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Prevents screen dimming by overriding SCREEN_OFF_TIMEOUT to Int.MAX_VALUE.
     * Requires WRITE_SETTINGS permission (user grants via Settings > Apps > Special access).
     * Falls back silently if not granted — wakelock still keeps screen on, just may dim.
     */
    private fun overrideScreenTimeout() {
        try {
            if (Settings.System.canWrite(this)) {
                val current = Settings.System.getInt(
                    contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000
                )
                if (current != Int.MAX_VALUE) {
                    savedScreenTimeout = current
                    Settings.System.putInt(
                        contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE
                    )
                    Log.d(TAG, "Screen timeout overridden ($current ms → MAX) to prevent dimming")
                }
            } else {
                Log.d(TAG, "WRITE_SETTINGS not granted — screen may still dim (wakelock is backup)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to override screen timeout", e)
        }
    }

    /**
     * Restores the screen timeout that was saved before [overrideScreenTimeout] was called.
     */
    private fun restoreScreenTimeout() {
        try {
            if (savedScreenTimeout > 0 && Settings.System.canWrite(this)) {
                Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, savedScreenTimeout
                )
                Log.d(TAG, "Screen timeout restored to $savedScreenTimeout ms")
                savedScreenTimeout = -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore screen timeout", e)
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
            val view = View(this)
            windowManager?.addView(view, params)
            overlayView = view
            Log.d(TAG, "KeepScreenOn overlay added")
        } catch (e: Exception) {
            Log.w(TAG, "KeepScreenOn overlay failed (system alert window not granted, falling back to wakelock)", e)
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

    private fun currentStatusText(): String {
        if (!notificationsEnabled) {
            return if (currentMode == "AUTO") "Auto-Caffeinate active" else "Caffeinate active"
        }

        val elapsed = System.currentTimeMillis() - startTimeMillis
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60

        val timeStr = if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d h", hours, minutes)
        } else {
            String.format(Locale.getDefault(), "%02d min", minutes)
        }

        return if (currentMode == "AUTO") {
            if (!targetAppName.isNullOrBlank()) {
                "Active for $timeStr • $targetAppName"
            } else {
                "Active for $timeStr"
            }
        } else {
            "Active for $timeStr"
        }
    }

    private fun createStatusNotification(text: String): Notification {
        val title = if (currentMode == "AUTO") "Auto-Caffeinate Active" else "Caffeinate is Active"
        return NotificationCompat.Builder(this, CHANNEL_STATUS_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notif_caffeinate)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(createOpenCaffeinatePendingIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", createStopPendingIntent())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateStatusNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_STATUS_ID, createStatusNotification(currentStatusText()))
    }

    private fun postReminderNotification(minutesElapsed: Long) {
        val keepGoingIntent = Intent(this, CaffeinateService::class.java).apply {
            action = ACTION_KEEP_GOING
        }
        val keepGoingPendingIntent = PendingIntent.getService(
            this, 10, keepGoingIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setContentTitle("Caffeinate Reminder")
            .setContentText("Hey, you've been using Caffeinate for $minutesElapsed min already.")
            .setSmallIcon(R.drawable.ic_notif_caffeinate)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createOpenCaffeinatePendingIntent())
            .addAction(0, "Keep going", keepGoingPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", createStopPendingIntent())
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_REMINDER_ID, notification)
    }

    private fun postAlertNotification(id: Int, title: String, message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notif_caffeinate)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createOpenCaffeinatePendingIntent())
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                "Caffeinate Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing status while Caffeinate is active"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Caffeinate Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders and auto-stop notifications"
            }

            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(alertsChannel)
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
        reminderJob?.cancel()
        notificationJob?.cancel()
        serviceScope.cancel()
        releaseWakeLocks()
        removeKeepScreenOnOverlay()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }
}
