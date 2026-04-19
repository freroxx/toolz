package com.frerox.toolz.ui.screens.media.catalog

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
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
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable

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
    onPlayAll: (List<CatalogTrack>, Int) -> Unit,
    onEnqueue: (CatalogTrack, Boolean) -> Unit
) {
    val state by catalogViewModel.uiState.collectAsState()
    val hasSeenOnboarding by catalogViewModel.hasSeenOnboarding.collectAsState()
    val showBetaCard by catalogViewModel.showBetaCard.collectAsState()
    val listState = rememberLazyGridState()
    val performanceMode = LocalPerformanceMode.current
    var isGridView by remember { mutableStateOf(false) } // Default to Spotify-style list
    
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
                    leadingContent = { Icon(Icons.Rounded.PlaylistAdd, null) },
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
                    itemsIndexed(musicUiState) { _, playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { Icon(Icons.Rounded.PlaylistPlay, null) },
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
            lastVisibleItem >= totalItems - 6 && !state.isLoading && !state.isLoadingMore && totalItems > 0
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            catalogViewModel.loadMore()
        }
    }

    LazyVerticalGrid(
        columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search Bar
        item(key = "search_bar", span = { GridItemSpan(maxLineSpan) }) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                CatalogSearchBar(
                    query = state.query,
                    performanceMode = performanceMode,
                    onQueryChange = { catalogViewModel.onSearchQueryChange(it) },
                    onClear = { catalogViewModel.onSearchQueryChange(""); catalogViewModel.loadTrending() }
                )
            }
        }

        // Section Title & View Toggle
        item(key = "section_title", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                if (showBetaCard) {
                    BetaHeader(
                        performanceMode = performanceMode,
                        onHide = { catalogViewModel.setShowBetaCard(false) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (state.mode == CatalogMode.TRENDING) Icons.AutoMirrored.Rounded.TrendingUp else Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (state.mode == CatalogMode.TRENDING) "Trending Now" else "Search songs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(Modifier.weight(1f))

                    if (state.tracks.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (state.tracks.isNotEmpty()) {
                                    val shuffled = state.tracks.shuffled()
                                    onPlayAll(shuffled, 0)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle All",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Rounded.List else Icons.Rounded.GridView,
                            contentDescription = "Toggle View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (!showBetaCard) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .size(32.dp)
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { catalogViewModel.setShowBetaCard(true) }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Science,
                                contentDescription = "Show Beta Card",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }


        // Loading Shimmer State
        if (state.isLoading) {
            items(8, key = { "shimmer_$it" }) { index ->
                ShimmerTrackCard(isGrid = isGridView)
            }
        }

        // Error State
        if (state.error != null && !state.isLoading && state.tracks.isEmpty()) {
            item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                CatalogErrorState(
                    message = state.error ?: "Something went wrong",
                    onRetry = {
                        catalogViewModel.clearError()
                        if (state.query.isNotBlank()) {
                            catalogViewModel.onSearchQueryChange(state.query)
                        } else {
                            catalogViewModel.loadTrending()
                        }
                    }
                )
            }
        }

        // Track Cards
        itemsIndexed(
            items = state.tracks,
            key = { _, track -> track.id + (if (isGridView) "_grid" else "_list") }
        ) { index, track ->
            val isDownloaded = remember(localTracks, track) { 
                localTracks.any { (it.sourceUrl == track.sourceUrl || it.uri == track.sourceUrl) && it.path != null } 
            }
            val downloadProgress = state.downloadingTracks[track.id]
            
            Box(modifier = Modifier.animateItem(
                fadeInSpec = tween(300, delayMillis = (index % 10) * 30),
                placementSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)
            )) {
                CatalogTrackCard(
                    track = track,
                    isGrid = isGridView,
                    isDownloaded = isDownloaded,
                    downloadProgress = downloadProgress,
                    performanceMode = performanceMode,
                    onDownload = { 
                        selectedTrackForMenu = track
                        showBottomSheet = true 
                    },
                    onLongClick = {
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
        }

        // Loading More Indicator
        if (state.isLoadingMore) {
            item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp)) // Slightly more rounded
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dynamic Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.7f)
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
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            tonalElevation = 4.dp
                        ) {
                            Icon(
                                Icons.Rounded.Cloud,
                                contentDescription = null,
                                modifier = Modifier.padding(6.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            tonalElevation = 4.dp
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.padding(6.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    if (track.duration > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.6f)
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

        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.1.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (performanceMode) 1.dp else 2.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(18.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Rounded.Cloud,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            DownloadButton(
                isDownloaded = isDownloaded,
                progress = downloadProgress,
                onDownload = onDownload
            )
            
            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
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
// Shimmer Loading Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OldShimmerTrackCard() {
    val performanceMode = LocalPerformanceMode.current
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    
    val defaultAlpha = if (performanceMode) 0.5f else 0.3f
    val highlightAlpha = if (performanceMode) 0.5f else 0.6f

    val shimmerTranslation by if (performanceMode) {
        remember { mutableFloatStateOf(0f) }
    } else {
        shimmerTransition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslation"
        )
    }

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = defaultAlpha),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = highlightAlpha),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = defaultAlpha)
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = shimmerColors,
                                start = Offset(size.width * shimmerTranslation, 0f),
                                end = Offset(size.width * (shimmerTranslation + 1f), size.height)
                            )
                        )
                    }
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .drawBehind {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = shimmerColors,
                                    start = Offset(size.width * shimmerTranslation, 0f),
                                    end = Offset(size.width * (shimmerTranslation + 1f), size.height)
                                )
                            )
                        }
                )
                Spacer(Modifier.height(8.dp))
                // Artist placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .drawBehind {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = shimmerColors,
                                    start = Offset(size.width * shimmerTranslation, 0f),
                                    end = Offset(size.width * (shimmerTranslation + 1f), size.height)
                                )
                            )
                        }
                )
            }

            Spacer(Modifier.width(8.dp))

            // Play button placeholder
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = shimmerColors,
                                start = Offset(size.width * shimmerTranslation, 0f),
                                end = Offset(size.width * (shimmerTranslation + 1f), size.height)
                            )
                        )
                    }
            )
        }
    }
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
