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
    // Independent scope for critical DataStore persistence. Never cancelled by
    // serviceScope.cancel() in onDestroy, so STOP persistence always lands even
    // when stopSelf() tears the service down mid-write. This was the root cause
    // of "can't turn off": mode stayed INFINITE in DataStore after stop.
    private val prefsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var notificationJob: Job? = null
    private var reminderJob: Job? = null
    private var watchdogJob: Job? = null
    private var configJob: Job? = null
    private var pendingAutoStopJob: Job? = null

    private var startTimeMillis: Long = 0
    private var currentMode: String = "OFF" // "OFF" | "INFINITE" | "AUTO"
    private var notificationsEnabled: Boolean = true
    private var backgroundNotificationsEnabled: Boolean = true

    companion object {
        private const val TAG = "CaffeinateService"
        private const val AUTO_EXIT_GRACE_MS = 2500L
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
        const val ACTION_UPDATE_CONFIG = "ACTION_UPDATE_CONFIG"

        const val EXTRA_TARGET_APP = "EXTRA_TARGET_APP"
        const val EXTRA_INFINITE = "EXTRA_INFINITE"
        const val EXTRA_INTERVAL = "EXTRA_INTERVAL"
        const val EXTRA_COLOR = "EXTRA_COLOR"

        private val _elapsedTimeFlow = MutableStateFlow(0L)
        val elapsedTimeFlow = _elapsedTimeFlow.asStateFlow()

        private val _isAutoRunningFlow = MutableStateFlow(false)
        val isAutoRunningFlow = _isAutoRunningFlow.asStateFlow()

        // Reactive running state so UI / tiles can collect instead of polling the
        // static var (which drifts when the service is toggled from tile or auto).
        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow = _isRunningFlow.asStateFlow()

        var isRunning = false
            private set

        /** Thread-safe snapshot for TileService / AccessibilityService fast paths. */
        fun setRunningFlag(running: Boolean) {
            isRunning = running
            _isRunningFlow.value = running
        }
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

    private fun ensureForeground() {
        // Background toggle OFF → satisfy FGS requirement, then hide "Caffeinate is active".
        if (!backgroundNotificationsEnabled) {
            try {
                val placeholder = createStatusNotification(currentStatusText())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_STATUS_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_STATUS_ID, placeholder)
                }
            } catch (_: Exception) {}
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            try {
                (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_STATUS_ID)
            } catch (_: Exception) {}
            return
        }
        val initialNotification = createStatusNotification(currentStatusText())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_STATUS_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_STATUS_ID, initialNotification)
            }
        } catch (e: Exception) {
            // Android 12+ can throw ForegroundServiceStartNotAllowedException when the
            // start came from background (boot/tile). Log and stop cleanly instead of crashing.
            Log.e(TAG, "startForeground failed — stopping session", e)
            try { stopSelf() } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START, ACTION_START_INFINITE -> {
                // Idempotent: repeated taps must not reset the timer or leak jobs.
                if (isRunning && currentMode == "INFINITE") {
                    updateStatusNotificationSafe()
                    requestTileUpdate()
                    return START_STICKY
                }
                pendingAutoStopJob?.cancel()
                pendingAutoStopJob = null
                // If auto was running, promote to manual infinite (keep original start time).
                try {
                    ensureForeground()
                } catch (_: Exception) { return START_STICKY }
                currentMode = "INFINITE"
                _isAutoRunningFlow.value = false
                startSession(isAuto = false)
            }
            ACTION_AUTO_START -> {
                // Any real foreground report cancels a pending grace-stop.
                pendingAutoStopJob?.cancel()
                pendingAutoStopJob = null
                // If user manually started INFINITE, don't downgrade or override mode
                if (isRunning && currentMode == "INFINITE") {
                    // Already running manually in infinite mode, ignore auto start
                } else {
                    if (!isRunning) {
                        try {
                            ensureForeground()
                        } catch (_: Exception) { return START_STICKY }
                        currentMode = "AUTO"
                        _isAutoRunningFlow.value = true
                        startSession(isAuto = true)
                    } else {
                        currentMode = "AUTO"
                        _isAutoRunningFlow.value = true
                        updateStatusNotificationSafe()
                    }
                }
            }
            ACTION_STOP -> {
                pendingAutoStopJob?.cancel()
                pendingAutoStopJob = null
                stopSession()
            }
            ACTION_AUTO_STOP -> {
                if (currentMode == "AUTO" || _isAutoRunningFlow.value || !isRunning) {
                    // Grace: transient exits (shade, recents, keyboard) cancel via
                    // a follow-up AUTO_START. Screen-off already stops immediately
                    // on the accessibility side + here via screenReceiver; this
                    // grace only smooths app-to-app transitions.
                    if (pendingAutoStopJob?.isActive == true) return START_STICKY
                    Log.d(TAG, "AUTO_STOP received -> grace $AUTO_EXIT_GRACE_MS ms before stopping")
                    pendingAutoStopJob = serviceScope.launch {
                        delay(AUTO_EXIT_GRACE_MS)
                        if (!isActive) return@launch
                        if (currentMode != "AUTO" && !_isAutoRunningFlow.value) return@launch
                        Log.d(TAG, "AUTO grace expired -> stopping")
                        stopSession()
                    }
                }
            }
            ACTION_TIMED_STOP -> {
                serviceScope.launch {
                    val autostopMins = try { settingsRepository.caffeinateAutoStopMins.first() } catch (_: Exception) { 60 }
                    // Guard: only honor the alarm if it matches the current session.
                    // Stale alarms (e.g. after a restart with a new start time) must not
                    // kill a fresh session.
                    val expectedTrigger = startTimeMillis + autostopMins * 60_000L
                    val drift = kotlin.math.abs(System.currentTimeMillis() - expectedTrigger)
                    stopSession()
                    if (notificationsEnabled && backgroundNotificationsEnabled && drift < 5 * 60_000L) {
                        try {
                            postAlertNotification(
                                NOTIFICATION_AUTOSTOP_ID,
                                "Caffeinate Stopped",
                                "Screen awake ended after $autostopMins min."
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            ACTION_KEEP_GOING -> {
                // Dismiss reminder notification
                try {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(NOTIFICATION_REMINDER_ID)
                } catch (_: Exception) {}
            }
            ACTION_UPDATE_CONFIG -> {
                if (isRunning) applyLiveConfig()
            }
            else -> {
                if (intent == null && isRunning) {
                    // Resurrected by START_STICKY
                    serviceScope.launch {
                        try {
                            val savedMode = try { settingsRepository.caffeinateMode.first() } catch (_: Exception) { "OFF" }
                            val savedStart = try { settingsRepository.caffeinateStartTime.first() } catch (_: Exception) { 0L }
                            if (savedMode != "OFF") {
                                currentMode = savedMode
                                startTimeMillis = if (savedStart > 0) savedStart else System.currentTimeMillis()
                                _isAutoRunningFlow.value = (savedMode == "AUTO")
                                acquireWakeLocks()
                                showKeepScreenOnOverlay()
                                startLoops()
                                startWatchdog()
                                observeConfigWhileRunning()
                            } else {
                                stopSession()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Resurrection failed", e)
                            stopSession()
                        }
                    }
                } else if (intent == null) {
                    // System restarted us with no state and nothing persisted: nothing to do.
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startSession(isAuto: Boolean) {
        setRunningFlag(true)
        try { acquireWakeLocks() } catch (e: Exception) {
            Log.e(TAG, "acquireWakeLocks failed", e)
        }
        showKeepScreenOnOverlay()
        overrideScreenTimeout()  // prevent dimming

        if (startTimeMillis <= 0L) {
            // Fresh process (e.g. boot restore): keep the persisted start so the
            // timer and auto-stop deadline survive reboots instead of resetting.
            startTimeMillis = try {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(1500L) {
                        settingsRepository.caffeinateStartTime.first()
                    } ?: 0L
                }.takeIf { it > 0L } ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
            // Guard against clock changes producing a future start time.
            if (startTimeMillis > System.currentTimeMillis()) {
                startTimeMillis = System.currentTimeMillis()
            }
        }

        // Persist on the independent prefsScope so the write survives onDestroy.
        prefsScope.launch {
            try {
                notificationsEnabled = try { settingsRepository.caffeinateNotificationsEnabled.first() } catch (_: Exception) { true }
                backgroundNotificationsEnabled = try { settingsRepository.backgroundNotificationsEnabled.first() } catch (_: Exception) { true }
                settingsRepository.setCaffeinateMode(currentMode)
                settingsRepository.setCaffeinateStartTime(startTimeMillis)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist caffeinate start", e)
            }
        }

        applyLiveConfig()
        startLoops()
        startWatchdog()
        observeConfigWhileRunning()
        updateStatusNotificationSafe()
        requestTileUpdate()
    }

    /**
     * (Re)applies auto-stop + reminder from current settings without touching
     * the session start time. Called on start and on [ACTION_UPDATE_CONFIG]
     * so slider changes take effect live.
     */
    private fun applyLiveConfig() {
        serviceScope.launch {
            try {
                notificationsEnabled = try { settingsRepository.caffeinateNotificationsEnabled.first() } catch (_: Exception) { true }
                backgroundNotificationsEnabled = try { settingsRepository.backgroundNotificationsEnabled.first() } catch (_: Exception) { true }
                val isAuto = currentMode == "AUTO" || _isAutoRunningFlow.value
                if (!isAuto) {
                    val autostopEnabled = try { settingsRepository.caffeinateAutoStopEnabled.first() } catch (_: Exception) { false }
                    if (autostopEnabled) {
                        val autostopMins = try { settingsRepository.caffeinateAutoStopMins.first() } catch (_: Exception) { 60 }
                        scheduleAutoStopAlarm(autostopMins)
                        // If the deadline already passed (e.g. device slept through it),
                        // stop now instead of waiting for a stale alarm.
                        val remaining = startTimeMillis + autostopMins * 60_000L - System.currentTimeMillis()
                        if (remaining <= 0L) {
                            Log.d(TAG, "Auto-stop deadline already passed -> stopping now")
                            stopSession()
                            return@launch
                        }
                    } else {
                        cancelAutoStopAlarm()
                    }

                    val reminderEnabled = try { settingsRepository.caffeinateReminderEnabled.first() } catch (_: Exception) { false }
                    if (reminderEnabled && notificationsEnabled) {
                        val reminderMins = try { settingsRepository.caffeinateReminderMins.first() } catch (_: Exception) { 30 }
                        startReminderLoop(reminderMins)
                    } else {
                        reminderJob?.cancel()
                        reminderJob = null
                    }
                } else {
                    cancelAutoStopAlarm()
                    reminderJob?.cancel()
                    reminderJob = null
                }
                updateStatusNotificationSafe()
                requestTileUpdate()
            } catch (e: Exception) {
                Log.w(TAG, "applyLiveConfig failed", e)
            }
        }
    }

    /** Observes settings while the session runs so toggles apply without restart. */
    private fun observeConfigWhileRunning() {
        configJob?.cancel()
        configJob = serviceScope.launch {
            try {
                kotlinx.coroutines.flow.combine(
                    settingsRepository.caffeinateAutoStopEnabled,
                    settingsRepository.caffeinateAutoStopMins,
                    settingsRepository.caffeinateReminderEnabled,
                    settingsRepository.caffeinateReminderMins,
                    settingsRepository.caffeinateNotificationsEnabled
                ) { _: Boolean, _: Int, _: Boolean, _: Int, _: Boolean -> Unit }
                    .collect {
                        if (isRunning) applyLiveConfig()
                    }
            } catch (_: Exception) {
                // Flow collection ended (scope cancelled on stop) — expected.
            }
        }
        // Background master switch applies live without restart.
        serviceScope.launch {
            try {
                settingsRepository.backgroundNotificationsEnabled.collect { enabled ->
                    val changed = enabled != backgroundNotificationsEnabled
                    backgroundNotificationsEnabled = enabled
                    if (changed && isRunning) {
                        if (!enabled) {
                            try {
                                (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_STATUS_ID)
                            } catch (_: Exception) {}
                        }
                        applyLiveConfig()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopSession() {
        // Idempotent + thread-safe: concurrent STOP intents (tile + notification
        // + UI) must not double-release wakelocks or double-persist.
        synchronized(this) {
            if (!isRunning && startTimeMillis == 0L && currentMode == "OFF") {
                try { stopSelf() } catch (_: Exception) {}
                return
            }
            setRunningFlag(false)
            currentMode = "OFF"
            _isAutoRunningFlow.value = false
            startTimeMillis = 0
            _elapsedTimeFlow.value = 0
        }

        try { cancelAutoStopAlarm() } catch (_: Exception) {}
        pendingAutoStopJob?.cancel()
        pendingAutoStopJob = null
        configJob?.cancel()
        configJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        reminderJob?.cancel()
        reminderJob = null
        notificationJob?.cancel()
        notificationJob = null

        try { releaseWakeLocks() } catch (_: Exception) {}
        try { removeKeepScreenOnOverlay() } catch (_: Exception) {}
        try { restoreScreenTimeout() } catch (_: Exception) {}  // undo our dimming-prevention override

        // CRITICAL: persist on prefsScope (independent of serviceScope) so the
        // write is never cancelled by onDestroy -> stopSelf ordering.
        prefsScope.launch {
            try {
                settingsRepository.setCaffeinateMode("OFF")
                settingsRepository.setCaffeinateStartTime(0L)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist caffeinate stop", e)
            }
        }

        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_STATUS_ID)
        } catch (_: Exception) {}
        requestTileUpdate()
        try { stopSelf() } catch (_: Exception) {}
    }

    private fun startLoops() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            var lastText: String? = null
            while (isActive && isRunning) {
                try {
                    val elapsed = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0L)
                    _elapsedTimeFlow.value = elapsed

                    val text = currentStatusText()
                    if (text != lastText) {
                        lastText = text
                        updateStatusNotificationSafe()
                    }
                } catch (_: Exception) {
                    // Never let the loop die on a transient failure.
                }
                delay(1000)
            }
        }
    }

    /**
     * Watchdog: some OEMs (Xiaomi/MIUI, OnePlus, Samsung) aggressively release
     * wakelocks, kill overlays, or revert the screen-timeout override.
     * Re-acquire every 30s if still supposed to run.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive && isRunning) {
                delay(30_000L)
                if (!isActive || !isRunning) break
                try {
                    if (cpuWakeLock?.isHeld != true || screenWakeLock?.isHeld != true) {
                        Log.w(TAG, "Watchdog: wakelock lost -> re-acquiring")
                        acquireWakeLocks()
                    }
                    if (overlayView == null) {
                        showKeepScreenOnOverlay()
                    }
                    // OEMs / users can revert SCREEN_OFF_TIMEOUT behind our back.
                    // Re-assert MAX while the session is active so dimming can't creep in.
                    try {
                        if (Settings.System.canWrite(this@CaffeinateService)) {
                            val current = Settings.System.getInt(
                                contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1
                            )
                            if (current != -1 && current != Int.MAX_VALUE) {
                                Log.w(TAG, "Watchdog: timeout reverted to $current ms -> re-overriding to MAX")
                                Settings.System.putInt(
                                    contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE
                                )
                            }
                        }
                    } catch (_: Exception) {}
                } catch (e: Exception) {
                    Log.w(TAG, "Watchdog re-acquire failed", e)
                }
            }
        }
    }

    private fun startReminderLoop(intervalMins: Int) {
        reminderJob?.cancel()
        val safeInterval = intervalMins.coerceIn(1, 480)
        reminderJob = serviceScope.launch {
            val intervalMillis = safeInterval * 60_000L
            while (isActive && isRunning) {
                delay(intervalMillis)
                if (isActive && isRunning && notificationsEnabled) {
                    try {
                        val elapsed = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0L)
                        val elapsedMins = TimeUnit.MILLISECONDS.toMinutes(elapsed)
                        postReminderNotification(elapsedMins)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun scheduleAutoStopAlarm(minutes: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val safeMinutes = minutes.coerceIn(1, 1440)
        // Clamp stale start times (clock changed while running) so we never
        // schedule an alarm in the past that fires instantly.
        val base = startTimeMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val triggerAt = (base + safeMinutes * 60_000L).coerceAtLeast(System.currentTimeMillis() + 60_000L)
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
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { alarmManager.canScheduleExactAlarms() } catch (_: Exception) { true }
            } else true
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } else {
                // No exact-alarm permission (Android 12+): fall back to inexact so
                // auto-stop still works, just with Doze batching tolerance.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "Scheduled auto-stop alarm in $safeMinutes minutes (exact=$canExact)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule auto-stop alarm", e)
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
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: run {
            Log.e(TAG, "PowerManager unavailable — cannot acquire wakelocks")
            return
        }

        try {
            if (cpuWakeLock == null) {
                cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Toolz:CaffeinateCpuLock").apply {
                    setReferenceCounted(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create CPU wakelock", e)
        }
        try {
            if (cpuWakeLock?.isHeld == false) {
                cpuWakeLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire CPU wakelock", e)
        }

        try {
            if (screenWakeLock == null) {
                @Suppress("DEPRECATION")
                screenWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                    "Toolz:CaffeinateScreenLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create screen wakelock", e)
        }
        try {
            if (screenWakeLock?.isHeld == false) {
                // 10-min timeout with watchdog re-acquire: bounds the hold so a
                // leaked lock can never drain the battery forever, while the
                // watchdog keeps an active session alive indefinitely.
                screenWakeLock?.acquire(10 * 60_000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire screen wakelock", e)
        }
    }

    private fun releaseWakeLocks() {
        try {
            if (screenWakeLock?.isHeld == true) {
                screenWakeLock?.release()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Screen wakelock under-locked on release", e)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release screen wakelock", e)
        }
        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "CPU wakelock under-locked on release", e)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release CPU wakelock", e)
        }
    }

    /**
     * Prevents screen dimming by overriding SCREEN_OFF_TIMEOUT to Int.MAX_VALUE.
     * Requires WRITE_SETTINGS permission (user grants via Settings > Apps > Special access).
     * The original value is persisted in DataStore (not just RAM) so a process
     * death can never leave the device stuck at MAX, and a stale MAX from a
     * previous crash is detected and repaired on next start.
     */
    private fun overrideScreenTimeout() {
        try {
            if (!Settings.System.canWrite(this)) {
                Log.d(TAG, "WRITE_SETTINGS not granted — screen may still dim (wakelock is backup)")
                return
            }
            val current = Settings.System.getInt(
                contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000
            )
            if (current == Int.MAX_VALUE) {
                // Stale MAX left by a previous crash/kill: try to restore the
                // persisted original, otherwise fall back to a safe 30s.
                serviceScope.launch {
                    try {
                        val persisted = try { settingsRepository.caffeinateSavedTimeout.first() } catch (_: Exception) { -1 }
                        val fallback = if (persisted > 0 && persisted != Int.MAX_VALUE) persisted else 30_000
                        savedScreenTimeout = fallback
                        Log.w(TAG, "Stale MAX timeout detected -> will restore to $fallback ms on stop")
                    } catch (_: Exception) {}
                }
                return
            }
            savedScreenTimeout = current
            Settings.System.putInt(
                contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE
            )
            Log.d(TAG, "Screen timeout overridden ($current ms → MAX) to prevent dimming")
            prefsScope.launch {
                try { settingsRepository.setCaffeinateSavedTimeout(current) } catch (e: Exception) {
                    Log.w(TAG, "Failed to persist saved timeout", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to override screen timeout", e)
        }
    }

    /**
     * Restores the screen timeout saved by [overrideScreenTimeout].
     * Falls back to the DataStore-persisted value if the in-memory copy was lost.
     */
    private fun restoreScreenTimeout() {
        try {
            var toRestore = savedScreenTimeout
            if (toRestore <= 0) {
                try {
                    toRestore = kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(1500L) {
                            settingsRepository.caffeinateSavedTimeout.first()
                        } ?: -1
                    }
                } catch (_: Exception) { toRestore = -1 }
            }
            if (toRestore > 0 && toRestore != Int.MAX_VALUE && Settings.System.canWrite(this)) {
                Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, toRestore
                )
                Log.d(TAG, "Screen timeout restored to $toRestore ms")
            }
            savedScreenTimeout = -1
            prefsScope.launch {
                try { settingsRepository.clearCaffeinateSavedTimeout() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore screen timeout", e)
        }
    }

    private fun showKeepScreenOnOverlay() {
        if (overlayView != null) return
        // Avoid a SecurityException log-spam loop when the user never granted
        // "Display over other apps" — the wakelock is the primary mechanism.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (!Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "Overlay skipped (no SYSTEM_ALERT_WINDOW) — wakelock is primary")
                    return
                }
            } catch (_: Exception) {}
        }
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
        val elapsed = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %02d min", hours, minutes)
        } else {
            String.format(Locale.getDefault(), "%d min", minutes)
        }
    }

    private fun createStatusNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_STATUS_ID)
            .setContentTitle("Caffeinate is active")
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
        // Background toggle hides the ongoing "Caffeinate is active" status.
        if (!backgroundNotificationsEnabled) {
            try { notificationManager.cancel(NOTIFICATION_STATUS_ID) } catch (_: Exception) {}
            return
        }
        notificationManager.notify(NOTIFICATION_STATUS_ID, createStatusNotification(currentStatusText()))
    }

    /** Never-crash variant used from idempotent paths and background loops. */
    private fun updateStatusNotificationSafe() {
        try {
            if (!isRunning) return
            updateStatusNotification()
        } catch (e: Exception) {
            Log.w(TAG, "updateStatusNotification failed", e)
        }
    }

    private fun postReminderNotification(minutesElapsed: Long) {
        if (!backgroundNotificationsEnabled) return
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
        if (!backgroundNotificationsEnabled) return
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
            try {
                TileService.requestListeningState(this, ComponentName(this, CaffeinateTileService::class.java))
            } catch (_: Exception) {}
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Swiping the app away must NOT kill an active session. START_STICKY
        // already asks for resurrection, but re-assert foreground state now so
        // there is no gap on OEMs that trim background services aggressively.
        if (isRunning) {
            try {
                ensureForeground()
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        // If the system killed us while a session was active, keep the static
        // flags + persisted mode so START_STICKY resurrection and the boot
        // receiver can restore. Only an explicit stopSession() clears them.
        val wasRunning = isRunning
        if (!wasRunning) {
            setRunningFlag(false)
        }
        configJob?.cancel()
        watchdogJob?.cancel()
        reminderJob?.cancel()
        notificationJob?.cancel()
        pendingAutoStopJob?.cancel()
        pendingAutoStopJob = null
        serviceScope.cancel()
        // NOTE: prefsScope is intentionally NOT cancelled here — a pending
        // stop-persist must be allowed to land.
        try { releaseWakeLocks() } catch (_: Exception) {}
        try { removeKeepScreenOnOverlay() } catch (_: Exception) {}
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
