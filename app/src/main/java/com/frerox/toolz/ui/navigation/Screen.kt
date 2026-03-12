package com.frerox.toolz.ui.navigation

sealed class Screen(val route: String) {
    object Loading : Screen("loading")
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    
    // Time & Productivity
    object Timer : Screen("timer")
    object Stopwatch : Screen("stopwatch")
    object WorldClock : Screen("world_clock")
    object Pomodoro : Screen("pomodoro")
    object FocusFlow : Screen("focus_flow")
    
    // Light & Optics
    object Flashlight : Screen("flashlight")
    object ScreenLight : Screen("screen_light")
    object Magnifier : Screen("magnifier")
    object Scanner : Screen("scanner")
    object LightMeter : Screen("light_meter")
    
    // Sensors & Navigation
    object Compass : Screen("compass")
    object BubbleLevel : Screen("bubble_level")
    object Speedometer : Screen("speedometer")
    object Altimeter : Screen("altimeter")
    object StepCounter : Screen("step_counter")
    
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
    object Notepad : Screen("notepad")
    object BatteryInfo : Screen("battery_info")
    object VoiceRecorder : Screen("voice_recorder")
    object FlipCoin : Screen("flip_coin")
    object PeriodicTable : Screen("periodic_table")
    object PdfReader : Screen("pdf_reader")
    object NotificationVault : Screen("notification_vault")
    
    // Media
    object MusicPlayer : Screen("music_player")
}
