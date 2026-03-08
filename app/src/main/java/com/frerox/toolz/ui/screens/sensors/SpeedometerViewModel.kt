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
import javax.inject.Inject
import kotlin.math.*

data class SpeedState(
    val speedKmh: Float = 0f,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isGpsEnabled: Boolean = true,
    val accuracy: Float = 0f
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
            
            var speed = if (location.hasSpeed()) location.speed else 0f
            
            // Manual calculation fallback if speed is 0 and we have a previous location
            if (speed == 0f && lastLocation != null) {
                val distance = location.distanceTo(lastLocation!!)
                val timeSec = (location.time - lastLocation!!.time) / 1000f
                if (timeSec > 0) {
                    val calculatedSpeed = distance / timeSec
                    // Only use calculated speed if it's reasonable and accuracy is good
                    if (calculatedSpeed < 100 && location.accuracy < 20) {
                        speed = calculatedSpeed
                    }
                }
            }
            
            lastLocation = location
            
            _speedState.value = SpeedState(
                speedKmh = speed * 3.6f, // Convert m/s to km/h
                altitude = location.altitude,
                latitude = location.latitude,
                longitude = location.longitude,
                isGpsEnabled = isLocationEnabled(),
                accuracy = location.accuracy
            )
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(0f)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
    }

    fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        lastLocation = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}
