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
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
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
    private var isBackgroundNotificationsEnabled = true

    // Stopwatch State
    // Single truth for laps lives here (S-P0-02/S-P1-02): the ViewModel only mirrors
    // this flow. Time math stays monotonic (elapsedRealtime()-base); never wall-clock.
    // No WakeLock is acquired for stopwatch — the UI uses view.keepScreenOn only.
    private val _stopwatchTime = MutableStateFlow(0L)
    val stopwatchTime: StateFlow<Long> = _stopwatchTime
    private val _isStopwatchRunning = MutableStateFlow(false)
    val isStopwatchRunning: StateFlow<Boolean> = _isStopwatchRunning
    private val _stopwatchLaps = MutableStateFlow<List<Long>>(emptyList())
    val stopwatchLaps: StateFlow<List<Long>> = _stopwatchLaps
    /** True when state was restored after a reboot (elapsedRealtime reset) — UI snackbars once. */
    private val _stopwatchRestoredAfterReboot = MutableStateFlow(false)
    val stopwatchRestoredAfterReboot: StateFlow<Boolean> = _stopwatchRestoredAfterReboot
    private var stopwatchJob: Job? = null
    private var stopwatchBase: Long = 0L
    private var stopwatchShowMsCached = true
    private var lastStopwatchLapRealtime: Long = 0L
    private var stopwatchPersistTicks = 0
    private var timeChangeReceiver: BroadcastReceiver? = null

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

    // Timer survival (T-P0-01): single source is timerEndTimestamp (elapsedRealtime).
    // Persisted atomically: endElapsed + initial + running + repeat. Never persist remaining alone.
    private var timerRepeatEnabled: Boolean = false
    // Additive separate player for timer (shared mediaPlayer stays for Pomodoro — do not change
    // shared signatures without Pomodoro agent). Timer paths use timerMediaPlayer.
    private var timerMediaPlayer: MediaPlayer? = null
    private var timerVolumeJob: Job? = null
    private val timerPlayerMutex = Mutex()
    private var timerRingStopJob: Job? = null
    private var timerWakeLock: PowerManager.WakeLock? = null
    private var timerAudioFocusRequest: AudioFocusRequest? = null

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
    // P-P0-01: NO in-memory workSessionsCount — cadence derives purely from persisted
    // _pomodoroSessionsDone via nextModeAfterWork(). Never reintroduce a second counter.

    // Pomodoro settings cache — DataStore is truth, these are read mirrors.
    private var pomodoroWorkMinutes = 25
    private var pomodoroShortBreakMinutes = 5
    private var pomodoroLongBreakMinutes = 15
    private var pomodoroSessionsGoalCached = 8
    private var pomodoroAutoStartCached = false
    private var pomodoroWakeLock: PowerManager.WakeLock? = null
    private var pomodoroAudioFocusRequest: AudioFocusRequest? = null
    private val pomodoroPlayerMutex = Mutex()
    private var pomodoroRingStopJob: Job? = null

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
            ACTION_TIMER_TOGGLE -> {
                if (_isTimerRunning.value) {
                    pauseTimer()
                } else {
                    val remaining = _timerRemaining.value
                    if (remaining <= 0L) {
                        // Dead-button guard (T-P1-02): never silently drop — notify user.
                        showTimerZeroError()
                    } else {
                        startTimer(remaining)
                    }
                }
            }
            ACTION_TIMER_STOP -> resetTimer()
            ACTION_TIMER_FINISH -> onTimerWatchdogFired()
            ACTION_TIMER_SNOOZE -> {
                val millis = intent.getLongExtra(EXTRA_SNOOZE_MILLIS, 60_000L).coerceIn(10_000L, 30 * 60_000L)
                snoozeTimer(millis)
            }
            ACTION_POMODORO_TOGGLE -> {
                if (_isPomodoroRunning.value) pausePomodoro()
                else startPomodoro(_pomodoroRemaining.value, _pomodoroMode.value)
            }
            ACTION_POMODORO_STOP, ACTION_POMODORO_RESET -> resetPomodoro()
            ACTION_POMODORO_SKIP -> skipPomodoro()
            ACTION_POMODORO_FINISH -> onPomodoroWatchdogFired()
            ACTION_TODO_STOP -> stopTodoSession()
            ACTION_STOP_ALARM -> stopAlarm()
            ACTION_DISMISS_ALARM -> {
                val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                // Timer dismiss == reset (clear initial unless repeat). Never touch Pomodoro semantics.
                if (notificationId == NotificationHelper.ID_TIMER_ALARM) {
                    dismissTimerAlarm()
                } else {
                    stopAlarm()
                }
                if (notificationId != -1) {
                    try {
                        val manager = getSystemService(NotificationManager::class.java)
                        manager.cancel(notificationId)
                    } catch (_: Exception) {}
                }
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Persist timer + pomodoro so swipe-away/process-death can resume.
        serviceScope.launch(Dispatchers.IO) {
            try { persistTimerState() } catch (_: Exception) {}
            try { persistPomodoroState() } catch (_: Exception) {}
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createAllChannels(this)

        // Timer repeat cache (for dismiss semantics) — additive, never renames existing keys.
        serviceScope.launch(Dispatchers.IO) {
            try {
                timerRepeatEnabled = settingsRepository.timerRepeat.first()
            } catch (_: Exception) {}
        }

        // Restore persisted timer (elapsedRealtime, never wall-clock).
        serviceScope.launch(Dispatchers.IO) {
            try {
                restoreTimerState()
            } catch (_: Exception) {}
        }
        
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
                settingsRepository.pomodoroNotifications,
                settingsRepository.backgroundNotificationsEnabled
            ) { global: Boolean, timer: Boolean, pomodoro: Boolean, background: Boolean ->
                listOf(global, timer, pomodoro, background)
            }.collect { flags ->
                isGlobalNotificationsEnabled = flags[0]
                isTimerNotificationsEnabled = flags[1]
                isPomodoroNotificationsEnabled = flags[2]
                isBackgroundNotificationsEnabled = flags[3]
                refreshNotifications()
            }
        }

        serviceScope.launch {
            combine(
                _isPomodoroRunning,
                _pomodoroMode,
                _pomodoroSessionsDone
            ) { running, mode, done ->
                Triple(running, mode, done)
            }.collect { _ ->
                // P-P1-03: NEVER push per-tick — remaining excluded from key on purpose.
                // Widget ticks locally via chronometer; push only on running/mode/done.
                pushPomodoroWidgetState()
            }
        }

        serviceScope.launch {
            combine(
                settingsRepository.pomodoroWorkMinutes,
                settingsRepository.pomodoroShortBreakMinutes,
                settingsRepository.pomodoroLongBreakMinutes
            ) { work, short, long -> Triple(work, short, long) }.collect { (work, short, long) ->
                pomodoroWorkMinutes = work.coerceIn(1, 60)
                pomodoroShortBreakMinutes = short.coerceIn(1, 15)
                pomodoroLongBreakMinutes = long.coerceIn(5, 45)

                // Update totalMs if not running
                if (!_isPomodoroRunning.value) {
                    _pomodoroTotalMs.value = durationForMode(_pomodoroMode.value)
                    _pomodoroRemaining.value = _pomodoroTotalMs.value
                    pushPomodoroWidgetState()
                }
            }
        }

        // P-P0-02: DataStore is truth for sessions/goal. Load synchronously at boot
        // (first() before collect) so we never init 0 and overwrite real value.
        serviceScope.launch(Dispatchers.IO) {
            try {
                _pomodoroSessionsDone.value = settingsRepository.pomodoroSessionsCompleted.first().coerceAtLeast(0)
            } catch (_: Exception) {}
            try {
                settingsRepository.pomodoroSessionsCompleted.collect { stored ->
                    _pomodoroSessionsDone.value = stored.coerceAtLeast(0)
                }
            } catch (_: Exception) {}
        }
        serviceScope.launch {
            try {
                pomodoroSessionsGoalCached = settingsRepository.pomodoroSessionsGoal.first().coerceIn(1, 12)
            } catch (_: Exception) {}
            try {
                settingsRepository.pomodoroSessionsGoal.collect { goal ->
                    pomodoroSessionsGoalCached = goal.coerceIn(1, 12)
                    pushPomodoroWidgetState()
                }
            } catch (_: Exception) {}
        }
        serviceScope.launch {
            try {
                pomodoroAutoStartCached = settingsRepository.pomodoroAutoStart.first()
            } catch (_: Exception) {}
            try {
                settingsRepository.pomodoroAutoStart.collect { pomodoroAutoStartCached = it }
            } catch (_: Exception) {}
        }
        // P-P0-03: restore persisted run (kill/reboot survival). Elapsed math, never wall end.
        serviceScope.launch(Dispatchers.IO) {
            try {
                restorePomodoroState()
            } catch (_: Exception) {}
        }

        // Stopwatch: adaptive ticker cadence follows the ms-display setting (S-P1-01).
        serviceScope.launch {
            settingsRepository.stopwatchShowMs.collect { stopwatchShowMsCached = it }
        }
        // Stopwatch: restore persisted session (kill/reboot survival, S-P0-01).
        serviceScope.launch { restoreStopwatchState() }
        // Stopwatch: wall-clock/NTP/tz shifts move the chronometer `when`
        // (S-P2-02) — re-issue the notification so it stays correct.
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (_isStopwatchRunning.value || _stopwatchTime.value > 0L) {
                        updateStopwatchNotification()
                    }
                }
            }
            // ACTION_TIME_CHANGED/TIMEZONE_CHANGED are protected broadcasts:
            // manifest-declared receivers never get them, so register at runtime.
            registerReceiver(receiver, filter)
            timeChangeReceiver = receiver
        } catch (_: Exception) {}
    }

    private fun refreshNotifications() {
        val manager = getSystemService(NotificationManager::class.java)
        // T-P0-02: Never demote FGS while timer running/ringing even if
        // backgroundNotificationsEnabled=false. That kills reliability on Android 12+.
        // Disabling background notifs while timer runs breaks reliability — VM shows in-app warning.
        if (_isTimerRunning.value || _isTimerRinging.value) {
            updateTimerNotification()
            ensureForeground()
            return
        }
        // Background notifications master switch: hides EVERYTHING related to
        // background activity (ongoing timer/stopwatch/pomodoro/todo + generic).
        // Alarms (timer finished) still fire — they are alerts, not background status.
        if (!isBackgroundNotificationsEnabled) {
            manager.cancel(NotificationHelper.ID_STOPWATCH)
            manager.cancel(NotificationHelper.ID_TIMER)
            manager.cancel(NotificationHelper.ID_POMODORO)
            manager.cancel(NotificationHelper.ID_TODO)
            hideForeground()
            return
        }
        if (_isStopwatchRunning.value) updateStopwatchNotification()
        if (_isTimerRunning.value) updateTimerNotification()
        if (_isPomodoroRunning.value) updatePomodoroNotification()
        if (_isTodoSessionActive.value) updateTodoNotification()
        
        // Remove notifications if disabled
        if (!isGlobalNotificationsEnabled) {
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
        // T-P0-02: timer running/ringing requires ongoing notification — never demote.
        if (_isTimerRunning.value || _isTimerRinging.value) {
            val notif = if (_isTimerRinging.value) {
                // Ringing keeps FGS with the ongoing timer notification (alert posted separately).
                createTimerNotification("Time is up!")
            } else {
                createTimerNotification()
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NotificationHelper.ID_FOREGROUND_SERVICE, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NotificationHelper.ID_FOREGROUND_SERVICE, notif)
                }
            } catch (_: Exception) {}
            return
        }
        // Background toggle OFF → satisfy the FGS start requirement, then demote
        // so no "Toolz is running" / ongoing notification stays visible.
        if (!isBackgroundNotificationsEnabled) {
            hideForeground()
            return
        }
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

    /**
     * Hides all background-activity notifications while keeping the service alive.
     * Calls startForeground first to satisfy the FGS-start timeout, then demotes
     * to background and cancels every ongoing notification.
     * T-P0-02: NEVER demote while timer running/ringing.
     */
    private fun hideForeground() {
        if (_isTimerRunning.value || _isTimerRinging.value) {
            ensureForeground()
            return
        }
        try {
            val silent = createGenericNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NotificationHelper.ID_FOREGROUND_SERVICE, silent, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NotificationHelper.ID_FOREGROUND_SERVICE, silent)
            }
        } catch (_: Exception) {}
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NotificationHelper.ID_FOREGROUND_SERVICE)
            manager.cancel(NotificationHelper.ID_STOPWATCH)
            manager.cancel(NotificationHelper.ID_TIMER)
            manager.cancel(NotificationHelper.ID_POMODORO)
            manager.cancel(NotificationHelper.ID_TODO)
        } catch (_: Exception) {}
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
    // Ticker runs on Dispatchers.Default (S-P1-01), never Main. Cadence is adaptive:
    // ~30ms while ms digits are shown, ~250ms otherwise (battery + recomposition).
    fun startStopwatch() {
        if (_isStopwatchRunning.value) return
        _isStopwatchRunning.value = true
        stopwatchBase = SystemClock.elapsedRealtime() - _stopwatchTime.value
        stopwatchPersistTicks = 0
        stopwatchJob?.cancel()
        stopwatchJob = serviceScope.launch(Dispatchers.Default) {
            while (_isStopwatchRunning.value) {
                _stopwatchTime.value = SystemClock.elapsedRealtime() - stopwatchBase
                // Throttled persist so a reboot loses seconds, not the whole session.
                if (++stopwatchPersistTicks % 160 == 0) persistStopwatchState()
                delay(if (stopwatchShowMsCached) 30L else 250L)
            }
        }
        persistStopwatchState()
        // Fix dual-ID desync (S-P0-02): both start AND pause refresh FGS + ID_STOPWATCH.
        ensureForeground()
        updateStopwatchNotification()
    }

    fun pauseStopwatch() {
        val wasRunning = _isStopwatchRunning.value
        // Snapshot the final tick BEFORE cancelling so pause loses <10ms (S-P1-01).
        if (wasRunning) {
            _stopwatchTime.value =
                (SystemClock.elapsedRealtime() - stopwatchBase).coerceAtLeast(0L)
        }
        _isStopwatchRunning.value = false
        stopwatchJob?.cancel()
        persistStopwatchState()
        ensureForeground()
        updateStopwatchNotification()
    }

    fun resetStopwatch() {
        _isStopwatchRunning.value = false
        stopwatchJob?.cancel()
        _stopwatchTime.value = 0L
        _stopwatchLaps.value = emptyList()
        lastStopwatchLapRealtime = 0L
        stopwatchBase = 0L
        _stopwatchRestoredAfterReboot.value = false
        serviceScope.launch { runCatching { settingsRepository.clearStopwatchState() } }
        // Cancel ONLY our notification (S-P0-02). Never touch ID_TIMER/ID_POMODORO here,
        // and never stopForeground unconditionally — that would kill co-running tools.
        try {
            getSystemService(NotificationManager::class.java).cancel(NotificationHelper.ID_STOPWATCH)
        } catch (_: Exception) {}
        ensureForeground()
        stopForegroundIfIdle()
    }

    /**
     * Record a lap. Single truth lives in the service (S-P1-02): the ViewModel only
     * mirrors [stopwatchLaps]. Laps are capped at [SettingsRepository.STOPWATCH_MAX_LAPS]
     * (extra laps are blocked, returns false); requires running + 500ms debounce.
     * @return true if the lap was recorded.
     */
    fun addStopwatchLap(): Boolean {
        if (!_isStopwatchRunning.value) return false
        val total = (SystemClock.elapsedRealtime() - stopwatchBase).coerceAtLeast(0L)
        if (total <= 0L) return false
        val nowRealtime = SystemClock.elapsedRealtime()
        if (nowRealtime - lastStopwatchLapRealtime < 500L) return false
        val current = _stopwatchLaps.value
        if (current.size >= SettingsRepository.STOPWATCH_MAX_LAPS) return false
        if (current.firstOrNull() == total) return false
        lastStopwatchLapRealtime = nowRealtime
        _stopwatchTime.value = total
        _stopwatchLaps.value = listOf(total) + current
        persistStopwatchState()
        return true
    }

    /** Cleared by the UI after showing the "session restored" snackbar once. */
    fun consumeStopwatchRestoreFlag() {
        _stopwatchRestoredAfterReboot.value = false
    }

    private fun persistStopwatchState() {
        val base = stopwatchBase
        val accumulated = _stopwatchTime.value
        val running = _isStopwatchRunning.value
        val laps = _stopwatchLaps.value
        serviceScope.launch {
            runCatching {
                settingsRepository.saveStopwatchState(
                    baseElapsed = base,
                    accumulated = accumulated,
                    running = running,
                    lapsJson = settingsRepository.serializeStopwatchLaps(laps),
                )
            }
        }
    }

    /**
     * Restore persisted session (S-P0-01). Kill mid-run resumes exactly via base math
     * (elapsedRealtime is boot-relative, unaffected by process death). After a reboot
     * elapsedRealtime resets, so a computed negative time clamps to [accumulated] and
     * raises [stopwatchRestoredAfterReboot] for the UI snackbar.
     */
    private suspend fun restoreStopwatchState() {
        val base: Long
        val accumulated: Long
        val running: Boolean
        val laps: List<Long>
        try {
            base = settingsRepository.stopwatchBaseElapsed.first()
            accumulated = settingsRepository.stopwatchAccumulated.first().coerceAtLeast(0L)
            running = settingsRepository.stopwatchRunning.first()
            laps = settingsRepository.parseStopwatchLaps(
                settingsRepository.stopwatchLapsJson.first()
            ).take(SettingsRepository.STOPWATCH_MAX_LAPS)
        } catch (_: Exception) {
            return
        }
        _stopwatchLaps.value = laps
        if (!running && accumulated <= 0L && laps.isEmpty()) return
        if (!running) {
            // Paused session: restore silently, no ticker, no snackbar.
            stopwatchBase = base
            _stopwatchTime.value = accumulated
            _isStopwatchRunning.value = false
            return
        }
        val now = SystemClock.elapsedRealtime()
        val computed = now - base
        if (base == 0L || computed < 0L) {
            // Reboot (or corrupt base): clamp to accumulated, keep running from there.
            stopwatchBase = now - accumulated
            _stopwatchTime.value = accumulated
            _isStopwatchRunning.value = true
            _stopwatchRestoredAfterReboot.value = true
        } else {
            stopwatchBase = base
            _stopwatchTime.value = computed.coerceAtLeast(0L)
            _isStopwatchRunning.value = true
        }
        stopwatchJob?.cancel()
        stopwatchJob = serviceScope.launch(Dispatchers.Default) {
            while (_isStopwatchRunning.value) {
                _stopwatchTime.value = SystemClock.elapsedRealtime() - stopwatchBase
                if (++stopwatchPersistTicks % 160 == 0) persistStopwatchState()
                delay(if (stopwatchShowMsCached) 30L else 250L)
            }
        }
        try {
            ensureForeground()
            updateStopwatchNotification()
        } catch (_: Exception) {}
    }

    /**
     * Shared-file protocol: only demote out of foreground when NO tool is active
     * (no stopwatch/timer/pomodoro/todo running and no alarm ringing).
     */
    private fun stopForegroundIfIdle() {
        if (!_isStopwatchRunning.value && !_isTimerRunning.value &&
            !_isPomodoroRunning.value && !_isTodoSessionActive.value &&
            !_isTimerRinging.value
        ) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        }
    }

    // --- Timer Logic (T-P0-01 survival: elapsedRealtime + DataStore + watchdog) ---
    fun startTimer(durationMillis: Long, initialDuration: Long? = null) {
        if (durationMillis <= 0) {
            showTimerZeroError()
            return
        }
        _timerRemaining.value = durationMillis
        if (initialDuration != null && initialDuration > 0) {
            _timerInitial.value = initialDuration
        } else if (_timerInitial.value <= 0L) {
            _timerInitial.value = durationMillis
        }
        _isTimerRunning.value = true
        _isTimerRinging.value = false
        timerEndTimestamp = SystemClock.elapsedRealtime() + durationMillis
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_timerRemaining.value > 0 && _isTimerRunning.value) {
                _timerRemaining.value = (timerEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                delay(250)
            }
            if (_timerRemaining.value == 0L && _isTimerRunning.value) {
                _isTimerRunning.value = false
                onTimerFinished()
            }
        }
        acquireTimerWakeLock(durationMillis + 60_000L)
        scheduleTimerWatchdog(timerEndTimestamp)
        serviceScope.launch(Dispatchers.IO) {
            try {
                persistTimerState()
                settingsRepository.setRepeatLastTimer(_timerInitial.value)
            } catch (_: Exception) {}
        }
        ensureForeground()
        updateTimerNotification()
    }

    fun pauseTimer() {
        // Capture remaining with elapsedRealtime math (drift <500ms) before cancelling.
        try {
            if (_isTimerRunning.value) {
                _timerRemaining.value = (timerEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            }
        } catch (_: Exception) {}
        _isTimerRunning.value = false
        timerJob?.cancel()
        cancelTimerWatchdog()
        releaseTimerWakeLock()
        serviceScope.launch(Dispatchers.IO) {
            try { persistTimerState() } catch (_: Exception) {}
        }
        ensureForeground()
        updateTimerNotification()
    }

    fun resetTimer() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        _timerRemaining.value = 0L
        _timerInitial.value = 0L
        timerEndTimestamp = 0L
        cancelTimerWatchdog()
        releaseTimerWakeLock()
        stopTimerAlarmOnly()
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NotificationHelper.ID_TIMER)
        } catch (_: Exception) {}
        serviceScope.launch(Dispatchers.IO) {
            try { persistTimerState() } catch (_: Exception) {}
        }
        // T-P0-02: stopForeground only when all idle — never demote timer while ringing (already stopped).
        maybeReleaseForegroundIfIdle()
    }

    fun setTimerInitial(millis: Long) {
        if (_isTimerRunning.value) return
        val safe = millis.coerceAtLeast(0L)
        _timerInitial.value = safe
        _timerRemaining.value = safe
        timerEndTimestamp = 0L
    }

    /** Additive: update remaining while keeping initial immutable (paused addTime). */
    fun setTimerRemainingPreservingInitial(remainingMs: Long) {
        if (_isTimerRunning.value) return
        _timerRemaining.value = remainingMs.coerceAtLeast(0L)
        serviceScope.launch(Dispatchers.IO) {
            try { persistTimerState() } catch (_: Exception) {}
        }
    }

    /** Dismiss == reset (clear initial unless repeat). Timer-only stop, never touches Pomodoro. */
    fun dismissTimerAlarm() {
        stopTimerAlarmOnly()
        _isTimerRinging.value = false
        _isTimerRunning.value = false
        timerJob?.cancel()
        timerRingStopJob?.cancel()
        cancelTimerWatchdog()
        releaseTimerWakeLock()
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NotificationHelper.ID_TIMER_ALARM)
            manager.cancel(NotificationHelper.ID_TIMER)
        } catch (_: Exception) {}
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (timerRepeatEnabled && _timerInitial.value > 0L) {
                    // Repeat: keep initial, stage it for next run.
                    _timerRemaining.value = _timerInitial.value
                    persistTimerState()
                } else {
                    _timerRemaining.value = 0L
                    _timerInitial.value = 0L
                    timerEndTimestamp = 0L
                    persistTimerState()
                }
            } catch (_: Exception) {}
        }
        maybeReleaseForegroundIfIdle()
    }

    fun snoozeTimer(millis: Long) {
        stopTimerAlarmOnly()
        _isTimerRinging.value = false
        _isTimerRunning.value = false
        timerRingStopJob?.cancel()
        startTimer(millis, _timerInitial.value.takeIf { it > 0L })
    }

    private fun onTimerFinished() {
        _isTimerRunning.value = false
        timerJob?.cancel()
        cancelTimerWatchdog()
        // T-P1-01: Always vibrate + post visual even if sound off (respect channel importance).
        try { vibrateFinish() } catch (_: Exception) {}
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NotificationHelper.ID_TIMER)
        } catch (_: Exception) {}
        // Sound via additive timer player with fallback; visual always posted.
        startTimerAlarm(timerRingtoneUri, isTimerGradualVolume)
        showTimerFinishedNotification()
        _isTimerRinging.value = true
        acquireTimerWakeLock(RING_TIMEOUT_MS)
        // Auto-stop ringing after 5min (T-P0-03).
        timerRingStopJob?.cancel()
        timerRingStopJob = serviceScope.launch {
            delay(RING_TIMEOUT_MS)
            try { dismissTimerAlarm() } catch (_: Exception) {}
        }
        serviceScope.launch(Dispatchers.IO) {
            try { persistTimerState() } catch (_: Exception) {}
        }
        ensureForeground()
    }

    /** Watchdog backup path (Doze defers coroutine): single source is timerEndTimestamp. */
    private fun onTimerWatchdogFired() {
        if (_isTimerRinging.value) return
        val now = SystemClock.elapsedRealtime()
        if (!_isTimerRunning.value && _timerRemaining.value <= 0L) {
            // Restore path: service was dead, watchdog fired without in-memory state.
            // Try persisted state — if end already passed, finish once.
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val end = settingsRepository.timerEndElapsed.first()
                    val initial = settingsRepository.timerInitialMs.first()
                    val wasRunning = settingsRepository.timerRunning.first()
                    val savedRemaining = try { settingsRepository.timerRemainingMs.first() } catch (_: Exception) { 0L }
                    val saveWall = try { settingsRepository.timerSaveWallMs.first() } catch (_: Exception) { 0L }
                    if (wasRunning && end > 0L) {
                        val implied = end - SystemClock.elapsedRealtime()
                        val isReboot = savedRemaining > 0L && implied > savedRemaining + 60_000L
                        if (isReboot) {
                            val wallDelta = if (saveWall > 0L) (System.currentTimeMillis() - saveWall).coerceAtLeast(0L) else 0L
                            val rem = (savedRemaining - wallDelta).coerceAtLeast(0L)
                            if (rem <= 0L) {
                                if (initial > 0L) _timerInitial.value = initial
                                _timerRemaining.value = 0L
                                _isTimerRunning.value = false
                                withContext(Dispatchers.Main) { onTimerFinished() }
                            } else {
                                _timerInitial.value = initial.takeIf { it > 0L } ?: savedRemaining
                                withContext(Dispatchers.Main) {
                                    startTimer(rem, _timerInitial.value.takeIf { it > 0L })
                                }
                            }
                        } else if (end <= SystemClock.elapsedRealtime()) {
                            if (initial > 0L) _timerInitial.value = initial
                            _timerRemaining.value = 0L
                            _isTimerRunning.value = false
                            withContext(Dispatchers.Main) { onTimerFinished() }
                        } else {
                            _timerInitial.value = initial
                            withContext(Dispatchers.Main) {
                                startTimer(end - SystemClock.elapsedRealtime(), initial.takeIf { it > 0L })
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            return
        }
        val remaining = (timerEndTimestamp - now).coerceAtLeast(0L)
        _timerRemaining.value = remaining
        if (remaining <= 0L) {
            _isTimerRunning.value = false
            onTimerFinished()
        }
    }

    private fun showTimerZeroError() {
        try {
            if (!isTimerNotificationsEnabled) return
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Timer.route)
            }
            val pi = PendingIntent.getActivity(this, 3103, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notif = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
                .setContentTitle("Timer needs a duration")
                .setContentText("Pick a duration greater than 0:00, then press Start.")
                .setSmallIcon(R.drawable.ic_shortcut_timer)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(3103, notif)
        } catch (_: Exception) {}
    }

    // ── Timer persistence + watchdog (elapsedRealtime) ──

    private suspend fun persistTimerState() {
        try {
            // Paused: endElapsed=0 + running=false (remaining held in _timerRemaining).
            // Running: endElapsed=timerEndTimestamp. Ringing: running=false (finish persisted).
            // Reboot fallback carries remaining + wall save time (elapsed resets on reboot).
            val running = _isTimerRunning.value
            val endElapsed = if (running) timerEndTimestamp else 0L
            val remaining = if (running) {
                (timerEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            } else {
                _timerRemaining.value.coerceAtLeast(0L)
            }
            settingsRepository.saveTimerState(
                endElapsed = endElapsed,
                initialMs = _timerInitial.value,
                running = running,
                repeat = timerRepeatEnabled,
                remainingMs = remaining,
                saveWallMs = System.currentTimeMillis(),
            )
        } catch (_: Exception) {}
    }

    private suspend fun restoreTimerState() {
        val endElapsed: Long
        val initial: Long
        val wasRunning: Boolean
        val repeat: Boolean
        val savedRemaining: Long
        val saveWall: Long
        try {
            endElapsed = settingsRepository.timerEndElapsed.first()
            initial = settingsRepository.timerInitialMs.first()
            wasRunning = settingsRepository.timerRunning.first()
            repeat = settingsRepository.timerRepeat.first()
            savedRemaining = try { settingsRepository.timerRemainingMs.first() } catch (_: Exception) { 0L }
            saveWall = try { settingsRepository.timerSaveWallMs.first() } catch (_: Exception) { 0L }
        } catch (_: Exception) { return }
        timerRepeatEnabled = repeat
        if (!wasRunning || endElapsed <= 0L) {
            // Restore staged initial for paused/idle continuity (additive, no running).
            if (initial > 0L && _timerInitial.value == 0L) {
                _timerInitial.value = initial
                if (_timerRemaining.value == 0L) {
                    _timerRemaining.value = savedRemaining.takeIf { it > 0L } ?: initial
                }
            }
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        // Reboot detection: elapsedRealtime resets on reboot, so end-now >> saved remaining.
        val elapsedImplied = endElapsed - nowElapsed
        val isReboot = savedRemaining > 0L && elapsedImplied > savedRemaining + 60_000L
        val remaining: Long = if (isReboot) {
            // Wall-delta fallback across reboot (end source stays elapsed; wall only for delta).
            val wallDelta = if (saveWall > 0L) (System.currentTimeMillis() - saveWall).coerceAtLeast(0L) else 0L
            (savedRemaining - wallDelta).coerceAtLeast(0L)
        } else {
            elapsedImplied.coerceAtLeast(0L)
        }
        if (remaining > 0L) {
            _timerInitial.value = initial.takeIf { it > 0L } ?: savedRemaining.takeIf { it > 0L } ?: remaining
            // Resume countdown on Main via startTimer (re-arms watchdog + wakelock).
            withContext(Dispatchers.Main) {
                try { startTimer(remaining, _timerInitial.value) } catch (_: Exception) {}
            }
        } else {
            // End already passed while dead — fire finish path once.
            _timerInitial.value = initial.takeIf { it > 0L } ?: 0L
            _timerRemaining.value = 0L
            _isTimerRunning.value = false
            withContext(Dispatchers.Main) {
                try { onTimerFinished() } catch (_: Exception) {}
            }
        }
    }

    private fun scheduleTimerWatchdog(endElapsed: Long) {
        try {
            val am = getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
                action = ACTION_TIMER_FINISH
            }
            val pi = PendingIntent.getBroadcast(
                this, REQ_TIMER_WATCHDOG, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try { am.cancel(pi) } catch (_: Exception) {}
            // Single source: endElapsed == timerEndTimestamp (elapsedRealtime).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val canExact = am.canScheduleExactAlarms()
                    if (canExact) {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                    }
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                }
            } else {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                } catch (_: Exception) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                }
            }
        } catch (_: Exception) {}
    }

    private fun cancelTimerWatchdog() {
        try {
            val am = getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(this, TimerAlarmReceiver::class.java).apply {
                action = ACTION_TIMER_FINISH
            }
            val pi = PendingIntent.getBroadcast(
                this, REQ_TIMER_WATCHDOG, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.cancel(pi)
        } catch (_: Exception) {}
    }

    // ── WakeLock with timeout (T-P0-03) ──

    private fun acquireTimerWakeLock(timeoutMs: Long) {
        try {
            if (timerWakeLock == null) {
                val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
                timerWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Toolz:TimerWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            val wl = timerWakeLock ?: return
            try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
            // Never hold without timeout.
            wl.acquire(timeoutMs.coerceIn(10_000L, 15 * 60_000L))
        } catch (_: Exception) {}
    }

    private fun releaseTimerWakeLock() {
        try {
            // Keep held while running/ringing; release only when fully idle.
            if (_isTimerRunning.value || _isTimerRinging.value) return
            timerWakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
    }

    private fun forceReleaseTimerWakeLock() {
        try { timerWakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
    }

    private fun isAnyToolActive(): Boolean {
        return _isStopwatchRunning.value || _isTimerRunning.value || _isTimerRinging.value ||
            _isPomodoroRunning.value || _isTodoSessionActive.value
    }

    /** stopForeground only when all idle (T-P0-02 / T-P1-02). */
    private fun maybeReleaseForegroundIfIdle() {
        if (isAnyToolActive()) {
            ensureForeground()
        } else {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            try {
                val manager = getSystemService(NotificationManager::class.java)
                manager.cancel(NotificationHelper.ID_TIMER)
            } catch (_: Exception) {}
        }
    }

    // ── Timer alarm audio (additive separate player, try/catch + fallback) ──

    private fun requestTimerAudioFocus(): Boolean {
        return try {
            val am = getSystemService(AudioManager::class.java) ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                timerAudioFocusRequest = req
                am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) { false }
    }

    private fun abandonTimerAudioFocus() {
        try {
            val am = getSystemService(AudioManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                timerAudioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                timerAudioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun resolveTimerAlarmUri(primary: String?): android.net.Uri {
        val candidates = listOfNotNull(
            primary?.takeIf { it.isNotBlank() }?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() },
            if (isCustomRingtoneEnabled && !customRingtoneUri.isNullOrBlank()) {
                runCatching { android.net.Uri.parse(customRingtoneUri!!) }.getOrNull()
            } else null,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        return candidates.first()
    }

    private fun startTimerAlarm(uriString: String?, gradual: Boolean) {
        serviceScope.launch(Dispatchers.IO) {
            timerPlayerMutex.withLock {
                try { stopTimerAlarmLocked() } catch (_: Exception) {}
                requestTimerAudioFocus()
                var started = false
                val tried = mutableListOf<android.net.Uri>()
                tried += resolveTimerAlarmUri(uriString)
                // Fallbacks: default alarm, notification, system default.
                runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) } .getOrNull()?.let { if (!tried.contains(it)) tried += it }
                runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }.getOrNull()?.let { if (!tried.contains(it)) tried += it }
                for (uri in tried) {
                    try {
                        val mp = MediaPlayer().apply {
                            try { setWakeMode(this@ToolService, PowerManager.PARTIAL_WAKE_LOCK) } catch (_: Exception) {}
                            setDataSource(this@ToolService, uri)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build(),
                            )
                            isLooping = true
                            setOnErrorListener { _, _, _ ->
                                serviceScope.launch(Dispatchers.IO) {
                                    timerPlayerMutex.withLock { try { stopTimerAlarmLocked() } catch (_: Exception) {} }
                                }
                                // Fallback handled by next loop iteration via fresh call.
                                true
                            }
                            prepare()
                        }
                        timerMediaPlayer = mp
                        if (gradual) {
                            mp.setVolume(0f, 0f)
                            withContext(Dispatchers.Main) { runCatching { mp.start() } }
                            started = true
                            timerVolumeJob?.cancel()
                            timerVolumeJob = serviceScope.launch(Dispatchers.IO) {
                                var volume = 0f
                                while (volume < 1f) {
                                    delay(500)
                                    volume = (volume + 0.05f).coerceIn(0f, 1f)
                                    try {
                                        timerMediaPlayer?.setVolume(volume, volume)
                                    } catch (_: IllegalStateException) { break }
                                    catch (_: Exception) { break }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) { runCatching { mp.start() } }
                            started = true
                        }
                        if (started) break
                    } catch (_: SecurityException) {
                        try { timerMediaPlayer?.release() } catch (_: Exception) {}
                        timerMediaPlayer = null
                        continue
                    } catch (_: IOException) {
                        try { timerMediaPlayer?.release() } catch (_: Exception) {}
                        timerMediaPlayer = null
                        continue
                    } catch (_: IllegalStateException) {
                        try { timerMediaPlayer?.release() } catch (_: Exception) {}
                        timerMediaPlayer = null
                        continue
                    } catch (_: Exception) {
                        try { timerMediaPlayer?.release() } catch (_: Exception) {}
                        timerMediaPlayer = null
                        continue
                    }
                }
                // Never crash service on missing ringtone — visual + vibration already fired.
            }
        }
    }

    private fun stopTimerAlarmLocked() {
        timerVolumeJob?.cancel()
        timerVolumeJob = null
        timerRingStopJob?.cancel()
        timerRingStopJob = null
        try { timerMediaPlayer?.stop() } catch (_: Exception) {}
        try { timerMediaPlayer?.release() } catch (_: Exception) {}
        timerMediaPlayer = null
        abandonTimerAudioFocus()
    }

    /** Timer-only stop — never touches shared Pomodoro player. */
    private fun stopTimerAlarmOnly() {
        serviceScope.launch(Dispatchers.IO) {
            timerPlayerMutex.withLock { try { stopTimerAlarmLocked() } catch (_: Exception) {} }
        }
        // Cancel timer alarm notification only (T-P1-02: correct IDs per caller).
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NotificationHelper.ID_TIMER_ALARM)
        } catch (_: Exception) {}
    }

    // --- Pomodoro Logic (P-P0-01/02/03 + P-P1-03 + P-P2-02) ---
    // DataStore is truth for sessions/goal/durations. Service owns auto-start + sound.
    // Widget is pushed on running/mode/done only — never per-tick.
    fun startPomodoro(durationMillis: Long, mode: String) {
        val safeMode = when (mode) {
            "SHORT_BREAK", "LONG_BREAK", "WORK" -> mode
            else -> "WORK"
        }
        val actualDuration = if (durationMillis <= 0) {
            durationForMode(safeMode)
        } else durationMillis.coerceAtLeast(1_000L)

        _pomodoroRemaining.value = actualDuration
        _pomodoroMode.value = safeMode
        _pomodoroTotalMs.value = durationForMode(safeMode).coerceAtLeast(actualDuration)
        if (_pomodoroTotalMs.value <= 0L) _pomodoroTotalMs.value = actualDuration
        _isPomodoroRunning.value = true
        pomodoroEndTimestamp = SystemClock.elapsedRealtime() + actualDuration
        pomodoroJob?.cancel()
        pomodoroJob = serviceScope.launch {
            while (_pomodoroRemaining.value > 0 && _isPomodoroRunning.value) {
                _pomodoroRemaining.value = (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                delay(1000)
            }
            if (_pomodoroRemaining.value == 0L && _isPomodoroRunning.value) {
                _isPomodoroRunning.value = false
                onPomodoroFinished()
            }
        }
        schedulePomodoroWatchdog(pomodoroEndTimestamp)
        serviceScope.launch(Dispatchers.IO) {
            try { persistPomodoroState() } catch (_: Exception) {}
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        ensureForeground()
        updatePomodoroNotification()
    }

    private fun onPomodoroFinished() {
        val completedMode = _pomodoroMode.value
        cancelPomodoroWatchdog()
        if (completedMode == "WORK") {
            // P-P0-02: increment in-memory first, then persist that exact value.
            // Never read-then-write blindly from a stale flow.
            val next = (_pomodoroSessionsDone.value + 1).coerceAtLeast(0).coerceAtMost(999)
            _pomodoroSessionsDone.value = next
            serviceScope.launch(Dispatchers.IO) {
                try { settingsRepository.setPomodoroSessionsCompleted(next) } catch (_: Exception) {}
            }
        }

        // P-P1-03: gate sound/vibration/heads-up like Timer — still cycleMode but silent
        // when global/pomodoro/background notifications are off.
        val audible = isGlobalNotificationsEnabled && isPomodoroNotificationsEnabled
        if (audible) {
            val title = if (completedMode == "WORK") "Work Session Finished" else "Break Finished"
            val message = if (completedMode == "WORK") "Time to take a break! 🌱" else "Ready to focus? 🔥"
            try { acquirePomodoroWakeLock(RING_TIMEOUT_MS) } catch (_: Exception) {}
            startAlarm(pomodoroRingtoneUri, isPomodoroGradualVolume)
            showAlarmNotification(title, message, NotificationHelper.ID_POMODORO_ALARM, Screen.Pomodoro.route)
            try { vibrateFinish() } catch (_: Exception) {}
        }
        _pomodoroFinishedCount.value++

        // P-P0-01: single phase truth — cycle from PERSISTED count.
        cyclePomodoroMode()

        serviceScope.launch(Dispatchers.IO) {
            try { settingsRepository.clearPomodoroRunState() } catch (_: Exception) {}
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        updatePomodoroNotification("Session finished!")

        // P-P0-04: Service owns auto-start. VM NEVER auto-starts from stale state.
        if (pomodoroAutoStartCached) {
            val nextMode = _pomodoroMode.value
            val nextDuration = durationForMode(nextMode)
            // Post to Main to keep StateFlow ordering deterministic.
            serviceScope.launch(Dispatchers.Main) {
                try { startPomodoro(nextDuration, nextMode) } catch (_: Exception) {}
            }
        } else {
            ensureForeground()
        }
        // Release the short finish hold once ringing path is armed; ringing stops
        // via stopAlarm() which also releases. Keep bounded by timeout regardless.
        serviceScope.launch {
            delay(15_000L)
            try { releasePomodoroWakeLockIfIdle() } catch (_: Exception) {}
        }
    }

    private fun cyclePomodoroMode() {
        // P-P0-01: ONE formula shared Service/VM/UI — derive from persisted count.
        val nextMode = nextPomodoroMode(_pomodoroMode.value, _pomodoroSessionsDone.value)
        _pomodoroMode.value = nextMode
        _pomodoroTotalMs.value = durationForMode(nextMode)
        _pomodoroRemaining.value = _pomodoroTotalMs.value
    }

    fun pausePomodoro() {
        try {
            if (_isPomodoroRunning.value) {
                _pomodoroRemaining.value = (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            }
        } catch (_: Exception) {}
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        cancelPomodoroWatchdog()
        serviceScope.launch(Dispatchers.IO) {
            try { persistPomodoroState() } catch (_: Exception) {}
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        updatePomodoroNotification("Paused")
        ensureForeground()
    }

    fun resetPomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        cancelPomodoroWatchdog()
        _pomodoroRemaining.value = durationForMode(_pomodoroMode.value)
        _pomodoroTotalMs.value = _pomodoroRemaining.value
        serviceScope.launch(Dispatchers.IO) {
            try { settingsRepository.clearPomodoroRunState() } catch (_: Exception) {}
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        // P-P1-03: never stopForeground(REMOVE) unconditionally — kills Timer/Stopwatch.
        try {
            getSystemService(NotificationManager::class.java).cancel(NotificationHelper.ID_POMODORO)
        } catch (_: Exception) {}
        ensureForeground()
        maybeReleaseForegroundIfIdle()
    }

    fun resetPomodoroGoal() {
        // Cadence resets automatically: nextModeAfterWork(0) restarts the %4 cycle.
        _pomodoroSessionsDone.value = 0
        serviceScope.launch(Dispatchers.IO) {
            try { settingsRepository.setPomodoroSessionsCompleted(0) } catch (_: Exception) {}
        }
        serviceScope.launch { pushPomodoroWidgetState() }
    }

    /**
     * Skip current phase WITHOUT counting work (P-P2-02).
     * 4 skips must never grant LONG — only real finishes advance cadence.
     */
    fun skipPomodoro() {
        _isPomodoroRunning.value = false
        pomodoroJob?.cancel()
        cancelPomodoroWatchdog()

        cyclePomodoroMode()

        serviceScope.launch(Dispatchers.IO) {
            try { settingsRepository.clearPomodoroRunState() } catch (_: Exception) {}
        }
        serviceScope.launch { pushPomodoroWidgetState() }
        // P-P1-03: never kill FGS for co-tenants.
        try {
            getSystemService(NotificationManager::class.java).cancel(NotificationHelper.ID_POMODORO)
        } catch (_: Exception) {}
        ensureForeground()
        maybeReleaseForegroundIfIdle()
    }

    fun setPomodoroMode(mode: String) {
        if (_isPomodoroRunning.value) return
        val safe = when (mode) {
            "SHORT_BREAK", "LONG_BREAK", "WORK" -> mode
            else -> "WORK"
        }
        _pomodoroMode.value = safe
        _pomodoroTotalMs.value = durationForMode(safe)
        _pomodoroRemaining.value = _pomodoroTotalMs.value
        serviceScope.launch { pushPomodoroWidgetState() }
        ensureForeground()
    }

    private fun durationForMode(mode: String): Long {
        // P-P2-05: clamp cached minutes so legacy 0 never yields duration 0
        // (setProgress(0,0) throws + instant-finish loop).
        val minutes = when (mode) {
            "SHORT_BREAK" -> pomodoroShortBreakMinutes.coerceIn(1, 15)
            "LONG_BREAK" -> pomodoroLongBreakMinutes.coerceIn(5, 45)
            else -> pomodoroWorkMinutes.coerceIn(1, 60)
        }
        return (minutes * 60 * 1000L).coerceAtLeast(60_000L)
    }

    // ── Pomodoro persistence + watchdog (elapsedRealtime, Doze-safe) ──

    private suspend fun persistPomodoroState() {
        try {
            val running = _isPomodoroRunning.value
            val endElapsed = if (running) pomodoroEndTimestamp else 0L
            val remaining = if (running) {
                (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            } else {
                _pomodoroRemaining.value.coerceAtLeast(0L)
            }
            settingsRepository.savePomodoroState(
                endElapsed = endElapsed,
                mode = _pomodoroMode.value,
                running = running,
                remainingMs = remaining,
                saveWallMs = System.currentTimeMillis(),
            )
        } catch (_: Exception) {}
    }

    private suspend fun restorePomodoroState() {
        val endElapsed: Long
        val wasRunning: Boolean
        val mode: String
        val savedRemaining: Long
        val saveWall: Long
        try {
            endElapsed = settingsRepository.pomodoroEndElapsed.first()
            wasRunning = settingsRepository.pomodoroRunning.first()
            mode = settingsRepository.pomodoroPersistedMode.first()
            savedRemaining = try { settingsRepository.pomodoroRemainingMs.first() } catch (_: Exception) { 0L }
            saveWall = try { settingsRepository.pomodoroSaveWallMs.first() } catch (_: Exception) { 0L }
        } catch (_: Exception) { return }
        val safeMode = when (mode) {
            "SHORT_BREAK", "LONG_BREAK", "WORK" -> mode
            else -> "WORK"
        }
        if (!wasRunning || endElapsed <= 0L) {
            // Restore staged mode/duration for idle continuity (no running).
            withContext(Dispatchers.Main) {
                try {
                    if (!_isPomodoroRunning.value) {
                        _pomodoroMode.value = safeMode
                        _pomodoroTotalMs.value = durationForMode(safeMode)
                        _pomodoroRemaining.value = savedRemaining.takeIf { it > 0L }
                            ?: _pomodoroTotalMs.value
                    }
                } catch (_: Exception) {}
            }
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        val implied = endElapsed - nowElapsed
        val isReboot = savedRemaining > 0L && implied > savedRemaining + 60_000L
        val remaining: Long = if (isReboot) {
            val wallDelta = if (saveWall > 0L) (System.currentTimeMillis() - saveWall).coerceAtLeast(0L) else 0L
            (savedRemaining - wallDelta).coerceAtLeast(0L)
        } else {
            implied.coerceAtLeast(0L)
        }
        if (remaining > 0L) {
            withContext(Dispatchers.Main) {
                try { startPomodoro(remaining, safeMode) } catch (_: Exception) {}
            }
        } else {
            // End passed while dead — fire finish path once on Main.
            withContext(Dispatchers.Main) {
                try {
                    _pomodoroMode.value = safeMode
                    _pomodoroRemaining.value = 0L
                    _isPomodoroRunning.value = false
                    onPomodoroFinished()
                } catch (_: Exception) {}
            }
        }
    }

    private fun schedulePomodoroWatchdog(endElapsed: Long) {
        try {
            val am = getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(this, PomodoroAlarmReceiver::class.java).apply {
                action = ACTION_POMODORO_FINISH
            }
            val pi = PendingIntent.getBroadcast(
                this, REQ_POMODORO_WATCHDOG, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try { am.cancel(pi) } catch (_: Exception) {}
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                    }
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                }
            } else {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                } catch (_: Exception) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, pi)
                }
            }
        } catch (_: Exception) {}
    }

    private fun cancelPomodoroWatchdog() {
        try {
            val am = getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(this, PomodoroAlarmReceiver::class.java).apply {
                action = ACTION_POMODORO_FINISH
            }
            val pi = PendingIntent.getBroadcast(
                this, REQ_POMODORO_WATCHDOG, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.cancel(pi)
        } catch (_: Exception) {}
    }

    /** Watchdog backup path (Doze defers coroutine): single source is pomodoroEndTimestamp. */
    private fun onPomodoroWatchdogFired() {
        if (_isPomodoroRunning.value) {
            val remaining = (pomodoroEndTimestamp - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            _pomodoroRemaining.value = remaining
            if (remaining <= 0L) {
                _isPomodoroRunning.value = false
                onPomodoroFinished()
            }
            return
        }
        // Service was dead — try persisted state; if end passed, finish once.
        if (_pomodoroRemaining.value <= 0L) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val end = settingsRepository.pomodoroEndElapsed.first()
                    val wasRunning = settingsRepository.pomodoroRunning.first()
                    val mode = try { settingsRepository.pomodoroPersistedMode.first() } catch (_: Exception) { "WORK" }
                    val savedRemaining = try { settingsRepository.pomodoroRemainingMs.first() } catch (_: Exception) { 0L }
                    val saveWall = try { settingsRepository.pomodoroSaveWallMs.first() } catch (_: Exception) { 0L }
                    if (wasRunning && end > 0L) {
                        val implied = end - SystemClock.elapsedRealtime()
                        val isReboot = savedRemaining > 0L && implied > savedRemaining + 60_000L
                        val rem = if (isReboot) {
                            val wallDelta = if (saveWall > 0L) (System.currentTimeMillis() - saveWall).coerceAtLeast(0L) else 0L
                            (savedRemaining - wallDelta).coerceAtLeast(0L)
                        } else implied.coerceAtLeast(0L)
                        val safeMode = when (mode) {
                            "SHORT_BREAK", "LONG_BREAK", "WORK" -> mode
                            else -> "WORK"
                        }
                        withContext(Dispatchers.Main) {
                            if (rem <= 0L) {
                                _pomodoroMode.value = safeMode
                                _pomodoroRemaining.value = 0L
                                _isPomodoroRunning.value = false
                                onPomodoroFinished()
                            } else {
                                startPomodoro(rem, safeMode)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun acquirePomodoroWakeLock(timeoutMs: Long) {
        try {
            if (pomodoroWakeLock == null) {
                val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
                pomodoroWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Toolz:PomodoroWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            val wl = pomodoroWakeLock ?: return
            try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
            // Short hold at finish only — never the whole focus (battery).
            wl.acquire(timeoutMs.coerceIn(10_000L, 5 * 60_000L))
        } catch (_: Exception) {}
    }

    private fun releasePomodoroWakeLockIfIdle() {
        try {
            // Keep held briefly after finish for sound/notif; release when ringing stops
            // or after the bounded delay. Never hold across a running phase.
            if (_isPomodoroRunning.value) return
            pomodoroWakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
    }

    // --- Todo Session Logic ---
    fun startTodoSession(taskId: Int, title: String) {
        // D-P1-03: switching tasks must NOT carry the old elapsed time.
        if (_todoTaskId.value != taskId) {
            _todoSessionTime.value = 0L
        }
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
        // Shared-file protocol: NEVER stopForeground(REMOVE) unconditionally —
        // that kills Timer/Stopwatch/Pomodoro. Demote only when all idle.
        stopForegroundIfIdle()
    }

    // --- Alarm Logic (P-P1-01: mutex + focus + IO ramp + coerce, no Main prepare) ---
    private fun startAlarm(uriString: String?, gradual: Boolean) {
        // Shared Pomodoro path — signature unchanged (additive hardening only).
        serviceScope.launch(Dispatchers.IO) {
            pomodoroPlayerMutex.withLock {
                try { stopSharedAlarmLocked() } catch (_: Exception) {}
                requestPomodoroAudioFocus()
                val finalUriString = if (isCustomRingtoneEnabled && !customRingtoneUri.isNullOrBlank()) {
                    customRingtoneUri
                } else {
                    uriString
                }
                val candidates = listOfNotNull(
                    finalUriString?.takeIf { it.isNotBlank() }?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() },
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                )
                var started = false
                for (uri in candidates) {
                    // P-P1-02: validate readable before setDataSource (revoked grant -> skip fast).
                    if (!isUriReadable(uri)) continue
                    var mp: MediaPlayer? = null
                    try {
                        mp = MediaPlayer().apply {
                            try { setWakeMode(this@ToolService, PowerManager.PARTIAL_WAKE_LOCK) } catch (_: Exception) {}
                            setDataSource(this@ToolService, uri)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build(),
                            )
                            isLooping = true
                            setOnErrorListener { _, _, _ -> true }
                            // IO thread: blocking prepare is safe here (never Main). Keeps
                            // behavior identical to Timer path; failures fall to next candidate.
                            prepare()
                        }
                        mediaPlayer = mp
                        if (gradual) {
                            mp.setVolume(0f, 0f)
                            withContext(Dispatchers.Main) { runCatching { mp.start() } }
                            started = true
                            volumeJob?.cancel()
                            volumeJob = serviceScope.launch(Dispatchers.IO) {
                                var volume = 0f
                                while (volume < 1f) {
                                    delay(500)
                                    volume = (volume + 0.05f).coerceIn(0f, 1f)
                                    try {
                                        // Snapshot under mutex target; break on release race.
                                        val cur = mediaPlayer
                                        if (cur == null) break
                                        cur.setVolume(volume, volume)
                                    } catch (_: IllegalStateException) { break }
                                    catch (_: Exception) { break }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) { runCatching { mp.start() } }
                            started = true
                        }
                        if (started) break
                    } catch (_: SecurityException) {
                        try { mp?.release() } catch (_: Exception) {}
                        if (mediaPlayer === mp) mediaPlayer = null
                        continue
                    } catch (_: IOException) {
                        try { mp?.release() } catch (_: Exception) {}
                        if (mediaPlayer === mp) mediaPlayer = null
                        continue
                    } catch (_: IllegalStateException) {
                        try { mp?.release() } catch (_: Exception) {}
                        if (mediaPlayer === mp) mediaPlayer = null
                        continue
                    } catch (_: Exception) {
                        try { mp?.release() } catch (_: Exception) {}
                        if (mediaPlayer === mp) mediaPlayer = null
                        continue
                    }
                }
                if (!started) {
                    // All candidates failed — release focus so we never hold it silently.
                    try { abandonPomodoroAudioFocus() } catch (_: Exception) {}
                }
                // Arm auto-stop for ringing (P-P0-03 parity with Timer): 5min cap.
                pomodoroRingStopJob?.cancel()
                pomodoroRingStopJob = serviceScope.launch {
                    delay(RING_TIMEOUT_MS)
                    try { stopPomodoroAlarmOnly() } catch (_: Exception) {}
                    try { releasePomodoroWakeLockIfIdle() } catch (_: Exception) {}
                }
            }
        }
    }

    /** True if the ringtone URI can actually be opened (grant still valid). */
    private fun isUriReadable(uri: android.net.Uri): Boolean {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.close()
            true
        } catch (_: Exception) {
            // file:// or unresolvable still attempted via setDataSource fallback chain.
            val scheme = try { uri.scheme } catch (_: Exception) { null }
            scheme == "file" || scheme == null
        }
    }

    private fun requestPomodoroAudioFocus(): Boolean {
        return try {
            val am = getSystemService(AudioManager::class.java) ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                pomodoroAudioFocusRequest = req
                am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) { false }
    }

    private fun abandonPomodoroAudioFocus() {
        try {
            val am = getSystemService(AudioManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pomodoroAudioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                pomodoroAudioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun stopSharedAlarmLocked() {
        // P-P1-01: cancel ramp BEFORE release (mutex caller holds pomodoroPlayerMutex).
        volumeJob?.cancel()
        volumeJob = null
        pomodoroRingStopJob?.cancel()
        pomodoroRingStopJob = null
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        abandonPomodoroAudioFocus()
    }

    /** Pomodoro-only stop — cancels pomodoro alarm ID, releases finish wakelock. */
    private fun stopPomodoroAlarmOnly() {
        serviceScope.launch(Dispatchers.IO) {
            pomodoroPlayerMutex.withLock { try { stopSharedAlarmLocked() } catch (_: Exception) {} }
        }
        try {
            getSystemService(NotificationManager::class.java).cancel(NotificationHelper.ID_POMODORO_ALARM)
        } catch (_: Exception) {}
        try { releasePomodoroWakeLockIfIdle() } catch (_: Exception) {}
    }

    fun stopAlarm() {
        // Backward-compatible shared stop: stops BOTH timer + shared players, cancels both alarm IDs.
        // Timer paths prefer stopTimerAlarmOnly(); Pomodoro paths keep using this.
        // P-P1-03: dismiss must leave no stuck heads-up — cancel BOTH alarm IDs + ongoing.
        _isTimerRinging.value = false
        serviceScope.launch(Dispatchers.IO) {
            timerPlayerMutex.withLock { try { stopTimerAlarmLocked() } catch (_: Exception) {} }
            pomodoroPlayerMutex.withLock { try { stopSharedAlarmLocked() } catch (_: Exception) {} }
        }
        try {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.cancel(NotificationHelper.ID_TIMER_ALARM)
            manager.cancel(NotificationHelper.ID_POMODORO_ALARM)
            manager.cancel(NotificationHelper.ID_POMODORO)
        } catch (_: Exception) {}
        try { releasePomodoroWakeLockIfIdle() } catch (_: Exception) {}
        maybeReleaseForegroundIfIdle()
    }

    /** Timer asked to set repeat flag — persisted atomically with state. */
    fun setTimerRepeatEnabled(enabled: Boolean) {
        timerRepeatEnabled = enabled
        serviceScope.launch(Dispatchers.IO) {
            try {
                settingsRepository.setTimerRepeat(enabled)
                persistTimerState()
            } catch (_: Exception) {}
        }
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
        val dismissPI = PendingIntent.getService(this, notificationId + 100, dismissIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // T-P1-01: FSI check on A14+ with fallback; distinct requestCodes; re-alert allowed.
        val canFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.canUseFullScreenIntent()
            } catch (_: Exception) { false }
        } else true
        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ALARM)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shortcut_timer)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(false)
            .setOngoing(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPI)
            .setSound(null) // Handled by MediaPlayer
        if (canFullScreen) {
            builder.setFullScreenIntent(pendingIntent, true)
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Deny-notif fallback: still vibrate (already fired) — no crash.
        } catch (_: Exception) {}
    }

    /** T-P1-01: Timer finish posts visual ALWAYS (even if sound off), vibrates, distinct IDs. */
    private fun showTimerFinishedNotification() {
        val route = Screen.Timer.route
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPI = PendingIntent.getActivity(
            this, REQ_TIMER_CONTENT, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismissIntent = Intent(this, ToolService::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(EXTRA_NOTIFICATION_ID, NotificationHelper.ID_TIMER_ALARM)
        }
        val dismissPI = PendingIntent.getService(
            this, REQ_TIMER_DISMISS, dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snoozeIntent = Intent(this, ToolService::class.java).apply {
            action = ACTION_TIMER_SNOOZE
            putExtra(EXTRA_SNOOZE_MILLIS, 60_000L)
        }
        val snoozePI = PendingIntent.getService(
            this, REQ_TIMER_SNOOZE, snoozeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopResetIntent = Intent(this, ToolService::class.java).apply {
            action = ACTION_TIMER_STOP
        }
        val stopResetPI = PendingIntent.getService(
            this, REQ_TIMER_STOPRESET, stopResetIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val canFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            } catch (_: Exception) { false }
        } else true
        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ALARM)
            .setContentTitle("Timer")
            .setContentText("Time is up!")
            .setSmallIcon(R.drawable.ic_shortcut_timer)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPI)
            .setOnlyAlertOnce(false)
            .setSound(null)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPI)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 1m", snoozePI)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop + Reset", stopResetPI)
        if (canFullScreen) builder.setFullScreenIntent(contentPI, true)
        try {
            getSystemService(NotificationManager::class.java).notify(NotificationHelper.ID_TIMER_ALARM, builder.build())
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
    }

    private fun createStopwatchNotification(): Notification {        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Stopwatch.route) }
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
            builder.setContentText("Paused: ${formatStopwatchElapsed(_stopwatchTime.value)}")
        }
        return builder.build()
    }

    private fun updateStopwatchNotification() {
        // Gate on global && background (S-P0-02); refreshNotifications() cancels instead.
        if (!isGlobalNotificationsEnabled || !isBackgroundNotificationsEnabled) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.ID_STOPWATCH, createStopwatchNotification())
    }

    /**
     * Stopwatch elapsed for notifications (S-P2-01): H:MM:SS with hours preserved,
     * unlike shared [formatTime] (MM:SS) which drops them ("90:00" ambiguity).
     */
    private fun formatStopwatchElapsed(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val totalSeconds = safe / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun createTimerNotification(text: String? = null): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.Timer.route) }
        val pendingIntent = PendingIntent.getActivity(this, 3002, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val toggleIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_TOGGLE }
        val togglePI = PendingIntent.getService(this, 21, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val stopIntent = Intent(this, ToolService::class.java).apply { action = ACTION_TIMER_STOP }
        val stopPI = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_TOOL_ACTIVE)
            .setContentTitle("Timer")
            .setSmallIcon(R.drawable.ic_shortcut_timer)
            .setOngoing(_isTimerRunning.value || _isTimerRinging.value)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
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
        // T-P0-02: timer running/ringing always posts ongoing notification, even if
        // backgroundNotificationsEnabled=false (reliability over preference).
        if (!isBackgroundNotificationsEnabled && !_isTimerRunning.value && !_isTimerRinging.value) return
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NotificationHelper.ID_TIMER, createTimerNotification(text))
        } catch (_: SecurityException) {
        } catch (_: Exception) {}
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
            .setSmallIcon(if (_pomodoroMode.value == "WORK") R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_foreground)
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

        val totalMs = _pomodoroTotalMs.value.coerceAtLeast(1L)
        val remainingMs = _pomodoroRemaining.value.coerceIn(0L, totalMs)

        if (_isPomodoroRunning.value) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() + remainingMs)
            // P-P1-03: SDK guard (Timer path already guards; Pomodoro must match).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
            builder.setContentText("Focusing... • ${formatTime(remainingMs)} left")
            // P-P2-05: guard setProgress(0,0) — max coerced >= 1, progress clamped.
            builder.setProgress(
                totalMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1),
                (totalMs - remainingMs).coerceIn(0L, totalMs).toInt(),
                false,
            )
        } else {
            builder.setContentText(text ?: "Paused • ${formatTime(remainingMs)} left")
            builder.setProgress(
                totalMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1),
                (totalMs - remainingMs).coerceIn(0L, totalMs).toInt(),
                false,
            )
        }
        return builder.build()
    }

    private fun updatePomodoroNotification(text: String? = null) {
        if (!isBackgroundNotificationsEnabled) return
        if (!isGlobalNotificationsEnabled || !isPomodoroNotificationsEnabled) {
            // P-P1-03: still cycle silently — never leave a stuck heads-up when gated.
            try {
                getSystemService(NotificationManager::class.java).cancel(NotificationHelper.ID_POMODORO)
            } catch (_: Exception) {}
            return
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NotificationHelper.ID_POMODORO, createPomodoroNotification(text))
        } catch (_: SecurityException) {
            // Deny-notif fallback: no crash, vibration already fired.
        } catch (_: Exception) {}
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
        if (!isBackgroundNotificationsEnabled) return
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
        // Persist before death so kill mid-run can resume; release WakeLock with timeout safety.
        try {
            serviceScope.launch(Dispatchers.IO) {
                try { persistTimerState() } catch (_: Exception) {}
                try { persistPomodoroState() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        // Stopwatch best-effort synchronous save (S-P0-01): eager persists on every
        // mutation already cover the common case; runBlocking covers a kill racing
        // the last tick. Bounded to 500ms to avoid ANR.
        try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(500L) {
                    if (_isStopwatchRunning.value || _stopwatchTime.value > 0L ||
                        _stopwatchLaps.value.isNotEmpty()
                    ) {
                        settingsRepository.saveStopwatchState(
                            baseElapsed = stopwatchBase,
                            accumulated = if (_isStopwatchRunning.value) {
                                (SystemClock.elapsedRealtime() - stopwatchBase).coerceAtLeast(0L)
                            } else {
                                _stopwatchTime.value
                            },
                            running = _isStopwatchRunning.value,
                            lapsJson = settingsRepository.serializeStopwatchLaps(_stopwatchLaps.value),
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        try {
            timeChangeReceiver?.let { unregisterReceiver(it) }
            timeChangeReceiver = null
        } catch (_: Exception) {}
        try { forceReleaseTimerWakeLock() } catch (_: Exception) {}
        try { pomodoroWakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        super.onDestroy()
        try { serviceScope.cancel() } catch (_: Exception) {}
    }

    // ── Glance Widget State Push ───────────────────────────────────────────
    // P-P1-03: Long millis (no Float rounding), cached goal, guarded inside manager.

    private suspend fun pushPomodoroWidgetState() {
        try {
            widgetUpdateManager.updatePomodoroWidget(
                mode = _pomodoroMode.value,
                remainingMs = _pomodoroRemaining.value.coerceAtLeast(0L),
                totalMs = _pomodoroTotalMs.value.coerceAtLeast(1L),
                isRunning = _isPomodoroRunning.value,
                sessionsDone = _pomodoroSessionsDone.value.coerceAtLeast(0),
                sessionsGoal = pomodoroSessionsGoalCached.coerceIn(1, 12),
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
        const val ACTION_TIMER_FINISH = "com.frerox.toolz.TIMER_FINISH"
        const val ACTION_TIMER_SNOOZE = "com.frerox.toolz.TIMER_SNOOZE"
        const val ACTION_POMODORO_TOGGLE = "com.frerox.toolz.POMODORO_TOGGLE"
        const val ACTION_POMODORO_STOP   = "com.frerox.toolz.POMODORO_STOP"
        const val ACTION_POMODORO_SKIP   = "com.frerox.toolz.POMO_SKIP"
        const val ACTION_POMODORO_RESET  = "com.frerox.toolz.POMO_RESET"
        const val ACTION_POMODORO_FINISH = "com.frerox.toolz.POMODORO_FINISH"
        const val ACTION_TODO_STOP       = "com.frerox.toolz.TODO_STOP"
        const val ACTION_DISMISS_ALARM   = "com.frerox.toolz.DISMISS_ALARM"
        const val ACTION_STOP_ALARM      = "com.frerox.toolz.STOP_ALARM"
        
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_SNOOZE_MILLIS = "snooze_millis"

        // Distinct requestCodes for timer watchdog/actions (never reuse Pomodoro IDs).
        const val REQ_TIMER_WATCHDOG = 3101
        const val REQ_TIMER_CONTENT = 3102
        const val REQ_TIMER_DISMISS = 3103
        const val REQ_TIMER_SNOOZE = 3104
        const val REQ_TIMER_STOPRESET = 3105
        // Pomodoro watchdog uses its own range — never reuse timer codes.
        const val REQ_POMODORO_WATCHDOG = 3201

        const val RING_TIMEOUT_MS = 5 * 60_000L
    }
}
