/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Developer console — "Mission Control" redesign:
 *   Home   → master power, live telemetry, quick actions, bug report path
 *   Spoofs → tile grid per tool + data-driven bottom-sheet configurators
 *   Labs   → crash lab (+ history), logcat v2, notifications, deep links, UI
 *   System → status, storage/DB, DataStore inspector, armed danger zone
 *
 * Every toggle here is local-only (see DEVELOPER-MODE.md). No telemetry:
 * bug reports leave the device ONLY via copy/share + the Toolz Discord.
 */

package com.frerox.toolz.ui.screens.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.DevUiOverrider
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.screens.developer.components.CommandPalette
import com.frerox.toolz.ui.screens.developer.components.CrashHistoryCard
import com.frerox.toolz.ui.screens.developer.components.DevBtn
import com.frerox.toolz.ui.screens.developer.components.DevCard
import com.frerox.toolz.ui.screens.developer.components.DevCardTitle
import com.frerox.toolz.ui.screens.developer.components.DevDivider
import com.frerox.toolz.ui.screens.developer.components.DevKv
import com.frerox.toolz.ui.screens.developer.components.DevSectionHeader
import com.frerox.toolz.ui.screens.developer.components.DevToggleLine
import com.frerox.toolz.ui.screens.developer.components.LogcatViewer
import com.frerox.toolz.ui.screens.developer.components.SpoofChip
import com.frerox.toolz.ui.screens.developer.components.SpoofToolTile
import com.frerox.toolz.ui.screens.developer.components.SpoofConfiguratorSheet
import com.frerox.toolz.ui.screens.developer.components.WhisperPreviewCard
import com.frerox.toolz.ui.screens.developer.components.TelemetryCard
import com.frerox.toolz.ui.screens.developer.components.DevCommand
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground

private enum class DevTab(val title: String) {
    HOME("Home"),
    SPOOFS("Spoofs"),
    LABS("Labs"),
    SYSTEM("System"),
}

