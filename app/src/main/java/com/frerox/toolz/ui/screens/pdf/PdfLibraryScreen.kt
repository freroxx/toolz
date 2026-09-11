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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.screens.pdf.components.PdfCover
import com.frerox.toolz.ui.screens.pdf.components.formatPdfDate
import com.frerox.toolz.ui.screens.pdf.components.formatPdfSize

/**
 * Document list: search field + plain rows. Nothing else.
 */
@Composable
fun PdfLibraryScreen(
    viewModel: PdfViewModel,
    onOpen: (PdfFile) -> Unit,
    onDelete: (PdfFile) -> Unit,
    onRename: (PdfFile) -> Unit,
    onAttachToNote: ((PdfFile) -> Unit)? = null,
    onImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val files by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Decode the first covers ahead of the rows so thumbnails are
    // already cached when they scroll into view.
    LaunchedEffect(files) {
        if (files.isNotEmpty()) viewModel.warmThumbnails(files)
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search documents") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        when {
            files.isEmpty() && query.isNotBlank() -> {
                LibraryMessage(
                    title = "No results",
                    body = "Nothing matches \"$query\".",
                    actionLabel = null,
                    onAction = {}
                )
            }

            files.isEmpty() -> {
                LibraryMessage(
                    title = "No documents",
                    body = "Import a PDF to start reading. Files stay on your device.",
                    actionLabel = "Import PDF",
                    onAction = onImport
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 12.dp, bottom = 32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files, key = { it.uri.toString() }) { file ->
                        PdfRow(
                            file = file,
                            viewModel = viewModel,
                            onOpen = { onOpen(file) },
                            onPin = { viewModel.togglePin(file.uri.toString()) },
                            onRename = { onRename(file) },
                            onDelete = { onDelete(file) },
                            onAttach = onAttachToNote?.let { { it(file) } }
                        )
                        LaunchedEffect(file.uri) { viewModel.prefetchInfo(file) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRow(
    file: PdfFile,
    viewModel: PdfViewModel,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAttach: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = rememberToolzHapticFeedback()

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(
                        file.displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Text(
                        documentSubtitle(file),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = {
                    PdfCover(
                        uri = file.uri,
                        renderEngine = viewModel.pdfRenderEngine,
                        modifier = Modifier.size(width = 56.dp, height = 72.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                trailingContent = {
                    Box {
                        IconButton(
                            onClick = {
                                haptic.tick()
                                menu = true
                            }
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Document options")
                        }
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (file.isPinned) "Unpin" else "Pin") },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                                onClick = { menu = false; onPin() }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; onRename() }
                            )
                            if (onAttach != null) {
                                DropdownMenuItem(
                                    text = { Text("Attach to note") },
                                    leadingIcon = { Icon(Icons.Rounded.AttachFile, null) },
                                    onClick = { menu = false; onAttach() }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { menu = false; onDelete() }
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            if (file.progress > 0f && file.pageCount > 1) {
                LinearProgressIndicator(
                    progress = { file.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

private fun documentSubtitle(file: PdfFile): String {
    val parts = mutableListOf<String>()
    if (file.pageCount > 0) {
        parts += if (file.pageCount == 1) "1 page" else "${file.pageCount} pages"
        if (file.progress > 0f) parts += "page ${file.lastPage + 1}"
    }
    val size = formatPdfSize(file.size)
    if (size.isNotBlank()) parts += size
    val date = formatPdfDate(
        if (file.lastAccessed > 0) file.lastAccessed / 1000 else file.lastModified
    )
    if (date.isNotBlank()) parts += date
    if (file.isPinned) parts += "Pinned"
    return parts.joinToString(" · ")
}

@Composable
private fun LibraryMessage(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onAction) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}
