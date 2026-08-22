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
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.screens.network.components.NetworkConsoleView
import com.frerox.toolz.ui.screens.network.suite.CellularAuditCard
import com.frerox.toolz.ui.screens.network.suite.NetTokens
import com.frerox.toolz.ui.screens.network.suite.DeviceMeshCard
import com.frerox.toolz.ui.screens.network.suite.LatencyStreamCard
import com.frerox.toolz.ui.screens.network.suite.MobileDataCard
import com.frerox.toolz.ui.screens.network.suite.PortScanCard
import com.frerox.toolz.ui.screens.network.suite.PublicIpCard
import com.frerox.toolz.ui.screens.network.suite.RoutesAuditCard
import com.frerox.toolz.ui.screens.network.suite.ShizukuAccessDialog
import com.frerox.toolz.ui.screens.network.suite.ShizukuPrompt
import com.frerox.toolz.ui.screens.network.suite.SocketsCard
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Six user-facing sections; Console lives behind the terminal FAB (dev-only). */
private enum class SuiteSection(val labelRes: Int, val inNav: Boolean = true) {
    OVERVIEW(R.string.st_WifiTweaksScreen_tab_overview),
    ANALYZER(R.string.st_WifiTweaksScreen_tab_analyzer),
    OPTIMIZER(R.string.st_WifiTweaksScreen_tab_profiles),
    DNS(R.string.st_WifiTweaksScreen_tab_dns),
    DIAGNOSTICS(R.string.st_WifiTweaksScreen_tab_diag),
    NETWORK(R.string.st_WifiTweaksScreen_tab_traffic),
    CONSOLE(R.string.st_WifiTweaksScreen_tab_console, inNav = false);

    companion object {
        val navEntries: List<SuiteSection> = entries.filter { it.inNav }
    }
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

