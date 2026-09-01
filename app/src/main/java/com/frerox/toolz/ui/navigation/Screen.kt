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

package com.frerox.toolz.ui.navigation

sealed class Screen(val route: String) {
    object Loading : Screen("loading")
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    object Update : Screen("update")
    object BackupRestore : Screen("backup_restore?initialUri={initialUri}") {
        fun createRoute(initialUri: String? = null) =
            "backup_restore" + (initialUri?.let { "?initialUri=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")
    }
    
    // AI
    object AiAssistant : Screen("ai_assistant?chatId={chatId}&isCoachMode={isCoachMode}") {
        fun createRoute(chatId: Int = -1, isCoachMode: Boolean = false) =
            "ai_assistant?chatId=$chatId&isCoachMode=$isCoachMode"
    }
    object SmartSearch : Screen("smart_search")
    
    // Time & Productivity
    object Timer : Screen("timer")
    object Stopwatch : Screen("stopwatch")
    object WorldClock : Screen("world_clock")
    object Pomodoro : Screen("pomodoro")
    object FocusFlow : Screen("focus_flow")
    object Todo : Screen("todo")
    object Calendar : Screen("calendar")
    object Caffeinate : Screen("caffeinate")
    
    // Light & Optics
    object Flashlight : Screen("flashlight")
    object ScreenLight : Screen("screen_light")
    object Magnifier : Screen("magnifier")
    object Scanner : Screen("scanner?initialImageUri={initialImageUri}") {
        fun createRoute(initialImageUri: String? = null) =
            "scanner" + (initialImageUri?.let { "?initialImageUri=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")
    }
    object QrGenerator : Screen("qr_generator")
    object LightMeter : Screen("light_meter")
    
    // Sensors & Navigation
    object Compass : Screen("compass")
    object BubbleLevel : Screen("bubble_level")
    object Speedometer : Screen("speedometer")
    object Altimeter : Screen("altimeter")
    object StepCounter : Screen("step_counter")
    object StepTrends : Screen("step_trends")
    
    // Math & Conversion
    object Calculator : Screen("calculator")
    object UnitConverter : Screen("unit_converter")
    object TipCalculator : Screen("tip_calculator")
    object BmiCalculator : Screen("bmi_calculator")
    object EquationSolver : Screen("equation_solver")
    
    // Utilities
    object Ruler : Screen("ruler")
    object SoundMeter : Screen("sound_meter")
    object ColorPicker : Screen("color_picker")
    object PasswordGenerator : Screen("password_generator")
    object PasswordVault : Screen("password_vault")
    object Notepad : Screen("notepad")
    object BatteryInfo : Screen("battery_info")
    object VoiceRecorder : Screen("voice_recorder")
    object FlipCoin : Screen("flip_coin")
    object PeriodicTable : Screen("periodic_table")
    object PdfReader : Screen("pdf_reader")
    object NotificationVault : Screen("notification_vault")
    object Clipboard : Screen("clipboard")
    object SmartEncrypter : Screen("smart_encrypter?initialUri={initialUri}&mode={mode}") {
        fun createRoute(initialUri: String? = null, mode: String? = null): String {
            var route = "smart_encrypter"
            val params = mutableListOf<String>()
            initialUri?.let { params.add("initialUri=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            mode?.let { params.add("mode=$it") }
            if (params.isNotEmpty()) {
                route += "?" + params.joinToString("&")
            }
            return route
        }
    }
    object DeviceInfo : Screen("device_info")
    object NetworkPowerSuite : Screen("network_power_suite")
    object WifiTweaks : Screen("wifi_tweaks")
    object Search : Screen("search?query={query}") {
        val homeRoute: String = "search"
        fun createRoute(query: String? = null) = if (query.isNullOrBlank()) "search" else "search?query=${java.net.URLEncoder.encode(query, "UTF-8")}"
    }
    object SitePermissions : Screen("site_permissions")
    object AdBlockConfig : Screen("ad_block_settings")
    object NextDnsSetup : Screen("next_dns_setup?url={url}") {
        fun createRoute(url: String) = "next_dns_setup?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
    }
    object TabManagement : Screen("tab_management")
    object Browser : Screen("browser?url={url}") {
        fun createRoute(url: String) = "browser?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
    }
    
    // Media
    object BackgroundRemover : Screen("background_remover?initialUri={initialUri}") {
        fun createRoute(initialUri: String? = null) =
            "background_remover" + (initialUri?.let { "?initialUri=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")
    }
    object MusicPlayer : Screen("music_player?tab={tab}&initialUri={initialUri}") {
        fun createRoute(tab: Int, initialUri: String? = null) =
            "music_player?tab=$tab" + (initialUri?.let { "&initialUri=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: "")
    }
    object FileConverter : Screen("file_converter?uri={uri}&title={title}&initialUris={initialUris}") {
        fun createRoute(uri: String? = null, title: String? = null, initialUris: List<String>? = null): String {
            val params = mutableListOf<String>()
            uri?.let { params.add("uri=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            title?.let { params.add("title=${java.net.URLEncoder.encode(it, "UTF-8")}") }
            initialUris?.let { params.add("initialUris=${java.net.URLEncoder.encode(it.joinToString(","), "UTF-8")}") }

            return if (params.isEmpty()) "file_converter" else "file_converter?" + params.joinToString("&")
        }
    }

    // System
    object FileCleaner : Screen("file_cleaner")
    object PurgeShot : Screen("purge_shot")
    object ToolShortcuts : Screen("tool_shortcuts")

    // Whisper — Privacy Messaging
    object WhisperOnboarding : Screen("whisper_onboarding")
    object WhisperAuth : Screen("whisper_auth")
    object Whisper : Screen("whisper")
    object WhisperChat : Screen("whisper_chat/{otherUserId}") {
        fun createRoute(otherUserId: String) = "whisper_chat/$otherUserId"
    }
    object WhisperUserProfile : Screen("whisper_profile/{userId}") {
        fun createRoute(userId: String) = "whisper_profile/$userId"
    }
}
