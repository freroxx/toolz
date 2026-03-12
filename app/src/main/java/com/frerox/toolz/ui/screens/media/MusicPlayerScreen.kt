package com.frerox.toolz.ui.screens.media

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
// NEW: Specific imports for the 2026 Blur/RenderEffect system
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
// NEW: Bridge between Android RenderEffect and Compose
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*
// NEW: Math utilities for the Sine Wave
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val sliderPos by viewModel.sliderPosition.collectAsState()
    val context = LocalContext.current

    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isManageStorageGranted = Environment.isExternalStorageManager()
            if (isManageStorageGranted) viewModel.scanMusic()
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.addCustomFolder(it)
            } catch (e: Exception) {
                viewModel.addCustomFolder(it)
            }
        }
    }

    val playlistThumbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPlaylist?.let { playlist ->
                viewModel.updatePlaylistThumbnail(playlist, it)
            }
        }
    }

    val filteredTracks = remember(state.tracks, searchQuery) {
        filterTracks(state.tracks, searchQuery)
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    if (state.isSelectionMode) {
                        TopAppBar(
                            title = { Text(text = "${state.selectedTracks.size} Selected", fontWeight = FontWeight.Black) },
                            navigationIcon = {
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Rounded.Close, "Clear Selection")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showMultiSelectPlaylistPicker = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to Playlist")
                                }
                            }
                        )
                    } else {
                        TopAppBar(
                            title = { Text(text = "MUSIC PLAYER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                if (currentTab == 2) {
                                    IconButton(onClick = { folderLauncher.launch(null) }) {
                                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Add Folder")
                                    }
                                }
                                IconButton(onClick = { viewModel.scanMusic() }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Library")
                                }
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        shape = RoundedCornerShape(24.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(text = "By Title", fontWeight = FontWeight.Bold) },
                                            onClick = { viewModel.setSortOrder(SortOrder.TITLE); showSortMenu = false },
                                            leadingIcon = { Icon(Icons.Rounded.Title, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = "By Artist", fontWeight = FontWeight.Bold) },
                                            onClick = { viewModel.setSortOrder(SortOrder.ARTIST); showSortMenu = false },
                                            leadingIcon = { Icon(Icons.Rounded.Person, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(text = "By Recent", fontWeight = FontWeight.Bold) },
                                            onClick = { viewModel.setSortOrder(SortOrder.RECENT); showSortMenu = false },
                                            leadingIcon = { Icon(Icons.Rounded.Schedule, null) }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        placeholder = { Text(text = "Search your music...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
                modifier = Modifier.shadow(16.dp, RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    if (state.currentTrack != null && !showFullPlayer) {
                        MiniPlayer(
                            track = state.currentTrack!!,
                            isPlaying = state.isPlaying,
                            progress = state.playbackPosition,
                            duration = state.duration,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onClick = { showFullPlayer = true }
                        )
                    }

                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 4.dp
                            )
                        },
                        divider = {}
                    ) {
                        val tabs = listOf(
                            "Tracks" to Icons.Rounded.MusicNote,
                            "Library" to Icons.AutoMirrored.Rounded.PlaylistPlay,
                            "Folders" to Icons.Rounded.Folder
                        )
                        tabs.forEachIndexed { index, (label, icon) ->
                            Tab(
                                selected = currentTab == index,
                                onClick = { currentTab = index },
                                text = { Text(text = label, fontWeight = if (currentTab == index) FontWeight.Black else FontWeight.Bold, letterSpacing = 0.5.sp) },
                                icon = { Icon(icon, null) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .fadingEdge(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.05f to Color.Black,
                    0.95f to Color.Black,
                    1f to Color.Transparent
                ),
                length = 24.dp
            )
        ) {
            if (!musicPermission.status.isGranted || !isManageStorageGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.FolderSpecial, null, modifier = Modifier.size(120.dp).alpha(0.1f), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(24.dp))
                        Text(text = "Permission Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            text = "To access and play your music library, Toolz needs storage permissions.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = {
                                if (!musicPermission.status.isGranted) musicPermission.launchPermissionRequest()
                                else if (!isManageStorageGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    manageStorageLauncher.launch(intent)
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp)
                        ) {
                            Text(text = "GRANT ACCESS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
                }
            } else if (state.isLoading && state.tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeCap = StrokeCap.Round, modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                        Spacer(Modifier.height(24.dp))
                        Text(text = "SYNCHRONIZING...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                    },
                    label = "TabContent"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> TrackList(filteredTracks, state, viewModel)
                        1 -> LibrarySection(state, viewModel,
                            onCreatePlaylist = { showPlaylistDialog = true },
                            onPlaylistClick = { selectedPlaylist = it },
                            onUpdateThumb = {
                                selectedPlaylist = it
                                playlistThumbLauncher.launch("image/*")
                            }
                        )
                        2 -> FolderList(state, onFolderClick = { name, tracks -> selectedFolderTracks = name to tracks })
                    }
                }
            }
        }

        if (showPlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showPlaylistDialog = false },
                onCreate = { name ->
                    viewModel.createPlaylist(name)
                    showPlaylistDialog = false
                }
            )
        }

        if (showMultiSelectPlaylistPicker) {
            MultiSelectPlaylistPicker(
                playlists = state.playlists,
                onDismiss = { showMultiSelectPlaylistPicker = false },
                onPlaylistSelected = {
                    viewModel.addSelectedTracksToPlaylist(it)
                    showMultiSelectPlaylistPicker = false
                }
            )
        }

        if (selectedFolderTracks != null) {
            FolderTracksDialog(
                folderName = selectedFolderTracks!!.first,
                tracks = selectedFolderTracks!!.second,
                onDismiss = { selectedFolderTracks = null },
                onPlayTrack = { track ->
                    viewModel.playTrack(track, selectedFolderTracks!!.second)
                    selectedFolderTracks = null
                },
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
                onDeletePlaylist = {
                    viewModel.deletePlaylist(it)
                    selectedPlaylist = null
                },
                onAddTrack = { track: MusicTrack -> viewModel.addTrackToPlaylist(selectedPlaylist!!, track) },
                onRemoveTrack = { trackUri: String -> viewModel.removeTrackFromPlaylist(selectedPlaylist!!, trackUri) },
                onPlayTrack = { track: MusicTrack, playlistTracks: List<MusicTrack> -> viewModel.playTrack(track, playlistTracks) }
            )
        }

        if (showFullPlayer && state.currentTrack != null) {
            FullPlayerView(
                state = state,
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
                onStop = {
                    viewModel.stop()
                    showFullPlayer = false
                },
                onSetSleepTimer = { viewModel.setSleepTimer(it) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onSetArtShape = { viewModel.setArtShape(it) },
                onToggleRotation = { viewModel.toggleRotation() },
                onTogglePip = { viewModel.togglePipEnabled() }
            )
        }
    }
}

@Composable
fun TrackList(tracks: List<MusicTrack>, state: MusicUiState, viewModel: MusicPlayerViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (tracks.isEmpty() && !state.isLoading) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.MusicOff, null, modifier = Modifier.size(100.dp).alpha(0.1f))
                        Spacer(Modifier.height(16.dp))
                        Text(text = "NO MUSIC FOUND", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(text = "Scan your device to locate audio files.", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.scanMusic() }, shape = RoundedCornerShape(20.dp)) {
                            Text(text = "REFRESH LIBRARY", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        } else {
            items(tracks, key = { it.uri }) { track ->
                val isSelected = state.selectedTracks.contains(track.uri)
                TrackItem(
                    track = track,
                    isCurrent = track.uri == state.currentTrack?.uri,
                    isSelected = isSelected,
                    isSelectionMode = state.isSelectionMode,
                    onClick = {
                        if (state.isSelectionMode) {
                            viewModel.toggleTrackSelection(track.uri)
                        } else {
                            viewModel.playTrack(track, tracks)
                        }
                    },
                    onLongClick = { viewModel.toggleTrackSelection(track.uri) },
                    onDelete = { viewModel.deleteTrack(track) },
                    onAddToPlaylist = { playlist: Playlist -> viewModel.addTrackToPlaylist(playlist, track) },
                    onToggleFavorite = { viewModel.toggleFavorite(track) },
                    playlists = state.playlists
                )
            }
        }
    }
}

@Composable
fun FolderList(state: MusicUiState, onFolderClick: (String, List<MusicTrack>) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp, top = 20.dp, start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.folders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No folders with music.", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            items(state.folders.keys.toList()) { folderName ->
                FolderCard(
                    folderName = folderName,
                    trackCount = state.folders[folderName]?.size ?: 0,
                    onClick = { onFolderClick(folderName, state.folders[folderName] ?: emptyList()) }
                )
            }
        }
    }
}

