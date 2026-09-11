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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.PdfAttachResult
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

/**
 * PDF entry: library <-> reader switch plus rename / delete / attach dialogs.
 * Signature preserved for MainActivity + dashboard intents.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: (Int) -> Unit = {},
    onNavigateToConverter: ((String, String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val haptic = rememberToolzHapticFeedback()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var renamingFile by remember { mutableStateOf<PdfFile?>(null) }
    var newFileName by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var deletingFile by remember { mutableStateOf<PdfFile?>(null) }
    var attachingFile by remember { mutableStateOf<PdfFile?>(null) }

    val isViewer = uiState is PdfUiState.Viewer

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            haptic.click()
            viewModel.importPdf(uri) { imported ->
                scope.launch {
                    snackbar.showSnackbar(
                        if (imported != null) "PDF imported" else "Import failed"
                    )
                }
            }
        }
    }

    if (isViewer) {
        Box(Modifier.fillMaxSize().toolzBackground()) {
            PdfReaderScreen(
                viewModel = viewModel,
                onBack = { viewModel.closeViewer() },
                onConvert = onNavigateToConverter,
                onAttachToNote = {
                    viewModel.activeUri.value?.let { uri ->
                        // Library hit is preferred (pinned/title/size), but docs
                        // opened via SAF / intent / note attachment are NOT in
                        // the vault list — build a lightweight PdfFile so the
                        // attach sheet still opens instead of doing nothing.
                        val known = viewModel.pdfFiles.value.find { it.uri == uri }
                        attachingFile = known ?: PdfFile(
                            uri = uri,
                            name = viewModel.activeTitle.value
                                .takeIf { it.isNotBlank() } ?: "Document.pdf",
                            size = 0L,
                            lastModified = 0L,
                            pageCount = viewModel.docState.value.totalPages
                                .coerceAtLeast(0),
                        )
                    }
                },
                onOpenNote = onNavigateToNote
            )
        }
    } else {
        val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
        var showSortMenu by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(Color.Transparent)
                        .padding(top = 0.dp)
                ) {
                    Box(Modifier.statusBarsPadding()) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shadowElevation = 3.dp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            ExpressiveTopAppBar(
                            title = { Text("PDFs") },
                            subtitle = {
                                val n = viewModel.pdfFiles.collectAsStateWithLifecycle().value.size
                                Text(
                                    when (n) {
                                        0 -> "No documents"
                                        1 -> "1 document"
                                        else -> "$n documents"
                                    }
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { haptic.click(); onNavigateBack() },
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        haptic.click()
                                        importer.launch(arrayOf("application/pdf"))
                                    }
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Import PDF")
                                }
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            Icons.Rounded.MoreVert,
                                            contentDescription = "Sort and refresh"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        Text(
                                            "Sort by",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                horizontal = 16.dp,
                                                vertical = 8.dp
                                            )
                                        )
                                        SortOption(
                                            label = "Recent",
                                            selected = sortOrder == PdfSortOrder.RECENT,
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(PdfSortOrder.RECENT)
                                            }
                                        )
                                        SortOption(
                                            label = "Name",
                                            selected = sortOrder == PdfSortOrder.NAME,
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(PdfSortOrder.NAME)
                                            }
                                        )
                                        SortOption(
                                            label = "Size",
                                            selected = sortOrder == PdfSortOrder.SIZE,
                                            onClick = {
                                                showSortMenu = false
                                                viewModel.setSortOrder(PdfSortOrder.SIZE)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Refresh") },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Refresh, null)
                                            },
                                            onClick = {
                                                showSortMenu = false
                                                haptic.tick()
                                                viewModel.refresh()
                                            }
                                        )
                                    }
                                }
                            },
                            titleHorizontalAlignment = Alignment.CenterHorizontally
                            )
                        }
                    }
                }
            },
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(
                Modifier.fillMaxSize().toolzBackground().padding(padding)
            ) {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        if (performanceMode) fadeIn() togetherWith fadeOut()
                        else fadeIn() togetherWith fadeOut()
                    },
                    label = "pdfShell"
                ) { state ->
                    when (state) {
                        is PdfUiState.Loading, is PdfUiState.Idle -> {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is PdfUiState.Error -> VaultError(
                            message = (state as PdfUiState.Error).message,
                            onRetry = { viewModel.refresh() },
                            onImport = { importer.launch(arrayOf("application/pdf")) }
                        )

                        else -> PdfLibraryScreen(
                            viewModel = viewModel,
                            onOpen = { viewModel.openPdf(it.uri, it.displayTitle) },
                            onDelete = { deletingFile = it },
                            onRename = {
                                renamingFile = it
                                newFileName = it.displayTitle
                                showRename = true
                            },
                            onAttachToNote = { attachingFile = it },
                            onImport = { importer.launch(arrayOf("application/pdf")) }
                        )
                    }
                }
            }
        }
    }

    if (showRename && renamingFile != null) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("File name") },
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renamingFile?.let { viewModel.renameFile(it, newFileName.trim()) }
                        showRename = false
                        haptic.click()
                    },
                    enabled = newFileName.trim().isNotEmpty()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    deletingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deletingFile = null },
            title = { Text("Delete this PDF?") },
            text = {
                Text("\"${file.displayTitle}\" will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(file)
                        deletingFile = null
                        haptic.click()
                        scope.launch { snackbar.showSnackbar("Deleted") }
                    }
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    attachingFile?.let { file ->
        AttachToNoteSheet(
            viewModel = viewModel,
            file = file,
            onOpenNote = onNavigateToNote,
            onDismiss = { attachingFile = null },
            onDone = { msg ->
                attachingFile = null
                scope.launch { snackbar.showSnackbar(msg) }
            }
        )
    }
}

@Composable
private fun SortOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null)
        },
        onClick = onClick
    )
}

@Composable
private fun VaultError(message: String, onRetry: () -> Unit, onImport: () -> Unit) {
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
            Text("Something went wrong", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row {
                FilledTonalButton(onClick = onRetry) {
                    Text("Retry")
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = onImport) {
                    Text("Import")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AttachToNoteSheet(
    viewModel: PdfViewModel,
    file: PdfFile,
    onOpenNote: (Int) -> Unit,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = rememberToolzHapticFeedback()
    val currentPage = viewModel.docState.collectAsStateWithLifecycle().value.currentPageIndex

    androidx.compose.runtime.LaunchedEffect(Unit) {
        notes = viewModel.recentNotes(30)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Attach to note", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                file.displayTitle +
                    if (viewModel.uiState.collectAsStateWithLifecycle().value is PdfUiState.Viewer) {
                        " · page ${currentPage + 1}"
                    } else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            if (notes.isEmpty()) {
                Text(
                    "No notes yet. Create one in Notes first, then attach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    note.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (note.content.isNotBlank()) {
                                    Text(
                                        note.content.take(80),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(
                                onClick = {
                                    if (busy) return@TextButton
                                    busy = true
                                    scope.launch {
                                        val page =
                                            if (viewModel.uiState.value is PdfUiState.Viewer) {
                                                currentPage
                                            } else 0
                                        val result = viewModel.attachPdfToNote(note.id, file, page)
                                        busy = false
                                        haptic.click()
                                        when (result) {
                                            is PdfAttachResult.Attached -> {
                                                onDone("Attached to note")
                                                onOpenNote(note.id)
                                            }
                                            PdfAttachResult.Capped ->
                                                onDone("Note already has 10 PDFs")
                                            PdfAttachResult.Unreadable ->
                                                onDone("Couldn't open that PDF on this device")
                                            is PdfAttachResult.Failed ->
                                                onDone("Couldn't attach — try again")
                                        }
                                    }
                                },
                                enabled = !busy
                            ) {
                                Text("Attach")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
