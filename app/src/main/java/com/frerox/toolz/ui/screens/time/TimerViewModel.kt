package com.frerox.toolz.ui.screens.time

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    val hapticEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _timerHistory = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val timerHistory: StateFlow<List<Pair<Int, Int>>> = _timerHistory.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false
    private var mediaPlayer: MediaPlayer? = null
    private var lastFinishCount = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            bindTimerFlows(binder.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            toolService = null
            isBound = false
        }
    }

    init {
        Intent(context, ToolService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        viewModelScope.launch {
            combine(
                settingsRepository.lastTimerMinutes,
                settingsRepository.lastTimerSeconds,
                settingsRepository.timerKeepScreenOn,
                settingsRepository.timerGradualVolume,
                settingsRepository.timerNotifications
            ) { min, sec, kso, gv, alarms ->
                Triple(min to sec, kso, gv) to alarms
            }.collect { (params, alarms) ->
                val (dur, kso, gv) = params
                _uiState.update {
                    it.copy(
                        selectedMinutes = dur.first.coerceIn(0, 999),
                        selectedSeconds = dur.second.coerceIn(0, 59),
                        keepScreenOn = kso,
                        gradualVolume = gv,
                        alarmsEnabled = alarms
                    )
                }
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

    fun lockPreset(index: Int, minutes: Int, seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateLockedTimerPreset(index, minutes, seconds)
        }
    }

    private fun bindTimerFlows(service: ToolService) {
        viewModelScope.launch {
            service.timerRemaining.collect { remaining ->
                _uiState.update {
                    it.copy(
                        remainingTime = remaining.coerceAtLeast(0L),
                        isPaused = !it.isRunning && remaining > 0L && !it.isFinished,
                    )
                }
            }
        }
        viewModelScope.launch {
            service.timerInitial.collect { initial ->
                _uiState.update { it.copy(initialTime = initial.coerceAtLeast(0L)) }
            }
        }
        viewModelScope.launch {
            service.isTimerRunning.collect { running ->
                _uiState.update {
                    it.copy(
                        isRunning = running,
                        isPaused = !running && it.remainingTime > 0L && !it.isFinished,
                        isFinished = if (running) false else it.isFinished,
                    )
                }
            }
        }
        viewModelScope.launch {
            service.isTimerRinging.collect { ringing ->
                if (ringing) {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            isFinished = true,
                            isRinging = true,
                            isPaused = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isFinished = false,
                            isRinging = false
                        )
                    }
                }
            }
        }
    }

    fun onTimeSelectedChange(min: Int, sec: Int) {
        val safeMinutes = min.coerceIn(0, 999)
        val safeSeconds = sec.coerceIn(0, 59)
        val duration = durationMillis(safeMinutes, safeSeconds)
        val shouldStageDuration = !_uiState.value.isRunning

        if (shouldStageDuration) {
            stopRingtone()
        }

        _uiState.update {
            it.copy(
                selectedMinutes = safeMinutes,
                selectedSeconds = safeSeconds,
                remainingTime = if (shouldStageDuration) duration else it.remainingTime,
                initialTime = if (shouldStageDuration) duration else it.initialTime,
                isFinished = if (shouldStageDuration) false else it.isFinished,
                isRinging = false,
                isPaused = shouldStageDuration && duration > 0L,
            )
        }

        if (shouldStageDuration) {
            toolService?.setTimerInitial(duration)
        }

        viewModelScope.launch {
            settingsRepository.setLastTimerDuration(safeMinutes, safeSeconds)
        }
    }

    fun setTimer(minutes: Int, seconds: Int) {
        if (_uiState.value.isRunning) return
        val safeMin = minutes.coerceIn(0, 999)
        val safeSec = seconds.coerceIn(0, 59)
        val totalMillis = durationMillis(safeMin, safeSec)
        stopRingtone()
        _uiState.update {
            it.copy(
                selectedMinutes = safeMin,
                selectedSeconds = safeSec,
                remainingTime = totalMillis,
                initialTime = totalMillis,
                isFinished = false,
                isRinging = false,
                isPaused = totalMillis > 0L,
            )
        }
        toolService?.setTimerInitial(totalMillis)
        
        viewModelScope.launch {
            settingsRepository.setLastTimerDuration(safeMin, safeSec)
        }
    }

    fun addTime(millis: Long) {
        val state = _uiState.value
        val base = if (state.remainingTime > 0L) state.remainingTime else durationMillis(state.selectedMinutes, state.selectedSeconds)
        val newRemaining = (base + millis).coerceIn(0L, MAX_TIMER_MILLIS)
        val newInitial = maxOf(state.initialTime, newRemaining)
        stopRingtone()
        _uiState.update {
            it.copy(
                remainingTime = newRemaining,
                initialTime = newInitial,
                isFinished = false,
                isPaused = !it.isRunning && newRemaining > 0L,
            )
        }
        if (state.isRunning) {
            toolService?.startTimer(newRemaining, newInitial)
        } else {
            toolService?.setTimerInitial(newRemaining)
        }
    }

    fun toggleStartStop() {
        val state = _uiState.value
        if (state.isRunning) {
            toolService?.pauseTimer()
            return
        }

        val duration = when {
            state.remainingTime > 0L -> state.remainingTime
            else -> durationMillis(state.selectedMinutes, state.selectedSeconds)
        }
        if (duration <= 0L) return

        val initial = when {
            state.initialTime > 0L -> state.initialTime
            else -> duration
        }

        stopRingtone()
        toolService?.startTimer(duration, initial)
        _uiState.update {
            it.copy(
                remainingTime = duration,
                initialTime = initial,
                isRunning = true,
                isPaused = false,
                isFinished = false,
                isRinging = false,
            )
        }
        viewModelScope.launch {
            settingsRepository.recordTimerUsage(state.selectedMinutes, state.selectedSeconds)
        }
    }

    fun setRepeatLastDuration(enabled: Boolean) {
        _uiState.update { it.copy(repeatLastDuration = enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTimerKeepScreenOn(enabled) }
    }

    fun setGradualVolume(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTimerGradualVolume(enabled) }
    }

    fun toggleAlarms() {
        viewModelScope.launch {
            settingsRepository.setTimerNotifications(!_uiState.value.alarmsEnabled)
        }
    }

    fun toggleHaptic() {
        viewModelScope.launch {
            settingsRepository.setHapticFeedback(!hapticEnabled.value)
        }
    }

    fun playRingtone() {
        // Handled by ToolService
    }

    fun stopRingtone() {
        toolService?.stopAlarm()
        _uiState.update { it.copy(isFinished = false, isRinging = false) }
    }

    fun reset() {
        toolService?.resetTimer()
        stopRingtone()
        _uiState.update {
            it.copy(
                remainingTime = 0L,
                initialTime = 0L,
                isRunning = false,
                isFinished = false,
                isRinging = false,
                isPaused = false,
            )
        }
    }

    fun resetToInitial() {
        val initial = _uiState.value.initialTime
        if (initial <= 0L) {
            reset()
            return
        }
        toolService?.setTimerInitial(initial)
        stopRingtone()
        _uiState.update {
            it.copy(
                remainingTime = initial,
                isRunning = false,
                isFinished = false,
                isRinging = false,
                isPaused = true,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
        }
        mediaPlayer?.release()
    }

    private fun durationMillis(minutes: Int, seconds: Int): Long {
        val totalSeconds = minutes.coerceIn(0, 999) * 60L + seconds.coerceIn(0, 59)
        return (totalSeconds * 1000L).coerceIn(0L, MAX_TIMER_MILLIS)
    }

    private companion object {
        const val MAX_TIMER_MILLIS = 999L * 60L * 1000L + 59L * 1000L
    }
}
