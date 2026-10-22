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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
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
    var showBetaInfo by rememberSaveable { mutableStateOf(false) }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Developer Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // Let the title shrink first so the BETA pill always
                            // stays on screen, even on narrow displays or with
                            // large font scaling.
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        BetaTag(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                showBetaInfo = true
                            },
                        )
                    }
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

    if (showBetaInfo) {
        BetaInfoDialog(onDismiss = { showBetaInfo = false })
    }
}

/**
 * Small M3-expressive "BETA" pill for the console title. The visual chip is
 * compact, but the tap target stays 48 dp tall and clips to a circle so the
 * ripple never looks like a rectangle around a round tag.
 */
@Composable
private fun BetaTag(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Beta version. Tap to learn what that means."
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.16f),
            ),
        ) {
            Text(
                text = "BETA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BetaInfoDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ExpressiveCard(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            elevation = 0.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Text(
                    text = "Developer Mode is in beta",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "This console is an experimental part of Toolz. It is safe to explore, " +
                        "but it is still being refined.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                BetaPoint(
                    title = "Expect rough edges",
                    body = "Labels, layout and behavior can change between updates, and a few " +
                        "corners may still look or act unfinished.",
                )
                BetaPoint(
                    title = "Some actions are real",
                    body = "Crash-lab tests close the app, and maintenance tools can delete local " +
                        "data. Every destructive action asks for confirmation first.",
                )
                BetaPoint(
                    title = "Feedback shapes it",
                    body = "If something looks wrong or confusing, send a fast report from " +
                        "Diagnostics so it can be fixed quickly.",
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = "Thanks for trying early builds.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }

                ToolzExpressiveButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BetaPoint(title: String, body: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
