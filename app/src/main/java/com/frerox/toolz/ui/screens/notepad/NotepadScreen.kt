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

package com.frerox.toolz.ui.screens.notepad

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.frerox.toolz.ui.components.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.PdfAttachResult
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.media.isCurrentNoteTrack
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

// ─────────────────────────────────────────────────────────────
//  Colour utilities
// ─────────────────────────────────────────────────────────────

private fun isDark(color: Color): Boolean {
    val lum = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return (1.0 - lum) >= 0.5
}

private fun noteContentColor(noteColor: Color) =
    if (isDark(noteColor)) Color.White else Color.Black

/** Opaque surface color for a note: 0 = legacy "no color" (transparent black)
 *  and any legacy ARGB with alpha < 255 must never render as transparent. */
private fun noteSurfaceColor(color: Int, fallback: Color): Color =
    if (color == 0) fallback else Color(color).copy(alpha = 1f)

// ─────────────────────────────────────────────────────────────
//  Constants & enums
// ─────────────────────────────────────────────────────────────

private const val NOTE_CARD_SIZE_AUTO   = "AUTO"
private const val NOTE_CARD_SIZE_SMALL  = "SMALL"
private const val NOTE_CARD_SIZE_MEDIUM = "MEDIUM"
private const val NOTE_CARD_SIZE_LARGE  = "LARGE"

private enum class NoteSort { DATE, TITLE, COLOR }

private enum class NotepadActionState { TOOLBAR, EDITOR, FULL_EDITOR, AI_TOOLS, SELECTION, VIEWER }

// ─────────────────────────────────────────────────────────────
//  Data models
// ─────────────────────────────────────────────────────────────

private data class ImageLayoutHint(val width: Int, val height: Int) {
    val aspectRatio: Float get() = if (height == 0) 1f else width.toFloat() / height.toFloat()
    val isLandscape: Boolean get() = aspectRatio > 1.15f
    val isLarge: Boolean get() = width * height >= 1_500_000
}

private data class NoteCardStyle(
    val span: Int,
    val minHeight: Dp,
    val imageHeight: Dp,
    val shape: RoundedCornerShape,
)

