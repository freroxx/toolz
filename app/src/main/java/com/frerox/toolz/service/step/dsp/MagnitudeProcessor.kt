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

package com.frerox.toolz.service.step.dsp

import kotlin.math.sqrt
import kotlin.math.abs

/**
 * DSP-grade magnitude processor for step detection.
 * Handles gravity isolation, linear acceleration extraction, and peak detection.
 * 
 * Optimized for low-power mobile DSP:
 * - Single-pass low-pass filters (no IIR cascade overhead)
 * - Ring-buffer magnitude history for O(1) windowed stats
 * - Zero allocations in hot path after init
 */
class MagnitudeProcessor(
    private val gravityAlpha: Float = 0.9f,
    private val magnitudeAlpha: Float = 0.5f
) {
    // Gravity vector via EMA low-pass (cutoff ~0.8Hz for 50Hz sampling)
    private val gravity = FloatArray(3)

    // Smoothed magnitude state
    private var smoothedMag = 0f

    // Sliding window for variance / peak detection
    private val magnitudeHistory = FloatRingBuffer(WINDOW_SIZE)

    // Peak detection state
    private var isRising = false
    private var currentPhasePeak = 0f
    private var lastPeakValue = 0f

    // Config (can be updated at runtime)
    var minPeakMagnitude: Float = 0.8f
    var minVarianceThreshold: Float = 0.01f

    /** Returns smoothed magnitude */
    fun processAccelerometer(values: FloatArray): Float {
        // --- Gravity isolation (EMA low-pass) ---
        gravity[0] = gravityAlpha * gravity[0] + (1 - gravityAlpha) * values[0]
        gravity[1] = gravityAlpha * gravity[1] + (1 - gravityAlpha) * values[1]
        gravity[2] = gravityAlpha * gravity[2] + (1 - gravityAlpha) * values[2]

        // --- Linear acceleration ---
        val ax = values[0] - gravity[0]
        val ay = values[1] - gravity[1]
        val az = values[2] - gravity[2]

        // --- Magnitude ---
        val rawMag = sqrt(ax * ax + ay * ay + az * az)

        // --- Smooth ---
        smoothedMag = magnitudeAlpha * smoothedMag + (1 - magnitudeAlpha) * rawMag
        magnitudeHistory.push(smoothedMag)

        return smoothedMag
    }

    /** Returns true if current smoothedMag is a peak crossing the noise floor */
    fun detectPeak(magnitude: Float, timeMs: Long, minInterPeakMs: Long): Boolean {
        val dt = timeMs - lastPeakTimeMs

        if (dt < minInterPeakMs) return false // Physically impossible interval

        val crossing = if (isRising) {
            magnitude < currentPhasePeak - PEAK_HYSTERESIS
        } else {
            magnitude > currentPhasePeak + PEAK_HYSTERESIS
        }

        if (crossing) {
            // Peak detected
            val peakDelta = abs(magnitude - currentPhasePeak)
            lastPeakTimeMs = timeMs
            lastPeakValue = currentPhasePeak
            isRising = true
            currentPhasePeak = magnitude
            return peakDelta >= minPeakMagnitude
        }

        // Track phase
        if (magnitude > currentPhasePeak) {
            currentPhasePeak = magnitude
            isRising = true
        } else if (isRising && magnitude < currentPhasePeak - PEAK_HYSTERESIS) {
            isRising = false
        }

        return false
    }

    /** Signal variance over the magnitude window */
    fun signalVariance(): Float = magnitudeHistory.variance()

    /** Signal mean over the magnitude window */
    fun signalMean(): Float = magnitudeHistory.mean()

    /** Reset state (for engine re-init) */
    fun reset() {
        gravity[0] = 0f; gravity[1] = 0f; gravity[2] = 0f
        smoothedMag = 0f
        magnitudeHistory.clear()
        isRising = false
        currentPhasePeak = 0f
        lastPeakTimeMs = 0L
        lastPeakValue = 0f
    }

    private var lastPeakTimeMs = 0L

    companion object {
        const val WINDOW_SIZE = 128 // ~2.5s @ 50Hz
        private const val PEAK_HYSTERESIS = 0.05f
    }
}

/** Fixed-size float ring buffer with O(1) mean/variance */
class FloatRingBuffer(capacity: Int) {
    private val data = FloatArray(capacity)
    private var head = 0
    private var count = 0
    private var sum = 0f
    private var sumSq = 0f

    fun push(value: Float) {
        if (count == data.size) {
            // Evict oldest
            val old = data[head]
            sum -= old
            sumSq -= old * old
        } else {
            count++
        }
        data[head] = value
        sum += value
        sumSq += value * value
        head = (head + 1) % data.size
    }

    fun clear() { head = 0; count = 0; sum = 0f; sumSq = 0f }

    fun mean(): Float = if (count > 0) sum / count else 0f

    fun variance(): Float {
        if (count < 2) return 0f
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    fun amplitude(): Float {
        if (count < 2) return 0f
        val m = mean()
        val variance = data.take(count).map { val d = it - m; d * d }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }

    fun isFull(): Boolean = count == data.size
}
