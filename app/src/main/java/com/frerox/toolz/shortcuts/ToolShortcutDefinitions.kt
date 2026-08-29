package com.frerox.toolz.shortcuts

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.frerox.toolz.R
import com.frerox.toolz.ui.navigation.Screen

/**
 * Central definitions for Tool Shortcuts.
 * Derived from DashboardViewModel.getDashboardCategories() – 48 tools.
 * This is the single source of truth for shortcuts (static + pinned).
 *
 * Each shortcut maps to a route understood by MainActivity.resolveExternalNavigationRoute
 * via EXTRA_NAVIGATE_TO. Tools with args use their createRoute() defaults.
 */
data class ToolShortcutDef(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val shortLabelRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val route: String,
    val rank: Int = 0
)

object ToolShortcutDefinitions {

    const val EXTRA_FROM_SHORTCUT = "from_shortcut"
    const val EXTRA_SHORTCUT_ID = "shortcut_id"

    // All 48 tools in dashboard order (6+5+5+8+11+13)
    val all: List<ToolShortcutDef> = listOf(
        // SmartFlow & AI (6)
        ToolShortcutDef(
            id = "shortcut_ai_assistant",
            labelRes = R.string.st_Tool_AiAssistant,
            shortLabelRes = R.string.st_Tool_AiAssistant,
            descriptionRes = R.string.st_Tool_AiAssistant_Desc,
            iconRes = R.drawable.ic_shortcut_ai_assistant,
            route = Screen.AiAssistant.createRoute(),
            rank = 0
        ),
        ToolShortcutDef(
            id = "shortcut_whisper",
            labelRes = R.string.st_Tool_Whisper,
            shortLabelRes = R.string.st_Tool_Whisper,
            descriptionRes = R.string.st_Tool_Whisper_Desc,
            iconRes = R.drawable.ic_shortcut_whisper,
            route = Screen.Whisper.route,
            rank = 5
        ),
        ToolShortcutDef(
            id = "shortcut_search",
            labelRes = R.string.st_Tool_Search,
            shortLabelRes = R.string.st_Tool_Search,
            descriptionRes = R.string.st_Tool_Search_Desc,
            iconRes = R.drawable.ic_shortcut_search,
            route = Screen.Search.route,
            rank = 6
        ),
        ToolShortcutDef(
            id = "shortcut_focus_flow",
            labelRes = R.string.st_Tool_FocusFlow,
            shortLabelRes = R.string.st_Tool_FocusFlow,
            descriptionRes = R.string.st_Tool_FocusFlow_Desc,
            iconRes = R.drawable.ic_shortcut_focus_flow,
            route = Screen.FocusFlow.route,
            rank = 7
        ),
        ToolShortcutDef(
            id = "shortcut_todo",
            labelRes = R.string.st_Tool_TodoList,
            shortLabelRes = R.string.st_Tool_TodoList,
            descriptionRes = R.string.st_Tool_TodoList_Desc,
            iconRes = R.drawable.ic_shortcut_todo,
            route = Screen.Todo.route,
            rank = 8
        ),
        ToolShortcutDef(
            id = "shortcut_notepad",
            labelRes = R.string.st_Tool_Notepad,
            shortLabelRes = R.string.st_Tool_Notepad,
            descriptionRes = R.string.st_Tool_Notepad_Desc,
            iconRes = R.drawable.ic_shortcut_notepad,
            route = Screen.Notepad.route,
            rank = 1
        ),
        // Time & Agenda (5)
        ToolShortcutDef(
            id = "shortcut_calendar",
            labelRes = R.string.st_Tool_Calendar,
            shortLabelRes = R.string.st_Tool_Calendar,
            descriptionRes = R.string.st_Tool_Calendar_Desc,
            iconRes = R.drawable.ic_shortcut_calendar,
            route = Screen.Calendar.route,
            rank = 9
        ),
        ToolShortcutDef(
            id = "shortcut_timer",
            labelRes = R.string.st_Tool_Timer,
            shortLabelRes = R.string.st_Tool_Timer,
            descriptionRes = R.string.st_Tool_Timer_Desc,
            iconRes = R.drawable.ic_shortcut_timer,
            route = Screen.Timer.route,
            rank = 2
        ),
        ToolShortcutDef(
            id = "shortcut_stopwatch",
            labelRes = R.string.st_Tool_Stopwatch,
            shortLabelRes = R.string.st_Tool_Stopwatch,
            descriptionRes = R.string.st_Tool_Stopwatch_Desc,
            iconRes = R.drawable.ic_shortcut_stopwatch,
            route = Screen.Stopwatch.route,
            rank = 10
        ),
        ToolShortcutDef(
            id = "shortcut_pomodoro",
            labelRes = R.string.st_Tool_Pomodoro,
            shortLabelRes = R.string.st_Tool_Pomodoro,
            descriptionRes = R.string.st_Tool_Pomodoro_Desc,
            iconRes = R.drawable.ic_shortcut_pomodoro,
            route = Screen.Pomodoro.route,
            rank = 3
        ),
        ToolShortcutDef(
            id = "shortcut_world_clock",
            labelRes = R.string.st_Tool_WorldClock,
            shortLabelRes = R.string.st_Tool_WorldClock,
            descriptionRes = R.string.st_Tool_WorldClock_Desc,
            iconRes = R.drawable.ic_shortcut_world_clock,
            route = Screen.WorldClock.route,
            rank = 11
        ),
        // Media & Audio (5)
        ToolShortcutDef(
            id = "shortcut_music_player",
            labelRes = R.string.st_Tool_MusicPlayer,
            shortLabelRes = R.string.st_Tool_MusicPlayer,
            descriptionRes = R.string.st_Tool_MusicPlayer_Desc,
            iconRes = R.drawable.ic_shortcut_music_player,
            route = Screen.MusicPlayer.createRoute(0),
            rank = 12
        ),
        ToolShortcutDef(
            id = "shortcut_voice_recorder",
            labelRes = R.string.st_Tool_VoiceRecorder,
            shortLabelRes = R.string.st_Tool_VoiceRecorder,
            descriptionRes = R.string.st_Tool_VoiceRecorder_Desc,
            iconRes = R.drawable.ic_shortcut_voice_recorder,
            route = Screen.VoiceRecorder.route,
            rank = 13
        ),
        ToolShortcutDef(
            id = "shortcut_file_converter",
            labelRes = R.string.st_Tool_FileConverter,
            shortLabelRes = R.string.st_Tool_FileConverter,
            descriptionRes = R.string.st_Tool_FileConverter_Desc,
            iconRes = R.drawable.ic_shortcut_file_converter,
            route = Screen.FileConverter.createRoute(),
            rank = 14
        ),
        ToolShortcutDef(
            id = "shortcut_sound_meter",
            labelRes = R.string.st_Tool_SoundMeter,
            shortLabelRes = R.string.st_Tool_SoundMeter,
            descriptionRes = R.string.st_Tool_SoundMeter_Desc,
            iconRes = R.drawable.ic_shortcut_sound_meter,
            route = Screen.SoundMeter.route,
            rank = 15
        ),
        ToolShortcutDef(
            id = "shortcut_background_remover",
            labelRes = R.string.st_Tool_BackgroundRemover,
            shortLabelRes = R.string.st_Tool_BackgroundRemover,
            descriptionRes = R.string.st_Tool_BackgroundRemover_Desc,
            iconRes = R.drawable.ic_shortcut_background_remover,
            route = Screen.BackgroundRemover.createRoute(),
            rank = 16
        ),
        // Utilities & Math (8)
        ToolShortcutDef(
            id = "shortcut_calculator",
            labelRes = R.string.st_Tool_Calculator,
            shortLabelRes = R.string.st_Tool_Calculator,
            descriptionRes = R.string.st_Tool_Calculator_Desc,
            iconRes = R.drawable.ic_shortcut_calculator,
            route = Screen.Calculator.route,
            rank = 17
        ),
        ToolShortcutDef(
            id = "shortcut_unit_converter",
            labelRes = R.string.st_Tool_UnitConverter,
            shortLabelRes = R.string.st_Tool_UnitConverter,
            descriptionRes = R.string.st_Tool_UnitConverter_Desc,
            iconRes = R.drawable.ic_shortcut_unit_converter,
            route = Screen.UnitConverter.route,
            rank = 18
        ),
        ToolShortcutDef(
            id = "shortcut_encrypter",
            labelRes = R.string.st_Tool_Encrypter,
            shortLabelRes = R.string.st_Tool_Encrypter,
            descriptionRes = R.string.st_Tool_Encrypter_Desc,
            iconRes = R.drawable.ic_shortcut_encrypter,
            route = Screen.SmartEncrypter.createRoute(),
            rank = 19
        ),
        ToolShortcutDef(
            id = "shortcut_equation_solver",
            labelRes = R.string.st_Tool_EquationSolver,
            shortLabelRes = R.string.st_Tool_EquationSolver,
            descriptionRes = R.string.st_Tool_EquationSolver_Desc,
            iconRes = R.drawable.ic_shortcut_equation_solver,
            route = Screen.EquationSolver.route,
            rank = 20
        ),
        ToolShortcutDef(
            id = "shortcut_pdf_reader",
            labelRes = R.string.st_Tool_PdfReader,
            shortLabelRes = R.string.st_Tool_PdfReader,
            descriptionRes = R.string.st_Tool_PdfReader_Desc,
            iconRes = R.drawable.ic_shortcut_pdf_reader,
            route = Screen.PdfReader.route,
            rank = 21
        ),
        ToolShortcutDef(
            id = "shortcut_tip_calc",
            labelRes = R.string.st_Tool_TipCalc,
            shortLabelRes = R.string.st_Tool_TipCalc,
            descriptionRes = R.string.st_Tool_TipCalc_Desc,
            iconRes = R.drawable.ic_shortcut_tip_calc,
            route = Screen.TipCalculator.route,
            rank = 22
        ),
        ToolShortcutDef(
            id = "shortcut_clipboard",
            labelRes = R.string.st_Tool_Clipboard,
            shortLabelRes = R.string.st_Tool_Clipboard,
            descriptionRes = R.string.st_Tool_Clipboard_Desc,
            iconRes = R.drawable.ic_shortcut_clipboard,
            route = Screen.Clipboard.route,
            rank = 23
        ),
        ToolShortcutDef(
            id = "shortcut_ruler",
            labelRes = R.string.st_Tool_Ruler,
            shortLabelRes = R.string.st_Tool_Ruler,
            descriptionRes = R.string.st_Tool_Ruler_Desc,
            iconRes = R.drawable.ic_shortcut_ruler,
            route = Screen.Ruler.route,
            rank = 24
        ),
        // Sensors & Vision (11)
        ToolShortcutDef(
            id = "shortcut_scanner",
            labelRes = R.string.st_Tool_Scanner,
            shortLabelRes = R.string.st_Tool_Scanner,
            descriptionRes = R.string.st_Tool_Scanner_Desc,
            iconRes = R.drawable.ic_shortcut_scanner,
            route = Screen.Scanner.createRoute(),
            rank = 25
        ),
        ToolShortcutDef(
            id = "shortcut_qr_generator",
            labelRes = R.string.st_Tool_QrGenerator,
            shortLabelRes = R.string.st_Tool_QrGenerator,
            descriptionRes = R.string.st_Tool_QrGenerator_Desc,
            iconRes = R.drawable.ic_shortcut_qr_generator,
            route = Screen.QrGenerator.route,
            rank = 26
        ),
        ToolShortcutDef(
            id = "shortcut_flashlight",
            labelRes = R.string.st_Tool_Flashlight,
            shortLabelRes = R.string.st_Tool_Flashlight,
            descriptionRes = R.string.st_Tool_Flashlight_Desc,
            iconRes = R.drawable.ic_shortcut_flashlight,
            route = Screen.Flashlight.route,
            rank = 27
        ),
        ToolShortcutDef(
            id = "shortcut_screen_light",
            labelRes = R.string.st_Tool_ScreenLight,
            shortLabelRes = R.string.st_Tool_ScreenLight,
            descriptionRes = R.string.st_Tool_ScreenLight_Desc,
            iconRes = R.drawable.ic_shortcut_screen_light,
            route = Screen.ScreenLight.route,
            rank = 28
        ),
        ToolShortcutDef(
            id = "shortcut_magnifier",
            labelRes = R.string.st_Tool_Magnifier,
            shortLabelRes = R.string.st_Tool_Magnifier,
            descriptionRes = R.string.st_Tool_Magnifier_Desc,
            iconRes = R.drawable.ic_shortcut_magnifier,
            route = Screen.Magnifier.route,
            rank = 29
        ),
        ToolShortcutDef(
            id = "shortcut_compass",
            labelRes = R.string.st_Tool_Compass,
            shortLabelRes = R.string.st_Tool_Compass,
            descriptionRes = R.string.st_Tool_Compass_Desc,
            iconRes = R.drawable.ic_shortcut_compass,
            route = Screen.Compass.route,
            rank = 30
        ),
        ToolShortcutDef(
            id = "shortcut_bubble_level",
            labelRes = R.string.st_Tool_BubbleLevel,
            shortLabelRes = R.string.st_Tool_BubbleLevel,
            descriptionRes = R.string.st_Tool_BubbleLevel_Desc,
            iconRes = R.drawable.ic_shortcut_bubble_level,
            route = Screen.BubbleLevel.route,
            rank = 31
        ),
        ToolShortcutDef(
            id = "shortcut_light_meter",
            labelRes = R.string.st_Tool_LightMeter,
            shortLabelRes = R.string.st_Tool_LightMeter,
            descriptionRes = R.string.st_Tool_LightMeter_Desc,
            iconRes = R.drawable.ic_shortcut_light_meter,
            route = Screen.LightMeter.route,
            rank = 32
        ),
        ToolShortcutDef(
            id = "shortcut_speedometer",
            labelRes = R.string.st_Tool_Speedometer,
            shortLabelRes = R.string.st_Tool_Speedometer,
            descriptionRes = R.string.st_Tool_Speedometer_Desc,
            iconRes = R.drawable.ic_shortcut_speedometer,
            route = Screen.Speedometer.route,
            rank = 33
        ),
        ToolShortcutDef(
            id = "shortcut_altimeter",
            labelRes = R.string.st_Tool_Altimeter,
            shortLabelRes = R.string.st_Tool_Altimeter,
            descriptionRes = R.string.st_Tool_Altimeter_Desc,
            iconRes = R.drawable.ic_shortcut_altimeter,
            route = Screen.Altimeter.route,
            rank = 34
        ),
        ToolShortcutDef(
            id = "shortcut_color_picker",
            labelRes = R.string.st_Tool_ColorPicker,
            shortLabelRes = R.string.st_Tool_ColorPicker,
            descriptionRes = R.string.st_Tool_ColorPicker_Desc,
            iconRes = R.drawable.ic_shortcut_color_picker,
            route = Screen.ColorPicker.route,
            rank = 35
        ),
        // System & Health (13)
        ToolShortcutDef(
            id = "shortcut_password_vault",
            labelRes = R.string.st_Tool_PasswordVault,
            shortLabelRes = R.string.st_Tool_PasswordVault,
            descriptionRes = R.string.st_Tool_PasswordVault_Desc,
            iconRes = R.drawable.ic_shortcut_password_vault,
            route = Screen.PasswordVault.route,
            rank = 36
        ),
        ToolShortcutDef(
            id = "shortcut_network_tweaks",
            labelRes = R.string.st_Tool_NetworkTweaks,
            shortLabelRes = R.string.st_Tool_NetworkTweaks,
            descriptionRes = R.string.st_Tool_NetworkTweaks_Desc,
            iconRes = R.drawable.ic_shortcut_network_tweaks,
            route = Screen.WifiTweaks.route,
            rank = 37
        ),
        ToolShortcutDef(
            id = "shortcut_random_gen",
            labelRes = R.string.st_Tool_RandomGen,
            shortLabelRes = R.string.st_Tool_RandomGen,
            descriptionRes = R.string.st_Tool_RandomGen_Desc,
            iconRes = R.drawable.ic_shortcut_random_gen,
            route = Screen.PasswordGenerator.route,
            rank = 38
        ),
        ToolShortcutDef(
            id = "shortcut_device_info",
            labelRes = R.string.st_Tool_DeviceInfo,
            shortLabelRes = R.string.st_Tool_DeviceInfo,
            descriptionRes = R.string.st_Tool_DeviceInfo_Desc,
            iconRes = R.drawable.ic_shortcut_device_info,
            route = Screen.DeviceInfo.route,
            rank = 39
        ),
        ToolShortcutDef(
            id = "shortcut_battery_info",
            labelRes = R.string.st_Tool_BatteryInfo,
            shortLabelRes = R.string.st_Tool_BatteryInfo,
            descriptionRes = R.string.st_Tool_BatteryInfo_Desc,
            iconRes = R.drawable.ic_shortcut_battery_info,
            route = Screen.BatteryInfo.route,
            rank = 40
        ),
        ToolShortcutDef(
            id = "shortcut_file_cleaner",
            labelRes = R.string.st_Tool_FileCleaner,
            shortLabelRes = R.string.st_Tool_FileCleaner,
            descriptionRes = R.string.st_Tool_FileCleaner_Desc,
            iconRes = R.drawable.ic_shortcut_file_cleaner,
            route = Screen.FileCleaner.route,
            rank = 41
        ),
        ToolShortcutDef(
            id = "shortcut_step_counter",
            labelRes = R.string.st_Tool_StepCounter,
            shortLabelRes = R.string.st_Tool_StepCounter,
            descriptionRes = R.string.st_Tool_StepCounter_Desc,
            iconRes = R.drawable.ic_shortcut_step_counter,
            route = Screen.StepCounter.route,
            rank = 42
        ),
        ToolShortcutDef(
            id = "shortcut_notification_vault",
            labelRes = R.string.st_Tool_NotificationVault,
            shortLabelRes = R.string.st_Tool_NotificationVault,
            descriptionRes = R.string.st_Tool_NotificationVault_Desc,
            iconRes = R.drawable.ic_shortcut_notification_vault,
            route = Screen.NotificationVault.route,
            rank = 43
        ),
        ToolShortcutDef(
            id = "shortcut_bmi_calc",
            labelRes = R.string.st_Tool_BmiCalc,
            shortLabelRes = R.string.st_Tool_BmiCalc,
            descriptionRes = R.string.st_Tool_BmiCalc_Desc,
            iconRes = R.drawable.ic_shortcut_bmi_calc,
            route = Screen.BmiCalculator.route,
            rank = 44
        ),
        ToolShortcutDef(
            id = "shortcut_periodic_table",
            labelRes = R.string.st_Tool_PeriodicTable,
            shortLabelRes = R.string.st_Tool_PeriodicTable,
            descriptionRes = R.string.st_Tool_PeriodicTable_Desc,
            iconRes = R.drawable.ic_shortcut_periodic_table,
            route = Screen.PeriodicTable.route,
            rank = 45
        ),
        ToolShortcutDef(
            id = "shortcut_caffeinate",
            labelRes = R.string.st_Tool_Caffeinate,
            shortLabelRes = R.string.st_Tool_Caffeinate,
            descriptionRes = R.string.st_Tool_Caffeinate_Desc,
            iconRes = R.drawable.ic_shortcut_caffeinate,
            route = Screen.Caffeinate.route,
            rank = 46
        ),
        ToolShortcutDef(
            id = "shortcut_flip_coin",
            labelRes = R.string.st_Tool_FlipCoin,
            shortLabelRes = R.string.st_Tool_FlipCoin,
            descriptionRes = R.string.st_Tool_FlipCoin_Desc,
            iconRes = R.drawable.ic_shortcut_flip_coin,
            route = Screen.FlipCoin.route,
            rank = 47
        ),
        ToolShortcutDef(
            id = "shortcut_purge_shot",
            labelRes = R.string.st_Tool_PurgeShot,
            shortLabelRes = R.string.st_Tool_PurgeShot,
            descriptionRes = R.string.st_Tool_PurgeShot_Desc,
            iconRes = R.drawable.ic_shortcut_purge_shot,
            route = Screen.PurgeShot.route,
            rank = 48
        )
    )

    fun findById(id: String): ToolShortcutDef? = all.find { it.id == id }
    fun findByRoute(route: String): ToolShortcutDef? {
        // Handle routes with query params: strip to base for search
        val base = route.substringBefore("?")
        return all.find { it.route.substringBefore("?") == base }
    }

    /** Top N for static shortcuts – rank 0..3 */
    val staticShortcuts: List<ToolShortcutDef>
        get() = all.sortedBy { it.rank }.take(4)
}
