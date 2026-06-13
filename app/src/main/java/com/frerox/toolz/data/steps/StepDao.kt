package com.frerox.toolz.data.steps

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE date = :date")
    fun getStepsForDate(date: String): Flow<StepEntry?>

    /** One-shot sync read used by the service's sensor handler. */
    @Query("SELECT * FROM steps WHERE date = :date LIMIT 1")
    suspend fun getStepsForDateSync(date: String): StepEntry?

    /**
     * Atomic step increment.  Preferred over INSERT-REPLACE because it avoids
     * the "read → compute → write" window where a concurrent coroutine could
     * overwrite an intermediate value.
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

    @Query("DELETE FROM steps WHERE date < :date")
    suspend fun deleteStepsBeforeDate(date: String)

    @Query("SELECT * FROM steps")
    suspend fun getAllStepsSync(): List<StepEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(entries: List<StepEntry>)
}
