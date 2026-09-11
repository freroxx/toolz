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

package com.frerox.toolz.ui.screens.pdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.screens.pdf.components.PdfCover
import com.frerox.toolz.ui.screens.pdf.components.formatPdfDate
import com.frerox.toolz.ui.screens.pdf.components.formatPdfSize
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.SquircleShape

/**
 * PDF Vault — library home of the remake.
 * Recents shelf + searchable, sortable vault in list/grid. Covers lazy-load
 * via PdfRenderEngine; metadata enriches in background (no startup jank).
 */
@Composable
fun PdfLibraryScreen(
    viewModel: PdfViewModel,
    onOpen: (PdfFile) -> Unit,
    onDelete: (PdfFile) -> Unit,
    onRename: (PdfFile) -> Unit,
    onAttachToNote: ((PdfFile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val files by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val recents by viewModel.recentFiles.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sort by viewModel.sortOrder.collectAsStateWithLifecycle()
    val mode by viewModel.viewMode.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val vibration = LocalVibrationManager.current

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            haptic.click()
            viewModel.importPdf(uri)
        }
    }

    Column(modifier.fillMaxSize()) {
        // Search
        PdfSearchField(
            query = query,
            onQuery = { viewModel.setSearchQuery(it) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // Continue reading
        val visibleRecents = remember(recents, query) {
            if (query.isNotBlank()) emptyList() else recents.filter { it.progress > 0f }.take(8)
        }
        AnimatedVisibility(visible = visibleRecents.isNotEmpty()) {
            Column {
                ShelfHeader(
                    icon = Icons.Rounded.History,
                    label = "CONTINUE READING",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleRecents, key = { it.uri.toString() }) { file ->
                        ContinueCard(
                            file = file,
                            viewModel = viewModel,
                            onOpen = { onOpen(file) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Sort + view toggle bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (files.isEmpty() && query.isNotBlank()) "NO MATCHES"
                else "${files.size} FILE${if (files.size == 1) "" else "S"}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.weight(1f)
            )
            ToolzConnectedButtonGroup(
                selectedIndex = sort.ordinal,
                options = listOf("New", "Name", "Size"),
                onOptionSelected = {
                    haptic.tick()
                    viewModel.setSortOrder(PdfSortOrder.entries[it])
                },
                modifier = Modifier.width(196.dp)
            )
            ToolzExpressiveIconButton(
                onClick = {
                    haptic.tick()
                    viewModel.setViewMode(
                        if (mode == PdfViewMode.LIST) PdfViewMode.GRID else PdfViewMode.LIST
                    )
                },
                shape = MediumExpressiveShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (mode == PdfViewMode.LIST) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda,
                    contentDescription = "Toggle view",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Body
        when {
            files.isEmpty() && query.isNotBlank() -> PdfNoResults(query = query)
            files.isEmpty() -> PdfEmptyVault(onImport = { importer.launch(arrayOf("application/pdf")) })
            mode == PdfViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 0.dp, bottom = 64.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    var menu by remember { mutableStateOf(false) }
                    StaggeredEntrance(index = files.indexOf(file).coerceAtMost(12)) {
                        Box {
                            PdfGridCard(file = file, viewModel = viewModel, onOpen = { onOpen(file) }) {
                                haptic.tick()
                                menu = true
                            }
                            VaultMenu(
                                expanded = menu,
                                file = file,
                                onDismiss = { menu = false },
                                onPin = { viewModel.togglePin(file.uri.toString()) },
                                onRename = { onRename(file) },
                                onDelete = { onDelete(file) },
                                onAttach = onAttachToNote?.let { { it(file) } }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 0.dp, bottom = 64.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    var menu by remember { mutableStateOf(false) }
                    StaggeredEntrance(index = files.indexOf(file).coerceAtMost(12)) {
                        Box {
                            PdfRowCard(file = file, viewModel = viewModel, onOpen = { onOpen(file) }) {
                                haptic.tick()
                                menu = true
                            }
                            VaultMenu(
                                expanded = menu,
                                file = file,
                                onDismiss = { menu = false },
                                onPin = { viewModel.togglePin(file.uri.toString()) },
                                onRename = { onRename(file) },
                                onDelete = { onDelete(file) },
                                onAttach = onAttachToNote?.let { { it(file) } }
                            )
                        }
                    }
                    LaunchedEffect(file.uri) { viewModel.prefetchInfo(file) }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    vibration?.let { }
}

@Composable
private fun ShelfHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
            Icon(icon, null, modifier = Modifier.padding(6.dp).size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PdfSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BouncyShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(
            1.dp,
            if (query.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Search, null, Modifier.size(20.dp),
                tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(
                        "Search documents…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    inner()
                }
            )
            AnimatedVisibility(visible = query.isNotBlank(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                androidx.compose.material3.IconButton(onClick = { onQuery("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(file: PdfFile, viewModel: PdfViewModel, onOpen: () -> Unit) {
    LaunchedEffect(file.uri) { viewModel.prefetchInfo(file) }
    ExpressiveCard(
        onClick = onOpen,
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.width(220.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PdfCover(
                uri = file.uri,
                renderEngine = viewModel.pdfRenderEngine,
                modifier = Modifier.size(width = 52.dp, height = 68.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.displayTitle, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (file.pageCount > 0) "PAGE ${file.lastPage + 1} / ${file.pageCount}" else "RESUME",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth().height(5.dp)
                ) {
                    Box {
                        Box(
                            Modifier.fillMaxWidth(fraction = file.progress.coerceIn(0f, 1f))
                                .height(5.dp)
                                .clip(SmallExpressiveShape)
                                .then(Modifier)
                        ) {
                            Surface(color = MaterialTheme.colorScheme.primary, shape = SmallExpressiveShape, modifier = Modifier.fillMaxSize()) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRowCard(file: PdfFile, viewModel: PdfViewModel, onOpen: () -> Unit, onMenu: () -> Unit) {
    ExpressiveCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().height(108.dp),
        shape = SquircleShape,
        containerColor = if (file.isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(
            if (file.isPinned) 1.5.dp else 1.dp,
            if (file.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(Modifier.padding(12.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            PdfCover(
                uri = file.uri,
                renderEngine = viewModel.pdfRenderEngine,
                modifier = Modifier.width(62.dp).fillMaxSize()
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (file.isPinned) Icon(Icons.Rounded.PushPin, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        file.displayTitle, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (file.pageCount > 0) Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = SmallExpressiveShape
                    ) {
                        Text(
                            "${file.pageCount} PGS", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        formatPdfSize(file.size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                val date = formatPdfDate(if (file.lastAccessed > 0) file.lastAccessed / 1000 else file.lastModified)
                if (date.isNotBlank()) Text(
                    date, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp
                )
            }
            ToolzExpressiveIconButton(
                onClick = onMenu, modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                ),
                shape = MediumExpressiveShape
            ) {
                Icon(Icons.Rounded.MoreVert, "More options", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PdfGridCard(file: PdfFile, viewModel: PdfViewModel, onOpen: () -> Unit, onMenu: () -> Unit) {
    LaunchedEffect(file.uri) { viewModel.prefetchInfo(file) }
    ExpressiveCard(
        onClick = onOpen,
        shape = SquircleShape,
        containerColor = if (file.isPinned) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (file.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Box {
                PdfCover(
                    uri = file.uri,
                    renderEngine = viewModel.pdfRenderEngine,
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                if (file.isPinned) Surface(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Rounded.PushPin, null, Modifier.padding(5.dp).size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                ToolzExpressiveIconButton(
                    onClick = onMenu, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
                    ),
                    shape = MediumExpressiveShape
                ) {
                    Icon(Icons.Rounded.MoreVert, null, Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                file.displayTitle, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    if (file.pageCount > 0) append("${file.pageCount} pgs · ")
                    append(formatPdfSize(file.size))
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VaultMenu(
    expanded: Boolean,
    file: PdfFile,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAttach: (() -> Unit)?
) {
    val haptic = rememberToolzHapticFeedback()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        offset = DpOffset(0.dp, 8.dp)
    ) {
        DropdownMenuItem(
            text = { Text(if (file.isPinned) "Unpin" else "Pin", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.PushPin, null, Modifier.size(20.dp)) },
            onClick = { onDismiss(); onPin(); haptic.tick() },
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        DropdownMenuItem(
            text = { Text("Rename", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp)) },
            onClick = { onDismiss(); onRename(); haptic.tick() },
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        if (onAttach != null) DropdownMenuItem(
            text = { Text("Attach to note", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)) },
            onClick = { onDismiss(); onAttach(); haptic.click() },
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DropdownMenuItem(
            text = { Text("Delete", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete(); haptic.tick() },
            modifier = Modifier.clip(MediumExpressiveShape)
        )
    }
}

@Composable
private fun PdfEmptyVault(onImport: () -> Unit) {
    val perf = LocalPerformanceMode.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoStories, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "NO PDFS FOUND", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Import a document to start reading — it stays on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = onImport,
                shape = BouncyShape,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("IMPORT PDF", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            if (!perf) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PdfNoResults(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Icon(Icons.Rounded.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text("No matches for \"$query\"", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}


