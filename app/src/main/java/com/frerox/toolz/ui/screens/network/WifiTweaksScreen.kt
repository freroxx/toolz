package com.frerox.toolz.ui.screens.network

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.network.*
import com.frerox.toolz.ui.theme.LocalVibrationManager
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class)
@Composable
fun WifiTweaksScreen(
    onBack: () -> Unit,
    viewModel: WifiTweaksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val vibrationManager = LocalVibrationManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDetailSheet by remember { mutableStateOf<WifiScanResult?>(null) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearLastActionMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Network Power-Suite",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = uiState.currentSsid,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.refreshEnvironment()
                        }) {
                            if (uiState.isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                        }
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.resetAllSettings()
                        }) {
                            Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = "Reset All")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                val fineGranted = permissionState.permissions.any { it.permission == Manifest.permission.ACCESS_FINE_LOCATION && it.status.isGranted }
                val coarseGranted = permissionState.permissions.any { it.permission == Manifest.permission.ACCESS_COARSE_LOCATION && it.status.isGranted }
                val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionState.permissions.any { it.permission == Manifest.permission.NEARBY_WIFI_DEVICES && it.status.isGranted }
                } else true

                if (!(fineGranted || coarseGranted) || !nearbyGranted) {
                    PermissionGate(
                        onGrant = {
                            vibrationManager?.vibrateClick()
                            permissionState.launchMultiplePermissionRequest()
                        }
                    )
                } else {
                    if (!uiState.locationEnabled) {
                        DisabledServiceCard(
                            title = "Location is off",
                            body = "Android hides nearby Wi-Fi scan results until Location is enabled.",
                            primaryLabel = "Open Location",
                            onPrimary = {
                                launchSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    if (uiState.hasPartialWifiPermissions) {
                        ServiceWarningCard(
                            title = "Limited accuracy",
                            body = "Only approximate location is granted. Some Wi-Fi details (like SSID) might be hidden by Android.",
                            primaryLabel = "Grant Fine Location",
                            onPrimary = {
                                vibrationManager?.vibrateClick()
                                permissionState.launchMultiplePermissionRequest()
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    WifiTabs(
                        selectedTab = selectedTab,
                        onSelect = { index ->
                            vibrationManager?.vibrateClick()
                            selectedTab = index
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "wifi_tweaks_tab_content",
                        modifier = Modifier.weight(1f)
                    ) { tab ->
                        when (tab) {
                            0 -> OverviewTab(
                                state = uiState,
                                onScan = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.startScan()
                                },
                                onFixConnection = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.fixMyConnection()
                                },
                                onReset = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.resetAllSettings()
                                },
                                onToggleAudio = viewModel::setAudioFeedback,
                                onOpenWifiSettings = {
                                    launchSettings(context, Settings.ACTION_WIFI_SETTINGS)
                                }
                            )

                            1 -> AnalyzerTab(
                                state = uiState,
                                onScan = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.startScan()
                                },
                                onSortSelected = viewModel::setScanSortMode,
                                onToggleHidden = viewModel::setShowHiddenNetworks,
                                onSelectAP = { showDetailSheet = it }
                            )

                            2 -> ProfilesTab(
                                state = uiState,
                                onBindShizuku = {
                                    requestShizuku(context)
                                },
                                onApplyProfile = { profile ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.applyProfile(profile)
                                },
                                onApplyTweak = { tweak ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.applyTweak(tweak)
                                },
                                onUndoTweak = { tweak ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.undoTweak(tweak)
                                }
                            )

                            3 -> DnsEngineTab(
                                state = uiState,
                                onBenchmark = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.benchmarkDns()
                                },
                                onApplyTweak = { tweak ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.applyTweak(tweak)
                                },
                                onRestoreAutomatic = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.restoreAutomaticPrivateDns()
                                },
                                onApplyCustom = { host ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.applyCustomDns(host)
                                }
                            )

                            else -> DiagnosticsTab(
                                state = uiState,
                                onCopySummary = {
                                    clipboard.setText(AnnotatedString(viewModel.buildDiagnosticSummary()))
                                    scope.launch { snackbarHostState.showSnackbar("Diagnostic summary copied.") }
                                },
                                onOpenWifiSettings = {
                                    launchSettings(context, Settings.ACTION_WIFI_SETTINGS)
                                },
                                onOpenDevSettings = {
                                    launchSettings(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showDetailSheet != null) {
            APDetailSheet(
                result = showDetailSheet!!,
                onDismiss = { showDetailSheet = null },
                onPing = { /* Could add direct internal ping logic here or in VM */ }
            )
        }
    }
}

@Composable
private fun WifiTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    val tabs = listOf("Overview", "Analyzer", "Profiles", "DNS", "Diag")
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedTab
                val container by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "tab_bg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(container)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 4.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
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
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onPrimary, shape = RoundedCornerShape(16.dp)) {
                Text(primaryLabel)
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
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onPrimary, shape = RoundedCornerShape(16.dp)) {
                Text(primaryLabel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(
    state: WifiTweaksUiState,
    onScan: () -> Unit,
    onFixConnection: () -> Unit,
    onReset: () -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 16.dp, bottom = 40.dp),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OverviewHeroCard(
                state = state,
                onScan = onScan,
                onOpenWifiSettings = onOpenWifiSettings
            )
        }
        item {
            PerformanceTrendCard(state = state)
        }
        item {
            QuickActionFloatingCard(onFix = onFixConnection, onReset = onReset)
        }
        item {
            StabilityMonitorCard(state = state)
        }
        item {
            InsightStrip(state = state)
        }
        item {
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
                        Column {
                            Text("Live feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            Text(
                                "Use sound while walking around a room to find the signal sweet spot.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.audioFeedbackEnabled,
                            onCheckedChange = onToggleAudio
                        )
                    }
                    SignalHistoryChart(
                        history = state.rssiHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
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
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connection snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    DetailRow("Band", state.networkConfig.band)
                    DetailRow("Channel", state.networkConfig.channel.takeIf { it != 0 }?.toString() ?: "-")
                    DetailRow("Wi-Fi standard", state.networkConfig.wifiStandard)
                    DetailRow("Link speed", "${state.networkConfig.linkSpeed} Mbps")
                    DetailRow("Security", state.networkConfig.security)
                    DetailRow("Private DNS", if (state.networkConfig.privateDnsActive) state.networkConfig.privateDnsServerName else "Automatic / off")
                }
            }
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
                Text("Latency Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val maxPing = (history.maxOrNull() ?: 100L).coerceAtLeast(100L).toFloat()
        val linePath = Path()

        history.forEachIndexed { index, ping ->
            val x = (index.toFloat() / (history.lastIndex.coerceAtLeast(1))) * width
            val y = height - (ping.toFloat() / maxPing).coerceIn(0f, 1f) * height
            if (index == 0) {
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
        }

        drawPath(
            path = linePath,
            color = lineColor.copy(alpha = 0.6f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
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
                Text("Fix My Connection")
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.SettingsBackupRestore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset All")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewHeroCard(
    state: WifiTweaksUiState,
    onScan: () -> Unit,
    onOpenWifiSettings: () -> Unit
) {
    val score = state.advice.healthScore
    val accent = when {
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
                        rssi = state.currentRssi,
                        accent = accent
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InsightChip(Icons.Rounded.Wifi, "${state.currentRssi} dBm")
                    InsightChip(Icons.Rounded.Speed, "${state.networkConfig.linkSpeed} Mbps")
                    InsightChip(Icons.Rounded.Route, "Ch ${state.networkConfig.channel.takeIf { it != 0 } ?: "-"}")
                    if (state.networkConfig.wifi6ECapable || state.networkConfig.wifi7Capable) {
                        InsightChip(Icons.Rounded.Bolt, state.networkConfig.wifiStandard)
                    }
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
                        Text(if (state.isScanning) "Scanning" else "Scan room", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = onOpenWifiSettings, 
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Settings", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalQualityGauge(score: Int, rssi: Int, accent: Color) {
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
            Text("Stability Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            
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
            Text("Advisor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalyzerTab(
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
                    Text("Spectrum Visualizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
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
                            Text("Nearby networks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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

        item {
            ElevatedCard(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Channel advisor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    if (state.congestion.isEmpty()) {
                        Text(
                            "Scan nearby networks to see congestion by channel.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.congestion.forEach { item ->
                            CongestionRow(item)
                        }
                    }
                }
            }
        }

        if (state.scanResults.isEmpty()) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No nearby networks yet. Run a scan to populate the analyzer.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.scanResults, key = { it.bssid }) { result ->
                NetworkResultCard(result, onClick = { onSelectAP(result) })
            }
        }
    }
}

@Composable
private fun SpectrumVisualizer(results: List<WifiScanResult>, currentBssid: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // Split into 2.4GHz and 5GHz sections or focus on primary? Let's show all relative.
        val minFreq = 2400f
        val maxFreq = 5900f // Simplified broad range
        
        // Draw baseline
        drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, height), Offset(width, height), strokeWidth = 2f)

        // Draw frequency labels
        val labels = listOf("2.4GHz" to 2450f, "5GHz" to 5500f)
        labels.forEach { (text, freq) ->
            val x = ((freq - minFreq) / (maxFreq - minFreq)) * width
            if (x in 0f..width) {
                drawLine(Color.Gray.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
            }
        }

        results.forEach { ap ->
            // Skip 6GHz for simple viz range if needed, but let's try mapping
            val normalizedFreq = (ap.frequency - minFreq) / (maxFreq - minFreq)
            if (normalizedFreq in 0f..1f) {
                val centerX = normalizedFreq * width
                val arcHeight = ((ap.rssi + 100f).coerceAtLeast(0f) / 70f) * height
                // Width based on band
                val arcWidth = when (ap.band) {
                    "2.4 GHz" -> width * 0.12f
                    "5 GHz" -> width * 0.06f
                    else -> width * 0.04f
                }

                val path = Path().apply {
                    moveTo(centerX - arcWidth, height)
                    quadraticTo(centerX, height - arcHeight, centerX + arcWidth, height)
                }
                
                drawPath(
                    path = path,
                    color = if (ap.bssid == currentBssid) primaryColor else secondaryColor.copy(alpha = 0.35f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun ScanningPulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

@Composable
private fun ProfilesTab(
    state: WifiTweaksUiState,
    onBindShizuku: () -> Unit,
    onApplyProfile: (WifiOptimizationProfile) -> Unit,
    onApplyTweak: (WifiTweak) -> Unit,
    onUndoTweak: (WifiTweak) -> Unit
) {
    val groupedTweaks = remember(state.tweaks) {
        state.tweaks.groupBy { it.category }
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
            ElevatedCard(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Quick profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    state.profiles.forEach { profile ->
                        ProfileCard(
                            profile = profile,
                            enabled = !profile.requiresShizuku || state.shizukuStatus.isServiceReady,
                            onApply = { onApplyProfile(profile) }
                        )
                    }
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
private fun DnsEngineTab(
    state: WifiTweaksUiState,
    onBenchmark: () -> Unit,
    onApplyTweak: (WifiTweak) -> Unit,
    onRestoreAutomatic: () -> Unit,
    onApplyCustom: (String) -> Unit
) {
    val dnsTweaks = state.tweaks.filter { it.id.startsWith("private_dns_") }
    var customHost by remember { mutableStateOf("") }

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
                        Text("DNS Benchmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
                    Text("Custom Private DNS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
                    Text("Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    dnsTweaks.forEach { tweak ->
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
        }
    }
}

@Composable
private fun DiagnosticsTab(
    state: WifiTweaksUiState,
    onCopySummary: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenDevSettings: () -> Unit
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
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Diagnostic terminal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        color = Color.Black.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            reverseLayout = false
                        ) {
                            items(state.diagnosticLogs) { log ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
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
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = log.message,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
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
                    Text("Network details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    ConfigGrid(state.networkConfig)
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
                    Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = onCopySummary,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy summary")
                        }
                        OutlinedButton(
                            onClick = onOpenWifiSettings,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.SettingsEthernet, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Settings")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenDevSettings,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Developer options")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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

    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container.copy(alpha = 0.9f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Shizuku cockpit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(shizuku.detail, style = MaterialTheme.typography.bodyMedium)
                }
                if (!shizuku.isServiceReady) {
                    Button(onClick = onBindShizuku, shape = RoundedCornerShape(16.dp)) {
                        Text("Connect")
                    }
                } else {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Ready") },
                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = Color(0xFF2E9D66).copy(alpha = 0.14f),
                            disabledLabelColor = Color(0xFF2E9D66),
                            disabledLeadingIconContentColor = Color(0xFF2E9D66)
                        )
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill("Binder", if (shizuku.isReachable) "Online" else "Offline")
                StatusPill("Permission", if (shizuku.isAuthorized) "Granted" else "Needed")
                StatusPill("Service", if (shizuku.isServiceReady) "Bound" else "Waiting")
                StatusPill("State refresh", if (state.isRefreshingTweakStates) "Checking" else "Idle")
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: WifiOptimizationProfile,
    enabled: Boolean,
    onApply: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                Text(profile.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(profile.accentLabel) },
                    shape = RoundedCornerShape(12.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        disabledLabelColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onApply, 
                enabled = enabled, 
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Apply", style = MaterialTheme.typography.labelLarge)
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
    val borderColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(tweak.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tweak.title, fontWeight = FontWeight.Black)
                Text(tweak.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onApply, enabled = enabled, shape = RoundedCornerShape(16.dp)) {
                Text(if (active) "Reapply" else "Use")
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
private fun InsightChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class)
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
                onClick = { onPing("192.168.1.1") }, 
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
    // Friis path loss model simplified: d = 10 ^ ((27.55 - (20 * log10(freq)) + abs(rssi)) / 20)
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

private fun requestShizuku(context: android.content.Context) {
    try {
        if (Shizuku.isPreV11()) {
            (context as? Activity)?.requestPermissions(arrayOf("rikka.shizuku.permission.API_V23"), WifiTweaksViewModel.SHIZUKU_CODE)
        } else {
            Shizuku.requestPermission(WifiTweaksViewModel.SHIZUKU_CODE)
        }
    } catch (_: Exception) {
    }
}

private fun launchSettings(context: android.content.Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
