package com.frerox.toolz.data.settings

import android.content.Context
import android.media.RingtoneManager
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
    private val SHUTTER_SOUND_URI = stringPreferencesKey("shutter_sound_uri")
    private val WORLD_CLOCK_ZONES = stringSetPreferencesKey("world_clock_zones")
    
    // Notifications
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val STEP_NOTIFICATIONS = booleanPreferencesKey("step_notifications")
    private val TIMER_NOTIFICATIONS = booleanPreferencesKey("timer_notifications")
    private val VOICE_RECORD_NOTIFICATIONS = booleanPreferencesKey("voice_record_notifications")
    
    // Widget Design
    private val WIDGET_BACKGROUND_COLOR = intPreferencesKey("widget_background_color")
    private val WIDGET_ACCENT_COLOR = intPreferencesKey("widget_accent_color")
    private val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")

    private val defaultAlarmUri: String by lazy {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: ""
    }
    
    private val defaultShutterUri: String by lazy {
        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.toString()
    }

    val stepGoal: Flow<Int> = context.dataStore.data.map { it[STEP_GOAL] ?: 10000 }
    val ringtoneUri: Flow<String?> = context.dataStore.data.map { it[RINGTONE_URI] ?: defaultAlarmUri }
    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "SYSTEM" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val customPrimaryColor: Flow<Int?> = context.dataStore.data.map { it[CUSTOM_PRIMARY_COLOR] }
    val shutterSoundEnabled: Flow<Boolean> = context.dataStore.data.map { it[SHUTTER_SOUND_ENABLED] ?: true }
    val shutterSoundUri: Flow<String?> = context.dataStore.data.map { it[SHUTTER_SOUND_URI] ?: defaultShutterUri }
    val worldClockZones: Flow<Set<String>> = context.dataStore.data.map { it[WORLD_CLOCK_ZONES] ?: setOf("UTC", "America/New_York", "Europe/London", "Asia/Tokyo") }

    // Notifications Flows
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val stepNotifications: Flow<Boolean> = context.dataStore.data.map { it[STEP_NOTIFICATIONS] ?: true }
    val timerNotifications: Flow<Boolean> = context.dataStore.data.map { it[TIMER_NOTIFICATIONS] ?: true }
    val voiceRecordNotifications: Flow<Boolean> = context.dataStore.data.map { it[VOICE_RECORD_NOTIFICATIONS] ?: true }

    // Widget Flows
    val widgetBackgroundColor: Flow<Int> = context.dataStore.data.map { it[WIDGET_BACKGROUND_COLOR] ?: 0xFFFFFFFF.toInt() }
    val widgetAccentColor: Flow<Int> = context.dataStore.data.map { it[WIDGET_ACCENT_COLOR] ?: 0xFF4CAF50.toInt() }
    val widgetOpacity: Flow<Float> = context.dataStore.data.map { it[WIDGET_OPACITY] ?: 0.9f }

    suspend fun setStepGoal(goal: Int) { context.dataStore.edit { it[STEP_GOAL] = goal } }
    suspend fun setRingtoneUri(uri: String) { context.dataStore.edit { it[RINGTONE_URI] = uri } }
    suspend fun setThemeMode(mode: String) { context.dataStore.edit { it[THEME_MODE] = mode } }
    suspend fun setDynamicColor(enabled: Boolean) { context.dataStore.edit { it[DYNAMIC_COLOR] = enabled } }
    suspend fun setCustomPrimaryColor(color: Int?) {
        context.dataStore.edit { 
            if (color == null) it.remove(CUSTOM_PRIMARY_COLOR) else it[CUSTOM_PRIMARY_COLOR] = color 
        }
    }
    suspend fun setShutterSoundEnabled(enabled: Boolean) { context.dataStore.edit { it[SHUTTER_SOUND_ENABLED] = enabled } }
    suspend fun setShutterSoundUri(uri: String) { context.dataStore.edit { it[SHUTTER_SOUND_URI] = uri } }
    suspend fun addWorldClockZone(zone: String) { context.dataStore.edit { it[WORLD_CLOCK_ZONES] = (it[WORLD_CLOCK_ZONES] ?: emptySet()) + zone } }
    suspend fun removeWorldClockZone(zone: String) { context.dataStore.edit { it[WORLD_CLOCK_ZONES] = (it[WORLD_CLOCK_ZONES] ?: emptySet()) - zone } }

    // Notification setters
    suspend fun setNotificationsEnabled(enabled: Boolean) { context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled } }
    suspend fun setStepNotifications(enabled: Boolean) { context.dataStore.edit { it[STEP_NOTIFICATIONS] = enabled } }
    suspend fun setTimerNotifications(enabled: Boolean) { context.dataStore.edit { it[TIMER_NOTIFICATIONS] = enabled } }
    suspend fun setVoiceRecordNotifications(enabled: Boolean) { context.dataStore.edit { it[VOICE_RECORD_NOTIFICATIONS] = enabled } }

    // Widget setters
    suspend fun setWidgetBackgroundColor(color: Int) { context.dataStore.edit { it[WIDGET_BACKGROUND_COLOR] = color } }
    suspend fun setWidgetAccentColor(color: Int) { context.dataStore.edit { it[WIDGET_ACCENT_COLOR] = color } }
    suspend fun setWidgetOpacity(opacity: Float) { context.dataStore.edit { it[WIDGET_OPACITY] = opacity } }
}
