/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Enabled Developer Mode console. Owns tab state, logcat lifecycle, sheets,
 * dialogs and the command palette; each tab lives in its own file.
 */

package com.frerox.toolz.ui.screens.developer

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.ui.screens.developer.components.CommandPalette
import com.frerox.toolz.ui.screens.developer.components.RouteLauncherSheet
import com.frerox.toolz.ui.screens.developer.components.SpoofConfiguratorSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeveloperConsole(
    viewModel: DeveloperModeViewModel,
    spoofs: Map<DevToolId, Boolean>,
    selectedTab: Int,
    onSelectTab: (DeveloperTab) -> Unit,
    paletteOpen: Boolean,
    onDismissPalette: () -> Unit,
    onOpenRoute: (String) -> Unit,
    activity: Activity?,
    modifier: Modifier = Modifier,
) {
    val params by viewModel.spoofParams.collectAsStateWithLifecycle()
    val appInfo by viewModel.appInfo.collectAsStateWithLifecycle()
    val heapHistory by viewModel.heapHistory.collectAsStateWithLifecycle()
    val crashNext by viewModel.devCrashOnNextLaunch.collectAsStateWithLifecycle()
    val crashHistory by viewModel.crashHistory.collectAsStateWithLifecycle()
    val logs by viewModel.logLines.collectAsStateWithLifecycle()
    val logcatTail by viewModel.logcatTail.collectAsStateWithLifecycle()
    val logcatScope by viewModel.logcatScope.collectAsStateWithLifecycle()
    val logcatClearedAtMs by viewModel.logcatClearedAtMs.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizukuState.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val postedNotificationIds by viewModel.postedNotificationIds.collectAsStateWithLifecycle()
    val shakeToOpen by viewModel.devShakeToOpen.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val tableState by viewModel.tableState.collectAsStateWithLifecycle()
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()
    val prefsState by viewModel.prefsState.collectAsStateWithLifecycle()

    var configuratorFor by remember { mutableStateOf<DevToolId?>(null) }
    var routeLauncherOpen by remember { mutableStateOf(false) }
    var crashToConfirm by remember { mutableStateOf<DevCrashTest?>(null) }
    var showClearCrashHistory by remember { mutableStateOf(false) }
    var showResetDeveloperMode by remember { mutableStateOf(false) }
    var showResetFixtures by remember { mutableStateOf(false) }
    var showClearDatabase by remember { mutableStateOf(false) }
    var showResetOnboarding by remember { mutableStateOf(false) }
    var showRestart by remember { mutableStateOf(false) }

    val tab = DeveloperTab.entries.getOrElse(selectedTab) { DeveloperTab.OVERVIEW }

    // Logcat commands are global in the palette, but the live tail is only
    // allowed to run while Diagnostics is on screen. Resuming from another
    // tab therefore navigates there first.
    val onToggleLogcatTail = {
        val resume = !logcatTail
        if (resume && tab != DeveloperTab.DIAGNOSTICS) {
            onSelectTab(DeveloperTab.DIAGNOSTICS)
        }
        viewModel.setLogcatTail(resume)
    }

    LaunchedEffect(tab) {
        when (tab) {
            DeveloperTab.DIAGNOSTICS -> {
                viewModel.checkShizukuStatus()
                viewModel.refreshNotificationsEnabled()
                viewModel.setLogcatVisible(true)
            }
            DeveloperTab.SYSTEM -> {
                viewModel.setLogcatVisible(false)
                viewModel.refreshStorageInfo()
                viewModel.refreshTableCounts()
                viewModel.refreshPrefs()
            }
            else -> viewModel.setLogcatVisible(false)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationsEnabled()
        when (tab) {
            DeveloperTab.DIAGNOSTICS -> viewModel.setLogcatVisible(true)
            DeveloperTab.SYSTEM -> {
                viewModel.setLogcatVisible(false)
                viewModel.refreshStorageInfo()
                viewModel.refreshTableCounts()
                viewModel.refreshPrefs()
            }
            else -> viewModel.setLogcatVisible(false)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.setLogcatVisible(false)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setLogcatVisible(false) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            DeveloperTab.entries.forEach { entry ->
                Tab(
                    selected = entry == tab,
                    onClick = { onSelectTab(entry) },
                    text = {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                DeveloperTab.OVERVIEW -> DeveloperOverviewTab(
                    spoofs = spoofs,
                    appInfo = appInfo,
                    heapHistory = heapHistory,
                    onToggleDevMode = viewModel::setDevMode,
                    onEnableAll = viewModel::spoofAll,
                    onClearAll = viewModel::clearAllSpoofs,
                    onDisableTool = { tool -> viewModel.setSpoof(tool, false) },
                    onApplyPreset = viewModel::applyPreset,
                    onOpenSandboxes = { onSelectTab(DeveloperTab.SANDBOXES) },
                    onOpenDiagnostics = { onSelectTab(DeveloperTab.DIAGNOSTICS) },
                    onCopyReport = viewModel::copyBugReport,
                    onShareReport = viewModel::shareBugReport,
                )

                DeveloperTab.SANDBOXES -> DeveloperSandboxesTab(
                    spoofs = spoofs,
                    params = params,
                    onToggleDevMode = viewModel::setDevMode,
                    onEnableAll = viewModel::spoofAll,
                    onClearAll = viewModel::clearAllSpoofs,
                    onToggleTool = viewModel::setSpoof,
                    onConfigure = { configuratorFor = it },
                )

                DeveloperTab.DIAGNOSTICS -> DeveloperDiagnosticsTab(
                    appInfo = appInfo,
                    logs = logs,
                    logcatTail = logcatTail,
                    logcatScope = logcatScope,
                    shizuku = shizuku,
                    clearedAtMs = logcatClearedAtMs,
                    crashNext = crashNext,
                    crashHistory = crashHistory,
                    notificationsEnabled = notificationsEnabled,
                    postedNotificationIds = postedNotificationIds,
                    onToggleTail = onToggleLogcatTail,
                    onScopeChange = viewModel::setLogcatScope,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onRefreshLogcat = viewModel::refreshLogcat,
                    onClearLogcat = viewModel::clearLogcat,
                    onCopyAllLogs = viewModel::copyAllLogs,
                    onCopyWarnLogs = viewModel::copyWarningsAndErrors,
                    onSendTestLog = { viewModel.sendTestLog() },
                    onSetCrashNext = viewModel::setCrashOnNextLaunch,
                    onCrash = { crashToConfirm = it },
                    onLogNonFatal = viewModel::logNonFatal,
                    onClearCrashHistory = { showClearCrashHistory = true },
                    onSendNotification = viewModel::sendTestNotification,
                    onClearNotifications = viewModel::cancelDevNotifications,
                    onOpenNotificationSettings = viewModel::openNotificationSettings,
                    onCopyReport = viewModel::copyBugReport,
                    onShareReport = viewModel::shareBugReport,
                )

                DeveloperTab.SYSTEM -> DeveloperSystemTab(
                    appInfo = appInfo,
                    themeMode = themeMode,
                    appLanguage = appLanguage,
                    shakeToOpen = shakeToOpen,
                    storageState = storageState,
                    tableState = tableState,
                    prefsState = prefsState,
                    onSetShakeToOpen = viewModel::setShakeToOpen,
                    onRefreshStorage = viewModel::refreshStorageInfo,
                    onClearCache = viewModel::clearCache,
                    onRefreshTables = viewModel::refreshTableCounts,
                    onClearDatabase = { showClearDatabase = true },
                    onRefreshPrefs = viewModel::refreshPrefs,
                    onCopyPrefs = { values ->
                        viewModel.copyText(
                            label = "Preferences",
                            text = values.entries.joinToString("\n") { (key, value) -> "$key=$value" },
                        )
                    },
                    onOpenRoute = onOpenRoute,
                    onOpenRouteLauncher = { routeLauncherOpen = true },
                    onResetFixtures = { showResetFixtures = true },
                    onResetOnboarding = { showResetOnboarding = true },
                    onResetDeveloperMode = { showResetDeveloperMode = true },
                    onRestart = { showRestart = true },
                    onOpenNotificationSettings = viewModel::openNotificationSettings,
                    onOpenSystemDeveloperOptions = viewModel::openSystemDeveloperOptions,
                    onOpenDiscord = viewModel::openDiscord,
                )
            }
        }
    }

    configuratorFor?.let { tool ->
        SpoofConfiguratorSheet(
            tool = tool,
            params = params,
            active = spoofs[tool] == true,
            onUpdate = viewModel::updateSpoofParams,
            onToggle = { enabled -> viewModel.setSpoof(tool, enabled) },
            onOpenRoute = onOpenRoute,
            onDismiss = { configuratorFor = null },
        )
    }

    if (routeLauncherOpen) {
        RouteLauncherSheet(
            onRouteSelected = onOpenRoute,
            onDismiss = { routeLauncherOpen = false },
        )
    }

    if (paletteOpen) {
        val commands = buildDeveloperCommands(
            viewModel = viewModel,
            logcatTail = logcatTail,
            onToggleLogcatTail = onToggleLogcatTail,
            onSelectTab = onSelectTab,
            onOpenRoute = onOpenRoute,
            onOpenRouteLauncher = { routeLauncherOpen = true },
            onCrash = { crashToConfirm = it },
            onResetFixtures = { showResetFixtures = true },
            onResetDeveloperMode = { showResetDeveloperMode = true },
            onClearDatabase = { showClearDatabase = true },
            onResetOnboarding = { showResetOnboarding = true },
            onRestart = { showRestart = true },
        )
        CommandPalette(
            visible = true,
            commands = commands,
            onDismiss = onDismissPalette,
        )
    }

    crashToConfirm?.let { test ->
        DevConfirmDialog(
            title = "Run ${test.title}?",
            body = "${test.description} Crash-lab history is written first, then the app closes.",
            confirmLabel = test.actionLabel,
            icon = Icons.Rounded.BugReport,
            destructive = true,
            onConfirm = {
                crashToConfirm = null
                viewModel.triggerCrash(test)
            },
            onDismiss = { crashToConfirm = null },
        )
    }

    if (showClearCrashHistory) {
        DevConfirmDialog(
            title = "Clear crash-lab history?",
            body = "Removes every recorded test event. Crash behavior and fixture values are unchanged.",
            confirmLabel = "Clear history",
            destructive = true,
            onConfirm = {
                showClearCrashHistory = false
                viewModel.clearCrashHistory()
            },
            onDismiss = { showClearCrashHistory = false },
        )
    }

    if (showResetFixtures) {
        DevConfirmDialog(
            title = "Reset fixture values?",
            body = "Restores every fixture parameter to its documented default. Sandbox selections and Developer Mode stay on.",
            confirmLabel = "Restore defaults",
            icon = Icons.Rounded.Restore,
            destructive = true,
            onConfirm = {
                showResetFixtures = false
                viewModel.resetSpoofParams()
            },
            onDismiss = { showResetFixtures = false },
        )
    }

    if (showResetDeveloperMode) {
        DevConfirmDialog(
            title = "Reset Developer Mode?",
            body = "Turns the master switch off, deactivates every sandbox and clears crash-lab history. Shake-to-open and fixture values are kept.",
            confirmLabel = "Reset",
            icon = Icons.Rounded.Science,
            destructive = true,
            onConfirm = {
                showResetDeveloperMode = false
                viewModel.resetAll()
            },
            onDismiss = { showResetDeveloperMode = false },
        )
    }

    if (showClearDatabase) {
        DevConfirmDialog(
            title = "Clear every Room table?",
            body = "Deletes every row in every AppDatabase table. This cannot be undone. Sandbox fixtures and preferences are not affected.",
            confirmLabel = "Delete all rows",
            icon = Icons.Rounded.DeleteForever,
            destructive = true,
            onConfirm = {
                showClearDatabase = false
                viewModel.clearDatabase()
            },
            onDismiss = { showClearDatabase = false },
        )
    }

    if (showResetOnboarding) {
        DevConfirmDialog(
            title = "Reset onboarding?",
            body = "The welcome flow appears on the next launch. Existing tools, preferences and sandbox values are kept.",
            confirmLabel = "Reset onboarding",
            onConfirm = {
                showResetOnboarding = false
                viewModel.resetOnboarding()
            },
            onDismiss = { showResetOnboarding = false },
        )
    }

    if (showRestart) {
        DevConfirmDialog(
            title = "Restart Toolz?",
            body = "The app closes and relaunches immediately so every screen picks up the current sandbox selection.",
            confirmLabel = "Restart",
            icon = Icons.Rounded.RestartAlt,
            onConfirm = {
                showRestart = false
                viewModel.restartApp(activity)
            },
            onDismiss = { showRestart = false },
        )
    }
}
