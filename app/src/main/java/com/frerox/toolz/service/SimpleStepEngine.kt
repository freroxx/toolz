package com.frerox.toolz.service

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SimpleStepEngine — a lightweight, low-latency step counting engine.
 *
 * Design philosophy:
 *   The StepDetectionEngine (DSP engine) is optimised for accuracy in noisy, ambiguous
 *   environments: it validates rhythm via autocorrelation, enforces coefficient-of-variation
 *   limits, and requires several consecutive rhythmic peaks before committing steps.  This
 *   makes it resilient against false positives but introduces ~3–5 second warm-up latency
 *   and can under-count very short bursts of walking (e.g. crossing a room in 10 steps).
 *
 *   The SimpleStepEngine trades some false-positive rejection for:
 *   - Immediate step registration (no rhythm warm-up period).
 *   - Uniform behaviour regardless of walking style or pace.
 *   - Predictable, auditable logic — each step follows directly from peak detection rules.
 *   - Lower CPU overhead (no ring-buffer autocorrelation on every sample).
 *
 *   It is still more than a naive threshold: it applies a gravity-separating low-pass filter,
 *   enforces a physiologically-meaningful inter-peak interval (MIN_INTER_PEAK_MS = 250 ms,
 *   corresponding to a maximum cadence of ~240 bpm which no human sustains), and uses a
 *   two-stage adaptive threshold (see AdaptiveThreshold) to track the local peak baseline
 *   without hard-coding a single number.
 *
 * When to use:
 *   Recommend this engine to users who:
 *   - Experience the DSP engine missing short walks or reporting zero steps on stair climbs.
 *   - Prefer "count everything, even if a few are wrong" over "only count confirmed patterns".
 *   - Have a very regular gait that rarely triggers false positives from phone vibrations.
 *
 *   The engine toggle lives in StepCounterSettingsBottomSheet under "Detection Engine" and
 *   persists via SettingsRepository.stepEngineMode.  The service reads this setting on start
 *   and routes sensor events to the appropriate engine.
 *
 * Thread-safety:
 *   All public methods are synchronised on lock.  Call from any thread; the sensor callback
 *   runs on a dedicated SensorThread in StepCounterService.
 */
