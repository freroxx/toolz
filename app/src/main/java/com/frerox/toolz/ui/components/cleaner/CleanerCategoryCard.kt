package com.frerox.toolz.ui.components.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.ui.theme.LocalVibrationManager

private val kSuccess = Color(0xFF4CAF50)

@Composable
fun CleanerCategoryCard(category: CleanCategory, isShizukuGranted: Boolean = true, onToggleItem: (String)->Unit = {}, onToggleDuplicate: (String,String)->Unit = {_,_->}, onOpenFile: (String)->Unit = {}, onLongPress: ()->Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val vibration = LocalVibrationManager.current
    val isEmpty = category.items.isEmpty()
    val grey = category.requiresShizuku && !isShizukuGranted
    val allSelected = remember(category.items) {
        category.items.isNotEmpty() && category.items.all { item ->
            when(item) {
                is com.frerox.toolz.data.cleaner.CleanItem.GenericFile -> item.file.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.Corpse -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.Duplicate -> item.group.files.any { it.isSelected }
                is com.frerox.toolz.data.cleaner.CleanItem.EmptyDir -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.MediaFile -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.ApkFile -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.AppCache -> item.entry.isSelected
                is com.frerox.toolz.data.cleaner.CleanItem.UnusedApp -> item.entry.isSelected
            }
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (grey) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
        onClick = {
            if (grey) return@Surface
            vibration?.vibrateClick()
            if (!isEmpty) expanded = !expanded else onLongPress()
        }
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allSelected && !grey,
                    onCheckedChange = {
                        if (grey) return@Checkbox
                        val target = !allSelected
                        category.items.forEach { item ->
                            when(item) {
                                is com.frerox.toolz.data.cleaner.CleanItem.GenericFile -> if (item.file.isSelected != target) onToggleItem(item.file.path)
                                is com.frerox.toolz.data.cleaner.CleanItem.Corpse -> if (item.entry.isSelected != target) onToggleItem(item.entry.path)
                                is com.frerox.toolz.data.cleaner.CleanItem.EmptyDir -> if (item.entry.isSelected != target) onToggleItem(item.entry.path)
                                is com.frerox.toolz.data.cleaner.CleanItem.MediaFile -> if (item.entry.isSelected != target) onToggleItem(item.entry.path)
                                is com.frerox.toolz.data.cleaner.CleanItem.ApkFile -> if (item.entry.isSelected != target) onToggleItem(item.entry.path)
                                is com.frerox.toolz.data.cleaner.CleanItem.AppCache -> if (item.entry.isSelected != target) onToggleItem(item.entry.packageName)
                                is com.frerox.toolz.data.cleaner.CleanItem.UnusedApp -> if (item.entry.isSelected != target) onToggleItem(item.entry.packageName)
                                is com.frerox.toolz.data.cleaner.CleanItem.Duplicate -> item.group.files.drop(1).forEach { f -> if (f.isSelected != target) onToggleDuplicate(item.group.hash, f.path) }
                            }
                        }
                    },
                    enabled = !grey,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = (if (category.isSafeToClean) kSuccess else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(iconFor(category.icon), null, Modifier.size(20.dp), tint = if (grey) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else if (category.isSafeToClean) kSuccess else MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(category.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp), color = if (grey) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f) else MaterialTheme.colorScheme.onSurface)
                        if (category.isSafeToClean && !isEmpty && !grey) {
                            Box(modifier = Modifier.size(8.dp).then(Modifier), contentAlignment = Alignment.Center) { Surface(shape = RoundedCornerShape(4.dp), color = kSuccess, modifier = Modifier.size(8.dp)) {} }
                            Text("Safe", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f))
                        }
                        if (grey) Text("Unlock with Shizuku", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.error.copy(alpha=0.8f))
                    }
                    Text(
                        when {
                            grey -> "Shizuku required"
                            category.totalSize > 0 -> Formatter.formatFileSize(context, category.totalSize) + " • ${category.items.size} items"
                            else -> "${category.items.size} items"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isEmpty && !grey) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("${category.items.size}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp), color = MaterialTheme.colorScheme.onSecondaryContainer) }
                    Spacer(Modifier.width(6.dp))
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
            if (expanded && !isEmpty && !grey) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Preview first item thumb if image/video
                    val first = category.items.firstOrNull()
                    if (first is com.frerox.toolz.data.cleaner.CleanItem.GenericFile) {
                        val f = first.file
                        if (f.extension.lowercase() in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm")) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                FileThumb(f, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(f.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), maxLines = 1, modifier = Modifier.weight(1f))
                                Text(android.text.format.Formatter.formatFileSize(context, f.sizeBytes), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    TextButton(onClick = { vibration?.vibrateClick(); onLongPress() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("View all ${category.items.size} items", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
                    }
                }
            }
        }
    }
}
private fun iconFor(name: String): ImageVector = when(name) {
    "DeleteSweep"->Icons.Rounded.DeleteSweep; "FileCopy"->Icons.Rounded.FileCopy; "AutoDelete"->Icons.Rounded.AutoDelete; "Straighten"->Icons.Rounded.Straighten; "FolderOff"->Icons.Rounded.FolderOff; "AppSettingsAlt"->Icons.Rounded.AppSettingsAlt; "Description"->Icons.Rounded.Description; "Image"->Icons.Rounded.Image; "Cached"->Icons.Rounded.Cached; "Android"->Icons.Rounded.Android; "Collections"->Icons.Rounded.Collections; "Storage"->Icons.Rounded.Storage; else->Icons.Rounded.Folder
}
// requiresShizuku handled via direct field
