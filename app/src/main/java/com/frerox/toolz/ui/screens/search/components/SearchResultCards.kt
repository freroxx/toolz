/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.search.SearchCategory
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.ToolzExpressiveButton

// ══════════════════════════════════════════════════════════
//  SOURCE ACCENT COLOURS
// ══════════════════════════════════════════════════════════

private val KnownSourceColors: Map<String, Color> = mapOf(
    "GOOGLE" to Color(0xFF4285F4),
    "DDG" to Color(0xFFDE5833),
    "DUCKDUCKGO" to Color(0xFFDE5833),
    "BRAVE" to Color(0xFFFF6000),
    "BING" to Color(0xFF008272),
    "ECOSIA" to Color(0xFF2B8A2B),
    "SWISSCOWS" to Color(0xFFD9253E),
    "STARTPAGE" to Color(0xFF3F4EAE),
    "YAHOO" to Color(0xFF5F01D1),
    "QWANT" to Color(0xFFF75708),
    "MARGINALIA" to Color(0xFF4C7A2E),
    "META" to Color(0xFF6750A4),
)

private val KnownSourceLabels: Map<String, String> = mapOf(
    "GOOGLE" to "Google",
    "DDG" to "DuckDuckGo",
    "DUCKDUCKGO" to "DuckDuckGo",
    "BRAVE" to "Brave",
    "BING" to "Bing",
    "ECOSIA" to "Ecosia",
    "SWISSCOWS" to "Swisscows",
    "STARTPAGE" to "Startpage",
    "WEB" to "Web",
    "YAHOO" to "Yahoo",
    "QWANT" to "Qwant",
    "MARGINALIA" to "Marginalia",
    "META" to "Meta",
    "DUCKDUCKGO" to "DuckDuckGo",
)

@Composable
internal fun sourceAccentColor(source: String): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val alpha = if (isDark) 0.85f else 1f
    val base = KnownSourceColors[source.uppercase()] ?: MaterialTheme.colorScheme.primary
    return base.copy(alpha = alpha)
}

internal fun sourceLabel(source: String): String =
    KnownSourceLabels[source.uppercase()] ?: source.take(10).replaceFirstChar { it.uppercase() }

// ══════════════════════════════════════════════════════════
//  SEARCH RESULT CARD — standard web result
// ══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val accentColor = sourceAccentColor(result.source)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "cardScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isPressed) 0.dp else 1.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            // Left accent bar — engine colour gradient
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(listOf(accentColor, accentColor.copy(alpha = 0.3f))),
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Domain + favicon + engine badge row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PrivacyFaviconImage(url = result.url, size = 16.dp)

                    Text(
                        text = result.displayUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (result.engines.size >= 2) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                // Compact consensus badge: "✓3 · Yahoo" — shows the count
                                // plus the primary (highest-ranked) engine so the user can
                                // see WHICH engine without opening the long-press sheet.
                                text = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_engines_count, result.engines.size, sourceLabel(result.source)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = sourceLabel(result.source),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }                }

                if (!result.breadcrumb.isNullOrBlank()) {
                    Text(
                        text = result.breadcrumb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )

                if (!result.date.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Schedule, null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                        Text(
                            text = result.date,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }

                if (result.snippet.isNotBlank()) {
                    Text(
                        text = result.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  SYNTHETIC CATEGORY CARD (Images / Videos "open in browser")
// ══════════════════════════════════════════════════════════

/**
 * Full-width card shown for Image and Video category results whose engine
 * renders results client-side in JavaScript. Rather than attempt to scrape
 * those results, we surface a direct "open in browser" affordance pointing
 * at the engine's native search UI.
 */
@Composable
fun SyntheticCategoryCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = sourceAccentColor(result.source)
    val isVideo = result.url.contains("video") || result.url.contains("vid=")

    ExpressiveCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        containerColor = accentColor.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        elevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = accentColor.copy(alpha = 0.18f), modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isVideo) Icons.Rounded.OndemandVideo else Icons.Rounded.Image,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(result.displayUrl, style = MaterialTheme.typography.labelSmall, color = accentColor.copy(alpha = 0.8f))
                }
            }

            Text(result.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)

            ToolzExpressiveButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(
                    com.frerox.toolz.R.string.st_SearchScreen_ws_open_in_browser,
                    if (isVideo) stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_cat_videos)
                    else stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_cat_images),
                ))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  NATIVE IMAGE CARD
// ══════════════════════════════════════════════════════════

