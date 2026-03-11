package com.frerox.toolz.data.settings

import android.media.RingtoneManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val STEP_GOAL = intPreferencesKey("step_goal")
    private val RINGTONE_URI = stringPreferencesKey("timer_ringtone_uri")
    private val THEME_MODE = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
    private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    private val CUSTOM_PRIMARY_COLOR = intPreferencesKey("custom_primary_color")
    private val CUSTOM_SECONDARY_COLOR = intPreferencesKey("custom_secondary_color")
    private val SHUTTER_SOUND_ENABLED = booleanPreferencesKey("shutter_sound_enabled")
    private val SHUTTER_SOUND_URI = stringPreferencesKey("shutter_sound_uri")
    private val WORLD_CLOCK_ZONES = stringSetPreferencesKey("world_clock_zones")
    
    // Notifications
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val STEP_NOTIFICATIONS = booleanPreferencesKey("step_notifications")
    private val TIMER_NOTIFICATIONS = booleanPreferencesKey("timer_notifications")
    private val VOICE_RECORD_NOTIFICATIONS = booleanPreferencesKey("voice_record_notifications")
    private val MUSIC_NOTIFICATIONS = booleanPreferencesKey("music_notifications")
    
    // Widget Design
    private val WIDGET_BACKGROUND_COLOR = intPreferencesKey("widget_background_color")
    private val WIDGET_ACCENT_COLOR = intPreferencesKey("widget_accent_color")
    private val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")

    // New Settings
    private val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    private val HAPTIC_INTENSITY = floatPreferencesKey("haptic_intensity")
    private val UNIT_SYSTEM = stringPreferencesKey("unit_system") // "METRIC", "IMPERIAL"
    private val SHOW_QIBLA = booleanPreferencesKey("show_qibla")

    // Music Player Settings
    private val MUSIC_AUDIO_FOCUS = booleanPreferencesKey("music_audio_focus")
    private val MUSIC_SHAKE_TO_SKIP = booleanPreferencesKey("music_shake_to_skip")
    private val MUSIC_PLAYBACK_SPEED = floatPreferencesKey("music_playback_speed")
    private val MUSIC_EQUALIZER_PRESET = stringPreferencesKey("music_equalizer_preset")
    private val SHOW_MUSIC_VISUALIZER = booleanPreferencesKey("show_music_visualizer")
    private val MUSIC_ART_SHAPE = stringPreferencesKey("music_art_shape") // "CIRCLE", "SQUARE"
    private val MUSIC_ROTATION_ENABLED = booleanPreferencesKey("music_rotation_enabled")
    private val MUSIC_PIP_ENABLED = booleanPreferencesKey("music_pip_enabled")

    // Step Counter Toggle
    private val STEP_COUNTER_ENABLED = booleanPreferencesKey("step_counter_enabled")

    // Universal Pill
    private val SHOW_TOOLZ_PILL = booleanPreferencesKey("show_toolz_pill")

    // Onboarding
    private val USER_NAME = stringPreferencesKey("user_name")
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    private val defaultAlarmUri: String by lazy {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: ""
    }
    
    private val defaultShutterUri: String by lazy {
        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.toString()
    }

    val stepGoal: Flow<Int> = dataStore.data.map { it[STEP_GOAL] ?: 10000 }
    val ringtoneUri: Flow<String?> = dataStore.data.map { it[RINGTONE_URI] ?: defaultAlarmUri }
    val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE] ?: "SYSTEM" }
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val customPrimaryColor: Flow<Int?> = dataStore.data.map { it[CUSTOM_PRIMARY_COLOR] }
    val customSecondaryColor: Flow<Int?> = dataStore.data.map { it[CUSTOM_SECONDARY_COLOR] }
    val shutterSoundEnabled: Flow<Boolean> = dataStore.data.map { it[SHUTTER_SOUND_ENABLED] ?: true }
    val shutterSoundUri: Flow<String?> = dataStore.data.map { it[SHUTTER_SOUND_URI] ?: defaultShutterUri }
    val worldClockZones: Flow<Set<String>> = dataStore.data.map { it[WORLD_CLOCK_ZONES] ?: setOf("UTC", "America/New_York", "Europe/London", "Asia/Tokyo") }

    // Notifications Flows
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val stepNotifications: Flow<Boolean> = dataStore.data.map { it[STEP_NOTIFICATIONS] ?: true }
    val timerNotifications: Flow<Boolean> = dataStore.data.map { it[TIMER_NOTIFICATIONS] ?: true }
    val voiceRecordNotifications: Flow<Boolean> = dataStore.data.map { it[VOICE_RECORD_NOTIFICATIONS] ?: true }
    val musicNotifications: Flow<Boolean> = dataStore.data.map { it[MUSIC_NOTIFICATIONS] ?: true }

    // Widget Flows
    val widgetBackgroundColor: Flow<Int> = dataStore.data.map { it[WIDGET_BACKGROUND_COLOR] ?: 0xFFFFFFFF.toInt() }
    val widgetAccentColor: Flow<Int> = dataStore.data.map { it[WIDGET_ACCENT_COLOR] ?: 0xFF4CAF50.toInt() }
    val widgetOpacity: Flow<Float> = dataStore.data.map { it[WIDGET_OPACITY] ?: 0.9f }

    // New Flows
    val hapticFeedback: Flow<Boolean> = dataStore.data.map { it[HAPTIC_FEEDBACK] ?: true }
    val hapticIntensity: Flow<Float> = dataStore.data.map { it[HAPTIC_INTENSITY] ?: 0.5f }
    val unitSystem: Flow<String> = dataStore.data.map { it[UNIT_SYSTEM] ?: "METRIC" }
    val showQibla: Flow<Boolean> = dataStore.data.map { it[SHOW_QIBLA] ?: false }

    // Music Flows
    val musicAudioFocus: Flow<Boolean> = dataStore.data.map { it[MUSIC_AUDIO_FOCUS] ?: true }
    val musicShakeToSkip: Flow<Boolean> = dataStore.data.map { it[MUSIC_SHAKE_TO_SKIP] ?: false }
    val musicPlaybackSpeed: Flow<Float> = dataStore.data.map { it[MUSIC_PLAYBACK_SPEED] ?: 1.0f }
    val musicEqualizerPreset: Flow<String> = dataStore.data.map { it[MUSIC_EQUALIZER_PRESET] ?: "Normal" }
    val showMusicVisualizer: Flow<Boolean> = dataStore.data.map { it[SHOW_MUSIC_VISUALIZER] ?: true }
    val musicArtShape: Flow<String> = dataStore.data.map { it[MUSIC_ART_SHAPE] ?: "CIRCLE" }
    val musicRotationEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_ROTATION_ENABLED] ?: true }
    val musicPipEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_PIP_ENABLED] ?: false }

    val stepCounterEnabled: Flow<Boolean> = dataStore.data.map { it[STEP_COUNTER_ENABLED] ?: true }

    val showToolzPill: Flow<Boolean> = dataStore.data.map { it[SHOW_TOOLZ_PILL] ?: true }
    val userName: Flow<String> = dataStore.data.map { it[USER_NAME] ?: "" }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }

    suspend fun setStepGoal(goal: Int) { dataStore.edit { it[STEP_GOAL] = goal } }
    suspend fun setRingtoneUri(uri: String) { dataStore.edit { it[RINGTONE_URI] = uri } }
    suspend fun setThemeMode(mode: String) { dataStore.edit { it[THEME_MODE] = mode } }
    suspend fun setDynamicColor(enabled: Boolean) { dataStore.edit { it[DYNAMIC_COLOR] = enabled } }
    suspend fun setCustomPrimaryColor(color: Int?) {
        dataStore.edit { 
            if (color == null) it.remove(CUSTOM_PRIMARY_COLOR) else it[CUSTOM_PRIMARY_COLOR] = color 
        }
    }
    suspend fun setCustomSecondaryColor(color: Int?) {
        dataStore.edit { 
            if (color == null) it.remove(CUSTOM_SECONDARY_COLOR) else it[CUSTOM_SECONDARY_COLOR] = color
        }
    }
    suspend fun setShutterSoundEnabled(enabled: Boolean) { dataStore.edit { it[SHUTTER_SOUND_ENABLED] = enabled } }
    suspend fun setShutterSoundUri(uri: String) { dataStore.edit { it[SHUTTER_SOUND_URI] = uri } }
    suspend fun addWorldClockZone(zone: String) { dataStore.edit { it[WORLD_CLOCK_ZONES] = (it[WORLD_CLOCK_ZONES] ?: emptySet()) + zone } }
    suspend fun removeWorldClockZone(zone: String) { dataStore.edit { it[WORLD_CLOCK_ZONES] = (it[WORLD_CLOCK_ZONES] ?: emptySet()) - zone } }

    // Notification setters
    suspend fun setNotificationsEnabled(enabled: Boolean) { dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled } }
    suspend fun setStepNotifications(enabled: Boolean) { dataStore.edit { it[STEP_NOTIFICATIONS] = enabled } }
    suspend fun setTimerNotifications(enabled: Boolean) { dataStore.edit { it[TIMER_NOTIFICATIONS] = enabled } }
    suspend fun setVoiceRecordNotifications(enabled: Boolean) { dataStore.edit { it[VOICE_RECORD_NOTIFICATIONS] = enabled } }
    suspend fun setMusicNotifications(enabled: Boolean) { dataStore.edit { it[MUSIC_NOTIFICATIONS] = enabled } }

    // Widget setters
    suspend fun setWidgetBackgroundColor(color: Int) { dataStore.edit { it[WIDGET_BACKGROUND_COLOR] = color } }
    suspend fun setWidgetAccentColor(color: Int) { dataStore.edit { it[WIDGET_ACCENT_COLOR] = color } }
    suspend fun setWidgetOpacity(opacity: Float) { dataStore.edit { it[WIDGET_OPACITY] = opacity } }

    // New Setters
    suspend fun setHapticFeedback(enabled: Boolean) { dataStore.edit { it[HAPTIC_FEEDBACK] = enabled } }
    suspend fun setHapticIntensity(intensity: Float) { dataStore.edit { it[HAPTIC_INTENSITY] = intensity } }
    suspend fun setUnitSystem(unit: String) { dataStore.edit { it[UNIT_SYSTEM] = unit } }
    suspend fun setShowQibla(enabled: Boolean) { dataStore.edit { it[SHOW_QIBLA] = enabled } }

    // Music Setters
    suspend fun setMusicAudioFocus(enabled: Boolean) { dataStore.edit { it[MUSIC_AUDIO_FOCUS] = enabled } }
    suspend fun setMusicShakeToSkip(enabled: Boolean) { dataStore.edit { it[MUSIC_SHAKE_TO_SKIP] = enabled } }
    suspend fun setMusicPlaybackSpeed(speed: Float) { dataStore.edit { it[MUSIC_PLAYBACK_SPEED] = speed } }
    suspend fun setMusicEqualizerPreset(preset: String) { dataStore.edit { it[MUSIC_EQUALIZER_PRESET] = preset } }
    suspend fun setShowMusicVisualizer(enabled: Boolean) { dataStore.edit { it[SHOW_MUSIC_VISUALIZER] = enabled } }
    suspend fun setMusicArtShape(shape: String) { dataStore.edit { it[MUSIC_ART_SHAPE] = shape } }
    suspend fun setMusicRotationEnabled(enabled: Boolean) { dataStore.edit { it[MUSIC_ROTATION_ENABLED] = enabled } }
    suspend fun setMusicPipEnabled(enabled: Boolean) { dataStore.edit { it[MUSIC_PIP_ENABLED] = enabled } }

    suspend fun setStepCounterEnabled(enabled: Boolean) { dataStore.edit { it[STEP_COUNTER_ENABLED] = enabled } }

    suspend fun setShowToolzPill(enabled: Boolean) { dataStore.edit { it[SHOW_TOOLZ_PILL] = enabled } }
    suspend fun setUserName(name: String) { dataStore.edit { it[USER_NAME] = name } }
    suspend fun setOnboardingCompleted(completed: Boolean) { dataStore.edit { it[ONBOARDING_COMPLETED] = completed } }
}
