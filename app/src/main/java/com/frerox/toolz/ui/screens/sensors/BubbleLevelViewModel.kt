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

package com.frerox.toolz.ui.screens.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** DEV-SPOOF: sampling rate of the simulated tilt stream (milliseconds). */
private const val SPOOF_TICK_MS = 50L

/**
 * State representing the inclination on X and Y axes.
 */
data class BubbleState(
    val x: Float = 0f,
    val y: Float = 0f,
    val rawX: Float = 0f,
    val rawY: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val isHeld: Boolean = false,
    val isListening: Boolean = false,
    val mode: LevelMode = LevelMode.BULLSEYE
)

enum class LevelMode {
    BULLSEYE, HORIZONTAL, VERTICAL
}

@HiltViewModel
class BubbleLevelViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: SpoofGate,
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _bubbleState = MutableStateFlow(BubbleState())
    val bubbleState: StateFlow<BubbleState> = _bubbleState.asStateFlow()

    /** DEV-SPOOF only: the synthetic tilt ticker replacing the accelerometer. */
    private var spoofTicker: Job? = null
    /** True while the screen wants the sensor running (start/stopListening parity). */
    private var listeningHardware = false

    init {
        // DEV-SPOOF: live switch — entering sandbox freezes the real sensor and a
        // simulated living tilt takes over; on exit real listening is re-armed if
        // the screen had asked for it.
        viewModelScope.launch {
            gate.active(DevToolId.SENSORS).collect { spoofed ->
                if (spoofed) {
                    sensorManager.unregisterListener(this@BubbleLevelViewModel)
                    startSpoofTicker()
                } else {
                    stopSpoofTicker()
                    if (listeningHardware) startListening()
                }
            }
        }
    }

    /** Runs the [SpoofSim] tilt stream in place of the accelerometer (idempotent). */
    private fun startSpoofTicker() {
        if (spoofTicker?.isActive == true) return
        spoofTicker = viewModelScope.launch {
            while (true) {
                if (!_bubbleState.value.isHeld) {
                    val t = SystemClock.elapsedRealtime()
                    val (degX, degY) = com.frerox.toolz.data.developer.SpoofSim.bubbleTilt(t)
                    // Same units the hardware path reports: axis values in m/s².
                    val axis = 9.81f // gravity sensor magnitude
                    val rawX = axis * kotlin.math.sin(Math.toRadians(3.5 * degX.toDouble())).toFloat()
                    val rawY = axis * kotlin.math.sin(Math.toRadians(3.5 * degY.toDouble())).toFloat()
                    currentX = rawX
                    currentY = rawY
                    _bubbleState.update {
                        it.copy(
                            rawX = rawX,
                            rawY = rawY,
                            x = rawX - it.offsetX,
                            y = rawY - it.offsetY,
                            isListening = true,
                        )
                    }
                }
                delay(SPOOF_TICK_MS)
            }
        }
        android.util.Log.w(
            "DevSpoof",
            "Bubble sandbox active — accelerometer unregistered, living tilt stream on",
        )
    }

    private fun stopSpoofTicker() {
        spoofTicker?.cancel()
        spoofTicker = null
    }

    fun startListening() {
        listeningHardware = true
        if (gate.sandboxed(DevToolId.SENSORS)) {
            startSpoofTicker()
            return
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            _bubbleState.update { it.copy(isListening = true) }
        }
    }

    // Low-pass filter constant
    private val alpha = 0.15f // Smoother for expressive feel
    private var currentX = 0f
    private var currentY = 0f

    fun stopListening() {
        listeningHardware = false
        // DEV-SPOOF: the simulated tilt lives only while the screen is up.
        stopSpoofTicker()
        sensorManager.unregisterListener(this)
        _bubbleState.update { it.copy(isListening = false) }
    }

    fun calibrate() {
        _bubbleState.update { 
            it.copy(
                offsetX = currentX,
                offsetY = currentY
            )
        }
    }

    fun resetCalibration() {
        _bubbleState.update { 
            it.copy(
                offsetX = 0f,
                offsetY = 0f
            )
        }
    }

    fun toggleHold() {
        _bubbleState.update { it.copy(isHeld = !it.isHeld) }
    }

    fun setMode(mode: LevelMode) {
        _bubbleState.update { it.copy(mode = mode) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // DEV-SPOOF: ignore real hardware while sandboxed.
        if (gate.sandboxed(DevToolId.SENSORS)) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && !_bubbleState.value.isHeld) {
            // Apply low-pass filter for smoother bubble movement
            currentX = currentX + alpha * (event.values[0] - currentX)
            currentY = currentY + alpha * (event.values[1] - currentY)
            
            _bubbleState.update { 
                it.copy(
                    rawX = currentX,
                    rawY = currentY,
                    x = currentX - it.offsetX,
                    y = currentY - it.offsetY
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
