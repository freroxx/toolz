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
    private val THEME_MODE = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val CUSTOM_PRIMARY_COLOR = intPreferencesKey("custom_primary_color")
    private val SHUTTER_SOUND_ENABLED = booleanPreferencesKey("shutter_sound_enabled")
    private val WORLD_CLOCK_ZONES = stringSetPreferencesKey("world_clock_zones")

    val stepGoal: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[STEP_GOAL] ?: 10000
    }

    val ringtoneUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[RINGTONE_URI]
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "SYSTEM"
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR] ?: true
    }

    val customPrimaryColor: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_PRIMARY_COLOR]
    }

    val shutterSoundEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SHUTTER_SOUND_ENABLED] ?: true
    }

    val worldClockZones: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[WORLD_CLOCK_ZONES] ?: setOf("UTC", "America/New_York", "Europe/London", "Asia/Tokyo")
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

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setCustomPrimaryColor(color: Int?) {
        context.dataStore.edit { preferences ->
            if (color == null) {
                preferences.remove(CUSTOM_PRIMARY_COLOR)
            } else {
                preferences[CUSTOM_PRIMARY_COLOR] = color
            }
        }
    }

    suspend fun setShutterSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHUTTER_SOUND_ENABLED] = enabled
        }
    }

    suspend fun addWorldClockZone(zone: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[WORLD_CLOCK_ZONES] ?: emptySet()
            preferences[WORLD_CLOCK_ZONES] = current + zone
        }
    }

    suspend fun removeWorldClockZone(zone: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[WORLD_CLOCK_ZONES] ?: emptySet()
            preferences[WORLD_CLOCK_ZONES] = current - zone
        }
    }
}
