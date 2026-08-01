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
 * SimpleStepEngine — lightweight, responsive step counting engine using vertical gravity projection.
 */
class SimpleStepEngine(
    private val onStepDetected: (count: Int, cadence: Int) -> Unit,
    private val onLog: (String) -> Unit = {},
    private val useHardwareStepCounter: Boolean = false
) {

    @Volatile var stepCount: Int = 0
        private set

    @Volatile var isSuspended: Boolean = false
        private set

    companion object {
        private const val ALPHA_GRAVITY = 0.88f
        private const val MIN_INTER_PEAK_MS = 280L  // Max ~214 bpm
        private const val MAX_INTER_PEAK_MS = 2000L // Min ~30 bpm
        private const val DEFAULT_THRESHOLD = 0.18f
        private const val MAX_GYRO_RAD = 3.5f
        private const val GYRO_FREEZE_MS = 400L
        private const val CADENCE_WINDOW = 4
    }

    private val lock = Any()

    private val gravity = FloatArray(3) { 0f }
    private var gravityInitialized = false

    private var isRising = false
    private var phasePeak = 0f
    private var lastPeakTimeMs = 0L

    private var gyroFreezeUntilMs = 0L

    private val intervalBuf = LongArray(CADENCE_WINDOW) { 0L }
    private var intervalHead = 0
    private var intervalCount = 0

    private var threshold = DEFAULT_THRESHOLD

    fun processAccelerometer(values: FloatArray, timeMs: Long) {
        synchronized(lock) {
            if (isSuspended || useHardwareStepCounter) return

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
            if (gMag < 1.0f) return // Sensor uninitialized or freefall

            // --- Step 2: Project acceleration onto vertical gravity vector ---
            val dotProduct = values[0] * gravity[0] + values[1] * gravity[1] + values[2] * gravity[2]
            val verticalAccel = (dotProduct / gMag) - gMag

            // --- Step 3: Vertical peak detection ---
            if (verticalAccel > threshold) {
                if (verticalAccel > phasePeak) {
                    phasePeak = verticalAccel
                }
                isRising = true
            } else if (isRising) {
                // Descending edge below threshold
                val interval = if (lastPeakTimeMs > 0L) timeMs - lastPeakTimeMs else 0L
                val gyroOk = timeMs >= gyroFreezeUntilMs

                if (interval > 0L && interval < MIN_INTER_PEAK_MS) {
                    // Refractory peak (heel strike / toe off double bounce)
                    isRising = false
                    phasePeak = 0f
                    return@synchronized
                }

                val validInterval = interval == 0L || (interval in MIN_INTER_PEAK_MS..MAX_INTER_PEAK_MS)

                if (validInterval && gyroOk && phasePeak >= threshold) {
                    if (interval > 0L) {
                        intervalBuf[intervalHead] = interval
                        intervalHead = (intervalHead + 1) % CADENCE_WINDOW
                        if (intervalCount < CADENCE_WINDOW) intervalCount++
                    }

                    stepCount++
                    val cadence = computeCadence()
                    onLog("SIMPLE: Accel Step #$stepCount | cadence=${cadence}bpm | vPeak=${String.format("%.2f", phasePeak)}")
                    onStepDetected(1, cadence)

                    lastPeakTimeMs = timeMs
                }

                isRising = false
                phasePeak = 0f
            }
        }
    }

    fun onOsStepDetected(delta: Int) {
        synchronized(lock) {
            if (delta > 0) {
                stepCount += delta
                val cadence = computeCadence()
                onLog("SIMPLE: OS +$delta step counted (total=$stepCount)")
                onStepDetected(delta, cadence)
            }
        }
    }

    fun processGyroscope(angularVelocity: Float, timeMs: Long) {
        synchronized(lock) {
            if (angularVelocity > MAX_GYRO_RAD) {
                gyroFreezeUntilMs = timeMs + GYRO_FREEZE_MS
            }
        }
    }

    fun setGpsSuspended(suspended: Boolean) {
        synchronized(lock) {
            isSuspended = suspended
            if (suspended) {
                isRising = false
                phasePeak = 0f
            }
        }
    }

    fun setSensitivity(sensitivity: Int) {
        synchronized(lock) {
            val normalized = sensitivity.coerceIn(0, 100) / 100f
            // Sensitivity 0 => threshold 0.30 (stricter)
            // Sensitivity 100 => threshold 0.12 (lenient)
            threshold = 0.30f - (0.18f * normalized)
        }
    }

    fun reset() {
        synchronized(lock) {
            stepCount = 0
            isRising = false
            phasePeak = 0f
            lastPeakTimeMs = 0L
            gyroFreezeUntilMs = 0L
            gravity.fill(0f)
            gravityInitialized = false
            intervalBuf.fill(0L)
            intervalHead = 0
            intervalCount = 0
        }
    }

    private fun computeCadence(): Int {
        if (intervalCount == 0) return 0
        val filled = minOf(intervalCount, CADENCE_WINDOW)
        var sum = 0L
        for (i in 0 until filled) {
            sum += intervalBuf[i]
        }
        val meanMs = sum / filled
        return if (meanMs > 0) (60_000L / meanMs).toInt() else 0
    }
}
