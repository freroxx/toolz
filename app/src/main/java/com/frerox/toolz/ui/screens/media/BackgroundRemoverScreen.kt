/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.screens.media.components.BackgroundCanvas
import com.frerox.toolz.ui.screens.media.components.BackgroundOptionsBar
import com.frerox.toolz.ui.screens.media.components.ModelHubContent
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.SquircleShape
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * Background Remover — M3 Expressive, 2026 redesign.
 *
 * Two states only:
 *   HERO    — one card, one headline, one action.
 *   EDITOR  — canvas fills the screen; controls float on it; two-button bar below.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackgroundRemoverScreen(
    onNavigateBack: () -> Unit,
    initialUri: String? = null,
    viewModel: BackgroundRemoverViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = rememberToolzHapticFeedback()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var isHubOpen by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            showOriginal = false
            viewModel.onImageSelected(uri)
        }
    }
    fun pick() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrEmpty() && initialUri != "{initialUri}") {
            try { viewModel.onImageSelected(android.net.Uri.parse(initialUri)) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        if (!uiState.isModelDownloaded && uiState.downloadedIds.isEmpty()) {
            kotlinx.coroutines.delay(400)
            isHubOpen = true
        }
    }

    LaunchedEffect(uiState.resultBitmap) { if (uiState.resultBitmap != null) haptic.success() }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            haptic.success()
            snackbar.showSnackbar("Saved to Pictures/Toolz", withDismissAction = true)
            viewModel.dismissSaveSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            snackbar.showSnackbar(msg, withDismissAction = true)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = Modifier.toolzBackground(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = SquircleShape,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Tool_BackgroundRemover),
                subtitle = when {
                    !uiState.isModelDownloaded -> "Set up AI model"
                    else -> uiState.selectedModel?.displayName
                },
                navigationIcon = {
                    ToolzTonalExpressiveIconButton(onClick = onNavigateBack, shape = SquircleShape) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    ToolzTonalExpressiveIconButton(onClick = { isHubOpen = true }, shape = SquircleShape) {
                        Icon(Icons.Rounded.Memory, "AI models", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { padding ->
        val hasImage = uiState.originalBitmap != null

        AnimatedContent(
            targetState = hasImage,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 12 }) togetherWith
                    (fadeOut(tween(180)))
            },
            label = "bg_state",
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { loaded ->
            if (!loaded) {
                HeroPane(
                    hasModel = uiState.isModelDownloaded,
                    onPick = ::pick,
                    onBrowseModels = { isHubOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EditorPane(
                    uiState = uiState,
                    showOriginal = showOriginal,
                    onToggleOriginal = { showOriginal = it == 1 },
                    onSelectBackground = { viewModel.setPreviewBackground(it) },
                    onReset = {
                        showOriginal = false
                        viewModel.clearResult()
                    },
                    onSave = {
                        uiState.resultBitmap?.let { bmp ->
                            val bg = uiState.previewBackground
                            if (bg is PreviewBackground.White || bg is PreviewBackground.Color) {
                                viewModel.saveResultWithBackground(bmp, bg)
                            } else viewModel.saveResult(bmp)
                        }
                    },
                    onShare = {
                        uiState.resultBitmap?.let { bmp ->
                            viewModel.getShareIntent(bmp)?.let {
                                context.startActivity(Intent.createChooser(it, "Share cutout"))
                            }
                        }
                    },
                    onChangePhoto = ::pick,
                    onOpenHub = { isHubOpen = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (isHubOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { isHubOpen = false },
            sheetState = sheetState,
            shape = SquircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = null,
        ) {
            ModelHubContent(
                selectedModel = uiState.selectedModel,
                downloadingId = if (uiState.isProcessing && uiState.downloadProgress in 0.01f..0.99f)
                    uiState.selectedModel?.id else null,
                downloadProgress = uiState.downloadProgress,
                downloadedIds = uiState.downloadedIds,
                onModelSelect = { viewModel.selectModel(it) },
                onDownloadClick = { viewModel.downloadModel(it) },
                onDeleteClick = { viewModel.deleteModel(it) },
                onProceed = { isHubOpen = false },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

// ─────────────────────────────── HERO ───────────────────────────────

@Composable
private fun HeroPane(
    hasModel: Boolean,
    onPick: () -> Unit,
    onBrowseModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val performanceMode = LocalPerformanceMode.current
    val ring = rememberInfiniteTransition(label = "hero_ring")
    val breathe by ring.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val glow by ring.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    Surface(
        onClick = onPick,
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier.padding(16.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // soft halo
                    if (!performanceMode) {
                        Box(
                            Modifier
                                .size(132.dp)
                                .graphicsLayer { scaleX = breathe; scaleY = breathe }
                                .clip(SquircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = glow * 0.35f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                        )
                                    )
                                ),
                        )
                    }
                    Surface(
                        shape = SquircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(96.dp).graphicsLayer { scaleX = breathe; scaleY = breathe },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Rounded.AutoAwesome, null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    "Remove any background",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "On-device AI · private · offline",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                ToolzExpressiveButton(
                    onClick = onPick,
                    shape = SquircleShape,
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(if (hasModel) Icons.Rounded.AddAPhoto else Icons.Rounded.Memory, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (hasModel) "Choose a photo" else "Get started",
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onBrowseModels) { Text("AI models") }
            }
        }
    }
}

// ────────────────────────────── EDITOR ──────────────────────────────

@Composable
private fun EditorPane(
    uiState: BackgroundRemoverUiState,
    showOriginal: Boolean,
    onToggleOriginal: (Int) -> Unit,
    onSelectBackground: (PreviewBackground) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onChangePhoto: () -> Unit,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasResult = uiState.resultBitmap != null
    val inferring = uiState.isProcessing && uiState.downloadProgress == 0f

    Column(modifier.padding(horizontal = 16.dp)) {

        // Canvas fills all remaining space
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(SquircleShape),
        ) {
            BackgroundCanvas(
                original = uiState.originalBitmap,
                result = uiState.resultBitmap,
                previewBackground = uiState.previewBackground,
                showOriginal = showOriginal,
                modifier = Modifier.fillMaxSize(),
            )

            // Isolated | Original — floats top-center
            if (hasResult) {
                ToolzConnectedButtonGroup(
                    selectedIndex = if (showOriginal) 1 else 0,
                    options = listOf("Isolated", "Original"),
                    unCheckedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                    checkedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                    onOptionSelected = onToggleOriginal,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                )
            }

            // Background modes — floats bottom-center
            if (hasResult && !showOriginal) {
                BackgroundOptionsBar(
                    selected = uiState.previewBackground,
                    onSelect = onSelectBackground,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                )
            }

            // Inference scrim
            if (inferring) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ToolzWavyCircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Removing background",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        uiState.selectedModel?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // No model yet → gentle gate
            if (!uiState.isModelDownloaded && !inferring) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "AI model needed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(14.dp))
                        ToolzExpressiveButton(onClick = onOpenHub, shape = SquircleShape, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open AI models", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action bar — exactly one row, always.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!hasResult) {
                ToolzTonalExpressiveIconButton(
                    onClick = onOpenHub,
                    shape = SquircleShape,
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Memory, "AI models") }

                ToolzExpressiveButton(
                    onClick = onChangePhoto,
                    enabled = !uiState.isProcessing,
                    shape = SquircleShape,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Choose photo", fontWeight = FontWeight.ExtraBold)
                }
            } else {
                ToolzTonalExpressiveIconButton(
                    onClick = onReset,
                    shape = SquircleShape,
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Refresh, "Reset") }

                ToolzExpressiveButton(
                    onClick = onSave,
                    enabled = !uiState.isProcessing,
                    shape = SquircleShape,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Rounded.SaveAlt, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Save PNG", fontWeight = FontWeight.ExtraBold)
                }

                ToolzTonalExpressiveIconButton(
                    onClick = onShare,
                    enabled = !uiState.isProcessing,
                    shape = SquircleShape,
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Share, "Share") }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
