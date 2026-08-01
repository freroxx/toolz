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
    SEARCHING,  // Buffering steps while validating cadence
    TRACKING,   // Committing buffered steps and updating smoothly
    SUSPENDED   // Forced by vehicle detection or GPS speed gate
}

/**
 * High-precision step engine enforcing biomechanical rhythm and vertical gravity projection.
 * Rejects hand wiggles, fidgeting, and non-gait motions.
 */
class StrictEngine(
    private val onStepEmitted: (Int) -> Unit,
    private val onLog: (String) -> Unit = {}
) {

    private val lock = Any()

    // --- State ---
    @Volatile var state: EngineState = EngineState.IDLE
        private set

    private var graceCounter = 0
    private var bufferCount = 0
    private var lastPeakTime = 0L
    private var isGpsSuspended = false
    private var gravityInitialized = false

    // Cadence stability buffer (stores last 3 step intervals)
    private val intervalBuf = LongArray(3) { 0L }
    private var intervalHead = 0
    private var intervalCount = 0

    // --- Peak Detection State ---
    private val gravity = FloatArray(3) { 0f }
    private var isRising = false
    private var currentPhasePeak = 0f
    private var lastProcessedTime = 0L

    // Sensitivity threshold
    private var minPeakLift = DEFAULT_MIN_PEAK_LIFT

    companion object {
        private const val ALPHA_GRAVITY = 0.88f
        
        // Biomechanical Frequency Constraints (Walking/Jogging Profile)
        private const val MIN_STEP_DELAY_MS = 280L  // ~214 bpm max
        private const val MAX_STEP_DELAY_MS = 1100L // ~55 bpm min
        
        private const val REQUIRED_BUFFER_STEPS = 5
        private const val GRACE_LIMIT = 2
        
        private const val IDLE_TIMEOUT_MS = 1800L
        private const val DEFAULT_MIN_PEAK_LIFT = 0.28f
    }

    /**
     * Process a raw accelerometer sample.
     */
    fun processAccelerometer(values: FloatArray, timeMs: Long) {
        synchronized(lock) {
            if (isGpsSuspended || state == EngineState.SUSPENDED) {
                lastProcessedTime = timeMs
                return
            }

            // --- Step 1: Low-pass filter to isolate gravity ---
            if (!gravityInitialized) {
                gravity[0] = values[0]
                gravity[1] = values[1]
                gravity[2] = values[2]
                gravityInitialized = true
            } else {
                gravity[0] = ALPHA_GRAVITY * gravity[0] + (1 - ALPHA_GRAVITY) * values[0]
                gravity[1] = ALPHA_GRAVITY * gravity[1] + (1 - ALPHA_GRAVITY) * values[1]
                gravity[2] = ALPHA_GRAVITY * gravity[2] + (1 - ALPHA_GRAVITY) * values[2]
            }

            val gMag = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
            if (gMag < 1.0f) return

            // --- Step 2: Project acceleration onto vertical gravity vector ---
            val dotProduct = values[0] * gravity[0] + values[1] * gravity[1] + values[2] * gravity[2]
            val verticalAccel = (dotProduct / gMag) - gMag

            // --- Step 3: Peak Detection on vertical acceleration ---
            val isPeak = detectPeak(verticalAccel)

            if (isPeak) {
                handlePeak(timeMs)
            } else {
                handleTimeouts(timeMs)
            }

            lastProcessedTime = timeMs
        }
    }

    private fun detectPeak(verticalAccel: Float): Boolean {
        val hysteresis = 0.04f

        if (isRising && verticalAccel < (currentPhasePeak - hysteresis)) {
            val peakHeight = currentPhasePeak

            if (peakHeight >= minPeakLift) {
                isRising = false
                currentPhasePeak = 0f
                return true
            }

            isRising = false
            currentPhasePeak = 0f
        } else if (verticalAccel > currentPhasePeak) {
            isRising = true
            currentPhasePeak = verticalAccel
        }

        return false
    }

    private fun handlePeak(timeMs: Long) {
        val interval = if (lastPeakTime > 0L) timeMs - lastPeakTime else 0L

        if (state == EngineState.IDLE) {
            lastPeakTime = timeMs
            bufferCount = 1
            intervalBuf.fill(0L)
            intervalHead = 0
            intervalCount = 0
            transitionTo(EngineState.SEARCHING)
            onLog("STRICT: First peak -> SEARCHING")
            return
        }

        // Sub-peak / refractory bounce (< 280ms)
        if (interval < MIN_STEP_DELAY_MS) {
            onLog("STRICT: Refractory peak ignored (${interval}ms)")
            return
        }

        // Check rhythm / cadence stability against recent intervals (deviation <= 25%)
        val isCadenceStable = if (intervalCount >= 2) {
            val filled = minOf(intervalCount, 3)
            var sum = 0L
            for (i in 0 until filled) sum += intervalBuf[i]
            val avg = sum / filled
            val diff = kotlin.math.abs(interval - avg)
            diff <= (0.25f * avg)
        } else {
            true
        }

        // Valid biomechanical step interval
        if (interval in MIN_STEP_DELAY_MS..MAX_STEP_DELAY_MS) {
            if (!isCadenceStable) {
                onLog("STRICT: Unstable cadence (${interval}ms) -> IDLE")
                resetToIdle()
                return
            }

            // Record interval
            intervalBuf[intervalHead] = interval
            intervalHead = (intervalHead + 1) % 3
            if (intervalCount < 3) intervalCount++

            lastPeakTime = timeMs

            when (state) {
                EngineState.SEARCHING -> {
                    bufferCount++
                    onLog("STRICT: Valid peak ($bufferCount/$REQUIRED_BUFFER_STEPS) Int=${interval}ms")
                    if (bufferCount >= REQUIRED_BUFFER_STEPS) {
                        transitionTo(EngineState.TRACKING)
                        onStepEmitted(REQUIRED_BUFFER_STEPS)
                        onLog("STRICT: Buffer full -> TRACKING (Emitted $REQUIRED_BUFFER_STEPS)")
                    }
                }
                EngineState.TRACKING -> {
                    graceCounter = 0
                    onStepEmitted(1)
                    onLog("STRICT: Valid peak -> TRACKING (Emitted 1) Int=${interval}ms")
                }
                else -> {}
            }
        } else {
            // interval > MAX_STEP_DELAY_MS -> cadence broken
            if (state == EngineState.TRACKING && graceCounter < GRACE_LIMIT) {
                graceCounter++
                lastPeakTime = timeMs
                onLog("STRICT: Long interval (${interval}ms) Grace=$graceCounter/$GRACE_LIMIT")
            } else {
                onLog("STRICT: Long interval (${interval}ms > ${MAX_STEP_DELAY_MS}ms) -> IDLE")
                resetToIdle()
            }
        }
    }

    private fun handleTimeouts(timeMs: Long) {
        if (lastPeakTime > 0L && state != EngineState.IDLE && state != EngineState.SUSPENDED) {
            val timeSinceLastPeak = timeMs - lastPeakTime
            if (timeSinceLastPeak > IDLE_TIMEOUT_MS) {
                onLog("STRICT: Peak timeout (${timeSinceLastPeak}ms) -> IDLE")
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
        intervalBuf.fill(0L)
        intervalHead = 0
        intervalCount = 0
        transitionTo(EngineState.IDLE)
    }

    private fun transitionTo(newState: EngineState) {
        if (state == newState) return
        state = newState
    }

    fun forceSuspend(suspended: Boolean) {
        synchronized(lock) {
            isGpsSuspended = suspended
            if (suspended) {
                onLog("STRICT: Suspended (GPS/Vehicle) -> SUSPENDED")
                transitionTo(EngineState.SUSPENDED)
            } else {
                if (state == EngineState.SUSPENDED) {
                    onLog("STRICT: Resumed (GPS/Vehicle) -> IDLE")
                    transitionTo(EngineState.IDLE)
                }
            }
        }
    }

    fun resetEngine() {
        synchronized(lock) {
            onLog("STRICT: Hard Reset")
            resetToIdle()
            gravity.fill(0f)
            gravityInitialized = false
            lastProcessedTime = 0L
        }
    }

    fun setSensitivity(sensitivity: Int) {
        synchronized(lock) {
            val normalized = sensitivity.coerceIn(0, 100) / 100f
            // Sensitivity 0 => minPeakLift = 0.40f (stricter)
            // Sensitivity 100 => minPeakLift = 0.18f (lenient)
            minPeakLift = 0.40f - (0.22f * normalized)
            onLog("STRICT: Sensitivity $sensitivity set minPeakLift=${String.format("%.2f", minPeakLift)}")
        }
    }
    
    fun onOsStepDetected(stepsDelta: Int) {
        synchronized(lock) {
            if (stepsDelta > 0) {
                onLog("STRICT: OS Step +$stepsDelta received")
            }
        }
    }
}
