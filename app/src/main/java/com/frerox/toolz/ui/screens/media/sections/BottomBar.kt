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

package com.frerox.toolz.ui.screens.media.sections

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.OptIn as AnnotationOptIn
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.media.ai.*
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Bar (MiniPlayer + TabRow)
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@Composable
fun ScreenBottomBar(
    state: MusicUiState,
    aiState: NowPlayingAiUiState,
    playbackPositionFlow: StateFlow<Long>,
    duration: Long,
    currentTab: Int,
    downloadCount: Int,
    avgDownloadProgress: Float,
    onTabChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onLongClickMiniPlayer: () -> Unit,
    onExpand: () -> Unit,
    isOnline: Boolean,
    isResolving: Boolean = false
) {
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()

    // NOTE: no animateContentSize here on purpose. The outer Column holds both
    // the MiniPlayer (which grows when expanded to show lyrics) and the PillTabRow
    // dock below it. Animating this container's size with a different spec than the
    // inner lyrics expandVertically made the dock dip downwards first and then rise
    // with the mini player. Without it, the dock stays pinned to the bottom and the
    // mini player grows upwards — the inner AnimatedVisibility already animates.
    Column(modifier = Modifier.navigationBarsPadding()) {
        // MiniPlayer
        AnimatedVisibility(
            visible = state.currentTrack != null || isResolving,
            enter = fadeIn(tween(300)) + slideInVertically(
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                initialOffsetY = { it }
            ),
            exit = fadeOut(tween(200)) + slideOutVertically(
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                targetOffsetY = { it }
            )
        ) {
            val trackToDisplay = state.currentTrack ?: MusicTrack(
                uri = "loading",
                title = if (isResolving) stringResource(R.string.st_MusicPlayerScreen_rs1) else stringResource(R.string.st_MusicPlayerScreen_ls2),
                artist = "Catalog",
                album = "Online",
                duration = 0
            )

            MiniPlayer(
                track = trackToDisplay,
                isPlaying = state.isPlaying,
                progressFlow = playbackPositionFlow,
                duration = duration,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onClick = onOpenFullPlayer,
                onLongClick = onLongClickMiniPlayer,
                onExpand = onExpand,
                isExpanded = aiState.isExpandedPill,
                lyricsState = aiState.lyricsState,
                rotationEnabled = state.rotationEnabled,
                artShape = state.artShape,
                downloadCount = downloadCount,
                avgDownloadProgress = avgDownloadProgress,
                isResolving = isResolving
            )
        }

        // M3 ExpressiveNavigationBar
        val tabItems = remember(state.isOnline, downloadCount, state.karaokeEnabled) {
            listOfNotNull(
                "Tracks" to Icons.Rounded.MusicNote,
                "Library" to Icons.AutoMirrored.Rounded.PlaylistPlay,
                if (state.karaokeEnabled) "Karaoke" to Icons.Rounded.MicExternalOn else null,
                if (state.isOnline) ("Catalog" to (if (downloadCount > 0) Icons.Rounded.CloudDownload else Icons.Rounded.Cloud)) else null
            )
        }

        PillTabRow(
            tabItems = tabItems,
            selectedTab = currentTab.coerceAtMost(tabItems.size - 1),
            onTabChange = {
                if (it < tabItems.size) onTabChange(it)
            }
        )
    }
}

