package com.frerox.toolz.ui.screens.media.catalog

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogContent(
    catalogViewModel: CatalogViewModel,
    musicRepository: MusicRepository,
    localTracks: List<MusicTrack>,
    currentTrack: MusicTrack?,
    onPlayTrack: (Uri, String, String, String, String) -> Unit,
    onPlayInKaraoke: (Uri, String, String, String, String) -> Unit,
    onEnqueue: (CatalogTrack, Boolean) -> Unit
) {
    val state by catalogViewModel.uiState.collectAsState()
    val hasSeenOnboarding by catalogViewModel.hasSeenOnboarding.collectAsState()
    val view = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    val vibrationManager = LocalVibrationManager.current
    val configuration = LocalConfiguration.current
    val isDark = LocalIsDarkTheme.current
    val listState = rememberLazyListState()

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

    val featuredTrack = state.quickPicks.firstOrNull()
    val dynamicColors = rememberDynamicColors(
        artworkUri = featuredTrack?.thumbnailUrl ?: currentTrack?.thumbnailUri,
        isDark = isDark
    )

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
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 &&
                lastVisible >= totalItems - 4 &&
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            dynamicColors.primary.copy(alpha = if (isDark) 0.10f else 0.07f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
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

            item {
                CatalogSearchBar(
                    query = state.query,
                    onQueryChange = { catalogViewModel.onSearchQueryChange(it) },
                    onClear = {
                        catalogViewModel.onSearchQueryChange("")
                        catalogViewModel.loadStorefront(currentTrack)
                    }
                )
            }

            item {
                GenreFilterChips(
                    selectedGenre = state.selectedGenre,
                    onGenreSelected = { catalogViewModel.onGenreSelected(it) }
                )
            }

            state.error?.let { message ->
                item {
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
                    item { HeroSkeleton() }
                    item { CatalogSectionSkeleton(title = "Trending Now", listMode = state.layoutMode == LayoutMode.LIST) }
                    item { CatalogSectionSkeleton(title = "Just for you", listMode = true) }
                } else {
                    if (state.quickPicks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Discover",
                                subtitle = "Fresh picks that rotate when you reopen Catalog",
                                icon = Icons.Rounded.AutoAwesome
                            )
                        }
                        item {
                            FeaturedCarousel(
                                tracks = state.quickPicks,
                                onTrackClick = { track ->
                                    catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                                        onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                                    }
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

                    item {
                        SectionHeader(
                            title = "Trending Now",
                            subtitle = "Eight sharper picks that rotate across refreshes",
                            icon = Icons.AutoMirrored.Rounded.TrendingUp
                        )
                    }
                    catalogTrackRows(
                        tracks = state.trending.take(8),
                        layoutMode = state.layoutMode,
                        columns = if (configuration.screenWidthDp > 600) 3 else 2,
                        downloadedSourceUrls = downloadedSourceUrls,
                        downloadingTracks = state.downloadingTracks,
                        onTrackClick = { track ->
                            catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                                onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                            }
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

                    item {
                        SectionHeader(
                            title = "Just for you",
                            subtitle = state.recommendationTitle.removePrefix("Just for you · ").ifBlank {
                                "Built from what you're listening to and coming back to"
                            },
                            icon = Icons.Rounded.Star
                        )
                    }
                    if (state.isLoadingRecommendations && state.justForYou.isEmpty()) {
                        item { RecommendationSkeleton() }
                    } else {
                        catalogTrackRows(
                            tracks = state.justForYou,
                            layoutMode = LayoutMode.LIST,
                            columns = 1,
                            downloadedSourceUrls = downloadedSourceUrls,
                            downloadingTracks = state.downloadingTracks,
                            onTrackClick = { track ->
                                catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                                    onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                                }
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
                        item { RecommendationSkeleton(compact = true) }
                    }
                }
            } else {
                item {
                    SectionHeader(
                        title = if (state.selectedGenre != null) state.selectedGenre!! else "Search Results",
                        subtitle = if (state.selectedGenre != null) "Genre mix with cleaner song-only results" else "Tap to play, long press for more actions",
                        icon = if (state.selectedGenre != null) Icons.Rounded.Category else Icons.Rounded.Search
                    )
                }

                if (state.isLoading) {
                    item { CatalogSectionSkeleton(title = "Loading", listMode = state.layoutMode == LayoutMode.LIST) }
                } else if (state.tracks.isEmpty()) {
                    item {
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
                        columns = if (configuration.screenWidthDp > 600) 3 else 2,
                        downloadedSourceUrls = downloadedSourceUrls,
                        downloadingTracks = state.downloadingTracks,
                        onTrackClick = { track ->
                            catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                                onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                            }
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
                    item { RecommendationSkeleton(compact = true) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.catalogTrackRows(
    tracks: List<CatalogTrack>,
    layoutMode: LayoutMode,
    columns: Int,
    downloadedSourceUrls: Set<String>,
    downloadingTracks: Map<String, Float>,
    onTrackClick: (CatalogTrack) -> Unit,
    onTrackDownload: (CatalogTrack) -> Unit,
    onTrackLongPress: (CatalogTrack) -> Unit,
    onTrackMore: (CatalogTrack) -> Unit = onTrackLongPress
) {
    if (layoutMode == LayoutMode.LIST || columns == 1) {
        items(tracks, key = { it.sourceUrl }) { track ->
            CatalogListCard(
                track = track,
                isDownloaded = downloadedSourceUrls.contains(track.sourceUrl),
                progress = downloadingTracks[track.id],
                onClick = { onTrackClick(track) },
                onDownload = { onTrackDownload(track) },
                onLongPress = { onTrackLongPress(track) },
                onMore = { onTrackMore(track) }
            )
        }
        return
    }

    items(tracks.chunked(columns), key = { row -> row.joinToString(separator = "|") { it.id } }) { rowTracks ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowTracks.forEach { track ->
                CatalogGridCard(
                    modifier = Modifier.weight(1f),
                    track = track,
                    isDownloaded = downloadedSourceUrls.contains(track.sourceUrl),
                    progress = downloadingTracks[track.id],
                    onClick = { onTrackClick(track) },
                    onDownload = { onTrackDownload(track) },
                    onLongPress = { onTrackLongPress(track) },
                    onMore = { onTrackMore(track) }
                )
            }
            repeat(columns - rowTracks.size) {
                Spacer(Modifier.weight(1f))
            }
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
        horizontalArrangement = Arrangement.End
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalIconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
            FilledTonalIconButton(onClick = onToggleLayout) {
                Icon(
                    imageVector = if (layoutMode == LayoutMode.GRID) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = "Toggle layout"
                )
            }
        }
    }
}

@Composable
private fun CatalogHeroHeader(
    state: CatalogUiState,
    accent: Color,
    onToggleLayout: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Discover",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.query.isBlank()) "Catalog refreshes every time you step back in, so these picks stay lively."
                        else "Song-first search with quick actions, cleaner results, and faster re-entry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggleLayout,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = if (state.layoutMode == LayoutMode.GRID) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = "Toggle layout"
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricChip("Trending", "${state.trending.size} live picks", Icons.AutoMirrored.Rounded.TrendingUp)
                MetricChip("For you", "${state.justForYou.size} tailored songs", Icons.Rounded.Star)
                MetricChip("Layout", if (state.layoutMode == LayoutMode.GRID) "Grid cards" else "List flow", Icons.Rounded.Tune)
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeaturedCarousel(
    tracks: List<CatalogTrack>,
    onTrackClick: (CatalogTrack) -> Unit,
    onLongClick: (CatalogTrack) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(tracks, key = { it.sourceUrl }) { track ->
            val chipContainer = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            val chipContent = MaterialTheme.colorScheme.onSurface
            Surface(
                modifier = Modifier
                    .width(250.dp)
                    .combinedClickable(onClick = { onTrackClick(track) }, onLongClick = { onLongClick(track) }),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            ) {
                Box {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = track.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.22f),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.10f),
                                        Color.Black.copy(alpha = 0.72f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        AssistChip(
                            onClick = { onTrackClick(track) },
                            label = { Text("Play now") },
                            leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = chipContainer,
                                labelColor = chipContent,
                                leadingIconContentColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            track.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${track.artist} · ${formatDuration(track.duration)}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FilterChip(
                selected = selectedGenre == null,
                onClick = { onGenreSelected(null) },
                label = { Text("All") },
                leadingIcon = if (selectedGenre == null) {
                    { Icon(Icons.Rounded.LibraryMusic, null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors()
            )
        }
        items(genres) { genre ->
            FilterChip(
                selected = selectedGenre == genre,
                onClick = { onGenreSelected(if (selectedGenre == genre) null else genre) },
                label = { Text(genre) }
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        Text(
                            "Search songs, artists, moods...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
            AnimatedVisibility(visible = query.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(Icons.Rounded.Close, null)
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
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onLongPress: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(26.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                        .padding(12.dp)
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
                IconButton(
                    onClick = onMore,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.28f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "More")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CatalogListCard(
    track: CatalogTrack,
    isDownloaded: Boolean,
    progress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onLongPress: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
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
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(74.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(formatDuration(track.duration)) },
                        leadingIcon = { Icon(Icons.Rounded.WifiTethering, null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onMore,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More")
            }
            Spacer(Modifier.width(4.dp))
            DownloadButton(
                isDownloaded = isDownloaded,
                progress = progress,
                onDownload = onDownload
            )
        }
    }
}

@Composable
private fun DurationBadge(duration: Long) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Text(
            text = formatDuration(duration),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
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
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        if (listMode) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(94.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(brush)
                )
            }
        } else {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.8f)
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
        targetValue = 1_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "catalogShimmerShift"
    )

    return Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        ),
        start = Offset(shift - 320f, shift - 320f),
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
