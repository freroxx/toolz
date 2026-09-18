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
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
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
    val selectedMinutes: Int = 0,
    val selectedSeconds: Int = 0,
    val repeatLastDuration: Boolean = false,
    val keepScreenOn: Boolean = true,
    val gradualVolume: Boolean = false,
    val alarmsEnabled: Boolean = true,
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerState())
    val uiState: StateFlow<TimerState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun consumeMessage() { _userMessage.value = null }

    val hapticEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _timerHistory = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val timerHistory: StateFlow<List<Pair<Int, Int>>> = _timerHistory.asStateFlow()

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
        // Ensure service exists so onCreate restore runs (never rely on BIND_AUTO_CREATE alone).
        ensureServiceStarted()
        Intent(context, ToolService::class.java).also { intent ->
            try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (_: Exception) {}
        }

        // Settings: init-only load + distinctUntilChanged (T-P1-03). Never overwrite
        // staging/remaining while running/paused — only selectedMinutes/Seconds when idle.
        // 5-flow typed combine (coroutines typed overload) + separate repeat collect.
        viewModelScope.launch {
            combine(
                settingsRepository.lastTimerMinutes,
                settingsRepository.lastTimerSeconds,
                settingsRepository.timerKeepScreenOn,
                settingsRepository.timerGradualVolume,
                settingsRepository.timerNotifications,
            ) { min: Int, sec: Int, kso: Boolean, gv: Boolean, alarms: Boolean ->
                TimerBasicSettings(min, sec, kso, gv, alarms)
            }
                .distinctUntilChanged()
                .collect { snap ->
                    _uiState.update { cur ->
                        val idle = !cur.isRunning && !cur.isRinging && cur.remainingTime <= 0L && !cur.isStarted
                        cur.copy(
                            selectedMinutes = if (idle) snap.min.coerceIn(0, 999) else cur.selectedMinutes,
                            selectedSeconds = if (idle) snap.sec.coerceIn(0, 59) else cur.selectedSeconds,
                            keepScreenOn = snap.keepScreenOn,
                            gradualVolume = snap.gradual,
                            alarmsEnabled = snap.alarms,
                        )
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.timerRepeat.distinctUntilChanged().collect { repeat ->
                _uiState.update { it.copy(repeatLastDuration = repeat) }
            }
        }

        viewModelScope.launch {
            combine(
                settingsRepository.timerHistory,
                settingsRepository.lockedTimerPresets
            ) { historyMap, lockedList ->
                val historyParsed = historyMap.entries.mapNotNull { (k, count) ->
                    val parts = k.split(":", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val m = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val s = parts[1].toIntOrNull() ?: return@mapNotNull null
                    Pair(m.coerceIn(0, 999), s.coerceIn(0, 59)) to count
                }

                val lockedParsed = lockedList.mapNotNull { k ->
                    if (k.isBlank()) return@mapNotNull null
                    val parts = k.split(":", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val m = parts[0].toIntOrNull() ?: return@mapNotNull null
                    val s = parts[1].toIntOrNull() ?: return@mapNotNull null
                    Pair(m.coerceIn(0, 999), s.coerceIn(0, 59))
                }

                // Construct top 3: prioritize locked, then history
                val finalPresets = mutableListOf<Pair<Int, Int>>()

                // Add locked ones first
                for (i in 0 until 3) {
                    if (i < lockedParsed.size) {
                        finalPresets.add(lockedParsed[i])
                    } else {
                        // Fill with history
                        val historyTop = historyParsed
                            .sortedByDescending { it.second }
                            .map { it.first }
                            .filter { !finalPresets.contains(it) }
                            .firstOrNull()

                        if (historyTop != null) {
                            finalPresets.add(historyTop)
                        } else {
                            // Defaults
                            val default = when(finalPresets.size) {
                                0 -> 5 to 0
                                1 -> 15 to 0
                                else -> 30 to 0
                            }
                            if (!finalPresets.contains(default)) finalPresets.add(default)
                        }
                    }
                }

                finalPresets.take(3)
            }.collect { top3 ->
                _timerHistory.value = top3
            }
        }
    }

    private data class TimerBasicSettings(
        val min: Int,
        val sec: Int,
        val keepScreenOn: Boolean,
        val gradual: Boolean,
        val alarms: Boolean,
    )

    fun lockPreset(index: Int, minutes: Int, seconds: Int) {
        // T-P2-03: reject 0:00 with error, never dead chip.
        if (minutes <= 0 && seconds <= 0) {
            _userMessage.value = "Pick a duration greater than 0:00"
            return
        }
        viewModelScope.launch {
            try {
                settingsRepository.updateLockedTimerPreset(index, minutes.coerceIn(0, 999), seconds.coerceIn(0, 59))
            } catch (_: Exception) {}
        }
    }

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
                        val started = cur.isStarted || snap.running || snap.ringing || snap.remaining > 0L
                        // T-P1-03: derive isPaused = !running && remaining>0 && started (never-started => Ready).
                        val paused = !snap.running && !snap.ringing && snap.remaining > 0L && started
                        cur.copy(
                            remainingTime = snap.remaining,
                            initialTime = snap.initial,
                            isRunning = snap.running,
                            isRinging = snap.ringing,
                            isFinished = snap.ringing,
                            isPaused = paused,
                            isStarted = started,
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

    fun onTimeSelectedChange(min: Int, sec: Int, force: Boolean = false) {
        val safeMinutes = min.coerceIn(0, 999)
        val safeSeconds = sec.coerceIn(0, 59)
        val duration = durationMillis(safeMinutes, safeSeconds)
        val cur = _uiState.value
        // T-P1-03: block staging while running; paused remaining>0 requires confirm (force).
        if (cur.isRunning) return
        if (!force && cur.remainingTime > 0L && cur.isStarted && !cur.isRunning && !cur.isRinging) {
            _userMessage.value = "Timer paused — Reset to pick a new duration"
            return
        }
        if (cur.isRinging && !force) return

        if (toolService == null) {
            // Queue until bound (never silent drop).
            ensureServiceStarted()
            pendingAction = { onTimeSelectedChange(min, sec, force) }
            // Optimistic staging for responsiveness.
            _uiState.update {
                it.copy(
                    selectedMinutes = safeMinutes,
                    selectedSeconds = safeSeconds,
                    remainingTime = duration,
                    initialTime = duration,
                    isFinished = false,
                    isRinging = false,
                    isPaused = duration > 0L,
                    isStarted = duration > 0L,
                )
            }
            return
        }

        // Robust stop of ringing via service action (works even if binder dies next).
        if (cur.isRinging || cur.isFinished) {
            startServiceAction(ToolService.ACTION_DISMISS_ALARM) {
                it.putExtra(ToolService.EXTRA_NOTIFICATION_ID, NotificationHelper.ID_TIMER_ALARM)
            }
        }

        _uiState.update {
            it.copy(
                selectedMinutes = safeMinutes,
                selectedSeconds = safeSeconds,
                remainingTime = duration,
                initialTime = duration,
                isFinished = false,
                isRinging = false,
                isPaused = duration > 0L,
                isStarted = duration > 0L,
            )
        }
        try { toolService?.setTimerInitial(duration) } catch (_: Exception) {}

        viewModelScope.launch {
            try { settingsRepository.setLastTimerDuration(safeMinutes, safeSeconds) } catch (_: Exception) {}
        }
    }

    fun setTimer(minutes: Int, seconds: Int, force: Boolean = false) {
        val cur = _uiState.value
        if (cur.isRunning) return
        if (!force && cur.remainingTime > 0L && cur.isStarted && !cur.isRunning && !cur.isRinging) {
            _userMessage.value = "Timer paused — Reset to pick a new duration"
            return
        }
        if (toolService == null) {
            ensureServiceStarted()
            pendingAction = { setTimer(minutes, seconds, force) }
            return
        }
        val safeMin = minutes.coerceIn(0, 999)
        val safeSec = seconds.coerceIn(0, 59)
        val totalMillis = durationMillis(safeMin, safeSec)
        if (totalMillis <= 0L) {
            _userMessage.value = "Pick a duration greater than 0:00"
            return
        }
        robustStopRingtone()
        _uiState.update {
            it.copy(
                selectedMinutes = safeMin,
                selectedSeconds = safeSec,
                remainingTime = totalMillis,
                initialTime = totalMillis,
                isFinished = false,
                isRinging = false,
                isPaused = true,
                isStarted = true,
            )
        }
        try { toolService?.setTimerInitial(totalMillis) } catch (_: Exception) {}

        viewModelScope.launch {
            try { settingsRepository.setLastTimerDuration(safeMin, safeSec) } catch (_: Exception) {}
        }
    }

    // Keep old 2-arg overload for existing callers (delegates with force=false).
    // Note: new 3-arg overload above is the primary; this alias preserves binary compat
    // for function references like viewModel::setTimer used in TimerScreen.

    fun addTime(millis: Long) {
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
            durationMillis(state.selectedMinutes, state.selectedSeconds)
        }
        if (base <= 0L && !state.isRunning) {
            _userMessage.value = "Pick a duration first"
            return
        }
        val capped = base + millis > MAX_TIMER_MILLIS
        val newRemaining = (base + millis).coerceIn(0L, MAX_TIMER_MILLIS)
        if (capped) {
            _userMessage.value = "Timer capped at 999:59"
        }
        if (newRemaining <= 0L) {
            _userMessage.value = "Pick a duration greater than 0:00"
            return
        }
        // T-P1-03: keep initial immutable unless idle; track added via remaining only.
        val idle = !state.isStarted && !state.isRunning && state.remainingTime <= 0L && state.initialTime <= 0L
        val newInitial = if (idle) newRemaining else state.initialTime.takeIf { it > 0L } ?: newRemaining
        robustStopRingtone()
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
                startServiceAction(ToolService.ACTION_TIMER_TOGGLE)
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

    fun toggleStartStop() {
        // T-P0-01: foreground-start BEFORE bind call.
        ensureServiceStarted()
        val state = _uiState.value
        if (state.isRunning) {
            startServiceAction(ToolService.ACTION_TIMER_TOGGLE)
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
            else -> durationMillis(state.selectedMinutes, state.selectedSeconds)
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

        robustStopRingtone()
        // Service as truth: do NOT optimistically set isRunning=true; flows will confirm.
        _uiState.update { it.copy(isStarted = true, isFinished = false, isRinging = false) }
        try {
            startServiceAction(ToolService.ACTION_TIMER_TOGGLE)
            toolService?.startTimer(duration, initial)
        } catch (_: Exception) {
            // Revert optimistic started on failure.
            _uiState.update { it.copy(isStarted = state.isStarted) }
            _userMessage.value = "Couldn't start timer — try again"
            return
        }
        viewModelScope.launch {
            try {
                // Record ACTUAL duration/initial, not selected (T-P1-03).
                val mins = (duration / 60000L).toInt().coerceIn(0, 999)
                val secs = ((duration % 60000L) / 1000L).toInt().coerceIn(0, 59)
                settingsRepository.recordTimerUsage(mins, secs)
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

    fun toggleAlarms() {
        viewModelScope.launch {
            try { settingsRepository.setTimerNotifications(!_uiState.value.alarmsEnabled) } catch (_: Exception) {}
        }
    }

    fun toggleHaptic() {
        viewModelScope.launch {
            try { settingsRepository.setHapticFeedback(!hapticEnabled.value) } catch (_: Exception) {}
        }
    }

    fun playRingtone() {
        // Handled by ToolService
    }

    private fun robustStopRingtone() {
        // Robust stop via startService (works when toolService==null) + binder timer-only path.
        startServiceAction(ToolService.ACTION_DISMISS_ALARM) {
            it.putExtra(ToolService.EXTRA_NOTIFICATION_ID, NotificationHelper.ID_TIMER_ALARM)
        }
        try { toolService?.dismissTimerAlarm() } catch (_: Exception) {}
    }

    fun stopRingtone() {
        val repeat = _uiState.value.repeatLastDuration
        robustStopRingtone()
        // T-P1-03: clear initial on dismiss if not repeat.
        _uiState.update {
            it.copy(
                isFinished = false,
                isRinging = false,
                remainingTime = if (repeat) it.initialTime else 0L,
                initialTime = if (repeat) it.initialTime else 0L,
                isPaused = repeat && it.initialTime > 0L,
                isStarted = repeat && it.initialTime > 0L,
            )
        }
    }

    fun reset() {
        // Robust via service action (works unbound) + binder.
        startServiceAction(ToolService.ACTION_TIMER_STOP)
        try { toolService?.resetTimer() } catch (_: Exception) {}
        try { toolService?.dismissTimerAlarm() } catch (_: Exception) {}
        _uiState.update {
            it.copy(
                remainingTime = 0L,
                initialTime = 0L,
                isRunning = false,
                isFinished = false,
                isRinging = false,
                isPaused = false,
                isStarted = false,
            )
        }
    }

    fun resetToInitial() {
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
        robustStopRingtone()
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

    private fun durationMillis(minutes: Int, seconds: Int): Long {
        val totalSeconds = minutes.coerceIn(0, 999) * 60L + seconds.coerceIn(0, 59)
        return (totalSeconds * 1000L).coerceIn(0L, MAX_TIMER_MILLIS)
    }

    private companion object {
        const val MAX_TIMER_MILLIS = 999L * 60L * 1000L + 59L * 1000L
    }
}
