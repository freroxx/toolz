package com.frerox.toolz.ui.screens.media

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.frerox.toolz.ui.components.fadingEdge
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
        if (searchQuery.isEmpty()) state.tracks
        else state.tracks.filter { 
            it.title.contains(searchQuery, ignoreCase = true) || 
            (it.artist?.contains(searchQuery, ignoreCase = true) == true) 
        }
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
                            title = { Text("${state.selectedTracks.size} Selected", fontWeight = FontWeight.Black) },
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
                            title = { Text("MUSIC PLAYER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
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
                                            text = { Text("By Title", fontWeight = FontWeight.Bold) },
                                            onClick = { viewModel.setSortOrder(SortOrder.TITLE); showSortMenu = false },
                                            leadingIcon = { Icon(Icons.Rounded.Title, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("By Artist", fontWeight = FontWeight.Bold) },
                                            onClick = { viewModel.setSortOrder(SortOrder.ARTIST); showSortMenu = false },
                                            leadingIcon = { Icon(Icons.Rounded.Person, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("By Recent", fontWeight = FontWeight.Bold) },
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
                        placeholder = { Text("Search your music...") },
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
                            progress = state.progress,
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
                                text = { Text(label, fontWeight = if (currentTab == index) FontWeight.Black else FontWeight.Bold, letterSpacing = 0.5.sp) },
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
                        Text("Permission Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            "To access and play your music library, Toolz needs storage permissions.", 
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
                            Text("GRANT ACCESS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
                }
            } else if (state.isLoading && state.tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeCap = StrokeCap.Round, modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                        Spacer(Modifier.height(24.dp))
                        Text("SYNCHRONIZING...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
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
                        Text("NO MUSIC FOUND", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text("Scan your device to locate audio files.", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.scanMusic() }, shape = RoundedCornerShape(20.dp)) {
                            Text("REFRESH LIBRARY", fontWeight = FontWeight.Black)
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
                    Text("No folders with music.", color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
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
                folderName, 
                fontWeight = FontWeight.Black, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "$trackCount TRACKS", 
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
        title = { Text(folderName.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
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
            Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) { Text("DONE", fontWeight = FontWeight.Black) }
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
            Text("QUICK ACCESS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
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
                            Text("Favorites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("${state.favoriteTracks.size} Items", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
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
                            Text("New List", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(44.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("RECENTLY PLAYED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { showRecentDetail = true }) { Text("VIEW ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
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
                Text("MOST PLAYED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { showMostPlayedDetail = true }) { Text("VIEW ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) }
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
            Text("MY COLLECTIONS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
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
    val screenHeight = configuration.screenHeightDp.dp
    
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
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
                    "PLAYING NOW", 
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
                            text = { Text("Visuals: Circular", fontWeight = FontWeight.Bold) },
                            onClick = { onSetArtShape("CIRCLE"); showOverflowMenu = false },
                            leadingIcon = { RadioButton(selected = state.artShape == "CIRCLE", onClick = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Visuals: Square", fontWeight = FontWeight.Bold) },
                            onClick = { onSetArtShape("SQUARE"); showOverflowMenu = false },
                            leadingIcon = { RadioButton(selected = state.artShape == "SQUARE", onClick = null) }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp).alpha(0.1f))
                        DropdownMenuItem(
                            text = { Text("Art Rotation", fontWeight = FontWeight.Bold) },
                            onClick = { onToggleRotation(); showOverflowMenu = false },
                            leadingIcon = { Switch(checked = state.rotationEnabled, onCheckedChange = null, modifier = Modifier.scale(0.7f)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
                            onClick = { showOverflowMenu = false; showSleepTimer = true },
                            leadingIcon = { Icon(Icons.Rounded.Timer, null) }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp).alpha(0.1f))
                        DropdownMenuItem(
                            text = { Text("Stop & Exit", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black) },
                            onClick = { showOverflowMenu = false; onStop() },
                            leadingIcon = { Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(screenHeight * 0.04f))

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

            Spacer(Modifier.height(48.dp))

            // Track Information
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    track.artist?.uppercase() ?: "UNKNOWN ARTIST",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(44.dp))

            // Premium Progress Slider
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                WavySlider(
                    value = state.progress.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..(state.duration.toFloat().coerceAtLeast(1f)),
                    isPlaying = state.isPlaying
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(state.progress),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        formatDuration(state.duration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(44.dp))

            // Main Controls (Enhanced)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onSkipPrev,
                    modifier = Modifier.size(76.dp).bouncyClick {},
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(44.dp))
                    }
                }

                Surface(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(116.dp).bouncyClick {},
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 16.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                            null, 
                            modifier = Modifier.size(68.dp)
                        )
                    }
                }

                Surface(
                    onClick = onSkipNext,
                    modifier = Modifier.size(76.dp).bouncyClick {},
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(44.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))

            // Secondary Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle, modifier = Modifier.bouncyClick {}) {
                    Icon(
                        if (state.isShuffleOn) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle, 
                        null, 
                        tint = if (state.isShuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { onToggleFavorite(track) }, modifier = Modifier.bouncyClick {}) {
                    val favScale by animateFloatAsState(if (track.isFavorite) 1.25f else 1f)
                    Icon(
                        if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(40.dp)
                            .scale(favScale)
                    )
                }

                IconButton(onClick = onToggleRepeat, modifier = Modifier.bouncyClick {}) {
                    Icon(
                        when(state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOneOn
                            Player.REPEAT_MODE_ALL -> Icons.Rounded.RepeatOn
                            else -> Icons.Rounded.Repeat
                        },
                        null,
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(60.dp))
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
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    
    val animatedValue by animateFloatAsState(
        targetValue = value,
        animationSpec = if (isDragged) snap() else tween(1000, easing = LinearEasing),
        label = "smoothProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackHeight = 12.dp

    Box(modifier = Modifier
        .fillMaxWidth()
        .height(64.dp), contentAlignment = Alignment.Center) {
        
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .padding(horizontal = 4.dp)) {
            val width = size.width
            val centerY = size.height / 2
            val progress = (animatedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
            val progressX = width * progress
            
            // Inactive Track
            drawLine(
                color = primaryColor.copy(alpha = 0.1f),
                start = Offset(progressX, centerY),
                end = Offset(width, centerY),
                strokeWidth = trackHeight.toPx(),
                cap = StrokeCap.Round
            )
            
            // Wavy Active Track
            val path = Path()
            path.moveTo(0f, centerY)
            val segments = (width / 2.5).toInt().coerceAtLeast(80)
            for (i in 0..segments) {
                val x = (i.toFloat() / segments) * progressX
                if (x > progressX) break
                
                val frequency = if (isPlaying) 20f else 10f
                val relativeX = (i.toFloat() / segments) * frequency
                
                val baseAmplitude = if (isPlaying) 12.dp.toPx() else 1.5.dp.toPx()
                val dragAmplitude = if (isDragged) 8.dp.toPx() else 0f
                val y = centerY + sin(relativeX - (if (isPlaying) phase else 0f)).toFloat() * (baseAmplitude + dragAmplitude)
                
                path.lineTo(x, y)
            }
            
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = (if (isDragged) 10.dp else 8.dp).toPx(), cap = StrokeCap.Round)
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = primaryColor,
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
        title = { Text("SLEEP TIMER", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
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
                            Text("$mins Minutes", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { onSet(null); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CANCEL TIMER", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
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
                    Text(playlist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("${playlistTracks.size} Tracks Indexed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
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
                    Text("SHUFFLE PLAY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
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
            title = { Text("SELECT TRACKS", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                val availableTracks = allTracks.filter { it.uri !in playlist.trackUris }
                if (availableTracks.isEmpty()) {
                    Text("All tracks already added.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                                        Text(track.title, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist ?: "Unknown Artist", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTrack = false }) { Text("DISMISS", fontWeight = FontWeight.Black) }
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
                    playlist.name, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Black, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.trackUris.size} TRACKS", 
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
                    track.title, 
                    fontWeight = FontWeight.Black, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis, 
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    track.artist?.uppercase() ?: "UNKNOWN ARTIST", 
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
                        text = { Text("Add to Playlist", fontWeight = FontWeight.Bold) }, 
                        onClick = { showPlaylistPicker = true; showMenu = false }, 
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(deleteLabel, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error) }, 
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
            title = { Text("SELECT PLAYLIST", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                if (playlists.isEmpty()) {
                    Text("No playlists available.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                                    Text(playlist.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("CANCEL", fontWeight = FontWeight.Black) }
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
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist?.uppercase() ?: "UNKNOWN ARTIST",
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
        title = { Text("NEW COLLECTION", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = { 
            OutlinedTextField(
                value = name, 
                onValueChange = { name = it }, 
                label = { Text("Playlist Name") }, 
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
            ) { Text("CREATE", fontWeight = FontWeight.Black) } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("DISMISS", fontWeight = FontWeight.Bold) } 
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
        title = { Text("ADD TO LIST...", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
        text = {
            if (playlists.isEmpty()) {
                Text("No collections found.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                                Text(playlist.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text("CANCEL", fontWeight = FontWeight.Black) }
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
