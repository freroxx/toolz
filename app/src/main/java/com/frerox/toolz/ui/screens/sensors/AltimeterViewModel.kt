package com.frerox.toolz.ui.screens.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val isTracking: Boolean = false
) {
    val altitudeDisplay: Double get() = altitudeMeters * unit.factor
    val maxAltitudeDisplay: Double get() = maxAltitudeMeters * unit.factor
    val minAltitudeDisplay: Double get() = if (minAltitudeMeters == Double.MAX_VALUE) 0.0 else minAltitudeMeters * unit.factor
    val pressureDisplay: Float get() = pressureHpa * pressureUnit.factor
}

@HiltViewModel
class AltimeterViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(AltimeterState())
    val uiState: StateFlow<AltimeterState> = _uiState.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            // Use GPS as a baseline or fallback if barometer is absent or uninitialized
            if (pressureSensor == null || _uiState.value.pressureHpa == 0f) {
                updateAltitude(location.altitude, "GPS", location.accuracy)
            }
        }
    }

    private fun updateAltitude(altitude: Double, source: String, accuracy: Float = 0f) {
        _uiState.update { 
            it.copy(
                altitudeMeters = altitude,
                source = source,
                maxAltitudeMeters = maxOf(it.maxAltitudeMeters, altitude),
                minAltitudeMeters = if (it.minAltitudeMeters == Double.MAX_VALUE) altitude else minOf(it.minAltitudeMeters, altitude),
                accuracy = accuracy
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI)
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(1f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
        _uiState.update { it.copy(isTracking = true) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
        _uiState.update { it.copy(maxAltitudeMeters = it.altitudeMeters, minAltitudeMeters = it.altitudeMeters) }
    }

    override fun onSensorChanged(event: SensorEvent) {
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
