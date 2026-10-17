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

package com.frerox.toolz.ui.screens.light

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.sensors.LightSensorManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** DEV-SPOOF: sampling rate of the simulated light stream (milliseconds). */
private const val SIM_TICK_MS = 250L

data class LightMeterState(
    val luxValue: Float = 0f,
    val history: List<Float> = emptyList(),
    val minLux: Float = Float.MAX_VALUE,
    val maxLux: Float = 0f,
    val avgLux: Float = 0f,
    val unit: LightUnit = LightUnit.LUX
)

enum class LightUnit {
    LUX, FOOT_CANDLE
}

@HiltViewModel
class LightMeterViewModel @Inject constructor(
    private val lightSensorManager: LightSensorManager,
    private val gate: com.frerox.toolz.data.developer.SpoofGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LightMeterState())
    val uiState: StateFlow<LightMeterState> = _uiState.asStateFlow()

    val hasSensor: Boolean = lightSensorManager.hasSensor()

    init {
        viewModelScope.launch {
            // DEV-SPOOF: sandboxed → the simulated light stream owns the meter and
            // the hardware stream is ignored; collecting real lux again the moment
            // the sandbox switches off (collectLatest cancels the active branch).
            gate.active(com.frerox.toolz.data.developer.DevToolId.SENSORS)
                .collectLatest { spoofed ->
                    if (spoofed) {
                        while (true) {
                            pushSensorValue(
                                com.frerox.toolz.data.developer.SpoofSim.lux(
                                    SystemClock.elapsedRealtime(),
                                    gate.params.value,
                                ),
                            )
                            delay(SIM_TICK_MS)
                        }
                    } else {
                        lightSensorManager.getLightLevel().collect { value ->
                            if (!gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SENSORS)) {
                                pushSensorValue(value)
                            }
                        }
                    }
                }
        }
    }

    private fun pushSensorValue(value: Float) {
        _uiState.update { currentState ->
            val newHistory = (currentState.history + value).takeLast(100)
            val newMax = maxOf(currentState.maxLux, value)
            val newAvg = if (newHistory.isNotEmpty()) newHistory.average().toFloat() else 0f

            currentState.copy(
                luxValue = value,
                history = newHistory,
                maxLux = newMax,
                minLux = if (currentState.minLux == Float.MAX_VALUE) value else {
                    minOf(currentState.minLux, value)
                },
                avgLux = newAvg
            )
        }
    }

    fun resetStats() {
        _uiState.update { it.copy(
            minLux = Float.MAX_VALUE,
            maxLux = 0f,
            avgLux = 0f,
            history = emptyList()
        ) }
    }

    fun toggleUnit() {
        _uiState.update { it.copy(unit = if (it.unit == LightUnit.LUX) LightUnit.FOOT_CANDLE else LightUnit.LUX) }
    }

    fun Float.toUnit(unit: LightUnit): Float {
        return if (unit == LightUnit.FOOT_CANDLE) this * 0.092903f else this
    }
}
