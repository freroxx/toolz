/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.media.downloader

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.R
import com.frerox.toolz.shortcuts.ToolShortcutDefinitions
import com.frerox.toolz.shortcuts.ToolShortcutManager
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveContainedLoadingIndicator
import com.frerox.toolz.ui.components.ExpressiveSplitButton
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveIconButton
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.worker.SocialDownloadWorker

/**
 * Unified Media Downloader tool — YouTube, TikTok and Instagram Reels.
 * M3 Expressive flow: select platforms → paste link → preview → quality → download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDownloaderScreen(
    onBack: () -> Unit,
    initialUrl: String? = null,
    onNavigateToSettings: () -> Unit = {},
    viewModel: MediaDownloaderViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val labels by viewModel.labels.collectAsState()
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val haptic = rememberToolzHapticFeedback()
    var showHistory by rememberSaveable { mutableStateOf(false) }

    val remote = ui.remote
    val hasResult = (remote != null && !remote.blocked) || ui.localSourceUrl != null
    val isBusy = ui.extracting || ui.probingLocal
    val isIdle = !hasResult && !isBusy && ui.error == null && ui.blockedMessage == null

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) viewModel.prefill(initialUrl)
    }
    LaunchedEffect(ui.downloadEnqueued) {
        ui.downloadEnqueued?.let {
            snackbar.showSnackbar(it.take(160))
            viewModel.consumeEnqueued()
        }
    }
    LaunchedEffect(ui.conversionStarted) {
        ui.conversionStarted?.let {
            snackbar.showSnackbar(it.take(160))
            viewModel.consumeConversionStarted()
        }
    }
    LaunchedEffect(hasResult, ui.error) {
        if (hasResult) haptic.success()
        else if (ui.error != null) haptic.error()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        containerColor = Color.Transparent,
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_MediaDownloader_Title),
                subtitle = platformNames(ui.enabledPlatforms, " • "),
                navigationIcon = {
                    ToolzTonalExpressiveIconButton(onClick = onBack, shape = SquircleShape) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    ToolzTonalExpressiveIconButton(
                        onClick = {
                            val def = ToolShortcutDefinitions.findById("shortcut_media_downloader")
                            if (def != null) ToolShortcutManager.requestPinShortcut(context, def)
                        },
                        shape = SquircleShape,
                    ) {
                        Icon(Icons.Rounded.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fadingEdges(top = 16.dp, bottom = 32.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "input") {
                DownloaderSection(0) {
                    InputCard(
                        viewModel = viewModel,
                        isBusy = isBusy,
                        onNavigateToSettings = onNavigateToSettings,
                    )
                }
            }

            if (history.isNotEmpty()) {
                item(key = "history") {
                    DownloaderSection(1) {
                        HistoryCard(
                            count = history.size,
                            onClick = { showHistory = true },
                        )
                    }
                }
            }

            item(key = "fetching") {
                AnimatedVisibility(
                    visible = isBusy,
                    enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                ) {
                    val skeletonMode = ui.detectedPlatform?.let {
                        viewModel.effectiveModeForUi(it)
                    } ?: ui.mode
                    FetchingSkeletonCard(
                        vertical = isVerticalVideo(ui.detectedPlatform),
                        mode = skeletonMode,
                    )
                }
            }

            item(key = "error") {
                AnimatedVisibility(
                    visible = ui.error != null,
                    enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                ) {
                    ui.error?.let { msg ->
                        StateMessageCard(
                            message = msg,
                            icon = Icons.Rounded.Error,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            onRetry = viewModel::extract,
                        )
                    }
                }
            }
            item(key = "blocked") {
                AnimatedVisibility(
                    visible = ui.blockedMessage != null,
                    enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                ) {
                    ui.blockedMessage?.let { msg ->
                        StateMessageCard(
                            message = msg,
                            icon = Icons.Rounded.Warning,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            item(key = "result") {
                AnimatedVisibility(
                    visible = hasResult && !isBusy,
                    enter = fadeIn(tween(140)) + expandVertically(tween(140)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
                ) {
                    if (hasResult) {
                        ResultCard(
                            viewModel = viewModel,
                            onDownload = { viewModel.downloadSelected(context) },
                        )
                    }
                }
            }

            if (isIdle) {
                item(key = "empty") {
                    DownloaderSection(1) {
                        EmptyStateHero(
                            hint = stringResource(
                                R.string.st_MediaDownloader_EmptyHint,
                                platformNames(ui.enabledPlatforms),
                            ),
                            platforms = stringResource(
                                R.string.st_MediaDownloader_WorksWith,
                                platformNames(ui.enabledPlatforms, " • "),
                            ),
                        )
                    }
                }
            }

            if (downloads.isNotEmpty()) {
                item(key = "downloads_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.st_MediaDownloader_Active),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                downloads.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                items(downloads, key = { it.id }) { info ->
                    val label = labels[info.id.toString()]
                    MediaDownloadRow(
                        info = info,
                        label = label,
                        progress = viewModel.downloadProgress(info),
                        fileUri = info.outputData.getString(SocialDownloadWorker.KEY_FILE_URI),
                        onCancel = { viewModel.cancelDownload(info.id) },
                        onOpen = { viewModel.openDownload(context, info, label) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        if (showHistory) {
            HistorySheet(
                history = history,
                downloads = downloads,
                labels = labels,
                onLinkClick = { entry ->
                    showHistory = false
                    viewModel.reopenHistory(entry)
                },
                onClearHistory = viewModel::clearHistory,
                onDismiss = { showHistory = false },
                downloadProgress = viewModel::downloadProgress,
                downloadFileUri = { info ->
                    info.outputData.getString(SocialDownloadWorker.KEY_FILE_URI)
                },
                onCancelDownload = viewModel::cancelDownload,
                onOpenDownload = { info, label ->
                    viewModel.openDownload(context, info, label)
                },
            )
        }
    }
}

// ── Input card ──────────────────────────────────────────────────────────────

@Composable
private fun InputCard(
    viewModel: MediaDownloaderViewModel,
    isBusy: Boolean,
    onNavigateToSettings: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()

    ExpressiveCard(onClick = {}, enabled = false, shape = LargeExpressiveShape) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = ui.url,
                onValueChange = viewModel::onUrlChange,
                placeholder = { Text(platformHint(ui.enabledPlatforms)) },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    if (ui.url.isNotBlank()) {
                        IconButton(onClick = { viewModel.onUrlChange("") }) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.st_MediaDownloader_Clear))
                        }
                    } else {
                        IconButton(onClick = {
                            readLinkFromClipboard(context)?.let { pasted ->
                                haptic.tick()
                                viewModel.onUrlChange(pasted)
                                viewModel.extract()
                            }
                        }) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                stringResource(R.string.st_MediaDownloader_Paste),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.extract() }),
                modifier = Modifier.fillMaxWidth(),
            )

            PlatformStatusRow(
                enabled = ui.enabledPlatforms,
                detected = ui.detectedPlatform,
                onToggle = viewModel::togglePlatform,
            )

            Text(
                stringResource(R.string.st_MediaDownloader_Options),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val modeIndex = when (
                ui.detectedPlatform?.let { viewModel.effectiveModeForUi(it) } ?: ui.mode
            ) {
                MediaDownloaderViewModel.DownloadMode.VIDEO -> 0
                MediaDownloaderViewModel.DownloadMode.BOTH -> 1
                MediaDownloaderViewModel.DownloadMode.AUDIO -> 2
            }
            ToolzConnectedButtonGroup(
                selectedIndex = modeIndex,
                options = listOf(
                    stringResource(R.string.st_MediaDownloader_VideoGroup),
                    stringResource(R.string.st_MediaDownloader_Both),
                    stringResource(R.string.st_MediaDownloader_AudioGroup),
                ),
                unCheckedIcons = listOf(
                    Icons.Outlined.Movie,
                    Icons.Outlined.Layers,
                    Icons.Outlined.MusicNote,
                ),
                checkedIcons = listOf(
                    Icons.Filled.Movie,
                    Icons.Filled.Layers,
                    Icons.Filled.MusicNote,
                ),
                onOptionSelected = { index ->
                    viewModel.setMode(
                        when (index) {
                            0 -> MediaDownloaderViewModel.DownloadMode.VIDEO
                            2 -> MediaDownloaderViewModel.DownloadMode.AUDIO
                            else -> MediaDownloaderViewModel.DownloadMode.BOTH
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            ExpressiveSplitButton(
                onClick = viewModel::extract,
                onMenuClick = onNavigateToSettings,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    if (isBusy) {
                        ExpressiveContainedLoadingIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            containerColor = Color.Transparent,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.CloudDownload,
                            null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                label = {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isBusy,
                        label = "ctaLabel",
                    ) { busy ->
                        Text(
                            if (busy) {
                                stringResource(R.string.st_MediaDownloader_Fetching)
                            } else {
                                stringResource(R.string.st_MediaDownloader_Get)
                            },
                            fontWeight = FontWeight.Black,
                        )
                    }
                },
            )

            if (!ui.apiConfigured) {
                Text(
                    stringResource(R.string.st_MediaDownloader_ServerNeeded),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ── Result card ─────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(
    viewModel: MediaDownloaderViewModel,
    onDownload: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val remote = ui.remote
    val title = remote?.title ?: ui.localTitle ?: "Media"
    val thumb = remote?.thumbnail ?: ui.localThumbnail
    val uploader = remote?.uploader
    val duration = remote?.duration ?: 0L
    val views = formatCount(remote?.stats?.view_count)
    val likes = formatCount(remote?.stats?.like_count)

    val videoOpts = remember(ui.options) { ui.options.filter { !it.isAudio } }
    val audioOpts = remember(ui.options) { ui.options.filter { it.isAudio } }
    val selected = ui.options.firstOrNull { it.id == ui.selectedId }

    ExpressiveCard(
        onClick = {},
        enabled = false,
        shape = LargeExpressiveShape,
        modifier = Modifier.animateContentSize(tween(140)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.st_MediaDownloader_ResultLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            val verticalThumb = isVerticalVideo(resolvePlatform(ui.detectedPlatform, remote?.platform))
            val thumbModifier = if (verticalThumb) {
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(20.dp))
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
            }
            Box(modifier = thumbModifier) {
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Movie,
                                null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
                if (duration > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp),
                    ) {
                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!uploader.isNullOrBlank()) {
                    Text(
                        uploader,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (views != null || likes != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (views != null) {
                            StatChip(icon = Icons.Rounded.Visibility, value = views)
                        }
                        if (likes != null) {
                            StatChip(icon = Icons.Rounded.Favorite, value = likes)
                        }
                    }
                }
            }

            if (ui.options.isNotEmpty()) {
                Text(
                    stringResource(R.string.st_MediaDownloader_ChooseQuality),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (videoOpts.isNotEmpty()) {
                    if (audioOpts.isNotEmpty()) {
                        Text(
                            stringResource(R.string.st_MediaDownloader_VideoGroup),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    videoOpts.forEach { opt ->
                        QualityOptionRow(
                            option = opt,
                            selected = ui.selectedId == opt.id,
                            onSelect = { viewModel.selectOption(opt.id) },
                        )
                    }
                }
                if (audioOpts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.st_MediaDownloader_AudioGroup),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    audioOpts.forEach { opt ->
                        QualityOptionRow(
                            option = opt,
                            selected = ui.selectedId == opt.id,
                            onSelect = { viewModel.selectOption(opt.id) },
                        )
                    }
                }
                if (selected?.isHd == true) {
                    Text(
                        stringResource(R.string.st_SearchScreen_ws_yt_hd_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    )
                }
                ToolzExpressiveButton(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Text(
                        "${stringResource(R.string.st_MediaDownloader_Download)} • ${selected?.label ?: ""}",
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ToolzExpressiveTextButton(onClick = viewModel::clearResult) {
                        Text(
                            stringResource(R.string.st_MediaDownloader_NewLink),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Clipboard ───────────────────────────────────────────────────────────────

private fun readLinkFromClipboard(context: Context): String? {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val text = clip.getItemAt(i).coerceToText(context)?.toString() ?: continue
            val url = Regex("https?://[^\\s]+").find(text)?.value
            if (url != null) return url
        }
        null
    } catch (_: Exception) {
        null
    }
}
