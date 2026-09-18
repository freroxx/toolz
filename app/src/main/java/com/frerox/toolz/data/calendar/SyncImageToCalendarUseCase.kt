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
import com.frerox.toolz.util.CalendarUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

private const val TAG = "SyncImageToCalendar"

data class AiCalendarEvent(
    val title: String,
    val start_time_iso: String,
    val duration_minutes: Int,
    val description: String
)

data class AiEventResult(
    val title: String,
    val timestamp: Long,
    val eventType: String,
    val subjectColor: String,
    val description: String? = null
)

sealed class SyncResult {
    data class New(val event: EventEntry) : SyncResult()
    data class Reschedule(val existing: EventEntry, val updated: EventEntry) : SyncResult()
}

class SyncImageToCalendarUseCase @Inject constructor(
    private val repository: EventRepository
) {
    /**
     * CAL-P1-03: strict pipeline.
     * - `isLenient=false`, tz=default; unparseable -> SKIP + warning (never `now`).
     * - `duration_minutes` carried into `endTimestamp`/`durationMinutes`.
     * - Reschedule narrowed to same-day ±2h + normalized EXACT title; preserves
     *   reminders/isCompleted/isRecurring flags; never resurrects completed.
     * - Range query (min-7d..max+7d), never full-table per batch; intra-batch dedup.
     */
    suspend fun processAiCalendarEventsWithWarnings(
        aiEvents: List<AiCalendarEvent>
    ): Pair<List<SyncResult>, List<String>> {
        val warnings = mutableListOf<String>()
        if (aiEvents.isEmpty()) return emptyList<SyncResult>() to warnings

        val tz = TimeZone.getDefault()
        val parsed = mutableListOf<Triple<AiCalendarEvent, Long, Long?>>()
        aiEvents.forEach { aiEvent ->
            val ts = parseIsoStrict(aiEvent.start_time_iso, tz)
            if (ts == null) {
                warnings.add("“${aiEvent.title}”: date couldn't be read — skipped.")
                Log.w(TAG, "Skipping unparseable start_time_iso='${aiEvent.start_time_iso}'")
                return@forEach
            }
            val duration = aiEvent.duration_minutes.coerceIn(1, 1439)
            val end = ts + duration * 60_000L
            parsed.add(Triple(aiEvent, ts, end))
        }
        if (parsed.isEmpty()) return emptyList<SyncResult>() to warnings

        // Intra-batch dedup by (normTitle, ts/5min buckets).
        val seen = mutableSetOf<Pair<String, Long>>()
        val deduped = parsed.filter { (ai, ts, _) ->
            val key = CalendarUtils.normalizedTitle(ai.title) to (ts / 300_000L)
            if (!seen.add(key)) {
                warnings.add("“${ai.title}”: duplicate in this batch — kept once.")
                false
            } else true
        }

        val minTs = deduped.minOf { it.second } - 7L * 24 * 60 * 60 * 1000
        val maxTs = deduped.maxOf { it.second } + 7L * 24 * 60 * 60 * 1000
        val existingEvents = try {
            repository.getEventsForRangeSync(minTs, maxTs)
        } catch (e: Exception) {
            Log.e(TAG, "Range query failed, falling back to filtered full load", e)
            try {
                repository.getAllEventsSync().filter { it.timestamp in minTs..maxTs }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback load also failed", e2)
                warnings.add("Couldn't read existing events — everything will be added as new.")
                emptyList()
            }
        }

        val results = mutableListOf<SyncResult>()
        deduped.forEach { (aiEvent, timestamp, end) ->
            val normTitle = CalendarUtils.normalizedTitle(aiEvent.title)
            // NARROW reschedule: normalized EXACT title + same day + within ±2h.
            // Never auto-merge across different eventTypes on the same day.
            val candidate = existingEvents.find {
                CalendarUtils.normalizedTitle(it.title) == normTitle &&
                    CalendarUtils.isSameDay(it.timestamp, timestamp) &&
                    kotlin.math.abs(it.timestamp - timestamp) <= 2L * 60 * 60 * 1000
            }

            if (candidate != null) {
                if (kotlin.math.abs(candidate.timestamp - timestamp) <= 60_000L) {
                    // Exact duplicate (same title, same day, same minute) -> skip silently.
                    return@forEach
                }
                if (candidate.isCompleted) {
                    // CAL-P1-03: never resurrect completed without confirm — add as new.
                    results.add(
                        SyncResult.New(
                            EventEntry(
                                title = aiEvent.title.trim(),
                                timestamp = timestamp,
                                endTimestamp = end,
                                durationMinutes = aiEvent.duration_minutes.coerceIn(1, 1439),
                                description = aiEvent.description,
                                eventType = "GENERAL",
                                subjectColor = "#9E9E9E",
                                remindersEnabled = true
                            )
                        )
                    )
                    warnings.add("“${aiEvent.title}”: a completed event exists — added as new instead of reopening it.")
                } else {
                    // Preserve flags/duration the AI schema doesn't carry.
                    val updated = candidate.copy(
                        timestamp = timestamp,
                        endTimestamp = end,
                        durationMinutes = aiEvent.duration_minutes.coerceIn(1, 1439),
                        description = aiEvent.description.ifBlank { candidate.description },
                        title = aiEvent.title.trim()
                    )
                    results.add(SyncResult.Reschedule(candidate, updated))
                }
            } else {
                results.add(
                    SyncResult.New(
                        EventEntry(
                            title = aiEvent.title.trim(),
                            timestamp = timestamp,
                            endTimestamp = end,
                            durationMinutes = aiEvent.duration_minutes.coerceIn(1, 1439),
                            description = aiEvent.description,
                            eventType = "GENERAL",
                            subjectColor = "#9E9E9E",
                            remindersEnabled = true
                        )
                    )
                )
            }
        }
        return results to warnings
    }

    suspend fun processAiCalendarEvents(aiEvents: List<AiCalendarEvent>): List<SyncResult> {
        val (results, warnings) = processAiCalendarEventsWithWarnings(aiEvents)
        warnings.forEach { Log.w(TAG, it) }
        return results
    }

    suspend fun processAiEvents(aiEvents: List<AiEventResult>): List<SyncResult> {
        if (aiEvents.isEmpty()) return emptyList()
        val minTs = aiEvents.minOf { it.timestamp } - 7L * 24 * 60 * 60 * 1000
        val maxTs = aiEvents.maxOf { it.timestamp } + 7L * 24 * 60 * 60 * 1000
        val existingEvents = try {
            repository.getEventsForRangeSync(minTs, maxTs)
        } catch (e: Exception) {
            Log.e(TAG, "Range query failed in processAiEvents", e)
            emptyList()
        }
        val results = mutableListOf<SyncResult>()
        val seen = mutableSetOf<Pair<String, Long>>()

        aiEvents.forEach { aiEvent ->
            val key = CalendarUtils.normalizedTitle(aiEvent.title) to (aiEvent.timestamp / 300_000L)
            if (!seen.add(key)) return@forEach // intra-batch dedup
            val eventType = aiEvent.eventType.uppercase().takeIf {
                it in setOf("EXAM", "EVALUATION", "DEADLINE", "BIRTHDAY", "MEETING", "HOLIDAY", "GENERAL")
            } ?: "GENERAL"

            val newEvent = EventEntry(
                title = aiEvent.title.trim(),
                timestamp = aiEvent.timestamp,
                eventType = eventType,
                subjectColor = aiEvent.subjectColor.takeIf {
                    Regex("^#[0-9A-Fa-f]{6}$").matches(it)
                } ?: "#9E9E9E",
                description = aiEvent.description,
                remindersEnabled = true
            )

            // NARROW: normalized exact title + same day + ±2h. No take(4) fuzzy merge.
            val candidate = existingEvents.find {
                CalendarUtils.normalizedTitle(it.title) == CalendarUtils.normalizedTitle(aiEvent.title) &&
                    CalendarUtils.isSameDay(it.timestamp, aiEvent.timestamp) &&
                    kotlin.math.abs(it.timestamp - aiEvent.timestamp) <= 2L * 60 * 60 * 1000
            }

            if (candidate != null) {
                if (kotlin.math.abs(candidate.timestamp - aiEvent.timestamp) <= 60_000L) {
                    return@forEach // exact duplicate -> skip
                }
                if (candidate.isCompleted) {
                    results.add(SyncResult.New(newEvent))
                } else {
                    // Preserve flags the AI payload doesn't carry.
                    results.add(
                        SyncResult.Reschedule(
                            candidate,
                            newEvent.copy(
                                id = candidate.id,
                                remindersEnabled = candidate.remindersEnabled,
                                isRecurring = candidate.isRecurring,
                                recurringInterval = candidate.recurringInterval,
                                recurringRule = candidate.recurringRule,
                                endTimestamp = candidate.endTimestamp,
                                durationMinutes = candidate.durationMinutes
                            )
                        )
                    )
                }
            } else {
                results.add(SyncResult.New(newEvent))
            }
        }
        return results
    }

    private fun parseIsoStrict(iso: String, tz: TimeZone): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        for (f in formats) {
            try {
                f.isLenient = false
                f.timeZone = tz
                return f.parse(iso.trim())?.time
            } catch (_: Exception) { }
        }
        return null
    }
}
