package com.frerox.toolz.ui.screens.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.*

data class SpeedState(
    val speedKmh: Float = 0f,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val totalDistanceMeters: Double = 0.0,
    val accuracy: Float = 0f,
    val isGpsEnabled: Boolean = true
)

@HiltViewModel
class SpeedometerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    
    private val _speedState = MutableStateFlow(SpeedState())
    val speedState: StateFlow<SpeedState> = _speedState.asStateFlow()

    private var lastLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            
            val speed = if (location.hasSpeed()) location.speed else {
                lastLocation?.let { last ->
                    val dist = location.distanceTo(last)
                    val time = (location.time - last.time) / 1000f
                    if (time > 0) dist / time else 0f
                } ?: 0f
            }
            
            val speedKmh = speed * 3.6f
            
            _speedState.update { state ->
                val newDistance = if (lastLocation != null) {
                    state.totalDistanceMeters + location.distanceTo(lastLocation!!)
                } else state.totalDistanceMeters

                state.copy(
                    speedKmh = speedKmh,
                    altitude = location.altitude,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    maxSpeedKmh = maxOf(state.maxSpeedKmh, speedKmh),
                    totalDistanceMeters = newDistance,
                    accuracy = location.accuracy,
                    isGpsEnabled = isLocationEnabled()
                )
            }
            lastLocation = location
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(0.5f)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
    }

    fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        lastLocation = null
    }

    fun resetStats() {
        _speedState.update { it.copy(maxSpeedKmh = 0f, totalDistanceMeters = 0.0) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}
