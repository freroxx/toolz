package com.frerox.toolz.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ToolService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Notifications State
    private var isGlobalNotificationsEnabled = true
    private var isTimerNotificationsEnabled = true

    // Stopwatch State
    private val _stopwatchTime = MutableStateFlow(0L)
    val stopwatchTime: StateFlow<Long> = _stopwatchTime
    private var stopwatchJob: Job? = null
    private var isStopwatchRunning = false
    private var stopwatchBase: Long = 0L

    // Timer State
    private val _timerRemaining = MutableStateFlow(0L)
    val timerRemaining: StateFlow<Long> = _timerRemaining
    private var timerJob: Job? = null
    private var isTimerRunning = false
    private var timerEndTimestamp: Long = 0L

    // Pomodoro State
    private val _pomodoroRemaining = MutableStateFlow(0L)
    val pomodoroRemaining: StateFlow<Long> = _pomodoroRemaining
    private var pomodoroJob: Job? = null
    private var isPomodoroRunning = false
    private var pomodoroMode = "WORK"
    private var pomodoroEndTimestamp: Long = 0L

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ToolService = this@ToolService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOPWATCH_TOGGLE -> if (isStopwatchRunning) pauseStopwatch() else startStopwatch()
            ACTION_TIMER_STOP -> resetTimer()
            ACTION_POMODORO_TOGGLE -> {
                if (isPomodoroRunning) pausePomodoro() 
                else startPomodoro(_pomodoroRemaining.value, pomodoroMode)
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        serviceScope.launch {
            combine(
                settingsRepository.notificationsEnabled,
                settingsRepository.timerNotifications
            ) { global, timer -> global to timer }.collect { (global, timer) ->
                isGlobalNotificationsEnabled = global
                isTimerNotificationsEnabled = timer
                if (isStopwatchRunning) updateStopwatchNotification()
                if (isTimerRunning) updateTimerNotification()
                if (isPomodoroRunning) updatePomodoroNotification()
            }
        }
    }

    // --- Stopwatch Logic ---
    fun startStopwatch() {
        if (isStopwatchRunning) return
        isStopwatchRunning = true
        stopwatchBase = SystemClock.elapsedRealtime() - _stopwatchTime.value
        stopwatchJob = serviceScope.launch {
            while (isStopwatchRunning) {
                _stopwatchTime.value = SystemClock.elapsedRealtime() - stopwatchBase
                // For live feel, we update less frequently if chronometer is used, 
                // but we keep the flow updated for UI.
                delay(100)
            }
        }
        startForeground(STOPWATCH_NOTIFICATION_ID, createStopwatchNotification())
    }

    fun pauseStopwatch() {
        isStopwatchRunning = false
        stopwatchJob?.cancel()
        updateStopwatchNotification()
    }

    fun resetStopwatch() {
        pauseStopwatch()
        _stopwatchTime.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // --- Timer Logic ---
    fun startTimer(durationMillis: Long) {
        _timerRemaining.value = durationMillis
        isTimerRunning = true
        timerEndTimestamp = SystemClock.elapsedRealtime() + durationMillis
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_timerRemaining.value > 0 && isTimerRunning) {
                _timerRemaining.value = (timerEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                delay(100)
            }
            if (_timerRemaining.value == 0L) {
                onTimerFinished()
            }
        }
        startForeground(TIMER_NOTIFICATION_ID, createTimerNotification())
    }

    fun pauseTimer() {
        isTimerRunning = false
        timerJob?.cancel()
        updateTimerNotification("Paused")
    }

    fun resetTimer() {
        isTimerRunning = false
        timerJob?.cancel()
        _timerRemaining.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun onTimerFinished() {
        isTimerRunning = false
        updateTimerNotification("Time is up!")
    }

    // --- Pomodoro Logic ---
    fun startPomodoro(durationMillis: Long, mode: String) {
        _pomodoroRemaining.value = durationMillis
        pomodoroMode = mode
        isPomodoroRunning = true
        pomodoroEndTimestamp = SystemClock.elapsedRealtime() + durationMillis
        pomodoroJob?.cancel()
        pomodoroJob = serviceScope.launch {
            while (_pomodoroRemaining.value > 0 && isPomodoroRunning) {
                _pomodoroRemaining.value = (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                delay(1000)
            }
            if (_pomodoroRemaining.value == 0L) {
                isPomodoroRunning = false
                updatePomodoroNotification("Session finished!")
            }
        }
        startForeground(POMODORO_NOTIFICATION_ID, createPomodoroNotification())
    }

    fun pausePomodoro() {
        isPomodoroRunning = false
        pomodoroJob?.cancel()
        updatePomodoroNotification("Paused")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Toolz Active Tools",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live notifications for active tools like Stopwatch and Timer"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createStopwatchNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "stopwatch")
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_STOPWATCH_TOGGLE }
        val togglePendingIntent = PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stopwatch")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(isStopwatchRunning)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                if (isStopwatchRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isStopwatchRunning) "Pause" else "Resume",
                togglePendingIntent
            )

        if (isGlobalNotificationsEnabled && isTimerNotificationsEnabled) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() - _stopwatchTime.value)
            if (!isStopwatchRunning) {
                builder.setContentText("Paused: ${formatTime(_stopwatchTime.value)}")
                builder.setUsesChronometer(false)
            }
        } else {
            builder.setContentText("Stopwatch is active")
        }

        return builder.build()
    }

    private fun updateStopwatchNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(STOPWATCH_NOTIFICATION_ID, createStopwatchNotification())
    }

    private fun createTimerNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "timer")
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(isTimerRunning)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
        
        if (isGlobalNotificationsEnabled && isTimerNotificationsEnabled) {
            if (isTimerRunning) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + _timerRemaining.value)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            } else {
                builder.setContentText(text ?: formatTime(_timerRemaining.value))
            }
        } else {
            builder.setContentText("Timer is active")
        }

        if (isTimerRunning || _timerRemaining.value > 0) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
        }
        
        return builder.build()
    }

    private fun updateTimerNotification(text: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TIMER_NOTIFICATION_ID, createTimerNotification(text))
    }

    private fun createPomodoroNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "pomodoro")
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_TOGGLE }
        val togglePendingIntent = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pomodoro ($pomodoroMode)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(isPomodoroRunning)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                if (isPomodoroRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPomodoroRunning) "Pause" else "Resume",
                togglePendingIntent
            )

        if (isGlobalNotificationsEnabled && isTimerNotificationsEnabled) {
            if (isPomodoroRunning) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + _pomodoroRemaining.value)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
            } else {
                builder.setContentText(text ?: formatTime(_pomodoroRemaining.value))
            }
        } else {
            builder.setContentText("Pomodoro session active")
        }

        return builder.build()
    }

    private fun updatePomodoroNotification(text: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(POMODORO_NOTIFICATION_ID, createPomodoroNotification(text))
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = (millis + 999) / 1000
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "tool_service_channel"
        const val STOPWATCH_NOTIFICATION_ID = 2001
        const val TIMER_NOTIFICATION_ID = 2002
        const val POMODORO_NOTIFICATION_ID = 2003

        const val ACTION_STOPWATCH_TOGGLE = "com.frerox.toolz.STOPWATCH_TOGGLE"
        const val ACTION_TIMER_STOP = "com.frerox.toolz.TIMER_STOP"
        const val ACTION_POMODORO_TOGGLE = "com.frerox.toolz.POMODORO_TOGGLE"
    }
}
