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
