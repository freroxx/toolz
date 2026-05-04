package com.frerox.toolz.ui.screens.media

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun KaraokeHistoryTab(
    viewModel: MusicPlayerViewModel,
    musicState: MusicUiState,
    onStartKaraoke: (com.frerox.toolz.data.music.MusicTrack) -> Unit
) {
    val context = LocalContext.current
    val historyFolder = remember { context.getExternalFilesDir(null) }
    var recordings by remember { mutableStateOf<List<File>>(emptyList()) }
    var showSongPicker by remember { mutableStateOf(false) }

    fun loadRecordings() {
        recordings = historyFolder
            ?.listFiles { file ->
                (file.extension == "m4a" && file.name.contains("recording", ignoreCase = true))
            }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    LaunchedEffect(Unit) { loadRecordings() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            HistoryHeader(
                recordingCount = recordings.size,
                onRefresh = { loadRecordings() }
            )

            StartKaraokeButton(onClick = { showSongPicker = true })

            Spacer(Modifier.height(8.dp))

            if (recordings.isEmpty()) {
                HistoryEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 160.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recordings, key = { it.absolutePath }) { file ->
                        RecordingItem(
                            file = file,
                            onPlay = {
                                viewModel.playUri(
                                    uri = Uri.fromFile(file),
                                    title = file.nameWithoutExtension,
                                    artist = "Karaoke Recording"
                                )
                            },
                            onDelete = {
                                file.delete()
                                loadRecordings()
                            }
                        )
                    }
                }
            }
        }

        if (showSongPicker) {
            SongPickerDialog(
                allTracks = musicState.tracks,
                onTrackSelected = { track ->
                    showSongPicker = false
                    onStartKaraoke(track)
                },
                onDismiss = { showSongPicker = false }
            )
        }
    }
}

@Composable
private fun HistoryHeader(recordingCount: Int, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                "KARAOKE HISTORY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (recordingCount == 0) "No recordings yet"
                else "$recordingCount recording${if (recordingCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    CircleShape
                )
                .size(40.dp)
        ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun StartKaraokeButton(onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "ctaPulse")
    val gradientShift by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "gradShift"
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(72.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            primary,
                            androidx.compose.ui.graphics.lerp(primary, secondary, 0.6f),
                            secondary.copy(alpha = 0.85f)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "START KARAOKE",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.5.sp,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Pick a song and start singing",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(bottom = 80.dp)
        ) {
            val inf = rememberInfiniteTransition(label = "emptyPulse")
            val alpha by inf.animateFloat(
                0.3f, 0.7f,
                infiniteRepeatable(tween(1400), RepeatMode.Reverse),
                label = "emptyAlpha"
            )
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MicNone,
                        null,
                        modifier = Modifier.size(44.dp).alpha(alpha),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                "No recordings yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                "Sing in Karaoke Mode with recording\nenabled to save your performances",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RecordingItem(
    file: File,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
    val dateString = remember(file) { dateFormat.format(Date(file.lastModified())) }
    val fileSizeKb = remember(file) { (file.length() / 1024f).roundToInt() }
    val sizeLabel = if (fileSizeKb >= 1024) "${fileSizeKb / 1024} MB" else "$fileSizeKb KB"

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        onClick = onPlay,
        tonalElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.nameWithoutExtension,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            dateString,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                        Text(
                            sizeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    }
                }

                IconButton(
                    onClick = { showDeleteConfirm = !showDeleteConfirm }
                ) {
                    Icon(
                        if (showDeleteConfirm) Icons.Rounded.ExpandLess else Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = if (showDeleteConfirm) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }

            AnimatedVisibility(
                visible = showDeleteConfirm,
                enter = expandVertically(spring()) + fadeIn(tween(200)),
                exit = shrinkVertically(spring()) + fadeOut(tween(150))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Delete this recording?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Delete", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerDialog(
    allTracks: List<com.frerox.toolz.data.music.MusicTrack>,
    onTrackSelected: (com.frerox.toolz.data.music.MusicTrack) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val filtered = remember(query, allTracks) {
        if (query.isBlank()) allTracks
        else allTracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                    (it.artist?.contains(query, ignoreCase = true) == true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Choose a Song",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "${allTracks.size} songs in your library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Search songs or artists…",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Clear, null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )
                }

                HorizontalDivider(modifier = Modifier.alpha(0.08f))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SearchOff,
                                null,
                                modifier = Modifier.size(44.dp).alpha(0.3f),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "No songs found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filtered, key = { it.uri }) { track ->
                            SongPickerItem(
                                track = track,
                                onClick = { onTrackSelected(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongPickerItem(
    track: com.frerox.toolz.data.music.MusicTrack,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "itemScale"
    )

    Surface(
        onClick = {
            pressed = true
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.padding(start = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Sing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
