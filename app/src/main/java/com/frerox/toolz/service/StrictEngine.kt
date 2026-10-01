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
    SEARCHING,  // First peaks seen, confirming rhythm
    TRACKING,   // Confirmed gait, emitting per-step
    SUSPENDED   // Forced by vehicle, driving speed, or missing mandatory GPS
}

/**
 * Strict engine: same vertical-gravity-projection pipeline as [SimpleStepEngine],
 * but tweaked stricter AND with mandatory GPS.
 *
 * Differences vs Simple:
 * - Higher peak threshold (0.28 vs 0.18) + tighter max interval (1100ms vs 2000ms)
 * - Longer gyro freeze (500ms) to reject hand shakes
 * - Requires a recent GPS fix: no fix / no location permission -> SUSPENDED
 *   (service drives this via [setGpsReady]; UI prompts for position permission).
 * - Short 2-step confirmation buffer (was 5) so the ring doesn't jump +5 at once.
 */
class StrictEngine(
    private val onStepEmitted: (Int) -> Unit,
    private val onLog: (String) -> Unit = {}
) {

    private val lock = Any()

    @Volatile var state: EngineState = EngineState.IDLE
        private set

    @Volatile var isSuspended: Boolean = false
        private set

    private var bufferCount = 0
    private var lastPeakTime = 0L
    private var isGpsSuspended = false
    private var gpsReady = false
    private var gravityInitialized = false

    private val intervalBuf = LongArray(CADENCE_WINDOW) { 0L }
    private var intervalHead = 0
    private var intervalCount = 0

    private val gravity = FloatArray(3) { 0f }
    private var isRising = false
    private var currentPhasePeak = 0f
    private var lastProcessedTime = 0L
    private var gyroFreezeUntilMs = 0L

    private var minPeakLift = DEFAULT_MIN_PEAK_LIFT

    companion object {
        private const val ALPHA_GRAVITY = 0.88f
        private const val MIN_STEP_DELAY_MS = 280L
        private const val MAX_STEP_DELAY_MS = 1100L
        private const val REQUIRED_BUFFER_STEPS = 2
        private const val MAX_GYRO_RAD = 3.5f
        private const val GYRO_FREEZE_MS = 500L
        private const val CADENCE_WINDOW = 4
        private const val IDLE_TIMEOUT_MS = 1800L
        private const val DEFAULT_MIN_PEAK_LIFT = 0.28f
    }

    fun processAccelerometer(values: FloatArray, timeMs: Long) {
        synchronized(lock) {
            if (isSuspended || isGpsSuspended || state == EngineState.SUSPENDED) {
                lastProcessedTime = timeMs
                return
            }
            // Mandatory GPS: no fix yet -> hold in SEARCHING, don't emit.
            if (!gpsReady) {
                if (state != EngineState.SEARCHING) transitionTo(EngineState.SEARCHING)
                lastProcessedTime = timeMs
                return
            }

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

            val dotProduct = values[0] * gravity[0] + values[1] * gravity[1] + values[2] * gravity[2]
            val verticalAccel = (dotProduct / gMag) - gMag

            if (verticalAccel > minPeakLift) {
                if (verticalAccel > currentPhasePeak) currentPhasePeak = verticalAccel
                isRising = true
            } else if (isRising) {
                val interval = if (lastPeakTime > 0L) timeMs - lastPeakTime else 0L
                val gyroOk = timeMs >= gyroFreezeUntilMs
                if (interval > 0L && interval < MIN_STEP_DELAY_MS) {
                    isRising = false
                    currentPhasePeak = 0f
                    return@synchronized
                }
                val validInterval = interval == 0L || (interval in MIN_STEP_DELAY_MS..MAX_STEP_DELAY_MS)
                if (validInterval && gyroOk && currentPhasePeak >= minPeakLift) {
                    if (interval > 0L) {
                        intervalBuf[intervalHead] = interval
                        intervalHead = (intervalHead + 1) % CADENCE_WINDOW
                        if (intervalCount < CADENCE_WINDOW) intervalCount++
                    }
                    handleConfirmedStep(timeMs)
                }
                isRising = false
                currentPhasePeak = 0f
            } else {
                handleTimeouts(timeMs)
            }
            lastProcessedTime = timeMs
        }
    }

    private fun handleConfirmedStep(timeMs: Long) {
        lastPeakTime = timeMs
        when (state) {
            EngineState.IDLE, EngineState.SEARCHING -> {
                bufferCount++
                if (bufferCount >= REQUIRED_BUFFER_STEPS) {
                    transitionTo(EngineState.TRACKING)
                    onStepEmitted(bufferCount)
                    onLog("STRICT: confirmed $bufferCount steps -> TRACKING")
                    bufferCount = 0
                } else {
                    transitionTo(EngineState.SEARCHING)
                    onLog("STRICT: buffering ($bufferCount/$REQUIRED_BUFFER_STEPS)")
                }
            }
            EngineState.TRACKING -> {
                onStepEmitted(1)
            }
            EngineState.SUSPENDED -> Unit
        }
    }

    private fun handleTimeouts(timeMs: Long) {
        if (lastPeakTime > 0L && state != EngineState.IDLE && state != EngineState.SUSPENDED) {
            if (timeMs - lastPeakTime > IDLE_TIMEOUT_MS) resetToIdle()
        }
    }

    private fun resetToIdle() {
        bufferCount = 0
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

    fun processGyroscope(angularVelocity: Float, timeMs: Long) {
        synchronized(lock) {
            if (angularVelocity > MAX_GYRO_RAD) {
                gyroFreezeUntilMs = timeMs + GYRO_FREEZE_MS
            }
        }
    }

    fun forceSuspend(suspended: Boolean) {
        synchronized(lock) {
            isGpsSuspended = suspended
            isSuspended = suspended
            if (suspended) {
                onLog("STRICT: Suspended (GPS/Vehicle) -> SUSPENDED")
                transitionTo(EngineState.SUSPENDED)
            } else if (state == EngineState.SUSPENDED && gpsReady) {
                onLog("STRICT: Resumed -> IDLE")
                transitionTo(EngineState.IDLE)
            }
        }
    }

    /** Back-compat alias used by the service driving-speed gate. */
    fun setGpsSuspended(suspended: Boolean) = forceSuspend(suspended)

    /**
     * Mandatory-GPS gate. Call with true on every trusted location fix,
     * false when permission is missing or the fix is stale.
     */
    fun setGpsReady(ready: Boolean) {
        synchronized(lock) {
            if (gpsReady == ready) return
            gpsReady = ready
            if (!ready) {
                onLog("STRICT: waiting for GPS fix -> SUSPENDED")
                transitionTo(EngineState.SUSPENDED)
            } else if (state == EngineState.SUSPENDED && !isGpsSuspended) {
                onLog("STRICT: GPS fix acquired -> IDLE")
                transitionTo(EngineState.IDLE)
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
            gyroFreezeUntilMs = 0L
        }
    }

    fun setSensitivity(sensitivity: Int) {
        synchronized(lock) {
            val normalized = sensitivity.coerceIn(0, 100) / 100f
            // Strict stays ~0.10 above Simple across the whole range.
            minPeakLift = (0.40f - (0.22f * normalized)).coerceAtLeast(0.22f)
            onLog("STRICT: Sensitivity $sensitivity minPeakLift=${String.format("%.2f", minPeakLift)}")
        }
    }

    fun onOsStepDetected(stepsDelta: Int) {
        val toEmit = synchronized(lock) {
            if (stepsDelta <= 0 || isGpsSuspended || isSuspended || state == EngineState.SUSPENDED || !gpsReady) {
                if (stepsDelta > 0) onLog("STRICT: OS Step +$stepsDelta suppressed (suspended/no-GPS)")
                0
            } else {
                onLog("STRICT: OS Step +$stepsDelta forwarded")
                stepsDelta
            }
        }
        if (toEmit > 0) onStepEmitted(toEmit)
    }
}
