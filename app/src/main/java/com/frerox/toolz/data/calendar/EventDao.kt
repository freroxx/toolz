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

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Int)

    @Query("SELECT * FROM events WHERE timestamp >= :start AND timestamp <= :end")
    fun getEventsForRange(start: Long, end: Long): Flow<List<EventEntry>>

    @Query("SELECT * FROM events WHERE isRecurring = 1")
    fun getRecurringEvents(): Flow<List<EventEntry>>

    @Query("SELECT * FROM events ORDER BY timestamp ASC")
    fun getAllEvents(): Flow<List<EventEntry>>
}
