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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
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
                    
                    DropdownMenu(
                        expanded = showSortMenu, 
                        onDismissRequest = { showSortMenu = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
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
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                modifier = Modifier.shadow(16.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
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
                                text = { Text(label, fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal) },
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
                length = 20.dp
            )
        ) {
            if (!musicPermission.status.isGranted || !isManageStorageGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.FolderSpecial, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        Spacer(Modifier.height(24.dp))
                        Text("Permission Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "To show all your music files, Toolz needs full file access permission.", 
                            textAlign = TextAlign.Center, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(32.dp))
                        
                        if (!musicPermission.status.isGranted) {
                            Button(
                                onClick = { musicPermission.launchPermissionRequest() },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
                            ) {
                                Text("Grant Media Access", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        
                        if (!isManageStorageGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    manageStorageLauncher.launch(intent)
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
                            ) {
                                Text("Grant All Files Access", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (state.isLoading && state.tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeCap = StrokeCap.Round, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(20.dp))
                        Text("Syncing Library...", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(500, easing = EaseInOutQuart)) + scaleIn(initialScale = 0.92f, animationSpec = tween(500, easing = EaseInOutQuart))) togetherWith 
                        (fadeOut(animationSpec = tween(500, easing = EaseInOutQuart)) + scaleOut(targetScale = 0.92f, animationSpec = tween(500, easing = EaseInOutQuart)))
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
        contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (tracks.isEmpty() && !state.isLoading) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.MusicOff, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(Modifier.height(16.dp))
                        Text("Library is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Add music or scan your device.", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.scanMusic() }) {
                            Text("Refresh Now")
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
        contentPadding = PaddingValues(bottom = 180.dp, top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (state.folders.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No music folders found.", color = MaterialTheme.colorScheme.outline)
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
            .height(180.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                        ), 
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Folder, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
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
                "$trackCount tracks", 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
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
        title = { Text(folderName, fontWeight = FontWeight.ExtraBold) },
        text = {
            Box(modifier = Modifier.height(450.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Done") }
        },
        shape = RoundedCornerShape(32.dp),
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp), 
        contentPadding = PaddingValues(bottom = 180.dp)
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("Your Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(20.dp))
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
                        .height(200.dp)
                        .bouncyClick { showFavoritesDetail = true },
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 4.dp
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        Column(modifier = Modifier.align(Alignment.BottomStart)) {
                            Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Favorites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("${state.favoriteTracks.size} items", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Create Playlist Card
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp)
                        .bouncyClick(onClick = onCreatePlaylist),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("New Playlist", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
            Text("Playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
            onDeletePlaylist = {},
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
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
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
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
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
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.size(36.dp))
                }
                Text(
                    "Now Playing", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center)
                )
                
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Visuals: Circular") },
                            onClick = { onSetArtShape("CIRCLE"); showOverflowMenu = false },
                            leadingIcon = { RadioButton(selected = state.artShape == "CIRCLE", onClick = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Visuals: Square") },
                            onClick = { onSetArtShape("SQUARE"); showOverflowMenu = false },
                            leadingIcon = { RadioButton(selected = state.artShape == "SQUARE", onClick = null) }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Art Rotation") },
                            onClick = { onToggleRotation(); showOverflowMenu = false },
                            leadingIcon = { Switch(checked = state.rotationEnabled, onCheckedChange = null, modifier = Modifier.scale(0.75f)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep Timer") },
                            onClick = { showOverflowMenu = false; showSleepTimer = true },
                            leadingIcon = { Icon(Icons.Rounded.Timer, null) }
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Stop & Close", color = MaterialTheme.colorScheme.error) },
                            onClick = { showOverflowMenu = false; onStop() },
                            leadingIcon = { Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(screenHeight * 0.04f))

            // Enhanced Album Art
            val artSize = (configuration.screenWidthDp * 0.85f).dp
            val shape = if (state.artShape == "CIRCLE") CircleShape else RoundedCornerShape(56.dp)
            Surface(
                modifier = Modifier
                    .size(artSize)
                    .rotate(if (state.isPlaying && state.rotationEnabled) rotation else 0f)
                    .shadow(48.dp, shape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(40.dp))

            // Redesigned Progress Section
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                WavySlider(
                    value = state.progress.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..(state.duration.toFloat().coerceAtLeast(1f)),
                    isPlaying = state.isPlaying
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(state.progress),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        formatDuration(state.duration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSkipPrev, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(52.dp))
                }

                Surface(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 12.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                            null, 
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                IconButton(onClick = onSkipNext, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(52.dp))
                }
            }
            
            Spacer(Modifier.height(40.dp))

            // Secondary Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        if (state.isShuffleOn) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle, 
                        null, 
                        tint = if (state.isShuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
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
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { onToggleFavorite(track) }) {
                    val favScale by animateFloatAsState(if (track.isFavorite) 1.25f else 1f)
                    Icon(
                        if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(36.dp)
                            .scale(favScale)
                    )
                }
            }
            
            Spacer(Modifier.height(56.dp))
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
    
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackHeight = 12.dp

    Box(modifier = Modifier
        .fillMaxWidth()
        .height(64.dp), contentAlignment = Alignment.Center) {
        
        // Background track with Canvas
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .padding(horizontal = 2.dp)) {
            val width = size.width
            val centerY = size.height / 2
            val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
            val progressX = width * progress
            
            // Inactive Track
            drawLine(
                color = primaryColor.copy(alpha = 0.15f),
                start = Offset(progressX, centerY),
                end = Offset(width, centerY),
                strokeWidth = trackHeight.toPx(),
                cap = StrokeCap.Round
            )
            
            // Wavy Active Track
            val path = Path()
            path.moveTo(0f, centerY)
            val segments = (width / 4).toInt().coerceAtLeast(50)
            for (i in 0..segments) {
                val x = (i.toFloat() / segments) * progressX
                if (x > progressX) break
                
                // Frequency increases when playing
                val frequency = if (isPlaying) 15f else 10f
                val relativeX = (i.toFloat() / segments) * frequency
                
                // Amplitude increases when playing or dragged
                val baseAmplitude = if (isPlaying) 8.dp.toPx() else 2.dp.toPx()
                val dragAmplitude = if (isDragged) 4.dp.toPx() else 0f
                val y = centerY + sin(relativeX - (if (isPlaying) phase else 0f)).toFloat() * (baseAmplitude + dragAmplitude)
                
                path.lineTo(x, y)
            }
            
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = (if (isDragged) 8.dp else 6.dp).toPx(), cap = StrokeCap.Round)
            )
        }

        // Invisible slider to handle interactions
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
        title = { Text("Set Sleep Timer", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    ListItem(
                        headlineContent = { Text("$mins Minutes", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSet(mins); onDismiss() },
                        leadingContent = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
                ListItem(
                    headlineContent = { Text("Cancel Timer", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSet(null); onDismiss() },
                    leadingContent = { Icon(Icons.Rounded.TimerOff, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        shape = RoundedCornerShape(32.dp)
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
        modifier = Modifier.fillMaxHeight(0.92f),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outlineVariant) }
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(130.dp), 
                    shape = RoundedCornerShape(32.dp), 
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 12.dp
                ) {
                    if (playlist.thumbnailUri != null) {
                        AsyncImage(model = playlist.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop)
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("${playlistTracks.size} tracks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (isEditable) {
                    IconButton(
                        onClick = { onDeletePlaylist(playlist) },
                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
                    ) { 
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) 
                    }
                }
            }
            
            Row(modifier = Modifier.padding(vertical = 24.dp)) {
                Button(
                    onClick = { onPlayPlaylist(playlist) }, 
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Shuffle Play", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                if (isEditable) {
                    Spacer(Modifier.width(16.dp))
                    FilledTonalIconButton(
                        onClick = { showAddTrack = true },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(28.dp))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f), 
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(playlistTracks) { track ->
                    TrackItem(
                        track = track, 
                        isCurrent = false, 
                        onClick = { onPlayTrack(track, playlistTracks) }, 
                        onDelete = { onRemoveTrack(track.uri) },
                        onAddToPlaylist = {},
                        onToggleFavorite = { /* viewModel.toggleFavorite(track) */ },
                        playlists = emptyList(),
                        deleteLabel = if (isEditable) "Remove from Playlist" else "Remove from Favorites"
                    )
                }
            }
        }
    }

    if (showAddTrack) {
        AlertDialog(
            onDismissRequest = { showAddTrack = false },
            title = { Text("Add items to ${playlist.name}", fontWeight = FontWeight.ExtraBold) },
            text = {
                val availableTracks = allTracks.filter { it.uri !in playlist.trackUris }
                if (availableTracks.isEmpty()) {
                    Text("No new tracks found to add.")
                } else {
                    LazyColumn(modifier = Modifier.height(400.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(availableTracks) { track ->
                            ListItem(
                                headlineContent = { Text(track.title, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(track.artist ?: "Unknown Artist") },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onAddTrack(track); showAddTrack = false },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTrack = false }) { Text("Cancel", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(32.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onLongClick: () -> Unit, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (playlist.thumbnailUri != null) {
                AsyncImage(
                    model = playlist.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.65f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), MaterialTheme.colorScheme.surface)
                            )
                        )
                )
            }
            
            // Floating Play Button
            Surface(
                onClick = onPlay,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                }
            }

            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.BottomStart)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic, 
                            null, 
                            modifier = Modifier.size(28.dp), 
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
                    "${playlist.trackUris.size} tracks recorded", 
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
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
            .padding(horizontal = 12.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(60.dp),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 2.dp,
                tonalElevation = 4.dp
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title, 
                    fontWeight = FontWeight.ExtraBold, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis, 
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    track.artist ?: "Unknown Artist", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
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
                    val favColor = if (track.isFavorite) Color.Red else MaterialTheme.colorScheme.outline
                    Icon(favIcon, null, tint = favColor, modifier = Modifier.size(24.dp))
                }
                
                IconButton(onClick = { showMenu = true }) { 
                    Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.outline) 
                }
                DropdownMenu(
                    expanded = showMenu, 
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", fontWeight = FontWeight.Medium) }, 
                        onClick = { showPlaylistPicker = true; showMenu = false }, 
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(deleteLabel, fontWeight = FontWeight.Medium) }, 
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
            title = { Text("Select Playlist", fontWeight = FontWeight.ExtraBold) },
            text = {
                if (playlists.isEmpty()) {
                    Text("Create a playlist first to add tracks.")
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name, fontWeight = FontWeight.Bold) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onAddToPlaylist(playlist); showPlaylistPicker = false },
                                leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Cancel", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(32.dp)
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
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .height(96.dp)
            .shadow(24.dp, RoundedCornerShape(32.dp))
            .bouncyClick(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 16.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressPercent)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 6.dp,
                    tonalElevation = 4.dp
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
                        track.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Toggle Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
        title = { Text("New Collection", fontWeight = FontWeight.ExtraBold) },
        text = { 
            OutlinedTextField(
                value = name, 
                onValueChange = { name = it }, 
                label = { Text("Playlist Name") }, 
                modifier = Modifier.fillMaxWidth(), 
                shape = RoundedCornerShape(20.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) 
        },
        confirmButton = { 
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(48.dp)
            ) { Text("Create", fontWeight = FontWeight.Bold) } 
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { Text("Cancel", fontWeight = FontWeight.Bold) } 
        },
        shape = RoundedCornerShape(36.dp),
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
        title = { Text("Add Selection to...", fontWeight = FontWeight.ExtraBold) },
        text = {
            if (playlists.isEmpty()) {
                Text("You don't have any playlists yet.")
            } else {
                LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name, fontWeight = FontWeight.Bold) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onPlaylistSelected(playlist) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) { Text("Cancel", fontWeight = FontWeight.Bold) }
        },
        shape = RoundedCornerShape(36.dp)
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