@Composable
fun NativeImageCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val accent = sourceAccentColor(result.source)
    Surface(
        modifier = modifier.fillMaxWidth().height(200.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { lc ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        lc()
                    }
                },
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = result.imageUrl ?: result.url,
                    contentDescription = result.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(Icons.Rounded.BrokenImage),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))),
                )
                if (onDownload != null) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Rounded.Download, stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_download_image_desc),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                // Engine tag intentionally removed here: it shared the TopEnd corner
                // with the download button. Engine attribution stays available via
                // the long-press "Found on" sheet.
            }
            Column(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (result.title.isNotBlank()) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }
                 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    PrivacyFaviconImage(url = result.url, size = 14.dp)
                    Text(
                        text = result.displayUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  NATIVE VIDEO CARD
// ══════════════════════════════════════════════════════════

@Composable
fun NativeVideoCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    videoId: String? = null,
    onClosePlayer: (() -> Unit)? = null,
) {
    val accentColor = sourceAccentColor(result.source)
    // Prefer native ExoPlayer to avoid WebView black screen (no audio/video) — WebView is fallback
    var useNative by remember(videoId, isPlaying) { mutableStateOf(true) }
    var nativeUrl by remember(videoId) { mutableStateOf<String?>(null) }
    var nativeLoading by remember(videoId) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(useNative, videoId, isPlaying) {
        if (isPlaying && useNative && nativeUrl == null && videoId != null && !nativeLoading) {
            nativeLoading = true
            val extracted = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                com.frerox.toolz.util.YouTubeStreamExtractor.extractYouTubeStreamUrl(videoId, 720, context)
            }
            nativeUrl = extracted
            nativeLoading = false
            if (nativeUrl == null) useNative = false
        }
    }
    // If native fails quickly, WebView will be shown as fallback; if WebView black screen detected via hasError153, it will auto offer native again (circular fallback handled by YouTubeInlinePlayer)
    Surface(
        onClick = if (isPlaying) { {} } else onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        shape = LargeExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(210.dp).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying && videoId != null && onClosePlayer != null) {
                    if (useNative && nativeUrl != null) {
                        YouTubeNativePlayer(
                            streamUrl = nativeUrl!!,
                            thumbnailUrl = result.imageUrl,
                            modifier = Modifier.fillMaxSize(),
                            onClose = { useNative=false; nativeUrl=null; onClosePlayer() },
                            onError = { useNative = false; nativeUrl = null }
                        )
                    } else if (useNative && nativeLoading) {
                        YouTubeNativeLoading(thumbnailUrl = result.imageUrl, modifier = Modifier.fillMaxSize(), onClose = { useNative=false; nativeLoading=false; onClosePlayer() })
                    } else {
                        YouTubeInlinePlayer(
                            videoId = videoId,
                            modifier = Modifier.fillMaxSize(),
                            onClose = { useNative=false; nativeUrl=null; onClosePlayer() },
                            thumbnailUrl = result.imageUrl,
                            onTryNative = { useNative = true },
                            onOpenInBrowser = { try { val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId")); context.startActivity(i) } catch (_: Exception){} },
                        )
                    }
                } else {
                    if (!result.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = result.imageUrl,
                            contentDescription = result.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = rememberVectorPainter(Icons.Rounded.OndemandVideo),
                        )
                    }
                    // Bottom gradient scrim for play button / date badge contrast on bright thumbnails
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))
                            )
                    )
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.65f), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_play), tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    // Inline download action — YouTube videos get the quality sheet.
                    if (onDownload != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                        ) {
                            IconButton(onClick = onDownload, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Rounded.Download, stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_download_video_desc),
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                    if (!result.date.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        ) {
                            Text(
                                text = result.date,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(result.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.snippet.isNotBlank()) {
                    Text(
                        result.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(result.displayUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(8.dp), color = accentColor.copy(alpha = 0.15f)) {
                        Text(
                            text = sourceLabel(result.source).ifBlank { stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_video_fallback) },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════
//  PROVIDER UNAVAILABLE
// ══════════════════════════════════════════════════════════

@Composable
fun ProviderUnavailableCard(
    category: SearchCategory,
    onSearchAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = LargeExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (category == SearchCategory.VIDEOS) Icons.Rounded.VideocamOff else Icons.Rounded.HideImage,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = if (category == SearchCategory.VIDEOS) stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_no_videos) else stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_no_images),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_no_media),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onSearchAll, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_search_all))
            }
        }
    }
}
