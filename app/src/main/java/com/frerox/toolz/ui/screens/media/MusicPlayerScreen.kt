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

package com.frerox.toolz.ui.screens.media

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
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
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
    var showKaraokeSettings by remember { mutableStateOf(false) }
    var selectedTrackForDownload by remember { mutableStateOf<CatalogTrack?>(null) }

    val catalogGridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()

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
    val tabs = remember(state.karaokeEnabled, state.isOnline) {
        listOfNotNull(
            "Tracks",
            "Library",
            if (state.isOnline && state.karaokeEnabled) "Karaoke" else null,
            if (state.isOnline) "Catalog" else null
        )
    }
    val currentTabLabel = tabs.getOrNull(currentTab)

    LaunchedEffect(state.currentTrack) {
        state.currentTrack?.let { aiViewModel.updateSong(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.playbackPosition.collect { pos ->
            aiViewModel.updateProgress(pos)
        }
    }
    LaunchedEffect(currentTabLabel) {
        if (currentTabLabel == "Catalog") {
            catalogViewModel.refreshOnOpen(state.currentTrack)
        }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                state = state,
                currentTab = currentTab,
                currentTabLabel = currentTabLabel,
                showSortMenu = showSortMenu,
                onShowSortMenu = { showSortMenu = it },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onBack = onBack,
                onAddPlaylist = { showPlaylistDialog = true },
                onRefresh = {
                    if (currentTabLabel == "Catalog") catalogViewModel.loadStorefront()
                    else viewModel.scanMusic()
                },
                onSort = { viewModel.setSortOrder(it) },
                onClearSelection = { viewModel.clearSelection() },
                onMultiAddPlaylist = { showMultiSelectPlaylistPicker = true },
                onResetCatalogOnboarding = { catalogViewModel.resetOnboarding() },
                onGoToTop = {
                    scope.launch {
                        catalogGridState.animateScrollToItem(0)
                    }
                }
            )
        },
        bottomBar = {
            ScreenBottomBar(
                state = state,
                aiState = aiState,
                playbackPositionFlow = viewModel.playbackPosition,
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
                isResolving = state.isResolvingCatalog || aiState.isResolvingInstrumental
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
                                (slideInHorizontally(tween(400, easing = EaseOutQuart)) { dir * it } + fadeIn(tween(300)))
                                    .togetherWith(
                                        slideOutHorizontally(tween(400, easing = EaseOutQuart)) { -dir * it / 3 } + fadeOut(tween(300))
                                    )
                            }
                        },
                        label = "TabContent"
                    ) { tabIndex ->
                        val tab = if (tabIndex < tabs.size) tabs[tabIndex] else "Tracks"

                        when (tab) {
                            "Tracks" -> TrackList(
                                tracks = filteredTracks,
                                state = state,
                                viewModel = viewModel,
                                searchQuery = searchQuery,
                                onOpenFullPlayer = { showFullPlayer = true },
                                onDownload = {
                                    val trackId = it.sourceUrl?.substringAfter("v=")?.substringBefore("&") ?: it.uri.hashCode().toString()
                                    selectedTrackForDownload = CatalogTrack(id = trackId, title = it.title, artist = it.artist ?: "Unknown", sourceUrl = it.sourceUrl ?: "", thumbnailUrl = it.thumbnailUri ?: "", duration = it.duration)
                                }
                            )
                            "Library" -> LibrarySection(
                                state = state,
                                viewModel = viewModel,
                                onCreatePlaylist = { showPlaylistDialog = true },
                                onPlaylistClick = { selectedPlaylist = it },
                                onUpdateThumb = { selectedPlaylist = it; playlistThumbLauncher.launch("image/*") },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                onFolderClick = { name, tracks -> selectedFolderTracks = name to tracks },
                                onAddFolder = { folderLauncher.launch(null) },
                                onDownload = {
                                    val trackId = it.sourceUrl?.substringAfter("v=")?.substringBefore("&") ?: it.uri.hashCode().toString()
                                    selectedTrackForDownload = CatalogTrack(id = trackId, title = it.title, artist = it.artist ?: "Unknown", sourceUrl = it.sourceUrl ?: "", thumbnailUrl = it.thumbnailUri ?: "", duration = it.duration)
                                }
                            )
                            "Karaoke" -> KaraokeTab(
                                viewModel = viewModel,
                                musicState = state,
                                onStartKaraoke = { track ->
                                    if (track.aiLyrics.isNullOrEmpty() && track.sourceUrl == null) {
                                        android.widget.Toast.makeText(context, "No available lyrics were found", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.playTrack(track)
                                        viewModel.toggleKaraokeMode()
                                        showFullPlayer = true
                                    }
                                },
                                onShowSettings = { showKaraokeSettings = true }
                            )
                            "Catalog" -> CatalogContent(
                                catalogViewModel = catalogViewModel,
                                musicRepository = viewModel.repository,
                                localTracks = state.tracks,
                                currentTrack = state.currentTrack,
                                gridState = catalogGridState,
                                onPlayTrack = { uri, title, artist, thumbUrl, sourceUrl ->
                                    viewModel.playUri(uri, title, artist, thumbUrl, sourceUrl)
                                    showFullPlayer = true
                                },
                                onPlayInKaraoke = { uri, title, artist, thumbUrl, sourceUrl ->
                                    viewModel.playUri(uri, title, artist, thumbUrl, sourceUrl)
                                    viewModel.setKaraokeMode(true)
                                    showFullPlayer = true
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

        if (showKaraokeSettings) {
            KaraokeSettingsModal(
                speechCorrectionEnabled = aiState.karaokeSpeechCorrectionEnabled,
                onSpeechCorrectionToggle = { aiViewModel.setKaraokeSpeechCorrectionEnabled(it) },
                quickSingEnabled = aiState.quickSingEnabled,
                onQuickSingToggle = { aiViewModel.setQuickSingEnabled(it) },
                autoRecordEnabled = aiState.autoRecordEnabled,
                onAutoRecordToggle = { aiViewModel.setAutoRecordEnabled(it) },
                singConfidentlyMode = aiState.singConfidentlyMode,
                onSingConfidentlyModeChange = { aiViewModel.setSingConfidentlyMode(it) },
                wordSyncEnabled = aiState.lyricsState.isKaraokeWordSyncEnabled,
                onWordSyncToggle = { aiViewModel.toggleKaraokeWordSyncEnabled() },
                onDismiss = { showKaraokeSettings = false }
            )
        }

        if (selectedPlaylist != null) {
            PlaylistDetailView(
                playlist = state.playlists.find { it.id == selectedPlaylist?.id } ?: selectedPlaylist!!,
                allTracks = state.tracks,
                onDismiss = { selectedPlaylist = null },
                onPlayPlaylist = { p, shuffle -> viewModel.playPlaylist(p, shuffle) },
                onDeletePlaylist = { viewModel.deletePlaylist(it); selectedPlaylist = null },
                onAddTrack = { viewModel.addTrackToPlaylist(selectedPlaylist!!, it) },
                onRemoveTrack = { viewModel.removeTrackFromPlaylist(selectedPlaylist!!, it) },
                onPlayTrack = { track, list -> viewModel.playTrack(track, list) },
                currentTrackUri = state.currentTrack?.uri,
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onDownload = {
                    val trackId = it.sourceUrl?.substringAfter("v=")?.substringBefore("&") ?: it.uri.hashCode().toString()
                    selectedTrackForDownload = CatalogTrack(id = trackId, title = it.title, artist = it.artist ?: "Unknown", sourceUrl = it.sourceUrl ?: "", thumbnailUrl = it.thumbnailUri ?: "", duration = it.duration)
                }
            )
        }

        if (showFullPlayer && state.currentTrack != null) {
            val visualizerData by viewModel.visualizerData.collectAsStateWithLifecycle()
            val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

            // Getting the permission is only half the fix — without this,
            // a grant that happens while the visualizer is already turned
            // on (e.g. from the settings toggle below) had nothing telling
            // the ViewModel to actually retry attaching, so the ring kept
            // sitting in its idle animation until the next play/pause even
            // though the permission was now granted.
            LaunchedEffect(micPermission.status.isGranted) {
                if (micPermission.status.isGranted && state.showVisualizer) {
                    viewModel.retryVisualizerAfterPermissionGranted()
                }
            }

            FullPlayerView(
                state = state,
                aiState = aiState,
                aiViewModel = aiViewModel,
                playbackPositionFlow = viewModel.playbackPosition,
                duration = duration,
                sliderPos = sliderPos,
                visualizerData = visualizerData,
                onSliderChange = { viewModel.onSliderChange(it) },
                onSliderChangeFinished = { viewModel.onSliderChangeFinished() },
                onDismiss = { showFullPlayer = false },
                onTogglePlay = { viewModel.togglePlayPause() },
                onPlay = { viewModel.play() },
                onPause = { viewModel.pause() },
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
                onQueueIndexSelect = { viewModel.seekToQueueIndex(it) },
                onToggleRotation = { viewModel.toggleRotation() },
                onTogglePip = { viewModel.togglePipEnabled() },
                onOpenAi = { viewModel.vibrationManager.vibrateClick(); showAiSheet = true },
                onToggleMusicSettings = { viewModel.toggleMusicSettings() },
                catalogStreamQuality = state.catalogStreamQuality,
                onSetCatalogStreamQuality = { quality -> viewModel.setCatalogStreamQuality(quality) },
                onSetPlaybackSpeed = { speed: Float -> viewModel.setPlaybackSpeed(speed) },
                onSetEqualizerPreset = { preset: String -> viewModel.setEqualizerPreset(preset) },
                onSetCustomEqualizerGain = { index: Int, gain: Float -> viewModel.setCustomEqualizerGain(index, gain) },
                onSetVisualizerSensitivity = { sensitivity: Float -> viewModel.setVisualizerSensitivity(sensitivity) },
                onSetVisualizerAutoSensitivity = { enabled: Boolean -> viewModel.setVisualizerAutoSensitivity(enabled) },
                onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemoveQueueItem = { index -> viewModel.removeQueueItem(index) },
                onClearQueue = { viewModel.clearQueue() },
                onToggleVisualizer = { viewModel.toggleShowVisualizer() },
                onToggleKaraoke = { viewModel.toggleKaraokeMode() },
                onNextSongConfirmed = { viewModel.skipNext() },
                onIncrementKaraokeSingCount = { viewModel.incrementKaraokeSingCount(it) },
                onSetVolume = { viewModel.setVolume(it) },
                onSetMutedByAi = { viewModel.setMutedByAi(it) },
                equalizerPresets = state.equalizerPresets
            )
        }

        if (showAiSheet) {
            NowPlayingAiBottomSheet(
                viewModel = aiViewModel,
                onDismiss = { showAiSheet = false },
                vibrationManager = viewModel.vibrationManager,
                onSeek = { viewModel.seekTo(it) },
                onToggleKaraoke = { viewModel.toggleKaraokeMode() },
                onSetMutedByAi = { viewModel.setMutedByAi(it) },
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
    currentTabLabel: String?,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onRefresh: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onClearSelection: () -> Unit,
    onMultiAddPlaylist: () -> Unit,
    onResetCatalogOnboarding: () -> Unit = {},
    onGoToTop: () -> Unit = {}
) {
    Column(modifier = Modifier.background(Color.Transparent)) {
    ExpressiveTopAppBar(
        // "STUDIO PLAYER" was decorative product branding that duplicated
        // nothing useful and a vague "Precision playback" tagline filled
        // the subtitle slot when idle. The subtitle slot is more useful
        // showing the one thing that actually changes as you navigate:
        // which tab you're on. Selection mode still overrides both with
        // the live count, since that's the more urgent piece of state.
        title = if (state.isSelectionMode) "${state.selectedTracks.size} Selected" else stringResource(R.string.st_MusicPlayerScreen_mp3),
        subtitle = if (state.isSelectionMode) null else currentTabLabel,
        navigationIcon = {
            ToolzExpressiveIconButton(
                onClick = if (state.isSelectionMode) onClearSelection else onBack,
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(if (state.isSelectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, null)
            }
        },
        actions = {
            if (state.isSelectionMode) {
                ToolzExpressiveIconButton(
                    onClick = onMultiAddPlaylist,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
                }
                } else {
                    if (currentTabLabel == "Catalog") {
                        ToolzExpressiveIconButton(
                            onClick = onGoToTop,
                            modifier = Modifier.padding(end = 4.dp).size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    when (currentTab) {
                            0 -> { // Tracks
                                ToolzExpressiveIconButton(onClick = onRefresh) {
                                    Icon(Icons.Rounded.Refresh, null)
                                }
                                Box {
                                    ToolzExpressiveIconButton(onClick = { onShowSortMenu(true) }) {
                                        Icon(Icons.AutoMirrored.Rounded.Sort, null)
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
                                    SortDropdownItem(stringResource(R.string.st_MusicPlayerScreen_ba7), Icons.Rounded.Person) {
                                        onSort(SortOrder.ARTIST); onShowSortMenu(false)
                                    }
                                    SortDropdownItem(stringResource(R.string.st_MusicPlayerScreen_br8), Icons.Rounded.Schedule) {
                                        onSort(SortOrder.RECENT); onShowSortMenu(false)
                                    }
                                }
                            }
                        }
                        1 -> { // Library
                                // Was "add folder" — folders already have their
                                // own add action inside the Folders section
                                // itself, so the header action is more useful
                                // as the higher-frequency "new playlist" shortcut.
                                ToolzExpressiveIconButton(onClick = onAddPlaylist) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                                }
                            }
                            2 -> { // Catalog
                                ToolzExpressiveIconButton(onClick = onRefresh) {
                                    Icon(Icons.Rounded.Refresh, null)
                                }
                            }
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
            ),
            modifier = Modifier.statusBarsPadding()
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f),
            modifier = Modifier.padding(top = 4.dp)
        )

        // Search bar — using M3 ExpressiveSearchField
        AnimatedVisibility(
            visible = currentTab == 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                // Track focus locally so the wrapper can grow/glow on focus
                // without needing ExpressiveSearchField to expose its own
                // interaction source — it only takes query/placeholder/icons.
                var isSearchFocused by remember { mutableStateOf(false) }
                val fieldScale by animateFloatAsState(
                    targetValue = if (isSearchFocused) 1f else 0.99f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                    label = "searchFieldScale"
                )
                val glowAlpha by animateFloatAsState(
                    targetValue = if (isSearchFocused) 1f else 0f,
                    animationSpec = tween(220),
                    label = "searchFieldGlow"
                )
                val glowColor = MaterialTheme.colorScheme.primary

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .graphicsLayer { scaleX = fieldScale; scaleY = fieldScale }
                        .drawBehind {
                            if (glowAlpha > 0f) {
                                drawRoundRect(
                                    color = glowColor.copy(alpha = 0.35f * glowAlpha),
                                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                            }
                        }
                        .onFocusEvent { isSearchFocused = it.hasFocus || it.isFocused }
                        .focusGroup()
                ) {
                    ExpressiveSearchField(
                        query = searchQuery,
                        onQueryChange = onSearchChange,
                        placeholder = {
                            Text(
                                stringResource(R.string.st_MusicPlayerScreen_st_hint4),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSearchFocused) 1.08f else 1f,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                label = "searchIconScale"
                            )
                            Icon(
                                Icons.Rounded.Search,
                                null,
                                tint = if (isSearchFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            )
                        },
                        trailingIcon = {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
                                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120))
                            ) {
                                Surface(
                                    onClick = { onSearchChange("") },
                                    modifier = Modifier.size(28.dp).bouncyClick { onSearchChange("") },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(6.dp))
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

@AnnotationOptIn(UnstableApi::class)
@Composable
private fun ScreenBottomBar(
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

    Column(modifier = Modifier.navigationBarsPadding()) {
        // MiniPlayer
        AnimatedVisibility(
            visible = state.currentTrack != null || isResolving,
            enter = fadeIn(tween(300)) + slideInVertically(
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                initialOffsetY = { -it }
            ),
            exit = fadeOut(tween(200)) + slideOutVertically(
                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                targetOffsetY = { -it }
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
    tabItems: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
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

// ─────────────────────────────────────────────────────────────────────────────
// Placeholders
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionPlaceholder(onAllow: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .verticalScroll(rememberScrollState())
        ) {
            StaggeredEntrance(index = 0) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = LargeExpressiveShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.LibraryMusic,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            StaggeredEntrance(index = 1) {
                Text(
                    "Storage Access",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            StaggeredEntrance(index = 2) {
                Text(
                    "Toolz needs access to find and play music on your device.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp
                )
            }

            Spacer(Modifier.height(48.dp))

            StaggeredEntrance(index = 3) {
                ToolzExpressiveButton(
                    onClick = onAllow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "GRANT ACCESS",
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ExpressiveLoadingWheel(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
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

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackList(
    tracks: List<MusicTrack>,
    state: MusicUiState,
    viewModel: MusicPlayerViewModel,
    searchQuery: String = "",
    onOpenFullPlayer: () -> Unit,
    onDownload: (MusicTrack) -> Unit
) {
    if (tracks.isEmpty() && !state.isLoading) {
        EmptyMusicPlaceholder(onScan = { viewModel.scanMusic() })
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Partition once per track-list change, not on every recomposition —
    // these lists back the lazy items directly.
    val offlineTracks = remember(tracks) { tracks.filter { it.path != null } }
    val onlineTracks = remember(tracks) { tracks.filter { it.path == null && it.sourceUrl != null } }
    val showOnlineSection = onlineTracks.isNotEmpty() && state.isOnline

    // Index of the online header within the flattened lazy item list, so the
    // tween arrow can jump straight to it — 1 offset for the offline header.
    val onlineHeaderIndex = if (offlineTracks.isNotEmpty()) offlineTracks.size + 1 else 0

    Box(modifier = Modifier.fillMaxSize()) {
        // Slim loading bar at the very top — visible during incremental scan
        // while tracks are already appearing (non-blocking UI)
        if (state.isLoading) {
            ExpressiveLinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().fadingEdges(top = 12.dp, bottom = 20.dp),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                bottom = 130.dp, top = if (state.isLoading) 5.dp else 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (offlineTracks.isNotEmpty()) {
                item(key = "offline_header") {
                    TrackSectionHeader(
                        label = stringResource(R.string.st_MusicPlayerScreen_off10),
                        count = offlineTracks.size,
                        icon = Icons.Rounded.Storage,
                        color = MaterialTheme.colorScheme.primary,
                        showTweenArrow = showOnlineSection,
                        arrowPointsDown = true,
                        onTween = {
                            // Was animateScrollToItem — on a LazyColumn that
                            // animates a smooth scroll THROUGH every
                            // intermediate item between the current
                            // position and the target, so with a large
                            // library (hundreds of offline tracks) jumping
                            // to the online section had to measure/compose
                            // its way through all of them during the
                            // animation, scaling directly with library
                            // size. This is a "jump to section" action, not
                            // an in-view scroll, so an instant jump is both
                            // the right UX call and removes the lag
                            // entirely — scrollToItem is O(1) regardless of
                            // list length.
                            scope.launch { listState.scrollToItem(onlineHeaderIndex) }
                        },
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)
                    )
                }
                // performanceMode and fast-scroll both skip the staggered
                // entrance — it's a per-item animation cost that compounds
                // badly once many rows enter/leave the viewport per frame.
                itemsIndexed(offlineTracks, key = { _, t -> t.uri }) { index, track ->
                    val isSelected = state.selectedTracks.contains(track.uri)
                    TrackListItem(
                        track = track,
                        isSelected = isSelected,
                        state = state,
                        viewModel = viewModel,
                        tracks = tracks,
                        onOpenFullPlayer = onOpenFullPlayer,
                        searchQuery = searchQuery,
                        onDownload = onDownload,
                        modifier = if (state.performanceMode) Modifier else Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = tween(220, easing = FastOutSlowInEasing)
                        )
                    )
                }
            }

            if (showOnlineSection) {
                item(key = "online_header") {
                    TrackSectionHeader(
                        label = stringResource(R.string.st_MusicPlayerScreen_on11),
                        count = onlineTracks.size,
                        icon = Icons.Rounded.Cloud,
                        color = MaterialTheme.colorScheme.secondary,
                        showTweenArrow = offlineTracks.isNotEmpty(),
                        arrowPointsDown = false,
                        onTween = {
                            scope.launch { listState.scrollToItem(0) }
                        },
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(onlineTracks, key = { _, t -> t.uri }) { index, track ->
                    val isSelected = state.selectedTracks.contains(track.uri)
                    TrackListItem(
                        track = track,
                        isSelected = isSelected,
                        state = state,
                        viewModel = viewModel,
                        tracks = tracks,
                        onOpenFullPlayer = onOpenFullPlayer,
                        searchQuery = searchQuery,
                        onDownload = onDownload,
                        modifier = if (state.performanceMode) Modifier else Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = tween(220, easing = FastOutSlowInEasing)
                        )
                    )
                }
            }
        } // end LazyColumn
    } // end Box
}

// Compact M3-expressive pill used for the Offline/Online section headers.
// A tiny circular chevron sits at the trailing edge so the person can jump
// straight to the other section without scrolling past a long list.
@Composable
private fun TrackSectionHeader(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    showTweenArrow: Boolean,
    arrowPointsDown: Boolean,
    onTween: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
                color = color
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "· $count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.65f)
            )
            if (showTweenArrow) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    onClick = onTween,
                    modifier = Modifier.size(28.dp).bouncyClick(onClick = onTween),
                    shape = CircleShape,
                    color = color.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (arrowPointsDown) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                            contentDescription = if (arrowPointsDown) "Jump to online tracks" else "Jump to offline tracks",
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun TrackListItem(
    track: MusicTrack,
    isSelected: Boolean,
    state: MusicUiState,
    viewModel: MusicPlayerViewModel,
    tracks: List<MusicTrack>,
    onOpenFullPlayer: () -> Unit,
    searchQuery: String,
    onDownload: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
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
            onCreatePlaylist = { viewModel.createPlaylist(it) },
            onToggleFavorite = { viewModel.toggleFavorite(track) },
            onDownload = { onDownload(track) },
            playlists = state.playlists,
            searchQuery = searchQuery,
            karaokeEnabled = state.karaokeEnabled,
            onKaraokeClick = {
                if (track.aiLyrics.isNullOrEmpty() && track.sourceUrl == null) {
                    android.widget.Toast.makeText(context, nalwfMsg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.playTrack(track, tracks)
                    viewModel.toggleKaraokeMode()
                    onOpenFullPlayer()
                }
            },
            modifier = modifier
        )
    } else {
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
            onCreatePlaylist = { viewModel.createPlaylist(it) },
            onToggleFavorite = { viewModel.toggleFavorite(track) },
            onDownload = { onDownload(track) },
            playlists = state.playlists,
            searchQuery = searchQuery,
            karaokeEnabled = state.karaokeEnabled,
            onKaraokeClick = {
                if (track.aiLyrics.isNullOrEmpty() && track.sourceUrl == null) {
                    android.widget.Toast.makeText(context, nalwfMsg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.playTrack(track, tracks)
                    viewModel.toggleKaraokeMode()
                    onOpenFullPlayer()
                }
            },
            modifier = modifier
        )
    }
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
            Text(stringResource(R.string.st_MusicPlayerScreen_ntf15), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.st_MusicPlayerScreen_sdtm16), color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            ToolzExpressiveButton(onClick = onScan, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_MusicPlayerScreen_sn17), fontWeight = FontWeight.Black)
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
    onCreatePlaylist: (String) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onDownload: () -> Unit = {},
    playlists: List<Playlist>,
    searchQuery: String = "",
    deleteLabel: String = "Delete",
    karaokeEnabled: Boolean = true,
    onKaraokeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Small, restrained bounce on the favorite toggle — mirrors the full
    // player's treatment but scaled down for a repeating list row. Skips
    // the pop on first composition so it only fires on a real toggle.
    val favScale = remember(track.uri) { Animatable(1f) }
    var isFavInitialized by remember(track.uri) { mutableStateOf(false) }
    LaunchedEffect(track.uri, track.isFavorite) {
        if (!isFavInitialized) {
            isFavInitialized = true
        } else {
            favScale.snapTo(0.75f)
            favScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
    }

    val cardColors = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        isCurrent  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
        else       -> Color.Transparent
    }
    // M3 Expressive favors asymmetric corner treatments over a uniform
    // radius — leading corners stay tight so the row reads as a rail item,
    // trailing corners open up toward the art. One shape value drives the
    // card, the art clip, and every overlay drawn on the art, so they can't
    // drift out of sync the way three separately-declared radii could.
    val artCorner = if (isCurrent) 18.dp else 14.dp
    val artShape = RoundedCornerShape(
        topStart = artCorner * 0.4f, bottomStart = artCorner * 0.4f,
        topEnd = artCorner, bottomEnd = artCorner
    )
    val cardShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp)

    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        containerColor = cardColors,
        elevation = 0.dp,
        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Now-playing accent — a slim primary bar instead of relying on
            // tint alone to signal "this is the track playing right now",
            // so it reads at a glance while scanning a long list.
            val barHeight by animateDpAsState(
                targetValue = if (isCurrent) 36.dp else 0.dp,
                animationSpec = if (LocalPerformanceMode.current) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "nowPlayingBar"
            )
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Thumbnail
            Box(modifier = Modifier.size(52.dp)) {
                AlbumArtImage(
                    url = track.thumbnailUri,
                    seed = track.title,
                    modifier = Modifier.fillMaxSize().clip(artShape),
                    iconSize = 22.dp
                )

                // Cloud badge for online (not-yet-downloaded) tracks
                if (track.path == null && track.sourceUrl != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(17.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Cloud,
                                contentDescription = "Streamed from Catalog, not downloaded",
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                if (isCurrent && !isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(artShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingBarsIndicator()
                    }
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(artShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.st_MusicPlayerScreen_sel18), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                val highlightColor = MaterialTheme.colorScheme.secondary
                Text(
                    text = if (searchQuery.isNotBlank()) highlightSearch(track.title, searchQuery, highlightColor) else AnnotatedString(track.title),
                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
                Text(
                    text = artistText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Streamed-but-undownloaded tracks get a dedicated download
                    // affordance; everything else is one tap into the overflow,
                    // instead of stacking a third permanent circular button.
                    if (track.path == null && track.sourceUrl != null) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.st_MusicPlayerScreen_d19),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (track.isFavorite) stringResource(R.string.st_MusicPlayerScreen_rff20) else stringResource(R.string.st_MusicPlayerScreen_atf21),
                            tint = if (track.isFavorite) Color(0xFFE0555C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { scaleX = favScale.value; scaleY = favScale.value }
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.st_MusicPlayerScreen_mo22),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.st_MusicPlayerScreen_atp23), fontWeight = FontWeight.Medium) },
                                onClick = { showPlaylistPicker = true; showMenu = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (karaokeEnabled && (!track.aiLyrics.isNullOrEmpty() || track.sourceUrl != null)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.st_MusicPlayerScreen_oik24), fontWeight = FontWeight.Medium) },
                                    onClick = { onKaraokeClick(); showMenu = false },
                                    leadingIcon = { Icon(Icons.Rounded.MicExternalOn, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                onClick = { onDelete(); showMenu = false },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists,
            onDismiss = { showPlaylistPicker = false },
            onSelect = onAddToPlaylist,
            onCreatePlaylist = onCreatePlaylist
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
fun TrackCard(track: MusicTrack, onClick: () -> Unit, cardWidth: Dp = 152.dp) {
    val performanceMode = LocalPerformanceMode.current
    Column(
        modifier = Modifier
            .width(cardWidth)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.Start
    ) {
        val artSize = cardWidth - 6.dp
        // Corner radius capped at 6dp (was up to 12dp on two corners). At
        // this card width a 12dp radius eats visibly into the square art.
        AlbumArtImage(
            url = track.thumbnailUri,
            seed = track.title,
            modifier = Modifier
                .size(artSize)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 6.dp)),
            iconSize = 32.dp
        )
        Spacer(Modifier.height(12.dp))
        // Text block width matches the art. `softWrap = false` was removed
        // from both Texts below — with `maxLines = 1` it's redundant for
        // preventing wrapping, but inside a horizontally-scrolling carousel
        // it was letting long titles get measured at their natural
        // (unconstrained) width before the ellipsis pass ran, so text
        // could render starting to the left of this Column's own left
        // edge instead of being clipped/ellipsized inside it — visible as
        // the first letter or two of the title/artist getting cut off on
        // the left rather than the end. `.fillMaxWidth()` makes sure each
        // Text is actually laid out to this exact box width so the
        // ellipsis has a real boundary to clip against.
        Column(
            modifier = Modifier
                .width(artSize)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: stringResource(R.string.st_MusicPlayerScreen_ua25)
            Text(
                text = artistText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.3.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Carousel Row (keeps the M3 multi-browse scroll/mask feel, but only
// masks the art — title/artist render outside the mask so they can't be
// clipped by it)
// ─────────────────────────────────────────────────────────────────────────────

// Why this exists: the Library tab's Recently Played / Most Played rows used
// to go through ExpressiveCarousel (HorizontalMultiBrowseCarousel), which
// wraps its ENTIRE per-item slot — art and anything else placed inside —
// in `.maskClip(shapes.large)`. That mask is scroll-position-driven, which
// is what gave the nice "items subtly resize/mask as they scroll past
// center" feel — but it also means anything inside that slot, including
// text below the art, gets clipped to the same shape. That mask, not
// TrackCard's own corner radius, was the actual source of the earlier
// text-clipping bug (title/artist losing their leading characters).
//
// This keeps the good scroll feel by letting the carousel mask/animate
// ONLY the art thumbnail — the part that's supposed to look carousel-y —
// and renders title/artist as ordinary text underneath, entirely outside
// the carousel's per-item masked box. The per-item slot width itself is
// animated as it scrolls past center; the text below is rendered at a
// fixed width (preferredItemWidth) rather than tracking that animation,
// which for a two-line caption under a resizing image is visually
// seamless, and guarantees the text can never be clipped by the mask
// regardless of where the item currently sits in the strip.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCarouselRow(
    tracks: List<MusicTrack>,
    onTrackClick: (MusicTrack) -> Unit,
    preferredItemWidth: Dp = 152.dp,
    itemSpacing: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 2.dp)
) {
    val carouselState = rememberCarouselState { tracks.size }
    val artShape = RoundedCornerShape(topStart = 6.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 6.dp)

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = preferredItemWidth,
        itemSpacing = itemSpacing,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxWidth().height(preferredItemWidth + 62.dp)
    ) { index ->
        val track = tracks[index]
        Column(
            modifier = Modifier.fillMaxHeight().bouncyClick(onClick = { onTrackClick(track) }),
            horizontalAlignment = Alignment.Start
        ) {
            AlbumArtImage(
                url = track.thumbnailUri,
                seed = track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .maskClip(artShape),
                iconSize = 32.dp
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .width(preferredItemWidth - 6.dp)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
                Text(
                    text = artistText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
                Text(stringResource(R.string.st_MusicPlayerScreen_nff28), color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.st_MusicPlayerScreen_acfutba29), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
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
        targetValue = if (isCurrentFolder) 0.7f else 0.14f,
        animationSpec = if (performanceMode) snap() else tween(300),
        label = "folderBorder"
    )
    // Special-cased folders (like the app's own download bucket) get a
    // distinct glyph so they read instantly in a scanning grid.
    val isDownloadsFolder = folderName == "Toolz Downloads"
    val folderIcon = when {
        isDownloadsFolder -> Icons.Rounded.DownloadDone
        isCurrentFolder -> Icons.Rounded.FolderOpen
        else -> Icons.Rounded.Folder
    }
    val accentColor = if (isCurrentFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    // Corner radius trimmed down further (16dp max) so the curve never
    // reaches the folder name's baseline, and the icon chip now sits on
    // its own tonal plate rather than floating directly on the card
    // background — gives the tile a clearer two-layer, "chip + content"
    // structure instead of one flat block of color.
    //
    // The container itself stays a neutral surface tint regardless of
    // playing state — previously an active folder swapped to a heavy
    // primaryContainer background AND primary-colored title text AND two
    // separate primary-tinted circles (icon chip + playing-bars badge), so
    // everything on the card read as the same hue with no anchor: the
    // folder name could wash out against its own background, leaving only
    // the icon chip's circle visible as "a blue dot" with the label
    // effectively invisible next to it. The accent color is now reserved
    // for the icon chip and a small trailing indicator only.
    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(148.dp).bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 18.dp),
        containerColor = if (isCurrentFolder)
            MaterialTheme.colorScheme.surfaceContainerHigh
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                    color = accentColor.copy(alpha = if (isCurrentFolder) 0.18f else 0.1f)
                ) {
                    Box(modifier = Modifier.padding(9.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            folderIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                if (isCurrentFolder) {
                    // Compact playing-bars badge — tonal chip is neutral so
                    // it doesn't compete with the icon chip above it for
                    // "which circle is the accent color" attention.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
                            PlayingBarsIndicator()
                        }
                    }
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                        modifier = Modifier.size(12.dp).padding(top = 4.dp, end = 2.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = folderName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    // Always the plain onSurface color — the folder name is
                    // the one thing on this card that must never compete
                    // with an accent-tinted background for contrast. Which
                    // folder is playing is already communicated by the
                    // playing-bars badge and the icon color above.
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (trackCount == 1) "1 TRACK" else "$trackCount TRACKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentFolder)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@AnnotationOptIn(UnstableApi::class)
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
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (currentlyPlayingInFolder) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
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
                            if (tracks.size == 1) "1 TRACK" else "${tracks.size} TRACKS",
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
                                    stringResource(R.string.st_MusicPlayerScreen_p30),
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
                // Play-all button — hidden rather than crashing when a
                // (freshly emptied) folder has nothing left to play.
                if (tracks.isNotEmpty()) {
                    ToolzExpressiveIconButton(onClick = { onPlayTrack(tracks.first()) }, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(26.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Search
            ExpressiveSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.st_MusicPlayerScreen_st_hint31), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
                        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120))
                    ) {
                        Surface(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp).bouncyClick { searchQuery = "" },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.st_MusicPlayerScreen_cs5),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.07f))
            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(48.dp).alpha(0.12f), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.st_MusicPlayerScreen_nm32), color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
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
                            onCreatePlaylist = { viewModel.createPlaylist(it) },
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

@AnnotationOptIn(UnstableApi::class)
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
    onAddFolder: () -> Unit = {},
    onDownload: (MusicTrack) -> Unit
) {
    var showFavoritesDetail by remember { mutableStateOf(false) }
    var showRecentDetail by remember { mutableStateOf(false) }
    var showMostPlayedDetail by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).fadingEdges(top = 16.dp, bottom = 24.dp),
        contentPadding = PaddingValues(bottom = 130.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        // The top app bar already reads "STUDIO PLAYER / Library", so this
        // doesn't repeat that title in a decorative gradient card — it just
        // states the one fact that matters (the count) plainly.
        item {
            Text(
                text = "${state.tracks.size} tracks · ${state.playlists.size} playlists",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
            )
        }

        // ── Stats header row ──────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorites
                QuickAccessCard(
                    modifier = Modifier.weight(1f).height(138.dp),
                    icon = Icons.Rounded.Favorite,
                    label = stringResource(R.string.st_MusicPlayerScreen_f33),
                    count = state.favoriteTracks.size,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showFavoritesDetail = true }
                )
                // Recently Played
                QuickAccessCard(
                    modifier = Modifier.weight(1f).height(138.dp),
                    icon = Icons.Rounded.History,
                    label = stringResource(R.string.st_MusicPlayerScreen_r34),
                    count = state.recentlyPlayed.size,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showRecentDetail = true }
                )
                // Most Played
                QuickAccessCard(
                    modifier = Modifier.weight(1f).height(138.dp),
                    icon = Icons.Rounded.TrendingUp,
                    label = stringResource(R.string.st_MusicPlayerScreen_tp35),
                    count = state.mostPlayed.size,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { showMostPlayedDetail = true }
                )
            }
        }

        // ── Recently Played carousel ──────────────────────────────────────────
        if (state.recentlyPlayed.isNotEmpty()) {
            item {
                RowSectionHeader(stringResource(R.string.st_MusicPlayerScreen_rp36)) { showRecentDetail = true }
                Spacer(Modifier.height(12.dp))
                TrackCarouselRow(
                    tracks = state.recentlyPlayed.take(12),
                    onTrackClick = { viewModel.playTrack(it, state.recentlyPlayed) }
                )
            }
        }

        // ── Most Played carousel ──────────────────────────────────────────────
        if (state.mostPlayed.isNotEmpty()) {
            item {
                RowSectionHeader(stringResource(R.string.st_MusicPlayerScreen_mop37)) { showMostPlayedDetail = true }
                Spacer(Modifier.height(12.dp))
                TrackCarouselRow(
                    tracks = state.mostPlayed.take(12),
                    onTrackClick = { viewModel.playTrack(it, state.mostPlayed) }
                )
            }
        }

        // ── Playlists ─────────────────────────────────────────────────────────
        // Header is always shown now — previously the whole section
        // (header, create button, everything) vanished when there were no
        // playlists yet, which meant a first-time user had no way to find
        // the create action inside the Library tab itself. The count in
        // the label doubles as a quiet confirmation that "0" really is
        // the current state, not a loading gap.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(if (state.playlists.isEmpty()) stringResource(R.string.st_MusicPlayerScreen_pl39) else stringResource(R.string.st_MusicPlayerScreen_pl39) + " · ${state.playlists.size}")
                Surface(
                    onClick = onCreatePlaylist,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.bouncyClick(onClick = onCreatePlaylist)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.st_MusicPlayerScreen_n9),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.playlists.isEmpty()) {
            item { PlaylistEmptyCard(onCreatePlaylist = onCreatePlaylist) }
        } else {
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

        // ── Folders ───────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(if (state.folders.isEmpty()) stringResource(R.string.st_MusicPlayerScreen_fl42) else stringResource(R.string.st_MusicPlayerScreen_fl42) + " · ${state.folders.size}")
                Surface(
                    onClick = onAddFolder,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.bouncyClick(onClick = onAddFolder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.st_MusicPlayerScreen_a43),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.folders.isEmpty()) {
            item {
                FolderEmptyCard(onAddFolder = onAddFolder)
            }
        } else {
            // All folder rows now live inside ONE LazyColumn item, wrapped
            // in their own Column with a 12dp spacedBy. Previously each row
            // was a separate item, so the LazyColumn's own top-level
            // spacedBy(20dp) ran *between* every row in addition to the
            // 12dp spacer this composable added itself — rows ended up
            // ~32dp apart instead of the intended tight 12dp rhythm, which
            // read as a much bigger gap than every other section.
            item {
                val folderRows = state.folders.keys.toList().chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    folderRows.forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
        }
    }

    // Detail overlays
    if (showFavoritesDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = stringResource(R.string.st_MusicPlayerScreen_f33), trackUris = state.favoriteTracks.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showFavoritesDetail = false },
            onPlayPlaylist = { _, shuffle -> if (state.favoriteTracks.isNotEmpty()) {
                val list = if (shuffle) state.favoriteTracks.shuffled() else state.favoriteTracks
                viewModel.playTrack(list.first(), list)
            } },
            onDeletePlaylist = {},
            onAddTrack = { viewModel.toggleFavorite(it) },
            onRemoveTrack = { uri -> state.favoriteTracks.find { it.uri == uri }?.let { viewModel.toggleFavorite(it) } },
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDownload = onDownload
        )
    }
    if (showRecentDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = stringResource(R.string.st_MusicPlayerScreen_r34), trackUris = state.recentlyPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showRecentDetail = false },
            onPlayPlaylist = { _, shuffle -> if (state.recentlyPlayed.isNotEmpty()) {
                val list = if (shuffle) state.recentlyPlayed.shuffled() else state.recentlyPlayed
                viewModel.playTrack(list.first(), list)
            } },
            onDeletePlaylist = {}, onAddTrack = {}, onRemoveTrack = {},
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDownload = onDownload
        )
    }
    if (showMostPlayedDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = stringResource(R.string.st_MusicPlayerScreen_tp35), trackUris = state.mostPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showMostPlayedDetail = false },
            onPlayPlaylist = { _, shuffle -> if (state.mostPlayed.isNotEmpty()) {
                val list = if (shuffle) state.mostPlayed.shuffled() else state.mostPlayed
                viewModel.playTrack(list.first(), list)
            } },
            onDeletePlaylist = {}, onAddTrack = {}, onRemoveTrack = {},
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDownload = onDownload
        )
    }

    // Rename dialog
    playlistToRename?.let { playlist ->
        var newName by remember(playlist.id) { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text(stringResource(R.string.st_MusicPlayerScreen_rp46), fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.st_MusicPlayerScreen_n47)) },
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
                ToolzExpressiveButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenamePlaylist(playlist, newName.trim())
                            playlistToRename = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = newName.isNotBlank()
                ) { Text(stringResource(R.string.st_MusicPlayerScreen_rn48), fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text(stringResource(R.string.st_MusicPlayerScreen_c49)) }
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
    // Asymmetric M3 Expressive corner, trimmed down from the previous
    // 24dp max — at this tile width a corner that large curved directly
    // under the label baseline and clipped the descenders of "Recent" /
    // "Top Played". 16dp still reads as a distinct expressive shape while
    // leaving the label column flat, unrounded ground to sit on. The
    // bottom padding is also given its own (larger) inset so the text
    // block clears the curve with margin instead of hugging it.
    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier.bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 12.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor
                )
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor.copy(alpha = 0.75f),
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playlist Card
// ─────────────────────────────────────────────────────────────────────────────

@Suppress("EXPERIMENTAL_API_USAGE")
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

    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        onLongClick = { showContextMenu = true },
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = 4.dp
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
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                ),
                                startY = 0f
                            )
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

            // Info and Play button
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (playlist.trackUris.size == 1) "1 track" else "${playlist.trackUris.size} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                
                Surface(
                    onClick = onPlay,
                    modifier = Modifier.size(46.dp).bouncyClick {},
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
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
                    leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
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
    onPlayPlaylist: (Playlist, Boolean) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onAddTrack: (MusicTrack) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    isEditable: Boolean = true,
    currentTrackUri: String? = null,
    onToggleFavorite: (MusicTrack) -> Unit = {},
    onDownload: (MusicTrack) -> Unit
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
        modifier = Modifier.fillMaxHeight(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Enhanced Dynamic Header ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                // Background blurred artwork
                if (!performanceMode && playlistTracks.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.thumbnailUri ?: playlistTracks.firstOrNull()?.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.2f)
                            .blur(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Artwork with elevation and better shape
                    Surface(
                        modifier = Modifier.size(160.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 16.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        if (playlist.thumbnailUri != null) {
                            AsyncImage(
                                model = playlist.thumbnailUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (playlistTracks.isNotEmpty()) {
                            val thumbs = (playlistTracks.map { it.thumbnailUri } + List(4) { null }).take(4)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = false
                            ) {
                                items(thumbs) { thumb ->
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.aspectRatio(1f).fillMaxSize(),
                                        error = rememberVectorPainter(Icons.Rounded.MusicNote)
                                    )
                                }
                            }
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (playingInThisPlaylist) {
                        Spacer(Modifier.height(8.dp))
                        PlayingBarsIndicator()
                    }
                }

                // Top buttons
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolzExpressiveIconButton(onClick = onDismiss, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), shape = CircleShape) {
                        Icon(Icons.Rounded.Close, null)
                    }

                    if (isEditable) {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            ToolzExpressiveIconButton(onClick = { showMoreMenu = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), shape = CircleShape) {
                                Icon(Icons.Rounded.MoreVert, null)
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        onDeletePlaylist(playlist)
                                        showMoreMenu = false
                                        onDismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Action Buttons ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolzExpressiveButton(
                    onClick = { onPlayPlaylist(playlist, false) },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("PLAY ALL", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                ToolzExpressiveButton(
                    onClick = { onPlayPlaylist(playlist, true) },
                    modifier = Modifier.weight(0.7f).height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("MIX", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Search Bar & Track Count ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search tracks…") }
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "${playlistTracks.size}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Track List ──────────────────────────────────────────────────
            if (filteredPlaylistTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.LibraryMusic,
                            null,
                            modifier = Modifier.size(64.dp).alpha(0.1f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Empty Playlist" else "No matches found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        if (isEditable && searchQuery.isEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            ToolzExpressiveButton(onClick = { showAddTrack = true }, shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Tracks")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(top = 16.dp, bottom = 32.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredPlaylistTracks, key = { _, t -> t.uri }) { index, track ->
                        val isCurrent = track.uri == currentTrackUri

                        ListItem(
                            headlineContent = {
                                Text(
                                    track.title,
                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    track.artist?.uppercase() ?: "UNKNOWN ARTIST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            },
                            leadingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        modifier = Modifier.width(28.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        if (isCurrent) {
                                            PlayingBarsIndicator()
                                        } else {
                                            AlbumArtImage(
                                                url = track.thumbnailUri,
                                                seed = track.uri,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                            )
                                        }

                                        // Cloud icon for online tracks
                                        if (track.path == null && track.sourceUrl != null) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-4).dp)
                                                    .size(16.dp),
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                                shape = CircleShape
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Cloud,
                                                        contentDescription = "Online",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Filled tonal container instead of a bare glyph — matches the
                                    // favorite toggle used in the full player, so the "loved" state
                                    // is legible against any card background, not just a red tint
                                    // floating on transparency.
                                    val haptic = LocalHapticFeedback.current
                                    ToolzExpressiveIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite(track)
                },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (track.isFavorite) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (track.isFavorite) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (track.isFavorite) stringResource(R.string.st_MusicPlayerScreen_rff20) else stringResource(R.string.st_MusicPlayerScreen_atf21),
                    modifier = Modifier.size(18.dp)
                )
            }

                                    if (track.path == null && track.sourceUrl != null) {
                                        ToolzExpressiveIconButton(
                                            onClick = { onDownload(track) },
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                            )
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.st_MusicPlayerScreen_d19), modifier = Modifier.size(19.dp))
                                        }
                                    }

                                    if (isEditable) {
                                        ToolzExpressiveIconButton(
                                            onClick = { onRemoveTrack(track.uri) },
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                                            )
                                        ) {
                                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove from playlist", modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlayTrack(track, filteredPlaylistTracks) }
                        )
                    }
                }
            }

            // Floating Add Button for editable playlists
            if (isEditable && filteredPlaylistTracks.isNotEmpty()) {
                ToolzExpressiveButton(
                    onClick = { showAddTrack = true },
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.End),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ADD SONGS", fontWeight = FontWeight.Black)
                }
            }
        }
    }

    if (showAddTrack) {
        val available = allTracks.filter { it.uri !in playlist.trackUris }
        SongPickerDialog(
            allTracks = available,
            onTrackSelected = { onAddTrack(it); showAddTrack = false },
            onDismiss = { showAddTrack = false }
        )
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Full Player View
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun FullPlayerView(
    state: MusicUiState,
    aiState: NowPlayingAiUiState,
    aiViewModel: NowPlayingAiViewModel = hiltViewModel(),
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
    val coroutineScope = rememberCoroutineScope()

    // ── Open/close choreography ──
    // Entrance: a gentle pop-in (slight overshoot via spring) instead of
    // relying only on the sheet's own default slide-up, so opening the full
    // player feels like one deliberate motion rather than two stacked ones.
    // Exit: a shrink/fade/settle-down of the whole sheet before the callbacks
    // actually fire. This now covers BOTH a normal dismiss (back press, scrim
    // tap, swipe-down) and "Stop & exit" — the same choreography either way,
    // the only difference is whether onStop() runs first.
    val entranceProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceProgress.animateTo(1f, animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
    }

    var isExiting by remember { mutableStateOf(false) }
    var exitStopsPlayback by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    LaunchedEffect(isExiting) {
        if (isExiting) {
            exitProgress.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
            if (exitStopsPlayback) onStop()
            onDismiss()
        }
    }
    // Normal close (back/scrim/swipe): plays the shrink-out, but doesn't stop playback.
    val requestClose: () -> Unit = { if (!isExiting) { exitStopsPlayback = false; isExiting = true } }
    // "Stop & exit" menu action: same shrink-out, but stops playback first.
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
        onDismissRequest = onDismiss,
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
                    scaleX = 1f - p * 0.16f
                    scaleY = 1f - p * 0.16f
                    alpha = 1f - p
                    translationY = p * 48.dp.toPx()
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
                                            DropdownMenuItem(text = { Text(stringResource(R.string.st_MusicPlayerScreen_ca60)) }, onClick = { onSetArtShape("CIRCLE"); showOverflowMenu = false }, leadingIcon = { RadioButton(selected = state.artShape == "CIRCLE", onClick = null) })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.st_MusicPlayerScreen_sa61)) }, onClick = { onSetArtShape("SQUARE"); showOverflowMenu = false }, leadingIcon = { RadioButton(selected = state.artShape == "SQUARE", onClick = null) })
                                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                                            DropdownMenuItem(text = { Text(stringResource(R.string.st_MusicPlayerScreen_ra62)) }, onClick = { onToggleRotation() }, leadingIcon = { Switch(checked = state.rotationEnabled, onCheckedChange = null, modifier = Modifier.scale(0.75f)) })
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

                            // ── 2. Album art ──
                            val squareCorner by animateDpAsState(
                                targetValue = if (state.isPlaying) 28.dp else 52.dp,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                                label = "artCornerMorph"
                            )
                            val artMaxSize = (configuration.screenWidthDp * 0.80f).dp.coerceAtMost(310.dp)
                            val artShape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(squareCorner)

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

                                        if (state.visualizerNeedsPermission) {
                                            val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
                                            Surface(
                                                onClick = { micPermission.launchPermissionRequest() },
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.padding(horizontal = 32.dp).zIndex(10f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Rounded.MicOff, null, modifier = Modifier.size(18.dp))
                                                    Text(
                                                        stringResource(R.string.st_MusicPlayerScreen_ttgmafv78),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
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
                                        AsyncImage(
                                            model = nextArt.thumbnailUri,
                                            contentDescription = "Album art for ${nextArt.title}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            error = rememberVectorPainter(Icons.Rounded.MusicNote)
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
                                        AsyncImage(
                                            model = prevArt.thumbnailUri,
                                            contentDescription = "Album art for ${prevArt.title}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            error = rememberVectorPainter(Icons.Rounded.MusicNote)
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
                                    AsyncImage(
                                        model = track.thumbnailUri,
                                        contentDescription = "Album art for ${track.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        error = rememberVectorPainter(Icons.Rounded.MusicNote)
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
                                                slideInVertically(
                                                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                                                ) { h -> h / 3 } +
                                                scaleIn(
                                                    initialScale = 0.92f,
                                                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                                )
                                            val exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
                                                slideOutVertically(tween(180)) { h -> -h / 4 }
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
                                            modifier = Modifier.basicMarquee()
                                        )
                                    }
                                    AnimatedContent(
                                        targetState = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
                                            ?: "Unknown artist",
                                        transitionSpec = {
                                            val enter = fadeIn(tween(360, delayMillis = 40, easing = FastOutSlowInEasing)) +
                                                slideInVertically(
                                                    animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
                                                ) { h -> h / 3 }
                                            val exit = fadeOut(tween(150, easing = FastOutLinearInEasing)) +
                                                slideOutVertically(tween(150)) { h -> -h / 4 }
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
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SquigglySlider(
                                    value = currentPos.toFloat(),
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
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
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
                            if (!state.showVisualizer && !micPermission.status.isGranted) {
                                micPermission.launchPermissionRequest()
                            }
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
                        if (state.visualizerNeedsPermission) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 32.dp).zIndex(10f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(R.string.st_MusicPlayerScreen_nmatrta79),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { micPermission.launchPermissionRequest() }) {
                                        Text(stringResource(R.string.st_MusicPlayerScreen_g80))
                                    }
                                }
                            }
                        }
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
        val maxBarLen = 26.dp.toPx()
        val minBarWidth = 2.5.dp.toPx()
        val maxBarWidth = 5.5.dp.toPx()
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

// ─────────────────────────────────────────────────────────────────────────────
// Queue Sheet — Material 3 Expressive
//
// Design language: one unmistakable hero, a calm dense list beneath it, and
// shape/motion doing the storytelling instead of decoration bolted on top.
//
//  • The now-playing card is a real "living" surface: a heavily blurred wash
//    of the actual album art sits behind it (not just a flat color gradient).
//    The decorative wavy waveform line that used to run here permanently was
//    removed — it was pure animation cost for a card that isn't even
//    clickable, and it's part of what made the sheet feel laggy to open.
//  • Every queue row's shape/elevation/scale is driven directly off
//    DragDropState (isDragging / draggingItemIndex) rather than a parallel
//    Surface-interaction proxy — the long-press-drag gesture consumes the
//    pointer before a click-interaction source would ever see it, so tying
//    the "picked up" look to the actual drag state is what makes the morph
//    reliably show up the moment a row is lifted, not just on tap-press.
//    Rows no longer also carry a swipe-to-dismiss gesture on the same
//    pointer input — that was a second detector contending with the drag
//    gesture for the same touch stream, which was part of what made
//    reordering feel unstable. Delete is a plain button now.
//  • The currently-playing track is filtered out of the reorderable list up
//    front (see `upcoming` below), not skipped mid-loop, and every row keeps
//    its real index into the full queue alongside its on-screen position —
//    this is what actually makes drag-to-reorder move the right tracks.
//  • The section header carries a live animated pill for the track count and
//    clearer, more confident actions (Play next batch / Clear) instead of a
//    single small icon button competing for attention with the title.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueSheet(
    queue: List<QueueEntry>,
    currentTrack: MusicTrack,
    currentQueueIndex: Int = 0,
    onTrackSelect: (MusicTrack) -> Unit,
    onQueueIndexSelect: (Int) -> Unit = {},
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val haptic = LocalHapticFeedback.current
    val dynamicColors = rememberDynamicColors(currentTrack.thumbnailUri)
    val reducedEffects = LocalPerformanceMode.current

    // Upcoming queue elements strictly start from index currentQueueIndex + 1.
    // The very first item shown in "Up Next" is currentQueueIndex + 1 (the next song to play).
    val upcoming = remember(queue, currentQueueIndex) {
        if (queue.isEmpty()) emptyList()
        else {
            val currIdx = currentQueueIndex.coerceIn(0, queue.size - 1)
            if (currIdx + 1 < queue.size) {
                queue.withIndex().drop(currIdx + 1).toList()
            } else emptyList()
        }
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(lazyListState) { fromSlot, toSlot ->
        val fromRow = fromSlot - 3
        val toRow = toSlot - 3
        if (fromRow in upcoming.indices && toRow in upcoming.indices) {
            onMove(upcoming[fromRow].index, upcoming[toRow].index)
        }
    }

    val isScrolled by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 24 }
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "queueScrimAlpha"
    )

    // Subtle "something is actively being reordered" backdrop tint — a
    // whole-sheet cue that a drag is in flight, on top of the per-row morph.
    val dragTintAlpha by animateFloatAsState(
        targetValue = if (dragDropState.isDragging) 0.05f else 0f,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
        label = "queueDragTint"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 4.dp)
                    .size(if (dragDropState.isDragging) 44.dp else 32.dp, 4.dp)
                    .clip(CircleShape)
                    .background(
                        if (dragDropState.isDragging) dynamicColors.primary.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    .animateContentSize(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            )
        },
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        if (dragTintAlpha > 0f) {
                            drawRect(dynamicColors.primary.copy(alpha = dragTintAlpha))
                        }
                    }
                }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .dragDropColumn(dragDropState, haptic),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Section title + live count pill + actions ──
                item(key = "queue_title_row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Up next",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.4).sp
                            )
                            AnimatedContent(
                                targetState = queue.size,
                                transitionSpec = {
                                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.6f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)))
                                        .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.6f))
                                },
                                label = "queueCountPill"
                            ) { count ->
                                if (count > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 12.dp),
                                        color = dynamicColors.primary.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            "$count",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Black,
                                            color = dynamicColors.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = queue.isNotEmpty(),
                            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.8f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
                            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)
                        ) {
                            FilledTonalIconButton(
                                onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClear(); onDismiss() },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear queue", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // ── Hero now-playing card — the sheet's one indulgent element ──
                item(key = "now_playing_hero") {
                    NowPlayingHeroCard(
                        track = currentTrack,
                        dynamicColors = dynamicColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 22.dp)
                    )
                }

                item(key = "up_next_label") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                        Text(
                            "COMING UP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            letterSpacing = 1.2.sp
                        )
                        AnimatedVisibility(visible = dragDropState.isDragging) {
                            Text(
                                "· drag to reorder",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = dynamicColors.primary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (upcoming.isEmpty()) {
                    item(key = "empty_state") {
                        QueueEmptyState(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                } else {
                    itemsIndexed(upcoming, key = { _, indexed -> indexed.value.id }) { rowIndex, indexed ->
                        val qTrack = indexed.value.track
                        val realQueueIndex = indexed.index
                        val absoluteIndex = rowIndex + 3 // offset for the 3 header rows above
                        QueueItem(
                            index = rowIndex,
                            absoluteIndex = absoluteIndex,
                            qTrack = qTrack,
                            onTrackSelect = { onQueueIndexSelect(realQueueIndex) },
                            onRemove = { onRemove(realQueueIndex) },
                            dragDropState = dragDropState,
                            dynamicColors = dynamicColors,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .animateItem(placementSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                        )
                    }
                }
            }

            // ── Scroll scrim ──
            // A thin gradient + hairline that fades in only once content has
            // actually scrolled beneath the drag handle — signals "there's
            // more above" without a permanent fixed header competing with
            // the hero card for attention.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(20.dp)
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            )
                        )
                    )
            )
        }
    }
}

// ── Now-playing hero card ──
// The sheet's signature element. A blurred wash of the actual album art
// (not a flat gradient) sits behind the content for real "this track" color
// presence. No decorative wavy/squiggly waveform line here anymore — it was
// a permanent infinite animation running the entire time the sheet was
// open, for a card that isn't even clickable, and it was one of the
// contributors to the sheet feeling laggy. The small "NOW PLAYING" pill
// with its playing-bars indicator already carries the "this is live" cue,
// so the card stays calm, cheap to keep on screen, and still reads as the
// one deliberately asymmetric, premium element here.
@Composable
private fun NowPlayingHeroCard(
    track: MusicTrack,
    dynamicColors: DynamicColors,
    modifier: Modifier = Modifier
) {
    val reducedEffects = LocalPerformanceMode.current
    val heroShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 40.dp)

    Surface(
        modifier = modifier,
        shape = heroShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .clip(heroShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // Blurred album-art backdrop — real color from the actual track,
            // not a synthetic gradient guess. Radius dropped from 28.dp to
            // 18.dp: past a certain point a bigger blur radius costs real GPU
            // time (larger sample kernel, larger required layer bounds) for a
            // visual difference nobody can actually see once it's sitting
            // behind text and a gradient wash anyway — 18.dp is
            // indistinguishable here at a third of the sample cost.
            if (!reducedEffects) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(18.dp)
                        .alpha(0.55f),
                    error = rememberVectorPainter(Icons.Rounded.MusicNote)
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                dynamicColors.primary.copy(alpha = if (reducedEffects) 0.22f else 0.30f),
                                dynamicColors.secondary.copy(alpha = if (reducedEffects) 0.14f else 0.20f)
                            )
                        )
                    )
                    .border(1.dp, dynamicColors.primary.copy(alpha = 0.18f), heroShape)
            )

            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 26.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp
                    ) {
                        AsyncImage(
                            model = track.thumbnailUri,
                            contentDescription = "Album art for ${track.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = rememberVectorPainter(Icons.Rounded.MusicNote)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
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
private fun QueueEmptyState(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "emptyBob")
    val reducedEffects = LocalPerformanceMode.current
    val bob by if (reducedEffects) remember { mutableFloatStateOf(0f) } else infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyBobValue"
    )

    Column(
        modifier = modifier.padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer { translationY = bob * 3f },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.QueuePlayNext,
                    null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Nothing queued yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tracks you add up next will show here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Queue row ──
// Shape communicates interaction directly: at rest, rows are a plain
// rounded rectangle; the moment a row is the one being dragged (driven off
// DragDropState itself, not a proxy interaction source — long-press-drag
// consumes the pointer before Surface's own click-interaction would ever
// see it) its trailing corner rounds out further toward the hero card's
// asymmetric language and it lifts with real shadow, so the active item is
// unmistakable without relying on scale/opacity tricks alone.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueItem(
    // Row position in the on-screen (current-track-filtered) list. Only
    // used here to trigger `onRemove` after the undo window and as the
    // default for `absoluteIndex` below — the caller's `onRemove` lambda
    // already has the real queue index baked in via closure, so whatever
    // this function passes to it is ignored. It is NOT an index into the
    // full queue; don't reuse it as one.
    index: Int,
    qTrack: MusicTrack,
    onTrackSelect: (MusicTrack) -> Unit,
    onRemove: (Int) -> Unit,
    dragDropState: DragDropState,
    modifier: Modifier = Modifier,
    absoluteIndex: Int = index,
    dynamicColors: DynamicColors? = null
) {
    // Swipe-to-dismiss used to sit on the exact same row as the long-press
    // drag gesture — two pointer-input detectors racing over the same touch
    // stream. That contention was a second, independent source of the drag
    // feeling unstable, on top of the animation-cost issues below. A plain
    // delete button removes the conflict entirely: only one gesture
    // recognizer (the long-press drag) ever owns the row's pointer input.
    // Undo is now a simple latch instead of a real timer/coroutine per row.
    var isPendingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000)
            if (isPendingDelete) onRemove(index)
        }
    }

    if (isPendingDelete) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(68.dp)
                .clickable { isPendingDelete = false },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(
                        "Removed \"${qTrack.title}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "UNDO",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        return
    }

    // Rows no longer extract their own per-track palette. A full-fidelity
    // Palette/bitmap-sample pass per row (multiplied by every row simultaneously
    // on sheet open, and again every time LazyColumn recycles an item into view
    // while scrolling) was the actual cause of the Up Next lag — it's real
    // decode+sample work competing with the drag/scroll thread, not just a
    // cheap color lookup. Rows now inherit the now-playing track's already-
    // computed accent instead; it reads as one cohesive sheet accent rather
    // than 20 competing per-row hues anyway, which is the better look here.
    val resolvedDynamicColors = dynamicColors ?: DynamicColors(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
        background = MaterialTheme.colorScheme.background,
        surface = MaterialTheme.colorScheme.surface,
        onSurface = MaterialTheme.colorScheme.onSurface
    )

    // Driven directly off the shared drag state: this is the row currently
    // being carried, full stop. No parallel pressed-state guess needed.
    val isBeingDragged = dragDropState.draggingItemIndex == absoluteIndex

    // Only a row that has ever been picked up pays for animated state — every
    // other resting row reads plain static values instead of running its own
    // corner/elevation/color/border spring. Previously all of this ran on
    // every row unconditionally: with 20+ rows alive across the list that's
    // up to 80 live animation subscriptions spun up together the instant the
    // sheet mounts, and again each time LazyColumn recycles a row back into
    // view — real per-row animator setup cost, not draw cost, which is what
    // actually made "Up next" feel laggy. `hasEverBeenDragged` latches true
    // on pickup and stays true, so the one row that was just dropped still
    // gets its spring-back-to-rest animation instead of snapping — everything
    // else never pays the cost at all.
    var hasEverBeenDragged by remember { mutableStateOf(false) }
    if (isBeingDragged) hasEverBeenDragged = true

    val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val cornerMorph: Dp
    val elevation: Dp
    val containerColor: Color
    val borderAlpha: Float
    if (hasEverBeenDragged) {
        cornerMorph = animateDpAsState(
            targetValue = if (isBeingDragged) 28.dp else 18.dp,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "queueItemCornerMorph"
        ).value
        elevation = animateDpAsState(
            targetValue = if (isBeingDragged) 10.dp else 0.dp,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
            label = "queueItemElevation"
        ).value
        containerColor = animateColorAsState(
            targetValue = if (isBeingDragged) surfaceContainerHigh else surfaceContainerLow,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
            label = "queueItemContainerColor"
        ).value
        borderAlpha = animateFloatAsState(
            targetValue = if (isBeingDragged) 0.4f else 0f,
            animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
            label = "queueItemBorderAlpha"
        ).value
    } else {
        cornerMorph = 18.dp
        elevation = 0.dp
        containerColor = surfaceContainerLow
        borderAlpha = 0f
    }
    val rowShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = 18.dp,
        bottomEnd = cornerMorph
    )

    Surface(
        onClick = { onTrackSelect(qTrack) },
        modifier = modifier
            .fillMaxWidth()
            .then(Modifier.dragDropItem(absoluteIndex, dragDropState)),
        shape = rowShape,
        tonalElevation = elevation,
        shadowElevation = elevation,
        color = containerColor,
        border = if (borderAlpha > 0f) BorderStroke(1.dp, resolvedDynamicColors.primary.copy(alpha = borderAlpha)) else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                AsyncImage(
                    model = qTrack.thumbnailUri,
                    contentDescription = "Album art for ${qTrack.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(Icons.Rounded.MusicNote)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    qTrack.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    qTrack.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { isPendingDelete = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = resolvedDynamicColors.primary.copy(alpha = if (isBeingDragged) 0.9f else 0.35f),
                modifier = Modifier.padding(start = 4.dp).size(20.dp)
            )
        }
    }
}

