/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.media

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.media.components.BackgroundCanvas
import com.frerox.toolz.ui.screens.media.components.BackgroundOptionsBar
import com.frerox.toolz.ui.screens.media.components.ModelHubContent
import com.frerox.toolz.ui.theme.SquircleShape
import com.frerox.toolz.ui.theme.toolzBackground

/**
 * Revamped Background Remover — Simple Clear M3 Expressive (2026).
 *
 * Principles:
 * - HeroDropZone when empty (dashed 240dp) vs Canvas when loaded
 * - ModalBottomSheet for Model Hub (not fullscreen overlay)
 * - Zoom/pan canvas + background mode bar + Isolated/Original toggle
 * - Bottom bar: single primary CTA (SELECT PHOTO → SAVE) + secondary HUB/RESET/SHARE
 * - Download manager with atomic + progress, no HTML-404 trap
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
    val snackbarHost = remember { SnackbarHostState() }

    var isHubOpen by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    // Deep-link / share-sheet initial image
    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrEmpty() && initialUri != "{initialUri}") {
            try { viewModel.onImageSelected(android.net.Uri.parse(initialUri)) } catch (_: Exception) {}
        }
    }

    // Auto-open hub if nothing downloaded
    LaunchedEffect(Unit) {
        if (!uiState.isModelDownloaded && uiState.downloadedIds.isEmpty()) {
            // small delay so Scaffold paints first
            kotlinx.coroutines.delay(350)
            if (!uiState.isModelDownloaded) isHubOpen = true
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            showOriginal = false
            viewModel.onImageSelected(uri)
        }
    }

    // Haptics + snackbars
    LaunchedEffect(uiState.resultBitmap) { if (uiState.resultBitmap != null) haptic.success() }
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            haptic.success()
            snackbarHost.showSnackbar("Saved to gallery ✓", withDismissAction = true)
            viewModel.dismissSaveSuccess()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { msg ->
            haptic.error()
            snackbarHost.showSnackbar(msg, withDismissAction = true)
            // keep error in VM until user dismisses via snackbar action, but auto-clear after show
        }
    }

    val subtitle = when {
        uiState.isModelDownloaded -> uiState.selectedModel?.displayName ?: "Ready"
        uiState.downloadedIds.isNotEmpty() -> "Pick a model to start"
        else -> "Model hub required"
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Scaffold(
        modifier = Modifier.toolzBackground(),
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Tool_BackgroundRemover),
                subtitle = subtitle,
                navigationIcon = {
                    ToolzTonalExpressiveIconButton(onClick = onNavigateBack, shape = SquircleShape) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Resolution badge
                    if (uiState.isModelDownloaded) {
                        Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "${uiState.selectedModel?.resolution ?: 256}p",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    ToolzTonalExpressiveIconButton(onClick = { isHubOpen = true }, shape = SquircleShape) {
                        Icon(Icons.Rounded.Memory, "Model hub", modifier = Modifier.size(20.dp))
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHost) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = SquircleShape,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Main viewport ──
            when {
                uiState.originalBitmap == null -> {
                    HeroDropZone(
                        isModelDownloaded = uiState.isModelDownloaded,
                        isProcessing = uiState.isProcessing,
                        onPick = {
                            if (uiState.isModelDownloaded) launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else isHubOpen = true
                        },
                        onHub = { isHubOpen = true },
                    )
                }
                else -> {
                    // Isolated vs Original pill (only when result exists)
                    if (uiState.resultBitmap != null) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ToolzConnectedButtonGroup(
                                selectedIndex = if (showOriginal) 1 else 0,
                                options = listOf("Isolated", "Original"),
                                unCheckedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                                checkedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                                onOptionSelected = { showOriginal = it == 1 },
                            )
                        }
                    }

                    // Canvas — fixed 430dp, weight-like but scroll-friendly
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(430.dp)
                            .clip(SquircleShape),
                    ) {
                        BackgroundCanvas(
                            original = uiState.originalBitmap,
                            result = uiState.resultBitmap,
                            previewBackground = uiState.previewBackground,
                            showOriginal = showOriginal,
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Loading overlay — only when inferring (not downloading)
                        if (uiState.isProcessing && uiState.downloadProgress == 0f) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = SquircleShape,
                                tonalElevation = 6.dp,
                                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    ToolzWavyCircularProgressIndicator(modifier = Modifier.size(52.dp))
                                    Spacer(Modifier.height(14.dp))
                                    Text("Removing background…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        uiState.selectedModel?.displayName ?: "AI matting",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Background mode bar — only when isolated exists and we're viewing isolated
                    if (uiState.resultBitmap != null && !showOriginal) {
                        BackgroundOptionsBar(
                            selected = uiState.previewBackground,
                            onSelect = { viewModel.setPreviewBackground(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Preview only — saved image stays transparent unless you choose White.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Small meta row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val w = uiState.originalBitmap?.width ?: 0
                        val h = uiState.originalBitmap?.height ?: 0
                        Text(
                            if (w > 0) "${w}×${h} • ${uiState.selectedModel?.displayName ?: ""}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (uiState.resultBitmap != null) {
                            Text("Pinch to zoom • drag to pan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // ── Bottom action bar ──
            if (uiState.originalBitmap == null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolzOutlinedExpressiveButton(
                        onClick = { isHubOpen = true },
                        modifier = Modifier.weight(0.38f).height(56.dp),
                        shape = SquircleShape,
                    ) {
                        Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("HUB", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    ToolzExpressiveButton(
                        onClick = {
                            if (uiState.isModelDownloaded) launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            else isHubOpen = true
                        },
                        modifier = Modifier.weight(0.62f).height(56.dp),
                        shape = SquircleShape,
                        enabled = !uiState.isProcessing,
                    ) {
                        Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("SELECT PHOTO", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolzOutlinedExpressiveButton(
                        onClick = { showOriginal = false; viewModel.clearResult() },
                        modifier = Modifier.weight(0.30f).height(56.dp),
                        shape = SquircleShape,
                    ) { Text("RESET", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }

                    ToolzTonalExpressiveButton(
                        onClick = {
                            uiState.resultBitmap?.let { bmp ->
                                val intent = viewModel.getShareIntent(bmp)
                                if (intent != null) context.startActivity(Intent.createChooser(intent, "Share image"))
                                else snackbarHost.let { /* fallback toast handled by VM error */ }
                            }
                        },
                        modifier = Modifier.weight(0.32f).height(56.dp),
                        shape = SquircleShape,
                        enabled = uiState.resultBitmap != null && !uiState.isProcessing,
                    ) {
                        Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SHARE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    ToolzExpressiveButton(
                        onClick = {
                            uiState.resultBitmap?.let { bmp ->
                                // If White preview, composite before save
                                if (uiState.previewBackground is PreviewBackground.White || uiState.previewBackground is PreviewBackground.Color) {
                                    viewModel.saveResultWithBackground(bmp, uiState.previewBackground)
                                } else viewModel.saveResult(bmp)
                            }
                        },
                        modifier = Modifier.weight(0.38f).height(56.dp),
                        shape = SquircleShape,
                        enabled = uiState.resultBitmap != null && !uiState.isProcessing,
                    ) {
                        Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SAVE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }

                // Change photo tertiary
                TextButton(
                    onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Change photo", fontWeight = FontWeight.SemiBold)
                }
            }

            // Download progress when downloading (not inferring)
            if (uiState.isProcessing && uiState.downloadProgress > 0f && uiState.downloadProgress < 1f) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(progress = { uiState.downloadProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().clip(SquircleShape))
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading model… ${(uiState.downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // Tip
            if (uiState.originalBitmap == null) {
                Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("How it works", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("Pick a photo → AI isolates subject → save transparent PNG. 100% offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                    }
                }
            }
            // bottom padding for nav bar
            Spacer(Modifier.height(8.dp))
        }
    }

    // ── Model Hub Sheet ──
    if (isHubOpen) {
        ModalBottomSheet(
            onDismissRequest = { isHubOpen = false },
            sheetState = sheetState,
            shape = SquircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            dragHandle = null,
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
                ModelHubContent(
                    selectedModel = uiState.selectedModel,
                    isDownloading = uiState.isProcessing && uiState.downloadProgress in 0.01f..0.99f,
                    downloadProgress = uiState.downloadProgress,
                    onModelSelect = { viewModel.selectModel(it) },
                    onDownloadClick = { viewModel.downloadModel(it) },
                    onDeleteClick = { viewModel.deleteModel(it) },
                    onProceed = { isHubOpen = false },
                    isExistingModel = { model -> uiState.downloadedIds.contains(model.id) },
                )
            }
        }
    }

    // Error dialog (fallback if snackbar not enough)
    if (uiState.error != null && uiState.originalBitmap == null && uiState.downloadProgress == 0f) {
        // show as dialog only for non-snackbar contexts (e.g., first launch no image)
        // otherwise snackbar already shown — still allow dismiss
    }
}