// ─────────────────────────────────────────────────────────────
//  Card-options bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCardOptionsSheet(
    note: Note,
    onDismiss: () -> Unit,
    onSizeSelected: (String) -> Unit,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val haptic    = rememberToolzHapticFeedback()
    val noteColor = noteSurfaceColor(note.color, MaterialTheme.colorScheme.surfaceContainerHighest)
    val onColor   = noteContentColor(noteColor)
    val sizeOptions = listOf(
        Triple(NOTE_CARD_SIZE_AUTO,   stringResource(R.string.st_NotepadScreen_auto51),   stringResource(R.string.st_NotepadScreen_ades52)),
        Triple(NOTE_CARD_SIZE_SMALL,  stringResource(R.string.st_NotepadScreen_s2),       stringResource(R.string.st_NotepadScreen_cqs3)),
        Triple(NOTE_CARD_SIZE_MEDIUM, stringResource(R.string.st_NotepadScreen_m4),       stringResource(R.string.st_NotepadScreen_blfen5)),
        Triple(NOTE_CARD_SIZE_LARGE,  stringResource(R.string.st_NotepadScreen_l6),       stringResource(R.string.st_NotepadScreen_ewfrm7)),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = noteColor,
        contentColor     = onColor,
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp, 4.dp).background(onColor.copy(0.25f), CircleShape))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Label + title
            Text(
                stringResource(R.string.st_NotepadScreen_cl1),
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Black,
                letterSpacing = 2.sp,
                color         = onColor.copy(0.7f),
            )
            Text(
                note.title.ifBlank { stringResource(R.string.st_NotepadScreen_u28) },
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color      = onColor,
            )

            Spacer(Modifier.height(4.dp))

            // Size options
            sizeOptions.forEach { (value, label, desc) ->
                val selected = note.cardSize.uppercase() == value
                val scale by animateFloatAsState(
                    targetValue   = if (selected) 1f else 0.98f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                    label         = "optionScale",
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .bouncyClick(scaleDown = 0.97f) {
                            haptic.click()
                            onSizeSelected(value)
                        },
                    shape  = LargeExpressiveShape,
                    color  = onColor.copy(if (selected) 0.22f else 0.12f),
                    border = BorderStroke(1.5.dp, onColor.copy(if (selected) 0.4f else 0.18f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color      = onColor,
                            )
                            Text(
                                desc,
                                style  = MaterialTheme.typography.bodySmall,
                                color  = onColor.copy(0.7f),
                            )
                        }
                        AnimatedVisibility(
                            visible = selected,
                            enter   = scaleIn(spring(0.5f, Spring.StiffnessMediumLow)) + fadeIn(),
                            exit    = scaleOut() + fadeOut(),
                        ) {
                            Surface(color = onColor.copy(0.18f), shape = CircleShape) {
                                Icon(
                                    Icons.Rounded.Check, null,
                                    Modifier.padding(4.dp).size(16.dp),
                                    tint = onColor,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = onColor.copy(0.1f))

            // Action row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(
                    Triple(Icons.Rounded.SelectAll, stringResource(R.string.st_NotepadScreen_sel8),    onSelect),
                    Triple(Icons.Rounded.ContentCopy, stringResource(R.string.st_NotepadScreen_dup9), onDuplicate),
                ).forEach { (icon, text, action) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .bouncyClick { haptic.click(); action() },
                        shape  = MediumExpressiveShape,
                        color  = onColor.copy(0.14f),
                        border = BorderStroke(1.dp, onColor.copy(0.18f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(icon, null, Modifier.size(18.dp), tint = onColor)
                            Text(
                                text,
                                style      = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color      = onColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Image layout hints
// ─────────────────────────────────────────────────────────────

@Composable
private fun rememberImageLayoutHints(notes: List<Note>): Map<String, ImageLayoutHint> {
    val context   = LocalContext.current
    val hints     = remember { mutableStateMapOf<String, ImageLayoutHint>() }
    val imageUris = remember(notes) { notes.mapNotNull { it.attachedImageUri }.distinct() }

    LaunchedEffect(imageUris) {
        imageUris.forEach { uri ->
            if (hints.containsKey(uri)) return@forEach
            val hint = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(input, null, options)
                        if (options.outWidth > 0 && options.outHeight > 0)
                            ImageLayoutHint(options.outWidth, options.outHeight)
                        else null
                    }
                } catch (_: Exception) { null }
            }
            if (hint != null) hints[uri] = hint
        }
    }
    return hints
}

/** Stable internal category keys ("All", "Pinned", …) render localized. */
private fun categoryLabelRes(category: String): Int = when (category) {
    "Pinned" -> R.string.st_NotepadScreen_catpin55
    "Audio"  -> R.string.st_NotepadScreen_cataud56
    "PDFs"   -> R.string.st_NotepadScreen_catpdf57
    "Images" -> R.string.st_NotepadScreen_catimg58
    else     -> R.string.st_NotepadScreen_catall54
}

private fun resolveNoteCardStyle(note: Note, imageHint: ImageLayoutHint?): NoteCardStyle {
    val requestedSize = note.cardSize.uppercase(Locale.getDefault())
    val baseShape = when {
        note.attachedAudioUri != null -> RoundedCornerShape(36.dp, 28.dp, 36.dp, 28.dp)
        note.attachedImageUri != null -> RoundedCornerShape(32.dp, 20.dp, 32.dp, 20.dp)
        note.attachedPdfUri   != null -> RoundedCornerShape(24.dp)
        else                          -> RoundedCornerShape(32.dp)
    }
    return when (requestedSize) {
        NOTE_CARD_SIZE_SMALL  -> NoteCardStyle(1, 180.dp, 112.dp, baseShape)
        NOTE_CARD_SIZE_MEDIUM -> NoteCardStyle(1, 228.dp, 148.dp, baseShape)
        NOTE_CARD_SIZE_LARGE  -> NoteCardStyle(
            span        = 2,
            minHeight   = 260.dp,
            imageHeight = if (imageHint?.isLandscape == true) 230.dp else 200.dp,
            shape       = baseShape,
        )
        else -> when {
            note.attachedAudioUri != null ->
                NoteCardStyle(2, 240.dp, 160.dp, baseShape)
            note.attachedImageUri != null && (imageHint?.isLandscape == true || imageHint?.isLarge == true) ->
                NoteCardStyle(2, 246.dp, if (imageHint.isLandscape) 230.dp else 200.dp, baseShape)
            note.attachedPdfUri != null ->
                NoteCardStyle(1, 220.dp, 108.dp, baseShape)
            note.content.length > 240 ->
                NoteCardStyle(2, 230.dp, 140.dp, baseShape)
            else ->
                NoteCardStyle(1, 200.dp, 132.dp, baseShape)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(
    viewModel     : NotepadViewModel,
    onBack        : () -> Unit,
    onPlayAudio   : (String) -> Unit,
    onViewPdf     : (String, Int) -> Unit,
    initialNoteId : Int? = null,
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val notes           by viewModel.notes.collectAsStateWithLifecycle()
    val deletedNotes     by viewModel.deletedNotes.collectAsStateWithLifecycle()
    val availablePdfs    by viewModel.availablePdfs.collectAsStateWithLifecycle()
    val musicState      by musicViewModel.uiState.collectAsStateWithLifecycle()
    // Perf: never collect playbackPosition here — it ticks ~10 Hz and would
    // recompose the whole screen + grid. Playing cards collect it in
    // isolation via CurrentTrackProgress below.
    val offlineMode     by viewModel.offlineModeEnabled.collectAsStateWithLifecycle(false)
    val notepadAiEnabled by viewModel.notepadAiEnabled.collectAsStateWithLifecycle(true)
    val performanceMode = LocalPerformanceMode.current
    val haptic          = rememberToolzHapticFeedback()
    val context         = LocalContext.current

    var viewedNoteId     by rememberSaveable { mutableStateOf<Int?>(null) }
    var editedNoteId     by rememberSaveable { mutableStateOf<Int?>(null) }
    var quickNoteDraft   by rememberSaveable { mutableStateOf<Note?>(null) }
    var isCreatingNote   by rememberSaveable { mutableStateOf(false) }
    var noteOptionsId    by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchQuery      by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    var selectedNoteIds  by remember { mutableStateOf(setOf<Int>()) }
    var forceSelectionMode by remember { mutableStateOf(false) }
    val isSelectionMode  by remember { derivedStateOf { selectedNoteIds.isNotEmpty() || forceSelectionMode } }
    var noteSort         by remember { mutableStateOf(NoteSort.DATE) }
    var showSortMenu     by remember { mutableStateOf(false) }
    var actionState      by rememberSaveable { mutableStateOf(NotepadActionState.TOOLBAR) }
    var showTrashSheet   by rememberSaveable { mutableStateOf(false) }
    
    // Deleting animation set — single-slot caused rapid-delete races.
    var deletingNoteIds by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(viewedNoteId) {
        if (viewedNoteId != null) {
            actionState = NotepadActionState.VIEWER
        } else if (actionState == NotepadActionState.VIEWER) {
            actionState = NotepadActionState.TOOLBAR
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope    = rememberCoroutineScope()
    var hasEntered by remember { mutableStateOf(false) }
    
    val noteDeletedMsg = stringResource(R.string.st_NotepadScreen_nd10)
    val undoLabel = stringResource(R.string.st_NotepadScreen_u11)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var lastPickedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val persistedUri = viewModel.persistImage(context, it)
                if (persistedUri != null) {
                    lastPickedImageUri = Uri.parse(persistedUri)
                }
            }
        }
    }

    val viewedNote = remember(notes, viewedNoteId) {
        viewedNoteId?.let { id -> notes.firstOrNull { it.id == id } }
    }
    val editedExistingNote = remember(notes, editedNoteId) {
        editedNoteId?.let { id -> notes.firstOrNull { it.id == id } }
    }
    val noteOptionsNote = remember(notes, noteOptionsId) {
        noteOptionsId?.let { id -> notes.firstOrNull { it.id == id } }
    }
    val imageHints             = rememberImageLayoutHints(notes)
    val allowEntranceAnimation = !performanceMode && notes.size <= 18

    // Entrance animations
    val topBarEntrance by animateFloatAsState(
        targetValue   = if (hasEntered) 1f else 0f,
        animationSpec = tween(if (allowEntranceAnimation) 260 else 0, easing = FastOutSlowInEasing),
        label         = "topBarEntrance",
    )
    val contentEntrance by animateFloatAsState(
        targetValue   = if (hasEntered) 1f else 0f,
        animationSpec = tween(if (allowEntranceAnimation) 320 else 0, 50, FastOutSlowInEasing),
        label         = "contentEntrance",
    )
    val fabEntrance by animateFloatAsState(
        targetValue   = if (hasEntered) 1f else 0f,
        animationSpec = tween(if (allowEntranceAnimation) 280 else 0, 80, FastOutSlowInEasing),
        label         = "fabEntrance",
    )

    LaunchedEffect(Unit) { hasEntered = true }

    // ── Back Gesture Handling ─────────────────────────────────────
    BackHandler(
        enabled = viewedNoteId != null || editedNoteId != null || isSelectionMode || 
                 actionState != NotepadActionState.TOOLBAR
    ) {
        when {
            viewedNoteId != null -> viewedNoteId = null
            editedNoteId != null -> editedNoteId = null
            actionState == NotepadActionState.AI_TOOLS -> {
                // If AI Tools has its own internal state, it's handled inside AiToolsPopup via onDismiss
                // But as a fallback/unified way:
                if (isSelectionMode) actionState = NotepadActionState.SELECTION
                else if (viewedNoteId != null) actionState = NotepadActionState.VIEWER
                else actionState = NotepadActionState.TOOLBAR
            }
            actionState == NotepadActionState.EDITOR || actionState == NotepadActionState.FULL_EDITOR -> {
                quickNoteDraft = null
                actionState = NotepadActionState.TOOLBAR
            }
            isSelectionMode -> {
                selectedNoteIds = emptySet()
                forceSelectionMode = false
            }
            else -> onBack()
        }
    }

    var initialNoteConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialNoteId) {
        if (!initialNoteConsumed && initialNoteId != null) {
            viewedNoteId = initialNoteId
            initialNoteConsumed = true
        }
    }
    // Only clear when the DB has loaded (non-empty at some point) and the
    // note is genuinely gone — never on transient empty during refresh.
    var notesEverLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(notes) { if (notes.isNotEmpty()) notesEverLoaded = true }
    LaunchedEffect(viewedNoteId, viewedNote, notesEverLoaded) {
        if (viewedNoteId != null && viewedNote == null && notesEverLoaded) viewedNoteId = null
    }
    LaunchedEffect(editedNoteId, editedExistingNote, notesEverLoaded) {
        if (editedNoteId != null && editedExistingNote == null && notesEverLoaded) editedNoteId = null
    }

    var selectedCategory by remember { mutableStateOf("All") }

    val categoryIcons = remember {
        mapOf(
            "All"    to Icons.Rounded.Notes,
            "Pinned" to Icons.Rounded.PushPin,
            "Audio"  to Icons.Rounded.Headphones,
            "PDFs"   to Icons.Rounded.Description,
            "Images" to Icons.Rounded.Image,
        )
    }

    val categories = remember(notes) {
        listOf(
            "All"    to notes.size,
            "Pinned" to notes.count { it.isPinned },
            "Audio"  to notes.count { it.attachedAudioUri != null },
            "PDFs"   to notes.count { it.attachedPdfUri   != null },
            "Images" to notes.count { it.attachedImageUri != null },
        )
    }

    val trimmedQuery = remember(searchQuery) { searchQuery.trim() }
    // Attachment-aware category counts: V2 rows or legacy slots count.
    val attachmentNoteIds = remember(notes) { emptySet<Int>() } // filled per-card via V2; legacy fallback below
    val filteredNotes = remember(notes, trimmedQuery, selectedCategory, noteSort) {
        val q = trimmedQuery
        notes
            .filter {
                (q.isEmpty() || it.title.contains(q, true) || it.content.contains(q, true)) &&
                        when (selectedCategory) {
                            "Pinned" -> it.isPinned
                            "Audio"  -> it.attachedAudioUri != null
                            "PDFs"   -> it.attachedPdfUri   != null
                            "Images" -> it.attachedImageUri != null
                            else     -> true
                        }
            }
            .sortedWith(when (noteSort) {
                NoteSort.DATE  -> compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt.takeIf { u -> u != 0L } ?: it.timestamp }
                NoteSort.TITLE -> compareByDescending<Note> { it.isPinned }.thenBy { it.title.lowercase(java.util.Locale.ROOT) }
                NoteSort.COLOR -> compareByDescending<Note> { it.isPinned }.thenBy { it.color }
            })
    }

    val allowCategoryAnimation = !performanceMode && filteredNotes.size <= 24

    LaunchedEffect(isSelectionMode) {
        if (isSelectionMode) {
            if (actionState != NotepadActionState.AI_TOOLS) {
                actionState = NotepadActionState.SELECTION
            }
        } else {
            if (actionState == NotepadActionState.SELECTION) {
                actionState = NotepadActionState.TOOLBAR
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost   = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        floatingActionButtonPosition = FabPosition.Center,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        floatingActionButton = {
            NotepadActionComponent(
                state = actionState,
                onStateChange = { actionState = it },
                viewModel = viewModel,
                notes = notes,
                selectedNotes = notes.filter { it.id in selectedNoteIds },
                onClearSelection = { 
                    selectedNoteIds = emptySet()
                    forceSelectionMode = false
                },
                initialDraft = viewedNote ?: quickNoteDraft,
                onDraftChange = { 
                    if (viewedNoteId != null) {
                        if (it == null) viewedNoteId = null
                    } else {
                        quickNoteDraft = it 
                    }
                },
                onSearchClick = {
                    searchFocusRequester.requestFocus()
                },
                onNoteGenerated = { gen ->
                    gen?.let {
                        viewModel.addNote(
                            title = it.title,
                            content = it.content,
                            color = com.frerox.toolz.data.notepad.NoteColors.parseHexOrNull(it.colorHex)?.let { c -> c } ?: android.graphics.Color.parseColor(com.frerox.toolz.data.notepad.NoteColors.FALLBACK_HEX),
                            fontSize = it.fontSize,
                            isBold = it.isBold,
                            isItalic = it.isItalic
                        ) { id -> viewedNoteId = id }
                    }
                },
                onNoteEdited = { original, gen ->
                    gen?.let {
                        viewModel.updateNote(original.copy(
                            title = it.title,
                            content = it.content,
                            color = com.frerox.toolz.data.notepad.NoteColors.parseHexOrNull(it.colorHex)?.let { c -> c } ?: android.graphics.Color.parseColor(com.frerox.toolz.data.notepad.NoteColors.FALLBACK_HEX),
                            fontSize = it.fontSize,
                            isBold = it.isBold,
                            isItalic = it.isItalic,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                },
                onDeleteRequest = { note ->
                    haptic.longClick()
                    deletingNoteIds = deletingNoteIds + note.id
                    viewedNoteId = null
                    scope.launch {
                        delay(400)
                        viewModel.deleteNote(note)
                        // Keep undo snackbar consistent with card path.
                        val res = snackbar.showSnackbar(noteDeletedMsg, undoLabel, duration = SnackbarDuration.Short)
                        if (res == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                        deletingNoteIds = deletingNoteIds - note.id
                    }
                },
                availableTracks = musicState.tracks,
                availablePdfs = availablePdfs,
                onSelectAll = {
                    selectedNoteIds = filteredNotes.map { it.id }.toSet()
                },
                onImagePickRequest = { imagePickerLauncher.launch("image/*") },
                newImageUri = lastPickedImageUri,
                onImageConsumed = { lastPickedImageUri = null },
                onViewPdf = onViewPdf,
                viewedNoteId = viewedNoteId,
                onNoteOptionsRequest = { noteOptionsId = it },
                offlineMode = offlineMode,
                aiToolsEnabled = notepadAiEnabled
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .toolzBackground()
                .graphicsLayer {
                    alpha        = contentEntrance
                    translationY = if (allowEntranceAnimation) (1f - contentEntrance) * 18.dp.toPx() else 0f
                }
        ) {
            Column(Modifier.fillMaxSize()) {
                // ── Top Bar Content moved inside Box to apply darkening ──
                Column(
                    Modifier
                        .graphicsLayer {
                            alpha = topBarEntrance
                            translationY =
                                if (allowEntranceAnimation) (1f - topBarEntrance) * -20.dp.toPx() else 0f
                        }
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface.copy(0.98f),
                                    MaterialTheme.colorScheme.surface.copy(0.85f),
                                    Color.Transparent,
                                )
                            )
                        )
                ) {
                    val appbarTitle = when {
                        isSelectionMode -> pluralStringResource(R.plurals.st_NotepadScreen_selcnt60, selectedNoteIds.size, selectedNoteIds.size)
                        viewedNoteId != null -> viewedNote?.title ?: stringResource(R.string.st_NotepadScreen_d29)
                        else -> stringResource(R.string.st_NotepadScreen_np12)
                    }
                    val appbarSubtitle = when {
                        isSelectionMode -> stringResource(R.string.st_NotepadScreen_sntta13)
                        viewedNoteId != null -> {
                            val config = androidx.compose.ui.platform.LocalConfiguration.current
                            remember(viewedNote?.timestamp, config) {
                                val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                    config.locales[0]
                                } else {
                                    config.locale
                                }
                                SimpleDateFormat("MMMM d", locale).format(Date(viewedNote?.timestamp ?: 0))
                            }
                        }
                        else -> stringResource(R.string.st_NotepadScreen_cyt14)
                    }

                    ExpressiveTopAppBar(
                        title = appbarTitle,
                        subtitle = appbarSubtitle,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            ToolzExpressiveIconButton(
                                onClick = {
                                    if (isSelectionMode) {
                                    selectedNoteIds = emptySet()
                                    forceSelectionMode = false
                                } else if (viewedNoteId != null) {
                                        viewedNoteId = null
                                    } else {
                                        onBack()
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.45f),
                                    contentColor = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(8.dp)
                            ) {
                                val navIcon = when {
                                    isSelectionMode || viewedNoteId != null -> Icons.Rounded.Close
                                    else -> Icons.AutoMirrored.Rounded.ArrowBack
                                }
                                Icon(
                                    navIcon,
                                    contentDescription = stringResource(R.string.st_NotepadScreen_cdb53)
                                )
                            }
                        },
                        actions = {
                            if (!isSelectionMode && viewedNoteId == null) {
                                ToolzExpressiveIconButton(
                                    onClick = { 
                                        haptic.click()
                                        forceSelectionMode = !forceSelectionMode
                                        if (!forceSelectionMode) selectedNoteIds = emptySet()
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (forceSelectionMode) MaterialTheme.colorScheme.primary.copy(0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                        contentColor = if (forceSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Checklist, stringResource(R.string.st_NotepadScreen_sel8), 
                                        Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box {
                                    ToolzExpressiveIconButton(
                                        onClick = { haptic.tick(); showSortMenu = true },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Sort,
                                            stringResource(R.string.st_NotepadScreen_sort_icon16),
                                            Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        shape = LargeExpressiveShape,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        listOf(
                                            NoteSort.DATE to stringResource(R.string.st_NotepadScreen_dm17),
                                            NoteSort.TITLE to stringResource(R.string.st_NotepadScreen_taz18),
                                            NoteSort.COLOR to stringResource(R.string.st_NotepadScreen_bc19),
                                        ).forEach { (sort, label) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        label,
                                                        fontWeight = if (noteSort == sort) FontWeight.Black else FontWeight.Normal,
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                },
                                                leadingIcon = {
                                                    if (noteSort == sort)
                                                        Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                    else
                                                        Spacer(Modifier.size(20.dp))
                                                },
                                                onClick = {
                                                    haptic.click()
                                                    noteSort = sort
                                                    showSortMenu = false
                                                },
                                                modifier = Modifier.expressivePressScale(remember { MutableInteractionSource() }, enabled = true)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = { haptic.click(); showTrashSheet = true },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(MediumExpressiveShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                                        .expressivePressScale(remember { MutableInteractionSource() }, enabled = true)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        stringResource(R.string.st_NotepadScreen_trash20),
                                        Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                        largeFlexible = true,
                        modifier = Modifier.statusBarsPadding(),
                    )

                    // ── Search bar (only in toolbar mode) ────────────────────
                    AnimatedVisibility(visible = !isSelectionMode && viewedNoteId == null) {
                        Column {
                            ExpressiveSearchField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                                    .focusRequester(searchFocusRequester),
                                placeholder = {
                                    Text(
                                        stringResource(R.string.st_NotepadScreen_syt_hint21),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Search, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        ToolzExpressiveIconButton(
                                            onClick = { haptic.tick(); searchQuery = "" },
                                            modifier = Modifier.size(32.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        ) {
                                            Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                                        }
                                    }
                                } else null,
                            )

                            // ── Category filter chips ───────────────────────
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .horizontalFadingEdges(left = 8.dp, right = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                categories.forEach { (category, count) ->
                                    val selected = selectedCategory == category
                                    ExpressiveFilterChip(
                                        selected = selected,
                                        onClick = { selectedCategory = category },
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            ) {
                                                Text(
                                                    stringResource(categoryLabelRes(category)),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                                                )
                                                AnimatedContent(
                                                    targetState = count,
                                                    transitionSpec = {
                                                        fadeIn(tween(160)) togetherWith fadeOut(
                                                            tween(120)
                                                        )
                                                    },
                                                    label = "categoryCount",
                                                ) { targetCount ->
                                                    Surface(
                                                        color = if (selected)
                                                            MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                                                0.18f
                                                            )
                                                        else
                                                            MaterialTheme.colorScheme.onSurface.copy(
                                                                0.1f
                                                            ),
                                                        shape = CircleShape,
                                                    ) {
                                                        Text(
                                                            "$targetCount",
                                                            modifier = Modifier.padding(
                                                                horizontal = 6.dp,
                                                                vertical = 1.dp
                                                            ),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Black,
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                categoryIcons[category] ?: Icons.Rounded.Notes,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        shape = SmallExpressiveShape,
                                    )
                                }
                            }
                        }
                    }
                }

                Crossfade(
                    targetState  = selectedCategory,
                    animationSpec = tween(if (allowCategoryAnimation) 200 else 0),
                    label        = "categorySwitch",
                    modifier = Modifier.weight(1f)
                ) { targetCategory ->
                    if (filteredNotes.isEmpty()) {
                        NotesEmptyState(
                            isSearching      = searchQuery.isNotBlank(),
                            selectedCategory = targetCategory,
                        )
                    } else {
                        LazyVerticalGrid(
                            columns         = GridCells.Fixed(2),
                            modifier        = Modifier.fillMaxSize().fadingEdges(top = 8.dp, bottom = 12.dp),
                            contentPadding  = PaddingValues(
                                start      = 20.dp,
                                end        = 20.dp,
                                top        = 12.dp,
                                bottom     = padding.calculateBottomPadding() + 96.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement   = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(
                                items = filteredNotes,
                                key   = { _, note -> note.id },
                                span  = { _, note ->
                                    GridItemSpan(
                                        resolveNoteCardStyle(
                                            note      = note,
                                            imageHint = note.attachedImageUri?.let(imageHints::get),
                                        ).span
                                    )
                                },
                            ) { index, note ->
                                val isCurrentTrack = note.attachedAudioUri?.let { isCurrentNoteTrack(musicState.currentTrack, it) } == true
                                val cardStyle = resolveNoteCardStyle(
                                    note      = note,
                                    imageHint = note.attachedImageUri?.let(imageHints::get),
                                )
                                StaggeredEntrance(index = index) {
                                    CurrentTrackProgress(
                                        isCurrent = isCurrentTrack,
                                        durationMs = musicState.duration,
                                        flow = musicViewModel.playbackPosition,
                                    ) { inlineProgress, livePos ->
                                    NoteCard(
                                        note                  = note,
                                        cardStyle             = cardStyle,
                                        allTracks             = musicState.tracks,
                                        isSelected            = note.id in selectedNoteIds,
                                        isSelectionMode       = isSelectionMode,
                                        isPlaying             = musicState.isPlaying && isCurrentTrack,
                                        isCurrentTrack        = isCurrentTrack,
                                        currentTrackThumbnail = musicState.currentTrack?.thumbnailUri,
                                        inlineProgress        = inlineProgress,
                                        audioPositionMs       = livePos,
                                        audioDurationMs       = musicState.duration,
                                        onSeekAudio           = { frac ->
                                            val d = musicViewModel.uiState.value.duration
                                            if (d > 0L) musicViewModel.seekTo((frac * d).toLong())
                                        },
                                        isDeleting            = note.id in deletingNoteIds,
                                        onClick = {
                                            haptic.click()
                                            if (isSelectionMode) {
                                                if (note.id in selectedNoteIds) {
                                                    selectedNoteIds = selectedNoteIds - note.id
                                                } else {
                                                    selectedNoteIds = selectedNoteIds + note.id
                                                }
                                            } else {
                                                viewedNoteId = note.id
                                            }
                                        },
                                        onLongClick = {
                                            haptic.longClick()
                                            forceSelectionMode = true
                                            if (note.id in selectedNoteIds) {
                                                selectedNoteIds = selectedNoteIds - note.id
                                            } else {
                                                selectedNoteIds = selectedNoteIds + note.id
                                            }
                                        },
                                        onDelete = {
                                            if (!isSelectionMode) {
                                                haptic.longClick()
                                                deletingNoteIds = deletingNoteIds + note.id
                                                scope.launch {
                                                    delay(400)
                                                    viewModel.deleteNote(note)
                                                    deletingNoteIds = deletingNoteIds - note.id
                                                    val r = snackbar.showSnackbar(
                                                        noteDeletedMsg,
                                                        actionLabel = undoLabel,
                                                        duration    = SnackbarDuration.Short,
                                                    )
                                                    if (r == SnackbarResult.ActionPerformed) {
                                                        haptic.tick()
                                                        viewModel.undoDelete()
                                                    }
                                                }
                                            }
                                        },
                                        onTogglePin = {
                                            if (!isSelectionMode) {
                                                haptic.tick()
                                                viewModel.togglePin(note)
                                            }
                                        },
                                        onPlayAudio = {
                                            haptic.click()
                                            note.attachedAudioUri?.let { onPlayAudio(it) }
                                        },
                                        onViewPdf = {
                                            haptic.click()
                                            note.attachedPdfUri?.let { onViewPdf(it, 0) }
                                        },
                                        isHidden = viewedNoteId == note.id,
                                        modifier = Modifier
                                            .graphicsLayer {
                                                alpha = if (viewedNoteId == note.id) 0f else 1f
                                            }
                                            .animateItem(
                                                fadeInSpec     = if (allowCategoryAnimation) tween(200) else snap(),
                                                fadeOutSpec    = if (allowCategoryAnimation) tween(150) else snap(),
                                                placementSpec  = if (allowCategoryAnimation) spring(
                                                    dampingRatio = 0.8f,
                                                    stiffness    = Spring.StiffnessMediumLow,
                                                ) else snap(),
                                            ),
                                    )
                                    } // CurrentTrackProgress
                                }
                            }
                        }
                    }
                }
            }

            // ── Viewer Floating Box ──────────────────────────────────────────
            val viewerNoteOffset by animateDpAsState(
                targetValue = if (actionState == NotepadActionState.AI_TOOLS) (-120).dp else (-40).dp,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
                label = "viewerNoteOffset"
            )

            // Hero Animation State
            val heroTransitionSpec = tween<Float>(600, easing = FastOutSlowInEasing)
            val expansionAlpha by animateFloatAsState(
                targetValue = if (viewedNoteId != null) 1f else 0f,
                animationSpec = heroTransitionSpec,
                label = "expansionAlpha"
            )
            
            val expansionScale by animateFloatAsState(
                targetValue = if (viewedNoteId != null) 1f else 0.85f,
                animationSpec = heroTransitionSpec,
                label = "expansionScale"
            )

            if (viewedNoteId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.82f * expansionAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewedNoteId = null
                        }
                ) {
                    viewedNote?.let { note ->
                        val noteColor = if (note.color == 0) MaterialTheme.colorScheme.surfaceContainerHighest else Color(note.color)
                        val onColor = if (note.color == 0) MaterialTheme.colorScheme.onSurface else noteContentColor(noteColor)
                        
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = viewerNoteOffset)
                .fillMaxWidth(0.92f)
                .heightIn(max = 700.dp)
                .scale(expansionScale)
                .alpha(expansionAlpha)
                .shadow(
                    elevation = 32.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(0.4f),
                    spotColor = Color.Black.copy(0.4f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = noteColor,
            contentColor = onColor,
            border = BorderStroke(2.dp, onColor.copy(0.12f))
        ) {
                            Column(
                                Modifier
                                    .padding(24.dp)
                                    .fadingEdges(top = 4.dp, bottom = 4.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    SimpleDateFormat("MMMM d, yyyy · HH:mm", Locale.getDefault())
                                        .format(Date(note.timestamp)).uppercase(java.util.Locale.ROOT),
                                    style         = MaterialTheme.typography.labelSmall,
                                    color         = onColor.copy(0.38f),
                                    fontWeight    = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    modifier      = Modifier.padding(bottom = 16.dp),
                                )

                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(bottom = 18.dp)
                                        ) {
                                            Text(
                                                note.title.ifEmpty { stringResource(R.string.st_NotepadScreen_u28) },
                                                style      = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Black,
                                                color      = onColor,
                                                modifier   = Modifier.weight(1f),
                                            )
                                            if (note.isPinned) {
                                                Surface(
                                                    color = onColor.copy(0.12f),
                                                    shape = SmallExpressiveShape,
                                                    modifier = Modifier.size(30.dp),
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Rounded.PushPin, contentDescription = stringResource(R.string.st_NotepadScreen_catpin55),
                                                            Modifier.size(15.dp).graphicsLayer { rotationZ = -20f },
                                                            tint = onColor.copy(0.8f),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Attachments (legacy previews + V2 strip rendered
                                // independently — a V2-only attach must still show).
                                if (note.attachedAudioUri != null || note.attachedPdfUri != null || note.attachedImageUri != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                                        note.attachedImageUri?.let { uri ->
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = stringResource(R.string.st_NotepadScreen_cdnimg65),
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(16.dp)),
                                                contentScale = ContentScale.FillWidth
                                            )
                                        }
                                        note.attachedPdfUri?.let { uri ->
                                            PdfPreview(uri = uri, onClick = { haptic.click(); onViewPdf(uri, 0) }, modifier = Modifier.height(150.dp))
                                        }
                                        note.attachedAudioUri?.let { uri ->
                                            val track = musicState.tracks.find { it.uri == uri }
                                            val isCurrent = isCurrentNoteTrack(musicState.currentTrack, uri)
                                            val playing = musicState.isPlaying && isCurrent
                                            CurrentTrackProgress(
                                                isCurrent = isCurrent,
                                                durationMs = musicState.duration,
                                                flow = musicViewModel.playbackPosition,
                                            ) { prog, livePos ->
                                            // Inline preview — taps toggle play/pause here, no navigation.
                                            MusicPill(
                                                title = note.attachedAudioName ?: stringResource(R.string.st_NotepadScreen_ma38),
                                                isPlaying = playing,
                                                isCurrentTrack = isCurrent,
                                                thumbnail = track?.thumbnailUri,
                                                artist = track?.artist,
                                                containerColor = onColor.copy(0.1f),
                                                contentColor = onColor,
                                                onClick = { onPlayAudio(uri) },
                                                progress = prog,
                                                positionMs = livePos,
                                                durationMs = musicState.duration,
                                                onSeek = { frac ->
                                                    val d = musicViewModel.uiState.value.duration
                                                    if (d > 0L) musicViewModel.seekTo((frac * d).toLong())
                                                },
                                                compact = false,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            }
                                        }
                                    }
                                }
                                // V2 multi-attach strip — outside the legacy-if so
                                // V2-only attaches render (self-hides when empty).
                                NotePdfAttachmentStrip(
                                    viewModel = viewModel,
                                    note = note,
                                    onOpenPdf = onViewPdf,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                                    // Legacy thumbnail card above already shows this URI.
                                    excludeUris = setOfNotNull(note.attachedPdfUri)
                                )

                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        note.content,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = note.fontSize.coerceIn(12f, 28f).sp,
                                            fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            lineHeight = (note.fontSize.coerceIn(12f, 28f) * 1.4f).sp
                                        ),
                                        color = onColor.copy(0.88f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create/Edit handled by NotepadActionComponent for morphing, 
    // but for editing existing notes from viewer, we use a dialog-like overlay with the same editor
    if (editedExistingNote != null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)).padding(16.dp)) {
            FullExpressiveEditor(
                viewModel = viewModel,
                note = editedExistingNote,
                onDismiss = { editedNoteId = null },
                onMinimize = { _ -> editedNoteId = null },
                availableTracks = musicState.tracks,
                availablePdfs = availablePdfs,
                onImagePickRequest = { imagePickerLauncher.launch("image/*") },
                newImageUri = lastPickedImageUri,
                onImageConsumed = { lastPickedImageUri = null },
                onViewPdf = onViewPdf
            )
        }
    }

    var showNoteOptions by remember { mutableStateOf(false) }
    LaunchedEffect(noteOptionsId) {
        showNoteOptions = noteOptionsId != null
    }

    if (showTrashSheet) {
        TrashBottomSheet(
            deletedNotes = deletedNotes,
            onDismiss = { showTrashSheet = false },
            onRestore = { viewModel.restoreNote(it) },
            onDeletePermanently = { viewModel.permanentlyDeleteNote(it) },
            onEmptyTrash = { viewModel.emptyTrash() }
        )
    }

    if (showNoteOptions) {
        noteOptionsNote?.let { note ->
            NoteCardOptionsSheet(
                note           = note,
                onDismiss      = { noteOptionsId = null; showNoteOptions = false },
                onSizeSelected = { selectedSize ->
                    viewModel.updateNoteCardSize(note, selectedSize)
                    noteOptionsId = null
                    showNoteOptions = false
                },
                onSelect = {
                    selectedNoteIds = selectedNoteIds + note.id
                    noteOptionsId   = null
                    showNoteOptions = false
                },
                onDuplicate = {
                    val srcId = note.id
                    viewModel.addNote(
                        title             = note.title,
                        content           = note.content,
                        color             = note.color,
                        fontStyle         = note.fontStyle,
                        fontSize          = note.fontSize,
                        isBold            = note.isBold,
                        isItalic          = note.isItalic,
                        attachedPdfUri    = note.attachedPdfUri,
                        attachedAudioUri  = note.attachedAudioUri,
                        attachedAudioName = note.attachedAudioName,
                        attachedImageUri  = note.attachedImageUri,
                    ) { newId ->
                        if (newId > 0) {
                            scope.launch {
                                try {
                                    // Copy V2 multi-attachments + card size (legacy copy lost them).
                                    val rows = viewModel.listAttachments(srcId)
                                    rows.forEach { r ->
                                        when (r.kind) {
                                            com.frerox.toolz.data.notepad.NoteAttachment.KIND_PDF ->
                                                viewModel.attachPdf(newId, android.net.Uri.parse(r.uri), r.pageHint)
                                            com.frerox.toolz.data.notepad.NoteAttachment.KIND_IMAGE ->
                                                viewModel.attachImage(newId, android.net.Uri.parse(r.uri))
                                            else ->
                                                viewModel.attachAudio(newId, r.uri, r.displayName)
                                        }
                                    }
                                    if (note.cardSize != "AUTO") {
                                        viewModel.getNoteById(newId)?.let { created ->
                                            viewModel.updateNoteCardSize(created, note.cardSize)
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    noteOptionsId = null
                    showNoteOptions = false
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────

@Composable
private fun NotesEmptyState(isSearching: Boolean, selectedCategory: String) {
    val performanceMode = LocalPerformanceMode.current
    val inf = rememberInfiniteTransition(label = "emptyPulse")
    val iconScale by inf.animateFloat(
        1f, 1.06f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "emptyScale",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(40.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(128.dp)
                    .graphicsLayer {
                        scaleX = if (performanceMode) 1f else iconScale
                        scaleY = if (performanceMode) 1f else iconScale
                    },
                shape = ExtraLargeExpressiveShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(0.28f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.12f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isSearching) Icons.Rounded.SearchOff else Icons.Rounded.NoteAdd,
                        null,
                        Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(0.55f),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = when {
                    isSearching        -> stringResource(R.string.st_NotepadScreen_nmf22)
                    selectedCategory != "All" -> stringResource(R.string.st_NotepadScreen_nonyet59, stringResource(categoryLabelRes(selectedCategory)))
                    else               -> stringResource(R.string.st_NotepadScreen_yca24)
                },
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurface,
                textAlign  = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    isSearching -> stringResource(R.string.st_NotepadScreen_tadk25)
                    selectedCategory != "All" -> stringResource(R.string.st_NotepadScreen_stataan26)
                    else        -> stringResource(R.string.st_NotepadScreen_ttbtswyfn27)
                },
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurface.copy(0.38f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Note card
// ─────────────────────────────────────────────────────────────

/**
 * Isolates the ~10 Hz playback ticker: only the currently-playing card
 * subscribes. Non-playing cards call [content] with zeros and never
 * collect, so the grid doesn't recompose 10x/sec.
 */
@Composable
private fun CurrentTrackProgress(
    isCurrent: Boolean,
    durationMs: Long,
    flow: kotlinx.coroutines.flow.Flow<Long>,
    content: @Composable (progress: Float, positionMs: Long) -> Unit,
) {
    if (!isCurrent) {
        content(0f, 0L)
        return
    }
    val live by flow.collectAsStateWithLifecycle(initialValue = 0L)
    val progress = if (durationMs > 0L) (live.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    content(progress, live)
}

@Composable
private fun NoteCard(
    note                  : Note,
    cardStyle             : NoteCardStyle,
    allTracks             : List<com.frerox.toolz.data.music.MusicTrack> = emptyList(),
    isSelected            : Boolean = false,
    isSelectionMode       : Boolean = false,
    isDeleting            : Boolean = false,
    isHidden              : Boolean = false,
    isPlaying             : Boolean,
    isCurrentTrack        : Boolean,
    currentTrackThumbnail : String?,
    inlineProgress        : Float = 0f,
    audioPositionMs       : Long = 0L,
    audioDurationMs       : Long = 0L,
    onSeekAudio           : ((Float) -> Unit)? = null,
    onClick               : () -> Unit,
    onLongClick           : () -> Unit = {},
    onDelete              : () -> Unit,
    onTogglePin           : () -> Unit,
    onPlayAudio           : () -> Unit,
    onViewPdf             : () -> Unit,
    modifier              : Modifier = Modifier,
) {
    val noteColor    = Color(note.color)
    val onColor      = noteContentColor(noteColor)
    val performanceMode = LocalPerformanceMode.current

    val cardScale = remember(note.id) { Animatable(1f) }
    LaunchedEffect(note.cardSize) {
        if (!performanceMode) {
            cardScale.snapTo(0.96f)
            cardScale.animateTo(
                targetValue   = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    val selectionScale by animateFloatAsState(
        targetValue   = if (isSelected) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label         = "selectionScale",
    )

    val wordCount = remember(note.content) { com.frerox.toolz.data.notepad.NoteText.wordCount(note.content) }

    val deleteColor by animateColorAsState(
        targetValue = if (isDeleting) Color.Red.copy(0.6f) else noteColor,
        animationSpec = tween(300),
        label = "deleteColor"
    )

    // Structural fix: NO nested clickables on this card. The outer container is a
    // plain Surface; the content zone and the info zone below each open the viewer
    // via their own tap target, while pin / delete / PDF / audio are siblings with
    // independent targets. A pin tap therefore can never open the viewer.
    Surface(
        modifier    = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = cardScale.value * selectionScale
                scaleX = s
                scaleY = s
                alpha = if (isDeleting || isHidden) 0f else 1f
            }
            .animateContentSize(
                animationSpec = if (performanceMode) tween(0)
                else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ),
        shape          = cardStyle.shape,
        color = deleteColor,
        contentColor   = if (isDeleting) Color.White else onColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = when {
            isSelected -> BorderStroke(3.5.dp, MaterialTheme.colorScheme.primary)
            note.isPinned -> BorderStroke(1.5.dp, onColor.copy(0.28f))
            else -> BorderStroke(1.5.dp, onColor.copy(0.12f))
        },
    ) {
        Box {
            Column(Modifier.defaultMinSize(minHeight = cardStyle.minHeight)) {

                // ── Zone A: opens the viewer ──────────────────────────────
                Box(
                    Modifier.then(
                        if (isDeleting || isHidden) Modifier
                        else Modifier.bouncyClick(onClick = onClick, onLongClick = onLongClick)
                    )
                ) {
                    Column {
                        // ── Top accent strip ────────────────────────────────
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            onColor.copy(0.06f),
                                            onColor.copy(0.14f),
                                            onColor.copy(0.06f),
                                        )
                                    )
                                )
                        )

                        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp)) {

                    // ── Title + pin indicator ───────────────────────────
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                            Text(
                                note.title.ifEmpty { stringResource(R.string.st_NotepadScreen_u28) },
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color      = onColor,
                                modifier   = Modifier.weight(1f),
                                maxLines   = 2,
                                overflow   = TextOverflow.Ellipsis,
                            )
                        if (note.isPinned) {
                            Surface(
                                color    = onColor.copy(0.12f),
                                shape    = SmallExpressiveShape,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.PushPin, null,
                                        Modifier.size(13.dp).graphicsLayer { rotationZ = -20f },
                                        tint = onColor.copy(0.8f),
                                    )
                                }
                            }
                        }
                    }

                    // ── Content preview ─────────────────────────────────
                    if (note.content.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .heightIn(max = 120.dp)
                                .fadingEdges(bottom = 6.dp)
                        ) {
                            Text(
                                note.content,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily    = noteFontFamily(note.fontStyle),
                                    fontWeight    = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle     = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                                    lineHeight    = 18.sp,
                                    letterSpacing = 0.1.sp,
                                ),
                                color    = onColor.copy(0.68f),
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // ── Attached image (no tap action: safe inside Zone A) ──
                    note.attachedImageUri?.let { uri ->
                        Spacer(Modifier.height(14.dp))
                        AsyncImage(
                            model        = uri,
                            contentDescription = stringResource(R.string.st_NotepadScreen_cdnimg65),
                            modifier     = Modifier
                                .fillMaxWidth()
                                .height(cardStyle.imageHeight)
                                .clip(LargeExpressiveShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                        } // Zone A padded column
                    } // Zone A column
                } // Zone A (opens viewer)

                // ── Zone B: attachment actions (own taps, never the viewer) ──
                if (note.attachedPdfUri != null || note.attachedAudioUri != null) {
                    Column(
                        Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        note.attachedPdfUri?.let { uri ->
                            PdfPreview(
                                uri      = uri,
                                onClick  = onViewPdf,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (cardStyle.span == 2) 132.dp else 100.dp)
                                    .clip(MediumExpressiveShape),
                            )
                        }
                        note.attachedAudioUri?.let { uri ->
                            val track = allTracks.find { it.uri == uri }
                            MusicPill(
                                title          = note.attachedAudioName ?: stringResource(R.string.st_NotepadScreen_attaud69),
                                isPlaying      = isPlaying,
                                isCurrentTrack = isCurrentTrack,
                                thumbnail      = track?.thumbnailUri ?: if (isPlaying) currentTrackThumbnail else null,
                                artist         = track?.artist,
                                containerColor = onColor.copy(0.08f),
                                contentColor   = onColor,
                                onClick        = onPlayAudio,
                                compact        = true,
                                progress       = inlineProgress,
                                positionMs     = audioPositionMs,
                                durationMs     = audioDurationMs,
                                onSeek         = onSeekAudio,
                                modifier       = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // ── Footer ──────────────────────────────────────────
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(
                        color     = onColor.copy(0.07f),
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(bottom = 10.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Info zone: opens the viewer.
                        Box(
                            Modifier
                                .weight(1f)
                                .bouncyClick(onClick = onClick, onLongClick = onLongClick)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        SimpleDateFormat("MMMM d", Locale.getDefault())
                                            .format(Date(note.timestamp))
                                            .uppercase(java.util.Locale.ROOT),
                                        style         = MaterialTheme.typography.labelSmall,
                                        fontWeight    = FontWeight.ExtraBold,
                                        color         = onColor.copy(0.5f),
                                        letterSpacing = 0.8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (wordCount > 0) {
                                        Text(
                                            pluralStringResource(R.plurals.st_NotepadScreen_words61, wordCount, wordCount),
                                            style         = MaterialTheme.typography.labelSmall,
                                            color         = onColor.copy(0.35f),
                                            fontWeight    = FontWeight.Bold,
                                        )
                                    }
                                }

                                // Attachment type badges
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    if (note.attachedAudioUri != null)
                                        AttachmentIndicator(Icons.Rounded.MusicNote, onColor, stringResource(R.string.st_NotepadScreen_cdhaud66))
                                    if (note.attachedPdfUri != null)
                                        AttachmentIndicator(Icons.Rounded.Description, onColor, stringResource(R.string.st_NotepadScreen_cdhpdf67))
                                    if (note.attachedImageUri != null)
                                        AttachmentIndicator(Icons.Rounded.Image, onColor, stringResource(R.string.st_NotepadScreen_cdhimg68))
                                }
                            }
                        }

                        // Pin + delete: independent targets, siblings of every
                        // viewer-opening zone — they can never open the viewer.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolzExpressiveIconButton(
                                onClick = onTogglePin,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (note.isPinned) onColor.copy(0.18f) else onColor.copy(0.08f),
                                    contentColor = onColor.copy(if (note.isPinned) 0.95f else 0.45f)
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.PushPin, contentDescription = if (note.isPinned) stringResource(R.string.st_NotepadScreen_cdunpin63) else stringResource(R.string.st_NotepadScreen_cdpin62),
                                    Modifier.size(17.dp).graphicsLayer { rotationZ = if (note.isPinned) -20f else 0f }
                                )
                            }
                            ToolzExpressiveIconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = onColor.copy(0.08f),
                                    contentColor = onColor.copy(0.45f)
                                )
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.st_NotepadScreen_cddel64), Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }

            // ── Selection overlay + checkmark ────────────────────────────
            androidx.compose.animation.AnimatedVisibility(
                visible  = isSelectionMode,
                modifier = Modifier.matchParentSize(),
                enter    = fadeIn(tween(160)),
                exit     = fadeOut(tween(120)),
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(0.1f) else Color.Transparent)
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible  = isSelectionMode,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                enter    = scaleIn(spring(0.5f, Spring.StiffnessMediumLow)) + fadeIn(tween(160)),
                exit     = scaleOut() + fadeOut(tween(100)),
            ) {
                Surface(
                    shape  = CircleShape,
                    color  = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.2f),
                    border = BorderStroke(2.dp, Color.White.copy(0.9f)),
                    modifier = Modifier.size(26.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check, null,
                            Modifier.size(20.dp).padding(3.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalColorPickerBanner(
    onColor: Color,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    onCustomColor: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberToolzHapticFeedback()
    val colors = listOf(
        0, // Default
        0xFFFFF9C4, 0xFFFFCCBC, 0xFFC8E6C9, 0xFFB3E5FC, 0xFFE1BEE7,
        0xFFF5F5F5, 0xFFD7CCC8, 0xFFCFD8DC, 0xFFFFE0B2, 0xFF263238,
        0xFFFF5252, 0xFFFF4081, 0xFF7C4DFF, 0xFF536DFE, 0xFF448AFF,
        0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A
    ).map { it.toInt() }

    val colorNames = mapOf(
        0 to R.string.st_NotepadScreen_cpd104,
        0xFFFFF9C4.toInt() to R.string.st_NotepadScreen_cpsy105,
        0xFFFFCCBC.toInt() to R.string.st_NotepadScreen_cpo106,
        0xFFC8E6C9.toInt() to R.string.st_NotepadScreen_cmg107,
        0xFFB3E5FC.toInt() to R.string.st_NotepadScreen_csb108,
        0xFFE1BEE7.toInt() to R.string.st_NotepadScreen_clav109,
        0xFFF5F5F5.toInt() to R.string.st_NotepadScreen_cpw110,
        0xFFD7CCC8.toInt() to R.string.st_NotepadScreen_cwt111,
        0xFFCFD8DC.toInt() to R.string.st_NotepadScreen_ccg112,
        0xFFFFE0B2.toInt() to R.string.st_NotepadScreen_cap113,
        0xFF263238.toInt() to R.string.st_NotepadScreen_cds114,
        0xFFFF5252.toInt() to R.string.st_NotepadScreen_crr115,
        0xFFFF4081.toInt() to R.string.st_NotepadScreen_cpa116,
        0xFF7C4DFF.toInt() to R.string.st_NotepadScreen_cdp117,
        0xFF536DFE.toInt() to R.string.st_NotepadScreen_cin118,
        0xFF448AFF.toInt() to R.string.st_NotepadScreen_ceb119,
        0xFF03A9F4.toInt() to R.string.st_NotepadScreen_clb120,
        0xFF00BCD4.toInt() to R.string.st_NotepadScreen_ccy121,
        0xFF009688.toInt() to R.string.st_NotepadScreen_ctl122,
        0xFF4CAF50.toInt() to R.string.st_NotepadScreen_cgn123,
        0xFF8BC34A.toInt() to R.string.st_NotepadScreen_clm124,
    )

    Surface(
        color = onColor.copy(0.08f),
        shape = LargeExpressiveShape,
        border = BorderStroke(1.dp, onColor.copy(0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Palette,
                        null,
                        Modifier.size(16.dp),
                        tint = onColor
                    )
                    Text(
                        stringResource(R.string.st_NotepadScreen_colhdr103),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = onColor,
                        letterSpacing = 1.2.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onCustomColor,
                        color = onColor.copy(0.12f),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Rounded.Colorize,
                            null,
                            Modifier
                                .padding(6.dp)
                                .size(14.dp),
                            tint = onColor
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        color = Color.Transparent,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            null,
                            Modifier
                                .padding(4.dp)
                                .size(16.dp),
                            tint = onColor.copy(0.5f)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lazyItems(colors) { colorInt ->
                    val isSelected = selectedColor == colorInt
                    val color = if (colorInt == 0) MaterialTheme.colorScheme.surfaceVariant else Color(colorInt)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .bouncyClick {
                                haptic.click()
                                onColorSelected(colorInt)
                            },
                        shape = MediumExpressiveShape,
                        color = if (isSelected) onColor.copy(0.12f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, onColor.copy(0.2f)) else null
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, onColor.copy(0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (colorInt == 0) {
                                    Icon(
                                        Icons.Rounded.Block,
                                        null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                                    )
                                }
                            }
                            Text(
                                text = colorNames[colorInt]?.let { stringResource(it) } ?: stringResource(R.string.st_NotepadScreen_ccc125),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                color = onColor.copy(if (isSelected) 1f else 0.7f),
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = onColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  AI style preview banner
// ─────────────────────────────────────────────────────────────

@Composable
private fun AiStyleBanner(style: AiNoteStyle, onColor: Color, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val previewColor = remember(style.colorHex) {
        try { Color(android.graphics.Color.parseColor(style.colorHex)) }
        catch (_: Exception) { Color(0xFFFFF9C4) }
    }

    Surface(
        color    = onColor.copy(0.08f),
        shape    = LargeExpressiveShape,
        border   = BorderStroke(1.dp, onColor.copy(0.1f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp), tint = onColor)
                Text(
                    stringResource(R.string.st_NotepadScreen_ass35),
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Black,
                    color         = onColor,
                    letterSpacing = 1.sp,
                    modifier      = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, onColor.copy(0.2f), CircleShape)
                    )
                    if (style.isBold) {
                        Surface(color = onColor.copy(0.1f), shape = CircleShape) {
                            Icon(Icons.Rounded.FormatBold, null, Modifier.size(16.dp).padding(2.dp), tint = onColor)
                        }
                    }
                    if (style.isItalic) {
                        Surface(color = onColor.copy(0.1f), shape = CircleShape) {
                            Icon(Icons.Rounded.FormatItalic, null, Modifier.size(16.dp).padding(2.dp), tint = onColor)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(style.reasoning, style = MaterialTheme.typography.bodySmall, color = onColor.copy(0.65f))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onAccept, color = onColor.copy(0.18f), shape = MediumExpressiveShape) {
                    Text(stringResource(R.string.st_NotepadScreen_a34), Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = onColor, letterSpacing = 1.sp)
                }
                Surface(onClick = onDismiss, color = Color.Transparent, shape = MediumExpressiveShape, border = BorderStroke(1.dp, onColor.copy(0.15f))) {
                    Text(stringResource(R.string.st_NotepadScreen_dis36), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = onColor.copy(0.6f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Attachment menu bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentMenuSheet(
    onDismiss  : () -> Unit,
    onPickAudio: () -> Unit,
    onPickPdf  : () -> Unit,
    onPickImage: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 40.dp)
                .navigationBarsPadding()
        ) {
            @Suppress("DEPRECATION")
            Text(stringResource(R.string.st_NotepadScreen_att37),
                style         = MaterialTheme.typography.labelSmall,
                fontWeight    = FontWeight.Black,
                color         = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier      = Modifier.padding(bottom = 16.dp, start = 4.dp),
            )
            AttachmentTypeItem(stringResource(R.string.st_NotepadScreen_ma38), stringResource(R.string.st_NotepadScreen_dmaud138), Icons.Rounded.MusicNote,     Color(0xFFFF4081)) { onPickAudio() }
            Spacer(Modifier.height(10.dp))
            AttachmentTypeItem(stringResource(R.string.st_NotepadScreen_pd39),  stringResource(R.string.st_NotepadScreen_dmpdf139), Icons.Rounded.Description, Color(0xFF2196F3)) { onPickPdf() }
            Spacer(Modifier.height(10.dp))
            AttachmentTypeItem(stringResource(R.string.st_NotepadScreen_i40),         stringResource(R.string.st_NotepadScreen_dmimg140), Icons.Rounded.Image, Color(0xFF4CAF50)) { onPickImage() }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Attachment picker dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerDialog(
    title         : String,
    isAudio       : Boolean,
    items         : List<Pair<String, String>>,
    onDismiss     : () -> Unit,
    onSelect      : (String, String) -> Unit,
    viewModel     : NotepadViewModel,
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
    emptyText     : String,
    browseLabel   : String? = null,
    onBrowse      : (() -> Unit)? = null,
) {
    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val haptic     = rememberToolzHapticFeedback()

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier  = Modifier.padding(28.dp).fillMaxWidth().heightIn(max = 500.dp),
            shape     = BouncyShape,
            color     = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    @Suppress("DEPRECATION")
                    Text(
                        title,
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp,
                    )
                    
                    IconButton(
                        onClick = {
                            if (isAudio) {
                                viewModel.refreshTracks()
                            } else {
                                viewModel.refreshPdfs()
                            }
                            haptic.tick()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                LazyColumn(
                    modifier            = Modifier.weight(1f).fadingEdges(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (items.isEmpty()) {
                        item {
                            Text(
                                emptyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                                modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    lazyItems(items) { (name, uri) ->
                        val isMusic  = isAudio
                        val track    = if (isMusic) musicState.tracks.find { it.uri == uri } else null
                        val isPlaying = isMusic && musicState.isPlaying && isCurrentNoteTrack(musicState.currentTrack, uri)

                        val inf = rememberInfiniteTransition(label = "rot")
                        val rotationRaw by inf.animateFloat(
                            0f, 360f,
                            infiniteRepeatable(tween(10000, easing = LinearEasing)),
                            label = "pickerRot",
                        )
                        val rotation = if (isPlaying) rotationRaw else 0f

                        Surface(
                            modifier = Modifier.fillMaxWidth().bouncyClick { haptic.click(); onSelect(name, uri) },
                            shape    = LargeExpressiveShape,
                            color    = if (track?.thumbnailUri != null) MaterialTheme.colorScheme.primaryContainer.copy(0.12f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            border   = BorderStroke(
                                1.dp,
                                when {
                                    isPlaying             -> MaterialTheme.colorScheme.primary.copy(0.5f)
                                    track?.thumbnailUri != null -> MaterialTheme.colorScheme.primary.copy(0.15f)
                                    else                  -> Color.Transparent
                                }
                            ),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp).graphicsLayer { rotationZ = rotation },
                                    shape    = CircleShape,
                                    color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    if (track?.thumbnailUri != null) {
                                        AsyncImage(
                                            model        = track.thumbnailUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier     = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (isMusic) Icons.Rounded.MusicNote else Icons.Rounded.Description,
                                                null, Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(0.6f),
                                            )
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    val isRealSong = track?.artist != null || track?.thumbnailUri != null
                                    Text(
                                        track?.title ?: name,
                                        style      = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isRealSong) FontWeight.ExtraBold else FontWeight.Bold,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis,
                                        color = if (isRealSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isMusic && track?.artist != null) {
                                        Text(
                                            track.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(0.7f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (isMusic) {
                                        Text(
                                            stringResource(R.string.st_NotepadScreen_genaud100),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f),
                                        )
                                    }
                                }
                                if (isPlaying) {
                                    Icon(Icons.Rounded.VolumeUp, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (browseLabel != null && onBrowse != null) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { haptic.click(); onBrowse() },
                        ) {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(browseLabel, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(
                        onClick = { haptic.click(); onDismiss() },
                    ) {
                        @Suppress("DEPRECATION")
                        Text(stringResource(R.string.st_NotepadScreen_c31), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Music pill
// ─────────────────────────────────────────────────────────────

private fun formatNoteAudioMs(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
fun MusicPill(
    title: String,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    thumbnail: String? = null,
    artist: String? = null,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    progress: Float = 0f,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    onSeek: ((Float) -> Unit)? = null,
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val performanceMode = LocalPerformanceMode.current
    val haptic = rememberToolzHapticFeedback()
    val isRealSong = artist != null || thumbnail != null
    // Seek bar is a sibling of the clickable row (never nested), so scrubbing
    // can never trigger play/pause.
    val seekEnabled = isCurrentTrack && durationMs > 0L && onSeek != null
    var scrubFraction by remember(isCurrentTrack, durationMs) { mutableStateOf<Float?>(null) }
    val shownFraction = (scrubFraction ?: progress).coerceIn(0f, 1f)
    val shownPositionMs = if (scrubFraction != null) (scrubFraction!! * durationMs).toLong() else positionMs

    val playingLbl      = stringResource(R.string.st_NotepadScreen_mpplay126)
    val playingPauseLbl = stringResource(R.string.st_NotepadScreen_mptpps127)
    val pausedLbl       = stringResource(R.string.st_NotepadScreen_mppr128)
    val tapPlayLabel    = stringResource(R.string.st_NotepadScreen_mptp129)
    val attachPlayLbl   = stringResource(R.string.st_NotepadScreen_mpat130)
    val audioUppercase  = stringResource(R.string.st_NotepadScreen_aud93)
    val secondaryLabel = when {
        isPlaying -> if (isRealSong && artist != null) artist.uppercase(java.util.Locale.ROOT) + " • " + playingLbl else playingPauseLbl
        isCurrentTrack -> pausedLbl
        isRealSong -> artist?.uppercase(java.util.Locale.ROOT)?.let { "$it • $tapPlayLabel" } ?: ("$audioUppercase • $tapPlayLabel")
        else -> attachPlayLbl
    }

    val inf = rememberInfiniteTransition(label = "pill")
    val rotationRaw by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(
            tween(if (isPlaying) 8_000 else 20_000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "thumbRot",
    )
    val rotation = if (performanceMode) 0f else rotationRaw

    // Animated equalizer bars shown while playing.
    val eqAnim = rememberInfiniteTransition(label = "pillEq")
    val eq1 by eqAnim.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(if (isPlaying && !performanceMode) 520 else 10_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq1",
    )
    val eq2 by eqAnim.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(if (isPlaying && !performanceMode) 430 else 10_000, delayMillis = 120, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq2",
    )
    val eq3 by eqAnim.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(if (isPlaying && !performanceMode) 610 else 10_000, delayMillis = 240, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eq3",
    )

    // Plain Surface: the ONLY click target is the row below. The play icon is a
    // static indicator and the seek bar is a non-nested sibling, so a tap can
    // never double-fire or leak into a parent card.
    Surface(
        shape = RoundedCornerShape(if (compact) 16.dp else 24.dp),
        color = containerColor.copy(0.15f),
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(
            if (isPlaying) 2.dp else 1.dp,
            if (isPlaying) contentColor.copy(0.4f) else contentColor.copy(0.12f)
        ),
        modifier = modifier
    ) {
        Column {
            Row(
                Modifier
                    .bouncyClick(onClick = { haptic.click(); onClick() })
                    .padding(
                        horizontal = if (compact) 10.dp else 14.dp,
                        vertical = if (compact) 8.dp else 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(if (compact) 36.dp else 52.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(0.08f))
                        .rotate(rotation),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnail != null) AsyncImage(
                        thumbnail,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    else Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        Modifier.size(if (compact) 18.dp else 24.dp),
                        tint = contentColor.copy(0.7f)
                    )

                    if (isPlaying) {
                        Surface(
                            Modifier
                                .size(if (compact) 8.dp else 12.dp)
                                .align(Alignment.Center),
                            CircleShape,
                            containerColor,
                            border = BorderStroke(1.5.dp, contentColor)
                        ) {}
                    }
                }

                Spacer(Modifier.width(if (compact) 12.dp else 16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (isPlaying && !performanceMode) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.height(12.dp)
                            ) {
                                listOf(eq1, eq2, eq3).forEach { s ->
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .fillMaxHeight(s)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(contentColor.copy(0.75f))
                                    )
                                }
                            }
                        } else if (isPlaying) {
                            Icon(
                                Icons.Rounded.GraphicEq, null,
                                Modifier.size(14.dp),
                                tint = contentColor.copy(0.6f)
                            )
                        }
                        Text(
                            secondaryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(0.55f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Static play/pause indicator (the whole row is the button).
                Box(
                    modifier = Modifier
                        .size(if (compact) 40.dp else 48.dp)
                        .background(contentColor.copy(0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isCurrentTrack && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isCurrentTrack && isPlaying) stringResource(R.string.st_NotepadScreen_mppause131) else stringResource(R.string.st_NotepadScreen_mpplayb132),
                        Modifier.size(if (compact) 18.dp else 24.dp),
                        tint = contentColor
                    )
                }
            }

            // Real seekable progress bar — only for the loaded track.
            if (isCurrentTrack) {
                if (seekEnabled) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (compact) 12.dp else 16.dp)
                            .padding(bottom = if (compact) 8.dp else 10.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                formatNoteAudioMs(shownPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(0.55f),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                formatNoteAudioMs(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(0.55f),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Slider(
                            value = shownFraction,
                            onValueChange = { scrubFraction = it },
                            onValueChangeFinished = {
                                scrubFraction?.let { onSeek(it) }
                                scrubFraction = null
                            },
                            modifier = Modifier.fillMaxWidth().height(if (compact) 24.dp else 28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = contentColor,
                                activeTrackColor = contentColor.copy(0.8f),
                                inactiveTrackColor = contentColor.copy(0.18f),
                            ),
                        )
                    }
                } else {
                    val clamped = progress.coerceIn(0f, 1f)
                    if (clamped > 0.005f) {
                        LinearProgressIndicator(
                            progress = { clamped },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (compact) 12.dp else 16.dp)
                                .padding(bottom = if (compact) 8.dp else 10.dp)
                                .height(3.dp)
                                .clip(CircleShape),
                            color = contentColor.copy(0.75f),
                            trackColor = contentColor.copy(0.15f),
                        )
                    } else if (isPlaying && !performanceMode) {
                        // Indeterminate shimmer while position is still resolving.
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (compact) 12.dp else 16.dp)
                                .padding(bottom = if (compact) 8.dp else 10.dp)
                                .height(3.dp)
                                .clip(CircleShape),
                            color = contentColor.copy(0.6f),
                            trackColor = contentColor.copy(0.15f),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PDF preview
// ─────────────────────────────────────────────────────────────

@Composable
fun PdfPreview(uri: String, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    ExpressiveCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
            var loadFailed by remember(uri) { mutableStateOf(false) }
            val targetWidth = remember(constraints.maxWidth) {
                // Cap at ~900px wide: enough for a thumbnail, ~3MB max.
                constraints.maxWidth.coerceIn(320, 900)
            }
            LaunchedEffect(uri, targetWidth) {
                loadFailed = false
                bitmap = withContext(Dispatchers.IO) {
                    var renderer: PdfRenderer? = null
                    var page: PdfRenderer.Page? = null
                    try {
                        val parsed = try { Uri.parse(uri) } catch (_: Exception) { return@withContext null }
                        val pfd = try {
                            context.contentResolver.openFileDescriptor(parsed, "r")
                        } catch (_: Exception) { null } ?: return@withContext null
                        pfd.use {
                            renderer = PdfRenderer(it)
                            val r = renderer ?: return@withContext null
                            if (r.pageCount <= 0) return@withContext null
                            page = r.openPage(0)
                            val pg = page ?: return@withContext null
                            val aspectRatio = if (pg.width == 0) 1f else pg.height.toFloat() / pg.width.toFloat()
                            val reqWidth = targetWidth
                            val reqHeight = ((reqWidth * aspectRatio).toInt()).coerceIn(240, 1200)
                            // NB: PdfRenderer only supports ARGB_8888 (RGB_565 throws).
                            val bmp = Bitmap.createBitmap(reqWidth, reqHeight, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    } catch (_: Error) {
                        // OOM and friends: report failure instead of crashing.
                        null
                    } catch (_: Exception) { null } finally {
                        try { page?.close() } catch (_: Exception) { }
                        try { renderer?.close() } catch (_: Exception) { }
                    }
                }
                if (bitmap == null) loadFailed = true
            }
            DisposableEffect(uri) {
                onDispose {
                    try { bitmap?.recycle() } catch (_: Exception) { }
                }
            }

            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Box {
                    Image(
                        currentBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.st_NotepadScreen_d29),
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Surface(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PictureAsPdf,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.st_NotepadScreen_d29),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            null,
                            Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                        Text(
                            if (loadFailed) context.resources.getString(R.string.notepad_pdf_preview_failed) else stringResource(R.string.st_NotepadScreen_pdfload135),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Remake V2 — multi-attachment PDF strip for a saved note.
 * Observes note_attachments, merges nothing (legacy single-slot card stays
 * above for compat), opens at the stored page hint, swipe-free remove.
 *
 * @param excludeUris V2 rows whose URI is already rendered as a thumbnail
 * card elsewhere (legacy slot) are skipped so the same PDF never shows
 * twice on one screen.
 */
@Composable
fun NotePdfAttachmentStrip(
    viewModel: NotepadViewModel,
    note: com.frerox.toolz.data.notepad.Note,
    onOpenPdf: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    excludeUris: Set<String> = emptySet()
) {
    val noteId = note.id
    val scope = rememberCoroutineScope()
    val haptic = rememberToolzHapticFeedback()
    val flow = remember(noteId) { viewModel.attachmentsFor(noteId) }
    val all by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val items = remember(all, excludeUris) {
        all.filter { a -> a.kind == com.frerox.toolz.data.notepad.NoteAttachment.KIND_PDF && a.uri !in excludeUris }
    }
    LaunchedEffect(noteId) {
        try { viewModel.ensureLegacyAttachments(note) } catch (_: Exception) { }
    }
    if (noteId == 0 || items.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { att ->
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            com.frerox.toolz.data.notepad.NoteText.displayFileName(att.displayName, att.uri),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (att.pageHint > 0) stringResource(R.string.st_NotepadScreen_pdfpage133, att.pageHint + 1) else stringResource(R.string.st_NotepadScreen_pdftap134),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ToolzExpressiveIconButton(
                        onClick = { haptic.click(); onOpenPdf(att.uri, att.pageHint) },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("›", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(6.dp))
                    ToolzExpressiveIconButton(
                        onClick = { scope.launch { viewModel.removeAttachment(att.id) }; haptic.tick() },
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Shared small components (public API preserved)
// ─────────────────────────────────────────────────────────────

@Composable
fun AttachmentTypeItem(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    val haptic = rememberToolzHapticFeedback()
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick { haptic.click(); onClick() },
        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape    = LargeExpressiveShape,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.18f)),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(48.dp).background(color.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = color)
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            }
        }
    }
}

@Composable
fun AttachmentIndicator(icon: ImageVector, color: Color, contentDescription: String? = null) {
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(12.dp), tint = color.copy(0.75f))
        }
    }
}

//  Editor formatting helpers
// ─────────────────────────────────────────────────────────────

@Composable
private fun ToolbarDivider(color: Color) {
    Box(Modifier.height(22.dp).width(1.dp).background(color.copy(0.12f)))
}

@Composable
private fun FontSizeControl(size: Float, tint: Color, onChange: (Float) -> Unit) {
    val haptic = rememberToolzHapticFeedback()
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier              = Modifier.widthIn(min = 120.dp),
    ) {
        Icon(Icons.Rounded.TextFields, null, Modifier.size(15.dp), tint = tint.copy(0.6f))
        Surface(
            onClick = { haptic.tick(); onChange((size - 1f).coerceAtLeast(12f)) },
            shape   = SmallExpressiveShape,
            color   = tint.copy(0.08f),
            border  = BorderStroke(1.dp, tint.copy(0.08f)),
        ) {
            Text("−", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = tint, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            "${size.toInt()}",
            color      = tint,
            fontWeight = FontWeight.Black,
            style      = MaterialTheme.typography.labelLarge,
            modifier   = Modifier.width(26.dp),
            textAlign  = TextAlign.Center,
        )
        Surface(
            onClick = { haptic.tick(); onChange((size + 1f).coerceAtMost(28f)) },
            shape   = SmallExpressiveShape,
            color   = tint.copy(0.08f),
            border  = BorderStroke(1.dp, tint.copy(0.08f)),
        ) {
            Text("+", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = tint, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EditorStatPill(label: String, color: Color) {
    Surface(
        color  = color.copy(0.07f),
        shape  = CircleShape,
        border = BorderStroke(1.dp, color.copy(0.07f)),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = color.copy(0.72f),
        )
    }
}

@Composable
private fun TypewriterText(
    text      : String,
    modifier  : Modifier = Modifier,
    style     : androidx.compose.ui.text.TextStyle,
    color     : Color,
    lineHeight: androidx.compose.ui.unit.TextUnit = style.lineHeight,
) {
    val performanceMode = LocalPerformanceMode.current
    var visibleLength by remember(text) { mutableIntStateOf(if (performanceMode) text.length else 0) }

    LaunchedEffect(text, performanceMode) {
        if (performanceMode) { visibleLength = text.length; return@LaunchedEffect }
        visibleLength = 0
        while (visibleLength < text.length) {
            visibleLength += if (text.length > 220) 3 else 1
            kotlinx.coroutines.delay(if (text.length > 220) 10L else 18L)
        }
    }

    Text(
        text       = text.take(visibleLength.coerceAtMost(text.length)),
        modifier   = modifier,
        style      = style,
        color      = color,
        lineHeight = lineHeight,
    )
}

// ─────────────────────────────────────────────────────────────
//  Custom color dialog
// ─────────────────────────────────────────────────────────────

@Composable
fun CustomColorDialog(
    initialColor   : Int,
    onDismiss      : () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    var hex    by remember { mutableStateOf("%06X".format(0xFFFFFF and initialColor)) }
    val haptic = rememberToolzHapticFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = BouncyShape,
        title = {
            Text(stringResource(R.string.st_NotepadScreen_cc32), fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelMedium)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val preview = remember(hex) {
                    try { Color(android.graphics.Color.parseColor("#$hex")) } catch (_: Exception) { Color.Gray }
                }
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape    = LargeExpressiveShape,
                    color    = preview,
                    border   = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {}
                OutlinedTextField(
                    value         = hex,
                    onValueChange = { if (it.length <= 6) hex = it.uppercase(java.util.Locale.ROOT).filter { c -> c in "0123456789ABCDEF" } },
                    label         = { Text(stringResource(R.string.st_NotepadScreen_hc33)) },
                    prefix        = { Text("#") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = LargeExpressiveShape,
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        try { onColorSelected(android.graphics.Color.parseColor("#$hex")) } catch (_: Exception) {}
                    }),
                )
            }
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = {
                    haptic.click()
                    try { onColorSelected(android.graphics.Color.parseColor("#$hex")) } catch (_: Exception) {}
                },
            ) { Text(stringResource(R.string.st_NotepadScreen_a34), fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = { haptic.click(); onDismiss() }) { Text(stringResource(R.string.st_NotepadScreen_c31)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────
//  Utility functions
// ─────────────────────────────────────────────────────────────

private fun detectInsertedRange(previous: String, updated: String): IntRange? {
    if (updated.length <= previous.length) return null
    var prefixLength = 0
    val sharedPrefixLimit = min(previous.length, updated.length)
    while (prefixLength < sharedPrefixLimit && previous[prefixLength] == updated[prefixLength]) prefixLength++
    var suffixLength = 0
    val previousRemaining = previous.length - prefixLength
    val updatedRemaining  = updated.length - prefixLength
    while (suffixLength < previousRemaining && suffixLength < updatedRemaining &&
        previous[previous.length - 1 - suffixLength] == updated[updated.length - 1 - suffixLength]) suffixLength++
    val insertedEndExclusive = updated.length - suffixLength
    if (insertedEndExclusive <= prefixLength) return null
    return prefixLength until insertedEndExclusive
}

private fun typingFadeVisualTransformation(
    animatedRange: IntRange?,
    animatedColor: Color,
    alpha        : Float,
    enabled      : Boolean,
): VisualTransformation {
    if (!enabled || animatedRange == null) return VisualTransformation.None
    return VisualTransformation { text ->
        val start        = animatedRange.first.coerceIn(0, text.length)
        val endExclusive = (animatedRange.last + 1).coerceIn(start, text.length)
        if (start >= endExclusive) {
            TransformedText(text, OffsetMapping.Identity)
        } else {
            val transformed = buildAnnotatedString {
                append(text)
                addStyle(SpanStyle(color = animatedColor.copy(alpha = alpha.coerceIn(0f, 1f))), start, endExclusive)
            }
            TransformedText(transformed, OffsetMapping.Identity)
        }
    }
}

private fun noteFontFamily(style: String): FontFamily = when (style) {
    "SERIF"     -> FontFamily.Serif
    "MONOSPACE" -> FontFamily.Monospace
    "CASUAL"    -> FontFamily.Cursive
    else        -> FontFamily.Default
}

@Composable
private fun transparentTextFieldColors(cursorColor: Color, textColor: Color = Color.Unspecified): TextFieldColors =
    TextFieldDefaults.colors(
        focusedContainerColor   = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor   = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor             = cursorColor,
        focusedTextColor        = textColor,
        unfocusedTextColor      = textColor,
    )

private typealias VibrationManager = com.frerox.toolz.util.VibrationManager

// ─────────────────────────────────────────────────────────────
//  Notepad Action Component (Floating Toolbar + Morphing)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NotepadActionComponent(
    state: NotepadActionState,
    onStateChange: (NotepadActionState) -> Unit,
    viewModel: NotepadViewModel,
    notes: List<Note>,
    selectedNotes: List<Note> = emptyList(),
    onClearSelection: () -> Unit = {},
    initialDraft: Note? = null,
    onDraftChange: (Note?) -> Unit,
    onSearchClick: () -> Unit,
    onNoteGenerated: (AiGeneratedNote?) -> Unit,
    onNoteEdited: (Note, AiGeneratedNote?) -> Unit,
    onDeleteRequest: (Note) -> Unit = {},
    onSelectAll: () -> Unit = {},
    availableTracks: List<com.frerox.toolz.data.music.MusicTrack> = emptyList(),
    availablePdfs: List<com.frerox.toolz.data.pdf.PdfFile> = emptyList(),
    onImagePickRequest: () -> Unit = {},
    newImageUri: Uri? = null,
    onImageConsumed: () -> Unit = {},
    onViewPdf: (String, Int) -> Unit = { _, _ -> },
    viewedNoteId: Int? = null,
    onNoteOptionsRequest: (Int) -> Unit = {},
    offlineMode: Boolean = false,
    aiToolsEnabled: Boolean = true,
) {
    val haptic = rememberToolzHapticFeedback()
    val performanceMode = LocalPerformanceMode.current
    val scope = rememberCoroutineScope()

    val smoothSpec = remember { 
        spring<Float>(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessLow
        )
    }
    val smoothDpSpec = remember { 
        spring<Dp>(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessLow
        )
    }
    val smoothIntSizeSpec = remember { 
        spring<IntSize>(
            dampingRatio = 0.9f,
            stiffness = Spring.StiffnessLow
        )
    }

    val cornerRadius by animateDpAsState(
        targetValue = when (state) {
            NotepadActionState.TOOLBAR, NotepadActionState.SELECTION, NotepadActionState.VIEWER -> 32.dp
            NotepadActionState.EDITOR, NotepadActionState.AI_TOOLS -> 28.dp
            NotepadActionState.FULL_EDITOR -> 24.dp
        },
        animationSpec = smoothDpSpec,
        label = "cornerRadius"
    )

    val widthFactor by animateFloatAsState(
        targetValue = when (state) {
            NotepadActionState.TOOLBAR -> 0.65f
            NotepadActionState.VIEWER -> 0.85f
            NotepadActionState.SELECTION -> 0.85f
            NotepadActionState.EDITOR, NotepadActionState.AI_TOOLS -> 0.95f
            NotepadActionState.FULL_EDITOR -> 0.95f
        },
        animationSpec = smoothSpec,
        label = "widthFactor"
    )

    val heightFactor by animateFloatAsState(
        targetValue = when (state) {
            NotepadActionState.FULL_EDITOR -> 0.88f
            else -> 0f
        },
        animationSpec = smoothSpec,
        label = "heightFactor"
    )

    val containerColor = when (state) {
        NotepadActionState.FULL_EDITOR -> {
            // Fix: Full editor background only changes if the user explicitly picked a color
            // or if we are editing an existing note that HAS a color.
            // If it's a new quick note (color == 0), use neutral surface.
            if (initialDraft == null || initialDraft.color == 0) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                Color(initialDraft.color)
            }
        }
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    // Labels are resolved here, in a composable context: the toolbar row
    // builder lambdas below are not composable, so they can't call
    // stringResource() themselves.
    val lblSearch = stringResource(R.string.st_NotepadScreen_s42)
    val lblAi     = stringResource(R.string.st_NotepadScreen_ai43)
    val lblEdit   = stringResource(R.string.st_NotepadScreen_lbled72)
    val lblDelete = stringResource(R.string.st_NotepadScreen_d44)
    val lblClear  = stringResource(R.string.st_NotepadScreen_lblclr77)

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFactor)
            .then(if (state == NotepadActionState.FULL_EDITOR) Modifier.fillMaxHeight(heightFactor) else Modifier)
            .imePadding()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(cornerRadius)
            )
            .background(
                color = containerColor,
                shape = RoundedCornerShape(cornerRadius)
            )
            .animateContentSize(
                animationSpec = if (performanceMode) tween(0)
                else smoothIntSizeSpec
            )
            .padding(
                when (state) {
                    NotepadActionState.TOOLBAR, NotepadActionState.SELECTION, NotepadActionState.VIEWER -> 4.dp
                    NotepadActionState.FULL_EDITOR -> 0.dp
                    else -> 16.dp
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(smoothSpec) + scaleIn(initialScale = 0.96f, animationSpec = smoothSpec))
                    .togetherWith(fadeOut(smoothSpec) + scaleOut(targetScale = 0.98f))
            },
            label = "actionContent"
        ) { targetState ->
            when (targetState) {
                NotepadActionState.TOOLBAR -> {
                    ToolzHorizontalFloatingToolbar(
                        expanded = true,
                        containerColor = Color.Transparent,
                        content = {
                            ToolzExpressiveIconButton(
                                onClick = {
                                    haptic.click()
                                    onStateChange(NotepadActionState.EDITOR)
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.st_NotepadScreen_newn70))
                            }
                        },
                        trailingContent = {
                            clickableItem(
                                onClick = {
                                    haptic.click()
                                    onSearchClick()
                                },
                                icon = { Icon(Icons.Rounded.Search, stringResource(R.string.st_NotepadScreen_s42)) },
                                label = lblSearch
                            )
                            if (aiToolsEnabled && !offlineMode) {
                                clickableItem(
                                    onClick = {
                                        haptic.click()
                                        onStateChange(NotepadActionState.AI_TOOLS)
                                    },
                                    icon = { Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.st_NotepadScreen_cdait76)) },
                                    label = lblAi
                                )
                            }
                        }
                    )
                }
                NotepadActionState.SELECTION -> {
                    ToolzHorizontalFloatingToolbar(
                        expanded = true,
                        containerColor = Color.Transparent,
                        content = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(0.15f),
                                    shape = CircleShape,
                                ) {
                                    Text(
                                        "${selectedNotes.size}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                ToolzExpressiveIconButton(
                                    onClick = {
                                        haptic.tick()
                                        onSelectAll()
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(Icons.Rounded.SelectAll, stringResource(R.string.st_NotepadScreen_cdsall71))
                                }
                            }
                        },
                        trailingContent = {
                            if (aiToolsEnabled && !offlineMode) {
                                clickableItem(
                                    onClick = {
                                        haptic.click()
                                        onStateChange(NotepadActionState.AI_TOOLS)
                                    },
                                    icon = { Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.st_NotepadScreen_ai43)) },
                                    label = lblAi
                                )
                            }
                            if (selectedNotes.size == 1) {
                                clickableItem(
                                    onClick = {
                                        haptic.click()
                                        onStateChange(NotepadActionState.FULL_EDITOR)
                                        onDraftChange(selectedNotes.first())
                                    },
                                    icon = { Icon(Icons.Rounded.Edit, stringResource(R.string.st_NotepadScreen_cdedit73)) },
                                    label = lblEdit
                                )
                            }
                            clickableItem(
                                onClick = {
                                    haptic.longClick()
                                    viewModel.deleteNotes(selectedNotes)
                                    onClearSelection()
                                },
                                icon = { Icon(Icons.Rounded.Delete, stringResource(R.string.st_NotepadScreen_d44)) },
                                label = lblDelete
                            )
                            clickableItem(
                                onClick = {
                                    haptic.click()
                                    onClearSelection()
                                    // Selection mode is driven by forceSelectionMode or selectedNoteIds
                                    // So we need to ensure the caller resets both.
                                },
                                icon = { Icon(Icons.Rounded.Close, stringResource(R.string.st_NotepadScreen_c31)) },
                                label = lblClear
                            )
                        }
                    )
                }
                NotepadActionState.EDITOR -> {
                    QuickEditor(
                        draft = initialDraft ?: Note(title = "", content = "", color = 0), // Use 0 for no color
                        onDraftUpdate = { onDraftChange(it) },
                        onDismiss = { 
                            onDraftChange(null)
                            onStateChange(NotepadActionState.TOOLBAR) 
                        },
                        onFullScreen = {
                            onStateChange(NotepadActionState.FULL_EDITOR)
                        },
                        onSave = {
                            viewModel.addNote(
                                title = it.title,
                                content = it.content,
                                color = it.color,
                                fontSize = it.fontSize,
                                isBold = it.isBold,
                                isItalic = it.isItalic
                            )
                            onDraftChange(null)
                            onStateChange(NotepadActionState.TOOLBAR)
                        }
                    )
                }
                NotepadActionState.FULL_EDITOR -> {
                    FullExpressiveEditor(
                        viewModel = viewModel,
                        note = initialDraft ?: Note(title = "", content = "", color = 0),
                        onDismiss = { 
                            onDraftChange(null)
                            onStateChange(NotepadActionState.TOOLBAR) 
                        },
                        onMinimize = { updatedNote ->
                            onDraftChange(updatedNote)
                            onStateChange(NotepadActionState.EDITOR)
                        },
                        availableTracks = availableTracks,
                        availablePdfs = availablePdfs,
                        onImagePickRequest = onImagePickRequest,
                        newImageUri = newImageUri,
                        onImageConsumed = onImageConsumed,
                        onViewPdf = onViewPdf
                    )
                }
                NotepadActionState.AI_TOOLS -> {
                    AiToolsPopup(
                        state = state,
                        viewModel = viewModel,
                        notes = notes,
                        selectedNotes = selectedNotes,
                        initialNote = initialDraft,
                        onDismiss = { 
                            if (selectedNotes.isNotEmpty()) onStateChange(NotepadActionState.SELECTION)
                            else if (viewedNoteId != null) onStateChange(NotepadActionState.VIEWER)
                            else onStateChange(NotepadActionState.TOOLBAR) 
                        },
                        onGenerate = {
                            onStateChange(NotepadActionState.TOOLBAR)
                            onNoteGenerated(it)
                        },
                        onEdit = { original, gen ->
                            if (viewedNoteId != null) onStateChange(NotepadActionState.VIEWER)
                            else onStateChange(NotepadActionState.TOOLBAR)
                            onNoteEdited(original, gen)
                        }
                    )
                }
                NotepadActionState.VIEWER -> {
                    // Exactly ONE 3-dots button (explicit below). trailingContent
                    // is null so the toolbar never adds its own overflow icon.
                    ToolzHorizontalFloatingToolbar(
                        expanded = true,
                        containerColor = Color.Transparent,
                        content = {
                            if (aiToolsEnabled && !offlineMode) {
                                ToolzExpressiveIconButton(
                                    onClick = {
                                        haptic.click()
                                        onStateChange(NotepadActionState.AI_TOOLS)
                                    },
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.st_NotepadScreen_cdait76))
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            ToolzExpressiveIconButton(
                                onClick = {
                                    haptic.click()
                                    onStateChange(NotepadActionState.FULL_EDITOR)
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.st_NotepadScreen_cdedit73))
                            }
                            Spacer(Modifier.width(8.dp))
                            ToolzExpressiveIconButton(
                                onClick = {
                                    haptic.tick()
                                    viewedNoteId?.let { onNoteOptionsRequest(it) }
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.st_NotepadScreen_cdopt74))
                            }
                            Spacer(Modifier.width(8.dp))
                            VerticalDivider(
                                modifier = Modifier.height(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f)
                            )
                            Spacer(Modifier.width(8.dp))
                            ToolzExpressiveIconButton(
                                onClick = {
                                    haptic.longClick()
                                    initialDraft?.let { onDeleteRequest(it) }
                                    onStateChange(NotepadActionState.TOOLBAR)
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.st_NotepadScreen_cddel64))
                            }
                            Spacer(Modifier.width(8.dp))
                            ToolzExpressiveIconButton(
                                onClick = {
                                    haptic.click()
                                    onStateChange(NotepadActionState.TOOLBAR)
                                    onDraftChange(null) // Clear viewed note id
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_NotepadScreen_cdclose75))
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FullExpressiveEditor(
    viewModel: NotepadViewModel,
    note: Note,
    onDismiss: () -> Unit,
    onMinimize: (Note) -> Unit,
    onViewPdf: (String, Int) -> Unit = { _, _ -> },
    availableTracks: List<com.frerox.toolz.data.music.MusicTrack> = emptyList(),
    availablePdfs: List<com.frerox.toolz.data.pdf.PdfFile> = emptyList(),
    onImagePickRequest: () -> Unit = {},
    newImageUri: Uri? = null,
    onImageConsumed: () -> Unit = {},
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val haptic = rememberToolzHapticFeedback()
    val isFocusMode by viewModel.isFocusMode.collectAsStateWithLifecycle()
    val aiStyle by viewModel.aiStyle.collectAsStateWithLifecycle()
    val isAiStyling by viewModel.isAiStyling.collectAsStateWithLifecycle()
    val offlineMode by viewModel.offlineModeEnabled.collectAsStateWithLifecycle(false)
    val notepadAiEnabled by viewModel.notepadAiEnabled.collectAsStateWithLifecycle(true)
    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Editor State — keyed on note id so switching notes never shows stale content.
    var currentNote by remember(note.id) { mutableStateOf(note) }
    var showColorGrid by remember { mutableStateOf(false) }
    var showAiStyleBanner by remember { mutableStateOf(false) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showPdfPicker by remember { mutableStateOf(false) }
    var isBrowsingPdf by remember { mutableStateOf(false) }
    val editorScope = rememberCoroutineScope()

    // SAF fallback: pick any PDF from the system picker (works without
    // All-files access). Persisted + copied to app-private so the note
    // keeps opening after reboot.
    val pdfBrowseLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        isBrowsingPdf = false
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        editorScope.launch {
            try {
                if (currentNote.id != 0) {
                    val result = viewModel.attachPdf(currentNote.id, uri)
                    // Sync the local editor copy so SAVE doesn't wipe the
                    // dual-written legacy slot with a stale null.
                    if (result is PdfAttachResult.Attached && result.uri.isNotBlank()) {
                        currentNote = currentNote.copy(
                            attachedPdfUri = currentNote.attachedPdfUri ?: result.uri
                        )
                    }
                    val msg = when (result) {
                        is PdfAttachResult.Attached -> context.getString(R.string.notepad_pdf_attached)
                        PdfAttachResult.Capped -> context.getString(R.string.notepad_pdf_capped)
                        PdfAttachResult.Unreadable -> context.getString(R.string.notepad_pdf_unreadable)
                        is PdfAttachResult.Failed -> context.getString(R.string.notepad_attach_failed)
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.refreshPdfs()
                } else {
                    val durable = viewModel.resolvePdfForDraft(uri)
                    if (durable != null) {
                        currentNote = currentNote.copy(attachedPdfUri = durable)
                        viewModel.refreshPdfs()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.notepad_pdf_unreadable),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } finally {
                isBrowsingPdf = false
                showPdfPicker = false
            }
        }
    }

    LaunchedEffect(aiStyle) {
        if (aiStyle != null) showAiStyleBanner = true
    }

    LaunchedEffect(newImageUri) {
        newImageUri?.let {
            currentNote = currentNote.copy(attachedImageUri = it.toString())
            onImageConsumed()
        }
    }

    val noteColor = if (currentNote.color == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color(currentNote.color)
    val onColor = if (currentNote.color == 0) MaterialTheme.colorScheme.onSurface else noteContentColor(noteColor)

    val words = remember(currentNote.content) { com.frerox.toolz.data.notepad.NoteText.wordCount(currentNote.content) }
    val chars = currentNote.content.length
    val readingTime = remember(currentNote.content) { com.frerox.toolz.data.notepad.NoteText.readingMinutes(currentNote.content) }

    val optionsAlpha by animateFloatAsState(
        targetValue = if (isFocusMode) 0f else 1f,
        animationSpec = tween(400),
        label = "optionsAlpha"
    )

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val editorScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.9f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "editorScale"
    )
    val editorAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "editorAlpha"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .graphicsLayer {
                scaleX = editorScale
                scaleY = editorScale
                alpha = editorAlpha
            },
        // Paint the sheet in the live note color (was Transparent, which left a
        // dull scrim and ignored color picks in the overlay-edit path).
        containerColor = noteColor,
        contentColor = onColor,
        topBar = {
            ExpressiveTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isFocusMode,
                        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                        label = "titleAnim"
                    ) { focused ->
                        Text(
                            stringResource(if (focused) R.string.st_NotepadScreen_edfocus81 else R.string.st_NotepadScreen_ededtr82),
                            fontWeight = FontWeight.Black,
                            color = onColor,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onMinimize(currentNote) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.st_NotepadScreen_cdmin78), tint = onColor, modifier = Modifier.size(28.dp))
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = !isFocusMode,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (notepadAiEnabled && !offlineMode) {
                                ToolzExpressiveIconButton(
                                    onClick = { 
                                        haptic.click()
                                        viewModel.suggestStyleForNote(currentNote)
                                    },
                                    enabled = !isAiStyling,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = onColor.copy(0.1f),
                                        contentColor = onColor
                                    ),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    if (isAiStyling) {
                                        ToolzWavyCircularProgressIndicator(modifier = Modifier.size(20.dp), color = onColor)
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, stringResource(R.string.st_NotepadScreen_cdaist79), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                    
                    ToolzExpressiveIconButton(
                        onClick = { viewModel.toggleFocusMode(context) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isFocusMode) onColor.copy(0.2f) else onColor.copy(0.1f),
                            contentColor = onColor
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            if (isFocusMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, 
                            stringResource(R.string.st_NotepadScreen_cdfocus80),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(8.dp))

                    ToolzExpressiveButton(
                        onClick = {
                            if (currentNote.id == 0) {
                                viewModel.addNote(
                                    title = currentNote.title,
                                    content = currentNote.content,
                                    color = currentNote.color,
                                    fontSize = currentNote.fontSize,
                                    isBold = currentNote.isBold,
                                    isItalic = currentNote.isItalic,
                                    attachedPdfUri = currentNote.attachedPdfUri,
                                    attachedAudioUri = currentNote.attachedAudioUri,
                                    attachedAudioName = currentNote.attachedAudioName,
                                    attachedImageUri = currentNote.attachedImageUri
                                )
                            } else {
                                viewModel.updateNote(currentNote.copy(timestamp = System.currentTimeMillis()))
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = onColor, contentColor = noteColor),
                        modifier = Modifier.padding(end = 12.dp),
                        shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(16.dp))
                    ) {
                        Text(stringResource(R.string.st_NotepadScreen_save83), fontWeight = FontWeight.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isFocusMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = notepadAiEnabled && !offlineMode && showAiStyleBanner && aiStyle != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        aiStyle?.let { style ->
                            AiStyleBanner(
                                style = style,
                                onColor = onColor,
                                onAccept = {
                                    try {
                                        currentNote = currentNote.copy(
                                            color = android.graphics.Color.parseColor(style.colorHex),
                                            fontSize = style.fontSize,
                                            isBold = style.isBold,
                                            isItalic = style.isItalic
                                        )
                                    } catch (_: Exception) {}
                                    showAiStyleBanner = false
                                    viewModel.clearAiStyle()
                                },
                                onDismiss = {
                                    showAiStyleBanner = false
                                    viewModel.clearAiStyle()
                                }
                            )
                        }
                    }

                    // Editor stats & Attachment pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = onColor.copy(0.1f),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(pluralStringResource(R.plurals.st_NotepadScreen_words61, words, words), style = MaterialTheme.typography.labelSmall, color = onColor)
                                Text("•", style = MaterialTheme.typography.labelSmall, color = onColor)
                                Text(stringResource(R.string.st_NotepadScreen_chrfs136, chars), style = MaterialTheme.typography.labelSmall, color = onColor)
                                Text("•", style = MaterialTheme.typography.labelSmall, color = onColor)
                                Text(stringResource(R.string.st_NotepadScreen_mintl137, readingTime), style = MaterialTheme.typography.labelSmall, color = onColor)
                            }
                        }
                        
                        IconButton(
                            onClick = { showAttachmentMenu = true },
                            modifier = Modifier.size(32.dp).background(onColor.copy(0.1f), CircleShape)
                        ) {
                            Icon(Icons.Rounded.AttachFile, stringResource(R.string.st_NotepadScreen_att37), modifier = Modifier.size(18.dp), tint = onColor)
                        }
                    }

                    // Animated Color Grid
                    AnimatedVisibility(
                        visible = showColorGrid,
                        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
                    ) {
                        VerticalColorPickerBanner(
                            onColor = onColor,
                            selectedColor = currentNote.color,
                            onColorSelected = { currentNote = currentNote.copy(color = it) },
                            onCustomColor = { showCustomColorDialog = true },
                            onDismiss = { showColorGrid = false }
                        )
                    }

                    // Toolbar with Color Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolzExpressiveIconToggleButton(
                            checked = currentNote.isBold,
                            onCheckedChange = { currentNote = currentNote.copy(isBold = it) },
                            colors = IconButtonDefaults.iconToggleButtonColors(contentColor = onColor, checkedContentColor = noteColor, checkedContainerColor = onColor)
                        ) { Icon(Icons.Rounded.FormatBold, stringResource(R.string.st_NotepadScreen_cdbold84)) }
                        
                        ToolzExpressiveIconToggleButton(
                            checked = currentNote.isItalic,
                            onCheckedChange = { currentNote = currentNote.copy(isItalic = it) },
                            colors = IconButtonDefaults.iconToggleButtonColors(contentColor = onColor, checkedContentColor = noteColor, checkedContainerColor = onColor)
                        ) { Icon(Icons.Rounded.FormatItalic, stringResource(R.string.st_NotepadScreen_cdital85)) }
                        
                        ToolzExpressiveIconToggleButton(
                            checked = showColorGrid,
                            onCheckedChange = { showColorGrid = it },
                            colors = IconButtonDefaults.iconToggleButtonColors(contentColor = onColor, checkedContentColor = noteColor, checkedContainerColor = onColor)
                        ) { Icon(Icons.Rounded.Palette, stringResource(R.string.st_NotepadScreen_cdcol86)) }
                        
                        Spacer(Modifier.weight(1f))
                        
                        FontSizeControl(currentNote.fontSize, onColor) { currentNote = currentNote.copy(fontSize = it) }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            StaggeredEntrance(index = 0) {
                TextField(
                    value = currentNote.title,
                    onValueChange = { currentNote = currentNote.copy(title = it) },
                    placeholder = { Text(stringResource(R.string.st_NotepadScreen_phtitle87), style = MaterialTheme.typography.headlineMedium, color = onColor.copy(0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = onColor),
                    colors = transparentTextFieldColors(cursorColor = onColor, textColor = onColor)
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            StaggeredEntrance(index = 1) {
                TextField(
                    value = currentNote.content,
                    onValueChange = { currentNote = currentNote.copy(content = it) },
                    placeholder = { Text(stringResource(R.string.st_NotepadScreen_phbody88), style = MaterialTheme.typography.bodyLarge, color = onColor.copy(0.5f)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = currentNote.fontSize.sp,
                        fontWeight = if (currentNote.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (currentNote.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                        color = onColor
                    ),
                    colors = transparentTextFieldColors(cursorColor = onColor, textColor = onColor)
                )
            }
            
            // Attachments preview
            if (currentNote.attachedImageUri != null || currentNote.attachedPdfUri != null || currentNote.attachedAudioUri != null) {
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    currentNote.attachedImageUri?.let { uri ->
                        StaggeredEntrance(index = 2) {
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = stringResource(R.string.st_NotepadScreen_cdattimg89),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.FillWidth
                                )
                                ToolzExpressiveIconButton(
                                    onClick = { currentNote = currentNote.copy(attachedImageUri = null) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.Black.copy(0.45f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_NotepadScreen_cdrmimg90), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    currentNote.attachedPdfUri?.let { uri ->
                        StaggeredEntrance(index = 3) {
                            Box {
                                PdfPreview(uri = uri, onClick = { onViewPdf(uri, 0) }, modifier = Modifier.height(150.dp))
                                ToolzExpressiveIconButton(
                                    onClick = {
                                        currentNote = currentNote.copy(attachedPdfUri = null)
                                        // Also drop the hidden V2 row for this URI so
                                        // it can't resurface via reverse-heal.
                                        if (currentNote.id != 0) {
                                            editorScope.launch {
                                                try { viewModel.removeAttachmentByUri(currentNote.id, uri) } catch (_: Exception) { }
                                            }
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.Black.copy(0.45f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_NotepadScreen_cdrmpdf91), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    currentNote.attachedAudioUri?.let { uri ->
                        StaggeredEntrance(index = 4) {
                            val editorTrack = availableTracks.find { it.uri == uri }
                                ?: musicState.tracks.find { it.uri == uri }
                            val editorIsCurrent = isCurrentNoteTrack(musicState.currentTrack, uri)
                            val editorIsPlaying = musicState.isPlaying && editorIsCurrent
                            val editorAudioName = currentNote.attachedAudioName
                                ?: stringResource(R.string.st_NotepadScreen_noteaud94)
                            CurrentTrackProgress(
                                isCurrent = editorIsCurrent,
                                durationMs = musicState.duration,
                                flow = musicViewModel.playbackPosition,
                            ) { editorProgress, editorLivePos ->
                            Box {
                                MusicPill(
                                    title = currentNote.attachedAudioName ?: editorTrack?.title ?: stringResource(R.string.st_NotepadScreen_aud93),
                                    isPlaying = editorIsPlaying,
                                    isCurrentTrack = editorIsCurrent,
                                    thumbnail = editorTrack?.thumbnailUri,
                                    artist = editorTrack?.artist,
                                    containerColor = onColor.copy(alpha = 0.1f),
                                    contentColor = onColor,
                                    onClick = {
                                        // Inline preview — no navigation.
                                        if (editorIsCurrent) {
                                            musicViewModel.togglePlayPause()
                                        } else {
                                            if (editorTrack != null) {
                                                musicViewModel.playTrack(editorTrack)
                                            } else {
                                                try {
                                                    musicViewModel.playUri(
                                                        Uri.parse(uri),
                                                        editorAudioName
                                                    )
                                                } catch (_: Exception) { }
                                            }
                                        }
                                    },
                                    progress = editorProgress,
                                    positionMs = editorLivePos,
                                    durationMs = musicState.duration,
                                    onSeek = { frac ->
                                        val d = musicViewModel.uiState.value.duration
                                        if (editorIsCurrent && d > 0L) {
                                            musicViewModel.seekTo((frac * d).toLong())
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(end = 40.dp)
                                )
                                ToolzExpressiveIconButton(
                                    onClick = {
                                        val doomed = currentNote
                                        currentNote = currentNote.copy(attachedAudioUri = null, attachedAudioName = null)
                                        if (doomed.id != 0 && doomed.attachedAudioUri != null) {
                                            editorScope.launch {
                                                try { viewModel.removeAttachmentByUri(doomed.id, doomed.attachedAudioUri!!) } catch (_: Exception) { }
                                            }
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.4f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_NotepadScreen_cdrmaud92), modifier = Modifier.size(18.dp))
                                }
                            }
                            } // CurrentTrackProgress
                        }
                    }
                }
            }
            // V2: saved-note multi-attachments. Deliberately OUTSIDE the
            // legacy-if above: V2-only attaches (no legacy slots set) must
            // still render, otherwise a successful attach looks like nothing
            // happened. The strip self-hides for drafts (id == 0) and when
            // the note has no PDF rows.
            if (currentNote.id != 0) {
                NotePdfAttachmentStrip(
                    viewModel = viewModel,
                    note = currentNote,
                    onOpenPdf = onViewPdf,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    // Legacy thumbnail card above already shows this URI —
                    // without this the same PDF renders as two cards.
                    excludeUris = setOfNotNull(currentNote.attachedPdfUri)
                )
            }
            Spacer(Modifier.height(100.dp))
        }
    }

    if (showAttachmentMenu) {
        AttachmentMenuSheet(
            onDismiss = { showAttachmentMenu = false },
            onPickAudio = { 
                showAudioPicker = true
                showAttachmentMenu = false 
            },
            onPickPdf = { 
                showPdfPicker = true
                showAttachmentMenu = false 
            },
            onPickImage = { 
                onImagePickRequest()
                showAttachmentMenu = false 
            }
        )
    }

    if (showAudioPicker) {
        AttachmentPickerDialog(
            title = stringResource(R.string.st_NotepadScreen_apatitle95),
            isAudio = true,
            items = availableTracks.map { it.title to it.uri },
            emptyText = stringResource(R.string.st_NotepadScreen_nthing160),
            onDismiss = { showAudioPicker = false },
            onSelect = { name, uri ->
                currentNote = currentNote.copy(attachedAudioUri = uri, attachedAudioName = name)
                showAudioPicker = false
            },
            viewModel = viewModel
        )
    }

    LaunchedEffect(showPdfPicker) {
        if (showPdfPicker) viewModel.refreshPdfs()
    }

    val pdfPickerItems = remember(availablePdfs) { availablePdfs.map { it.name to it.uri.toString() } }
    if (showPdfPicker) {
        AttachmentPickerDialog(
            title = stringResource(R.string.st_NotepadScreen_apdftit96),
            isAudio = false,
            items = pdfPickerItems,
            onDismiss = { showPdfPicker = false },
            emptyText = stringResource(R.string.st_NotepadScreen_pdfempty97),
            browseLabel = if (isBrowsingPdf) stringResource(R.string.st_NotepadScreen_opening98) else stringResource(R.string.st_NotepadScreen_browse99),
            onBrowse = {
                if (!isBrowsingPdf) {
                    isBrowsingPdf = true
                    try {
                        pdfBrowseLauncher.launch(arrayOf("application/pdf"))
                    } catch (_: Exception) {
                        isBrowsingPdf = false
                    }
                }
            },
            onSelect = { name, uri ->
                showPdfPicker = false
                val noteId = currentNote.id
                val parsed = try { Uri.parse(uri) } catch (_: Exception) { null }
                if (parsed == null) {
                    android.widget.Toast.makeText(context, context.getString(R.string.notepad_pdf_unreadable), android.widget.Toast.LENGTH_SHORT).show()
                } else if (noteId != 0) {
                    // Saved note: persist into multi-attach table (grant verified).
                    editorScope.launch {
                        val result = viewModel.attachPdf(noteId, parsed)
                        if (result is PdfAttachResult.Attached && result.uri.isNotBlank()) {
                            currentNote = currentNote.copy(
                                attachedPdfUri = currentNote.attachedPdfUri ?: result.uri
                            )
                        }
                        val msg = when (result) {
                            is PdfAttachResult.Attached -> context.getString(R.string.notepad_pdf_attached)
                            PdfAttachResult.Capped -> context.getString(R.string.notepad_pdf_capped)
                            PdfAttachResult.Unreadable -> context.getString(R.string.notepad_pdf_unreadable)
                            is PdfAttachResult.Failed -> context.getString(R.string.notepad_attach_failed)
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Draft: resolve to a durable copy now so SAVE keeps a
                    // readable URI even for fragile MediaStore entries.
                    val picked = Uri.parse(uri)
                    editorScope.launch {
                        val durable = try {
                            viewModel.resolvePdfForDraft(picked)
                        } catch (_: Exception) {
                            null
                        }
                        if (durable != null) {
                            currentNote = currentNote.copy(attachedPdfUri = durable)
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.notepad_pdf_unreadable),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            },
            viewModel = viewModel
        )
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initialColor = currentNote.color,
            onDismiss = { showCustomColorDialog = false },
            onColorSelected = { 
                currentNote = currentNote.copy(color = it)
                showCustomColorDialog = false
            }
        )
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setFocusMode(false)
        }
    }
}

@Composable
private fun QuickEditor(
    draft: Note,
    onDraftUpdate: (Note) -> Unit,
    onDismiss: () -> Unit,
    onFullScreen: () -> Unit,
    onSave: (Note) -> Unit
) {
    val haptic = rememberToolzHapticFeedback()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.EditNote,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    stringResource(R.string.st_NotepadScreen_qn45),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolzExpressiveIconButton(
                    onClick = {
                        haptic.click()
                        onFullScreen()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(0.4f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Rounded.OpenInFull,
                        stringResource(R.string.st_NotepadScreen_cdxpand101),
                        modifier = Modifier.size(16.dp)
                    )
                }
                ToolzExpressiveIconButton(
                    onClick = {
                        haptic.tick()
                        onDismiss()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(R.string.st_NotepadScreen_cdclosegen102),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextField(
                value = draft.title,
                onValueChange = { onDraftUpdate(draft.copy(title = it)) },
                placeholder = {
                    Text(
                        stringResource(R.string.st_NotepadScreen_phtitle87),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = transparentTextFieldColors(
                    cursorColor = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onSurface
                ),
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                singleLine = true
            )

            TextField(
                value = draft.content,
                onValueChange = { onDraftUpdate(draft.copy(content = it)) },
                placeholder = {
                    Text(
                        stringResource(R.string.st_NotepadScreen_phbody88),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 200.dp),
                colors = transparentTextFieldColors(
                    cursorColor = MaterialTheme.colorScheme.primary,
                    textColor = MaterialTheme.colorScheme.onSurface
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }

        ToolzExpressiveButton(
            onClick = {
                haptic.success()
                onSave(draft)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(16.dp))
        ) {
            Text(
                stringResource(R.string.st_NotepadScreen_sn46),
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AiToolsPopup(
    state: NotepadActionState,
    viewModel: NotepadViewModel,
    notes: List<Note>,
    selectedNotes: List<Note> = emptyList(),
    initialNote: Note? = null,
    onDismiss: () -> Unit,
    onGenerate: (AiGeneratedNote?) -> Unit,
    onEdit: (Note, AiGeneratedNote?) -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val scope = rememberCoroutineScope()
    
    // Initial step depends on selection
    var step by remember { 
        mutableIntStateOf(0) 
    }
    
    var selectedNote by remember(state, initialNote, selectedNotes) {
        mutableStateOf(
            if (state == NotepadActionState.VIEWER) initialNote
            else if (selectedNotes.size == 1) selectedNotes.first()
            else null
        )
    }

    var aiLoading by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var showModelSettings by remember { mutableStateOf(false) }

    var aiMode by remember {
        mutableIntStateOf(
            if (selectedNotes.isNotEmpty()) 1 // Default to Edit if selected
            else 0
        )
    }

    val models by viewModel.availableModels.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedAiModel.collectAsStateWithLifecycle()
    val aiSummary by viewModel.aiSummary.collectAsStateWithLifecycle()
    // Publishing AI results must be a side-effect, never during composition.
    LaunchedEffect(aiSummary) {
        if (aiSummary != null) aiLoading = false
    }
    // Guard: entering summarize with no note must not spin forever.
    LaunchedEffect(step, aiMode, selectedNote) {
        if (step == 3 && aiMode == 2 && selectedNote == null && state != NotepadActionState.VIEWER) {
            aiLoading = false
        }
    }

    BackHandler(enabled = step > 0) {
        when (step) {
            1 -> step = 0
            2 -> {
                if (state == NotepadActionState.VIEWER || selectedNotes.size == 1) step = 0
                else step = 1
            }
            3 -> {
                if (state == NotepadActionState.VIEWER || selectedNotes.size == 1) step = 0
                else step = 1
                viewModel.clearAiSummary()
            }
        }
    }

    // Handle initial state for multiple selected notes
    LaunchedEffect(Unit) {
        if (selectedNotes.size > 1) {
            // If multiple notes selected, we show the menu first to choose Edit/Summarize
            // then go to picker if needed.
            aiMode = 1 // Default to edit
            step = 0 
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                @Suppress("DEPRECATION")
                Text(stringResource(R.string.st_NotepadScreen_ant47), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
            Row {
                ToolzExpressiveIconButton(
                    onClick = { showModelSettings = true },
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                ToolzExpressiveIconButton(
                    onClick = onDismiss,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (showModelSettings) {
            ModelSettingsView(
                models = models,
                selectedModel = selectedModel,
                onSelect = { viewModel.setSelectedModel(it) },
                onBack = { showModelSettings = false }
            )
        } else {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "aiStepTransition"
            ) { targetStep ->
                when (targetStep) {
                    0 -> {
                        AiMenu(
                            isSelectionMode = selectedNotes.isNotEmpty(),
                            isViewerMode = state == NotepadActionState.VIEWER,
                            onGenerate = {
                                aiMode = 0
                                step = 2 
                            },
                            onEdit = {
                                aiMode = 1
                                if (state == NotepadActionState.VIEWER || selectedNotes.size == 1) {
                                    // already set selectedNote in remember
                                    step = 2
                                } else {
                                    step = 1
                                }
                            },
                            onSummarize = {
                                aiMode = 2
                                if (state == NotepadActionState.VIEWER || selectedNotes.size == 1) {
                                    val target = selectedNote
                                        ?: (if (selectedNotes.size == 1) selectedNotes.first() else initialNote)
                                    if (target != null) {
                                        selectedNote = target
                                        step = 3
                                        aiLoading = true
                                        viewModel.onNoteFocused(target)
                                        viewModel.summarizeNote(target)
                                    } else {
                                        aiLoading = false
                                    }
                                } else {
                                    step = 1
                                }
                            }
                        )
                    }
                    1 -> {
                        NotePicker(
                            notes = if (selectedNotes.isNotEmpty()) selectedNotes else notes,
                            onSelect = {
                                selectedNote = it
                                if (aiMode == 1) {
                                    step = 2
                                } else {
                                    step = 3
                                    aiLoading = true
                                    viewModel.onNoteFocused(it)
                                    viewModel.summarizeNote(it)
                                }
                            },
                            onBack = { 
                                step = 0 
                            }
                        )
                    }
                    2 -> {
                        PromptInput(
                            prompt = aiPrompt,
                            isGenerate = aiMode == 0,
                            onPromptChange = { aiPrompt = it },
                            onConfirm = {
                                if (aiLoading) return@PromptInput
                                if (aiPrompt.isBlank()) return@PromptInput
                                if (aiMode != 0 && selectedNote == null) return@PromptInput
                                aiLoading = true
                                if (aiMode == 0) {
                                    viewModel.generateNoteAi(aiPrompt) { gen ->
                                        aiLoading = false
                                        onGenerate(gen)
                                    }
                                } else {
                                    selectedNote?.let { note ->
                                        viewModel.editNoteWithPromptAi(note, aiPrompt) { gen ->
                                            aiLoading = false
                                            onEdit(note, gen)
                                        }
                                    } ?: run { aiLoading = false }
                                }
                            },
                            onBack = { 
                                if (state == NotepadActionState.VIEWER || selectedNotes.size == 1) step = 0
                                else if (aiMode == 0) step = 0 
                                else step = 1 
                            }
                        )
                    }
                    3 -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ToolzExpressiveIconButton(
                                        onClick = { 
                                            if (state == NotepadActionState.VIEWER || selectedNotes.size == 1) step = 0
                                            else step = 1
                                            viewModel.clearAiSummary() 
                                        }, 
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.st_NotepadScreen_sumttl141), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                
                                if (aiSummary != null && !aiLoading) {
                                    ToolzExpressiveIconButton(
                                        onClick = {
                                            selectedNote?.let {
                                                aiLoading = true
                                                viewModel.summarizeNote(it, forceRegenerate = true)
                                            }
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.4f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            aiSummary?.let { summary ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = MediumExpressiveShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TypewriterText(
                                        text = summary,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (aiLoading) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                ToolzLoadingIndicator()
            }
        }
    }
}

@Composable
private fun ModelSettingsView(
    models: List<String>,
    selectedModel: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolzExpressiveIconButton(
                onClick = onBack, 
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_NotepadScreen_aimodels142), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            lazyItems(models) { model ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(model) },
                    color = if (model == selectedModel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Text(model, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AiMenu(
    isSelectionMode: Boolean = false,
    isViewerMode: Boolean = false,
    onGenerate: () -> Unit,
    onEdit: () -> Unit,
    onSummarize: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isViewerMode && !isSelectionMode) {
            AiMenuItem(stringResource(R.string.st_NotepadScreen_ainnew143), stringResource(R.string.st_NotepadScreen_ainnewd144), Icons.Rounded.Add, onGenerate)
        }
        AiMenuItem(
            stringResource(R.string.st_NotepadScreen_aitrn145),
            if (isViewerMode || (isSelectionMode && !isViewerMode)) stringResource(R.string.st_NotepadScreen_aitrnt146) else stringResource(R.string.st_NotepadScreen_aitnre147),
            Icons.Rounded.Edit,
            onEdit,
        )
        AiMenuItem(stringResource(R.string.st_NotepadScreen_aisum148), stringResource(R.string.st_NotepadScreen_aisd149), Icons.Rounded.Summarize, onSummarize)
    }
}

@Composable
private fun AiMenuItem(title: String, desc: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
            }
        }
    }
}

@Composable
private fun NotePicker(
    notes: List<Note>,
    onSelect: (Note) -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolzExpressiveIconButton(
                onClick = onBack, 
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_NotepadScreen_aipick150), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lazyItems(notes) { note ->
                Surface(
                    onClick = { onSelect(note) },
                    modifier = Modifier.size(100.dp),
                    shape = SmallExpressiveShape,
                    color = noteSurfaceColor(note.color, MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(Modifier.padding(8.dp)) {
                        Text(note.title, style = MaterialTheme.typography.labelSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptInput(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    isGenerate: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolzExpressiveIconButton(
                onClick = onBack, 
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (isGenerate) stringResource(R.string.st_NotepadScreen_aigenq151) else stringResource(R.string.st_NotepadScreen_aiedq152), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        TextField(
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text(stringResource(R.string.st_NotepadScreen_aihint153)) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )
        ToolzExpressiveButton(onClick = onConfirm, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.st_NotepadScreen_aigo154), fontWeight = FontWeight.Black)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrashBottomSheet(
    deletedNotes: List<Note>,
    onDismiss: () -> Unit,
    onRestore: (Note) -> Unit,
    onDeletePermanently: (Note) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val haptic = rememberToolzHapticFeedback()
    var confirmEmpty by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmDeleteId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val trashDateFmt = androidx.compose.runtime.remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp, 4.dp).background(MaterialTheme.colorScheme.onSurface.copy(0.2f), CircleShape))
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.st_NotepadScreen_t48),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        pluralStringResource(R.plurals.st_NotepadScreen_trashcnt155, deletedNotes.size, deletedNotes.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                    )
                }
                
                if (deletedNotes.isNotEmpty()) {
                    ToolzOutlinedExpressiveButton(
                        onClick = { haptic.longClick(); confirmEmpty = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(0.3f))
                    ) {
                        Text(stringResource(R.string.st_NotepadScreen_et49), fontWeight = FontWeight.Black)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (deletedNotes.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.st_NotepadScreen_tie50),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    lazyItems(deletedNotes, key = { it.id }) { note ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = noteSurfaceColor(note.color, MaterialTheme.colorScheme.surfaceContainerHighest).copy(0.1f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHighest),
                            shape = LargeExpressiveShape,
                            border = BorderStroke(1.dp, noteSurfaceColor(note.color, MaterialTheme.colorScheme.surfaceContainerHighest).copy(0.2f))
                        ) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    Modifier.size(8.dp).background(noteSurfaceColor(note.color, MaterialTheme.colorScheme.surfaceContainerHighest), CircleShape)
                                )
                                Column(Modifier.weight(1f)) {
                                    val title = if (note.title.isBlank()) stringResource(R.string.st_NotepadScreen_u28) else note.title
                                    Text(
                                        title,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (note.deletedTimestamp <= 0L) ""
                                        else trashDateFmt.format(Date(note.deletedTimestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                                    )
                                }
                                
                                    IconButton(onClick = { haptic.click(); onRestore(note) }) {
                                    Icon(Icons.Rounded.Restore, stringResource(R.string.st_NotepadScreen_cdrestore156), tint = MaterialTheme.colorScheme.primary)
                                }
                                
                                IconButton(onClick = { haptic.click(); confirmDeleteId = note.id }) {
                                    Icon(Icons.Rounded.DeleteForever, stringResource(R.string.st_NotepadScreen_d44), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            if (confirmEmpty) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmEmpty = false },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmEmpty = false; onEmptyTrash() }) {
                            Text(stringResource(R.string.st_NotepadScreen_et49))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { confirmEmpty = false }) {
                            Text(stringResource(R.string.st_NotepadScreen_c31))
                        }
                    },
                    title = { Text(stringResource(R.string.st_NotepadScreen_et49)) },
                    text = { Text(stringResource(R.string.st_NotepadScreen_etrb159)) },
                )
            }
            confirmDeleteId?.let { doomedId ->
                deletedNotes.firstOrNull { it.id == doomedId }?.let { doomed ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmDeleteId = null },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { confirmDeleteId = null; onDeletePermanently(doomed) }) {
                                Text(stringResource(R.string.st_NotepadScreen_d44))
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { confirmDeleteId = null }) {
                                Text(stringResource(R.string.st_NotepadScreen_c31))
                            }
                        },
                        title = { Text(stringResource(R.string.st_NotepadScreen_delt157)) },
                        text = { Text(stringResource(R.string.st_NotepadScreen_delb158)) },
                    )
                }
            }
        }
    }
}
