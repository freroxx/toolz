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

package com.frerox.toolz.ui.screens.livingframes

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Portrait
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Toll
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.ui.graphics.vector.ImageVector
import com.frerox.toolz.R
import com.frerox.toolz.ui.navigation.Screen

/**
 * Curated list of tools a Living Widget can open. Routes are the same
 * canonical destinations the dashboard uses; [routeOrNull] rejects stale or
 * hand-edited metadata so a widget tap can never navigate to an unknown route.
 */
data class LivingFrameTool(
    @StringRes val titleRes: Int,
    val route: String,
    val icon: ImageVector
)

object LivingFrameTools {

    val ALL: List<LivingFrameTool> = listOf(
        LivingFrameTool(R.string.st_Tool_AiAssistant, Screen.AiAssistant.createRoute(), Icons.Rounded.AutoAwesome),
        LivingFrameTool(R.string.st_Tool_Whisper, Screen.Whisper.route, Icons.AutoMirrored.Rounded.Chat),
        LivingFrameTool(R.string.st_Tool_Search, Screen.Search.homeRoute, Icons.Rounded.Search),
        LivingFrameTool(R.string.st_Tool_FocusFlow, Screen.FocusFlow.route, Icons.Rounded.Toll),
        LivingFrameTool(R.string.st_Tool_TodoList, Screen.Todo.route, Icons.Rounded.TaskAlt),
        LivingFrameTool(R.string.st_Tool_Notepad, Screen.Notepad.route, Icons.Rounded.Description),

        LivingFrameTool(R.string.st_Tool_Calendar, Screen.Calendar.route, Icons.Rounded.CalendarMonth),
        LivingFrameTool(R.string.st_Tool_Timer, Screen.Timer.route, Icons.Rounded.Timer),
        LivingFrameTool(R.string.st_Tool_Stopwatch, Screen.Stopwatch.route, Icons.Rounded.History),
        LivingFrameTool(R.string.st_Tool_Pomodoro, Screen.Pomodoro.route, Icons.Rounded.AvTimer),
        LivingFrameTool(R.string.st_Tool_WorldClock, Screen.WorldClock.route, Icons.Rounded.Public),

        LivingFrameTool(R.string.st_Tool_MusicPlayer, Screen.MusicPlayer.createRoute(0), Icons.Rounded.MusicNote),
        LivingFrameTool(R.string.st_Tool_MediaDownloader, Screen.MediaDownloader.homeRoute, Icons.Rounded.CloudDownload),
        LivingFrameTool(R.string.st_Tool_VoiceRecorder, Screen.VoiceRecorder.route, Icons.Rounded.Mic),
        LivingFrameTool(R.string.st_Tool_FileConverter, Screen.FileConverter.createRoute(), Icons.Rounded.Transform),
        LivingFrameTool(R.string.st_Tool_SoundMeter, Screen.SoundMeter.route, Icons.Rounded.GraphicEq),
        LivingFrameTool(R.string.st_Tool_BackgroundRemover, Screen.BackgroundRemover.createRoute(), Icons.Rounded.Portrait),
        LivingFrameTool(R.string.st_Tool_LivingFrames, Screen.LivingWidgets.route, Icons.Rounded.Image),

        LivingFrameTool(R.string.st_Tool_Calculator, Screen.Calculator.route, Icons.Rounded.Calculate),
        LivingFrameTool(R.string.st_Tool_UnitConverter, Screen.UnitConverter.route, Icons.Rounded.SyncAlt),
        LivingFrameTool(R.string.st_Tool_Encrypter, Screen.SmartEncrypter.createRoute(), Icons.Rounded.EnhancedEncryption),
        LivingFrameTool(R.string.st_Tool_EquationSolver, Screen.EquationSolver.route, Icons.Rounded.Functions),
        LivingFrameTool(R.string.st_Tool_PdfReader, Screen.PdfReader.route, Icons.Rounded.PictureAsPdf),
        LivingFrameTool(R.string.st_Tool_TipCalc, Screen.TipCalculator.route, Icons.AutoMirrored.Rounded.ReceiptLong),
        LivingFrameTool(R.string.st_Tool_Clipboard, Screen.Clipboard.route, Icons.Rounded.ContentPaste),
        LivingFrameTool(R.string.st_Tool_Ruler, Screen.Ruler.route, Icons.Rounded.Straighten),

        LivingFrameTool(R.string.st_Tool_Scanner, Screen.Scanner.createRoute(), Icons.Rounded.QrCodeScanner),
        LivingFrameTool(R.string.st_Tool_QrGenerator, Screen.QrGenerator.route, Icons.Rounded.QrCode),
        LivingFrameTool(R.string.st_Tool_Flashlight, Screen.Flashlight.route, Icons.Rounded.FlashlightOn),
        LivingFrameTool(R.string.st_Tool_ScreenLight, Screen.ScreenLight.route, Icons.Rounded.Laptop),
        LivingFrameTool(R.string.st_Tool_Magnifier, Screen.Magnifier.route, Icons.Rounded.ZoomIn),
        LivingFrameTool(R.string.st_Tool_Compass, Screen.Compass.route, Icons.Rounded.Explore),
        LivingFrameTool(R.string.st_Tool_BubbleLevel, Screen.BubbleLevel.route, Icons.Rounded.Architecture),
        LivingFrameTool(R.string.st_Tool_LightMeter, Screen.LightMeter.route, Icons.Rounded.LightMode),
        LivingFrameTool(R.string.st_Tool_Speedometer, Screen.Speedometer.route, Icons.Rounded.Speed),
        LivingFrameTool(R.string.st_Tool_Altimeter, Screen.Altimeter.route, Icons.Rounded.Terrain),
        LivingFrameTool(R.string.st_Tool_ColorPicker, Screen.ColorPicker.route, Icons.Rounded.Palette),

        LivingFrameTool(R.string.st_Tool_PasswordVault, Screen.PasswordVault.route, Icons.Rounded.Security),
        LivingFrameTool(R.string.st_Tool_NetworkTweaks, Screen.WifiTweaks.route, Icons.Rounded.NetworkCheck),
        LivingFrameTool(R.string.st_Tool_RandomGen, Screen.PasswordGenerator.route, Icons.Rounded.Key),
        LivingFrameTool(R.string.st_Tool_DeviceInfo, Screen.DeviceInfo.route, Icons.Rounded.Info),
        LivingFrameTool(R.string.st_Tool_BatteryInfo, Screen.BatteryInfo.route, Icons.Rounded.BatteryChargingFull),
        LivingFrameTool(R.string.st_Tool_FileCleaner, Screen.FileCleaner.route, Icons.Rounded.CleaningServices),
        LivingFrameTool(R.string.st_Tool_StepCounter, Screen.StepCounter.route, Icons.AutoMirrored.Rounded.DirectionsRun),
        LivingFrameTool(R.string.st_Tool_NotificationVault, Screen.NotificationVault.route, Icons.Rounded.VerifiedUser),
        LivingFrameTool(R.string.st_Tool_BmiCalc, Screen.BmiCalculator.route, Icons.Rounded.MonitorWeight),
        LivingFrameTool(R.string.st_Tool_PeriodicTable, Screen.PeriodicTable.route, Icons.Rounded.Science),
        LivingFrameTool(R.string.st_Tool_Caffeinate, Screen.Caffeinate.route, Icons.Rounded.Coffee),
        LivingFrameTool(R.string.st_Tool_FlipCoin, Screen.FlipCoin.route, Icons.Rounded.Casino),
        LivingFrameTool(R.string.st_Tool_PurgeShot, Screen.PurgeShot.route, Icons.Rounded.DeleteSweep)
    )

    fun byRoute(route: String?): LivingFrameTool? =
        route?.let { candidate -> ALL.firstOrNull { it.route == candidate } }

    /** Canonical route for [route], or null when the target no longer exists. */
    fun routeOrNull(route: String?): String? = byRoute(route)?.route
}
