package com.frerox.toolz.ui.screens.cleaner

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.ui.components.cleaner.AppIconThumb
import com.frerox.toolz.ui.components.cleaner.CircleSelect
import com.frerox.toolz.ui.components.cleaner.CleanerThumb
import com.frerox.toolz.ui.components.cleaner.FolderThumb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CleanerSortOption(val label: String) {
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest"),
    DATE_DESC("Newest"),
    DATE_ASC("Oldest"),
    NAME_ASC("Name")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerDetailSheet(
    category: CleanCategory,
    onToggleItem: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onSetDuplicateKeeper: ((String, String) -> Unit)? = null,
    onSetAllDuplicateKeepers: ((Boolean) -> Unit)? = null,
    onSetItemsSelected: ((Set<String>, Boolean) -> Unit)? = null,
    onAutoClear: (() -> Unit)? = null,
    onAutoClearApp: ((String, String) -> Unit)? = null,
    onOpenAppSettings: ((String) -> Unit)? = null,
    onExcludeApp: ((String) -> Unit)? = null,
    onClean: (() -> Unit)? = null,
    onOpenFile: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var activeSort by remember { mutableStateOf(CleanerSortOption.SIZE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var activeSubFilter by remember { mutableStateOf("All") }

    // Sub-filters applicable to this category
    val availableSubFilters = remember(category.id) {
        when (category.id) {
            "screenshots" -> listOf("All", "This week", "Older than 1 month", "Older than 6 months", "> 2 MB")
            "install_media", "media_clutter" -> listOf("All", "Videos", "WhatsApp", "Telegram", "Downloads")
            "dupes" -> listOf("All", "Images", "Videos", "Audio", "Documents", "> 10 MB")
            "large" -> listOf("All", "> 500 MB", "> 100 MB", "Videos", "Archives", "Documents")
            "apk" -> listOf("All", "Installed (Redundant)", "Not Installed", "> 50 MB")
            "system_junk" -> listOf("All", "Logs", "Temp files", "Caches")
            "corpse" -> listOf("All", "Data", "OBB", "Media")
            else -> emptyList()
        }
    }

    val filtered = remember(category.items, query, activeSubFilter, activeSort) {
        var list = category.items

        // Apply sub-filter
        if (activeSubFilter != "All") {
            val now = System.currentTimeMillis()
            list = list.filter { item ->
                when (activeSubFilter) {
                    "This week" -> itemTimestamp(item) >= now - 7 * 24 * 60 * 60 * 1000L
                    "Older than 1 month" -> itemTimestamp(item) < now - 30L * 24 * 60 * 60 * 1000L
                    "Older than 6 months" -> itemTimestamp(item) < now - 180L * 24 * 60 * 60 * 1000L
                    "> 500 MB" -> item.sizeBytes() > 500L * 1024 * 1024
                    "> 100 MB" -> item.sizeBytes() > 100L * 1024 * 1024
                    "> 50 MB" -> item.sizeBytes() > 50L * 1024 * 1024
                    "> 10 MB" -> item.sizeBytes() > 10L * 1024 * 1024
                    "> 2 MB" -> item.sizeBytes() > 2L * 1024 * 1024
                    "> 1 MB" -> item.sizeBytes() > 1024 * 1024L
                    "Installed (Redundant)" -> item is CleanItem.ApkFile && item.entry.isRedundant
                    "Not Installed" -> item is CleanItem.ApkFile && !item.entry.isRedundant
                    "Images" -> itemFileExt(item) in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
                    "Videos" -> itemFileExt(item) in setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "m4v")
                    "Audio" -> itemFileExt(item) in setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
                    "Documents" -> itemFileExt(item) in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub")
                    "Archives" -> itemFileExt(item) in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
                    "Screenshots" -> item is CleanItem.MediaFile && item.entry.type == MediaType.SCREENSHOT
                    "WhatsApp" -> item is CleanItem.MediaFile && item.entry.type == MediaType.WHATSAPP
                    "Telegram" -> item is CleanItem.MediaFile && item.entry.type == MediaType.TELEGRAM
                    "Downloads" -> item is CleanItem.MediaFile && item.entry.type == MediaType.DOWNLOAD
                    "Installers" -> item is CleanItem.ApkFile
                    "Logs" -> item is CleanItem.GenericFile && item.file.extension in setOf("log", "logcat", "crash", "stacktrace", "dmp")
                    "Temp files" -> item is CleanItem.GenericFile && item.file.extension in setOf("tmp", "temp", "part", "partial", "crdownload", "chk", "old", "bak")
                    "Caches" -> item is CleanItem.GenericFile && item.file.extension in setOf("cache", "exo", "fb_temp", "thumbdata", "thumb", "thumbnails")
                    "Data" -> item is CleanItem.Corpse && item.entry.type == CorpseType.DATA
                    "OBB" -> item is CleanItem.Corpse && item.entry.type == CorpseType.OBB
                    "Media" -> item is CleanItem.Corpse && item.entry.type == CorpseType.MEDIA
                    else -> true
                }
            }
        }

        // Apply search query
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter {
                when (it) {
                    is CleanItem.GenericFile -> it.file.name.lowercase().contains(q) || it.file.path.lowercase().contains(q)
                    is CleanItem.Corpse -> it.entry.packageName.lowercase().contains(q) || it.entry.path.lowercase().contains(q)
                    is CleanItem.Duplicate -> it.group.files.any { f -> f.path.lowercase().contains(q) }
                    is CleanItem.EmptyDir -> it.entry.name.lowercase().contains(q) || it.entry.path.lowercase().contains(q)
                    is CleanItem.MediaFile -> it.entry.name.lowercase().contains(q) || it.entry.path.lowercase().contains(q)
                    is CleanItem.ApkFile -> it.entry.name.lowercase().contains(q) || (it.entry.packageName?.lowercase()?.contains(q) == true)
                    is CleanItem.AppCache -> it.entry.appName.lowercase().contains(q) || it.entry.packageName.lowercase().contains(q)
                    is CleanItem.UnusedApp -> it.entry.appName.lowercase().contains(q) || it.entry.packageName.lowercase().contains(q)
                }
            }
        }

        // Apply sorting
        when (activeSort) {
            CleanerSortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes() }
            CleanerSortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes() }
            CleanerSortOption.DATE_DESC -> list.sortedByDescending { itemTimestamp(it) }
            CleanerSortOption.DATE_ASC -> list.sortedBy { itemTimestamp(it) }
            CleanerSortOption.NAME_ASC -> list.sortedBy { displayName(it).lowercase() }
        }
    }

    val allFilteredSelected = remember(filtered) {
        filtered.isNotEmpty() && filtered.all { it.isSelected() }
    }
    val selectedCount = remember(category.items) { category.selectedCount() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(detailIconFor(category.icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp))
                    Text(
                        "${selectedCount} of ${category.items.size} selected • ${Formatter.formatFileSize(context, category.totalSize)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (category.selectedSize > 0) {
                        Text(
                            "${Formatter.formatFileSize(context, category.selectedSize)} selected",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                }
            }

            // Sub-filter chips row
            if (availableSubFilters.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableSubFilters.forEach { filter ->
                        FilterChip(
                            selected = activeSubFilter == filter,
                            onClick = { activeSubFilter = filter },
                            label = { Text(filter, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)) },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }

            // Search & Sort & Select-All bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                            }
                        }
                    },
                    placeholder = { Text("Search files", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                )

                // Select All / Deselect All Chip
                if (filtered.isNotEmpty() && onSetItemsSelected != null) {
                    FilledTonalIconButton(
                        onClick = {
                            val ids = filtered.map { it.stableId() }.toSet()
                            onSetItemsSelected(ids, !allFilteredSelected)
                        },
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            if (allFilteredSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                            contentDescription = if (allFilteredSelected) "Deselect all" else "Select all",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Sort Dropdown
                Box {
                    FilledTonalIconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Sort, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        CleanerSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = if (activeSort == option) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    activeSort = option
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                if (onAutoClear != null && category.id == "app_cache" && category.items.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onAutoClear,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Rounded.Cached, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Auto", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))

            if (category.id == "app_cache" && category.items.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Cached, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Auto-clear App Caches", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                            Text("Clears internal cache via Accessibility automation", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (onAutoClear != null) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onAutoClear,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Start", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }

            if (category.id == "dupes" && category.items.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Duplicate Keepers", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                            Text("1 copy is preserved, others can be cleaned", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (onSetAllDuplicateKeepers != null) {
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { onSetAllDuplicateKeepers(false) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Oldest", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            }
                            Spacer(Modifier.width(4.dp))
                            FilledTonalButton(
                                onClick = { onSetAllDuplicateKeepers(true) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Newest", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            }
                        }
                    }
                }
            }

            // File items list
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 520.dp)
            ) {
                items(filtered, key = { it.stableId() }) { item ->
                    when (item) {
                        is CleanItem.Duplicate -> DuplicateGroupRow(
                            group = item,
                            onToggleDup = onToggleDuplicate,
                            onSetKeeper = onSetDuplicateKeeper,
                            onOpen = onOpenFile
                        )
                        else -> SimpleRow(item, onToggleItem, onOpenFile, onAutoClearApp, onOpenAppSettings, onExcludeApp)
                    }
                }
                if (filtered.isEmpty()) item {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.SearchOff, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No matches found", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (query.isNotEmpty() || activeSubFilter != "All") {
                            TextButton(onClick = { query = ""; activeSubFilter = "All" }) {
                                Text("Reset filters")
                            }
                        }
                    }
                }
            }

            // Sticky footer: clean without closing the sheet first
            if (onClean != null && category.selectedSize > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f))
                Button(
                    onClick = onClean,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(52.dp)
                ) {
                    Text(
                        "Clean ${Formatter.formatFileSize(context, category.selectedSize)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun itemTimestamp(item: CleanItem): Long = when (item) {
    is CleanItem.GenericFile -> item.file.lastModified
    is CleanItem.MediaFile -> item.entry.lastModified
    is CleanItem.ApkFile -> item.entry.lastModified
    is CleanItem.Duplicate -> item.group.files.firstOrNull()?.lastModified ?: 0L
    is CleanItem.Corpse -> 0L
    is CleanItem.EmptyDir -> 0L
    is CleanItem.AppCache -> 0L
    is CleanItem.UnusedApp -> item.entry.lastUsed
}

private fun displayName(item: CleanItem): String = when (item) {
    is CleanItem.GenericFile -> item.file.name
    is CleanItem.Corpse -> item.entry.packageName
    is CleanItem.EmptyDir -> item.entry.name
    is CleanItem.MediaFile -> item.entry.name
    is CleanItem.ApkFile -> item.entry.name
    is CleanItem.AppCache -> item.entry.appName
    is CleanItem.UnusedApp -> item.entry.appName
    is CleanItem.Duplicate -> item.group.files.firstOrNull()?.path?.substringAfterLast('/') ?: "Duplicate"
}

@Composable
private fun SimpleRow(
    item: CleanItem,
    onToggleItem: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAutoClearApp: ((String, String) -> Unit)? = null,
    onOpenAppSettings: ((String) -> Unit)? = null,
    onExcludeApp: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val id = when (item) {
        is CleanItem.GenericFile -> item.file.path
        is CleanItem.Corpse -> item.entry.path
        is CleanItem.EmptyDir -> item.entry.path
        is CleanItem.MediaFile -> item.entry.path
        is CleanItem.ApkFile -> item.entry.path
        is CleanItem.AppCache -> item.entry.packageName
        is CleanItem.UnusedApp -> item.entry.packageName
        is CleanItem.Duplicate -> ""
    }
    val name = displayName(item)
    val sub = when (item) {
        is CleanItem.GenericFile -> "${Formatter.formatFileSize(context, item.file.sizeBytes)} • ${fmtDate(item.file.lastModified)} • ${item.file.path}"
        is CleanItem.Corpse -> "${Formatter.formatFileSize(context, item.entry.sizeBytes)} • ${item.entry.path}"
        is CleanItem.EmptyDir -> item.entry.path
        is CleanItem.MediaFile -> "${Formatter.formatFileSize(context, item.entry.sizeBytes)} • ${fmtDate(item.entry.lastModified)} • ${item.entry.path}"
        is CleanItem.ApkFile -> {
            val verText = if (item.entry.versionName != null) "v${item.entry.versionName}" else ""
            val status = when {
                item.entry.isRedundant -> "Installed (v${item.entry.installedVersionName ?: "?"}) — safe to clean"
                item.entry.packageName != null -> "Package: ${item.entry.packageName}"
                else -> "Package installer"
            }
            "${Formatter.formatFileSize(context, item.entry.sizeBytes)} • $verText • $status"
        }
        is CleanItem.AppCache -> "${Formatter.formatFileSize(context, item.entry.cacheBytes)} • ${item.entry.packageName}"
        is CleanItem.UnusedApp -> "${Formatter.formatFileSize(context, item.entry.sizeBytes)} • ${item.entry.packageName}"
        is CleanItem.Duplicate -> ""
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable {
            val p = when (item) {
                is CleanItem.GenericFile -> item.file.path
                is CleanItem.MediaFile -> item.entry.path
                is CleanItem.ApkFile -> item.entry.path
                else -> null
            }
            p?.let { onOpen(it) }
        }
    ) {
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
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (item is CleanItem.ApkFile && item.entry.isRedundant) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Redundant",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (item is CleanItem.AppCache && onAutoClearApp != null) {
                TextButton(
                    onClick = { onAutoClearApp(item.entry.packageName, item.entry.appName) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.Cached, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                    Text("Clear", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                }
            }
            if (item is CleanItem.AppCache && (onAutoClearApp != null || onOpenAppSettings != null || onExcludeApp != null)) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.MoreVert, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (onAutoClearApp != null) DropdownMenuItem(
                            text = { Text("Auto-clear this app") },
                            leadingIcon = { Icon(Icons.Rounded.Cached, null, Modifier.size(18.dp)) },
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
private fun DuplicateGroupRow(
    group: CleanItem.Duplicate,
    onToggleDup: (String, String) -> Unit,
    onSetKeeper: ((String, String) -> Unit)? = null,
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    val g = group.group
    val reclaimable = (g.files.size - 1).coerceAtLeast(0) * g.sizeBytes

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${g.files.size} copies • ${Formatter.formatFileSize(context, g.sizeBytes)} each",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        "Reclaimable ${Formatter.formatFileSize(context, reclaimable)} • tap any copy to preview",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        g.files.forEachIndexed { idx, f ->
            val keep = idx == 0
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(f.path) }
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (keep) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Bookmark, contentDescription = "Original kept", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
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
                            if (keep) "Original — kept by default"
                            else "${Formatter.formatFileSize(context, g.sizeBytes)} • ${fmtDate(f.lastModified)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (keep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (!keep && onSetKeeper != null) {
                        TextButton(
                            onClick = { onSetKeeper(g.hash, f.path) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Keep this", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }
}

private fun fmtDate(ms: Long): String = try {
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(ms))
} catch (_: Exception) { "" }

private fun detailIconFor(name: String): androidx.compose.ui.graphics.vector.ImageVector = when (name) {
    "DeleteSweep" -> Icons.Rounded.DeleteSweep
    "FileCopy" -> Icons.Rounded.FileCopy
    "AutoDelete" -> Icons.Rounded.AutoDelete
    "Straighten" -> Icons.Rounded.Straighten
    "Cached" -> Icons.Rounded.Cached
    "Android" -> Icons.Rounded.Android
    "Collections" -> Icons.Rounded.Collections
    "Screenshot" -> Icons.Rounded.Screenshot
    "Storage" -> Icons.Rounded.Storage
    else -> Icons.Rounded.Folder
}

private fun itemFileExt(item: CleanItem): String = when (item) {
    is CleanItem.GenericFile -> item.file.extension.lowercase()
    is CleanItem.MediaFile -> item.entry.extension.lowercase()
    is CleanItem.Duplicate -> item.group.files.firstOrNull()?.path?.substringAfterLast('.', "")?.lowercase().orEmpty()
    is CleanItem.ApkFile -> "apk"
    else -> ""
}

