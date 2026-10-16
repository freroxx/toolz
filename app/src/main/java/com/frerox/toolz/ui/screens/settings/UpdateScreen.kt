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

package com.frerox.toolz.ui.screens.settings

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.update.PendingUpdate
import com.frerox.toolz.data.update.UpdateManagerState
import com.frerox.toolz.data.update.formatBytes
import com.frerox.toolz.data.update.formatEta
import com.frerox.toolz.data.update.formatSpeed
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzLoadingIndicator
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.expressivePressScale
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val managerState by viewModel.managerState.collectAsState()
    val preferredAbi by viewModel.preferredAbi.collectAsState()
    val autoUpdate by viewModel.autoUpdateEnabled.collectAsState()
    UpdateScreenContent(
        onBack = onBack,
        currentVersionName = currentVersionName,
        currentVersionCode = currentVersionCode,
        managerState = managerState,
        preferredAbi = preferredAbi,
        autoUpdateEnabled = autoUpdate,
        onCheck = { viewModel.checkForUpdates() },
        onDownload = { viewModel.startDownload(it) },
        onCancelDownload = { viewModel.cancelDownload() },
        onInstall = { viewModel.installReady() },
        onSkip = { viewModel.skipVersion(it) },
        onSetPreferredAbi = { viewModel.setPreferredAbi(it) },
        onSetAutoUpdate = { viewModel.setAutoUpdateEnabled(it) },
        getDeviceAbi = { viewModel.getDeviceAbi() },
        needsInstallPermission = { viewModel.needsInstallPermission() },
        onOpenInstallSettings = { ctx -> viewModel.openInstallSettings(ctx) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreenContent(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long,
    managerState: UpdateManagerState,
    preferredAbi: String,
    autoUpdateEnabled: Boolean,
    onCheck: () -> Unit,
    onDownload: (PendingUpdate?) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onSkip: (String) -> Unit,
    onSetPreferredAbi: (String) -> Unit,
    onSetAutoUpdate: (Boolean) -> Unit,
    getDeviceAbi: () -> String,
    needsInstallPermission: () -> Boolean,
    onOpenInstallSettings: (android.content.Context) -> Unit
) {
    val context = LocalContext.current
    var showAbiSettings by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Updater", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    ToolzExpressiveIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = { showHelpSheet = true },
                        modifier = Modifier.padding(end = 4.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) { Icon(Icons.Rounded.ErrorOutline, contentDescription = "Help") }
                    ToolzExpressiveIconButton(
                        onClick = { showAbiSettings = true },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) { Icon(Icons.Rounded.Settings, contentDescription = "ABI Settings") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UpdateHeroCard(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                managerState = managerState
            )

            AnimatedContent(
                targetState = managerState,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)))
                        .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 1.04f))
                },
                label = "update_state",
                modifier = Modifier.fillMaxWidth()
            ) { state ->
                when (state) {
                    is UpdateManagerState.Idle -> IdleCard(onCheck, autoUpdateEnabled, onSetAutoUpdate)
                    is UpdateManagerState.Checking -> CheckingCard()
                    is UpdateManagerState.Available -> AvailableCard(
                        update = state.update,
                        onDownload = { onDownload(state.update) },
                        onSkip = { onSkip(state.update.version) }
                    )
                    is UpdateManagerState.Downloading -> DownloadingCard(
                        update = state.update,
                        bytesDownloaded = state.bytesDownloaded,
                        totalBytes = state.totalBytes,
                        speedBps = state.speedBps,
                        progress = state.progress,
                        onCancel = onCancelDownload
                    )
                    is UpdateManagerState.ReadyToInstall -> ReadyCard(
                        update = state.update,
                        apkSize = try { state.apkFile.length() } catch (_: Exception) { 0L },
                        needsPermission = needsInstallPermission(),
                        onInstall = onInstall,
                        onOpenSettings = { onOpenInstallSettings(context) }
                    )
                    is UpdateManagerState.Installing -> InstallingCard(update = state.update)
                    is UpdateManagerState.Error -> ErrorCard(message = state.message, onRetry = onCheck)
                    is UpdateManagerState.UpToDate -> UpToDateCard(currentVersionName, onCheck)
                    is UpdateManagerState.Installed -> InstalledCard(onCheck)
                }
            }

            // Secondary: device ABI row (always visible, compact).
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Device build", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (preferredAbi == "AUTO") "Auto • ${getDeviceAbi()}" else preferredAbi,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(onClick = { showAbiSettings = true }) { Text("CHANGE") }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }

    if (showHelpSheet) {
        UpdateHelpBottomSheet(
            onDismiss = { showHelpSheet = false },
            onNavigateToUrl = { url ->
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
            }
        )
    }

    if (showAbiSettings) {
        AlertDialog(
            onDismissRequest = { showAbiSettings = false },
            title = { Text("Preferred build", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Pick the APK variant. Auto follows your device CPU.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    listOf("AUTO", "arm64-v8a", "armeabi-v7a", "x86_64", "x86").forEach { abi ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onSetPreferredAbi(abi); showAbiSettings = false }.padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = preferredAbi == abi, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (abi == "AUTO") "Auto (${getDeviceAbi()})" else abi, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAbiSettings = false }) { Text("CLOSE") } }
        )
    }
}

