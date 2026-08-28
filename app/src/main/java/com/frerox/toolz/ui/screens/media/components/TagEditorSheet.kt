package com.frerox.toolz.ui.screens.media.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorSheet(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, thumbnailUri: String?, lyrics: String) -> Unit
) {
    val context = LocalContext.current
    var title by remember(track.uri) { mutableStateOf(track.title) }
    var artist by remember(track.uri) { mutableStateOf(track.artist ?: "") }
    var album by remember(track.uri) { mutableStateOf(track.album ?: "") }
    // Use aiLyrics as embedded lyrics initial value; fallback to empty
    var lyrics by remember(track.uri) { mutableStateOf(track.aiLyrics ?: "") }
    var pickedThumbUri by remember(track.uri) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val thumbPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pickedThumbUri = it.toString() }
    }

    // Detect changes
    val hasChanges = remember(title, artist, album, lyrics, pickedThumbUri) {
        title.trim() != track.title ||
                artist.trim() != (track.artist ?: "") ||
                album.trim() != (track.album ?: "") ||
                lyrics.trim() != (track.aiLyrics ?: "") ||
                pickedThumbUri != null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Edit tags",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = track.uri.substringAfterLast("/").take(40),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onDismiss, enabled = !isSaving) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Thumbnail preview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                        .clickable { thumbPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val displayThumb = pickedThumbUri ?: track.thumbnailUri
                    if (!displayThumb.isNullOrBlank()) {
                        AsyncImage(
                            model = displayThumb,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit thumbnail", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cover art", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Tap image to pick a new cover. Will be embedded into file when possible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    if (pickedThumbUri != null) {
                        AssistChip(
                            onClick = { pickedThumbUri = null },
                            label = { Text("Revert") },
                            leadingIcon = { Icon(Icons.Rounded.Undo, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                leadingIcon = { Icon(Icons.Rounded.Title, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving
            )
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Album") },
                leadingIcon = { Icon(Icons.Rounded.Album, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSaving
            )

            // Embedded lyrics
            Text("Embedded lyrics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = lyrics,
                onValueChange = { lyrics = it },
                placeholder = { Text("Add synchronized [mm:ss] or plain lyrics…") },
                leadingIcon = { Icon(Icons.Rounded.Lyrics, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp),
                maxLines = 12,
                enabled = !isSaving
            )
            Text(
                "Saved to file when possible (MP3/M4A) and always to library cache. Use [mm:ss.xx] for synced lines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Save button
            Button(
                onClick = {
                    if (!hasChanges || isSaving) return@Button
                    isSaving = true
                    onSave(
                        title.trim(),
                        artist.trim(),
                        album.trim(),
                        pickedThumbUri,
                        lyrics.trim()
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = hasChanges && !isSaving && title.isNotBlank()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text("Saving…", fontWeight = FontWeight.Black)
                } else {
                    Icon(Icons.Rounded.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save tags", fontWeight = FontWeight.Black)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
