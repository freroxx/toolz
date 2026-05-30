package com.frerox.toolz.data.network

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val tag: String,
    val message: String,
    val level: String // "SUCCESS", "WARNING", "ERROR", "INFO"
)

@Dao
interface DiagnosticLogDao {
    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<DiagnosticLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DiagnosticLogEntry)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearAll()
}
