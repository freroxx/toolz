package com.frerox.toolz.data.device.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceSpecsDao {
    @Query("SELECT * FROM device_specs_cache WHERE `query` = :query")
    suspend fun getSpecsByQuery(query: String): DeviceSpecCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecs(specs: DeviceSpecCacheEntity)

    @Query("DELETE FROM device_specs_cache")
    suspend fun clearAllSpecs()

    @Query("DELETE FROM device_specs_cache WHERE timestamp < :expirationTime")
    suspend fun deleteOldSpecs(expirationTime: Long)
}
