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
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.*

data class CompassState(
    val azimuth: Float = 0f,
    val displayAzimuth: Float = 0f, // Normalized 0-360 for UI text
    val targetHeading: Float? = null,
    val isFullScreen: Boolean = false,
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
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _uiState = MutableStateFlow(CompassState())
    val uiState: StateFlow<CompassState> = _uiState.asStateFlow()

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false
    
    private var currentAzimuth = 0f

    init {
        viewModelScope.launch {
            settingsRepository.showQibla.collect { show ->
                _uiState.update { it.copy(showQibla = show) }
                if (show) updateLocationAndQibla()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationAndQibla() {
        try {
            val lastKnown = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            } catch (e: Exception) {
                null
            }

            if (lastKnown != null) {
                applyLocation(lastKnown)
            }

            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> LocationManager.PASSIVE_PROVIDER
            }

            locationManager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context)
            ) { location ->
                location?.let { applyLocation(it) }
            }
        } catch (e: SecurityException) {
        } catch (e: Exception) {}
    }

    private fun applyLocation(location: Location) {
        val qibla = calculateQibla(location.latitude, location.longitude)
        _uiState.update { state -> 
            state.copy(
                qiblaAngle = qibla,
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
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

    fun setTargetHeading(heading: Float?) {
        _uiState.update { it.copy(targetHeading = heading) }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
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

        // Fix: Smooth rotation without 360-degree jump
        val targetAzimuth = (azimuthInDegrees + 360) % 360
        val delta = shortestAngleDist(currentAzimuth % 360, targetAzimuth)
        currentAzimuth += delta
        
        _uiState.update { it.copy(
            azimuth = currentAzimuth,
            displayAzimuth = targetAzimuth,
            pitch = pitch,
            roll = roll,
            isLevel = abs(pitch) < 5 && abs(roll) < 5
        ) }
    }
    
    private fun shortestAngleDist(a: Float, b: Float): Float {
        var d = b - a
        while (d < -180) d += 360
        while (d > 180) d -= 360
        return d
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
