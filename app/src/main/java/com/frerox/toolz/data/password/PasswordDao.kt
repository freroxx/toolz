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

package com.frerox.toolz.data.password

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Escape SQLite LIKE wildcards so user input can't over-match. */
fun String.escapeLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY name COLLATE NOCASE ASC")
    fun getAllPasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE id = :id")
    suspend fun getPasswordById(id: Int): PasswordEntity?

    @Query("SELECT * FROM passwords WHERE url LIKE '%' || :domain || '%' ESCAPE '\\' OR name LIKE '%' || :domain || '%' ESCAPE '\\'")
    suspend fun getPasswordsByDomain(domain: String): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE lower(url) = lower(:host) OR lower(url) LIKE '%://' || lower(:host) || '/%' ESCAPE '\\' OR lower(url) LIKE '%://' || lower(:host) ESCAPE '\\' OR lower(url) LIKE '%://' || lower(:host) || ':%' ESCAPE '\\'")
    suspend fun getPasswordsByExactHost(host: String): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE lower(url) LIKE '%://' || lower(:registrable) || '/%' ESCAPE '\\' OR lower(url) LIKE '%://' || lower(:registrable) ESCAPE '\\' OR lower(url) LIKE '%://' || lower(:registrable) || ':%' ESCAPE '\\'")
    suspend fun getByRegistrableDomain(registrable: String): List<PasswordEntity>

    @Query("UPDATE passwords SET lastUsedAt = :ts WHERE id = :id")
    suspend fun updateLastUsed(id: Int, ts: Long)

    @Query("SELECT * FROM passwords WHERE name LIKE '%' || :query || '%' ESCAPE '\\' COLLATE NOCASE OR username LIKE '%' || :query || '%' ESCAPE '\\' COLLATE NOCASE OR url LIKE '%' || :query || '%' ESCAPE '\\' COLLATE NOCASE")
    suspend fun searchPasswords(query: String): List<PasswordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPassword(password: PasswordEntity): Long

    @Update
    suspend fun updatePassword(password: PasswordEntity)

    @Query("UPDATE passwords SET pwnedCount = :count WHERE id = :id")
    suspend fun updatePwnedCount(id: Int, count: Int?)

    @Delete
    suspend fun deletePassword(password: PasswordEntity)

    @Query("SELECT COUNT(*) FROM passwords")
    suspend fun getPasswordCount(): Int

    @Query("SELECT * FROM passwords")
    suspend fun getAllPasswordsSync(): List<PasswordEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPasswords(passwords: List<PasswordEntity>): List<Long>

    @Transaction
    suspend fun insertPasswordsTx(passwords: List<PasswordEntity>) {
        passwords.forEach { insertPassword(it.copy(id = 0)) }
    }
}
