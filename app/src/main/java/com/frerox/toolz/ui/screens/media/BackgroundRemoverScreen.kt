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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.media.components.ModelHubContent
import com.frerox.toolz.ui.theme.SquircleShape
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File

/**
 * Background Remover screen using TensorFlow Lite for segmentation and Toolz Expressive UI.
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
    
    // Auto-open hub if no model is selected and none exist
    LaunchedEffect(Unit) {
        if (uiState.selectedModel == null) {
            val modelsDir = File(context.filesDir, "models")
            val hasAnyModel = modelsDir.exists() && (modelsDir.listFiles()?.size ?: 0) > 0
            if (!hasAnyModel) {
                isHubOpen = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.toolzBackground(),
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Tool_BackgroundRemover),
                subtitle = uiState.selectedModel?.displayName ?: stringResource(R.string.st_Tool_BackgroundRemover_Desc),
                actions = {
                    ToolzTonalExpressiveIconButton(
                        onClick = { isHubOpen = true },
                        shape = SquircleShape,
                    ) {
                        Icon(Icons.Rounded.Memory, "Models", modifier = Modifier.size(20.dp))
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
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = uiState.resultBitmap ?: uiState.originalBitmap,
                            transitionSpec = {
                                fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                            },
                            label = "image_transition"
                        ) { bitmap ->
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "hint_anim")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 0.6f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1500, easing = EaseInOutSine),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "alpha"
                                    )
                                    
                                    Icon(
                                        Icons.Rounded.AddPhotoAlternate,
                                        null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "Select an image to remove background",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        if (uiState.isProcessing && uiState.downloadProgress == 0f) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    shape = SquircleShape,
                                    modifier = Modifier.size(120.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                        Spacer(Modifier.height(12.dp))
                                        Text("Processing", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.resultBitmap == null) {
                        ToolzExpressiveButton(
                            onClick = {
                                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = SquircleShape,
                            enabled = uiState.isModelDownloaded
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.st_BackgroundRemover_Pick), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else {
                        ToolzOutlinedExpressiveButton(
                            onClick = { viewModel.clearResult() },
                            modifier = Modifier.weight(0.4f).height(56.dp),
                            shape = SquircleShape
                        ) {
                            Text(stringResource(R.string.st_BackgroundRemover_Clear), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        ToolzExpressiveButton(
                            onClick = { uiState.resultBitmap?.let { viewModel.saveResult(it) } },
                            modifier = Modifier.weight(0.6f).height(56.dp),
                            shape = SquircleShape
                        ) {
                            Icon(Icons.Rounded.Save, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.st_BackgroundRemover_Save), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
            
            // Model Hub Overlay
            AnimatedVisibility(
                visible = isHubOpen,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(modifier = Modifier.padding(20.dp)) {
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
                    Text("OK")
                }
            },
            title = { Text("AI Error", fontWeight = FontWeight.Bold) },
            text = { Text(uiState.error!!, fontSize = 14.sp) },
            shape = SquircleShape
        )
    }
}
