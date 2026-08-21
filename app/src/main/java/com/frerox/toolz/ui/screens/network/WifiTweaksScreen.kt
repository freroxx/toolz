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
                                    LogLevel.ERROR -> Color(0xFFC84B4B)
                                    LogLevel.WARNING -> Color(0xFFD97D2C)
                                    LogLevel.SUCCESS -> Color(0xFF2E9D66)
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
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
                text = "Android treats Wi-Fi scans as location-sensitive data. Grant access so the analyzer, channel advisor, and live diagnostics can work.",
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
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        )
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
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        )
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
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.st_WifiTweaksScreen_7c4d),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            OverviewHeroCard(
                state = state,
                onScan = onScan,
                onOpenWifiSettings = onOpenWifiSettings
            )
        }
        
        item {
            SmartFixHeaderCard(
                state = state,
                onFixConnection = onFixConnection
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PerformanceTrendCard(state = state)
                }
                Box(modifier = Modifier.weight(1f)) {
                    StabilityMonitorCard(state = state)
                }
            }
        }

        item {
            QuickActionFloatingCard(onFix = onFixConnection, onReset = onReset)
        }

        item {
            InsightStrip(state = state)
        }

        item {
            LiveFeedbackCard(
                state = state,
                onToggleAudio = onToggleAudio
            )
        }

        if (extraCards != null) {
            item { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { extraCards() } }
        }
    }
}

@Composable
private fun PerformanceTrendCard(state: WifiTweaksUiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.st_WifiTweaksScreen_5f6e), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                state.stability.publicPingMs?.let {
                    Text("${it}ms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            
            Box(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                PingHistoryChart(
                    history = state.pingHistory,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PingHistoryChart(
    history: List<Long>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val points = remember(history) { history.takeLast(50) }
    val valid = remember(points) { points.filter { it > 0 } }

    Canvas(modifier = modifier) {
        if (valid.size < 2) {
            // dashed placeholder + empty label handled by caller; draw baseline
            drawLine(
                color = lineColor.copy(alpha = 0.12f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            )
            // faint dots for packet loss
            points.forEachIndexed { i, v ->
                if (v <= 0) {
                    val x = (i.toFloat() / 49f) * size.width
                    drawCircle(color = errorColor.copy(alpha = 0.35f), radius = 2.2.dp.toPx(), center = Offset(x, size.height * 0.72f))
                }
            }
            return@Canvas
        }
        val width = size.width
        val height = size.height
        val minPing = valid.minOrNull()!!.toFloat()
        val maxPing = valid.maxOrNull()!!.coerceAtLeast((minPing + 30).toLong()).toFloat()
        val range = (maxPing - minPing).coerceAtLeast(1f)
        val path = Path()
        valid.forEachIndexed { idx, ping ->
            val x = (idx.toFloat() / (valid.lastIndex.coerceAtLeast(1).toFloat())) * width
            val y = height - ((ping.toFloat() - minPing) / range).coerceIn(0f, 1f) * height * 0.78f - (height * 0.11f)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // area fill first
        val fill = Path().apply { addPath(path); lineTo(width, height); lineTo(0f, height); close() }
        drawPath(fill, brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.18f), Color.Transparent)))
        drawPath(path, color = lineColor, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun QuickActionFloatingCard(onFix: () -> Unit, onReset: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onFix,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.FlashOn, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_WifiTweaksScreen_2b8a))
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.SettingsBackupRestore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_WifiTweaksScreen_4d9c))
            }
        }
    }
}

@Composable
private fun LiveFeedbackCard(
    state: WifiTweaksUiState,
    onToggleAudio: (Boolean) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.st_WifiTweaksScreen_6a1b), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        stringResource(R.string.st_WifiTweaksScreen_1b2c),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.audioFeedbackEnabled,
                    onCheckedChange = onToggleAudio,
                    thumbContent = {
                        Icon(
                            if (state.audioFeedbackEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            null,
                            Modifier.size(12.dp)
                        )
                    }
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                SignalHistoryChart(
                    history = state.rssiHistory,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
                
                // Visual peak indicator
                val peakRssi = state.rssiHistory.maxByOrNull { it.rssi }?.rssi ?: -100
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    StatusPill("Peak", "$peakRssi dBm")
                }
            }
        }
    }
}

