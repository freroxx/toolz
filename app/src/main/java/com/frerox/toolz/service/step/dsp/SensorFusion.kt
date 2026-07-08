package com.frerox.toolz.service.step.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Sensor fusion layer that combines accelerometer and gyroscope data
 * to reject false step signals from hand movements and vehicle vibration.
 * 
 * Provides a clean abstraction so the step detection engine doesn't
 * need to directly couple with raw SensorManager events.
 * 
 * Key fusion decisions:
 * - Gyro freeze: detect hand shake → temporarily suppress step detection
 * - Vehicle mode: sustained low-amplitude vibration → suppress until stillness
 * - Free-fall detection: near-zero-g → suppress (dropped phone)
 */
class SensorFusion(
    private val onSuspensionChange: (suspended: Boolean, reason: SuspensionReason) -> Unit
) {
    enum class SuspensionReason {
        VEHICLE,      // Sustained low-amp vibration pattern
        HAND_SHAKE,   // High angular velocity (hand gesturing)
        FREE_FALL,    // Near-zero-g (phone dropped)
        GPS_SPEED     // GPS indicates >10 m/s (in vehicle)
    }
    
    enum class Mode { NORMAL, VEHICLE, HAND_SHAKE, FREE_FALL }
    
    var mode: Mode = Mode.NORMAL
        private set
    
    // --- Gyroscope state ---
    private var gyroFreezeEndTime = 0L
    private var lastAngularVelocity = 0f
    
    // --- Vehicle detection state ---
    private var vehicleWindow = FloatRingBuffer(VEHICLE_WINDOW_SIZE)
    private var vehicleContinuousLowAmpStart = 0L
    
    // --- Free-fall detection ---
    private var freeFallWindow = FloatRingBuffer(FREE_FALL_WINDOW_SIZE)
    
    // --- Config ---
    var maxAngularVelocityRad: Float = 3.0f
    var vehicleWindowMs: Long = 3000L
    var vehicleMaxAmplitude: Float = 0.8f
    var handShakeFreezeMs: Long = 500L
    var freeFallThreshold: Float = 1.0f  // m/s² — near zero-g
    
    // --- GPS suspension (external) ---
    @Volatile var isGpsSuspended = false
    
    /**
     * Process gyroscope data. Returns true if step detection should be frozen.
     */
    fun processGyroscope(angularVelocity: Float, timeMs: Long): Boolean {
        lastAngularVelocity = angularVelocity
        val absOmega = abs(angularVelocity)
        
        if (absOmega > maxAngularVelocityRad) {
            gyroFreezeEndTime = timeMs + handShakeFreezeMs
            mode = Mode.HAND_SHAKE
            onSuspensionChange(true, SuspensionReason.HAND_SHAKE)
            return true
        }
        
        if (timeMs > gyroFreezeEndTime && mode == Mode.HAND_SHAKE) {
            mode = Mode.NORMAL
            onSuspensionChange(false, SuspensionReason.HAND_SHAKE)
        }
        
        return timeMs <= gyroFreezeEndTime
    }
    
    /**
     * Process accelerometer magnitude for vehicle / free-fall detection.
     * @param rawMagnitude acceleration magnitude in m/s²
     * @return true if step detection should be suppressed
     */
    fun processAccelerometer(rawMagnitude: Float, timeMs: Long): Boolean {
        vehicleWindow.push(rawMagnitude)
        freeFallWindow.push(rawMagnitude)
        
        // --- Free-fall detection ---
        if (freeFallWindow.isFull()) {
            val mean = freeFallWindow.mean()
            if (mean < freeFallThreshold) {
                mode = Mode.FREE_FALL
                onSuspensionChange(true, SuspensionReason.FREE_FALL)
                return true
            }
        }
        
        // --- Vehicle detection ---
        if (vehicleWindow.isFull()) {
            val amp = vehicleWindow.amplitude()
            val mean = vehicleWindow.mean()
            
            // Vehicle: sustained low-amplitude vibration with consistent mean near 9.8
            if (amp < vehicleMaxAmplitude && abs(mean - 9.8f) < 2.0f) {
                if (vehicleContinuousLowAmpStart == 0L) {
                    vehicleContinuousLowAmpStart = timeMs
                } else if (timeMs - vehicleContinuousLowAmpStart > vehicleWindowMs) {
                    mode = Mode.VEHICLE
                    onSuspensionChange(true, SuspensionReason.VEHICLE)
                    return true
                }
            } else {
                // Reset vehicle detection on any significant motion
                vehicleContinuousLowAmpStart = 0L
                if (mode == Mode.VEHICLE) {
                    mode = Mode.NORMAL
                    onSuspensionChange(false, SuspensionReason.VEHICLE)
                }
            }
        }
        
        return false
    }
    
    /**
     * Call when GPS speed indicates vehicle motion (>10 m/s).
     * External service should call this directly.
     */
    fun setGpsSuspension(suspended: Boolean) {
        isGpsSuspended = suspended
        if (suspended) {
            mode = Mode.VEHICLE
            onSuspensionChange(true, SuspensionReason.GPS_SPEED)
        } else if (mode == Mode.VEHICLE) {
            mode = Mode.NORMAL
            onSuspensionChange(false, SuspensionReason.GPS_SPEED)
        }
    }
    
    /** True if any sensor fusion source is suppressing steps */
    fun isSuppressed(): Boolean = isGpsSuspended || mode != Mode.NORMAL
    
    /** Current suppression reason if suppressed */
    fun suppressionReason(): SuspensionReason? {
        return when {
            isGpsSuspended -> SuspensionReason.GPS_SPEED
            mode == Mode.VEHICLE -> SuspensionReason.VEHICLE
            mode == Mode.HAND_SHAKE -> SuspensionReason.HAND_SHAKE
            mode == Mode.FREE_FALL -> SuspensionReason.FREE_FALL
            else -> null
        }
    }
    
    /** Reset all state */
    fun reset() {
        mode = Mode.NORMAL
        gyroFreezeEndTime = 0L
        vehicleContinuousLowAmpStart = 0L
        vehicleWindow.clear()
        freeFallWindow.clear()
        isGpsSuspended = false
    }
    
    companion object {
        private const val VEHICLE_WINDOW_SIZE = 150   // ~3s @ 50Hz
        private const val FREE_FALL_WINDOW_SIZE = 25  // ~0.5s @ 50Hz
    }
}
