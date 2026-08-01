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

/**
 * Adaptive threshold learner — adjusts detection parameters based on 
 * the user's actual walking cadence and signal strength.
 * 
 * Runs in two modes:
 * - LEARNING: observes confirmed steps to build user baseline (first ~50 steps)
 * - ADAPTIVE: fine-tunes thresholds using exponential moving average of cadence
 * 
 * This enables the engine to adapt to:
 * - Slow walkers (elderly) vs joggers
 * - Device placement ( pocket vs hand vs belt )
 * - Walking surface (treadmill vs uneven terrain)
 */
class AdaptiveThreshold(
    private val learnWindowSize: Int = 50,   // steps to build baseline
    private val emaAlpha: Float = 0.1f        // responsiveness of adaptation
) {
    enum class Mode { LEARNING, ADAPTIVE }
    
    // --- Observed step metrics ---
    private val stepIntervalHistory = LongRingBuffer(MAX_INTERVAL_HISTORY)
    private val peakMagnitudeHistory = FloatArray(MAX_INTERVAL_HISTORY) { NOT_SET }
    private var historyHead = 0
    private var confirmedSteps = 0
    
    // --- Learned baselines ---
    private var learnedMeanInterval = 0L       // ms between steps
    private var learnedMeanMagnitude = 0f       // peak magnitude
    private var learnedVarianceThreshold = 0.01f
    private var learnedMinPeak = 0.6f
    
    // --- Current mode ---
    var mode: Mode = Mode.LEARNING
        private set
    
    // --- Runtime EMA state (updated every confirmed step) ---
    private var emaInterval = 0f
    private var emaMagnitude = 0f
    private var emaVariance = 0.01f
    
    // --- Config presets keyed by user speed ---
    data class ThresholdPreset(
        val minPeakMagnitude: Float,
        val minVarianceThreshold: Float,
        val maxCv: Float,           // coefficient of variation tolerance
        val requiredRhythmicSteps: Int,
        val correlationThreshold: Double
    )
    
    /**
     * Called each time a step is confirmed. Observes the cadence and builds a baseline.
     * @param intervalMs time since the previous confirmed step
     * @param peakMagnitude the magnitude delta at the detected peak
     * @param signalVariance variance of the magnitude window at detection time
     */
    fun observeStep(intervalMs: Long, peakMagnitude: Float, signalVariance: Float) {
        if (mode == Mode.LEARNING) {
            // Record into learning window
            stepIntervalHistory.push(intervalMs)
            peakMagnitudeHistory[historyHead % MAX_INTERVAL_HISTORY] = peakMagnitude
            historyHead++
            confirmedSteps++
            
            if (confirmedSteps >= learnWindowSize) {
                computeBaseline()
                mode = Mode.ADAPTIVE
            }
        } else {
            // Adaptive EMA update
            emaInterval = if (emaInterval == 0f) intervalMs.toFloat()
                           else emaInterval * (1 - emaAlpha) + intervalMs * emaAlpha
            emaMagnitude = if (emaMagnitude == 0f) peakMagnitude
                           else emaMagnitude * (1 - emaAlpha) + peakMagnitude * emaAlpha
            emaVariance = if (emaVariance < 0.001f) signalVariance
                          else emaVariance * (1 - emaAlpha) + signalVariance * emaAlpha
            
            stepIntervalHistory.push(intervalMs)
            
            // Gentle re-tune: if EMA shifts >20% from learned baseline, update preset
            val intervalRatio = learnedMeanInterval.toFloat() / emaInterval.coerceAtLeast(1f)
            if (intervalRatio > 1.3f || intervalRatio < 0.7f) {
                recalculateThresholdPreset()
            }
        }
    }
    
    /** Called when walking session ends — flush learning state */
    fun onSessionEnd() {
        // Keep learned baselines for next session
    }
    
    /** Called on engine reset — clear all state */
    fun reset() {
        stepIntervalHistory.clear()
        historyHead = 0
        confirmedSteps = 0
        learnedMeanInterval = 0L
        learnedMeanMagnitude = 0f
        learnedVarianceThreshold = 0.01f
        learnedMinPeak = 0.6f
        emaInterval = 0f
        emaMagnitude = 0f
        emaVariance = 0.01f
        mode = Mode.LEARNING
    }
    
    /** Get current threshold preset based on learned / adaptive state */
    fun getPreset(): ThresholdPreset {
        return when (mode) {
            Mode.LEARNING -> DEFAULT_PRESET
            Mode.ADAPTIVE -> {
                // Choose preset based on current cadence
                val cadence = if (emaInterval > 0) 60000f / emaInterval else 1000f
                when {
                    cadence < 80  -> PRESET_SLOW       // <80 steps/min = slow walker
                    cadence < 110 -> PRESET_NORMAL      // 80-110 = normal walk
                    cadence < 150 -> PRESET_FAST        // 110-150 = brisk walk/jog
                    else           -> PRESET_RUNNER     // >150 = running
                }
            }
        }
    }
    
    /** Check if current signal variance is above noise floor */
    fun isAboveNoiseFloor(variance: Float): Boolean {
        return when (mode) {
            Mode.LEARNING -> variance >= DEFAULT_PRESET.minVarianceThreshold
            Mode.ADAPTIVE -> variance >= learnedVarianceThreshold * 0.8f
        }
    }
    
    /** Estimated steps per minute from learned interval */
    fun estimatedCadence(): Int {
        val interval = when (mode) {
            Mode.LEARNING -> learnedMeanInterval.takeIf { it > 0 } ?: 600L
            Mode.ADAPTIVE -> emaInterval.toLong().takeIf { it > 0 } ?: learnedMeanInterval.takeIf { it > 0 } ?: 600L
        }
        return if (interval > 0) (60000 / interval).toInt() else 0
    }
    
    private fun computeBaseline() {
        val intervals = stepIntervalHistory.toList().filter { it > 0 }
        if (intervals.isEmpty()) return
        
        learnedMeanInterval = intervals.average().toLong()
        
        val mags = peakMagnitudeHistory.filter { it != NOT_SET && it > 0 }
        if (mags.isNotEmpty()) {
            learnedMeanMagnitude = mags.average().toFloat()
            learnedMinPeak = (learnedMeanMagnitude * 0.6f).coerceIn(0.4f, 1.5f)
            learnedVarianceThreshold = (mags.map { (it - learnedMeanMagnitude) * (it - learnedMeanMagnitude) }.average() * 0.1f)
                .toFloat().coerceIn(0.005f, 0.05f)
        }
    }
    
    private fun recalculateThresholdPreset() {
        // Adjust learnedMinPeak toward EMA magnitude if drift detected
        learnedMinPeak = (learnedMinPeak * 0.7f + emaMagnitude * 0.3f * 0.6f).coerceIn(0.4f, 1.5f)
    }
    
    companion object {
        private const val MAX_INTERVAL_HISTORY = 100
        private const val NOT_SET = -1f
        
        // Default preset (before any learning)
        private val DEFAULT_PRESET = ThresholdPreset(
            minPeakMagnitude = 0.8f,
            minVarianceThreshold = 0.01f,
            maxCv = 0.32f,
            requiredRhythmicSteps = 6,
            correlationThreshold = 0.65
        )
        
        private val PRESET_SLOW = ThresholdPreset(
            minPeakMagnitude = 0.5f,    // lighter steps
            minVarianceThreshold = 0.006f,
            maxCv = 0.45f,              // more lenient interval variance
            requiredRhythmicSteps = 4,
            correlationThreshold = 0.55
        )
        
        private val PRESET_NORMAL = ThresholdPreset(
            minPeakMagnitude = 0.7f,
            minVarianceThreshold = 0.008f,
            maxCv = 0.35f,
            requiredRhythmicSteps = 5,
            correlationThreshold = 0.62
        )
        
        private val PRESET_FAST = ThresholdPreset(
            minPeakMagnitude = 0.9f,    // stronger peaks at faster cadence
            minVarianceThreshold = 0.012f,
            maxCv = 0.28f,
            requiredRhythmicSteps = 6,
            correlationThreshold = 0.68
        )
        
        private val PRESET_RUNNER = ThresholdPreset(
            minPeakMagnitude = 1.1f,
            minVarianceThreshold = 0.015f,
            maxCv = 0.25f,
            requiredRhythmicSteps = 7,
            correlationThreshold = 0.72
        )
    }
}
