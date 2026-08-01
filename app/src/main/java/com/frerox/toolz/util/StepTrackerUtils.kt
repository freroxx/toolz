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

package com.frerox.toolz.util

import com.frerox.toolz.data.steps.StepEntry
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Step-tracking utility functions: calorie estimation, distance conversion,
 * move-minute tracking, GPS gating, Kalman filtering, and bucket aggregation.
 *
 * Unless noted otherwise, all functions are thread-safe and have no mutable
 * shared state.  [KalmanFilter] is NOT thread-safe — a separate instance must
 * be created per service lifecycle (as done in [StepCounterService]).
 */
object StepTrackerUtils {

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    /** Default MET value for moderate walking (~5 km/h). */
    private const val MET_WALKING = 3.5f

    /** Default assumed cadence (steps per minute) used by move-minute estimation. */
    private const val DEFAULT_STEPS_PER_MINUTE = 100f

    /**
     * Minimum steps on a day to count it as "active" for move-minute purposes.
     * Matches the WHO / standard fitness-tracker threshold.
     */
    private const val ACTIVE_DAY_THRESHOLD = 250

    /**
     * Minimum absolute state estimate (metres) below which spike detection
     * is skipped to avoid floating-point overflow in the Kalman filter.
     */
    private const val MIN_ESTIMATE_FOR_SPIKE_DETECTION = 1e-6

    /**
     * Factor by which the measurement-noise covariance (R) is multiplied
     * when a spike is detected. 50× inflation means the outlier contributes
     * only ~2 % of its raw reading, heavily suppressing GPS glitches.
     */
    private const val R_INFLATION_FACTOR = 50.0

    // ---------------------------------------------------------------------------
    // Calorie Calculation
    // Uses the MET (Metabolic Equivalent of Task) model.
    // Formula:  kcal = MET × weightKg × durationHours
    // MET ≈ 3.5 for brisk walking (5 km/h); 3.0 for casual walking.
    // ---------------------------------------------------------------------------

    /**
     * Simplified calorie estimate using a per-1k-step coefficient.
     * Fast path — no weight or stride needed.
     *
     * @param steps         Total step count.
     * @param caloriesPer1k Kilocalories burned per 1,000 steps (user-configured).
     * @return Estimated kilocalories, rounded toward zero.
     */
    fun calculateCalories(steps: Int, caloriesPer1k: Int): Int =
        (steps / 1000.0 * caloriesPer1k).toInt()

    /**
     * Weight-aware MET calorie estimate.
     *
     * @param steps        Total step count.
     * @param stepLengthCm Stride length in centimetres (used only to derive duration).
     * @param weightKg     Body weight in kilograms (default 70 kg).
     * @param met          Metabolic Equivalent of Task for the activity
     *                     (default 3.5 — brisk walk).
     *                     Typical values: walking 3.0–3.5 | light jog 5.8 | running 9.8.
     * @return Estimated kilocalories, at least 0.
     */
    fun calculateCaloriesMet(
        steps: Int,
        stepLengthCm: Int,
        weightKg: Float = 70f,
        met: Float = MET_WALKING
    ): Int {
        if (steps == 0) return 0
        val durationHours = (steps * stepLengthCm / 100f) / (DEFAULT_STEPS_PER_MINUTE * 60f)
        return (met * weightKg * durationHours).toInt().coerceAtLeast(0)
    }

    // ---------------------------------------------------------------------------
    // Distance helpers
    // ---------------------------------------------------------------------------

    /**
     * Converts a step count to distance using the supplied stride length.
     *
     * @param steps        Total step count.
     * @param stepLengthCm Stride length in centimetres.
     * @return Distance in kilometres.
     */
    fun calculateDistanceKm(steps: Int, stepLengthCm: Int): Double =
        (steps * stepLengthCm) / 100_000.0

    /** km → miles conversion. */
    fun kmToMiles(km: Double): Double = km * 0.621371

    // ---------------------------------------------------------------------------
    // Move-Minutes
    // ---------------------------------------------------------------------------

    /**
     * Returns the number of qualifying "move minutes" based on total daily steps.
     * Each minute of continuous walking above the [ACTIVE_DAY_THRESHOLD] cadence
     * counts as one move minute.
     *
     * @param steps Total steps for the day.
     * @return Whole move minutes.
     */
    fun calculateMoveMinutes(steps: Int): Int =
        (steps / DEFAULT_STEPS_PER_MINUTE).toInt()

    // ---------------------------------------------------------------------------
    // GPS gating
    // ---------------------------------------------------------------------------

    /**
     * Returns `true` when the device appears stationary so the location listener
     * can sleep to save battery.
     *
     * @param stepsDeltaInWindow New steps detected in the observation window.
     * @param threshold          Minimum steps to be considered "active" (default 5).
     */
    fun isDeviceStatic(stepsDeltaInWindow: Int, threshold: Int = 5): Boolean =
        stepsDeltaInWindow < threshold

    // ---------------------------------------------------------------------------
    // Kalman Filter — adaptive R variant for GPS spike rejection
    // ---------------------------------------------------------------------------

