/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Developer console — redesigned: 3 tabs (Spoofs / Labs / System), M3
 * Expressive components only (ListItem + Switch, TabRow, cards), no
 * hand-rolled rows. Every toggle here is local-only (see DEVELOPER-MODE.md).
 */

package com.frerox.toolz.ui.screens.developer

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.FakeWhisperSandbox
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.screens.developer.components.SpoofBanner
import com.frerox.toolz.ui.screens.developer.components.SpoofChip
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

private fun spoofSummary(tool: DevToolId, p: SpoofParams): String = when (tool) {
    DevToolId.BATTERY -> "${p.batteryLevel}% • ${p.batteryTempC}°C"
    DevToolId.DEVICE -> p.deviceModel
    DevToolId.NETWORK -> "${p.networkSsid} • ${p.networkPingMs}ms"
    DevToolId.STEPS -> "${p.stepsToday} steps"
    DevToolId.SENSORS -> "${p.compassDeg}° • ${p.soundDb}dB • ${p.speedKmh}km/h"
    DevToolId.MUSIC -> "${p.musicTrack} — ${p.musicArtist}"
    DevToolId.DOWNLOADER -> "${p.downloadProgress}% (stuck)"
    DevToolId.CLEANER -> "${p.storageFullPct}% full"
    DevToolId.TIMERS -> "${p.timerSecs}s timer"
    DevToolId.QR -> p.qrPayload.take(24)
    else -> "fixture active"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeveloperModeScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit = {},
    viewModel: DeveloperModeViewModel = hiltViewModel(),
) {
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val devOn by viewModel.devModeEnabled.collectAsState(initial = false)
    val spoofs by viewModel.allSpoofFlags.collectAsState(initial = emptyMap())
    val activeCount by viewModel.activeSandboxCount.collectAsState(initial = 0)
    val verbose by viewModel.devVerboseLogging.collectAsState(initial = false)
    val mockDelay by viewModel.devMockDelayMs.collectAsState(initial = 0)
    val perfHud by viewModel.devPerfHud.collectAsState(initial = false)
    val forceOffline by viewModel.devForceOffline.collectAsState(initial = false)
    val crashNext by viewModel.devCrashOnNextLaunch.collectAsState(initial = false)
    val query by viewModel.searchQuery.collectAsState()
    val info by viewModel.appInfo.collectAsState()
    val logs by viewModel.logLines.collectAsState()
    val lastAction by viewModel.lastAction.collectAsState()
    val params by viewModel.spoofParams.collectAsState()
    val shakeToOpen by viewModel.devShakeToOpen.collectAsState(initial = false)
    val tables by viewModel.tableCounts.collectAsState()
    val storage by viewModel.storageInfo.collectAsState()
    val prefsDump by viewModel.prefsSnapshot.collectAsState()

    val activity = context as? android.app.Activity
    var rtlOn by remember { mutableStateOf(activity?.let { com.frerox.toolz.data.developer.DevUiOverrider.isRtl(it) } == true) }

    var tab by remember { mutableStateOf(0) }
    var searchActive by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showCrashConfirm by remember { mutableStateOf<String?>(null) }

    lastAction?.let { msg ->
        scope.launch { snackbar.showSnackbar(msg) }
        viewModel.consumeAction()
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset developer mode?", fontWeight = FontWeight.Black) },
            text = { Text("Turns master OFF and clears all 18 spoof flags. Slider values are kept. Real data returns instantly.") },
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
            text = { Text("This will $type the process NOW. Unsaved work may be lost. Local test only.") },
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

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "Developer Mode",
                subtitle = if (devOn) "ON • $activeCount sandbox${if (activeCount == 1) "" else "es"} active • local only" else "Handle with care",
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
                        onClick = { vibrationManager?.vibrateClick(); showResetDialog = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp))
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
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onSearch = {},
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        placeholder = { Text("Search tools, labs, prefs…") },
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                content = {},
            )

            if (!devOn) {
                DevCard(Modifier.padding(horizontal = 24.dp)) {
                    Text("Developer Mode is OFF", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        "Turn it on in Settings to unlock these tools. Spoofs stay local-only and auto-disable after every app update.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.setDevMode(true) }) {
                        Text("ENABLE DEVELOPER MODE", fontWeight = FontWeight.Black)
                    }
                }
            }

            if (devOn && activeCount > 0) {
                SpoofBanner(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    text = "$activeCount SPOOF SANDBOX${if (activeCount == 1) "" else "ES"} ACTIVE — fake local data. Nothing was sent, saved, or deleted.",
                )
            }

            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(horizontal = 24.dp)) {
                listOf("Spoofs", "Labs", "System").forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onClick = { vibrationManager?.vibrateClick(); tab = i },
                        text = { Text(title, fontWeight = FontWeight.Black) },
                    )
                }
            }

            when (tab) {
                0 -> SpoofsTab(
                    devOn = devOn,
                    spoofs = spoofs,
                    activeCount = activeCount,
                    params = params,
                    query = query,
                    viewModel = viewModel,
                )
                1 -> LabsTab(
                    devOn = devOn,
                    verbose = verbose,
                    mockDelay = mockDelay,
                    perfHud = perfHud,
                    forceOffline = forceOffline,
                    crashNext = crashNext,
                    logs = logs,
                    query = query,
                    activity = activity,
                    rtlOn = rtlOn,
                    onRtl = {
                        rtlOn = it
                        activity?.let { a -> com.frerox.toolz.data.developer.DevUiOverrider.setRtl(a, it) }
                    },
                    onOpenRoute = onOpenRoute,
                    showCrashConfirm = { showCrashConfirm = it },
                    viewModel = viewModel,
                )
                else -> SystemTab(
                    devOn = devOn,
                    info = info,
                    shakeToOpen = shakeToOpen,
                    tables = tables,
                    storage = storage,
                    prefsDump = prefsDump,
                    query = query,
                    viewModel = viewModel,
                )
            }
        }
    }
}

