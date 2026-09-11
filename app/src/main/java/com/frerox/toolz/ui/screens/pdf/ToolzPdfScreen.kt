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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SquircleShape
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// PDF Vault entry — remake V2 shell.
// Library <-> Reader switch with shared rename/delete/attach dialogs.
// Signature preserved for MainActivity + dashboard intents.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: (Int) -> Unit = {},
    onNavigateToConverter: ((String, String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val docState by viewModel.docState.collectAsStateWithLifecycle()
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
                onAttachToNote = { viewModel.activeUri.value?.let { uri ->
                    viewModel.pdfFiles.value.find { it.uri == uri }?.let { attachingFile = it }
                } },
                onOpenNote = onNavigateToNote
            )
        }
    } else {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(Color.Transparent)
                        .padding(top = 0.dp)
                ) {
                    Box(Modifier.statusBarsPadding()) {
                    ExpressiveTopAppBar(
                        title = {
                            Text(
                                "PDF VAULT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        subtitle = {
                            val n = viewModel.pdfFiles.collectAsStateWithLifecycle().value.size
                            Text(
                                if (n == 0) "Your documents" else "$n document${if (n == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        navigationIcon = {
                            ToolzExpressiveIconButton(
                                onClick = { haptic.click(); onNavigateBack() },
                                modifier = Modifier.padding(start = 8.dp).size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = MediumExpressiveShape
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp))
                            }
                        },
                        actions = {
                            ToolzExpressiveIconButton(
                                onClick = { haptic.tick(); viewModel.refresh() },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                shape = MediumExpressiveShape
                            ) {
                                Icon(Icons.Rounded.Refresh, "Refresh", Modifier.size(20.dp))
                            }
                            ToolzExpressiveIconButton(
                                onClick = { haptic.click(); importer.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.padding(end = 8.dp).size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = MediumExpressiveShape
                            ) {
                                Icon(Icons.Rounded.Add, "Import", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                        titleHorizontalAlignment = Alignment.CenterHorizontally
                    )
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
                        else (fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.97f)) togetherWith fadeOut()
                    },
                    label = "pdfShell"
                ) { state ->
                    when (state) {
                        is PdfUiState.Loading, is PdfUiState.Idle -> VaultLoading()
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
                            onAttachToNote = { attachingFile = it }
                        )
                    }
                }
            }
        }
    }

    // ── Rename ──
    if (showRename && renamingFile != null) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            shape = SquircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text("RENAME PDF", fontWeight = FontWeight.Black, letterSpacing = 1.sp, style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MediumExpressiveShape,
                    label = { Text("File name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        renamingFile?.let { viewModel.renameFile(it, newFileName.trim()) }
                        showRename = false
                        haptic.click()
                        scope.launch { snackbar.showSnackbar("Renamed") }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MediumExpressiveShape,
                    enabled = newFileName.trim().isNotEmpty()
                ) {
                    Text("RENAME", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCEL", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                }
            }
        )
    }

    // ── Delete confirm ──
    deletingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deletingFile = null },
            shape = SquircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("DELETE PDF?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text("\"${file.displayTitle}\" will be permanently deleted.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        viewModel.deleteFile(file)
                        deletingFile = null
                        haptic.click()
                        scope.launch { snackbar.showSnackbar("Deleted") }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MediumExpressiveShape
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("DELETE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFile = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── Attach to note ──
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
private fun VaultLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ToolzWavyCircularProgressIndicator(Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "SCANNING VAULT", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun VaultError(message: String, onRetry: () -> Unit, onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Text("SOMETHING WENT WRONG", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row {
                ToolzExpressiveButton(onClick = onRetry, shape = MediumExpressiveShape) {
                    Text("RETRY", fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                ToolzExpressiveButton(onClick = onImport, shape = MediumExpressiveShape) {
                    Text("IMPORT", fontWeight = FontWeight.Black)
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("ATTACH TO NOTE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "\"${file.displayTitle}\"" + if (viewModel.uiState.collectAsStateWithLifecycle().value is PdfUiState.Viewer) " · page ${currentPage + 1}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (notes.isEmpty()) {
                Text(
                    "No notes yet — create one in Notepad first, then attach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { note ->
                        androidx.compose.material3.Surface(
                            shape = MediumExpressiveShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        note.title.ifBlank { "Untitled" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        note.content.take(80),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                ToolzExpressiveButton(
                                    onClick = {
                                        if (busy) return@ToolzExpressiveButton
                                        busy = true
                                        scope.launch {
                                            val page = if (viewModel.uiState.value is PdfUiState.Viewer) currentPage else 0
                                            val ok = viewModel.attachPdfToNote(note.id, file, page)
                                            busy = false
                                            haptic.click()
                                            if (ok) {
                                                onDone("Attached — opens at p.${page + 1}")
                                                onOpenNote(note.id)
                                            } else {
                                                onDone("Note already has 10 PDFs")
                                            }
                                        }
                                    },
                                    shape = MediumExpressiveShape,
                                    enabled = !busy
                                ) {
                                    Text("ATTACH", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