@Composable
 fun PlayerControlButton(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    ToolzExpressiveIconButton(onClick = onClick, modifier = Modifier.size(size), shape = CircleShape) {
        content()
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
                ToolzExpressiveButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
                ToolzExpressiveButton(
                    onClick = onHide,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.VisibilityOff, null, modifier = Modifier.size(18.dp))
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
                            targetState = track,
                            transitionSpec = {
                                if (performanceMode) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (slideInVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) { it / 3 } + fadeIn(tween(400))) togetherWith
                                            (slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { -it / 3 } + fadeOut(tween(300)))
                                }.using(SizeTransform(clip = false))
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
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                }

                // ── Expandable lyrics + progress section ──────────────────────
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = if (performanceMode) fadeIn() + expandVertically() else
                        fadeIn(tween(280)) + expandVertically(
                            spring(dampingRatio = miniPlayerDamping, stiffness = miniPlayerStiffness),
                            expandFrom = Alignment.Top
                        ),
                    exit = if (performanceMode) fadeOut() + shrinkVertically() else
                        fadeOut(tween(200)) + shrinkVertically(
                            spring(dampingRatio = miniPlayerDamping, stiffness = miniPlayerStiffness),
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
// ─────────────────────────────────────────────────────────────────────────────
// Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogStreamQualitySheet(
    currentQuality: String,
    onDismiss: () -> Unit,
    onQualitySelected: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Stream quality", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Only applies to online Catalog playback. Auto stays adaptive and is the default.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            listOf(
                "AUTO" to "Adaptive quality based on the resolved stream",
                "HIGH" to "Prefer the highest bitrate stream",
                "MEDIUM" to "Balanced quality with lighter data use",
                "LOW" to "Use the smallest available audio stream"
            ).forEach { (quality, description) ->
                Surface(
                    onClick = { onQualitySelected(quality) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (currentQuality == quality) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentQuality == quality, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                quality.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

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
            ToolzExpressiveButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp),
                enabled = name.isNotBlank()
            ) {
                Text("CREATE", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            ToolzExpressiveButton(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(14.dp)) {
                Text("CANCEL", fontWeight = FontWeight.Bold)
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit,
    // New: lets the sheet create a playlist itself instead of dead-ending
    // on "no playlists found" with no way out but Cancel. The name is
    // reported back to the caller, which creates the playlist through the
    // ViewModel; this composable then auto-selects the first newly
    // appeared playlist with that name once `playlists` recomposes, so
    // creating one still results in the track landing inside it — same
    // as picking an existing playlist would.
    onCreatePlaylist: (String) -> Unit = {}
) {
    var isCreating by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var pendingName by remember { mutableStateOf<String?>(null) }

    // Once the caller's list includes a playlist matching the name we just
    // asked to create, select it and close — this is what makes "create"
    // inside this sheet actually finish the save, not just open a second
    // dialog.
    LaunchedEffect(playlists, pendingName) {
        val name = pendingName ?: return@LaunchedEffect
        val created = playlists.lastOrNull { it.name == name }
        if (created != null) {
            onSelect(created)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                shape = CircleShape
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVE TO PLAYLIST",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                // Persistent create shortcut, not just an empty-state
                // fallback — someone saving a track often wants a new
                // playlist even when others already exist.
                if (!isCreating) {
                    Surface(
                        onClick = { isCreating = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.bouncyClick(onClick = { isCreating = true })
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text("NEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isCreating) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Playlist name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                        Surface(
                            onClick = {
                                val trimmed = newPlaylistName.trim()
                                if (trimmed.isNotBlank()) {
                                    pendingName = trimmed
                                    onCreatePlaylist(trimmed)
                                    isCreating = false
                                    newPlaylistName = ""
                                }
                            },
                            enabled = newPlaylistName.isNotBlank(),
                            shape = CircleShape,
                            color = if (newPlaylistName.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Create playlist",
                                    tint = if (newPlaylistName.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (playlists.isEmpty() && !isCreating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "No playlists yet",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Text(
                            "Tap NEW above to create one",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            } else if (playlists.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Surface(
                            onClick = {
                                onSelect(playlist)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${playlist.trackUris.size} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistEmptyCard(onCreatePlaylist: () -> Unit) {
    // Mirrors FolderEmptyCard's language (bordered icon plate, left-aligned
    // copy, circular action chip) so the two empty states in the same tab
    // read as one designed family instead of two unrelated fallbacks.
    Surface(
        onClick = onCreatePlaylist,
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onCreatePlaylist),
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 8.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "No playlists yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Group your favorite tracks into a set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Create playlist",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun FolderEmptyCard(onAddFolder: () -> Unit) {
    // A left-aligned row with an explicit action button reads as an
    // invitation to act, not a decorative dead-end. The icon now sits on
    // its own bordered plate — echoing the same "chip + content" language
    // as the populated FolderCard grid — so the empty state feels like
    // part of the same family instead of a generic fallback block.
    Surface(
        onClick = onAddFolder,
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onAddFolder),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "No custom folders yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Pick a music directory to track it here",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add folder",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistPickerRow(playlist: Playlist, onClick: () -> Unit) {
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricCustomizationSheet(
    state: AiLyricsState,
    onDismiss: () -> Unit,
    onResetDefaults: () -> Unit,
    onToggleSeek: () -> Unit,
    onToggleAlwaysSync: () -> Unit,
    onToggleWordSync: () -> Unit,
    onSetLayout: (LyricsLayout) -> Unit,
    onSetFont: (LyricsFont) -> Unit
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
                .padding(bottom = 48.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
            ) {
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

                TextButton(
                    onClick = onResetDefaults,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            // Options Group
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        ExpressiveSwitch(
                            checked = state.isSeekEnabled,
                            onCheckedChange = { onToggleSeek() }
                        )
                    }
                }

                // Always Sync toggle
                Surface(
                    onClick = onToggleAlwaysSync,
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
                            Text("Always Sync", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text("Prioritize synced lyrics over plain text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExpressiveSwitch(
                            checked = state.alwaysSync,
                            onCheckedChange = { onToggleAlwaysSync() }
                        )
                    }
                }

                // Synced Words toggle
                Surface(
                    onClick = onToggleWordSync,
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
                            Text("Synced Words", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            Text("Highlights lyrics word by word", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ExpressiveSwitch(
                            checked = state.isWordSyncEnabled,
                            onCheckedChange = { onToggleWordSync() }
                        )
                    }
                }
            }

            // Alignment
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Typography", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    ToolzExpressiveIconButton(onClick = { /* TODO: Font Picker */ }, modifier = Modifier.size(32.dp), shape = CircleShape) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricsFont.entries.forEach { font ->
                        val selected = state.fontFamily == font
                        val fontFamily = when(font) {
                            LyricsFont.SANS_SERIF -> androidx.compose.ui.text.font.FontFamily.Default
                            LyricsFont.SERIF -> androidx.compose.ui.text.font.FontFamily.Serif
                            LyricsFont.MONOSPACE -> androidx.compose.ui.text.font.FontFamily.Monospace
                            LyricsFont.CURSIVE -> androidx.compose.ui.text.font.FontFamily.Cursive
                            LyricsFont.DISPLAY -> androidx.compose.ui.text.font.FontFamily.Default
                            LyricsFont.HANDWRITING -> androidx.compose.ui.text.font.FontFamily.Cursive
                        }

                        Surface(
                            onClick = { onSetFont(font) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
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
}
// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

fun highlightSearch(text: String, query: String, highlightColor: Color): AnnotatedString {
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

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

fun filterTracks(tracks: List<MusicTrack>, query: String): List<MusicTrack> {
    if (query.isBlank()) return tracks
    return tracks.filter {
        it.title.contains(query, ignoreCase = true) ||
                it.artist?.contains(query, ignoreCase = true) == true
    }
}
