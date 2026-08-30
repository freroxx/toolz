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
import com.frerox.toolz.ui.screens.media.sections.MusicTopBar
import com.frerox.toolz.ui.screens.media.sections.ScreenBottomBar
import com.frerox.toolz.ui.screens.media.sections.ScreenTopBar
import com.frerox.toolz.ui.screens.media.sections.FolderTracksDialog
import com.frerox.toolz.ui.screens.media.sections.LibrarySection
import com.frerox.toolz.ui.screens.media.sections.PlaylistDetailView
import com.frerox.toolz.ui.screens.media.sections.PlaylistPickerRow
import com.frerox.toolz.ui.screens.media.sections.TrackList
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*
import com.frerox.toolz.ui.screens.media.sections.FullPlayerView
import com.frerox.toolz.ui.screens.media.sections.QueueSheet

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalPermissionsApi::class
)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    aiViewModel: NowPlayingAiViewModel = hiltViewModel(),
    catalogViewModel: CatalogViewModel = hiltViewModel(),
    initialTab: Int = 0,
    initialUri: String? = null,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    val catalogState by catalogViewModel.uiState.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val sliderPos by viewModel.sliderPosition.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrEmpty() && initialUri != "{initialUri}") {
            try {
                viewModel.playUri(android.net.Uri.parse(initialUri))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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

    // Auto-refresh library every time the user opens the tool, and periodically while active
    LaunchedEffect(Unit) {
        viewModel.refreshLibraryOnOpen()
        while (isActive) {
            delay(45_000)
            viewModel.refreshLibrarySilent()
        }
    }

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

    val isLogicalPlaying = state.isPlaying || aiState.isInstrumentalPlaying

    // FIX: Decouple microphone listening from the player's buffering state.
    // The previous version would pause/resume the recognizer on every 1s
    // buffering gap, causing the "on/off" flickering reported by users.
    // We now use playWhenReady for intent-based gating.
    LaunchedEffect(state.isKaraokeActive, aiState.karaokeSpeechCorrectionEnabled, state.playWhenReady) {
        if (state.isKaraokeActive && aiState.karaokeSpeechCorrectionEnabled) {
            if (state.playWhenReady) aiViewModel.resumeKaraokeListening()
            else aiViewModel.pauseKaraokeListening()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.queueWarning) {
        state.queueWarning?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeQueueWarning()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .fadingEdges(top = 20.dp, bottom = 24.dp)
                .animateContentSize(animationSpec = tween(260, easing = FastOutSlowInEasing))
        ) {
            when {
                // P1-09 fix: music only needs READ_MEDIA_AUDIO; MANAGE_ALL_FILES is optional
                // for power users. Don't block playback if audio permission is granted.
                !musicPermission.status.isGranted -> {
                    PermissionPlaceholder(
                        onAllow = { musicPermission.launchPermissionRequest() }
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
