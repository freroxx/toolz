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

package com.frerox.toolz.data.calendar

import android.content.ContentResolver
import android.content.Context
import android.graphics.Color
import android.provider.CalendarContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads events from Android's system [CalendarContract] provider.
 *
 * Works with any calendar app that exposes data through the platform
 * CalendarContract API: Google Calendar, Samsung Calendar, AOSP Calendar,
 * Nextcloud DAVx⁵-synced calendars, etc.
 *
 * Requires [android.Manifest.permission.READ_CALENDAR].
 */
@Singleton
class PhoneCalendarImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PhoneCalendarImporter"
    }

    data class PhoneCalendar(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val color: Int,
        val eventCount: Int = 0,
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  List available phone calendars
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Query the CalendarContract for all calendars visible to this app.
     * Returns an empty list if permission is not granted.
     */
    suspend fun getPhoneCalendars(): List<PhoneCalendar> = withContext(Dispatchers.IO) {
        val cr: ContentResolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val calendars = mutableListOf<PhoneCalendar>()
        runCatching {
            cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection, null, null, null
            )?.use { cursor ->
                val idIdx   = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accIdx  = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val colIdx  = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                while (cursor.moveToNext()) {
                    val id   = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: "Calendar $id"
                    val acc  = cursor.getString(accIdx) ?: ""
                    val col  = runCatching { cursor.getInt(colIdx) }.getOrDefault(Color.GRAY)
                    calendars.add(PhoneCalendar(id, name, acc, col))
                }
            }
        }.onFailure { Log.e(TAG, "getPhoneCalendars failed: ${it.message}") }
        calendars
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Import events from a specific calendar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Import all events from [calendarId] that start on or after [since].
     * Returns a list of [EventEntry] ready to insert via [EventRepository].
     */
    suspend fun importFromCalendar(
        calendarId: Long,
        since: Long = 0L,
    ): List<EventEntry> = withContext(Dispatchers.IO) {
        val cr: ContentResolver = context.contentResolver
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.CALENDAR_COLOR,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.STATUS,
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ?"
        val selArgs = arrayOf(calendarId.toString(), since.toString())

        val events = mutableListOf<EventEntry>()
        runCatching {
            cr.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                val titleIdx    = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val descIdx     = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val startIdx    = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx      = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val durIdx      = cursor.getColumnIndex(CalendarContract.Events.DURATION)
                val colorIdx    = cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val calColorIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_COLOR)
                val rruleIdx    = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                val statusIdx   = cursor.getColumnIndex(CalendarContract.Events.STATUS)

                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIdx)?.trim() ?: continue
                    if (title.isBlank()) continue

                    val dtstart = cursor.getLong(startIdx)
                    val dtend: Long? = if (endIdx >= 0 && !cursor.isNull(endIdx)) cursor.getLong(endIdx) else null
                    val durStr: String? = if (durIdx >= 0 && !cursor.isNull(durIdx)) cursor.getString(durIdx) else null

                    val endMs = dtend ?: durStr?.let { IcsParser.run { dtstart + (parseDurationPublic(it) ?: 3600_000L) } }
                    val durationMin = if (endMs != null) ((endMs - dtstart) / 60_000).toInt().coerceIn(1, 1440) else 60

                    val rawColor = if (colorIdx >= 0 && !cursor.isNull(colorIdx)) cursor.getInt(colorIdx)
                    else if (calColorIdx >= 0 && !cursor.isNull(calColorIdx)) cursor.getInt(calColorIdx)
                    else Color.GRAY
                    val hexColor = "#%06X".format(rawColor and 0xFFFFFF)

                    val rrule = if (rruleIdx >= 0 && !cursor.isNull(rruleIdx)) cursor.getString(rruleIdx) else null
                    val recurrence = mapRrule(rrule)

                    val isCompleted = if (statusIdx >= 0) cursor.getInt(statusIdx) == CalendarContract.Events.STATUS_CANCELED else false

                    val description = if (descIdx >= 0 && !cursor.isNull(descIdx)) cursor.getString(descIdx)?.trim() else null

                    events.add(
                        EventEntry(
                            id = 0,
                            title = title.take(200),
                            description = description,
                            timestamp = dtstart,
                            eventType = "GENERAL",
                            subjectColor = hexColor,
                            isRecurring = recurrence != Recurrence.NONE,
                            recurringRule = recurrence,
                            endTimestamp = endMs,
                            durationMinutes = durationMin,
                            isCompleted = isCompleted,
                        )
                    )
                }
            }
        }.onFailure { Log.e(TAG, "importFromCalendar($calendarId) failed: ${it.message}") }
        events
    }

    /** Import from multiple calendars, deduplicating by title+timestamp. */
    suspend fun importFromCalendars(
        calendarIds: Set<Long>,
        since: Long = 0L,
    ): List<EventEntry> {
        val seen = HashSet<String>()
        val result = mutableListOf<EventEntry>()
        for (id in calendarIds) {
            for (event in importFromCalendar(id, since)) {
                val key = "${event.title}@${event.timestamp}"
                if (seen.add(key)) result.add(event)
            }
        }
        return result.sortedBy { it.timestamp }
    }

    private fun mapRrule(rrule: String?): Recurrence {
        if (rrule.isNullOrBlank()) return Recurrence.NONE
        val upper = rrule.uppercase()
        return when {
            upper.contains("FREQ=DAILY")   -> Recurrence.DAILY
            upper.contains("FREQ=WEEKLY")  -> Recurrence.WEEKLY
            upper.contains("FREQ=MONTHLY") -> Recurrence.MONTHLY
            upper.contains("FREQ=YEARLY")  -> Recurrence.YEARLY
            else -> Recurrence.NONE
        }
    }

    // Expose IcsParser's duration parser for use here without reflection
    private fun parseDurationPublic(value: String): Long? {
        val v = value.trim().uppercase()
        var millis = 0L
        val numBuf = StringBuilder()
        for (ch in v) {
            when {
                ch.isDigit() -> numBuf.append(ch)
                ch == 'W' -> { millis += numBuf.toString().toLongOrNull()?.let { it * 7 * 24 * 3600_000L } ?: 0; numBuf.clear() }
                ch == 'D' -> { millis += numBuf.toString().toLongOrNull()?.let { it * 24 * 3600_000L } ?: 0; numBuf.clear() }
                ch == 'H' -> { millis += numBuf.toString().toLongOrNull()?.let { it * 3600_000L } ?: 0; numBuf.clear() }
                ch == 'M' -> { millis += numBuf.toString().toLongOrNull()?.let { it * 60_000L } ?: 0; numBuf.clear() }
                ch == 'S' -> { millis += numBuf.toString().toLongOrNull()?.let { it * 1_000L } ?: 0; numBuf.clear() }
                else -> numBuf.clear()
            }
        }
        return millis.takeIf { it > 0 }
    }
}
