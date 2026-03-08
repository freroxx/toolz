package com.frerox.toolz.ui.screens.time

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.service.ToolService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StopwatchState(
    val elapsedTime: Long = 0L,
    val isRunning: Boolean = false,
    val laps: List<Long> = emptyList()
)

@HiltViewModel
class StopwatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(StopwatchState())
    val uiState: StateFlow<StopwatchState> = _uiState.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            
            // Sync with service state
            viewModelScope.launch {
                toolService?.stopwatchTime?.collect { time ->
                    _uiState.update { it.copy(elapsedTime = time) }
                }
            }
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
    }

    fun toggleStartStop() {
        val currentlyRunning = _uiState.value.isRunning
        if (currentlyRunning) {
            toolService?.pauseStopwatch()
        } else {
            toolService?.startStopwatch()
        }
        _uiState.update { it.copy(isRunning = !currentlyRunning) }
    }

    fun reset() {
        toolService?.resetStopwatch()
        _uiState.update { it.copy(elapsedTime = 0L, isRunning = false, laps = emptyList()) }
    }

    fun lap() {
        val currentTotal = _uiState.value.elapsedTime
        if (currentTotal > 0) {
            _uiState.update { it.copy(laps = listOf(currentTotal) + it.laps) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
    }
}
