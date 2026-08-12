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

package com.frerox.toolz.ui.screens.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.media.components.ModelHubContent
import com.frerox.toolz.ui.theme.SquircleShape
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File

/**
 * Clean & Simple Material 3 Expressive Background Remover Screen with Model Hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundRemoverScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackgroundRemoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = rememberToolzHapticFeedback()
    val context = androidx.compose.ui.platform.LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    val successMsg = stringResource(R.string.st_BackgroundRemover_Success)

    LaunchedEffect(uiState.resultBitmap) {
        if (uiState.resultBitmap != null) {
            haptic.success()
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            haptic.success()
            android.widget.Toast.makeText(context, successMsg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var isHubOpen by remember { mutableStateOf(false) }
    var selectedPreviewTab by remember { mutableIntStateOf(0) } // 0 = Isolated, 1 = Original

    // Auto-open Model Hub if no model is downloaded yet
    LaunchedEffect(Unit) {
        val modelsDir = File(context.filesDir, "models")
        val hasDownloadedModel = modelsDir.exists() && (modelsDir.listFiles()?.any { it.length() > 1024 } == true)
        if (!hasDownloadedModel) {
            isHubOpen = true
        }
    }

    Scaffold(
        modifier = Modifier.toolzBackground(),
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Tool_BackgroundRemover),
                subtitle = uiState.selectedModel?.displayName ?: "Model Hub Required",
                actions = {
                    ToolzTonalExpressiveIconButton(
                        onClick = { isHubOpen = true },
                        shape = SquircleShape,
                    ) {
                        Icon(Icons.Rounded.Memory, "Model Hub", modifier = Modifier.size(20.dp))
                    }
                },
                navigationIcon = {
                    ToolzTonalExpressiveIconButton(
                        onClick = onNavigateBack,
                        shape = SquircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main Photo Canvas Viewport
                ExpressiveCard(
                    onClick = {
                        if (!uiState.isModelDownloaded) {
                            isHubOpen = true
                        } else if (uiState.resultBitmap == null && !uiState.isProcessing) {
                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = SquircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        
                        val activeBitmap = if (selectedPreviewTab == 1) {
                            uiState.originalBitmap
                        } else {
                            uiState.resultBitmap ?: uiState.originalBitmap
                        }

                        // Render Checkerboard Background pattern when isolated cut-out is active
                        if (selectedPreviewTab == 0 && uiState.resultBitmap != null) {
                            CheckerboardPattern(modifier = Modifier.fillMaxSize())
                        }

                        AnimatedContent(
                            targetState = activeBitmap,
                            transitionSpec = {
                                fadeIn(tween(350)) togetherWith fadeOut(tween(350))
                            },
                            label = "preview_canvas"
                        ) { bitmap ->
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Photo Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                EmptyStatePlaceholder(
                                    isModelDownloaded = uiState.isModelDownloaded,
                                    onPickClick = {
                                        if (uiState.isModelDownloaded) {
                                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        } else {
                                            isHubOpen = true
                                        }
                                    }
                                )
                            }
                        }

                        // Top View Toggle Pill ("Isolated" vs "Original")
                        if (uiState.resultBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 16.dp)
                            ) {
                                ToolzConnectedButtonGroup(
                                    selectedIndex = selectedPreviewTab,
                                    options = listOf("Isolated", "Original"),
                                    unCheckedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                                    checkedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                                    onOptionSelected = { selectedPreviewTab = it }
                                )
                            }
                        }

                        // Wavy Circular Loading Indicator during AI inference
                        if (uiState.isProcessing && uiState.downloadProgress == 0f) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shape = SquircleShape,
                                    tonalElevation = 6.dp,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        ToolzWavyCircularProgressIndicator(modifier = Modifier.size(56.dp))
                                        Spacer(Modifier.height(18.dp))
                                        Text(
                                            "Removing Background...",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.resultBitmap == null) {
                        ToolzOutlinedExpressiveButton(
                            onClick = { isHubOpen = true },
                            modifier = Modifier
                                .weight(0.4f)
                                .height(60.dp),
                            shape = SquircleShape
                        ) {
                            Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("HUB", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }

                        ToolzExpressiveButton(
                            onClick = {
                                if (uiState.isModelDownloaded) {
                                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } else {
                                    isHubOpen = true
                                }
                            },
                            modifier = Modifier
                                .weight(0.6f)
                                .height(60.dp),
                            shape = SquircleShape,
                            enabled = !uiState.isProcessing
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("SELECT PHOTO", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                    } else {
                        ToolzOutlinedExpressiveButton(
                            onClick = { viewModel.clearResult() },
                            modifier = Modifier
                                .weight(0.35f)
                                .height(60.dp),
                            shape = SquircleShape
                        ) {
                            Text("RESET", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }

                        ToolzExpressiveButton(
                            onClick = { uiState.resultBitmap?.let { viewModel.saveResult(it) } },
                            modifier = Modifier
                                .weight(0.65f)
                                .height(60.dp),
                            shape = SquircleShape,
                            enabled = !uiState.isProcessing
                        ) {
                            Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("SAVE IMAGE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Model Hub Modal Overlay
            AnimatedVisibility(
                visible = isHubOpen,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().toolzBackground(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        ModelHubContent(
                            selectedModel = uiState.selectedModel,
                            isDownloading = uiState.isProcessing && uiState.downloadProgress > 0f,
                            downloadProgress = uiState.downloadProgress,
                            onModelSelect = { viewModel.selectModel(it) },
                            onDownloadClick = { viewModel.downloadModel(it) },
                            onDeleteClick = { viewModel.deleteModel(it) },
                            onProceed = { isHubOpen = false },
                            isExistingModel = { model ->
                                File(context.filesDir, "models/${model.fileName}").let { it.exists() && it.length() > 1024 }
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Error", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = { Text(uiState.error!!, fontSize = 14.sp, lineHeight = 20.sp) },
            shape = SquircleShape
        )
    }
}

@Composable
private fun EmptyStatePlaceholder(
    isModelDownloaded: Boolean,
    onPickClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Icon(
            if (isModelDownloaded) Icons.Rounded.AutoAwesomeMotion else Icons.Rounded.CloudDownload,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            if (isModelDownloaded) "Isolate Subject" else "Download AI Model",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isModelDownloaded) "Select a photo from gallery to remove background"
            else "Open the Model Hub to download a segmentation engine",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        ToolzExpressiveButton(
            onClick = onPickClick,
            shape = SquircleShape,
            modifier = Modifier.height(48.dp)
        ) {
            Icon(
                if (isModelDownloaded) Icons.Rounded.AddAPhoto else Icons.Rounded.Memory,
                null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isModelDownloaded) "SELECT PHOTO" else "OPEN MODEL HUB",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun CheckerboardPattern(modifier: Modifier = Modifier) {
    val darkTileColor = Color(0xFFD0D0D0).copy(alpha = 0.35f)
    val lightTileColor = Color(0xFFEBEBEB).copy(alpha = 0.35f)
    val tileSizePx = 24f

    Canvas(modifier = modifier) {
        val cols = (size.width / tileSizePx).toInt() + 1
        val rows = (size.height / tileSizePx).toInt() + 1

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val isDark = (r + c) % 2 == 0
                drawRect(
                    color = if (isDark) darkTileColor else lightTileColor,
                    topLeft = Offset(c * tileSizePx, r * tileSizePx),
                    size = Size(tileSizePx, tileSizePx)
                )
            }
        }
    }
}
