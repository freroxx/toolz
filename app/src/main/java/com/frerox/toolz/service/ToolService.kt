package com.frerox.toolz.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
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
    private var isPomodoroNotificationsEnabled = true

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
    private val _isTimerRinging = MutableStateFlow(false)
    val isTimerRinging: StateFlow<Boolean> = _isTimerRinging
    private var timerJob: Job? = null
    private var timerEndTimestamp: Long = 0L

    // Pomodoro State
    private val _pomodoroRemaining = MutableStateFlow(25 * 60 * 1000L)
    val pomodoroRemaining: StateFlow<Long> = _pomodoroRemaining
    private val _isPomodoroRunning = MutableStateFlow(false)
    val isPomodoroRunning: StateFlow<Boolean> = _isPomodoroRunning
    private val _pomodoroSessionsDone = MutableStateFlow(0)
    val pomodoroSessionsDone: StateFlow<Int> = _pomodoroSessionsDone
    private val _pomodoroMode = MutableStateFlow("WORK")
    val pomodoroModeState: StateFlow<String> = _pomodoroMode
    private val _pomodoroTotalMs = MutableStateFlow(25 * 60 * 1000L)
    val pomodoroTotalMs: StateFlow<Long> = _pomodoroTotalMs
    private val _pomodoroFinishedCount = MutableStateFlow(0)
    val pomodoroFinishedCount: StateFlow<Int> = _pomodoroFinishedCount
    private var pomodoroJob: Job? = null
    private var pomodoroEndTimestamp: Long = 0L
    private var workSessionsCount = 0

    // Pomodoro settings cache
    private var pomodoroWorkMinutes = 25
    private var pomodoroShortBreakMinutes = 5
    private var pomodoroLongBreakMinutes = 15

    // Alarm State
    private var mediaPlayer: MediaPlayer? = null
    private var volumeJob: Job? = null
    private var isTimerGradualVolume = false
    private var isPomodoroGradualVolume = false
    private var timerRingtoneUri: String? = null
    private var pomodoroRingtoneUri: String? = null
    private var isCustomRingtoneEnabled = false
    private var customRingtoneUri: String? = null

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
        ensureForeground()
        when (intent?.action) {
            ACTION_STOPWATCH_TOGGLE -> if (_isStopwatchRunning.value) pauseStopwatch() else startStopwatch()
            ACTION_STOPWATCH_STOP -> resetStopwatch()
            ACTION_TIMER_TOGGLE -> if (_isTimerRunning.value) pauseTimer() else startTimer(_timerRemaining.value)
            ACTION_TIMER_STOP -> resetTimer()
            ACTION_POMODORO_TOGGLE -> {
                if (_isPomodoroRunning.value) pausePomodoro()
                else startPomodoro(_pomodoroRemaining.value, _pomodoroMode.value)
            }
            ACTION_POMODORO_STOP, ACTION_POMODORO_RESET -> resetPomodoro()
            ACTION_POMODORO_SKIP -> skipPomodoro()
            ACTION_TODO_STOP -> stopTodoSession()
            ACTION_STOP_ALARM -> stopAlarm()
            ACTION_DISMISS_ALARM -> {
                stopAlarm()
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
                settingsRepository.timerGradualVolume,
                settingsRepository.pomodoroGradualVolume,
                settingsRepository.ringtoneUri,
                settingsRepository.pomodoroRingtoneUri,
                combine(settingsRepository.customRingtoneEnabled, settingsRepository.customRingtoneUri) { a, b -> a to b }
            ) { values -> 
                val tg = values[0] as Boolean
                val pg = values[1] as Boolean
                val tr = values[2] as String?
                val pr = values[3] as String?
                val custom = values[4] as Pair<Boolean, String?>
                
                isTimerGradualVolume = tg
                isPomodoroGradualVolume = pg
                timerRingtoneUri = tr
                pomodoroRingtoneUri = pr
                isCustomRingtoneEnabled = custom.first
                customRingtoneUri = custom.second
            }.collect {}
        }

        serviceScope.launch {
            combine(
                settingsRepository.notificationsEnabled,
                settingsRepository.timerNotifications,
                settingsRepository.pomodoroNotifications
            ) { global, timer, pomodoro -> Triple(global, timer, pomodoro) }.collect { (global, timer, pomodoro) ->
                isGlobalNotificationsEnabled = global
                isTimerNotificationsEnabled = timer
                isPomodoroNotificationsEnabled = pomodoro
                refreshNotifications()
            }
        }

        serviceScope.launch {
            combine(
                _isPomodoroRunning,
                _pomodoroMode,
                _pomodoroSessionsDone,
                _pomodoroRemaining
            ) { running, mode, done, remaining ->
                running to Triple(mode, done, remaining)
            }.collect { _ ->
                pushPomodoroWidgetState()
            }
        }

        serviceScope.launch {
            combine(
                settingsRepository.pomodoroWorkMinutes,
                settingsRepository.pomodoroShortBreakMinutes,
                settingsRepository.pomodoroLongBreakMinutes
            ) { work, short, long -> Triple(work, short, long) }.collect { (work, short, long) ->
                pomodoroWorkMinutes = work
                pomodoroShortBreakMinutes = short
                pomodoroLongBreakMinutes = long
                
                // Update totalMs if not running
                if (!_isPomodoroRunning.value) {
                    _pomodoroTotalMs.value = durationForMode(_pomodoroMode.value)
                    _pomodoroRemaining.value = _pomodoroTotalMs.value
                    pushPomodoroWidgetState()
                }
            }
        }
    }

    private fun refreshNotifications() {
        if (_isStopwatchRunning.value) updateStopwatchNotification()
        if (_isTimerRunning.value) updateTimerNotification()
        if (_isPomodoroRunning.value) updatePomodoroNotification()
        if (_isTodoSessionActive.value) updateTodoNotification()
        
        // Remove notifications if disabled
        if (!isGlobalNotificationsEnabled) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NotificationHelper.ID_STOPWATCH)
            manager.cancel(NotificationHelper.ID_TIMER)
            manager.cancel(NotificationHelper.ID_POMODORO)
            manager.cancel(NotificationHelper.ID_TODO)
            ensureForeground()
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            if (!isTimerNotificationsEnabled) manager.cancel(NotificationHelper.ID_TIMER)
            if (!isPomodoroNotificationsEnabled) {
                manager.cancel(NotificationHelper.ID_POMODORO)
            } else if (_isPomodoroRunning.value || (_pomodoroRemaining.value < _pomodoroTotalMs.value)) {
                updatePomodoroNotification()
            } else {
                manager.cancel(NotificationHelper.ID_POMODORO)
            }
            ensureForeground()
        }
    }

    private fun ensureForeground() {
        val notif = when {
            _isStopwatchRunning.value -> createStopwatchNotification()
            _isTimerRunning.value -> createTimerNotification()
            _isPomodoroRunning.value -> createPomodoroNotification()
            _isTodoSessionActive.value -> createTodoNotification()
            isPomodoroNotificationsEnabled && (_isPomodoroRunning.value || (_pomodoroRemaining.value < _pomodoroTotalMs.value)) -> createPomodoroNotification()
            else -> createGenericNotification()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NotificationHelper.ID_FOREGROUND_SERVICE, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NotificationHelper.ID_FOREGROUND_SERVICE, notif)
        }
    }

    private fun createGenericNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 3000, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
            .setContentTitle("Toolz is running")
            .setContentText("Active background services")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
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
        ensureForeground()
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
        ensureForeground()
    }

    fun pauseTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        ensureForeground()
    }

    fun resetTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        _timerRemaining.value = 0L
        _timerInitial.value = 0L
        ensureForeground()
    }

    fun setTimerInitial(millis: Long) {
        if (_isTimerRunning.value) return
        _timerInitial.value = millis
        _timerRemaining.value = millis
    }

    private fun onTimerFinished() {
        _isTimerRunning.value = false
        if (isTimerNotificationsEnabled) {
            startAlarm(timerRingtoneUri, isTimerGradualVolume)
            showAlarmNotification("Timer", "Time is up!", NotificationHelper.ID_TIMER_ALARM, Screen.Timer.route)
        }
        _isTimerRinging.value = true
        ensureForeground()
    }

    // --- Pomodoro Logic ---
    fun startPomodoro(durationMillis: Long, mode: String) {
        val actualDuration = if (durationMillis <= 0) {
            durationForMode(mode)
        } else durationMillis

        _pomodoroRemaining.value = actualDuration
        _pomodoroMode.value = mode
        _pomodoroTotalMs.value = durationForMode(mode)
        _isPomodoroRunning.value = true
        pomodoroEndTimestamp = SystemClock.elapsedRealtime() + actualDuration
        pomodoroJob?.cancel()
        pomodoroJob = serviceScope.launch {
            while (_pomodoroRemaining.value > 0 && _isPomodoroRunning.value) {
                _pomodoroRemaining.value = (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                // Removed redundant pushPomodoroWidgetState() from here as it's handled reactively or by Chronometer
                delay(1000)
            }
            if (_pomodoroRemaining.value == 0L && _isPomodoroRunning.value) {
                _isPomodoroRunning.value = false
                onPomodoroFinished()
            }
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        ensureForeground()
    }

    private fun onPomodoroFinished() {
        val completedMode = _pomodoroMode.value
        if (completedMode == "WORK") {
            workSessionsCount++
            serviceScope.launch {
                settingsRepository.setPomodoroSessionsCompleted(_pomodoroSessionsDone.value + 1)
            }
        }
        
        val title   = if (completedMode == "WORK") "Work Session Finished" else "Break Finished"
        val message = if (completedMode == "WORK") "Time to take a break! 🌱" else "Ready to focus? 🔥"
        startAlarm(pomodoroRingtoneUri, isPomodoroGradualVolume)
        showAlarmNotification(title, message, NotificationHelper.ID_POMODORO_ALARM, Screen.Pomodoro.route)
        vibrateFinish()
        _pomodoroFinishedCount.value++
        
        // Cycle mode automatically
        cyclePomodoroMode()
        
        serviceScope.launch { pushPomodoroWidgetState() }
        updatePomodoroNotification("Session finished!")
    }

    private fun cyclePomodoroMode() {
        val nextMode = when {
            _pomodoroMode.value == "WORK" && workSessionsCount % 4 == 0 -> "LONG_BREAK"
            _pomodoroMode.value == "WORK" -> "SHORT_BREAK"
            else -> "WORK"
        }
        _pomodoroMode.value = nextMode
        _pomodoroTotalMs.value = durationForMode(nextMode)
        _pomodoroRemaining.value = _pomodoroTotalMs.value
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
        _pomodoroRemaining.value = durationForMode(_pomodoroMode.value)
        _pomodoroTotalMs.value = _pomodoroRemaining.value
        serviceScope.launch { pushPomodoroWidgetState() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NotificationHelper.ID_POMODORO)
    }

    fun resetPomodoroGoal() {
        serviceScope.launch {
            settingsRepository.setPomodoroSessionsCompleted(0)
        }
    }

    /** Skip current Pomodoro session — treated as finished without alarm. */
    fun skipPomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        
        if (_pomodoroMode.value == "WORK") {
            workSessionsCount++
            serviceScope.launch {
                settingsRepository.setPomodoroSessionsCompleted(_pomodoroSessionsDone.value + 1)
            }
        }
        
        cyclePomodoroMode()
        
        serviceScope.launch { pushPomodoroWidgetState() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NotificationHelper.ID_POMODORO)
    }

    fun setPomodoroMode(mode: String) {
        if (_isPomodoroRunning.value) return
        _pomodoroMode.value = mode
        _pomodoroTotalMs.value = durationForMode(mode)
        _pomodoroRemaining.value = _pomodoroTotalMs.value
        serviceScope.launch { pushPomodoroWidgetState() }
    }

    private fun durationForMode(mode: String): Long = when (mode) {
        "SHORT_BREAK" -> pomodoroShortBreakMinutes * 60 * 1000L
        "LONG_BREAK" -> pomodoroLongBreakMinutes * 60 * 1000L
        else -> pomodoroWorkMinutes * 60 * 1000L
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
        ensureForeground()
    }

    fun stopTodoSession() {
        _isTodoSessionActive.value = false
        todoJob?.cancel()
        _todoSessionTime.value = 0L
        _todoTaskId.value = null
        _todoTaskTitle.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // --- Alarm Logic ---
    private fun startAlarm(uriString: String?, gradual: Boolean) {
        stopAlarm()
        
        val finalUriString = if (isCustomRingtoneEnabled && !customRingtoneUri.isNullOrBlank()) {
            customRingtoneUri
        } else {
            uriString
        }

        val uri = finalUriString?.let { android.net.Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@ToolService, uri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            prepare()
            if (gradual) {
                setVolume(0f, 0f)
                start()
                volumeJob = serviceScope.launch {
                    var volume = 0f
                    while (volume < 1f) {
                        delay(500)
                        volume += 0.05f
                        setVolume(volume, volume)
                    }
                }
            } else {
                start()
            }
        }
    }

    fun stopAlarm() {
        _isTimerRinging.value = false
        volumeJob?.cancel()
        volumeJob = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(NotificationHelper.ID_TIMER_ALARM)
        ensureForeground()
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
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPI)
            .setSound(null) // Handled by MediaPlayer

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, builder.build())
    }

    private fun createStopwatchNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Stopwatch.route) }
        val pendingIntent = PendingIntent.getActivity(this, 3001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

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
        val pendingIntent = PendingIntent.getActivity(this, 3002, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

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
        val intent = Intent(this, MainActivity::class.java).apply { 
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Pomodoro.route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 3003, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_TOGGLE }
        val togglePI = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val skipIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_SKIP }
        val skipPI = PendingIntent.getService(this, 32, skipIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_POMODORO_STOP }
        val stopPI = PendingIntent.getService(this, 31, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val modeLabel = when (_pomodoroMode.value) {
            "WORK" -> "Focus Session"
            "SHORT_BREAK" -> "Short Break"
            "LONG_BREAK" -> "Long Break"
            else -> "Pomodoro"
        }

        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
            .setContentTitle(modeLabel)
            .setSmallIcon(if (_pomodoroMode.value == "WORK") R.drawable.ic_launcher_foreground else R.drawable.ic_coffee)
            .setOngoing(_isPomodoroRunning.value)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                if (_isPomodoroRunning.value) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                if (_isPomodoroRunning.value) "Pause" else "Resume",
                togglePI
            )
            .addAction(R.drawable.ic_widget_next, "Skip", skipPI)
            .addAction(R.drawable.ic_notif_close, "Stop", stopPI)

        val totalMs = _pomodoroTotalMs.value
        val remainingMs = _pomodoroRemaining.value
        
        if (_isPomodoroRunning.value) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() + remainingMs)
            builder.setChronometerCountDown(true)
            builder.setContentText("Focusing... • ${formatTime(remainingMs)} left")
            builder.setProgress(totalMs.toInt(), (totalMs - remainingMs).toInt(), false)
        } else {
            builder.setContentText(text ?: "Paused • ${formatTime(remainingMs)} left")
            builder.setProgress(totalMs.toInt(), (totalMs - remainingMs).toInt(), false)
        }
        return builder.build()
    }

    private fun updatePomodoroNotification(text: String? = null) {
        if (!isGlobalNotificationsEnabled || !isPomodoroNotificationsEnabled) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.ID_POMODORO, createPomodoroNotification(text))
    }

    private fun createTodoNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Todo.route) }
        val pendingIntent = PendingIntent.getActivity(this, 3004, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

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
            widgetUpdateManager.updatePomodoroWidget(
                mode = _pomodoroMode.value,
                remainingMs = _pomodoroRemaining.value.toFloat(),
                totalMs = _pomodoroTotalMs.value.toFloat(),
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
        const val ACTION_STOP_ALARM      = "com.frerox.toolz.STOP_ALARM"
        
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
