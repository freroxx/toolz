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
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

// region Tuning constants

/** Number of one-second samples kept for the trend chart. */
const val SPEED_HISTORY_SIZE = 60

private const val UPDATE_INTERVAL_MS = 1_000L
private const val TICK_MS = 1_000L

/** Fixes worse than this are shown as "poor signal" but never used for speed or distance. */
private const val MAX_USABLE_ACCURACY_M = 50f
private const val GOOD_ACCURACY_M = 10f
private const val FAIR_ACCURACY_M = 25f

/** Below ~1.8 km/h a GPS speed is indistinguishable from drift. */
private const val STATIONARY_THRESHOLD_MPS = 0.5f
private const val MIN_BEARING_SPEED_MPS = 1f

/** With no usable fix for this long, the speed reads zero instead of freezing on the last value. */
private const val STALE_SPEED_AFTER_MS = 3_000L
private const val SIGNAL_LOST_AFTER_MS = 5_000L

/** Gaps longer than this between recorded fixes are not integrated into distance or moving time. */
private const val MAX_TRIP_GAP_MS = 5_000L

private const val LIMIT_STEP = 5
private const val GAUGE_GROW_FACTOR = 0.92f
private const val GAUGE_SHRINK_FACTOR = 0.8f

// endregion

// region Models

/** Supported speed units. Each unit also decides its distance and altitude units. */
enum class SpeedUnit(
    val label: String,
    private val mpsFactor: Float,
    val distanceLabel: String,
    private val metersPerDistanceUnit: Double,
    val altitudeLabel: String,
    private val altitudeFactor: Double,
    val limitMin: Int,
    val limitMax: Int,
    private val gaugeMaxes: List<Float>,
) {
    KMH("km/h", 3.6f, "km", 1_000.0, "m", 1.0, 10, 200, listOf(60f, 120f, 180f, 240f, 300f)),
    MPH("mph", 2.236936f, "mi", 1_609.344, "ft", 3.28084, 10, 125, listOf(60f, 120f, 180f)),
    KNOTS("kn", 1.943844f, "nmi", 1_852.0, "m", 1.0, 5, 100, listOf(30f, 60f, 90f, 120f)),
    MS("m/s", 1f, "km", 1_000.0, "m", 1.0, 5, 60, listOf(30f, 60f, 90f));

    fun toDisplay(mps: Float): Float = mps * mpsFactor
    fun toMps(value: Float): Float = value / mpsFactor
    fun toDistance(meters: Double): Double = meters / metersPerDistanceUnit
    fun toAltitude(meters: Double): Double = meters * altitudeFactor

    /** Smallest gauge scale that comfortably fits [demand]. */
    private fun gaugeMaxFor(demand: Float): Float =
        gaugeMaxes.firstOrNull { demand <= it * GAUGE_GROW_FACTOR } ?: gaugeMaxes.last()

    /**
     * Picks the next gauge scale. Grows immediately, but only shrinks once the demand is well
     * below the smaller scale, so the dial doesn't flip back and forth around a boundary.
     */
    fun nextGaugeMax(current: Float, demand: Float): Float {
        val needed = gaugeMaxFor(demand)
        return when {
            current !in gaugeMaxes -> needed
            needed > current -> needed
            needed < current && demand < needed * GAUGE_SHRINK_FACTOR -> needed
            else -> current
        }
    }
}

enum class GpsSignal { DISABLED, SEARCHING, POOR, FAIR, GOOD }

enum class TripState { IDLE, RECORDING, PAUSED }

/** Which axis the HUD mirrors on. Depends on how the phone sits against the windshield. */
enum class HudFlip { VERTICAL, HORIZONTAL }

@Immutable
data class SpeedSettings(
    val unit: SpeedUnit = SpeedUnit.KMH,
    val speedLimitEnabled: Boolean = false,
    /** Expressed in [unit]. */
    val speedLimit: Int = 100,
    val keepScreenOn: Boolean = true,
    val hudFlip: HudFlip = HudFlip.VERTICAL,
)

