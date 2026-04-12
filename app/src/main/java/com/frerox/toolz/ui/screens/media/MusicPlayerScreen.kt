package com.frerox.toolz.ui.screens.media

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import androidx.annotation.OptIn as AnnotationOptIn
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.ui.components.DragDropState
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.components.dragDropColumn
import com.frerox.toolz.ui.components.dragDropItem
import com.frerox.toolz.ui.components.rememberDragDropState
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.ui.screens.media.ai.*
import com.frerox.toolz.ui.screens.media.catalog.CatalogContent
import com.frerox.toolz.ui.screens.media.catalog.CatalogViewModel
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    aiViewModel: NowPlayingAiViewModel = hiltViewModel(),
    catalogViewModel: CatalogViewModel = hiltViewModel(),
    initialTab: Int = 0,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    val catalogState by catalogViewModel.uiState.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val sliderPos by viewModel.sliderPosition.collectAsState()
    val context = LocalContext.current

    // Download Popup (Moved from CatalogScreen for persistence)
    if (catalogState.showDownloadPopup && catalogState.activeDownload != null) {
        val progress = catalogState.downloadingTracks[catalogState.activeDownload!!.id] ?: 0f
        SpinningDownloadPopup(
            track = catalogState.activeDownload!!,
            progress = progress,
            onCancel = { catalogViewModel.cancelDownload(catalogState.activeDownload!!.id) },
            onHide = { catalogViewModel.hideDownloadPopup() }
        )
    }

    val downloadCount = catalogState.downloadingTracks.size
    val avgDownloadProgress = if (downloadCount > 0) {
        catalogState.downloadingTracks.values.average().toFloat()
    } else 0f

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var currentTab by remember { mutableIntStateOf(initialTab) }
    var searchQuery by remember { mutableStateOf("") }
    var showMultiSelectPlaylistPicker by remember { mutableStateOf(false) }
    var selectedFolderTracks by remember { mutableStateOf<Pair<String, List<MusicTrack>>?>(null) }

    val musicPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var isManageStorageGranted by remember {
        mutableStateOf(Environment.isExternalStorageManager())
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isManageStorageGranted = Environment.isExternalStorageManager()
        if (isManageStorageGranted) viewModel.scanMusic()
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                viewModel.addCustomFolder(it)
            } catch (e: Exception) {
                viewModel.addCustomFolder(it)
            }
        }
    }

    val playlistThumbLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPlaylist?.let { playlist -> viewModel.updatePlaylistThumbnail(playlist, it) }
        }
    }

    val filteredTracks = remember(state.tracks, searchQuery) {
        filterTracks(state.tracks, searchQuery)
    }

    LaunchedEffect(state.currentTrack) {
        state.currentTrack?.let { aiViewModel.updateSong(it) }
    }
    LaunchedEffect(playbackPosition) {
        aiViewModel.updateProgress(playbackPosition)
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                state = state,
                currentTab = currentTab,
                showSortMenu = showSortMenu,
                onShowSortMenu = { showSortMenu = it },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onBack = onBack,
                onAddFolder = { folderLauncher.launch(null) },
                onRefresh = { viewModel.scanMusic() },
                onSort = { viewModel.setSortOrder(it) },
                onClearSelection = { viewModel.clearSelection() },
                onMultiAddPlaylist = { showMultiSelectPlaylistPicker = true },
                onResetCatalogOnboarding = { catalogViewModel.resetOnboarding() }
            )
        },
        bottomBar = {
            ScreenBottomBar(
                state = state,
                aiState = aiState,
                playbackPosition = playbackPosition,
                duration = duration,
                currentTab = currentTab,
                downloadCount = downloadCount,
                avgDownloadProgress = avgDownloadProgress,
                onTabChange = { currentTab = it },
                onTogglePlay = { viewModel.togglePlayPause() },
                onNext = { viewModel.skipNext() },
                onPrevious = { viewModel.skipPrevious() },
                onOpenFullPlayer = { showFullPlayer = true },
                onLongClickMiniPlayer = { catalogViewModel.showDownloadPopup() },
                onExpand = { aiViewModel.toggleExpandedPill() },
                isOnline = state.isOnline,
                isResolving = state.isResolvingCatalog
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fadingEdges(top = 20.dp, bottom = 20.dp)
        ) {
            when {
                !musicPermission.status.isGranted || !isManageStorageGranted -> {
                    PermissionPlaceholder(
                        onAllow = {
                            if (!musicPermission.status.isGranted) musicPermission.launchPermissionRequest()
                            else {
                                manageStorageLauncher.launch(
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                )
                            }
                        }
                    )
                }
                state.isLoading && state.tracks.isEmpty() -> {
                    LoadingPlaceholder()
                }
                else -> {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            if (state.performanceMode) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(
                                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
                                ) { dir * it / 3 } + fadeIn(tween(300)))
                                    .togetherWith(
                                        slideOutHorizontally(
                                            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
                                        ) { -dir * it / 3 } + fadeOut(tween(200))
                                    )
                            }
                        },
                        label = "TabContent"
                    ) { tab ->
                        when (tab) {
                            0 -> TrackList(filteredTracks, state, viewModel, searchQuery)
                            1 -> LibrarySection(
                                state, viewModel,
                                onCreatePlaylist = { showPlaylistDialog = true },
                                onPlaylistClick = { selectedPlaylist = it },
                                onUpdateThumb = { selectedPlaylist = it; playlistThumbLauncher.launch("image/*") },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onFolderClick = { name, tracks -> selectedFolderTracks = name to tracks },
                                onAddFolder = { folderLauncher.launch(null) }
                            )
                            2 -> CatalogContent(
                                catalogViewModel = catalogViewModel,
                                musicRepository = viewModel.repository,
                                localTracks = state.tracks,
                                onPlayTrack = { uri, title, artist, thumbUrl, sourceUrl ->
                                    viewModel.playUri(uri, title, artist, thumbUrl, sourceUrl)
                                },
                                onPlayAll = { tracks, startIndex ->
                                    viewModel.playCatalogTracks(tracks, startIndex)
                                },
                                onEnqueue = { track, playNext ->
                                    viewModel.enqueueCatalogTrack(track, playNext)
                                }
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(catalogState.isResolving) {
            viewModel.setResolvingCatalog(catalogState.isResolving)
        }

        // ── Dialogs & overlays ───────────────────────────────────────────────
        if (showPlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showPlaylistDialog = false },
                onCreate = { name -> viewModel.createPlaylist(name); showPlaylistDialog = false }
            )
        }
        if (showMultiSelectPlaylistPicker) {
            MultiSelectPlaylistPicker(
                playlists = state.playlists,
                onDismiss = { showMultiSelectPlaylistPicker = false },
                onPlaylistSelected = { viewModel.addSelectedTracksToPlaylist(it); showMultiSelectPlaylistPicker = false }
            )
        }
        if (selectedFolderTracks != null) {
            FolderTracksDialog(
                folderName = selectedFolderTracks!!.first,
                tracks = selectedFolderTracks!!.second,
                onDismiss = { selectedFolderTracks = null },
                onPlayTrack = { track -> viewModel.playTrack(track, selectedFolderTracks!!.second); selectedFolderTracks = null },
                state = state,
                viewModel = viewModel
            )
        }
        if (selectedPlaylist != null) {
            PlaylistDetailView(
                playlist = state.playlists.find { it.id == selectedPlaylist?.id } ?: selectedPlaylist!!,
                allTracks = state.tracks,
                onDismiss = { selectedPlaylist = null },
                onPlayPlaylist = { viewModel.playPlaylist(it) },
                onDeletePlaylist = { viewModel.deletePlaylist(it); selectedPlaylist = null },
                onAddTrack = { viewModel.addTrackToPlaylist(selectedPlaylist!!, it) },
                onRemoveTrack = { viewModel.removeTrackFromPlaylist(selectedPlaylist!!, it) },
                onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                currentTrackUri = state.currentTrack?.uri,
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
        }
        if (showFullPlayer && state.currentTrack != null) {
            val visualizerData by viewModel.visualizerData.collectAsStateWithLifecycle()
            FullPlayerView(
                state = state,
                aiState = aiState,
                aiViewModel = aiViewModel,
                playbackPosition = playbackPosition,
                duration = duration,
                sliderPos = sliderPos,
                visualizerData = visualizerData,
                onSliderChange = { viewModel.onSliderChange(it) },
                onSliderChangeFinished = { viewModel.onSliderChangeFinished() },
                onDismiss = { showFullPlayer = false },
                onTogglePlay = { viewModel.togglePlayPause() },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrev = { viewModel.skipPrevious() },
                onSeek = { viewModel.seekTo(it) },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() },
                onStop = { viewModel.stop(); showFullPlayer = false },
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onSetArtShape = { viewModel.setArtShape(it) },
                onTrackSelect = { viewModel.playTrack(it, state.queue.map { it.track }) },
                onToggleRotation = { viewModel.toggleRotation() },
                onTogglePip = { viewModel.togglePipEnabled() },
                onOpenAi = { viewModel.vibrationManager.vibrateClick(); showAiSheet = true },
                onToggleMusicSettings = { viewModel.toggleMusicSettings() },
                onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                onSetEqualizerPreset = { viewModel.setEqualizerPreset(it) },
                onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemoveQueueItem = { index -> viewModel.removeQueueItem(index) },
                onClearQueue = { viewModel.clearQueue() },
                onToggleVisualizer = { viewModel.toggleShowVisualizer() },
                equalizerPresets = state.equalizerPresets
            )
        }
        if (showAiSheet) {
            NowPlayingAiBottomSheet(
                viewModel = aiViewModel,
                onDismiss = { showAiSheet = false },
                vibrationManager = viewModel.vibrationManager,
                onSeek = { viewModel.seekTo(it) },
                onPlayRecommendation = { recommendation ->
                    // Convert AiRecommendation to a CatalogTrack to use the existing discovery/playback pipeline
                    val catalogTrack = com.frerox.toolz.data.catalog.CatalogTrack(
                        id = recommendation.videoId ?: recommendation.title.hashCode().toString(),
                        title = recommendation.title,
                        artist = recommendation.artist,
                        thumbnailUrl = recommendation.thumbnailUrl ?: "",
                        duration = 0,
                        sourceUrl = if (recommendation.videoId != null) 
                            "https://www.youtube.com/watch?v=${recommendation.videoId}" 
                            else "https://www.youtube.com/results?search_query=${recommendation.title}+${recommendation.artist}"
                    )
                    
                    catalogViewModel.resolveAndPlay(catalogTrack) { uri, title, artist, thumbUrl, sourceUrl ->
                        val metadata = androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .setDisplayTitle(title)
                            .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                            .setIsPlayable(true)
                            .setArtworkUri(android.net.Uri.parse(thumbUrl))
                            .setExtras(android.os.Bundle().apply {
                                putString("source_url", sourceUrl)
                            })
                            .build()
                        val mediaItem = androidx.media3.common.MediaItem.Builder()
                            .setMediaId(sourceUrl)
                            .setUri(uri)
                            .setMediaMetadata(metadata)
                            .build()
                        val p: androidx.media3.common.Player = viewModel.player
                        p.stop()
                        p.setMediaItem(mediaItem)
                        p.prepare()
                        p.play()
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenTopBar(
    state: MusicUiState,
    currentTab: Int,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddFolder: () -> Unit,
    onRefresh: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onClearSelection: () -> Unit,
    onMultiAddPlaylist: () -> Unit,
    onResetCatalogOnboarding: () -> Unit = {}
) {
    Surface(color = Color.Transparent, tonalElevation = 0.dp) {
        Column(modifier = Modifier.background(Color.Transparent).statusBarsPadding()) {
            AnimatedContent(
                targetState = state.isSelectionMode,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "topBarMode"
            ) { selectionMode ->
                if (selectionMode) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${state.selectedTracks.size}",
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "SELECTED",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onClearSelection,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        actions = {
                            FilledTonalIconButton(
                                onClick = onMultiAddPlaylist,
                                shape = RoundedCornerShape(14.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "MUSIC",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 3.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                AnimatedVisibility(
                                    visible = currentTab == 2,
                                    enter = fadeIn() + expandHorizontally(),
                                    exit = fadeOut() + shrinkHorizontally()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = onResetCatalogOnboarding
                                            )
                                        ) {
                                            Text(
                                                "BETA",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        actions = {
                            Box {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = currentTab == 2,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    IconButton(onClick = onAddFolder) {
                                        Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.onSurface)
                            }
                            Box {
                                IconButton(onClick = { onShowSortMenu(true) }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { onShowSortMenu(false) },
                                    shape = RoundedCornerShape(24.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    SortDropdownItem("By Title", Icons.Rounded.Title) {
                                        onSort(SortOrder.TITLE); onShowSortMenu(false)
                                    }
                                    SortDropdownItem("By Artist", Icons.Rounded.Person) {
                                        onSort(SortOrder.ARTIST); onShowSortMenu(false)
                                    }
                                    SortDropdownItem("By Recent", Icons.Rounded.Schedule) {
                                        onSort(SortOrder.RECENT); onShowSortMenu(false)
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f),
                modifier = Modifier.padding(top = 4.dp)
            )

            // Search bar
            AnimatedVisibility(
                visible = currentTab == 0,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    val searchFocused = remember { mutableStateOf(false) }
                    val borderAlpha by animateFloatAsState(
                        targetValue = if (searchFocused.value || searchQuery.isNotEmpty()) 1f else 0f,
                        animationSpec = tween(250),
                        label = "searchBorder"
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        placeholder = {
                            Text(
                                "Search tracks, artists…",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                                exit = fadeOut() + scaleOut(targetScale = 0.7f)
                            ) {
                                IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(32.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha * 0.5f),
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SortDropdownItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.Bold) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Bar (MiniPlayer + TabRow)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScreenBottomBar(
    state: MusicUiState,
    aiState: NowPlayingAiUiState,
    playbackPosition: Long,
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        modifier = Modifier.shadow(20.dp, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            // MiniPlayer
            AnimatedVisibility(
                visible = state.currentTrack != null || isResolving,
                enter = fadeIn(tween(300)) + expandVertically(
                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(tween(200)) + shrinkVertically(
                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top
                )
            ) {
                val trackToDisplay = state.currentTrack ?: MusicTrack(
                    uri = "loading",
                    title = if (isResolving) "Resolving Stream..." else "Loading song…",
                    artist = "Catalog",
                    album = "Online",
                    duration = 0
                )

                MiniPlayer(
                    track = trackToDisplay,
                    isPlaying = state.isPlaying,
                    progress = playbackPosition,
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

            // Custom TabRow
            val tabs = listOf(
                "Tracks" to Icons.Rounded.MusicNote,
                "Library" to Icons.AutoMirrored.Rounded.PlaylistPlay,
                "Catalog" to if (downloadCount > 0) Icons.Rounded.CloudDownload else Icons.Rounded.Cloud
            )

            PillTabRow(
                tabs = tabs,
                selectedTab = currentTab.coerceAtMost(tabs.size - 1),
                onTabChange = onTabChange
            )
        }
    }
}

@Composable
private fun PillTabRow(
    tabs: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            val selected = selectedTab == index
            val weight by animateFloatAsState(
                targetValue = if (selected) 1.35f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "tabWeight$index"
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(250),
                label = "tabBg$index"
            )
            Surface(
                modifier = Modifier
                    .weight(weight)
                    .height(48.dp)
                    .bouncyClick { onTabChange(index) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(200)) + expandHorizontally(tween(250, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200, easing = FastOutSlowInEasing))
                        ) {
                            Text(
                                text = "  $label",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Placeholders
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionPlaceholder(onAllow: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            // Animated icon
            val pulse = rememberInfiniteTransition(label = "pulse")
            val scale by pulse.animateFloat(
                1f, 1.08f,
                infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "pulseScale"
            )
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(scale)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            CircleShape
                        )
                )
                Icon(
                    Icons.Rounded.FolderSpecial,
                    null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Storage Access",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Toolz needs access to find and play music on your device.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = onAllow,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(58.dp),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("GRANT ACCESS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val inf = rememberInfiniteTransition(label = "loadBars")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                listOf(0, 100, 200, 50, 150).forEachIndexed { i, delayMs ->
                    val h by inf.animateFloat(
                        10f, 36f,
                        infiniteRepeatable(tween(600 + i * 80, easing = FastOutSlowInEasing, delayMillis = delayMs), RepeatMode.Reverse),
                        label = "bar$i"
                    )
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f + i * 0.04f))
                    )
                }
            }
            Text(
                "SCANNING LIBRARY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track List
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackList(tracks: List<MusicTrack>, state: MusicUiState, viewModel: MusicPlayerViewModel, searchQuery: String = "") {
    if (tracks.isEmpty() && !state.isLoading) {
        EmptyMusicPlaceholder(onScan = { viewModel.scanMusic() })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Slim loading bar at the very top — visible during incremental scan
        // while tracks are already appearing (non-blocking UI)
        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                strokeCap = StrokeCap.Round
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 130.dp, top = if (state.isLoading) 5.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(tracks, key = { _, t -> t.uri }) { index, track ->
                val isSelected = state.selectedTracks.contains(track.uri)

                if (state.performanceMode) {
                    TrackItem(
                        track = track,
                        isCurrent = track.uri == state.currentTrack?.uri,
                        isSelected = isSelected,
                        isSelectionMode = state.isSelectionMode,
                        onClick = {
                            if (state.isSelectionMode) viewModel.toggleTrackSelection(track.uri)
                            else viewModel.playTrack(track, tracks)
                        },
                        onLongClick = { viewModel.toggleTrackSelection(track.uri) },
                        onDelete = { viewModel.deleteTrack(track) },
                        onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        playlists = state.playlists,
                        searchQuery = searchQuery
                    )
                } else {
                    // Simple animateItem() — no per-item LaunchedEffect/stagger
                    // (stagger caused O(n) LaunchedEffect overhead on large lists)
                    TrackItem(
                        track = track,
                        isCurrent = track.uri == state.currentTrack?.uri,
                        isSelected = isSelected,
                        isSelectionMode = state.isSelectionMode,
                        onClick = {
                            if (state.isSelectionMode) viewModel.toggleTrackSelection(track.uri)
                            else viewModel.playTrack(track, tracks)
                        },
                        onLongClick = { viewModel.toggleTrackSelection(track.uri) },
                        onDelete = { viewModel.deleteTrack(track) },
                        onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
                        onToggleFavorite = { viewModel.toggleFavorite(track) },
                        playlists = state.playlists,
                        searchQuery = searchQuery,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        } // end LazyColumn
    } // end Box
}

@Composable
private fun EmptyMusicPlaceholder(onScan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(
                Icons.Rounded.MusicOff,
                null,
                modifier = Modifier.size(80.dp).alpha(0.1f),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))
            Text("No tracks found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text("Scan your device to discover music", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            FilledTonalButton(onClick = onScan, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("SCAN NOW", fontWeight = FontWeight.Black)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Item
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrackItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onToggleFavorite: () -> Unit = {},
    playlists: List<Playlist>,
    searchQuery: String = "",
    deleteLabel: String = "Delete",
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val performanceMode = LocalPerformanceMode.current

    // Direct color selection — no animateColorAsState in lists (too expensive)
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        isCurrent  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else       -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.02f else 1f,
        animationSpec = if (performanceMode) snap() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "trackScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
        tonalElevation = if (isCurrent) 4.dp else 0.dp,
        border = if (isCurrent)
            BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with playing indicator overlay
            Box {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 3.dp  // constant — no recomp
                ) {
                    AsyncImage(
                        model = track.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = rememberVectorPainter(Icons.Rounded.MusicNote)
                    )
                }
                // Selection overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f),
                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.7f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                // Playing bars overlay for current track
                if (isCurrent && !isSelected) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingBarsIndicator()
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                val highlightColor = MaterialTheme.colorScheme.primary
                Text(
                    text = remember(track.title, searchQuery, highlightColor) {
                        highlightSearch(track.title, searchQuery, highlightColor)
                    },
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(2.dp))
                val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
                Text(
                    text = remember(artistText, searchQuery, highlightColor) {
                        highlightSearch(artistText, searchQuery, highlightColor)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            } else {
                val favScale by animateFloatAsState(
                    targetValue = if (track.isFavorite) 1.2f else 1f,
                    animationSpec = if (performanceMode) snap() else spring(Spring.DampingRatioMediumBouncy),
                    label = "favScale"
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (track.isFavorite) Color(0xFFE05C5C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp).scale(favScale)
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist", fontWeight = FontWeight.SemiBold) },
                            onClick = { showPlaylistPicker = true; showMenu = false },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp).alpha(0.08f))
                        DropdownMenuItem(
                            text = { Text(deleteLabel, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(); showMenu = false },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists,
            onDismiss = { showPlaylistPicker = false },
            onSelect = { onAddToPlaylist(it); showPlaylistPicker = false }
        )
    }
}

// Animated playing bars
@Composable
private fun PlayingBarsIndicator() {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            listOf(10.dp, 14.dp, 8.dp).forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
        }
        return
    }

    val inf = rememberInfiniteTransition(label = "playingBars")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(0, 100, 50).forEach { delay ->
            val h by inf.animateFloat(
                4f, 14f,
                infiniteRepeatable(tween(500, easing = FastOutSlowInEasing, delayMillis = delay), RepeatMode.Reverse),
                label = "bar$delay"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Card (horizontal scroll)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrackCard(track: MusicTrack, onClick: () -> Unit) {
    val performanceMode = LocalPerformanceMode.current
    Column(
        modifier = Modifier
            .width(132.dp)
            .bouncyClick(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .size(132.dp)
                .shadow(
                    if (performanceMode) 4.dp else 12.dp,
                    RoundedCornerShape(22.dp),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                ),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = if (performanceMode) 0.dp else 2.dp
        ) {
            AsyncImage(
                model = track.thumbnailUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = rememberVectorPainter(Icons.Rounded.MusicNote)
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
        Text(
            text = artistText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
            letterSpacing = 0.3.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Folder List + Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FolderList(state: MusicUiState, onFolderClick: (String, List<MusicTrack>) -> Unit) {
    if (state.folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.FolderOff, null, modifier = Modifier.size(72.dp).alpha(0.1f), tint = MaterialTheme.colorScheme.onSurface)
                Text("No folders found", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text("Add a custom folder using the + button above", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 130.dp, top = 14.dp, start = 14.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.folders.keys.toList()) { folderName ->
            val tracks = state.folders[folderName] ?: emptyList()
            // Count how many tracks from this folder are favorites or currently playing
            val hasCurrentTrack = state.currentTrack?.let { current -> tracks.any { it.uri == current.uri } } == true
            FolderCard(
                folderName = folderName,
                trackCount = tracks.size,
                isCurrentFolder = hasCurrentTrack,
                onClick = { onFolderClick(folderName, tracks) }
            )
        }
    }
}

@Composable
fun FolderCard(
    folderName: String,
    trackCount: Int,
    isCurrentFolder: Boolean = false,
    onClick: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val borderAlpha by animateFloatAsState(
        targetValue = if (isCurrentFolder) 0.6f else 0.2f,
        animationSpec = if (performanceMode) snap() else tween(300),
        label = "folderBorder"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = if (isCurrentFolder)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = androidx.compose.foundation.BorderStroke(
            if (isCurrentFolder) 1.5.dp else 1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background: two overlapping circles for depth (skip in performance mode)
            if (!performanceMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.025f),
                        radius = size.width * 0.55f,
                        center = Offset(size.width * 1.1f, -size.height * 0.3f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.015f),
                        radius = size.width * 0.4f,
                        center = Offset(size.width * 0.9f, size.height * 1.1f)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Icon row
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            folderName == "Toolz Downloads" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                            isCurrentFolder -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (folderName == "Toolz Downloads") Icons.Rounded.DownloadDone else Icons.Rounded.Folder,
                                null,
                                tint = if (folderName == "Toolz Downloads") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    // "Now playing" badge
                    if (isCurrentFolder) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                PlayingBarsIndicator()
                            }
                        }
                    }
                }

                // Text info
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = folderName,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (trackCount == 1) "1 track" else "$trackCount tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isCurrentFolder) 1f else 0.75f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderTracksDialog(
    folderName: String,
    tracks: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    state: MusicUiState,
    viewModel: MusicPlayerViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    val currentlyPlayingInFolder = state.currentTrack?.let { current -> tracks.any { it.uri == current.uri } } == true

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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

        // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        folderName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${tracks.size} TRACKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        if (currentlyPlayingInFolder) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    "• PLAYING",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
                // Play all button
                FilledIconButton(
                    onClick = { onPlayTrack(tracks.first()) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(26.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tracks…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                    }
                },
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.07f))
            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(48.dp).alpha(0.12f), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text("No matches", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.uri }) { track ->
                        TrackItem(
                            track = track,
                            isCurrent = track.uri == state.currentTrack?.uri,
                            isSelected = state.selectedTracks.contains(track.uri),
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) viewModel.toggleTrackSelection(track.uri)
                                else onPlayTrack(track)
                            },
                            onLongClick = { viewModel.toggleTrackSelection(track.uri) },
                            onDelete = { viewModel.deleteTrack(track) },
                            onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            playlists = state.playlists
                        )
                    }
                }
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Library Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LibrarySection(
    state: MusicUiState,
    viewModel: MusicPlayerViewModel,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onUpdateThumb: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit = {},  // wire to ViewModel
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> }, // wire to ViewModel
    onFolderClick: (String, List<MusicTrack>) -> Unit = { _, _ -> },
    onAddFolder: () -> Unit = {}
) {
    var showFavoritesDetail by remember { mutableStateOf(false) }
    var showRecentDetail by remember { mutableStateOf(false) }
    var showMostPlayedDetail by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 130.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // ── Quick access cards ───────────────────────────────────────────────
        item {
            SectionLabel("LIBRARY")
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorites
                QuickAccessCard(
                    modifier = Modifier.weight(1.1f).height(200.dp),
                    icon = Icons.Rounded.Favorite,
                    label = "Favorites",
                    count = state.favoriteTracks.size,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showFavoritesDetail = true }
                )
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // New playlist
                    QuickActionCard(
                        modifier = Modifier.fillMaxWidth().height(94.dp),
                        icon = Icons.Rounded.Add,
                        label = "New List",
                        onClick = onCreatePlaylist
                    )
                    // Track count chip
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(94.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "${state.tracks.size}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "TRACKS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        // ── Recently Played ──────────────────────────────────────────────────
        item {
            RowSectionHeader("RECENTLY PLAYED") { showRecentDetail = true }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(state.recentlyPlayed.take(12)) { track ->
                    TrackCard(track = track, onClick = { viewModel.playTrack(track, state.recentlyPlayed) })
                }
            }
        }

        // ── Most Played ──────────────────────────────────────────────────────
        item {
            RowSectionHeader("MOST PLAYED") { showMostPlayedDetail = true }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(state.mostPlayed.take(12)) { track ->
                    TrackCard(track = track, onClick = { viewModel.playTrack(track, state.mostPlayed) })
                }
            }
        }

        // ── Playlists ────────────────────────────────────────────────────────
        if (state.playlists.isNotEmpty()) {
            item {
                SectionLabel("PLAYLISTS")
            }

            items(state.playlists.chunked(2)) { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chunk.forEach { playlist ->
                        Box(modifier = Modifier.weight(1f)) {
                            val thumbs = remember(playlist, state.tracks) {
                                playlist.trackUris
                                    .take(4)
                                    .mapNotNull { uri -> state.tracks.find { it.uri == uri }?.thumbnailUri }
                            }
                            PlaylistCard(
                                playlist = playlist,
                                firstTrackThumbnails = thumbs,
                                onClick = { onPlaylistClick(playlist) },
                                onPlay = { viewModel.playPlaylist(playlist) },
                                onDelete = { onDeletePlaylist(playlist) },
                                onRename = { playlistToRename = playlist },
                                onUpdateThumb = { onUpdateThumb(playlist) }
                            )
                        }
                    }
                    if (chunk.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── Folders ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("FOLDERS")
                IconButton(onClick = onAddFolder) {
                    Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (state.folders.isEmpty()) {
            item {
                FolderEmptyCard(onAddFolder = onAddFolder)
            }
        } else {
            items(state.folders.keys.toList().chunked(2)) { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chunk.forEach { folderName ->
                        val tracks = state.folders[folderName] ?: emptyList()
                        val hasCurrentTrack = state.currentTrack?.let { current -> tracks.any { it.uri == current.uri } } == true
                        Box(modifier = Modifier.weight(1f)) {
                            FolderCard(
                                folderName = folderName,
                                trackCount = tracks.size,
                                isCurrentFolder = hasCurrentTrack,
                                onClick = { onFolderClick(folderName, tracks) }
                            )
                        }
                    }
                    if (chunk.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    // Detail overlays
    if (showFavoritesDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = "Favorites", trackUris = state.favoriteTracks.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showFavoritesDetail = false },
            onPlayPlaylist = { if (state.favoriteTracks.isNotEmpty()) viewModel.playTrack(state.favoriteTracks.first(), state.favoriteTracks) },
            onDeletePlaylist = {},
            onAddTrack = { viewModel.toggleFavorite(it) },
            onRemoveTrack = { uri -> state.favoriteTracks.find { it.uri == uri }?.let { viewModel.toggleFavorite(it) } },
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) }
        )
    }
    if (showRecentDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = "Recently Played", trackUris = state.recentlyPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showRecentDetail = false },
            onPlayPlaylist = { if (state.recentlyPlayed.isNotEmpty()) viewModel.playTrack(state.recentlyPlayed.first(), state.recentlyPlayed) },
            onDeletePlaylist = {}, onAddTrack = {}, onRemoveTrack = {},
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) }
        )
    }
    if (showMostPlayedDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = "Most Played", trackUris = state.mostPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showMostPlayedDetail = false },
            onPlayPlaylist = { if (state.mostPlayed.isNotEmpty()) viewModel.playTrack(state.mostPlayed.first(), state.mostPlayed) },
            onDeletePlaylist = {}, onAddTrack = {}, onRemoveTrack = {},
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) }
        )
    }

    // Rename dialog
    playlistToRename?.let { playlist ->
        var newName by remember(playlist.id) { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenamePlaylist(playlist, newName.trim())
                            playlistToRename = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = newName.isNotBlank()
                ) { Text("RENAME", fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text("CANCEL") }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun RowSectionHeader(title: String, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(title)
        TextButton(onClick = onViewAll) {
            Text("SEE ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QuickAccessCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = containerColor
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Surface(
                modifier = Modifier.size(48.dp).align(Alignment.TopStart),
                shape = RoundedCornerShape(16.dp),
                color = contentColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = contentColor, modifier = Modifier.size(26.dp))
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(text = "$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = contentColor)
                Text(text = label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = contentColor.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun QuickActionCard(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        modifier = modifier.bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Text(text = label, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playlist Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: Playlist,
    firstTrackThumbnails: List<String?> = emptyList(),
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit = {},
    onRename: () -> Unit = {},
    onUpdateThumb: () -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val performanceMode = LocalPerformanceMode.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showContextMenu = true }
            ),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Background: custom thumb > mosaic > solid gradient ────────────
            when {
                playlist.thumbnailUri != null -> {
                    AsyncImage(
                        model = playlist.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(if (performanceMode) 0.8f else 0.55f)
                    )
                    if (!performanceMode) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)), startY = 60f)
                            )
                        )
                    }
                }
                firstTrackThumbnails.isNotEmpty() && !performanceMode -> {
                    // Always show mosaic when we have any thumbnails, unless in performance mode
                    val thumbs = (firstTrackThumbnails + List(4) { null }).take(4)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = thumbs[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                            AsyncImage(model = thumbs[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = thumbs[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                            AsyncImage(model = thumbs[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)), startY = 70f)
                        )
                    )
                }
                else -> {
                    // Solid gradient fallback when empty playlist or in performance mode with no explicit thumb
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            if (performanceMode) {
                                SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        )
                    )
                    // Music note icon centered
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        modifier = Modifier.size(72.dp).align(Alignment.Center)
                    )
                }
            }

            // Play button
            Surface(
                onClick = onPlay,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).size(44.dp).bouncyClick {},
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                }
            }

            // Info
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(text = playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "${playlist.trackUris.size} TRACKS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }

            // ── Long-press context menu ───────────────────────────────────────
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                shape = RoundedCornerShape(20.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Play", fontWeight = FontWeight.Bold) },
                    onClick = { showContextMenu = false; onPlay() },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Rename", fontWeight = FontWeight.Bold) },
                    onClick = { showContextMenu = false; onRename() },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Set cover image", fontWeight = FontWeight.Bold) },
                    onClick = { showContextMenu = false; onUpdateThumb() },
                    leadingIcon = { Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.primary) }
                )
                HorizontalDivider(modifier = Modifier.alpha(0.1f).padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Delete", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error) },
                    onClick = { showContextMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Playlist Detail View
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    allTracks: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onAddTrack: (MusicTrack) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    isEditable: Boolean = true,
    currentTrackUri: String? = null,  // highlights the currently playing song
    onToggleFavorite: (MusicTrack) -> Unit = {}
) {
    var showAddTrack by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val playlistTracks = remember(allTracks, playlist.trackUris) {
        playlist.trackUris.mapNotNull { uri -> allTracks.find { it.uri == uri } }
    }
    val filteredPlaylistTracks = remember(playlistTracks, searchQuery) {
        if (searchQuery.isBlank()) playlistTracks
        else playlistTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    val playingInThisPlaylist = currentTrackUri != null && playlistTracks.any { it.uri == currentTrackUri }
    val performanceMode = LocalPerformanceMode.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f),
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
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 4.dp)) {

            // ── Header ────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail — mosaic or custom art
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 10.dp,
                    border = BorderStroke(
                        if (playingInThisPlaylist) 2.dp else 1.dp,
                        if (playingInThisPlaylist) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                ) {
                    if (playlist.thumbnailUri != null) {
                        AsyncImage(model = playlist.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else if (playlistTracks.isNotEmpty()) {
                        val thumbs = (playlistTracks.map { it.thumbnailUri } + List(4) { null }).take(4)
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.weight(1f)) {
                                AsyncImage(model = thumbs[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                                AsyncImage(model = thumbs[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                            }
                            Row(modifier = Modifier.weight(1f)) {
                                AsyncImage(model = thumbs[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                                AsyncImage(model = thumbs[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                            }
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                Spacer(Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${playlistTracks.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (playingInThisPlaylist) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PlayingBarsIndicator()
                                    Text("PLAYING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }
                }

                if (isEditable) {
                    IconButton(
                        onClick = { onDeletePlaylist(playlist) },
                        modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { onPlayPlaylist(playlist) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PLAY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { onPlayPlaylist(playlist) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = if (performanceMode) null else ButtonDefaults.buttonElevation(4.dp)
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SHUFFLE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                }
                if (isEditable) {
                    FilledTonalIconButton(
                        onClick = { showAddTrack = true },
                        modifier = Modifier.size(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Search ────────────────────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search in playlist…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                    }
                },
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.07f))
            Spacer(Modifier.height(6.dp))

            if (searchQuery.isNotEmpty()) {
                Text(
                    "${filteredPlaylistTracks.size} result${if (filteredPlaylistTracks.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            // ── Track list ────────────────────────────────────────────────────
            if (filteredPlaylistTracks.isEmpty() && searchQuery.isNotEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(48.dp).alpha(0.12f), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text("No matches", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (playlistTracks.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(64.dp).alpha(0.1f), tint = MaterialTheme.colorScheme.onSurface)
                        Text("Playlist is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        if (isEditable) {
                            FilledTonalButton(onClick = { showAddTrack = true }, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Tracks", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    itemsIndexed(filteredPlaylistTracks, key = { _, t -> t.uri }) { index, track ->
                        val isCurrent = track.uri == currentTrackUri
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Track number or playing bars
                            Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                                if (isCurrent) {
                                    PlayingBarsIndicator()
                                } else {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                TrackItem(
                                    track = track,
                                    isCurrent = isCurrent,
                                    onClick = { onPlayTrack(track, filteredPlaylistTracks) },
                                    onDelete = { onRemoveTrack(track.uri) },
                                    onAddToPlaylist = {},
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    playlists = emptyList(),
                                    deleteLabel = "Remove"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add Tracks dialog ─────────────────────────────────────────────────────
    if (showAddTrack) {
        val available = allTracks.filter { it.uri !in playlist.trackUris }
        AlertDialog(
            onDismissRequest = { showAddTrack = false },
            title = { Text("ADD TRACKS", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                if (available.isEmpty()) {
                    Text("All tracks are already added.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.height(430.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(available) { track ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().bouncyClick { onAddTrack(track); showAddTrack = false },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp)) {
                                        AsyncImage(model = track.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                        Text(track.artist ?: "Unknown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddTrack = false }) { Text("CANCEL", fontWeight = FontWeight.Black) } },
            shape = RoundedCornerShape(36.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Full Player View
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerView(
    state: MusicUiState,
    aiState: NowPlayingAiUiState,
    aiViewModel: NowPlayingAiViewModel = hiltViewModel(),
    playbackPosition: Long,
    duration: Long,
    sliderPos: Long?,
    visualizerData: FloatArray,
    onSliderChange: (Long) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
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
    onToggleRotation: () -> Unit,
    onTogglePip: () -> Unit,
    onOpenAi: () -> Unit,
    onToggleMusicSettings: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetEqualizerPreset: (String) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onToggleVisualizer: () -> Unit,
    equalizerPresets: List<String>
) {
    val track = state.currentTrack ?: return
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyricsCustomization by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current

    val infiniteTransition = rememberInfiniteTransition(label = "playerRot")
    val rotation by if (state.performanceMode) {
        remember { mutableFloatStateOf(0f) }
    } else {
        infiniteTransition.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Restart),
            label = "artRot"
        )
    }

    if (state.showMusicSettings) {
        MusicSettingsSheet(
            state = state,
            equalizerPresets = equalizerPresets,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSetEqualizerPreset = onSetEqualizerPreset,
            onToggleVisualizer = onToggleVisualizer,
            onDismiss = onToggleMusicSettings
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        dragHandle = null,
        modifier = Modifier.fillMaxSize(),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

            // Blurred art background
            if (!state.performanceMode) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect = AndroidRenderEffect.createBlurEffect(130f, 130f, AndroidShader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            alpha = 0.11f
                        },
                    error = rememberVectorPainter(Icons.Rounded.MusicNote)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                                )
                            )
                        )
                )
            }

            // ── Single weight-based Column — no scroll, no overlays ───────────
            // Root cause of clipping: verticalScroll + fixed overlay boxes meant
            // the overlays covered content without the scroll knowing their height.
            // This Column uses weight(1f) on the art Box so Compose distributes
            // all remaining space correctly and nothing is cut off.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── 1. Header ─────────────────────────────────────────────────
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Dismiss pill
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.height(38.dp).wrapContentWidth().bouncyClick {},
                        shape = RoundedCornerShape(19.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    // Active sleep timer badge
                    AnimatedVisibility(visible = state.sleepTimerActive && state.sleepTimerRemaining != null) {
                        Surface(
                            onClick = { showSleepTimerPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.error)
                                Text(
                                    state.sleepTimerRemaining?.let { formatDuration(it) } ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // More menu
                    Box {
                        Surface(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(19.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MoreVert, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            DropdownMenuItem(text = { Text("Circular art", fontWeight = FontWeight.Bold) }, onClick = { onSetArtShape("CIRCLE"); showOverflowMenu = false }, leadingIcon = { RadioButton(selected = state.artShape == "CIRCLE", onClick = null) })
                            DropdownMenuItem(text = { Text("Square art", fontWeight = FontWeight.Bold) }, onClick = { onSetArtShape("SQUARE"); showOverflowMenu = false }, leadingIcon = { RadioButton(selected = state.artShape == "SQUARE", onClick = null) })
                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                            DropdownMenuItem(text = { Text("Rotate art", fontWeight = FontWeight.Bold) }, onClick = { onToggleRotation(); showOverflowMenu = false }, leadingIcon = { Switch(checked = state.rotationEnabled, onCheckedChange = null, modifier = Modifier.scale(0.75f)) })
                            DropdownMenuItem(text = { Text("Sleep timer", fontWeight = FontWeight.Bold) }, onClick = { showOverflowMenu = false; showSleepTimerPicker = true }, leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary) })
                            DropdownMenuItem(
                                text = { Text("Music settings", fontWeight = FontWeight.Bold) },
                                onClick = { showOverflowMenu = false; onToggleMusicSettings() },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Lyrics settings", fontWeight = FontWeight.Bold) },
                                onClick = { showOverflowMenu = false; showLyricsCustomization = true },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                            DropdownMenuItem(text = { Text("Stop & exit", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error) }, onClick = { showOverflowMenu = false; onStop() }, leadingIcon = { Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }

                if (showLyricsCustomization) {
                    LyricCustomizationDialog(
                        state = aiState.lyricsState,
                        onDismiss = { showLyricsCustomization = false },
                        onToggleSeek = { aiViewModel.toggleSeekEnabled() },
                        onSetLayout = { aiViewModel.setLyricsLayout(it) },
                        onSetFont = { aiViewModel.setLyricsFont(it) }
                    )
                }

                // ── 2. Album art — weight(1f) fills all remaining vertical space ─
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val artMaxSize = (configuration.screenWidthDp * 0.76f).dp.coerceAtMost(300.dp)
                    val artShape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(56.dp)

                    if (!state.performanceMode && state.showVisualizer) {
                        AudioVisualizerHalo(
                            visualizerData = visualizerData,
                            isPlaying = state.isPlaying,
                            artMaxSize = artMaxSize,
                            shape = state.artShape,
                            thumbnailUri = track.thumbnailUri,
                            rotation = if (state.isPlaying && state.rotationEnabled) rotation else 0f
                        )
                    } else if (!state.performanceMode) {
                        val haloAlpha by animateFloatAsState(if (state.isPlaying) 0.45f else 0.1f, tween(900), label = "hA")
                        val haloScale by animateFloatAsState(if (state.isPlaying) 1.08f else 1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "hS")
                        Box(modifier = Modifier.size(artMaxSize).scale(haloScale).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha * 0.4f), Color.Transparent)), artShape))
                    }

                    AnimatedContent(
                        targetState = track.thumbnailUri,
                        transitionSpec = {
                            (fadeIn(tween(500)) + scaleIn(initialScale = 0.9f, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)))
                                .togetherWith(fadeOut(tween(400)) + scaleOut(targetScale = 1.1f))
                        },
                        label = "fullArtTransition"
                    ) { uri ->
                        Surface(
                            modifier = Modifier
                                .size(artMaxSize)
                                .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f)
                                .shadow(if (state.performanceMode) 6.dp else 36.dp, artShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            shape = artShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                error = rememberVectorPainter(Icons.Rounded.MusicNote)
                            )
                        }
                    }
                }

                // ── 3. Track info ─────────────────────────────────────────────
                Spacer(Modifier.height(24.dp))
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        val enter = fadeIn(tween(400)) + slideInVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) { it / 6 }
                        val exit = fadeOut(tween(250)) + slideOutVertically(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) { -it / 8 }
                        enter.togetherWith(exit)
                    },
                    label = "trackInfo"
                ) { currentTrack ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(text = currentTrack.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        val artistText = currentTrack.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
                        Text(text = artistText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    }
                }

                // ── 4. Slider ─────────────────────────────────────────────────
                Spacer(Modifier.height(24.dp))
                val currentPos = sliderPos ?: playbackPosition
                Column(modifier = Modifier.fillMaxWidth()) {
                    SquigglySlider(
                        value = currentPos.toFloat(),
                        onValueChange = { onSliderChange(it.toLong()) },
                        onValueChangeFinished = onSliderChangeFinished,
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        isPlaying = state.isPlaying
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(currentPos), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    }
                }

                // ── 5. Transport controls ─────────────────────────────────────
                Spacer(Modifier.height(28.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PlayerControlButton(size = 56.dp, onClick = onSkipPrev) {
                        Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    val playScale by animateFloatAsState(
                        if (state.isPlaying) 1f else 1.08f,
                        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                        label = "pS"
                    )
                    Surface(
                        onClick = onTogglePlay,
                        modifier = Modifier.width(96.dp).height(64.dp).scale(if (state.performanceMode) 1f else playScale).bouncyClick {},
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = if (state.isPlaying) 6.dp else 18.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Crossfade(targetState = state.isPlaying, animationSpec = tween(220), label = "ppCF") { playing ->
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    PlayerControlButton(size = 56.dp, onClick = onSkipNext) {
                        Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // ── 6. Secondary controls ─────────────────────────────────────
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    // Favorite
                    val favScale by animateFloatAsState(if (track.isFavorite) 1.25f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "fS")
                    IconButton(onClick = { onToggleFavorite(track) }, modifier = Modifier.size(48.dp)) {
                        Icon(if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (track.isFavorite) Color(0xFFE05C5C) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(24.dp).scale(if (state.performanceMode) 1f else favScale))
                    }
                    // Shuffle
                    val shuffleActive = state.isShuffleOn
                    Box(contentAlignment = Alignment.BottomCenter) {
                        IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                            Icon(if (shuffleActive) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle, null, tint = if (shuffleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(24.dp))
                        }
                        if (shuffleActive) Box(modifier = Modifier.size(5.dp).offset(y = (-4).dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                    // Repeat
                    val repeatActive = state.repeatMode != Player.REPEAT_MODE_OFF
                    Box(contentAlignment = Alignment.BottomCenter) {
                        IconButton(onClick = onToggleRepeat, modifier = Modifier.size(48.dp)) {
                            Icon(
                                when (state.repeatMode) { 
                                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn 
                                    Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn 
                                    else -> Icons.Rounded.Repeat 
                                }, 
                                null, 
                                tint = if (repeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        if (repeatActive) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .offset(y = (-4).dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                    // Queue
                    IconButton(onClick = { showQueue = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(24.dp))
                    }
                    // AI / Lyrics
                    if (aiState.isAiEnabled) {
                        AiSparkleButton(onClick = onOpenAi)
                    } else {
                        IconButton(onClick = onOpenAi, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Lyrics, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
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
            onTrackSelect = { onTrackSelect(it) },
            onDismiss = { showQueue = false },
            onMove = { from, to -> onMoveQueueItem(from, to) },
            onRemove = { index -> onRemoveQueueItem(index) },
            onClear = { onClearQueue() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicSettingsSheet(
    state: MusicUiState,
    equalizerPresets: List<String>,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetEqualizerPreset: (String) -> Unit,
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
        ) {
            Text(
                "Music Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Text(
                "Playback Speed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speeds.forEach { speed ->
                    val isSelected = state.playbackSpeed == speed
                    Surface(
                        onClick = { onSetPlaybackSpeed(speed) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${speed}x",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Audio Visualizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Show animated halo around art",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.showVisualizer,
                    onCheckedChange = { onToggleVisualizer() }
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Equalizer Presets",
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
        }
    }
}

@Composable
fun AudioVisualizerHalo(
    visualizerData: FloatArray,
    isPlaying: Boolean,
    artMaxSize: Dp,
    shape: String,
    thumbnailUri: String?,
    rotation: Float = 0f
) {
    val dynamicColors = rememberDynamicColors(thumbnailUri)
    val primary = dynamicColors.primary
    val tertiary = dynamicColors.secondary
    
    val smoothedData = remember { mutableStateListOf<Float>() }
    LaunchedEffect(visualizerData) {
        if (visualizerData.isEmpty()) return@LaunchedEffect
        if (smoothedData.size != 64) {
            smoothedData.clear()
            repeat(64) { smoothedData.add(0f) }
        }
        val chunkSize = (visualizerData.size / 64).coerceAtLeast(1)
        for (i in 0 until 64) {
            var sum = 0f
            val start = i * chunkSize
            val end = (start + chunkSize).coerceAtMost(visualizerData.size)
            for (j in start until end) {
                sum += visualizerData[j]
            }
            val avg = if (chunkSize > 0) sum / chunkSize else 0f
            // Higher sensitivity and better smoothing
            smoothedData[i] = smoothedData[i] * 0.12f + avg * 0.88f
        }
    }

    val baseScale by animateFloatAsState(
        if (isPlaying) 1.02f + (smoothedData.take(12).average().toFloat() / 120f).coerceAtMost(0.15f) else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "baseScale"
    )

    Canvas(modifier = Modifier.size(artMaxSize).scale(baseScale).rotate(rotation)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val path = Path()
        
        val points = 64
        val angleStep = (2 * Math.PI / points).toFloat()
        
        for (i in 0..points) {
            val idx = i % points
            val rawMagnitude = smoothedData.getOrElse(idx) { 0f }
            // Dynamic sensitivity: boost higher frequencies slightly
            val freqFactor = 1f + (idx.toFloat() / points) * 0.5f
            val magnitude = (rawMagnitude * freqFactor / 60f).coerceAtMost(50f)
            
            val offsetDist = 14.dp.toPx() + magnitude.dp.toPx()
            val angle = i * angleStep
            
            val x: Float
            val y: Float
            
            if (shape == "CIRCLE") {
                val r = radius + offsetDist
                x = center.x + kotlin.math.cos(angle.toDouble()).toFloat() * r
                y = center.y + kotlin.math.sin(angle.toDouble()).toFloat() * r
            } else {
                // Square/Rounded mapping matching the 56.dp corner radius
                val cosA = kotlin.math.cos(angle.toDouble()).toFloat()
                val sinA = kotlin.math.sin(angle.toDouble()).toFloat()
                
                val absCos = kotlin.math.abs(cosA)
                val absSin = kotlin.math.abs(sinA)
                
                // Superellipse-like formula for rounded square
                val n = 4.5f // exponent for roundness
                val rBase = radius * (1f / Math.pow((Math.pow(absCos.toDouble(), n.toDouble()) + Math.pow(absSin.toDouble(), n.toDouble())), 1.0/n)).toFloat()
                
                val r = rBase + offsetDist
                x = center.x + cosA * r
                y = center.y + sinA * r
            }
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()

        // Subtle Glow effect
        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.3f), Color.Transparent),
                center = center,
                radius = radius + 60.dp.toPx()
            ),
            style = Fill
        )

        drawPath(
            path = path,
            color = tertiary.copy(alpha = 0.7f),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.cornerPathEffect(12f)
            )
        )
        
        // Inner pulse line
        drawPath(
            path = path,
            color = primary.copy(alpha = 0.4f),
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sleep Timer Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerSheet(
    currentTimerActive: Boolean,
    remainingMs: Long?,
    onSet: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    FilledTonalButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Black)
                    }
                }
            }

            if (currentTimerActive && remainingMs != null) {
                Spacer(Modifier.height(14.dp))
                val prog by animateFloatAsState(1f - (remainingMs.toFloat() / (90 * 60_000f)).coerceIn(0f, 1f), tween(800), label = "tP")
                LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = MaterialTheme.colorScheme.error, trackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.08f))
            Spacer(Modifier.height(16.dp))

            listOf(listOf(5, 10, 15), listOf(30, 45, 60), listOf(90)).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { mins ->
                        Surface(
                            modifier = Modifier.weight(1f).height(70.dp).bouncyClick { onSet(mins) },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
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

// ─────────────────────────────────────────────────────────────────────────────
// Queue Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueSheet(
    queue: List<QueueEntry>,
    currentTrack: MusicTrack,
    onTrackSelect: (MusicTrack) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val haptic = LocalHapticFeedback.current
    
    // For interactive reordering
    var listData by remember(queue) { mutableStateOf(queue) }
    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(lazyListState) { fromIndex, toIndex ->
        // Adjust for the header item at index 0
        val adjFrom = fromIndex - 1
        val adjTo = toIndex - 1
        
        if (adjFrom >= 0 && adjTo >= 0 && adjFrom < listData.size && adjTo < listData.size) {
            listData = listData.toMutableList().apply {
                add(adjTo, removeAt(adjFrom))
            }
            onMove(adjFrom, adjTo)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(), 
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp), 
                        shape = RoundedCornerShape(16.dp), 
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Rounded.QueueMusic, 
                                null, 
                                tint = MaterialTheme.colorScheme.onPrimaryContainer, 
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            "Up Next", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "${queue.size} tracks in queue", 
                            style = MaterialTheme.typography.labelMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onClear(); onDismiss() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear", fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.08f))
            Spacer(Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            Icons.Rounded.QueuePlayNext, 
                            null, 
                            modifier = Modifier.size(64.dp).alpha(0.1f), 
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Queue is empty", 
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), 
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .dragDropColumn(dragDropState, haptic), 
                    verticalArrangement = Arrangement.spacedBy(10.dp), 
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        Text(
                            "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        QueueItem(
                            index = -1,
                            qTrack = currentTrack,
                            isCurrent = true,
                            onTrackSelect = {},
                            onRemove = {},
                            isDraggable = false,
                            modifier = Modifier.animateItem()
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "UP NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    itemsIndexed(listData, key = { _, entry -> entry.id }) { index, entry ->
                        val qTrack = entry.track
                        val isCurrent = qTrack.uri == currentTrack.uri
                        if (isCurrent) return@itemsIndexed

                        QueueItem(
                            index = index + 1, // Adjusted for the header items (NOW PLAYING label + current track + UP NEXT label)
                            qTrack = qTrack,
                            isCurrent = false,
                            onTrackSelect = { onTrackSelect(qTrack) },
                            onRemove = { onRemove(index) },
                            dragDropState = dragDropState,
                            isDraggable = true,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueItem(
    index: Int,
    qTrack: MusicTrack,
    isCurrent: Boolean,
    onTrackSelect: (MusicTrack) -> Unit,
    onRemove: (Int) -> Unit,
    isDraggable: Boolean = true,
    dragDropState: DragDropState? = null,
    modifier: Modifier = Modifier
) {
    var isPendingDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000)
            if (isPendingDelete) {
                onRemove(index)
            }
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart && !isCurrent) {
                isPendingDelete = true
                true
            } else false
        }
    )

    if (isPendingDelete) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                .clickable { 
                    isPendingDelete = false
                    scope.launch { 
                        dismissState.snapTo(SwipeToDismissBoxValue.Settled) 
                    } 
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text(
                    "Removed \"${qTrack.title}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "UNDO",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        return
    }

    val dynamicColors = if (LocalPerformanceMode.current) {
        DynamicColors(
            primary = MaterialTheme.colorScheme.primary,
            secondary = MaterialTheme.colorScheme.secondary,
            background = MaterialTheme.colorScheme.background,
            surface = MaterialTheme.colorScheme.surface,
            onSurface = MaterialTheme.colorScheme.onSurface
        )
    } else {
        rememberDynamicColors(qTrack.thumbnailUri)
    }
    val dragModifier = if (isDraggable && dragDropState != null) {
        Modifier.dragDropItem(index, dragDropState)
    } else Modifier

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier.then(dragModifier),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            val progress = dismissState.progress
                            if (progress > 0.05f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            dynamicColors.primary.copy(alpha = 0.6f * progress)
                                        ),
                                        startX = size.width * (1f - progress),
                                        endX = size.width
                                    )
                                )
                            }
                        }
                    }
            )
        }
    ) {
        Surface(
            onClick = { onTrackSelect(qTrack) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            },
            border = if (isCurrent) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            } else null
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    if (isCurrent) PlayingBarsIndicator()
                    else Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 2.dp
                ) {
                    AsyncImage(
                        model = qTrack.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = rememberVectorPainter(Icons.Rounded.MusicNote)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        qTrack.title,
                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        qTrack.artist?.uppercase() ?: "UNKNOWN ARTIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isDraggable) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 4.dp).size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlButton(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size).bouncyClick {},
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center, content = { content() })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini Player
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// 4. Spinning Download Popup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SpinningDownloadPopup(
    track: com.frerox.toolz.data.catalog.CatalogTrack,
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

@Composable
fun MiniPlayer(
    track: MusicTrack,
    isPlaying: Boolean,
    progress: Long,
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
    val performanceMode = LocalPerformanceMode.current
    val isDark = LocalIsDarkTheme.current
    val dynamicColors = rememberDynamicColors(track.thumbnailUri)
    val targetProgress = if (duration > 0) progress.toFloat() / duration else 0f

    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "miniSwipeOffset"
    )

    // Progress animation — skip in performance mode
    val animatedProgress by if (performanceMode) {
        remember(targetProgress) { mutableFloatStateOf(targetProgress) }
    } else {
        animateFloatAsState(targetProgress, tween(450, easing = LinearOutSlowInEasing), label = "miniProg")
    }

    // Corner radius morphs between collapsed/expanded
    val cornerRadius by animateDpAsState(
        if (isExpanded) 24.dp else 40.dp,
        if (performanceMode) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
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
        if (performanceMode) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "miniElev"
    )

    // Font mapping for lyrics
    val fontFamily = remember(lyricsState?.fontFamily) {
        when(lyricsState?.fontFamily) {
            LyricsFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
            LyricsFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
            LyricsFont.CURSIVE -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.Default
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .graphicsLayer {
                translationX = animatedOffsetX
                rotationZ = animatedOffsetX / 20f // Subtle tilt while swiping
                alpha = 1f - (kotlin.math.abs(animatedOffsetX) / 600f).coerceIn(0f, 0.5f)
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 150) {
                            onPrevious()
                        } else if (offsetX < -150) {
                            onNext()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change: PointerInputChange, dragAmount: Float ->
                        change.consume()
                        offsetX += dragAmount
                    }
                )
            }
            .animateContentSize(
                if (performanceMode) snap()
                else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            )
            .shadow(elevation, RoundedCornerShape(cornerRadius), spotColor = dynamicColors.primary.copy(alpha = 0.3f))
            // clip() ensures nothing (progress wash, bottom bar) bleeds outside the pill
            .clip(RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = Color.Transparent,
        shape = RoundedCornerShape(cornerRadius)
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
        Box(modifier = Modifier.background(Brush.verticalGradient(listOf(lighterSurface, darkerSurface)))) {
            // ── Background progress wash — covers FULL surface height ─────────
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

                    // Consolidated Thumbnail with Pulse and Download Indicator
                    val infiniteTransitionPulse = rememberInfiniteTransition(label = "playerPulse")
                    val pulseScalePlayer by if (downloadCount > 0 && !performanceMode) {
                        infiniteTransitionPulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.04f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = FastOutSlowInEasing),
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
                            .scale(pulseScalePlayer)
                            .graphicsLayer {
                                rotationZ = if (rotationEnabled && isPlaying && !performanceMode) artRotation else 0f
                            }
                            .shadow(if (performanceMode) 2.dp else 6.dp, finalArtShape)
                            .clip(finalArtShape)
                            .then(
                                if (downloadCount > 0) Modifier.border(2.dp, dynamicColors.primary.copy(alpha = 0.6f), finalArtShape)
                                else Modifier
                            )
                    ) {
                        AnimatedContent(
                            targetState = track.thumbnailUri,
                            transitionSpec = {
                                if (performanceMode) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(tween(400)) + scaleIn(initialScale = 0.85f) togetherWith
                                            fadeOut(tween(400)) + scaleOut(targetScale = 0.85f)
                                }
                            },
                            label = "artTransition"
                        ) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = rememberVectorPainter(Icons.Rounded.MusicNote)
                            )
                        }

                        if (downloadCount > 0) {
                            val infiniteTransitionDownload = rememberInfiniteTransition(label = "download")
                            val pulseScale by if (performanceMode) {
                                remember { mutableFloatStateOf(1f) }
                            } else {
                                infiniteTransitionDownload.animateFloat(
                                    initialValue = 0.95f,
                                    targetValue = 1.05f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1200, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "pulse"
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                                    .scale(pulseScale)
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { avgDownloadProgress / 100f },
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
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
                            targetState = track,
                            transitionSpec = {
                                if (performanceMode) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    if (targetState.uri != initialState.uri) {
                                        (slideInHorizontally { it / 2 } + fadeIn(tween(300))) togetherWith
                                                (slideOutHorizontally { -it / 2 } + fadeOut(tween(300)))
                                    } else {
                                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                    }.using(SizeTransform(clip = false))
                                }
                            },
                            label = "trackInfoTransition"
                        ) { currentTrack ->
                            Column {
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isDark) Color(0xFFEEEEEE) else Color(0xFF111111)
                                )
                                val artistText = currentTrack.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
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
                    Surface(
                        onClick = onExpand,
                        modifier = Modifier.size(42.dp).bouncyClick {},
                        shape = CircleShape,
                        color = dynamicColors.primary.copy(alpha = if (isExpanded) 0.25f else 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.ExpandLess,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp).rotate(if (performanceMode) (if (isExpanded) 0f else 180f) else chevronRotF)
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Play/Pause
                    Surface(
                        onClick = if (isResolving) ({}) else onTogglePlay,
                        modifier = Modifier.size(54.dp).bouncyClick {}.alpha(if (isResolving) 0.6f else 1f),
                        shape = CircleShape,
                        color = dynamicColors.primary,
                        contentColor = if (isDark) Color(0xFF111111) else Color(0xFFEEEEEE),
                        shadowElevation = if (performanceMode) 2.dp else 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = if (isDark) Color(0xFF111111) else Color(0xFFEEEEEE),
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
                                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(30.dp))
                                }
                            }
                        }
                    }
                }

                // ── Expandable lyrics + progress section ──────────────────────
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = if (performanceMode) fadeIn() + expandVertically() else
                        fadeIn(tween(220)) + expandVertically(
                            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                            expandFrom = Alignment.Top
                        ),
                    exit = if (performanceMode) fadeOut() + shrinkVertically() else
                        fadeOut(tween(160)) + shrinkVertically(
                            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                            shrinkTowards = Alignment.Top
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
                                    Text(
                                        "♪  Enjoy the music  ♪",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = lyricColor.copy(alpha = 0.35f),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = fontFamily,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
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
                                        Text(
                                            "♪  Enjoy the music  ♪",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = lyricColor.copy(alpha = 0.35f),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = fontFamily,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
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
                            LinearProgressIndicator(
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
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = dynamicColors.primary,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onSet: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Sleep Timer", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { mins ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().bouncyClick { onSet(mins); onDismiss() },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$mins",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(40.dp)
                            )
                            Text("minutes", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { onSet(null); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel timer", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                }
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(36.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NEW PLAYLIST", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp),
                enabled = name.isNotBlank()
            ) {
                Text("CREATE", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", fontWeight = FontWeight.Bold) }
        },
        shape = RoundedCornerShape(36.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun MultiSelectPlaylistPicker(playlists: List<Playlist>, onDismiss: () -> Unit, onPlaylistSelected: (Playlist) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADD TO PLAYLIST", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists yet.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(playlists) { playlist ->
                        PlaylistPickerRow(playlist = playlist) { onPlaylistSelected(playlist) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL", fontWeight = FontWeight.Black) } },
        shape = RoundedCornerShape(36.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun PlaylistPickerDialog(playlists: List<Playlist>, onDismiss: () -> Unit, onSelect: (Playlist) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADD TO PLAYLIST", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists available.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(playlists) { playlist ->
                        PlaylistPickerRow(playlist = playlist) { onSelect(playlist) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL", fontWeight = FontWeight.Black) } },
        shape = RoundedCornerShape(36.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun FolderEmptyCard(onAddFolder: () -> Unit) {
    Surface(
        onClick = onAddFolder,
        modifier = Modifier.fillMaxWidth().height(140.dp).bouncyClick {},
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.CreateNewFolder,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No custom folders added",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Tap to select a music directory",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun PlaylistPickerRow(playlist: Playlist, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(playlist.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("${playlist.trackUris.size} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun LyricCustomizationDialog(
    state: AiLyricsState,
    onDismiss: () -> Unit,
    onToggleSeek: () -> Unit,
    onSetLayout: (LyricsLayout) -> Unit,
    onSetFont: (LyricsFont) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Done", fontWeight = FontWeight.ExtraBold)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Settings,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text("Lyrics Style", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Seek toggle
                Surface(
                    onClick = onToggleSeek,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fast Seeking", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text("Long press lyrics to jump in song", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.isSeekEnabled, 
                            onCheckedChange = { onToggleSeek() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                // Alignment
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Alignment", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsLayout.entries.forEach { layout ->
                            val selected = state.layout == layout
                            val icon = when(layout) {
                                LyricsLayout.LEFT -> Icons.AutoMirrored.Rounded.Notes
                                LyricsLayout.CENTER -> Icons.Rounded.FormatAlignCenter
                                LyricsLayout.RIGHT -> Icons.AutoMirrored.Rounded.FormatAlignRight
                            }
                            
                            Surface(
                                onClick = { onSetLayout(layout) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(icon, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(layout.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Font
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Typography", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LyricsFont.entries.forEach { font ->
                            val selected = state.fontFamily == font
                            val fontFamily = when(font) {
                                LyricsFont.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.Default
                                LyricsFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
                                LyricsFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
                                LyricsFont.CURSIVE -> androidx.compose.ui.text.font.FontFamily.Cursive
                            }

                            Surface(
                                onClick = { onSetFont(font) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        font.name.lowercase().replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = fontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

private fun highlightSearch(text: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val index = text.indexOf(query, ignoreCase = true)
    if (index == -1) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Black)) {
            append(text.substring(index, index + query.length))
        }
        append(text.substring(index + query.length))
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun filterTracks(tracks: List<MusicTrack>, query: String): List<MusicTrack> {
    if (query.isBlank()) return tracks
    return tracks.filter {
        it.title.contains(query, ignoreCase = true) ||
                it.artist?.contains(query, ignoreCase = true) == true
    }
}
