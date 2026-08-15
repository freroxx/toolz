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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.frerox.toolz.ui.screens.media

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.ConversionEngine
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  FileConverterScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FileConverterScreen(
    viewModel: FileConverterViewModel,
    onBack: () -> Unit,
    initialUri: android.net.Uri? = null,
    initialTitle: String? = null,
    initialUris: String? = null,
) {
    val uiState         by viewModel.uiState.collectAsState()
    val context          = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val hapticEnabled    = LocalHapticEnabled.current
    val performanceMode  = LocalPerformanceMode.current

    var highQuality         by remember { mutableStateOf(true) }
    var showFormatSheet     by remember { mutableStateOf(false) }
    var showAllFormatsSheet by remember { mutableStateOf(false) }
    var showInfoSheet       by remember { mutableStateOf(false) }

    LaunchedEffect(initialUri, initialUris) {
        if (initialUri != null && initialUri.toString() != "{uri}") {
            viewModel.onFilesSelected(listOf(initialUri))
            showFormatSheet = true
        } else if (!initialUris.isNullOrEmpty() && initialUris != "{initialUris}") {
            try {
                val uris = initialUris.split(",").map { android.net.Uri.parse(it) }
                viewModel.onFilesSelected(uris)
                if (uris.size > 1) {
                    viewModel.switchMode(ConversionMode.BATCH)
                }
                // Always show format sheet for auto-opened files
                showFormatSheet = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // BroadcastReceiver
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                val qPos   = intent?.getIntExtra("queue_pos", 1) ?: 1
                val qTotal = intent?.getIntExtra("queue_total", 1) ?: 1
                when (intent?.action) {
                    "COM_FREROX_TOOLZ_CONVERSION_PROGRESS" -> {
                        viewModel.onConversionProgress(intent.getIntExtra("progress", 0), qPos, qTotal)
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_SUCCESS" -> {
                        val path = intent.getStringExtra("output_path") ?: ""
                        viewModel.onConversionSuccess(path, qPos, qTotal)
                        if (hapticEnabled) vibrationManager?.vibrateSuccess()
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_ERROR" -> {
                        viewModel.onConversionError(
                            intent.getStringExtra("error_message") ?: "Unknown error",
                            qPos, qTotal,
                        )
                        vibrationManager?.vibrateError()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("COM_FREROX_TOOLZ_CONVERSION_PROGRESS")
            addAction("COM_FREROX_TOOLZ_CONVERSION_SUCCESS")
            addAction("COM_FREROX_TOOLZ_CONVERSION_ERROR")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Single-file launcher
    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFilesSelected(listOf(uri))
            showFormatSheet = true
        }
    }

    // Multi-file launcher
    val multiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(uris)
        }
    }

    // Bottom sheets
    if (showFormatSheet && uiState.selectedFiles.isNotEmpty()) {
        ConversionTypeSheet(
            uri       = uiState.selectedFiles.first().uri,
            fileCount = uiState.selectedFiles.size,
            mimeType  = uiState.selectedFiles.first().mimeType,
            onDismiss = { showFormatSheet = false; viewModel.clearFormatSelection() },
            onTypeSelected = { type ->
                viewModel.startConversion(
                    uris       = uiState.selectedFiles.map { it.uri },
                    type       = type,
                    highQuality = highQuality,
                )
                showFormatSheet = false
            },
        )
    }
    if (showAllFormatsSheet) {
        AllFormatsSheet(onDismiss = { showAllFormatsSheet = false })
    }
    if (showInfoSheet) {
        EngineInfoSheet(onDismiss = { showInfoSheet = false })
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_FileConverterScreen_fc1),
                subtitle = stringResource(R.string.st_FileConverterScreen_cf2),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_FileConverterScreen_b3))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            highQuality = !highQuality
                            vibrationManager?.vibrateTick()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (highQuality) Icons.Rounded.HighQuality else Icons.Rounded.Speed,
                            contentDescription = stringResource(R.string.st_FileConverterScreen_q4),
                            tint = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .then(
                        if (!performanceMode) Modifier.fadingEdges(top = 24.dp, bottom = 24.dp)
                        else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))

                // ── Mode Switcher ─────────────────────────────────────────────
                AnimatedVisibility(
                    visible = !uiState.isConverting && !uiState.conversionSuccess,
                    enter = fadeIn(tween(300)) + expandVertically(),
                    exit = fadeOut(tween(200)) + shrinkVertically(),
                ) {
                    ModeSwitcher(
                        mode = uiState.conversionMode,
                        onSwitch = { mode ->
                            vibrationManager?.vibrateTick()
                            viewModel.switchMode(mode)
                        },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Main animated state ───────────────────────────────────────
                AnimatedContent(
                    targetState = Triple(
                        uiState.conversionSuccess,
                        uiState.isConverting,
                        uiState.conversionMode,
                    ),
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                                scaleIn(initialScale = 0.96f, animationSpec = spring(stiffness = Spring.StiffnessMedium)))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 1.02f, animationSpec = tween(200)))
                    },
                    label = "mainState",
                ) { (success, converting, mode) ->
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        when {
                            success    -> SuccessView(
                                outputFiles     = uiState.outputFiles,
                                selectedFiles   = uiState.selectedFiles,
                                conversionType  = uiState.conversionType,
                                highQuality     = highQuality,
                                performanceMode = performanceMode,
                                filesErrored    = uiState.filesErrored,
                                lastErrorMessage = uiState.lastErrorMessage,
                                onReset         = { viewModel.reset() },
                            )
                            converting -> ConvertingView(
                                progress        = uiState.progress,
                                queuePos        = uiState.queuePos,
                                queueTotal      = uiState.queueTotal,
                                conversionType  = uiState.conversionType,
                                performanceMode = performanceMode,
                                onCancel        = {
                                    viewModel.cancelConversion()
                                    vibrationManager?.vibrateTick()
                                },
                            )
                            mode == ConversionMode.BATCH -> BatchIdleView(
                                stagedFiles     = uiState.batchStagedFiles,
                                performanceMode = performanceMode,
                                highQuality     = highQuality,
                                onToggleQuality = { highQuality = it; vibrationManager?.vibrateTick() },
                                onAddFiles      = { multiLauncher.launch("*/*") },
                                onRemoveFile    = { viewModel.removeFromBatch(it) },
                                onShowAllFormats = { showAllFormatsSheet = true },
                                onConvert       = {
                                    viewModel.prepareBatchForConversion()
                                    showFormatSheet = true
                                },
                            )
                            else -> SingleIdleView(
                                performanceMode  = performanceMode,
                                highQuality      = highQuality,
                                onToggleQuality  = { highQuality = it; vibrationManager?.vibrateTick() },
                                onSelectFile     = { singleLauncher.launch("*/*") },
                                onShowAllFormats = { showAllFormatsSheet = true },
                                onShowInfo       = { showInfoSheet = true },
                            )
                        }
                    }
                }

                // ── Error Dialog ──────────────────────────────────────────────
                if (uiState.error != null) {
                    ErrorDialog(
                        message = uiState.error!!,
                        onDismiss = { viewModel.dismissError() }
                    )
                }

                // ── Recent conversions ────────────────────────────────────────
                AnimatedVisibility(
                    visible = uiState.recentConversions.isNotEmpty() && !uiState.isConverting && !uiState.conversionSuccess,
                    enter   = fadeIn(tween(600)) + slideInVertically { it / 2 },
                    exit    = fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Spacer(Modifier.height(36.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.History, null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                )
                                Text(
                                    text       = stringResource(R.string.st_FileConverterScreen_ch5),
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Surface(
                                onClick = { vibrationManager?.vibrateTick(); viewModel.clearHistory() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            ) {
                                Text(
                                    "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                        uiState.recentConversions.reversed().forEachIndexed { i, recent ->
                            StaggeredEntrance(index = i) {
                                RecentConversionItem(
                                    recent  = recent,
                                    context = LocalContext.current,
                                    onDismiss = { viewModel.removeHistoryItem(recent) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(64.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mode Switcher
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ModeSwitcher(
    mode: ConversionMode,
    onSwitch: (ConversionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface.copy(alpha = 0.7f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ConversionMode.entries.forEach { m ->
            val selected = mode == m
            val bgAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "modeAlpha",
            )
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.97f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "modeScale",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) primary.copy(alpha = bgAlpha)
                        else Color.Transparent
                    )
                    .clickable { onSwitch(m) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (m == ConversionMode.SINGLE) Icons.Rounded.FilePresent
                                      else Icons.Rounded.FolderCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (m == ConversionMode.SINGLE) "Single File" else "Batch",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single File Idle View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SingleIdleView(
    performanceMode: Boolean,
    highQuality: Boolean,
    onToggleQuality: (Boolean) -> Unit,
    onSelectFile: () -> Unit,
    onShowAllFormats: () -> Unit,
    onShowInfo: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HeroDropZone(
            performanceMode = performanceMode,
            subtitle = "Tap to select a file",
            onSelectFile = {
                vibrationManager?.vibrateClick()
                onSelectFile()
            },
        )

        StaggeredEntrance(index = 1) {
            QualityConfigCard(
                highQuality = highQuality,
                onToggle    = onToggleQuality,
            )
        }

        StaggeredEntrance(index = 2) {
            QuickFormatsGrid(onShowAllFormats = onShowAllFormats)
        }

        StaggeredEntrance(index = 3) {
            ToolzOutlinedExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onShowInfo()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MediumExpressiveShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.Info, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_FileConverterScreen_lae6), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Batch Idle View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BatchIdleView(
    stagedFiles: List<FileInfo>,
    performanceMode: Boolean,
    highQuality: Boolean,
    onToggleQuality: (Boolean) -> Unit,
    onAddFiles: () -> Unit,
    onRemoveFile: (FileInfo) -> Unit,
    onShowAllFormats: () -> Unit,
    onConvert: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val primary = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (stagedFiles.isEmpty()) {
            HeroDropZone(
                performanceMode = performanceMode,
                subtitle = "Tap to select multiple files",
                icon = Icons.Rounded.FolderCopy,
                onSelectFile = {
                    vibrationManager?.vibrateClick()
                    onAddFiles()
                },
            )
        } else {
            // Staged files header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "${stagedFiles.size} file${if (stagedFiles.size > 1) "s" else ""} staged",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Ready to convert",
                        style = MaterialTheme.typography.labelMedium,
                        color = primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    onClick = { vibrationManager?.vibrateClick(); onAddFiles() },
                    shape = CircleShape,
                    color = primary.copy(alpha = 0.1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp), tint = primary)
                        Text("Add More", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = primary)
                    }
                }
            }

            // Staged files list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stagedFiles.forEachIndexed { i, file ->
                    StaggeredEntrance(index = i) {
                        StagedFileItem(file = file, onRemove = { onRemoveFile(file) })
                    }
                }
            }

            // Quality toggle
            QualityConfigCard(highQuality = highQuality, onToggle = onToggleQuality)

            // Convert All button
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onConvert()
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = LargeExpressiveShape,
                colors = ButtonDefaults.buttonColors(containerColor = primary),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Convert ${stagedFiles.size} File${if (stagedFiles.size > 1) "s" else ""}",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        StaggeredEntrance(index = if (stagedFiles.isEmpty()) 2 else stagedFiles.size + 2) {
            QuickFormatsGrid(onShowAllFormats = onShowAllFormats)
        }
    }
}

@Composable
private fun StagedFileItem(
    file: FileInfo,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val ext = file.name.substringAfterLast(".", "").lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heif", "heic", "avif")
    val color = mimeToColor(file.mimeType)

    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Thumbnail or icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(SmallExpressiveShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isImage) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(file.uri).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = ext.uppercase().take(4),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = color,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatFileSize(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Rounded.Close, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Hero Drop Zone
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroDropZone(
    performanceMode: Boolean,
    subtitle: String,
    icon: ImageVector = Icons.Rounded.FileUpload,
    onSelectFile: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "hero")

    val pulseScale by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.018f,
            animationSpec = infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "pulse",
        )
    } else remember { mutableFloatStateOf(1f) }

    val iconTranslation by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = -10f,
            animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "icon",
        )
    } else remember { mutableFloatStateOf(0f) }

    // Animated ring rotation
    val ringRotation by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart),
            label = "ring",
        )
    } else remember { mutableFloatStateOf(0f) }

    ExpressiveCard(
        onClick = onSelectFile,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale },
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 3.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Dashed border hint
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .clip(LargeExpressiveShape)
                    .border(
                        width = 2.dp,
                        color = primary.copy(alpha = 0.2f),
                        shape = LargeExpressiveShape,
                    ),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            translationY = iconTranslation
                            rotationZ = if (!performanceMode) ringRotation * 0.05f else 0f
                        }
                        .background(primary.copy(alpha = 0.09f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = primary,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.st_FileConverterScreen_tsf7),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun QualityConfigCard(
    highQuality: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val color = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    ExpressiveCard(
        onClick = { onToggle(!highQuality) },
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        containerColor = color.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f)),
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (highQuality) Icons.Rounded.HighQuality else Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (highQuality) stringResource(R.string.st_FileConverterScreen_eq9)
                    else stringResource(R.string.st_FileConverterScreen_pm10),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (highQuality) stringResource(R.string.st_FileConverterScreen_bva11)
                    else stringResource(R.string.st_FileConverterScreen_fc12),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            ExpressiveSwitch(checked = highQuality, onCheckedChange = onToggle)
        }
    }
}

