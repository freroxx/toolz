package com.frerox.toolz.data.focus

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val limitMillis: Long,
    val isEnabled: Boolean = true
)
