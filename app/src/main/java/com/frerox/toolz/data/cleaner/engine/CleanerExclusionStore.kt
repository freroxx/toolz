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

package com.frerox.toolz.data.cleaner.engine

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CleanerExclusionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val KEY_EXCLUSIONS = stringSetPreferencesKey("cleaner_exclusions")
    val exclusionsFlow: Flow<Set<String>> = dataStore.data.map { it[KEY_EXCLUSIONS] ?: emptySet() }
    suspend fun addExclusion(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_EXCLUSIONS] ?: emptySet()
            prefs[KEY_EXCLUSIONS] = current + path
        }
    }
    suspend fun removeExclusion(path: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_EXCLUSIONS] ?: emptySet()
            prefs[KEY_EXCLUSIONS] = current - path
        }
    }
    suspend fun clearAll() {
        dataStore.edit { it.remove(KEY_EXCLUSIONS) }
    }
    fun isExcluded(path: String, exclusions: Set<String>): Boolean {
        if (exclusions.isEmpty()) return false
        return exclusions.any { ex -> path == ex || path.startsWith(ex + "/") || path.contains(ex) }
    }
}