// ---------- Tabs ----------

@Composable
private fun SpoofsTab(
    devOn: Boolean,
    spoofs: Map<String, Boolean>,
    activeCount: Int,
    params: SpoofParams,
    query: String,
    viewModel: DeveloperModeViewModel,
) {
    val tools = remember(query) {
        DevToolId.entries.filter {
            query.isBlank() || it.title.contains(query, true) || it.prefKey.contains(query, true)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DevCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("All sandboxes", fontWeight = FontWeight.Black)
                        Text(
                            if (!devOn) "Master is OFF — everything shows real data"
                            else if (activeCount == 0) "None active — everything shows real data"
                            else "$activeCount active — tap a row to configure",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(enabled = devOn && activeCount > 0, onClick = { viewModel.clearAllSpoofs() }) {
                        Text("TURN ALL OFF", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
                if (query.isBlank()) {
                    DevDivider()
                    Text("Fixture values (persisted, applied locally)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    DevSliderRow("Battery", "${params.batteryLevel}%", params.batteryLevel.toFloat(), 0f, 100f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(batteryLevel = v.toInt()) }
                    }
                    DevSliderRow("Battery temp", "${params.batteryTempC}°C", params.batteryTempC, -10f, 65f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(batteryTempC = v) }
                    }
                    DevSliderRow("Steps", "${params.stepsToday}", params.stepsToday.toFloat(), 0f, 200000f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(stepsToday = v.toInt()) }
                    }
                    DevSliderRow("Fake ping", "${params.networkPingMs}ms", params.networkPingMs.toFloat(), 0f, 10000f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(networkPingMs = v.toInt()) }
                    }
                    DevSliderRow("Compass", "${params.compassDeg}°", params.compassDeg.toFloat(), 0f, 359f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(compassDeg = v.toInt()) }
                    }
                    DevSliderRow("Sound", "${params.soundDb}dB", params.soundDb.toFloat(), 0f, 140f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(soundDb = v.toInt()) }
                    }
                    DevSliderRow("Speed", "${params.speedKmh} km/h", params.speedKmh.toFloat(), 0f, 1200f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(speedKmh = v.toInt()) }
                    }
                    DevSliderRow("Altitude", "${params.altitudeM} m", params.altitudeM.toFloat(), -500f, 12000f, devOn) { v ->
                        viewModel.updateSpoofParams { it.copy(altitudeM = v.toInt()) }
                    }
                    var charging by remember(params.batteryCharging) { mutableStateOf(params.batteryCharging) }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Battery charging", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Switch(checked = charging, enabled = devOn, onCheckedChange = {
                            charging = it
                            viewModel.updateSpoofParams { p -> p.copy(batteryCharging = it) }
                        })
                    }
                }
            }
        }
        if (tools.isEmpty()) {
            item { Text("No tools match \"$query\".", modifier = Modifier.padding(8.dp)) }
        }
        items(tools, key = { it.prefKey }) { tool ->
            val on = spoofs[tool.prefKey] == true
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (on && devOn) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                onClick = { if (devOn) viewModel.setSpoof(tool, !on) },
            ) {
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tool.title, fontWeight = FontWeight.Bold)
                            if (on && devOn) {
                                Spacer(Modifier.width(8.dp))
                                SpoofChip()
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            if (on && devOn) "SANDBOX • ${spoofSummary(tool, params)}" else tool.subtitle,
                            fontSize = 12.sp,
                        )
                    },
                    leadingContent = {
                        Surface(
                            shape = CircleShape,
                            color = if (on && devOn) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    tool.title.firstOrNull()?.toString() ?: "D",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Switch(checked = on, enabled = devOn, onCheckedChange = { viewModel.setSpoof(tool, it) })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        if (query.isBlank()) {
            item {
                DevCard {
                    Text(FakeWhisperSandbox.BANNER, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text("Whisper sandbox preview (offline)", fontWeight = FontWeight.Black)
                    FakeWhisperSandbox.contacts.forEach { c ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold)
                                Text(c.lastMessage, style = MaterialTheme.typography.bodySmall)
                            }
                            if (c.unread > 0) {
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                                    Text("${c.unread}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Text("Send is disabled here — fixtures only. No Supabase, no realtime.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun LabsTab(
    devOn: Boolean,
    verbose: Boolean,
    mockDelay: Int,
    perfHud: Boolean,
    forceOffline: Boolean,
    crashNext: Boolean,
    logs: List<String>,
    query: String,
    activity: android.app.Activity?,
    rtlOn: Boolean,
    onRtl: (Boolean) -> Unit,
    onOpenRoute: (String) -> Unit,
    showCrashConfirm: (String) -> Unit,
    viewModel: DeveloperModeViewModel,
) {
    fun show(vararg kw: String) = query.isBlank() || kw.any { it.contains(query, true) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (show("crash", "anr", "oom", "error", "lab")) {
            DevCard {
                DevCardTitle("Crash & error lab")
                Text("Each action is local. Confirm first.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn("NPE", Icons.Rounded.BugReport) { showCrashConfirm("NPE") }
                    DevBtn("ANR 6s", Icons.Rounded.BugReport) { showCrashConfirm("ANR") }
                    DevBtn("OOM", Icons.Rounded.BugReport) { showCrashConfirm("OOM") }
                }
                Spacer(Modifier.height(8.dp))
                DevBtn("Log non-fatal test exception", Icons.Rounded.BugReport) { viewModel.logNonFatal() }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Crash on next launch", fontWeight = FontWeight.Bold)
                        Text("Cleared after update", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = crashNext, enabled = devOn, onCheckedChange = viewModel::setCrashOnNextLaunch)
                }
            }
        }
        if (show("notif", "channel", "flood", "stress", "alarm")) {
            DevCard {
                DevCardTitle("Notification lab ([SPOOF TEST])")
                Text("Posted to real channels for look/sound checks; titles are tagged, IDs live in 60001+.", style = MaterialTheme.typography.bodySmall)
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
                DevBtn("System notification settings", Icons.Rounded.Share) {
                    com.frerox.toolz.data.developer.DevUiOverrider.openAppNotificationSettings(viewModel.appContext())
                }
            }
        }
        if (show("logcat", "log", "verbose", "offline", "delay", "mock")) {
            DevCard {
                DevCardTitle("Data lab")
                DevToggleLine("Verbose logging", verbose, devOn, viewModel::setVerbose)
                DevToggleLine("Force offline override", forceOffline, devOn, viewModel::setForceOffline)
                Text("Mock network delay: ${mockDelay}ms", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Slider(value = mockDelay.toFloat(), onValueChange = { viewModel.setMockDelay(it.toInt()) }, valueRange = 0f..5000f, enabled = devOn)
                Text("Logcat (last 300, filtered)", fontWeight = FontWeight.Black, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn("Refresh", Icons.Rounded.BugReport) { viewModel.refreshLogcat() }
                    DevBtn("Clear", Icons.Rounded.DeleteSweep) { viewModel.clearLogcat() }
                    DevBtn("Copy", Icons.Rounded.ContentCopy) { viewModel.copyText("Logcat", logs.joinToString("\n")) }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.85f)).padding(10.dp),
                ) {
                    Text(
                        if (logs.isEmpty()) "(tap Refresh)" else logs.takeLast(40).joinToString("\n"),
                        color = Color(0xFF9EF0B0), fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        if (show("network", "route", "deep", "intent", "navigat", "link")) {
            DevCard {
                DevCardTitle("Navigation & deep-link lab")
                listOf(
                    "dashboard" to "Home", "settings" to "Settings", "battery_info" to "Battery",
                    "device_info" to "Device info", "step_counter" to "Steps", "media_downloader" to "Downloader",
                    "file_cleaner" to "Cleaner", "whisper" to "Whisper (real)",
                ).forEach { (route, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = devOn) { onOpenRoute(route) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("→ $route", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        if (show("ui", "hud", "perf", "rtl", "font", "theme", "dark", "light", "language", "locale", "endpoint")) {
            DevCard {
                DevCardTitle("UI playground")
                DevToggleLine("Perf HUD overlay flag (applies on restart)", perfHud, devOn, viewModel::setPerfHud)
                DevToggleLine("Force RTL (this Activity, reversible)", rtlOn, devOn && activity != null, onRtl)
                Text("Font scale (recreates Activity)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.85f to "S", 1f to "M", 1.3f to "L", 1.6f to "XL").forEach { (scale, label) ->
                        TextButton(enabled = devOn && activity != null, onClick = { activity?.let { com.frerox.toolz.data.developer.DevUiOverrider.setFontScale(it, scale) } }) {
                            Text(label, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text("Force appearance", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SYSTEM" to "Sys", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                        TextButton(enabled = devOn, onClick = { viewModel.setThemeMode(mode) }) { Text(label, fontWeight = FontWeight.Black) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn("System dev options", Icons.Rounded.Share) {
                        com.frerox.toolz.data.developer.DevUiOverrider.openSystemDeveloperOptions(viewModel.appContext())
                    }
                }
                Text("Layout bounds need WRITE_SECURE_SETTINGS — use system dev options.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DevDivider()
                Text("Language forcer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en" to "EN", "ar" to "AR", "fr" to "FR", "es" to "ES", "system" to "SYS").forEach { (code, label) ->
                        TextButton(enabled = devOn, onClick = { viewModel.applyLanguage(code) }) { Text(label, fontWeight = FontWeight.Black) }
                    }
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
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SystemTab(
    devOn: Boolean,
    info: DevAppInfo,
    shakeToOpen: Boolean,
    tables: List<DevTableCount>,
    storage: DevStorageInfo,
    prefsDump: Map<String, String>,
    query: String,
    viewModel: DeveloperModeViewModel,
) {
    fun show(vararg kw: String) = query.isBlank() || kw.any { it.contains(query, true) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (show("status", "version", "build", "app info", "bug", "shake", "fingerprint")) {
            DevCard {
                DevCardTitle("Status & build")
                DevKv("Version", "${info.versionName} (${info.versionCode})")
                DevKv("Package", info.packageName)
                DevKv("Uptime", "${info.uptimeMs / 1000}s")
                DevKv("Heap", info.heapMb)
                DevKv("Native heap", info.nativeHeapMb)
                DevKv("Threads", info.threads.toString())
                DevKv("Density", "${com.frerox.toolz.data.developer.DevUiOverrider.densityDpi(viewModel.appContext())}dpi")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn("Copy", Icons.Rounded.ContentCopy) { viewModel.copyAppInfo(info) }
                    DevBtn("Bug template", Icons.Rounded.ContentCopy) { viewModel.copyBugTemplate(info) }
                }
                Spacer(Modifier.height(8.dp))
                DevToggleLine("Shake to open console", shakeToOpen, devOn, viewModel::setShakeToOpen)
            }
        }
        if (show("room", "table", "database", "storage", "cache", "files")) {
            DevCard {
                DevCardTitle("Storage & database (read-only)")
                DevKv("Cache", storage.format(storage.cacheBytes))
                DevKv("Files", storage.format(storage.filesBytes))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn("Refresh", Icons.Rounded.BugReport) { viewModel.refreshStorageInfo(); viewModel.refreshTableCounts() }
                    DevBtn("Clear cache", Icons.Rounded.DeleteSweep) { viewModel.clearCache() }
                }
                Spacer(Modifier.height(8.dp))
                if (tables.isEmpty()) Text("No counts yet — tap Refresh.", style = MaterialTheme.typography.bodySmall)
                else tables.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(t.name, modifier = Modifier.weight(1f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(if (t.rows < 0) "?" else "${t.rows}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
        if (show("prefs", "datastore", "inspector", "keys")) {
            DevCard {
                DevCardTitle("DataStore inspector (sensitive redacted)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DevBtn("Dump prefs", Icons.Rounded.BugReport) { viewModel.refreshPrefs() }
                    DevBtn("Copy", Icons.Rounded.ContentCopy) { viewModel.copyText("Prefs", prefsDump.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
                }
                Spacer(Modifier.height(8.dp))
                val shown = if (query.isBlank()) prefsDump.entries.toList()
                else prefsDump.entries.filter { it.key.contains(query, true) }
                if (shown.isEmpty()) Text("Empty — tap Dump prefs.", style = MaterialTheme.typography.bodySmall)
                else Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.85f)).padding(10.dp),
                ) {
                    SelectionContainer {
                        Text(
                            shown.take(120).joinToString("\n") { "${it.key}=${it.value}" },
                            color = Color(0xFF9EF0B0), fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
        DevCard {
            DevCardTitle("Danger zone")
            DevBtn("Turn off all spoofs", Icons.Rounded.Science) { viewModel.clearAllSpoofs() }
            Spacer(Modifier.height(8.dp))
            Text("Auto-reset: dev mode + all spoofs turn OFF on every version bump. Onboarding reset also clears dev flags.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(80.dp))
    }
}

// ---------- Shared bits ----------

@Composable
private fun DevCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun DevCardTitle(text: String) {
    Text(text.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun DevDivider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun DevKv(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(v, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun DevToggleLine(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun DevSliderRow(title: String, valueText: String, value: Float, min: Float, max: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(valueText, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max, enabled = enabled)
    }
}

@Composable
private fun DevBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.buttonColors(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
