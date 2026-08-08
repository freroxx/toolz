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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.media.components.ModelHubContent
import com.frerox.toolz.ui.theme.SquircleShape
import com.frerox.toolz.ui.theme.toolzBackground

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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
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
    
    // Auto-open hub if no model is downloaded
    LaunchedEffect(uiState.isModelDownloaded) {
        if (!uiState.isModelDownloaded) {
            isHubOpen = true
        }
    }

    Scaffold(
        modifier = Modifier.toolzBackground(),
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Tool_BackgroundRemover),
                subtitle = stringResource(R.string.st_Tool_BackgroundRemover_Desc),
                actions = {
                    if (uiState.isModelDownloaded) {
                        ToolzTonalExpressiveIconButton(
                            onClick = { isHubOpen = true },
                            shape = SquircleShape,
                        ) {
                            Icon(Icons.Rounded.Memory, "Model Hub")
                        }
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = uiState.resultBitmap ?: uiState.originalBitmap,
                            transitionSpec = {
                                fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith
                                        fadeOut(spring(stiffness = Spring.StiffnessLow))
                            },
                            label = "image_transition"
                        ) { bitmap ->
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "hint_anim")
                                    val scale by infiniteTransition.animateFloat(
                                        initialValue = 0.95f,
                                        targetValue = 1.05f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(2000, easing = EaseInOutSine),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "scale"
                                    )
                                    
                                    Icon(
                                        Icons.Rounded.Portrait,
                                        null,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            },
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Text(
                                        stringResource(R.string.st_BackgroundRemover_Hint),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 32.dp)
                                    )
                                }
                            }
                        }

                        if (uiState.isProcessing && uiState.downloadProgress == 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ExpressiveStatePill(
                                        text = stringResource(R.string.st_BackgroundRemover_Processing),
                                        icon = Icons.Rounded.AutoAwesome,
                                        color = MaterialTheme.colorScheme.primary,
                                        isFilled = true
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    ToolzWavyLinearProgressIndicator(
                                        modifier = Modifier.width(200.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                StaggeredEntrance(index = 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (uiState.resultBitmap == null) {
                            ToolzExpressiveButton(
                                onClick = {
                                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
                                shape = SquircleShape
                            ) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.st_BackgroundRemover_Pick), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            ToolzOutlinedExpressiveButton(
                                onClick = {
                                    viewModel.clearResult()
                                },
                                modifier = Modifier
                                    .weight(0.4f)
                                    .height(64.dp),
                                shape = SquircleShape
                            ) {
                                Text(stringResource(R.string.st_BackgroundRemover_Clear), fontWeight = FontWeight.Bold)
                            }

                            ToolzExpressiveButton(
                                onClick = {
                                    uiState.resultBitmap?.let { viewModel.saveResult(it) }
                                },
                                modifier = Modifier
                                    .weight(0.6f)
                                    .height(64.dp),
                                shape = SquircleShape
                            ) {
                                Icon(Icons.Rounded.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.st_BackgroundRemover_Save), fontWeight = FontWeight.Bold)
                            }
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
                    Box(modifier = Modifier.padding(24.dp)) {
                        ModelHubContent(
                            selectedModel = uiState.selectedModel,
                            isDownloading = uiState.isProcessing && uiState.downloadProgress > 0f,
                            downloadProgress = uiState.downloadProgress,
                            onModelSelect = { viewModel.selectModel(it) },
                            onDownloadClick = { viewModel.downloadModel(it) },
                            onProceed = { isHubOpen = false },
                            isModelDownloaded = uiState.isModelDownloaded
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
            title = { Text(stringResource(R.string.st_BackgroundRemover_Error)) },
            text = { Text(uiState.error!!) },
            shape = SquircleShape
        )
    }
}
