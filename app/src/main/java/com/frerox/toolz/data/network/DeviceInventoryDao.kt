/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.network

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * P5 persistence — device inventory (mesh) + scan snapshots.
 * Stores last-seen MAC → vendor mapping, survives process death (delta “new device” detection).
 */
@Entity(tableName = "device_inventory")
data class DeviceInventoryEntity(
    @PrimaryKey val mac: String, // normalized AA:BB:CC:DD:EE:FF
    val ip: String,
    val hostname: String,
    val vendor: String,
    val typeLabel: String,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val isGateway: Boolean = false
)

@Entity(tableName = "scan_snapshots")
data class ScanSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val ssid: String,
    val bssid: String,
    val json: String // serialized scanResults + congestion
)

@Dao
interface DeviceInventoryDao {
    @Query("SELECT * FROM device_inventory ORDER BY lastSeenMs DESC")
    fun observeAll(): Flow<List<DeviceInventoryEntity>>

    @Query("SELECT * FROM device_inventory WHERE mac = :mac LIMIT 1")
    suspend fun byMac(mac: String): DeviceInventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceInventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeviceInventoryEntity>)

    @Query("DELETE FROM device_inventory WHERE lastSeenMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)

    @Query("DELETE FROM device_inventory")
    suspend fun clearAll()
}

@Dao
interface ScanSnapshotDao {
    @Insert
    suspend fun insert(entity: ScanSnapshotEntity)

    @Query("SELECT * FROM scan_snapshots ORDER BY timestampMs DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<ScanSnapshotEntity>>

    @Query("DELETE FROM scan_snapshots WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}
