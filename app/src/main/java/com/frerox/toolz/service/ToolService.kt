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
    private val _isStopwatchRunning = MutableStateFlow(false)
    val isStopwatchRunning: StateFlow<Boolean> = _isStopwatchRunning
    private var stopwatchJob: Job? = null
    private var stopwatchBase: Long = 0L

    // Timer State
    private val _timerRemaining = MutableStateFlow(0L)
    val timerRemaining: StateFlow<Long> = _timerRemaining
    private val _timerInitial = MutableStateFlow(0L)
    val timerInitial: StateFlow<Long> = _timerInitial
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning: StateFlow<Boolean> = _isTimerRunning
    private var timerJob: Job? = null
    private var timerEndTimestamp: Long = 0L

    // Pomodoro State
    private val _pomodoroRemaining = MutableStateFlow(0L)
    val pomodoroRemaining: StateFlow<Long> = _pomodoroRemaining
    private val _isPomodoroRunning = MutableStateFlow(false)
    val isPomodoroRunning: StateFlow<Boolean> = _isPomodoroRunning
    private var pomodoroJob: Job? = null
    private var pomodoroMode = "WORK"
    private var pomodoroEndTimestamp: Long = 0L

    // Todo Session State
    private val _todoSessionTime = MutableStateFlow(0L)
    val todoSessionTime: StateFlow<Long> = _todoSessionTime
    private val _isTodoSessionActive = MutableStateFlow(false)
    val isTodoSessionActive: StateFlow<Boolean> = _isTodoSessionActive
    private val _todoTaskId = MutableStateFlow<Int?>(null)
    val todoTaskId: StateFlow<Int?> = _todoTaskId
    private val _todoTaskTitle = MutableStateFlow<String?>(null)
    val todoTaskTitle: StateFlow<String?> = _todoTaskTitle
    private var todoJob: Job? = null
    private var todoBase: Long = 0L

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): ToolService = this@ToolService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOPWATCH_TOGGLE -> if (_isStopwatchRunning.value) pauseStopwatch() else startStopwatch()
            ACTION_STOPWATCH_STOP -> resetStopwatch()
            ACTION_TIMER_TOGGLE -> if (_isTimerRunning.value) pauseTimer() else startTimer(_timerRemaining.value)
            ACTION_TIMER_STOP -> resetTimer()
            ACTION_POMODORO_TOGGLE -> {
                if (_isPomodoroRunning.value) pausePomodoro() 
                else startPomodoro(_pomodoroRemaining.value, pomodoroMode)
            }
            ACTION_POMODORO_STOP -> resetPomodoro()
            ACTION_TODO_STOP -> stopTodoSession()
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
                refreshNotifications()
            }
        }
    }

    private fun refreshNotifications() {
        if (_isStopwatchRunning.value) updateStopwatchNotification()
        if (_isTimerRunning.value) updateTimerNotification()
        if (_isPomodoroRunning.value) updatePomodoroNotification()
        if (_isTodoSessionActive.value) updateTodoNotification()
    }

    // --- Stopwatch Logic ---
    fun startStopwatch() {
        if (_isStopwatchRunning.value) return
        _isStopwatchRunning.value = true
        stopwatchBase = SystemClock.elapsedRealtime() - _stopwatchTime.value
        stopwatchJob = serviceScope.launch {
            while (_isStopwatchRunning.value) {
                _stopwatchTime.value = SystemClock.elapsedRealtime() - stopwatchBase
                delay(100)
            }
        }
        startForeground(STOPWATCH_NOTIFICATION_ID, createStopwatchNotification())
    }

    fun pauseStopwatch() {
        _isStopwatchRunning.value = false
        stopwatchJob?.cancel()
        updateStopwatchNotification()
    }

    fun resetStopwatch() {
        _isStopwatchRunning.value = false
        stopwatchJob?.cancel()
        _stopwatchTime.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // --- Timer Logic ---
    fun startTimer(durationMillis: Long, initialDuration: Long? = null) {
        if (durationMillis <= 0) return
        _timerRemaining.value = durationMillis
        initialDuration?.let { _timerInitial.value = it }
        _isTimerRunning.value = true
        timerEndTimestamp = SystemClock.elapsedRealtime() + durationMillis
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_timerRemaining.value > 0 && _isTimerRunning.value) {
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
        _isTimerRunning.value = false
        timerJob?.cancel()
        updateTimerNotification("Paused")
    }

    fun resetTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        _timerRemaining.value = 0L
        _timerInitial.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun setTimerInitial(millis: Long) {
        _timerInitial.value = millis
        _timerRemaining.value = millis
    }

    private fun onTimerFinished() {
        _isTimerRunning.value = false
        updateTimerNotification("Time is up!")
    }

    // --- Pomodoro Logic ---
    fun startPomodoro(durationMillis: Long, mode: String) {
        _pomodoroRemaining.value = durationMillis
        pomodoroMode = mode
        _isPomodoroRunning.value = true
        pomodoroEndTimestamp = SystemClock.elapsedRealtime() + durationMillis
        pomodoroJob?.cancel()
        pomodoroJob = serviceScope.launch {
            while (_pomodoroRemaining.value > 0 && _isPomodoroRunning.value) {
                _pomodoroRemaining.value = (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                delay(1000)
            }
            if (_pomodoroRemaining.value == 0L) {
                _isPomodoroRunning.value = false
                updatePomodoroNotification("Session finished!")
            }
        }
        startForeground(POMODORO_NOTIFICATION_ID, createPomodoroNotification())
    }

    fun pausePomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        updatePomodoroNotification("Paused")
    }

    fun resetPomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        _pomodoroRemaining.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // --- Todo Session Logic ---
    fun startTodoSession(taskId: Int, title: String) {
        _todoTaskId.value = taskId
        _todoTaskTitle.value = title
        _isTodoSessionActive.value = true
        todoBase = SystemClock.elapsedRealtime() - _todoSessionTime.value
        todoJob?.cancel()
        todoJob = serviceScope.launch {
            while (_isTodoSessionActive.value) {
                _todoSessionTime.value = SystemClock.elapsedRealtime() - todoBase
                delay(1000)
            }
        }
        startForeground(TODO_NOTIFICATION_ID, createTodoNotification())
    }

    fun stopTodoSession() {
        _isTodoSessionActive.value = false
        todoJob?.cancel()
        _todoSessionTime.value = 0L
        _todoTaskId.value = null
        _todoTaskTitle.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Toolz Active Tools",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live notifications for active tools"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createStopwatchNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "stopwatch") }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_STOPWATCH_TOGGLE }
        val togglePI = PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_STOPWATCH_STOP }
        val stopPI = PendingIntent.getService(this, 11, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Stopwatch")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(_isStopwatchRunning.value)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(if (_isStopwatchRunning.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (_isStopwatchRunning.value) "Pause" else "Resume", togglePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)

        if (isGlobalNotificationsEnabled && isTimerNotificationsEnabled) {
            if (_isStopwatchRunning.value) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() - _stopwatchTime.value)
            } else {
                builder.setContentText("Paused: ${formatTime(_stopwatchTime.value)}")
            }
        }
        return builder.build()
    }

    private fun updateStopwatchNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(STOPWATCH_NOTIFICATION_ID, createStopwatchNotification())
    }

    private fun createTimerNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "timer") }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_TOGGLE }
        val togglePI = PendingIntent.getService(this, 21, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_STOP }
        val stopPI = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Timer")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(_isTimerRunning.value)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(if (_isTimerRunning.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (_isTimerRunning.value) "Pause" else "Resume", togglePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)
        
        if (isGlobalNotificationsEnabled && isTimerNotificationsEnabled) {
            if (_isTimerRunning.value) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + _timerRemaining.value)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
            } else {
                builder.setContentText(text ?: formatTime(_timerRemaining.value))
            }
        }
        return builder.build()
    }

    private fun updateTimerNotification(text: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TIMER_NOTIFICATION_ID, createTimerNotification(text))
    }

    private fun createPomodoroNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "pomodoro") }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_TOGGLE }
        val togglePI = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_STOP }
        val stopPI = PendingIntent.getService(this, 31, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pomodoro ($pomodoroMode)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(_isPomodoroRunning.value)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(if (_isPomodoroRunning.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (_isPomodoroRunning.value) "Pause" else "Resume", togglePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)

        if (isGlobalNotificationsEnabled && isTimerNotificationsEnabled) {
            if (_isPomodoroRunning.value) {
                builder.setUsesChronometer(true)
                builder.setWhen(System.currentTimeMillis() + _pomodoroRemaining.value)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
            } else {
                builder.setContentText(text ?: formatTime(_pomodoroRemaining.value))
            }
        }
        return builder.build()
    }

    private fun updatePomodoroNotification(text: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(POMODORO_NOTIFICATION_ID, createPomodoroNotification(text))
    }

    private fun createTodoNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra("navigate_to", "todo") }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TODO_STOP }
        val stopPI = PendingIntent.getService(this, 41, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Active Task: ${_todoTaskTitle.value}")
            .setContentText("Focus session in progress")
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() - _todoSessionTime.value)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Session", stopPI)
            .build()
    }

    private fun updateTodoNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TODO_NOTIFICATION_ID, createTodoNotification())
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
        const val TODO_NOTIFICATION_ID = 2004

        const val ACTION_STOPWATCH_TOGGLE = "com.frerox.toolz.STOPWATCH_TOGGLE"
        const val ACTION_STOPWATCH_STOP = "com.frerox.toolz.STOPWATCH_STOP"
        const val ACTION_TIMER_TOGGLE = "com.frerox.toolz.TIMER_TOGGLE"
        const val ACTION_TIMER_STOP = "com.frerox.toolz.TIMER_STOP"
        const val ACTION_POMODORO_TOGGLE = "com.frerox.toolz.POMODORO_TOGGLE"
        const val ACTION_POMODORO_STOP = "com.frerox.toolz.POMODORO_STOP"
        const val ACTION_TODO_STOP = "com.frerox.toolz.TODO_STOP"
    }
}
