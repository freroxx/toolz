package com.frerox.toolz.data.steps

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steps")
data class StepEntry(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    val steps: Int,
    val lastSensorValue: Int = 0
)
