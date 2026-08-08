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

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.update.UpdateHelper
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val preferredAbi by viewModel.preferredAbi.collectAsState()
    
    UpdateScreenContent(
        onBack = onBack,
        currentVersionName = currentVersionName,
        currentVersionCode = currentVersionCode,
        uiState = uiState,
        preferredAbi = preferredAbi,
        onCheckForUpdates = { viewModel.checkForUpdates() },
        onResetState = { viewModel.resetState() },
        onSetPreferredAbi = { viewModel.setPreferredAbi(it) },
        getDeviceAbi = { viewModel.getDeviceAbi() },
        onStartDownload = { viewModel.startDownload(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreenContent(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long,
    uiState: UpdateUiState,
    preferredAbi: String,
    onCheckForUpdates: () -> Unit,
    onResetState: () -> Unit,
    onSetPreferredAbi: (String) -> Unit,
    getDeviceAbi: () -> String,
    onStartDownload: (String) -> Unit
) {
    val context = LocalContext.current
    var showAbiSettings by remember { mutableStateOf(false) }
    var showHelpSheet by remember { mutableStateOf(false) }

    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = uiState is UpdateUiState.Checking

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Updater",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    ToolzExpressiveIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = { showHelpSheet = true },
                        modifier = Modifier.padding(end = 4.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = "Help")
                    }
                    ToolzExpressiveIconButton(
                        onClick = { showAbiSettings = true },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "ABI Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            ExpressiveRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Central Icon with Expressive Styling
                Surface(
                    modifier = Modifier
                        .size(160.dp)
                        .expressivePressScale(remember { MutableInteractionSource() }),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Toolz v$currentVersionName",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "BUILD $currentVersionCode",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                         scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)))
                            .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 1.1f))
                    },
                    label = "update_state",
                    modifier = Modifier.fillMaxWidth()
                ) { state ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when (state) {
                            is UpdateUiState.Downloading -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "DOWNLOADING PATCH",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                    
                                    Spacer(Modifier.height(16.dp))
                                    
                                    val animatedProgress by animateFloatAsState(
                                        targetValue = state.progress / 100f,
                                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                                        label = "progress"
                                    )

                                    ToolzWavyLinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .height(16.dp)
                                    )
                                    
                                    Spacer(Modifier.height(12.dp))
                                    
                                    Text(
                                        "${state.progress.toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            is UpdateUiState.Checking -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ToolzLoadingIndicator(modifier = Modifier.size(64.dp))
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        "Checking for updates...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is UpdateUiState.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Rounded.ErrorOutline, 
                                        null, 
                                        tint = MaterialTheme.colorScheme.error, 
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        state.message, 
                                        color = MaterialTheme.colorScheme.error, 
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    ToolzExpressiveButton(
                                        onClick = onCheckForUpdates,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                    ) {
                                        Text("RETRY CONNECTION", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                            else -> {
                                ToolzExpressiveButton(
                                    onClick = onCheckForUpdates,
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Icon(Icons.Rounded.SystemUpdate, null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("CHECK FOR UPDATES", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showHelpSheet) {
        UpdateHelpBottomSheet(
            onDismiss = { showHelpSheet = false },
            onNavigateToUrl = { url ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        )
    }

    if (showAbiSettings) {
        AlertDialog(
            onDismissRequest = { showAbiSettings = false },
            title = { Text("Select Preferred ABI", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    listOf("AUTO", "armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onSetPreferredAbi(abi)
                                    showAbiSettings = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = preferredAbi == abi,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (abi == "AUTO") "Auto-detect (${getDeviceAbi()})" else abi,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbiSettings = false }) {
                    Text("CLOSE")
                }
            }
        )
    }

    if (uiState is UpdateUiState.Success || uiState is UpdateUiState.ManifestSuccess) {
        val (latestVersion, changelog, apkUrl) = when (val state = uiState) {
            is UpdateUiState.Success -> {
                val bestAsset = UpdateHelper.getBestAsset(state.release.assets, preferredAbi)
                Triple(
                    state.release.tagName.removePrefix("v"), 
                    state.release.body ?: "No changelog provided.", 
                    bestAsset?.downloadUrl ?: ""
                )
            }
            is UpdateUiState.ManifestSuccess -> {
                val bestRelease = state.manifest.releases?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
                Triple(
                    state.manifest.versionName, 
                    state.manifest.changelog ?: "No changelog provided.", 
                    bestRelease?.downloadUrl ?: ""
                )
            }
            else -> Triple("", "", "")
        }
        
        val isNewer = isNewerVersion(currentVersionName, latestVersion)

        ModalBottomSheet(
            onDismissRequest = onResetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Surface(
                    color = if (isNewer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = if (isNewer) "NEW UPDATE AVAILABLE" else "SYSTEM UP TO DATE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = if (isNewer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                Spacer(Modifier.height(16.dp))

                Text(
                    "v$latestVersion",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                
                if (isNewer) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "CHANGELOG", 
                        style = MaterialTheme.typography.labelLarge, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Surface(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            MarkdownContent(
                                markdown = changelog,
                                baseFontSize = 15.sp,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    ToolzExpressiveButton(
                        onClick = {
                            if (apkUrl.isNotEmpty()) {
                                onStartDownload(apkUrl)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        enabled = apkUrl.isNotEmpty(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("DOWNLOAD & INSTALL", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    
                    if (apkUrl.isEmpty()) {
                        val targetAbi = if (preferredAbi == "AUTO") getDeviceAbi() else preferredAbi
                        Text(
                            "No compatible APK found for $targetAbi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "You're currently using the most advanced version of Toolz. No updates are required at this time.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(32.dp))
                    ToolzExpressiveButton(
                        onClick = onResetState,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("DISMISS", fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateHelpBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToUrl: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Update System",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "Toolz features a robust update mechanism designed to keep your utility suite at peak performance.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HelpSection(
                title = "Automated Checks",
                description = "We synchronize with our GitHub repository and manifest to identify new builds specifically for your device architecture.",
                icon = Icons.Rounded.SystemUpdate
            )

            HelpSection(
                title = "Manual Override",
                description = "In case of network synchronization issues, you can access the releases page directly to download APKs.",
                icon = Icons.Rounded.Download
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolzExpressiveButton(
                    onClick = { onNavigateToUrl("https://github.com/freroxx/toolz/releases") },
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(12.dp))
                    Text("VIEW ALL RELEASES", fontWeight = FontWeight.Black)
                }
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HelpSection(title: String, description: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
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
            onBack = {},
            currentVersionName = "1.1.1",
            currentVersionCode = 12,
            uiState = UpdateUiState.Idle,
            preferredAbi = "AUTO",
            onCheckForUpdates = {},
            onResetState = {},
            onSetPreferredAbi = {},
            getDeviceAbi = { "arm64-v8a" },
            onStartDownload = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun UpdateHelpPreview() {
    com.frerox.toolz.ui.theme.ToolzTheme {
        UpdateHelpBottomSheet(
            onDismiss = {},
            onNavigateToUrl = {}
        )
    }
}

private fun isNewerVersion(current: String, latest: String): Boolean {
    val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
    val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
    
    for (i in 0 until minOf(currentParts.size, latestParts.size)) {
        if (latestParts[i] > currentParts[i]) return true
        if (latestParts[i] < currentParts[i]) return false
    }
    return latestParts.size > currentParts.size
}
