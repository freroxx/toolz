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

package com.frerox.toolz.ui.screens.network

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.network.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.screens.network.suite.NetCard
import com.frerox.toolz.ui.screens.network.suite.healthTint
import com.frerox.toolz.ui.screens.network.suite.NetPill
import com.frerox.toolz.ui.screens.network.suite.NetTokens
import com.frerox.toolz.ui.screens.network.suite.ScoreArc
import com.frerox.toolz.ui.screens.network.suite.SectionLabel
import com.frerox.toolz.ui.screens.network.suite.StatTile
import com.frerox.toolz.ui.screens.network.components.NetworkConsoleView
import com.frerox.toolz.ui.screens.network.suite.MiniMetric
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.ui.components.fadingEdges
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.DateFormat
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BenchmarkSelectionSheet(
    state: WifiTweaksUiState,
    providers: List<Triple<String, String, String>>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.st_WifiTweaksScreen_select_benchmark), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(providers) { (id, name, host) ->
                    val selected = id in state.selectedBenchmarkProviders
                    Surface(
                        onClick = { onToggle(id, !selected) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(name, fontWeight = FontWeight.Bold)
                                Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(checked = selected, onCheckedChange = { onToggle(id, it) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiagnosticLogSheet(
    logs: List<DiagnosticLog>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.st_WifiTweaksScreen_diag_terminal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                color = Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(12.dp).fadingEdges(top = 16.dp, bottom = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(logs) { log ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "[${log.tag}]",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (log.level) {
                                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                    LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                                    LogLevel.SUCCESS -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PermissionGate(onGrant: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = NetTokens.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Nearby Wi-Fi permission needed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android treats Wi-Fi scans as location-sensitive data. Grant access so the analyzer, channel advisor, and live diagnostics can work.\n\nToolz declares NEARBY_WIFI_DEVICES with neverForLocation — your location is never collected, only nearby SSIDs are read to map spectrum.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onGrant, shape = RoundedCornerShape(18.dp)) {
                Text("Grant access")
            }
        }
    }
}

@Composable
private fun DisabledServiceCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit
) {
    Card(
        
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Warning,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onPrimary) {
                Text(primaryLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ServiceWarningCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit
) {
    Card(
        
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Info,
                null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onPrimary) {
                Text(primaryLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OverviewTab(
    state: WifiTweaksUiState,
    onScan: () -> Unit,
    onFixConnection: () -> Unit,
    onReset: () -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onOpenWifiSettings: () -> Unit,
    extraCards: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(NetTokens.SpacingL)
    ) {
        item { SectionLabel(stringResource(R.string.st_WifiTweaksScreen_7c4d).uppercase()) }

        // ── HERO ────────────────────────────────────────────────────────────
        item {
            NetCard(contentPadding = NetTokens.SpacingXL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (!state.networkConfig.isConnected) "Not connected" else state.currentSsid,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            when {
                                !state.networkConfig.isConnected -> "Connect to Wi-Fi to see details"
                                else -> "${state.networkConfig.wifiStandard} · ${state.networkConfig.band}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(NetTokens.SpacingM))
                    ScoreArc(score = state.advice.healthScore)
                }

                if (state.advice.summary.isNotBlank()) {
                    Text(state.advice.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingS)) {
                    StatTile("Signal", "${state.currentRssi}", subvalue = "dBm", modifier = Modifier.weight(1f))
                    StatTile("Link", "${state.networkConfig.linkSpeed}", subvalue = "Mbps", modifier = Modifier.weight(1f))
                    StatTile(
                        "Channel",
                        state.networkConfig.channel.takeIf { it != 0 }?.toString() ?: "—",
                        subvalue = state.networkConfig.band.takeIf { it != "-" && it != "Unknown" },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingS)) {
                    Button(
                        onClick = onScan,
                        enabled = !state.isScanning,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = NetTokens.SpacingXL, vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isScanning) "Scanning…" else stringResource(R.string.st_WifiTweaksScreen_c3d4))
                    }
                    FilledTonalIconButton(
                        onClick = onOpenWifiSettings,
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.st_WifiTweaksScreen_e5f6))
                    }
                }
            }
        }

        // ── QUICK FIXES (P0 diagnose-first) ─────────────────────────────────
        val hasAdvice = state.advice.recommendation.isNotBlank() &&
            !state.advice.recommendation.startsWith("Scan nearby")
        // Prefer diagnose-list over raw thresholds so card never shows without actionable fix
        val diagnoseEmpty = state.networkConfig.isThrottlingEnabled.not() &&
            !(state.currentRssi < -70 && state.currentRssi > -100) &&
            !(state.privateDnsHost.isBlank() && state.privateDnsMode.equals("Automatic", true))
        if (!diagnoseEmpty || hasAdvice || state.stability.packetLossRate > 0.05 || state.stability.jitterMs > 15.0) {
            item {
                NetCard(
                    title = stringResource(R.string.st_WifiTweaksScreen_2b8a),
                    subtitle = state.advice.recommendation.ifBlank { "Latency or loss above healthy thresholds." },
                    icon = Icons.Rounded.AutoFixHigh,
                    trailing = null
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingS)) {
                        FilledTonalButton(
                            onClick = onFixConnection,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run fixes")
                        }
                        OutlinedButton(
                            onClick = onReset,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Reset all")
                        }
                    }
                }
            }
        }

        // ── STABILITY ───────────────────────────────────────────────────────
        item {
            NetCard(title = "Stability", icon = Icons.Rounded.QueryStats, trailing = {
                state.stability.publicPingMs?.let {
                    NetPill("${it} ms public", emphasized = it < 80)
                }
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingS)) {
                    StatTile("Gateway", state.stability.gatewayPingMs?.toString() ?: "—", subvalue = "ms", modifier = Modifier.weight(1f))
                    StatTile("DNS", state.stability.dnsPingMs?.toString() ?: "—", subvalue = "ms", modifier = Modifier.weight(1f))
                    StatTile("Jitter", "%.0f".format(state.stability.jitterMs), subvalue = "ms", modifier = Modifier.weight(1f))
                }
                PingSparkline(history = state.pingHistory, modifier = Modifier.fillMaxWidth().height(56.dp))
                Text(
                    "${state.stability.history.count { it > 0 }}/${state.stability.history.size} pings ok · loss ${"%.0f".format(state.stability.packetLossRate * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── THIS NETWORK ────────────────────────────────────────────────────
        if (state.networkConfig.isConnected) {
            item {
                NetCard(title = "This network", icon = Icons.Rounded.Settings, subtitle = state.currentSsid) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailRowNet("IP address", state.networkConfig.ip)
                        DetailRowNet("Subnet", state.networkConfig.subnet)
                        DetailRowNet("Gateway", state.networkConfig.gateway)
                        DetailRowNet("DNS 1", state.networkConfig.dns1)
                        if (state.networkConfig.dns2 != "-") DetailRowNet("DNS 2", state.networkConfig.dns2)
                        DetailRowNet("BSSID", state.networkConfig.bssid)
                        val widthLabel = state.networkConfig.channelWidthMhz.takeIf { it != 20 }?.let { "${it}MHz" } ?: "20MHz"
                        DetailRowNet("Channel", "Ch ${state.networkConfig.channel} · $widthLabel")
                        if (state.networkConfig.privateDnsActive) {
                            DetailRowNet("Private DNS", state.networkConfig.privateDnsServerName)
                        }
                    }
                }
            }
        }

        // ── LIVE SIGNAL ─────────────────────────────────────────────────────
        item {
            NetCard(
                title = "Live signal",
                icon = Icons.Rounded.Wifi,
                trailing = {
                    Switch(
                        checked = state.audioFeedbackEnabled,
                        onCheckedChange = onToggleAudio,
                        thumbContent = {
                            Icon(
                                if (state.audioFeedbackEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(NetTokens.InnerShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    SignalHistoryChart(history = state.rssiHistory, modifier = Modifier.fillMaxSize().padding(12.dp))
                    NetPill(
                        "peak ${state.rssiHistory.maxByOrNull { it.rssi }?.rssi ?: -100} dBm",
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    )
                }
            }
        }

        if (extraCards != null) {
            item { Column(verticalArrangement = Arrangement.spacedBy(NetTokens.SpacingL)) { extraCards() } }
        }
    }
}

/** Tiny single-color ping trend; dashes when data is insufficient. */
@Composable
private fun PingSparkline(history: List<Long>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.clip(NetTokens.InnerShape)) {
        val valid = history.filter { it > 0 }.takeLast(40)
        if (valid.size < 2) {
            drawLine(
                color = lineColor.copy(alpha = 0.25f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            return@Canvas
        }
        val minV = valid.min().toFloat()
        val maxV = valid.max().coerceAtLeast((minV + 20).toLong()).toFloat()
        val range = (maxV - minV).coerceAtLeast(1f)
        val path = Path()
        valid.forEachIndexed { i, v ->
            val x = i.toFloat() / (valid.lastIndex.coerceAtLeast(1)) * size.width
            val y = size.height - (((v.toFloat() - minV) / range).coerceIn(0f, 1f)) * size.height * 0.86f - size.height * 0.07f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnalyzerTab(
    state: WifiTweaksUiState,
    onScan: () -> Unit,
    onSortSelected: (WifiScanSortMode) -> Unit,
    onToggleHidden: (Boolean) -> Unit,
    onSelectAP: (WifiScanResult) -> Unit
) {
    val sorts = listOf(
        WifiScanSortMode.SIGNAL to "Signal",
        WifiScanSortMode.CHANNEL to "Channel",
        WifiScanSortMode.SECURITY to "Security",
        WifiScanSortMode.NAME to "Name"
    )
    var ssidFilter by rememberSaveable { mutableStateOf("ALL") }
    var bandFilter by rememberSaveable { mutableStateOf("ALL") }

    // Single source filtered+sorted (P3 fix) — computed outside LazyColumn
    val filteredResults = run {
        state.scanResults.filter {
            (ssidFilter == "ALL" || it.ssid == ssidFilter) &&
            (bandFilter == "ALL" || it.band.startsWith(bandFilter))
        }.sortedWith(
            when (state.scanSortMode) {
                WifiScanSortMode.SIGNAL -> compareByDescending<WifiScanResult> { it.rssi }
                WifiScanSortMode.CHANNEL -> compareBy<WifiScanResult> { it.channel }.thenByDescending { it.rssi }
                WifiScanSortMode.SECURITY -> compareBy<WifiScanResult> { it.security }.thenByDescending { it.rssi }
                WifiScanSortMode.NAME -> compareBy<WifiScanResult> { it.ssid.lowercase() }
            }
        )
    }
    val openCount = state.scanResults.count { it.security == "Open" }
    val recommended = state.congestion.filter { it.isRecommended }

    LazyColumn(
        modifier = Modifier.fillMaxSize().fadingEdges(top = 16.dp, bottom = 40.dp),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(NetTokens.SpacingL)
    ) {
        item { SectionLabel(stringResource(R.string.st_WifiTweaksScreen_7e8f).uppercase()) }

        // HERO — single spectrum (M3 Expressive, no duplication)
        item {
            NetCard(
                title = "${state.scanResults.size} networks in range",
                subtitle = state.lastScanTimestamp?.let { "Last scan ${DateFormat.getTimeInstance(DateFormat.SHORT).format(it)}" } ?: "No scan yet — tap Scan",
                icon = Icons.Rounded.Wifi,
                trailing = {
                    FilledTonalButton(onClick = onScan, shape = RoundedCornerShape(50), enabled = !state.isScanning) {
                        if (state.isScanning) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isScanning) stringResource(R.string.st_Net_Scanning) else stringResource(R.string.st_Net_Scan))
                    }
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                        .clip(NetTokens.InnerShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    SpectrumVisualizer(results = state.scanResults, currentBssid = state.networkConfig.bssid)
                    if (state.isScanning) ScanningPulse()
                    if (state.scanResults.isEmpty() && !state.isScanning) {
                        Text(
                            "Spectrum empty — scan to map",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                if (recommended.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recommended.forEach { rec ->
                            AssistChip(
                                onClick = {},
                                label = { Text("Ch ${rec.channel} • ${rec.band} ✓") },
                                leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            )
                        }
                    }
                } else if (state.isScanning) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.st_Net_ScanningDots)) }, enabled = false)
                }
                // Channel utilization moved into hero as pill row — no second spectrum card
                if (state.congestion.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.st_Net_ChannelUtil), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.congestion.sortedByDescending { it.networkCount }.take(4).forEach { CongestionRow(it) }
                        if (state.congestion.size > 4) Text("+${state.congestion.size - 4} more channels", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // CONTROLS — simple, 48dp chips, single row per group
        item {
            NetCard(
                title = "Filters",
                subtitle = "${filteredResults.size} shown • ${state.scanResults.size} total",
                icon = Icons.Rounded.Tune
            ) {
                // SSID chips (max 4 + All)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ssidFilter == "ALL", onClick = { ssidFilter = "ALL" }, label = { Text(stringResource(R.string.st_Net_All)) }, shape = RoundedCornerShape(50))
                    state.scanResults.map { it.ssid }.distinct().filter { it.isNotBlank() }.take(4).forEach { ssid ->
                        FilterChip(selected = ssidFilter == ssid, onClick = { ssidFilter = ssid }, label = { Text(ssid, maxLines = 1) }, shape = RoundedCornerShape(50))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sorts.forEach { (mode, label) ->
                        FilterChip(selected = state.scanSortMode == mode, onClick = { onSortSelected(mode) }, label = { Text(label) }, shape = RoundedCornerShape(50))
                    }
                    FilterChip(selected = state.showHiddenNetworks, onClick = { onToggleHidden(!state.showHiddenNetworks) }, label = { Text(stringResource(R.string.st_Net_Hidden)) }, shape = RoundedCornerShape(50))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All bands", "2.4" to "2.4 GHz", "5" to "5 GHz", "6" to "6 GHz").forEach { (key, label) ->
                        FilterChip(selected = bandFilter == key, onClick = { bandFilter = key }, label = { Text(label) }, shape = RoundedCornerShape(50))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Networks", "${state.scanResults.size}", modifier = Modifier.weight(1f))
                    StatTile("Open", "$openCount", tint = if (openCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    StatTile("Hidden", "${state.scanResults.count { it.isHidden }}", modifier = Modifier.weight(1f))
                }
            }
        }

        if (filteredResults.isEmpty() && !state.isScanning) {
            item {
                NetCard(contentPadding = NetTokens.SpacingXL) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        Text(stringResource(R.string.st_Net_NoNetworksHint), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.st_Net_TapScanHint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        items(filteredResults, key = { it.bssid }) { result ->
            NetworkResultCard(result = result, onClick = { onSelectAP(result) })
        }

        // Security as tonal banner (not full card) — expressive, non-intrusive
        item { SecurityAuditCard(state = state) }

        item { ExportActionsCard() }
    }
}

@Composable
private fun ChannelSpectrumCard(state: WifiTweaksUiState) {
    Card(
        
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.st_Net_SpectrumMap), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("${state.scanResults.size} networks • ${state.congestion.count { it.isRecommended }} recommended", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Rounded.Hub, null, modifier = Modifier.padding(10.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.35f)).padding(12.dp)) {
                SpectrumVisualizer(results = state.scanResults, currentBssid = state.networkConfig.bssid)
                if (state.isScanning) ScanningPulse()
            }
            // Expressive band pills
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.congestion.filter { it.isRecommended }.forEach { rec ->
                    AssistChip(
                        onClick = {},
                        label = { Text("Ch ${rec.channel} • ${rec.band} ✓") },
                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    )
                }
                if (state.congestion.none { it.isRecommended }) {
                    AssistChip(onClick = {}, label = { Text("Scanning…") }, enabled = false)
                }
            }
        }
    }
}

@Composable
private fun ExportActionsCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val vm: WifiTweaksViewModel = hiltViewModel()
    Card(shape = NetTokens.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(vm.exportScanCsv()))
                    android.widget.Toast.makeText(context, "CSV copied", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.st_Net_CopyCsv))
            }
            FilledTonalButton(
                onClick = {
                    // P5 share intent + clipboard fallback
                    val json = vm.exportDiagnosticJson()
                    clipboard.setText(AnnotatedString(json))
                    runCatching {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(android.content.Intent.EXTRA_TEXT, json)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Toolz network diagnostic")
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share diagnostic").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        android.widget.Toast.makeText(context, "JSON copied", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.st_Net_ShareJson))
            }
        }
    }
}

@Composable
private fun SecurityAuditCard(state: WifiTweaksUiState) {
    val open = state.scanResults.count { it.security == "Open" }
    val wpa3 = state.scanResults.count { it.security.contains("WPA3") }
    val score = when {
        state.scanResults.isEmpty() -> null
        open == 0 && wpa3 > 0 -> 92
        open == 0 -> 78
        open <= 2 -> 55
        else -> 30
    }
    Card(
        
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = if ((score ?: 100) < 50) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if ((score ?: 100) >= 70) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(if ((score ?:100) >=70) Icons.Rounded.VerifiedUser else Icons.Rounded.Warning, null, tint = if ((score ?:100) >=70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.st_Net_SecurityAudit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                Text(
                    when {
                        state.scanResults.isEmpty() -> "Scan to audit nearby networks."
                        open == 0 -> "No open networks nearby. ${wpa3} WPA3 networks found."
                        else -> "$open open networks nearby — avoid auto-joining."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (score != null) Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (score >=70) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SpectrumVisualizer(results: List<WifiScanResult>, currentBssid: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        val minFreq = 2400f
        val maxFreq = 6000f 
        
        // Draw baseline
        drawLine(Color.Gray.copy(alpha = 0.15f), Offset(0f, height), Offset(width, height), strokeWidth = 1.dp.toPx())

        // Draw frequency guides
        val guides = listOf("2.4G" to 2442f, "5G" to 5500f, "6G" to 5950f)
        guides.forEach { (text, freq) ->
            val x = ((freq - minFreq) / (maxFreq - minFreq)) * width
            if (x in 0f..width) {
                drawLine(Color.Gray.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
                val layout = textMeasurer.measure(text, labelStyle)
                drawText(layout, color = Color.Gray.copy(alpha = 0.4f), topLeft = Offset(x - layout.size.width/2, height + 4.dp.toPx()))
            }
        }

        results.sortedBy { it.rssi }.forEach { ap ->
            val normalizedFreq = (ap.frequency - minFreq) / (maxFreq - minFreq)
            if (normalizedFreq in 0f..1f) {
                val centerX = normalizedFreq * width
                val arcHeight = ((ap.rssi + 100f).coerceIn(0f, 70f) / 70f) * height * 0.85f
                
                val arcWidth = when {
                    ap.frequency < 3000 -> width * 0.14f // 2.4GHz
                    ap.frequency < 5900 -> width * 0.08f // 5GHz
                    else -> width * 0.06f // 6GHz
                }

                val path = Path().apply {
                    moveTo(centerX - arcWidth/2, height)
                    quadraticTo(centerX, height - arcHeight, centerX + arcWidth/2, height)
                    close()
                }
                
                val isCurrent = ap.bssid == currentBssid
                val isHidden = ap.isHidden
                val isSecure = ap.security.contains("WPA", ignoreCase = true) || ap.security.contains("SAE", ignoreCase = true)
                
                val baseColor = when {
                    isCurrent -> primaryColor
                    isHidden -> Color.Gray
                    !isSecure -> errorColor
                    else -> secondaryColor
                }
                
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        listOf(baseColor.copy(alpha = if (isCurrent) 0.5f else 0.2f), Color.Transparent)
                    )
                )
                
                drawPath(
                    path = path,
                    color = baseColor.copy(alpha = if (isCurrent) 0.9f else 0.4f),
                    style = Stroke(width = (if (isCurrent) 2.dp else 1.dp).toPx(), cap = StrokeCap.Round)
                )
                
                // SSID Label for significant signals
                if (ap.rssi > -75 || isCurrent) {
                    val label = if (isHidden) "[Hidden]" else ap.ssid
                    val layout = textMeasurer.measure(label, labelStyle.copy(color = baseColor))
                    if (centerX + layout.size.width/2 < width && centerX - layout.size.width/2 > 0) {
                        drawText(
                            layout, 
                            color = baseColor, 
                            topLeft = Offset(centerX - layout.size.width/2, height - arcHeight - 14.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanningPulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
    )
}

@Composable
internal fun ProfilesTab(
    state: WifiTweaksUiState,
    onBindShizuku: () -> Unit,
    onApplyProfile: (WifiOptimizationProfile) -> Unit,
    onApplyTweak: (WifiTweak) -> Unit,
    onUndoTweak: (WifiTweak) -> Unit
) {
    val groupedTweaks = remember(state.tweaks) {
        state.tweaks.filter { !it.id.startsWith("private_dns_") }.groupBy { it.category }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 40.dp),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ShizukuCockpit(state = state, onBindShizuku = onBindShizuku)
        }

        item {
            SectionLabel("READY-TO-USE PROFILES")
            Spacer(Modifier.height(4.dp))
        }
        items(state.profiles, key = { "profile_${it.id}" }) { profile ->
            ProfileCard(
                profile = profile,
                active = profile.tweakIds.all { state.tweakResults[it]?.isApplied == true },
                appliedCount = profile.tweakIds.count { state.tweakResults[it]?.isApplied == true },
                totalCount = profile.tweakIds.size,
                onApply = { onApplyProfile(profile) }
            )
        }
        item {
            SectionLabel("INDIVIDUAL TWEAKS")
            Spacer(Modifier.height(4.dp))
        }

        groupedTweaks.forEach { (category, tweaks) ->
            item {
                SectionLabel(categoryTitle(category).uppercase())
            }
            items(tweaks, key = { it.id }) { tweak ->
                TweakCard(
                    tweak = tweak,
                    result = state.tweakResults[tweak.id],
                    shizukuReady = state.shizukuStatus.isServiceReady,
                    onApply = { onApplyTweak(tweak) },
                    onUndo = { onUndoTweak(tweak) }
                )
            }
        }
    }
}

@Composable
internal fun DnsEngineTab(
    state: WifiTweaksUiState,
    onBenchmark: () -> Unit,
    onApplyTweak: (WifiTweak) -> Unit,
    onRestoreAutomatic: () -> Unit,
    onApplyCustom: (String) -> Unit,
    onShowSelection: () -> Unit
) {
    val dnsTweaks = state.tweaks.filter { it.id.startsWith("private_dns_") }
    var customHost by remember { mutableStateOf("") }
    var dnsFilter by rememberSaveable { mutableStateOf("ALL") }
    val dnsCategoryMap = remember { mapOf(
        "adguard" to "PRIVACY", "quad9" to "SECURITY", "cloudflare" to "SPEED",
        "google" to "SPEED", "nextdns" to "PRIVACY", "mullvad" to "PRIVACY",
        "opendns" to "SECURITY", "cleanbrowsing" to "FAMILY", "controld" to "PRIVACY"
    ) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 40.dp),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            NetCard(
                title = stringResource(R.string.st_WifiTweaksScreen_a7b8),
                subtitle = "${state.selectedBenchmarkProviders.size} servers • tap Run to measure",
                icon = Icons.Rounded.Bolt,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShowSelection) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Select servers")
                        }
                        FilledTonalButton(
                            onClick = onBenchmark,
                            enabled = !state.isBenchmarkingDns,
                            shape = RoundedCornerShape(50)
                        ) {
                            if (state.isBenchmarkingDns) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Run")
                            }
                        }
                    }
                }
            ) {
                if (state.dnsBenchmarkResults.isEmpty()) {
                    Text(
                        "No results yet. Choose providers and run a benchmark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.dnsBenchmarkResults.sortedBy { it.latencyMs ?: 9999L }.forEach { result ->
                            DnsBenchmarkRow(result)
                        }
                    }
                }
            }
        }

        item {
            NetCard(
                title = stringResource(R.string.st_WifiTweaksScreen_c9d0),
                subtitle = "Custom DoT hostname",
                icon = Icons.Rounded.Public
            ) {
                OutlinedTextField(
                    value = customHost,
                    onValueChange = { customHost = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("your-id.dns.nextdns.io") },
                    supportingText = { Text("NextDNS users: paste your assigned hostname from nextdns.io → Setup") },
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        IconButton(onClick = { onApplyCustom(customHost) }, enabled = customHost.isNotBlank()) {
                            Icon(Icons.Rounded.Check, contentDescription = "Apply")
                        }
                    }
                )
            }
        }

        item {
            NetCard(
                title = stringResource(R.string.st_WifiTweaksScreen_e1f2),
                subtitle = "Current: ${state.privateDnsMode} ${if (state.privateDnsHost.isNotBlank()) "• ${state.privateDnsHost}" else ""} — One tap to switch",
                icon = Icons.Rounded.Public,
                trailing = {
                    TextButton(
                        onClick = onRestoreAutomatic,
                        enabled = state.shizukuStatus.isServiceReady
                    ) { Text("Auto") }
                }
            ) {
                if (!state.shizukuStatus.isServiceReady) {
                    Text(
                        "Enable Shizuku to apply presets system-wide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val groups = listOf(
                    "Secure" to listOf("private_dns_quad9", "private_dns_adguard"),
                    "Fast" to listOf("private_dns_cloudflare", "private_dns_google"),
                    "Family" to emptyList<String>()
                )
                // P4 DNS category chips (filter presets)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL", "SPEED", "PRIVACY", "SECURITY", "FAMILY").forEach { cat ->
                        FilterChip(
                            selected = dnsFilter == cat,
                            onClick = { dnsFilter = cat },
                            label = { Text(cat) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // All known presets, ordered: Secure → Fast → Family (extras fall into Fast)
                    val ordered = dnsTweaks.filter { tw ->
                        if (dnsFilter == "ALL") true else {
                            val key = tw.id.substringAfter("private_dns_")
                            dnsCategoryMap[key] == dnsFilter || tw.id.contains(dnsFilter.lowercase())
                        }
                    }.sortedBy { tw ->
                        when {
                            tw.id.contains("quad9") || tw.id.contains("adguard") -> 0
                            tw.id.contains("cloudflare") || tw.id.contains("google") -> 1
                            else -> 2
                        }
                    }
                    ordered.forEachIndexed { idx, tweak ->
                        val prevGroup = ordered.getOrNull(idx - 1)?.let { groupOf(it.id) }
                        val thisGroup = groupOf(tweak.id)
                        if (idx > 0 && prevGroup != thisGroup) {
                            Text(
                                thisGroup.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = NetTokens.SpacingS)
                            )
                        }
                        PresetDnsRow(
                            tweak = tweak,
                            active = state.tweakResults[tweak.id]?.isApplied == true,
                            enabled = state.shizukuStatus.isServiceReady,
                            onApply = { onApplyTweak(tweak) }
                        )
                    }
                }
            }
        }
    }
}

private fun groupOf(id: String): String = when {
    id.contains("quad9") || id.contains("adguard") -> "secure"
    id.contains("cloudflare") || id.contains("google") -> "fast"
    else -> "more"
}

@Composable
private fun DnsBenchmarkRow(result: WifiDnsBenchmarkResult) {
    Surface(
        shape = NetTokens.InnerShape,
        color = if (result.isRecommended) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NetTokens.SpacingM, vertical = NetTokens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingM)
        ) {
            if (result.isRecommended) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                    Text(
                        "★ BEST",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                NetPill(if (result.dotLatencyMs != null) "DoT ✓" else "DoT —")
            }
            Text(
                text = result.latencyMs?.let { "${it}ms" } ?: "Timeout",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    result.latencyMs == null -> MaterialTheme.colorScheme.error
                    result.latencyMs < 50 -> healthTint(90)
                    result.latencyMs < 100 -> healthTint(60)
                    else -> healthTint(30)
                }
            )
        }
    }
}

@Composable
internal fun DiagnosticsTab(
    state: WifiTweaksUiState,
    onCopySummary: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenDevSettings: () -> Unit,
    onRunSpeedTest: () -> Unit,
    onCancelSpeedTest: () -> Unit = {},
    onRunTraceRoute: (String) -> Unit,
    extraCards: (@Composable () -> Unit)? = null
) {
    var traceTarget by remember(state.lastTraceTarget) { mutableStateOf(state.lastTraceTarget) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.st_WifiTweaksScreen_g3h4),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            NetCard(
                title = stringResource(R.string.st_WifiTweaksScreen_i5j6),
                subtitle = if (state.speedTest.isRunning) state.speedTest.phaseLabel else "Full test · download, upload & bufferbloat",
                icon = Icons.Rounded.Speed,
                trailing = {
                    if (state.speedTest.isRunning) {
                        OutlinedButton(onClick = onCancelSpeedTest, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel")
                        }
                    } else {
                        Button(onClick = onRunSpeedTest, shape = RoundedCornerShape(50)) {
                            Text(stringResource(R.string.st_WifiTweaksScreen_g7h8))
                        }
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingL)
                ) {
                    Column {
                        Text(
                            "${state.speedTest.downloadSpeedMbps.roundToInt()}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text("Mbps down", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.speedTest.uploadSpeedMbps > 0 || !state.speedTest.isRunning) {
                        Column {
                            Text(
                                "${state.speedTest.uploadSpeedMbps.roundToInt()}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("up", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val grade = state.speedTest.bloatGrade
                    if (grade != null && !state.speedTest.isRunning) {
                        Spacer(Modifier.weight(1f))
                        StatTile(
                            label = "bufferbloat",
                            value = grade.letter,
                            subvalue = "${state.speedTest.idleLatencyMs ?: "-"}→${state.speedTest.loadedLatencyMs ?: "-"} ms",
                            tint = when (grade) {
                                com.frerox.toolz.data.network.BloatGrade.A_PLUS,
                                com.frerox.toolz.data.network.BloatGrade.A -> MaterialTheme.colorScheme.primary
                                com.frerox.toolz.data.network.BloatGrade.B,
                                com.frerox.toolz.data.network.BloatGrade.C -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { state.speedTest.progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                if (!state.speedTest.isRunning && state.speedTest.error != null) {
                    Text(
                        state.speedTest.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            NetCard(
                title = stringResource(R.string.st_WifiTweaksScreen_i9j0),
                subtitle = if (state.isTracing) "Tracing ${traceTarget} — ${state.traceHops.size} hops" else if (state.traceHops.isNotEmpty()) "${state.traceHops.size} hops • ${state.traceHops.lastOrNull()?.ip ?: ""}" else "Path to a target host",
                icon = Icons.Rounded.Route,
                trailing = {
                    if (state.isTracing) {
                        FilledTonalButton(onClick = {}, enabled = false, shape = RoundedCornerShape(50)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Tracing")
                        }
                    } else {
                        FilledTonalButton(onClick = { onRunTraceRoute(traceTarget) }, shape = RoundedCornerShape(50)) {
                            Text("Trace")
                        }
                    }
                }
            ) {
                OutlinedTextField(
                    value = traceTarget,
                    onValueChange = { traceTarget = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.st_WifiTweaksScreen_k1l2)) },
                    placeholder = { Text("1.1.1.1 or dns.google") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (traceTarget.isNotBlank()) IconButton(onClick = { traceTarget = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                    }
                )
                if (state.isTracing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetPill("Hop ${state.traceHops.size + 1} probing…", emphasized = true)
                        Text("Streaming via ping-TTL (no binary needed)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.traceHops.isEmpty()) {
                    if (state.isTracing) {
                        Text("Probing… first hop is your gateway, then upstream. This takes ~5–8s.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Surface(shape = NetTokens.InnerShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.Route, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Text("Run a trace to see each hop on the path. Requires Shizuku for raw TTL probes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.traceHops.forEach { hop ->
                            Surface(shape = NetTokens.InnerShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(50), color = if (hop.latencyMs == null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(28.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("${hop.hop}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (hop.latencyMs == null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(hop.ip, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (hop.label.isNotBlank() && hop.label != "Hop ${hop.hop}") {
                                            Text(hop.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Text(if (hop.latencyMs == null) "no reply" else hop.method, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(
                                        text = hop.latencyMs?.let { "${it}ms" } ?: "*",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (hop.latencyMs == null) MaterialTheme.colorScheme.error else healthTint((90 - (hop.latencyMs ?: 200).coerceIn(0, 180) / 2).toInt())
                                    )
                                }
                            }
                        }
                        if (!state.isTracing && state.traceHops.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NetPill("${state.traceHops.size} hops", emphasized = true)
                                NetPill(state.traceHops.lastOrNull()?.let { if (it.ip == traceTarget) "Reached" else "Ended" } ?: "Done")
                            }
                        }
                    }
                }
            }
        }

        item {
            NetCard(title = stringResource(R.string.st_WifiTweaksScreen_m3n4), icon = Icons.Rounded.Settings, subtitle = state.currentSsid) {
                ConfigGrid(state.networkConfig)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolzOutlinedExpressiveButton(
                    onClick = onCopySummary,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_WifiTweaksScreen_o5p6))
                }
                ToolzOutlinedExpressiveButton(
                    onClick = onOpenWifiSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.st_WifiTweaksScreen_q7r8))
                }
            }
            ToolzOutlinedExpressiveButton(
                onClick = onOpenDevSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.DeveloperMode, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_WifiTweaksScreen_s9t0))
            }
        }

        if (extraCards != null) {
            item { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { extraCards() } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrafficTab(
    state: WifiTweaksUiState
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 40.dp),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.st_WifiTweaksScreen_u1v2), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.activeProcesses.sumOf { it.rxKbps }.roundToInt()} Kbps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Upload", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.activeProcesses.sumOf { it.txKbps }.roundToInt()} Kbps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (state.activeProcesses.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CloudOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text("No active traffic detected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Requires Shizuku for process mapping", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        } else {
            items(state.activeProcesses.sortedByDescending { it.rxKbps + it.txKbps }) { process ->
                TrafficProcessCard(process)
            }
        }
    }
}

@Composable
private fun TrafficProcessCard(process: ProcessNetworkUsage) {
    Card(
        
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (process.protocol == "UDP") Icons.Rounded.WifiTethering else Icons.Rounded.Lan,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(process.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${process.protocol} • ${process.state.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = process.remoteAddr,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${process.txKbps.roundToInt()} ↑",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${process.rxKbps.roundToInt()} ↓",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ShizukuCockpit(
    state: WifiTweaksUiState,
    onBindShizuku: () -> Unit
) {
    val shizuku = state.shizukuStatus
    val isReady = shizuku.isServiceReady
    NetCard(
        title = if (isReady) "Shizuku connected" else "Shizuku required",
        subtitle = if (isReady) "Privileged operations enabled" else "Needed for system tweaks and DNS",
        icon = if (isReady) Icons.Rounded.Bolt else Icons.Rounded.Lock,
        trailing = {
            if (!isReady) {
                Button(onClick = onBindShizuku, shape = RoundedCornerShape(50)) { Text("Enable") }
            } else {
                NetPill("Active", emphasized = true)
            }
        }
    ) {
        Text(
            if (isReady) "All tweaks and audits are available."
            else "Install and start Shizuku to unlock advanced networking controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NetPill("Binder " + if (shizuku.isReachable) "online" else "offline", emphasized = shizuku.isReachable)
            NetPill("Permission " + if (shizuku.isAuthorized) "granted" else "pending", emphasized = shizuku.isAuthorized)
        }
    }
}
@Composable
private fun ProfileCard(
    profile: WifiOptimizationProfile,
    active: Boolean,
    appliedCount: Int,
    totalCount: Int,
    onApply: () -> Unit
) {
    Surface(
        shape = NetTokens.CardShape,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(NetTokens.SpacingL), verticalArrangement = Arrangement.spacedBy(NetTokens.SpacingM)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingM)) {
                Surface(
                    shape = NetTokens.InnerShape,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            profile.icon,
                            contentDescription = null,
                            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(profile.accentLabel, style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (active) NetPill("ACTIVE", emphasized = true)
            }

            Text(profile.description, style = MaterialTheme.typography.bodySmall, color = if (active) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$appliedCount of $totalCount steps applied",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onApply, shape = RoundedCornerShape(50)) {
                        Text(if (active) "Re-apply" else "Apply profile")
                    }
                }
            }
        }
    }
}

@Composable
private fun TweakCard(
    tweak: WifiTweak,
    result: TweakResult?,
    shizukuReady: Boolean,
    onApply: () -> Unit,
    onUndo: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(tweak.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(tweak.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(tweak.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // P3 fix: single source status pill — no duplicate Ready/Verified, correct tint (not red for unverified)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isRunning = result?.status == TweakStatus.RUNNING
                val isFailed = result?.status == TweakStatus.FAILED
                val isApplied = result?.isApplied == true
                val verified = result?.verified
                val (pillText, pillEmphasized) = when {
                    isRunning -> "Working…" to false
                    isFailed -> (result?.message?.takeIf { it.isNotBlank() } ?: "Failed") to false
                    isApplied && verified == true -> "Active · verified" to true
                    isApplied && verified == null -> "Active · unverified" to false
                    isApplied && verified == false -> "Not active" to false
                    result?.status == TweakStatus.MANUAL -> "Manual" to false
                    else -> "Ready" to false
                }
                val pillColor = when {
                    isFailed -> MaterialTheme.colorScheme.error
                    isApplied && verified == true -> MaterialTheme.colorScheme.primary
                    isApplied && verified == null -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = if (pillEmphasized) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(pillText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (pillEmphasized) MaterialTheme.colorScheme.onSecondaryContainer else pillColor)
                    }
                    if (!isRunning && !isFailed && result?.message?.isNotBlank() == true && pillText != result.message) {
                        Text(result.message!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (tweak.riskNote != null) {
                    Text(
                        tweak.riskNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onApply,
                    enabled = tweak.type == TweakType.MANUAL_GUIDE || shizukuReady || tweak.manualSteps.isNotEmpty(),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(if (tweak.type == TweakType.MANUAL_GUIDE) "Guide" else "Apply", style = MaterialTheme.typography.labelLarge)
                }
                if (tweak.revertCommands.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onUndo,
                        enabled = shizukuReady,
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Undo", style = MaterialTheme.typography.labelLarge)
                    }
                }
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(if (expanded) "Less" else "Details", style = MaterialTheme.typography.labelLarge)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    if (tweak.manualSteps.isNotEmpty()) {
                        Text("Manual path", style = MaterialTheme.typography.labelLarge)
                        tweak.manualSteps.forEachIndexed { index, step ->
                            Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (tweak.applyCommands.isNotEmpty()) {
                        Text("Command path", style = MaterialTheme.typography.labelLarge)
                        tweak.applyCommands.forEach { command ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "adb shell $command",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetDnsRow(
    tweak: WifiTweak,
    active: Boolean,
    enabled: Boolean,
    onApply: () -> Unit
) {
    Surface(
        shape = NetTokens.InnerShape,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (active) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply).let { m -> if (!enabled) m else m }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NetTokens.SpacingM, vertical = NetTokens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingM)
        ) {
            // radio indicator
            Surface(shape = RoundedCornerShape(50), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(18.dp)) {
                if (active) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(50)))
                    }
                }
            }
            Icon(
                imageVector = tweak.icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tweak.title.replace("Private DNS: ", ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = tweak.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (active) Icon(Icons.Rounded.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ConfigGrid(config: NetworkConfigInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailRow("Internal IP", config.ip)
        DetailRow("Gateway", config.gateway)
        DetailRow("Subnet", config.subnet)
        DetailRow("DNS 1", config.dns1)
        DetailRow("DNS 2", config.dns2)
        DetailRow("BSSID", config.bssid)
        DetailRow("Wi-Fi Standard", config.wifiStandard)
        DetailRow("MAC handling", config.macAddress)
        DetailRow("Frequency", "${config.frequency} MHz")
        DetailRow("Private DNS", if (config.privateDnsActive) config.privateDnsServerName else "Automatic / off")
    }
}

@Composable
private fun NetworkResultCard(result: WifiScanResult, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = NetTokens.InnerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NetTokens.SpacingM, vertical = NetTokens.SpacingM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NetTokens.SpacingM)
        ) {
            SignalBars(rssi = result.rssi)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (result.isHidden) "Hidden network" else result.ssid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NetPill(
                        result.security,
                        emphasized = result.security == "Open"
                    )
                    NetPill("Ch ${result.channel.takeIf { it != 0 } ?: "?"} · ${result.band}${if (result.channelWidthMhz > 20) " · ${result.channelWidthMhz}MHz" else ""}")
                }
            }
            Text(
                "${result.rssi}",
                style = MaterialTheme.typography.labelLarge,
                color = healthTint(
                    ((result.rssi + 90) * 100 / 60).coerceIn(0, 100)
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CongestionRow(item: ChannelCongestion) {
    val accent = if (item.isRecommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = if (item.isRecommended) 0.12f else 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Channel ${item.channel}  •  ${item.band}",
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${item.networkCount} networks nearby • avg ${item.averageRssi.roundToInt()} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.isRecommended) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Recommended") },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = accent.copy(alpha = 0.16f),
                        disabledLabelColor = accent
                    )
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
private fun InsightChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun StatusPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SignalHistoryChart(
    history: List<com.frerox.toolz.data.network.RssiHistoryPoint>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val minRssi = -100f
        val maxRssi = -30f
        val points = history.takeLast(60)
        val linePath = Path()

        points.forEachIndexed { index, point ->
            val x = (index.toFloat() / (points.lastIndex.coerceAtLeast(1))) * width
            val y = height - ((point.rssi - minRssi) / (maxRssi - minRssi)) * height
            if (index == 0) {
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                listOf(
                    lineColor.copy(alpha = 0.24f),
                    Color.Transparent
                )
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun LinearMeter(strength: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(strength.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
                    )
                )
        )
    }
}

@Composable
private fun SignalBars(rssi: Int) {
    val strength = when {
        rssi >= -50 -> 4
        rssi >= -65 -> 3
        rssi >= -78 -> 2
        rssi >= -88 -> 1
        else -> 0
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(((index + 1) * 6).dp)
                    .clip(RoundedCornerShape(topStart = 999.dp, topEnd = 999.dp))
                    .background(
                        if (index < strength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun APDetailSheet(result: WifiScanResult, onDismiss: () -> Unit, onPing: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(result.ssid, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            
            Column {
                Text("BSSID: ${result.bssid}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Frequency: ${result.frequency} MHz", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailBox("Vendor", lookupVendor(result.bssid), Icons.Rounded.Store, Modifier.weight(1f))
                DetailBox("Distance", "Est. ${calculateDistance(result.rssi, result.frequency)}m", Icons.Rounded.Straighten, Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailBox("Channel", result.channel.toString(), Icons.Rounded.Numbers, Modifier.weight(1f))
                DetailBox("Security", result.security, Icons.Rounded.Shield, Modifier.weight(1f))
            }

            Button(
                onClick = { onPing(result.ssid) }, 
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Rounded.NetworkCheck, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Ping AP (Internal)")
            }
        }
    }
}

@Composable
private fun DetailBox(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun lookupVendor(bssid: String): String {
    val prefix = bssid.take(8).uppercase().replace(":", "")
    return when (prefix) {
        "BCFFC0" -> "Espressif Systems"
        "C05627" -> "TP-Link"
        "001122" -> "Apple"
        "E4956E" -> "Samsung"
        "001E06" -> "Cisco"
        "D807B6" -> "Google"
        "B0BE76" -> "Hewlett Packard"
        else -> "Generic Manufacturer"
    }
}

private fun calculateDistance(rssi: Int, freq: Int): String {
    val exp = (27.55 - (20 * kotlin.math.log10(freq.toDouble())) + kotlin.math.abs(rssi)) / 20.0
    return "%.1f".format(10.0.pow(exp))
}

private fun signalStrengthPercent(rssi: Int): Float {
    return ((rssi + 100).coerceIn(0, 70) / 70f)
}

private fun statusLabel(result: TweakResult?): String {
    return when (result?.status) {
        TweakStatus.RUNNING -> "Working"
        TweakStatus.SUCCESS -> if (result.verified == true) "Verified" else "Applied"
        TweakStatus.FAILED -> "Failed"
        TweakStatus.UNSUPPORTED -> "Locked"
        TweakStatus.MANUAL -> "Manual"
        else -> "Ready"
    }
}

private fun categoryTitle(category: TweakCategory): String {
    return when (category) {
        TweakCategory.PERFORMANCE -> "Performance"
        TweakCategory.STABILITY -> "Stability"
        TweakCategory.PRIVACY -> "Privacy"
        TweakCategory.POWER -> "Power and roaming"
    }
}

internal fun requestShizuku(context: Context) {
    try {
        if (Shizuku.isPreV11()) {
            (context as? Activity)?.requestPermissions(arrayOf("rikka.shizuku.permission.API_V23"), WifiTweaksViewModel.SHIZUKU_CODE)
        } else {
            Shizuku.requestPermission(WifiTweaksViewModel.SHIZUKU_CODE)
        }
    } catch (_: Exception) {
    }
}

internal fun launchSettings(context: Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun SmartFixHeaderCard(
    state: WifiTweaksUiState,
    onFixConnection: () -> Unit
) {
    val fixes = remember(state.currentRssi, state.networkConfig.isThrottlingEnabled) {
        buildList {
            if (state.networkConfig.isThrottlingEnabled) {
                add(
                    SmartFixRecommendation(
                        id = "scan_throttle",
                        title = "Wi-Fi Scan Throttling Active",
                        description = "Android is delaying Wi-Fi scans. Disabling scan throttling makes roaming and discovery instant.",
                        severity = RecommendationSeverity.WARNING,
                        tweakIds = listOf("scan_throttle")
                    )
                )
            }
            if (state.currentRssi < -70 && state.currentRssi > -100) {
                add(
                    SmartFixRecommendation(
                        id = "weak_signal",
                        title = "Weak Signal Recovery",
                        description = "Signal is low (${state.currentRssi} dBm). Enable rapid stall recovery and aggressive AP roaming.",
                        severity = RecommendationSeverity.CRITICAL,
                        tweakIds = listOf("avoid_bad_wifi", "data_stall_logic")
                    )
                )
            }
        }
    }

    if (fixes.isNotEmpty()) {
        ExpressiveCard(
            onClick = onFixConnection,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            "Smart Fix Recommendations",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ) {
                        Text(
                            text = "${fixes.size} Actionable",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                fixes.forEach { fix ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = fix.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = fix.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                ToolzExpressiveButton(
                    onClick = onFixConnection,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply Smart Fixes")
                }
            }
        }
    }
}

@Composable
internal fun SpeedHistoryCard(
    history: List<com.frerox.toolz.data.network.SpeedHistoryEntity>,
    onClear: () -> Unit
) {
    Card(
        
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.st_Net_SpeedHistory), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.st_Net_Runs90d, history.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClear, enabled = history.isNotEmpty()) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Clear history")
                }
            }
            if (history.size < 2) {
                Text(stringResource(R.string.st_Net_RunTrendHint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Sparkline(
                    values = history.reversed().map { it.downloadMbps },
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniMetric("Latest", "${history.first().downloadMbps.roundToInt()}")
                    MiniMetric("Best", "${history.maxOf { it.downloadMbps }.roundToInt()}")
                    MiniMetric("Average", "${history.map { it.downloadMbps }.average().roundToInt()}")
                    val grades = history.mapNotNull { it.bloatGrade }
                    if (grades.isNotEmpty()) MiniMetric("Bloat", grades.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "-")
                }
                // P5 p50/p95 ISP latency (from loadedLatencyMs/idle fallback)
                val latencies = history.mapNotNull { it.loadedLatencyMs ?: it.idleLatencyMs }.sorted()
                if (latencies.isNotEmpty()) {
                    fun pct(p: Double): Long = latencies[((latencies.size - 1).coerceAtLeast(0) * p).toInt()]
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        NetPill("p50 ${pct(0.5)}ms")
                        NetPill("p95 ${pct(0.95)}ms")
                        NetPill("${latencies.size} samples")
                    }
                }
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val maxV = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i.toFloat() / (values.lastIndex.coerceAtLeast(1)) * size.width
            val y = size.height - ((v / maxV).toFloat().coerceIn(0f, 1f)) * size.height * 0.9f - size.height * 0.05f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun DetailRowNet(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
