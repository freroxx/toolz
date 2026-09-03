package com.frerox.toolz.ui.screens.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.isSelected
import com.frerox.toolz.data.cleaner.sizeBytes
import com.frerox.toolz.data.cleaner.stableId
import com.frerox.toolz.ui.components.cleaner.AppIconThumb
import com.frerox.toolz.ui.components.cleaner.CircleSelect
import com.frerox.toolz.ui.components.cleaner.CleanerThumb
import com.frerox.toolz.ui.components.cleaner.FolderThumb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerDetailSheet(category: CleanCategory, onToggleItem:(String)->Unit, onToggleDuplicate:(String,String)->Unit, onSelectAll:(Boolean)->Unit = {}, allSelected: Boolean = false, onAutoClear: (() -> Unit)? = null, onAutoClearApp: ((String, String) -> Unit)? = null, onOpenAppSettings: ((String) -> Unit)? = null, onExcludeApp: ((String) -> Unit)? = null, onClean: (() -> Unit)? = null, onOpenFile:(String)->Unit, onDismiss:()->Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(category.items, query) {
        var list = category.items
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter {
                when (it) {
                    is CleanItem.GenericFile -> it.file.name.lowercase().contains(q) || it.file.path.lowercase().contains(q)
                    is CleanItem.Corpse -> it.entry.packageName.lowercase().contains(q) || it.entry.path.lowercase().contains(q)
                    is CleanItem.Duplicate -> it.group.files.any { f -> f.path.lowercase().contains(q) }
                    is CleanItem.EmptyDir -> it.entry.name.lowercase().contains(q) || it.entry.path.lowercase().contains(q)
                    is CleanItem.MediaFile -> it.entry.name.lowercase().contains(q)
                    is CleanItem.ApkFile -> it.entry.name.lowercase().contains(q)
                    is CleanItem.AppCache -> it.entry.appName.lowercase().contains(q) || it.entry.packageName.lowercase().contains(q)
                    is CleanItem.UnusedApp -> it.entry.appName.lowercase().contains(q)
                }
            }
        }
        list.sortedByDescending { it.sizeBytes() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(detailIconFor(category.icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp))
                    Text("${category.items.size} items • ${Formatter.formatFileSize(context, category.totalSize)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (category.selectedSize > 0) Text("${Formatter.formatFileSize(context, category.selectedSize)} selected",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.primary)
                    category.description?.let { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = allSelected, onClick = { onSelectAll(!allSelected) }, label = { Text(if (allSelected) "Clear all" else "Select all", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)) })
                if (onAutoClear != null && category.id == "app_cache" && category.items.isNotEmpty()) {
                    AssistChip(onClick = onAutoClear, leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(14.dp)) },
                        label = { Text("Auto-clear", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)) })
                }
                if (category.truncatedCount > 0) Text("+${category.truncatedCount} more not shown", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(16.dp)) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) } },
                    placeholder = { Text("Search files", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                items(filtered, key = { it.stableId() }) { item ->
                    when (item) {
                        is CleanItem.Duplicate -> DuplicateGroupRow(item, onToggleDuplicate, onOpenFile)
                        else -> SimpleRow(item, onToggleItem, onOpenFile, onAutoClearApp, onOpenAppSettings, onExcludeApp)
                    }
                }
                if (filtered.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.SearchOff, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No matches", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { query = "" }) { Text("Clear search") }
                    }
                }
            }
            // Sticky footer: clean without closing the sheet first.
            if (onClean != null && category.selectedSize > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
                Button(onClick = onClean, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp)) {
                    Text("Clean ${Formatter.formatFileSize(context, category.selectedSize)}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp))
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun displayName(item: CleanItem): String = when(item){    is CleanItem.GenericFile->item.file.name; is CleanItem.Corpse->item.entry.packageName
    is CleanItem.EmptyDir->item.entry.name; is CleanItem.MediaFile->item.entry.name
    is CleanItem.ApkFile->item.entry.name; is CleanItem.AppCache->item.entry.appName
    is CleanItem.UnusedApp->item.entry.appName; is CleanItem.Duplicate->item.group.files.firstOrNull()?.path?.substringAfterLast('/') ?: "Duplicate"
}

@Composable
private fun SimpleRow(
    item: CleanItem,
    onToggleItem:(String)->Unit,
    onOpen:(String)->Unit,
    onAutoClearApp: ((String, String) -> Unit)? = null,
    onOpenAppSettings: ((String) -> Unit)? = null,
    onExcludeApp: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val id = when(item){ is CleanItem.GenericFile->item.file.path; is CleanItem.Corpse->item.entry.path; is CleanItem.EmptyDir->item.entry.path; is CleanItem.MediaFile->item.entry.path; is CleanItem.ApkFile->item.entry.path; is CleanItem.AppCache->item.entry.packageName; is CleanItem.UnusedApp->item.entry.packageName; is CleanItem.Duplicate->"" }
    val name = displayName(item)
    val sub = when(item){
        is CleanItem.GenericFile->"${Formatter.formatFileSize(context, item.file.sizeBytes)} • ${fmtDate(item.file.lastModified)} • ${item.file.path}"
        is CleanItem.Corpse->"${Formatter.formatFileSize(context, item.entry.sizeBytes)} • ${item.entry.path}"
        is CleanItem.EmptyDir->item.entry.path
        is CleanItem.MediaFile->"${Formatter.formatFileSize(context, item.entry.sizeBytes)} • ${item.entry.path}"
        is CleanItem.ApkFile->"${Formatter.formatFileSize(context, item.entry.sizeBytes)} • v${item.entry.versionName ?: "?"} • ${item.entry.path}"
        is CleanItem.AppCache->"${Formatter.formatFileSize(context, item.entry.cacheBytes)} • ${item.entry.packageName}"
        is CleanItem.UnusedApp->"${Formatter.formatFileSize(context, item.entry.sizeBytes)} • ${item.entry.packageName}"
        is CleanItem.Duplicate->""
    }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable {
        val p = when(item){ is CleanItem.GenericFile->item.file.path; is CleanItem.MediaFile->item.entry.path; is CleanItem.ApkFile->item.entry.path; else->null }; p?.let { onOpen(it) }
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleSelect(checked = item.isSelected(), onToggle = { onToggleItem(id) })
            Spacer(Modifier.width(12.dp))
            when (item) {
                is CleanItem.GenericFile -> CleanerThumb(data = item.file.thumbnailUri ?: item.file.path, ext = item.file.extension, modifier = Modifier.size(46.dp), corner = 15.dp)
                is CleanItem.MediaFile -> CleanerThumb(data = item.entry.thumbnailUri ?: item.entry.path, ext = item.entry.extension, modifier = Modifier.size(46.dp), corner = 15.dp)
                is CleanItem.ApkFile -> CleanerThumb(data = null, ext = "apk", modifier = Modifier.size(46.dp), corner = 15.dp)
                is CleanItem.AppCache -> AppIconThumb(item.entry.packageName, modifier = Modifier.size(46.dp), corner = 15.dp)
                is CleanItem.UnusedApp -> AppIconThumb(item.entry.packageName, modifier = Modifier.size(46.dp), corner = 15.dp)
                is CleanItem.Corpse -> FolderThumb(modifier = Modifier.size(46.dp), corner = 15.dp)
                is CleanItem.EmptyDir -> FolderThumb(modifier = Modifier.size(46.dp), corner = 15.dp)
                is CleanItem.Duplicate -> {}
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), maxLines = 1); Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            if (item is CleanItem.AppCache && (onAutoClearApp != null || onOpenAppSettings != null || onExcludeApp != null)) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.MoreVert, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (onAutoClearApp != null) DropdownMenuItem(
                            text = { Text("Auto-clear this app") },
                            leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onAutoClearApp(item.entry.packageName, item.entry.appName) }
                        )
                        if (onOpenAppSettings != null) DropdownMenuItem(
                            text = { Text("Open app settings") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onOpenAppSettings(item.entry.packageName) }
                        )
                        if (onExcludeApp != null) DropdownMenuItem(
                            text = { Text("Exclude app") },
                            leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onExcludeApp(item.entry.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupRow(group: CleanItem.Duplicate, onToggleDup:(String,String)->Unit, onOpen:(String)->Unit) {
    val context = LocalContext.current
    val g = group.group
    val reclaimable = (g.files.size - 1).coerceAtLeast(0) * g.sizeBytes
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${g.files.size} copies • ${Formatter.formatFileSize(context, g.sizeBytes)} each", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    Text("Reclaimable ${Formatter.formatFileSize(context, reclaimable)} • oldest kept", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        g.files.sortedBy { it.lastModified }.forEachIndexed { idx, f ->
            val keep = idx == 0
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable(enabled = !keep) { onOpen(f.path) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (keep) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(26.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bookmark, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                    } else {
                        CircleSelect(checked = f.isSelected, onToggle = { onToggleDup(g.hash, f.path) }, size = 24.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    CleanerThumb(data = f.path, ext = f.path.substringAfterLast('.', ""), modifier = Modifier.size(42.dp), corner = 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.path.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), maxLines = 1)
                        Text(
                            if (keep) "Original — always kept"
                            else "${Formatter.formatFileSize(context, g.sizeBytes)} • ${fmtDate(f.lastModified)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (keep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun fmtDate(ms: Long): String = try { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms)) } catch (_: Exception) { "" }

private fun detailIconFor(name: String): androidx.compose.ui.graphics.vector.ImageVector = when (name) {
    "DeleteSweep" -> Icons.Rounded.DeleteSweep; "FileCopy" -> Icons.Rounded.FileCopy
    "AutoDelete" -> Icons.Rounded.AutoDelete; "Straighten" -> Icons.Rounded.Straighten
    "Cached" -> Icons.Rounded.Cached; "Android" -> Icons.Rounded.Android
    "Collections" -> Icons.Rounded.Collections; "Storage" -> Icons.Rounded.Storage
    else -> Icons.Rounded.Folder
}
