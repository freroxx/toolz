package com.frerox.toolz.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.widget.WidgetUpdateManager
import com.frerox.toolz.widget.glance.PomodoroGlanceWidget
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

    @Inject
    lateinit var widgetUpdateManager: WidgetUpdateManager

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
    private val _pomodoroRemaining = MutableStateFlow(25 * 60 * 1000L) // Default to 25:00
    val pomodoroRemaining: StateFlow<Long> = _pomodoroRemaining
    private val _isPomodoroRunning = MutableStateFlow(false)
    val isPomodoroRunning: StateFlow<Boolean> = _isPomodoroRunning
    private val _pomodoroSessionsDone = MutableStateFlow(0)
    val pomodoroSessionsDone: StateFlow<Int> = _pomodoroSessionsDone
    private var pomodoroJob: Job? = null
    private var pomodoroMode = "WORK"
    private var pomodoroEndTimestamp: Long = 0L
    private var workSessionsCount = 0

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
            ACTION_POMODORO_STOP, ACTION_POMODORO_RESET -> resetPomodoro()
            ACTION_POMODORO_SKIP -> skipPomodoro()
            ACTION_TODO_STOP -> stopTodoSession()
            ACTION_DISMISS_ALARM -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (notificationId != -1) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.cancel(notificationId)
                }
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createAllChannels(this)
        
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
        startForeground(NotificationHelper.ID_STOPWATCH, createStopwatchNotification())
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
        startForeground(NotificationHelper.ID_TIMER, createTimerNotification())
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
        showAlarmNotification("Timer", "Time is up!", NotificationHelper.ID_TIMER_ALARM, Screen.Timer.route)
        updateTimerNotification("Time is up!")
    }

    // --- Pomodoro Logic ---
    fun startPomodoro(durationMillis: Long, mode: String) {
        val actualDuration = if (durationMillis <= 0) {
            when (mode) {
                "SHORT_BREAK" -> 5 * 60 * 1000L
                "LONG_BREAK" -> 15 * 60 * 1000L
                else -> 25 * 60 * 1000L
            }
        } else durationMillis

        _pomodoroRemaining.value = actualDuration
        pomodoroMode = mode
        _isPomodoroRunning.value = true
        pomodoroEndTimestamp = SystemClock.elapsedRealtime() + actualDuration
        pomodoroJob?.cancel()
        pomodoroJob = serviceScope.launch {
            while (_pomodoroRemaining.value > 0 && _isPomodoroRunning.value) {
                _pomodoroRemaining.value = (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                pushPomodoroWidgetState()
                delay(1000)
            }
            if (_pomodoroRemaining.value == 0L && _isPomodoroRunning.value) {
                _isPomodoroRunning.value = false
                onPomodoroFinished()
            }
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        startForeground(NotificationHelper.ID_POMODORO, createPomodoroNotification())
    }

    private fun onPomodoroFinished() {
        if (pomodoroMode == "WORK") {
            workSessionsCount++
            _pomodoroSessionsDone.value++
        }
        
        val title   = if (pomodoroMode == "WORK") "Work Session Finished" else "Break Finished"
        val message = if (pomodoroMode == "WORK") "Time to take a break! 🌱" else "Ready to focus? 🔥"
        showAlarmNotification(title, message, NotificationHelper.ID_POMODORO_ALARM, Screen.Pomodoro.route)
        vibrateFinish()
        
        // Cycle mode automatically
        cyclePomodoroMode()
        
        serviceScope.launch { pushPomodoroWidgetState() }
        updatePomodoroNotification("Session finished!")
    }

    private fun cyclePomodoroMode() {
        pomodoroMode = when {
            pomodoroMode == "WORK" && workSessionsCount % 4 == 0 -> "LONG_BREAK"
            pomodoroMode == "WORK" -> "SHORT_BREAK"
            else -> "WORK"
        }
        _pomodoroRemaining.value = when (pomodoroMode) {
            "SHORT_BREAK" -> 5 * 60 * 1000L
            "LONG_BREAK" -> 15 * 60 * 1000L
            else -> 25 * 60 * 1000L
        }
    }

    fun pausePomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        serviceScope.launch { pushPomodoroWidgetState() }
        updatePomodoroNotification("Paused")
    }

    fun resetPomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        _pomodoroRemaining.value = when (pomodoroMode) {
            "SHORT_BREAK" -> 5 * 60 * 1000L
            "LONG_BREAK" -> 15 * 60 * 1000L
            else -> 25 * 60 * 1000L
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** Skip current Pomodoro session — treated as finished without alarm. */
    fun skipPomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        
        if (pomodoroMode == "WORK") {
            workSessionsCount++
            _pomodoroSessionsDone.value++
        }
        
        cyclePomodoroMode()
        
        serviceScope.launch { pushPomodoroWidgetState() }
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
        startForeground(NotificationHelper.ID_TODO, createTodoNotification())
    }

    fun stopTodoSession() {
        _isTodoSessionActive.value = false
        todoJob?.cancel()
        _todoSessionTime.value = 0L
        _todoTaskId.value = null
        _todoTaskTitle.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun showAlarmNotification(title: String, message: String, notificationId: Int, route: String) {
        val intent = Intent(this, MainActivity::class.java).apply { 
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val dismissIntent = Intent(this, ToolService::class.java).apply { 
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPI = PendingIntent.getService(this, notificationId + 100, dismissIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ALARM)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPI)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, builder.build())
    }

    private fun createStopwatchNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Stopwatch.route) }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_STOPWATCH_TOGGLE }
        val togglePI = PendingIntent.getService(this, 1, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_STOPWATCH_STOP }
        val stopPI = PendingIntent.getService(this, 11, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
            .setContentTitle("Stopwatch")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(_isStopwatchRunning.value)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(if (_isStopwatchRunning.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (_isStopwatchRunning.value) "Pause" else "Resume", togglePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)

        if (_isStopwatchRunning.value) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() - _stopwatchTime.value)
            builder.setContentText("Stopwatch is running")
        } else {
            builder.setContentText("Paused: ${formatTime(_stopwatchTime.value)}")
        }
        return builder.build()
    }

    private fun updateStopwatchNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.ID_STOPWATCH, createStopwatchNotification())
    }

    private fun createTimerNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Timer.route) }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_TOGGLE }
        val togglePI = PendingIntent.getService(this, 21, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_STOP }
        val stopPI = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
            .setContentTitle("Timer")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(_isTimerRunning.value)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(if (_isTimerRunning.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (_isTimerRunning.value) "Pause" else "Resume", togglePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)
        
        if (_isTimerRunning.value) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() + _timerRemaining.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
            builder.setContentText("Timer is running")
        } else {
            builder.setContentText(text ?: formatTime(_timerRemaining.value))
        }
        return builder.build()
    }

    private fun updateTimerNotification(text: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.ID_TIMER, createTimerNotification(text))
    }

    private fun createPomodoroNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Pomodoro.route) }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_TOGGLE }
        val togglePI = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_STOP }
        val stopPI = PendingIntent.getService(this, 31, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
            .setContentTitle("Pomodoro ($pomodoroMode)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(_isPomodoroRunning.value)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(if (_isPomodoroRunning.value) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (_isPomodoroRunning.value) "Pause" else "Resume", togglePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)

        if (_isPomodoroRunning.value) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() + _pomodoroRemaining.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
            builder.setContentText("Session in progress")
        } else {
            builder.setContentText(text ?: formatTime(_pomodoroRemaining.value))
        }
        return builder.build()
    }

    private fun updatePomodoroNotification(text: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.ID_POMODORO, createPomodoroNotification(text))
    }

    private fun createTodoNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Todo.route) }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TODO_STOP }
        val stopPI = PendingIntent.getService(this, 41, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
            .setContentTitle("Active Task: ${_todoTaskTitle.value}")
            .setContentText("Focus session in progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis() - _todoSessionTime.value)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Session", stopPI)
            .build()
    }

    private fun updateTodoNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.ID_TODO, createTodoNotification())
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

    // ── Glance Widget State Push ───────────────────────────────────────────

    private suspend fun pushPomodoroWidgetState() {
        try {
            val totalMs = when (pomodoroMode) {
                "SHORT_BREAK" -> 5 * 60 * 1000f
                "LONG_BREAK"  -> 15 * 60 * 1000f
                else          -> 25 * 60 * 1000f
            }
            widgetUpdateManager.updatePomodoroWidget(
                mode = pomodoroMode,
                remainingMs = _pomodoroRemaining.value.toFloat(),
                totalMs = totalMs,
                isRunning = _isPomodoroRunning.value,
                sessionsDone = _pomodoroSessionsDone.value,
                sessionsGoal = 8
            )
        } catch (_: Exception) {}
    }

    // ── Vibration ─────────────────────────────────────────────────────────

    private fun vibrateFinish() {
        try {
            val pattern = longArrayOf(0, 300, 100, 300, 100, 600)
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = getSystemService(Vibrator::class.java)
                vib?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val ACTION_STOPWATCH_TOGGLE = "com.frerox.toolz.STOPWATCH_TOGGLE"
        const val ACTION_STOPWATCH_STOP = "com.frerox.toolz.STOPWATCH_STOP"
        const val ACTION_TIMER_TOGGLE = "com.frerox.toolz.TIMER_TOGGLE"
        const val ACTION_TIMER_STOP = "com.frerox.toolz.TIMER_STOP"
        const val ACTION_POMODORO_TOGGLE = "com.frerox.toolz.POMODORO_TOGGLE"
        const val ACTION_POMODORO_STOP   = "com.frerox.toolz.POMODORO_STOP"
        const val ACTION_POMODORO_SKIP   = "com.frerox.toolz.POMO_SKIP"
        const val ACTION_POMODORO_RESET  = "com.frerox.toolz.POMO_RESET"
        const val ACTION_TODO_STOP       = "com.frerox.toolz.TODO_STOP"
        const val ACTION_DISMISS_ALARM   = "com.frerox.toolz.DISMISS_ALARM"
        
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