private data class FormatCategory(
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
    val color: Color,
)

@Composable
private fun QuickFormatsGrid(onShowAllFormats: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val categories = remember {
        listOf(
            FormatCategory(Icons.Rounded.Movie,       "Video",  "MP4, MKV, MOV",  Color(0xFFD32F2F)),
            FormatCategory(Icons.Rounded.MusicNote,   "Audio",  "MP3, WAV, AAC",  Color(0xFF1976D2)),
            FormatCategory(Icons.Rounded.Image,       "Image",  "PNG, JPG, WEBP", Color(0xFF388E3C)),
            FormatCategory(Icons.Rounded.Description, "Docs",   "PDF, TXT, MD",   Color(0xFFFBC02D)),
            FormatCategory(Icons.Rounded.Animation,   "Motion", "GIF, WebP",      Color(0xFFC2185B)),
            FormatCategory(Icons.Rounded.Code,        "Vector", "SVG → any",      Color(0xFF7B1FA2)),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.st_FileConverterScreen_sat25),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Surface(
                onClick = { vibrationManager?.vibrateTick(); onShowAllFormats() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            ) {
                Text(
                    stringResource(R.string.st_FileConverterScreen_va26),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        categories.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { cat ->
                    ExpressiveCard(
                        onClick = { vibrationManager?.vibrateTick(); onShowAllFormats() },
                        modifier = Modifier.weight(1f),
                        shape = LargeExpressiveShape,
                        containerColor = cat.color.copy(alpha = 0.07f),
                        border = BorderStroke(1.dp, cat.color.copy(alpha = 0.14f)),
                        elevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(cat.color.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(cat.icon, null, modifier = Modifier.size(20.dp), tint = cat.color)
                            }
                            Column {
                                Text(cat.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                                Text(cat.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Converting View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConvertingView(
    progress: Int,
    queuePos: Int,
    queueTotal: Int,
    conversionType: ConversionEngine.ConversionType?,
    performanceMode: Boolean,
    onCancel: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val vibrationManager = LocalVibrationManager.current

    val infiniteTransition = rememberInfiniteTransition(label = "converting")
    val glowAlpha by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f, targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        )
    } else remember { mutableFloatStateOf(0.3f) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Progress indicator
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            // Glow ring behind
            if (!performanceMode) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(
                            Brush.radialGradient(listOf(primary.copy(alpha = glowAlpha * 0.3f), Color.Transparent)),
                            CircleShape,
                        )
                )
            }
            ExpressiveCircularProgressIndicator(
                progress = if (progress > 0) ({ progress / 100f }) else ({ 0f }),
                modifier = Modifier.fillMaxSize(),
                color = primary,
                trackColor = primary.copy(alpha = 0.1f),
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (progress > 0) {
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = primary,
                        letterSpacing = (-1).sp,
                    )
                } else {
                    ExpressiveLoadingWheel(modifier = Modifier.size(64.dp), color = primary)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(R.string.st_FileConverterScreen_ca27),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )

        Spacer(Modifier.height(8.dp))

        // Queue progress (batch mode)
        if (queueTotal > 1) {
            // Individual file progress bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "File $queuePos of $queueTotal",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { queuePos.toFloat() / queueTotal },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = primary,
                    trackColor = primary.copy(alpha = 0.1f),
                )
            }
        }

        conversionType?.let {
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                shape = CircleShape,
                color = primary.copy(alpha = 0.08f),
            ) {
                Text(
                    text = "→ .${it.extension.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        Surface(
            onClick = {
                vibrationManager?.vibrateTick()
                onCancel()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.st_FileConverterScreen_c28),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Success View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessView(
    outputFiles: List<String>,
    selectedFiles: List<FileInfo>,
    conversionType: ConversionEngine.ConversionType?,
    highQuality: Boolean,
    performanceMode: Boolean,
    filesErrored: Int,
    lastErrorMessage: String?,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val sf30 = stringResource(R.string.st_FileConverterScreen_sf30)
    val successColor = MaterialTheme.colorScheme.primary
    val partialSuccess = filesErrored > 0

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Bouncy icon
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = successColor.copy(alpha = 0.1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (partialSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Check,
                        null,
                        Modifier.size(44.dp),
                        tint = if (partialSuccess) MaterialTheme.colorScheme.tertiary else successColor,
                    )
                }
            }
        }

        // Title
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (partialSuccess) "Partially Done" else stringResource(R.string.st_FileConverterScreen_s29),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (partialSuccess)
                    "${outputFiles.size} converted · $filesErrored failed"
                else
                    "${outputFiles.size} file${if (outputFiles.size > 1) "s" else ""} " + stringResource(R.string.st_FileConverterScreen_rtu55),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }

        // Error notice for partial
        if (partialSuccess && lastErrorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MediumExpressiveShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text(
                        "$filesErrored file(s) failed: $lastErrorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Summary card
        if (outputFiles.isNotEmpty()) {
            ConversionSummaryCard(
                selectedFiles  = selectedFiles,
                outputPaths    = outputFiles,
                conversionType = conversionType,
                highQuality    = highQuality,
                performanceMode = performanceMode,
            )
        }

        // Actions
        if (outputFiles.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ToolzOutlinedExpressiveButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        try {
                            if (outputFiles.size == 1) {
                                val file = File(outputFiles.first())
                                val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = context.contentResolver.getType(uri) ?: "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, sf30))
                            } else {
                                // Share all
                                val uris = ArrayList(outputFiles.map { path ->
                                    FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", File(path))
                                })
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, sf30))
                            }
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = LargeExpressiveShape,
                ) {
                    Icon(Icons.Rounded.IosShare, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (outputFiles.size > 1) "Share All" else stringResource(R.string.st_FileConverterScreen_sh31),
                        fontWeight = FontWeight.Black,
                    )
                }

                ToolzExpressiveButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        onReset()
                    },
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = LargeExpressiveShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(R.string.st_FileConverterScreen_d32), fontWeight = FontWeight.Black)
                }
            }
        } else {
            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onReset()
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = LargeExpressiveShape,
            ) {
                Text("Try Again", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ConversionSummaryCard(
    selectedFiles: List<FileInfo>,
    outputPaths: List<String>,
    conversionType: ConversionEngine.ConversionType?,
    highQuality: Boolean,
    performanceMode: Boolean,
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val oa38 = stringResource(R.string.st_FileConverterScreen_oa38)
    val sf30 = stringResource(R.string.st_FileConverterScreen_sf30)

    val totalInputSize  = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    val totalOutputSize = remember(outputPaths) { outputPaths.sumOf { File(it).length() } }
    val sizeDiff = totalOutputSize - totalInputSize
    val sizeDiffPercent = if (totalInputSize > 0) (sizeDiff.toFloat() / totalInputSize * 100).toInt() else 0
    val catColor = categoryColor(conversionType?.category ?: "")

    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Top Bar: Formats & Quality ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category & Flow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(catColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            categoryIcon(conversionType?.category ?: ""),
                            contentDescription = null,
                            tint = catColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val inLabel = if (selectedFiles.size == 1)
                                selectedFiles.first().name.substringAfterLast(".", "?").uppercase()
                            else
                                "${selectedFiles.size} FILES"
                            Text(
                                text = inLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = (conversionType?.extension ?: "???").uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = conversionType?.category ?: "Conversion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // Quality Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (highQuality) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                    border = BorderStroke(
                        1.dp,
                        if (highQuality) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (highQuality) Icons.Rounded.HighQuality else Icons.Rounded.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (highQuality) "HQ" else "Fast",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // ── Metric Comparison Bar ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MediumExpressiveShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Metric 1: Output Size
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.st_FileConverterScreen_ts35).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatFileSize(totalOutputSize),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))

                // Metric 2: Difference
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SIZE DIFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val isSmaller = sizeDiff <= 0
                        Icon(
                            imageVector = if (isSmaller) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isSmaller) Color(0xFF388E3C) else Color(0xFFD32F2F),
                        )
                        Text(
                            text = "${if (sizeDiff > 0) "+" else ""}$sizeDiffPercent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isSmaller) Color(0xFF388E3C) else Color(0xFFD32F2F),
                        )
                    }
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))

                // Metric 3: File Count
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "OUTPUT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${outputPaths.size} ${if (outputPaths.size == 1) "File" else "Files"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── Output Files Showcase with Thumbnails ───────────────────────────
            if (outputPaths.isNotEmpty()) {
                Text(
                    text = if (outputPaths.size > 1) "CONVERTED FILES (${outputPaths.size})" else "OUTPUT FILE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                )

                if (outputPaths.size == 1) {
                    // Single File Hero Card
                    val path = outputPaths.first()
                    val file = remember(path) { File(path) }

                    ExpressiveCard(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            try {
                                val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, oa38))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = LargeExpressiveShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        elevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // Thumbnail
                            ConvertedMediaThumbnail(
                                file = file,
                                category = conversionType?.category ?: "",
                                modifier = Modifier.size(68.dp),
                            )

                            // Title & Size
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = formatFileSize(file.length()),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    Text(
                                        text = "Tap to open",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                            }

                            // Action: Open Icon Button
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Batch Files: Horizontal Scroll with Rich Thumbnail Cards
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!performanceMode) Modifier.horizontalFadingEdges(left = 12.dp, right = 12.dp) else Modifier)
                    ) {
                        items(outputPaths) { path ->
                            val file = remember(path) { File(path) }

                            ExpressiveCard(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, oa38))
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.width(160.dp),
                                shape = LargeExpressiveShape,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                                elevation = 1.dp,
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    // Thumbnail preview
                                    ConvertedMediaThumbnail(
                                        file = file,
                                        category = conversionType?.category ?: "",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp),
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = formatFileSize(file.length()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Saved Location Footer ─────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Saved in Downloads/Toolz/${conversionType?.category ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Renders an expressive, high-polish thumbnail for any converted media type:
 * Images, Videos (with extracted frame & play badge), Audio (with equalizer waveform visual),
 * Documents (with PDF/doc sheets), and Archives.
 */
@Composable
fun ConvertedMediaThumbnail(
    file: File,
    category: String,
    modifier: Modifier = Modifier,
) {
    val ext = file.extension.lowercase()
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heif", "heic", "avif")
    val isVideo = ext in listOf("mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "3gp", "m4v", "ts")
    val isAudio = ext in listOf("mp3", "wav", "aac", "flac", "m4a", "ogg", "opus", "amr", "aiff", "mka")
    val isDoc = ext in listOf("pdf", "txt", "md", "html", "htm")
    val isArchive = ext in listOf("zip", "apk", "xapk")
    val context = LocalContext.current
    val color = categoryColor(category)

    val videoBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = file.path) {
        if (isVideo && file.exists()) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.path)
                    val frame = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                    retriever.release()
                    frame
                } catch (_: Exception) { null }
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(MediumExpressiveShape)
            .background(color.copy(alpha = 0.08f))
    ) {
        when {
            isImage && file.exists() -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(file).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            isVideo && videoBitmap != null -> {
                Image(
                    bitmap = videoBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            isAudio -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.04f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            isDoc -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFBC02D).copy(alpha = 0.2f), Color(0xFFFBC02D).copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ext == "pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                        contentDescription = null,
                        tint = Color(0xFFF57F17),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            isArchive -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.FolderZip,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            else -> {
                Icon(
                    categoryIcon(category),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Extension Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(
                text = ext.uppercase().take(4),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = color,
                fontSize = 8.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Error Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.st_FileConverterScreen_cf39), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.st_FileConverterScreen_err40), style = MaterialTheme.typography.bodyMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vibrationManager?.vibrateTick()
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message))
                android.widget.Toast.makeText(context, context.getString(R.string.st_FileConverterScreen_ctc41), android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.st_FileConverterScreen_cel42), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { vibrationManager?.vibrateTick(); onDismiss() }) {
                Text(stringResource(R.string.st_FileConverterScreen_cl43), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Engine Info Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EngineInfoSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 32.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SettingsInputComponent, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.st_FileConverterScreen_tfc44), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.st_FileConverterScreen_aw45), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            Spacer(Modifier.height(28.dp))
            val performanceMode = LocalPerformanceMode.current
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .then(if (!performanceMode) Modifier.fadingEdges(top = 16.dp, bottom = 20.dp) else Modifier)
            ) {
                item { InfoSection(Icons.Rounded.Code,     stringResource(R.string.st_FileConverterScreen_ca46),  stringResource(R.string.st_FileConverterScreen_ca_desc47)) }
                item { InfoSection(Icons.Rounded.Speed,    stringResource(R.string.st_FileConverterScreen_pq48),  stringResource(R.string.st_FileConverterScreen_pq_desc49)) }
                item { InfoSection(Icons.Rounded.Security, stringResource(R.string.st_FileConverterScreen_pf50),  stringResource(R.string.st_FileConverterScreen_pf_desc51)) }
                item { InfoSection(Icons.Rounded.Layers,   stringResource(R.string.st_FileConverterScreen_bp52),  stringResource(R.string.st_FileConverterScreen_bp_desc53)) }
            }
            Spacer(Modifier.height(20.dp))
            ToolzExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = LargeExpressiveShape,
            ) {
                Text(stringResource(R.string.st_FileConverterScreen_gi54), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun InfoSection(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(
            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), lineHeight = 20.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Conversion Type Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConversionTypeSheet(
    uri: Uri,
    fileCount: Int,
    mimeType: String,
    onDismiss: () -> Unit,
    onTypeSelected: (ConversionEngine.ConversionType) -> Unit,
) {
    val context = LocalContext.current
    val effectiveMime = mimeType.ifBlank { context.contentResolver.getType(uri) ?: "" }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val availableTypes = remember(effectiveMime) {
        ConversionEngine.ConversionType.entries.filter { type ->
            if (effectiveMime.isBlank()) return@filter true
            type.inputMimes.any { effectiveMime.startsWith(it) || effectiveMime == it }
        }
    }

    val categories = remember(availableTypes) {
        listOf("All") + availableTypes.map { it.category }.distinct()
    }

    val filteredTypes = remember(searchQuery, availableTypes, selectedCategory) {
        availableTypes
            .filter { type ->
                val matchesSearch = searchQuery.isBlank() ||
                    type.extension.contains(searchQuery, ignoreCase = true) ||
                    type.label.contains(searchQuery, ignoreCase = true)
                val matchesCat = selectedCategory == "All" || type.category == selectedCategory
                matchesSearch && matchesCat
            }
            .sortedByDescending { it.isPopular }
    }

    val performanceMode = LocalPerformanceMode.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 22.dp).navigationBarsPadding()) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.st_FileConverterScreen_sof57), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    if (fileCount > 1) {
                        Text("BATCH · $fileCount FILES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
            }

            Spacer(Modifier.height(16.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.st_FileConverterScreen_sf_hint58)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Clear, null, Modifier.size(18.dp)) } }
                } else null,
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Spacer(Modifier.height(14.dp))

            // Category chips
            if (categories.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .then(if (!performanceMode) Modifier.horizontalFadingEdges(left = 8.dp, right = 16.dp) else Modifier),
                ) {
                    items(categories) { cat ->
                        val selected = cat == selectedCategory
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontWeight = if (selected) FontWeight.Black else FontWeight.Normal) },
                            shape = CircleShape,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .then(if (!performanceMode) Modifier.fadingEdges(top = 12.dp, bottom = 24.dp) else Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredTypes, key = { it.name }) { type ->
                    TypeOptionItem(type = type, onClick = { onTypeSelected(type) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  All Formats Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AllFormatsSheet(onDismiss: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val allTypes = ConversionEngine.ConversionType.entries.toList()
    val filteredTypes = remember(searchQuery) {
        allTypes.filter {
            searchQuery.isBlank() ||
                it.extension.contains(searchQuery, ignoreCase = true) ||
                it.label.contains(searchQuery, ignoreCase = true)
        }.groupBy { it.category }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 22.dp).navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.st_FileConverterScreen_lof59), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.st_FileConverterScreen_sf50_hint60)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Clear, null, Modifier.size(18.dp)) } }
                } else null,
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
            )
            val performanceMode = LocalPerformanceMode.current
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .then(if (!performanceMode) Modifier.fadingEdges(top = 12.dp, bottom = 24.dp) else Modifier)
            ) {
                filteredTypes.forEach { (cat, types) ->
                    item {
                        Text(
                            text = cat.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp),
                        )
                    }
                    itemsIndexed(types) { _, type ->
                        val color = categoryColor(type.category)
                        ExpressiveCard(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = MediumExpressiveShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            elevation = 0.dp,
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                                Text(".${type.extension.uppercase()}", modifier = Modifier.width(68.dp), fontWeight = FontWeight.Black, color = color, style = MaterialTheme.typography.bodyMedium)
                                Text(type.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (type.isPopular) Icon(Icons.Rounded.Star, null, Modifier.size(13.dp), tint = Color(0xFFFFD700))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Type Option Item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TypeOptionItem(type: ConversionEngine.ConversionType, onClick: () -> Unit) {
    val color = categoryColor(type.category)
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(42.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(type.category), null, Modifier.size(22.dp), tint = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(type.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    if (type.isPopular) {
                        Surface(shape = CircleShape, color = Color(0xFFFFD700).copy(alpha = 0.15f)) {
                            Text("⚡", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(".${type.extension} · ${type.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Recent Conversion Item (swipe to dismiss)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecentConversionItem(
    recent: RecentConversion,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                vibrationManager?.vibrateTick()
                onDismiss()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MediumExpressiveShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete, null,
                    modifier = Modifier.padding(end = 20.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        ExpressiveCard(
            onClick = {
                try {
                    val file = File(recent.outputPath)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.st_FileConverterScreen_of56)))
                    }
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MediumExpressiveShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            elevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val color = categoryColor(recent.category)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(categoryIcon(recent.category), null, Modifier.size(18.dp), tint = color)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(recent.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        File(recent.outputPath).name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    fmt.format(Date(recent.timestampMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun categoryColor(category: String): Color = when (category) {
    "Videos"     -> Color(0xFFD32F2F)
    "Audio"      -> Color(0xFF1976D2)
    "Images"     -> Color(0xFF388E3C)
    "Documents"  -> Color(0xFFFBC02D)
    "Animations" -> Color(0xFFC2185B)
    "Archives"   -> Color(0xFF795548)
    else         -> MaterialTheme.colorScheme.primary
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "Audio"      -> Icons.Rounded.MusicNote
    "Animations" -> Icons.Rounded.Animation
    "Images"     -> Icons.Rounded.Image
    "Documents"  -> Icons.Rounded.Description
    "Archives"   -> Icons.Rounded.FolderZip
    else         -> Icons.Rounded.Movie
}

private fun mimeToColor(mime: String): Color = when {
    mime.startsWith("video") -> Color(0xFFD32F2F)
    mime.startsWith("audio") -> Color(0xFF1976D2)
    mime.startsWith("image") -> Color(0xFF388E3C)
    mime == "application/pdf" -> Color(0xFFFBC02D)
    mime.startsWith("text") -> Color(0xFFFBC02D)
    else -> Color(0xFF607D8B)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
    else -> "${"%.2f".format(bytes / (1024f * 1024f * 1024f))} GB"
}
