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

package com.frerox.toolz.ui.screens.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.theme.LocalVibrationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerDetailSheet(
    category: CleanCategory,
    onToggleItem: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val vibration = LocalVibrationManager.current
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text("${category.items.size} items • ${Formatter.formatFileSize(context, category.totalSize)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
            }
            // simple list for detail
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(category.items, key = { item ->
                    when (item) {
                        is CleanItem.GenericFile -> "f_${item.file.path}"
                        is CleanItem.Corpse -> "c_${item.entry.path}"
                        is CleanItem.Duplicate -> "d_${item.group.hash}"
                        is CleanItem.EmptyDir -> "e_${item.entry.path}"
                        is CleanItem.MediaFile -> "m_${item.entry.path}"
                        is CleanItem.ApkFile -> "a_${item.entry.path}"
                        is CleanItem.AppCache -> "ac_${item.entry.packageName}"
                        is CleanItem.UnusedApp -> "u_${item.entry.packageName}"
                    }
                }) { item ->
                    DetailGridCard(item = item, onToggleItem = onToggleItem, onToggleDuplicate = onToggleDuplicate, onOpenFile = onOpenFile, isSafe = category.isSafeToClean)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailGridCard(
    item: CleanItem,
    onToggleItem: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
    isSafe: Boolean
) {
    val vibration = LocalVibrationManager.current
    val context = LocalContext.current
    val isSelected = when (item) {
        is CleanItem.GenericFile -> item.file.isSelected
        is CleanItem.Corpse -> item.entry.isSelected
        is CleanItem.EmptyDir -> item.entry.isSelected
        is CleanItem.MediaFile -> item.entry.isSelected
        is CleanItem.ApkFile -> item.entry.isSelected
        is CleanItem.AppCache -> item.entry.isSelected
        is CleanItem.UnusedApp -> item.entry.isSelected
        is CleanItem.Duplicate -> item.group.files.any { it.isSelected }
    }
    val name = when (item) {
        is CleanItem.GenericFile -> item.file.name
        is CleanItem.Corpse -> item.entry.packageName
        is CleanItem.EmptyDir -> item.entry.name
        is CleanItem.MediaFile -> item.entry.name
        is CleanItem.ApkFile -> item.entry.name
        is CleanItem.AppCache -> item.entry.appName
        is CleanItem.UnusedApp -> item.entry.appName
        is CleanItem.Duplicate -> item.group.files.firstOrNull()?.path?.substringAfterLast('/') ?: "Duplicate"
    }
    val size = when (item) {
        is CleanItem.GenericFile -> item.file.sizeBytes
        is CleanItem.Corpse -> item.entry.sizeBytes
        is CleanItem.EmptyDir -> 0L
        is CleanItem.MediaFile -> item.entry.sizeBytes
        is CleanItem.ApkFile -> item.entry.sizeBytes
        is CleanItem.AppCache -> item.entry.cacheBytes
        is CleanItem.UnusedApp -> item.entry.sizeBytes
        is CleanItem.Duplicate -> item.group.sizeBytes
    }
    val accent = if (isSafe) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = BouncyShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = if (isSelected) BorderStroke(2.dp, accent) else null,
        onClick = {
            vibration?.vibrateClick()
            when (item) {
                is CleanItem.GenericFile -> onToggleItem(item.file.path)
                is CleanItem.Corpse -> onToggleItem(item.entry.path)
                is CleanItem.EmptyDir -> onToggleItem(item.entry.path)
                is CleanItem.MediaFile -> onToggleItem(item.entry.path)
                is CleanItem.ApkFile -> onToggleItem(item.entry.path)
                is CleanItem.AppCache -> onToggleItem(item.entry.packageName)
                is CleanItem.UnusedApp -> onToggleItem(item.entry.packageName)
                is CleanItem.Duplicate -> {
                    val f = item.group.files.find { it.isSelected } ?: item.group.files.lastOrNull()
                    f?.let { onToggleDuplicate(item.group.hash, it.path) }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                Icon(
                    when (item) {
                        is CleanItem.AppCache -> Icons.Rounded.Cached
                        is CleanItem.ApkFile -> Icons.Rounded.Android
                        is CleanItem.EmptyDir -> Icons.Rounded.FolderOff
                        else -> Icons.Rounded.InsertDriveFile
                    },
                    null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Text(name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Formatter.formatFileSize(context, size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).then(Modifier), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = accent) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp).padding(2.dp), tint = Color.White)
                    }
                }
            }
        }
    }
}
