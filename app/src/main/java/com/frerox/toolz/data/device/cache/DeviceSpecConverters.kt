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
