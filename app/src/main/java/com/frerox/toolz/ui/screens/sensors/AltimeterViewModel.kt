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

data class AltimeterState(
    val altitudeMeters: Double = 0.0,
    val pressureHpa: Float = 0f,
    val source: String = "Detecting...",
    val maxAltitude: Double = 0.0,
    val minAltitude: Double = Double.MAX_VALUE,
    val accuracy: Float = 0f
)

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
                maxAltitude = maxOf(it.maxAltitude, altitude),
                minAltitude = if (it.minAltitude == Double.MAX_VALUE) altitude else minOf(it.minAltitude, altitude),
                accuracy = accuracy
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateDistanceMeters(1f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, context.mainLooper)
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
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
