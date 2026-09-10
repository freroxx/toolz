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
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.frerox.toolz.R
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.screens.media.components.BackgroundCanvas
import com.frerox.toolz.ui.screens.media.components.BackgroundOptionsBar
import com.frerox.toolz.ui.screens.media.components.ModelHubContent
import com.frerox.toolz.ui.screens.media.components.downloadStatusLine
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.SquircleShape
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

/**
 * Background Remover — M3 Expressive, 2026 revamp.
 *
 * Two states only:
 *   HERO    — one card, one headline, one action.
 *   EDITOR  — canvas fills the screen; controls float on it; two-button bar below.
 *
 * The UI renders the ViewModel's [BgStage] — it never infers pipeline state.
 * Failures are inline cards with a retry action, never drive-by snackbars.
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
    val scope = rememberCoroutineScope()

    var isHubOpen by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    var meteredAsk by remember { mutableStateOf<BackgroundModel?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            showOriginal = false
            viewModel.onImageSelected(uri)
        }
    }
    fun pick() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun requestDownload(model: BackgroundModel) {
        // Select first so progress, naming, and post-download auto-run all refer
        // to the model the user actually tapped — never a stale selection.
        if (viewModel.uiState.value.selectedModel != model) viewModel.selectModel(model)
        if (viewModel.downloadNeedsMeteredConsent(model)) meteredAsk = model
        else viewModel.downloadModel(model)
    }

    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrEmpty() && initialUri != "{initialUri}") {
            try { viewModel.onImageSelected(android.net.Uri.parse(initialUri)) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(uiState.resultBitmap) { if (uiState.resultBitmap != null) haptic.success() }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            haptic.success()
            snackbar.showSnackbar(
                context.getString(R.string.st_BackgroundRemover_SavedTo),
                withDismissAction = true,
            )
            viewModel.dismissSaveSuccess()
        }
    }

    // Save-path errors only — pipeline failures render as inline cards.
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
                    !uiState.isModelDownloaded -> stringResource(R.string.st_BackgroundRemover_SetupModel)
                    else -> uiState.selectedModel?.displayName
                },
                navigationIcon = {
                    ToolzTonalExpressiveIconButton(onClick = onNavigateBack, shape = SquircleShape) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_Back))
                    }
                },
                actions = {
                    ToolzTonalExpressiveIconButton(onClick = { isHubOpen = true }, shape = SquircleShape) {
                        Icon(
                            Icons.Rounded.Memory,
                            stringResource(R.string.st_BackgroundRemover_Models),
                            modifier = Modifier.size(20.dp),
                        )
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
                            viewModel.saveResultWithBackground(bmp, uiState.previewBackground)
                        }
                    },
                    onShare = {
                        uiState.resultBitmap?.let { bmp ->
                            viewModel.getShareIntent(bmp)?.let {
                                context.startActivity(
                                    Intent.createChooser(
                                        it,
                                        context.getString(R.string.st_BackgroundRemover_ShareCutout),
                                    ),
                                )
                            }
                        }
                    },
                    onChangePhoto = ::pick,
                    onOpenHub = { isHubOpen = true },
                    onCancelWork = { viewModel.cancelActive() },
                    onRetry = { failure ->
                        when (failure.retry) {
                            RetryAction.OPEN_HUB -> {
                                viewModel.dismissError()
                                isHubOpen = true
                            }
                            RetryAction.PICK_IMAGE -> {
                                viewModel.dismissError()
                                pick()
                            }
                            else -> viewModel.retryFromFailure()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    meteredAsk?.let { model ->
        AlertDialog(
            onDismissRequest = { meteredAsk = null },
            icon = { Icon(Icons.Rounded.WarningAmber, null) },
            title = { Text(stringResource(R.string.st_BackgroundRemover_MeteredTitle)) },
            text = {
                Text(
                    stringResource(
                        R.string.st_BackgroundRemover_MeteredText,
                        model.shortName,
                        model.sizeLabel,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    meteredAsk = null
                    viewModel.downloadModel(model, allowMetered = true)
                }) { Text(stringResource(R.string.st_BackgroundRemover_MeteredAllow)) }
            },
            dismissButton = {
                TextButton(onClick = { meteredAsk = null }) {
                    Text(stringResource(R.string.st_Common_Cancel))
                }
            },
        )
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
                downloadingId = uiState.downloadingId,
                downloadedBytes = uiState.downloadedBytes,
                totalBytes = uiState.totalBytes,
                downloadSpeedBps = uiState.downloadSpeedBps,
                downloadedIds = uiState.downloadedIds,
                onModelSelect = {
                    viewModel.selectModel(it)
                    if (uiState.downloadedIds.contains(it.id)) isHubOpen = false
                },
                onDownloadClick = ::requestDownload,
                onDeleteClick = { model ->
                    viewModel.deleteModel(model)
                    scope.launch {
                        val res = snackbar.showSnackbar(
                            message = context.getString(R.string.st_BackgroundRemover_ModelRemoved, model.shortName),
                            actionLabel = context.getString(R.string.st_BackgroundRemover_Redownload),
                            withDismissAction = true,
                        )
                        if (res == SnackbarResult.ActionPerformed) requestDownload(model)
                    }
                },
                onCancelDownload = { viewModel.cancelActive() },
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
    val breathe = if (performanceMode) 1f else {
        val t = rememberInfiniteTransition(label = "hero_breathe")
        t.animateFloat(
            initialValue = 0.985f, targetValue = 1.015f,
            animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe",
        ).value
    }

    Surface(
        onClick = if (hasModel) onPick else onBrowseModels,
        shape = SquircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.padding(16.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Surface(
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(104.dp)
                        .graphicsLayer { scaleX = breathe; scaleY = breathe },
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Rounded.Face, null,
                            modifier = Modifier.size(46.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(R.string.st_BackgroundRemover_HeroTitle),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(36.dp))

                ToolzExpressiveButton(
                    onClick = if (hasModel) onPick else onBrowseModels,
                    shape = SquircleShape,
                    modifier = Modifier.height(54.dp),
                ) {
                    Icon(
                        if (hasModel) Icons.Rounded.AddAPhoto else Icons.Rounded.Memory,
                        null, Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (hasModel) stringResource(R.string.st_BackgroundRemover_ChooseAPhoto)
                        else stringResource(R.string.st_BackgroundRemover_SetupModel),
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (!hasModel) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onPick) {
                        Text(
                            stringResource(R.string.st_BackgroundRemover_PickFirst),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBrowseModels) {
                        Text(stringResource(R.string.st_BackgroundRemover_Models))
                    }
                }
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
    onCancelWork: () -> Unit,
    onRetry: (BgFailure) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberToolzHapticFeedback()
    val hasResult = uiState.resultBitmap != null
    val working = uiState.stage == BgStage.SEGMENTING || uiState.stage == BgStage.MATTING
    val failed = uiState.stage == BgStage.FAILED && uiState.failure != null

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
            if (hasResult && !working) {
                ToolzConnectedButtonGroup(
                    selectedIndex = if (showOriginal) 1 else 0,
                    options = listOf(
                        stringResource(R.string.st_BackgroundRemover_Isolated),
                        stringResource(R.string.st_BackgroundRemover_Original),
                    ),
                    unCheckedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                    checkedIcons = listOf(Icons.Rounded.ContentCut, Icons.Rounded.Image),
                    onOptionSelected = {
                        haptic.tick()
                        onToggleOriginal(it)
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                )
            }

            // Background modes — floats bottom-center
            if (hasResult && !showOriginal && !working && !failed) {
                BackgroundOptionsBar(
                    selected = uiState.previewBackground,
                    onSelect = {
                        haptic.tick()
                        onSelectBackground(it)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                )
            }

            // Working scrim — names the stage, offers cancellation
            if (working) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ToolzWavyCircularProgressIndicator(modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (uiState.stage == BgStage.MATTING) stringResource(R.string.st_BackgroundRemover_WorkingEdge)
                            else stringResource(R.string.st_BackgroundRemover_WorkingBg),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        uiState.selectedModel?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                it.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onCancelWork) {
                            Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.st_Common_Cancel),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Inline failure — message plus the one action that fixes it
            if (failed) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = SquircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        ) {
                            Icon(
                                Icons.Rounded.WarningAmber, null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                uiState.failure?.message.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(14.dp))
                            ToolzExpressiveButton(
                                onClick = { uiState.failure?.let(onRetry) },
                                shape = SquircleShape,
                                modifier = Modifier.height(48.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (uiState.failure?.retry) {
                                        RetryAction.OPEN_HUB -> stringResource(R.string.st_BackgroundRemover_OpenModels)
                                        RetryAction.PICK_IMAGE -> stringResource(R.string.st_BackgroundRemover_ChooseAnother)
                                        else -> stringResource(R.string.st_BackgroundRemover_Retry)
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Downloading with a photo loaded — byte truth bar, hub holds the details.
            // Indeterminate while the server hides the total: live MB, never a frozen %.
            if (uiState.stage == BgStage.DOWNLOADING && uiState.downloadingId != null) {
                val dlName = BackgroundModel.fromId(uiState.downloadingId)?.shortName.orEmpty()
                Column(Modifier.align(Alignment.TopCenter).padding(top = 10.dp, start = 24.dp, end = 24.dp)) {
                    if (uiState.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$dlName · ${
                            downloadStatusLine(
                                uiState.downloadedBytes,
                                uiState.totalBytes,
                                uiState.downloadSpeedBps,
                            )
                        }",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // No model yet → gentle gate over a dim (sheet no longer auto-opens on first run)
            if (!uiState.isModelDownloaded && !working && !failed && uiState.stage != BgStage.DOWNLOADING) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.st_BackgroundRemover_ModelNeeded),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Spacer(Modifier.height(14.dp))
                        ToolzExpressiveButton(onClick = onOpenHub, shape = SquircleShape, modifier = Modifier.height(48.dp)) {
                            Icon(Icons.Rounded.Memory, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.st_BackgroundRemover_OpenModels),
                                fontWeight = FontWeight.Bold,
                            )
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
                ) {
                    Icon(
                        Icons.Rounded.Memory,
                        stringResource(R.string.st_BackgroundRemover_Models),
                    )
                }

                ToolzExpressiveButton(
                    onClick = onChangePhoto,
                    enabled = !working && uiState.stage != BgStage.DOWNLOADING,
                    shape = SquircleShape,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.st_BackgroundRemover_ChooseAPhoto),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            } else {
                ToolzTonalExpressiveIconButton(
                    onClick = onReset,
                    shape = SquircleShape,
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Refresh, stringResource(R.string.st_BackgroundRemover_Reset)) }

                ToolzExpressiveButton(
                    onClick = onSave,
                    enabled = !working,
                    shape = SquircleShape,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Rounded.SaveAlt, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.st_BackgroundRemover_SavePng),
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                ToolzTonalExpressiveIconButton(
                    onClick = onShare,
                    enabled = !working,
                    shape = SquircleShape,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        stringResource(R.string.st_BackgroundRemover_Share),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
