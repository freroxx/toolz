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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Comprehensive unit tests for [StepTrackerUtils].
 */
class StepTrackerUtilsTest {

    // ---------------------------------------------------------------------------
    // 1. Calorie calculation
    // ---------------------------------------------------------------------------

    @Test
    fun `calculateCalories returns 0 for 0 steps`() {
        assertEquals(0, StepTrackerUtils.calculateCalories(0, 40))
    }

    @Test
    fun `calculateCalories returns correct value for exact 1k`() {
        assertEquals(40, StepTrackerUtils.calculateCalories(1000, 40))
    }

    @Test
    fun `calculateCalories scales linearly`() {
        assertEquals(200, StepTrackerUtils.calculateCalories(5000, 40))
        assertEquals(400, StepTrackerUtils.calculateCalories(10_000, 40))
    }

    @Test
    fun `calculateCaloriesMet returns 0 for 0 steps`() {
        assertEquals(0, StepTrackerUtils.calculateCaloriesMet(
            steps = 0,
            stepLengthCm = 75,
            weightKg = 70f,
            met = 3.5f
        ))
    }

    @Test
    fun `calculateCaloriesMet differentiates by MET value`() {
        // steps=10000, stride=40cm → durationHours = (10000 × 40/100) / (100 × 60) = 0.667h
        // At 70kg: met=3.0 → ~140 kcal, met=9.8 → ~459 kcal  (both > 0, different from each other)
        val steps = 10_000
        val stride = 40
        val weight = 70f
        val lowMet = StepTrackerUtils.calculateCaloriesMet(steps, stride, weight, met = 3.0f)
        val highMet = StepTrackerUtils.calculateCaloriesMet(steps, stride, weight, met = 9.8f)
        assertTrue("High-MET ($highMet) should be > low-MET ($lowMet)", highMet > lowMet)
    }

    @Test
    fun `calculateCaloriesMet is never negative`() {
        val result = StepTrackerUtils.calculateCaloriesMet(
            steps = 1,
            stepLengthCm = 75,
            weightKg = 70f
        )
        assertTrue(result >= 0)
    }

    // ---------------------------------------------------------------------------
    // 2. Distance helpers
    // ---------------------------------------------------------------------------

    @Test
    fun `calculateDistanceKm returns 0 for 0 steps`() {
        assertEquals(0.0, StepTrackerUtils.calculateDistanceKm(0, 75), 0.0)
    }

    @Test
    fun `calculateDistanceKm uses stride length`() {
        // 10k × 75cm = 750,000cm = 7.5km
        assertEquals(7.5, StepTrackerUtils.calculateDistanceKm(10_000, 75), 0.001)
        // 10k × 100cm = 10km
        assertEquals(10.0, StepTrackerUtils.calculateDistanceKm(10_000, 100), 0.001)
    }

    @Test
    fun `kmToMiles converts correctly`() {
        assertEquals(6.21371, StepTrackerUtils.kmToMiles(10.0), 0.0001)
        assertEquals(0.621371, StepTrackerUtils.kmToMiles(1.0), 0.0001)
    }

    // ---------------------------------------------------------------------------
    // 3. Move minutes
    // ---------------------------------------------------------------------------

    @Test
    fun `calculateMoveMinutes returns 0 for 0 steps`() {
        assertEquals(0, StepTrackerUtils.calculateMoveMinutes(0))
    }

    @Test
    fun `calculateMoveMinutes returns correct minute count`() {
        // 100 steps/min × 30 min = 3000 steps → 30 minutes
        assertEquals(30, StepTrackerUtils.calculateMoveMinutes(3000))
    }

    @Test
    fun `calculateMoveMinutes rounds down`() {
        // 150 steps / 100 = 1.5 → 1 (truncates)
        assertEquals(1, StepTrackerUtils.calculateMoveMinutes(150))
    }

    // ---------------------------------------------------------------------------
    // 4. GPS gating
    // ---------------------------------------------------------------------------

    @Test
    fun `isDeviceStatic returns true when below threshold`() {
        assertTrue(StepTrackerUtils.isDeviceStatic(0, 5))
        assertTrue(StepTrackerUtils.isDeviceStatic(4, 5))
    }

    @Test
    fun `isDeviceStatic returns false when at or above threshold`() {
        assertFalse(StepTrackerUtils.isDeviceStatic(5, 5))
        assertFalse(StepTrackerUtils.isDeviceStatic(100, 5))
    }

    @Test
    fun `isDeviceStatic uses default threshold of 5`() {
        assertTrue(StepTrackerUtils.isDeviceStatic(4))   // 4 < 5
        assertFalse(StepTrackerUtils.isDeviceStatic(5)) // 5 >= 5
    }

    // ---------------------------------------------------------------------------
    // 5. Kalman filter
    // ---------------------------------------------------------------------------

    @Test
    fun `KalmanFilter first measurement returns the value itself`() {
        val filter = StepTrackerUtils.KalmanFilter()
        assertEquals(10.0, filter.filter(10.0), 0.0)
    }

    @Test
    fun `KalmanFilter smooths subsequent measurements`() {
        val filter = StepTrackerUtils.KalmanFilter()
        filter.filter(10.0)
        val second = filter.filter(12.0)
        assertTrue("Second value should be between 10 and 12 but was $second", second > 10.0 && second < 12.0)
    }

