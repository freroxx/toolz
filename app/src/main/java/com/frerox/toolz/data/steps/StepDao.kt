package com.frerox.toolz.data.steps

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE date = :date")
    fun getStepsForDate(date: String): Flow<StepEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSteps(stepEntry: StepEntry)

    @Query("SELECT * FROM steps ORDER BY date DESC LIMIT 7")
    fun getRecentSteps(): Flow<List<StepEntry>>
}
