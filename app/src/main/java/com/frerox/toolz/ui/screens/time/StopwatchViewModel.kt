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
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StopwatchState(
    val elapsedTime: Long = 0L,
    val isRunning: Boolean = false,
    val laps: List<Long> = emptyList(),
    val keepScreenOn: Boolean = true,
    val showMilliseconds: Boolean = true,
    val lastLapAt: Long = 0L,
)

@HiltViewModel
class StopwatchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
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
            bindStopwatchFlows(binder.getService())
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
            settingsRepository.stopwatchKeepScreenOn.collect { enabled ->
                _uiState.update { it.copy(keepScreenOn = enabled) }
            }
        }
    }

    private fun bindStopwatchFlows(service: ToolService) {
        viewModelScope.launch {
            service.stopwatchTime.collect { time ->
                _uiState.update { it.copy(elapsedTime = time.coerceAtLeast(0L)) }
            }
        }
        viewModelScope.launch {
            service.isStopwatchRunning.collect { running ->
                _uiState.update { it.copy(isRunning = running) }
            }
        }
    }

    fun toggleStartStop() {
        if (_uiState.value.isRunning) {
            toolService?.pauseStopwatch()
        } else {
            toolService?.startStopwatch()
        }
    }

    fun reset() {
        toolService?.resetStopwatch()
        _uiState.update {
            it.copy(
                elapsedTime = 0L,
                isRunning = false,
                laps = emptyList(),
                lastLapAt = 0L,
            )
        }
    }

    fun lap() {
        val currentTotal = _uiState.value.elapsedTime
        if (currentTotal <= 0L) return
        _uiState.update {
            if (it.laps.firstOrNull() == currentTotal) {
                it
            } else {
                it.copy(laps = listOf(currentTotal) + it.laps, lastLapAt = currentTotal)
            }
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStopwatchKeepScreenOn(enabled) }
    }

    fun setShowMilliseconds(enabled: Boolean) {
        _uiState.update { it.copy(showMilliseconds = enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
    }
}
