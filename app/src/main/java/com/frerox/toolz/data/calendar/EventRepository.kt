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
        return eventDao.getAllEvents().map { allEvents ->
            allEvents.filter { event ->
                if (event.isRecurring && event.recurringInterval == "YEARLY") {
                    isEventInYearlyRange(event, start, end)
                } else {
                    event.timestamp in start..end
                }
            }
        }
    }

    private fun isEventInYearlyRange(event: EventEntry, start: Long, end: Long): Boolean {
        val eventCal = Calendar.getInstance().apply { timeInMillis = event.timestamp }
        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        val endCal = Calendar.getInstance().apply { timeInMillis = end }

        // Simple yearly check: does the month and day fall between start and end?
        // This is a bit complex for multi-month ranges, so we'll simplify:
        // Check if the event's month/day occurs in the years spanned by start and end.
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

    fun getTasksWithDueDate(): Flow<List<TaskEntry>> {
        return taskDao.getTasksWithDueDate()
    }

    suspend fun insertEvent(event: EventEntry) = eventDao.insertEvent(event)
    
    suspend fun updateEvent(event: EventEntry) = eventDao.updateEvent(event)

    suspend fun deleteEvent(eventId: Int) = eventDao.deleteEvent(eventId)

    fun getUpcomingEvents(): Flow<List<EventEntry>> {
        val now = System.currentTimeMillis()
        val oneWeekLater = now + 7 * 24 * 60 * 60 * 1000L
        return getEventsForRange(now, oneWeekLater)
    }
}