class SimpleStepEngine(
    /** Invoked on the calling thread each time one confirmed step is detected. */
    private val onStepDetected: (count: Int, cadence: Int) -> Unit,
    /** Developer logging callback. */
    private val onLog: (String) -> Unit = {},
    /** Whether to prefer hardware step counter events over accelerometer. */
    private val useHardwareStepCounter: Boolean = false
) {

    // -------------------------------------------------------------------------
    // Public API (mirrors StepDetectionEngine surface used by the service)
    // -------------------------------------------------------------------------

    /** Total steps counted since last [reset]. */
    @Volatile var stepCount: Int = 0
        private set

    /** Whether GPS speed gate has forced the engine into a suspended state. */
    @Volatile var isSuspended: Boolean = false
        private set

    // -------------------------------------------------------------------------
    // Tuning constants
    // -------------------------------------------------------------------------
    companion object {
        private const val ALPHA_GRAVITY = 0.9f
        private const val ALPHA_MAGNITUDE = 0.65f

        /** Minimum ms between peaks (max cadence ~170 bpm is 353ms, be lenient to 250ms) */
        private const val MIN_INTER_PEAK_MS = 250L
        private const val MAX_INTER_PEAK_MS = 2_500L
        private const val DEFAULT_MIN_PEAK_LIFT = 0.30f

        /** Gyro gate: 3.5 rad/s ≈ 200 deg/s — a quick wrist flick */
        private const val MAX_GYRO_RAD = 3.5f
        private const val GYRO_FREEZE_MS = 400L
        private const val CADENCE_WINDOW = 4
    }

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------
    private val lock = Any()

    // Gravity low-pass filter state
    private val gravity = FloatArray(3) { 0f }

    // Smoothed magnitude of the linear-acceleration vector
    private var smoothedMag = 0f

    // Peak detector state
    private var isRising = false
    private var phasePeak = 0f
    private var lastPeakTimeMs = 0L

    // Adaptive threshold — tracks the rolling peak average
    private val adaptiveThreshold = AdaptiveThreshold()

    // Gyroscope gate
    private var gyroFreezeUntilMs = 0L

    // Cadence ring buffer (stores recent inter-peak intervals in ms)
    private val intervalBuf = LongArray(CADENCE_WINDOW) { 0L }
    private var intervalHead = 0
    private var intervalCount = 0

    // Sensitivity tuning
    private var currentMinPeakLift = DEFAULT_MIN_PEAK_LIFT

    // -------------------------------------------------------------------------
    // Public methods
    // -------------------------------------------------------------------------

    /**
     * Process a raw accelerometer sample (x, y, z in m/s²) at time [timeMs].
     * Called from the SensorThread; safe to call at up to 100 Hz.
     */
    fun processAccelerometer(values: FloatArray, timeMs: Long) {
        synchronized(lock) {
            if (isSuspended) return

            // ---- Step 1: Gravity isolation via low-pass filter ----
            gravity[0] = ALPHA_GRAVITY * gravity[0] + (1 - ALPHA_GRAVITY) * values[0]
            gravity[1] = ALPHA_GRAVITY * gravity[1] + (1 - ALPHA_GRAVITY) * values[1]
            gravity[2] = ALPHA_GRAVITY * gravity[2] + (1 - ALPHA_GRAVITY) * values[2]

            val linX = values[0] - gravity[0]
            val linY = values[1] - gravity[1]
            val linZ = values[2] - gravity[2]

            val rawMag = sqrt(linX * linX + linY * linY + linZ * linZ)

            // ---- Step 2: Magnitude smoothing ----
            smoothedMag = ALPHA_MAGNITUDE * smoothedMag + (1 - ALPHA_MAGNITUDE) * rawMag

            // ---- Step 3: Peak detection ----
            val threshold = adaptiveThreshold.current()

            if (smoothedMag > threshold) {
                if (smoothedMag > phasePeak) {
                    phasePeak = smoothedMag
                }
                if (!isRising) onLog("SIMPLE: Rising! Threshold=${String.format("%.2f", threshold)}")
                isRising = true
            } else if (isRising) {
                // Descending edge — candidate peak
                val peakLift = phasePeak - threshold

                // Enforce minimum lift (ignore low-energy noise peaks)
                if (peakLift >= getMinPeakLiftFallback()) {
                    val interval = if (lastPeakTimeMs > 0L) timeMs - lastPeakTimeMs else 0L

                    val validInterval = interval == 0L ||
                            (interval in MIN_INTER_PEAK_MS..MAX_INTER_PEAK_MS)

                    val gyroOk = timeMs >= gyroFreezeUntilMs
                    
                    onLog("SIMPLE: Peak Lift=${String.format("%.2f", peakLift)} Interval=${interval}ms GyroOk=$gyroOk")

                    if (validInterval && gyroOk) {
                        // ---- Confirmed step ----
                        adaptiveThreshold.observe(phasePeak)

                        if (interval > 0L) {
                            intervalBuf[intervalHead] = interval
                            intervalHead = (intervalHead + 1) % CADENCE_WINDOW
                            if (intervalCount < CADENCE_WINDOW) intervalCount++
                        }

                        if (useHardwareStepCounter) {
                            // If using hardware step counter, we shouldn't be processing accel for steps.
                            // But just in case, do nothing here.
                        } else {
                            stepCount++
                            val cadence = computeCadence()
                            onLog("SIMPLE: Step #$stepCount | cadence=${cadence}bpm")
                            onStepDetected(1, cadence)
                        }

                        lastPeakTimeMs = timeMs
                    }
                }

                // Reset phase ONLY after processing the descending edge
                isRising = false
                phasePeak = 0f
            }
        }
    }

    /**
     * Process a hardware step counter event (delta).
     * In SIMPLE mode, we instantly trust the OS and emit it.
     */
    fun onOsStepDetected(delta: Int) {
        synchronized(lock) {
            if (useHardwareStepCounter && delta > 0) {
                stepCount += delta
                onLog("SIMPLE: OS +$delta directly emitted (total=$stepCount)")
                onStepDetected(delta, computeCadence())
            }
        }
    }

    /**
     * Process a gyroscope sample.  High angular velocity gates out the next
     * [GYRO_FREEZE_MS] milliseconds of step detection.
     */
    fun processGyroscope(angularVelocity: Float, timeMs: Long) {
        synchronized(lock) {
            if (angularVelocity > MAX_GYRO_RAD) {
                gyroFreezeUntilMs = timeMs + GYRO_FREEZE_MS
            }
        }
    }

    /**
     * GPS speed gate — call with [suspended] = true when speed > 10 m/s (likely driving).
     * Matches the interface used by [StepCounterService.locationCallback].
     */
    fun setGpsSuspended(suspended: Boolean) {
        synchronized(lock) {
            isSuspended = suspended
            if (suspended) {
                isRising = false
                phasePeak = 0f
            }
        }
    }

    /**
     * Sets engine sensitivity (0-100).
     */
    fun setSensitivity(sensitivity: Int) {
        synchronized(lock) {
            val normalized = sensitivity.coerceIn(0, 100) / 100f
            // Lower sensitivity = higher threshold (stricter)
            // Higher sensitivity = lower threshold (more lenient)
            // Range: 0.15f (lenient) to 0.5f (strict)
            currentMinPeakLift = 0.5f - (0.35f * normalized)
            adaptiveThreshold.setSensitivity(normalized)
        }
    }

    /**
     * Resets all engine state.  Call on a new day, sensor power-cycle, or when the
     * user switches engine mode.
     */
    fun reset() {
        synchronized(lock) {
            stepCount = 0
            isRising = false
            phasePeak = 0f
            lastPeakTimeMs = 0L
            gyroFreezeUntilMs = 0L
            smoothedMag = 0f
            gravity.fill(0f)
            intervalBuf.fill(0L)
            intervalHead = 0
            intervalCount = 0
            adaptiveThreshold.reset()
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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

    /**
     * Minimum "peak lift" required for a candidate peak.
     * Falls back to currentMinPeakLift when the adaptive threshold is below
     * the hard floor (avoids counting tremors at rest).
     */
    private fun getMinPeakLiftFallback(): Float =
        maxOf(currentMinPeakLift, adaptiveThreshold.current() * 0.25f)

    // -------------------------------------------------------------------------
    // Adaptive threshold (inner class — not shared with DSP engine)
    // -------------------------------------------------------------------------

    /**
     * Tracks a rolling exponential-moving-average of recent peak magnitudes.
     *
     * The threshold is set at a ratio of the rolling peak average.
     */
    private inner class AdaptiveThreshold {
        private val DECAY = 0.85f        // Faster decay
        private var ratio = 0.45f        
        private var floor = 0.15f        
        private val INITIAL_PEAK = 1.5f 

        private var rollingPeak = INITIAL_PEAK

        fun current(): Float = maxOf(rollingPeak * ratio, floor)

        fun observe(peak: Float) {
            rollingPeak = DECAY * rollingPeak + (1 - DECAY) * peak
        }

        fun setSensitivity(normalized: Float) {
            // Lower sensitivity = higher ratio/floor (stricter)
            // Higher sensitivity = lower ratio/floor (more lenient)
            ratio = 0.55f - (0.20f * normalized) 
            floor = 0.20f - (0.10f * normalized) 
        }

        fun reset() {
            rollingPeak = INITIAL_PEAK
        }
    }
}
