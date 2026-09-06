/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.trash.CleanerTrashEntity
import com.frerox.toolz.ui.components.cleaner.CircleSelect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerTrashSheet(
    trashEntries: List<CleanerTrashEntity>,
    trashTotalBytes: Long,
    onRestoreItem: (Long) -> Unit,
    onRestoreAll: () -> Unit,
    onRestoreSelected: ((Set<Long>) -> Unit)? = null,
    onDeleteItemPermanently: (Long) -> Unit,
    onDeleteSelectedPermanently: ((Set<Long>) -> Unit)? = null,
    onEmptyTrash: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showEmptyConfirm by remember { mutableStateOf(false) }
    var itemToDeletePermanently by remember { mutableStateOf<CleanerTrashEntity?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(trashEntries, query) {
        if (query.isBlank()) trashEntries
        else trashEntries.filter {
            it.originalPath.contains(query, ignoreCase = true)
        }
    }

    val selectedBytes = remember(selectedIds, trashEntries) {
        trashEntries.filter { it.id in selectedIds }.sumOf { it.sizeBytes }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            icon = { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error) },
            title = { Text("Empty Trash?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    "This will permanently delete all ${trashEntries.size} items and immediately reclaim ${Formatter.formatFileSize(context, trashTotalBytes)} of disk space. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEmptyConfirm = false
                        onEmptyTrash()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Empty Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${selectedIds.size} items permanently?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    "${Formatter.formatFileSize(context, selectedBytes)} will be permanently removed from disk.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedIds
                        selectedIds = emptySet()
                        showBatchDeleteConfirm = false
                        onDeleteSelectedPermanently?.invoke(ids)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Delete Selected")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    itemToDeletePermanently?.let { item ->
        val filename = item.originalPath.substringAfterLast('/')
        AlertDialog(
            onDismissRequest = { itemToDeletePermanently = null },
            icon = { Icon(Icons.Rounded.DeleteForever, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete permanently?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
            text = {
                Text(
                    "\"$filename\" (${Formatter.formatFileSize(context, item.sizeBytes)}) will be permanently deleted from storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = item.id
                        selectedIds = selectedIds - id
                        itemToDeletePermanently = null
                        onDeleteItemPermanently(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDeletePermanently = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Cleaner Trash",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    )
                    Text(
                        if (selectedIds.isNotEmpty()) {
                            "${selectedIds.size} of ${filtered.size} selected • ${Formatter.formatFileSize(context, selectedBytes)}"
                        } else if (trashEntries.isNotEmpty()) {
                            "${Formatter.formatFileSize(context, trashTotalBytes)} in trash • ${trashEntries.size} item(s)"
                        } else {
                            "0 B • Trash is empty"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(20.dp))
                }
            }

            if (trashEntries.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                null,
                                Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Trash is empty",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "When you clean files, they are safely preserved in Trash for 7 days before permanent removal so you can restore them anytime.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    FilledTonalButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Close")
                    }
                }
            } else {
                // Actions bar
                if (selectedIds.isNotEmpty()) {
                    // Batch contextual action bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showBatchDeleteConfirm = true },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Delete (${selectedIds.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                val ids = selectedIds
                                selectedIds = emptySet()
                                onRestoreSelected?.invoke(ids)
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Restore (${selectedIds.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            )
                        }

                        OutlinedButton(
                            onClick = { selectedIds = emptySet() },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                        }
                    }
                } else {
                    // Standard action bar: Empty Trash + Restore All
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showEmptyConfirm = true },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Rounded.DeleteForever, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Empty Trash",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            )
                        }

                        FilledTonalButton(
                            onClick = onRestoreAll,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Restore All",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            )
                        }
                    }
                }

                // Search field + Select All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search trash…", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Close, null, Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet()
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            if (selectedIds.isNotEmpty() && selectedIds.size == filtered.size) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                            contentDescription = "Select All",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Trash Items list
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isSel = item.id in selectedIds
                        TrashItemRow(
                            item = item,
                            isSelected = isSel,
                            onToggleSelect = {
                                selectedIds = if (isSel) selectedIds - item.id else selectedIds + item.id
                            },
                            onRestore = { onRestoreItem(item.id) },
                            onDeletePermanently = { itemToDeletePermanently = item }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashItemRow(
    item: CleanerTrashEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    val context = LocalContext.current
    val filename = item.originalPath.substringAfterLast('/')
    val parentDir = item.originalPath.substringBeforeLast('/', "")
    val ext = filename.substringAfterLast('.', "").lowercase()

    val icon = remember(item.type, ext) {
        when {
            ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic") -> Icons.Rounded.Image
            ext in setOf("mp4", "mkv", "avi", "mov", "webm") -> Icons.Rounded.VideoFile
            ext in setOf("apk", "apks", "xapk") -> Icons.Rounded.Android
            ext in setOf("mp3", "flac", "wav", "m4a", "ogg") -> Icons.Rounded.AudioFile
            item.type.contains("corpse", ignoreCase = true) -> Icons.Rounded.FolderOff
            item.type.contains("empty", ignoreCase = true) -> Icons.Rounded.Folder
            else -> Icons.Rounded.Description
        }
    }

    val daysLeft = remember(item.expiresAt) {
        val now = System.currentTimeMillis()
        val diffMs = item.expiresAt - now
        (diffMs / (24 * 60 * 60 * 1000L)).coerceAtLeast(0L).toInt()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleSelect(
                checked = isSelected,
                onToggle = onToggleSelect,
                size = 24.dp
            )

            Spacer(Modifier.width(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    filename,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    parentDir.takeLast(40),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Formatter.formatFileSize(context, item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "• $daysLeft day(s) left",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Restore action
            IconButton(
                onClick = onRestore,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Restore,
                    contentDescription = "Restore",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Delete permanently action
            IconButton(
                onClick = onDeletePermanently,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = "Delete Permanently",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
