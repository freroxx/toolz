package com.frerox.toolz.data.crypto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crypto_history")
data class CryptoHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val input: String,
    val result: String,
    val algorithm: String,
    val type: String = "ENCRYPT", // ENCRYPT, DECRYPT, HASH, ENCODE, DECODE
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)
