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

package com.frerox.toolz.data.catalog

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "catalog_search_history")
data class CatalogSearchEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface CatalogSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(entry: CatalogSearchEntry)

    @Query("SELECT * FROM catalog_search_history ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSearches(): Flow<List<CatalogSearchEntry>>

    @Query("SELECT * FROM catalog_search_history")
    suspend fun getAllSearchesSync(): List<CatalogSearchEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearches(entries: List<CatalogSearchEntry>)

    @Query("DELETE FROM catalog_search_history")
    suspend fun clearHistory()
}
