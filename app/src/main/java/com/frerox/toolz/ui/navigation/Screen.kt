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
    object BackupRestore : Screen("backup_restore")
    
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
    object Scanner : Screen("scanner")
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
    object SmartEncrypter : Screen("smart_encrypter")
    object DeviceInfo : Screen("device_info")
    object NetworkPowerSuite : Screen("network_power_suite")
    object WifiTweaks : Screen("wifi_tweaks")
    object Search : Screen("search")
    object AdBlockConfig : Screen("ad_block_settings")
    object NextDnsSetup : Screen("next_dns_setup?url={url}") {
        fun createRoute(url: String) = "next_dns_setup?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
    }
    object TabManagement : Screen("tab_management")
    object Browser : Screen("browser?url={url}") {
        fun createRoute(url: String) = "browser?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
    }
    
    // Media
    object BackgroundRemover : Screen("background_remover")
    object MusicPlayer : Screen("music_player?tab={tab}") {
        fun createRoute(tab: Int) = "music_player?tab=$tab"
    }
    object FileConverter : Screen("file_converter?uri={uri}&title={title}") {
        fun createRoute(uri: String? = null, title: String? = null): String {
            if (uri == null) return "file_converter"
            val encodedUri = java.net.URLEncoder.encode(uri, "UTF-8")
            val encodedTitle = title?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: "Document"
            return "file_converter?uri=$encodedUri&title=$encodedTitle"
        }
    }

    // System
    object FileCleaner : Screen("file_cleaner")

    // Whisper — Privacy Messaging
    object WhisperAuth : Screen("whisper_auth")
    object Whisper : Screen("whisper")
    object WhisperChat : Screen("whisper_chat/{otherUserId}") {
        fun createRoute(otherUserId: String) = "whisper_chat/$otherUserId"
    }
    object WhisperUserProfile : Screen("whisper_profile/{userId}") {
        fun createRoute(userId: String) = "whisper_profile/$userId"
    }
}
