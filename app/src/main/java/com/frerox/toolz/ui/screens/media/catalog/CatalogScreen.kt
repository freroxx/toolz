package com.frerox.toolz.ui.screens.media.catalog

import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogContent(
    catalogViewModel: CatalogViewModel,
    musicRepository: MusicRepository,
    localTracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    gridState: LazyGridState = rememberLazyGridState(),
    onPlayTrack: (Uri, String, String, String, String) -> Unit,
    onPlayInKaraoke: (Uri, String, String, String, String) -> Unit,
    onEnqueue: (CatalogTrack, Boolean) -> Unit
) {
    val state by catalogViewModel.uiState.collectAsState()
    val hasSeenOnboarding by catalogViewModel.hasSeenOnboarding.collectAsState()
    val view = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val configuration = LocalConfiguration.current
    val isDark = LocalIsDarkTheme.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val playlists by musicRepository.allPlaylists.collectAsState(initial = emptyList())
    val downloadedSourceUrls = remember(localTracks) {
        localTracks
            .filter { it.sourceUrl != null && (it.path != null || it.album == "Toolz Downloads") }
            .mapNotNull { it.sourceUrl }
            .toSet()
    }

    var selectedTrackForDownload by remember { mutableStateOf<CatalogTrack?>(null) }
    var trackForAction by remember { mutableStateOf<CatalogTrack?>(null) }
    var playlistTargetTrack by remember { mutableStateOf<CatalogTrack?>(null) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    var focusedTrack by remember { mutableStateOf<CatalogTrack?>(null) }
    var focusedTrackSize by remember { mutableStateOf(Size.Zero) }
    var focusedTrackRadius by remember { mutableStateOf(24.dp) }
    var shockwaveOffset by remember { mutableStateOf(Offset.Zero) }
    var showShockwave by remember { mutableStateOf(false) }
    val isReturning = remember { mutableStateOf(false) }
    var rootCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    fun resetFocus() {
        isReturning.value = true
        scope.launch {
            delay(450) 
            focusedTrack = null
            isReturning.value = false
        }
    }

    LaunchedEffect(focusedTrack, state.error) {
        if (state.error != null) {
            resetFocus()
        }
        if (focusedTrack != null && !isReturning.value) {
            delay(12000) // 12 seconds safety timeout
            resetFocus()
        }
    }

    val blurAlpha by animateFloatAsState(
        targetValue = if (focusedTrack != null && !isReturning.value) 1f else 0f,
        animationSpec = tween(if (isReturning.value) 500 else 800, easing = FastOutSlowInEasing),
        label = "blurAlpha"
    )

    // Using a lambda for blur to avoid recompositions
    val getBlurAlpha = remember { { blurAlpha } }

    var focusedTrackLayoutMode by remember { mutableStateOf<LayoutMode>(LayoutMode.LIST) }

    val onResolveAndPlay: (CatalogTrack, androidx.compose.ui.layout.LayoutCoordinates, androidx.compose.ui.unit.Dp, LayoutMode) -> Unit = { track, coords, radius, mode ->
        if (focusedTrack == null && rootCoordinates != null) {
            val positionInRoot = rootCoordinates!!.localPositionOf(coords, Offset.Zero)
            focusedTrack = track
            focusedTrackLayoutMode = mode
            focusedTrackSize = Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            focusedTrackRadius = radius
            shockwaveOffset = positionInRoot
            showShockwave = true
            isReturning.value = false
            vibrationManager?.vibrateClick()
                ?: view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            
            catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                resetFocus()
            }
        }
    }

    if (!hasSeenOnboarding) {
        CatalogOnboardingDialog(onDismiss = { catalogViewModel.dismissOnboarding() })
    }

    selectedTrackForDownload?.let { track ->
        val format by catalogViewModel.downloadFormat.collectAsState()
        val quality by catalogViewModel.downloadQuality.collectAsState()
        DownloadOptionsBottomSheet(
            onDismiss = { selectedTrackForDownload = null },
            currentFormat = format,
            currentQuality = quality,
            onFormatSelected = { catalogViewModel.setDownloadFormat(it) },
            onQualitySelected = { catalogViewModel.setDownloadQuality(it) },
            onStartDownload = {
                catalogViewModel.downloadTrack(track)
                selectedTrackForDownload = null
            }
        )
    }

    trackForAction?.let { track ->
        CatalogTrackActionsSheet(
            track = track,
            isDownloaded = downloadedSourceUrls.contains(track.sourceUrl),
            onDismiss = { trackForAction = null },
            onPlayNow = {
                catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                    onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                }
                trackForAction = null
            },
            onPlayNext = {
                onEnqueue(track, true)
                trackForAction = null
            },
            onDownload = {
                selectedTrackForDownload = track
                trackForAction = null
            },
            onAddToPlaylist = {
                playlistTargetTrack = track
                trackForAction = null
                showPlaylistPicker = true
            },
            onOpenKaraoke = {
                catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                    onPlayInKaraoke(uri, title, artist, thumbUrl, sourceUrl)
                }
                trackForAction = null
            }
        )
    }

    if (showPlaylistPicker && playlistTargetTrack != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist", fontWeight = FontWeight.Black) },
            text = {
                if (playlists.isEmpty()) {
                    Text(
                        "Create a playlist in Library first, then you can save catalog tracks here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(playlists) { playlist ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                onClick = {
                                    playlistTargetTrack?.let { selected ->
                                        catalogViewModel.addToPlaylist(playlist, selected)
                                    }
                                    showPlaylistPicker = false
                                    playlistTargetTrack = null
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(playlist.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${playlist.trackUris.size} tracks",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPlaylistPicker = false
                    playlistTargetTrack = null
                }) { Text("Close") }
            }
        )
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 &&
                lastVisible >= totalItems - 6 &&
                !state.isLoading &&
                !state.isLoadingMore &&
                !state.isLoadingRecommendations &&
                !state.isLoadingMoreRecommendations
        }
    }

    LaunchedEffect(shouldLoadMore, state.query, state.justForYou.size, state.tracks.size) {
        if (shouldLoadMore) {
            catalogViewModel.loadMore()
        }
    }

    val localDensity = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val currentBlur = getBlurAlpha()
                    if (currentBlur > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blurPx = (currentBlur * 18 * localDensity.density).coerceAtLeast(0.1f)
                        renderEffect = android.graphics.RenderEffect
                            .createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.DECAL)
                            .asComposeRenderEffect()
                    }
                }
        ) {
            val columns = if (configuration.screenWidthDp > 600) 4 else 3
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ... (items remain the same)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogTopActions(
                        onToggleLayout = {
                            catalogViewModel.setLayoutMode(
                                if (state.layoutMode == LayoutMode.GRID) LayoutMode.LIST else LayoutMode.GRID
                            )
                        },
                        onRefresh = { catalogViewModel.loadStorefront(currentTrack) },
                        layoutMode = state.layoutMode
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    CatalogSearchBar(
                        query = state.query,
                        onQueryChange = { catalogViewModel.onSearchQueryChange(it) },
                        onClear = {
                            catalogViewModel.onSearchQueryChange("")
                            catalogViewModel.loadStorefront(currentTrack)
                        }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    GenreFilterChips(
                        selectedGenre = state.selectedGenre,
                        onGenreSelected = { catalogViewModel.onGenreSelected(it) }
                    )
                }

                state.error?.let { message ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorCard(
                            message = message,
                            onRetry = {
                                catalogViewModel.clearError()
                                if (state.query.isBlank()) catalogViewModel.loadStorefront(currentTrack)
                                else catalogViewModel.onSearchQueryChange(state.query)
                            }
                        )
                    }
                }

                if (state.query.isBlank()) {
                    if (state.isLoading) {
                        item(span = { GridItemSpan(maxLineSpan) }) { HeroSkeleton() }
                        item(span = { GridItemSpan(maxLineSpan) }) { CatalogSectionSkeleton(title = "Trending Now", listMode = state.layoutMode == LayoutMode.LIST) }
                        item(span = { GridItemSpan(maxLineSpan) }) { CatalogSectionSkeleton(title = "Just for you", listMode = true) }
                    } else {
                        if (state.quickPicks.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionHeader(
                                    title = "Discover",
                                    subtitle = "Rotate on refresh • Best of modern sounds",
                                    icon = Icons.Rounded.AutoAwesome
                                )
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                FeaturedCarousel(
                                    tracks = state.quickPicks,
                                    focusedTrackUrl = focusedTrack?.sourceUrl,
                                    onTrackClick = { track, coords ->
                                        onResolveAndPlay(track, coords, 24.dp, LayoutMode.GRID)
                                    },
                                    onLongClick = { track ->
                                        if (hapticEnabled) {
                                            vibrationManager?.vibrateLongClick()
                                                ?: view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        }
                                        trackForAction = track
                                    }
                                )
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                title = "Trending Now",
                                subtitle = "Eight sharper picks that rotate across refreshes",
                                icon = Icons.AutoMirrored.Rounded.TrendingUp
                            )
                        }
                        catalogTrackRows(
                            tracks = state.trending.take(9),
                            layoutMode = state.layoutMode,
                            downloadedSourceUrls = downloadedSourceUrls,
                            downloadingTracks = state.downloadingTracks,
                            focusedTrackUrl = focusedTrack?.sourceUrl,
                            onTrackClick = { track, coords, radius, mode ->
                                onResolveAndPlay(track, coords, radius, mode)
                            },
                            onTrackDownload = { selectedTrackForDownload = it },
                            onTrackLongPress = { track ->
                                if (hapticEnabled) {
                                    vibrationManager?.vibrateLongClick()
                                        ?: view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                trackForAction = track
                            }
                        )

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(
                                title = "Just for you",
                                subtitle = state.recommendationTitle.removePrefix("Just for you · ").ifBlank {
                                    "Built from what you're listening to and coming back to"
                                },
                                icon = Icons.Rounded.Star
                            )
                        }
                        if (state.isLoadingRecommendations && state.justForYou.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) { RecommendationSkeleton() }
                        } else {
                            catalogTrackRows(
                                tracks = state.justForYou,
                                layoutMode = state.layoutMode, // Now respects layout mode
                                downloadedSourceUrls = downloadedSourceUrls,
                                downloadingTracks = state.downloadingTracks,
                                focusedTrackUrl = focusedTrack?.sourceUrl,
                                onTrackClick = { track, coords, radius, mode ->
                                    onResolveAndPlay(track, coords, radius, mode)
                                },
                                onTrackDownload = { selectedTrackForDownload = it },
                                onTrackLongPress = { track ->
                                    if (hapticEnabled) {
                                        vibrationManager?.vibrateLongClick()
                                            ?: view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                    trackForAction = track
                                }
                            )
                        }
                        if (state.isLoadingMoreRecommendations) {
                            item(span = { GridItemSpan(maxLineSpan) }) { RecommendationSkeleton(compact = true) }
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = if (state.selectedGenre != null) state.selectedGenre!! else "Search Results",
                            subtitle = if (state.selectedGenre != null) "Genre mix with cleaner song-only results" else "Tap to play, long press for more actions",
                            icon = if (state.selectedGenre != null) Icons.Rounded.Category else Icons.Rounded.Search
                        )
                    }

                    if (state.isLoading) {
                        item(span = { GridItemSpan(maxLineSpan) }) { CatalogSectionSkeleton(title = "Loading", listMode = state.layoutMode == LayoutMode.LIST) }
                    } else if (state.tracks.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(56.dp).alpha(0.35f))
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "No songs found for \"${state.query}\"",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Try an artist name, a mood, or a cleaner title.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        catalogTrackRows(
                            tracks = state.tracks,
                            layoutMode = state.layoutMode,
                            downloadedSourceUrls = downloadedSourceUrls,
                            downloadingTracks = state.downloadingTracks,
                            focusedTrackUrl = focusedTrack?.sourceUrl,
                            onTrackClick = { track, coords, radius, mode ->
                                onResolveAndPlay(track, coords, radius, mode)
                            },
                            onTrackDownload = { selectedTrackForDownload = it },
                            onTrackLongPress = { track ->
                                if (hapticEnabled) {
                                    vibrationManager?.vibrateLongClick()
                                        ?: view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                trackForAction = track
                            }
                        )
                    }

                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) { RecommendationSkeleton(compact = true) }
                    }
                }
            }
        }

        if (showShockwave && !performanceMode) {
            val dynamicColorsForShockwave = rememberDynamicColors(
                artworkUri = focusedTrack?.thumbnailUrl,
                isDark = isDark
            )
            
            ShockwaveOverlay(
                offset = shockwaveOffset,
                size = focusedTrackSize,
                radius = focusedTrackRadius,
                color = dynamicColorsForShockwave.primary,
                onFinished = { showShockwave = false }
            )
        }

        focusedTrack?.let { track ->
            if (!performanceMode) {
                val dynamicColorsForFocus = rememberDynamicColors(
                    artworkUri = track.thumbnailUrl,
                    isDark = isDark
                )
                FocusedTrackOverlay(
                    track = track,
                    offset = shockwaveOffset,
                    size = focusedTrackSize,
                    radius = focusedTrackRadius,
                    color = dynamicColorsForFocus.primary,
                    layoutMode = focusedTrackLayoutMode,
                    isDiscover = state.quickPicks.any { it.sourceUrl == track.sourceUrl },
                    isReturning = isReturning.value
                )
            }
        }
    }
}

