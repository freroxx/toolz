package com.frerox.toolz.data.device.cache

import androidx.room.TypeConverter
import com.frerox.toolz.data.device.DeviceSpecResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DeviceSpecConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromDeviceSpec(value: DeviceSpecResponse): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toDeviceSpec(value: String): DeviceSpecResponse {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            DeviceSpecResponse(matchedDevice = "Unknown")
        }
    }
}
