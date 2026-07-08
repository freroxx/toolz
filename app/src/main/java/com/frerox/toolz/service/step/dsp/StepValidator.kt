package com.frerox.toolz.service.step.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Validates candidate step sequences using rhythm-based heuristics:
 * 1. Coefficient-of-Variation (CV) check — reject irregular intervals
 * 2. Autocorrelation — confirms periodicity in step timing
 * 3. Rhythmic step count — requires N consecutive rhythmic steps before confirming
 * 
 * Designed for real-time O(n) evaluation with fixed memory footprint.
 */
class StepValidator(
    private val intervalHistory: LongArray = LongArray(MAX_INTERVAL_HISTORY),
    private var historyCount: Int = 0,
    private var historyHead: Int = 0
) {
    private var consecutiveRhythmicSteps = 0
    
    // Configurable per sensitivity
    var maxCv: Float = 0.32f
    var correlationThreshold: Double = 0.65
    var requiredRhythmicSteps: Int = 6
    
    data class ValidationResult(
        val isValid: Boolean,
        val isWalking: Boolean,
        val cadence: Int,           // steps per minute
        val cv: Float,             // coefficient of variation
        val correlation: Double    // autocorrelation coefficient
    )
    
    /**
     * Evaluate a new step interval. Call after each candidate peak.
     * @param intervalMs time since last step (ms)
     * @return ValidationResult with rhythm analysis
     */
    fun evaluate(intervalMs: Long): ValidationResult {
        // Enforce physical bounds
        if (intervalMs < MIN_STEP_INTERVAL_MS || intervalMs > MAX_STEP_INTERVAL_MS) {
            consecutiveRhythmicSteps = 0
            return ValidationResult(
                isValid = false, isWalking = false,
                cadence = 0, cv = 1f, correlation = 0.0
            )
        }
        
        // Add to history
        intervalHistory[historyHead] = intervalMs
        historyHead = (historyHead + 1) % MAX_INTERVAL_HISTORY
        historyCount = minOf(historyCount + 1, MAX_INTERVAL_HISTORY)
        
        if (historyCount < 3) {
            return ValidationResult(
                isValid = false, isWalking = false,
                cadence = (60000.0 / intervalMs).toInt(),
                cv = 0f, correlation = 0.0
            )
        }
        
        // CV check
        val cv = computeCv()
        val isRhythmic = cv <= maxCv
        
        if (isRhythmic) {
            consecutiveRhythmicSteps++
        } else {
            // Graceful degradation — don't immediately reset
            consecutiveRhythmicSteps = maxOf(0, consecutiveRhythmicSteps - 2)
        }
        
        // Autocorrelation check
        val correlation = computeAutocorrelation()
        val isPeriodic = correlation >= correlationThreshold
        
        val cadence = (60000.0 / intervalMs).toInt()
        
        val isWalking = consecutiveRhythmicSteps >= requiredRhythmicSteps
        
        return ValidationResult(
            isValid = isPeriodic && isRhythmic,
            isWalking = isWalking,
            cadence = cadence,
            cv = cv,
            correlation = correlation
        )
    }
    
    /** Reset validation state (on timeout or GPS suspend) */
    fun reset() {
        historyCount = 0
        historyHead = 0
        consecutiveRhythmicSteps = 0
    }
    
    /** Current cadence estimate */
    fun currentCadence(): Int {
        if (historyCount == 0) return 0
        val recent = getRecentIntervals(3)
        return if (recent.isEmpty()) 0 else (60000.0 / recent.average()).toInt()
    }
    
    /** True if we've seen a confirmed walking sequence */
    fun hasConfirmedRhythm(): Boolean = consecutiveRhythmicSteps >= requiredRhythmicSteps
    
    // --- Private DSP helpers ---
    
    private fun computeCv(): Float {
        val intervals = getRecentIntervals()
        if (intervals.size < 2) return 1f
        
        val mean = intervals.average()
        if (mean <= 0) return 1f
        
        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        return (stdDev / mean).toFloat().coerceIn(0f, 1f)
    }
    
    /**
     * Compute lag-1 autocorrelation of the interval sequence.
     * Values near 1.0 = highly periodic (rhythmic walking).
     * Values near 0.0 = random/irregular (not walking).
     * 
     * r = Σ((x[i] - μ)(x[i+1] - μ)) / Σ((x[i] - μ)²)
     */
    private fun computeAutocorrelation(): Double {
        val intervals = getRecentIntervals()
        if (intervals.size < 4) return 0.0
        
        val mean = intervals.average()
        
        var numerator = 0.0
        var denominator = 0.0
        
        for (i in 0 until intervals.size - 1) {
            val dx1 = intervals[i] - mean
            val dx2 = intervals[i + 1] - mean
            numerator += dx1 * dx2
            denominator += dx1 * dx1
        }
        
        return if (denominator > 0) numerator / denominator else 0.0
    }
    
    private fun getRecentIntervals(count: Int = historyCount): List<Long> {
        if (historyCount == 0) return emptyList()
        val result = mutableListOf<Long>()
        val start = if (historyCount < MAX_INTERVAL_HISTORY) 0
                    else (historyHead - historyCount + MAX_INTERVAL_HISTORY) % MAX_INTERVAL_HISTORY
        var idx = start
        repeat(minOf(count, historyCount)) {
            if (intervalHistory[idx] > 0) result.add(intervalHistory[idx])
            idx = (idx + 1) % MAX_INTERVAL_HISTORY
        }
        return result
    }
    
    companion object {
        private const val MAX_INTERVAL_HISTORY = 16
        const val MIN_STEP_INTERVAL_MS = 220L
        const val MAX_STEP_INTERVAL_MS = 1000L
    }
}
