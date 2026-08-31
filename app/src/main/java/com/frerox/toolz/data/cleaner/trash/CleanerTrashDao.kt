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

package com.frerox.toolz.data.cleaner.trash

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CleanerTrashDao {
    @Query("SELECT * FROM cleaner_trash ORDER BY deletedAt DESC")
    suspend fun getAll(): List<CleanerTrashEntity>
    @Query("SELECT * FROM cleaner_trash WHERE expiresAt < :now")
    suspend fun getExpired(now: Long = System.currentTimeMillis()): List<CleanerTrashEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CleanerTrashEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CleanerTrashEntity>)
    @Query("DELETE FROM cleaner_trash WHERE id = :id")
    suspend fun deleteById(id: Long)
    @Query("DELETE FROM cleaner_trash WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())
    @Query("DELETE FROM cleaner_trash")
    suspend fun clearAll()
}