    /**
     * Scalar Kalman filter for smoothing noisy GPS / sensor distance readings.
     *
     * **Thread-confinement:** this class is **not thread-safe**. Create one
     * instance per service lifecycle and access it only from that thread.
     * (A single instance is created in [StepCounterService.onCreate]].)
     *
     * @param q          Process-noise covariance — how much the true value may
     *                   drift between measurements. Smaller = smoother output.
     *                   Default 0.0001 is appropriate for sub-metre GPS data.
     * @param r          Initial measurement-noise covariance. Larger = more
     *                   smoothing. Default 0.01.
     */
    class KalmanFilter(
        private val q: Double = 0.0001,
        private var r: Double = 0.01
    ) {
        /** Estimated state value, or `null` before the first measurement. */
        private var x: Double? = null

        /** Estimation error covariance. */
        private var p: Double = 1.0

        /** Kalman gain from the last update. */
        private var k: Double = 0.0

        /**
         * Spike threshold: if |measurement − estimate| > estimate × SPIKE_MULTIPLIER,
         * the measurement is treated as a GPS glitch and R is inflated.
         */
        private val SPIKE_MULTIPLIER = 3.0

        /**
         * Filters a new noisy measurement and returns the updated state estimate.
         * R is automatically inflated (persisted to the instance field) when a spike
         * is detected, so subsequent measurements continue to be treated with extra
         * scepticism until [reset] is called.
         *
         * @param measurement Raw sensor / GPS reading.
         * @return Smoothed estimate.
         */
        fun filter(measurement: Double): Double {
            if (x == null) {
                x = measurement
                return x!!
            }

            // Spike guard: skip detection when the estimate is tiny to avoid
            // division overflow (e.g. near a sensor cold-start).
            val absX = kotlin.math.abs(x!!)
            val spikeThreshold = if (absX > MIN_ESTIMATE_FOR_SPIKE_DETECTION) {
                x!! * SPIKE_MULTIPLIER
            } else {
                Double.POSITIVE_INFINITY  // treat all readings as normal when x ≈ 0
            }

            // Adaptive R: inflate measurement noise when a large jump is detected.
            // The inflated value is PERSISTED back into r so it affects subsequent calls.
            if (kotlin.math.abs(measurement - x!!) > spikeThreshold) {
                r *= R_INFLATION_FACTOR
            }

            // Prediction step.
            p += q

            // Measurement update.
            k = p / (p + r)
            x = x!! + k * (measurement - x!!)
            p *= (1 - k)

            return x!!
        }

        /**
         * Resets internal state to construction defaults.
         * Note: r is intentionally NOT reset so that R inflation learned from
         * recent spikes persists across pauses (avoids re-learning on every restart).
         */
        fun reset() {
            x = null
            p = 1.0
            k = 0.0
        }
    }

    // ---------------------------------------------------------------------------
    // Formatting helpers
    // ---------------------------------------------------------------------------

    private val stepFormatter: NumberFormat by lazy {
        NumberFormat.getNumberInstance(Locale.getDefault())
    }

    /**
     * Formats a step count with thousands separators, e.g. 12,345.
     */
    fun formatSteps(steps: Int): String = stepFormatter.format(steps)

    /**
     * Formats a distance value to 2 decimal places with "km" unit.
     */
    fun formatDistance(km: Double): String =
        String.format(Locale.getDefault(), "%.2f km", km)

    // ---------------------------------------------------------------------------
    // Bucket aggregation  (all use java.time)
    // ---------------------------------------------------------------------------

    private val isoDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * Returns steps for each day of the current week (Mon–Sun), oldest first.
     * Uses ISO week numbering (Monday = first day of week).
     */
    fun aggregateWeekly(entries: List<StepEntry>): List<StepEntry> {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)

        return (0..6).map { dayOffset ->
            val date = monday.plusDays(dayOffset.toLong())
            val dateStr = date.format(isoDateFormatter)
            val steps = entries.find { it.date == dateStr }?.steps ?: 0
            StepEntry(
                date = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                steps = steps,
                lastSensorValue = 0
            )
        }
    }

    /**
     * Returns steps for each of the last 5 complete weeks, oldest first.
     * Label format: "MMM WN" (e.g., "Jun W2").
     */
    fun aggregateMonthly(entries: List<StepEntry>): List<StepEntry> {
        val today = LocalDate.now()
        val result = mutableListOf<StepEntry>()

        for (weeksAgo in 4 downTo 0) {
            val weekStart = today.minusWeeks(weeksAgo.toLong()).with(DayOfWeek.MONDAY)
            val weekFields = WeekFields.of(Locale.getDefault())
            val weekOfMonth = weekStart.get(weekFields.weekOfMonth())
            val monthName = weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            var weekSteps = 0

            for (dayOffset in 0..6) {
                val date = weekStart.plusDays(dayOffset.toLong())
                val dateStr = date.format(isoDateFormatter)
                weekSteps += entries.find { it.date == dateStr }?.steps ?: 0
            }

            result.add(
                StepEntry(
                    date = "$monthName W$weekOfMonth",
                    steps = weekSteps,
                    lastSensorValue = 0
                )
            )
        }
        return result
    }

    /**
     * Returns steps for each month of the current year, January → December.
     */
    fun aggregateYearly(entries: List<StepEntry>): List<StepEntry> {
        val year = LocalDate.now().year

        return (1..12).map { month ->
            val monthPrefix = YearMonth.of(year, month).format(
                DateTimeFormatter.ofPattern("yyyy-MM")
            )
            val monthSteps = entries
                .filter { it.date.startsWith(monthPrefix) }
                .sumOf { it.steps }
            StepEntry(
                date = YearMonth.of(year, month)
                    .month
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                steps = monthSteps,
                lastSensorValue = 0
            )
        }
    }
}
