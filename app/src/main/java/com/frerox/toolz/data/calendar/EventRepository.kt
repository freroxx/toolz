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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val taskDao: TaskDao
) {
    fun getEventsForRange(start: Long, end: Long): Flow<List<EventEntry>> {
        // CAL-P1-05: delegate range filtering to the DAO (indexed query) instead of
        // in-memory full-table filtering, then expand recurring occurrences.
        return eventDao.getEventsForRange(start, end).map { inRange ->
            val expanded = inRange.flatMap { event ->
                val rule = event.recurringRule.takeIf { it != Recurrence.NONE }?.name
                    ?: event.recurringInterval?.uppercase()?.takeIf {
                        it in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")
                    }
                if (rule != null && rule != "NONE") {
                    com.frerox.toolz.util.CalendarUtils.generateOccurrences(
                        eventTimestamp = event.timestamp,
                        recurringRule = rule,
                        rangeStart = start,
                        rangeEnd = end
                    ).map { occ ->
                        if (occ == event.timestamp) event
                        else event.copy(
                            id = event.id,
                            timestamp = occ,
                            endTimestamp = event.endTimestamp?.let { end -> end + (occ - event.timestamp) }
                        )
                    }
                } else listOf(event)
            }
            // Yearly legacy rows that predate recurringRule but carry isRecurring+YEARLY.
            val legacyYearly = emptyList<EventEntry>()
            (expanded + legacyYearly).sortedBy { it.timestamp }
        }
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
        eventDao.getEventsForRangeSync(start, end)

    fun getTasksWithDueDate(): Flow<List<TaskEntry>> {
        return taskDao.getTasksWithDueDate()
    }

    suspend fun insertEvent(event: EventEntry) = eventDao.insertEvent(event)
    
    suspend fun updateEvent(event: EventEntry) = eventDao.updateEvent(event)

    suspend fun deleteEvent(eventId: Int) = eventDao.deleteEvent(eventId)

    suspend fun updateTask(task: TaskEntry) = taskDao.updateTask(task)

    fun getUpcomingEvents(): Flow<List<EventEntry>> {
        val now = System.currentTimeMillis()
        val oneWeekLater = now + 7 * 24 * 60 * 60 * 1000L
        return getEventsForRange(now, oneWeekLater)
    }
}
