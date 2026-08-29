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
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import androidx.annotation.OptIn as AnnotationOptIn
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveSlider
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.DragDropState
import com.frerox.toolz.ui.components.rememberDragDropState
import com.frerox.toolz.ui.components.dragDropColumn
import com.frerox.toolz.ui.components.dragDropItem
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.components.KaraokeMicIcon
import com.frerox.toolz.ui.screens.media.*
import com.frerox.toolz.ui.screens.media.ai.*
import com.frerox.toolz.ui.screens.media.catalog.*
import com.frerox.toolz.ui.screens.media.components.ArtSettingsSheet
import com.frerox.toolz.ui.screens.media.components.TagEditorSheet
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*
import androidx.activity.compose.BackHandler

// ─────────────────────────────────────────────────────────────────────────────
// Full Player View
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerView(
    state: MusicUiState,
    aiState: NowPlayingAiUiState,
    aiViewModel: NowPlayingAiViewModel = hiltViewModel(),
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
    playbackPositionFlow: StateFlow<Long>,
    duration: Long,
    sliderPos: Long?,
    visualizerData: FloatArray,
    onSliderChange: (Long) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onStop: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onSetArtShape: (String) -> Unit,
    onTrackSelect: (MusicTrack) -> Unit,
    onQueueIndexSelect: (Int) -> Unit,
    onToggleRotation: () -> Unit,
    onTogglePip: () -> Unit,
    onOpenAi: () -> Unit,
    onToggleMusicSettings: () -> Unit,
    catalogStreamQuality: String,
    onSetCatalogStreamQuality: (String) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetEqualizerPreset: (String) -> Unit,
    onSetCustomEqualizerGain: (Int, Float) -> Unit,
    onSetVisualizerSensitivity: (Float) -> Unit,
    onSetVisualizerAutoSensitivity: (Boolean) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onToggleVisualizer: () -> Unit,
    onToggleKaraoke: () -> Unit,
    onNextSongConfirmed: () -> Unit,
    onIncrementKaraokeSingCount: (MusicTrack) -> Unit,
    onSetVolume: (Float) -> Unit,
    onSetMutedByAi: (Boolean) -> Unit,
    equalizerPresets: List<String>
) {
    val haptics = LocalHapticFeedback.current
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return
    val configuration = LocalConfiguration.current
    // Full-screen drop distance for the exit choreography: the sheet slides
    // down past the bottom of the screen before it's dismissed.
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    val pauseCd = stringResource(R.string.st_MusicPlayerScreen_pause68)
    val playCd = stringResource(R.string.st_MusicPlayerScreen_play69)

    // ── Keep screen awake while the full player is open ──
    // Tied to isPlaying: stays awake while actively playing, lets the
    // screen sleep normally the moment playback is paused. Always cleared
    // on dispose so the flag never leaks into other screens.
    val screenAwakeView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(state.isPlaying) {
        screenAwakeView.keepScreenOn = state.isPlaying
        onDispose { screenAwakeView.keepScreenOn = false }
    }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyricsCustomization by remember { mutableStateOf(false) }
    var showCatalogQualitySheet by remember { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var showArtSettings by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // The sheet state lives for the whole sheet composition and is reused on
    // every open/close lifecycle; dismiss is driven by our own exitProgress
    // choreography below (isExiting), so this state is only ever the sheet's
    // structural anchor (Expanded/Hidden), not the exit animation driver.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Open/close choreography ──
    // Entrance: a gentle pop-in (slight overshoot via spring) instead of
    // relying only on the sheet's own default slide-up, so opening the full
    // player feels like one deliberate motion rather than two stacked ones.
    // Exit: the whole sheet slides down off-screen (a full display height),
    // with a subtle scale-down and fade, before the callbacks fire. This now
    // covers BOTH a normal dismiss (back press, scrim tap, swipe-down) and
    // "Stop & exit" — the same choreography either way, the only difference
    // being whether onStop() runs before onDismiss().
    val entranceProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceProgress.animateTo(1f, animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
    }

    var isExiting by remember { mutableStateOf(false) }
    var exitStopsPlayback by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    // Distance the whole sheet travels on exit — one full display height, so
    // the surface drops all the way off-screen instead of settling by a token
    // 48dp nudge. Resolved once per configuration (it cannot change mid-exit)
    // and captured into the graphicsLayer lambda, keeping per-frame reads there.
    val exitSlidePx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    LaunchedEffect(isExiting) {
        if (isExiting) {
            exitProgress.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
            if (exitStopsPlayback) onStop()
            onDismiss()
        }
    }
    // Normal close (back/scrim/swipe): plays the slide-down, but doesn't stop playback.
    val requestClose: () -> Unit = { if (!isExiting) { exitStopsPlayback = false; isExiting = true } }
    // "Stop & exit" menu action: same slide-down, but stops playback first.
    val requestStopAndExit: () -> Unit = { if (!isExiting) { exitStopsPlayback = true; isExiting = true } }

    // ── Sleep timer completion → stop & exit ──
    // The countdown itself is driven upstream (ViewModel); here we just
    // watch for the timer going from active to inactive. We distinguish
    // "it actually ran out" from "the person hit Cancel" by remembering
    // the last remaining-time reading we saw *while still active* — if
    // that reading was already at/near zero when deactivation happens,
    // the timer expired naturally rather than being cancelled early.
    var wasSleepTimerActive by remember { mutableStateOf(state.sleepTimerActive) }
    var lastSeenRemaining by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.sleepTimerActive, state.sleepTimerRemaining) {
        if (state.sleepTimerActive) {
            lastSeenRemaining = state.sleepTimerRemaining
        } else if (wasSleepTimerActive) {
            // Just transitioned from active → inactive this recomposition.
            val expiredNaturally = (lastSeenRemaining ?: 0L) <= 1000L
            if (expiredNaturally) requestStopAndExit()
            lastSeenRemaining = null
        }
        wasSleepTimerActive = state.sleepTimerActive
    }

    // ── Skip direction, used to orient the next/prev track transitions so a
    // "next" feels like content advancing left and "prev" like it's returning
    // from the right, instead of every change looking identical. Set right
    // before each skip call (buttons + swipe), read by the art/info
    // AnimatedContent transitionSpecs below. ──
    var skipDirection by remember { mutableIntStateOf(1) }
    val goNext: () -> Unit = { skipDirection = 1; onSkipNext() }
    val goPrev: () -> Unit = { skipDirection = -1; onSkipPrev() }

    val isOnlineCatalogTrack = remember(track.sourceUrl, track.path) {
        track.sourceUrl != null && track.path == null
    }

    // ── Art rotation ──
    // A single persistent Animatable instead of an infiniteTransition that got
    // thrown away and recreated on every play/pause. That old approach could
    // only ever start from 0f, so pausing (which switched to a fixed 0f state)
    // and resuming (which restarted the infinite animation at 0f) made the
    // disc visibly snap back to its starting angle. Here the angle lives in
    // one Animatable for the composable's lifetime: pausing simply cancels the
    // driving coroutine — the value stays exactly where it was — and resuming
    // restarts it from that same value, at the same constant angular speed,
    // for a seamless continuation.
    val rotationAnimatable = remember { Animatable(0f) }
    val rotationFeatureOn = state.rotationEnabled && !state.performanceMode
    val rotationSpinning = rotationFeatureOn && state.isPlaying
    val fullSpinMs = 34_000f

    LaunchedEffect(rotationSpinning) {
        if (!rotationSpinning) return@LaunchedEffect // freeze in place
        while (isActive) {
            val start = rotationAnimatable.value % 360f
            if (start != rotationAnimatable.value) rotationAnimatable.snapTo(start)
            val remainingDegrees = 360f - start
            val durationMs = (fullSpinMs * (remainingDegrees / 360f)).toInt().coerceAtLeast(1)
            rotationAnimatable.animateTo(
                targetValue = start + remainingDegrees,
                animationSpec = tween(durationMs, easing = LinearEasing)
            )
            rotationAnimatable.snapTo(0f) // visually identical to 360°, keeps the float small
        }
    }

    if (state.showMusicSettings) {
        MusicSettingsSheet(
            state = state,
            equalizerPresets = equalizerPresets,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSetEqualizerPreset = onSetEqualizerPreset,
            onSetCustomEqualizerGain = onSetCustomEqualizerGain,
            onSetVisualizerSensitivity = onSetVisualizerSensitivity,
            onSetVisualizerAutoSensitivity = onSetVisualizerAutoSensitivity,
            onToggleVisualizer = onToggleVisualizer,
            onDismiss = onToggleMusicSettings
        )
    }

    if (showCatalogQualitySheet && isOnlineCatalogTrack) {
        CatalogStreamQualitySheet(
            currentQuality = catalogStreamQuality,
            onDismiss = { showCatalogQualitySheet = false },
            onQualitySelected = {
                onSetCatalogStreamQuality(it)
                showCatalogQualitySheet = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = requestClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        dragHandle = null,
        modifier = Modifier.fillMaxSize(),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = true)
    ) {
        val dynamicColors = rememberDynamicColors(track.thumbnailUri)

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = exitProgress.value
                    scaleX = 1f - p * 0.02f
                    scaleY = 1f - p * 0.02f
                    alpha = 1f - p
                    translationY = p * screenHeightPx
                },
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                androidx.activity.compose.BackHandler(enabled = state.isKaraokeActive) {
                    onToggleKaraoke()
                }

                RevampedNowPlayingBackground(
                    artworkUri = track.thumbnailUri,
                    performanceMode = state.performanceMode,
                    modifier = Modifier.fillMaxSize()
                ) {
                }

                AnimatedContent(
                    targetState = state.isKaraokeActive,
                    transitionSpec = {
                        (fadeIn(tween(500)) + scaleIn(initialScale = 0.94f))
                            .togetherWith(fadeOut(tween(300)) + scaleOut(targetScale = 1.04f))
                    },
                    label = "karaokeTransition"
                ) { isKaraoke ->
                    if (isKaraoke) {
                        KaraokeView(
                            state = state,
                            aiState = aiState,
                            aiViewModel = aiViewModel,
                            onToggleKaraoke = onToggleKaraoke,
                            onPlay = {
                                onPlay()
                                if (aiState.isSingConfidentlyActive || aiState.isResolvingInstrumental) {
                                    aiViewModel.toggleSingConfidentlyActive(true) { onSeek(it) }
                                }
                            },
                            onPause = { onPause() },
                            onStop = {
                                onStop()
                                if (aiState.isSingConfidentlyActive || aiState.isResolvingInstrumental) {
                                    aiViewModel.toggleSingConfidentlyActive(false) { onSeek(it) }
                                }
                            },
                            onSkipNext = onSkipNext,
                            onSeek = {
                                onSeek(it)
                                aiViewModel.seekTo(it)
                            },
                            onSetVolume = onSetVolume,
                            onSetMutedByAi = onSetMutedByAi,
                            onNextSongConfirmed = onNextSongConfirmed,
                            playbackPosition = playbackPosition,
                            visualizerData = visualizerData
                        )

                        LaunchedEffect(aiState.karaokeScore) {
                            if (aiState.karaokeScore > 0 && playbackPosition >= state.duration - 2000) {
                                state.currentTrack?.let { onIncrementKaraokeSingCount(it) }
                            }
                        }
                    } else {
                        // One-shot entrance so opening the player feels like a
                        // gentle arrival rather than an instant cut — content
                        // eases up and in, then settles. Runs once per track
                        // dismiss/reopen (not on every recomposition).
                        var entered by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { entered = true }
                        val entranceAlpha by animateFloatAsState(
                            if (entered) 1f else 0f,
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            label = "entranceAlpha"
                        )
                        val entranceOffset by animateFloatAsState(
                            if (entered) 0f else 28f,
                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                            label = "entranceOffset"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(horizontal = 24.dp)
                                .graphicsLayer {
                                    alpha = entranceAlpha
                                    translationY = entranceOffset
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // ── 1. Header — quiet, functional, no branding ──
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ToolzExpressiveIconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.st_MusicPlayerScreen_cp59),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AnimatedVisibility(
                                        visible = state.sleepTimerActive && state.sleepTimerRemaining != null,
                                        enter = fadeIn(tween(250)) + scaleIn(
                                            initialScale = 0.6f,
                                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                        ),
                                        exit = fadeOut(tween(180)) + scaleOut(
                                            targetScale = 0.6f,
                                            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh)
                                        )
                                    ) {
                                        Surface(
                                            onClick = { showSleepTimerPicker = true },
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Timer,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    state.sleepTimerRemaining?.let { formatDuration(it) } ?: "",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                    Box {
                                        ToolzExpressiveIconButton(
                                            onClick = { showOverflowMenu = true },
                                            modifier = Modifier.size(44.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Icon(
                                                Icons.Rounded.MoreVert,
                                                contentDescription = "More options",
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showOverflowMenu,
                                            onDismissRequest = { showOverflowMenu = false },
                                            shape = RoundedCornerShape(24.dp)
                                        ) {
                                            // Combined art settings — opens expressive sheet
                                            DropdownMenuItem(
                                                text = { Text("Art settings", fontWeight = FontWeight.Medium) },
                                                onClick = { showOverflowMenu = false; showArtSettings = true },
                                                leadingIcon = { Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                                trailingIcon = { Text(state.artShape.lowercase().replaceFirstChar { it.uppercase() } + if (state.rotationEnabled) " · Rotating" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Edit tags", fontWeight = FontWeight.Medium) },
                                                onClick = { showOverflowMenu = false; showTagEditor = true },
                                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                            )
                                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                                            DropdownMenuItem(text = { Text(stringResource(R.string.st_MusicPlayerScreen_st63)) }, onClick = { showOverflowMenu = false; showSleepTimerPicker = true }, leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary) })
                                            if (isOnlineCatalogTrack) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.st_MusicPlayerScreen_sq88) + " · ${catalogStreamQuality.lowercase().replaceFirstChar { it.uppercase() }}") },
                                                    onClick = { showOverflowMenu = false; showCatalogQualitySheet = true },
                                                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.st_MusicPlayerScreen_ms64)) },
                                                onClick = { showOverflowMenu = false; onToggleMusicSettings() },
                                                leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary) }
                                            )
                                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.st_MusicPlayerScreen_ls_menu65)) },
                                                onClick = { showOverflowMenu = false; showLyricsCustomization = true },
                                                leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary) }
                                            )
                                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.st_MusicPlayerScreen_se66), color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    requestStopAndExit()
                                                },
                                                leadingIcon = { Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (showLyricsCustomization) {
                                LyricCustomizationSheet(
                                    state = aiState.lyricsState,
                                    onDismiss = { showLyricsCustomization = false },
                                    onResetDefaults = {
                                        aiViewModel.setLyricsLayout(LyricsLayout.CENTER)
                                        aiViewModel.setLyricsFont(LyricsFont.SANS_SERIF)
                                    },
                                    onToggleSeek = { aiViewModel.toggleSeekEnabled() },
                                    onToggleAlwaysSync = { aiViewModel.toggleAlwaysSync() },
                                    onToggleWordSync = { aiViewModel.toggleWordSyncEnabled() },
                                    onSetLayout = { layout -> aiViewModel.setLyricsLayout(layout) },
                                    onSetFont = { font -> aiViewModel.setLyricsFont(font) }
                                )
                            }

                            if (showArtSettings) {
                                ArtSettingsSheet(
                                    artShape = state.artShape,
                                    rotationEnabled = state.rotationEnabled,
                                    onSetArtShape = { onSetArtShape(it) },
                                    onToggleRotation = { onToggleRotation() },
                                    onDismiss = { showArtSettings = false }
                                )
                            }

                            if (showTagEditor) {
                                TagEditorSheet(
                                    track = track,
                                    onDismiss = { showTagEditor = false },
                                    onSave = { title, artist, album, thumb, lyrics ->
                                        musicViewModel.editTrackTags(track, title, artist, album, thumb, lyrics) { ok ->
                                            showTagEditor = false
                                        }
                                    }
                                )
                            }

                            // ── 2. Album art ──
                            val squareCorner by animateDpAsState(
                                targetValue = if (state.isPlaying) 28.dp else 52.dp,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                                label = "artCornerMorph"
                            )
                            val artMaxSize = (configuration.screenWidthDp * 0.80f).dp.coerceAtMost(310.dp)
                            val artShape = when (state.artShape) {
                                "CIRCLE" -> CircleShape
                                "SQUIRCLE", "SQUARE_ROUNDED" -> RoundedCornerShape(36.dp)
                                else -> RoundedCornerShape(squareCorner)
                            }

                            // ── Queue-derived next/prev preview ──
                            // Purely for what's shown mid-drag — it mirrors the linear
                            // queue order around the current track. If shuffle/repeat can
                            // make the ViewModel's *actual* next/prev diverge from simple
                            // queue-index neighbors, swap these two lines for real
                            // "peek next/prev" fields on MusicUiState instead; the push
                            // mechanics below don't care where the preview track comes from.
                            val queueTracks = remember(state.queue) { state.queue.map { it.track } }
                            val currentQueueIndex = remember(queueTracks, track.uri) {
                                queueTracks.indexOfFirst { it.uri == track.uri }
                            }
                            val nextPreviewTrack = remember(queueTracks, currentQueueIndex) {
                                queueTracks.getOrNull(currentQueueIndex + 1)
                            }
                            val prevPreviewTrack = remember(queueTracks, currentQueueIndex) {
                                if (currentQueueIndex > 0) queueTracks.getOrNull(currentQueueIndex - 1) else null
                            }

                            // ── Unified drag-to-skip: one offset drives both the outgoing
                            // and incoming disk every frame, so the incoming disk visibly
                            // pushes the current one aside instead of appearing only after
                            // release via a separate canned transition. Keyed on the track
                            // uri so a real (committed) track change always starts the next
                            // disk fresh at rest, with no leftover offset to unwind. ──
                            val dragOffsetPx = remember(track.uri) { Animatable(0f) }
                            val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
                            val commitThresholdPx = screenWidthPx * 0.28f

                            // ── Button-driven skip, reusing the exact same disk-push motion
                            // as a committed swipe. Instead of jump-cutting the art on tap,
                            // this animates dragOffsetPx all the way to the commit target
                            // first (same spring as the drag-release commit path) and only
                            // fires the real skip once that settle finishes — so tapping the
                            // prev/next buttons looks and feels like a fast, deliberate swipe
                            // rather than a separate, disconnected transition. ──
                            val animatedSkip: (next: Boolean) -> Unit = { next ->
                                if (!dragOffsetPx.isRunning) {
                                    coroutineScope.launch {
                                        dragOffsetPx.animateTo(
                                            targetValue = if (next) -screenWidthPx else screenWidthPx,
                                            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                                        )
                                        if (next) goNext() else goPrev()
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .pointerInput(track.uri) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                val offset = dragOffsetPx.value
                                                val committed = kotlin.math.abs(offset) > commitThresholdPx
                                                coroutineScope.launch {
                                                    if (committed) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        // dragging right (offset > 0) reveals the prev disk from
                                                        // the left, so a positive offset commit means "prev"
                                                        val committingNext = offset < 0f
                                                        dragOffsetPx.animateTo(
                                                            targetValue = if (committingNext) -screenWidthPx else screenWidthPx,
                                                            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                                                        )
                                                        if (committingNext) goNext() else goPrev()
                                                    } else {
                                                        dragOffsetPx.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                                        )
                                                    }
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                val prevOffset = dragOffsetPx.value
                                                val next = (prevOffset + dragAmount).coerceIn(-screenWidthPx, screenWidthPx)
                                                coroutineScope.launch { dragOffsetPx.snapTo(next) }
                                                // A single crisp tick right as the drag crosses the commit
                                                // threshold — confirms "this will skip if released now".
                                                if ((prevOffset < commitThresholdPx && next >= commitThresholdPx) ||
                                                    (prevOffset > -commitThresholdPx && next <= -commitThresholdPx) ||
                                                    (prevOffset > commitThresholdPx && next <= commitThresholdPx) ||
                                                    (prevOffset < -commitThresholdPx && next >= -commitThresholdPx)
                                                ) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.showVisualizer) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AudioVisualizerHalo(
                                            visualizerData = visualizerData,
                                            isPlaying = state.isPlaying,
                                            artMaxSize = artMaxSize,
                                            shape = state.artShape,
                                            thumbnailUri = track.thumbnailUri,
                                            trackKey = track.uri,
                                            rotation = if (rotationFeatureOn) rotationAnimatable.value else 0f,
                                            sensitivity = state.visualizerSensitivity,
                                            autoSensitivity = state.visualizerAutoSensitivity
                                        )


                                    }
                                } else if (!state.performanceMode) {
                                    val haloAlpha by animateFloatAsState(if (state.isPlaying) 0.32f else 0.08f, tween(900), label = "haloAlpha")
                                    val haloScale by animateFloatAsState(if (state.isPlaying) 1.04f else 1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "haloScale")
                                    // graphicsLayer instead of Modifier.scale(): the gradient
                                    // brush below is otherwise re-evaluated every tick the
                                    // spring is still settling, on top of the scale's own
                                    // relayout — graphicsLayer defers the read to the draw
                                    // phase and composites it as a pure transform instead.
                                    Box(
                                        modifier = Modifier
                                            .size(artMaxSize)
                                            .graphicsLayer { scaleX = haloScale; scaleY = haloScale }
                                            .background(
                                                Brush.radialGradient(listOf(dynamicColors.primary.copy(alpha = haloAlpha * 0.4f), Color.Transparent)),
                                                artShape
                                            )
                                    )
                                }

                                // Both preview tracks are composed once, up front, and kept
                                // mounted permanently — visibility, position and scale are
                                // driven entirely inside graphicsLayer{} below. Previously the
                                // incoming disk (and its AsyncImage) was added/removed from the
                                // tree via an `if (dragFraction != 0f)` check that itself read
                                // dragOffsetPx.value directly in the composable body. That made
                                // Compose recompose (not just relayout/redraw) this whole scope
                                // on every single animation frame, and repeatedly mount/unmount
                                // an image loader mid-gesture — which is what actually caused
                                // the visible stutter on skip/swipe. graphicsLayer lambdas are
                                // evaluated on the draw phase only, so the same 60fps drag now
                                // costs zero recompositions.
                                val nextArt = nextPreviewTrack
                                val prevArt = prevPreviewTrack

                                if (nextArt != null) {
                                    Surface(
                                        modifier = Modifier
                                            .size(artMaxSize)
                                            .graphicsLayer {
                                                val fraction = (dragOffsetPx.value / screenWidthPx).coerceIn(-1f, 1f)
                                                val revealing = fraction < 0f
                                                translationX = dragOffsetPx.value + screenWidthPx
                                                val settle = if (revealing) kotlin.math.abs(fraction) else 0f
                                                scaleX = 0.90f + settle * 0.10f
                                                scaleY = scaleX
                                                alpha = settle
                                            },
                                        shape = artShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, dynamicColors.primary.copy(alpha = 0.10f))
                                    ) {
                                        AlbumArtImage(
                                            url = nextArt.thumbnailUri,
                                            seed = nextArt.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            iconSize = 48.dp
                                        )
                                    }
                                }

                                if (prevArt != null) {
                                    Surface(
                                        modifier = Modifier
                                            .size(artMaxSize)
                                            .graphicsLayer {
                                                val fraction = (dragOffsetPx.value / screenWidthPx).coerceIn(-1f, 1f)
                                                val revealing = fraction > 0f
                                                translationX = dragOffsetPx.value - screenWidthPx
                                                val settle = if (revealing) kotlin.math.abs(fraction) else 0f
                                                scaleX = 0.90f + settle * 0.10f
                                                scaleY = scaleX
                                                alpha = settle
                                            },
                                        shape = artShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, dynamicColors.primary.copy(alpha = 0.10f))
                                    ) {
                                        AlbumArtImage(
                                            url = prevArt.thumbnailUri,
                                            seed = prevArt.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            iconSize = 48.dp
                                        )
                                    }
                                }

                                // Outgoing (current) disk — plain Surface driven by the same
                                // dragOffsetPx, no separate AnimatedContent/transitionSpec
                                // layered on top, so there's exactly one source of truth for
                                // its position during both manual drag and the post-release
                                // commit/cancel spring. All per-frame reads live inside
                                // graphicsLayer, same reasoning as above.
                                Surface(
                                    modifier = Modifier
                                        .size(artMaxSize)
                                        .graphicsLayer {
                                            rotationZ = if (rotationFeatureOn) rotationAnimatable.value else 0f
                                            translationX = dragOffsetPx.value
                                            val settle = kotlin.math.abs((dragOffsetPx.value / screenWidthPx).coerceIn(-1f, 1f))
                                            scaleX = 1f - settle * 0.10f
                                            scaleY = scaleX
                                            alpha = 1f - settle * 0.35f
                                        }
                                        .shadow(
                                            if (state.performanceMode) 6.dp else 28.dp,
                                            artShape,
                                            spotColor = dynamicColors.primary.copy(alpha = 0.45f)
                                        ),
                                    shape = artShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, dynamicColors.primary.copy(alpha = 0.10f))
                                ) {
                                    AlbumArtImage(
                                        url = track.thumbnailUri,
                                        seed = track.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        iconSize = 48.dp
                                    )
                                }
                            }

                            // ── 3. Track info — plain type hierarchy, no chrome ──
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Title/author get their own AnimatedContent (rather
                                    // than sharing one with the favorite button) so the
                                    // text swap can use a softer blur+slide+fade "settle"
                                    // that suits typography, independent of whatever the
                                    // favorite icon is doing.
                                    AnimatedContent(
                                        targetState = track.title,
                                        transitionSpec = {
                                            val dir = skipDirection
                                            val enter = fadeIn(tween(360, easing = FastOutSlowInEasing)) +
                                                slideInHorizontally(
                                                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                                                ) { w -> (w / 3) * dir } +
                                                scaleIn(
                                                    initialScale = 0.92f,
                                                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                                    transformOrigin = TransformOrigin(if (dir > 0) 0f else 1f, 0.5f)
                                                )
                                            val exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
                                                slideOutHorizontally(tween(180)) { w -> -(w / 4) * dir }
                                            enter.togetherWith(exit).using(SizeTransform(clip = false))
                                        },
                                        label = "trackTitle"
                                    ) { title ->
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .basicMarquee()
                                                .horizontalFadingEdges(left = 24.dp, right = 24.dp)
                                        )
                                    }
                                    AnimatedContent(
                                        targetState = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                            ?: "Unknown artist",
                                        transitionSpec = {
                                            val dir = skipDirection
                                            val enter = fadeIn(tween(360, delayMillis = 40, easing = FastOutSlowInEasing)) +
                                                slideInHorizontally(
                                                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                                                ) { w -> (w / 3) * dir }
                                            val exit = fadeOut(tween(150, easing = FastOutLinearInEasing)) +
                                                slideOutHorizontally(tween(150)) { w -> -(w / 4) * dir }
                                            enter.togetherWith(exit).using(SizeTransform(clip = false))
                                        },
                                        label = "trackArtist"
                                    ) { artist ->
                                        Text(
                                            text = artist,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // ── Favorite button — expressive burst ──
                                // Three coordinated motions instead of a single scale
                                // pop: (1) an overshoot scale with a quick anticipatory
                                // dip below 1x before the bounce, (2) a small ±rotation
                                // wobble so the heart feels flicked rather than just
                                // resized, (3) a soft radiating ring that expands and
                                // fades behind the icon on "add" only, echoing the fill
                                // color so it reads as a little burst of warmth. Skips
                                // all of this on first composition of a track so it
                                // only ever plays on a genuine toggle.
                                val favScale = remember(track.uri) { Animatable(1f) }
                                val favRotation = remember(track.uri) { Animatable(0f) }
                                val ringProgress = remember(track.uri) { Animatable(1f) }
                                var isFavInitialized by remember(track.uri) { mutableStateOf(false) }
                                LaunchedEffect(track.uri, track.isFavorite) {
                                    if (!isFavInitialized) {
                                        isFavInitialized = true
                                    } else {
                                        launch {
                                            favScale.snapTo(0.85f)
                                            favScale.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                                            )
                                        }
                                        launch {
                                            val kick = if (track.isFavorite) 14f else -10f
                                            favRotation.snapTo(0f)
                                            favRotation.animateTo(kick, tween(90, easing = FastOutSlowInEasing))
                                            favRotation.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
                                        }
                                        if (track.isFavorite) {
                                            launch {
                                                ringProgress.snapTo(0f)
                                                ringProgress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
                                            }
                                        }
                                    }
                                }
                                val favColor = MaterialTheme.colorScheme.error
                                Box(contentAlignment = Alignment.Center) {
                                    // Radiating ring, drawn behind the button, only visible
                                    // mid-animation (alpha fades to 0 as it expands to 0).
                                    Canvas(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .graphicsLayer { alpha = (1f - ringProgress.value).coerceIn(0f, 1f) }
                                    ) {
                                        if (ringProgress.value < 1f) {
                                            val strokeWidth = 2.dp.toPx()
                                            val r = (size.minDimension / 2f) * (0.6f + ringProgress.value * 0.7f)
                                            drawCircle(
                                                color = favColor,
                                                radius = r,
                                                center = center,
                                                style = Stroke(width = strokeWidth)
                                            )
                                        }
                                    }
                                    ToolzExpressiveIconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onToggleFavorite(track)
                                        },
                                        modifier = Modifier.size(44.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (track.isFavorite) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = if (track.isFavorite) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = if (track.isFavorite) stringResource(R.string.st_MusicPlayerScreen_rff20) else stringResource(R.string.st_MusicPlayerScreen_atf21),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    scaleX = favScale.value
                                                    scaleY = favScale.value
                                                    rotationZ = favRotation.value
                                                }
                                        )
                                    }
                                }
                            }

                            // ── 4. Slider ──
                            Spacer(Modifier.height(18.dp))
                            val currentPos = sliderPos ?: playbackPosition
                            // Glide the slider thumb back to its resting place when a new
                            // track drops the position to a small value: pass the RAW target
                            // while scrubbing (sliderPos != null) so the thumb tracks the
                            // finger 1:1, and smoothly tween short of that (e.g. a track
                            // switch resetting to 0) otherwise. snap() in performance mode.
                            val sliderGlide by animateFloatAsState(
                                targetValue = currentPos.toFloat(),
                                animationSpec = if (state.performanceMode || sliderPos != null) snap() else
                                    tween(450, easing = FastOutSlowInEasing),
                                label = "sliderGlide"
                            )
                            val sliderValue = if (sliderPos != null || state.performanceMode) currentPos.toFloat() else sliderGlide
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SquigglySlider(
                                    value = sliderValue,
                                    onValueChange = { onSliderChange(it.toLong()) },
                                    onValueChangeFinished = onSliderChangeFinished,
                                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                    isPlaying = state.isPlaying
                                )
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(formatDuration(currentPos), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                }
                            }

                            // ── 5. Transport controls ──
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolzExpressiveIconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        animatedSkip(false)
                                    },
                                    modifier = Modifier.size(58.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.st_MusicPlayerScreen_pt67), modifier = Modifier.size(36.dp))
                                }

                                val playScale by animateFloatAsState(
                                    if (state.isPlaying) 1f else 1.06f,
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                    label = "playScale"
                                )
                                val playOnColor = if (dynamicColors.primary.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
                                val playShape by animateFloatAsState(
                                    targetValue = if (state.isPlaying) 30f else 46f,
                                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                    label = "playShapeMorph"
                                )
                                ToolzExpressiveButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTogglePlay()
                                    },
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(64.dp)
                                        .scale(if (state.performanceMode) 1f else playScale)
                                        .semantics { contentDescription = if (state.isPlaying) pauseCd else playCd },
                                    shape = RoundedCornerShape(playShape.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = dynamicColors.primary,
                                        contentColor = playOnColor
                                    )
                                ) {
                                    // Real morph instead of a plain crossfade: the outgoing icon
                                    // scales down while fading fast, the incoming one springs up
                                    // from below full size — reads as one shape transforming into
                                    // the other rather than two icons dissolving into each other.
                                    AnimatedContent(
                                        targetState = state.isPlaying,
                                        transitionSpec = {
                                            (scaleIn(
                                                initialScale = 0.5f,
                                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                                            ) + fadeIn(tween(140)))
                                                .togetherWith(
                                                    scaleOut(
                                                        targetScale = 0.5f,
                                                        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh)
                                                    ) + fadeOut(tween(100))
                                                )
                                        },
                                        label = "playPauseMorph"
                                    ) { playing ->
                                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
                                    }
                                }

                                ToolzExpressiveIconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        animatedSkip(true)
                                    },
                                    modifier = Modifier.size(58.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.st_MusicPlayerScreen_nt70), modifier = Modifier.size(36.dp))
                                }
                            }

                            // ── 6. Secondary controls — real segmented toggle group ──
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                shape = RoundedCornerShape(32.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SegmentToggle(
                                        checked = state.isShuffleOn,
                                        onClick = onToggleShuffle,
                                        checkedIcon = Icons.Rounded.ShuffleOn,
                                        uncheckedIcon = Icons.Rounded.Shuffle,
                                        contentDescription = stringResource(R.string.st_MusicPlayerScreen_shuf71)
                                    )
                                    SegmentToggle(
                                        checked = state.repeatMode != Player.REPEAT_MODE_OFF,
                                        onClick = onToggleRepeat,
                                        checkedIcon = when (state.repeatMode) {
                                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn
                                            Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn
                                            else -> Icons.Rounded.Repeat
                                        },
                                        uncheckedIcon = Icons.Rounded.Repeat,
                                        contentDescription = when (state.repeatMode) {
                                            Player.REPEAT_MODE_ONE -> "Repeat one"
                                            Player.REPEAT_MODE_ALL -> "Repeat all"
                                            else -> "Repeat off"
                                        }
                                    )
                                    SegmentToggle(
                                        checked = false,
                                        onClick = { showQueue = true },
                                        checkedIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                                        uncheckedIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = stringResource(R.string.st_MusicPlayerScreen_q72)
                                    )
                                    if (state.isOnline) {
                                        if (state.karaokeEnabled && (!track.aiLyrics.isNullOrEmpty() || track.sourceUrl != null || aiState.instrumentalMatch != null)) {
                                            KaraokeMicIcon(
                                                isActive = state.isKaraokeActive,
                                                onClick = onToggleKaraoke,
                                                size = 50.dp,
                                                iconSize = 22.dp,
                                                thumbnailUri = track.thumbnailUri,
                                                isLoading = aiState.isResolvingInstrumental
                                            )
                                        }
                                        SegmentToggle(
                                            checked = aiState.isAiEnabled,
                                            onClick = onOpenAi,
                                            checkedIcon = Icons.Rounded.AutoAwesome,
                                            uncheckedIcon = Icons.Rounded.Lyrics,
                                            contentDescription = stringResource(R.string.st_MusicPlayerScreen_ail73)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }

    if (showSleepTimerPicker) {
        SleepTimerSheet(
            currentTimerActive = state.sleepTimerActive,
            remainingMs = state.sleepTimerRemaining,
            onSet = { mins -> onSetSleepTimer(mins); showSleepTimerPicker = false },
            onCancel = { onSetSleepTimer(null); showSleepTimerPicker = false },
            onDismiss = { showSleepTimerPicker = false }
        )
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentTrack = track,
            currentQueueIndex = state.currentQueueIndex,
            onTrackSelect = { onTrackSelect(it) },
            onQueueIndexSelect = { onQueueIndexSelect(it) },
            onDismiss = { showQueue = false },
            onMove = { from, to -> onMoveQueueItem(from, to) },
            onRemove = { index -> onRemoveQueueItem(index) },
            onClear = { onClearQueue() }
        )
    }
}

