/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Developer options screen: clean, reliable, professional internal engineering console.
 *
 * Organized into 3 primary tabs:
 *   1. Mocks & Sandboxes — per-tool local mock sandbox controls and parameters
 *   2. Diagnostics & Logs — real PID-filtered logcat, crash tests, notification tests, memory stats
 *   3. Storage & Routes   — storage stats, database table counts, DataStore inspector, deep link launcher
 *
 * Zero telemetry: all diagnostics stay strictly local on device.
 */

package com.frerox.toolz.ui.screens.developer

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.frerox.toolz.ui.screens.developer.components.DevCommand
import com.frerox.toolz.ui.screens.developer.components.DevMonoBox
import com.frerox.toolz.ui.screens.developer.components.DevSectionHeader
import com.frerox.toolz.ui.screens.developer.components.LogcatViewer
import com.frerox.toolz.ui.screens.developer.components.SpoofChip
import com.frerox.toolz.ui.screens.developer.components.SpoofConfiguratorSheet
import com.frerox.toolz.ui.screens.developer.components.SpoofToolTile
import com.frerox.toolz.ui.screens.developer.components.WhisperPreviewCard
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground

private enum class DevTab(val title: String) {
    MOCKS("Sandboxes"),
    DIAGNOSTICS("Diagnostics"),
    STORAGE("Storage"),
}

private data class DevRoute(val route: String, val label: String, val category: String)

