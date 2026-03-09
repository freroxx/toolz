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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.random.Random

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
    var currentTab by remember { mutableIntStateOf(0) } // 0: Tracks, 1: Folders, 2: Playlists
    var searchQuery by remember { mutableStateOf("") }
    var showMultiSelectPlaylistPicker by remember { mutableStateOf(false) }
    var selectedFolderTracks by remember { mutableStateOf<Pair<String, List<MusicTrack>>?>(null) }

    val musicPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    LaunchedEffect(musicPermission.status.isGranted) {
        if (musicPermission.status.isGranted) {
            viewModel.scanMusic()
        }
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
                Column {
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
                                if (currentTab == 1) {
                                    IconButton(onClick = { folderLauncher.launch(null) }) {
                                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Add Folder")
                                    }
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
                        placeholder = { Text("Search your library...") },
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
                Column {
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
                        val tabs = listOf("Tracks" to Icons.Rounded.MusicNote, "Folders" to Icons.Rounded.Folder, "Playlists" to Icons.AutoMirrored.Rounded.PlaylistPlay)
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
                        1 -> FolderList(state, onFolderClick = { name, tracks -> selectedFolderTracks = name to tracks })
                        2 -> PlaylistSection(state, viewModel, 
                            onCreatePlaylist = { showPlaylistDialog = true },
                            onPlaylistClick = { selectedPlaylist = it },
                            onUpdateThumb = { 
                                selectedPlaylist = it
                                playlistThumbLauncher.launch("image/*") 
                            }
                        )
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
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
                onSetSleepTimer = { viewModel.setSleepTimer(it) }
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
        if (tracks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.MusicOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Text("No tracks found", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.titleMedium)
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
fun PlaylistSection(
    state: MusicUiState, 
    viewModel: MusicPlayerViewModel,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onUpdateThumb: (Playlist) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Playlists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            IconButton(
                onClick = onCreatePlaylist,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp), 
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            items(state.playlists) { playlist ->
                PlaylistCard(
                    playlist = playlist, 
                    onClick = { onPlaylistClick(playlist) },
                    onLongClick = { onUpdateThumb(playlist) }
                )
            }
        }
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
    onSetSleepTimer: (Int?) -> Unit
) {
    val track = state.currentTrack ?: return
    var showSleepTimer by remember { mutableStateOf(false) }
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
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
                Text("Now Playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { 
                    val intent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, 0)
                    intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                    }
                }) {
                    Icon(Icons.Rounded.Equalizer, null)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (state.showVisualizer) {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    MusicVisualizer(isPlaying = state.isPlaying, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(24.dp))
            }

            Box(contentAlignment = Alignment.Center) {
                val pulseScale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1.08f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                
                Surface(
                    modifier = Modifier.size(280.dp).scale(pulseScale).alpha(0.06f),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {}
                
                Surface(
                    modifier = Modifier
                        .size(280.dp)
                        .rotate(if (state.isPlaying) rotation else 0f)
                        .clip(CircleShape)
                        .graphicsLayer {
                            shadowElevation = 40.dp.toPx()
                            shape = CircleShape
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
                        error = painterResource(android.R.drawable.ic_menu_report_image)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.weight(1f))

            Slider(
                value = state.progress.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..(state.duration.toFloat().coerceAtLeast(1f)),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(state.progress), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(formatDuration(state.duration), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
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

                IconButton(onClick = onSkipPrev, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(44.dp))
                }

                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                        null, 
                        modifier = Modifier.size(56.dp)
                    )
                }

                IconButton(onClick = onSkipNext, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(44.dp))
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
            }
            
            Spacer(Modifier.height(40.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showSleepTimer = true }) {
                    Icon(Icons.Rounded.Timer, null, tint = if (state.sleepTimerMinutes != null) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                Spacer(Modifier.width(16.dp))
                TextButton(
                    onClick = onStop,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop", fontWeight = FontWeight.ExtraBold)
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSleepTimer) {
        SleepTimerDialog(onDismiss = { showSleepTimer = false }, onSet = onSetSleepTimer)
    }
}

@Composable
fun MusicVisualizer(isPlaying: Boolean, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    val heights = List(24) { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = if (isPlaying) Random.nextFloat() * 0.8f + 0.2f else 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = Random.nextInt(400, 800), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { heightState ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightState.value)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                        )
                    )
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
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit
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
                IconButton(onClick = { onDeletePlaylist(playlist) }) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
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
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(
                    onClick = { showAddTrack = true },
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.Add, null)
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
                        playlists = emptyList(),
                        deleteLabel = "Remove from Playlist"
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
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier.width(150.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(150.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (playlist.thumbnailUri != null) {
                    AsyncImage(
                        model = playlist.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(playlist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    playlists: List<Playlist>,
    deleteLabel: String = "Delete"
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isCurrent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant), 
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = track.thumbnailUri, 
                    contentDescription = null, 
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(android.R.drawable.ic_menu_report_image)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected, 
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            } else if (isCurrent) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.outline) }
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
fun MiniPlayer(track: MusicTrack, isPlaying: Boolean, onTogglePlay: () -> Unit, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUri, 
                contentDescription = null, 
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), 
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_report_image)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(track.artist ?: "Unknown Artist", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled, 
                    null, 
                    modifier = Modifier.size(48.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