/** Deep-link launchpad: every arg-free route in the app, searchable. */
private val DevRoutes: List<Pair<String, String>> = listOf(
    "dashboard" to "Home dashboard",
    "settings" to "Settings",
    "app_skin" to "App Skin",
    "icon_catalog" to "Icon Catalog",
    "update" to "Update check",
    "toolz_news" to "Toolz News",
    "battery_info" to "Battery info",
    "device_info" to "Device info",
    "network_power_suite" to "Network power suite",
    "wifi_tweaks" to "Wi-Fi tweaks",
    "step_counter" to "Step counter",
    "step_trends" to "Step trends",
    "compass" to "Compass",
    "bubble_level" to "Bubble level",
    "speedometer" to "Speedometer",
    "altimeter" to "Altimeter",
    "light_meter" to "Light meter",
    "sound_meter" to "Sound meter",
    "flashlight" to "Flashlight",
    "screen_light" to "Screen light",
    "caffeinate" to "Caffeinate",
    "purge_shot" to "PurgeShot",
    "timer" to "Timer",
    "stopwatch" to "Stopwatch",
    "pomodoro" to "Pomodoro",
    "todo" to "Todo",
    "calendar" to "Calendar",
    "notepad" to "Notepad",
    "clipboard" to "Clipboard",
    "notification_vault" to "Notification vault",
    "qr_generator" to "QR generator",
    "media_downloader" to "Media downloader",
    "music_player" to "Music player",
    "file_cleaner" to "File cleaner",
    "file_converter" to "File converter",
    "whisper" to "Whisper (real)",
    "focus_flow" to "Focus Flow",
    "smart_search" to "Smart search",
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeveloperModeScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit = {},
    viewModel: DeveloperModeViewModel = hiltViewModel(),
) {
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val devOn by viewModel.devModeEnabled.collectAsState(initial = false)
    val spoofs by viewModel.spoofSnapshot.collectAsState(initial = emptyMap())
    val activeCount by viewModel.activeSandboxCount.collectAsState(initial = 0)
    val verbose by viewModel.devVerboseLogging.collectAsState(initial = false)
    val mockDelay by viewModel.devMockDelayMs.collectAsState(initial = 0)
    val perfHud by viewModel.devPerfHud.collectAsState(initial = false)
    val forceOffline by viewModel.devForceOffline.collectAsState(initial = false)
    val crashNext by viewModel.devCrashOnNextLaunch.collectAsState(initial = false)
    val logs by viewModel.logLines.collectAsState()
    val logTail by viewModel.logcatTail.collectAsState()
    val lastAction by viewModel.lastAction.collectAsState()
    val params by viewModel.spoofParams.collectAsState()
    val shakeToOpen by viewModel.devShakeToOpen.collectAsState(initial = false)
    val tables by viewModel.tableCounts.collectAsState()
    val storage by viewModel.storageInfo.collectAsState()
    val prefsDump by viewModel.prefsSnapshot.collectAsState()
    val info by viewModel.appInfo.collectAsState()
    val heapHistory by viewModel.heapHistory.collectAsState()
    val crashHistory by viewModel.crashHistory.collectAsState()

    val activity = context as? android.app.Activity
    var rtlOn by remember { mutableStateOf(activity?.let { DevUiOverrider.isRtl(it) } == true) }

    var tab by remember { mutableStateOf(DevTab.HOME.ordinal) }
    var paletteOpen by remember { mutableStateOf(false) }
    var configuratorFor by remember { mutableStateOf<DevToolId?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf<String?>(null) }
    var dangerArmed by remember { mutableStateOf(false) }
    var showDevEnableNotice by remember { mutableStateOf(false) }

    LaunchedEffect(lastAction) {
        val msg = lastAction ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.consumeAction()
    }

    // Stop the 1s logcat tail when this tab or the app is no longer visible.
    LaunchedEffect(tab) {
        if (DevTab.entries[tab] != DevTab.LABS) viewModel.setLogcatTail(false)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.setLogcatTail(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset developer mode?", fontWeight = FontWeight.Black) },
            text = { Text("Turns master OFF and clears all 18 spoof flags, crash history included. Slider values are kept. Real data returns instantly.") },
            confirmButton = {
                Button(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.resetAll()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("RESET", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(28.dp),
        )
    }

    showCrashConfirm?.let { type ->
        AlertDialog(
            onDismissRequest = { showCrashConfirm = null },
            title = { Text("Crash the app?", fontWeight = FontWeight.Black) },
            text = { Text("This will crash with $type NOW and record the test event locally. Unsaved work may be lost.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.triggerCrash(type); showCrashConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("CRASH", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { showCrashConfirm = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(28.dp),
        )
    }

    val commands = buildList {
        add(DevCommand("Toggle master ${if (devOn) "off" else "on"}", "Developer mode switch") { viewModel.setDevMode(!devOn) })
        add(DevCommand("Turn all spoofs off", "Real data everywhere") { viewModel.clearAllSpoofs() })
        if (!devOn) add(DevCommand("Enable developer mode", "Show warning first") { showDevEnableNotice = true })
        add(DevCommand("Copy bug report", "Local text report") { viewModel.copyBugReport(info) })
        add(DevCommand("Share bug report", "System share sheet — you paste it") { viewModel.shareBugReport(info) })
        add(DevCommand("Open Toolz Discord", "Paste the report there") { DevUiOverrider.openToolzDiscord(context) })
        add(DevCommand("Refresh logcat", "Read last 300 lines") { viewModel.refreshLogcat() })
        add(DevCommand("Clear logcat", "logcat -c (local)") { viewModel.clearLogcat() })
        add(DevCommand("Fire alarm notification", "[SPOOF TEST] tagged") {
            viewModel.sendSpoofNotification(com.frerox.toolz.util.NotificationHelper.CHANNEL_TOOL_ALARM, "Alarm", "Test from palette", 60109)
        })
        add(DevCommand("Flood x6 notifications", "Stress test") { viewModel.floodNotifications() })
        add(DevCommand("Cancel dev notifications", "Removes [SPOOF TEST] posts") { viewModel.cancelDevNotifications() })
        add(DevCommand("Clear cache", "App cache dir only") { viewModel.clearCache() })
        add(DevCommand("Dump prefs", "Sensitive values redacted") { viewModel.refreshPrefs() })
        add(DevCommand("Open system dev options", "Android settings") { DevUiOverrider.openSystemDeveloperOptions(context) })
        add(DevCommand("Force RTL on", "Current activity") { rtlOn = true; activity?.let { DevUiOverrider.setRtl(it, true) } })
        add(DevCommand("Turn RTL off", "Current activity") { rtlOn = false; activity?.let { DevUiOverrider.setRtl(it, false) } })
        add(DevCommand("Crash: NPE", "Lab test", danger = true) { showCrashConfirm = "NPE" })
        add(DevCommand("Crash: ANR 6s", "Lab test", danger = true) { showCrashConfirm = "ANR" })
        add(DevCommand("Crash: OOM", "Lab test", danger = true) { showCrashConfirm = "OOM" })
        add(DevCommand("Reset developer mode", "Master off + spoofs off", danger = true) { showResetDialog = true })
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Developer Mode",
                subtitle = if (devOn) "ON • $activeCount sandbox${if (activeCount == 1) "" else "es"} • local only"
                else "Handle with care",
                navigationIcon = {
                    IconButton(
                        onClick = { vibrationManager?.vibrateClick(); onBack() },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (devOn && activeCount > 0) {
                        SpoofChip(modifier = Modifier.padding(end = 4.dp), label = "$activeCount SPOOF")
                    }
                    IconButton(
                        onClick = { vibrationManager?.vibrateClick(); paletteOpen = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    ) { Icon(Icons.Rounded.Tune, contentDescription = "Command palette") }
                    IconButton(
                        onClick = { vibrationManager?.vibrateClick(); showResetDialog = true },
                        modifier = Modifier.padding(end = 8.dp, start = 0.dp).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                    ) { Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(horizontal = 24.dp)) {
                DevTab.entries.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i,
                        onClick = { vibrationManager?.vibrateClick(); tab = i },
                        text = { Text(t.title, fontWeight = FontWeight.Black) },
                    )
                }
            }

            when (DevTab.entries[tab]) {
                DevTab.HOME -> HomeTab(
                    devOn = devOn,
                    spoofs = spoofs,
                    activeCount = activeCount,
                    info = info,
                    heapHistory = heapHistory,
                    params = params,
                    onSetDevMode = { enabled ->
                        if (enabled) showDevEnableNotice = true else viewModel.setDevMode(false)
                    },
                    onOpenConfigurator = { configuratorFor = it },
                    onOpenRoute = onOpenRoute,
                    refreshStorage = {
                        viewModel.refreshStorageInfo()
                        viewModel.refreshTableCounts()
                    },
                    viewModel = viewModel,
                )
                DevTab.SPOOFS -> SpoofsTabRoot(
                    devOn = devOn,
                    spoofs = spoofs,
                    params = params,
                    viewModel = viewModel,
                    onOpenConfigurator = { configuratorFor = it },
                    onSetSpoof = viewModel::setSpoof,
                )
                DevTab.LABS -> LabsTab(
                    devOn = devOn,
                    verbose = verbose,
                    mockDelay = mockDelay,
                    perfHud = perfHud,
                    forceOffline = forceOffline,
                    crashNext = crashNext,
                    logs = logs,
                    logTail = logTail,
                    crashHistory = crashHistory,
                    activity = activity,
                    rtlOn = rtlOn,
                    onRtl = {
                        rtlOn = it
                        activity?.let { a -> DevUiOverrider.setRtl(a, it) }
                    },
                    onOpenRoute = onOpenRoute,
                    showCrashConfirm = { showCrashConfirm = it },
                    viewModel = viewModel,
                )
                DevTab.SYSTEM -> SystemTab(
                    devOn = devOn,
                    spoofOn = spoofs,
                    info = info,
                    shakeToOpen = shakeToOpen,
                    tables = tables,
                    storage = storage,
                    prefsDump = prefsDump,
                    dangerArmed = dangerArmed,
                    onArm = { dangerArmed = it },
                    onReset = { showResetDialog = true },
                    viewModel = viewModel,
                )
            }
        }
    }

    if (showDevEnableNotice) {
        AlertDialog(
            onDismissRequest = { showDevEnableNotice = false },
            title = { Text("Enable Developer Mode?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                Text(
                    "• Internal diagnostics + LOCAL-ONLY spoofs (fake data never leaves the device).\n" +
                        "• Crash lab, test notifications and data labs can disturb the app until you turn them off.\n" +
                        "• Auto-disables after every app update.\n\n" +
                        "No telemetry: bug reports are copied/shared by you only.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.setDevMode(true)
                        showDevEnableNotice = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("ENABLE", fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { vibrationManager?.vibrateClick(); showDevEnableNotice = false }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(40.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    CommandPalette(visible = paletteOpen, commands = commands, onDismiss = { paletteOpen = false })

    SpoofConfiguratorSheet(
        tool = configuratorFor,
        params = params,
        devOn = devOn,
        sandboxActive = configuratorFor?.let { spoofs[it] == true } == true,
        onUpdateSpoofParams = viewModel::updateSpoofParams,
        onSetSpoof = viewModel::setSpoof,
        onOpenRoute = {
            configuratorFor = null
            onOpenRoute(it)
        },
        onDismiss = { configuratorFor = null },
    )
}

// ---------- HOME ----------

@Composable
private fun HomeTab(
    devOn: Boolean,
    spoofs: Map<DevToolId, Boolean>,
    activeCount: Int,
    info: DevAppInfo,
    heapHistory: List<Float>,
    params: SpoofParams,
    onSetDevMode: (Boolean) -> Unit,
    onOpenConfigurator: (DevToolId) -> Unit,
    onOpenRoute: (String) -> Unit,
    refreshStorage: () -> Unit,
    viewModel: DeveloperModeViewModel,
) {
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Master power
        item {
            DevCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Developer Mode", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            if (devOn) "ON • $activeCount sandbox${if (activeCount == 1) "" else "es"} • all local"
                            else "OFF — every tool shows real data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = devOn, onCheckedChange = onSetDevMode)
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                ) {
                    Text(
                        "LOCAL ONLY • NO TELEMETRY • AUTO-OFF ON UPDATE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // Live telemetry
        item { DevCard { TelemetryCard(info, heapHistory) } }
        // Quick actions
        item {
            DevCard {
                DevCardTitle("Quick actions")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn("Bug report", Icons.Rounded.BugReport) { viewModel.copyBugReport(info) }
                    DevBtn("Share", Icons.Rounded.Share) { viewModel.shareBugReport(info) }
                    DevBtn("Discord", Icons.Rounded.Forum) { DevUiOverrider.openToolzDiscord(context) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn(
                        if (activeCount > 0) "All spoofs off ($activeCount)" else "All spoofs off",
                        Icons.Rounded.Science,
                        enabled = devOn && activeCount > 0,
                    ) {
                        vibrationManager?.vibrateLongClick()
                        viewModel.clearAllSpoofs()
                    }
                    DevBtn("Refresh storage", Icons.Rounded.DeleteSweep) { refreshStorage() }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Reports never leave this device on their own: copy or share, then paste into the Toolz Discord server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Active sandboxes
        item { DevSectionHeader("Active sandboxes") }
        if (activeCount == 0) {
            item {
                DevCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (devOn) "No sandbox active — every screen is real data. Tap the Spoofs tab to arm one."
                            else "Enable Developer Mode to arm sandboxes.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        } else {
            item {
                DevCard {
                    spoofs.filter { it.value }.keys.forEach { tool ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenConfigurator(tool) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tool.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "SANDBOX • ${spoofFixtureSummary(tool, params)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SpoofChip(label = "OPEN")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ---------- SPOOFS ----------

@Composable
private fun SpoofsTabRoot(
    devOn: Boolean,
    spoofs: Map<DevToolId, Boolean>,
    params: SpoofParams,
    viewModel: DeveloperModeViewModel,
    onOpenConfigurator: (DevToolId) -> Unit,
    onSetSpoof: (DevToolId, Boolean) -> Unit,
) {
    val query by viewModel.searchQuery.collectAsState()
    val tools = remember(query) {
        DevToolId.entries.filter {
            query.isBlank() || it.title.contains(query, true) || it.prefKey.contains(query, true)
        }
    }
    val activeCount = spoofs.values.count { it }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search tools…", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(span = { GridItemSpan(2) }) {
            DevCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sandboxes", fontWeight = FontWeight.Black)
                        Text(
                            if (!devOn) "Master OFF — everything shows real data"
                            else if (activeCount == 0) "None active — tap a tile to configure"
                            else "$activeCount active — tap a tile to configure its fixtures",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(enabled = devOn && activeCount > 0, onClick = { viewModel.clearAllSpoofs() }) {
                        Text("TURN ALL OFF", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }
        }
        DevToolGroup.entries.forEach { group ->
            val groupTools = tools.filter { it.group == group }
            if (groupTools.isEmpty()) return@forEach
            item(span = { GridItemSpan(2) }, key = "h_${group.name}") {
                DevSectionHeader(group.title)
            }
            items(groupTools, key = { it.prefKey }) { tool ->
                SpoofToolTile(
                    tool = tool,
                    params = params,
                    active = spoofs[tool] == true,
                    devOn = devOn,
                    onToggle = { onSetSpoof(tool, it) },
                    onConfigure = { onOpenConfigurator(tool) },
                )
            }
        }
        if (tools.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text("No tools match \"$query\".", modifier = Modifier.padding(8.dp))
            }
        }
        item(span = { GridItemSpan(2) }) { DevSectionHeader("Whisper sandbox preview") }
        item(span = { GridItemSpan(2) }) { WhisperPreviewCard() }
        item(span = { GridItemSpan(2) }) { Spacer(Modifier.height(80.dp)) }
    }
}

// ---------- LABS ----------

@Composable
private fun LabsTab(
    devOn: Boolean,
    verbose: Boolean,
    mockDelay: Int,
    perfHud: Boolean,
    forceOffline: Boolean,
    crashNext: Boolean,
    logs: List<String>,
    logTail: Boolean,
    crashHistory: List<CrashLabEvent>,
    activity: android.app.Activity?,
    rtlOn: Boolean,
    onRtl: (Boolean) -> Unit,
    onOpenRoute: (String) -> Unit,
    showCrashConfirm: (String) -> Unit,
    viewModel: DeveloperModeViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DevCard {
            DevCardTitle("Crash & error lab")
            Text("Each action is local and recorded into local test history.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevBtn("NPE", Icons.Rounded.BugReport) { showCrashConfirm("NPE") }
                DevBtn("ANR 6s", Icons.Rounded.BugReport) { showCrashConfirm("ANR") }
                DevBtn("OOM", Icons.Rounded.BugReport) { showCrashConfirm("OOM") }
            }
            Spacer(Modifier.height(8.dp))
            DevBtn("Log non-fatal test exception", Icons.Rounded.BugReport) { viewModel.logNonFatal() }
            Spacer(Modifier.height(4.dp))
            DevToggleLine("Crash on next launch", crashNext, devOn, viewModel::setCrashOnNextLaunch, "Cleared after update")
            DevDivider()
            CrashHistoryCard(events = crashHistory, onClear = { viewModel.clearCrashHistory() })
        }

        DevCard {
            DevCardTitle("Notification lab ([SPOOF TEST])")
            Text("Posted to real channels for look/sound checks; tagged, IDs 60001+.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            listOf(
                com.frerox.toolz.util.NotificationHelper.CHANNEL_TOOL_ALARM to "Alarm",
                com.frerox.toolz.util.NotificationHelper.CHANNEL_TOOL_ACTIVE to "Active tool",
                com.frerox.toolz.util.NotificationHelper.CHANNEL_STEP_COUNTER to "Steps",
                com.frerox.toolz.util.NotificationHelper.CHANNEL_TASK_REMINDERS to "Task",
                com.frerox.toolz.util.NotificationHelper.CHANNEL_BACKUPS to "Backup",
                com.frerox.toolz.util.NotificationHelper.CHANNEL_TOOLZ_NEWS to "News",
            ).forEachIndexed { i, (ch, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = devOn) { viewModel.sendSpoofNotification(ch, label, "Tap to dismiss — no side effects", 60101 + i) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Fire: $label", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(ch, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevBtn("Flood x6", Icons.Rounded.BugReport) { viewModel.floodNotifications() }
                DevBtn("Cancel dev posts", Icons.Rounded.DeleteSweep) { viewModel.cancelDevNotifications() }
            }
            Spacer(Modifier.height(8.dp))
            DevBtn("System notification settings", Icons.Rounded.Share) { DevUiOverrider.openAppNotificationSettings(viewModel.appContext()) }
        }

        DevCard {
            DevCardTitle("Logcat v2 (local)")
            LogcatViewer(
                lines = logs,
                tail = logTail,
                liveAvailable = true,
                onTail = viewModel::setLogcatTail,
                onRefresh = { viewModel.refreshLogcat() },
                onClear = { viewModel.clearLogcat() },
                onCopyAll = { viewModel.copyText("Logcat", logs.joinToString("\n")) },
            )
        }

        DevCard {
            DevCardTitle("Data lab")
            DevToggleLine("Verbose logging", verbose, devOn, viewModel::setVerbose)
            DevToggleLine("Force offline override", forceOffline, devOn, viewModel::setForceOffline)
            Text("Mock network delay: ${mockDelay}ms", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            androidx.compose.material3.Slider(
                value = mockDelay.toFloat(),
                onValueChange = { viewModel.setMockDelay(it.toInt()) },
                valueRange = 0f..5000f,
                enabled = devOn,
            )
        }

        DevCard {
            DevCardTitle("Deep-link launchpad")
            LaunchpadSearch(devOn, onOpenRoute)
        }

        DevCard {
            DevCardTitle("UI playground")
            DevToggleLine("Perf HUD overlay flag (applies on restart)", perfHud, devOn, viewModel::setPerfHud)
            DevToggleLine("Force RTL (this Activity, reversible)", rtlOn, devOn && activity != null, onRtl)
            Text("Font scale (recreates Activity)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(0.85f to "S", 1f to "M", 1.3f to "L", 1.6f to "XL").forEachIndexed { i, (scale, label) ->
                    SegmentedButton(
                        selected = false,
                        onClick = { activity?.let { DevUiOverrider.setFontScale(it, scale) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                        enabled = devOn && activity != null,
                    ) { Text(label, fontWeight = FontWeight.Black) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Force appearance", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("SYSTEM" to "Sys", "LIGHT" to "Light", "DARK" to "Dark").forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = false,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        enabled = devOn,
                    ) { Text(label, fontWeight = FontWeight.Black) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevBtn("System dev options", Icons.Rounded.Share) { DevUiOverrider.openSystemDeveloperOptions(viewModel.appContext()) }
            }
            Text(
                "Layout bounds need WRITE_SECURE_SETTINGS — use system dev options.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DevDivider()
            Text("Language forcer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("en" to "EN", "ar" to "AR", "fr" to "FR", "es" to "ES").forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = false,
                        onClick = { viewModel.applyLanguage(code) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                        enabled = devOn,
                    ) { Text(label, fontWeight = FontWeight.Black) }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { viewModel.applyLanguage("system") }) {
                Text("RESET TO SYSTEM LANGUAGE", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            DevDivider()
            Text("Endpoints (build-time, info only)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            listOf(
                "Anthropic" to "https://api.anthropic.com/",
                "Lyrics" to "https://lrclib.net/api/",
                "Toolz backend" to "https://toolz-app.vercel.app/",
                "Supabase" to "BuildConfig.SUPABASE_URL",
            ).forEach { (name, url) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.copyText(name, url) }.padding(vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(url, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun LaunchpadSearch(devOn: Boolean, onOpenRoute: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search routes…", fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    val routes = DevRoutes.filter { query.isBlank() || it.first.contains(query, true) || it.second.contains(query, true) }
    if (routes.isEmpty()) {
        Text("No route matches \"$query\".", style = MaterialTheme.typography.bodySmall)
    } else {
        routes.forEach { (route, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = devOn) { onOpenRoute(route) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("→ $route", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ---------- SYSTEM ----------

@Composable
private fun SystemTab(
    devOn: Boolean,
    spoofOn: Map<DevToolId, Boolean>,
    info: DevAppInfo,
    shakeToOpen: Boolean,
    tables: List<DevTableCount>,
    storage: DevStorageInfo,
    prefsDump: Map<String, String>,
    dangerArmed: Boolean,
    onArm: (Boolean) -> Unit,
    onReset: () -> Unit,
    viewModel: DeveloperModeViewModel,
) {
    var prefQuery by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DevCard {
            DevCardTitle("Status & build")
            DevKv("Version", "${info.versionName} (${info.versionCode})")
            DevKv("Package", info.packageName)
            DevKv("Uptime", "${info.uptimeMs / 1000}s")
            DevKv("Heap", info.heapMb)
            DevKv("Native heap", info.nativeHeapMb)
            DevKv("Threads", info.threads.toString())
            DevKv("Density", "${DevUiOverrider.densityDpi(viewModel.appContext())}dpi")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevBtn("Copy report", Icons.Rounded.ContentCopy) { viewModel.copyBugReport(info) }
                DevBtn("Share", Icons.Rounded.Share) { viewModel.shareBugReport(info) }
            }
            Spacer(Modifier.height(6.dp))
            DevToggleLine("Shake to open console", shakeToOpen, devOn, viewModel::setShakeToOpen)
            Text(
                "Crash history + all dev flags auto-reset on every version bump.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DevCard {
            DevCardTitle("Storage & database (read-only)")
            DevKv("Cache", storage.format(storage.cacheBytes))
            DevKv("Files", storage.format(storage.filesBytes))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevBtn("Refresh", Icons.Rounded.BugReport) {
                    viewModel.refreshStorageInfo()
                    viewModel.refreshTableCounts()
                }
                DevBtn("Clear cache", Icons.Rounded.DeleteSweep) { viewModel.clearCache() }
            }
            Spacer(Modifier.height(8.dp))
            if (tables.isEmpty()) Text("No counts yet — tap Refresh.", style = MaterialTheme.typography.bodySmall)
            else tables.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(t.name, modifier = Modifier.weight(1f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text(if (t.rows < 0) "?" else "${t.rows}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(Modifier.height(3.dp))
            }
        }

        DevCard {
            DevCardTitle("DataStore inspector (sensitive redacted)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DevBtn("Dump prefs", Icons.Rounded.BugReport) { viewModel.refreshPrefs() }
                DevBtn("Copy", Icons.Rounded.ContentCopy) {
                    viewModel.copyText("Prefs", prefsDump.entries.joinToString("\n") { "${it.key}=${it.value}" })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = prefQuery,
                onValueChange = { prefQuery = it },
                placeholder = { Text("Search keys…", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val shown = if (prefQuery.isBlank()) prefsDump.entries.toList()
            else prefsDump.entries.filter { it.key.contains(prefQuery, true) }
            if (shown.isEmpty()) Text("Empty — tap Dump prefs.", style = MaterialTheme.typography.bodySmall)
            else Box(
                modifier = Modifier.fillMaxWidth().height(200.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.85f)).padding(10.dp),
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(shown.take(300).size) { i ->
                            val (k, v) = shown[i]
                            Text(
                                "$k=$v",
                                color = Color(0xFF9EF0B0),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        DevCard {
            DevCardTitle("Danger zone")
            DevToggleLine(
                "ARM dangerous actions",
                dangerArmed,
                true,
                onArm,
                "Buttons below only work while armed",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DevBtn(
                    "Turn off all spoofs",
                    Icons.Rounded.Science,
                    danger = dangerArmed,
                    enabled = dangerArmed && devOn,
                ) { viewModel.clearAllSpoofs() }
                DevBtn(
                    "Reset dev mode",
                    Icons.Rounded.RestartAlt,
                    danger = dangerArmed,
                    enabled = dangerArmed,
                ) { onReset() }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Auto-reset: dev mode + all spoofs + crash history turn OFF on every version bump. Onboarding reset also clears dev flags.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}
