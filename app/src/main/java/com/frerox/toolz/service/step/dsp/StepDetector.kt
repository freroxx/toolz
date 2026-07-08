package com.frerox.toolz.service.step.dsp

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Modular step detection pipeline that coordinates DSP components.
 * 
 * Architecture:
 * 
 *  [SensorManager] 
 *       │
 *       ▼
 *  ┌─────────────┐
 *  │SensorFusion │ ← gyroscope + vehicle/freefall detection
 *  └──────┬──────┘
 *         │ filtered
 *         ▼
 *  ┌─────────────┐
 *  │Magnitude    │ ← gravity isolation + peak detection
 *  │Processor    │
 *  └──────┬──────┘
 *         │ smoothed magnitude
 *         ▼
 *  ┌─────────────┐
 *  │Adaptive     │ ← threshold learning + cadence adaptation
 *  │Threshold    │
 *  └──────┬──────┘
 *         │ validated peaks
 *         ▼
 *  ┌─────────────┐
 *  │Step         │ ← CV + autocorrelation rhythm validation
 *  │Validator    │
 *  └──────┬──────┘
 *         │ confirmed steps
 *         ▼
 *      [callback]
 * 
 * Benefits over monolithic design:
 * - Each module is independently testable with synthetic data
 * - Threshold adaptation runs without touching peak detection logic
 * - Sensor fusion decoupling means gyroscope handling doesn't affect DSP path
 * - All ring buffers use fixed-size arrays (zero heap allocation in hot path)
 */