private val DevRoutes: List<DevRoute> = listOf(
    // System
    DevRoute("dashboard", "Home Dashboard", "System"),
    DevRoute("settings", "Settings", "System"),
    DevRoute("app_skin", "App Skin", "System"),
    DevRoute("icon_catalog", "Icon Catalog", "System"),
    DevRoute("update", "Update Checker", "System"),
    DevRoute("toolz_news", "Toolz News", "System"),
    // Hardware & Sensors
    DevRoute("battery_info", "Battery Info", "Hardware"),
    DevRoute("device_info", "Device Info", "Hardware"),
    DevRoute("network_power_suite", "Network Power Suite", "Hardware"),
    DevRoute("wifi_tweaks", "Wi-Fi Tweaks", "Hardware"),
    DevRoute("step_counter", "Step Counter", "Hardware"),
    DevRoute("step_trends", "Step Trends", "Hardware"),
    DevRoute("compass", "Compass", "Hardware"),
    DevRoute("bubble_level", "Bubble Level", "Hardware"),
    DevRoute("speedometer", "Speedometer", "Hardware"),
    DevRoute("altimeter", "Altimeter", "Hardware"),
    DevRoute("light_meter", "Light Meter", "Hardware"),
    DevRoute("sound_meter", "Sound Meter", "Hardware"),
    DevRoute("flashlight", "Flashlight", "Hardware"),
    DevRoute("screen_light", "Screen Light", "Hardware"),
    DevRoute("caffeinate", "Caffeinate", "Hardware"),
    DevRoute("purge_shot", "PurgeShot", "Hardware"),
    // Time & Productivity
    DevRoute("timer", "Timer", "Time"),
    DevRoute("stopwatch", "Stopwatch", "Time"),
    DevRoute("pomodoro", "Pomodoro", "Time"),
    DevRoute("todo", "Todo", "Time"),
    DevRoute("calendar", "Calendar", "Time"),
    DevRoute("notepad", "Notepad", "Time"),
    // Apps
    DevRoute("clipboard", "Clipboard Manager", "Apps"),
    DevRoute("notification_vault", "Notification Vault", "Apps"),
    DevRoute("qr_generator", "QR Generator", "Apps"),
    DevRoute("media_downloader", "Media Downloader", "Apps"),
    DevRoute("music_player", "Music Player", "Apps"),
    DevRoute("file_cleaner", "File Cleaner", "Apps"),
    DevRoute("file_converter", "File Converter", "Apps"),
    DevRoute("whisper", "Whisper", "Apps"),
    DevRoute("focus_flow", "Focus Flow", "Apps"),
    DevRoute("smart_search", "Smart Search", "Apps"),
)

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun DeveloperModeScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit = {},
    viewModel: DeveloperModeViewModel = hiltViewModel(),
) {
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbar = remember { SnackbarHostState() }

    val devOn by viewModel.devModeEnabled.collectAsState(initial = false)
    val spoofs by viewModel.spoofSnapshot.collectAsState(initial = emptyMap())
    val activeCount by viewModel.activeSandboxCount.collectAsState(initial = 0)
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
    val crashHistory by viewModel.crashHistory.collectAsState()

    var selectedTab by remember { mutableIntStateOf(DevTab.MOCKS.ordinal) }
    var paletteOpen by remember { mutableStateOf(false) }
    var configuratorFor by remember { mutableStateOf<DevToolId?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearDbDialog by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf<String?>(null) }
    var showDevEnableNotice by remember { mutableStateOf(false) }

    // Gate: the console is unreachable while the master switch is OFF.
    if (!devOn) {
        DevLockedPanel(
            onBack = onBack,
            onEnable = { showDevEnableNotice = true },
        )
        if (showDevEnableNotice) {
            DevEnableNoticeDialog(
                onConfirm = { viewModel.setDevMode(true) },
                onDismiss = { showDevEnableNotice = false },
            )
        }
        return
    }

    LaunchedEffect(lastAction) {
        val msg = lastAction ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.consumeAction()
    }

    // Start or stop live logcat streaming based on active tab
    LaunchedEffect(selectedTab) {
        if (DevTab.entries[selectedTab] == DevTab.DIAGNOSTICS) {
            viewModel.setLogcatTail(true)
            viewModel.refreshLogcat()
        } else {
            viewModel.setLogcatTail(false)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (DevTab.entries[selectedTab] == DevTab.DIAGNOSTICS) {
            viewModel.setLogcatTail(true)
            viewModel.refreshLogcat()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.setLogcatTail(false) }

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Developer Settings?", fontWeight = FontWeight.Bold) },
            text = { Text("This turns Developer Mode OFF and resets all tool sandboxes and test crash history.") },
            confirmButton = {
                Button(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.resetAll()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
        )
    }

    // Clear Database confirmation dialog
    if (showClearDbDialog) {
        AlertDialog(
            onDismissRequest = { showClearDbDialog = false },
            title = { Text("Clear All Database Tables?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently clear all rows in all SQLite database tables across all tools. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        vibrationManager?.vibrateLongClick()
                        viewModel.clearDatabase()
                        showClearDbDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Clear All Data") }
            },
            dismissButton = { TextButton(onClick = { showClearDbDialog = false }) { Text("Cancel") } },
        )
    }

    // Crash confirmation dialog
    showCrashConfirm?.let { type ->
        AlertDialog(
            onDismissRequest = { showCrashConfirm = null },
            title = { Text("Simulate $type Crash?", fontWeight = FontWeight.Bold) },
            text = { Text("This will trigger a real unhandled exception on the main thread now to test crash handling.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.triggerCrash(type)
                        showCrashConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Crash Now") }
            },
            dismissButton = { TextButton(onClick = { showCrashConfirm = null }) { Text("Cancel") } },
        )
    }

    val commands = buildList {
        add(DevCommand("Toggle Developer Mode", "Switch developer mode master on/off") { viewModel.setDevMode(!devOn) })
        add(DevCommand("Enable all 18 tool sandboxes", "Mock all tools with fixture data") { viewModel.spoofAll() })
        add(DevCommand("Disable all tool sandboxes", "Restore live hardware and network data") { viewModel.clearAllSpoofs() })
        add(DevCommand("Toggle Shizuku privileged logcat", "Switch between ADB shell and standard logcat") { viewModel.setForceShizukuLogcat(!viewModel.forceShizukuLogcat.value) })
        add(DevCommand("Authorize Shizuku", "Request privileged shell permission for logcat and system tools") { viewModel.requestShizukuPermission() })
        add(DevCommand("Emit test log", "Add instant test log entry to stream") { viewModel.sendTestLog() })
        add(DevCommand("Clear logcat stream", "Clear in-memory and system logcat buffer") { viewModel.clearLogcat() })
        add(DevCommand("Copy diagnostic report", "App version, device specs, memory, uptime") { viewModel.copyBugReport(info) })
        add(DevCommand("Share diagnostic report", "Share via system chooser") { viewModel.shareBugReport(info) })
        add(DevCommand("Restart app", "Relaunch fresh application process") { viewModel.restartApp(activity) })
        add(DevCommand("Clear app cache", "Free temporary files in cache directory") { viewModel.clearCache() })
        add(DevCommand("Reset onboarding state", "Show welcome screen on next launch") { viewModel.resetOnboarding() })
        add(DevCommand("Clear all database tables", "Purge all Room database tables", danger = true) { showClearDbDialog = true })
        add(DevCommand("Open system developer options", "Android system settings") { DevUiOverrider.openSystemDeveloperOptions(context) })
        add(DevCommand("Open notification settings", "Android notification channels") { DevUiOverrider.openAppNotificationSettings(context) })
        add(DevCommand("Test: NullPointerException", "Crash test", danger = true) { showCrashConfirm = "NPE" })
        add(DevCommand("Test: IllegalStateException", "Crash test", danger = true) { showCrashConfirm = "IllegalState" })
        add(DevCommand("Test: 10s ANR", "Freeze main thread for 10s", danger = true) { showCrashConfirm = "ANR" })
        add(DevCommand("Reset developer mode", "Master off + sandboxes cleared", danger = true) { showResetDialog = true })
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Developer Options",
                subtitle = if (devOn) "Active · $activeCount mock sandbox${if (activeCount == 1) "" else "es"}" else "Disabled",
                navigationIcon = {
                    IconButton(onClick = { vibrationManager?.vibrateClick(); onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (devOn && activeCount > 0) {
                        SpoofChip(modifier = Modifier.padding(end = 4.dp), label = "$activeCount ACTIVE")
                    }
                    IconButton(onClick = { vibrationManager?.vibrateClick(); paletteOpen = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Command palette")
                    }
                    IconButton(onClick = { vibrationManager?.vibrateClick(); showResetDialog = true }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset all")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                largeFlexible = false,
                modifier = Modifier.statusBarsPadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            // Master Switch Banner Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (devOn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (devOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = if (devOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Developer Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (devOn) "Mock sandboxes intercept hardware & network APIs"
                                else "All tools use real hardware & live network APIs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = devOn,
                            onCheckedChange = { viewModel.setDevMode(it) },
                        )
                    }

                    if (devOn) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.spoofAll() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Enable All Mocks", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearAllSpoofs() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Disable All", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                DevTab.entries.forEachIndexed { i, t ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { vibrationManager?.vibrateClick(); selectedTab = i },
                        text = {
                            Text(
                                t.title,
                                fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
                }
            }

            // Tab Content
            when (DevTab.entries[selectedTab]) {
                DevTab.MOCKS -> MocksTabContent(
                    devOn = devOn,
                    spoofs = spoofs,
                    params = params,
                    viewModel = viewModel,
                    onOpenConfigurator = { configuratorFor = it },
                    onOpenRoute = onOpenRoute,
                )
                DevTab.DIAGNOSTICS -> DiagnosticsTabContent(
                    info = info,
                    logs = logs,
                    logTail = logTail,
                    crashNext = crashNext,
                    crashHistory = crashHistory,
                    viewModel = viewModel,
                    onCrashConfirm = { showCrashConfirm = it },
                )
                DevTab.STORAGE -> StorageTabContent(
                    devOn = devOn,
                    storage = storage,
                    tables = tables,
                    prefsDump = prefsDump,
                    shakeToOpen = shakeToOpen,
                    viewModel = viewModel,
                    onOpenRoute = onOpenRoute,
                    onClearDbClick = { showClearDbDialog = true },
                )
            }
        }
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

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1: MOCKS & SANDBOXES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MocksTabContent(
    devOn: Boolean,
    spoofs: Map<DevToolId, Boolean>,
    params: SpoofParams,
    viewModel: DeveloperModeViewModel,
    onOpenConfigurator: (DevToolId) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val query by viewModel.searchQuery.collectAsState()
    var selectedGroup by remember { mutableStateOf<DevToolGroup?>(null) }

    val filteredTools = remember(query, selectedGroup) {
        DevToolId.entries.filter { tool ->
            val matchesQuery = query.isBlank() || tool.title.contains(query, ignoreCase = true) || tool.prefKey.contains(query, ignoreCase = true)
            val matchesGroup = selectedGroup == null || tool.group == selectedGroup
            matchesQuery && matchesGroup
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Quick Presets Row
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Presets:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChip(
                    selected = false,
                    onClick = { viewModel.applyPreset(DemoPreset.SHOWCASE) },
                    label = { Text("Showcase", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = false,
                    onClick = { viewModel.applyPreset(DemoPreset.EDGE_CASES) },
                    label = { Text("Edge Cases", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = false,
                    onClick = { viewModel.applyPreset(DemoPreset.TRAVEL) },
                    label = { Text("Travel", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = false,
                    onClick = { viewModel.clearAllSpoofs() },
                    label = { Text("Reset to Live", fontSize = 12.sp) },
                )
            }
        }

        // Search & Filter
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search tool sandboxes…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
        }

        // Group Filter Chips
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { selectedGroup = null },
                    label = { Text("All (${DevToolId.entries.size})") },
                )
                DevToolGroup.entries.forEach { group ->
                    val count = DevToolId.entries.count { it.group == group }
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = if (selectedGroup == group) null else group },
                        label = { Text("${group.title} ($count)") },
                    )
                }
            }
        }

        // Tool Tiles
        items(filteredTools, key = { it.prefKey }) { tool ->
            SpoofToolTile(
                tool = tool,
                params = params,
                active = spoofs[tool] == true,
                devOn = devOn,
                onToggle = { viewModel.setSpoof(tool, it) },
                onConfigure = { onOpenConfigurator(tool) },
                onLaunchScreen = onOpenRoute,
            )
        }

        if (filteredTools.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No tool sandboxes match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(60.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 2: DIAGNOSTICS & LOGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DiagnosticsTabContent(
    info: DevAppInfo,
    logs: List<String>,
    logTail: Boolean,
    crashNext: Boolean,
    crashHistory: List<CrashLabEvent>,
    viewModel: DeveloperModeViewModel,
    onCrashConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    val areNotificationsEnabled = remember { viewModel.areNotificationsEnabled() }
    val shizukuActive by viewModel.shizukuActive.collectAsState()
    val forceShizuku by viewModel.forceShizukuLogcat.collectAsState()
    val logcatScope by viewModel.logcatScope.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── 1. Live Logcat (Real PID / Shizuku logs) ─────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Logcat",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "PID ${android.os.Process.myPid()}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LogcatViewer(
                        lines = logs,
                        tail = logTail,
                        onTail = viewModel::setLogcatTail,
                        shizukuActive = shizukuActive,
                        forceShizuku = forceShizuku,
                        onToggleForceShizuku = viewModel::setForceShizukuLogcat,
                        onRequestShizuku = viewModel::requestShizukuPermission,
                        scope = logcatScope,
                        onScopeChange = viewModel::setLogcatScope,
                        onRefresh = { viewModel.refreshLogcat() },
                        onTestLog = { viewModel.sendTestLog() },
                        onClear = { viewModel.clearLogcat() },
                        onCopyAll = { viewModel.copyAllLogs() },
                        onCopyWarningsAndErrors = { viewModel.copyWarningsAndErrors() },
                    )
                }
            }
        }

        // ── 2. Device & Memory Health ─────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Device Health & Process",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.copyBugReport(info) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy diagnostics", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { viewModel.shareBugReport(info) }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share diagnostics", modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiagStatPill("Heap RAM", info.heapMb, Modifier.weight(1f))
                        DiagStatPill("Native RAM", info.nativeHeapMb, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiagStatPill("Threads", "${info.threads}", Modifier.weight(1f))
                        DiagStatPill("Uptime", "${info.uptimeMs / 1000}s", Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Crash & Exception Testing ─────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Crash & Stability Testing", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Simulate unhandled errors on the main thread to test crash recovery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onCrashConfirm("NPE") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("NPE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onCrashConfirm("IllegalState") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("IllegalState", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onCrashConfirm("ANR") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("10s ANR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onCrashConfirm("OOM") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("OOM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.logNonFatal() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Log Non-Fatal Exception to Logcat", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Crash on next launch", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Simulates a crash during Application.onCreate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = crashNext,
                            onCheckedChange = { viewModel.setCrashOnNextLaunch(it) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    CrashHistoryCard(events = crashHistory, onClear = { viewModel.clearCrashHistory() })
                }
            }
        }

        // ── Notification Channel Testing ──────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notification Channels", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))

                    if (!areNotificationsEnabled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Notifications are disabled", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    Text("Android system permission is currently blocked.", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { DevUiOverrider.openAppNotificationSettings(context) }) {
                                    Text("Enable", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    listOf(
                        com.frerox.toolz.util.NotificationHelper.CHANNEL_TOOL_ALARM to "Alarm Notification",
                        com.frerox.toolz.util.NotificationHelper.CHANNEL_TOOL_ACTIVE to "Active Tool Foreground",
                        com.frerox.toolz.util.NotificationHelper.CHANNEL_STEP_COUNTER to "Step Counter Status",
                        com.frerox.toolz.util.NotificationHelper.CHANNEL_TASK_REMINDERS to "Task Reminder Alert",
                        com.frerox.toolz.util.NotificationHelper.CHANNEL_BACKUPS to "Backup Progress",
                        com.frerox.toolz.util.NotificationHelper.CHANNEL_TOOLZ_NEWS to "Toolz News Update",
                    ).forEachIndexed { i, (channelId, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.sendSpoofNotification(
                                        channelId = channelId,
                                        title = label,
                                        body = "Test notification for channel: $channelId",
                                        id = 60101 + i,
                                    )
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Send Test →", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.cancelDevNotifications() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear All Test Notifications", fontSize = 12.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun DiagStatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 3: STORAGE & ROUTES
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StorageTabContent(
    devOn: Boolean,
    storage: DevStorageInfo,
    tables: List<DevTableCount>,
    prefsDump: Map<String, String>,
    shakeToOpen: Boolean,
    viewModel: DeveloperModeViewModel,
    onOpenRoute: (String) -> Unit,
    onClearDbClick: () -> Unit,
) {
    val context = LocalContext.current
    var routeQuery by remember { mutableStateOf("") }
    var prefQuery by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Storage & State Tools ─────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Storage & App State", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiagStatPill("App Cache", storage.format(storage.cacheBytes), Modifier.weight(1f))
                        DiagStatPill("App Files", storage.format(storage.filesBytes), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.clearCache() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear Cache", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetOnboarding() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Reset Onboarding", fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onClearDbClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear All Database Tables", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Room Database Table Viewer ────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Room Database", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.refreshTableCounts() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                    }

                    if (tables.isEmpty()) {
                        TextButton(onClick = { viewModel.refreshTableCounts() }) {
                            Text("Tap to inspect database table row counts")
                        }
                    } else {
                        tables.forEach { table ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(table.name, modifier = Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                Text("${table.rows} rows", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // ── DataStore Preferences Inspector ───────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DataStore Preferences", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.refreshPrefs() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                        }
                    }

                    if (prefsDump.isEmpty()) {
                        TextButton(onClick = { viewModel.refreshPrefs() }) {
                            Text("Tap to read all DataStore preference keys")
                        }
                    } else {
                        OutlinedTextField(
                            value = prefQuery,
                            onValueChange = { prefQuery = it },
                            placeholder = { Text("Search keys…", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(8.dp))

                        val filtered = remember(prefsDump, prefQuery) {
                            if (prefQuery.isBlank()) prefsDump.entries.toList()
                            else prefsDump.entries.filter { it.key.contains(prefQuery, ignoreCase = true) }
                        }

                        DevMonoBox(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filtered.size) { i ->
                                    val (k, v) = filtered[i]
                                    Text(
                                        "$k = $v",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = if (k.startsWith("spoof_")) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.copyText(k, "$k=$v") }
                                            .padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Deep Link Screen Launcher ─────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Screen Launcher", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Instant test navigation to any screen in the application.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = routeQuery,
                        onValueChange = { routeQuery = it },
                        placeholder = { Text("Filter ${DevRoutes.size} routes…", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(8.dp))

                    val filteredRoutes = remember(routeQuery) {
                        if (routeQuery.isBlank()) DevRoutes
                        else DevRoutes.filter { it.route.contains(routeQuery, ignoreCase = true) || it.label.contains(routeQuery, ignoreCase = true) }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredRoutes.take(15).forEach { dr ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpenRoute(dr.route) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(dr.label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(dr.route, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Rounded.OpenInNew, contentDescription = "Launch", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (filteredRoutes.size > 15) {
                            Text(
                                "+ ${filteredRoutes.size - 15} more routes (type in search box to filter)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── System Shortcuts & Gestures ───────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("System & Gestures", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Shake to open console", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("2.6g acceleration threshold, zero sensor battery cost when OFF", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = shakeToOpen,
                            enabled = devOn,
                            onCheckedChange = { viewModel.setShakeToOpen(it) },
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { DevUiOverrider.openSystemDeveloperOptions(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Android System Developer Options", fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { DevUiOverrider.openAppNotificationSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open App Notification Settings", fontSize = 12.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOCKED STATE & DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DevLockedPanel(
    onBack: () -> Unit,
    onEnable: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp).size(36.dp),
            )
        }
        Text(
            "Developer Options Locked",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Developer Mode is currently disabled. Enable it to access internal diagnostics, live logcat, memory monitoring, and mock tool sandboxes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vibrationManager?.vibrateClick(); onEnable() },
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Enable Developer Mode", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = { vibrationManager?.vibrateClick(); onBack() }) {
            Text("Back", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DevEnableNoticeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Developer Options?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Developer Mode provides diagnostic tools, logcat viewing, and mock tool sandboxes for testing.\n\n" +
                    "All data and logs stay strictly local on this device. Developer flags automatically reset after app updates.",
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
            ) { Text("Enable") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
