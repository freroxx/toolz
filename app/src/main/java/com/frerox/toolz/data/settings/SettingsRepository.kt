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
    private val BACKGROUND_GRADIENT_ENABLED = booleanPreferencesKey("background_gradient_enabled")
    private val SHUTTER_SOUND_ENABLED = booleanPreferencesKey("shutter_sound_enabled")
    private val SHUTTER_SOUND_URI = stringPreferencesKey("shutter_sound_uri")
    private val WORLD_CLOCK_ZONES = stringSetPreferencesKey("world_clock_zones")
    
    // Dashboard View
    private val DASHBOARD_VIEW = stringPreferencesKey("dashboard_view") // "DEFAULT", "LIST"
    private val PINNED_TOOLS = stringSetPreferencesKey("pinned_tools")
    private val RECENT_TOOLS = stringSetPreferencesKey("recent_tools") // Stored as "timestamp:route"
    private val SHOW_RECENT_TOOLS = booleanPreferencesKey("show_recent_tools")
    private val SHOW_QUICK_NOTES = booleanPreferencesKey("show_quick_notes")

    // Notifications
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val NOTIFICATION_VAULT_ENABLED = booleanPreferencesKey("notification_vault_enabled")
    private val STEP_NOTIFICATIONS = booleanPreferencesKey("step_notifications")
    private val TIMER_NOTIFICATIONS = booleanPreferencesKey("timer_notifications")
    private val VOICE_RECORD_NOTIFICATIONS = booleanPreferencesKey("voice_record_notifications")
    private val MUSIC_NOTIFICATIONS = booleanPreferencesKey("music_notifications")
    private val NOTIFICATION_RETENTION_DAYS = intPreferencesKey("notification_retention_days")

    private val SEARCH_FIRST_TIME = booleanPreferencesKey("search_first_time")
    private val SEARCH_ADBLOCK_ENABLED = booleanPreferencesKey("search_adblock_enabled")
    private val SEARCH_DNS_PROVIDER = stringPreferencesKey("search_dns_provider") // "DEFAULT", "ADGUARD", "CLOUDFLARE", "GOOGLE", "CUSTOM"
    private val SEARCH_CUSTOM_DNS = stringPreferencesKey("search_custom_dns")
    private val SEARCH_RECENT_DNS = stringSetPreferencesKey("search_recent_dns")

    val searchFirstTime: Flow<Boolean> = dataStore.data.map { it[SEARCH_FIRST_TIME] ?: true }
    val searchAdBlockEnabled: Flow<Boolean> = dataStore.data.map { it[SEARCH_ADBLOCK_ENABLED] ?: true }
    val searchDnsProvider: Flow<String> = dataStore.data.map { it[SEARCH_DNS_PROVIDER] ?: "ADGUARD" }
    val searchCustomDns: Flow<String> = dataStore.data.map { it[SEARCH_CUSTOM_DNS] ?: "" }
    val searchRecentDns: Flow<Set<String>> = dataStore.data.map { it[SEARCH_RECENT_DNS] ?: emptySet() }

    suspend fun setSearchFirstTime(isFirstTime: Boolean) {
        dataStore.edit { it[SEARCH_FIRST_TIME] = isFirstTime }
    }

    suspend fun setSearchAdBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[SEARCH_ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setDnsProvider(provider: String) {
        dataStore.edit { it[SEARCH_DNS_PROVIDER] = provider }
    }

    suspend fun setCustomDns(dns: String) {
        dataStore.edit { it[SEARCH_CUSTOM_DNS] = dns }
        if (dns.isNotBlank()) {
            dataStore.edit { pref ->
                val current = pref[SEARCH_RECENT_DNS] ?: emptySet()
                val updated = (current + dns).toList()
                pref[SEARCH_RECENT_DNS] = if (updated.size > 5) updated.takeLast(5).toSet() else updated.toSet()
            }
        }
    }
    
    suspend fun removeRecentDns(dns: String) {
        dataStore.edit { pref ->
            val current = pref[SEARCH_RECENT_DNS] ?: emptySet()
            pref[SEARCH_RECENT_DNS] = current - dns
        }
    }
    private val HIDDEN_NOTIFICATION_APPS = stringSetPreferencesKey("hidden_notification_apps")
    private val CUSTOM_NOTIFICATION_CATEGORIES = stringSetPreferencesKey("custom_notification_categories")
    private val APP_CATEGORY_MAPPINGS = stringSetPreferencesKey("app_category_mappings") // List of "package:category"
    private val APP_NAME_MAPPINGS = stringSetPreferencesKey("app_name_mappings") // List of "package:customName"
    
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
    private val MUSIC_SHAKE_SENSITIVITY = floatPreferencesKey("music_shake_sensitivity")
    private val MUSIC_PLAYBACK_SPEED = floatPreferencesKey("music_playback_speed")
    private val MUSIC_EQUALIZER_PRESET = stringPreferencesKey("music_equalizer_preset")
    private val SHOW_MUSIC_VISUALIZER = booleanPreferencesKey("show_music_visualizer")
    private val MUSIC_ART_SHAPE = stringPreferencesKey("music_art_shape") // "CIRCLE", "SQUARE"
    private val MUSIC_ROTATION_ENABLED = booleanPreferencesKey("music_rotation_enabled")
    private val MUSIC_PIP_ENABLED = booleanPreferencesKey("music_pip_enabled")
    private val MUSIC_AI_ENABLED = booleanPreferencesKey("music_ai_enabled")
    private val MUSIC_KEEP_SCREEN_ON_LYRICS = booleanPreferencesKey("music_keep_screen_on_lyrics")
    private val MUSIC_LYRICS_LAYOUT = stringPreferencesKey("music_lyrics_layout") // "LEFT", "CENTER", "RIGHT"
    private val MUSIC_LYRICS_SEEK_ENABLED = booleanPreferencesKey("music_lyrics_seek_enabled")
    private val MUSIC_LYRICS_FONT = stringPreferencesKey("music_lyrics_font") // "SANS_SERIF", "SERIF", "MONOSPACE", "CURSIVE"

    // Performance Mode
    private val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")

    // Step Counter Toggle
    private val STEP_COUNTER_ENABLED = booleanPreferencesKey("step_counter_enabled")

    // Universal Pill
    private val SHOW_TOOLZ_PILL = booleanPreferencesKey("show_toolz_pill")
    private val FILL_THE_PILL_ENABLED = booleanPreferencesKey("fill_the_pill_enabled")
    private val PILL_TODO_ENABLED = booleanPreferencesKey("pill_todo_enabled")
    private val PILL_FOCUS_ENABLED = booleanPreferencesKey("pill_focus_enabled")

    // Onboarding
    private val USER_NAME = stringPreferencesKey("user_name")
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val CATALOG_ONBOARDING_COMPLETED = booleanPreferencesKey("catalog_onboarding_completed")
    private val SHOW_CATALOG_BETA_CARD = booleanPreferencesKey("show_catalog_beta_card")

    // Download Settings
    private val DOWNLOAD_FORMAT = stringPreferencesKey("download_format") // "M4A", "OPUS", "MP3"
    private val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality") // "HIGH", "MEDIUM", "LOW"

    // Timer Duration Persistence
    private val LAST_TIMER_MINUTES = intPreferencesKey("last_timer_minutes")
    private val LAST_TIMER_SECONDS = intPreferencesKey("last_timer_seconds")

    // Update System
    private val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
    private val DOWNLOADED_APK_PATH = stringPreferencesKey("downloaded_apk_path")
    private val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
    private val UPDATE_AVAILABLE_VERSION = stringPreferencesKey("update_available_version")
    private val UPDATE_CHANGELOG = stringPreferencesKey("update_changelog")
    private val UPDATE_APK_URL = stringPreferencesKey("update_apk_url")
    private val PREFERRED_ABI = stringPreferencesKey("preferred_abi") // "AUTO", "armeabi-v7a", "arm64-v8a", "x86", "x86_64"

    // AI Focus Custom Instructions
    private val FOCUS_AI_CUSTOM_INSTRUCTIONS = stringPreferencesKey("focus_ai_custom_instructions")

    // Converter Settings
    private val CONVERTER_CUSTOM_OUTPUT_PATH = stringPreferencesKey("converter_custom_output_path")

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
    val backgroundGradientEnabled: Flow<Boolean> = dataStore.data.map { it[BACKGROUND_GRADIENT_ENABLED] ?: true }
    val shutterSoundEnabled: Flow<Boolean> = dataStore.data.map { it[SHUTTER_SOUND_ENABLED] ?: true }
    val shutterSoundUri: Flow<String?> = dataStore.data.map { it[SHUTTER_SOUND_URI] ?: defaultShutterUri }
    val worldClockZones: Flow<Set<String>> = dataStore.data.map { it[WORLD_CLOCK_ZONES] ?: setOf("UTC", "America/New_York", "Europe/London", "Asia/Tokyo") }
    
    val dashboardView: Flow<String> = dataStore.data.map { it[DASHBOARD_VIEW] ?: "DEFAULT" }
    val pinnedTools: Flow<Set<String>> = dataStore.data.map { it[PINNED_TOOLS] ?: emptySet() }
    val recentTools: Flow<List<String>> = dataStore.data.map { pref ->
        val current = pref[RECENT_TOOLS] ?: emptySet()
        current.asSequence()
            .map { it.split(":", limit = 2) }
            .filter { it.size == 2 }
            .sortedByDescending { it[0].toLongOrNull() ?: 0L }
            .map { it[1] }
            .distinct()
            .take(5)
            .toList()
    }
    val showRecentTools: Flow<Boolean> = dataStore.data.map { it[SHOW_RECENT_TOOLS] ?: true }
    val showQuickNotes: Flow<Boolean> = dataStore.data.map { it[SHOW_QUICK_NOTES] ?: true }

    // Notifications Flows
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val notificationVaultEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATION_VAULT_ENABLED] ?: true }
    val stepNotifications: Flow<Boolean> = dataStore.data.map { it[STEP_NOTIFICATIONS] ?: true }
    val timerNotifications: Flow<Boolean> = dataStore.data.map { it[TIMER_NOTIFICATIONS] ?: true }
    val voiceRecordNotifications: Flow<Boolean> = dataStore.data.map { it[VOICE_RECORD_NOTIFICATIONS] ?: true }
    val musicNotifications: Flow<Boolean> = dataStore.data.map { it[MUSIC_NOTIFICATIONS] ?: true }
    val notificationRetentionDays: Flow<Int> = dataStore.data.map { it[NOTIFICATION_RETENTION_DAYS] ?: 30 }
    val hiddenNotificationApps: Flow<Set<String>> = dataStore.data.map { it[HIDDEN_NOTIFICATION_APPS] ?: emptySet() }
    val customNotificationCategories: Flow<Set<String>> = dataStore.data.map { it[CUSTOM_NOTIFICATION_CATEGORIES] ?: setOf("Social", "Finance", "Work", "General") }
    val appCategoryMappings: Flow<Map<String, String>> = dataStore.data.map { pref ->
        pref[APP_CATEGORY_MAPPINGS]?.associate { 
            val parts = it.split(":")
            parts[0] to parts.getOrElse(1) { "General" }
        } ?: emptyMap()
    }
    val appNameMappings: Flow<Map<String, String>> = dataStore.data.map { pref ->
        pref[APP_NAME_MAPPINGS]?.associate {
            val idx = it.indexOf(":")
            if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else it to it
        } ?: emptyMap()
    }

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
    val musicShakeSensitivity: Flow<Float> = dataStore.data.map { it[MUSIC_SHAKE_SENSITIVITY] ?: 0.3f }
    val musicPlaybackSpeed: Flow<Float> = dataStore.data.map { it[MUSIC_PLAYBACK_SPEED] ?: 1.0f }
    val musicEqualizerPreset: Flow<String> = dataStore.data.map { it[MUSIC_EQUALIZER_PRESET] ?: "Normal" }
    val showMusicVisualizer: Flow<Boolean> = dataStore.data.map { it[SHOW_MUSIC_VISUALIZER] ?: true }
    val musicArtShape: Flow<String> = dataStore.data.map { it[MUSIC_ART_SHAPE] ?: "CIRCLE" }
    val musicRotationEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_ROTATION_ENABLED] ?: true }
    val musicPipEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_PIP_ENABLED] ?: false }
    val musicAiEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_AI_ENABLED] ?: true }
    val musicKeepScreenOnLyrics: Flow<Boolean> = dataStore.data.map { it[MUSIC_KEEP_SCREEN_ON_LYRICS] ?: true }
    val musicLyricsLayout: Flow<String> = dataStore.data.map { it[MUSIC_LYRICS_LAYOUT] ?: "RIGHT" }
    val musicLyricsSeekEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_LYRICS_SEEK_ENABLED] ?: false }
    val musicLyricsFont: Flow<String> = dataStore.data.map { it[MUSIC_LYRICS_FONT] ?: "SANS_SERIF" }

    val performanceMode: Flow<Boolean> = dataStore.data.map { it[PERFORMANCE_MODE] ?: false }

    val stepCounterEnabled: Flow<Boolean> = dataStore.data.map { it[STEP_COUNTER_ENABLED] ?: false }

    val showToolzPill: Flow<Boolean> = dataStore.data.map { it[SHOW_TOOLZ_PILL] ?: true }
    val fillThePillEnabled: Flow<Boolean> = dataStore.data.map { it[FILL_THE_PILL_ENABLED] ?: true }
    val pillTodoEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_TODO_ENABLED] ?: true }
    val pillFocusEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_FOCUS_ENABLED] ?: false }

    val userName: Flow<String> = dataStore.data.map { it[USER_NAME] ?: "" }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }
    val catalogOnboardingCompleted: Flow<Boolean> = dataStore.data.map { it[CATALOG_ONBOARDING_COMPLETED] ?: false }
    val showCatalogBetaCard: Flow<Boolean> = dataStore.data.map { it[SHOW_CATALOG_BETA_CARD] ?: true }

    val downloadFormat: Flow<String> = dataStore.data.map { it[DOWNLOAD_FORMAT] ?: "M4A" }
    val downloadQuality: Flow<String> = dataStore.data.map { it[DOWNLOAD_QUALITY] ?: "HIGH" }

    val lastTimerMinutes: Flow<Int> = dataStore.data.map { it[LAST_TIMER_MINUTES] ?: 0 }
    val lastTimerSeconds: Flow<Int> = dataStore.data.map { it[LAST_TIMER_SECONDS] ?: 0 }

    val lastUpdateCheck: Flow<Long> = dataStore.data.map { it[LAST_UPDATE_CHECK] ?: 0L }
    val downloadedApkPath: Flow<String?> = dataStore.data.map { it[DOWNLOADED_APK_PATH] }
    val autoUpdateEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_UPDATE_ENABLED] ?: false }
    val updateAvailableVersion: Flow<String?> = dataStore.data.map { it[UPDATE_AVAILABLE_VERSION] }
    val updateChangelog: Flow<String?> = dataStore.data.map { it[UPDATE_CHANGELOG] }
    val updateApkUrl: Flow<String?> = dataStore.data.map { it[UPDATE_APK_URL] }
    val preferredAbi: Flow<String> = dataStore.data.map { it[PREFERRED_ABI] ?: "AUTO" }

    val focusAiCustomInstructions: Flow<String> = dataStore.data.map { it[FOCUS_AI_CUSTOM_INSTRUCTIONS] ?: "" }

    val converterCustomOutputPath: Flow<String?> = dataStore.data.map { it[CONVERTER_CUSTOM_OUTPUT_PATH] }

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
    suspend fun setBackgroundGradientEnabled(enabled: Boolean) { dataStore.edit { it[BACKGROUND_GRADIENT_ENABLED] = enabled } }
    suspend fun setShutterSoundEnabled(enabled: Boolean) { dataStore.edit { it[SHUTTER_SOUND_ENABLED] = enabled } }
    suspend fun setShutterSoundUri(uri: String) { dataStore.edit { it[SHUTTER_SOUND_URI] = uri } }
    suspend fun addWorldClockZone(zone: String) { dataStore.edit { it[WORLD_CLOCK_ZONES] = (it[WORLD_CLOCK_ZONES] ?: emptySet()) + zone } }
    suspend fun removeWorldClockZone(zone: String) { dataStore.edit { it[WORLD_CLOCK_ZONES] = (it[WORLD_CLOCK_ZONES] ?: emptySet()) - zone } }
    
    suspend fun setDashboardView(view: String) { dataStore.edit { it[DASHBOARD_VIEW] = view } }
    suspend fun togglePinnedTool(route: String) {
        dataStore.edit { pref ->
            val current = pref[PINNED_TOOLS] ?: emptySet()
            pref[PINNED_TOOLS] = if (current.contains(route)) current - route else current + route
        }
    }

    suspend fun addRecentTool(route: String) {
        dataStore.edit { pref ->
            val current = pref[RECENT_TOOLS] ?: emptySet()
            val timestamp = System.currentTimeMillis()
            val filtered = current.filterNot { it.endsWith(":$route") }.toSet()
            pref[RECENT_TOOLS] = filtered + "$timestamp:$route"
        }
    }
    
    suspend fun setShowRecentTools(enabled: Boolean) { dataStore.edit { it[SHOW_RECENT_TOOLS] = enabled } }
    suspend fun setShowQuickNotes(enabled: Boolean) { dataStore.edit { it[SHOW_QUICK_NOTES] = enabled } }

    // Notification setters
    suspend fun setNotificationsEnabled(enabled: Boolean) { dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled } }
    suspend fun setNotificationVaultEnabled(enabled: Boolean) { dataStore.edit { it[NOTIFICATION_VAULT_ENABLED] = enabled } }
    suspend fun setStepNotifications(enabled: Boolean) { dataStore.edit { it[STEP_NOTIFICATIONS] = enabled } }
    suspend fun setTimerNotifications(enabled: Boolean) { dataStore.edit { it[TIMER_NOTIFICATIONS] = enabled } }
    suspend fun setVoiceRecordNotifications(enabled: Boolean) { dataStore.edit { it[VOICE_RECORD_NOTIFICATIONS] = enabled } }
    suspend fun setMusicNotifications(enabled: Boolean) { dataStore.edit { it[MUSIC_NOTIFICATIONS] = enabled } }
    suspend fun setNotificationRetentionDays(days: Int) { dataStore.edit { it[NOTIFICATION_RETENTION_DAYS] = days } }
    suspend fun addHiddenNotificationApp(packageName: String) { dataStore.edit { it[HIDDEN_NOTIFICATION_APPS] = (it[HIDDEN_NOTIFICATION_APPS] ?: emptySet()) + packageName } }
    suspend fun removeHiddenNotificationApp(packageName: String) { dataStore.edit { it[HIDDEN_NOTIFICATION_APPS] = (it[HIDDEN_NOTIFICATION_APPS] ?: emptySet()) - packageName } }
    suspend fun setNotificationCategories(categories: Set<String>) { dataStore.edit { it[CUSTOM_NOTIFICATION_CATEGORIES] = categories } }
    suspend fun setAppCategoryMapping(packageName: String, category: String) {
        dataStore.edit { pref ->
            val current = pref[APP_CATEGORY_MAPPINGS] ?: emptySet()
            val filtered = current.filterNot { it.startsWith("$packageName:") }.toSet()
            pref[APP_CATEGORY_MAPPINGS] = filtered + "$packageName:$category"
        }
    }

    suspend fun removeAppCategoryMapping(packageName: String) {
        dataStore.edit { pref ->
            val current = pref[APP_CATEGORY_MAPPINGS] ?: emptySet()
            pref[APP_CATEGORY_MAPPINGS] = current.filterNot { it.startsWith("$packageName:") }.toSet()
        }
    }

    suspend fun setAppNameMapping(packageName: String, customName: String) {
        dataStore.edit { pref ->
            val current = pref[APP_NAME_MAPPINGS] ?: emptySet()
            val filtered = current.filterNot { it.startsWith("$packageName:") }.toSet()
            pref[APP_NAME_MAPPINGS] = filtered + "$packageName:$customName"
        }
    }

    suspend fun removeAppNameMapping(packageName: String) {
        dataStore.edit { pref ->
            val current = pref[APP_NAME_MAPPINGS] ?: emptySet()
            pref[APP_NAME_MAPPINGS] = current.filterNot { it.startsWith("$packageName:") }.toSet()
        }
    }

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
    suspend fun setMusicShakeSensitivity(sensitivity: Float) { dataStore.edit { it[MUSIC_SHAKE_SENSITIVITY] = sensitivity } }
    suspend fun setMusicPlaybackSpeed(speed: Float) { dataStore.edit { it[MUSIC_PLAYBACK_SPEED] = speed } }
    suspend fun setMusicEqualizerPreset(preset: String) { dataStore.edit { it[MUSIC_EQUALIZER_PRESET] = preset } }
    suspend fun setShowMusicVisualizer(enabled: Boolean) { dataStore.edit { it[SHOW_MUSIC_VISUALIZER] = enabled } }
    suspend fun setMusicArtShape(shape: String) { dataStore.edit { it[MUSIC_ART_SHAPE] = shape } }
    suspend fun setMusicRotationEnabled(enabled: Boolean) { dataStore.edit { it[MUSIC_ROTATION_ENABLED] = enabled } }
    suspend fun setMusicPipEnabled(enabled: Boolean) { dataStore.edit { it[MUSIC_PIP_ENABLED] = enabled } }
    suspend fun setMusicAiEnabled(enabled: Boolean) { dataStore.edit { it[MUSIC_AI_ENABLED] = enabled } }
    suspend fun setMusicKeepScreenOnLyrics(enabled: Boolean) { dataStore.edit { it[MUSIC_KEEP_SCREEN_ON_LYRICS] = enabled } }
    suspend fun setMusicLyricsLayout(layout: String) { dataStore.edit { it[MUSIC_LYRICS_LAYOUT] = layout } }
    suspend fun setMusicLyricsSeekEnabled(enabled: Boolean) { dataStore.edit { it[MUSIC_LYRICS_SEEK_ENABLED] = enabled } }
    suspend fun setMusicLyricsFont(font: String) { dataStore.edit { it[MUSIC_LYRICS_FONT] = font } }

    suspend fun setPerformanceMode(enabled: Boolean) { dataStore.edit { it[PERFORMANCE_MODE] = enabled } }

    suspend fun setStepCounterEnabled(enabled: Boolean) { dataStore.edit { it[STEP_COUNTER_ENABLED] = enabled } }

    suspend fun setShowToolzPill(enabled: Boolean) { dataStore.edit { it[SHOW_TOOLZ_PILL] = enabled } }
    suspend fun setFillThePillEnabled(enabled: Boolean) { dataStore.edit { it[FILL_THE_PILL_ENABLED] = enabled } }
    suspend fun setPillTodoEnabled(enabled: Boolean) { dataStore.edit { it[PILL_TODO_ENABLED] = enabled } }
    suspend fun setPillFocusEnabled(enabled: Boolean) { dataStore.edit { it[PILL_FOCUS_ENABLED] = enabled } }

    suspend fun setUserName(name: String) { dataStore.edit { it[USER_NAME] = name } }
    suspend fun setOnboardingCompleted(completed: Boolean) { dataStore.edit { it[ONBOARDING_COMPLETED] = completed } }
    suspend fun setCatalogOnboardingCompleted(completed: Boolean) { dataStore.edit { it[CATALOG_ONBOARDING_COMPLETED] = completed } }
    suspend fun setShowCatalogBetaCard(show: Boolean) { dataStore.edit { it[SHOW_CATALOG_BETA_CARD] = show } }

    suspend fun setDownloadFormat(format: String) { dataStore.edit { it[DOWNLOAD_FORMAT] = format } }
    suspend fun setDownloadQuality(quality: String) { dataStore.edit { it[DOWNLOAD_QUALITY] = quality } }

    suspend fun setLastTimerDuration(minutes: Int, seconds: Int) {
        dataStore.edit {
            it[LAST_TIMER_MINUTES] = minutes
            it[LAST_TIMER_SECONDS] = seconds
        }
    }

    suspend fun setLastUpdateCheck(timestamp: Long) { dataStore.edit { it[LAST_UPDATE_CHECK] = timestamp } }
    suspend fun setDownloadedApkPath(path: String?) {
        dataStore.edit {
            if (path == null) it.remove(DOWNLOADED_APK_PATH) else it[DOWNLOADED_APK_PATH] = path
        }
    }
    suspend fun setAutoUpdateEnabled(enabled: Boolean) { dataStore.edit { it[AUTO_UPDATE_ENABLED] = enabled } }
    
    suspend fun setAvailableUpdate(version: String?, changelog: String?, apkUrl: String?) {
        dataStore.edit { pref ->
            version?.let { pref[UPDATE_AVAILABLE_VERSION] = it } ?: pref.remove(UPDATE_AVAILABLE_VERSION)
            changelog?.let { pref[UPDATE_CHANGELOG] = it } ?: pref.remove(UPDATE_CHANGELOG)
            apkUrl?.let { pref[UPDATE_APK_URL] = it } ?: pref.remove(UPDATE_APK_URL)
        }
    }

    suspend fun setPreferredAbi(abi: String) { dataStore.edit { it[PREFERRED_ABI] = abi } }

    suspend fun setFocusAiCustomInstructions(instructions: String) {
        dataStore.edit { it[FOCUS_AI_CUSTOM_INSTRUCTIONS] = instructions }
    }

    suspend fun setConverterCustomOutputPath(path: String?) {
        dataStore.edit {
            if (path == null) it.remove(CONVERTER_CUSTOM_OUTPUT_PATH) else it[CONVERTER_CUSTOM_OUTPUT_PATH] = path
        }
    }

    suspend fun resetOnboarding() {
        dataStore.edit {
            it.remove(USER_NAME)
            it.remove(ONBOARDING_COMPLETED)
        }
    }
}
