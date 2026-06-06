package com.frerox.toolz.data.device.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.frerox.toolz.data.device.DeviceSpecResponse

@Entity(tableName = "device_specs_cache")
data class DeviceSpecCacheEntity(
    @PrimaryKey val query: String,
    val specs: DeviceSpecResponse,
    val timestamp: Long = System.currentTimeMillis()
)