@Composable
private fun UpdateHeroCard(
    currentVersionName: String,
    currentVersionCode: Long,
    managerState: UpdateManagerState
) {
    val pendingVersion = when (managerState) {
        is UpdateManagerState.Available -> managerState.update.version
        is UpdateManagerState.Downloading -> managerState.update.version
        is UpdateManagerState.ReadyToInstall -> managerState.update.version
        is UpdateManagerState.Installing -> managerState.update.version
        else -> null
    }
    val isCritical = (managerState as? UpdateManagerState.Available)?.update?.isCritical == true
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                modifier = Modifier.size(72.dp).expressivePressScale(remember { MutableInteractionSource() }),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SystemUpdate, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Toolz v$currentVersionName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (isCritical) {
                        Surface(color = MaterialTheme.colorScheme.error, shape = MaterialTheme.shapes.small) {
                            Text("CRITICAL", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
                Text("BUILD $currentVersionCode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                if (pendingVersion != null) {
                    Text("→ v$pendingVersion available", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text("You're on the update channel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun IdleCard(onCheck: () -> Unit, autoUpdateEnabled: Boolean, onSetAutoUpdate: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Check for updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("We compare your build against GitHub releases and the signed manifest, then pick the APK for your CPU.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolzExpressiveButton(onClick = onCheck, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null); Spacer(Modifier.width(10.dp)); Text("CHECK FOR UPDATES", fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-update", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("Download + open installer automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoUpdateEnabled, onCheckedChange = onSetAutoUpdate)
            }
        }
    }
}

@Composable
private fun CheckingCard() {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ToolzLoadingIndicator(modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Checking for updates…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("GitHub releases → signed manifest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun AvailableCard(update: PendingUpdate, onDownload: () -> Unit, onSkip: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, border = if (update.isCritical) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = if (update.isCritical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text(if (update.isCritical) "CRITICAL UPDATE" else "NEW UPDATE", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = if (update.isCritical) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (update.sizeBytes != null && update.sizeBytes > 0) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Text(formatBytes(update.sizeBytes), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Text("v${update.version}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("${update.abi} • ${update.source} • code ${update.versionCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))) {
                Column(Modifier.padding(14.dp).heightIn(max = if (expanded) 320.dp else 140.dp).verticalScroll(rememberScrollState())) {
                    MarkdownContent(markdown = update.changelog.ifBlank { "Bug fixes and performance improvements." }, baseFontSize = 14.sp, textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (expanded) "SHOW LESS" else "READ FULL CHANGELOG")
            }
            ToolzExpressiveButton(onClick = onDownload, modifier = Modifier.fillMaxWidth().height(62.dp)) {
                Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(10.dp)); Text("DOWNLOAD & AUTO-INSTALL", fontWeight = FontWeight.Black)
            }
            Text("Downloads in-app with progress, verifies the file, then opens the system installer — no browser detour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (!update.isCritical) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("SKIP THIS VERSION") }
                }
            } else {
                Text("Critical releases can't be skipped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DownloadingCard(update: PendingUpdate, bytesDownloaded: Long, totalBytes: Long, speedBps: Long, progress: Float, onCancel: () -> Unit) {
    val animated by animateFloatAsState(targetValue = progress / 100f, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "dl")
    val remaining = if (totalBytes > 0) (totalBytes - bytesDownloaded).coerceAtLeast(0) else -1
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DOWNLOADING v${update.version}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            ToolzWavyLinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(16.dp))
            Text("${progress.toInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                if (totalBytes > 0) "${formatBytes(bytesDownloaded)} of ${formatBytes(totalBytes)}" else formatBytes(bytesDownloaded),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (speedBps > 0) "${formatSpeed(speedBps)} • ETA ${formatEta(remaining, speedBps)}" else "Connecting…",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCancel) { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("CANCEL") }
        }
    }
}

@Composable
private fun ReadyCard(update: PendingUpdate, apkSize: Long, needsPermission: Boolean, onInstall: () -> Unit, onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
            Text("Ready to install", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("v${update.version} downloaded${if (apkSize > 0) " (${formatBytes(apkSize)})" else ""} and verified. One tap opens the system installer.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (needsPermission) {
                Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Permission needed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("Allow \"Install unknown apps\" for Toolz, then come back and tap Install.", style = MaterialTheme.typography.bodySmall)
                        ToolzExpressiveButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("OPEN INSTALL SETTINGS", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            ToolzExpressiveButton(onClick = onInstall, modifier = Modifier.fillMaxWidth().height(62.dp)) {
                Icon(Icons.Rounded.InstallMobile, null); Spacer(Modifier.width(10.dp)); Text("INSTALL NOW", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun InstallingCard(update: PendingUpdate) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ToolzLoadingIndicator(modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("Installing v${update.version}…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Confirm on the system installer screen. Toolz restarts when done.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            ToolzExpressiveButton(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("RETRY", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun UpToDateCard(currentVersionName: String, onCheck: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(52.dp))
            Text("Up to date", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Toolz v$currentVersionName is the latest. We'll check again in the background.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            TextButton(onClick = onCheck) { Text("CHECK AGAIN") }
        }
    }
}

@Composable
private fun InstalledCard(onCheck: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text("Update installed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Enjoy the new release.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateHelpBottomSheet(onDismiss: () -> Unit, onNavigateToUrl: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("How updates work", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Checks run every 24h + on launch. Downloads stream in-app with resume and verification, then the system installer finishes the job. Critical updates can't be skipped.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HelpSection("Verified builds", "GitHub release first, signed manifest as fallback. Tampered manifests are rejected.", Icons.Rounded.PriorityHigh)
            HelpSection("Right APK, first try", "Auto-detects your CPU (arm64, arm32, x86) or use your override in settings.", Icons.Rounded.Settings)
            HelpSection("Background friendly", "Auto-update downloads quietly and nudges you only when the installer is ready.", Icons.Rounded.Download)
            ToolzExpressiveButton(onClick = { onNavigateToUrl("https://github.com/freroxx/toolz/releases") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(10.dp)); Text("VIEW ALL RELEASES", fontWeight = FontWeight.Black)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("CLOSE", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun HelpSection(title: String, description: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(Modifier.size(44.dp), RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UpdateScreenPreview() {
    com.frerox.toolz.ui.theme.ToolzTheme {
        UpdateScreenContent(
            onBack = {}, currentVersionName = "1.1.7", currentVersionCode = 18,
            managerState = UpdateManagerState.Available(
                PendingUpdate("1.1.8", 19, "### Highlights\n- Faster downloads\n- New installer", "https://example.com/a.apk", 42_000_000L, null, false, "arm64-v8a", "manifest")
            ),
            preferredAbi = "AUTO", autoUpdateEnabled = true,
            onCheck = {}, onDownload = {}, onCancelDownload = {}, onInstall = {}, onSkip = {},
            onSetPreferredAbi = {}, onSetAutoUpdate = {}, getDeviceAbi = { "arm64-v8a" },
            needsInstallPermission = { false }, onOpenInstallSettings = {}
        )
    }
}

private fun isNewerVersion(current: String, latest: String): Boolean {
    val c = current.split(".").mapNotNull { it.toIntOrNull() }
    val l = latest.split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until minOf(c.size, l.size)) {
        if (l[i] > c[i]) return true
        if (l[i] < c[i]) return false
    }
    return l.size > c.size
}