// ── Small helper: one segment of the secondary-controls toggle group ──
// The active state gets a real filled tonal pill (secondaryContainer),
// not just a tint swap — you should be able to tell what's on at a glance,
// not squint at an icon color.
@Composable
private fun SegmentToggle(
    checked: Boolean,
    onClick: () -> Unit,
    checkedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    uncheckedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String? = null
) {
    val containerColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "segmentContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "segmentContent"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (checked) checkedIcon else uncheckedIcon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
 fun MusicSettingsSheet(
    state: MusicUiState,
    equalizerPresets: List<String>,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetEqualizerPreset: (String) -> Unit,
    onSetCustomEqualizerGain: (Int, Float) -> Unit,
    onSetVisualizerSensitivity: (Float) -> Unit,
    onSetVisualizerAutoSensitivity: (Boolean) -> Unit,
    onToggleVisualizer: () -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.st_MusicPlayerScreen_ms_title74),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.st_MusicPlayerScreen_ps75),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${"%.2f".format(state.playbackSpeed)}x",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            ExpressiveSlider(
                value = state.playbackSpeed,
                onValueChange = onSetPlaybackSpeed,
                valueRange = 0.5f..2.5f,
                modifier = Modifier.fillMaxWidth()
            )

            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(speeds) { speed ->
                    ExpressiveFilterChip(
                        selected = state.playbackSpeed == speed,
                        onClick = { onSetPlaybackSpeed(speed) },
                        label = { Text("${speed}x", fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.st_MusicPlayerScreen_av76),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.st_MusicPlayerScreen_sahaa77),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                    ExpressiveSwitch(
                        checked = state.showVisualizer,
                        onCheckedChange = {
                            onToggleVisualizer()
                        }
                    )
                }

                AnimatedVisibility(
                    visible = state.showVisualizer,
                    enter = fadeIn(tween(220)) + expandVertically(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
                    exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.st_MusicPlayerScreen_asens81),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.st_MusicPlayerScreen_athldes82),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ExpressiveSwitch(
                                checked = state.visualizerAutoSensitivity,
                                onCheckedChange = onSetVisualizerAutoSensitivity
                            )
                        }

                        AnimatedVisibility(
                            visible = !state.visualizerAutoSensitivity,
                            enter = fadeIn(tween(220)) + expandVertically(animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
                            exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(R.string.st_MusicPlayerScreen_sens83),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { onSetVisualizerSensitivity(0.5f) }) {
                                        Text(stringResource(R.string.st_MusicPlayerScreen_r84), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                ExpressiveSlider(
                                    value = state.visualizerSensitivity,
                                    onValueChange = onSetVisualizerSensitivity,
                                    valueRange = 0.1f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

        Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.st_MusicPlayerScreen_eq85),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            LazyRow(
                modifier = Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 24.dp)
            ) {
                items(equalizerPresets) { preset ->
                    val isSelected = state.equalizerPreset == preset
                    Surface(
                        onClick = { onSetEqualizerPreset(preset) },
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                preset,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (state.equalizerPreset == "Custom") {
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val bands = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
                        state.customEqualizerGains.forEachIndexed { index, gain ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f)
                            ) {
                                Text(
                                    "${gain.toInt()}dB",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Custom Vertical Slider
                                    Slider(
                                        value = gain,
                                        onValueChange = { onSetCustomEqualizerGain(index, it) },
                                        valueRange = -15f..15f,
                                        modifier = Modifier
                                            .graphicsLayer {
                                                rotationZ = -90f
                                                transformOrigin = TransformOrigin.Center
                                            }
                                            .width(130.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                        )
                                    )
                                }
                                Text(
                                    bands.getOrNull(index) ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Audio Visualizer — Material 3 Expressive radial bars
//
// Design goals:
//  • Reads as a ring of discrete capsule "bars" (M3 Expressive loves distinct,
//    countable shapes over blobby continuous forms) instead of a single
//    wobbling outline.
//  • Uses the album's extracted dynamic color, blending primary → secondary
//    across the ring so it always feels tied to the artwork.
//  • Has real idle motion: when paused, bars don't go flat — they breathe
//    gently in a slow wave, so the screen never feels "dead".
//  • Bars are individually spring-animated per frame, not just linearly
//    interpolated, so peaks feel snappy and settle with a soft bounce.
// ─────────────────────────────────────────────────────────────────────────────

// ── Audio visualizer ──
// A radial reactive ring, built around three ideas:
//  - Everything hot (smoothing, gain, path building) runs in one
//    withFrameNanos loop scaled by real elapsed time, so it looks identical
//    at 60/90/120Hz and never recomposes the composable itself — only the
//    Canvas's draw phase reads the live values, via graphicsLayer/drawWithCache.
//  - Bars are drawn as actual rounded capsules (M3 Expressive shape
//    language) whose *width* grows with amplitude, not just their length —
//    quiet bars read as thin ticks, loud ones bloom into pills. All bars
//    fold into a single Path per frame, drawn with one drawPath() call.
//  - Auto-sensitivity does real per-song auto-gain with a noise-gate, so
//    near-silence can't be misread as a strong signal (the old failure mode
//    that made the ring look "reactive" to nothing) while genuinely quiet
//    passages still settle into their own visible dynamic range.
private const val VIS_BAR_COUNT = 32
// Calibrated against the 0..100 magnitude scale produced by
// MusicPlayerViewModel.computeSpectrum() — roughly "typical loud passage",
// not the theoretical max. Used only in manual (non-auto) sensitivity mode.
private const val MANUAL_REFERENCE_PEAK = 40f

@Composable
fun AudioVisualizerHalo(
    visualizerData: FloatArray,
    isPlaying: Boolean,
    artMaxSize: Dp,
    shape: String,
    thumbnailUri: String?,
    trackKey: String? = null,
    rotation: Float = 0f,
    sensitivity: Float = 1.0f,
    autoSensitivity: Boolean = true
) {
    val dynamicColors = rememberDynamicColors(thumbnailUri)
    val primary = dynamicColors.primary
    val secondary = dynamicColors.secondary
    val tertiary = androidx.compose.ui.graphics.lerp(primary, secondary, 0.5f)
    // Skips the extra glow path + ambient radial gradient on devices/users
    // that opted into performance mode — the ring itself stays fully
    // reactive either way, this only drops the purely decorative bloom.
    val reducedEffects = LocalPerformanceMode.current

    // Per-bar smoothed amplitude (0f..1f), kept as one plain FloatArray so a
    // frame update touches one object, not 32 separate mutableStateOf cells.
    val smoothed = remember { FloatArray(VIS_BAR_COUNT) }
    var smoothedVersion by remember { mutableIntStateOf(0) }
    // Scratch buffer reused every frame instead of allocated fresh — part of
    // the reported "lag" was GC pressure from a new FloatArray(32) on every
    // single frame at 60-120fps.
    val rawTargets = remember { FloatArray(VIS_BAR_COUNT) }
    // Second scratch buffer holding a lightly neighbor-blended version of
    // rawTargets — softens bar-to-bar flicker (adjacent bars catching the
    // same transient at slightly different strengths) into a more cohesive
    // wave shape, without smearing real hits since each bar keeps most of
    // its own value.
    val blendedTargets = remember { FloatArray(VIS_BAR_COUNT) }
    var idlePhase by remember { mutableFloatStateOf(0f) }

    // ── Auto sensitivity with a noise gate ──
    // Raw FFT magnitudes have no fixed scale, so bars are normalized against
    // a slowly-decaying running peak rather than a hardcoded ceiling — that
    // alone fixes "silently stays flat because the real signal never
    // reaches the assumed 0..100 range". On top of that, `noiseFloor` tracks
    // the quiet-signal baseline (mic/codec hiss, DAC noise floor) and is
    // subtracted before normalizing, so that baseline never gets amplified
    // into visible bar motion during silence or between tracks — the other
    // reported failure mode, where the ring looked "reactive" to nothing.
    var runningPeak by remember { mutableFloatStateOf(1f) }
    var noiseFloor by remember { mutableFloatStateOf(0f) }
    // Tracks whether the last frame was actively receiving music. On the
    // frame where this flips from false→true (first play, resume from
    // pause, or the visualizer re-attaching after a gap) we seed
    // runningPeak/noiseFloor directly from the very first sample instead of
    // slowly lerping from whatever the previous track left behind — that
    // slow crawl was the actual cause of the ring taking several seconds to
    // "wake up" after pressing play or skipping tracks.
    var hasStarted by remember { mutableStateOf(false) }

    // ── Soft spring layer ──
    // `smoothed[]` (above) is the audio-reactive envelope: fast attack, dt-
    // scaled release, tuned for accuracy against the music. Feeding that
    // straight into bar length still reads as slightly mechanical since it's
    // a pure exponential chase with no momentum. `displayed[]` is a second,
    // purely cosmetic layer that trails `smoothed[]` with real spring
    // physics (position + velocity), so each bar settles with a soft, springy
    // give instead of snapping straight to its target — "softer" without
    // making the ring any less responsive to the actual music, since the
    // audio-accuracy work still happens one layer upstream.
    val displayed = remember { FloatArray(VIS_BAR_COUNT) }
    val velocity = remember { FloatArray(VIS_BAR_COUNT) }

    // ── Ring scale ──
    // Driven inside the same per-frame loop as the bars and applied through
    // graphicsLayer (read at the draw phase), so updating it never triggers
    // recomposition — the actual fix for the reported lag, since the canvas
    // draw itself was already cheap.
    var ringScaleValue by remember { mutableFloatStateOf(1f) }

    // ── Live parameter capture ──
    // LaunchedEffect only restarts when its *keys* change. The previous
    // version keyed on (isPlaying, autoSensitivity, sensitivity) but not
    // `visualizerData` — which meant the running frame loop captured
    // whichever FloatArray reference existed at the moment it last
    // (re)started, and kept reading that same stale snapshot forever,
    // since the array updates ~60x/sec from the ViewModel without ever
    // changing those three keys. That's the actual root cause of the ring
    // "randomly staying idle" (it launched once with an empty array before
    // the first FFT capture arrived, and never saw a real sample again) and
    // of "bad auto sensitivity" (the gain/noise-floor tracking was being
    // fed the same single sample over and over instead of live audio).
    // rememberUpdatedState + a single effect that never restarts fixes both:
    // every iteration of the frame loop now reads whatever the latest
    // composition actually passed in.
    val liveData = rememberUpdatedState(visualizerData)
    val livePlaying = rememberUpdatedState(isPlaying)
    val liveAutoSensitivity = rememberUpdatedState(autoSensitivity)
    val liveSensitivity = rememberUpdatedState(sensitivity)
    // Read every frame inside the loop below to detect a track change
    // directly, rather than inferring it from loudness jumps or gaps in
    // `visualizerData` — both of those can fail to fire (new track happens
    // to be similarly loud, or the data stream never actually goes empty
    // across the skip), which was the real cause of the ring occasionally
    // staying idle after a skip and of it taking many seconds to settle
    // into the new track's dynamics.
    val liveTrackKey = rememberUpdatedState(trackKey)

    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        var lastTrackKey = liveTrackKey.value
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = frameNanos

            val playingNow = livePlaying.value
            val dataNow = liveData.value
            val autoSensNow = liveAutoSensitivity.value
            val sensNow = liveSensitivity.value

            // Track changed since last frame (skip, auto-advance, or
            // selecting a different track from the queue) — force an
            // immediate recalibration on the very next real sample instead
            // of waiting for the loudness-jump heuristic to notice, and
            // reset the display envelope so any lingering shape from the
            // previous track doesn't slowly bleed into the new one.
            val trackKeyNow = liveTrackKey.value
            if (trackKeyNow != lastTrackKey) {
                lastTrackKey = trackKeyNow
                hasStarted = false
                for (i in 0 until VIS_BAR_COUNT) {
                    smoothed[i] = 0f
                    velocity[i] = 0f
                }
            }

            if (playingNow && dataNow.isNotEmpty()) {
                // Downsample by max, not average — average washes out sharp
                // transients (kicks/snares), which reads as sluggish even
                // with clearly active audio. Per-bar targets still use this
                // raw max so individual hits stay snappy; only the *global*
                // peak-tracking measure below is spike-resistant.
                val chunk = (dataNow.size / VIS_BAR_COUNT).coerceAtLeast(1)
                var frameMin = Float.MAX_VALUE
                var sumSq = 0f
                // Track the loudest 3 bars this frame (insertion into a
                // fixed-size top-3, no allocation) instead of a single max.
                var top1 = 0f; var top2 = 0f; var top3 = 0f
                for (i in 0 until VIS_BAR_COUNT) {
                    var m = 0f
                    val start = i * chunk
                    val end = (start + chunk).coerceAtMost(dataNow.size)
                    for (j in start until end) {
                        val v = kotlin.math.abs(dataNow[j])
                        if (v > m) m = v
                    }
                    rawTargets[i] = m
                    if (m < frameMin) frameMin = m
                    sumSq += m * m
                    when {
                        m > top1 -> { top3 = top2; top2 = top1; top1 = m }
                        m > top2 -> { top3 = top2; top2 = m }
                        m > top3 -> top3 = m
                    }
                }
                val frameRms = kotlin.math.sqrt(sumSq / VIS_BAR_COUNT)
                // Spike-resistant "how loud right now" measure: the average
                // of the 3 loudest bars, blended with overall RMS, instead
                // of the single loudest bar. One stray FFT bin can no longer
                // yank the auto-gain (and therefore the whole ring) around —
                // a genuine hit still lights up several bars at once and
                // registers at full strength, just without the jitter a raw
                // max was prone to.
                val loudNow = (top1 + top2 + top3) / 3f * 0.75f + frameRms * 0.25f

                val quietSample = frameMin.coerceAtMost(loudNow * 0.2f)

                if (!hasStarted) {
                    // Fresh start: press play, resume, or the visualizer
                    // reattaching after a gap. Seed the noise floor *first*,
                    // then derive the peak reference from it — using the
                    // previous track's leftover noise floor here was a real
                    // bug: a loud track followed by a quiet one could leave
                    // the new track's peak reference pinned near its floor,
                    // so the ring barely moved until the noise floor slowly
                    // caught up.
                    noiseFloor = quietSample
                    val freshGated = (loudNow - noiseFloor).coerceAtLeast(0f)
                    runningPeak = if (autoSensNow) freshGated.coerceAtLeast(0.05f)
                    else MANUAL_REFERENCE_PEAK / sensNow.coerceAtLeast(0.05f)
                    hasStarted = true
                } else {
                    val gatedLoud = (loudNow - noiseFloor).coerceAtLeast(0f)
                    val desiredPeak = if (autoSensNow) gatedLoud.coerceAtLeast(0.05f) else {
                        // Manual mode: fixed reference ceiling scaled by the
                        // sensitivity slider (0.1x..1.5x), no per-song
                        // adaptation. MANUAL_REFERENCE_PEAK is calibrated to
                        // sit around typical (not peak) music energy on the
                        // 0..100 scale computeSpectrum() produces, so
                        // sensitivity = 1.0 lands at a sensible
                        // middle-of-the-road gain with visible range above
                        // and below it.
                        MANUAL_REFERENCE_PEAK / sensNow.coerceAtLeast(0.05f)
                    }
                    // Still update the noise floor and peak reference even
                    // mid-stream (track skipped without a gap in data), but
                    // detect a large loudness jump — a strong signal that
                    // the track itself changed — and snap to it quickly
                    // instead of crawling for several seconds.
                    val peakRatio = if (runningPeak > 0.0001f) desiredPeak / runningPeak else 999f
                    val bigJump = peakRatio > 2.2f || peakRatio < 0.4f
                    val floorTau = if (bigJump) 0.5f else 1.3f
                    noiseFloor = lerp(noiseFloor, quietSample, 1f - kotlin.math.exp(-dt / floorTau))

                    val peakTimeConstant = if (autoSensNow) {
                        when {
                            // A track change (or a huge dynamic swing) — snap
                            // fast rather than crawling.
                            bigJump -> 0.15f
                            // Getting louder: catch it within a couple frames.
                            desiredPeak > runningPeak -> 0.22f
                            // Getting quieter: still settle in ~1s, not ~9s —
                            // fast enough to feel immediate, slow enough that
                            // a single quiet beat doesn't renormalize the ring.
                            else -> 0.7f
                        }
                    } else {
                        if (bigJump) 0.15f else 0.25f
                    }
                    runningPeak = lerp(runningPeak, desiredPeak, 1f - kotlin.math.exp(-dt / peakTimeConstant))
                }

                val gain = 1f / runningPeak.coerceAtLeast(0.02f)
                // Attack / release, both scaled by dt so the feel stays
                // constant regardless of the display's refresh rate. Slightly
                // softer than before on both ends — still catches a hit
                // within a frame or two, but settles with less of a snap.
                val attackRate = 1f - kotlin.math.exp(-dt / 0.045f)
                val releaseRate = 1f - kotlin.math.exp(-dt / 0.26f)

                // Light neighbor blending across bars (edge-clamped, not
                // wrapped) softens single-bar flicker into a more cohesive
                // wave, while each bar still keeps most of its own value so
                // real transients don't get smeared away.
                for (i in 0 until VIS_BAR_COUNT) {
                    val prev = rawTargets[(i - 1).coerceAtLeast(0)]
                    val next = rawTargets[(i + 1).coerceAtMost(VIS_BAR_COUNT - 1)]
                    blendedTargets[i] = rawTargets[i] * 0.72f + (prev + next) * 0.14f
                }

                for (i in 0 until VIS_BAR_COUNT) {
                    val gated = (blendedTargets[i] - noiseFloor).coerceAtLeast(0f)
                    // A gentle perceptual curve (sqrt) instead of a flat
                    // linear map — quiet passages stay visibly alive instead
                    // of collapsing to near-zero, and peaks don't clip as
                    // sharply, which reads as springier motion rather than a
                    // raw VU meter.
                    val linear = (gated * gain).coerceIn(0f, 1f)
                    val target = kotlin.math.sqrt(linear)
                    val current = smoothed[i]
                    smoothed[i] = if (target > current) {
                        lerp(current, target, attackRate)
                    } else {
                        lerp(current, target, releaseRate)
                    }
                }
            } else {
                hasStarted = false
                // Idle: a slow traveling wave around the ring, rather than
                // every bar rising and falling in lockstep. A single shared
                // pulse reads as one rigid unit breathing; offsetting each
                // bar's phase by its position around the ring turns the same
                // motion into a soft ripple that visually "moves", which
                // sits much better with the rest of the calmer idle UI.
                idlePhase += dt * (2 * Math.PI.toFloat() / 6f)
                val twoPi = 2 * Math.PI.toFloat()
                if (idlePhase > twoPi) idlePhase -= twoPi
                for (i in 0 until VIS_BAR_COUNT) {
                    val phaseOffset = (i.toFloat() / VIS_BAR_COUNT) * (2 * Math.PI.toFloat())
                    val wave = kotlin.math.sin(idlePhase - phaseOffset) * 0.5f + 0.5f
                    // Two waves at slightly different speeds/weights so the
                    // ripple never repeats in an obviously mechanical loop.
                    val phaseOffset2 = phaseOffset * 1.7f
                    val wave2 = kotlin.math.sin(idlePhase * 0.63f - phaseOffset2) * 0.5f + 0.5f
                    val pulse = (wave * 0.7f + wave2 * 0.3f) * 0.11f
                    smoothed[i] = lerp(smoothed[i], pulse, 1f - kotlin.math.exp(-dt / 0.5f))
                }
            }
            // Spring-chase `displayed[]` toward this frame's `smoothed[]`
            // target. Semi-implicit Euler with a stiffness/damping pair tuned
            // just above critical damping — enough spring to feel soft and
            // alive, without any visible overshoot or wobble. Frame-rate
            // independent since both terms are scaled by dt.
            val stiffness = 70f
            val damping = 18f // critical damping for k=70 is ~2*sqrt(70)=~16.7; slightly above it avoids any overshoot/wobble
            for (i in 0 until VIS_BAR_COUNT) {
                val delta = smoothed[i] - displayed[i]
                velocity[i] += (delta * stiffness - velocity[i] * damping) * dt
                displayed[i] += velocity[i] * dt
            }

            // One version bump = one recomposition-free "frame is ready"
            // signal; the Canvas below reads `displayed` by direct reference,
            // so this only invalidates the draw phase, nothing upstream.
            smoothedVersion++

            var lowEnd = 0f
            for (i in 0 until 12) lowEnd += smoothed[i]
            lowEnd /= 12f
            val ringTarget = if (playingNow) 1f + (lowEnd * 0.045f) else 1f
            ringScaleValue = lerp(ringScaleValue, ringTarget, 1f - kotlin.math.exp(-dt / 0.09f))
        }
    }

    val barDirections = remember(VIS_BAR_COUNT) {
        val angleStep = (2 * Math.PI / VIS_BAR_COUNT).toFloat()
        FloatArray(VIS_BAR_COUNT * 2).also { arr ->
            for (i in 0 until VIS_BAR_COUNT) {
                val angle = -Math.PI.toFloat() / 2f + i * angleStep
                arr[i * 2] = kotlin.math.cos(angle.toDouble()).toFloat()
                arr[i * 2 + 1] = kotlin.math.sin(angle.toDouble()).toFloat()
            }
        }
    }
    // Reused scratch objects for building each bar's rotated capsule. Before
    // this, the per-bar loop allocated a fresh Path() + Matrix() on *every
    // one of the 32 bars, every single frame* (on top of the two top-level
    // paths) — real GC pressure at 60-120fps. `.reset()` + reuse turns that
    // into zero steady-state allocation.
    val scratchBarPath = remember { Path() }
    val scratchMatrix = remember { androidx.compose.ui.graphics.Matrix() }
    val mainPath = remember { Path() }
    val glowPath = remember { Path() }

    // Sweep brush only depends on the album's dynamic colors, not on
    // anything that changes per-frame — recreating it (and its 5 color
    // stops) 60-120 times a second was pure waste. Recomputed only when the
    // colors themselves change (i.e. on album art change), via `remember`'s
    // key comparison, instead of every draw call.
    val sweepBrush = remember(primary, secondary, tertiary) {
        Brush.sweepGradient(colors = listOf(primary, tertiary, secondary, tertiary, primary))
    }

    // One-shot fade-in on first composition (visualizer just turned on, or
    // the full player just opened with it already enabled) so it eases onto
    // screen instead of popping in at full opacity. Self-contained here so
    // it doesn't touch how the caller decides whether to show this at all.
    val mountAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        mountAlpha.animateTo(1f, tween(280))
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { invisibleToUser() }
            .graphicsLayer {
                // Reading ringScaleValue/rotation here (draw phase) instead of
                // via Modifier.scale()/.rotate() at the composable's top level
                // is what actually stops this from recomposing every frame.
                scaleX = ringScaleValue
                scaleY = ringScaleValue
                rotationZ = rotation * 0.15f
                alpha = mountAlpha.value
            }
    ) {
        // Reading the version counter here (draw phase, inside DrawScope)
        // is what ties redraw to the per-frame loop without ever touching
        // Compose state read in the composable body.
        @Suppress("UNUSED_EXPRESSION") smoothedVersion

        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension / 2.16f
        val minBarLen = 3.dp.toPx()
        val maxBarLen = 40.dp.toPx() // Compact but high relative movement
        val minBarWidth = 2.5.dp.toPx()
        val maxBarWidth = 6.dp.toPx()
        val cornerR = CornerRadius(maxBarWidth / 2f, maxBarWidth / 2f)

        // Reused every frame instead of two fresh Path() allocations per
        // draw call — the same steady-state-allocation fix already applied
        // to scratchBarPath/scratchMatrix below, just extended to the two
        // top-level paths that were still being rebuilt from scratch.
        mainPath.reset()
        glowPath.reset()

        for (i in 0 until VIS_BAR_COUNT) {
            val amplitude = displayed[i].coerceIn(0f, 1f)
            val barLen = minBarLen + (maxBarLen - minBarLen) * amplitude
            // M3 Expressive signature touch: the capsule itself widens with
            // loudness, not just stretches — quiet bars are thin ticks, loud
            // ones bloom into pills, instead of every bar sharing one fixed
            // thickness.
            val barWidth = minBarWidth + (maxBarWidth - minBarWidth) * amplitude
            val dirX = barDirections[i * 2]
            val dirY = barDirections[i * 2 + 1]
            val innerR = baseRadius
            val outerR = baseRadius + barLen

            // Build each bar as an actual rounded-rect capsule (rotated into
            // place) instead of a stroked line — reads as a real M3 pill
            // shape and lets width and length vary independently.
            val midR = (innerR + outerR) / 2f
            val midX = center.x + dirX * midR
            val midY = center.y + dirY * midR
            val angleDeg = Math.toDegrees(kotlin.math.atan2(dirY.toDouble(), dirX.toDouble())).toFloat()

            val rect = androidx.compose.ui.geometry.Rect(
                left = midX - (outerR - innerR) / 2f,
                top = midY - barWidth / 2f,
                right = midX + (outerR - innerR) / 2f,
                bottom = midY + barWidth / 2f
            )
            val barMatrix = scratchMatrix.apply {
                reset()
                translate(midX, midY, 0f)
                rotateZ(angleDeg)
                translate(-midX, -midY, 0f)
            }

            val barShape = scratchBarPath.apply {
                reset()
                addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, cornerR))
                transform(barMatrix)
            }
            mainPath.addPath(barShape)
            if (!reducedEffects && isPlaying && amplitude > 0.45f) {
                glowPath.addPath(barShape)
            }
        }

        // Sweeping conic-style tint: a rotating brush reads as a single
        // cohesive ring color rather than N flat-tinted segments, at zero
        // extra per-bar cost. `sweepBrush` is cached above (recomputed only
        // when the dynamic colors change) since sweepGradient defaults to
        // centering on the drawn shape's own bounds, so it doesn't need a
        // per-frame `center` recalculated from `size` here.
        if (!glowPath.isEmpty) {
            drawPath(path = glowPath, brush = sweepBrush, alpha = 0.30f)
        }
        drawPath(
            path = mainPath,
            brush = sweepBrush,
            alpha = if (isPlaying) 0.9f else 0.35f
        )

        // Soft ambient glow tying the ring back to the artwork underneath.
        if (!reducedEffects) {
            val outerGlowR = baseRadius + maxBarLen + 40.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = if (isPlaying) 0.16f else 0.08f), Color.Transparent),
                    center = center,
                    radius = outerGlowR
                ),
                radius = outerGlowR,
                center = center
            )
        }

        // Thin inner contact ring right at the art's edge — anchors the bars visually.
        drawCircle(
            color = primary.copy(alpha = 0.22f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sleep Timer Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
 fun SleepTimerSheet(
    currentTimerActive: Boolean,
    remainingMs: Long?,
    lastCustomMinutes: Int = 20,
    onSet: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column {
                        Text("Sleep Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(
                            if (currentTimerActive && remainingMs != null) "Stops in ${formatDuration(remainingMs)}"
                            else "Music stops after selected time",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentTimerActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (currentTimerActive) {
                    ToolzExpressiveButton(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                        Text("Cancel", fontWeight = FontWeight.Black)
                    }
                }
            }

            if (currentTimerActive && remainingMs != null) {
                Spacer(Modifier.height(14.dp))
                val prog by animateFloatAsState(1f - (remainingMs.toFloat() / (90 * 60_000f)).coerceIn(0f, 1f), tween(800), label = "tP")
                com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = MaterialTheme.colorScheme.error, trackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.08f))
            Spacer(Modifier.height(16.dp))

            listOf(listOf(5, 10, 15), listOf(30, 45, 60), listOf(90)).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { mins ->
                        ToolzExpressiveButton(
                            onClick = { onSet(mins) },
                            modifier = Modifier.weight(1f).height(70.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$mins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Text("min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}


private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

