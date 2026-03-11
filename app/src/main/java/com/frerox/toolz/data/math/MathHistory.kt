package com.frerox.toolz.data.math

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "math_history")
data class MathHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MathHistoryDao {
    @Query("SELECT * FROM math_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<MathHistory>>

    @Insert
    suspend fun insert(history: MathHistory)

    @Query("DELETE FROM math_history")
    suspend fun clearAll()
}
