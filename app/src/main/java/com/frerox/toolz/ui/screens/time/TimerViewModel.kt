package com.frerox.toolz.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerState(
    val remainingTime: Long = 0L,
    val initialTime: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false
)

@HiltViewModel
class TimerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(TimerState())
    val uiState: StateFlow<TimerState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun setTimer(minutes: Int, seconds: Int) {
        val totalMillis = (minutes * 60 + seconds) * 1000L
        _uiState.update { it.copy(remainingTime = totalMillis, initialTime = totalMillis, isFinished = false) }
    }

    fun toggleStartStop() {
        if (_uiState.value.isRunning) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        if (_uiState.value.remainingTime <= 0) return
        
        _uiState.update { it.copy(isRunning = true, isFinished = false) }
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingTime > 0) {
                delay(100)
                _uiState.update { it.copy(remainingTime = (it.remainingTime - 100).coerceAtLeast(0)) }
            }
            _uiState.update { it.copy(isRunning = false, isFinished = true) }
        }
    }

    private fun pause() {
        _uiState.update { it.copy(isRunning = false) }
        timerJob?.cancel()
    }

    fun reset() {
        pause()
        _uiState.update { it.copy(remainingTime = it.initialTime, isFinished = false) }
    }
}
