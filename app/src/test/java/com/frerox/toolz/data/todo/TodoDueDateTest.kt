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

package com.frerox.toolz.data.todo

import com.frerox.toolz.util.CalendarUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * T-P0-02: DatePicker UTC-midnight must normalize to LOCAL 09:00 on the SAME
 * picked day in every timezone (GMT-4 pick Sep19 stays Sep19, never Sep18
 * 20:00). Reuses [CalendarUtils] — never manual offset math (DST break).
 */
class TodoDueDateTest {

    private fun withTz(id: String, block: () -> Unit) {
        val prev = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            TimeZone.setDefault(prev)
        }
    }

    private fun ymd(millis: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    /** Simulates the TodoScreen picker confirm path for a UTC-midnight pick. */
    private fun pickLocalDueDate(year: Int, month: Int, day: Int): Long {
        val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return CalendarUtils.combineDatePickerUtcWithTime(utcMidnight, 9, 0)
    }

    @Test
    fun gmtMinus4_pick_staysSameDay_at0900() {
        withTz("America/New_York") {
            val due = pickLocalDueDate(2026, Calendar.SEPTEMBER, 19)
            assertEquals(Triple(2026, Calendar.SEPTEMBER, 19), ymd(due))
            assertEquals(9, hourOf(due))
            assertTrue(CalendarUtils.isSameDay(due, due))
        }
    }

    @Test
    fun gmtPlus530_pick_staysSameDay_at0900() {
        withTz("Asia/Kolkata") {
            val due = pickLocalDueDate(2026, Calendar.SEPTEMBER, 19)
            assertEquals(Triple(2026, Calendar.SEPTEMBER, 19), ymd(due))
            assertEquals(9, hourOf(due))
        }
    }

    @Test
    fun gmtPlus14_pick_staysSameDay_at0900() {
        withTz("Pacific/Kiritimati") {
            val due = pickLocalDueDate(2026, Calendar.SEPTEMBER, 19)
            assertEquals(Triple(2026, Calendar.SEPTEMBER, 19), ymd(due))
            assertEquals(9, hourOf(due))
        }
    }

    @Test
    fun dstDay_pick_staysSameDay() {
        // US spring-forward 2026-03-08 in New York — manual offset math breaks here.
        withTz("America/New_York") {
            val due = pickLocalDueDate(2026, Calendar.MARCH, 8)
            assertEquals(Triple(2026, Calendar.MARCH, 8), ymd(due))
            assertEquals(9, hourOf(due))
        }
    }

    @Test
    fun roundTrip_localDue_survivesPicker() {
        withTz("America/New_York") {
            val due = pickLocalDueDate(2026, Calendar.SEPTEMBER, 19)
            // initialSelectedDateMillis path: local -> UTC -> combine stays put.
            val utc = CalendarUtils.localTimestampToDatePickerUtc(due)
            val back = CalendarUtils.combineDatePickerUtcWithTime(utc, 9, 0)
            assertEquals(ymd(due), ymd(back))
            assertEquals(9, hourOf(back))
        }
    }
}