@Immutable
data class SpeedUiState(
    val settings: SpeedSettings = SpeedSettings(),
    val speedMps: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val distanceMeters: Double = 0.0,
    val movingTimeMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val altitudeMeters: Double? = null,
    val bearingDegrees: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val signal: GpsSignal = GpsSignal.SEARCHING,
    val tripState: TripState = TripState.IDLE,
    val speedHistory: List<Float> = emptyList(),
    /** Current gauge scale in display units. Managed by the ViewModel (see [SpeedUnit.nextGaugeMax]). */
    val gaugeMax: Float = 0f,
    val isHudMode: Boolean = false,
) {
    val unit: SpeedUnit get() = settings.unit
    val speedDisplay: Float get() = unit.toDisplay(speedMps)
    val maxSpeedDisplay: Float get() = unit.toDisplay(maxSpeedMps)
    val averageSpeedMps: Float
        get() = if (movingTimeMs > 0L) (distanceMeters / (movingTimeMs / 1_000.0)).toFloat() else 0f
    val averageSpeedDisplay: Float get() = unit.toDisplay(averageSpeedMps)

    /** Compared on the rounded value so the alert matches the number the user reads. */
    val isOverLimit: Boolean
        get() = settings.speedLimitEnabled && speedDisplay.roundToInt() > settings.speedLimit
}

/** What a trip looked like before a reset, so the reset can be undone. */
@Immutable
data class TripSnapshot(
    val tripState: TripState,
    val maxSpeedMps: Float,
    val distanceMeters: Double,
    val movingTimeMs: Long,
    val elapsedMs: Long,
)

// endregion

// region Data sources

sealed interface GpsEvent {
    data class Fix(val location: Location) : GpsEvent
    data class Satellites(val used: Int, val visible: Int) : GpsEvent
}

/**
 * Wraps [LocationManager] as a cold [Flow]. Updates are registered on collection and removed
 * when the collector is cancelled, so nothing can leak.
 *
 * Requires minSdk 24 (GNSS status).
 */
class GpsSource @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val isEnabled: Boolean
        get() = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun events(): Flow<GpsEvent> = callbackFlow {
        // minDistance = 0 on purpose: with a distance filter the OS stops delivering fixes when the
        // device is stationary, and the last (non-zero) speed would stay on screen.
        val locationListener = LocationListener { trySend(GpsEvent.Fix(it)) }

        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                trySend(GpsEvent.Satellites(used = used, visible = status.satelliteCount))
            }
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                locationListener,
                Looper.getMainLooper(),
            )
            locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            close()
        } catch (e: IllegalArgumentException) {
            close()
        }

        awaitClose {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
        }
    }
}

/** Small SharedPreferences-backed store for the user's speedometer settings. */
class SpeedometerPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): SpeedSettings {
        val defaults = SpeedSettings()
        return SpeedSettings(
            unit = prefs.getString(KEY_UNIT, null)
                ?.let { name -> SpeedUnit.entries.firstOrNull { it.name == name } }
                ?: defaults.unit,
            speedLimitEnabled = prefs.getBoolean(KEY_LIMIT_ENABLED, defaults.speedLimitEnabled),
            speedLimit = prefs.getInt(KEY_LIMIT, defaults.speedLimit),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, defaults.keepScreenOn),
            hudFlip = prefs.getString(KEY_HUD_FLIP, null)
                ?.let { name -> HudFlip.entries.firstOrNull { it.name == name } }
                ?: defaults.hudFlip,
        )
    }

    fun save(settings: SpeedSettings) {
        prefs.edit()
            .putString(KEY_UNIT, settings.unit.name)
            .putBoolean(KEY_LIMIT_ENABLED, settings.speedLimitEnabled)
            .putInt(KEY_LIMIT, settings.speedLimit)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putString(KEY_HUD_FLIP, settings.hudFlip.name)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "speedometer"
        const val KEY_UNIT = "unit"
        const val KEY_LIMIT_ENABLED = "limit_enabled"
        const val KEY_LIMIT = "limit"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_HUD_FLIP = "hud_flip"
    }
}

// endregion