    // P6 battery gate
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { tweaksVm.setScreenActive(true); powerVm.setScreenActive(true) }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> { tweaksVm.setScreenActive(false); powerVm.setScreenActive(false) }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        launch { tweaksVm.events.collect { snackbarHostState.showSnackbar(it) } }
        launch { powerVm.events.collect { snackbarHostState.showSnackbar(it) } }
    }
    // Auto-enable Shizuku if permission already granted — no tap needed
    LaunchedEffect(power.privilegedState.isAuthorized, power.privilegedState.isServiceReady) {
        if (power.privilegedState.isAuthorized && !power.privilegedState.isServiceReady) {
            powerVm.verifyPrivilegedAccess()
        }
    }
    // M3 Expressive gate — show on entry if Shizuku is required and not ready (once per composition)
    var hasShownShizukuGate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(700)
        if (!hasShownShizukuGate && (!power.privilegedState.isAuthorized || !power.privilegedState.isServiceReady)) {
            hasShownShizukuGate = true
            shizukuPrompt = ShizukuPrompt(
                "Shizuku required",
                "System tweaks, Private DNS presets, and traceroute need Shizuku. Diagnostics work without it — but for full power, enable Shizuku."
            )
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
    val fineOrCoarse = permissionState.permissions.any {
        (it.permission == Manifest.permission.ACCESS_FINE_LOCATION || it.permission == Manifest.permission.ACCESS_COARSE_LOCATION) && it.status.isGranted
    }
    val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionState.permissions.any { it.permission == Manifest.permission.NEARBY_WIFI_DEVICES && it.status.isGranted }
    } else true
    val hasWifiPermission = fineOrCoarse && nearbyGranted

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

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
        },
        bottomBar = {
            if (!hasWifiPermission) return@Scaffold
            if (isExpanded()) {
                // rail occupies the Row below instead
            } else {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SuiteSection.navEntries.forEachIndexed { index, section ->
                        NavigationBarItem(
                            selected = index == selectedSection,
                            onClick = {
                                vibrationManager?.vibrateTick()
                                selectedSection = index
                            },
                            icon = { Icon(SuiteSectionIcon(section), contentDescription = stringResource(section.labelRes)) },
                            label = { Text(stringResource(section.labelRes), maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasWifiPermission && isExpanded()) {
                NavigationRail(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                    Spacer(Modifier.height(8.dp))
                    SuiteSection.navEntries.forEachIndexed { index, section ->
                        NavigationRailItem(
                            selected = index == selectedSection,
                            onClick = {
                                vibrationManager?.vibrateTick()
                                selectedSection = index
                            },
                            icon = { Icon(SuiteSectionIcon(section), contentDescription = stringResource(section.labelRes)) },
                            label = { Text(stringResource(section.labelRes), maxLines = 1) }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                // P3: keep nav visible but gate only scan/tweak actions; PermissionGate is banner not full-screen
                AnimatedContent(
                    targetState = selectedSection,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 8 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally { -it / 8 } + fadeOut(tween(160)))
                        } else {
                            (slideInHorizontally { -it / 8 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally { it / 8 } + fadeOut(tween(160)))
                        }
                    },
                    label = "suite_section",
                    modifier = Modifier.fillMaxSize()
                ) { sectionOrdinal ->
                    val section = SuiteSection.entries[sectionOrdinal]
                    val needsGate = false // P0: keep gate inside OverviewTab only; suite stays navigable
                    if (needsGate) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PermissionGate(onGrant = {
                                vibrationManager?.vibrateClick()
                                permissionState.launchMultiplePermissionRequest()
                            })
                            androidx.compose.material3.Text(
                                "Diagnostics still work without Wi-Fi permission; scanning & tweaks need it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = NetTokens.SpacingL)) {
                        when (section) {
                            SuiteSection.OVERVIEW -> OverviewTab(
                                state = uiState,
                                onScan = { vibrationManager?.vibrateClick(); tweaksVm.startScan() },
                                onFixConnection = { vibrationManager?.vibrateClick(); tweaksVm.fixMyConnection() },
                                onReset = { vibrationManager?.vibrateClick(); tweaksVm.resetAllSettings() },
                                onToggleAudio = tweaksVm::setAudioFeedback,
                                onOpenWifiSettings = { launchSettings(context, Settings.ACTION_WIFI_SETTINGS) },
                                extraCards = {
                                    // Perfectly organized — no double spacers, single SpacingM rhythm via parent LazyColumn + Column
                                    PublicIpCard(state = power, onRefresh = { powerVm.fetchPublicIp() })
                                    val hostPorts by powerVm.hostPortResults.collectAsStateWithLifecycle()
                                    val hostScanning by powerVm.hostPortScanning.collectAsStateWithLifecycle()
                                    DeviceMeshCard(
                                        state = power,
                                        onScanDevices = { powerVm.scanSubnet() },
                                        onScanPortsForHost = { ip -> powerVm.scanPortsForHost(ip) },
                                        onWakeHost = { mac, ip -> powerVm.wakeHost(mac, ip) },
                                        hostPortResults = hostPorts,
                                        hostPortScanning = hostScanning
                                    )
                                    MobileDataCard(
                                        state = power,
                                        privilegedReady = privilegedReady,
                                        onToggle = { enabled ->
                                            if (privilegedReady) powerVm.toggleMobileData(enabled)
                                            else requestShizukuAccess("Mobile data toggle", "System radio controls are protected. Connect Shizuku to unlock one-tap mobile data toggles.")
                                        }
                                    )
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
                                onCancelSpeedTest = { tweaksVm.cancelSpeedTest() },
                                onRunTraceRoute = { target ->
                                    // P4 fix: traceroute now always gives UI feedback via tweaksVm (isTracing + traceHops + snackbar)
                                    // PowerVm streaming kept as fallback for privileged path
                                    if (target.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("Enter a host to trace") }
                                    } else {
                                        tweaksVm.runTraceRoute(target)
                                        if (privilegedReady) powerVm.runTraceRoute(target)
                                        else requestShizukuAccess("Traceroute", "Traceroute runs through the privileged Shizuku shell.")
                                    }
                                },
                                extraCards = {
                                    LatencyStreamCard(state = power)
                                    Spacer(Modifier.height(NetTokens.SpacingL))
                                    SpeedHistoryCard(
                                        history = tweaksVm.speedHistory.collectAsStateWithLifecycle().value,
                                        onClear = { tweaksVm.clearSpeedHistory() }
                                    )
                                }
                            )

                            SuiteSection.NETWORK -> TrafficColumn(power)

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

                // Terminal FAB — tap: log sheet · hold ~2s: developer console
                val scope2 = rememberCoroutineScope()
                var holding by remember { mutableStateOf(false) }
                FloatingActionButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        showTerminalSheet = true
                    },
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        .also { interactionSource ->
                            LaunchedEffect(interactionSource) {
                                interactionSource.interactions.collect { interaction ->
                                    if (interaction is androidx.compose.foundation.interaction.PressInteraction.Press) {
                                        holding = true
                                        kotlinx.coroutines.delay(2000)
                                        if (holding) {
                                            vibrationManager?.vibrateTick()
                                            selectedSection = SuiteSection.CONSOLE.ordinal
                                            holding = false
                                        }
                                    } else if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release ||
                                               interaction is androidx.compose.foundation.interaction.PressInteraction.Cancel) {
                                        holding = false
                                    }
                                }
                            }
                        },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = NetTokens.SpacingL, bottom = NetTokens.SpacingL),
                    containerColor = if (holding) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = "Hold for console, tap for logs")
                }
            }
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
                }, shape = RoundedCornerShape(50)) { Text("Got it") }
            },
            dismissButton = { TextButton(onClick = { tweaksVm.dismissDisclaimer() }) { Text("Skip") } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("Powerful tool ahead", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Changes via Shizuku alter real system behavior but are journaled and revertible from Optimizer → Undo.\n\nDiagnostics work without Shizuku; system-level optimizations need it.")
            },
            shape = RoundedCornerShape(28.dp),
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
    SuiteSection.NETWORK -> Icons.Rounded.Lan
    SuiteSection.CONSOLE -> Icons.Rounded.Terminal
}

@Composable
private fun TrafficColumn(power: com.frerox.toolz.data.network.NetworkPowerUiState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(NetTokens.SpacingL), contentPadding = PaddingValues(top = NetTokens.SpacingM, bottom = 96.dp)) {
        item { SocketsCard(power) }
        item { CellularAuditCard(power) }
        item { RoutesAuditCard(power) }
    }
}

@Composable
private fun isExpanded(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