private fun LazyGridScope.catalogTrackRows(
    tracks: List<CatalogTrack>,
    layoutMode: LayoutMode,
    downloadedSourceUrls: Set<String>,
    downloadingTracks: Map<String, Float>,
    focusedTrackUrl: String?,
    onTrackClick: (CatalogTrack, androidx.compose.ui.layout.LayoutCoordinates, androidx.compose.ui.unit.Dp, LayoutMode) -> Unit,
    onTrackDownload: (CatalogTrack) -> Unit,
    onTrackLongPress: (CatalogTrack) -> Unit
) {
    if (layoutMode == LayoutMode.LIST) {
        this.itemsIndexed(
            items = tracks,
            key = { _, track -> track.sourceUrl },
            span = { _, _ -> GridItemSpan(this.maxLineSpan) }
        ) { index, track ->
            var itemCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            
            val entryAlpha = remember { Animatable(0f) }
            val entryOffsetY = remember { Animatable(30f) }
            
            LaunchedEffect(track.sourceUrl) {
                delay((index % 8) * 60L)
                launch { entryAlpha.animateTo(1f, tween(500)) }
                launch { entryOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
            }

            CatalogListCard(
                track = track,
                isDownloaded = downloadedSourceUrls.contains(track.sourceUrl),
                progress = downloadingTracks[track.sourceUrl],
                isFocused = focusedTrackUrl == track.sourceUrl,
                onClick = { itemCoords?.let { onTrackClick(track, it, 24.dp, LayoutMode.LIST) } },
                onDownload = { onTrackDownload(track) },
                onLongPress = { onTrackLongPress(track) },
                onMore = { onTrackLongPress(track) },
                modifier = Modifier
                    .graphicsLayer {
                        alpha = entryAlpha.value
                        translationY = entryOffsetY.value
                    }
                    .onGloballyPositioned { itemCoords = it }
            )
        }
    } else {
        this.itemsIndexed(
            items = tracks,
            key = { _, track -> track.sourceUrl }
        ) { index, track ->
            var itemCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            
            val entryAlpha = remember { Animatable(0f) }
            val entryOffsetY = remember { Animatable(30f) }
            
            LaunchedEffect(track.sourceUrl) {
                delay((index % 12) * 50L)
                launch { entryAlpha.animateTo(1f, tween(500)) }
                launch { entryOffsetY.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
            }

            CatalogGridCard(
                track = track,
                isDownloaded = downloadedSourceUrls.contains(track.sourceUrl),
                progress = downloadingTracks[track.sourceUrl],
                isFocused = focusedTrackUrl == track.sourceUrl,
                onClick = { itemCoords?.let { onTrackClick(track, it, 24.dp, LayoutMode.GRID) } },
                onDownload = { onTrackDownload(track) },
                onLongPress = { onTrackLongPress(track) },
                onMore = { onTrackLongPress(track) },
                modifier = Modifier
                    .graphicsLayer {
                        alpha = entryAlpha.value
                        translationY = entryOffsetY.value
                    }
                    .onGloballyPositioned { itemCoords = it }
            )
        }
    }
}

@Composable
private fun CatalogTopActions(
    layoutMode: LayoutMode,
    onToggleLayout: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Catalog",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Discover and stream",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = onRefresh, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Refresh, null)
            }
            FilledTonalIconButton(onClick = onToggleLayout, shape = RoundedCornerShape(16.dp)) {
                Icon(if (layoutMode == LayoutMode.GRID) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, null)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FocusedTrackOverlay(
    track: CatalogTrack,
    offset: Offset,
    size: Size,
    radius: androidx.compose.ui.unit.Dp,
    color: Color,
    layoutMode: LayoutMode,
    isDiscover: Boolean,
    isReturning: Boolean
) {
    val overlayScaleTarget = if (layoutMode == LayoutMode.LIST) 1.04f else 1.08f
    val scale by animateFloatAsState(
        targetValue = if (isReturning) 1f else overlayScaleTarget,
        animationSpec = tween(
            durationMillis = if (isReturning) 500 else 700,
            easing = FastOutSlowInEasing
        ),
        label = "overlayScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isReturning) 0f else 1f,
        animationSpec = tween(if (isReturning) 450 else 500),
        label = "overlayAlpha"
    )
    
    val getScale = remember { { scale } }
    val getAlpha = remember { { alpha } }

    val density = LocalDensity.current
    
    val motionBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val blur = if (!isReturning) ( (1f - alpha) * 12f) else 0f
        if (blur > 0.1f) android.graphics.RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL) else null
    } else null

    Box(modifier = Modifier.fillMaxSize().graphicsLayer { 
        this.alpha = getAlpha()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderEffect = motionBlur?.asComposeRenderEffect()
        }
    }) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                .graphicsLayer {
                    scaleX = getScale()
                    scaleY = getScale()
                    shadowElevation = if (isReturning) 0f else 40.dp.toPx()
                    spotShadowColor = color.copy(alpha = 0.6f)
                    ambientShadowColor = color.copy(alpha = 0.6f)
                    clip = true
                    shape = RoundedCornerShape(radius)
                }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(radius))
        ) {
            if (isDiscover) {
                FeaturedCarouselItem(track = track, isFocused = true, color = color, staticScale = true)
            } else if (layoutMode == LayoutMode.GRID) {
                CatalogGridCard(
                    track = track,
                    isDownloaded = false,
                    progress = null,
                    isFocused = true,
                    staticScale = true,
                    onClick = {},
                    onDownload = {},
                    onLongPress = {},
                    onMore = {}
                )
            } else {
                CatalogListCard(
                    track = track,
                    isDownloaded = false,
                    progress = null,
                    isFocused = true,
                    staticScale = true,
                    onClick = {},
                    onDownload = {},
                    onLongPress = {},
                    onMore = {}
                )
            }
        }
    }
}

