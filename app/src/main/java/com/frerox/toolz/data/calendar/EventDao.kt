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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntry): Long

    @Update
    suspend fun updateEvent(event: EventEntry)

    // CAL-P1-05: row-count variant for future undo/merge flows (additive).
    @Update
    suspend fun updateEventCount(event: EventEntry): Int

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Int)

    // CAL-P3: non-destructive clear used by backup REPLACE-after-merge (additive).
    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("SELECT * FROM events WHERE timestamp >= :start AND timestamp <= :end")
    fun getEventsForRange(start: Long, end: Long): Flow<List<EventEntry>>

    // CAL-P0-03/P1-03: range query without full-table load.
    @Query("SELECT * FROM events WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getEventsForRangeSync(start: Long, end: Long): List<EventEntry>

    @Query("SELECT * FROM events WHERE isRecurring = 1")
    fun getRecurringEvents(): Flow<List<EventEntry>>

    @Query("SELECT * FROM events ORDER BY timestamp ASC")
    fun getAllEvents(): Flow<List<EventEntry>>

    @Query("SELECT * FROM events")
    suspend fun getAllEventsSync(): List<EventEntry>

    @Query("SELECT * FROM events ORDER BY timestamp ASC")
    suspend fun getAllEventsSyncOrdered(): List<EventEntry>

    // CAL-P0-03: reschedule without full-table load.
    @Query("SELECT * FROM events WHERE timestamp > :now ORDER BY timestamp ASC")
    suspend fun getUpcomingSync(now: Long): List<EventEntry>

    // CAL-P0-07: single-row fetch per firing (no full-table O(n) + SQLCipher open per alarm).
    @Query("SELECT * FROM events WHERE id = :eventId LIMIT 1")
    suspend fun getEventByIdSync(eventId: Int): EventEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntry>)

    // CAL-P1-05: bulk insert in one transaction (additive).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEventsTx(events: List<EventEntry>)
}
