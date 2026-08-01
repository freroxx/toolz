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

package com.frerox.toolz.data.device

import com.frerox.toolz.data.device.cache.DeviceSpecCacheEntity
import com.frerox.toolz.data.device.cache.DeviceSpecsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceSpecsRepository @Inject constructor(
    private val api: DeviceSpecsApi,
    private val dao: DeviceSpecsDao,
) {
    suspend fun getDeviceSpecs(model: String, forceRefresh: Boolean = false): Result<Pair<DeviceSpecResponse, Boolean>> = runCatching {
        val query = model.trim()
        
        // 1. Try to get from local cache first if not forcing refresh
        if (!forceRefresh) {
            val cached = dao.getSpecsByQuery(query)
            if (cached != null) {
                // Check if cache is fresh (e.g., less than 15 days old as backend syncs bi-monthly)
                val fifteenDaysMillis = 15L * 24 * 60 * 60 * 1000
                if (System.currentTimeMillis() - cached.timestamp < fifteenDaysMillis) {
                    return@runCatching cached.specs to true
                }
            }
        }

        // 2. Fetch from network
        val networkSpecs = api.getDeviceSpecs(query)
        
        // 3. Save to cache for future instant loading
        dao.insertSpecs(DeviceSpecCacheEntity(query, networkSpecs))
        
        networkSpecs to false
    }

    suspend fun clearCache() {
        dao.clearAllSpecs()
    }
}
