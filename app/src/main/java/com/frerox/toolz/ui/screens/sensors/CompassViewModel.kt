package com.frerox.toolz.ui.screens.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    val accuracy: Int = 0,
    val isLevel: Boolean = true,
    val pitch: Float = 0f,
    val roll: Float = 0f
)

@HiltViewModel
class CompassViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val _uiState = MutableStateFlow(CompassState())
    val uiState: StateFlow<CompassState> = _uiState.asStateFlow()

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    
    private val alpha = 0.95f
    private var filteredAzimuth = 0f

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
        val kaabaLat = Math.toRadians(21.422487)
        val kaabaLng = Math.toRadians(39.826206)
        
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)
        
        val y = sin(kaabaLng - lngRad)
        val x = cos(latRad) * tan(kaabaLat) - sin(latRad) * cos(kaabaLng - lngRad)
        
        var qibla = Math.toDegrees(atan2(y, x)).toFloat()
        return (qibla + 360) % 360
    }

    fun startListening() {
        if (rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
        } else {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, lastAccelerometer, 0, 3)
                lastAccelerometerSet = true
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, lastMagnetometer, 0, 3)
                lastMagnetometerSet = true
            }

            if (lastAccelerometerSet && lastMagnetometerSet) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)) {
                    SensorManager.getOrientation(rotationMatrix, orientation)
                } else return
            } else return
        }

        var azimuthInDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

        _uiState.value.latitude?.let { lat ->
            _uiState.value.longitude?.let { lng ->
                val declination = GeomagneticField(
                    lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()
                ).declination
                azimuthInDegrees += declination
            }
        }

        val targetAzimuth = (azimuthInDegrees + 360) % 360
        filteredAzimuth = normalizeAngle(filteredAzimuth + shortestAngleDist(filteredAzimuth, targetAzimuth) * (1 - alpha))
        
        _uiState.update { it.copy(
            azimuth = filteredAzimuth,
            pitch = pitch,
            roll = roll,
            isLevel = abs(pitch) < 5 && abs(roll) < 5 // Polished: Sharper threshold
        ) }
    }
    
    private fun shortestAngleDist(a: Float, b: Float): Float {
        var d = b - a
        while (d < -180) d += 360
        while (d > 180) d -= 360
        return d
    }

    private fun normalizeAngle(a: Float): Float {
        var ang = a
        while (ang < 0) ang += 360
        while (ang >= 360) ang -= 360
        return ang
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            _uiState.update { it.copy(accuracy = accuracy) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
