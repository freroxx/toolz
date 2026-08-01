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

import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

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
    suspend fun processAiCalendarEvents(aiEvents: List<AiCalendarEvent>): List<SyncResult> {
        val existingEvents = repository.getAllEvents().first()
        val results = mutableListOf<SyncResult>()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        aiEvents.forEach { aiEvent ->
            val timestamp = try {
                sdf.parse(aiEvent.start_time_iso)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            val newEvent = EventEntry(
                title = aiEvent.title,
                timestamp = timestamp,
                description = aiEvent.description,
                eventType = "GENERAL", // AI schema doesn't provide type anymore
                subjectColor = "#9E9E9E" // Default grey
            )

            val similarTitleNearby = existingEvents.find {
                it.title.equals(aiEvent.title, ignoreCase = true) &&
                Math.abs(it.timestamp - timestamp) < 7L * 24 * 60 * 60 * 1000
            }

            if (similarTitleNearby != null) {
                if (!isSameDay(similarTitleNearby.timestamp, timestamp) ||
                    Math.abs(similarTitleNearby.timestamp - timestamp) > 60000) {
                    results.add(SyncResult.Reschedule(similarTitleNearby, newEvent.copy(id = similarTitleNearby.id)))
                }
            } else {
                results.add(SyncResult.New(newEvent))
            }
        }
        return results
    }

    suspend fun processAiEvents(aiEvents: List<AiEventResult>): List<SyncResult> {
        val existingEvents = repository.getAllEvents().first()
        val results = mutableListOf<SyncResult>()

        aiEvents.forEach { aiEvent ->
            val eventType = aiEvent.eventType.uppercase()

            val newEvent = EventEntry(
                title = aiEvent.title,
                timestamp = aiEvent.timestamp,
                eventType = eventType,
                subjectColor = aiEvent.subjectColor,
                description = aiEvent.description
            )

            // Decisions logic:
            // 1. Find if an event with the exact same title exists in a nearby timeframe (e.g., 7 days)
            val similarTitleNearby = existingEvents.find { 
                it.title.equals(aiEvent.title, ignoreCase = true) &&
                Math.abs(it.timestamp - aiEvent.timestamp) < 7L * 24 * 60 * 60 * 1000
            }

            if (similarTitleNearby != null) {
                // If title matches and it's either a different day OR a different time (more than 1 minute diff)
                if (!isSameDay(similarTitleNearby.timestamp, aiEvent.timestamp) || 
                    Math.abs(similarTitleNearby.timestamp - aiEvent.timestamp) > 60000) {
                    results.add(SyncResult.Reschedule(similarTitleNearby, newEvent.copy(id = similarTitleNearby.id)))
                } else {
                    // It's a duplicate (same title, same day, same time) -> Skip
                }
            } else {
                // Check if any event of the SAME TYPE exists on the same day with very similar title
                val sameDateAndType = existingEvents.find {
                    isSameDay(it.timestamp, aiEvent.timestamp) && 
                    it.eventType == eventType && 
                    it.title.take(4).equals(aiEvent.title.take(4), ignoreCase = true)
                }
                
                if (sameDateAndType != null) {
                    results.add(SyncResult.Reschedule(sameDateAndType, newEvent.copy(id = sameDateAndType.id)))
                } else {
                    results.add(SyncResult.New(newEvent))
                }
            }
        }
        return results
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
