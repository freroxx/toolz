package com.frerox.toolz.ui.screens.media

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var currentTab by remember { mutableIntStateOf(0) } // 0: Tracks, 1: Library, 2: Folders
    var searchQuery by remember { mutableStateOf("") }
    var showMultiSelectPlaylistPicker by remember { mutableStateOf(false) }
    var selectedFolderTracks by remember { mutableStateOf<Pair<String, List<MusicTrack>>?>(null) }

    val musicPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.addCustomFolder(it) }
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
        if (searchQuery.isEmpty()) state.tracks
        else state.tracks.filter { it.title.contains(searchQuery, ignoreCase = true) || (it.artist?.contains(searchQuery, ignoreCase = true) == true) }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    if (state.isSelectionMode) {
                        TopAppBar(
                            title = { Text("${state.selectedTracks.size} Selected", fontWeight = FontWeight.Bold) },
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
                            title = { Text("Music", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium) },
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
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                                }
                            }
                        )
                    }
                    
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (state.sortOrder == order) Icon(Icons.Rounded.Check, null)
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search library...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
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
                                text = { Text(label, fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal) },
                                icon = { Icon(icon, null) }
                            )
                        }
                    }
                    if (state.currentTrack != null && !showFullPlayer) {
                        MiniPlayer(
                            track = state.currentTrack!!,
                            isPlaying = state.isPlaying,
                            progress = state.progress,
                            duration = state.duration,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onClick = { showFullPlayer = true }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!musicPermission.status.isGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        Spacer(Modifier.height(16.dp))
                        Text("Permission needed to access music files", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { musicPermission.launchPermissionRequest() },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else if (state.isLoading && state.tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeCap = StrokeCap.Round)
                        Spacer(Modifier.height(16.dp))
                        Text("Scanning library...", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
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
                onToggleRotation = { viewModel.toggleRotation() }
            )
        }
    }
}