@Composable
private fun OverviewHeroCard(
    state: WifiTweaksUiState,
    onScan: () -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    val score = remember(state) { state.advice.healthScore.takeIf { it != 0 } ?: run {
        val rssiBonus = ((state.currentRssi + 100) / 70f * 40f).coerceIn(0f, 40f)
        val stabilityBonus = (1.0 - state.stability.packetLossRate) * 40f
        val jitterBonus = (1.0 - (state.stability.jitterMs / 50.0).coerceIn(0.0, 1.0)) * 20f
        (rssiBonus + stabilityBonus + jitterBonus).roundToInt().coerceIn(0, 100)
    } }
    
    val color = when {
        score >= 75 -> Color(0xFF2E9D66)
        score >= 55 -> Color(0xFFD97D2C)
        else -> Color(0xFFC84B4B)
    }

    ElevatedCard(
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        start = Offset.Zero,
                        end = Offset(1200f, 700f)
                    )
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentSsid,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = state.advice.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    SignalQualityGauge(
                        score = score,
                        rssi = state.currentRssi
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InsightChip(Icons.Rounded.Wifi, "${state.currentRssi} dBm")
                    InsightChip(Icons.Rounded.Speed, "${state.networkConfig.linkSpeed} Mbps")
                    InsightChip(Icons.Rounded.Route, "Ch ${state.networkConfig.channel.takeIf { it != 0 } ?: "-"}")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onScan,
                        shape = RoundedCornerShape(20.dp),
                        enabled = !state.isScanning,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isScanning) "Scanning" else stringResource(R.string.st_WifiTweaksScreen_c3d4), style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onOpenWifiSettings, 
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.st_WifiTweaksScreen_e5f6), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalQualityGauge(score: Int, rssi: Int) {
    val progress = (score / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress, 
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "health_gauge"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color(0xFFC84B4B), Color(0xFFD97D2C), Color(0xFF2E9D66), Color(0xFF2E9D66))
                ),
                startAngle = 140f,
                sweepAngle = 260f * animatedProgress,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            Text("$rssi dBm", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StabilityMonitorCard(state: WifiTweaksUiState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.st_WifiTweaksScreen_3c4d), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StabilityItem("Gateway", state.stability.gatewayPingMs?.toString() ?: "--", "ms")
                StabilityItem("DNS", state.stability.dnsPingMs?.toString() ?: "--", "ms")
                StabilityItem("Public", state.stability.publicPingMs?.toString() ?: "--", "ms")
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StabilityItem("Jitter", "%.1f".format(state.stability.jitterMs), "ms")
                StabilityItem("Packet Loss", "%.1f".format(state.stability.packetLossRate * 100), "%")
            }
        }
    }
}

