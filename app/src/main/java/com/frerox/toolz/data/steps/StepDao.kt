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

package com.frerox.toolz.data.steps

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class StepStats(
    val total: Long,
    val max: Int,
    val count: Int,
    val avg: Double
)

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE date = :date")
    fun getStepsForDate(date: String): Flow<StepEntry?>

    /** One-shot sync read used by the service's sensor handler. */
    @Query("SELECT * FROM steps WHERE date = :date LIMIT 1")
    suspend fun getStepsForDateSync(date: String): StepEntry?

    /**
     * True atomic increment. Returns number of rows updated (0 if no row yet).
     * Preferred for sensor emissions to avoid read-modify-write races.
     */
    @Query("UPDATE steps SET steps = steps + :delta, lastSensorValue = :sensorVal WHERE date = :date")
    suspend fun incrementSteps(date: String, delta: Int, sensorVal: Int): Int

    /**
     * Legacy absolute set. Kept for UI-initiated corrections only.
     * Do NOT use from the sensor hot path — use [incrementSteps].
     */
    @Query("UPDATE steps SET steps = :steps, lastSensorValue = :sensorVal WHERE date = :date")
    suspend fun atomicUpdateSteps(date: String, steps: Int, sensorVal: Int)

    /** Used to decide INSERT vs UPDATE without fetching the full row. */
    @Query("SELECT COUNT(*) FROM steps WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSteps(stepEntry: StepEntry)

    @Query("SELECT * FROM steps ORDER BY date DESC LIMIT 7")
    fun getRecentSteps(): Flow<List<StepEntry>>

    @Query("SELECT * FROM steps WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getStepsInRange(startDate: String, endDate: String): Flow<List<StepEntry>>

    @Query("SELECT * FROM steps WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getStepsInRangeAsc(startDate: String, endDate: String): Flow<List<StepEntry>>

    @Query("SELECT COALESCE(SUM(steps),0) AS total, COALESCE(MAX(steps),0) AS max, COUNT(*) AS count, COALESCE(AVG(steps),0.0) AS avg FROM steps WHERE date >= :startDate AND date <= :endDate")
    suspend fun getStatsInRange(startDate: String, endDate: String): StepStats

    @Query("DELETE FROM steps WHERE date < :date")
    suspend fun deleteStepsBeforeDate(date: String)

    @Query("SELECT * FROM steps")
    suspend fun getAllStepsSync(): List<StepEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(entries: List<StepEntry>)
}
