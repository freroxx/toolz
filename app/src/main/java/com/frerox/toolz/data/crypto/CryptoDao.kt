package com.frerox.toolz.data.crypto

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CryptoDao {
    @Query("SELECT * FROM crypto_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<CryptoHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CryptoHistoryEntry)

    @Delete
    suspend fun deleteEntry(entry: CryptoHistoryEntry)

    @Query("DELETE FROM crypto_history")
    suspend fun clearHistory()
    @Query("SELECT * FROM crypto_history")
    suspend fun getAllHistorySync(): List<CryptoHistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entries: List<CryptoHistoryEntry>)
}
