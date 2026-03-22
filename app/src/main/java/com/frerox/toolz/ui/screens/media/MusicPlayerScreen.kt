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
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.screens.media.ai.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.util.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    aiViewModel: NowPlayingAiViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val sliderPos by viewModel.sliderPosition.collectAsState()
    val context = LocalContext.current

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var currentTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showMultiSelectPlaylistPicker by remember { mutableStateOf(false) }
    var selectedFolderTracks by remember { mutableStateOf<Pair<String, List<MusicTrack>>?>(null) }

    val musicPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    var isManageStorageGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
            else true
        )
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isManageStorageGranted = Environment.isExternalStorageManager()
            if (isManageStorageGranted) viewModel.scanMusic()
        }
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
                onMultiAddPlaylist = { showMultiSelectPlaylistPicker = true }
            )
        },
        bottomBar = {
            ScreenBottomBar(
                state = state,
                aiState = aiState,
                playbackPosition = playbackPosition,
                duration = duration,
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                onTogglePlay = { viewModel.togglePlayPause() },
                onOpenFullPlayer = { showFullPlayer = true },
                onExpand = { aiViewModel.toggleExpandedPill() }
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
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
                            0 -> TrackList(filteredTracks, state, viewModel)
                            1 -> LibrarySection(
                                state, viewModel,
                                onCreatePlaylist = { showPlaylistDialog = true },
                                onPlaylistClick = { selectedPlaylist = it },
                                onUpdateThumb = { selectedPlaylist = it; playlistThumbLauncher.launch("image/*") },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) }
                            )
                            2 -> FolderList(state) { name, tracks -> selectedFolderTracks = name to tracks }
                        }
                    }
                }
            }
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
            FullPlayerView(
                state = state,
                aiState = aiState,
                playbackPosition = playbackPosition,
                duration = duration,
                sliderPos = sliderPos,
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
                onToggleRotation = { viewModel.toggleRotation() },
                onTogglePip = { viewModel.togglePipEnabled() },
                onOpenAi = { viewModel.vibrationManager.vibrateClick(); showAiSheet = true }
            )
        }
        if (showAiSheet) {
            NowPlayingAiBottomSheet(
                viewModel = aiViewModel,
                onDismiss = { showAiSheet = false },
                vibrationManager = viewModel.vibrationManager
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
    onMultiAddPlaylist: () -> Unit
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
                            Text(
                                text = "MUSIC",
                                fontWeight = FontWeight.Black,
                                letterSpacing = 3.sp,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
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
    onTabChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onExpand: () -> Unit
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
                visible = state.currentTrack != null,
                enter = fadeIn(tween(300)) + expandVertically(
                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(tween(200)) + shrinkVertically(
                    animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top
                )
            ) {
                state.currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = state.isPlaying,
                        progress = playbackPosition,
                        duration = duration,
                        performanceMode = state.performanceMode,
                        onTogglePlay = onTogglePlay,
                        onClick = onOpenFullPlayer,
                        onExpand = onExpand,
                        isExpanded = aiState.isExpandedPill,
                        lyricsState = aiState.lyricsState,
                        rotationEnabled = state.rotationEnabled
                    )
                }
            }

            // Custom TabRow
            PillTabRow(
                selectedTab = currentTab,
                onTabChange = onTabChange
            )
        }
    }
}

