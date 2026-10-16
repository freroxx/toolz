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

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Supported elevation units.
 */
enum class AltitudeUnit(val label: String, val factor: Float) {
    METERS("METERS", 1.0f),
    FEET("FEET", 3.28084f)
}

/**
 * Supported atmospheric pressure units.
 */
enum class PressureUnit(val label: String, val factor: Float) {
    HPA("hPa", 1.0f),
    INHG("inHg", 0.02953f),
    MBAR("mbar", 1.0f)
}

/**
 * State representation for the precision altimeter.
 */
data class AltimeterState(
    val altitudeMeters: Double = 0.0,
    val pressureHpa: Float = 0f,
    val source: String = "Detecting...",
    val maxAltitudeMeters: Double = 0.0,
    val minAltitudeMeters: Double = Double.MAX_VALUE,
    val accuracy: Float = 0f,
    val unit: AltitudeUnit = AltitudeUnit.METERS,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val isTracking: Boolean = false,
    val referenceAltitudeMeters: Double? = null,
    val climbRateMps: Double = 0.0,
    val lastUpdateTime: Long = 0L
) {
    val altitudeDisplay: Double get() = altitudeMeters * unit.factor
    val maxAltitudeDisplay: Double get() = maxAltitudeMeters * unit.factor
    val minAltitudeDisplay: Double get() = if (minAltitudeMeters == Double.MAX_VALUE) 0.0 else minAltitudeMeters * unit.factor
    val pressureDisplay: Float get() = pressureHpa * pressureUnit.factor
    val relativeAltitudeDisplay: Double? get() = referenceAltitudeMeters?.let { (altitudeMeters - it) * unit.factor }
    val climbRateDisplay: Double get() = climbRateMps * unit.factor
}

@HiltViewModel
class AltimeterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: com.frerox.toolz.data.developer.SpoofGate,
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _uiState = MutableStateFlow(AltimeterState())
    val uiState: StateFlow<AltimeterState> = _uiState.asStateFlow()

    init {
        // DEV-SPOOF: live switch — sandbox freezes barometer+GPS, fixture takes over.
        viewModelScope.launch {
            gate.active(com.frerox.toolz.data.developer.DevToolId.SENSORS).collect { spoofed ->
                if (spoofed) {
                    sensorManager.unregisterListener(this@AltimeterViewModel)
                    try { locationManager.removeUpdates(locationListener) } catch (_: Exception) { }
                    emitSpoof()
                }
            }
        }
    }

    private fun emitSpoof() {
        // DEV-SPOOF fixture: Everest base-camp-ish altitude, RAM-only, no GPS/baro.
        val p = gate.params.value
        _uiState.update {
            it.copy(
                altitudeMeters = p.altitudeM.toDouble(),
                pressureHpa = 1008.5f,
                source = "SPOOF",
                maxAltitudeMeters = p.altitudeM.toDouble(),
                minAltitudeMeters = p.altitudeM.toDouble(),
                accuracy = 1f,
                climbRateMps = 0.0,
                isTracking = true,
            )
        }
        android.util.Log.w("DevSpoof", "Altimeter sandbox active — GPS+baro off, alt=${p.altitudeM}m")
    }

    private val locationListener = LocationListener { location ->
        // DEV-SPOOF: ignore GPS while sandboxed.
        if (gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SENSORS)) return@LocationListener
        // Use GPS as a baseline or fallback if barometer is absent or uninitialized
        if (pressureSensor == null || _uiState.value.pressureHpa == 0f) {
            updateAltitude(location.altitude, "GPS", location.accuracy)
        }
    }

    private fun updateAltitude(altitude: Double, source: String, accuracy: Float = 0f) {
        val currentTime = System.currentTimeMillis()
        _uiState.update { state ->
            val timeDelta = if (state.lastUpdateTime > 0) (currentTime - state.lastUpdateTime) / 1000.0 else 0.0
            val altitudeDelta = altitude - state.altitudeMeters
            val rawClimbRate = if (timeDelta > 0.1) altitudeDelta / timeDelta else state.climbRateMps

            // Apply simple low-pass filter to smooth climb rate
            val smoothedClimbRate = state.climbRateMps * 0.8 + rawClimbRate * 0.2

            state.copy(
                altitudeMeters = altitude,
                source = source,
                maxAltitudeMeters = maxOf(state.maxAltitudeMeters, altitude),
                minAltitudeMeters = if (state.minAltitudeMeters == Double.MAX_VALUE) altitude else minOf(state.minAltitudeMeters, altitude),
                accuracy = accuracy,
                climbRateMps = if (timeDelta > 0) smoothedClimbRate else 0.0,
                lastUpdateTime = currentTime
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        // DEV-SPOOF: never touch barometer/GPS while sandboxed.
        if (gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SENSORS)) {
            emitSpoof()
            return
        }
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI)
        }
        // 3 000 ms interval, 1 m minimum distance — matches the old FusedLocation request
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            3_000L,
            1.0f,
            locationListener
        )
        _uiState.update { it.copy(isTracking = true) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(locationListener)
        _uiState.update { it.copy(isTracking = false) }
    }

    fun toggleUnit() {
        _uiState.update { state ->
            val nextUnit = if (state.unit == AltitudeUnit.METERS) AltitudeUnit.FEET else AltitudeUnit.METERS
            state.copy(unit = nextUnit)
        }
    }

    fun togglePressureUnit() {
        _uiState.update { state ->
            val units = PressureUnit.entries
            val nextIndex = (state.pressureUnit.ordinal + 1) % units.size
            state.copy(pressureUnit = units[nextIndex])
        }
    }

    fun resetStats() {
        _uiState.update { it.copy(maxAltitudeMeters = it.altitudeMeters, minAltitudeMeters = it.altitudeMeters, climbRateMps = 0.0) }
    }

    fun setReferenceAltitude() {
        _uiState.update { it.copy(referenceAltitudeMeters = it.altitudeMeters) }
    }

    fun clearReferenceAltitude() {
        _uiState.update { it.copy(referenceAltitudeMeters = null) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // DEV-SPOOF: ignore barometer while sandboxed.
        if (gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.SENSORS)) return
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            // Standard Barometric formula for altitude calculation
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            _uiState.update { it.copy(pressureHpa = pressure) }
            updateAltitude(altitude.toDouble(), "Barometer")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
