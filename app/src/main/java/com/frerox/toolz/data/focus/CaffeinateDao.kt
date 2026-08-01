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

package com.frerox.toolz.data.focus

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "caffeinate_apps")
data class CaffeinateApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,
    val isAutoEnabled: Boolean = false
)

@Dao
interface CaffeinateDao {
    @Query("SELECT * FROM caffeinate_apps")
    fun getAllApps(): Flow<List<CaffeinateApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<CaffeinateApp>)

    @Update
    suspend fun updateApp(app: CaffeinateApp)

    @Query("SELECT * FROM caffeinate_apps WHERE isAutoEnabled = 1")
    suspend fun getAutoEnabledApps(): List<CaffeinateApp>
    
    @Query("DELETE FROM caffeinate_apps")
    suspend fun deleteAll()
    @Query("SELECT * FROM caffeinate_apps")
    suspend fun getAllAppsSync(): List<CaffeinateApp>
}
