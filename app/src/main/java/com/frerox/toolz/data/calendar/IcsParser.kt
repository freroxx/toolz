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

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * RFC 5545 iCalendar (.ics) parser and writer.
 *
 * Pure Kotlin — zero external dependencies. Handles:
 * - RFC 5545 §3.1 line folding (lines > 75 octets wrapped with CRLF + space)
 * - CRLF and LF line endings
 * - UTC timestamps (suffix Z) and floating local timestamps
 * - DTSTART, DTEND, DURATION, SUMMARY, DESCRIPTION, UID, RRULE
 * - X-APPLE-CALENDAR-COLOR / COLOR for event colour
 *
 * Compatible with: Google Calendar, Apple Calendar, Outlook, Thunderbird,
 * Nextcloud Calendar, Proton Calendar, Fastmail, Radicale, Baikal, DAVx⁵, etc.
 */
object IcsParser {

    private const val TAG = "IcsParser"

    // ── UTC formatters ──────────────────────────────────────────────────────

    private val utcFmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val utcFmtNoZ = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val dateFmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ── RRULE mapping ───────────────────────────────────────────────────────

    private val rruleToRecurrence = mapOf(
        "FREQ=DAILY"   to Recurrence.DAILY,
        "FREQ=WEEKLY"  to Recurrence.WEEKLY,
        "FREQ=MONTHLY" to Recurrence.MONTHLY,
        "FREQ=YEARLY"  to Recurrence.YEARLY,
    )
    private val recurrenceToRrule = mapOf(
        Recurrence.DAILY   to "FREQ=DAILY",
        Recurrence.WEEKLY  to "FREQ=WEEKLY",
        Recurrence.MONTHLY to "FREQ=MONTHLY",
        Recurrence.YEARLY  to "FREQ=YEARLY",
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  Parse
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse an ICS string and return a list of [EventEntry] objects.
     * Unknown/malformed VEVENTs are skipped with a log warning.
     */
    fun parse(ics: String): List<EventEntry> {
        val lines = unfold(ics)
        val events = mutableListOf<EventEntry>()
        var inVEvent = false
        val props = mutableMapOf<String, String>()

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inVEvent = true
                    props.clear()
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    inVEvent = false
                    runCatching { buildEventEntry(props) }.fold(
                        onSuccess = { it?.let { e -> events.add(e) } },
                        onFailure = { Log.w(TAG, "Skipping malformed VEVENT: ${it.message}") }
                    )
                }
                inVEvent -> {
                    // Split on first colon; param values may contain colons
                    val colonIdx = line.indexOf(':')
                    if (colonIdx > 0) {
                        val key = line.substring(0, colonIdx).uppercase(Locale.ROOT)
                        val value = line.substring(colonIdx + 1)
                        // For parameterized properties like DTSTART;TZID=America/New_York
                        val baseKey = key.substringBefore(';')
                        props[baseKey] = value
                        // Keep TZID param if present
                        if (key.contains("TZID=")) {
                            val tzid = key.substringAfter("TZID=").substringBefore(';')
                            props["${baseKey}_TZID"] = tzid
                        }
                    }
                }
            }
        }
        return events
    }

    /** RFC 5545 §3.1: unfold long lines (CRLF/LF followed by a space or tab) */
    private fun unfold(ics: String): List<String> {
        val normalized = ics.replace("\r\n", "\n").replace("\r", "\n")
        // Unfold: newline + (space|tab) → continuation
        val unfolded = normalized.replace(Regex("\n[ \t]"), "")
        return unfolded.split('\n')
    }

    private fun buildEventEntry(props: Map<String, String>): EventEntry? {
        val summary = props["SUMMARY"]?.unescapeIcs()?.trim() ?: return null
        if (summary.isBlank()) return null

        val dtstart = parseIcsDate(props["DTSTART"], props["DTSTART_TZID"]) ?: return null
        val dtend = props["DTEND"]?.let { parseIcsDate(it, props["DTEND_TZID"]) }
        val duration = props["DURATION"]?.let { parseDuration(it) }

        val endTs: Long? = dtend ?: duration?.let { dtstart + it }
        val durationMinutes = if (endTs != null) ((endTs - dtstart) / 60_000).toInt().coerceIn(1, 1440) else 60

        val description = props["DESCRIPTION"]?.unescapeIcs()?.trim()
        val uid = props["UID"] ?: UUID.randomUUID().toString()

        val color = (props["COLOR"] ?: props["X-APPLE-CALENDAR-COLOR"])
            ?.let { normalizeColor(it) } ?: "#9E9E9E"

        val rrule = props["RRULE"]?.let { rule ->
            rruleToRecurrence.entries.find { rule.uppercase().startsWith(it.key) }?.value
        } ?: Recurrence.NONE

        // Map event type heuristically from CATEGORIES or preserve custom category
        val rawCat = props["CATEGORIES"]?.trim() ?: ""
        val categories = rawCat.uppercase()
        val eventType = when {
            categories.contains("EXAM") || categories.contains("TEST") -> "EXAM"
            categories.contains("EVALUATION") -> "EVALUATION"
            categories.contains("BIRTHDAY") -> "BIRTHDAY"
            categories.contains("MEETING") || categories.contains("CONFERENCE") -> "MEETING"
            categories.contains("HOLIDAY") -> "HOLIDAY"
            categories.contains("DEADLINE") -> "DEADLINE"
            rawCat.isNotBlank() -> rawCat.split(',').first().trim()
            else -> "GENERAL"
        }

        return EventEntry(
            id = 0, // auto-generate on insert
            title = summary.take(200),
            description = description,
            timestamp = dtstart,
            eventType = eventType,
            subjectColor = color,
            isRecurring = rrule != Recurrence.NONE,
            recurringRule = rrule,
            endTimestamp = endTs,
            durationMinutes = durationMinutes,
        )
    }

    private fun parseIcsDate(value: String?, tzidHint: String?): Long? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        return runCatching {
            when {
                v.endsWith("Z") -> utcFmt.parse(v)?.time
                v.length == 15 -> { // floating local: 20240115T090000
                    val tz = if (!tzidHint.isNullOrBlank()) {
                        TimeZone.getTimeZone(tzidHint).takeIf { it.id != "GMT" } ?: TimeZone.getDefault()
                    } else TimeZone.getDefault()
                    SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = tz }.parse(v)?.time
                }
                v.length == 8 -> dateFmt.parse(v)?.time // date-only: 20240115
                else -> null
            }
        }.getOrNull()
    }

    /** Parse DURATION like PT1H30M, P1D, PT45M */
    private fun parseDuration(value: String): Long? {
        return runCatching {
            val v = value.trim().uppercase()
            var millis = 0L
            val numBuf = StringBuilder()
            for (ch in v) {
                when {
                    ch.isDigit() -> numBuf.append(ch)
                    ch == 'W' -> { millis += numBuf.toString().toLong() * 7 * 24 * 3600_000L; numBuf.clear() }
                    ch == 'D' -> { millis += numBuf.toString().toLong() * 24 * 3600_000L; numBuf.clear() }
                    ch == 'H' -> { millis += numBuf.toString().toLong() * 3600_000L; numBuf.clear() }
                    ch == 'M' -> { millis += numBuf.toString().toLong() * 60_000L; numBuf.clear() }
                    ch == 'S' -> { millis += numBuf.toString().toLong() * 1_000L; numBuf.clear() }
                    else -> numBuf.clear()
                }
            }
            millis.takeIf { it > 0 }
        }.getOrNull()
    }

    private fun normalizeColor(raw: String): String {
        val s = raw.trim()
        val hex = if (s.startsWith("#")) s else "#$s"
        return if (hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) hex else "#9E9E9E"
    }

    private fun String.unescapeIcs(): String =
        replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",")
            .replace("\\;", ";").replace("\\\\", "\\")

    // ─────────────────────────────────────────────────────────────────────────
    //  Serialize (Export)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Serialize a list of [EventEntry] to an RFC 5545 compliant ICS string.
     * The output is valid for import into any compliant calendar app.
     */
    fun serialize(events: List<EventEntry>, calendarName: String = "Toolz Calendar"): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//Toolz//Toolz Calendar//EN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        sb.appendFolded("X-WR-CALNAME:${calendarName.escapeIcs()}")
        sb.append("\r\n")

        for (event in events) {
            sb.append("BEGIN:VEVENT\r\n")
            sb.appendFolded("UID:toolz-${event.id}-${event.timestamp}@toolz")
            sb.append("\r\n")
            sb.appendFolded("DTSTART:${utcFmt.format(Date(event.timestamp))}")
            sb.append("\r\n")
            val endMs = event.endTimestamp ?: (event.timestamp + event.durationMinutes * 60_000L)
            sb.appendFolded("DTEND:${utcFmt.format(Date(endMs))}")
            sb.append("\r\n")
            sb.appendFolded("SUMMARY:${event.title.escapeIcs()}")
            sb.append("\r\n")
            event.description?.let {
                sb.appendFolded("DESCRIPTION:${it.escapeIcs()}")
                sb.append("\r\n")
            }
            if (event.recurringRule != Recurrence.NONE) {
                recurrenceToRrule[event.recurringRule]?.let { rule ->
                    sb.appendFolded("RRULE:$rule")
                    sb.append("\r\n")
                }
            }
            sb.appendFolded("CATEGORIES:${event.eventType}")
            sb.append("\r\n")
            sb.appendFolded("COLOR:${event.subjectColor}")
            sb.append("\r\n")
            sb.appendFolded("X-APPLE-CALENDAR-COLOR:${event.subjectColor}")
            sb.append("\r\n")
            if (event.isCompleted) {
                sb.append("STATUS:COMPLETED\r\n")
            }
            sb.append("END:VEVENT\r\n")
        }

        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    /** RFC 5545 §3.1: fold lines longer than 75 octets (CRLF + space continuation) */
    private fun StringBuilder.appendFolded(line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) {
            append(line)
            return
        }
        // fold at 75-byte boundaries (be careful not to split multi-byte chars)
        var start = 0
        var first = true
        while (start < line.length) {
            val prefix = if (first) "" else " "
            first = false
            val maxBytes = if (prefix.isEmpty()) 75 else 74 // account for leading space
            var end = start
            var byteCount = prefix.length
            while (end < line.length) {
                val codePoint = line.codePointAt(end)
                val charLen = Character.charCount(codePoint)
                val charBytes = line.substring(end, end + charLen).toByteArray(Charsets.UTF_8).size
                if (byteCount + charBytes > maxBytes) break
                byteCount += charBytes
                end += charLen
            }
            if (end == start) { // single char larger than limit, just append it
                end = start + Character.charCount(line.codePointAt(start))
            }
            append(prefix)
            append(line.substring(start, end))
            if (end < line.length) append("\r\n")
            start = end
        }
    }

    private fun String.escapeIcs(): String =
        replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")
}
