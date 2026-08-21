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

package com.frerox.toolz.data.network

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * P5: persisted speed-test history. Powers trend sparkline and ISP latency stats.
 * Retention policy: rolling 90 days, purged on insert; user-clearable.
 */
@Entity(tableName = "network_speed_history")
data class SpeedHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val idleLatencyMs: Long?,
    val loadedLatencyMs: Long?,
    val bloatGrade: String?,
    val ssid: String
)

data class LatencyStats(
    val samples: Int,
    val p50Ms: Long?,
    val p95Ms: Long?
)

@Dao
interface SpeedHistoryDao {

    @Insert
    suspend fun insert(entry: SpeedHistoryEntity)

    @Query("SELECT * FROM network_speed_history ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<SpeedHistoryEntity>>

    @Query("SELECT * FROM network_speed_history WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    suspend fun since(sinceMs: Long): List<SpeedHistoryEntity>

    @Query("DELETE FROM network_speed_history")
    suspend fun clearAll()

    @Query("DELETE FROM network_speed_history WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)

    companion object {
        const val RETENTION_MS: Long = 90L * 24 * 60 * 60 * 1000 // 90 days
    }
}
