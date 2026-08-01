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

import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.*
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.frerox.toolz.ui.theme.toolzBackground
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class RecordingMetadata(
    val title: String,
    val artist: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val timestamp: Long,
    val score: Int = -1,
    val grade: String = ""
)

data class RecordingItem(
    val file: File?,
    val uri: Uri?,
    val name: String,
    val lastModified: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeTab(
    viewModel: MusicPlayerViewModel,
    musicState: MusicUiState,
    onStartKaraoke: (com.frerox.toolz.data.music.MusicTrack) -> Unit,
    onShowSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyFolder = remember { context.getExternalFilesDir(null) }
    var recordings by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }
    var showSongPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun getMetadata(item: RecordingItem): RecordingMetadata? {
        return try {
            val fileName = item.file?.nameWithoutExtension ?: item.name.substringBeforeLast(".")
            val parent = item.file?.parentFile ?: context.getExternalFilesDir(null)
            val metaFile = File(parent, "$fileName.json")
            if (metaFile.exists()) {
                val content = metaFile.readText()
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(RecordingMetadata::class.java)
                adapter.fromJson(content)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun loadRecordings() {
        val items = mutableListOf<RecordingItem>()

        // 1. App-specific folder (Internal)
        historyFolder?.listFiles { file ->
            val ext = file.extension.lowercase()
            (ext == "m4a" || ext == "mp3" || ext == "opus") &&
                    (file.name.contains("recording", ignoreCase = true) ||
                            file.name.contains("karaoke", ignoreCase = true) ||
                            file.name.startsWith("Karaoke -"))
        }?.forEach { file ->
            items.add(RecordingItem(file, Uri.fromFile(file), file.name, file.lastModified()))
        }

        // 2. MediaStore (Public Music/Karaoke)
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM
            )
            
            // Search by folder AND by Album name to catch all recordings
            val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.ALBUM} = ?"
            val selectionArgs = arrayOf("%Music/Karaoke%", "Toolz Karaoke Recordings")
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "unknown_recording"
                    val date = cursor.getLong(dateCol) * 1000L
                    val path = cursor.getString(dataCol) ?: continue
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // Avoid duplicates if they are already in the internal list
                    if (items.none { it.file?.absolutePath == path }) {
                        items.add(RecordingItem(File(path), uri, name, date))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        recordings = items.sortedByDescending { it.lastModified }
    }

    LaunchedEffect(Unit) { loadRecordings() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadRecordings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isRefreshing by remember { mutableStateOf(false) }

    // Update stats for the main top bar
    LaunchedEffect(recordings) {
        withContext(Dispatchers.IO) {
            val allMetadata = recordings.mapNotNull { getMetadata(it) }
            val avgScore = if (allMetadata.isNotEmpty()) {
                allMetadata.filter { it.score >= 0 }.let { scored ->
                    if (scored.isEmpty()) -1 else scored.map { it.score }.average().toInt()
                }
            } else -1
            withContext(Dispatchers.Main) {
                viewModel.updateKaraokeStats(recordings.size, avgScore)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recordings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalIconButton(
                onClick = onShowSettings,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Rounded.Settings, "Karaoke Settings", modifier = Modifier.size(18.dp))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (recordings.isEmpty()) {
                KaraokeEmptyState()
            } else {
                val refreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        loadRecordings()
                        isRefreshing = false
                    },
                    state = refreshState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().fadingEdges(top = 16.dp, bottom = 64.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 120.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recordings, key = { it.uri.toString() }) { item ->
                            val metadata = remember(item) { getMetadata(item) }

                            RecordingCard(
                                item = item,
                                metadata = metadata,
                                onPlay = {
                                    item.uri?.let { playUri ->
                                        viewModel.playUri(
                                            uri = playUri,
                                            title = metadata?.title ?: item.name.substringBeforeLast("."),
                                            artist = metadata?.artist ?: "Karaoke Recording",
                                            thumbUrl = metadata?.thumbnailUrl
                                        )
                                    }
                                },
                                    onDelete = {
                                    try {
                                        item.file?.delete()
                                        val fileName = item.file?.nameWithoutExtension ?: item.name.substringBeforeLast(".")
                                        val parent = item.file?.parentFile ?: context.getExternalFilesDir(null)
                                        val metaFile = File(parent, "$fileName.json")
                                        if (metaFile.exists()) metaFile.delete()

                                        // Also try deleting via content resolver if it's a MediaStore URI
                                        val deleteUri = item.uri
                                        if (deleteUri != null && deleteUri.toString().startsWith("content://")) {
                                            context.contentResolver.delete(deleteUri, null, null)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                    loadRecordings()
                                }
                            )
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { showSongPicker = true },
                icon = { Icon(Icons.Rounded.Mic, null) },
                text = { Text("New Session") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .bouncyClick { showSongPicker = true }
            )

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
}

@Composable
private fun KaraokeEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = ExtraLargeExpressiveShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MicExternalOn,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Text(
                "No recordings yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Start a karaoke session to record your first performance",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingCard(
    item: RecordingItem,
    metadata: RecordingMetadata?,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd · HH:mm", Locale.getDefault()) }
    val dateString = remember(item) { dateFormat.format(Date(item.lastModified)) }
    val score = metadata?.score ?: -1
    val grade = if (score >= 0) metadata?.grade?.takeIf { it.isNotEmpty() } else null

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box {
        ExpressiveCard(
            onClick = onPlay,
            onLongClick = { showMenu = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtImage(
                    url = metadata?.thumbnailUrl,
                    seed = metadata?.title ?: item.name.substringBeforeLast("."),
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                    iconSize = 24.dp
                )

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata?.title ?: item.name.substringBeforeLast("."),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = metadata?.artist ?: "Karaoke Recording",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                        Text(" · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text(
                            dateString,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (score >= 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${grade ?: ""} $score%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text("Play Again", fontWeight = FontWeight.Bold) },
                onClick = {
                    showMenu = false
                    onPlay()
                },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    showDeleteConfirm = true
                },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Recording?") },
            text = { Text("This will permanently remove this performance.") },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                ToolzOutlinedExpressiveButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            shape = ExtraLargeExpressiveShape
        )
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        "Choose a Song",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    ToolzExpressiveIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd).size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Rounded.Close, "Close", modifier = Modifier.size(18.dp))
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    placeholder = { Text("Search library…", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = "" }
                            ) {
                                Icon(Icons.Rounded.Clear, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fadingEdges(top = 20.dp, bottom = 24.dp),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
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
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtImage(
                url = track.thumbnailUri,
                seed = track.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist ?: "Unknown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        }
    }
}
