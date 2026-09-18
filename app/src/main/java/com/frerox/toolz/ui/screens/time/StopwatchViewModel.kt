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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StopwatchState(
    val elapsedTime: Long = 0L,
    val isRunning: Boolean = false,
    // Mirrors ToolService.stopwatchLaps — the service is the single truth (S-P1-02).
    val laps: List<Long> = emptyList(),
    val keepScreenOn: Boolean = true,
    val showMilliseconds: Boolean = true,
    val lastLapAt: Long = 0L,
    /** True once bound to the service; Start is disabled until then (S-P0-01). */
    val isBound: Boolean = false,
    /** True until persisted state preload + service bind complete (no 0/false flicker). */
    val isLoading: Boolean = true,
    /** True when the service restored the session after a reboot — UI snackbars once. */
    val restoredAfterReboot: Boolean = false,
    /** True when laps hit the 200 cap — UI toasts once. */
    val lapCapped: Boolean = false,
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
    private var preloadDone = false
    private var pendingToggle = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            _uiState.update { it.copy(isBound = true, isLoading = !preloadDone) }
            bindStopwatchFlows(binder.getService())
            if (pendingToggle) {
                pendingToggle = false
                toggleStartStop()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            toolService = null
            isBound = false
            _uiState.update { it.copy(isBound = false) }
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
        viewModelScope.launch {
            settingsRepository.stopwatchShowMs.collect { enabled ->
                _uiState.update { it.copy(showMilliseconds = enabled) }
            }
        }
        // Preload persisted session so first composition shows restored values
        // instead of flickering 0/paused while the async rebind lands (S-P1-03).
        viewModelScope.launch {
            try {
                val accumulated = settingsRepository.stopwatchAccumulated.first()
                val running = settingsRepository.stopwatchRunning.first()
                val laps = settingsRepository.parseStopwatchLaps(
                    settingsRepository.stopwatchLapsJson.first()
                )
                _uiState.update {
                    it.copy(
                        elapsedTime = accumulated.coerceAtLeast(0L),
                        isRunning = running,
                        laps = laps,
                    )
                }
            } catch (_: Exception) {
            } finally {
                preloadDone = true
                _uiState.update { it.copy(isLoading = !isBound) }
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
        viewModelScope.launch {
            service.stopwatchLaps.collect { laps ->
                _uiState.update { it.copy(laps = laps) }
            }
        }
        viewModelScope.launch {
            service.stopwatchRestoredAfterReboot.collect { restored ->
                _uiState.update { it.copy(restoredAfterReboot = restored) }
            }
        }
    }

    /**
     * Promote to a started foreground service BEFORE touching stopwatch state
     * (S-P0-01): bind-only (BIND_AUTO_CREATE) lets an unbind — navigate/swipe —
     * destroy a running session. The null-action start only runs ensureForeground().
     */
    private fun ensureServiceStarted() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ToolService::class.java))
        } catch (_: Exception) {
            try {
                context.startService(Intent(context, ToolService::class.java))
            } catch (_: Exception) {}
        }
    }

    fun toggleStartStop() {
        val service = toolService
        if (service == null || !isBound) {
            // Never silently drop (S-P0-01): start FGS + queue until bound.
            ensureServiceStarted()
            pendingToggle = true
            return
        }
        ensureServiceStarted()
        if (_uiState.value.isRunning) {
            service.pauseStopwatch()
        } else {
            service.startStopwatch()
        }
    }

    fun reset() {
        pendingToggle = false
        toolService?.resetStopwatch()
        _uiState.update {
            it.copy(
                elapsedTime = 0L,
                isRunning = false,
                laps = emptyList(),
                lastLapAt = 0L,
                lapCapped = false,
                restoredAfterReboot = false,
            )
        }
    }

    /**
     * Single truth lives in the service (S-P1-02): no local prepend, no dual-write.
     * Gated on running with 500ms debounce + 200 cap inside the service.
     */
    fun lap() {
        val service = toolService ?: return
        if (!_uiState.value.isRunning) return
        val recorded = try {
            service.addStopwatchLap()
        } catch (_: Exception) {
            false
        }
        if (recorded) {
            _uiState.update { it.copy(lastLapAt = it.elapsedTime, lapCapped = false) }
        } else if (service.stopwatchLaps.value.size >= SettingsRepository.STOPWATCH_MAX_LAPS) {
            _uiState.update { it.copy(lapCapped = true) }
        }
    }

    fun consumeLapCapped() {
        _uiState.update { it.copy(lapCapped = false) }
    }

    fun consumeRestoreFlag() {
        _uiState.update { it.copy(restoredAfterReboot = false) }
        try {
            toolService?.consumeStopwatchRestoreFlag()
        } catch (_: Exception) {}
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStopwatchKeepScreenOn(enabled) }
    }

    fun setShowMilliseconds(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStopwatchShowMs(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try {
                context.unbindService(connection)
            } catch (_: Exception) {}
            isBound = false
        }
    }
}
