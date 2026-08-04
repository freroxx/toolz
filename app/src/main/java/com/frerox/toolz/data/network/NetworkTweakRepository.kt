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

package com.frerox.toolz.data.network

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTweakRepository @Inject constructor(
    private val shizukuExecutor: ShizukuShellExecutor,
    private val diagnosticLogDao: DiagnosticLogDao
) {
    private val _tweakResults = MutableStateFlow<Map<String, TweakResult>>(emptyMap())
    val tweakResults = _tweakResults.asStateFlow()

    val tweaks = buildTweaks()

    init {
        _tweakResults.value = tweaks.associate { it.id to TweakResult(id = it.id) }
    }

    suspend fun addLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        diagnosticLogDao.insertLog(
            DiagnosticLogEntry(
                timestamp = System.currentTimeMillis(),
                tag = tag,
                message = message,
                level = level.name
            )
        )
    }

    fun getLogs(): Flow<List<DiagnosticLogEntry>> = diagnosticLogDao.getRecentLogs()

    suspend fun clearLogs() {
        diagnosticLogDao.clearAll()
        addLog("SYSTEM", "Console logs cleared", LogLevel.INFO)
    }

    suspend fun runRawCommand(command: String): String {
        addLog("COMMAND", "> $command", LogLevel.INFO)
        val result = try {
            shizukuExecutor.executeForResult(command)
        } catch (e: Exception) {
            addLog("COMMAND", "Execution error: ${e.message}", LogLevel.ERROR)
            return "Error: ${e.message}"
        }
        val output = if (result.isSuccess) {
            result.stdout.ifBlank { "Command executed successfully (exit code 0)." }
        } else {
            "Failed (exit code ${result.exitCode}):\n${result.stderr.ifBlank { result.stdout }}"
        }
        addLog("COMMAND", output, if (result.isSuccess) LogLevel.SUCCESS else LogLevel.ERROR)
        return output
    }

    suspend fun applyTweak(tweak: WifiTweak): Boolean {
        if (tweak.type == TweakType.MANUAL_GUIDE) {
            updateTweakResult(tweak.id, TweakStatus.MANUAL, "Manual steps required.")
            addLog("TWEAK", "Manual guide requested for ${tweak.title}", LogLevel.INFO)
            return true
        }

        updateTweakResult(tweak.id, TweakStatus.RUNNING, "Applying...")
        addLog("TWEAK", "Applying tweak: ${tweak.title}", LogLevel.INFO)
        
        val result = runCommands(tweak.applyCommands)
        if (!result.first) {
            updateTweakResult(tweak.id, TweakStatus.FAILED, result.second)
            addLog("TWEAK", "Failed to apply ${tweak.title}: ${result.second}", LogLevel.ERROR)
            return false
        }

        // Verification check if verification command is specified
        if (!tweak.verificationCommand.isNullOrBlank()) {
            val verifyResult = try {
                shizukuExecutor.executeForResult(tweak.verificationCommand)
            } catch (e: Exception) {
                null
            }
            if (verifyResult == null || !verifyResult.isSuccess) {
                val warnMsg = "Applied, but verification check failed (OEM restriction or Android 14+ setting override)."
                updateTweakResult(tweak.id, TweakStatus.UNSUPPORTED, warnMsg, isApplied = false)
                addLog("DIAGNOSTIC", "Verification failed for ${tweak.title}: $warnMsg", LogLevel.WARNING)
                return false
            }
        }

        updateTweakResult(tweak.id, TweakStatus.SUCCESS, "Active & Verified", isApplied = true)
        addLog("TWEAK", "Applied and verified ${tweak.title} successfully", LogLevel.SUCCESS)
        return true
    }

    suspend fun undoTweak(tweak: WifiTweak): Boolean {
        updateTweakResult(tweak.id, TweakStatus.RUNNING, "Reverting...")
        addLog("TWEAK", "Reverting tweak: ${tweak.title}", LogLevel.INFO)
        val result = runCommands(tweak.revertCommands)
        if (!result.first) {
            updateTweakResult(tweak.id, TweakStatus.FAILED, result.second)
            addLog("TWEAK", "Failed to revert ${tweak.title}: ${result.second}", LogLevel.ERROR)
            return false
        }
        updateTweakResult(tweak.id, TweakStatus.IDLE, "Restored default", isApplied = false)
        addLog("TWEAK", "Reverted ${tweak.title}", LogLevel.INFO)
        return true
    }

    private fun updateTweakResult(id: String, status: TweakStatus, message: String, isApplied: Boolean = false) {
        _tweakResults.update { current ->
            current.toMutableMap().apply {
                this[id] = TweakResult(
                    id = id,
                    status = status,
                    message = message,
                    isApplied = isApplied,
                    lastUpdatedMs = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun runCommands(commands: List<String>): Pair<Boolean, String> {
        for (command in commands) {
            addLog("COMMAND", "Executing: $command", LogLevel.INFO)
            val result = try {
                shizukuExecutor.executeForResult(command)
            } catch (e: Exception) {
                return false to (e.message ?: "Command execution failed")
            }
            
            if (!result.isSuccess) {
                val error = result.stderr.ifBlank { result.stdout }
                if (error.contains("SecurityException", ignoreCase = true) || error.contains("does not have access", ignoreCase = true)) {
                    addLog("TWEAK", "Command restricted by system: $command", LogLevel.WARNING)
                    continue 
                }
                return false to (result.stderr.ifBlank { "Exit code ${result.exitCode}" })
            }
        }
        return true to ""
    }

    fun buildProfiles(): List<WifiOptimizationProfile> {
        return listOf(
            WifiOptimizationProfile(
                id = "gaming",
                title = "Gaming & Esports",
                description = "Forces lowest possible Wi-Fi latency, disables scan throttling, and enables rapid stall recovery.",
                icon = Icons.Rounded.SportsEsports,
                tweakIds = listOf("low_latency_mode", "scan_throttle", "data_stall_logic"),
                accentLabel = "Low Latency"
            ),
            WifiOptimizationProfile(
                id = "streaming",
                title = "Media Streaming",
                description = "Prevents deep Wi-Fi sleep off-screen, aggressive access point roaming, and bad Wi-Fi avoidance.",
                icon = Icons.Rounded.Speed,
                tweakIds = listOf("scan_throttle", "suspend_optimizations", "avoid_bad_wifi"),
                accentLabel = "High Throughput"
            ),
            WifiOptimizationProfile(
                id = "privacy",
                title = "Privacy Guard",
                description = "Ensures captive portal safety and guides WPA3 security setup.",
                icon = Icons.Rounded.VerifiedUser,
                tweakIds = listOf("captive_portal_detection", "force_wpa3"),
                accentLabel = "Secure"
            ),
            WifiOptimizationProfile(
                id = "power_save",
                title = "Battery Saver",
                description = "Optimizes background scan interval to 5 minutes and manages automatic network wakeup.",
                icon = Icons.Rounded.Timer,
                tweakIds = listOf("scan_interval", "wifi_auto_wakeup"),
                accentLabel = "Energy Efficient"
            )
        )
    }

    fun getSmartFixes(currentRssi: Int, isThrottling: Boolean): List<SmartFixRecommendation> {
        val fixes = mutableListOf<SmartFixRecommendation>()
        if (isThrottling) {
            fixes += SmartFixRecommendation(
                id = "scan_throttle",
                title = "Unlock faster scans",
                description = "Wi-Fi scan throttling is active, which delays network discovery.",
                reason = "Wi-Fi scan throttling is enabled in global settings.",
                tweakIds = listOf("scan_throttle"),
                severity = RecommendationSeverity.WARNING
            )
        }
        if (currentRssi < -70 && currentRssi > -100) {
            fixes += SmartFixRecommendation(
                id = "weak_signal",
                title = "Weak signal recovery",
                description = "RSSI is currently low ($currentRssi dBm).",
                reason = "Signal attenuation detected. Enable bad-Wi-Fi avoidance and fast stall recovery.",
                tweakIds = listOf("avoid_bad_wifi", "data_stall_logic", "adaptive_connectivity"),
                severity = RecommendationSeverity.CRITICAL
            )
        }
        return fixes
    }

    private fun buildTweaks(): List<WifiTweak> {
        return listOf(
            WifiTweak(
                id = "scan_throttle",
                title = "Disable scan throttling",
                description = "Lets Android scan more often so roaming and discovery feel snappier.",
                icon = Icons.Rounded.Speed,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PERFORMANCE,
                applyCommands = listOf("settings put global wifi_scan_throttle_enabled 0"),
                revertCommands = listOf("settings put global wifi_scan_throttle_enabled 1"),
                verificationCommand = "[ \"$(settings get global wifi_scan_throttle_enabled)\" = \"0\" ]",
                manualSteps = listOf(
                    "Open Developer options.",
                    "Find Wi-Fi scan throttling.",
                    "Turn it off."
                ),
                riskNote = "May increase battery use if apps scan often."
            ),
            WifiTweak(
                id = "low_latency_mode",
                title = "Low latency mode",
                description = "Forces the Wi-Fi chip into high-performance mode for gaming/streaming.",
                icon = Icons.Rounded.SportsEsports,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.PERFORMANCE,
                applyCommands = listOf(
                    "cmd wifi set-power-save-mode disabled",
                    "svc wifi set-power-save-mode disabled",
                    "settings put global wifi_sleep_policy 2"
                ),
                revertCommands = listOf(
                    "cmd wifi set-power-save-mode enabled",
                    "svc wifi set-power-save-mode enabled",
                    "settings delete global wifi_sleep_policy"
                ),
                verificationCommand = "cmd wifi get-power-save-mode | grep -q 'disabled' || [ \"$(settings get global wifi_sleep_policy)\" = \"2\" ]",
                riskNote = "Higher battery consumption while active."
            ),
            WifiTweak(
                id = "data_stall_logic",
                title = "Rapid stall recovery",
                description = "Enables faster recovery when the connection 'freezes' or stops passing packets.",
                icon = Icons.Rounded.FlashOn,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global wifi_data_stall_recovery_on 1"),
                revertCommands = listOf("settings put global wifi_data_stall_recovery_on 0"),
                verificationCommand = "[ \"$(settings get global wifi_data_stall_recovery_on)\" = \"1\" ]"
            ),
            WifiTweak(
                id = "scan_interval",
                title = "Optimized scan interval",
                description = "Increases the background scan interval to 5 minutes to reduce interruption and save power.",
                icon = Icons.Rounded.Timer,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.POWER,
                applyCommands = listOf("settings put global wifi_framework_scan_interval_ms 300000"),
                revertCommands = listOf("settings delete global wifi_framework_scan_interval_ms"),
                verificationCommand = "[ \"$(settings get global wifi_framework_scan_interval_ms)\" = \"300000\" ]"
            ),
            WifiTweak(
                id = "force_wpa3",
                title = "Prefer WPA3-SAE",
                description = "Encourages the system to use the latest security protocol where available.",
                icon = Icons.Rounded.VerifiedUser,
                type = TweakType.MANUAL_GUIDE,
                category = TweakCategory.PRIVACY,
                manualSteps = listOf(
                    "Open current network settings.",
                    "Look for 'Security' or 'WPA3' options.",
                    "Select SAE (WPA3) if your router supports it."
                )
            ),
            WifiTweak(
                id = "aggressive_roaming",
                title = "Aggressive AP roaming",
                description = "Pushes the device to hand off faster between access points in a mesh.",
                icon = Icons.Rounded.SwapHoriz,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PERFORMANCE,
                applyCommands = listOf("settings put global wifi_enable_aggressive_handover 1"),
                revertCommands = listOf("settings put global wifi_enable_aggressive_handover 0"),
                verificationCommand = "[ \"$(settings get global wifi_enable_aggressive_handover)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Developer options.",
                    "Enable Wi-Fi verbose logging if your ROM exposes roaming details.",
                    "Test while walking between access points."
                ),
                riskNote = "Can cause extra roaming on noisy networks."
            ),
            WifiTweak(
                id = "wifi_auto_wakeup",
                title = "Wi-Fi auto wakeup",
                description = "Turns Wi-Fi back on around saved networks so you do not stay on mobile data longer than needed.",
                icon = Icons.Rounded.Sync,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.POWER,
                applyCommands = listOf("settings put global wifi_wakeup_enabled 1"),
                revertCommands = listOf("settings put global wifi_wakeup_enabled 0"),
                verificationCommand = "[ \"$(settings get global wifi_wakeup_enabled)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Wi-Fi settings.",
                    "Go to network preferences.",
                    "Turn on Wi-Fi automatically."
                )
            ),
            WifiTweak(
                id = "avoid_bad_wifi",
                title = "Avoid poor Wi-Fi",
                description = "Allows Android to bail out of weak Wi-Fi sooner instead of clinging to it.",
                icon = Icons.Rounded.SignalWifiStatusbarConnectedNoInternet4,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global network_avoid_bad_wifi 1"),
                revertCommands = listOf("settings put global network_avoid_bad_wifi 0"),
                verificationCommand = "[ \"$(settings get global network_avoid_bad_wifi)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Network settings.",
                    "Turn on Adaptive connectivity or the equivalent vendor option."
                )
            ),
            WifiTweak(
                id = "captive_portal_detection",
                title = "Captive portal detection",
                description = "Keeps Android's portal checks enabled so hotels, campuses, and cafes open their sign-in pages reliably.",
                icon = Icons.Rounded.Route,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global captive_portal_mode 1"),
                revertCommands = listOf("settings put global captive_portal_mode 1"),
                verificationCommand = "[ \"$(settings get global captive_portal_mode)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Wi-Fi settings.",
                    "Forget and reconnect to the hotspot.",
                    "Keep Private DNS automatic if the portal still will not open."
                )
            ),
            WifiTweak(
                id = "suspend_optimizations",
                title = "Keep Wi-Fi awake off-screen",
                description = "Stops deeper Wi-Fi sleep so background sync and notifications stay more reliable.",
                icon = Icons.Rounded.PowerSettingsNew,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global wifi_suspend_optimizations_enabled 0"),
                revertCommands = listOf("settings put global wifi_suspend_optimizations_enabled 1"),
                verificationCommand = "[ \"$(settings get global wifi_suspend_optimizations_enabled)\" = \"0\" ]",
                manualSteps = listOf(
                    "Open vendor battery management.",
                    "Exclude Wi-Fi or the affected app from aggressive standby rules."
                ),
                riskNote = "Slightly higher idle battery drain."
            ),
            WifiTweak(
                id = "ip_reachability",
                title = "Relax IP reachability disconnects",
                description = "Useful for routers that trigger false disconnects during idle or multicast traffic.",
                icon = Icons.Rounded.Route,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global wifi_ip_reachability_disconnect_enabled 0"),
                revertCommands = listOf("settings put global wifi_ip_reachability_disconnect_enabled 1"),
                verificationCommand = "[ \"$(settings get global wifi_ip_reachability_disconnect_enabled)\" = \"0\" ]",
                riskNote = "Use only if you already see random disconnects."
            ),
            WifiTweak(
                id = "mobile_data_always_on",
                title = "Keep mobile data warm",
                description = "Maintains fast failover when Wi-Fi drops, especially while moving.",
                icon = Icons.Rounded.SettingsSuggest,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.POWER,
                applyCommands = listOf("settings put global mobile_data_always_on 1"),
                revertCommands = listOf("settings put global mobile_data_always_on 0"),
                verificationCommand = "[ \"$(settings get global mobile_data_always_on)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Developer options.",
                    "Turn on Mobile data always active."
                ),
                riskNote = "Can use more battery and background cellular data."
            ),
            WifiTweak(
                id = "adaptive_connectivity",
                title = "Adaptive connectivity",
                description = "Lets Android blend Wi-Fi and cellular decisions for smoother failover on supported devices.",
                icon = Icons.Rounded.SettingsSuggest,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global adaptive_connectivity_enabled 1"),
                revertCommands = listOf("settings put global adaptive_connectivity_enabled 0"),
                verificationCommand = "[ \"$(settings get global adaptive_connectivity_enabled)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Network settings.",
                    "Enable Adaptive connectivity if your device exposes it."
                ),
                riskNote = "Availability depends on Android build and vendor settings."
            ),
            WifiTweak(
                id = "verbose_logging",
                title = "Verbose Wi-Fi logging",
                description = "Turns on deeper Wi-Fi logging so diagnostics and roaming behavior are easier to inspect.",
                icon = Icons.Rounded.NetworkCheck,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("cmd wifi set-wifi-verbose-logging-enabled true"),
                revertCommands = listOf("cmd wifi set-wifi-verbose-logging-enabled false"),
                manualSteps = listOf(
                    "Open Developer options.",
                    "Enable Wi-Fi verbose logging."
                ),
                riskNote = "Diagnostic only. Leave off once testing is done."
            ),
            WifiTweak(
                id = "mtu_optimizer",
                title = "MTU Optimization",
                description = "Set optimal packet size for reduced fragmentation.",
                icon = Icons.Rounded.Straighten,
                type = TweakType.MANUAL_GUIDE,
                category = TweakCategory.STABILITY,
                manualSteps = listOf("Test ping with different packet sizes", "Apply best value in router settings")
            ),
            WifiTweak(
                id = "stack_reset",
                title = "Wi-Fi Stack Reset",
                description = "Fully cycle the Wi-Fi stack and clear kernel cache.",
                icon = Icons.Rounded.RestartAlt,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("svc wifi disable", "sleep 1", "svc wifi enable")
            )
        )
    }
}