@HiltViewModel
class SpeedometerViewModel @Inject constructor(
    private val gps: GpsSource,
    private val prefs: SpeedometerPrefs,
) : ViewModel() {

    private val _state = MutableStateFlow(SpeedUiState(settings = prefs.load()).withGauge())
    val state: StateFlow<SpeedUiState> = _state.asStateFlow()

    private var updatesJob: Job? = null

    // All of the below is only touched on the main thread (viewModelScope + main-looper callbacks).
    private var lastFixAtMs: Long? = null
    private var lastUsableFixAtMs: Long? = null
    private var previousUsableFix: Location? = null
    private var lastTripFixNanos: Long? = null
    private var segmentStartMs = 0L
    private var committedElapsedMs = 0L

    // region Lifecycle

    /** Starts listening for GPS. Call when the screen is visible and location permission is granted. */
    fun startUpdates() {
        if (updatesJob?.isActive == true) return

        if (_state.value.tripState == TripState.RECORDING) {
            segmentStartMs = SystemClock.elapsedRealtime()
            lastTripFixNanos = null
        }
        updatesJob = viewModelScope.launch {
            launch { gps.events().collect(::onEvent) }
            launch {
                while (true) {
                    delay(TICK_MS)
                    onTick()
                }
            }
        }
    }

    /** Stops listening. A recording trip is paused implicitly: no fixes arrive and no time is counted. */
    fun stopUpdates() {
        updatesJob?.cancel()
        updatesJob = null

        if (_state.value.tripState == TripState.RECORDING) {
            val now = SystemClock.elapsedRealtime()
            committedElapsedMs += now - segmentStartMs
            segmentStartMs = now
        }
        lastFixAtMs = null
        lastUsableFixAtMs = null
        previousUsableFix = null
        lastTripFixNanos = null

        updateState {
            it.copy(
                speedMps = 0f,
                signal = if (it.signal == GpsSignal.DISABLED) GpsSignal.DISABLED else GpsSignal.SEARCHING,
                elapsedMs = committedElapsedMs,
            )
        }
    }

    // endregion

    // region GPS handling

    private fun onEvent(event: GpsEvent) {
        when (event) {
            is GpsEvent.Fix -> onFix(event.location)
            is GpsEvent.Satellites -> updateState {
                it.copy(satellitesUsed = event.used, satellitesVisible = event.visible)
            }
        }
    }

    private fun onFix(location: Location) {
        val now = SystemClock.elapsedRealtime()
        lastFixAtMs = now

        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        if (accuracy == null || accuracy > MAX_USABLE_ACCURACY_M) {
            updateState { it.copy(accuracyMeters = accuracy, signal = GpsSignal.POOR) }
            return
        }

        lastUsableFixAtMs = now
        val speed = resolveSpeed(location)
        previousUsableFix = location

        val current = _state.value
        val recording = current.tripState == TripState.RECORDING
        var distance = current.distanceMeters
        var moving = current.movingTimeMs
        if (recording) {
            val dtMs = lastTripFixNanos?.let { (location.elapsedRealtimeNanos - it) / 1_000_000L }
            if (dtMs != null && dtMs in 1L..MAX_TRIP_GAP_MS && speed > 0f) {
                // Integrating the Doppler speed is steadier than summing position deltas, which jitter.
                distance += speed.toDouble() * dtMs / 1_000.0
                moving += dtMs
            }
            lastTripFixNanos = location.elapsedRealtimeNanos
        }

        val bearing = if (speed >= MIN_BEARING_SPEED_MPS && location.hasBearing()) {
            location.bearing
        } else {
            current.bearingDegrees
        }
        val signal = when {
            accuracy <= GOOD_ACCURACY_M -> GpsSignal.GOOD
            accuracy <= FAIR_ACCURACY_M -> GpsSignal.FAIR
            else -> GpsSignal.POOR
        }

        updateState {
            it.copy(
                speedMps = speed,
                maxSpeedMps = if (recording) max(it.maxSpeedMps, speed) else it.maxSpeedMps,
                distanceMeters = distance,
                movingTimeMs = moving,
                altitudeMeters = if (location.hasAltitude()) location.altitude else it.altitudeMeters,
                bearingDegrees = bearing,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = accuracy,
                signal = signal,
                speedHistory = it.speedHistory.pushed(speed),
            )
        }
    }

    private fun resolveSpeed(location: Location): Float {
        val raw = if (location.hasSpeed()) location.speed else derivedSpeed(location)
        return if (raw < STATIONARY_THRESHOLD_MPS) 0f else raw
    }

    private fun derivedSpeed(location: Location): Float {
        val previous = previousUsableFix ?: return 0f
        val seconds = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1e9f
        return if (seconds > 0f) location.distanceTo(previous) / seconds else 0f
    }

    /** Runs once a second: detects lost signal, zeroes stale speed and advances the trip clock. */
    private fun onTick() {
        val now = SystemClock.elapsedRealtime()
        val enabled = gps.isEnabled
        val hasRecentFix = lastFixAtMs?.let { now - it <= SIGNAL_LOST_AFTER_MS } == true
        val hasRecentUsableFix = lastUsableFixAtMs?.let { now - it <= STALE_SPEED_AFTER_MS } == true
        val recording = _state.value.tripState == TripState.RECORDING

        updateState {
            val stale = !hasRecentUsableFix
            it.copy(
                signal = when {
                    !enabled -> GpsSignal.DISABLED
                    !hasRecentFix -> GpsSignal.SEARCHING
                    else -> it.signal
                },
                speedMps = if (stale) 0f else it.speedMps,
                // Keep the chart moving through a dropout instead of freezing it.
                speedHistory = if (stale && it.speedHistory.isNotEmpty()) it.speedHistory.pushed(0f) else it.speedHistory,
                elapsedMs = if (recording) committedElapsedMs + (now - segmentStartMs) else it.elapsedMs,
            )
        }
    }

    // endregion

    // region Trip

    fun toggleTrip() {
        when (_state.value.tripState) {
            TripState.RECORDING -> pauseTrip()
            TripState.IDLE, TripState.PAUSED -> startOrResumeTrip()
        }
    }

    private fun startOrResumeTrip() {
        segmentStartMs = SystemClock.elapsedRealtime()
        lastTripFixNanos = null
        updateState { it.copy(tripState = TripState.RECORDING) }
    }

    private fun pauseTrip() {
        committedElapsedMs += SystemClock.elapsedRealtime() - segmentStartMs
        lastTripFixNanos = null
        updateState { it.copy(tripState = TripState.PAUSED, elapsedMs = committedElapsedMs) }
    }

    /** Clears the trip. Returns a snapshot for [restoreTrip], or null if there was nothing to reset. */
    fun resetTrip(): TripSnapshot? {
        val current = _state.value
        if (current.tripState == TripState.IDLE) return null

        val elapsed = if (current.tripState == TripState.RECORDING) {
            committedElapsedMs + (SystemClock.elapsedRealtime() - segmentStartMs)
        } else {
            committedElapsedMs
        }
        val snapshot = TripSnapshot(
            tripState = current.tripState,
            maxSpeedMps = current.maxSpeedMps,
            distanceMeters = current.distanceMeters,
            movingTimeMs = current.movingTimeMs,
            elapsedMs = elapsed,
        )

        committedElapsedMs = 0L
        lastTripFixNanos = null
        updateState {
            it.copy(
                tripState = TripState.IDLE,
                maxSpeedMps = 0f,
                distanceMeters = 0.0,
                movingTimeMs = 0L,
                elapsedMs = 0L,
            )
        }
        return snapshot
    }

    fun restoreTrip(snapshot: TripSnapshot) {
        // If the user already started a new trip, don't overwrite it.
        if (_state.value.tripState != TripState.IDLE) return

        committedElapsedMs = snapshot.elapsedMs
        segmentStartMs = SystemClock.elapsedRealtime()
        lastTripFixNanos = null
        updateState {
            it.copy(
                tripState = snapshot.tripState,
                maxSpeedMps = snapshot.maxSpeedMps,
                distanceMeters = snapshot.distanceMeters,
                movingTimeMs = snapshot.movingTimeMs,
                elapsedMs = snapshot.elapsedMs,
            )
        }
    }

    // endregion

    // region Settings

    fun setUnit(unit: SpeedUnit) = updateSettings { it.withUnit(unit) }

    fun cycleUnit() {
        val units = SpeedUnit.entries
        setUnit(units[(_state.value.unit.ordinal + 1) % units.size])
    }

    fun setSpeedLimitEnabled(enabled: Boolean) = updateSettings { it.copy(speedLimitEnabled = enabled) }

    fun setSpeedLimit(limit: Int) = updateSettings {
        it.copy(speedLimit = limit.coerceIn(it.unit.limitMin, it.unit.limitMax))
    }

    fun setKeepScreenOn(keepOn: Boolean) = updateSettings { it.copy(keepScreenOn = keepOn) }

    fun setHudFlip(flip: HudFlip) = updateSettings { it.copy(hudFlip = flip) }

    fun toggleHudMode() = updateState { it.copy(isHudMode = !it.isHudMode) }

    /** Converts the limit to the new unit, rounded to the slider step, so it survives unit changes. */
    private fun SpeedSettings.withUnit(newUnit: SpeedUnit): SpeedSettings {
        val mps = unit.toMps(speedLimit.toFloat())
        val converted = (newUnit.toDisplay(mps) / LIMIT_STEP).roundToInt() * LIMIT_STEP
        return copy(unit = newUnit, speedLimit = converted.coerceIn(newUnit.limitMin, newUnit.limitMax))
    }

    private inline fun updateSettings(transform: (SpeedSettings) -> SpeedSettings) {
        val updated = transform(_state.value.settings)
        prefs.save(updated)
        updateState { it.copy(settings = updated) }
    }

    // endregion

    private inline fun updateState(transform: (SpeedUiState) -> SpeedUiState) {
        _state.update { transform(it).withGauge() }
    }

    private fun SpeedUiState.withGauge(): SpeedUiState {
        val limit = if (settings.speedLimitEnabled) settings.speedLimit.toFloat() else 0f
        return copy(gaugeMax = unit.nextGaugeMax(gaugeMax, max(speedDisplay, limit)))
    }

    private fun List<Float>.pushed(value: Float): List<Float> = (this + value).takeLast(SPEED_HISTORY_SIZE)
}