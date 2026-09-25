/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.media.downloader

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator
import com.frerox.toolz.ui.components.ExpressiveStatePill
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.ToolzWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback

// ── Platform selector ───────────────────────────────────────────────────────

private data class PlatformMeta(
    val platform: MediaDownloaderRepository.Platform,
    val label: String,
    val dot: Color,
)

private val platformMetas = listOf(
    PlatformMeta(MediaDownloaderRepository.Platform.YOUTUBE, "YouTube", Color(0xFFFF0000)),
    PlatformMeta(MediaDownloaderRepository.Platform.TIKTOK, "TikTok", Color(0xFF000000)),
    PlatformMeta(MediaDownloaderRepository.Platform.INSTAGRAM, "Reels", Color(0xFFDD2A7B)),
)

/** Display names in fixed order, restricted to the enabled set. */
fun platformNames(
    enabled: Set<MediaDownloaderRepository.Platform>,
    separator: String = " / ",
): String {
    val ordered = listOf(
        MediaDownloaderRepository.Platform.YOUTUBE to "YouTube",
        MediaDownloaderRepository.Platform.TIKTOK to "TikTok",
        MediaDownloaderRepository.Platform.INSTAGRAM to "Instagram",
    ).filter { (platform, _) -> platform in enabled }.map { (_, label) -> label }
    return if (ordered.isEmpty()) {
        listOf("YouTube", "TikTok", "Instagram").joinToString(separator)
    } else {
        ordered.joinToString(separator)
    }
}

/** Adaptive input hint, e.g. "Paste TikTok / Instagram link…" when YouTube is off. */
fun platformHint(enabled: Set<MediaDownloaderRepository.Platform>): String =
    "Paste ${platformNames(enabled)} link…"

/** Prefers the locally detected platform, falls back to the server-reported one. */
fun resolvePlatform(
    detected: MediaDownloaderRepository.Platform?,
    server: String?,
): MediaDownloaderRepository.Platform? {
    if (detected != null) return detected
    return when (server?.lowercase()) {
        "youtube" -> MediaDownloaderRepository.Platform.YOUTUBE
        "tiktok" -> MediaDownloaderRepository.Platform.TIKTOK
        "instagram" -> MediaDownloaderRepository.Platform.INSTAGRAM
        else -> null
    }
}

/** Vertical artwork (TikTok / Reels) gets a portrait thumbnail instead of 16:9. */
fun isVerticalVideo(platform: MediaDownloaderRepository.Platform?): Boolean =
    platform == MediaDownloaderRepository.Platform.TIKTOK ||
        platform == MediaDownloaderRepository.Platform.INSTAGRAM

@Composable
fun PlatformStatusRow(
    enabled: Set<MediaDownloaderRepository.Platform>,
    detected: MediaDownloaderRepository.Platform?,
    onToggle: (MediaDownloaderRepository.Platform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.st_MediaDownloader_Platforms),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.st_MediaDownloader_PlatformsHint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            platformMetas.forEach { meta ->
                val isEnabled = meta.platform in enabled
                // TikTok brand dot is pure black — unreadable on dark theme, so use
                // onSurface there while keeping brand colors for the other two.
                val dot = if (meta.platform == MediaDownloaderRepository.Platform.TIKTOK) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    meta.dot
                }
                ExpressiveFilterChip(
                    selected = isEnabled,
                    onClick = { onToggle(meta.platform) },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    label = {
                        Text(
                            meta.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isEnabled) dot else dot.copy(alpha = 0.3f),
                                    CircleShape,
                                ),
                        )
                    },
                )
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
    // Always-present radio: color crossfades instead of mounting a check badge,
    // so selecting never shifts the row content sideways.
    val radioColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(200),
        label = "qualityRadio",
    )
    Surface(
        onClick = {
            haptic.tick()
            onSelect()
        },
        modifier = modifier.fillMaxWidth(),
        shape = SmallExpressiveShape,
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
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Icon(
                    if (option.isAudio) Icons.Rounded.MusicNote else Icons.Rounded.HighQuality,
                    null,
                    modifier = Modifier.padding(6.dp).size(20.dp),
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
                    fontWeight = FontWeight.SemiBold,
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .border(2.dp, radioColor, CircleShape),
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(radioColor, CircleShape),
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
        shape = MediumExpressiveShape,
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

// ── Loading skeleton (shimmer, layout-matched to ResultCard) ────────────────

@Composable
private fun rememberShimmerBrush(delayMs: Int): Brush {
    val transition = rememberInfiniteTransition(label = "dlShimmer$delayMs")
    val progress by transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, delayMillis = delayMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dlShimmerProgress$delayMs",
    )
    return Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        startX = progress * 900f,
        endX = progress * 900f + 500f,
    )
}

