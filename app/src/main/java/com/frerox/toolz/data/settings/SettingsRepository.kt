package com.frerox.toolz.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val STEP_GOAL = intPreferencesKey("step_goal")
    private val RINGTONE_URI = stringPreferencesKey("timer_ringtone_uri")
    private val DARK_MODE = booleanPreferencesKey("dark_mode")

    val stepGoal: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[STEP_GOAL] ?: 10000
    }

    val ringtoneUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[RINGTONE_URI]
    }

    val darkMode: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE]
    }

    suspend fun setStepGoal(goal: Int) {
        context.dataStore.edit { preferences ->
            preferences[STEP_GOAL] = goal
        }
    }

    suspend fun setRingtoneUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[RINGTONE_URI] = uri
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE] = enabled
        }
    }
}