@Composable
fun TrackList(tracks: List<MusicTrack>, state: MusicUiState, viewModel: MusicPlayerViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
    ) {
        if (tracks.isEmpty() && !state.isLoading) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MusicOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Text("No tracks found", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.scanMusic() }) {
                            Text("Refresh Library")
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
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.folders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No folders found", color = MaterialTheme.colorScheme.outline)
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
            .height(160.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ), 
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Folder, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                folderName, 
                fontWeight = FontWeight.Bold, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "$trackCount songs", 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
        title = { Text(folderName, fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.height(450.dp)) {
                LazyColumn {
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
            TextButton(onClick = onDismiss) { Text("Close") }
        }
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(16.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Favorites Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .bouncyClick { showFavoritesDetail = true },
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Box(modifier = Modifier.padding(20.dp)) {
                        Column(modifier = Modifier.align(Alignment.BottomStart)) {
                            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Favorites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("${state.favoriteTracks.size} tracks", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Recent Card or Create Playlist Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .bouncyClick(onClick = onCreatePlaylist),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    tonalElevation = 1.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("New Playlist", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
            Text("Your Playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
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
            onDeletePlaylist = {}, // Cannot delete favorites list itself
            onAddTrack = { viewModel.toggleFavorite(it) },
            onRemoveTrack = { uri -> state.favoriteTracks.find { it.uri == uri }?.let { viewModel.toggleFavorite(it) } },
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerView(
    state: MusicUiState,
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
    onToggleRotation: () -> Unit
) {
    val track = state.currentTrack ?: return
    var showSleepTimer by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "artRotation"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.size(32.dp))
                }
                Text("Now Playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                
                Row {
                    IconButton(onClick = { /* Queue placeholder */ }) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Shape: Circular") },
                                onClick = { onSetArtShape("CIRCLE"); showOverflowMenu = false },
                                leadingIcon = { RadioButton(selected = state.artShape == "CIRCLE", onClick = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Shape: Square") },
                                onClick = { onSetArtShape("SQUARE"); showOverflowMenu = false },
                                leadingIcon = { RadioButton(selected = state.artShape == "SQUARE", onClick = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Rotation") },
                                onClick = { onToggleRotation(); showOverflowMenu = false },
                                leadingIcon = { Switch(checked = state.rotationEnabled, onCheckedChange = null, modifier = Modifier.scale(0.7f)) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Equalizer") },
                                onClick = {
                                    showOverflowMenu = false
                                    val intent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0)
                                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {}
                                },
                                leadingIcon = { Icon(Icons.Rounded.Equalizer, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sleep Timer") },
                                onClick = {
                                    showOverflowMenu = false
                                    showSleepTimer = true
                                },
                                leadingIcon = { Icon(Icons.Rounded.Timer, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Stop Playback", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showOverflowMenu = false
                                    onStop()
                                },
                                leadingIcon = { Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Box(contentAlignment = Alignment.Center) {
                val shape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(48.dp)
                Surface(
                    modifier = Modifier
                        .size(300.dp)
                        .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f)
                        .clip(shape)
                        .graphicsLayer {
                            shadowElevation = 20.dp.toPx()
                            this.shape = shape
                            clip = true
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 12.dp
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

            Spacer(Modifier.height(48.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(1f))

            WavySlider(
                value = state.progress.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..(state.duration.toFloat().coerceAtLeast(1f)),
                isPlaying = state.isPlaying
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(state.progress), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatDuration(state.duration), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onSkipPrev,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(36.dp))
                    }
                }

                Surface(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                            null, 
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Surface(
                    onClick = onSkipNext,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(36.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        if (state.isShuffleOn) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle, 
                        null, 
                        tint = if (state.isShuffleOn) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        when(state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn
                            Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn
                            else -> Icons.Rounded.Repeat
                        },
                        null,
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = { onToggleFavorite(track) }) {
                    val favScale by animateFloatAsState(if (track.isFavorite) 1.3f else 1f)
                    Icon(
                        if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (track.isFavorite) Color.Red else LocalContentColor.current.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp).scale(favScale)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showSleepTimer) {
        SleepTimerDialog(onDismiss = { showSleepTimer = false }, onSet = onSetSleepTimer)
    }
}

@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 10.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2
            val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
            val progressX = width * progress
            
            // Draw Inactive Track
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = androidx.compose.ui.geometry.Offset(progressX, centerY),
                end = androidx.compose.ui.geometry.Offset(width, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Draw Wavy Active Track
            val path = Path()
            path.moveTo(0f, centerY)
            val segments = 100
            for (i in 0..segments) {
                val x = (i.toFloat() / segments) * progressX
                val relativeX = (i.toFloat() / segments) * 10f // frequency
                val y = centerY + sin(relativeX - (if (isPlaying) phase else 0f)).toFloat() * 8.dp.toPx()
                path.lineTo(x, y)
            }
            
            drawPath(
                path = path,
                color = Color(0xFFE91E63),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit, onSet: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { mins ->
                    ListItem(
                        headlineContent = { Text("$mins Minutes") },
                        modifier = Modifier.clickable { onSet(mins); onDismiss() },
                        leadingContent = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
                ListItem(
                    headlineContent = { Text("Turn Off", color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onSet(null); onDismiss() },
                    leadingContent = { Icon(Icons.Rounded.TimerOff, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
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

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.9f)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(130.dp), 
                    shape = RoundedCornerShape(28.dp), 
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    if (playlist.thumbnailUri != null) {
                        AsyncImage(model = playlist.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${playlistTracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
                if (isEditable) {
                    IconButton(onClick = { onDeletePlaylist(playlist) }) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            
            Row(modifier = Modifier.padding(vertical = 24.dp)) {
                Button(
                    onClick = { onPlayPlaylist(playlist) }, 
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play All")
                }
                if (isEditable) {
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconButton(
                        onClick = { showAddTrack = true },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null)
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(playlistTracks) { track ->
                    TrackItem(
                        track = track, 
                        isCurrent = false, 
                        onClick = { onPlayTrack(track, playlistTracks) }, 
                        onDelete = { onRemoveTrack(track.uri) },
                        onAddToPlaylist = {},
                        onToggleFavorite = { /* Handled elsewhere if needed */ },
                        playlists = emptyList<Playlist>(),
                        deleteLabel = if (isEditable) "Remove from Playlist" else "Remove from Favorites"
                    )
                }
            }
        }
    }

    if (showAddTrack) {
        AlertDialog(
            onDismissRequest = { showAddTrack = false },
            title = { Text("Add Track to ${playlist.name}") },
            text = {
                val availableTracks = allTracks.filter { it.uri !in playlist.trackUris }
                if (availableTracks.isEmpty()) {
                    Text("No more tracks to add.")
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp)) {
                        items(availableTracks) { track ->
                            ListItem(
                                headlineContent = { Text(track.title) },
                                supportingContent = { Text(track.artist ?: "Unknown") },
                                modifier = Modifier.clickable { onAddTrack(track); showAddTrack = false }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTrack = false }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onLongClick: () -> Unit, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (playlist.thumbnailUri != null) {
                AsyncImage(
                    model = playlist.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.4f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                            )
                        )
                )
            }
            
            // Play Button on Card
            Surface(
                onClick = onPlay,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Column(
                modifier = Modifier.padding(20.dp).align(Alignment.BottomStart)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic, 
                            null, 
                            modifier = Modifier.size(24.dp), 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    playlist.name, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Black, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.trackUris.size} tracks", 
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(8.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp
            ) {
                AsyncImage(
                    model = track.thumbnailUri, 
                    contentDescription = null, 
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(android.R.drawable.ic_media_play)
                )
                if (isSelected) {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title, 
                    fontWeight = FontWeight.Bold, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis, 
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    track.artist ?: "Unknown Artist", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                    Icon(
                        if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(onClick = { showMenu = true }) { 
                    Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp)) 
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") }, 
                        onClick = { showPlaylistPicker = true; showMenu = false }, 
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(deleteLabel) }, 
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
            title = { Text("Add to Playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists created yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                modifier = Modifier.clickable { onAddToPlaylist(playlist); showPlaylistPicker = false },
                                leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Cancel") }
            }
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
    val progressPercent = if (duration > 0) progress.toFloat() / duration else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .bouncyClick(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Progress background
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressPercent)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp
                ) {
                    AsyncImage(
                        model = track.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(android.R.drawable.ic_media_play)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Thin progress line at bottom
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist", fontWeight = FontWeight.Bold) },
        text = { 
            OutlinedTextField(
                value = name, 
                onValueChange = { name = it }, 
                label = { Text("Playlist Name") }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            ) 
        },
        confirmButton = { 
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Create") } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("Cancel") } 
        }
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
        title = { Text("Add Selected to Playlist", fontWeight = FontWeight.Bold) },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists created yet.")
            } else {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            modifier = Modifier.clickable { onPlaylistSelected(playlist) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text("Cancel") }
        }
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