    @Test
    fun `KalmanFilter spike detection suppresses outliers`() {
        val filter = StepTrackerUtils.KalmanFilter()
        filter.filter(100.0)            // establish baseline x ≈ 100
        val normal = filter.filter(101.0)  // small jump
        val spiked = filter.filter(500.0)  // large spike — R should be inflated

        // The spiked reading should pull less than 33% of the way toward 500
        val fraction = (spiked - normal) / (500.0 - normal)
        assertTrue("Spike contribution should be < 33%%, was ${(fraction * 100).toInt()}%%", fraction < 0.33)
    }

    @Test
    fun `KalmanFilter reset clears state`() {
        val filter = StepTrackerUtils.KalmanFilter()
        filter.filter(100.0)
        filter.filter(500.0) // triggers R inflation
        filter.reset()
        val afterReset = filter.filter(100.0)
        // After reset, first reading is returned as-is
        assertEquals(100.0, afterReset, 0.0)
    }

    @Test
    fun `KalmanFilter handles tiny initial values without overflow`() {
        val filter = StepTrackerUtils.KalmanFilter()
        assertEquals(1e-10, filter.filter(1e-10), 0.0)
        val result = filter.filter(1e-5)  // should not overflow
        assertTrue(result.isFinite())
    }

    @Test
    fun `KalmanFilter default q and r give smooth output`() {
        val filter = StepTrackerUtils.KalmanFilter()
        filter.filter(1.0)
        val second = filter.filter(2.0)
        assertTrue("Second reading should be smoothed toward 2 but not fully there, was $second", second in 1.5..2.0)
    }

    // ---------------------------------------------------------------------------
    // 6. Formatting
    // ---------------------------------------------------------------------------

    @Test
    fun `formatSteps adds thousands separators`() {
        assertEquals("0", StepTrackerUtils.formatSteps(0))
        assertEquals("1,000", StepTrackerUtils.formatSteps(1000))
        assertEquals("12,345", StepTrackerUtils.formatSteps(12345))
    }

    @Test
    fun `formatSteps handles large numbers`() {
        val result = StepTrackerUtils.formatSteps(1_000_000)
        assertTrue(result.contains("1"))
    }

    @Test
    fun `formatDistance formats to 2 decimal places`() {
        assertEquals("7.50 km", StepTrackerUtils.formatDistance(7.5))
        assertEquals("0.00 km", StepTrackerUtils.formatDistance(0.0))
    }

    // ---------------------------------------------------------------------------
    // 7. Aggregation — weekly
    // ---------------------------------------------------------------------------

    @Test
    fun `aggregateWeekly returns 7 entries`() {
        val today = LocalDate.now()
        val dateStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val entries = listOf(StepEntry(date = dateStr, steps = 1000, lastSensorValue = 0))
        val result = StepTrackerUtils.aggregateWeekly(entries)
        assertEquals(7, result.size)
    }

    @Test
    fun `aggregateWeekly sums matching dates and 0 for missing`() {
        val today = LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val dateStr = monday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val entries = listOf(StepEntry(date = dateStr, steps = 500, lastSensorValue = 0))
        val result = StepTrackerUtils.aggregateWeekly(entries)

        // Monday entry should have 500 steps
        val mondayEntry = result.first()
        assertEquals(500, mondayEntry.steps)
        // All others should be 0
        result.drop(1).forEach { assertEquals("Expected 0 steps for ${it.date}", 0, it.steps) }
    }

    // ---------------------------------------------------------------------------
    // 7. Aggregation — monthly
    // ---------------------------------------------------------------------------

    @Test
    fun `aggregateMonthly returns 5 entries`() {
        assertEquals(5, StepTrackerUtils.aggregateMonthly(emptyList()).size)
    }

    @Test
    fun `aggregateMonthly sums all days in each week bucket`() {
        val today = LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY).minusWeeks(4)

        // Full week of 1000 steps/day → week total = 7000
        val entries = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            StepEntry(
                date = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                steps = 1000,
                lastSensorValue = 0
            )
        }

        val result = StepTrackerUtils.aggregateMonthly(entries)
        // Oldest week (index 0) should be 7000
        assertEquals(7000, result.first().steps)
    }

    // ---------------------------------------------------------------------------
    // 7. Aggregation — yearly
    // ---------------------------------------------------------------------------

    @Test
    fun `aggregateYearly returns 12 entries`() {
        assertEquals(12, StepTrackerUtils.aggregateYearly(emptyList()).size)
    }

    @Test
    fun `aggregateYearly sums all months of current year`() {
        val year = LocalDate.now().year
        val monthPrefix = String.format("%04d-%02d", year, 6)
        val entries = listOf(StepEntry(date = "$monthPrefix-15", steps = 5000, lastSensorValue = 0))
        val result = StepTrackerUtils.aggregateYearly(entries)

        // June is index 5 (0-based, Jan=0)
        assertEquals(5000, result[5].steps)
        // All other months should be 0
        result.filterIndexed { index, _ -> index != 5 }
            .forEach { assertEquals("Expected 0 for ${it.date}", 0, it.steps) }
    }
}
