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

import java.util.Calendar
import java.util.Objects
import java.util.TimeZone

/**
 * Shared calendar date helpers (CAL-P0-01, CAL-P1-03).
 *
 * Owned by the Calendar agent; Todo reuses [datePickerUtcToLocalYMD] — do not fork.
 *
 * DatePicker contract: [androidx.compose.material3.DatePickerState.selectedDateMillis]
 * is UTC-midnight. Never treat it as local millis and never add/subtract
 * `timezoneOffset` manually (breaks DST) — use Calendar/Time APIs here.
 */
object CalendarUtils {

    /**
     * Extract Y/M/D from a DatePicker UTC-midnight millis using a UTC calendar.
     * Returns Triple(year, month (0-based, Calendar convention), dayOfMonth).
     */
    fun datePickerUtcToLocalYMD(utcMillis: Long): Triple<Int, Int, Int> {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return Triple(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Reverse of [datePickerUtcToLocalYMD]: convert a local timestamp's Y/M/D
     * into UTC-midnight millis suitable for `initialSelectedDateMillis`.
     */
    fun localTimestampToDatePickerUtc(localMillis: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = localMillis }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0, 0, 0
            )
            set(Calendar.MILLISECOND, 0)
        }
        return utc.timeInMillis
    }

    /**
     * Combine a DatePicker UTC-midnight [datePartUtc] with user [hour]/[minute]
     * into a local timestamp, preserving the picked Y/M/D exactly.
     */
    fun combineDatePickerUtcWithTime(datePartUtc: Long, hour: Int, minute: Int): Long {
        val (y, m, d) = datePickerUtcToLocalYMD(datePartUtc)
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Shared `isSameDay` — extracted so SyncImage + UI use one implementation. */
    fun isSameDay(t1: Long, t2: Long, tz: TimeZone = TimeZone.getDefault()): Boolean {
        val c1 = Calendar.getInstance(tz).apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance(tz).apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    /** Normalized title for dedup: trim + lowercase. */
    fun normalizedTitle(title: String): String = title.trim().lowercase()

    // ── Month navigation (CAL-P0-02) ────────────────────────────────
    // Lenient Calendar arithmetic skips February (Jan-31 +1mo -> Mar-03).
    // DAY_OF_MONTH=1 FIRST, then add/set, then clamp to the target month length.

    fun addMonthsClamped(baseMillis: Long, delta: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = baseMillis }
        val origDay = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, delta)
        cal.set(Calendar.DAY_OF_MONTH, minOf(origDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return cal.timeInMillis
    }

    fun setYearMonthClamped(
        baseMillis: Long, year: Int, month: Int, tz: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = baseMillis }
        val origDay = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, minOf(origDay, cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return cal.timeInMillis
    }

    // ── Alarm requestCodes (CAL-P0-08) ──────────────────────────────
    // Central scheme: hash(eventId, typeCode). 0=24H, 1=12H, 2=1H, 3.. snooze.
    const val TYPE_24H = 0
    const val TYPE_12H = 1
    const val TYPE_1H = 2

    fun typeCode(type: String): Int = when (type) {
        "24H" -> TYPE_24H
        "12H" -> TYPE_12H
        "1H" -> TYPE_1H
        else -> 9
    }

    fun alarmRequestCode(eventId: Int, type: Int): Int {
        // Guard against Int overflow from naive eventId*10 math.
        if (eventId > Int.MAX_VALUE / 10 - 10) {
            return Objects.hash(eventId, type)
        }
        return try {
            Math.addExact(Math.multiplyExact(eventId, 10), type)
        } catch (_: ArithmeticException) {
            Objects.hash(eventId, type)
        }
    }

    fun alarmRequestCode(eventId: Int, type: String): Int =
        alarmRequestCode(eventId, typeCode(type))

    fun snoozeRequestCode(eventId: Int, minutes: Int): Int {
        // Distinct namespace from the 0..9 lead-type suffixes.
        val base = eventId.toLong() * 1000L + 500L + (minutes % 500)
        return if (base in Int.MIN_VALUE..Int.MAX_VALUE) base.toInt()
        else Objects.hash(eventId, minutes, "snooze")
    }

    fun notificationId(eventId: Int, type: String): Int =
        alarmRequestCode(eventId, type)

    // ── Recurrence expansion (CAL-P1-05) ────────────────────────────
    /**
     * Expand [eventTimestamp] occurrences of [recurringRule] inside [rangeStart..rangeEnd].
     * Feb29 clamps to Feb28 on non-leap years. Clamped to ≤5y from [now].
     */
    fun generateOccurrences(
        eventTimestamp: Long,
        recurringRule: String?,
        rangeStart: Long,
        rangeEnd: Long,
        now: Long = System.currentTimeMillis(),
        tz: TimeZone = TimeZone.getDefault()
    ): List<Long> {
        val rule = recurringRule?.uppercase() ?: "NONE"
        if (rule == "NONE" || rule.isBlank()) {
            return if (eventTimestamp in rangeStart..rangeEnd) listOf(eventTimestamp) else emptyList()
        }
        val maxEnd = Calendar.getInstance(tz).apply {
            timeInMillis = now
            add(Calendar.YEAR, 5)
        }.timeInMillis
        val end = minOf(rangeEnd, maxEnd)
        if (eventTimestamp > end) return emptyList()

        val base = Calendar.getInstance(tz).apply { timeInMillis = eventTimestamp }
        val baseDay = base.get(Calendar.DAY_OF_MONTH)
        val baseMonth = base.get(Calendar.MONTH)
        val out = mutableListOf<Long>()
        // YEARLY: iterate calendar years and clamp day-of-month (Feb29 -> Feb28
        // non-leap, Feb29 again on leap years). Never add(Calendar.YEAR,1) on a
        // Feb29 cursor — lenient roll turns it into Mar-01 permanently.
        if (rule == "YEARLY") {
            var year = Calendar.getInstance(tz).apply { timeInMillis = rangeStart }.get(Calendar.YEAR)
            val eventYear = base.get(Calendar.YEAR)
            if (year < eventYear) year = eventYear
            val endYear = Calendar.getInstance(tz).apply { timeInMillis = end }.get(Calendar.YEAR)
            while (year <= endYear) {
                val c = (base.clone() as Calendar).apply {
                    // DAY=1 first so the YEAR change can't lenient-roll Feb29->Mar01.
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, baseMonth)
                    set(Calendar.DAY_OF_MONTH, minOf(baseDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                if (c.timeInMillis in rangeStart..end) out.add(c.timeInMillis)
                year++
                if (out.size > 60) break
            }
            return out
        }

        var cursor = (base.clone() as Calendar)
        // Fast-forward to range.
        var guard = 0
        while (cursor.timeInMillis < rangeStart && guard++ < 5000) {
            when (rule) {
                "DAILY" -> cursor.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> cursor.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> {
                    cursor.set(Calendar.DAY_OF_MONTH, 1)
                    cursor.add(Calendar.MONTH, 1)
                    cursor.set(Calendar.DAY_OF_MONTH, minOf(baseDay, cursor.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                "YEARLY" -> cursor.add(Calendar.YEAR, 1)
                else -> break
            }
        }
        guard = 0
        while (cursor.timeInMillis <= end && guard++ < 2000) {
            out.add(cursor.timeInMillis)
            when (rule) {
                "DAILY" -> cursor.add(Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> cursor.add(Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> {
                    cursor.set(Calendar.DAY_OF_MONTH, 1)
                    cursor.add(Calendar.MONTH, 1)
                    cursor.set(Calendar.DAY_OF_MONTH, minOf(baseDay, cursor.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                "YEARLY" -> cursor.add(Calendar.YEAR, 1)
                else -> break
            }
        }
        return out
    }
}