@Composable
private fun FeaturedCarousel(
    tracks: List<CatalogTrack>,
    focusedTrackUrl: String?,
    onTrackClick: (CatalogTrack, androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    onLongClick: (CatalogTrack) -> Unit
) {
    val rowState = rememberLazyListState()
    
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val colors = listOf(Color.Black, Color.Transparent)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = colors,
                        startX = size.width - 60f,
                        endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = colors.reversed(),
                        startX = 0f,
                        endX = 60f
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        itemsIndexed(tracks, key = { _, track -> track.sourceUrl }) { index, track ->
            var itemCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            val isFocused = focusedTrackUrl == track.sourceUrl
            
            val entryAlpha = remember { Animatable(0f) }
            val entryOffsetY = remember { Animatable(20f) }
            
            LaunchedEffect(Unit) {
                delay(index * 100L)
                launch { entryAlpha.animateTo(1f, tween(600)) }
                launch { entryOffsetY.animateTo(0f, tween(600, easing = FastOutSlowInEasing)) }
            }

            // Calculate parallax offset based on scroll position
            val parallaxOffset by remember {
                derivedStateOf {
                    val layoutInfo = rowState.layoutInfo
                    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                    if (itemInfo != null) {
                        val center = (layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset) / 2f
                        val itemCenter = (itemInfo.offset + itemInfo.size / 2f)
                        (itemCenter - center) * 0.08f // Adjust strength
                    } else 0f
                }
            }

            FeaturedCarouselItem(
                track = track,
                isFocused = isFocused,
                parallaxOffset = parallaxOffset,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = entryAlpha.value
                        translationY = entryOffsetY.value
                    }
                    .onGloballyPositioned { itemCoords = it }
                    .combinedClickable(
                        onClick = { itemCoords?.let { onTrackClick(track, it) } },
                        onLongClick = { onLongClick(track) }
                    )
            )
        }
    }
}

