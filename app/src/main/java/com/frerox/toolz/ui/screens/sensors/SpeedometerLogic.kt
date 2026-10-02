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

import kotlin.math.abs
import kotlin.math.sign

// Accuracy bands mirror the ViewModel cutoffs so the filter and the signal pill agree.
internal const val SPEED_GOOD_ACCURACY_M = 10f
internal const val SPEED_FAIR_ACCURACY_M = 25f

/** Output holds zero until the smoothed speed clearly exits the drift band. */
internal const val STATIONARY_ENTER_MPS = 0.5f
internal const val STATIONARY_EXIT_MPS = 0.9f

/** A jump bigger than this over a poor fix is treated as a GPS glitch, not motion. */
internal const val SPIKE_CLAMP_MPS = 8f

/** Doppler fixes less precise than this are trusted half as much. */
internal const val BAD_SPEED_ACCURACY_MPS = 2.5f

internal const val ALTITUDE_EMA_ALPHA = 0.3f

/** Accuracy reported to the smoother for step-derived speeds (trusted, responsive). */
internal const val STEP_SPEED_ACCURACY_M = 5f

/**
 * Converts a step count observed in a window to metres per second.
 * Pure so it is unit-testable; the sensor plumbing lives in [StepCadenceSource].
 */
internal fun stepSpeedMps(stepsInWindow: Int, windowSec: Double, strideCm: Int): Float {
    if (stepsInWindow <= 0 || windowSec <= 0.0 || strideCm <= 0) return 0f
    return (stepsInWindow * strideCm / 100.0 / windowSec).toFloat()
}

/**
 * Pure speed filter: median-of-3 (kills single-sample spikes) + accuracy-weighted
 * EMA (smooths jitter) + stationary hysteresis (stops 0 <-> 2 km/h flicker).
 * No Android dependencies, so it is unit-testable.
 */
class SpeedSmoother {
    private val window = ArrayDeque<Float>()
    private var ema: Float? = null
    var stationary: Boolean = true
        private set

    fun reset() {
        window.clear()
        ema = null
        stationary = true
    }

    fun update(rawMps: Float, accuracyM: Float, speedAccuracyMps: Float?): Float {
        window.addLast(rawMps.coerceAtLeast(0f))
        if (window.size > 3) window.removeFirst()
        val median = window.sorted()[window.size / 2]

        val previous = ema
        var base = median
        val untrusted = accuracyM > SPEED_FAIR_ACCURACY_M ||
            (speedAccuracyMps != null && speedAccuracyMps > BAD_SPEED_ACCURACY_MPS)
        if (previous != null && abs(median - previous) > SPIKE_CLAMP_MPS && untrusted) {
            base = previous + sign(median - previous) * SPIKE_CLAMP_MPS
        }

        var alpha = when {
            accuracyM <= SPEED_GOOD_ACCURACY_M -> 0.6f
            accuracyM <= SPEED_FAIR_ACCURACY_M -> 0.35f
            else -> 0.2f
        }
        if (speedAccuracyMps != null && speedAccuracyMps > BAD_SPEED_ACCURACY_MPS) alpha *= 0.5f
        val smoothed = if (previous == null) base else previous + alpha * (base - previous)
        ema = smoothed

        return if (stationary) {
            if (smoothed > STATIONARY_EXIT_MPS) {
                stationary = false
                smoothed
            } else {
                0f
            }
        } else {
            if (smoothed < STATIONARY_ENTER_MPS) {
                stationary = true
                ema = 0f
                window.clear()
                0f
            } else {
                smoothed
            }
        }
    }
}

internal fun smoothAltitude(previous: Double?, raw: Double, alpha: Float = ALTITUDE_EMA_ALPHA): Double =
    if (previous == null) raw else previous + alpha * (raw - previous)

/** Auto-record starts a trip on open unless the user manually paused this session. */
internal fun shouldAutoStart(tripState: TripState, autoRecord: Boolean, userPaused: Boolean): Boolean =
    autoRecord && !userPaused && tripState == TripState.IDLE

/** With auto-record on, Reset clears the stats but keeps recording. */
internal fun resetStaysRecording(autoRecord: Boolean, userPaused: Boolean): Boolean =
    autoRecord && !userPaused
