package com.frerox.toolz.ui.screens.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

data class CompassState(
    val azimuth: Float = 0f,
    val qiblaAngle: Float? = null,
    val showQibla: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Int = 0
)

@HiltViewModel
class CompassViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(CompassState())
    val uiState: StateFlow<CompassState> = _uiState.asStateFlow()

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    init {
        viewModelScope.launch {
            settingsRepository.showQibla.collect { show ->
                _uiState.update { it.copy(showQibla = show) }
                if (show) updateLocationAndQibla()
            }
        }
    }

    private fun updateLocationAndQibla() {
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let {
                        val qibla = calculateQibla(it.latitude, it.longitude)
                        _uiState.update { state -> 
                            state.copy(
                                qiblaAngle = qibla,
                                latitude = it.latitude,
                                longitude = it.longitude
                            )
                        }
                    }
                }
        } catch (e: SecurityException) {}
    }

    private fun calculateQibla(lat: Double, lng: Double): Float {
        val kaabaLat = 21.422487
        val kaabaLng = 39.826206
        
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)
        val kLatRad = Math.toRadians(kaabaLat)
        val kLngRad = Math.toRadians(kaabaLng)
        
        val y = sin(kLngRad - lngRad)
        val x = cos(latRad) * tan(kLatRad) - sin(latRad) * cos(kLngRad - lngRad)
        
        var qibla = Math.toDegrees(atan2(y, x)).toFloat()
        return (qibla + 360) % 360
    }

    fun startListening() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        if (_uiState.value.showQibla) updateLocationAndQibla()
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, lastAccelerometer, lastMagnetometer)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val azimuthInRadians = orientation[0]
                var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()
                
                // Apply declination if location is available
                _uiState.value.latitude?.let { lat ->
                    _uiState.value.longitude?.let { lng ->
                        val declination = GeomagneticField(
                            lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()
                        ).declination
                        azimuthInDegrees += declination
                    }
                }

                _uiState.update { it.copy(azimuth = (azimuthInDegrees + 360) % 360) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            _uiState.update { it.copy(accuracy = accuracy) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
