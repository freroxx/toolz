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

/**
 * Supported velocity units with conversion factors.
 */
enum class SpeedUnit(val label: String, val factor: Float, val precision: Int = 0) {
    KMH("KM/H", 3.6f, 0),
    MPH("MPH", 2.23694f, 0),
    KNOTS("KNOTS", 1.94384f, 1)
}

/**
 * State representation for the high-precision speedometer.
 */
data class SpeedState(
    val speedMps: Float = 0f,
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val maxSpeedMps: Float = 0f,
    val totalDistanceMeters: Double = 0.0,
    val accuracy: Float = 0f,
    val isGpsEnabled: Boolean = true,
    val unit: SpeedUnit = SpeedUnit.KMH,
    val isTracking: Boolean = false
) {
    val speedDisplay: Float get() = speedMps * unit.factor
    val maxSpeedDisplay: Float get() = maxSpeedMps * unit.factor
    
    // Derived progress for gauges (0.0 to 1.0) based on typical max speeds
    val speedProgress: Float get() = (speedDisplay / 160f).coerceIn(0f, 1f)
}

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
            
            val speedMps = if (location.hasSpeed()) location.speed else {
                lastLocation?.let { last ->
                    val dist = location.distanceTo(last)
                    val time = (location.time - last.time) / 1000f
                    if (time > 0) dist / time else 0f
                } ?: 0f
            }
            
            _speedState.update { state ->
                val newDistance = if (lastLocation != null) {
                    state.totalDistanceMeters + location.distanceTo(lastLocation!!)
                } else state.totalDistanceMeters

                state.copy(
                    speedMps = if (speedMps < 0.5f) 0f else speedMps, // Noise filtering
                    altitude = location.altitude,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    maxSpeedMps = maxOf(state.maxSpeedMps, speedMps),
                    totalDistanceMeters = newDistance,
                    accuracy = location.accuracy,
                    isGpsEnabled = isLocationEnabled()
                )
            }
            lastLocation = location
        }

        override fun onLocationAvailability(availability: LocationAvailability) {
            _speedState.update { it.copy(isGpsEnabled = availability.isLocationAvailable) }
        }
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(0.5f)
            .setWaitForAccurateLocation(false)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
        _speedState.update { it.copy(isTracking = true) }
    }

    fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        lastLocation = null
        _speedState.update { it.copy(isTracking = false, speedMps = 0f) }
    }

    fun toggleUnit() {
        _speedState.update { state ->
            val units = SpeedUnit.entries
            val nextIndex = (state.unit.ordinal + 1) % units.size
            state.copy(unit = units[nextIndex])
        }
    }

    fun resetStats() {
        _speedState.update { it.copy(maxSpeedMps = 0f, totalDistanceMeters = 0.0) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTracking()
    }
}
