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

    @Query("SELECT * FROM math_history")
    suspend fun getAllHistorySync(): List<MathHistory>

    @Insert
    suspend fun insertHistories(entries: List<MathHistory>)
}
