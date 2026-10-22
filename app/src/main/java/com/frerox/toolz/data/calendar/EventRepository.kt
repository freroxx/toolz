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

import com.frerox.toolz.data.todo.TaskDao
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.util.CalendarUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val taskDao: TaskDao
) {
    companion object {
        /**
         * Stable key for rendered rows. Occurrence copies share the template [EventEntry.id],
         * so lists MUST key by [occurrenceKey] ("id@timestamp"), never by id alone —
         * otherwise occurrences of one series collapse/duplicate in LazyColumn.
         */
        fun occurrenceKey(event: EventEntry): String = "${event.id}@${event.timestamp}"

        /** Single rule-resolution used by every expansion path (never fork). */
        fun recurrenceRuleOf(event: EventEntry): String? {
            event.recurringRule.takeIf { it != Recurrence.NONE }?.let { return it.name }
            return event.recurringInterval?.uppercase()?.takeIf {
                it in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
            }
        }

        fun isRecurring(event: EventEntry): Boolean =
            recurrenceRuleOf(event) != null || event.isRecurring

        /**
         * UI expansion over a FULL event list (all templates present, so no range-miss).
         * Non-recurring rows pass through untouched (full history preserved — zero
         * behavior change for them). Recurring templates gain bounded occurrences:
         * DAILY/WEEKLY within [now-90d, now+180d], MONTHLY/YEARLY within [now-2y, now+5y]
         * (generateOccurrences clamps to now+5y regardless). Bounds keep a daily series
         * from exploding the agenda into thousands of rows.
         *
         * Series semantics v1: occurrence copies share the template id — toggling,
         * editing or deleting an occurrence acts on the WHOLE series (no exdate model).
         * Lists MUST key rows by [occurrenceKey].
         */
        fun expandForUi(
            all: List<EventEntry>,
            now: Long = System.currentTimeMillis()
        ): List<EventEntry> {
            if (all.isEmpty()) return all
            val day = 24 * 60 * 60 * 1000L
            val out = ArrayList<EventEntry>(all.size + 64)
            val seen = HashSet<String>(all.size + 64)
            for (event in all) {
                val rule = recurrenceRuleOf(event)
                if (rule == null && !event.isRecurring) {
                    if (seen.add(occurrenceKey(event))) out.add(event)
                    continue
                }
                val (start, end) = when (rule ?: "YEARLY") {
                    "DAILY", "WEEKLY" -> (now - 90 * day) to (now + 180 * day)
                    else -> (now - 730 * day) to (now + 5 * 365 * day)
                }
                CalendarUtils.generateOccurrences(
                    eventTimestamp = event.timestamp,
                    recurringRule = rule ?: "YEARLY",
                    rangeStart = start,
                    rangeEnd = end,
                    now = now
                ).forEach { occ ->
                    val row = if (occ == event.timestamp) event
                    else event.copy(
                        timestamp = occ,
                        endTimestamp = event.endTimestamp?.let { e -> e + (occ - event.timestamp) }
                    )
                    if (seen.add(occurrenceKey(row))) out.add(row)
                }
            }
            return out.sortedBy { it.timestamp }
        }
    }

    fun getEventsForRange(start: Long, end: Long): Flow<List<EventEntry>> {
        // FIX (recurrence): a YEARLY/DAILY template from a prior year never falls
        // inside [start,end], so a pure range query can never expand it. Combine the
        // indexed range rows with the (small) recurring-templates table, expand, dedupe.
        return combine(
            eventDao.getEventsForRange(start, end),
            eventDao.getRecurringTemplates()
        ) { inRange, templates ->
            expandWithTemplates(inRange, templates, start, end)
        }
    }

    /** Shared expansion used by the Flow and sync variants (single implementation). */
    private fun expandWithTemplates(
        inRange: List<EventEntry>,
        templates: List<EventEntry>,
        start: Long,
        end: Long
    ): List<EventEntry> {
        val templateIds = templates.map { it.id }.toSet()
        // Non-recurring rows already in range pass through untouched.
        val direct = inRange.filter { it.id !in templateIds && !isRecurring(it) }
        val seen = HashSet<String>(direct.size + 64)
        direct.forEach { seen.add(occurrenceKey(it)) }
        val out = direct.toMutableList()
        // Every template expands over the window (generateOccurrences returns the
        // original timestamp too when it falls inside, so no special-casing).
        for (template in templates) {
            val rule = recurrenceRuleOf(template) ?: "YEARLY"
            CalendarUtils.generateOccurrences(
                eventTimestamp = template.timestamp,
                recurringRule = rule,
                rangeStart = start,
                rangeEnd = end
            ).forEach { occ ->
                val row = if (occ == template.timestamp) template
                else template.copy(
                    timestamp = occ,
                    endTimestamp = template.endTimestamp?.let { e -> e + (occ - template.timestamp) }
                )
                if (seen.add(occurrenceKey(row))) out.add(row)
            }
        }
        return out.sortedBy { it.timestamp }
    }

    private fun isEventInYearlyRange(event: EventEntry, start: Long, end: Long): Boolean {
        val eventCal = Calendar.getInstance().apply { timeInMillis = event.timestamp }
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }

        for (year in startCal.get(Calendar.YEAR)..endCal.get(Calendar.YEAR)) {
            val occurrence = (eventCal.clone() as Calendar).apply {
                set(Calendar.YEAR, year)
            }
            if (occurrence.timeInMillis in start..end) return true
        }
        return false
    }

    fun getAllEvents(): Flow<List<EventEntry>> {
        return eventDao.getAllEvents()
    }

    // CAL-P0-03/P0-07/P1-02: sync single-shot DAO delegates (IO callers only, never Main).
    suspend fun getAllEventsSync(): List<EventEntry> = eventDao.getAllEventsSync()

    suspend fun getEventByIdSync(eventId: Int): EventEntry? = eventDao.getEventByIdSync(eventId)

    suspend fun getUpcomingSync(now: Long): List<EventEntry> = eventDao.getUpcomingSync(now)

    suspend fun getEventsForRangeSync(start: Long, end: Long): List<EventEntry> =
        expandWithTemplates(
            eventDao.getEventsForRangeSync(start, end),
            eventDao.getRecurringTemplatesSync(),
            start, end
        )

    fun getTasksWithDueDate(): Flow<List<TaskEntry>> {
        return taskDao.getTasksWithDueDate()
    }

    suspend fun insertEvent(event: EventEntry) = eventDao.insertEvent(event)
    
    suspend fun insertEventsBulk(events: List<EventEntry>) = eventDao.insertEventsTx(events)

    /** Result summary for deduplicated sync/import operations. */
    data class DeduplicationResult(
        val insertedCount: Int,
        val updatedCount: Int,
        val skippedCount: Int,
    ) {
        val totalProcessed: Int get() = insertedCount + updatedCount + skippedCount
    }

    /**
     * Inserts incoming events with robust deduplication.
     * Prevents duplication across multiple sync runs and within the incoming batch.
     */
    suspend fun insertEventsDeduplicated(incoming: List<EventEntry>): DeduplicationResult {
        if (incoming.isEmpty()) return DeduplicationResult(0, 0, 0)

        val existingEvents = eventDao.getAllEventsSync()

        fun dedupeKey(title: String, ts: Long): String {
            val normTitle = title.trim().lowercase()
            val minuteBucket = ts / 60_000L
            return "$normTitle@$minuteBucket"
        }

        val existingMap = mutableMapOf<String, EventEntry>()
        for (e in existingEvents) {
            existingMap[dedupeKey(e.title, e.timestamp)] = e
        }

        val toInsert = mutableListOf<EventEntry>()
        val toUpdate = mutableListOf<EventEntry>()
        var skipped = 0

        // 1. Deduplicate within incoming batch first
        val distinctIncoming = mutableListOf<EventEntry>()
        val seenInBatch = mutableSetOf<String>()
        for (event in incoming) {
            val key = dedupeKey(event.title, event.timestamp)
            if (seenInBatch.add(key)) {
                distinctIncoming.add(event)
            } else {
                skipped++
            }
        }

        // 2. Match against existing DB records
        for (event in distinctIncoming) {
            val key = dedupeKey(event.title, event.timestamp)
            val matched = existingMap[key]
            if (matched != null) {
                // Check if attributes changed (description, color, recurrence, etc.)
                val isIdentical = matched.title == event.title &&
                        matched.description == event.description &&
                        matched.eventType == event.eventType &&
                        matched.subjectColor == event.subjectColor &&
                        matched.durationMinutes == event.durationMinutes &&
                        matched.recurringRule == event.recurringRule
                if (!isIdentical) {
                    toUpdate.add(event.copy(id = matched.id))
                } else {
                    skipped++
                }
            } else {
                toInsert.add(event.copy(id = 0))
                existingMap[key] = event
            }
        }

        if (toInsert.isNotEmpty()) {
            eventDao.insertEventsTx(toInsert)
        }
        for (update in toUpdate) {
            eventDao.updateEvent(update)
        }

        return DeduplicationResult(
            insertedCount = toInsert.size,
            updatedCount = toUpdate.size,
            skippedCount = skipped
        )
    }

    /**
     * Purges existing duplicate events from the database (e.g. created by earlier syncs).
     * Keeps the earliest created event for each (title, minute-bucket) pair and deletes copies.
     * Returns the count of deleted duplicate events.
     */
    suspend fun removeDuplicateEvents(): Int {
        val allEvents = eventDao.getAllEventsSync()
        if (allEvents.size <= 1) return 0

        fun dedupeKey(e: EventEntry): String {
            val normTitle = e.title.trim().lowercase()
            val minuteBucket = e.timestamp / 60_000L
            return "$normTitle@$minuteBucket"
        }

        val seen = mutableSetOf<String>()
        val duplicateIds = mutableListOf<Int>()

        for (e in allEvents) {
            val key = dedupeKey(e)
            if (!seen.add(key)) {
                duplicateIds.add(e.id)
            }
        }

        if (duplicateIds.isNotEmpty()) {
            eventDao.deleteEvents(duplicateIds)
        }
        return duplicateIds.size
    }

    suspend fun deleteEvents(eventIds: List<Int>) {
        if (eventIds.isNotEmpty()) {
            eventDao.deleteEvents(eventIds)
        }
    }

    suspend fun clearAllEvents() {
        eventDao.deleteAll()
    }
    
    suspend fun updateEvent(event: EventEntry) = eventDao.updateEvent(event)

    suspend fun deleteEvent(eventId: Int) = eventDao.deleteEvent(eventId)

    suspend fun updateTask(task: TaskEntry) = taskDao.updateTask(task)

    fun getUpcomingEvents(): Flow<List<EventEntry>> {
        val now = System.currentTimeMillis()
        val oneWeekLater = now + 7 * 24 * 60 * 60 * 1000L
        return getEventsForRange(now, oneWeekLater)
    }
}