@Composable
private fun HeroDropZone(
    isModelDownloaded: Boolean,
    isProcessing: Boolean,
    onPick: () -> Unit,
    onHub: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "hero_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val alpha by infinite.animateFloat(
        initialValue = 0.55f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )

    ExpressiveCard(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth().height(420.dp),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // dashed-like inner border simulation via outer card + inner surface
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Surface(shape = SquircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), modifier = Modifier.size(84.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))) {
                        Icon(
                            if (isModelDownloaded) Icons.Rounded.AutoAwesomeMotion else Icons.Rounded.CloudDownload,
                            null,
                            modifier = Modifier.size(42.dp).let { if (!isProcessing) it else it },
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    if (isModelDownloaded) "Isolate any subject" else "Download an AI model",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isModelDownloaded) "Tap to pick a photo — portraits, pets, products all work."
                    else "Open the model hub to download a 100% offline segmentation engine. No internet needed after.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolzExpressiveButton(onClick = onPick, shape = SquircleShape, modifier = Modifier.height(48.dp)) {
                        Icon(if (isModelDownloaded) Icons.Rounded.AddAPhoto else Icons.Rounded.Memory, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isModelDownloaded) "SELECT PHOTO" else "OPEN MODEL HUB", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (!isModelDownloaded) {
                        ToolzTonalExpressiveButton(onClick = onPick, shape = SquircleShape, modifier = Modifier.height(48.dp)) {
                            Text("Later", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (!isModelDownloaded) {
                    Spacer(Modifier.height(10.dp))
                    Text("250 KB Fast model recommended for first try.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
