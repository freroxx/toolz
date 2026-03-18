package com.frerox.toolz.data.calendar

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String? = null,
    val timestamp: Long,
    val eventType: String = "GENERAL",
    val subjectColor: String = "#6200EE",
    val isRecurring: Boolean = false,
    val recurringInterval: String? = null,
    val isCompleted: Boolean = false,
    val remindersEnabled: Boolean = false
)
