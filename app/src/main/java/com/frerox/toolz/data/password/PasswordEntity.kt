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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String?,
    val username: String,
    val password: String,
    val strength: Int, // 0-4
    val pwnedCount: Int? = null,
    // V3-FIX: for Whisper vault entries records whether the credential is a 64-char
    // anon token; null = unknown/legacy.
    @ColumnInfo(defaultValue = "NULL") val isToken: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val passwordHistory: List<String> = emptyList()
) {
    val isComplete: Boolean get() = username.isNotBlank() && password.isNotBlank()
}
