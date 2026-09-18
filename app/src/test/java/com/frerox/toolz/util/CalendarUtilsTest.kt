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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * CAL-P0-01: DatePicker UTC-midnight round-trips must preserve the picked day
 * in every timezone (GMT+2 / GMT-4 / GMT+14), including DST days.
 *
 * CAL-P0-02: month arithmetic must never skip February.
 */
class CalendarUtilsTest {

    private fun withTz(id: String, block: () -> Unit) {
        val prev = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(prev)
        }
    }

    private fun ymd(millis: Long, tz: TimeZone = TimeZone.getDefault()): Triple<Int, Int, Int> {
        val c = Calendar.getInstance(tz).apply { timeInMillis = millis }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }

    // ── CAL-P0-01 ──────────────────────────────────────────────────

    @Test
    fun datePickerRoundTrip_gmtPlus2_staysSameDay() {
        withTz("Europe/Berlin") {
            // Jun-10 10:00 local -> DatePicker UTC -> combine with 10:00 -> Jun-10.
            val local = Calendar.getInstance().apply {
                set(2026, Calendar.JUNE, 10, 10, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val utcMidnight = CalendarUtils.localTimestampToDatePickerUtc(local)
            val combined = CalendarUtils.combineDatePickerUtcWithTime(utcMidnight, 10, 0)
            assertEquals(Triple(2026, Calendar.JUNE, 10), ymd(combined))
        }
    }

    @Test
    fun datePickerRoundTrip_gmtMinus4_staysSameDay() {
        withTz("America/New_York") {
            val local = Calendar.getInstance().apply {
                set(2026, Calendar.JUNE, 10, 10, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val utcMidnight = CalendarUtils.localTimestampToDatePickerUtc(local)
            val combined = CalendarUtils.combineDatePickerUtcWithTime(utcMidnight, 10, 0)
            assertEquals(Triple(2026, Calendar.JUNE, 10), ymd(combined))
        }
    }

    @Test
    fun datePickerRoundTrip_gmtPlus14_staysSameDay() {
        withTz("Pacific/Kiritimati") {
            val local = Calendar.getInstance().apply {
                set(2026, Calendar.JUNE, 10, 10, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val utcMidnight = CalendarUtils.localTimestampToDatePickerUtc(local)
            val combined = CalendarUtils.combineDatePickerUtcWithTime(utcMidnight, 10, 0)
            assertEquals(Triple(2026, Calendar.JUNE, 10), ymd(combined))
        }
    }

    @Test
    fun datePickerRoundTrip_dstDay_staysSameDay() {
        // US DST spring-forward day 2026-03-08 in New York.
        withTz("America/New_York") {
            val local = Calendar.getInstance().apply {
                set(2026, Calendar.MARCH, 8, 10, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val utcMidnight = CalendarUtils.localTimestampToDatePickerUtc(local)
            val combined = CalendarUtils.combineDatePickerUtcWithTime(utcMidnight, 10, 0)
            assertEquals(Triple(2026, Calendar.MARCH, 8), ymd(combined))
        }
    }

    @Test
    fun datePickerUtcExtractsYmdInUtc() {
        // 2026-06-10T00:00Z must read back as Jun-10 regardless of default tz.
        withTz("Pacific/Kiritimati") {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2026, Calendar.JUNE, 10, 0, 0, 0)
            }.timeInMillis
            assertEquals(
                Triple(2026, Calendar.JUNE, 10),
                CalendarUtils.datePickerUtcToLocalYMD(utc)
            )
        }
    }

    // ── CAL-P0-02 ──────────────────────────────────────────────────

    private fun jan31_2026(tz: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(tz).apply {
            set(2026, Calendar.JANUARY, 31, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun nextMonth_jan31_landsInFebruary() {
        val tz = TimeZone.getTimeZone("UTC")
        val next = CalendarUtils.addMonthsClamped(jan31_2026(tz), 1, tz)
        val c = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH)) // 2026 not a leap year
    }

    @Test
    fun nextMonth_jan31_leapYear_landsFeb29() {
        val tz = TimeZone.getTimeZone("UTC")
        val jan31Leap = Calendar.getInstance(tz).apply {
            set(2024, Calendar.JANUARY, 31, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val next = CalendarUtils.addMonthsClamped(jan31Leap, 1, tz)
        val c = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
        assertEquals(29, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun previousMonth_mar31_landsInFebruary() {
        val tz = TimeZone.getTimeZone("UTC")
        val mar31 = Calendar.getInstance(tz).apply {
            set(2026, Calendar.MARCH, 31, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val prev = CalendarUtils.addMonthsClamped(mar31, -1, tz)
        val c = Calendar.getInstance(tz).apply { timeInMillis = prev }
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
    }

    @Test
    fun nextMonth_dec_rollsYear() {
        val tz = TimeZone.getTimeZone("UTC")
        val dec15 = Calendar.getInstance(tz).apply {
            set(2026, Calendar.DECEMBER, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val next = CalendarUtils.addMonthsClamped(dec15, 1, tz)
        val c = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals(2027, c.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, c.get(Calendar.MONTH))
        assertEquals(15, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun setDate_jan31_toFebruary_clamps() {
        val tz = TimeZone.getTimeZone("UTC")
        val res = CalendarUtils.setYearMonthClamped(jan31_2026(tz), 2026, Calendar.FEBRUARY, tz)
        val c = Calendar.getInstance(tz).apply { timeInMillis = res }
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH))
    }

    // ── CAL-P1-05: Feb29 yearly ────────────────────────────────────

    @Test
    fun yearlyFeb29_appearsFeb28_nonLeap() {
        val tz = TimeZone.getTimeZone("UTC")
        val feb29_2024 = Calendar.getInstance(tz).apply {
            set(2024, Calendar.FEBRUARY, 29, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val rangeStart = Calendar.getInstance(tz).apply {
            set(2025, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val rangeEnd = Calendar.getInstance(tz).apply {
            set(2025, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val occ = CalendarUtils.generateOccurrences(
            feb29_2024, "YEARLY", rangeStart, rangeEnd,
            now = rangeStart, tz = tz
        )
        assertEquals(1, occ.size)
        val c = Calendar.getInstance(tz).apply { timeInMillis = occ[0] }
        assertEquals(2025, c.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH))
        assertEquals(28, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun alarmRequestCode_stableAndDistinct() {
        val a = CalendarUtils.alarmRequestCode(42, "24H")
        val b = CalendarUtils.alarmRequestCode(42, "12H")
        val c = CalendarUtils.alarmRequestCode(42, "1H")
        assertTrue(a != b && b != c && a != c)
        assertEquals(a, CalendarUtils.alarmRequestCode(42, "24H"))
        // Overflow guard: huge ids must not throw.
        CalendarUtils.alarmRequestCode(Int.MAX_VALUE, "1H")
        CalendarUtils.snoozeRequestCode(Int.MAX_VALUE, 15)
    }

    @Test
    fun isSameDay_sharedHelper() {
        val tz = TimeZone.getTimeZone("UTC")
        val a = Calendar.getInstance(tz).apply {
            set(2026, Calendar.JUNE, 10, 1, 0, 0)
        }.timeInMillis
        val b = Calendar.getInstance(tz).apply {
            set(2026, Calendar.JUNE, 10, 23, 59, 0)
        }.timeInMillis
        val c = Calendar.getInstance(tz).apply {
            set(2026, Calendar.JUNE, 11, 0, 0, 0)
        }.timeInMillis
        assertTrue(CalendarUtils.isSameDay(a, b, tz))
        assertTrue(!CalendarUtils.isSameDay(a, c, tz))
    }
}
