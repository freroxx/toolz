/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.media.downloader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.video.VideoFrameDecoder
import com.frerox.toolz.R
import com.frerox.toolz.data.downloader.MediaDownloaderRepository
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator
import com.frerox.toolz.ui.components.ExpressiveStatePill
import com.frerox.toolz.ui.components.ExpressiveTypingDots
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback

// ── Platform status ─────────────────────────────────────────────────────────

private data class PlatformMeta(val label: String, val dot: Color)

@Composable
fun PlatformStatusRow(
    detected: MediaDownloaderRepository.Platform?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlatformStatusPill(
            meta = PlatformMeta("YouTube", Color(0xFFFF0000)),
            highlighted = detected == MediaDownloaderRepository.Platform.YOUTUBE,
            dimmed = detected != null && detected != MediaDownloaderRepository.Platform.YOUTUBE,
            modifier = Modifier.weight(1f),
        )
        PlatformStatusPill(
            meta = PlatformMeta("TikTok", Color(0xFF000000)),
            highlighted = detected == MediaDownloaderRepository.Platform.TIKTOK,
            dimmed = detected != null && detected != MediaDownloaderRepository.Platform.TIKTOK,
            modifier = Modifier.weight(1f),
        )
        PlatformStatusPill(
            meta = PlatformMeta("Reels", Color(0xFFDD2A7B)),
            highlighted = detected == MediaDownloaderRepository.Platform.INSTAGRAM,
            dimmed = detected != null && detected != MediaDownloaderRepository.Platform.INSTAGRAM,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlatformStatusPill(
    meta: PlatformMeta,
    highlighted: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = when {
        highlighted -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val content = when {
        highlighted -> MaterialTheme.colorScheme.onSecondaryContainer
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
        border = if (highlighted) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = if (dimmed) meta.dot.copy(alpha = 0.35f) else meta.dot,
                modifier = Modifier.size(10.dp),
            ) {}
            androidx.compose.foundation.layout.Spacer(Modifier.width(7.dp))
            Text(
                meta.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (highlighted) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedVisibility(
                visible = highlighted,
                enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut(),
            ) {
                Row {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

// ── Quality option row ──────────────────────────────────────────────────────

@Composable
fun QualityOptionRow(
    option: MediaDownloaderViewModel.QualityOption,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberToolzHapticFeedback()
    Surface(
        onClick = {
            haptic.tick()
            onSelect()
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Icon(
                    if (option.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.HighQuality,
                    null,
                    modifier = Modifier.padding(8.dp).size(19.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (option.detail.isNotBlank()) {
                    Text(
                        option.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut(),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        modifier = Modifier.padding(5.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

// ── State message card (error / blocked) ────────────────────────────────────

@Composable
fun StateMessageCard(
    message: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = {},
        enabled = false,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = contentColor.copy(alpha = 0.12f)) {
                Icon(icon, null, modifier = Modifier.padding(9.dp).size(20.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (onRetry != null) {
                    ToolzTonalExpressiveButton(onClick = onRetry) {
                        Text(stringResource(R.string.st_MediaDownloader_Retry), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ── Loading skeleton ────────────────────────────────────────────────────────

@Composable
fun FetchingSkeletonCard(modifier: Modifier = Modifier) {
    ExpressiveCard(
        onClick = {},
        enabled = false,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(180.dp),
            ) {}
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(0.75f).height(18.dp),
            ) {}
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(0.45f).height(14.dp),
            ) {}
            ExpressiveLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.st_MediaDownloader_FetchingLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                ExpressiveTypingDots()
            }
        }
    }
}

// ── Empty state hero ────────────────────────────────────────────────────────

@Composable
fun EmptyStateHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                Icons.Rounded.Download,
                null,
                modifier = Modifier.padding(26.dp).size(44.dp),
            )
        }
        Text(
            stringResource(R.string.st_MediaDownloader_EmptyTitle),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            stringResource(R.string.st_MediaDownloader_Empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ── Download row ────────────────────────────────────────────────────────────

@Composable
fun MediaDownloadRow(
    info: WorkInfo,
    label: MediaDownloaderViewModel.DownloadLabel?,
    progress: Float,
    fileUri: String?,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
    val openable = info.state == WorkInfo.State.SUCCEEDED
    val haptic = rememberToolzHapticFeedback()
    ExpressiveCard(
        onClick = {
            if (openable) {
                haptic.click()
                onOpen()
            }
        },
        enabled = openable,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DownloadThumb(
                    fileUri = fileUri,
                    remoteThumb = label?.thumbnailUrl,
                    isAudio = label?.isAudio == true,
                    showPlayBadge = openable && label?.isAudio != true,
                    modifier = Modifier.size(56.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        label?.title ?: stringResource(fallbackTitle(info)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DownloadStatusPill(state = info.state)
                        if (label != null) {
                            Text(
                                label.qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (active) {
                    ToolzOutlinedExpressiveIconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.st_MediaDownloader_Cancel))
                    }
                }
            }
            if (active) {
                if (progress.isNaN() || progress <= 0f) {
                    ExpressiveLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    ToolzWavyLinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!progress.isNaN() && progress > 0f && info.state == WorkInfo.State.RUNNING) {
                    Text(
                        "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

/**
 * Real thumbnail for a download row. Priority: decoded video frame from the saved
 * file (content URI) → remote artwork URL → expressive icon tile. Audio files
 * never attempt frame decode (Coil would fail) and use artwork directly.
 */
@Composable
private fun DownloadThumb(
    fileUri: String?,
    remoteThumb: String?,
    isAudio: Boolean,
    showPlayBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        androidx.compose.foundation.layout.Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            val hasVideoFile = !fileUri.isNullOrBlank() && !isAudio
            when {
                hasVideoFile -> {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context).data(fileUri).apply {
                            decoderFactory(VideoFrameDecoder.Factory())
                            size(Size(224, 224))
                        }.build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = { RemoteThumbOrIcon(remoteThumb, isAudio) },
                    )
                }
                !remoteThumb.isNullOrBlank() -> {
                    SubcomposeAsyncImage(
                        model = remoteThumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = { RemoteThumbOrIcon(null, isAudio) },
                    )
                }
                else -> RemoteThumbOrIcon(null, isAudio)
            }
            if (showPlayBadge) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp),
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        stringResource(R.string.st_MediaDownloader_Open),
                        modifier = Modifier.padding(3.dp).size(13.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteThumbOrIcon(remoteThumb: String?, isAudio: Boolean) {
    if (!remoteThumb.isNullOrBlank()) {
        AsyncImage(
            model = remoteThumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            if (isAudio) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
            null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun DownloadStatusPill(state: WorkInfo.State) {
    when (state) {
        WorkInfo.State.RUNNING -> ExpressiveStatePill(
            text = stringResource(R.string.st_MediaDownloader_StatusDownloading),
            icon = Icons.Rounded.Download,
            color = MaterialTheme.colorScheme.primary,
            isFilled = true,
        )
        WorkInfo.State.ENQUEUED -> ExpressiveStatePill(
            text = stringResource(R.string.st_MediaDownloader_StatusQueued),
            icon = Icons.Rounded.Schedule,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkInfo.State.SUCCEEDED -> ExpressiveStatePill(
            text = stringResource(R.string.st_MediaDownloader_StatusDone),
            icon = Icons.Rounded.Check,
            color = Color(0xFF2E7D32),
            isFilled = true,
        )
        WorkInfo.State.FAILED -> ExpressiveStatePill(
            text = stringResource(R.string.st_MediaDownloader_StatusFailed),
            icon = Icons.Rounded.Error,
            color = MaterialTheme.colorScheme.error,
            isFilled = true,
        )
        WorkInfo.State.CANCELLED -> ExpressiveStatePill(
            text = stringResource(R.string.st_MediaDownloader_StatusCancelled),
            icon = Icons.Rounded.Close,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkInfo.State.BLOCKED -> ExpressiveStatePill(
            text = stringResource(R.string.st_MediaDownloader_StatusWaiting),
            icon = Icons.Rounded.Schedule,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun fallbackTitle(info: WorkInfo): Int {
    val tags = info.tags.joinToString(" ")
    return when {
        "mp3" in tags || "music" in tags -> R.string.st_MediaDownloader_FallbackAudio
        "social" in tags -> R.string.st_MediaDownloader_FallbackMedia
        else -> R.string.st_MediaDownloader_FallbackVideo
    }
}

// ── Formatting helpers ──────────────────────────────────────────────────────

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val m = seconds / 60
    val s = seconds % 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s) else "%d:%02d".format(m, s)
}

fun formatCount(n: Long?): String? {
    if (n == null || n <= 0) return null
    return when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }
}

// Re-export for staggered sections without extra imports at call site.
@Composable
fun DownloaderSection(index: Int, content: @Composable () -> Unit) {
    StaggeredEntrance(index = index) { content() }
}
