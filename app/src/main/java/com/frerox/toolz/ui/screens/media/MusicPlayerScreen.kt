package com.frerox.toolz.ui.screens.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.ui.components.bouncyClick
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerScreen(
    viewModel: MusicPlayerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addTracks(uris)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music Player", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                    }
                    IconButton(onClick = { filePickerLauncher.launch("audio/*") }) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = "Add Music")
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
                }
            )
        },
        bottomBar = {
            if (state.currentTrack != null && !showFullPlayer) {
                MiniPlayer(
                    track = state.currentTrack!!,
                    isPlaying = state.isPlaying,
                    onTogglePlay = { viewModel.togglePlayPause() },
                    onClick = { showFullPlayer = true }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (state.isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                }
            }

            if (state.tracks.isEmpty() && !state.isLoading) {
                item {
                    EmptyMusicState(onAddMusic = { filePickerLauncher.launch("audio/*") })
                }
            } else {
                // Now Playing Card (Modern Music Card)
                if (state.currentTrack != null) {
                    item {
                        NowPlayingCard(
                            track = state.currentTrack!!,
                            isPlaying = state.isPlaying,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onStop = { viewModel.stop() },
                            onClick = { showFullPlayer = true }
                        )
                    }
                }

                // Playlists Section
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Playlists",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showPlaylistDialog = true }) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "New Playlist")
                            }
                        }
                        
                        if (state.playlists.isEmpty()) {
                            Text(
                                "No playlists created",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(state.playlists) { playlist ->
                                    PlaylistCard(playlist = playlist, onClick = { viewModel.playPlaylist(playlist) })
                                }
                            }
                        }
                    }
                }

                // Tracks Section
                item {
                    Text(
                        "All Tracks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                items(state.tracks) { track ->
                    TrackItem(
                        track = track,
                        isCurrent = track.uri == state.currentTrack?.uri,
                        onClick = { viewModel.playTrack(track) },
                        onDelete = { viewModel.deleteTrack(track) }
                    )
                }
            }
        }

        if (showPlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showPlaylistDialog = false },
                onCreate = { name ->
                    viewModel.createPlaylist(name, emptyList())
                    showPlaylistDialog = false
                }
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
                }
            )
        }
    }
}

@Composable
fun NowPlayingCard(
    track: MusicTrack,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(180.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (track.thumbnailUri != null) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.3f)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ) {
                    if (track.thumbnailUri != null) {
                        AsyncImage(
                            model = track.thumbnailUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.padding(24.dp))
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.artist ?: "Unknown Artist", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = onTogglePlay, modifier = Modifier.size(48.dp)) {
                            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onStop) {
                            Icon(Icons.Rounded.Stop, null)
                        }
                    }
                }
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
    onStop: () -> Unit
) {
    val track = state.currentTrack ?: return
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
                }
                Text("Now Playing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop")
                }
            }

            Spacer(Modifier.height(32.dp))

            Surface(
                modifier = Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(32.dp)),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp
            ) {
                if (track.thumbnailUri != null) {
                    AsyncImage(
                        model = track.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(48.dp))

            Slider(
                value = state.progress.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..(state.duration.toFloat().coerceAtLeast(1f)),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(state.progress), style = MaterialTheme.typography.labelSmall)
                Text(formatDuration(state.duration), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.isShuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onSkipPrev, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }

                FilledTonalIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(48.dp)
                    )
                }

                IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                            Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(120.dp).bouncyClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer
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
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TrackItem(track: MusicTrack, isCurrent: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUri != null) {
                    AsyncImage(
                        model = track.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isCurrent) {
                Icon(
                    Icons.Rounded.GraphicEq, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) }
                    )
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: MusicTrack,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .bouncyClick(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (track.thumbnailUri != null) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null)
                }
            }
            
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist ?: "Unknown", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                    contentDescription = null,
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
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name) }, shape = RoundedCornerShape(12.dp)) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyMusicState(onAddMusic: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.LibraryMusic,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(16.dp))
            Text("Your library is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAddMusic, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import Tracks")
            }
        }
    }
}
