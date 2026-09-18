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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Recurrence rule for calendar events (CAL-P1-05). Stored via [CalendarConverters]. */
enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

@Entity(
    tableName = "events",
    indices = [Index("timestamp"), Index("eventType")]
)
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
    val remindersEnabled: Boolean = false,
    // CAL-P1-05: duration/end + typed recurrence. Keep isRecurring/recurringInterval
    // for one version as compat (derived from recurringRule where possible).
    val endTimestamp: Long? = null,
    val durationMinutes: Int = 60,
    val recurringRule: Recurrence = Recurrence.NONE
)
