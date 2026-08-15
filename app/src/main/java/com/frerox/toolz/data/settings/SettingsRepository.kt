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

package com.frerox.toolz.data.settings

import android.media.RingtoneManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    
    // Caffeinate
    private val CAFFEINATE_AUTO_ALL_APPS = booleanPreferencesKey("caffeinate_auto_all_apps")
    private val CAFFEINATE_AUTO_SUMMARY_NOTIFICATION = booleanPreferencesKey("caffeinate_auto_summary_notification")
    private val ACCESSIBILITY_BRIDGE_WAS_ACTIVE = booleanPreferencesKey("accessibility_bridge_was_active")
    
    // Timer & Stopwatch Settings
    private val TIMER_KEEP_SCREEN_ON = booleanPreferencesKey("timer_keep_screen_on")
    private val STOPWATCH_KEEP_SCREEN_ON = booleanPreferencesKey("stopwatch_keep_screen_on")
    private val TIMER_GRADUAL_VOLUME = booleanPreferencesKey("timer_gradual_volume")
    private val POMODORO_GRADUAL_VOLUME = booleanPreferencesKey("pomodoro_gradual_volume")
    
    // Dashboard View
    private val DASHBOARD_VIEW = stringPreferencesKey("dashboard_view") // "DEFAULT", "LIST"
    private val PINNED_TOOLS = stringSetPreferencesKey("pinned_tools")
    private val RECENT_TOOLS = stringSetPreferencesKey("recent_tools") // Stored as "timestamp:route"
    private val SHOW_RECENT_TOOLS = booleanPreferencesKey("show_recent_tools")
    private val SHOW_QUICK_NOTES = booleanPreferencesKey("show_quick_notes")
    private val SHOW_DASHBOARD_STATS = booleanPreferencesKey("show_dashboard_stats")

    // Notifications
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    private val NOTIFICATION_VAULT_ENABLED = booleanPreferencesKey("notification_vault_enabled")
    private val STEP_NOTIFICATIONS = booleanPreferencesKey("step_notifications")
    private val TIMER_NOTIFICATIONS = booleanPreferencesKey("timer_notifications")
    private val VOICE_RECORD_NOTIFICATIONS = booleanPreferencesKey("voice_record_notifications")
    private val MUSIC_NOTIFICATIONS = booleanPreferencesKey("music_notifications")
    private val FILE_CONVERSION_NOTIFICATIONS = booleanPreferencesKey("file_conversion_notifications")
    private val APP_UPDATE_NOTIFICATIONS = booleanPreferencesKey("app_update_notifications")
    private val TASK_REMINDER_NOTIFICATIONS = booleanPreferencesKey("task_reminder_notifications")
    private val EVENT_REMINDER_NOTIFICATIONS = booleanPreferencesKey("event_reminder_notifications")
    private val FLASHLIGHT_NOTIFICATIONS = booleanPreferencesKey("flashlight_notifications")
    private val POMODORO_NOTIFICATIONS = booleanPreferencesKey("pomodoro_notifications")
    private val BACKUP_NOTIFICATIONS = booleanPreferencesKey("backup_notifications")
    private val KARAOKE_ENABLED = booleanPreferencesKey("karaoke_enabled")
    private val NOTIFICATION_RETENTION_DAYS = intPreferencesKey("notification_retention_days")

    // Pomodoro Settings
    private val POMODORO_WORK_MINUTES = intPreferencesKey("pomodoro_work_minutes")
    private val POMODORO_SHORT_BREAK_MINUTES = intPreferencesKey("pomodoro_short_break_minutes")
    private val POMODORO_LONG_BREAK_MINUTES = intPreferencesKey("pomodoro_long_break_minutes")
    private val POMODORO_AUTO_START = booleanPreferencesKey("pomodoro_auto_start")
    private val POMODORO_KEEP_SCREEN_ON = booleanPreferencesKey("pomodoro_keep_screen_on")
    private val POMODORO_SESSIONS_GOAL = intPreferencesKey("pomodoro_sessions_goal")
    private val POMODORO_SESSIONS_COMPLETED = intPreferencesKey("pomodoro_sessions_completed")
    private val POMODORO_RINGTONE_URI = stringPreferencesKey("pomodoro_ringtone_uri")
    private val POMODORO_SHOW_QUOTES = booleanPreferencesKey("pomodoro_show_quotes")
    private val POMODORO_QUOTES = stringPreferencesKey("pomodoro_quotes")

    private val CUSTOM_RINGTONE_ENABLED = booleanPreferencesKey("custom_ringtone_enabled")
    private val CUSTOM_RINGTONE_URI = stringPreferencesKey("custom_ringtone_uri")

    private val SEARCH_FIRST_TIME = booleanPreferencesKey("search_first_time")
    private val SEARCH_ADBLOCK_ENABLED = booleanPreferencesKey("search_adblock_enabled")
    private val SEARCH_ADBLOCK_BLOCKLISTS = stringSetPreferencesKey("search_adblock_blocklists")
    private val SEARCH_ADBLOCK_ALLOWLISTS = stringSetPreferencesKey("search_adblock_allowlists")
    private val SEARCH_NEXTDNS_ID = stringPreferencesKey("search_nextdns_id")
    private val SEARCH_NEXTDNS_DNS_URL = stringPreferencesKey("search_nextdns_dns_url")
    private val SEARCH_ENABLED_IMPORTED_LISTS = stringSetPreferencesKey("search_enabled_imported_lists")
    private val SEARCH_ADBLOCK_IMPORTED_COUNT = intPreferencesKey("search_adblock_imported_count")
    private val SEARCH_FLOATING_TOOLBAR_VISIBLE = booleanPreferencesKey("search_floating_toolbar_visible")
    private val SEARCH_DNS_PROVIDER = stringPreferencesKey("search_dns_provider") // "DEFAULT", "ADGUARD", "CLOUDFLARE", "GOOGLE", "CUSTOM"
    private val SEARCH_CUSTOM_DNS = stringPreferencesKey("search_custom_dns")
    private val SEARCH_CUSTOM_DNS_SECONDARY = stringPreferencesKey("search_custom_dns_secondary")
    private val SEARCH_RECENT_DNS = stringSetPreferencesKey("search_recent_dns")
    private val SEARCH_ENGINE = stringPreferencesKey("search_engine") // "GOOGLE", "BING", "DUCKDUCKGO"
    private val SEARCH_SAFE_SEARCH = booleanPreferencesKey("search_safe_search")
    private val SEARCH_REGION = stringPreferencesKey("search_region")
    private val SEARCH_CUSTOM_ENGINE_URL = stringPreferencesKey("search_custom_engine_url")
    private val SEARCH_INCOGNITO_ENABLED = booleanPreferencesKey("search_incognito_enabled")

    val searchFirstTime: Flow<Boolean> = dataStore.data.map { it[SEARCH_FIRST_TIME] ?: true }
    val searchAdBlockEnabled: Flow<Boolean> = dataStore.data.map { it[SEARCH_ADBLOCK_ENABLED] ?: true }
    val searchAdBlockBlocklists: Flow<Set<String>> = dataStore.data.map { it[SEARCH_ADBLOCK_BLOCKLISTS] ?: emptySet() }
    val searchAdBlockAllowlists: Flow<Set<String>> = dataStore.data.map { it[SEARCH_ADBLOCK_ALLOWLISTS] ?: emptySet() }
    val searchNextDnsId: Flow<String> = dataStore.data.map { it[SEARCH_NEXTDNS_ID] ?: "" }
    val searchNextDnsDnsUrl: Flow<String> = dataStore.data.map { it[SEARCH_NEXTDNS_DNS_URL] ?: "" }
    val searchEnabledImportedLists: Flow<Set<String>> = dataStore.data.map { it[SEARCH_ENABLED_IMPORTED_LISTS] ?: emptySet() }
    val searchAdBlockImportedCount: Flow<Int> = dataStore.data.map { it[SEARCH_ADBLOCK_IMPORTED_COUNT] ?: 0 }
    val searchFloatingToolbarVisible: Flow<Boolean> = dataStore.data.map { it[SEARCH_FLOATING_TOOLBAR_VISIBLE] ?: true }
    val searchDnsProvider: Flow<String> = dataStore.data.map { it[SEARCH_DNS_PROVIDER] ?: "ADGUARD" }
    val searchCustomDns: Flow<String> = dataStore.data.map { it[SEARCH_CUSTOM_DNS] ?: "" }
    val searchCustomDnsSecondary: Flow<String> = dataStore.data.map { it[SEARCH_CUSTOM_DNS_SECONDARY] ?: "" }
    val searchRecentDns: Flow<Set<String>> = dataStore.data.map { it[SEARCH_RECENT_DNS] ?: emptySet() }
    val searchEngine: Flow<String> = dataStore.data.map { it[SEARCH_ENGINE] ?: "DUCKDUCKGO" }
    val searchSafeSearch: Flow<Boolean> = dataStore.data.map { it[SEARCH_SAFE_SEARCH] ?: true }
    val searchRegion: Flow<String> = dataStore.data.map { it[SEARCH_REGION] ?: "wt-wt" } // default: no region
    val searchCustomEngineUrl: Flow<String> = dataStore.data.map { it[SEARCH_CUSTOM_ENGINE_URL] ?: "" }
    val searchIncognitoEnabled: Flow<Boolean> = dataStore.data.map { it[SEARCH_INCOGNITO_ENABLED] ?: false }

    companion object {
        val SEARCH_AUTOFILL_ENABLED = booleanPreferencesKey("search_autofill_enabled")
        val LAST_BIOMETRIC_VERIFICATION_TIME = longPreferencesKey("last_biometric_verification_time")

        val DEFAULT_POMODORO_QUOTES = """
            "And the universe said I love you because you are love." (The End Poem, Minecraft)
            "It is possible to commit no mistakes and still lose. That is not weakness, that is life." (Captain Picard, Star Trek)
            "All we have to decide is what to do with the time that is given us." (Gandalf, The Lord of the Rings)
            "It gets easier. Every day it gets a little easier. But you gotta do it every day — that’s the hard part. But it does get easier." (The Jogging Baboon, BoJack Horseman)
            "What is better — to be born good, or to overcome your evil nature through great effort?" (Paarthurnax, Skyrim)
            "In the midst of winter, I found there was, within me, an invincible summer." (Albert Camus)
            "You have power over your mind — not outside events. Realize this, and you will find strength." (Marcus Aurelius, Meditations)
            "The first principle is that you must not fool yourself, and you are the easiest person to fool." (Richard Feynman)
            "Somewhere, something incredible is waiting to be known." (Carl Sagan)
            "Tell me, what is it you plan to do with your one wild and precious life?" (Mary Oliver)
            "We are all in the gutter, but some of us are looking at the stars." (Oscar Wilde)
            "I have loved the stars too fondly to be fearful of the night." (Sarah Williams)
            "A man chooses, a slave obeys." (Andrew Ryan, BioShock)
            "We choose to go to the Moon in this decade and do the other things, not because they are easy, but because they are hard." (John F. Kennedy)
            "The cosmos is within us. We are made of star-stuff. We are a way for the universe to know itself." (Carl Sagan)
            "Do not pray for an easy life, pray for the strength to endure a difficult one." (Bruce Lee)
            "Even in the darkest times, hope is something you give yourself. That is the meaning of inner strength." (Iroh, Avatar: The Airbender)
            "The monsters turned out to be just regular people." (Cheryl Mason, Silent Hill 3)
            "Time you enjoy wasting is not wasted time." (Marthe Troly-Curtin)
            "You are the player. Wake up." (The End Poem, Minecraft)
            "The mystery of life isn't a problem to solve, but a reality to experience." (Frank Herbert, Dune)
            "To live is the rarest thing in the world. Most people exist, that is all." (Oscar Wilde)
            "I must not fear. Fear is the mind-killer." (Bene Gesserit Litany, Dune)
            "The world is not beautiful; therefore, it is." (Kino, Kino's Journey)
            "Don't ever think you're nothing. There's always a place for you, somewhere." (Sora, Kingdom Hearts)
        """.trimIndent()
    }

    val searchAutofillEnabled: Flow<Boolean> = dataStore.data.map { it[SEARCH_AUTOFILL_ENABLED] ?: true }
    val lastBiometricVerificationTime: Flow<Long> = dataStore.data.map { it[LAST_BIOMETRIC_VERIFICATION_TIME] ?: 0L }

    suspend fun setSearchAutofillEnabled(enabled: Boolean) {
        dataStore.edit { it[SEARCH_AUTOFILL_ENABLED] = enabled }
    }

    suspend fun setLastBiometricVerificationTime(time: Long) {
        dataStore.edit { it[LAST_BIOMETRIC_VERIFICATION_TIME] = time }
    }

    suspend fun setSearchFirstTime(isFirstTime: Boolean) {
        dataStore.edit { it[SEARCH_FIRST_TIME] = isFirstTime }
    }

    suspend fun setSearchAdBlockEnabled(enabled: Boolean) {
        dataStore.edit { it[SEARCH_ADBLOCK_ENABLED] = enabled }
    }

    suspend fun setSearchAdBlockBlocklists(blocklists: Set<String>) {
        dataStore.edit { it[SEARCH_ADBLOCK_BLOCKLISTS] = blocklists }
    }

    suspend fun setSearchAdBlockAllowlists(allowlists: Set<String>) {
        dataStore.edit { it[SEARCH_ADBLOCK_ALLOWLISTS] = allowlists }
    }

    suspend fun setSearchNextDnsId(id: String) {
        dataStore.edit { it[SEARCH_NEXTDNS_ID] = id }
    }

    suspend fun setSearchNextDnsDnsUrl(url: String) {
        dataStore.edit { it[SEARCH_NEXTDNS_DNS_URL] = url }
    }

    suspend fun setSearchEnabledImportedLists(lists: Set<String>) {
        dataStore.edit { it[SEARCH_ENABLED_IMPORTED_LISTS] = lists }
    }

    suspend fun setSearchAdBlockImportedCount(count: Int) {
        dataStore.edit { it[SEARCH_ADBLOCK_IMPORTED_COUNT] = count }
    }

    suspend fun setSearchFloatingToolbarVisible(visible: Boolean) {
        dataStore.edit { it[SEARCH_FLOATING_TOOLBAR_VISIBLE] = visible }
    }

    suspend fun setDnsProvider(provider: String) {
        dataStore.edit { it[SEARCH_DNS_PROVIDER] = provider }
    }

    suspend fun setSearchEngine(engine: String) {
        dataStore.edit { it[SEARCH_ENGINE] = engine }
    }

    suspend fun setSearchSafeSearch(enabled: Boolean) {
        dataStore.edit { it[SEARCH_SAFE_SEARCH] = enabled }
    }

    suspend fun setSearchRegion(region: String) {
        dataStore.edit { it[SEARCH_REGION] = region }
    }

    suspend fun setSearchCustomEngineUrl(url: String) {
        dataStore.edit { it[SEARCH_CUSTOM_ENGINE_URL] = url }
    }

    suspend fun setSearchIncognitoEnabled(enabled: Boolean) {
        dataStore.edit { it[SEARCH_INCOGNITO_ENABLED] = enabled }
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
    
    suspend fun setCustomDnsSecondary(dns: String) {
        dataStore.edit { it[SEARCH_CUSTOM_DNS_SECONDARY] = dns }
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
    private val SHOW_QIBLA = booleanPreferencesKey("show_qibla")

    // Music Player Settings
    private val MUSIC_AUDIO_FOCUS = booleanPreferencesKey("music_audio_focus")
    private val MUSIC_AUDIO_FOCUS_DUCKING = booleanPreferencesKey("music_audio_focus_ducking")
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
    private val MUSIC_LYRICS_FONT = stringPreferencesKey("music_lyrics_font") // "SANS_SERIF", "SERIF", "MONOSPACE", "CURSIVE", "DISPLAY", "HANDWRITING"
    private val MUSIC_LYRICS_ALWAYS_SYNC = booleanPreferencesKey("music_lyrics_always_sync")
    private val MUSIC_LYRICS_WORD_SYNC_ENABLED = booleanPreferencesKey("music_lyrics_word_sync_enabled")
    private val KARAOKE_WORD_SYNC_ENABLED = booleanPreferencesKey("karaoke_word_sync_enabled")
    private val KARAOKE_SING_CONFIDENTLY_ENABLED = booleanPreferencesKey("karaoke_sing_confidently_enabled")
    private val KARAOKE_SING_CONFIDENTLY_MODE = stringPreferencesKey("karaoke_sing_confidently_mode")
    private val KARAOKE_SPEECH_CORRECTION_ENABLED = booleanPreferencesKey("karaoke_speech_correction_enabled")
    private val KARAOKE_QUICK_SING_ENABLED = booleanPreferencesKey("karaoke_quick_sing_enabled")
    private val KARAOKE_AUTO_RECORD_ENABLED = booleanPreferencesKey("karaoke_auto_record_enabled")
    private val MUSIC_VISUALIZER_SENSITIVITY = floatPreferencesKey("music_visualizer_sensitivity")
    private val MUSIC_VISUALIZER_AUTO_SENSITIVITY = booleanPreferencesKey("music_visualizer_auto_sensitivity")
    private val MUSIC_CUSTOM_EQUALIZER = stringPreferencesKey("music_custom_equalizer") // JSON or list of gains
    private val MUSIC_SLEEP_TIMER_LAST_CUSTOM_MINUTES = intPreferencesKey("music_sleep_timer_last_custom_minutes")
    private val MUSIC_LAST_PLAYED_URI = stringPreferencesKey("music_last_played_uri")
    private val MUSIC_LAST_PLAYED_POSITION = longPreferencesKey("music_last_played_position")
    private val MUSIC_LAST_PLAYED_QUEUE = stringPreferencesKey("music_last_played_queue") // JSON array of URIs

    // Performance Mode
    private val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode")
    private val SHOW_TOP_APP_BAR_DESCRIPTIONS = booleanPreferencesKey("show_top_app_bar_descriptions")

    // Step Counter Toggle
    private val STEP_COUNTER_ENABLED = booleanPreferencesKey("step_counter_enabled")
    private val STEP_HISTORY_RETENTION = stringPreferencesKey("step_history_retention")
    private val AI_FITNESS_AGENT_ENABLED = booleanPreferencesKey("ai_fitness_agent_enabled")
    private val AI_FITNESS_AGENT_PROVIDER = stringPreferencesKey("ai_fitness_agent_provider")
    private val AI_FITNESS_AGENT_MODEL = stringPreferencesKey("ai_fitness_agent_model")
    private val AI_FITNESS_AGENT_TONE = stringPreferencesKey("ai_fitness_agent_tone")
    private val AI_FITNESS_AGENT_MOOD = stringPreferencesKey("ai_fitness_agent_mood")
    private val AI_FITNESS_AGENT_STYLE = stringPreferencesKey("ai_fitness_agent_style")
    private val STEP_LENGTH_CM = intPreferencesKey("step_length_cm")
    private val CALORIES_PER_1000_STEPS = intPreferencesKey("calories_per_1000_steps")
    private val MEASUREMENT_SYSTEM = stringPreferencesKey("measurement_system")
    private val STEP_USE_GPS = booleanPreferencesKey("step_use_gps")
    private val STEP_BATTERY_SAVE = booleanPreferencesKey("step_battery_save")
    private val STEP_SENSITIVITY = intPreferencesKey("step_sensitivity")
    private val STEP_ENGINE_MODE = stringPreferencesKey("step_engine_mode")
    private val LAST_OS_STEP_COUNT = longPreferencesKey("last_os_step_count")

    // Universal Pill
    private val SHOW_TOOLZ_PILL = booleanPreferencesKey("show_toolz_pill")
    private val FILL_THE_PILL_ENABLED = booleanPreferencesKey("fill_the_pill_enabled")
    private val PILL_TODO_ENABLED = booleanPreferencesKey("pill_todo_enabled")
    private val PILL_FOCUS_ENABLED = booleanPreferencesKey("pill_focus_enabled")
    private val PILL_MUSIC_ENABLED = booleanPreferencesKey("pill_music_enabled")
    private val PILL_TIMER_ENABLED = booleanPreferencesKey("pill_timer_enabled")
    private val PILL_STOPWATCH_ENABLED = booleanPreferencesKey("pill_stopwatch_enabled")
    private val PILL_POMODORO_ENABLED = booleanPreferencesKey("pill_pomodoro_enabled")
    private val PILL_STEPS_ENABLED = booleanPreferencesKey("pill_steps_enabled")
    private val PILL_RECORDER_ENABLED = booleanPreferencesKey("pill_recorder_enabled")
    private val PILL_CAFFEINATE_ENABLED = booleanPreferencesKey("pill_caffeinate_enabled")
    private val PILL_FLASHLIGHT_ENABLED = booleanPreferencesKey("pill_flashlight_enabled")
    private val PILL_CATALOG_DOWNLOAD_ENABLED = booleanPreferencesKey("pill_catalog_download_enabled")
    private val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
    private val AUTO_BACKUP_CUSTOM_DAYS = intPreferencesKey("auto_backup_custom_days")

    // Onboarding
    private val USER_NAME = stringPreferencesKey("user_name")
    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    private val APP_LANGUAGE = stringPreferencesKey("app_language")
    private val CATALOG_ONBOARDING_COMPLETED = booleanPreferencesKey("catalog_onboarding_completed")
    private val WHISPER_BETA_WARNING_SHOWN = booleanPreferencesKey("whisper_beta_warning_shown")
    private val WHISPER_ONBOARDING_SHOWN = booleanPreferencesKey("whisper_onboarding_shown")
    private val SHOW_CATALOG_BETA_CARD = booleanPreferencesKey("show_catalog_beta_card")
    private val ACTIVE_DOWNLOAD_JSON = stringPreferencesKey("active_download_json")

    private val LIVE_VPN_NOTIFICATIONS = booleanPreferencesKey("live_vpn_notifications")
    private val LIVE_DNS_NOTIFICATIONS = booleanPreferencesKey("live_dns_notifications")

    val liveVpnNotifications: Flow<Boolean> = dataStore.data.map { it[LIVE_VPN_NOTIFICATIONS] ?: true }
    val liveDnsNotifications: Flow<Boolean> = dataStore.data.map { it[LIVE_DNS_NOTIFICATIONS] ?: false }

    val caffeinateAutoAllApps: Flow<Boolean> = dataStore.data.map { it[CAFFEINATE_AUTO_ALL_APPS] ?: false }
    val caffeinateAutoSummaryNotification: Flow<Boolean> = dataStore.data.map { it[CAFFEINATE_AUTO_SUMMARY_NOTIFICATION] ?: true }
    val accessibilityBridgeWasActive: Flow<Boolean> = dataStore.data.map { it[ACCESSIBILITY_BRIDGE_WAS_ACTIVE] ?: false }

    suspend fun setLiveVpnNotifications(enabled: Boolean) {
        dataStore.edit { it[LIVE_VPN_NOTIFICATIONS] = enabled }
    }

    suspend fun setLiveDnsNotifications(enabled: Boolean) {
        dataStore.edit { it[LIVE_DNS_NOTIFICATIONS] = enabled }
    }

    suspend fun setCaffeinateAutoAllApps(enabled: Boolean) {
        dataStore.edit { it[CAFFEINATE_AUTO_ALL_APPS] = enabled }
    }

    suspend fun setCaffeinateAutoSummaryNotification(enabled: Boolean) {
        dataStore.edit { it[CAFFEINATE_AUTO_SUMMARY_NOTIFICATION] = enabled }
    }

    suspend fun setAccessibilityBridgeWasActive(active: Boolean) {
        dataStore.edit { it[ACCESSIBILITY_BRIDGE_WAS_ACTIVE] = active }
    }

    // Download Settings
    private val DOWNLOAD_FORMAT = stringPreferencesKey("download_format") // "M4A", "OPUS", "MP3"
    private val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality") // "HIGH", "MEDIUM", "LOW"
    private val CATALOG_STREAM_QUALITY = stringPreferencesKey("catalog_stream_quality") // "AUTO", "HIGH", "MEDIUM", "LOW"

    // Timer Duration Persistence
    private val LAST_TIMER_MINUTES = intPreferencesKey("last_timer_minutes")
    private val LAST_TIMER_SECONDS = intPreferencesKey("last_timer_seconds")
    private val TIMER_HISTORY = stringPreferencesKey("timer_history")  // JSON: "min:sec" -> count
    private val LOCKED_TIMER_PRESETS = stringPreferencesKey("locked_timer_presets") // JSON: List of "min:sec"

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

    // PDF Settings
    private val PDF_AI_OCR_ENHANCE = booleanPreferencesKey("pdf_ai_ocr_enhance")

    // AI Search
    private val AI_SEARCH_ENABLED = booleanPreferencesKey("ai_search_enabled")
    private val AI_SEARCH_ICON_VISIBLE = booleanPreferencesKey("ai_search_icon_visible")
    private val AI_SEARCH_CHAT_ENABLED = booleanPreferencesKey("ai_search_chat_enabled")

    // Offline Mode
    private val OFFLINE_MODE_ENABLED = booleanPreferencesKey("offline_mode_enabled")

    // Focus Flow Session Active (Blocks distractions)
    private val FOCUS_FLOW_SESSION_ACTIVE = booleanPreferencesKey("focus_flow_session_active")

    // AI Clipboard Monitoring
    private val AI_CLIPBOARD_MONITORING = booleanPreferencesKey("ai_clipboard_monitoring")

    // Crypto Settings
    private val CRYPTO_LAST_ALGORITHM = stringPreferencesKey("crypto_last_algorithm")

    // Loading Screen Persistence
    private val LAST_LOADING_TIME = longPreferencesKey("last_loading_time")

    private val NETWORK_BENCHMARK_SERVERS = stringSetPreferencesKey("network_benchmark_servers")
    private val NETWORK_LAST_TRACE_TARGET = stringPreferencesKey("network_last_trace_target")
    private val NETWORK_AUTO_CONNECT_SHIZUKU = booleanPreferencesKey("network_auto_connect_shizuku")
    private val NETWORK_DISCLAIMER_SHOWN = booleanPreferencesKey("network_disclaimer_shown")

    // BMI Persistence
    private val BMI_HEIGHT = stringPreferencesKey("bmi_height")
    private val BMI_WEIGHT = stringPreferencesKey("bmi_weight")
    private val BMI_AGE = stringPreferencesKey("bmi_age")
    private val BMI_GENDER = stringPreferencesKey("bmi_gender")
    private val BMI_ACTIVITY = stringPreferencesKey("bmi_activity")
    private val BMI_IS_KG = booleanPreferencesKey("bmi_is_kg")
    private val BMI_IS_CM = booleanPreferencesKey("bmi_is_cm")

    // Flip Coin Settings
    private val FLIP_COIN_HEADS_IMAGE_URI = stringPreferencesKey("flip_coin_heads_image_uri")
    private val FLIP_COIN_TAILS_IMAGE_URI = stringPreferencesKey("flip_coin_tails_image_uri")

    private val defaultAlarmUri: String by lazy {
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString() ?: ""
    }
    
    private val defaultShutterUri: String by lazy {
        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.toString()
    }

    val offlineModeEnabled: Flow<Boolean> = dataStore.data.map { it[OFFLINE_MODE_ENABLED] ?: false }

    val focusFlowSessionActive: Flow<Boolean> = dataStore.data.map { it[FOCUS_FLOW_SESSION_ACTIVE] ?: false }

    val stepGoal: Flow<Int> = dataStore.data.map { 
        try {
            it[STEP_GOAL] ?: 10000
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("step_goal")]?.toIntOrNull()) ?: 10000
        }
    }
    val ringtoneUri: Flow<String?> = dataStore.data.map { it[RINGTONE_URI] ?: defaultAlarmUri }
    val themeMode: Flow<String> = dataStore.data.map { it[THEME_MODE] ?: "SYSTEM" }
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[DYNAMIC_COLOR] ?: true }
    val customPrimaryColor: Flow<Int?> = dataStore.data.map { 
        try {
            it[CUSTOM_PRIMARY_COLOR]
        } catch (e: ClassCastException) {
            it[stringPreferencesKey("custom_primary_color")]?.toIntOrNull()
        }
    }
    val customSecondaryColor: Flow<Int?> = dataStore.data.map { 
        try {
            it[CUSTOM_SECONDARY_COLOR]
        } catch (e: ClassCastException) {
            it[stringPreferencesKey("custom_secondary_color")]?.toIntOrNull()
        }
    }
    val backgroundGradientEnabled: Flow<Boolean> = dataStore.data.map { it[BACKGROUND_GRADIENT_ENABLED] ?: true }
    val shutterSoundEnabled: Flow<Boolean> = dataStore.data.map { it[SHUTTER_SOUND_ENABLED] ?: true }
    val shutterSoundUri: Flow<String?> = dataStore.data.map { it[SHUTTER_SOUND_URI] ?: defaultShutterUri }
    val worldClockZones: Flow<Set<String>> = dataStore.data.map { it[WORLD_CLOCK_ZONES] ?: setOf("UTC", "America/New_York", "Europe/London", "Asia/Tokyo") }
    
    val timerKeepScreenOn: Flow<Boolean> = dataStore.data.map { it[TIMER_KEEP_SCREEN_ON] ?: true }
    val stopwatchKeepScreenOn: Flow<Boolean> = dataStore.data.map { it[STOPWATCH_KEEP_SCREEN_ON] ?: true }
    val timerGradualVolume: Flow<Boolean> = dataStore.data.map { it[TIMER_GRADUAL_VOLUME] ?: false }
    val pomodoroGradualVolume: Flow<Boolean> = dataStore.data.map { it[POMODORO_GRADUAL_VOLUME] ?: false }

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
    val showDashboardStats: Flow<Boolean> = dataStore.data.map { it[SHOW_DASHBOARD_STATS] ?: true }

    // Notifications Flows
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val notificationVaultEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATION_VAULT_ENABLED] ?: true }
    val stepNotifications: Flow<Boolean> = dataStore.data.map { it[STEP_NOTIFICATIONS] ?: true }
    val timerNotifications: Flow<Boolean> = dataStore.data.map { it[TIMER_NOTIFICATIONS] ?: true }
    val voiceRecordNotifications: Flow<Boolean> = dataStore.data.map { it[VOICE_RECORD_NOTIFICATIONS] ?: true }
    val musicNotifications: Flow<Boolean> = dataStore.data.map { it[MUSIC_NOTIFICATIONS] ?: true }
    val fileConversionNotifications: Flow<Boolean> = dataStore.data.map { it[FILE_CONVERSION_NOTIFICATIONS] ?: true }
    val appUpdateNotifications: Flow<Boolean> = dataStore.data.map { it[APP_UPDATE_NOTIFICATIONS] ?: true }
    val taskReminderNotifications: Flow<Boolean> = dataStore.data.map { it[TASK_REMINDER_NOTIFICATIONS] ?: true }
    val eventReminderNotifications: Flow<Boolean> = dataStore.data.map { it[EVENT_REMINDER_NOTIFICATIONS] ?: true }
    val pomodoroNotifications: Flow<Boolean> = dataStore.data.map { it[POMODORO_NOTIFICATIONS] ?: true }
    val backupNotifications: Flow<Boolean> = dataStore.data.map { it[BACKUP_NOTIFICATIONS] ?: true }
    val flashlightNotificationsEnabled: Flow<Boolean> = dataStore.data.map { it[FLASHLIGHT_NOTIFICATIONS] ?: true }
    val karaokeEnabled: Flow<Boolean> = dataStore.data.map { it[KARAOKE_ENABLED] ?: true }
    val notificationRetentionDays: Flow<Int> = dataStore.data.map { 
        try {
            it[NOTIFICATION_RETENTION_DAYS] ?: 30
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("notification_retention_days")]?.toIntOrNull()) ?: 30
        }
    }
    
    // Pomodoro Flows
    val pomodoroWorkMinutes: Flow<Int> = dataStore.data.map { 
        try {
            it[POMODORO_WORK_MINUTES] ?: 25
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("pomodoro_work_minutes")]?.toIntOrNull()) ?: 25
        }
    }
    val pomodoroShortBreakMinutes: Flow<Int> = dataStore.data.map { 
        try {
            it[POMODORO_SHORT_BREAK_MINUTES] ?: 5
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("pomodoro_short_break_minutes")]?.toIntOrNull()) ?: 5
        }
    }
    val pomodoroLongBreakMinutes: Flow<Int> = dataStore.data.map { 
        try {
            it[POMODORO_LONG_BREAK_MINUTES] ?: 15
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("pomodoro_long_break_minutes")]?.toIntOrNull()) ?: 15
        }
    }
    val pomodoroAutoStart: Flow<Boolean> = dataStore.data.map { it[POMODORO_AUTO_START] ?: false }
    val pomodoroKeepScreenOn: Flow<Boolean> = dataStore.data.map { it[POMODORO_KEEP_SCREEN_ON] ?: true }
    val pomodoroSessionsGoal: Flow<Int> = dataStore.data.map { 
        try {
            it[POMODORO_SESSIONS_GOAL] ?: 8
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("pomodoro_sessions_goal")]?.toIntOrNull()) ?: 8
        }
    }
    val pomodoroSessionsCompleted: Flow<Int> = dataStore.data.map { 
        try {
            it[POMODORO_SESSIONS_COMPLETED] ?: 0
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("pomodoro_sessions_completed")]?.toIntOrNull()) ?: 0
        }
    }
    val pomodoroRingtoneUri: Flow<String?> = dataStore.data.map { it[POMODORO_RINGTONE_URI] ?: defaultAlarmUri }
    val pomodoroShowQuotes: Flow<Boolean> = dataStore.data.map { it[POMODORO_SHOW_QUOTES] ?: true }
    val pomodoroQuotes: Flow<String> = dataStore.data.map { it[POMODORO_QUOTES] ?: DEFAULT_POMODORO_QUOTES }

    val customRingtoneEnabled: Flow<Boolean> = dataStore.data.map { it[CUSTOM_RINGTONE_ENABLED] ?: false }
    val customRingtoneUri: Flow<String?> = dataStore.data.map { it[CUSTOM_RINGTONE_URI] }

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
    val widgetBackgroundColor: Flow<Int> = dataStore.data.map { 
        try {
            it[WIDGET_BACKGROUND_COLOR] ?: 0xFFFFFFFF.toInt()
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("widget_background_color")]?.toIntOrNull()) ?: 0xFFFFFFFF.toInt()
        }
    }
    val widgetAccentColor: Flow<Int> = dataStore.data.map { 
        try {
            it[WIDGET_ACCENT_COLOR] ?: 0xFF4CAF50.toInt()
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("widget_accent_color")]?.toIntOrNull()) ?: 0xFF4CAF50.toInt()
        }
    }
    val widgetOpacity: Flow<Float> = dataStore.data.map { it[WIDGET_OPACITY] ?: 0.9f }

    // New Flows
    val hapticFeedback: Flow<Boolean> = dataStore.data.map { it[HAPTIC_FEEDBACK] ?: true }
    val hapticIntensity: Flow<Float> = dataStore.data.map { it[HAPTIC_INTENSITY] ?: 0.5f }
    val showQibla: Flow<Boolean> = dataStore.data.map { it[SHOW_QIBLA] ?: false }

    // Music Flows
    val musicAudioFocus: Flow<Boolean> = dataStore.data.map { it[MUSIC_AUDIO_FOCUS] ?: true }
    val musicAudioFocusDucking: Flow<Boolean> = dataStore.data.map { it[MUSIC_AUDIO_FOCUS_DUCKING] ?: false }
    val musicShakeToSkip: Flow<Boolean> = dataStore.data.map { it[MUSIC_SHAKE_TO_SKIP] ?: false }
    val musicShakeSensitivity: Flow<Float> = dataStore.data.map { it[MUSIC_SHAKE_SENSITIVITY] ?: 0.3f }
    val musicPlaybackSpeed: Flow<Float> = dataStore.data.map { it[MUSIC_PLAYBACK_SPEED] ?: 1.0f }
    val musicEqualizerPreset: Flow<String> = dataStore.data.map { it[MUSIC_EQUALIZER_PRESET] ?: "Normal" }
    val showMusicVisualizer: Flow<Boolean> = dataStore.data.map { it[SHOW_MUSIC_VISUALIZER] ?: false }
    val musicArtShape: Flow<String> = dataStore.data.map { it[MUSIC_ART_SHAPE] ?: "SQUARE" }
    val musicRotationEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_ROTATION_ENABLED] ?: false }
    val musicPipEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_PIP_ENABLED] ?: false }

    val musicAiEnabled: Flow<Boolean> = combine(
        dataStore.data.map { it[MUSIC_AI_ENABLED] ?: true },
        offlineModeEnabled
    ) { enabled, offline -> if (offline) false else enabled }
    val musicKeepScreenOnLyrics: Flow<Boolean> = dataStore.data.map { it[MUSIC_KEEP_SCREEN_ON_LYRICS] ?: true }
    val musicLyricsLayout: Flow<String> = dataStore.data.map { it[MUSIC_LYRICS_LAYOUT] ?: "LEFT" }
    val musicLyricsSeekEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_LYRICS_SEEK_ENABLED] ?: true }
    val musicLyricsFont: Flow<String> = dataStore.data.map { it[MUSIC_LYRICS_FONT] ?: "SANS_SERIF" }
    val musicLyricsAlwaysSync: Flow<Boolean> = dataStore.data.map { it[MUSIC_LYRICS_ALWAYS_SYNC] ?: true }
    val musicLyricsWordSyncEnabled: Flow<Boolean> = dataStore.data.map { it[MUSIC_LYRICS_WORD_SYNC_ENABLED] ?: true }
    val karaokeWordSyncEnabled: Flow<Boolean> = dataStore.data.map { it[KARAOKE_WORD_SYNC_ENABLED] ?: true }
    val karaokeSingConfidentlyEnabled: Flow<Boolean> = dataStore.data.map { it[KARAOKE_SING_CONFIDENTLY_ENABLED] ?: false }
    // Mode supersedes the legacy boolean. Default is AUTO.
    val karaokeSingConfidentlyMode: Flow<String> = dataStore.data.map {
        it[KARAOKE_SING_CONFIDENTLY_MODE] ?: "AUTO"
    }
    val karaokeSpeechCorrectionEnabled: Flow<Boolean> = dataStore.data.map { it[KARAOKE_SPEECH_CORRECTION_ENABLED] ?: false }
    val karaokeQuickSingEnabled: Flow<Boolean> = dataStore.data.map { it[KARAOKE_QUICK_SING_ENABLED] ?: true }
    val karaokeAutoRecordEnabled: Flow<Boolean> = dataStore.data.map { it[KARAOKE_AUTO_RECORD_ENABLED] ?: true }
    val musicVisualizerSensitivity: Flow<Float> = dataStore.data.map { it[MUSIC_VISUALIZER_SENSITIVITY] ?: 1.0f }
    val musicVisualizerAutoSensitivity: Flow<Boolean> = dataStore.data.map { it[MUSIC_VISUALIZER_AUTO_SENSITIVITY] ?: true }
    val musicSleepTimerLastCustomMinutes: Flow<Int> = dataStore.data.map { it[MUSIC_SLEEP_TIMER_LAST_CUSTOM_MINUTES] ?: 20 }
    val musicLastPlayedUri: Flow<String?> = dataStore.data.map { it[MUSIC_LAST_PLAYED_URI] }
    val musicLastPlayedPosition: Flow<Long> = dataStore.data.map { it[MUSIC_LAST_PLAYED_POSITION] ?: 0L }
    val musicLastPlayedQueue: Flow<String?> = dataStore.data.map { it[MUSIC_LAST_PLAYED_QUEUE] }
    val musicCustomEqualizer: Flow<String> = dataStore.data.map { it[MUSIC_CUSTOM_EQUALIZER] ?: "" }

    val performanceMode: Flow<Boolean> = dataStore.data.map { it[PERFORMANCE_MODE] ?: false }
    val showTopAppBarDescriptions: Flow<Boolean> = dataStore.data.map { it[SHOW_TOP_APP_BAR_DESCRIPTIONS] ?: false }

    val stepCounterEnabled: Flow<Boolean> = dataStore.data.map { it[STEP_COUNTER_ENABLED] ?: false }
    val stepHistoryRetention: Flow<String> = dataStore.data.map { it[STEP_HISTORY_RETENTION] ?: "Forever" }
    val aiFitnessAgentEnabled: Flow<Boolean> = dataStore.data.map { it[AI_FITNESS_AGENT_ENABLED] ?: false }
    val aiFitnessAgentProvider: Flow<String> = dataStore.data.map { it[AI_FITNESS_AGENT_PROVIDER] ?: "Gemini" }
    val aiFitnessAgentModel: Flow<String> = dataStore.data.map { it[AI_FITNESS_AGENT_MODEL] ?: "gemini-3.0-flash" }
    val aiFitnessAgentTone: Flow<String> = dataStore.data.map { it[AI_FITNESS_AGENT_TONE] ?: "Professional" }
    val aiFitnessAgentMood: Flow<String> = dataStore.data.map { it[AI_FITNESS_AGENT_MOOD] ?: "Encouraging" }
    val aiFitnessAgentStyle: Flow<String> = dataStore.data.map { it[AI_FITNESS_AGENT_STYLE] ?: "Concise" }
    val stepLengthCm: Flow<Int> = dataStore.data.map { 
        try {
            it[STEP_LENGTH_CM] ?: 75
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("step_length_cm")]?.toIntOrNull()) ?: 75
        }
    }
    val caloriesPer1000Steps: Flow<Int> = dataStore.data.map { 
        try {
            it[CALORIES_PER_1000_STEPS] ?: 40
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("calories_per_1000_steps")]?.toIntOrNull()) ?: 40
        }
    }
    val measurementSystem: Flow<String> = dataStore.data.map { it[MEASUREMENT_SYSTEM] ?: "Metric" }
    val stepUseGps: Flow<Boolean> = dataStore.data.map { it[STEP_USE_GPS] ?: false }
    val stepBatterySave: Flow<Boolean> = dataStore.data.map { it[STEP_BATTERY_SAVE] ?: true }
    val stepSensitivity: Flow<Int> = dataStore.data.map { 
        try {
            it[STEP_SENSITIVITY] ?: 50
        } catch (e: ClassCastException) {
            // Migration: handle old string value if exists
            (it[stringPreferencesKey("step_sensitivity")]?.toIntOrNull()) ?: 50
        }
    }
    val stepEngineMode: Flow<String> = dataStore.data.map { it[STEP_ENGINE_MODE] ?: "SIMPLE" }
    val lastOsStepCount: Flow<Long> = dataStore.data.map { it[LAST_OS_STEP_COUNT] ?: -1L }

    val showToolzPill: Flow<Boolean> = dataStore.data.map { it[SHOW_TOOLZ_PILL] ?: true }
    val fillThePillEnabled: Flow<Boolean> = dataStore.data.map { it[FILL_THE_PILL_ENABLED] ?: false }
    val pillTodoEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_TODO_ENABLED] ?: true }
    val pillFocusEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_FOCUS_ENABLED] ?: true }
    val pillMusicEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_MUSIC_ENABLED] ?: true }
    val pillTimerEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_TIMER_ENABLED] ?: true }
    val pillStopwatchEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_STOPWATCH_ENABLED] ?: true }
    val pillPomodoroEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_POMODORO_ENABLED] ?: true }
    val pillStepsEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_STEPS_ENABLED] ?: true }
    val pillRecorderEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_RECORDER_ENABLED] ?: true }
    val pillCaffeinateEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_CAFFEINATE_ENABLED] ?: true }
    val pillFlashlightEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_FLASHLIGHT_ENABLED] ?: true }
    val pillCatalogDownloadEnabled: Flow<Boolean> = dataStore.data.map { it[PILL_CATALOG_DOWNLOAD_ENABLED] ?: true }
    val backupFrequency: Flow<String> = dataStore.data.map { it[BACKUP_FREQUENCY] ?: "Never" }
    val autoBackupCustomDays: Flow<Int> = dataStore.data.map { 
        try {
            it[AUTO_BACKUP_CUSTOM_DAYS] ?: 1
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("auto_backup_custom_days")]?.toIntOrNull()) ?: 1
        }
    }

    val userName: Flow<String> = dataStore.data.map { it[USER_NAME] ?: "" }
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }
    val appLanguage: Flow<String> = dataStore.data.map { it[APP_LANGUAGE] ?: "en" }
    val catalogOnboardingCompleted: Flow<Boolean> = dataStore.data.map { it[CATALOG_ONBOARDING_COMPLETED] ?: false }
    val whisperBetaWarningShown: Flow<Boolean> = dataStore.data.map { it[WHISPER_BETA_WARNING_SHOWN] ?: false }
    val whisperOnboardingShown: Flow<Boolean> = dataStore.data.map { it[WHISPER_ONBOARDING_SHOWN] ?: false }
    val showCatalogBetaCard: Flow<Boolean> = dataStore.data.map { it[SHOW_CATALOG_BETA_CARD] ?: true }
    val activeDownloadJson: Flow<String?> = dataStore.data.map { it[ACTIVE_DOWNLOAD_JSON] }

    val downloadFormat: Flow<String> = dataStore.data.map { it[DOWNLOAD_FORMAT] ?: "M4A" }
    val downloadQuality: Flow<String> = dataStore.data.map { it[DOWNLOAD_QUALITY] ?: "HIGH" }
    val catalogStreamQuality: Flow<String> = dataStore.data.map { it[CATALOG_STREAM_QUALITY] ?: "AUTO" }

    val lastTimerMinutes: Flow<Int> = dataStore.data.map { 
        try {
            it[LAST_TIMER_MINUTES] ?: 0
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("last_timer_minutes")]?.toIntOrNull()) ?: 0
        }
    }
    val lastTimerSeconds: Flow<Int> = dataStore.data.map { 
        try {
            it[LAST_TIMER_SECONDS] ?: 0
        } catch (e: ClassCastException) {
            (it[stringPreferencesKey("last_timer_seconds")]?.toIntOrNull()) ?: 0
        }
    }

    val timerHistory: Flow<Map<String, Int>> = dataStore.data.map { prefs ->
        val json = prefs[TIMER_HISTORY] ?: "{}"
        parseTimerHistoryJson(json)
    }

    val lockedTimerPresets: Flow<List<String>> = dataStore.data.map { prefs ->
        val json = prefs[LOCKED_TIMER_PRESETS] ?: "[]"
        parseLockedPresetsJson(json)
    }

    val lastUpdateCheck: Flow<Long> = dataStore.data.map { it[LAST_UPDATE_CHECK] ?: 0L }
    val downloadedApkPath: Flow<String?> = dataStore.data.map { it[DOWNLOADED_APK_PATH] }
    val autoUpdateEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_UPDATE_ENABLED] ?: false }
    val updateAvailableVersion: Flow<String?> = dataStore.data.map { it[UPDATE_AVAILABLE_VERSION] }
    val updateChangelog: Flow<String?> = dataStore.data.map { it[UPDATE_CHANGELOG] }
    val updateApkUrl: Flow<String?> = dataStore.data.map { it[UPDATE_APK_URL] }
    val preferredAbi: Flow<String> = dataStore.data.map { it[PREFERRED_ABI] ?: "AUTO" }

    val focusAiCustomInstructions: Flow<String> = dataStore.data.map { it[FOCUS_AI_CUSTOM_INSTRUCTIONS] ?: "" }

    val converterCustomOutputPath: Flow<String?> = dataStore.data.map { it[CONVERTER_CUSTOM_OUTPUT_PATH] }

    val pdfAiOcrEnhance: Flow<Boolean> = combine(
        dataStore.data.map { it[PDF_AI_OCR_ENHANCE] ?: false },
        offlineModeEnabled
    ) { enabled, offline -> if (offline) false else enabled }

    val aiSearchEnabled: Flow<Boolean> = combine(
        dataStore.data.map { it[AI_SEARCH_ENABLED] ?: false },
        offlineModeEnabled
    ) { enabled, offline -> if (offline) false else enabled }

    val aiSearchChatEnabled: Flow<Boolean> = combine(
        dataStore.data.map { it[AI_SEARCH_CHAT_ENABLED] ?: false },
        offlineModeEnabled
    ) { enabled, offline -> if (offline) false else enabled }

    val aiSearchIconVisible: Flow<Boolean> = dataStore.data.map { it[AI_SEARCH_ICON_VISIBLE] ?: false }

    val aiClipboardMonitoringEnabled: Flow<Boolean> = combine(
        dataStore.data.map { it[AI_CLIPBOARD_MONITORING] ?: false },
        offlineModeEnabled
    ) { enabled, offline -> if (offline) false else enabled }

    val lastCryptoAlgorithm: Flow<String?> = dataStore.data.map { it[CRYPTO_LAST_ALGORITHM] }

    val lastLoadingTime: Flow<Long> = dataStore.data.map { it[LAST_LOADING_TIME] ?: 0L }

    val networkBenchmarkServers: Flow<Set<String>> = dataStore.data.map {
        it[NETWORK_BENCHMARK_SERVERS] ?: setOf("cloudflare", "google", "quad9", "adguard")
    }
    val networkLastTraceTarget: Flow<String> = dataStore.data.map { it[NETWORK_LAST_TRACE_TARGET] ?: "1.1.1.1" }
    val networkAutoConnectShizuku: Flow<Boolean> = dataStore.data.map { it[NETWORK_AUTO_CONNECT_SHIZUKU] ?: true }
    val networkDisclaimerShown: Flow<Boolean> = dataStore.data.map { it[NETWORK_DISCLAIMER_SHOWN] ?: false }

    // BMI Flows
    val bmiHeight: Flow<String> = dataStore.data.map { it[BMI_HEIGHT] ?: "" }
    val bmiWeight: Flow<String> = dataStore.data.map { it[BMI_WEIGHT] ?: "" }
    val bmiAge: Flow<String> = dataStore.data.map { it[BMI_AGE] ?: "" }
    val bmiGender: Flow<String> = dataStore.data.map { it[BMI_GENDER] ?: "MALE" }
    val bmiActivity: Flow<String> = dataStore.data.map { it[BMI_ACTIVITY] ?: "SEDENTARY" }
    val bmiIsKg: Flow<Boolean> = dataStore.data.map { it[BMI_IS_KG] ?: true }
    val bmiIsCm: Flow<Boolean> = dataStore.data.map { it[BMI_IS_CM] ?: true }

    val flipCoinHeadsImageUri: Flow<String?> = dataStore.data.map { it[FLIP_COIN_HEADS_IMAGE_URI] }
    val flipCoinTailsImageUri: Flow<String?> = dataStore.data.map { it[FLIP_COIN_TAILS_IMAGE_URI] }

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
    
    suspend fun setTimerKeepScreenOn(enabled: Boolean) { dataStore.edit { it[TIMER_KEEP_SCREEN_ON] = enabled } }
    suspend fun setStopwatchKeepScreenOn(enabled: Boolean) { dataStore.edit { it[STOPWATCH_KEEP_SCREEN_ON] = enabled } }
    suspend fun setTimerGradualVolume(enabled: Boolean) { dataStore.edit { it[TIMER_GRADUAL_VOLUME] = enabled } }
    suspend fun setPomodoroGradualVolume(enabled: Boolean) { dataStore.edit { it[POMODORO_GRADUAL_VOLUME] = enabled } }

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
    suspend fun setShowDashboardStats(enabled: Boolean) { dataStore.edit { it[SHOW_DASHBOARD_STATS] = enabled } }

    // Notification setters
    suspend fun setNotificationsEnabled(enabled: Boolean) { dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled } }
    suspend fun setNotificationVaultEnabled(enabled: Boolean) { dataStore.edit { it[NOTIFICATION_VAULT_ENABLED] = enabled } }
    suspend fun setStepNotifications(enabled: Boolean) { dataStore.edit { it[STEP_NOTIFICATIONS] = enabled } }
    suspend fun setTimerNotifications(enabled: Boolean) { dataStore.edit { it[TIMER_NOTIFICATIONS] = enabled } }
    suspend fun setVoiceRecordNotifications(enabled: Boolean) { dataStore.edit { it[VOICE_RECORD_NOTIFICATIONS] = enabled } }
    suspend fun setMusicNotifications(enabled: Boolean) { dataStore.edit { it[MUSIC_NOTIFICATIONS] = enabled } }
    suspend fun setFileConversionNotifications(enabled: Boolean) { dataStore.edit { it[FILE_CONVERSION_NOTIFICATIONS] = enabled } }
    suspend fun setAppUpdateNotifications(enabled: Boolean) { dataStore.edit { it[APP_UPDATE_NOTIFICATIONS] = enabled } }
    suspend fun setTaskReminderNotifications(enabled: Boolean) { dataStore.edit { it[TASK_REMINDER_NOTIFICATIONS] = enabled } }
    suspend fun setEventReminderNotifications(enabled: Boolean) { dataStore.edit { it[EVENT_REMINDER_NOTIFICATIONS] = enabled } }
    suspend fun setPomodoroNotifications(enabled: Boolean) { dataStore.edit { it[POMODORO_NOTIFICATIONS] = enabled } }
    suspend fun setBackupNotifications(enabled: Boolean) { dataStore.edit { it[BACKUP_NOTIFICATIONS] = enabled } }
    suspend fun setFlashlightNotificationsEnabled(enabled: Boolean) { dataStore.edit { it[FLASHLIGHT_NOTIFICATIONS] = enabled } }
    suspend fun setKaraokeEnabled(enabled: Boolean) { dataStore.edit { it[KARAOKE_ENABLED] = enabled } }
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

    suspend fun clearFocusMappings() {
        dataStore.edit { pref ->
            pref.remove(APP_CATEGORY_MAPPINGS)
            pref.remove(APP_NAME_MAPPINGS)
        }
    }

    // Pomodoro Setters
    suspend fun setPomodoroWorkMinutes(minutes: Int) { dataStore.edit { it[POMODORO_WORK_MINUTES] = minutes } }
    suspend fun setPomodoroShortBreakMinutes(minutes: Int) { dataStore.edit { it[POMODORO_SHORT_BREAK_MINUTES] = minutes } }
    suspend fun setPomodoroLongBreakMinutes(minutes: Int) { dataStore.edit { it[POMODORO_LONG_BREAK_MINUTES] = minutes } }
    suspend fun setPomodoroAutoStart(enabled: Boolean) { dataStore.edit { it[POMODORO_AUTO_START] = enabled } }
    suspend fun setPomodoroKeepScreenOn(enabled: Boolean) { dataStore.edit { it[POMODORO_KEEP_SCREEN_ON] = enabled } }
    suspend fun setPomodoroSessionsGoal(goal: Int) { dataStore.edit { it[POMODORO_SESSIONS_GOAL] = goal } }
    suspend fun setPomodoroSessionsCompleted(completed: Int) { dataStore.edit { it[POMODORO_SESSIONS_COMPLETED] = completed } }
    suspend fun setPomodoroRingtoneUri(uri: String) { dataStore.edit { it[POMODORO_RINGTONE_URI] = uri } }
    suspend fun setPomodoroShowQuotes(enabled: Boolean) { dataStore.edit { it[POMODORO_SHOW_QUOTES] = enabled } }
    suspend fun setPomodoroQuotes(quotes: String) { dataStore.edit { it[POMODORO_QUOTES] = quotes } }

    suspend fun setCustomRingtoneEnabled(enabled: Boolean) { dataStore.edit { it[CUSTOM_RINGTONE_ENABLED] = enabled } }
    suspend fun setCustomRingtoneUri(uri: String?) {
        dataStore.edit { 
            if (uri == null) it.remove(CUSTOM_RINGTONE_URI) else it[CUSTOM_RINGTONE_URI] = uri
        }
    }

    // Widget setters
    suspend fun setWidgetBackgroundColor(color: Int) { dataStore.edit { it[WIDGET_BACKGROUND_COLOR] = color } }
    suspend fun setWidgetAccentColor(color: Int) { dataStore.edit { it[WIDGET_ACCENT_COLOR] = color } }
    suspend fun setWidgetOpacity(opacity: Float) { dataStore.edit { it[WIDGET_OPACITY] = opacity } }

    // New Setters
    suspend fun setHapticFeedback(enabled: Boolean) { dataStore.edit { it[HAPTIC_FEEDBACK] = enabled } }
    suspend fun setHapticIntensity(intensity: Float) { dataStore.edit { it[HAPTIC_INTENSITY] = intensity } }
    suspend fun setShowQibla(enabled: Boolean) { dataStore.edit { it[SHOW_QIBLA] = enabled } }

    // Music Setters
    suspend fun setMusicAudioFocus(enabled: Boolean) { dataStore.edit { it[MUSIC_AUDIO_FOCUS] = enabled } }
    suspend fun setMusicAudioFocusDucking(enabled: Boolean) { dataStore.edit { it[MUSIC_AUDIO_FOCUS_DUCKING] = enabled } }
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
    suspend fun setMusicLyricsAlwaysSync(enabled: Boolean) { dataStore.edit { it[MUSIC_LYRICS_ALWAYS_SYNC] = enabled } }
    suspend fun setMusicLyricsWordSyncEnabled(enabled: Boolean) { dataStore.edit { it[MUSIC_LYRICS_WORD_SYNC_ENABLED] = enabled } }
    suspend fun setKaraokeWordSyncEnabled(enabled: Boolean) { dataStore.edit { it[KARAOKE_WORD_SYNC_ENABLED] = enabled } }
    suspend fun setKaraokeSingConfidentlyEnabled(enabled: Boolean) { dataStore.edit { it[KARAOKE_SING_CONFIDENTLY_ENABLED] = enabled } }
    suspend fun setKaraokeSingConfidentlyMode(mode: String) {
        dataStore.edit {
            it[KARAOKE_SING_CONFIDENTLY_MODE] = mode
            // Keep legacy boolean in sync so older code reading it still works.
            it[KARAOKE_SING_CONFIDENTLY_ENABLED] = (mode != "OFF")
        }
    }
    suspend fun setKaraokeSpeechCorrectionEnabled(enabled: Boolean) { dataStore.edit { it[KARAOKE_SPEECH_CORRECTION_ENABLED] = enabled } }
    suspend fun setKaraokeQuickSingEnabled(enabled: Boolean) { dataStore.edit { it[KARAOKE_QUICK_SING_ENABLED] = enabled } }
    suspend fun setKaraokeAutoRecordEnabled(enabled: Boolean) { dataStore.edit { it[KARAOKE_AUTO_RECORD_ENABLED] = enabled } }
    suspend fun setMusicVisualizerSensitivity(sensitivity: Float) { dataStore.edit { it[MUSIC_VISUALIZER_SENSITIVITY] = sensitivity } }
    suspend fun setMusicVisualizerAutoSensitivity(enabled: Boolean) { dataStore.edit { it[MUSIC_VISUALIZER_AUTO_SENSITIVITY] = enabled } }
    suspend fun setMusicSleepTimerLastCustomMinutes(minutes: Int) { dataStore.edit { it[MUSIC_SLEEP_TIMER_LAST_CUSTOM_MINUTES] = minutes } }
    suspend fun setMusicLastPlayedState(uri: String?, position: Long, queue: String? = null) {
        dataStore.edit {
            if (uri == null) it.remove(MUSIC_LAST_PLAYED_URI) else it[MUSIC_LAST_PLAYED_URI] = uri
            it[MUSIC_LAST_PLAYED_POSITION] = position
            if (queue == null) it.remove(MUSIC_LAST_PLAYED_QUEUE) else it[MUSIC_LAST_PLAYED_QUEUE] = queue
        }
    }
    suspend fun setMusicCustomEqualizer(data: String) { dataStore.edit { it[MUSIC_CUSTOM_EQUALIZER] = data } }

    suspend fun setPerformanceMode(enabled: Boolean) { dataStore.edit { it[PERFORMANCE_MODE] = enabled } }
    suspend fun setShowTopAppBarDescriptions(enabled: Boolean) { dataStore.edit { it[SHOW_TOP_APP_BAR_DESCRIPTIONS] = enabled } }

    suspend fun setStepCounterEnabled(enabled: Boolean) { dataStore.edit { it[STEP_COUNTER_ENABLED] = enabled } }
    suspend fun setStepHistoryRetention(retention: String) { dataStore.edit { it[STEP_HISTORY_RETENTION] = retention } }
    suspend fun setAiFitnessAgentEnabled(enabled: Boolean) { dataStore.edit { it[AI_FITNESS_AGENT_ENABLED] = enabled } }
    suspend fun setAiFitnessAgentProvider(provider: String) { dataStore.edit { it[AI_FITNESS_AGENT_PROVIDER] = provider } }
    suspend fun setAiFitnessAgentModel(model: String) { dataStore.edit { it[AI_FITNESS_AGENT_MODEL] = model } }
    suspend fun setAiFitnessAgentTone(tone: String) { dataStore.edit { it[AI_FITNESS_AGENT_TONE] = tone } }
    suspend fun setAiFitnessAgentMood(mood: String) { dataStore.edit { it[AI_FITNESS_AGENT_MOOD] = mood } }
    suspend fun setAiFitnessAgentStyle(style: String) { dataStore.edit { it[AI_FITNESS_AGENT_STYLE] = style } }
    suspend fun setStepLengthCm(length: Int) { dataStore.edit { it[STEP_LENGTH_CM] = length } }
    suspend fun setCaloriesPer1000Steps(calories: Int) { dataStore.edit { it[CALORIES_PER_1000_STEPS] = calories } }
    suspend fun setMeasurementSystem(system: String) { dataStore.edit { it[MEASUREMENT_SYSTEM] = system } }
    suspend fun setStepUseGps(enabled: Boolean) { dataStore.edit { it[STEP_USE_GPS] = enabled } }
    suspend fun setStepBatterySave(enabled: Boolean) { dataStore.edit { it[STEP_BATTERY_SAVE] = enabled } }
    suspend fun setStepSensitivity(sensitivity: Int) { dataStore.edit { it[STEP_SENSITIVITY] = sensitivity } }
    suspend fun setStepEngineMode(mode: String) { dataStore.edit { it[STEP_ENGINE_MODE] = mode } }
    suspend fun setLastOsStepCount(count: Long) { dataStore.edit { it[LAST_OS_STEP_COUNT] = count } }

    suspend fun setShowToolzPill(enabled: Boolean) { dataStore.edit { it[SHOW_TOOLZ_PILL] = enabled } }
    suspend fun setFillThePillEnabled(enabled: Boolean) { dataStore.edit { it[FILL_THE_PILL_ENABLED] = enabled } }
    suspend fun setPillTodoEnabled(enabled: Boolean) { dataStore.edit { it[PILL_TODO_ENABLED] = enabled } }
    suspend fun setPillFocusEnabled(enabled: Boolean) { dataStore.edit { it[PILL_FOCUS_ENABLED] = enabled } }
    suspend fun setPillMusicEnabled(enabled: Boolean) { dataStore.edit { it[PILL_MUSIC_ENABLED] = enabled } }
    suspend fun setPillTimerEnabled(enabled: Boolean) { dataStore.edit { it[PILL_TIMER_ENABLED] = enabled } }
    suspend fun setPillStopwatchEnabled(enabled: Boolean) { dataStore.edit { it[PILL_STOPWATCH_ENABLED] = enabled } }
    suspend fun setPillPomodoroEnabled(enabled: Boolean) { dataStore.edit { it[PILL_POMODORO_ENABLED] = enabled } }
    suspend fun setPillStepsEnabled(enabled: Boolean) { dataStore.edit { it[PILL_STEPS_ENABLED] = enabled } }
    suspend fun setPillRecorderEnabled(enabled: Boolean) { dataStore.edit { it[PILL_RECORDER_ENABLED] = enabled } }
    suspend fun setPillCaffeinateEnabled(enabled: Boolean) { dataStore.edit { it[PILL_CAFFEINATE_ENABLED] = enabled } }
    suspend fun setPillFlashlightEnabled(enabled: Boolean) { dataStore.edit { it[PILL_FLASHLIGHT_ENABLED] = enabled } }
    suspend fun setPillCatalogDownloadEnabled(enabled: Boolean) { dataStore.edit { it[PILL_CATALOG_DOWNLOAD_ENABLED] = enabled } }
    suspend fun setBackupFrequency(freq: String) { dataStore.edit { it[BACKUP_FREQUENCY] = freq } }
    suspend fun setAutoBackupCustomDays(days: Int) { dataStore.edit { it[AUTO_BACKUP_CUSTOM_DAYS] = days } }

    suspend fun setUserName(name: String) { dataStore.edit { it[USER_NAME] = name } }
    suspend fun setOnboardingCompleted(completed: Boolean) { dataStore.edit { it[ONBOARDING_COMPLETED] = completed } }
    suspend fun setAppLanguage(lang: String) { dataStore.edit { it[APP_LANGUAGE] = lang } }
    suspend fun setCatalogOnboardingCompleted(completed: Boolean) { dataStore.edit { it[CATALOG_ONBOARDING_COMPLETED] = completed } }
    suspend fun setWhisperBetaWarningShown(shown: Boolean) { dataStore.edit { it[WHISPER_BETA_WARNING_SHOWN] = shown } }
    suspend fun setWhisperOnboardingShown(shown: Boolean) { dataStore.edit { it[WHISPER_ONBOARDING_SHOWN] = shown } }
    suspend fun setShowCatalogBetaCard(show: Boolean) { dataStore.edit { it[SHOW_CATALOG_BETA_CARD] = show } }
    suspend fun setActiveDownloadJson(json: String?) {
        dataStore.edit {
            if (json == null) it.remove(ACTIVE_DOWNLOAD_JSON) else it[ACTIVE_DOWNLOAD_JSON] = json
        }
    }

    suspend fun setDownloadFormat(format: String) { dataStore.edit { it[DOWNLOAD_FORMAT] = format } }
    suspend fun setDownloadQuality(quality: String) { dataStore.edit { it[DOWNLOAD_QUALITY] = quality } }
    suspend fun setCatalogStreamQuality(quality: String) { dataStore.edit { it[CATALOG_STREAM_QUALITY] = quality } }

    suspend fun setLastTimerDuration(minutes: Int, seconds: Int) {
        dataStore.edit {
            it[LAST_TIMER_MINUTES] = minutes
            it[LAST_TIMER_SECONDS] = seconds
        }
    }

    suspend fun recordTimerUsage(minutes: Int, seconds: Int) {
        val key = "$minutes:$seconds"
        dataStore.edit { prefs ->
            val json = prefs[TIMER_HISTORY] ?: "{}"
            val history = parseTimerHistoryJson(json).toMutableMap()
            history[key] = (history[key] ?: 0) + 1
            // Keep top 20 entries max
            val sorted = history.entries.sortedByDescending { it.value }.take(20)
            val newHistory = sorted.associate { it.key to it.value }
            prefs[TIMER_HISTORY] = serializeTimerHistoryJson(newHistory)
        }
    }

    suspend fun updateLockedTimerPreset(index: Int, minutes: Int, seconds: Int) {
        dataStore.edit { prefs ->
            val json = prefs[LOCKED_TIMER_PRESETS] ?: "[]"
            val locked = parseLockedPresetsJson(json).toMutableList()
            
            // Ensure list has enough elements
            while (locked.size <= index) {
                locked.add("")
            }
            
            locked[index] = "$minutes:$seconds"
            prefs[LOCKED_TIMER_PRESETS] = serializeLockedPresetsJson(locked)
        }
    }

    private fun parseLockedPresetsJson(json: String): List<String> {
        return try {
            val trimmed = json.trim()
            if (trimmed == "[]" || trimmed.isBlank()) return emptyList()
            val content = trimmed.removePrefix("[").removeSuffix("]")
            if (content.isBlank()) return emptyList()
            content.split(",").map { it.trim().removeSurrounding("\"") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeLockedPresetsJson(list: List<String>): String {
        if (list.isEmpty()) return "[]"
        return "[${list.joinToString(",") { "\"$it\"" }}]"
    }

    private fun parseTimerHistoryJson(json: String): Map<String, Int> {
        return try {
            val map = mutableMapOf<String, Int>()
            val trimmed = json.trim()
            if (trimmed == "{}" || trimmed.isBlank()) return emptyMap()
            val entries = trimmed.removePrefix("{").removeSuffix("}")
            if (entries.isBlank()) return emptyMap()
            entries.split(",").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val k = parts[0].trim().removeSurrounding("\"")
                    val v = parts[1].trim().removeSuffix("}").toIntOrNull() ?: 0
                    map[k] = v
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun serializeTimerHistoryJson(map: Map<String, Int>): String {
        if (map.isEmpty()) return "{}"
        val entries = map.entries.joinToString(",") { kvp -> "\"${kvp.key}\":${kvp.value}" }
        return "{$entries}"
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

    suspend fun setPdfAiOcrEnhance(enabled: Boolean) {
        dataStore.edit { it[PDF_AI_OCR_ENHANCE] = enabled }
    }

    suspend fun setAiSearchEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_SEARCH_ENABLED] = enabled }
    }

    suspend fun setAiSearchChatEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_SEARCH_CHAT_ENABLED] = enabled }
    }

    suspend fun setAiSearchIconVisible(visible: Boolean) {
        dataStore.edit { it[AI_SEARCH_ICON_VISIBLE] = visible }
    }

    suspend fun setAiClipboardMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_CLIPBOARD_MONITORING] = enabled }
    }

    suspend fun setLastCryptoAlgorithm(algorithm: String) {
        dataStore.edit { it[CRYPTO_LAST_ALGORITHM] = algorithm }
    }

    suspend fun setOfflineModeEnabled(enabled: Boolean) {
        dataStore.edit { it[OFFLINE_MODE_ENABLED] = enabled }
    }

    suspend fun setFocusFlowSessionActive(active: Boolean) {
        dataStore.edit { it[FOCUS_FLOW_SESSION_ACTIVE] = active }
    }

    suspend fun setLastLoadingTime(timestamp: Long) {
        dataStore.edit { it[LAST_LOADING_TIME] = timestamp }
    }

    suspend fun setNetworkBenchmarkServers(servers: Set<String>) {
        dataStore.edit { it[NETWORK_BENCHMARK_SERVERS] = servers }
    }

    suspend fun setNetworkLastTraceTarget(target: String) {
        dataStore.edit { it[NETWORK_LAST_TRACE_TARGET] = target }
    }

    suspend fun setNetworkAutoConnectShizuku(enabled: Boolean) {
        dataStore.edit { it[NETWORK_AUTO_CONNECT_SHIZUKU] = enabled }
    }

    suspend fun setNetworkDisclaimerShown(shown: Boolean) {
        dataStore.edit { it[NETWORK_DISCLAIMER_SHOWN] = shown }
    }

    suspend fun setBmiHeight(height: String) { dataStore.edit { it[BMI_HEIGHT] = height } }
    suspend fun setBmiWeight(weight: String) { dataStore.edit { it[BMI_WEIGHT] = weight } }
    suspend fun setBmiAge(age: String) { dataStore.edit { it[BMI_AGE] = age } }
    suspend fun setBmiGender(gender: String) { dataStore.edit { it[BMI_GENDER] = gender } }
    suspend fun setBmiActivity(activity: String) { dataStore.edit { it[BMI_ACTIVITY] = activity } }
    suspend fun setBmiIsKg(isKg: Boolean) { dataStore.edit { it[BMI_IS_KG] = isKg } }
    suspend fun setBmiIsCm(isCm: Boolean) { dataStore.edit { it[BMI_IS_CM] = isCm } }

    suspend fun setFlipCoinHeadsImageUri(uri: String?) {
        dataStore.edit { 
            if (uri == null) it.remove(FLIP_COIN_HEADS_IMAGE_URI) else it[FLIP_COIN_HEADS_IMAGE_URI] = uri
        }
    }

    suspend fun setFlipCoinTailsImageUri(uri: String?) {
        dataStore.edit {
            if (uri == null) it.remove(FLIP_COIN_TAILS_IMAGE_URI) else it[FLIP_COIN_TAILS_IMAGE_URI] = uri
        }
    }

    suspend fun resetOnboarding() {
        dataStore.edit {
            it.remove(USER_NAME)
            it.remove(ONBOARDING_COMPLETED)
        }
    }
}