@Composable
private fun PillTabRow(selectedTab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf(
        "Tracks" to Icons.Rounded.MusicNote,
        "Library" to Icons.AutoMirrored.Rounded.PlaylistPlay,
        "Folders" to Icons.Rounded.Folder
    )
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
fun TrackList(tracks: List<MusicTrack>, state: MusicUiState, viewModel: MusicPlayerViewModel) {
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
                        playlists = state.playlists
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

@OptIn(ExperimentalFoundationApi::class)
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
    deleteLabel: String = "Delete",
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    // Direct color selection — no animateColorAsState in lists (too expensive)
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        isCurrent  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else       -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp),
        color = bgColor,
        border = if (isCurrent)
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
                        error = painterResource(android.R.drawable.ic_media_play)
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
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist?.uppercase() ?: "UNKNOWN ARTIST",
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
                    animationSpec = spring(Spring.DampingRatioMediumBouncy),
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
    Column(
        modifier = Modifier
            .width(132.dp)
            .bouncyClick(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .size(132.dp)
                .shadow(10.dp, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = track.thumbnailUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(android.R.drawable.ic_media_play)
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
        Text(
            text = track.artist?.uppercase() ?: "UNKNOWN",
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
    val borderAlpha by animateFloatAsState(
        if (isCurrentFolder) 0.6f else 0.2f,
        tween(300),
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
            // Background: two overlapping circles for depth
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.025f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 1.1f, -size.height * 0.3f)
                )
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.015f),
                    radius = size.width * 0.4f,
                    center = Offset(size.width * 0.9f, size.height * 1.1f)
                )
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
                        color = if (isCurrentFolder)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Folder,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Handle
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            Spacer(Modifier.height(16.dp))

            // Header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(folderName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${tracks.size} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (currentlyPlayingInFolder) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)) {
                                Text("PLAYING", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }
                // Play all button
                FilledTonalIconButton(onClick = { onPlayTrack(tracks.first()) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search in folder…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)) },
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
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> }  // wire to ViewModel
) {
    var showFavoritesDetail by remember { mutableStateOf(false) }
    var showRecentDetail by remember { mutableStateOf(false) }
    var showMostPlayedDetail by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 130.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Quick access cards ───────────────────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SectionLabel("LIBRARY")
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorites
                QuickAccessCard(
                    modifier = Modifier.weight(1f).height(200.dp),
                    icon = Icons.Rounded.Favorite,
                    label = "Favorites",
                    count = state.favoriteTracks.size,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showFavoritesDetail = true }
                )
                Column(
                    modifier = Modifier.weight(0.85f),
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
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
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
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // ── Recently Played ──────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(36.dp))
            RowSectionHeader("RECENTLY PLAYED") { showRecentDetail = true }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.recentlyPlayed.take(12)) { track ->
                    TrackCard(track = track, onClick = { viewModel.playTrack(track, state.recentlyPlayed) })
                }
            }
        }

        // ── Most Played ──────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(36.dp))
            RowSectionHeader("MOST PLAYED") { showMostPlayedDetail = true }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.mostPlayed.take(12)) { track ->
                    TrackCard(track = track, onClick = { viewModel.playTrack(track, state.mostPlayed) })
                }
            }
        }

        // ── Playlists ────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(36.dp))
            SectionLabel("PLAYLISTS")
            Spacer(Modifier.height(16.dp))
        }

        items(state.playlists.chunked(2)) { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
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
                        modifier = Modifier.fillMaxSize().alpha(0.55f)
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)), startY = 60f)
                        )
                    )
                }
                firstTrackThumbnails.isNotEmpty() -> {
                    // Always show mosaic when we have any thumbnails
                    val thumbs = (firstTrackThumbnails + List(4) { null }).take(4)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = thumbs[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
                            AsyncImage(model = thumbs[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = thumbs[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
                            AsyncImage(model = thumbs[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)), startY = 70f)
                        )
                    )
                }
                else -> {
                    // Solid gradient fallback when empty playlist
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            )
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.95f),
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            }
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
                    border = androidx.compose.foundation.BorderStroke(
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
                                AsyncImage(model = thumbs[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
                                AsyncImage(model = thumbs[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
                            }
                            Row(modifier = Modifier.weight(1f)) {
                                AsyncImage(model = thumbs[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
                                AsyncImage(model = thumbs[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = painterResource(android.R.drawable.ic_media_play))
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
                    IconButton(onClick = { onDeletePlaylist(playlist) }, modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), CircleShape)) {
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
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PLAY ALL", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = { onPlayPlaylist(playlist) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(4.dp)
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SHUFFLE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                }
                if (isEditable) {
                    FilledTonalIconButton(onClick = { showAddTrack = true }, modifier = Modifier.size(50.dp), shape = RoundedCornerShape(16.dp)) {
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
    playbackPosition: Long,
    duration: Long,
    sliderPos: Long?,
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
    onToggleRotation: () -> Unit,
    onTogglePip: () -> Unit,
    onOpenAi: () -> Unit
) {
    val track = state.currentTrack ?: return
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)
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
                            renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                AndroidRenderEffect.createBlurEffect(130f, 130f, AndroidShader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            else null
                            alpha = 0.11f
                        },
                    error = painterResource(android.R.drawable.ic_media_play)
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
                            HorizontalDivider(modifier = Modifier.alpha(0.08f).padding(vertical = 4.dp))
                            DropdownMenuItem(text = { Text("Stop & exit", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error) }, onClick = { showOverflowMenu = false; onStop() }, leadingIcon = { Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }

                // ── 2. Album art — weight(1f) fills all remaining vertical space ─
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val artMaxSize = (configuration.screenWidthDp * 0.76f).dp.coerceAtMost(300.dp)
                    val artShape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(56.dp)

                    if (!state.performanceMode) {
                        val haloAlpha by animateFloatAsState(if (state.isPlaying) 0.45f else 0.1f, tween(900), label = "hA")
                        val haloScale by animateFloatAsState(if (state.isPlaying) 1.08f else 1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow), label = "hS")
                        Box(modifier = Modifier.size(artMaxSize).scale(haloScale).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha * 0.4f), Color.Transparent)), artShape))
                    }

                    Surface(
                        modifier = Modifier
                            .size(artMaxSize)
                            .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f)
                            .shadow(if (state.performanceMode) 6.dp else 36.dp, artShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shape = artShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    ) {
                        AsyncImage(
                            model = track.thumbnailUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = painterResource(android.R.drawable.ic_media_play)
                        )
                    }
                }

                // ── 3. Track info ─────────────────────────────────────────────
                Spacer(Modifier.height(18.dp))
                AnimatedContent(
                    targetState = track.uri,
                    transitionSpec = { (fadeIn(tween(300)) + slideInVertically { it / 5 }).togetherWith(fadeOut(tween(180))) },
                    label = "trackInfo"
                ) { _ ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(text = track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(text = track.artist?.uppercase() ?: "UNKNOWN ARTIST", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    }
                }

                // ── 4. Slider ─────────────────────────────────────────────────
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
                        Text(formatDuration(currentPos), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    }
                }

                // ── 5. Transport controls ─────────────────────────────────────
                Spacer(Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PlayerControlButton(size = 56.dp, onClick = onSkipPrev) {
                        Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    val playScale by animateFloatAsState(if (state.isPlaying) 1f else 1.05f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "pS")
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
                Spacer(Modifier.height(16.dp))
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
                    Box {
                        IconButton(onClick = onToggleRepeat, modifier = Modifier.size(48.dp)) {
                            Icon(when (state.repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn; Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn; else -> Icons.Rounded.Repeat }, null, tint = if (repeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(24.dp))
                        }
                        androidx.compose.animation.AnimatedVisibility(visible = repeatActive, enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.5f), exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.5f)) {
                            Surface(modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp), shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary) {
                                Text(if (state.repeatMode == Player.REPEAT_MODE_ONE) "1" else "∞", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp), fontSize = 8.sp)
                            }
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
        QueueSheet(queue = state.queue, currentTrack = track, onDismiss = { showQueue = false })
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<MusicTrack>,
    currentTrack: MusicTrack,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f).padding(horizontal = 20.dp)
        ) {
            Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text("Up Next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("${queue.size} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.07f))
            Spacer(Modifier.height(8.dp))

            if (queue.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.QueuePlayNext, null, modifier = Modifier.size(48.dp).alpha(0.12f), tint = MaterialTheme.colorScheme.onSurface)
                        Text("Queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(queue, key = { _, t -> t.uri }) { index, qTrack ->
                        val isCurrent = qTrack.uri == currentTrack.uri
                        Surface(
                            modifier = Modifier.fillMaxWidth().animateItem(),
                            shape = RoundedCornerShape(18.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)) else null
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                    if (isCurrent) PlayingBarsIndicator()
                                    else Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), textAlign = TextAlign.Center)
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(12.dp)) {
                                    AsyncImage(model = qTrack.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), error = painterResource(android.R.drawable.ic_media_play))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(qTrack.title, fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    Text(qTrack.artist?.uppercase() ?: "UNKNOWN", style = MaterialTheme.typography.labelSmall, color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (isCurrent) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
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

@Composable
fun MiniPlayer(
    track: MusicTrack,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    performanceMode: Boolean,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit,
    onExpand: () -> Unit = {},
    isExpanded: Boolean = false,
    lyricsState: AiLyricsState? = null,
    rotationEnabled: Boolean = true
) {
    val targetProgress = if (duration > 0) progress.toFloat() / duration else 0f

    // Progress animation — skip in performance mode
    val animatedProgress by if (performanceMode) {
        remember(targetProgress) { mutableFloatStateOf(targetProgress) }
    } else {
        animateFloatAsState(targetProgress, tween(600, easing = LinearOutSlowInEasing), label = "miniProg")
    }

    // Corner radius morphs between collapsed/expanded
    val cornerRadius by animateDpAsState(
        if (isExpanded) 24.dp else 40.dp,
        if (performanceMode) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
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
        if (performanceMode) 4.dp else if (isExpanded) 20.dp else 12.dp,
        if (performanceMode) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
        label = "miniElev"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .animateContentSize(
                if (performanceMode) snap()
                else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            )
            .shadow(elevation, RoundedCornerShape(cornerRadius), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            // clip() ensures nothing (progress wash, bottom bar) bleeds outside the pill
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(16.dp),
        shape = RoundedCornerShape(cornerRadius)
    ) {
        Box {
            // ── Background progress wash — covers FULL surface height ─────────
            // Bug fix: previous version locked height to 76dp so the wash
            // disappeared in the expanded state. Now it fills the entire surface.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = if (isExpanded) 0.14f else 0.16f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                            )
                        )
                    )
            )

            Column {
                // ── Always-visible compact row ────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album art — circle when expanded, rounded rect when collapsed
                    val artShape = if (isExpanded) CircleShape else RoundedCornerShape(14.dp)
                    Surface(
                        modifier = Modifier
                            .size(54.dp)
                            .rotate(if (isPlaying && rotationEnabled && isExpanded && !performanceMode) artRotation else 0f),
                        shape = artShape,
                        shadowElevation = if (performanceMode) 2.dp else 5.dp
                    ) {
                        AsyncImage(
                            model = track.thumbnailUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = painterResource(android.R.drawable.ic_media_play)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    // Song info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleMedium,  // bigger than before (was bodyMedium)
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            track.artist?.uppercase() ?: "UNKNOWN",
                            style = MaterialTheme.typography.labelMedium,  // bigger than before (was labelSmall)
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Expand chevron — clean circle button, no "lyrics" label
                    val chevronRotF by animateFloatAsState(
                        if (isExpanded) 0f else 180f,
                        if (performanceMode) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        label = "chevronF"
                    )
                    Surface(
                        onClick = onExpand,
                        modifier = Modifier.size(36.dp).bouncyClick {},
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isExpanded) 0.15f else 0.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.ExpandLess,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp).rotate(if (performanceMode) (if (isExpanded) 0f else 180f) else chevronRotF)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Play/Pause
                    Surface(
                        onClick = onTogglePlay,
                        modifier = Modifier.size(50.dp).bouncyClick {},
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = if (performanceMode) 2.dp else 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (performanceMode) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    null,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Crossfade(targetState = isPlaying, animationSpec = tween(180), label = "ppMini") { playing ->
                                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }

                // ── Expandable lyrics + progress section ──────────────────────
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = if (performanceMode) EnterTransition.None else
                        fadeIn(tween(220)) + expandVertically(
                            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                            expandFrom = Alignment.Top
                        ),
                    exit = if (performanceMode) ExitTransition.None else
                        fadeOut(tween(160)) + shrinkVertically(
                            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow),
                            shrinkTowards = Alignment.Top
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Thin separator
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(0.35f).alpha(if (performanceMode) 0.2f else 0.12f)
                        )
                        Spacer(Modifier.height(12.dp))

                        // Lyric line — fixed 64dp height so the pill never layout-shifts per line
                        Box(
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (performanceMode) {
                                // Performance mode: instant text swap, no animation
                                if (currentLyric != null) {
                                    Text(
                                        currentLyric,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                    )
                                } else {
                                    Text(
                                        "♪  Enjoy the music  ♪",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        fontWeight = FontWeight.Bold,
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
                                            style = MaterialTheme.typography.titleMedium,  // bigger (was titleSmall)
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 22.sp,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                        )
                                    } else {
                                        Text(
                                            "♪  Enjoy the music  ♪",
                                            style = MaterialTheme.typography.bodyMedium,  // bigger (was bodySmall)
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

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
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                formatDuration(duration),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
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

// ─────────────────────────────────────────────────────────────────────────────
// Utilities
// ─────────────────────────────────────────────────────────────────────────────

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