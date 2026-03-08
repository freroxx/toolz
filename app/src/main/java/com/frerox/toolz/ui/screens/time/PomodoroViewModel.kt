package com.frerox.toolz.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PomodoroMode(val minutes: Int) {
    WORK(25), SHORT_BREAK(5), LONG_BREAK(15)
}

data class PomodoroState(
    val remainingTime: Long = PomodoroMode.WORK.minutes * 60 * 1000L,
    val mode: PomodoroMode = PomodoroMode.WORK,
    val isRunning: Boolean = false,
    val sessionsCompleted: Int = 0
)

@HiltViewModel
class PomodoroViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PomodoroState())
    val uiState: StateFlow<PomodoroState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun toggleStartStop() {
        if (_uiState.value.isRunning) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        _uiState.update { it.copy(isRunning = true) }
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingTime > 0) {
                delay(1000)
                _uiState.update { it.copy(remainingTime = (it.remainingTime - 1000).coerceAtLeast(0)) }
            }
            onSessionComplete()
        }
    }

    private fun pause() {
        _uiState.update { it.copy(isRunning = false) }
        timerJob?.cancel()
    }

    private fun onSessionComplete() {
        _uiState.update {
            val newSessions = if (it.mode == PomodoroMode.WORK) it.sessionsCompleted + 1 else it.sessionsCompleted
            val nextMode = when {
                it.mode == PomodoroMode.WORK && newSessions % 4 == 0 -> PomodoroMode.LONG_BREAK
                it.mode == PomodoroMode.WORK -> PomodoroMode.SHORT_BREAK
                else -> PomodoroMode.WORK
            }
            it.copy(
                mode = nextMode,
                remainingTime = nextMode.minutes * 60 * 1000L,
                isRunning = false,
                sessionsCompleted = newSessions
            )
        }
    }

    fun reset() {
        pause()
        _uiState.update { it.copy(remainingTime = it.mode.minutes * 60 * 1000L, isRunning = false) }
    }

    fun skip() {
        pause()
        onSessionComplete()
    }
}