@Composable
private fun StabilityItem(label: String, value: String, unit: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(2.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightStrip(state: WifiTweaksUiState) {
    ElevatedCard(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.st_WifiTweaksScreen_5d6e), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                text = state.advice.recommendation,
                style = MaterialTheme.typography.bodyLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Strongest: ${state.advice.strongestNetwork}") },
                    leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                        disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Open: ${state.advice.openNetworks}") },
                    leadingIcon = { Icon(Icons.Rounded.Security, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                        disabledLeadingIconContentColor = MaterialTheme.colorScheme.tertiary
                    )
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("Visible: ${state.advice.totalNetworks}") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                        disabledLeadingIconContentColor = MaterialTheme.colorScheme.secondary
                    )
                )
                
                if (state.networkConfig.wifi6ECapable || state.networkConfig.wifi7Capable) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(state.networkConfig.wifiStandard) },
                        leadingIcon = { Icon(Icons.Rounded.Bolt, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                            disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
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
    
    var ssidFilter by remember { mutableStateOf("ALL") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 40.dp),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.st_WifiTweaksScreen_7e8f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        SpectrumVisualizer(
                            results = state.scanResults,
                            currentBssid = state.networkConfig.bssid
                        )
                        if (state.isScanning) {
                            ScanningPulse()
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.st_WifiTweaksScreen_9f0a), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text(
                                text = state.lastScanTimestamp?.let {
                                    "Last scan ${DateFormat.getTimeInstance(DateFormat.SHORT).format(it)}"
                                } ?: "No scan yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(onClick = onScan, shape = RoundedCornerShape(20.dp), enabled = !state.isScanning) {
                            if (state.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isScanning) "Scanning" else "Scan")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = ssidFilter == "ALL",
                            onClick = { ssidFilter = "ALL" },
                            label = { Text("All") },
                            shape = RoundedCornerShape(12.dp)
                        )
                        state.scanResults.map { it.ssid }.distinct().filter { it.isNotBlank() }.take(8).forEach { ssid ->
                            FilterChip(
                                selected = ssidFilter == ssid,
                                onClick = { ssidFilter = ssid },
                                label = { Text(ssid) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        sorts.forEach { (mode, label) ->
                            FilterChip(
                                selected = state.scanSortMode == mode,
                                onClick = { onSortSelected(mode) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                        FilterChip(
                            selected = state.showHiddenNetworks,
                            onClick = { onToggleHidden(!state.showHiddenNetworks) },
                            label = { Text("Show hidden") },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }

        val filteredResults = state.scanResults.filter { 
            ssidFilter == "ALL" || it.ssid == ssidFilter
        }

        items(filteredResults, key = { it.bssid }) { result ->
            NetworkResultCard(
                result = result,
                onClick = { onSelectAP(result) }
            )
        }

        item {
            ChannelSpectrumCard(state = state)
        }
        item {
            SecurityAuditCard(state = state)
        }
        item {
            ExportActionsCard()
        }
        item {
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.BarChart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Channel Utilization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                    if (state.congestion.isEmpty()) {
                        Text(
                            "Scan nearby networks to see congestion by channel.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.congestion.sortedByDescending { it.networkCount }.forEach { item ->
                            CongestionRow(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelSpectrumCard(state: WifiTweaksUiState) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Spectrum Map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
    val state by vm.uiState.collectAsStateWithLifecycle()
    ElevatedCard(shape = RoundedCornerShape(28.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.exportScanCsv())); android.widget.Toast.makeText(context, "CSV copied", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy CSV")
            }
            FilledTonalButton(onClick = { clipboard.setText(AnnotatedString(vm.exportDiagnosticJson())); android.widget.Toast.makeText(context, "JSON copied", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy JSON")
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
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = if ((score ?: 100) < 50) MaterialTheme.colorScheme.errorContainer.copy(alpha=0.7f) else MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if ((score ?: 100) >= 70) Color(0xFF2E9D66).copy(alpha=0.15f) else Color(0xFFC84B4B).copy(alpha=0.15f)), contentAlignment = Alignment.Center) {
                Icon(if ((score ?:100) >=70) Icons.Rounded.VerifiedUser else Icons.Rounded.Warning, null, tint = if ((score ?:100) >=70) Color(0xFF2E9D66) else Color(0xFFC84B4B))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Security Audit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                Text(
                    when {
                        state.scanResults.isEmpty() -> "Scan to audit nearby networks."
                        open == 0 -> "No open networks nearby. ${wpa3} WPA3 networks found."
                        else -> "$open open networks nearby — avoid auto-joining."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (score != null) Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (score >=70) Color(0xFF2E9D66) else Color(0xFFC84B4B))
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
            Text(stringResource(R.string.st_WifiTweaksScreen_y5z6), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                state.profiles.forEach { profile ->
                    ProfileCard(
                        profile = profile,
                        active = profile.tweakIds.all { state.tweakResults[it]?.isApplied == true },
                        onApply = { onApplyProfile(profile) }
                    )
                }
            }
        }

        groupedTweaks.forEach { (category, tweaks) ->
            item {
                Text(
                    text = categoryTitle(category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
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
    var dnsFilter by remember { mutableStateOf("ALL") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 40.dp),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.st_WifiTweaksScreen_a7b8), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text("${state.selectedBenchmarkProviders.size} servers selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onShowSelection) {
                                Icon(Icons.Rounded.Settings, "Select Servers")
                            }
                            FilledTonalButton(
                                onClick = onBenchmark,
                                enabled = !state.isBenchmarkingDns && state.shizukuStatus.isServiceReady,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (state.isBenchmarkingDns) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("Run")
                            }
                        }
                    }

                    if (state.dnsBenchmarkResults.isEmpty()) {
                        Text(
                            "Benchmark nearby providers to see which has the lowest latency.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.dnsBenchmarkResults.sortedBy { it.latencyMs ?: 9999L }.forEach { result ->
                            DnsBenchmarkRow(result)
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.st_WifiTweaksScreen_c9d0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    OutlinedTextField(
                        value = customHost,
                        onValueChange = { customHost = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("dns.example.com") },
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            IconButton(onClick = { onApplyCustom(customHost) }, enabled = state.shizukuStatus.isServiceReady) {
                                Icon(Icons.Rounded.Check, null)
                            }
                        }
                    )
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.st_WifiTweaksScreen_e1f2), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ALL", "SEC", "SPD").forEach { filter ->
                                FilterChip(
                                    selected = dnsFilter == filter,
                                    onClick = { dnsFilter = filter },
                                    label = { Text(filter) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                    
                    val filteredTweaks = when(dnsFilter) {
                        "SEC" -> dnsTweaks.filter { it.id.contains("quad9") || it.id.contains("adguard") }
                        "SPD" -> dnsTweaks.filter { it.id.contains("cloudflare") || it.id.contains("google") }
                        else -> dnsTweaks
                    }

                    filteredTweaks.forEach { tweak ->
                        PresetDnsRow(
                            tweak = tweak,
                            active = state.tweakResults[tweak.id]?.isApplied == true,
                            enabled = state.shizukuStatus.isServiceReady,
                            onApply = { onApplyTweak(tweak) }
                        )
                    }
                    OutlinedButton(
                        onClick = onRestoreAutomatic,
                        shape = RoundedCornerShape(18.dp),
                        enabled = state.shizukuStatus.isServiceReady,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore automatic")
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsBenchmarkRow(result: WifiDnsBenchmarkResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(result.name, fontWeight = FontWeight.Bold)
            Text(result.hostname, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (result.isRecommended) {
                Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFB700), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = result.latencyMs?.let { "${it}ms" } ?: "Timeout",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Black,
                color = when {
                    result.latencyMs == null -> MaterialTheme.colorScheme.error
                    result.latencyMs < 50 -> Color(0xFF2E9D66)
                    result.latencyMs < 100 -> Color(0xFFD97D2C)
                    else -> MaterialTheme.colorScheme.error
                }
            )
            if (result.dotLatencyMs != null) {
                Spacer(Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        "DoT ${result.dotLatencyMs}ms",
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
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
            ExpressiveCard(
                onClick = onRunSpeedTest,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(stringResource(R.string.st_WifiTweaksScreen_i5j6), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (state.speedTest.isRunning) state.speedTest.phaseLabel else "Last result: ${state.speedTest.downloadSpeedMbps.roundToInt()} Mbps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        
                        ToolzExpressiveButton(
                            onClick = onRunSpeedTest,
                            enabled = !state.speedTest.isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (state.speedTest.isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.st_WifiTweaksScreen_g7h8))
                        }
                    }

                    Box(contentAlignment = Alignment.Center) {
                        LinearProgressIndicator(
                            progress = { state.speedTest.progress },
                            modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.st_WifiTweaksScreen_i9j0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    
                    OutlinedTextField(
                        value = traceTarget,
                        onValueChange = { traceTarget = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.st_WifiTweaksScreen_k1l2)) },
                        shape = RoundedCornerShape(20.dp),
                        trailingIcon = {
                            IconButton(onClick = { onRunTraceRoute(traceTarget) }, enabled = !state.isTracing) {
                                if (state.isTracing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.PlayArrow, null)
                            }
                        }
                    )
                    if (!state.speedTest.isRunning && state.speedTest.bloatGrade != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            MiniMetric("Idle", "${state.speedTest.idleLatencyMs ?: "-"}ms")
                            MiniMetric("Loaded", "${state.speedTest.loadedLatencyMs ?: "-"}ms")
                            val g = state.speedTest.bloatGrade!!
                            MiniMetric("Bloat", g.letter)
                        }
                    }

                    if (state.traceHops.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.traceHops.forEach { hop ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Hop ${hop.hop}", fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodySmall)
                                        Text(hop.ip, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = hop.latencyMs?.let { "${it}ms" } ?: "*",
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hop.latencyMs == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.st_WifiTweaksScreen_m3n4), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp))
                ElevatedCard(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        ConfigGrid(state.networkConfig)
                    }
                }
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
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.st_WifiTweaksScreen_u1v2), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.activeProcesses.sumOf { it.rxKbps }.roundToInt()} Kbps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2E9D66))
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
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
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
                        color = Color(0xFF2E9D66),
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
    val container = when {
        shizuku.isServiceReady -> Color(0xFFDBF3E6)
        shizuku.isReachable -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    
    val contentColor = when {
        shizuku.isServiceReady -> Color(0xFF1E5D3F)
        shizuku.isReachable -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container.copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.st_WifiTweaksScreen_w3x4), 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black,
                        color = contentColor.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        if (shizuku.isServiceReady) "Service Active" else "Engine Locked", 
                        style = MaterialTheme.typography.headlineSmall, 
                        fontWeight = FontWeight.Black,
                        color = contentColor
                    )
                }
                
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (shizuku.isServiceReady) Icons.Rounded.Bolt else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Text(
                text = if (shizuku.isServiceReady) 
                    "Full access granted. All advanced networking tweaks and real-time process audits are available." 
                    else "Shizuku is required for advanced features like Private DNS management and TraceRoute.",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.8f)
            )

            if (!shizuku.isServiceReady) {
                Button(
                    onClick = onBindShizuku,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = contentColor, contentColor = container)
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Initialize Service")
                }
            }
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusBadge("Binder", if (shizuku.isReachable) "Online" else "Offline", shizuku.isReachable)
                StatusBadge("Permission", if (shizuku.isAuthorized) "Granted" else "Pending", shizuku.isAuthorized)
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, value: String, success: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = (if (success) Color(0xFF2E9D66) else Color.Gray).copy(alpha = 0.1f),
        border = BorderStroke(1.dp, (if (success) Color(0xFF2E9D66) else Color.Gray).copy(alpha = 0.2f))
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (success) Color(0xFF1E5D3F) else Color.DarkGray
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileCard(
    profile: WifiOptimizationProfile,
    active: Boolean,
    onApply: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        modifier = Modifier.combinedClickable(
            onClick = onApply,
            onLongClick = { showDetails = true }
        )
    ) {
        Row(
            modifier = Modifier
                .width(280.dp)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(profile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (active) "Active" else profile.accentLabel) },
                    shape = RoundedCornerShape(12.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = if (active) Color(0xFF2E9D66).copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        disabledLabelColor = if (active) Color(0xFF2E9D66) else MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                Button(onClick = onApply) {
                    Text("Apply Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close")
                }
            },
            title = { Text(profile.title, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(profile.description)
                    Text("Included Tweaks:", fontWeight = FontWeight.Bold)
                    profile.tweakIds.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, tint = Color(0xFF2E9D66), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(id.replace("_", " ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
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
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    label = statusLabel(result),
                    value = result?.message?.takeIf { it.isNotBlank() } ?: if (result?.isApplied == true) "Active" else "Ready"
                )
                if (tweak.riskNote != null) {
                    Text(
                        tweak.riskNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
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
    val borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    val background = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onApply)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tweak.icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tweak.title.replace("Private DNS: ", ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = tweak.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (active) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "USE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
    val strength = signalStrengthPercent(result.rssi)
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.ssid, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(result.bssid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SignalBars(result.rssi)
            }
            LinearMeter(strength = strength)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill("Band", result.band)
                StatusPill("Channel", result.channel.toString())
                StatusPill("Security", result.security)
                StatusPill("Signal", "${result.rssi} dBm")
            }
        }
    }
}

@Composable
private fun CongestionRow(item: ChannelCongestion) {
    val accent = if (item.isRecommended) Color(0xFF2E9D66) else MaterialTheme.colorScheme.primary
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
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
                        listOf(Color(0xFFC84B4B), Color(0xFFD97D2C), Color(0xFF2E9D66))
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
        TweakStatus.SUCCESS -> "Applied"
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
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Speed history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("${history.size} runs · last 90 days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClear, enabled = history.isNotEmpty()) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Clear history")
                }
            }
            if (history.size < 2) {
                Text("Run a few speed tests to build a trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Sparkline(
                    values = history.reversed().map { it.downloadMbps },
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniMetric("Now", "${history.first().downloadMbps.roundToInt()} Mbps")
                    MiniMetric("Best", "${history.maxOf { it.downloadMbps }.roundToInt()} Mbps")
                    val grades = history.mapNotNull { it.bloatGrade }
                    if (grades.isNotEmpty()) MiniMetric("Bloat mode", grades.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "-")
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
