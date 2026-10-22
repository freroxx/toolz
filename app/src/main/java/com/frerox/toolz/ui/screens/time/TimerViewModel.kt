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

package com.frerox.toolz.ui.screens.time

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerState(
    val remainingTime: Long = 0L,
    val initialTime: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val isRinging: Boolean = false,
    val isPaused: Boolean = false,
    val isStarted: Boolean = false,
    // True once the countdown has actually run. Staging a duration (wheel /
    // preset, never started) leaves this false, so further edits stay free —
    // the replace-confirm + guards below only kick in after a real run.
    val hasRun: Boolean = false,
    val selectedMinutes: Int = 0,
    val selectedSeconds: Int = 0,
    val selectedHours: Int = 0,
    val repeatLastDuration: Boolean = false,
    val keepScreenOn: Boolean = true,
    val gradualVolume: Boolean = false,
    val alarmsEnabled: Boolean = true,
    val showMilliseconds: Boolean = false,
    val showStatusPill: Boolean = true,
    val ringtoneUri: String? = null,
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    // DEV-SPOOF: gates the RAM fixture countdown when the sandbox is on.
    private val gate: com.frerox.toolz.data.developer.SpoofGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerState())

    // DEV-SPOOF: fixture countdown (null = not sandboxed). A RAM timer that
    // loops "stage → run → ring" with no alarm service, no FGS and no
    // notifications; its duration comes from the Timer sandbox parameter.
    private val _sandboxTimer = MutableStateFlow<TimerState?>(null)
    private var sandboxTickJob: Job? = null
    private var sandboxActiveNow = false

    val timersSandboxActive: Boolean
        get() = gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.TIMERS)

    /** DEV-SPOOF: sandboxed → the whole countdown renders from fixtures. */
    val uiState: StateFlow<TimerState> = combine(
        _uiState.asStateFlow(),
        _sandboxTimer,
        gate.active(com.frerox.toolz.data.developer.DevToolId.TIMERS),
    ) { real, fixture, spoofed ->
        if (spoofed && fixture != null) fixture else real
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerState())

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun consumeMessage() { _userMessage.value = null }

    val hapticEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _timerHistory = MutableStateFlow<List<Triple<Int, Int, Int>>>(emptyList())
    val timerHistory: StateFlow<List<Triple<Int, Int, Int>>> = _timerHistory.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false
    // T-P1-03: keep Job refs, cancel on re-bind/disconnect. Single combine reducer.
    private var serviceCollectJob: Job? = null
    private var pendingAction: (() -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            bindTimerFlows(binder.getService())
            // Drain queued staging (T-P1-03: queue until bound, never silent drop).
            val pending = pendingAction
            pendingAction = null
            try { pending?.invoke() } catch (_: Exception) {}
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceCollectJob?.cancel()
            serviceCollectJob = null
            toolService = null
            isBound = false
        }
    }

    init {
        // DEV-SPOOF: sandboxed → never bind or start the real ToolService; the
        // fixture timer owns the dial. Live-switch detaches/reattaches it.
        if (timersSandboxActive) {
            enterSandboxImmediately()
        } else {
            ensureServiceStarted()
            Intent(context, ToolService::class.java).also { intent ->
                try {
                    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (_: Exception) {}
            }
        }
        // DEV-SPOOF: watch the live switch.
        viewModelScope.launch {
            gate.active(com.frerox.toolz.data.developer.DevToolId.TIMERS).collect { spoofed ->
                if (spoofed && !sandboxActiveNow) {
                    enterSandboxImmediately()
                } else if (!spoofed && sandboxActiveNow) {
                    sandboxActiveNow = false
                    sandboxTickJob?.cancel()
                    sandboxTickJob = null
                    _sandboxTimer.value = null
                    ensureServiceStarted()
                    Intent(context, ToolService::class.java).also { intent ->
                        try {
                            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // Settings: init-only load + distinctUntilChanged (T-P1-03). Never overwrite
        // staging/remaining while running/paused — only selectedMinutes/Seconds when idle.
        // 5-flow typed combine (coroutines typed overload) + separate repeat collect.
        viewModelScope.launch {
            // 6-flow typed combine has no overload here — nest 3 (h/m/s) + 4.
            val hmsFlow = combine(
                settingsRepository.lastTimerHours,
                settingsRepository.lastTimerMinutes,
                settingsRepository.lastTimerSeconds,
            ) { hr: Int, min: Int, sec: Int -> Triple(hr, min, sec) }
            combine(
                hmsFlow,
                settingsRepository.timerKeepScreenOn,
                settingsRepository.timerGradualVolume,
                settingsRepository.timerNotifications,
            ) { hms: Triple<Int, Int, Int>, kso: Boolean, gv: Boolean, alarms: Boolean ->
                TimerBasicSettings(hms.first, hms.second, hms.third, kso, gv, alarms)
            }
                .distinctUntilChanged()
                .collect { snap ->
                    _uiState.update { cur ->
                        val idle = !cur.isRunning && !cur.isRinging && cur.remainingTime <= 0L && !cur.isStarted
                        if (!idle) {
                            cur.copy(
                                keepScreenOn = snap.keepScreenOn,
                                gradualVolume = snap.gradual,
                                alarmsEnabled = snap.alarms,
                            )
                        } else {
                            val hr = snap.hr.coerceIn(0, 99)
                            val min = snap.min.coerceIn(0, 59)
                            val sec = snap.sec.coerceIn(0, 59)
                            // Idle with a remembered duration: seed the readout
                            // from the wheels so dial and set-duration match on
                            // cold start (remembered data), like fresh staging.
                            val remembered = durationMillis(hr, min, sec)
                            cur.copy(
                                selectedHours = hr,
                                selectedMinutes = min,
                                selectedSeconds = sec,
                                remainingTime = if (remembered > 0L) remembered else cur.remainingTime,
                                initialTime = if (remembered > 0L) remembered else cur.initialTime,
                                isPaused = if (remembered > 0L) true else cur.isPaused,
                                isStarted = if (remembered > 0L) true else cur.isStarted,
                                keepScreenOn = snap.keepScreenOn,
                                gradualVolume = snap.gradual,
                                alarmsEnabled = snap.alarms,
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.timerRepeat.distinctUntilChanged().collect { repeat ->
                _uiState.update { it.copy(repeatLastDuration = repeat) }
            }
        }
        viewModelScope.launch {
            settingsRepository.timerShowMs.distinctUntilChanged().collect { showMs ->
                _uiState.update { it.copy(showMilliseconds = showMs) }
            }
        }
        viewModelScope.launch {
            settingsRepository.timerShowStatusPill.distinctUntilChanged().collect { show ->
                _uiState.update { it.copy(showStatusPill = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.ringtoneUri.distinctUntilChanged().collect { uri ->
                _uiState.update { it.copy(ringtoneUri = uri) }
            }
        }

        viewModelScope.launch {
            // Presets = most-used history first, then defaults. (Lock-preset
            // feature removed: nothing writes locked slots anymore, so they
            // are no longer consulted here.)
            settingsRepository.timerHistory.collect { historyMap ->
                // Keys are "h:min:sec"; legacy "min:sec" entries (pre-hours) still parse with h=0.
                fun parseHms(key: String): Triple<Int, Int, Int>? {
                    val parts = key.split(":")
                    return when (parts.size) {
                        3 -> {
                            val h = parts[0].toIntOrNull() ?: return null
                            val m = parts[1].toIntOrNull() ?: return null
                            val s = parts[2].toIntOrNull() ?: return null
                            Triple(h.coerceIn(0, 99), m.coerceIn(0, 59), s.coerceIn(0, 59))
                        }
                        2 -> {
                            val m = parts[0].toIntOrNull() ?: return null
                            val s = parts[1].toIntOrNull() ?: return null
                            Triple(0, m.coerceIn(0, 59), s.coerceIn(0, 59))
                        }
                        else -> null
                    }
                }
                val finalPresets = historyMap.entries
                    .mapNotNull { (k, count) -> (parseHms(k) ?: return@mapNotNull null) to count }
                    .sortedByDescending { it.second }
                    .map { it.first }
                    // Skip dead 0:00:00 entries — tapping one would stage 0ms
                    // and dead-end Start with "pick a duration".
                    .filter { (it.first * 3600 + it.second * 60 + it.third) > 0 }
                    .distinct()
                    .take(3)
                    .toMutableList()
                // Defaults fill any gaps.
                listOf(Triple(0, 5, 0), Triple(0, 15, 0), Triple(0, 30, 0)).forEach { default ->
                    if (finalPresets.size < 3 && !finalPresets.contains(default)) finalPresets.add(default)
                }
                _timerHistory.value = finalPresets.take(3)
            }
        }
    }

    private data class TimerBasicSettings(
        val hr: Int,
        val min: Int,
        val sec: Int,
        val keepScreenOn: Boolean,
        val gradual: Boolean,
        val alarms: Boolean,
    )

    private fun ensureServiceStarted() {
        try {
            val intent = Intent(context, ToolService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    private fun startServiceAction(action: String, extras: (Intent) -> Unit = {}) {
        try {
            val intent = Intent(context, ToolService::class.java).apply {
                this.action = action
                extras(this)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    private fun bindTimerFlows(service: ToolService) {
        // Cancel duplicates on re-bind (T-P1-03).
        serviceCollectJob?.cancel()
        serviceCollectJob = viewModelScope.launch {
            combine(
                service.timerRemaining,
                service.timerInitial,
                service.isTimerRunning,
                service.isTimerRinging,
            ) { rem: Long, init: Long, running: Boolean, ringing: Boolean ->
                TimerServiceSnapshot(
                    remaining = rem.coerceAtLeast(0L),
                    initial = init.coerceAtLeast(0L),
                    running = running,
                    ringing = ringing,
                )
            }
                .distinctUntilChanged()
                .collect { snap ->
                    _uiState.update { cur ->
                        // Single truth for the readout: when the service holds no
                        // session (fully idle snapshot) the dial shows the staged
                        // wheel duration, so wheel and precision text always agree
                        // at rest. Staged H/M/S persist via lastTimer* ("remember").
                        val idleSnap = !snap.running && !snap.ringing &&
                            snap.remaining <= 0L && snap.initial <= 0L
                        val staged = durationMillis(cur.selectedHours, cur.selectedMinutes, cur.selectedSeconds)
                        val showStaged = idleSnap && staged > 0L
                        val effRemaining = if (showStaged) staged else snap.remaining
                        val effInitial = if (showStaged) staged else snap.initial
                        // Started latches on, but a fully-zero snapshot is
                        // authoritative: without the effInitial clause the
                        // flag re-latched true from intermediate emissions and
                        // reset could never return to true idle.
                        val started = snap.running || snap.ringing || effRemaining > 0L ||
                            (cur.isStarted && effInitial > 0L)
                        // T-P1-03: derive isPaused = !running && remaining>0 && started (never-started => Ready).
                        val paused = !snap.running && !snap.ringing && effRemaining > 0L && started
                        cur.copy(
                            remainingTime = effRemaining,
                            initialTime = effInitial,
                            isRunning = snap.running,
                            isRinging = snap.ringing,
                            isFinished = snap.ringing,
                            isPaused = paused,
                            isStarted = started,
                            // A fully-idle service means nothing has run; staged
                            // edits stay free (no replace-confirm) until a run.
                            hasRun = if (idleSnap) false else cur.hasRun || snap.running,
                        )
                    }
                }
        }
    }

    private data class TimerServiceSnapshot(
        val remaining: Long,
        val initial: Long,
        val running: Boolean,
        val ringing: Boolean,
    )

    fun onTimeSelectedChange(hours: Int, min: Int, sec: Int, force: Boolean = false) {
        // DEV-SPOOF: sandboxed → stage on the fixture exactly like the real
        // pipeline (wheels apply to remaining/initial, staged = never ran).
        if (timersSandboxActive) {
            val safeHours = hours.coerceIn(0, 99)
            val safeMinutes = min.coerceIn(0, 59)
            val safeSeconds = sec.coerceIn(0, 59)
            val duration = (((safeHours * 3600L) + (safeMinutes * 60L) + safeSeconds) * 1000L)
            val fixture = _sandboxTimer.value ?: sandboxFixture()
            if (fixture.isRunning) return // staging blocked while running, as real mode
            if (fixture.isRinging && !force) return
            _sandboxTimer.value = fixture.copy(
                selectedHours = safeHours,
                selectedMinutes = safeMinutes,
                selectedSeconds = safeSeconds,
                remainingTime = duration,
                initialTime = duration,
                isFinished = false,
                isRinging = false,
                isPaused = duration > 0L,
                isStarted = duration > 0L,
                hasRun = false, // fresh staging counts as never-run
            )
            return
        }
        val safeHours = hours.coerceIn(0, 99)
        val safeMinutes = min.coerceIn(0, 59)
        val safeSeconds = sec.coerceIn(0, 59)
        val duration = durationMillis(safeHours, safeMinutes, safeSeconds)
        val cur = _uiState.value
        // T-P1-03: block staging while running; paused-after-run requires confirm (force).
        // Merely staged (never ran) durations edit freely.
        if (cur.isRunning) return
        if (!force && cur.remainingTime > 0L && cur.hasRun && !cur.isRunning && !cur.isRinging) {
            _userMessage.value = "Timer paused — Start a new timer or Reset"
            return
        }
        if (cur.isRinging && !force) return

        if (toolService == null) {
            // Queue until bound (never silent drop).
            ensureServiceStarted()
            pendingAction = { onTimeSelectedChange(hours, min, sec, force) }
            // Optimistic staging for responsiveness.
            _uiState.update {
                it.copy(
                    selectedHours = safeHours,
                    selectedMinutes = safeMinutes,
                    selectedSeconds = safeSeconds,
                    remainingTime = duration,
                    initialTime = duration,
                    isFinished = false,
                    isRinging = false,
                    isPaused = duration > 0L,
                    isStarted = duration > 0L,
                    // Fresh staging counts as never-run: edits stay free.
                    hasRun = false,
                )
            }
            return
        }

        // Direct-only stop (never the async DISMISS intent — it would land after
        // setTimerInitial below and wipe the staged duration; see stopRingtoneDirect).
        // Binder is non-null on this path; when it dies next, flows reconcile.
        if (cur.isRinging || cur.isFinished) {
            try { toolService?.dismissTimerAlarm() } catch (_: Exception) {}
        }

        _uiState.update {
            it.copy(
                selectedHours = safeHours,
                selectedMinutes = safeMinutes,
                selectedSeconds = safeSeconds,
                remainingTime = duration,
                initialTime = duration,
                isFinished = false,
                isRinging = false,
                isPaused = duration > 0L,
                isStarted = duration > 0L,
                // Fresh staging counts as never-run: edits stay free.
                hasRun = false,
            )
        }
        try { toolService?.setTimerInitial(duration) } catch (_: Exception) {}

        viewModelScope.launch {
            try { settingsRepository.setLastTimerDuration(safeHours, safeMinutes, safeSeconds) } catch (_: Exception) {}
        }
    }

    /** Compat: legacy min/sec callers (hours = 0). */
    fun onTimeSelectedChange(min: Int, sec: Int, force: Boolean = false) {
        // DEV-SPOOF: sandboxed → wheels stage on the fixture only.
        if (timersSandboxActive) {
            onTimeSelectedChange(hours = 0, min = min, sec = sec, force = force)
            return
        }
        onTimeSelectedChange(0, min, sec, force)
    }

    fun setTimer(hours: Int, minutes: Int, seconds: Int, force: Boolean = false) {
        // DEV-SPOOF: sandboxed → restage the fixture with the real staging
        // semantics: zero is a valid (empty) stage, running blocks edits.
        if (timersSandboxActive) {
            val fixture = _sandboxTimer.value ?: sandboxFixture()
            if (fixture.isRunning) return
            if (fixture.isRinging && !force) return
            val totalMillis = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L
            _sandboxTimer.value = fixture.copy(
                selectedHours = hours.coerceIn(0, 99),
                selectedMinutes = minutes.coerceIn(0, 59),
                selectedSeconds = seconds.coerceIn(0, 59),
                remainingTime = totalMillis,
                initialTime = totalMillis,
                isFinished = false,
                isRinging = false,
                isPaused = totalMillis > 0L,
                isStarted = totalMillis > 0L,
                hasRun = false,
            )
            return
        }
        val cur = _uiState.value
        if (cur.isRunning) return
        if (!force && cur.remainingTime > 0L && cur.hasRun && !cur.isRunning && !cur.isRinging) {
            _userMessage.value = "Timer paused — Start a new timer or Reset"
            return
        }
        val safeHr = hours.coerceIn(0, 99)
        val safeMin = minutes.coerceIn(0, 59)
        val safeSec = seconds.coerceIn(0, 59)
        val totalMillis = durationMillis(safeHr, safeMin, safeSec)
        if (toolService == null) {
            ensureServiceStarted()
            pendingAction = { setTimer(hours, minutes, seconds, force) }
            // FIX: optimistic staging while unbound (was: silent no-op — tapping a
            // preset on cold start did nothing visible until bind completed).
            // Mirrors onTimeSelectedChange so the Start button enables instantly.
            if (totalMillis > 0L) {
                _uiState.update {
                    it.copy(
                        selectedHours = safeHr,
                        selectedMinutes = safeMin,
                        selectedSeconds = safeSec,
                        remainingTime = totalMillis,
                        initialTime = totalMillis,
                        isFinished = false,
                        isRinging = false,
                        isPaused = true,
                        isStarted = true,
                        // Fresh staging counts as never-run: edits stay free.
                        hasRun = false,
                    )
                }
            }
            return
        }
        if (totalMillis <= 0L) {
            _userMessage.value = "Pick a duration greater than 0:00"
            return
        }
        stopRingtoneDirect()
        _uiState.update {
            it.copy(
                selectedHours = safeHr,
                selectedMinutes = safeMin,
                selectedSeconds = safeSec,
                remainingTime = totalMillis,
                initialTime = totalMillis,
                isFinished = false,
                isRinging = false,
                isPaused = true,
                isStarted = true,
                // Fresh staging counts as never-run: edits stay free.
                hasRun = false,
            )
        }
        try { toolService?.setTimerInitial(totalMillis) } catch (_: Exception) {}

        viewModelScope.launch {
            try { settingsRepository.setLastTimerDuration(safeHr, safeMin, safeSec) } catch (_: Exception) {}
        }
    }

    /** Compat: legacy min/sec callers (hours = 0). */
    fun setTimer(minutes: Int, seconds: Int, force: Boolean = false) {
        // DEV-SPOOF: sandboxed → delegate to the fixture restage.
        if (timersSandboxActive) { setTimer(0, minutes, seconds, force); return }
        setTimer(0, minutes, seconds, force)
    }

    fun addTime(millis: Long) {
        // DEV-SPOOF: sandboxed → extend the fixture countdown only.
        if (timersSandboxActive) {
            _sandboxTimer.update { cur ->
                val fixture = cur ?: sandboxFixture()
                val initial = ((fixture.initialTime + millis).coerceAtLeast(1000L)).coerceAtMost(21_600_000L)
                fixture.copy(
                    initialTime = initial,
                    remainingTime = (fixture.remainingTime + millis).coerceAtLeast(0L).coerceAtMost(initial),
                    isFinished = false,
                    isRinging = false,
                )
            }
            return
        }
        val state = _uiState.value
        // T-P0-01: startForegroundService BEFORE bind call; never rely on BIND_AUTO_CREATE alone.
        ensureServiceStarted()
        if (toolService == null) {
            pendingAction = { addTime(millis) }
            return
        }
        val base = if (state.remainingTime > 0L) {
            state.remainingTime
        } else {
            durationMillis(state.selectedHours, state.selectedMinutes, state.selectedSeconds)
        }
        if (base <= 0L && !state.isRunning) {
            _userMessage.value = "Pick a duration first"
            return
        }
        val capped = base + millis > MAX_TIMER_MILLIS
        val newRemaining = (base + millis).coerceIn(0L, MAX_TIMER_MILLIS)
        if (capped) {
            _userMessage.value = "Timer capped at 99:59:59"
        }
        if (newRemaining <= 0L) {
            _userMessage.value = "Pick a duration greater than 0:00"
            return
        }
        // T-P1-03: keep initial immutable unless idle; track added via remaining only.
        val idle = !state.isStarted && !state.isRunning && state.remainingTime <= 0L && state.initialTime <= 0L
        val newInitial = if (idle) newRemaining else state.initialTime.takeIf { it > 0L } ?: newRemaining
        stopRingtoneDirect()
        _uiState.update {
            it.copy(
                remainingTime = newRemaining,
                initialTime = newInitial,
                isFinished = false,
                isPaused = !it.isRunning && newRemaining > 0L,
                isStarted = true,
            )
        }
        try {
            if (state.isRunning) {
                // Re-arm end + watchdog with new remaining (service is truth for end).
                // NOTE: no ACTION_TIMER_TOGGLE here — the toggle intent is async and
                // would be handled AFTER this direct call, pausing the timer we just
                // extended. Direct binder call is sufficient (FGS ensured above).
                toolService?.startTimer(newRemaining, newInitial)
            } else if (state.remainingTime > 0L || state.isStarted) {
                // Paused: preserve initial.
                toolService?.setTimerRemainingPreservingInitial(newRemaining)
                // Keep staged initial if none yet.
                if (state.initialTime <= 0L) toolService?.setTimerInitial(newRemaining)
            } else {
                toolService?.setTimerInitial(newRemaining)
            }
        } catch (_: Exception) {}
    }

    // ── DEV-SPOOF: fixture timer internals ─────────────────────────────────

    private fun sandboxFixture(
        seconds: Long = gate.params.value.timerSecs.toLong(),
    ): TimerState {
        val totalSeconds = seconds
            .coerceIn(SpoofParams.TIMER_MIN.toLong(), SpoofParams.TIMER_MAX.toLong())
            .toInt()
        return TimerState(
            remainingTime = totalSeconds * 1000L,
            initialTime = totalSeconds * 1000L,
            isStarted = true,
            isPaused = false,
            hasRun = false,
            selectedHours = totalSeconds / 3_600,
            selectedMinutes = (totalSeconds % 3_600) / 60,
            selectedSeconds = totalSeconds % 60,
            alarmsEnabled = false, // sandbox: no notification/alarm machinery at all
        )
    }

    private fun enterSandboxImmediately() {
        sandboxActiveNow = true
        val seconds = gate.params.value.timerSecs.coerceIn(SpoofParams.TIMER_MIN, SpoofParams.TIMER_MAX)
        _sandboxTimer.value = sandboxFixture(seconds.toLong())
        try { context.unbindService(connection) } catch (_: Exception) {}
        try { context.stopService(Intent(context, ToolService::class.java)) } catch (_: Exception) {}
        isBound = false
        toolService = null
        serviceCollectJob?.cancel()
        serviceCollectJob = null
        pendingAction = null
        startSandboxTicker()
        android.util.Log.w(
            "DevSpoof",
            "Timers sandbox active — ${seconds}s fixture loop, ToolService detached (no alarms)",
        )
    }

    private fun startSandboxTicker() {
        if (sandboxTickJob?.isActive == true) return
        sandboxTickJob = viewModelScope.launch {
            while (true) {
                val s = _sandboxTimer.value ?: return@launch
                when {
                    s.isRunning && !s.isPaused -> {
                        val next = (s.remainingTime - 100L).coerceAtLeast(0L)
                        _sandboxTimer.value =
                            if (next == 0L) {
                                // Fake "ring" — visual only, no sound/notification.
                                s.copy(
                                    remainingTime = 0L,
                                    isRunning = false,
                                    isFinished = true,
                                    isRinging = true,
                                )
                            } else {
                                s.copy(remainingTime = next)
                            }
                    }
                    s.isRinging -> {
                        delay(1500)
                        _sandboxTimer.value = _sandboxTimer.value
                            ?.copy(isFinished = false, isRinging = false)
                        delay(250)
                        // Re-arm onto the exact staged duration (no /1000 loss).
                        _sandboxTimer.value = _sandboxTimer.value
                            ?.let { cur -> cur.copy(remainingTime = cur.initialTime) }
                    }
                }
                delay(100)
            }
        }
    }

    /** DEV-SPOOF: start/pause/dismiss against the RAM fixture only. */
    private fun toggleSandboxTimer() {
        val s = _sandboxTimer.value ?: sandboxFixture()
        _sandboxTimer.value = when {
            s.isFinished || s.isRinging -> s.copy(
                remainingTime = s.initialTime,
                isRunning = false,
                isPaused = s.initialTime > 0L,
                isStarted = s.initialTime > 0L,
                isFinished = false,
                isRinging = false,
                hasRun = false,
            )
            else -> {
                val starting = !s.isRunning
                s.copy(
                    isRunning = starting,
                    isPaused = !starting,
                    isStarted = true,
                    hasRun = s.hasRun || starting,
                )
            }
        }
    }

    // ── Facade: Timer commands ──────────────────────────────────────────────

    fun toggleStartStop() {
        // DEV-SPOOF: sandboxed → the fixture loops in RAM; service untouched.
        if (timersSandboxActive) { toggleSandboxTimer(); return }
        // T-P0-01: foreground-start BEFORE bind call.
        ensureServiceStarted()
        val state = _uiState.value
        if (state.isRunning) {
            // Direct pause only. A TOGGLE intent here is redundant (same effect)
            // and races the direct call; binder is non-null on this path.
            try { toolService?.pauseTimer() } catch (_: Exception) {}
            return
        }
        if (state.isRinging) {
            // Ringing: robust stop via service action (works when toolService==null).
            startServiceAction(ToolService.ACTION_DISMISS_ALARM) {
                it.putExtra(ToolService.EXTRA_NOTIFICATION_ID, NotificationHelper.ID_TIMER_ALARM)
            }
            try { toolService?.dismissTimerAlarm() } catch (_: Exception) {
                try { toolService?.stopAlarm() } catch (_: Exception) {}
            }
            return
        }

        val duration = when {
            state.remainingTime > 0L -> state.remainingTime
            else -> durationMillis(state.selectedHours, state.selectedMinutes, state.selectedSeconds)
        }
        // T-P1-02: validate >0 else notify user (never silent, never dead button).
        if (duration <= 0L) {
            _userMessage.value = "Pick a duration greater than 0:00"
            startServiceAction(ToolService.ACTION_TIMER_TOGGLE)
            return
        }

        val initial = when {
            state.initialTime > 0L -> state.initialTime
            else -> duration
        }

        if (toolService == null) {
            pendingAction = { toggleStartStop() }
            // Optimistic started flag only (service remains truth for isRunning).
            _uiState.update { it.copy(isStarted = true) }
            return
        }

        stopRingtoneDirect()
        // Service as truth: do NOT optimistically set isRunning=true; flows will confirm.
        // NOTE: no ACTION_TIMER_TOGGLE — it is async and would be handled AFTER the
        // direct startTimer below, immediately pausing the timer we just started
        // (the "timer starts then instantly pauses / never runs" bug). FGS was
        // already ensured at the top of this function.
        _uiState.update { it.copy(isStarted = true, isFinished = false, isRinging = false, hasRun = true) }
        try {
            toolService?.startTimer(duration, initial)
        } catch (_: Exception) {
            // Revert optimistic started on failure.
            _uiState.update { it.copy(isStarted = state.isStarted, hasRun = state.hasRun) }
            _userMessage.value = "Couldn't start timer — try again"
            return
        }
        viewModelScope.launch {
            try {
                // Record ACTUAL duration/initial, not selected (T-P1-03).
                val totalSec = (duration / 1000L).toInt()
                val hrs = (totalSec / 3600).coerceIn(0, 99)
                val mins = ((totalSec % 3600) / 60).coerceIn(0, 59)
                val secs = (totalSec % 60).coerceIn(0, 59)
                settingsRepository.recordTimerUsage(hrs, mins, secs)
            } catch (_: Exception) {}
        }
    }

    fun setRepeatLastDuration(enabled: Boolean) {
        _uiState.update { it.copy(repeatLastDuration = enabled) }
        viewModelScope.launch {
            try { settingsRepository.setTimerRepeat(enabled) } catch (_: Exception) {}
        }
        try { toolService?.setTimerRepeatEnabled(enabled) } catch (_: Exception) {}
        if (toolService == null) {
            // Persist even when unbound (service caches on next bind via DataStore).
            ensureServiceStarted()
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { try { settingsRepository.setTimerKeepScreenOn(enabled) } catch (_: Exception) {} }
    }

    fun setGradualVolume(enabled: Boolean) {
        viewModelScope.launch { try { settingsRepository.setTimerGradualVolume(enabled) } catch (_: Exception) {} }
    }

    fun setShowMilliseconds(enabled: Boolean) {
        _uiState.update { it.copy(showMilliseconds = enabled) }
        viewModelScope.launch { try { settingsRepository.setTimerShowMs(enabled) } catch (_: Exception) {} }
    }

    fun setShowStatusPill(enabled: Boolean) {
        _uiState.update { it.copy(showStatusPill = enabled) }
        viewModelScope.launch { try { settingsRepository.setTimerShowStatusPill(enabled) } catch (_: Exception) {} }
    }

    fun toggleAlarms() {
        val next = !_uiState.value.alarmsEnabled
        if (!next && _uiState.value.isRinging) {
            stopRingtoneDirect()
        }
        viewModelScope.launch {
            try { settingsRepository.setTimerNotifications(next) } catch (_: Exception) {}
        }
    }

    fun setRingtoneUri(uri: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val trimmed = uri.trim()
                if (trimmed.isBlank()) {
                    settingsRepository.setRingtoneUri("")
                    return@launch
                }
                val parsed = runCatching { android.net.Uri.parse(trimmed) }.getOrNull()
                if (parsed == null) {
                    return@launch
                }
                val readable = try {
                    context.contentResolver.openFileDescriptor(parsed, "r")?.close()
                    true
                } catch (_: SecurityException) {
                    false
                } catch (_: Exception) {
                    true
                }
                if (readable) {
                    try {
                        if (parsed.scheme == "content") {
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    parsed,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                    settingsRepository.setRingtoneUri(trimmed)
                } else {
                    settingsRepository.setRingtoneUri("")
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleHaptic() {
        viewModelScope.launch {
            try { settingsRepository.setHapticFeedback(!hapticEnabled.value) } catch (_: Exception) {}
        }
    }

    fun playRingtone() {
        // DEV-SPOOF: sandboxed → the fake ring is visual only, no audio session.
        if (timersSandboxActive) return
        // Handled by ToolService
    }

    /**
     * Synchronous binder-only stop for staging/starting paths (setTimer, addTime,
     * toggle-start, resetToInitial). Never sends the async DISMISS intent: an intent
     * queued now would be handled AFTER the state writes below and dismissTimerAlarm()
     * would cancel the fresh job + watchdog and zero persisted state. When unbound
     * there is nothing running to stop (flows prove it), so no intent is needed.
     */
    private fun stopRingtoneDirect() {
        val s = _uiState.value
        if (!s.isRinging && !s.isFinished) return
        try { toolService?.dismissTimerAlarm() } catch (_: Exception) {}
    }

    private fun robustStopRingtone() {
        // FIX ("timer not working at all"): this helper sends an ASYNC
        // ACTION_DISMISS_ALARM intent + a direct dismissTimerAlarm() that cancels
        // the countdown job, the watchdog AND zeroes persisted state. Firing it
        // unconditionally before startTimer()/addTime() meant the intent landed
        // AFTER the fresh start and killed it every time. Only act when actually
        // ringing/finishing — callers staging or starting an idle timer skip it.
        // (stopRingtone() calls this while flags are still true, so it passes.)
        val s = _uiState.value
        if (!s.isRinging && !s.isFinished) return
        // Robust stop via startService (works when toolService==null) + binder timer-only path.
        startServiceAction(ToolService.ACTION_DISMISS_ALARM) {
            it.putExtra(ToolService.EXTRA_NOTIFICATION_ID, NotificationHelper.ID_TIMER_ALARM)
        }
        try { toolService?.dismissTimerAlarm() } catch (_: Exception) {}
    }

    fun stopRingtone() {
        val repeat = _uiState.value.repeatLastDuration
        robustStopRingtone()
        // T-P1-03: dismiss returns to the staged wheel duration (repeat keeps
        // initial, which equals staged) — the dial never drops to 00:00.
        _uiState.update { cur ->
            val staged = durationMillis(cur.selectedHours, cur.selectedMinutes, cur.selectedSeconds)
            val restore = if (repeat) cur.initialTime else staged
            val restoreInitial = if (repeat) cur.initialTime else staged
            cur.copy(
                isFinished = false,
                isRinging = false,
                remainingTime = restore,
                initialTime = restoreInitial,
                isPaused = restore > 0L,
                isStarted = restore > 0L,
                // Dismissed without repeat = fresh timer; with repeat the
                // staged duration keeps its ran history (edits need confirm).
                hasRun = if (repeat) cur.hasRun else false,
            )
        }
    }

    fun reset() {
        // DEV-SPOOF: sandboxed → re-arms onto the currently staged duration
        // (the configured fixture duration when nothing is staged); no
        // service action at all.
        if (timersSandboxActive) {
            sandboxTickJob?.cancel()
            sandboxTickJob = null
            val initial = _sandboxTimer.value?.initialTime?.coerceAtLeast(0L) ?: 0L
            _sandboxTimer.value =
                if (initial > 0L) {
                    sandboxFixture(seconds = initial / 1000L).copy(
                        remainingTime = initial,
                        isStarted = true,
                        isPaused = true, // staged but not ticking, like a real reset
                    )
                } else {
                    sandboxFixture()
                }
            startSandboxTicker()
            return
        }
        // Robust via service action (works unbound) + binder.
        startServiceAction(ToolService.ACTION_TIMER_STOP)
        try { toolService?.resetTimer() } catch (_: Exception) {}
        try { toolService?.dismissTimerAlarm() } catch (_: Exception) {}
        // A queued staging/start must not resurrect the timer after reset.
        pendingAction = null
        _uiState.update { cur ->
            // Stop and return to the staged wheel duration: the dial shows the
            // set duration (wheels keep their values), never 00:00.
            val staged = durationMillis(cur.selectedHours, cur.selectedMinutes, cur.selectedSeconds)
            cur.copy(
                remainingTime = staged,
                initialTime = staged,
                isRunning = false,
                isFinished = false,
                isRinging = false,
                isPaused = staged > 0L,
                isStarted = staged > 0L,
                hasRun = false,
            )
        }
    }

    fun resetToInitial() {
        // DEV-SPOOF: sandboxed → re-arms the staged duration exactly (no /1000 loss).
        if (timersSandboxActive) {
            val fixture = _sandboxTimer.value ?: sandboxFixture()
            if (fixture.initialTime <= 0L) { reset(); return } // mirrors the real fallback
            _sandboxTimer.value = fixture.copy(
                remainingTime = fixture.initialTime,
                isRunning = false,
                isPaused = fixture.initialTime > 0L,
                isStarted = fixture.initialTime > 0L,
                isFinished = false,
                isRinging = false,
                hasRun = false,
            )
            return
        }
        val initial = _uiState.value.initialTime
        if (initial <= 0L) {
            reset()
            return
        }
        if (toolService == null) {
            ensureServiceStarted()
            pendingAction = { resetToInitial() }
            return
        }
        try { toolService?.setTimerInitial(initial) } catch (_: Exception) {}
        stopRingtoneDirect()
        _uiState.update {
            it.copy(
                remainingTime = initial,
                isRunning = false,
                isFinished = false,
                isRinging = false,
                isPaused = true,
                isStarted = true,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { serviceCollectJob?.cancel() } catch (_: Exception) {}
        try {
            if (isBound) {
                context.unbindService(connection)
            }
        } catch (_: Exception) {}
    }

    private fun durationMillis(hours: Int, minutes: Int, seconds: Int): Long {
        val totalSeconds = hours.coerceIn(0, 99) * 3600L +
            minutes.coerceIn(0, 59) * 60L +
            seconds.coerceIn(0, 59)
        return (totalSeconds * 1000L).coerceIn(0L, MAX_TIMER_MILLIS)
    }

    /** Compat: legacy min/sec callers (hours = 0). */
    private fun durationMillis(minutes: Int, seconds: Int): Long {
        return durationMillis(0, minutes, seconds)
    }

    private companion object {
        const val MAX_TIMER_MILLIS = 99L * 3600L * 1000L + 59L * 60L * 1000L + 59L * 1000L
    }
}
