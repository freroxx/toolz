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

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.network.RecommendationSeverity
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.screens.network.components.NetworkConsoleView
import com.frerox.toolz.ui.screens.network.suite.DeviceMeshCard
import com.frerox.toolz.ui.screens.network.suite.LatencyStreamCard
import com.frerox.toolz.ui.screens.network.suite.MobileDataCard
import com.frerox.toolz.ui.screens.network.suite.PortScanCard
import com.frerox.toolz.ui.screens.network.suite.PublicIpCard
import com.frerox.toolz.ui.screens.network.suite.RoutesAuditCard
import com.frerox.toolz.ui.screens.network.suite.ShizukuAccessDialog
import com.frerox.toolz.ui.screens.network.suite.ShizukuPrompt
import com.frerox.toolz.ui.screens.network.suite.SocketsCard
import com.frerox.toolz.ui.screens.network.suite.CellularAuditCard
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * P3: THE single Network entry point. Merges the retired WifiTweaksScreen and
 * NetworkPowerSuiteScreen behind one adaptive shell:
 *  - expanded width (≥840dp): NavigationRail
 *  - compact: expressive pill bottom bar
 * Sections consume both ViewModels; PowerSuite-only features live in /suite.
 */
private enum class SuiteSection(val labelRes: Int) {
    OVERVIEW(R.string.st_WifiTweaksScreen_tab_overview),
    ANALYZER(R.string.st_WifiTweaksScreen_tab_analyzer),
    OPTIMIZER(R.string.st_WifiTweaksScreen_tab_profiles),
    DNS(R.string.st_WifiTweaksScreen_tab_dns),
    DIAGNOSTICS(R.string.st_WifiTweaksScreen_tab_diag),
    TRAFFIC(R.string.st_WifiTweaksScreen_tab_traffic),
    CONSOLE(R.string.st_WifiTweaksScreen_tab_console)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NetworkSuiteScreen(
    onBack: () -> Unit,
    tweaksVm: WifiTweaksViewModel = hiltViewModel(),
    powerVm: NetworkViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val vibrationManager = LocalVibrationManager.current
    val scope = rememberCoroutineScope()

    val uiState by tweaksVm.uiState.collectAsStateWithLifecycle()
    val power by powerVm.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedSection by rememberSaveable { mutableIntStateOf(SuiteSection.OVERVIEW.ordinal) }
    var showTerminalSheet by rememberSaveable { mutableStateOf(false) }
    var showBenchmarkSheet by rememberSaveable { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf<com.frerox.toolz.data.network.WifiScanResult?>(null) }
    var shizukuPrompt by rememberSaveable { mutableStateOf<ShizukuPrompt?>(null) }

    val privilegedReady = power.privilegedState.isAuthorized && power.privilegedState.isServiceReady
    // P6: pause polling loops while this screen is not visible (battery)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    tweaksVm.setScreenActive(true)
                    powerVm.setScreenActive(true)
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    tweaksVm.setScreenActive(false)
                    powerVm.setScreenActive(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }


    // events from BOTH viewmodels feed one snackbar
    LaunchedEffect(Unit) {
        launch {
            tweaksVm.events.collect { snackbarHostState.showSnackbar(it) }
        }
        launch {
            powerVm.events.collect { snackbarHostState.showSnackbar(it) }
        }
    }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)
    val fineGranted = permissionState.permissions.any { it.permission == Manifest.permission.ACCESS_FINE_LOCATION && it.status.isGranted }
    val coarseGranted = permissionState.permissions.any { it.permission == Manifest.permission.ACCESS_COARSE_LOCATION && it.status.isGranted }
    val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionState.permissions.any { it.permission == Manifest.permission.NEARBY_WIFI_DEVICES && it.status.isGranted }
    } else true
    val hasWifiPermission = (fineGranted || coarseGranted) && nearbyGranted

    fun requestShizukuAccess(featureName: String, supportingText: String) {
        when {
            !power.privilegedState.isReachable -> shizukuPrompt = ShizukuPrompt(featureName, supportingText)
            !power.privilegedState.isAuthorized -> try {
                if (Shizuku.isPreV11()) {
                    (context as? Activity)?.requestPermissions(
                        arrayOf("rikka.shizuku.permission.API_V23"),
                        MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE
                    )
                } else Shizuku.requestPermission(MainActivity.SHIZUKU_PERMISSION_REQUEST_CODE)
                shizukuPrompt = ShizukuPrompt(featureName, supportingText)
            } catch (_: Exception) {
                shizukuPrompt = ShizukuPrompt(featureName, supportingText)
            }
            else -> powerVm.verifyPrivilegedAccess()
        }
    }

    Box(modifier = Modifier.fillMaxSize().toolzBackground()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExpressiveTopAppBar(
                        title = stringResource(R.string.st_NetworkPowerSuiteScreen_f1a2),
                        navigationIcon = {
                            IconButton(onClick = {
                                vibrationManager?.vibrateClick()
                                onBack()
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_Back))
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                vibrationManager?.vibrateClick()
                                tweaksVm.refreshEnvironment()
                                powerVm.refreshSuite()
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.st_WifiTweaksScreen_1a2b))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            },
            bottomBar = {
                if (!hasWifiPermission) return@Scaffold
                SuiteBottomBar(
                    selected = selectedSection,
                    onSelect = {
                        vibrationManager?.vibrateTick()
                        selectedSection = it
                    },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Adaptive rail for expanded widths
                if (hasWifiPermission && isExpanded()) {
                    SuiteRail(
                        selected = selectedSection,
                        onSelect = {
                            vibrationManager?.vibrateTick()
                            selectedSection = it
                        }
                    )
                }

                AnimatedContent(
                    targetState = selectedSection,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 6 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally { -it / 6 } + fadeOut(tween(180)))
                        } else {
                            (slideInHorizontally { -it / 6 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally { it / 6 } + fadeOut(tween(180)))
                        }
                    },
                    label = "suite_section",
                    modifier = Modifier.weight(1f)
                ) { sectionOrdinal ->
                    val section = SuiteSection.entries[sectionOrdinal]
                    if (!hasWifiPermission) {
                        PermissionGate(onGrant = {
                            vibrationManager?.vibrateClick()
                            permissionState.launchMultiplePermissionRequest()
                        })
                        return@AnimatedContent
                    }
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            when (section) {
                                SuiteSection.OVERVIEW -> OverviewTab(
                                    state = uiState,
                                    onScan = { vibrationManager?.vibrateClick(); tweaksVm.startScan() },
                                    onFixConnection = { vibrationManager?.vibrateClick(); tweaksVm.fixMyConnection() },
                                    onReset = { vibrationManager?.vibrateClick(); tweaksVm.resetAllSettings() },
                                    onToggleAudio = tweaksVm::setAudioFeedback,
                                    onOpenWifiSettings = { launchSettings(context, Settings.ACTION_WIFI_SETTINGS) },
                                    extraCards = {
                                        PublicIpCard(state = power, onRefresh = { powerVm.fetchPublicIp() })
                                        Spacer(Modifier.height(20.dp))
                                        DeviceMeshCard(state = power, onScanDevices = { powerVm.scanSubnet() })
                                        Spacer(Modifier.height(20.dp))
                                        MobileDataCard(
                                            state = power,
                                            privilegedReady = privilegedReady,
                                            onToggle = { enabled ->
                                                if (privilegedReady) powerVm.toggleMobileData(enabled)
                                                else requestShizukuAccess("Mobile data toggle", "System radio controls are protected. Connect Shizuku to unlock one-tap mobile data toggles.")
                                            }
                                        )
                                        Spacer(Modifier.height(20.dp))
                                        PortScanCard(state = power, onScanPorts = { powerVm.scanGatewayPorts() })
                                    }
                                )

                                SuiteSection.ANALYZER -> AnalyzerTab(
                                    state = uiState,
                                    onScan = { vibrationManager?.vibrateClick(); tweaksVm.startScan() },
                                    onSortSelected = tweaksVm::setScanSortMode,
                                    onToggleHidden = tweaksVm::setShowHiddenNetworks,
                                    onSelectAP = { showDetailSheet = it }
                                )

                                SuiteSection.OPTIMIZER -> ProfilesTab(
                                    state = uiState,
                                    onBindShizuku = { requestShizuku(context) },
                                    onApplyProfile = { vibrationManager?.vibrateClick(); tweaksVm.applyProfile(it) },
                                    onApplyTweak = { vibrationManager?.vibrateClick(); tweaksVm.applyTweak(it) },
                                    onUndoTweak = { vibrationManager?.vibrateClick(); tweaksVm.undoTweak(it) }
                                )

                                SuiteSection.DNS -> DnsEngineTab(
                                    state = uiState,
                                    onBenchmark = { vibrationManager?.vibrateClick(); tweaksVm.benchmarkDns() },
                                    onApplyTweak = { vibrationManager?.vibrateClick(); tweaksVm.applyTweak(it) },
                                    onRestoreAutomatic = { vibrationManager?.vibrateClick(); tweaksVm.restoreAutomaticPrivateDns() },
                                    onApplyCustom = { vibrationManager?.vibrateClick(); tweaksVm.applyCustomDns(it) },
                                    onShowSelection = { showBenchmarkSheet = true }
                                )

                                SuiteSection.DIAGNOSTICS -> DiagnosticsTab(
                                    state = uiState,
                                    onCopySummary = {
                                        clipboard.setText(AnnotatedString(tweaksVm.buildDiagnosticSummary()))
                                        scope.launch { snackbarHostState.showSnackbar("Diagnostic summary copied.") }
                                    },
                                    onOpenWifiSettings = { launchSettings(context, Settings.ACTION_WIFI_SETTINGS) },
                                    onOpenDevSettings = { launchSettings(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) },
                                    onRunSpeedTest = { tweaksVm.runSpeedTest() },
                                    onRunTraceRoute = { target ->
                                        if (privilegedReady) powerVm.runTraceRoute(target)
                                        else requestShizukuAccess("Traceroute", "Traceroute uses shell networking tools that are only available through the privileged Shizuku layer.")
                                    },
                                    extraCards = {
                                        LatencyStreamCard(state = power)
                                        Spacer(Modifier.height(20.dp))
                                        SpeedHistoryCard(
                                            history = tweaksVm.speedHistory.collectAsStateWithLifecycle().value,
                                            onClear = { tweaksVm.clearSpeedHistory() }
                                        )
                                    }
                                )

                                SuiteSection.TRAFFIC -> TrafficColumn(power)

                                SuiteSection.CONSOLE -> NetworkConsoleView(
                                    logs = uiState.diagnosticLogs,
                                    isShizukuReady = uiState.shizukuStatus.isServiceReady,
                                    consoleEnabled = uiState.consoleEnabled,
                                    onToggleConsole = tweaksVm::setConsoleEnabled,
                                    onExecuteRawCommand = tweaksVm::executeRawCommand,
                                    onClearLogs = tweaksVm::clearLogs
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating console launcher
        FloatingActionButton(
            onClick = {
                vibrationManager?.vibrateClick()
                showTerminalSheet = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = if (hasWifiPermission) 96.dp else 24.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Terminal, contentDescription = "Open diagnostics console")
        }
    }

    // ── Sheets & dialogs ──
    if (showTerminalSheet) {
        DiagnosticLogSheet(logs = uiState.diagnosticLogs, onDismiss = { showTerminalSheet = false })
    }
    if (showBenchmarkSheet) {
        BenchmarkSelectionSheet(
            state = uiState,
            providers = tweaksVm.benchmarkProviders(),
            onToggle = tweaksVm::updateBenchmarkSelection,
            onDismiss = { showBenchmarkSheet = false }
        )
    }
    showDetailSheet?.let { result ->
        APDetailSheet(result = result, onDismiss = { showDetailSheet = null }, onPing = { target ->
            scope.launch {
                val latency = tweaksVm.pingHost(target)
                snackbarHostState.showSnackbar("Ping to $target: ${latency ?: "Timeout"}ms")
            }
        })
    }
    shizukuPrompt?.let { prompt ->
        ShizukuAccessDialog(
            prompt = prompt,
            isServiceReady = power.privilegedState.isServiceReady,
            isAuthorized = power.privilegedState.isAuthorized,
            isReachable = power.privilegedState.isReachable,
            onDismiss = { shizukuPrompt = null },
            onRequestAccess = { requestShizukuAccess(prompt.featureName, prompt.supportingText) },
            onVerify = {
                powerVm.verifyPrivilegedAccess()
                shizukuPrompt = null
            }
        )
    }
    if (uiState.showDisclaimer) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(onClick = {
                    vibrationManager?.vibrateClick()
                    tweaksVm.dismissDisclaimer()
                }, shape = RoundedCornerShape(16.dp)) { Text("Got it") }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("Powerful tool ahead", fontWeight = FontWeight.Black)
                }
            },
            text = {
                Text(
                    "This suite can change real system network behavior via Shizuku. Every change is journaled and revertible from Optimizer → Undo.\n\n" +
                        "Diagnostics work without Shizuku; system-level optimizations need it."
                )
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

// ── Section compositions ────────────────────────────────────────────────────

@Composable
private fun SuiteSectionIcon(section: SuiteSection) = when (section) {
    SuiteSection.OVERVIEW -> Icons.Rounded.Dashboard
    SuiteSection.ANALYZER -> Icons.Rounded.Wifi
    SuiteSection.OPTIMIZER -> Icons.Rounded.AutoAwesome
    SuiteSection.DNS -> Icons.Rounded.Public
    SuiteSection.DIAGNOSTICS -> Icons.Rounded.Analytics
    SuiteSection.TRAFFIC -> Icons.Rounded.Lan
    SuiteSection.CONSOLE -> Icons.Rounded.Terminal
}


@Composable
private fun TrafficColumn(power: com.frerox.toolz.data.network.NetworkPowerUiState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
        item { SocketsCard(power) }
        item { CellularAuditCard(power) }
        item { RoutesAuditCard(power) }
    }
}

// ── Navigation ──────────────────────────────────────────────────────────────

@Composable
private fun isExpanded(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840


@Composable
private fun SuiteRail(selected: Int, onSelect: (Int) -> Unit) {
    NavigationRail(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
        Spacer(Modifier.height(8.dp))
        SuiteSection.entries.forEachIndexed { index, section ->
            NavigationRailItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = { Icon(SuiteSectionIcon(section), contentDescription = null) },
                label = { Text(stringResource(section.labelRes), maxLines = 1) }
            )
        }
    }
}

@Composable
private fun SuiteBottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SuiteSection.entries.forEachIndexed { index, section ->
                val isSelected = index == selected
                val bg by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    label = "suite_tab_bg"
                )
                val scale by animateFloatAsState(
                    if (isSelected) 1f else 0.94f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "suite_tab_scale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(index)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(
                            SuiteSectionIcon(section),
                            contentDescription = stringResource(section.labelRes),
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                        Text(
                            stringResource(section.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
