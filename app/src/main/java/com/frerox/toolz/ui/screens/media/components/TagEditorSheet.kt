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

package com.frerox.toolz.ui.screens.media.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.ui.components.AlbumArtImage
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.screens.media.DefaultDynamicColors
import com.frerox.toolz.ui.screens.media.rememberDynamicColors
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import com.frerox.toolz.ui.theme.LocalPerformanceMode

/**
 * M3 Expressive tag editor bottom sheet.
 * Ripped chrome with LargeExpressiveShape (36dp) top corners, dynamic-color accents
 * derived from the track cover, staggered field entrances and a bouncy save button —
 * matching the rest of the player's expressive design language.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TagEditorSheet(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, thumbnailUri: String?, lyrics: String) -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    val performanceMode = LocalPerformanceMode.current
    var title by remember(track.uri) { mutableStateOf(track.title) }
    var artist by remember(track.uri) { mutableStateOf(track.artist ?: "") }
    var album by remember(track.uri) { mutableStateOf(track.album ?: "") }
    // Use aiLyrics as embedded lyrics initial value; fallback to empty
    var lyrics by remember(track.uri) { mutableStateOf(track.aiLyrics ?: "") }
    var pickedThumbUri by remember(track.uri) { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val thumbPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { picked ->
            // GetContent grants transient permission to the Activity only; the
            // Repository runs on ApplicationContext and would lose it after the
            // sheet closes, causing the cover to revert. Copy immediately via
            // the Activity's resolver to a private file and pass file:// onward.
            try {
                val tempFile = java.io.File(context.cacheDir, "tag_pick_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(picked)?.use { ins ->
                    java.io.FileOutputStream(tempFile).use { outs -> ins.copyTo(outs) }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    pickedThumbUri = Uri.fromFile(tempFile).toString()
                } else {
                    // Fallback to original content:// if copy somehow failed — still try
                    pickedThumbUri = picked.toString()
                }
            } catch (e: Exception) {
                android.util.Log.w("TagEditorSheet", "pick copy failed", e)
                pickedThumbUri = picked.toString()
            }
            haptic.tick()
        }
    }

    // Detect changes
    val hasChanges = remember(title, artist, album, lyrics, pickedThumbUri) {
        title.trim() != track.title ||
                artist.trim() != (track.artist ?: "") ||
                album.trim() != (track.album ?: "") ||
                lyrics.trim() != (track.aiLyrics ?: "") ||
                pickedThumbUri != null
    }

    // Dynamic-color accent derived from the current cover; falls back to theme
    // primary while there is no art (or while the palette is still being read).
    val isDark = LocalIsDarkTheme.current
    val dynamicColors = rememberDynamicColors(track.thumbnailUri, isDark)
    val accentTarget =
        if (!track.thumbnailUri.isNullOrBlank() && dynamicColors != DefaultDynamicColors) dynamicColors.primary
        else MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(
        targetValue = accentTarget,
        animationSpec = tween(400),
        label = "tagSheetAccent"
    )
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF111318) else Color.White

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = {
            // Styled expressive pill handle (36x4dp at ~40% onSurfaceVariant)
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ──
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
                FilledTonalIconButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // ── Cover & tags section ──
            ExpressiveSectionHeader("Cover & tags", accent = accent, modifier = Modifier.padding(top = 4.dp))
            StaggeredEntrance(index = 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                            .bouncyClick(scaleDown = 0.96f) { thumbPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        // Uniform seeded placeholder sits UNDER the image, so a missing
                        // or failed load never shows a bare/blank state.
                        AlbumArtImage(
                            url = pickedThumbUri ?: track.thumbnailUri,
                            seed = track.uri,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit thumbnail", tint = onAccent, modifier = Modifier.size(16.dp))
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
                        // Animated Revert chip — appears once a new cover is picked,
                        // drops out while saving.
                        val revertContent: @Composable () -> Unit = {
                            AssistChip(
                                onClick = {
                                    pickedThumbUri = null
                                    haptic.tick()
                                },
                                label = { Text("Revert") },
                                leadingIcon = { Icon(Icons.Rounded.Undo, null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        if (performanceMode) {
                            if (pickedThumbUri != null) revertContent()
                        } else {
                            AnimatedVisibility(
                                visible = pickedThumbUri != null,
                                enter = fadeIn(tween(220)) + expandVertically(animationSpec = tween(220)),
                                exit = fadeOut(tween(160)) + shrinkVertically(animationSpec = tween(160))
                            ) {
                                revertContent()
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            StaggeredEntrance(index = 1) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    leadingIcon = { Icon(Icons.Rounded.Title, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    enabled = !isSaving
                )
            }
            StaggeredEntrance(index = 2) {
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    enabled = !isSaving
                )
            }
            StaggeredEntrance(index = 3) {
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    leadingIcon = { Icon(Icons.Rounded.Album, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    enabled = !isSaving
                )
            }

            // ── Embedded lyrics section ──
            ExpressiveSectionHeader("Embedded lyrics", accent = accent, modifier = Modifier.padding(top = 4.dp))
            StaggeredEntrance(index = 4) {
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    placeholder = { Text("Add synchronized [mm:ss] or plain lyrics…") },
                    leadingIcon = { Icon(Icons.Rounded.Lyrics, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 12,
                    enabled = !isSaving
                )
            }
            Text(
                "Saved to file when possible (MP3/M4A) and always to library cache. Use [mm:ss.xx] for synced lines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Save button ──
            ToolzExpressiveButton(
                onClick = {
                    if (!hasChanges || isSaving) return@ToolzExpressiveButton
                    isSaving = true
                    onSave(
                        title.trim(),
                        artist.trim(),
                        album.trim(),
                        pickedThumbUri,
                        lyrics.trim()
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = hasChanges && !isSaving && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = onAccent,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = onAccent)
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

/**
 * Expressive section header — small primary-colored bold title with an accent
 * dot, so fields feel grouped the same way the rest of the player's sheets do.
 */
@Composable
private fun ExpressiveSectionHeader(
    text: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}