@Composable
private fun FeaturedCarouselItem(
    modifier: Modifier = Modifier,
    track: CatalogTrack,
    isFocused: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    staticScale: Boolean = false,
    parallaxOffset: Float = 0f
) {
    val scale by animateFloatAsState(if (isFocused && !staticScale) 1.05f else 1f, label = "featuredScale")
    
    Surface(
        modifier = modifier
            .width(240.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = if (isFocused) BorderStroke(3.dp, color) else null
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(24.dp))) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .graphicsLayer { translationX = parallaxOffset },
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = track.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(1f, 1f),
                            blurRadius = 2f
                        )
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GenreFilterChips(
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit
) {
    val genres = listOf(
        "Pop", "Hip Hop", "R&B", "Afrobeats", "Amapiano", "Electronic", "Rock", "Jazz", "K-Pop", "Lo-fi"
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        item {
            ExpressiveFilterChip(
                selected = selectedGenre == null,
                onClick = { onGenreSelected(null) },
                label = "All",
                icon = Icons.Rounded.LibraryMusic
            )
        }
        items(genres) { genre ->
            ExpressiveFilterChip(
                selected = selectedGenre == genre,
                onClick = { onGenreSelected(if (selectedGenre == genre) null else genre) },
                label = genre
            )
        }
    }
}

