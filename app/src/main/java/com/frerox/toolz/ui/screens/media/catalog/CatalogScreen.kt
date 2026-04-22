package com.frerox.toolz.ui.screens.media.catalog

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

// ─────────────────────────────────────────────────────────────────────────────
// Root Catalog Content
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogContent(
    catalogViewModel: CatalogViewModel,
    musicRepository: MusicRepository,
    localTracks: List<com.frerox.toolz.data.music.MusicTrack>,
    onPlayTrack: (Uri, String, String, String, String) -> Unit,
    onEnqueue: (CatalogTrack, Boolean) -> Unit
) {
    val state by catalogViewModel.uiState.collectAsState()
    val hasSeenOnboarding by catalogViewModel.hasSeenOnboarding.collectAsState()
    val showBetaCard by catalogViewModel.showBetaCard.collectAsState()
    val listState = rememberLazyListState()
    val performanceMode = LocalPerformanceMode.current
    val view = LocalView.current
    val hapticEnabled = LocalHapticEnabled.current
    val vibrationManager = LocalVibrationManager.current
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrackForMenu by remember { mutableStateOf<CatalogTrack?>(null) }
    var showTrackMenu by remember { mutableStateOf(false) }
    var trackForAction by remember { mutableStateOf<CatalogTrack?>(null) }
    
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val musicUiState by musicRepository.allPlaylists.collectAsState(initial = emptyList())

    // Onboarding Dialog
    if (!hasSeenOnboarding) {
        CatalogOnboardingDialog(onDismiss = { catalogViewModel.dismissOnboarding() })
    }

    // Quality Bottom Sheet
    if (showBottomSheet && selectedTrackForMenu != null) {
        val format by catalogViewModel.downloadFormat.collectAsState()
        val quality by catalogViewModel.downloadQuality.collectAsState()
        
        DownloadOptionsBottomSheet(
            onDismiss = { showBottomSheet = false },
            currentFormat = format,
            currentQuality = quality,
            onFormatSelected = { catalogViewModel.setDownloadFormat(it) },
            onQualitySelected = { catalogViewModel.setDownloadQuality(it) },
            onStartDownload = selectedTrackForMenu?.let { track ->
                {
                    catalogViewModel.downloadTrack(track)
                    showBottomSheet = false
                }
            } ?: {}
        )
    }

    // Track Action Menu
    if (showTrackMenu && trackForAction != null) {
        ModalBottomSheet(
            onDismissRequest = { showTrackMenu = false },
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                // Track Info Header
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = trackForAction!!.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(trackForAction!!.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(trackForAction!!.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                
                ListItem(
                    headlineContent = { Text("Play Next") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                    modifier = Modifier.clickable { 
                        onEnqueue(trackForAction!!, true)
                        showTrackMenu = false 
                    }
                )
                ListItem(
                    headlineContent = { Text("Add to Playlist") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    modifier = Modifier.clickable { 
                        showPlaylistPicker = true
                        showTrackMenu = false 
                    }
                )
            }
        }
    }

    if (showPlaylistPicker && trackForAction != null) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to Playlist") },
            text = {
                LazyColumn {
                    items(musicUiState) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                            modifier = Modifier.clickable {
                                trackForAction?.let { track ->
                                    catalogViewModel.addToPlaylist(playlist, track)
                                }
                                showPlaylistPicker = false
                                trackForAction = null
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Infinite scroll trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5 && !state.isLoading && !state.isLoadingMore && totalItems > 0 && state.mode == CatalogMode.SEARCH
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            catalogViewModel.loadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Sticky Search & Header Area
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                CatalogSearchBar(
                    query = state.query,
                    performanceMode = performanceMode,
                    onQueryChange = { catalogViewModel.onSearchQueryChange(it) },
                    onClear = { catalogViewModel.onSearchQueryChange(""); catalogViewModel.loadStorefront() }
                )
                
                Spacer(Modifier.height(16.dp))
                
                GenreFilterChips(
                    selectedGenre = state.selectedGenre,
                    onGenreSelected = { catalogViewModel.onGenreSelected(it) }
                )
            }
        }

        // Beta Card Section
        if (showBetaCard && state.query.isBlank()) {
            item(key = "beta_card") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    BetaHeader(
                        performanceMode = performanceMode,
                        onHide = { catalogViewModel.setShowBetaCard(false) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (state.query.isBlank()) {
            // Storefront Mode (Spotify-style)
            
            // 1. Quick Picks (Grid Layout)
            if (state.quickPicks.isNotEmpty()) {
                item(key = "quick_picks_title") {
                    SectionHeader(title = "Quick Picks", icon = Icons.Rounded.AutoAwesome)
                }
                item(key = "quick_picks_grid") {
                    QuickPicksGrid(
                        tracks = state.quickPicks,
                        localTracks = localTracks,
                        downloadingTracks = state.downloadingTracks,
                        performanceMode = performanceMode,
                        onTrackClick = { track ->
                            catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                                onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                            }
                        },
                        onDownload = { track ->
                            selectedTrackForMenu = track
                            showBottomSheet = true
                        },
                        onLongClick = { track ->
                            if (hapticEnabled) {
                                if (vibrationManager != null) vibrationManager.vibrateLongClick()
                                else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                            trackForAction = track
                            showTrackMenu = true
                        }
                    )
                }
            }

            // 2. New Releases / Trending (Horizontal Carousel)
            if (state.trending.isNotEmpty()) {
                item(key = "trending_title") {
                    SectionHeader(title = "Trending Now", icon = Icons.AutoMirrored.Rounded.TrendingUp)
                }
                item(key = "trending_carousel") {
                    TrendingCarousel(
                        tracks = state.trending.take(10),
                        onTrackClick = { track ->
                            catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                                onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                            }
                        },
                        onLongClick = { track ->
                            if (hapticEnabled) {
                                if (vibrationManager != null) vibrationManager.vibrateLongClick()
                                else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                            trackForAction = track
                            showTrackMenu = true
                        }
                    )
                }
            }
            
            // 3. Just for you (Normal list)
            item(key = "recommended_title") {
                SectionHeader(title = "Based on your activity", icon = Icons.Rounded.History)
            }
            items(state.trending.drop(10).take(20)) { track ->
                val isDownloaded = remember(localTracks, track) { 
                    localTracks.any { it.sourceUrl == track.sourceUrl || it.uri == track.sourceUrl } 
                }
                ListTrackCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    track = track,
                    isDownloaded = isDownloaded,
                    downloadProgress = state.downloadingTracks[track.id],
                    performanceMode = performanceMode,
                    onDownload = { 
                        selectedTrackForMenu = track
                        showBottomSheet = true 
                    },
                    onLongClick = {
                        if (hapticEnabled) {
                            if (vibrationManager != null) vibrationManager.vibrateLongClick()
                            else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        trackForAction = track
                        showTrackMenu = true
                    },
                    onClick = {
                        catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                            onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                        }
                    }
                )
            }
        } else {
            // Search / Genre Mode (Standard list with results)
            item(key = "search_results_title") {
                SectionHeader(
                    title = if (state.selectedGenre != null) state.selectedGenre!! else "Search Results",
                    icon = if (state.selectedGenre != null) Icons.Rounded.Category else Icons.Rounded.Search
                )
            }

            if (state.isLoading) {
                items(8) { ShimmerTrackCard(isGrid = false) }
            }

            items(state.tracks) { track ->
                val isDownloaded = remember(localTracks, track) { 
                    localTracks.any { it.sourceUrl == track.sourceUrl || it.uri == track.sourceUrl } 
                }
                ListTrackCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    track = track,
                    isDownloaded = isDownloaded,
                    downloadProgress = state.downloadingTracks[track.id],
                    performanceMode = performanceMode,
                    onDownload = { 
                        selectedTrackForMenu = track
                        showBottomSheet = true 
                    },
                    onLongClick = {
                        if (hapticEnabled) {
                            if (vibrationManager != null) vibrationManager.vibrateLongClick()
                            else view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        trackForAction = track
                        showTrackMenu = true
                    },
                    onClick = {
                        catalogViewModel.resolveAndPlay(track) { uri, title, artist, thumbUrl, sourceUrl ->
                            onPlayTrack(uri, title, artist, thumbUrl, sourceUrl)
                        }
                    }
                )
            }

            if (state.isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = CircleShape,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GenreFilterChips(
    selectedGenre: String?,
    onGenreSelected: (String?) -> Unit
) {
    val genres = listOf("Pop", "Rock", "Hip Hop", "Jazz", "Electronic", "Classical", "Lo-Fi", "Country")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        item {
            FilterChip(
                selected = selectedGenre == null,
                onClick = { onGenreSelected(null) },
                label = { Text("All") },
                shape = CircleShape
            )
        }
        items(genres) { genre ->
            FilterChip(
                selected = selectedGenre == genre,
                onClick = { onGenreSelected(if (selectedGenre == genre) null else genre) },
                label = { Text(genre) },
                shape = CircleShape
            )
        }
    }
}

@Composable
private fun QuickPicksGrid(
    tracks: List<CatalogTrack>,
    localTracks: List<com.frerox.toolz.data.music.MusicTrack>,
    downloadingTracks: Map<String, Float>,
    performanceMode: Boolean,
    onTrackClick: (CatalogTrack) -> Unit,
    onDownload: (CatalogTrack) -> Unit,
    onLongClick: (CatalogTrack) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val rows = tracks.chunked(2)
        rows.take(3).forEach { rowTracks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTracks.forEach { track ->
                    val isDownloaded = remember(localTracks, track) { 
                        localTracks.any { it.sourceUrl == track.sourceUrl || it.uri == track.sourceUrl } 
                    }
                    QuickPickItem(
                        modifier = Modifier.weight(1f),
                        track = track,
                        isDownloaded = isDownloaded,
                        downloadProgress = downloadingTracks[track.id],
                        onClick = { onTrackClick(track) },
                        onDownload = { onDownload(track) },
                        onLongClick = { onLongClick(track) }
                    )
                }
                if (rowTracks.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickPickItem(
    modifier: Modifier = Modifier,
    track: CatalogTrack,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        modifier = modifier
            .height(72.dp)
            .bouncyClick(scaleDown = 0.96f, onClick = onClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            if (downloadProgress != null) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    strokeCap = StrokeCap.Round
                )
            } else if (isDownloaded) {
                Icon(
                    Icons.Rounded.CheckCircle, 
                    null, 
                    tint = MaterialTheme.colorScheme.primary, 
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendingCarousel(
    tracks: List<CatalogTrack>,
    onTrackClick: (CatalogTrack) -> Unit,
    onLongClick: (CatalogTrack) -> Unit = {}
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(tracks) { track ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { onTrackClick(track) },
                        onLongClick = { onLongClick(track) }
                    )
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Beta Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BetaHeader(performanceMode: Boolean, onHide: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "betaGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing Beta Badge
            Box(contentAlignment = Alignment.Center) {
                if (!performanceMode) {
                    // Glow ring
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(glowScale)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.15f),
                                CircleShape
                            )
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.scale(if (performanceMode) 1f else glowScale * 0.95f)
                ) {
                    Text(
                        text = "BETA",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Experimental Feature",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Stream music from the cloud. Results powered by NewPipe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }

            IconButton(
                onClick = onHide,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Hide Beta Card",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Icon(
                Icons.Rounded.Science,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 4.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Catalog Search Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CatalogSearchBar(
    query: String,
    performanceMode: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (query.isNotEmpty()) 1f else 0f,
        animationSpec = tween(250),
        label = "catalogSearchBorder"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (performanceMode) 0.5f else 0.3f),
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (query.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha * 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Search the cloud…",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Cloud,
                    contentDescription = null,
                    tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 0.7f)
                ) {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(32.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Catalog Track Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CatalogTrackCard(
    modifier: Modifier = Modifier,
    track: CatalogTrack,
    isGrid: Boolean = false,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    performanceMode: Boolean,
    onDownload: () -> Unit,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    if (isGrid) {
        GridTrackCard(
            track = track,
            isDownloaded = isDownloaded,
            downloadProgress = downloadProgress,
            performanceMode = performanceMode,
            onDownload = onDownload,
            onLongClick = onLongClick,
            onClick = onClick
        )
    } else {
        ListTrackCard(
            modifier = modifier,
            track = track,
            isDownloaded = isDownloaded,
            downloadProgress = downloadProgress,
            performanceMode = performanceMode,
            onDownload = onDownload,
            onLongClick = onLongClick,
            onClick = onClick
        )
    }
}

@Composable
private fun GridTrackCard(
    track: CatalogTrack,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    performanceMode: Boolean,
    onDownload: () -> Unit,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = remember(isDark, surfaceColor) {
        if (isDark) surfaceColor.copy(alpha = 0.3f).compositeOver(Color.Black)
        else surfaceColor.copy(alpha = 0.5f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(cardColor)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Glassmorphic Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // Content Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Status Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isDownloaded) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Rounded.Cloud,
                                contentDescription = null,
                                modifier = Modifier.padding(6.dp),
                                tint = Color.White
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.padding(6.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    if (track.duration > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = formatDuration(track.duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Bottom Row: Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Bottom
                ) {
                    DownloadButton(
                        isDownloaded = isDownloaded,
                        progress = downloadProgress,
                        onDownload = onDownload,
                        compact = true
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.2).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ListTrackCard(
    modifier: Modifier = Modifier,
    track: CatalogTrack,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    performanceMode: Boolean,
    onDownload: () -> Unit,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val cardColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClick(scaleDown = 0.98f, onClick = onClick)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = cardColor,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                if (isDownloaded) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp)
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(3.dp, cardColor.compositeOver(MaterialTheme.colorScheme.surface), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }

            if (downloadProgress != null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round
                    )
                }
            } else {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isDownloaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                                        else Color.Transparent
                    )
                ) {
                    Icon(
                        if (isDownloaded) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                        contentDescription = "Download",
                        tint = if (isDownloaded) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            IconButton(
                onClick = onLongClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
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
        label = "DownloadState"
    ) { targetState ->
        when (targetState) {
            2 -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Downloaded",
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
                tint = Color(0xFF4CAF50)
            )
            1 -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (compact) 24.dp else 28.dp)) {
                CircularProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    strokeWidth = 2.dp,
                    color = if (compact) Color.White else MaterialTheme.colorScheme.primary
                )
            }
            0 -> IconButton(
                onClick = onDownload,
                modifier = Modifier.size(if (compact) 32.dp else 36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (compact) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Icon(
                    Icons.Rounded.FileDownload,
                    contentDescription = "Download",
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                    tint = if (compact) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ShimmerTrackCard(isGrid: Boolean) {
    if (isGrid) {
        Column(modifier = Modifier.padding(4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .shimmerEffect()
            )
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(CircleShape).shimmerEffect())
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp).clip(CircleShape).shimmerEffect())
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(CircleShape).shimmerEffect())
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.3f).height(12.dp).clip(CircleShape).shimmerEffect())
            }
        }
    }
}

private fun Modifier.shimmerEffect(): Modifier = this.drawBehind {
    // Basic placeholder shim effect if not using a library
    drawRect(Color.Gray.copy(alpha = 0.1f))
}

// ─────────────────────────────────────────────────────────────────────────────
// Error State
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CatalogErrorState(
    message: String,
    onRetry: () -> Unit
) {
    val isOffline = message.contains("internet", ignoreCase = true) ||
            message.contains("network", ignoreCase = true) ||
            message.contains("connect", ignoreCase = true)

    val pulse = rememberInfiniteTransition(label = "errorPulse")
    val scale by pulse.animateFloat(
        1f, 1.06f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "errorScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                            CircleShape
                        )
                )
                Icon(
                    if (isOffline) Icons.Rounded.WifiOff else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (isOffline) "No Internet" else "Oops!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isOffline) "Connect to the internet to browse the catalog."
                else message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            FilledTonalButton(
                onClick = onRetry,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Onboarding Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CatalogOnboardingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("GOT IT", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.Center) {
                    val infiniteTransition = rememberInfiniteTransition(label = "glow")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 0.8f,
                        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "alpha"
                    )
                    Box(Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape))
                    Icon(
                        Icons.Rounded.Science,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Welcome to Catalog Beta",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                "Experience the future of Toolz. Stream and download millions of tracks for free directly. And it only requires an active internet connection.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Quality Selection Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

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
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp, height = 4.dp) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text("Download Quality", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
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
                        shape = RoundedCornerShape(16.dp),
                        color = if (currentQuality == quality) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        border = if (currentQuality == quality) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentQuality == quality, onClick = { onQualitySelected(quality) })
                            Spacer(Modifier.width(12.dp))
                            Text(quality, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val isGlobalSettings = onStartDownload == {} // Hacky but works for now as I passed it from UI

            if (isGlobalSettings) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Settings are saved automatically.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = onStartDownload,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Download", fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Spinning Download Popup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpinningDownloadPopup(
    track: CatalogTrack,
    progress: Float,
    onCancel: () -> Unit,
    onHide: () -> Unit
) {
    val rotation = rememberInfiniteTransition(label = "rotation")
    val angle by rotation.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "angle"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress"
    )

    AlertDialog(
        onDismissRequest = onHide,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(180.dp),
                        strokeWidth = 10.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        strokeCap = StrokeCap.Round
                    )
                    
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .graphicsLayer { rotationZ = angle },
                        contentScale = ContentScale.Crop
                    )
                    
                    // Progress percentage overlay
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 12.dp)
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(Modifier.height(32.dp))
                
                Text(
                    track.title, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold, 
                    textAlign = TextAlign.Center, 
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist, 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(32.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                    Button(
                        onClick = onHide,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hide")
                    }
                }
            }
        },
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        String.format("%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
