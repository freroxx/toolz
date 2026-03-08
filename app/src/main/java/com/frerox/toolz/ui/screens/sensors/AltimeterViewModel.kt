package com.frerox.toolz.ui.screens.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
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
    val source: String = "Waiting..."
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
            if (pressureSensor == null) {
                result.lastLocation?.let { location ->
                    _uiState.update { it.copy(altitudeMeters = location.altitude, source = "GPS") }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (pressureSensor != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI)
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
            _uiState.update { it.copy(altitudeMeters = altitude.toDouble(), pressureHpa = pressure, source = "Barometer") }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
