/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Developer Mode console.
 *
 * Four coherent tabs:
 *   Overview     — master control, active sandboxes, runtime stats, presets
 *   Sandboxes    — searchable/grouped fixture controls for all tools
 *   Diagnostics  — local report, logcat, crash lab, notifications
 *   System       — runtime/read-only context, storage, preferences, links
 *
 * Nothing leaves the device. Fixtures, logs and reports stay local; text
 * leaves only through the clipboard or the system share sheet on request.
 */

package com.frerox.toolz.ui.screens.developer

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.screens.developer.components.DevInlineNotice
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground

internal enum class DeveloperTab(val title: String) {
    OVERVIEW("Overview"),
    SANDBOXES("Sandboxes"),
    DIAGNOSTICS("Diagnostics"),
    SYSTEM("System"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperModeScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit = {},
    viewModel: DeveloperModeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val vibrationManager = LocalVibrationManager.current
    val snackbar = remember { SnackbarHostState() }

    val devOn by viewModel.devModeEnabled.collectAsStateWithLifecycle()
    val spoofs by viewModel.spoofSnapshot.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(DeveloperTab.OVERVIEW.ordinal) }
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    var showEnableDialog by rememberSaveable { mutableStateOf(false) }
    val currentTab = DeveloperTab.entries.getOrElse(selectedTab) { DeveloperTab.OVERVIEW }
    val activeCount = spoofs.values.count { it }

    LaunchedEffect(devOn) {
        if (devOn != true) paletteOpen = false
    }

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbar.showSnackbar(
            message = current.text,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    Text(
                        text = "Developer Mode",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = {
                    Text(
                        text = when (devOn) {
                            null -> "Loading local console"
                            false -> "Off · console locked"
                            true -> "${currentTab.title} · $activeCount of ${DevToolId.entries.size} active"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (devOn == true) {
                        IconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                paletteOpen = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Open command palette",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(innerPadding),
        ) {
            when (devOn) {
                null -> DeveloperLoadingPanel()
                false -> DeveloperLockedPanel(
                    onEnable = {
                        vibrationManager?.vibrateClick()
                        showEnableDialog = true
                    },
                )
                true -> DeveloperConsole(
                    viewModel = viewModel,
                    spoofs = spoofs,
                    selectedTab = selectedTab,
                    onSelectTab = { tab ->
                        vibrationManager?.vibrateClick()
                        selectedTab = tab.ordinal
                    },
                    paletteOpen = paletteOpen,
                    onDismissPalette = { paletteOpen = false },
                    onOpenRoute = onOpenRoute,
                    activity = activity,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showEnableDialog) {
        DevConfirmDialog(
            title = "Turn on Developer Mode?",
            body = "Sandboxes replace tool output with local fixtures. Real data, files, network and sensors stay untouched. You can turn everything off at any time.",
            confirmLabel = "Turn on",
            icon = Icons.Rounded.Science,
            onConfirm = {
                showEnableDialog = false
                viewModel.setDevMode(true)
            },
            onDismiss = { showEnableDialog = false },
        )
    }
}

@Composable
private fun DeveloperLoadingPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DeveloperLockedPanel(onEnable: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(84.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Text(
                    text = "Developer Mode is off",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Turn on the local sandbox console to inspect fixture data, runtime stats, logcat and storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onEnable,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                    )
                    Text("Turn on Developer Mode")
                }
                DevInlineNotice(
                    icon = Icons.Rounded.Lock,
                    text = "Local-only. No account, network access or uploads; every value here is generated on this device.",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