@Composable
fun FolderCard(folderName: String, trackCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(86.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = folderName,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$trackCount TRACKS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun FolderTracksDialog(
    folderName: String,
    tracks: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    state: MusicUiState,
    viewModel: MusicPlayerViewModel
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = folderName.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            Box(modifier = Modifier.height(500.dp)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(tracks) { track ->
                        TrackItem(
                            track = track,
                            isCurrent = track.uri == state.currentTrack?.uri,
                            isSelected = state.selectedTracks.contains(track.uri),
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleTrackSelection(track.uri)
                                } else {
                                    onPlayTrack(track)
                                }
                            },
                            onLongClick = { viewModel.toggleTrackSelection(track.uri) },
                            onDelete = { viewModel.deleteTrack(track) },
                            onAddToPlaylist = { playlist: Playlist -> viewModel.addTrackToPlaylist(playlist, track) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            playlists = state.playlists
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) { Text(text = "DONE", fontWeight = FontWeight.Black) }
        },
        shape = RoundedCornerShape(40.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun LibrarySection(
    state: MusicUiState,
    viewModel: MusicPlayerViewModel,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onUpdateThumb: (Playlist) -> Unit
) {
    var showFavoritesDetail by remember { mutableStateOf(false) }
    var showRecentDetail by remember { mutableStateOf(false) }
    var showMostPlayedDetail by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Spacer(Modifier.height(28.dp))
            Text(text = "QUICK ACCESS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Favorites Card
                Surface(
                    modifier = Modifier
                        .weight(1.1f)
                        .height(220.dp)
                        .bouncyClick { showFavoritesDetail = true },
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        Column(modifier = Modifier.align(Alignment.BottomStart)) {
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.25f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(text = "Favorites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(text = "${state.favoriteTracks.size} Items", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }

                // Create Playlist Card
                Surface(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(220.dp)
                        .bouncyClick(onClick = onCreatePlaylist),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(70.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(text = "New List", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(44.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "RECENTLY PLAYED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { showRecentDetail = true }) { Text(text = "VIEW ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.recentlyPlayed.take(10)) { track ->
                    TrackCard(track = track, onClick = { viewModel.playTrack(track, state.recentlyPlayed) })
                }
            }
        }

        item {
            Spacer(Modifier.height(44.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "MOST PLAYED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { showMostPlayedDetail = true }) { Text(text = "VIEW ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.mostPlayed.take(10)) { track ->
                    TrackCard(track = track, onClick = { viewModel.playTrack(track, state.mostPlayed) })
                }
            }
        }

        item {
            Spacer(Modifier.height(44.dp))
            Text(text = "MY COLLECTIONS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
        }

        items(state.playlists.chunked(2)) { chunk ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                chunk.forEach { playlist ->
                    Box(modifier = Modifier.weight(1f)) {
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) },
                            onLongClick = { onUpdateThumb(playlist) },
                            onPlay = { viewModel.playPlaylist(playlist) }
                        )
                    }
                }
                if (chunk.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

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
            isEditable = false
        )
    }

    if (showRecentDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = "Recently Played", trackUris = state.recentlyPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showRecentDetail = false },
            onPlayPlaylist = { if (state.recentlyPlayed.isNotEmpty()) viewModel.playTrack(state.recentlyPlayed.first(), state.recentlyPlayed) },
            onDeletePlaylist = {},
            onAddTrack = { },
            onRemoveTrack = { },
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false
        )
    }

    if (showMostPlayedDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = "Most Played", trackUris = state.mostPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showMostPlayedDetail = false },
            onPlayPlaylist = { if (state.mostPlayed.isNotEmpty()) viewModel.playTrack(state.mostPlayed.first(), state.mostPlayed) },
            onDeletePlaylist = {},
            onAddTrack = { },
            onRemoveTrack = { },
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false
        )
    }
}

@Composable
fun TrackCard(track: MusicTrack, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .bouncyClick(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .size(140.dp)
                .shadow(8.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = track.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerView(
    state: MusicUiState,
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
    onTogglePip: () -> Unit
) {
    val track = state.currentTrack ?: return
    var showSleepTimer by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "artRotation"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Animated Blurred Background
            AsyncImage(
                model = track.thumbnailUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            AndroidRenderEffect
                                .createBlurEffect(
                                    100f,
                                    100f,
                                    AndroidShader.TileMode.CLAMP
                                )
                                .asComposeRenderEffect()
                        } else null
                        alpha = 0.15f
                    },
                error = painterResource(android.R.drawable.ic_media_play)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.size(52.dp).align(Alignment.CenterStart),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.padding(10.dp).size(32.dp))
                    }

                    Text(
                        text = "PLAYING NOW",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        Surface(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", modifier = Modifier.padding(14.dp))
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = "Visuals: Circular", fontWeight = FontWeight.Bold) },
                                onClick = { onSetArtShape("CIRCLE"); showOverflowMenu = false },
                                leadingIcon = { RadioButton(selected = state.artShape == "CIRCLE", onClick = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(text = "Visuals: Square", fontWeight = FontWeight.Bold) },
                                onClick = { onSetArtShape("SQUARE"); showOverflowMenu = false },
                                leadingIcon = { RadioButton(selected = state.artShape == "SQUARE", onClick = null) }
                            )
                            HorizontalDivider(Modifier.padding(vertical = 4.dp).alpha(0.1f))
                            DropdownMenuItem(
                                text = { Text(text = "Art Rotation", fontWeight = FontWeight.Bold) },
                                onClick = { onToggleRotation(); showOverflowMenu = false },
                                leadingIcon = { Switch(checked = state.rotationEnabled, onCheckedChange = null, modifier = Modifier.scale(0.7f)) }
                            )
                            DropdownMenuItem(
                                text = { Text(text = "Sleep Timer", fontWeight = FontWeight.Bold) },
                                onClick = { showOverflowMenu = false; showSleepTimer = true },
                                leadingIcon = { Icon(Icons.Rounded.Timer, null) }
                            )
                            HorizontalDivider(Modifier.padding(vertical = 4.dp).alpha(0.1f))
                            DropdownMenuItem(
                                text = { Text(text = "Stop & Exit", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black) },
                                onClick = { showOverflowMenu = false; onStop() },
                                leadingIcon = { Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Premium Album Art
                val artSize = (configuration.screenWidthDp * 0.85f).dp
                val shape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(72.dp)
                Surface(
                    modifier = Modifier
                        .size(artSize)
                        .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f)
                        .shadow(40.dp, shape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(4.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    AsyncImage(
                        model = track.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(android.R.drawable.ic_media_play)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Track Information
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = track.artist?.uppercase() ?: "UNKNOWN ARTIST",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(32.dp))

                // Premium Progress Slider
                val currentPos = sliderPos ?: state.playbackPosition
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    SquigglySlider(
                        value = currentPos.toFloat(),
                        onValueChange = { onSliderChange(it.toLong()) },
                        onValueChangeFinished = onSliderChangeFinished,
                        valueRange = 0f..(state.duration.toFloat().coerceAtLeast(1f)),
                        isPlaying = state.isPlaying
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPos),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatDuration(state.duration),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // Control Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onToggleFavorite(track) }) {
                        val favScale by animateFloatAsState(if (track.isFavorite) 1.25f else 1f)
                        Icon(
                            if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null,
                            tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp).scale(favScale)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton(onClick = onSkipPrev, modifier = Modifier.size(56.dp).bouncyClick {}) {
                            Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(40.dp))
                        }

                        Surface(
                            onClick = onTogglePlay,
                            modifier = Modifier.size(90.dp).bouncyClick {},
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    null,
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                        }

                        IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp).bouncyClick {}) {
                            Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(40.dp))
                        }
                    }

                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            if (state.isShuffleOn) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle,
                            null,
                            tint = if (state.isShuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = onToggleRepeat) {
                        Icon(
                            when(state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn
                                Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn
                                else -> Icons.Rounded.Repeat
                            },
                            null,
                            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (showSleepTimer) {
        SleepTimerDialog(onDismiss = { showSleepTimer = false }, onSet = onSetSleepTimer)
    }
}

@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    // 1. Phase animation (Horizontal movement)
    val infiniteTransition = rememberInfiniteTransition(label = "wave_motion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_state"
    )

    // 2. Amplitude animation (Flattens wave when paused/dragged)
    val currentAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isDragged) 5f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "amplitude_state"
    )

    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Optimized Canvas using drawWithCache to prevent allocations during draw
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .drawWithCache {
                    val path = Path()
                    onDrawBehind {
                        // Slider standard padding is 12dp. Aligning here prevents "drift"
                        val horizontalPadding = 12.dp.toPx()
                        val canvasWidth = size.width - (horizontalPadding * 2)
                        val centerY = size.height / 2

                        val range = valueRange.endInclusive - valueRange.start
                        val progress = if (range > 0) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
                        val thumbX = horizontalPadding + (canvasWidth * progress)

                        val waveFreq = 32.dp.toPx() // Wavelength
                        val amp = currentAmplitude.dp.toPx()

                        // A. Draw Inactive Track (Right side - straight)
                        drawLine(
                            color = inactive,
                            start = Offset(thumbX, centerY),
                            end = Offset(size.width - horizontalPadding, centerY),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // B. Draw Active Squiggly Path (Left side)
                        path.reset()
                        path.moveTo(horizontalPadding, centerY)

                        val step = 4f // Performance step
                        var x = 0f
                        val activeWidth = thumbX - horizontalPadding

                        while (x < activeWidth) {
                            // Subtraction in sin() creates forward motion
                            val y = centerY + amp * sin((2 * PI * x / waveFreq) - phase).toFloat()
                            path.lineTo(horizontalPadding + x, y)
                            x += step
                        }
                        path.lineTo(thumbX, centerY) // Smooth snap to thumb position

                        drawPath(
                            path = path,
                            color = primary,
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // C. Draw Thumb (Always present)
                        drawCircle(
                            color = primary,
                            radius = if (isDragged) 10.dp.toPx() else 8.dp.toPx(),
                            center = Offset(thumbX, centerY)
                        )
                    }
                }
        )

        // Invisible Interactive Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onSet: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "SLEEP TIMER", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(15, 30, 45, 60, 90).forEach { mins ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClick { onSet(mins); onDismiss() },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(20.dp))
                            Text(text = "$mins Minutes", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onSet(null); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "CANCEL TIMER", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                }
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(40.dp)
    )
}

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
    isEditable: Boolean = true
) {
    var showAddTrack by remember { mutableStateOf(false) }
    val playlistTracks = remember(allTracks, playlist.trackUris) {
        allTracks.filter { it.uri in playlist.trackUris }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.95f),
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(150.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 16.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    if (playlist.thumbnailUri != null) {
                        AsyncImage(model = playlist.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = playlist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(text = "${playlistTracks.size} Tracks Indexed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                }
                if (isEditable) {
                    IconButton(
                        onClick = { onDeletePlaylist(playlist) },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    }
                }
            }

            Row(modifier = Modifier.padding(vertical = 36.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onPlayPlaylist(playlist) },
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(text = "SHUFFLE PLAY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                }
                if (isEditable) {
                    FilledTonalIconButton(
                        onClick = { showAddTrack = true },
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(36.dp))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                items(playlistTracks) { track ->
                    TrackItem(
                        track = track,
                        isCurrent = false,
                        onClick = { onPlayTrack(track, playlistTracks) },
                        onDelete = { onRemoveTrack(track.uri) },
                        onAddToPlaylist = {},
                        onToggleFavorite = {},
                        playlists = emptyList(),
                        deleteLabel = "Remove"
                    )
                }
            }
        }
    }

    if (showAddTrack) {
        AlertDialog(
            onDismissRequest = { showAddTrack = false },
            title = { Text(text = "SELECT TRACKS", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                val availableTracks = allTracks.filter { it.uri !in playlist.trackUris }
                if (availableTracks.isEmpty()) {
                    Text(text = "All tracks already added.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    LazyColumn(modifier = Modifier.height(450.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(availableTracks) { track ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().bouncyClick { onAddTrack(track); showAddTrack = false },
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(14.dp)) {
                                        AsyncImage(model = track.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop)
                                    }
                                    Spacer(Modifier.width(18.dp))
                                    Column {
                                        Text(text = track.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(text = track.artist ?: "Unknown Artist", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTrack = false }) { Text(text = "DISMISS", fontWeight = FontWeight.Black) }
            },
            shape = RoundedCornerShape(40.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onLongClick: () -> Unit, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(44.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (playlist.thumbnailUri != null) {
                AsyncImage(
                    model = playlist.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.75f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f), MaterialTheme.colorScheme.surface)
                            )
                        )
                )
            }

            // Play Button Overlay
            Surface(
                onClick = onPlay,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(64.dp).bouncyClick {},
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                }
            }

            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.BottomStart)
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackUris.size} TRACKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

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
    deleteLabel: String = "Delete"
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(28.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)) else null
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 4.dp
            ) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(android.R.drawable.ic_media_play)
                )
                if (isSelected) {
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = track.artist?.uppercase() ?: "UNKNOWN ARTIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            } else {
                IconButton(onClick = onToggleFavorite) {
                    val favIcon = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
                    val favColor = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    Icon(favIcon, null, tint = favColor, modifier = Modifier.size(26.dp))
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.outline)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Add to Playlist", fontWeight = FontWeight.Bold) },
                        onClick = { showPlaylistPicker = true; showMenu = false },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(text = deleteLabel, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text(text = "SELECT PLAYLIST", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                if (playlists.isEmpty()) {
                    Text(text = "No playlists available.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                } else {
                    LazyColumn(modifier = Modifier.height(350.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlists) { playlist ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().bouncyClick { onAddToPlaylist(playlist); showPlaylistPicker = false },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(18.dp))
                                    Text(text = playlist.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text(text = "CANCEL", fontWeight = FontWeight.Black) }
            },
            shape = RoundedCornerShape(40.dp)
        )
    }
}

@Composable
fun MiniPlayer(
    track: MusicTrack,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit
) {
    val targetProgress = if (duration > 0) progress.toFloat() / duration else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "smoothMiniProgress"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .height(100.dp)
            .shadow(32.dp, RoundedCornerShape(36.dp))
            .bouncyClick(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
        shape = RoundedCornerShape(36.dp),
        tonalElevation = 16.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp
                ) {
                    AsyncImage(
                        model = track.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(android.R.drawable.ic_media_play)
                    )
                }

                Spacer(Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist?.uppercase() ?: "UNKNOWN ARTIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.6.sp
                    )
                }

                Surface(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(60.dp).bouncyClick {},
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Toggle Play",
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "NEW COLLECTION", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = "Playlist Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp)
            ) { Text(text = "CREATE", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "DISMISS", fontWeight = FontWeight.Bold) }
        },
        shape = RoundedCornerShape(40.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun MultiSelectPlaylistPicker(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "ADD TO LIST...", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            if (playlists.isEmpty()) {
                Text(text = "No collections found.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn(modifier = Modifier.height(350.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists) { playlist ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().bouncyClick { onPlaylistSelected(playlist) },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(18.dp))
                                Text(text = playlist.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text(text = "CANCEL", fontWeight = FontWeight.Black) }
        },
        shape = RoundedCornerShape(40.dp)
    )
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
            (it.artist?.contains(query, ignoreCase = true) == true)
    }
}
