package com.frerox.toolz.ui.screens.time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StopwatchState(
    val elapsedTime: Long = 0L,
    val isRunning: Boolean = false,
    val laps: List<Long> = emptyList()
)

@HiltViewModel
class StopwatchViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StopwatchState())
    val uiState: StateFlow<StopwatchState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var lastTimestamp: Long = 0L

    fun toggleStartStop() {
        if (_uiState.value.isRunning) {
            stop()
        } else {
            start()
        }
    }

    private fun start() {
        _uiState.update { it.copy(isRunning = true) }
        lastTimestamp = System.currentTimeMillis()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(10)
                val current = System.currentTimeMillis()
                val diff = current - lastTimestamp
                lastTimestamp = current
                _uiState.update { it.copy(elapsedTime = it.elapsedTime + diff) }
            }
        }
    }

    private fun stop() {
        _uiState.update { it.copy(isRunning = false) }
        timerJob?.cancel()
    }

    fun reset() {
        stop()
        _uiState.update { StopwatchState() }
    }

    fun lap() {
        val currentTotal = _uiState.value.elapsedTime
        _uiState.update { it.copy(laps = listOf(currentTotal) + it.laps) }
    }
}