@Composable
fun FetchingSkeletonCard(
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = {},
        enabled = false,
        modifier = modifier,
        shape = LargeExpressiveShape,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section label placeholder ("Preview").
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(rememberShimmerBrush(0)),
            )
            // Thumbnail: 16:9 for YouTube, fixed portrait height for TikTok/Reels.
            if (vertical) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(rememberShimmerBrush(40)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(rememberShimmerBrush(40)),
                )
            }
            // Title (2 lines) + uploader line, same widths as real text.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(17.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(rememberShimmerBrush(80)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(17.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(rememberShimmerBrush(120)),
            )
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(rememberShimmerBrush(160)),
            )
            // Stats row placeholders (views + likes pills).
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(rememberShimmerBrush(200)),
                )
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(rememberShimmerBrush(240)),
                )
            }
            // Section label placeholder ("Choose quality").
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(rememberShimmerBrush(280)),
            )
            // Quality rows, mirroring QualityOptionRow: 64dp, icon tile + 2 bars + radio.
            repeat(4) { i ->
                val brush = rememberShimmerBrush(320 + i * 40)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SmallExpressiveShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 14.dp)
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(brush),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(15.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(brush),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(brush),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(brush),
                    )
                }
            }
            // Download CTA placeholder.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(rememberShimmerBrush(640)),
            )
        }
    }
}

// ── Empty state hero ────────────────────────────────────────────────────────

@Composable
fun EmptyStateHero(
    hint: String? = null,
    platforms: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MediumExpressiveShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                null,
                modifier = Modifier.padding(18.dp).size(32.dp),
            )
        }
        Text(
            stringResource(R.string.st_MediaDownloader_EmptyTitle),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            hint ?: stringResource(R.string.st_MediaDownloader_Empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (!platforms.isNullOrBlank()) {
            Text(
                platforms,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
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
        shape = MediumExpressiveShape,
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
                } else if (openable) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            Icons.Rounded.OpenInNew,
                            stringResource(R.string.st_MediaDownloader_Open),
                            modifier = Modifier.padding(9.dp).size(17.dp),
                        )
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
        Box(
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
                            Box(
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
                            Box(
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
            icon = Icons.Rounded.CloudDownload,
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
            color = MaterialTheme.colorScheme.tertiary,
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

// ── History entry card ──────────────────────────────────────────────────────

@Composable
fun HistoryCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberToolzHapticFeedback()
    ExpressiveCard(
        onClick = {
            haptic.click()
            onClick()
        },
        modifier = modifier,
        shape = MediumExpressiveShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.History,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.st_MediaDownloader_History),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── History bottom sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    history: List<MediaDownloaderViewModel.HistoryEntry>,
    downloads: List<WorkInfo>,
    labels: Map<String, MediaDownloaderViewModel.DownloadLabel>,
    onLinkClick: (MediaDownloaderViewModel.HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    downloadProgress: (WorkInfo) -> Float,
    downloadFileUri: (WorkInfo) -> String?,
    onCancelDownload: (java.util.UUID) -> Unit,
    onOpenDownload: (WorkInfo, MediaDownloaderViewModel.DownloadLabel?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 8.dp)
                    .size(48.dp, 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.st_MediaDownloader_History),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    ToolzExpressiveTextButton(onClick = onClearHistory) {
                        Text(
                            stringResource(R.string.st_MediaDownloader_ClearAll),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .fadingEdges(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (history.isNotEmpty()) {
                    item(key = "links_header") {
                        Text(
                            stringResource(R.string.st_MediaDownloader_Links),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(history, key = { it.url }) { entry ->
                        HistoryLinkRow(entry = entry, onClick = onLinkClick)
                    }
                }
                if (downloads.isNotEmpty()) {
                    item(key = "downloads_header") {
                        Text(
                            stringResource(R.string.st_MediaDownloader_Active),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(downloads, key = { it.id }) { info ->
                        val label = labels[info.id.toString()]
                        MediaDownloadRow(
                            info = info,
                            label = label,
                            progress = downloadProgress(info),
                            fileUri = downloadFileUri(info),
                            onCancel = { onCancelDownload(info.id) },
                            onOpen = { onOpenDownload(info, label) },
                        )
                    }
                }
                if (history.isEmpty() && downloads.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.st_MediaDownloader_NoHistory),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryLinkRow(
    entry: MediaDownloaderViewModel.HistoryEntry,
    onClick: (MediaDownloaderViewModel.HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberToolzHapticFeedback()
    Surface(
        onClick = {
            haptic.click()
            onClick(entry)
        },
        modifier = modifier.fillMaxWidth(),
        shape = SmallExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (!entry.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = entry.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Movie,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        historyPlatformLabel(entry.platform),
                        formatHistoryDate(entry.timestampMs),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun historyPlatformLabel(platform: MediaDownloaderRepository.Platform?): String? =
    when (platform) {
        MediaDownloaderRepository.Platform.YOUTUBE -> "YouTube"
        MediaDownloaderRepository.Platform.TIKTOK -> "TikTok"
        MediaDownloaderRepository.Platform.INSTAGRAM -> "Reels"
        null -> null
    }

private fun formatHistoryDate(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    if (diff < 0) return ""
    val minutes = diff / 60_000
    if (minutes < 1) return "Just now"
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    if (days < 7) return "${days}d ago"
    return try {
        java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            .format(java.util.Date(timestampMs))
    } catch (_: Exception) {
        ""
    }.trim().ifBlank { "" }
}