class StepDetector(
    private val onStepDetected: (stepCount: Int, cadence: Int) -> Unit,
    private val onStateChange: (StepState) -> Unit = {}
) {
    
    enum class StepState { IDLE, CANDIDATE, WALKING, SUSPENDED }
    
    // --- Modules ---
    private val magnitudeProcessor = MagnitudeProcessor()
    private val adaptiveThreshold = AdaptiveThreshold()
    private val stepValidator = StepValidator()
    private val sensorFusion = SensorFusion { suspended, reason ->
        if (suspended) setState(StepState.SUSPENDED)
        else if (state == StepState.SUSPENDED) setState(StepState.IDLE)
    }
    
    // --- State ---
    private var state: StepState = StepState.IDLE
    private var stepCount = 0
    private var lastStepTime = 0L
    private var candidateTimeout = 0L
    private var walkingTimeout = 0L
    
    // --- Config delegation (from sensitivity) ---
    fun setSensitivity(sensitivity: Int) {
        val preset = adaptiveThreshold.getPreset()
        val config = sensitivityToConfig(sensitivity)
        
        magnitudeProcessor.minPeakMagnitude = config.minPeakMagnitude
        magnitudeProcessor.minVarianceThreshold = config.minVarianceThreshold
        
        stepValidator.maxCv = config.maxCv
        stepValidator.requiredRhythmicSteps = config.requiredRhythmicSteps
        stepValidator.correlationThreshold = config.correlationThreshold
    }
    
    /** Process raw accelerometer data. Call from sensor callback. */
    fun processAccelerometer(values: FloatArray, timeMs: Long) {
        if (sensorFusion.isGpsSuspended) return
        
        val rawMagnitude = sqrt(
            values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
        )
        
        // Sensor fusion first — vehicle / freefall check
        if (sensorFusion.processAccelerometer(rawMagnitude, timeMs)) return
        
        // DSP pipeline
        val smoothedMag = magnitudeProcessor.processAccelerometer(values)
        val variance = magnitudeProcessor.signalVariance()
        
        // Noise floor check
        if (!adaptiveThreshold.isAboveNoiseFloor(variance)) {
            handleTimeouts(timeMs)
            return
        }
        
        // Peak detection
        val isPeak = magnitudeProcessor.detectPeak(
            smoothedMag, 
            timeMs, 
            MIN_INTER_PEAK_INTERVAL_MS
        )
        
        if (!isPeak) {
            handleTimeouts(timeMs)
            return
        }
        
        // Evaluate candidate interval
        val interval = if (lastStepTime > 0) timeMs - lastStepTime else 0L
        
        val result = stepValidator.evaluate(interval)
        
        when (state) {
            StepState.IDLE, StepState.CANDIDATE -> {
                if (result.isValid) {
                    confirmStep(timeMs, smoothedMag, variance, interval, result)
                }
            }
            StepState.WALKING -> {
                if (result.cv <= stepValidator.maxCv) {
                    emitStep(timeMs, result.cadence)
                } else {
                    // Rhythm broken — reset
                    stepValidator.reset()
                    setState(StepState.IDLE)
                }
            }
            StepState.SUSPENDED -> {
                // Wait for fusion to clear
            }
        }
        
        handleTimeouts(timeMs)
    }
    
    /** Process gyroscope data. Call from sensor callback. */
    fun processGyroscope(angularVelocity: Float, timeMs: Long) {
        sensorFusion.processGyroscope(angularVelocity, timeMs)
    }
    
    /** External GPS speed gate — suppress when speed > 10 m/s */
    fun setGpsSuspended(suspended: Boolean) {
        sensorFusion.setGpsSuspension(suspended)
    }
    
    /** Reset all state */
    fun reset() {
        magnitudeProcessor.reset()
        adaptiveThreshold.reset()
        stepValidator.reset()
        sensorFusion.reset()
        stepCount = 0
        lastStepTime = 0L
        setState(StepState.IDLE)
    }
    
    /** Current step count */
    fun getStepCount(): Int = stepCount
    
    /** Current cadence (steps/min) */
    fun getCadence(): Int = stepValidator.currentCadence()
    
    /** Current engine state */
    fun getState(): StepState = state
    
    /** True if currently in confirmed walking state */
    fun isWalking(): Boolean = state == StepState.WALKING
    
    private fun confirmStep(timeMs: Long, peakMag: Float, variance: Float, interval: Long, result: StepValidator.ValidationResult) {
        adaptiveThreshold.observeStep(interval, peakMag, variance)
        stepCount++
        lastStepTime = timeMs
        candidateTimeout = timeMs + CANDIDATE_TIMEOUT_MS
        walkingTimeout = timeMs + WALKING_TIMEOUT_MS
        setState(StepState.CANDIDATE)
        onStepDetected(stepCount, result.cadence)
    }
    
    private fun emitStep(timeMs: Long, cadence: Int) {
        stepCount++
        lastStepTime = timeMs
        walkingTimeout = timeMs + WALKING_TIMEOUT_MS
        if (state != StepState.WALKING) setState(StepState.WALKING)
        onStepDetected(stepCount, cadence)
    }
    
    private fun setState(newState: StepState) {
        if (state != newState) {
            state = newState
            onStateChange(newState)
        }
    }
    
    private fun handleTimeouts(timeMs: Long) {
        when (state) {
            StepState.CANDIDATE -> {
                if (timeMs > candidateTimeout) {
                    stepValidator.reset()
                    setState(StepState.IDLE)
                }
            }
            StepState.WALKING -> {
                if (timeMs > walkingTimeout) {
                    adaptiveThreshold.onSessionEnd()
                    stepValidator.reset()
                    setState(StepState.IDLE)
                }
            }
            else -> {}
        }
    }
    
    // --- Config presets (same as StepDetectionEngine for compatibility) ---
    private data class Config(
        val minPeakMagnitude: Float,
        val minVarianceThreshold: Float,
        val maxCv: Float,
        val requiredRhythmicSteps: Int,
        val correlationThreshold: Double
    )
    
    private fun sensitivityToConfig(sensitivity: Int): Config {
        return when {
            sensitivity <= 20 -> Config(1.0f, 0.015f, 0.25f, 8, 0.75)
            sensitivity <= 40 -> Config(0.9f, 0.012f, 0.28f, 7, 0.70)
            sensitivity <= 60 -> Config(0.8f, 0.01f, 0.32f, 6, 0.65)
            sensitivity <= 80 -> Config(0.7f, 0.008f, 0.38f, 4, 0.57)
            else -> Config(0.6f, 0.005f, 0.45f, 3, 0.50)
        }
    }
    
    companion object {
        private const val MIN_INTER_PEAK_INTERVAL_MS = 150L
        private const val CANDIDATE_TIMEOUT_MS = 2000L
        private const val WALKING_TIMEOUT_MS = 3000L
    }
}