@Composable
private fun PillTabRow(
    tabItems: List<Pair<String, ImageVector>>,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabItems.forEachIndexed { index, (label, icon) ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(if (selected) 1.5f else 1f)
                        .fillMaxHeight()
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onTabChange(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            icon, null, modifier = Modifier.size(24.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(200)) + expandHorizontally(tween(250)),
                            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200))
                        ) {
                            Text(
                                label,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: MusicTrack,
    isPlaying: Boolean,
    progressFlow: StateFlow<Long>,
    duration: Long,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onExpand: () -> Unit = {},
    isExpanded: Boolean = false,
    lyricsState: AiLyricsState? = null,
    rotationEnabled: Boolean = true,
    artShape: String = "SQUARE",
    downloadCount: Int = 0,
    avgDownloadProgress: Float = 0f,
    isResolving: Boolean = false
) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val isDark = LocalIsDarkTheme.current
    val dynamicColors = rememberDynamicColors(track.thumbnailUri)
    val targetProgress = if (duration > 0) progress.toFloat() / duration else 0f

    val pauseCd = stringResource(R.string.st_MusicPlayerScreen_pause68)
    val playCd = stringResource(R.string.st_MusicPlayerScreen_play69)
    val enjoyMusicText = stringResource(R.string.st_MusicPlayerScreen_etm87)

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        // No-bounce spring: this drives the snap-back-to-center after every
        // swipe, so any overshoot reads as a jiggle on a UI element the user
        // sees constantly. DampingRatioNoBouncy settles cleanly in one motion.
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "miniSwipeOffset"
    )

    // Progress animation — skip in performance mode
    val animatedProgress by if (performanceMode) {
        remember(targetProgress) { mutableFloatStateOf(targetProgress) }
    } else {
        animateFloatAsState(targetProgress, tween(450, easing = LinearOutSlowInEasing), label = "miniProg")
    }

    // Shared spec for everything that morphs on expand/collapse — corner radius,
    // elevation, and the lyrics panel's own expandVertically/shrinkVertically all use
    // these same numbers so the whole transition settles together as one soft motion
    // instead of several independently-timed animations drifting apart mid-transition.
    val miniPlayerDamping = Spring.DampingRatioLowBouncy
    val miniPlayerStiffness = Spring.StiffnessMediumLow

    // Corner radius morphs between collapsed/expanded
    val cornerRadius by animateDpAsState(
        if (isExpanded) 24.dp else 40.dp,
        if (performanceMode) snap() else spring(dampingRatio = miniPlayerDamping, stiffness = miniPlayerStiffness),
        label = "miniCorner"
    )

    // Art rotation (only when expanded + playing)
    val infiniteTransition = rememberInfiniteTransition(label = "miniArt")
    val artRotation by if (performanceMode) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
            label = "artRot"
        )
    }

    // ── Crash-safe lyric resolution ───────────────────────────────────────────
    val currentLyricIndex = remember(progress, lyricsState) {
        val lyrics = lyricsState?.syncedLyrics
        when {
            lyrics.isNullOrEmpty() -> -1
            else -> {
                val idx = lyrics.indexOfLast { it.timeMs <= progress }
                if (idx < 0) -1 else idx
            }
        }
    }
    val currentLyric: String? = remember(currentLyricIndex, lyricsState) {
        val lyrics = lyricsState?.syncedLyrics
        if (currentLyricIndex >= 0 && !lyrics.isNullOrEmpty() && currentLyricIndex < lyrics.size)
            lyrics[currentLyricIndex].content
        else null
    }

    // Elevation animation
    val elevation by animateDpAsState(
        if (performanceMode) 4.dp else if (isExpanded) 16.dp else 8.dp,
        if (performanceMode) snap() else spring(dampingRatio = miniPlayerDamping, stiffness = miniPlayerStiffness),
        label = "miniElev"
    )

    // Font mapping for lyrics
    val fontFamily = remember(lyricsState?.fontFamily) {
        when(lyricsState?.fontFamily) {
            LyricsFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
            LyricsFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
            LyricsFont.CURSIVE -> androidx.compose.ui.text.font.FontFamily.Cursive
            LyricsFont.DISPLAY -> androidx.compose.ui.text.font.FontFamily.SansSerif
            LyricsFont.HANDWRITING -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.Default
        }
    }

    // NOTE: this outer wrapper used to be a Surface(color = Color.Transparent).
    // Material3's Surface always clips its content to `shape`, which defaults
    // to RectangleShape when unset. So even fully transparent, it was
    // silently clipping everything inside — including the inner Surface's own
    // rounded corners — to a sharp rectangle sitting flush against the
    // rounded pill's bounding box. Most of the pill never touched that
    // boundary, but the two bottom corners' curve met it exactly at the arc,
    // so the outer square clip sliced across the antialiased edge of the
    // inner rounded corner and squared it off. A plain Box has no shape/clip
    // of its own, so the inner Surface below is now the only thing defining
    // this composable's silhouette, and the corners stay smoothly rounded
    // through the whole expand/collapse animation.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .graphicsLayer {
                // Horizontal-only movement: no rotationZ. The previous
                // rotationZ = animatedOffsetX / 20f tilted the pill like a
                // card being flicked away, which read as a diagonal/off-axis
                // motion rather than a clean horizontal swipe. Translation is
                // the only transform now, so the pill slides straight left
                // and right and nothing else.
                translationX = animatedOffsetX
                // Fade starts later and finishes closer to the flick
                // threshold (150) instead of fading fully by 600px of drag,
                // so the pill stays visible through the part of the gesture
                // where the user is actually deciding whether to commit,
                // and only dissolves near the point where it's about to
                // trigger next/previous.
                alpha = 1f - (kotlin.math.abs(animatedOffsetX) / 260f).coerceIn(0f, 0.45f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 150) onPrevious()
                        else if (offsetX < -150) onNext()
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Rubber-band resistance: raw finger movement is damped
                        // as offsetX grows, so the pill never tracks the finger
                        // 1:1. This is what makes the gesture feel smooth and
                        // controlled rather than a direct, sometimes-jerky
                        // finger-follow — the further you drag, the more it
                        // resists, like pulling against a soft spring.
                        val resistance = 1f - (kotlin.math.abs(offsetX) / 900f).coerceIn(0f, 0.6f)
                        offsetX += dragAmount * resistance
                    }
                )
            }
    ) {
        val appSurface = MaterialTheme.colorScheme.surface
        val appSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant

        val lighterSurface = androidx.compose.ui.graphics.lerp(
            appSurfaceVariant,
            dynamicColors.primary,
            if (isDark) 0.15f else 0.08f
        )
        val darkerSurface = androidx.compose.ui.graphics.lerp(
            appSurface,
            dynamicColors.primary,
            if (isDark) 0.08f else 0.04f
        )

        val miniPlayerShape = RoundedCornerShape(cornerRadius)
        Box(
            modifier = Modifier
                // Shadow first, BEFORE clip — shadow needs to paint outside the
                // shape's bounds (that's the whole point of a shadow), so it must
                // not be clipped. clip = false here is what actually fixes the
                // dark square scrim: Surface's shadowElevation draws its shadow as
                // a separate rectangular graphicsLayer pass that wasn't reliably
                // re-clipping to `shape` on every frame while cornerRadius was
                // mid-animation, so a faint dark rectangle leaked out from behind
                // the rounded pill. Modifier.shadow always re-evaluates against
                // the live `shape` instance we pass it, so it stays in sync with
                // the animated corner radius every frame.
                .shadow(elevation, miniPlayerShape, clip = false)
                .clip(miniPlayerShape)
                .background(Brush.verticalGradient(listOf(lighterSurface.copy(alpha = 0.9f), darkerSurface.copy(alpha = 0.9f))))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), miniPlayerShape)
        ) {
            Box(
                modifier = Modifier
                    // Re-clip here too: combinedClickable's ripple draws
                    // within this Box's own bounds, and without a clip on
                    // this level the ripple can bleed past the rounded
                    // corners even though the parent Box above is already
                    // clipped — each Box only clips its own drawing, not its
                    // children's independently-drawn effects like ripples.
                    .clip(miniPlayerShape)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
            ) {
                // ── Background progress wash — clipped to parent ───────────
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .fillMaxWidth(animatedProgress)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    dynamicColors.primary.copy(alpha = if (isExpanded) 0.12f else 0.15f),
                                    dynamicColors.primary.copy(alpha = 0.02f)
                                )
                            )
                        )
                )

                Column {
                    // ── Always-visible compact row ────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val finalArtShape = when (artShape) {
                        "CIRCLE" -> CircleShape
                        "SQUIRCLE" -> RoundedCornerShape(22.dp)
                        else -> RoundedCornerShape(16.dp)
                    }

                    // Consolidated Thumbnail with Pulse and Download/Resolving Indicator
                    val infiniteTransitionPulse = rememberInfiniteTransition(label = "playerPulse")
                    val pulseScalePlayer by if ((downloadCount > 0 || isResolving) && !performanceMode) {
                        infiniteTransitionPulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "playerPulseScale"
                        )
                    } else {
                        remember { mutableFloatStateOf(1f) }
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            // Was Modifier.scale(pulseScalePlayer): a top-level .scale()
                            // reads its state at the modifier-chain level, which forces
                            // every modifier after it in the chain (shadow, clip, border)
                            // to re-evaluate on every pulse tick instead of just being
                            // composited as a transformed layer. graphicsLayer{} reads
                            // the value at draw time instead, so the pulse (which runs
                            // continuously for the whole download/resolve duration, not
                            // just once) no longer forces shadow/clip recompute 60-120
                            // times a second.
                            .graphicsLayer {
                                scaleX = pulseScalePlayer
                                scaleY = pulseScalePlayer
                            }
                            .shadow(if (performanceMode) 2.dp else 6.dp, finalArtShape)
                            .clip(finalArtShape)
                            .then(
                                if (downloadCount > 0 || isResolving) Modifier.border(2.dp, dynamicColors.primary.copy(alpha = 0.6f), finalArtShape)
                                else Modifier
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = if (rotationEnabled && isPlaying && !performanceMode) artRotation else 0f
                                }
                        ) {
                            AnimatedContent(
                                targetState = track.uri to track.thumbnailUri,
                                transitionSpec = {
                                    if (performanceMode) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        fadeIn(tween(400)) + scaleIn(initialScale = 0.85f) togetherWith
                                                fadeOut(tween(400)) + scaleOut(targetScale = 0.85f)
                                    }
                                },
                                label = "artTransition"
                            ) { pair ->
                                AlbumArtImage(
                                    url = pair.second,
                                    seed = track.uri,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    iconSize = 24.dp
                                )
                            }
                        }

                        if (isResolving && !performanceMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }

                        if (downloadCount > 0 && !isResolving) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { avgDownloadProgress },
                                    modifier = Modifier.size(42.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    strokeCap = StrokeCap.Round
                                )
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    // Consolidated Track Info with smooth transitions
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        AnimatedContent(
                            // Key on semantic display identity (uri + title + artist), NOT the
                            // whole MusicTrack instance: the 45s library auto-refresh re-emits
                            // fresh MusicTrack objects with identical fields, which used to
                            // replay this swap animation every refresh. Identical content must
                            // not animate — only a real track change does.
                            targetState = "${track.uri}|${track.title}|${track.artist}",
                            transitionSpec = {
                                if (performanceMode) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (slideInVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(400))) togetherWith
                                            (slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { -it / 3 } + fadeOut(tween(300)))
                                }.using(SizeTransform(clip = false))
                            },
                            label = "trackInfoTransition"
                        ) { _ ->
                            Column {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isDark) Color(0xFFEEEEEE) else Color(0xFF111111)
                                )
                                val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
                                Text(
                                    text = artistText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Expand chevron
                    val chevronRotF by animateFloatAsState(
                        if (isExpanded) 0f else 180f,
                        if (performanceMode) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        label = "chevronF"
                    )
                    ToolzExpressiveIconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = dynamicColors.primary.copy(alpha = if (isExpanded) 0.25f else 0.18f)),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Rounded.ExpandLess,
                            null,
                            tint = dynamicColors.primary,
                            modifier = Modifier.size(26.dp).rotate(if (performanceMode) (if (isExpanded) 0f else 180f) else chevronRotF)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Play/Pause
                    ToolzExpressiveButton(
                        onClick = if (isResolving) ({}) else onTogglePlay,
                        modifier = Modifier.size(54.dp).alpha(if (isResolving) 0.6f else 1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = if (isDark) Color(0xFF111111) else Color.White
                        )
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = if (isDark) Color(0xFF111111) else Color.White,
                                strokeWidth = 3.dp
                            )
                        } else if (performanceMode) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            Crossfade(targetState = isPlaying, animationSpec = tween(180), label = "ppMini") { playing ->
                                // Same real morph as the full player (scale + fade), so play/pause
                                // reads as one shape transforming rather than two icons dissolving.
                                AnimatedContent(
                                    targetState = playing,
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.5f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn(tween(140)))
                                            .togetherWith(
                                                scaleOut(targetScale = 0.5f, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh)) + fadeOut(tween(100))
                                            )
                                    },
                                    label = "ppMiniMorph"
                                ) { pl ->
                                    Icon(if (pl) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(30.dp))
                                }
                            }
                        }
                    }
                }

                // ── Expandable lyrics + progress section ──────────────────────
                // Expands upwards: the dock below stays pinned, the compact row above
                // is pushed up. expandFrom/shrinkTowards = Bottom keeps the bottom
                // edge (adjacent to the dock) fixed. Top-anchored expansion did the
                // opposite — it pushed the dock down first, then the whole bar rose.
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = if (performanceMode) fadeIn() + expandVertically() else
                        fadeIn(tween(280)) + expandVertically(
                            spring(dampingRatio = miniPlayerDamping, stiffness = miniPlayerStiffness),
                            expandFrom = Alignment.Bottom
                        ),
                    exit = if (performanceMode) fadeOut() + shrinkVertically() else
                        fadeOut(tween(200)) + shrinkVertically(
                            spring(dampingRatio = miniPlayerDamping, stiffness = miniPlayerStiffness),
                            shrinkTowards = Alignment.Bottom
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                            .background(
                                color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Thin separator
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(0.35f).alpha(if (performanceMode) 0.2f else 0.12f)
                        )
                        Spacer(Modifier.height(14.dp))

                        // Lyric line — fixed 64dp height so the pill never layout-shifts per line
                        Box(
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val lyricColor = if (isDark) Color(0xFFDDDDDD) else Color(0xFF333333)

                            if (performanceMode) {
                                // Performance mode: instant text swap, no animation
                                if (currentLyric != null) {
                                    Text(
                                        currentLyric,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center,
                                        fontFamily = fontFamily,
                                        color = lyricColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            null,
                                            modifier = Modifier.size(20.dp).alpha(0.35f),
                                            tint = lyricColor
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            stringResource(R.string.st_MusicPlayerScreen_etm87),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = lyricColor.copy(alpha = 0.35f),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = fontFamily,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                AnimatedContent(
                                    targetState = currentLyric,
                                    transitionSpec = {
                                        (fadeIn(tween(350)) + slideInVertically(
                                            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
                                        ) { it / 2 })
                                            .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
                                    },
                                    label = "miniLyric"
                                ) { lyric ->
                                    if (lyric != null) {
                                        Text(
                                            lyric,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            fontFamily = fontFamily,
                                            color = lyricColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.MusicNote,
                                                null,
                                                modifier = Modifier.size(20.dp).alpha(0.35f),
                                                tint = lyricColor
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Enjoy the music",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = lyricColor.copy(alpha = 0.35f),
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = fontFamily,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Progress bar row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                formatDuration(progress),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color(0xFFE5E5E5).copy(alpha = 0.7f) else Color(0xFF222222).copy(alpha = 0.7f)
                            )
                            com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                                color = dynamicColors.primary,
                                trackColor = dynamicColors.primary.copy(alpha = 0.15f),
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                formatDuration(duration),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color(0xFFE5E5E5).copy(alpha = 0.4f) else Color(0xFF222222).copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Thin progress line at bottom — collapsed state only
            AnimatedVisibility(
                visible = !isExpanded,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (performanceMode) EnterTransition.None else fadeIn(tween(130)),
                exit = if (performanceMode) ExitTransition.None else fadeOut(tween(90))
            ) {
                com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).height(4.dp),
                    color = dynamicColors.primary,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
}

// Alias / alternate name required by task
@Composable
fun MusicBottomBar(
    state: MusicUiState,
    aiState: NowPlayingAiUiState,
    playbackPositionFlow: StateFlow<Long>,
    duration: Long,
    currentTab: Int,
    downloadCount: Int,
    avgDownloadProgress: Float,
    onTabChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onLongClickMiniPlayer: () -> Unit,
    onExpand: () -> Unit,
    isOnline: Boolean,
    isResolving: Boolean = false
) = ScreenBottomBar(
    state = state,
    aiState = aiState,
    playbackPositionFlow = playbackPositionFlow,
    duration = duration,
    currentTab = currentTab,
    downloadCount = downloadCount,
    avgDownloadProgress = avgDownloadProgress,
    onTabChange = onTabChange,
    onTogglePlay = onTogglePlay,
    onNext = onNext,
    onPrevious = onPrevious,
    onOpenFullPlayer = onOpenFullPlayer,
    onLongClickMiniPlayer = onLongClickMiniPlayer,
    onExpand = onExpand,
    isOnline = isOnline,
    isResolving = isResolving
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