@Composable
private fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, label = "chipScale")
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(if (selected) 16.dp else 24.dp)
    
    Surface(
        onClick = onClick,
        modifier = Modifier.graphicsLayer { 
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        color = backgroundColor,
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(16.dp), tint = contentColor)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun CatalogSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search songs, artists, moods...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogGridCard(
    modifier: Modifier = Modifier,
    track: CatalogTrack,
    isDownloaded: Boolean,
    progress: Float?,
    isFocused: Boolean = false,
    staticScale: Boolean = false,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onLongPress: () -> Unit,
    onMore: () -> Unit
) {
    val scale by animateFloatAsState(if (isFocused && !staticScale) 1.05f else 1f, label = "gridScale")
    Surface(
        modifier = modifier
            .aspectRatio(0.85f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isFocused) 20.dp.toPx() else 0f
            }
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = if (isFocused) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DurationBadge(track.duration)
                        DownloadButton(
                            isDownloaded = isDownloaded,
                            progress = progress,
                            onDownload = onDownload,
                            compact = true
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CatalogListCard(
    modifier: Modifier = Modifier,
    track: CatalogTrack,
    isDownloaded: Boolean,
    progress: Float?,
    isFocused: Boolean = false,
    staticScale: Boolean = false,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onLongPress: () -> Unit,
    onMore: () -> Unit
) {
    val scale by animateFloatAsState(if (isFocused && !staticScale) 1.03f else 1f, label = "listScale")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isFocused) 15.dp.toPx() else 0f
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DownloadButton(
                    isDownloaded = isDownloaded,
                    progress = progress,
                    onDownload = onDownload,
                    compact = true
                )
                IconButton(
                    onClick = onMore,
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(Icons.Rounded.MoreHoriz, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactDurationBadge(duration: Long) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Rounded.WifiTethering,
                null,
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatDuration(duration),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DurationBadge(duration: Long) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Text(
            text = formatDuration(duration),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DownloadButton(
    isDownloaded: Boolean,
    progress: Float?,
    onDownload: () -> Unit,
    compact: Boolean = false
) {
    AnimatedContent(
        targetState = when {
            isDownloaded -> 2
            progress != null -> 1
            else -> 0
        },
        label = "catalogDownloadButton"
    ) { state ->
        when (state) {
            2 -> Surface(
                modifier = Modifier.size(if (compact) 34.dp else 40.dp),
                shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                    )
                }
            }

            1 -> Box(
                modifier = Modifier
                    .size(if (compact) 34.dp else 40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress ?: 0f },
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(if (compact) 24.dp else 28.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            else -> IconButton(
                onClick = onDownload,
                modifier = Modifier.size(if (compact) 34.dp else 40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Rounded.FileDownload,
                    contentDescription = "Download",
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.82f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun HeroSkeleton() {
    val brush = shimmerBrush()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogSectionSkeleton(title: String, listMode: Boolean) {
    val brush = shimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
        if (listMode) {
            repeat(3) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(brush)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(brush)
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.4f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(brush)
                            )
                        }
                    }
                }
            }
        } else {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.74f)
                                .clip(RoundedCornerShape(26.dp))
                                .background(brush)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationSkeleton(compact: Boolean = false) {
    val brush = shimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(if (compact) 2 else 4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "catalogShimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "catalogShimmerShift"
    )

    return Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        start = Offset(shift - 600f, shift - 600f),
        end = Offset(shift, shift)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTrackActionsSheet(
    track: CatalogTrack,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenKaraoke: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(formatDuration(track.duration)) }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (isDownloaded) "Downloaded" else "Online") },
                            leadingIcon = {
                                Icon(
                                    if (isDownloaded) Icons.Rounded.Check else Icons.Rounded.GraphicEq,
                                    null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            CatalogActionRow("Play now", Icons.Rounded.PlayArrow, onPlayNow)
            CatalogActionRow("Play next", Icons.AutoMirrored.Rounded.QueueMusic, onPlayNext)
            CatalogActionRow(if (isDownloaded) "Download saved" else "Download", Icons.Rounded.Download, onDownload, enabled = !isDownloaded)
            CatalogActionRow("Add to playlist", Icons.AutoMirrored.Rounded.PlaylistAdd, onAddToPlaylist)
            CatalogActionRow("Play in karaoke", Icons.Rounded.MicExternalOn, onOpenKaraoke)
        }
    }
}

@Composable
private fun CatalogActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.95f else 0.55f),
        onClick = if (enabled) onClick else { {} }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun CatalogOnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.size(78.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Science, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Welcome to Catalog",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                "Search, stream, and download online songs with a cleaner Discover experience. Long press any track for quicker actions.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Start exploring", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(32.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsBottomSheet(
    onDismiss: () -> Unit,
    currentFormat: String,
    currentQuality: String,
    onFormatSelected: (String) -> Unit,
    onQualitySelected: (String) -> Unit,
    onStartDownload: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text("Download", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose how the track should be saved for offline playback.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            Text("Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("M4A", "OPUS", "MP3").forEachIndexed { index, format ->
                    SegmentedButton(
                        selected = currentFormat == format,
                        onClick = { onFormatSelected(format) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                    ) {
                        Text(format)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Quality", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("HIGH", "MEDIUM", "LOW").forEach { quality ->
                    Surface(
                        onClick = { onQualitySelected(quality) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (currentQuality == quality) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentQuality == quality, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(quality, fontWeight = FontWeight.Bold)
                                Text(
                                    when (quality) {
                                        "HIGH" -> "Largest file, best detail"
                                        "MEDIUM" -> "Balanced quality and size"
                                        else -> "Smaller file, lighter download"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onStartDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("Start download", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinningDownloadPopup(
    track: CatalogTrack,
    progress: Float,
    onCancel: () -> Unit,
    onHide: () -> Unit
) {
    val rotation = rememberInfiniteTransition(label = "catalogDownloadRotation")
    val angle by rotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart),
        label = "catalogDownloadAngle"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "catalogDownloadProgress"
    )

    AlertDialog(
        onDismissRequest = onHide,
        confirmButton = {},
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(164.dp),
                        strokeWidth = 10.dp,
                        strokeCap = StrokeCap.Round
                    )
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .graphicsLayer { rotationZ = angle },
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onHide,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Hide")
                    }
                }
            }
        },
        shape = RoundedCornerShape(30.dp)
    )
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun ShockwaveOverlay(
    offset: Offset,
    size: Size,
    radius: androidx.compose.ui.unit.Dp,
    color: Color,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(2000, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))
        )
        onFinished()
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Animate alpha via graphicsLayer to keep it on the render thread
                alpha = (1f - progress.value)
            }
    ) {
        val expansion = progress.value * max(this.size.width, this.size.height) * 0.75f
        val strokeWidth = (80 * (1f - progress.value)).dp.toPx()
        val rectRadius = with(density) { radius.toPx() }
        
        drawRoundRect(
            color = color,
            topLeft = Offset(offset.x - expansion, offset.y - expansion),
            size = Size(size.width + expansion * 2, size.height + expansion * 2),
            cornerRadius = CornerRadius(rectRadius + expansion),
            style = Stroke(width = strokeWidth),
            alpha = 0.35f
        )
        
        // Subtler second layer
        val expansion2 = progress.value * max(this.size.width, this.size.height) * 0.45f
        drawRoundRect(
            color = color,
            topLeft = Offset(offset.x - expansion2, offset.y - expansion2),
            size = Size(size.width + expansion2 * 2, size.height + expansion2 * 2),
            cornerRadius = CornerRadius(rectRadius + expansion2),
            style = Stroke(width = strokeWidth * 0.4f),
            alpha = 0.15f
        )
    }
}
