package com.frerox.toolz.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

@AndroidEntryPoint
class ToolService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Stopwatch State
    private val _stopwatchTime = MutableStateFlow(0L)
    val stopwatchTime: StateFlow<Long> = _stopwatchTime
    private var stopwatchJob: Job? = null
    private var isStopwatchRunning = false

    // Timer State
    private val _timerRemaining = MutableStateFlow(0L)
    val timerRemaining: StateFlow<Long> = _timerRemaining
    private var timerJob: Job? = null
    private var isTimerRunning = false

    // Pomodoro State
    private val _pomodoroRemaining = MutableStateFlow(0L)
    val pomodoroRemaining: StateFlow<Long> = _pomodoroRemaining
    private var pomodoroJob: Job? = null
    private var isPomodoroRunning = false
    private var pomodoroMode = "WORK"

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
    }

    // --- Stopwatch Logic ---
    fun startStopwatch() {
        if (isStopwatchRunning) return
        isStopwatchRunning = true
        val startTimestamp = System.currentTimeMillis() - _stopwatchTime.value
        stopwatchJob = serviceScope.launch {
            while (isStopwatchRunning) {
                _stopwatchTime.value = System.currentTimeMillis() - startTimestamp
                updateStopwatchNotification()
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
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var lastUpdate = System.currentTimeMillis()
            while (_timerRemaining.value > 0 && isTimerRunning) {
                delay(100)
                val now = System.currentTimeMillis()
                val diff = now - lastUpdate
                _timerRemaining.value = (_timerRemaining.value - diff).coerceAtLeast(0)
                lastUpdate = now
                updateTimerNotification()
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
        pomodoroJob?.cancel()
        pomodoroJob = serviceScope.launch {
            var lastUpdate = System.currentTimeMillis()
            while (_pomodoroRemaining.value > 0 && isPomodoroRunning) {
                delay(1000)
                val now = System.currentTimeMillis()
                val diff = now - lastUpdate
                _pomodoroRemaining.value = (_pomodoroRemaining.value - diff).coerceAtLeast(0)
                lastUpdate = now
                updatePomodoroNotification()
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
                "Active Tools",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createStopwatchNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_STOPWATCH_TOGGLE }
        val togglePendingIntent = PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stopwatch")
            .setContentText(formatTime(_stopwatchTime.value))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(isStopwatchRunning)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .addAction(
                if (isStopwatchRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isStopwatchRunning) "Pause" else "Resume",
                togglePendingIntent
            )
            .build()
    }

    private fun updateStopwatchNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(STOPWATCH_NOTIFICATION_ID, createStopwatchNotification())
    }

    private fun createTimerNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer")
            .setContentText(text ?: formatTime(_timerRemaining.value))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(isTimerRunning)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
        
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_TOGGLE }
        val togglePendingIntent = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pomodoro ($pomodoroMode)")
            .setContentText(text ?: formatTime(_pomodoroRemaining.value))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(isPomodoroRunning)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .addAction(
                if (isPomodoroRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPomodoroRunning) "Pause" else "Resume",
                togglePendingIntent
            )
            .build()
    }

    private fun updatePomodoroNotification(text: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(POMODORO_NOTIFICATION_ID, createPomodoroNotification(text))
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
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
