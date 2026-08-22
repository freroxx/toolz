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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser

/**
 * SINGLE source of truth for every Wi-Fi/network tweak, profile and DNS provider.
 *
 * Rules enforced here:
 *  - one catalog; the old duplicates (WifiTweaksViewModel.buildTweaks,
 *    NetworkTweakRepository.buildTweaks) were merged into this superset.
 *  - revertCommands restore the AOSP default, never re-apply the applied value.
 *  - every non-manual tweak carries a verificationCommand so the UI can show an
 *    honest three-state result instead of "Active" for unverifiable writes.
 */
object TweakCatalog {

    val tweaks: List<WifiTweak> = listOf(
        WifiTweak(
            id = "scan_throttle",
            title = "Disable scan throttling",
            description = "Lets Android scan more often so roaming and discovery feel snappier.",
            icon = Icons.Rounded.Speed,
            type = TweakType.SHIZUKU_OR_GUIDE,
            category = TweakCategory.PERFORMANCE,
            applyCommands = listOf("settings put global wifi_scan_throttle_enabled 0"),
            revertCommands = listOf("settings put global wifi_scan_throttle_enabled 1"),
            verificationCommand = "[ \"\$(settings get global wifi_scan_throttle_enabled)\" = \"0\" ]",
            manualSteps = listOf(
                "Open Developer options.",
                "Find Wi-Fi scan throttling.",
                "Turn it off."
            ),
            riskNote = "May increase battery use if apps scan often.",
            oemNotes = mapOf(
                "xiaomi" to "HyperOS may reset this after rebooting.",
                "huawei" to "EMUI hides the developer toggle; shell write usually still works."
            )
        ),
        WifiTweak(
            id = "low_latency_mode",
            title = "Low latency mode",
            description = "Forces the Wi-Fi chip into high-performance mode for gaming/streaming.",
            icon = Icons.Rounded.SportsEsports,
            type = TweakType.SHIZUKU_ONLY,
            category = TweakCategory.PERFORMANCE,
            applyCommands = listOf(
                "cmd wifi set-power-save-mode disabled || true",
                "svc wifi set-power-save-mode disabled || true",
                "settings put global wifi_sleep_policy 2"
            ),
            revertCommands = listOf(
                "cmd wifi set-power-save-mode enabled || true",
                "svc wifi set-power-save-mode enabled || true",
                "settings delete global wifi_sleep_policy"
            ),
            verificationCommand =
                "cmd wifi get-power-save-mode | grep -q 'disabled' || [ \"\$(settings get global wifi_sleep_policy)\" = \"2\" ]",
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
            revertCommands = listOf("settings delete global wifi_data_stall_recovery_on"),
            verificationCommand = "[ \"\$(settings get global wifi_data_stall_recovery_on)\" = \"1\" ]"
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
            verificationCommand = "[ \"\$(settings get global wifi_framework_scan_interval_ms)\" = \"300000\" ]"
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
            verificationCommand = "[ \"\$(settings get global wifi_enable_aggressive_handover)\" = \"1\" ]",
            manualSteps = listOf(
                "Open Wi-Fi settings → Advanced or Roaming.",
                "Enable aggressive roaming / fast transition if your ROM exposes it.",
                "Test while walking between mesh nodes."
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
            verificationCommand = "[ \"\$(settings get global wifi_wakeup_enabled)\" = \"1\" ]",
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
            verificationCommand = "[ \"\$(settings get global network_avoid_bad_wifi)\" = \"1\" ]",
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
            applyCommands = listOf("settings put global captive_portal_mode 1"),
            // honest default: remove the override entirely so the OS default applies
            revertCommands = listOf("settings delete global captive_portal_mode"),
            verificationCommand = "[ \"\$(settings get global captive_portal_mode)\" = \"1\" ]",
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
            verificationCommand = "[ \"\$(settings get global wifi_suspend_optimizations_enabled)\" = \"0\" ]",
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
            verificationCommand = "[ \"\$(settings get global wifi_ip_reachability_disconnect_enabled)\" = \"0\" ]",
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
            verificationCommand = "[ \"\$(settings get global mobile_data_always_on)\" = \"1\" ]",
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
            verificationCommand = "[ \"\$(settings get global adaptive_connectivity_enabled)\" = \"1\" ]",
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
            verificationCommand = "cmd wifi get-wifi-verbose-logging-enabled | grep -q 'true'",
            manualSteps = listOf(
                "Open Developer options.",
                "Enable Wi-Fi verbose logging."
            ),
            riskNote = "Diagnostic only. Leave off once testing is done."
        ),
        WifiTweak(
            id = "private_dns_cloudflare",
            title = "Private DNS: Cloudflare",
            description = "A fast privacy baseline using 1.1.1.1 over DNS-over-TLS.",
            icon = Icons.Rounded.Public,
            type = TweakType.SHIZUKU_OR_GUIDE,
            category = TweakCategory.PRIVACY,
            applyCommands = listOf(
                "settings put global private_dns_mode hostname",
                "settings put global private_dns_spec 1dot1dot1dot1.cloudflare-dns.com"
            ),
            revertCommands = listOf(
                "settings put global private_dns_mode opportunistic",
                "settings delete global private_dns_spec"
            ),
            verificationCommand =
                "[ \"\$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"\$(settings get global private_dns_spec)\" = \"1dot1dot1dot1.cloudflare-dns.com\" ]",
            manualSteps = listOf(
                "Open Network and internet settings.",
                "Tap Private DNS.",
                "Choose Private DNS provider hostname.",
                "Enter 1dot1dot1dot1.cloudflare-dns.com."
            )
        ),
        WifiTweak(
            id = "private_dns_google",
            title = "Private DNS: Google",
            description = "Reliable DNS-over-TLS using dns.google.",
            icon = Icons.Rounded.Public,
            type = TweakType.SHIZUKU_OR_GUIDE,
            category = TweakCategory.PRIVACY,
            applyCommands = listOf(
                "settings put global private_dns_mode hostname",
                "settings put global private_dns_spec dns.google"
            ),
            revertCommands = listOf(
                "settings put global private_dns_mode opportunistic",
                "settings delete global private_dns_spec"
            ),
            verificationCommand =
                "[ \"\$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"\$(settings get global private_dns_spec)\" = \"dns.google\" ]",
            manualSteps = listOf(
                "Open Network and internet settings.",
                "Tap Private DNS.",
                "Choose Private DNS provider hostname.",
                "Enter dns.google."
            )
        ),
        WifiTweak(
            id = "private_dns_quad9",
            title = "Private DNS: Quad9",
            description = "Strong privacy with malware filtering on dns.quad9.net.",
            icon = Icons.Rounded.Shield,
            type = TweakType.SHIZUKU_OR_GUIDE,
            category = TweakCategory.PRIVACY,
            applyCommands = listOf(
                "settings put global private_dns_mode hostname",
                "settings put global private_dns_spec dns.quad9.net"
            ),
            revertCommands = listOf(
                "settings put global private_dns_mode opportunistic",
                "settings delete global private_dns_spec"
            ),
            verificationCommand =
                "[ \"\$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"\$(settings get global private_dns_spec)\" = \"dns.quad9.net\" ]",
            manualSteps = listOf(
                "Open Network and internet settings.",
                "Tap Private DNS.",
                "Choose Private DNS provider hostname.",
                "Enter dns.quad9.net."
            )
        ),
        WifiTweak(
            id = "private_dns_adguard",
            title = "Private DNS: AdGuard",
            description = "Blocks many ads and trackers using dns.adguard-dns.com.",
            icon = Icons.Rounded.PrivacyTip,
            type = TweakType.SHIZUKU_OR_GUIDE,
            category = TweakCategory.PRIVACY,
            applyCommands = listOf(
                "settings put global private_dns_mode hostname",
                "settings put global private_dns_spec dns.adguard-dns.com"
            ),
            revertCommands = listOf(
                "settings put global private_dns_mode opportunistic",
                "settings delete global private_dns_spec"
            ),
            verificationCommand =
                "[ \"\$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"\$(settings get global private_dns_spec)\" = \"dns.adguard-dns.com\" ]",
            manualSteps = listOf(
                "Open Network and internet settings.",
                "Tap Private DNS.",
                "Choose Private DNS provider hostname.",
                "Enter dns.adguard-dns.com."
            )
        ),
        WifiTweak(
            id = "manual_mac_randomization",
            title = "Use randomized MAC",
            description = "Prevents hotspots and venues from tracking one static Wi-Fi identity.",
            icon = Icons.Rounded.GppGood,
            type = TweakType.MANUAL_GUIDE,
            category = TweakCategory.PRIVACY,
            manualSteps = listOf(
                "Open the current Wi-Fi network details.",
                "Open Privacy or MAC address type.",
                "Choose randomized MAC."
            )
        ),
        WifiTweak(
            id = "manual_forget_open_networks",
            title = "Forget risky open networks",
            description = "Clean out old open hotspots so the phone does not keep probing for them.",
            icon = Icons.Rounded.AutoAwesome,
            type = TweakType.MANUAL_GUIDE,
            category = TweakCategory.PRIVACY,
            manualSteps = listOf(
                "Open Saved networks.",
                "Forget old airport, hotel, and cafe hotspots you no longer need."
            )
        ),
        WifiTweak(
            id = "manual_metered_hotspot",
            title = "Mark noisy hotspots as metered",
            description = "A great fallback when one network should stay connected but not burn bandwidth in the background.",
            icon = Icons.Rounded.BatterySaver,
            type = TweakType.MANUAL_GUIDE,
            category = TweakCategory.POWER,
            manualSteps = listOf(
                "Open the hotspot's Wi-Fi details.",
                "Find Network usage or Metered.",
                "Set it to Metered."
            )
        )
    )

    val profiles: List<WifiOptimizationProfile> = listOf(
        WifiOptimizationProfile(
            id = "profile_gaming",
            title = "Gaming Pro",
            description = "Lowest latency possible. Disables power saving and forces high performance.",
            icon = Icons.Rounded.SportsEsports,
            tweakIds = listOf("low_latency_mode", "scan_throttle", "data_stall_logic"),
            accentLabel = "Pro Performance"
        ),
        WifiOptimizationProfile(
            id = "profile_fast_lane",
            title = "Fast Lane",
            description = "Roaming-first profile for mesh routers and busy environments.",
            icon = Icons.Rounded.Speed,
            tweakIds = listOf("scan_throttle", "aggressive_roaming", "mobile_data_always_on"),
            accentLabel = "Speed"
        ),
        WifiOptimizationProfile(
            id = "profile_stability",
            title = "Steady Link",
            description = "Best when Wi-Fi drops in the background or sticks to a bad access point.",
            icon = Icons.Rounded.NetworkCheck,
            tweakIds = listOf("avoid_bad_wifi", "suspend_optimizations", "ip_reachability"),
            accentLabel = "Stability"
        ),
        WifiOptimizationProfile(
            id = "profile_privacy",
            title = "Ghost Mode",
            description = "Maximum privacy with MAC randomization and secure DNS defaults.",
            icon = Icons.Rounded.Shield,
            tweakIds = listOf("manual_mac_randomization", "private_dns_cloudflare", "verbose_logging"),
            accentLabel = "Privacy"
        )
    )

    fun byId(id: String): WifiTweak? = tweaks.firstOrNull { it.id == id }
}
