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

package com.frerox.toolz.service

import kotlin.math.sqrt

/**
 * States for the Strict Step Tracking Engine
 */
enum class EngineState {
    IDLE,       // Waiting for motion
    SEARCHING,  // Buffering steps (1-5) while validating cadence
    TRACKING,   // Committing buffered steps and updating smoothly
    SUSPENDED   // Forced by vehicle detection or GPS speed gate
}

/**
 * A zero-allocation, low-overhead step tracking engine using ONLY raw accelerometer data.
 * Implements a strict biomechanical frequency filter, a 6-step buffer, and vehicle detection.
 */
class StrictEngine(
    private val onStepEmitted: (Int) -> Unit,
    private val onLog: (String) -> Unit = {}
) {

    // --- State ---
    @Volatile var state: EngineState = EngineState.IDLE
        private set

    private var graceCounter = 0
    private var bufferCount = 0
    private var lastPeakTime = 0L
    private var isGpsSuspended = false
    private var vehicleContinuousLowAmpStart = 0L

    // --- Peak Detection State ---
    private val gravity = FloatArray(3) { 0f }
    private var smoothedMagnitude = 0f
    private var isRising = false
    private var currentPhasePeak = 0f
    private var lastProcessedTime = 0L

    // --- Constants ---
    companion object {
        private const val ALPHA_GRAVITY = 0.92f
        private const val ALPHA_MAGNITUDE = 0.60f
        
        // Biomechanical Frequency Constraints (Strict Walking Profile)
        private const val MIN_STEP_DELAY_MS = 360L // ~2.77 Hz (Fast jogging max)
        private const val MAX_STEP_DELAY_MS = 750L // ~1.33 Hz (Slow walking min)
        
        private const val REQUIRED_BUFFER_STEPS = 6
        private const val GRACE_LIMIT = 2
        
        private const val IDLE_TIMEOUT_MS = 2000L
        private const val MIN_PEAK_LIFT = 0.8f

        private const val VEHICLE_WINDOW_MS = 4000L
        private const val VEHICLE_MAX_AMPLITUDE = 0.7f
    }

    /**
     * Process a raw accelerometer sample.
     * Guaranteed zero-allocation path.
     */
    fun processAccelerometer(values: FloatArray, timeMs: Long) {
        if (isGpsSuspended) {
            lastProcessedTime = timeMs
            return
        }

        // --- Step 1: Gravity Isolation (Low-Pass Filter) ---
        gravity[0] = ALPHA_GRAVITY * gravity[0] + (1 - ALPHA_GRAVITY) * values[0]
        gravity[1] = ALPHA_GRAVITY * gravity[1] + (1 - ALPHA_GRAVITY) * values[1]
        gravity[2] = ALPHA_GRAVITY * gravity[2] + (1 - ALPHA_GRAVITY) * values[2]

        val ax = values[0] - gravity[0]
        val ay = values[1] - gravity[1]
        val az = values[2] - gravity[2]
        val rawMagnitude = sqrt(ax * ax + ay * ay + az * az)

        // --- Step 2: Magnitude Smoothing ---
        smoothedMagnitude = ALPHA_MAGNITUDE * smoothedMagnitude + (1 - ALPHA_MAGNITUDE) * rawMagnitude

        // --- Step 3: Vehicle Detection ---
        detectVehicle(rawMagnitude, timeMs)

        if (state == EngineState.SUSPENDED) {
            lastProcessedTime = timeMs
            return
        }

        // --- Step 4: Peak Detection ---
        val isPeak = detectPeak(smoothedMagnitude, timeMs)

        if (isPeak) {
            handlePeak(timeMs)
        } else {
            handleTimeouts(timeMs)
        }

        lastProcessedTime = timeMs
    }

    private fun detectPeak(magnitude: Float, timeMs: Long): Boolean {
        val hysteresis = 0.05f

        if (isRising && magnitude < (currentPhasePeak - hysteresis)) {
            val peakHeight = currentPhasePeak 

            if (peakHeight >= MIN_PEAK_LIFT) {
                isRising = false
                currentPhasePeak = 0f
                return true
            }

            isRising = false
            currentPhasePeak = 0f
        } else if (magnitude > currentPhasePeak) {
            isRising = true
            currentPhasePeak = magnitude
        }

        return false
    }

    private fun handlePeak(timeMs: Long) {
        val interval = if (lastPeakTime > 0) timeMs - lastPeakTime else 0L
        val isValidInterval = interval == 0L || (interval in MIN_STEP_DELAY_MS..MAX_STEP_DELAY_MS)

        when (state) {
            EngineState.IDLE -> {
                lastPeakTime = timeMs
                bufferCount = 1
                transitionTo(EngineState.SEARCHING)
                onLog("STRICT: First peak -> SEARCHING")
            }
            EngineState.SEARCHING -> {
                if (isValidInterval) {
                    bufferCount++
                    lastPeakTime = timeMs
                    onLog("STRICT: Valid peak ($bufferCount/$REQUIRED_BUFFER_STEPS) Int=${interval}ms")
                    
                    if (bufferCount >= REQUIRED_BUFFER_STEPS) {
                        transitionTo(EngineState.TRACKING)
                        onStepEmitted(REQUIRED_BUFFER_STEPS)
                        onLog("STRICT: Buffer full -> TRACKING (Emitted $REQUIRED_BUFFER_STEPS)")
                    }
                } else {
                    onLog("STRICT: Invalid interval in SEARCHING (${interval}ms) -> IDLE")
                    resetToIdle()
                }
            }
            EngineState.TRACKING -> {
                if (isValidInterval) {
                    graceCounter = 0
                    lastPeakTime = timeMs
                    onStepEmitted(1)
                    onLog("STRICT: Valid peak -> TRACKING (Emitted 1) Int=${interval}ms")
                } else {
                    graceCounter++
                    lastPeakTime = timeMs // Still update time to check next interval
                    onLog("STRICT: Invalid interval in TRACKING (${interval}ms) Grace=$graceCounter")
                    
                    if (graceCounter > GRACE_LIMIT) {
                        onLog("STRICT: Grace exceeded -> IDLE")
                        resetToIdle()
                    }
                }
            }
            EngineState.SUSPENDED -> {
                // No peaks processed in SUSPENDED state
            }
        }
    }

    private fun detectVehicle(magnitude: Float, timeMs: Long) {
        if (magnitude < VEHICLE_MAX_AMPLITUDE) {
            if (vehicleContinuousLowAmpStart == 0L) vehicleContinuousLowAmpStart = timeMs
            else if (timeMs - vehicleContinuousLowAmpStart > VEHICLE_WINDOW_MS) {
                if (state != EngineState.SUSPENDED) {
                    onLog("STRICT: VEHICLE DETECTED -> SUSPENDED")
                    transitionTo(EngineState.SUSPENDED)
                }
            }
        } else {
            vehicleContinuousLowAmpStart = 0L
            if (state == EngineState.SUSPENDED && !isGpsSuspended) {
                onLog("STRICT: VEHICLE CLEARED -> IDLE")
                transitionTo(EngineState.IDLE)
            }
        }
    }

    private fun handleTimeouts(timeMs: Long) {
        if (lastProcessedTime > 0) {
            val idleTime = timeMs - lastProcessedTime
            if (idleTime > IDLE_TIMEOUT_MS && state != EngineState.IDLE && state != EngineState.SUSPENDED) {
                onLog("STRICT: Global timeout (${idleTime}ms) -> IDLE")
                resetToIdle()
            }
        }
    }

    private fun resetToIdle() {
        bufferCount = 0
        graceCounter = 0
        lastPeakTime = 0L
        isRising = false
        currentPhasePeak = 0f
        transitionTo(EngineState.IDLE)
    }

    private fun transitionTo(newState: EngineState) {
        if (state == newState) return
        state = newState
    }

    /**
     * External GPS speed gate — suppress when speed > 10 m/s
     */
    fun forceSuspend(suspended: Boolean) {
        isGpsSuspended = suspended
        if (suspended) {
            onLog("STRICT: Suspended (GPS) -> SUSPENDED")
            transitionTo(EngineState.SUSPENDED)
        } else {
            if (state == EngineState.SUSPENDED) {
                onLog("STRICT: Resumed (GPS) -> IDLE")
                transitionTo(EngineState.IDLE)
            }
        }
    }

    /**
     * Reset all engine state.
     */
    fun resetEngine() {
        onLog("STRICT: Hard Reset")
        resetToIdle()
        gravity.fill(0f)
        smoothedMagnitude = 0f
        lastProcessedTime = 0L
        vehicleContinuousLowAmpStart = 0L
    }

    // Compatibility method for the service
    fun setSensitivity(sensitivity: Int) {
        onLog("STRICT: Sensitivity $sensitivity ignored (Strict profile enforced)")
    }
    
    // Compatibility method for the service
    fun onOsStepDetected(stepsDelta: Int) {
        onLog("STRICT: OS Step +$stepsDelta ignored in strict mode")
    }
}
