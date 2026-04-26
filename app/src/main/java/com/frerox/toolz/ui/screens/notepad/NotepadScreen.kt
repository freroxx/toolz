package com.frerox.toolz.ui.screens.notepad

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.ui.components.MarkdownSegment
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.parseMarkdownToSegments
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────
//  Note colour utility
// ─────────────────────────────────────────────────────────────

private fun isDark(color: Color): Boolean {
    val lum = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return (1.0 - lum) >= 0.5
}

private fun noteContentColor(noteColor: Color) =
    if (isDark(noteColor)) Color.White else Color.Black

// ─────────────────────────────────────────────────────────────
//  Main screen
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(
    viewModel     : NotepadViewModel,
    onBack        : () -> Unit,
    onPlayAudio   : (String) -> Unit,
    onViewPdf     : (String) -> Unit,
    initialNoteId : Int? = null,
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val notes       by viewModel.notes.collectAsStateWithLifecycle()
    val musicState  by musicViewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibration       = LocalVibrationManager.current

    var showEditor    by remember { mutableStateOf(false) }
    var noteToEdit    by remember { mutableStateOf<Note?>(null) }
    var searchQuery   by remember { mutableStateOf("") }
    var selectedNoteIds by remember { mutableStateOf(setOf<Int>()) }
    val isSelectionMode by remember { derivedStateOf { selectedNoteIds.isNotEmpty() } }

    val snackbar      = remember { SnackbarHostState() }
    val scope         = rememberCoroutineScope()

    // Deep-link to a specific note
    LaunchedEffect(initialNoteId, notes) {
        if (initialNoteId != null && notes.isNotEmpty()) {
            notes.find { it.id == initialNoteId }?.let {
                noteToEdit = it
            }
        }
    }

    var selectedCategory by remember { mutableStateOf("All") }

    val filteredNotes = remember(notes, searchQuery, selectedCategory) {
        notes
            .filter {
                (it.title.contains(searchQuery, true) || it.content.contains(searchQuery, true)) &&
                when (selectedCategory) {
                    "Pinned" -> it.isPinned
                    "Audio"  -> it.attachedAudioUri != null
                    "PDFs"   -> it.attachedPdfUri != null
                    "Images" -> it.attachedImageUri != null
                    else     -> true
                }
            }
            .sortedWith(
                compareByDescending<Note> { it.isPinned }
                    .thenByDescending { it.timestamp }
            )
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost   = { SnackbarHost(snackbar) },
        topBar         = {
            Column(
                Modifier
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(0.95f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
            ) {
                // Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = isSelectionMode,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "title"
                        ) { selecting ->
                            Text(
                                if (selecting) "${selectedNoteIds.size} SELECTED" else "MY NOTES",
                                fontWeight    = FontWeight.Black,
                                style         = MaterialTheme.typography.headlineMedium,
                                letterSpacing = (-1).sp,
                                color         = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            "${notes.size} total notes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isSelectionMode) {
                            IconButton(
                                onClick = {
                                    vibration?.vibrateLongClick()
                                    val notesToDelete = notes.filter { it.id in selectedNoteIds }
                                    viewModel.deleteNotes(notesToDelete)
                                    selectedNoteIds = emptySet()
                                    scope.launch {
                                        val r = snackbar.showSnackbar(
                                            "${notesToDelete.size} notes deleted",
                                            actionLabel = "UNDO",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (r == SnackbarResult.ActionPerformed) {
                                            vibration?.vibrateTick()
                                            viewModel.undoDelete()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)),
                            ) {
                                Icon(Icons.Rounded.Delete, "Delete Selected", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(
                                onClick = { selectedNoteIds = emptySet() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                            ) {
                                Icon(Icons.Rounded.Close, "Cancel")
                            }
                        } else {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                            }
                        }
                    }
                }

                // Glassy Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.1f))
                ) {
                    TextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder   = { 
                            Text(
                                "Search your thoughts…", 
                                color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                style = MaterialTheme.typography.bodyMedium
                            ) 
                        },
                        modifier      = Modifier.fillMaxWidth(),
                        leadingIcon   = {
                            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon  = if (searchQuery.isNotEmpty()) { {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                            }
                        }} else null,
                        singleLine    = true,
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }
                // Filter Chips
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .fadingEdge(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent)), 24.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf("All", "Pinned", "Audio", "PDFs", "Images")
                    
                    categories.forEach { category ->
                        val selected = selectedCategory == category
                        Surface(
                            onClick = { vibration?.vibrateTick(); selectedCategory = category },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(0.4f),
                            border = BorderStroke(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(0.1f))
                        ) {
                            Text(
                                category.uppercase(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        },
        floatingActionButton = {
            Surface(
                onClick = { vibration?.vibrateClick(); noteToEdit = null; showEditor = true },
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 8.dp)
                    .height(64.dp)
                    .widthIn(min = 160.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Text(
                        "NEW NOTE", 
                        fontWeight = FontWeight.Black, 
                        letterSpacing = 0.5.sp, 
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = spring(Spring.DampingRatioLowBouncy))) togetherWith
                    fadeOut(tween(300))
                },
                label = "categorySwitch"
            ) { targetCategory ->
                if (filteredNotes.isEmpty()) {
                    NotesEmptyState(isSearching = searchQuery.isNotEmpty(), selectedCategory = targetCategory)
                } else {
                    LazyVerticalStaggeredGrid(
                        columns             = StaggeredGridCells.Fixed(2),
                        modifier            = Modifier.fillMaxSize(),
                        contentPadding      = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalItemSpacing = 16.dp,
                    ) {
                        itemsIndexed(filteredNotes, key = { _, n -> n.id }) { _, note ->
                            NoteCard(
                                note                  = note,
                                isSelected            = note.id in selectedNoteIds,
                                isPlaying             = musicState.isPlaying && musicState.currentTrack?.uri == note.attachedAudioUri,
                                currentTrackThumbnail = musicState.currentTrack?.thumbnailUri,
                                onClick               = {
                                    if (isSelectionMode) {
                                        vibration?.vibrateTick()
                                        selectedNoteIds = if (note.id in selectedNoteIds) {
                                            selectedNoteIds - note.id
                                        } else {
                                            selectedNoteIds + note.id
                                        }
                                    } else {
                                        vibration?.vibrateClick()
                                        noteToEdit = note
                                        showEditor = true
                                    }
                                },
                                onLongClick           = {
                                    vibration?.vibrateLongClick()
                                    selectedNoteIds = selectedNoteIds + note.id
                                },
                            onDelete = {
                                if (!isSelectionMode) {
                                    vibration?.vibrateLongClick()
                                    viewModel.deleteNote(note)
                                    scope.launch {
                                        val r = snackbar.showSnackbar(
                                            "Note deleted",
                                            actionLabel = "UNDO",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (r == SnackbarResult.ActionPerformed) {
                                            vibration?.vibrateTick()
                                            viewModel.undoDelete()
                                        }
                                    }
                                }
                            },
                            onTogglePin = { 
                                if (!isSelectionMode) {
                                    vibration?.vibrateTick(); viewModel.togglePin(note) 
                                }
                            },
                            onPlayAudio = { vibration?.vibrateClick(); note.attachedAudioUri?.let { onPlayAudio(it) } },
                            onViewPdf   = { vibration?.vibrateClick(); note.attachedPdfUri?.let  { onViewPdf(it)   } },
                            modifier    = Modifier.animateItem(
                                fadeInSpec    = if (performanceMode) snap() else spring(),
                                fadeOutSpec   = if (performanceMode) snap() else spring(),
                                placementSpec = if (performanceMode) snap() else spring(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

    // ── Overlay layer ──────────────────────────────────────────────────────

    // Show editor for new note
    if (showEditor && noteToEdit == null) {
        NoteEditorDialog(
            note      = null,
            viewModel = viewModel,
            onDismiss = { showEditor = false },
            onSave    = { title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName, imageUri ->
                viewModel.addNote(title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName, imageUri)
                showEditor = false
            },
        )
    }

    // Show viewer for existing note
    noteToEdit?.let { note ->
        NoteViewerSheet(
            note                  = note,
            viewModel             = viewModel,
            isPlaying             = musicState.isPlaying && musicState.currentTrack?.uri == note.attachedAudioUri,
            currentTrackThumbnail = musicState.currentTrack?.thumbnailUri,
            musicViewModel        = musicViewModel,
            onDismiss             = { noteToEdit = null; showEditor = false },
            onEdit                = { showEditor = true },
            onPlayAudio           = onPlayAudio,
            onViewPdf             = onViewPdf,
        )

        if (showEditor) {
            NoteEditorDialog(
                note      = note,
                viewModel = viewModel,
                onDismiss = { showEditor = false },
                onSave    = { title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName, imageUri ->
                    val updated = note.copy(
                        title             = title,
                        content           = content,
                        color             = color,
                        fontStyle         = fontStyle,
                        fontSize          = fontSize,
                        isBold            = bold,
                        isItalic          = italic,
                        attachedPdfUri    = pdfUri,
                        attachedAudioUri  = audioUri,
                        attachedAudioName = audioName,
                        attachedImageUri  = imageUri,
                        timestamp         = System.currentTimeMillis(),
                    )
                    viewModel.updateNote(updated)
                    noteToEdit = updated
                    showEditor = false
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(40.dp),
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape    = RoundedCornerShape(40.dp),
                color    = MaterialTheme.colorScheme.primaryContainer.copy(0.25f),
                border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(0.1f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isSearching) Icons.Rounded.SearchOff else Icons.Rounded.NoteAdd,
                        null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(0.6f)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                if (isSearching) "No matches found" else if (selectedCategory != "All") "No $selectedCategory yet" else "Your thoughts await",
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isSearching) "Try a different search term" else "Tap the '+' button to start writing your masterpiece.",
                style      = MaterialTheme.typography.bodyMedium,
                color      = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Note card (staggered grid item)
// ─────────────────────────────────────────────────────────────

@Composable
fun NoteCard(
    note                  : Note,
    isSelected            : Boolean = false,
    isPlaying             : Boolean,
    currentTrackThumbnail : String?,
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

    Surface(
        modifier       = modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick, onLongClick = onLongClick),
        shape          = RoundedCornerShape(28.dp),
        color          = noteColor,
        shadowElevation = if (performanceMode) 0.dp else 4.dp,
        border         = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                         else BorderStroke(1.dp, onColor.copy(alpha = 0.08f)),
    ) {
        Box {
            Column(Modifier.padding(20.dp)) {
                // Title + pin
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        note.title.ifEmpty { "Untitled" },
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color      = onColor,
                        modifier   = Modifier.weight(1f),
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                    if (note.isPinned) {
                        Surface(
                            color = onColor.copy(0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PushPin, null,
                                    modifier = Modifier.size(12.dp).graphicsLayer { rotationZ = -15f },
                                    tint     = onColor,
                                )
                            }
                        }
                    }
                }

                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        note.content,
                        style     = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = noteFontFamily(note.fontStyle),
                            fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle  = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                            lineHeight = 20.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color     = onColor.copy(0.75f),
                        maxLines  = 6,
                        overflow  = TextOverflow.Ellipsis,
                    )
                }

                // Previews / Attachments
                if (note.attachedAudioUri != null || note.attachedPdfUri != null || note.attachedImageUri != null) {
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Image Preview
                        note.attachedImageUri?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // PDF Preview
                        note.attachedPdfUri?.let { uri ->
                            PdfPreview(
                                uri = uri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }

                        // Music Pill
                        note.attachedAudioUri?.let { uri ->
                            MusicPill(
                                title = note.attachedAudioName ?: "Attached Audio",
                                isPlaying = isPlaying,
                                thumbnail = if (isPlaying) currentTrackThumbnail else null,
                                containerColor = onColor.copy(0.08f),
                                contentColor = onColor,
                                onClick = onPlayAudio,
                                compact = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Footer
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(note.timestamp)).uppercase(),
                        style      = MaterialTheme.typography.labelSmall,
                        color      = onColor.copy(0.4f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(onClick = onTogglePin, Modifier.size(24.dp)) {
                            Icon(
                                Icons.Rounded.PushPin, null,
                                modifier = Modifier.size(16.dp),
                                tint     = onColor.copy(if (note.isPinned) 1f else 0.2f),
                            )
                        }
                        IconButton(onClick = onDelete, Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = onColor.copy(0.2f))
                        }
                    }
                }
            }

            if (isSelected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.primary,
                    border   = BorderStroke(2.dp, Color.White),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        modifier = Modifier.size(18.dp).padding(2.dp),
                        tint     = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Note viewer bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteViewerSheet(
    note                  : Note,
    viewModel             : NotepadViewModel,
    isPlaying             : Boolean,
    currentTrackThumbnail : String?,
    musicViewModel        : MusicPlayerViewModel,
    onDismiss             : () -> Unit,
    onEdit                : () -> Unit,
    onPlayAudio           : (String) -> Unit,
    onViewPdf             : (String) -> Unit,
) {
    val vibration    = LocalVibrationManager.current
    val noteColor    = Color(note.color)
    val onColor      = noteContentColor(noteColor)
    val aiSummary    by viewModel.aiSummary.collectAsState()
    val isSummarizing by viewModel.isAiSummarizing.collectAsState()
    var showSummary  by remember { mutableStateOf(false) }

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
                Box(
                    Modifier
                        .size(36.dp, 4.dp)
                        .background(onColor.copy(0.25f), CircleShape)
                )
            }
        },
        modifier = Modifier.fillMaxHeight(0.92f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // ── Toolbar ─────────────────────────────────────────────────
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        SimpleDateFormat("MMMM dd, yyyy · HH:mm", Locale.getDefault()).format(Date(note.timestamp)).uppercase(),
                        style      = MaterialTheme.typography.labelSmall,
                        color      = onColor.copy(0.4f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // AI Summarize
                        ViewerActionButton(
                            icon    = Icons.Rounded.AutoAwesome,
                            tint    = onColor,
                            bgAlpha = 0.15f,
                            bgColor = onColor,
                            onClick = {
                                vibration?.vibrateTick()
                                if (!isSummarizing) viewModel.summarizeNote(note)
                                showSummary = true
                            },
                        )
                        // Edit
                        ViewerActionButton(
                            icon    = Icons.Rounded.Edit,
                            tint    = onColor,
                            bgAlpha = 0.15f,
                            bgColor = onColor,
                            onClick = { vibration?.vibrateClick(); onEdit() },
                        )
                        // More options
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            ViewerActionButton(
                                icon    = Icons.Rounded.MoreVert,
                                tint    = onColor,
                                bgAlpha = 0.1f,
                                bgColor = onColor,
                                onClick = { vibration?.vibrateTick(); showMoreMenu = true },
                            )
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                containerColor = noteColor.copy(0.95f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("PIN NOTE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(18.dp)) },
                                    onClick = { viewModel.togglePin(note); showMoreMenu = false },
                                    colors = MenuDefaults.itemColors(textColor = onColor, leadingIconColor = onColor)
                                )
                                DropdownMenuItem(
                                    text = { Text("DELETE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp)) },
                                    onClick = { viewModel.deleteNote(note); onDismiss(); showMoreMenu = false },
                                    colors = MenuDefaults.itemColors(textColor = onColor, leadingIconColor = onColor)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Stats + utility bar ──────────────────────────────────
                val wordCount = remember(note.content) {
                    note.content.split(Regex("""\s+""")).count { it.isNotBlank() }
                }
                val charCount  = note.content.length
                val readMin    = remember(wordCount) { maxOf(1, wordCount / 200) }
                val context    = LocalContext.current

                Surface(
                    color  = onColor.copy(0.08f),
                    shape  = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, onColor.copy(0.06f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // Word count
                        NoteStatChip("$wordCount words", Icons.Rounded.TextFields, onColor)
                        NoteStatChip("$charCount chars", Icons.Rounded.Tag, onColor)
                        NoteStatChip("~$readMin min", Icons.Rounded.Schedule, onColor)

                        Spacer(Modifier.weight(1f))

                        // Actions
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    vibration?.vibrateClick()
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("Note", note.content)
                                    )
                                },
                                color  = onColor.copy(0.12f),
                                shape  = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, null, Modifier.padding(8.dp).size(16.dp), tint = onColor)
                            }
                            Surface(
                                onClick = {
                                    vibration?.vibrateClick()
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, note.title.ifEmpty { "Note" })
                                        putExtra(android.content.Intent.EXTRA_TEXT,
                                            "${note.title.ifEmpty { "Note" }}\n\n${note.content}")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share note"))
                                },
                                color  = onColor.copy(0.12f),
                                shape  = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.Share, null, Modifier.padding(8.dp).size(16.dp), tint = onColor)
                            }
                        }
                    }
                }
            }

            // ── Content ─────────────────────────────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .fadingEdges(top = 16.dp, bottom = 40.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp),
            ) {
                Text(
                    note.title.ifEmpty { "Untitled" },
                    style      = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color      = onColor,
                    modifier   = Modifier.padding(bottom = 18.dp),
                )

                // Attachments row
                if (note.attachedAudioUri != null || note.attachedPdfUri != null || note.attachedImageUri != null) {
                    Row(
                        Modifier
                            .padding(bottom = 24.dp)
                            .fadingEdge(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent)), 20.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        note.attachedAudioUri?.let { uri ->
                            MusicPill(
                                title          = note.attachedAudioName ?: "Audio",
                                isPlaying      = isPlaying,
                                thumbnail      = currentTrackThumbnail,
                                containerColor = onColor.copy(0.08f),
                                contentColor   = onColor,
                                onClick        = { vibration?.vibrateClick(); onPlayAudio(uri) },
                                compact        = false,
                                musicViewModel = musicViewModel,
                                modifier       = Modifier.widthIn(max = 280.dp),
                            )
                        }
                        note.attachedPdfUri?.let { uri ->
                            Surface(
                                onClick = { vibration?.vibrateClick(); onViewPdf(uri) },
                                color   = onColor.copy(0.08f),
                                shape   = RoundedCornerShape(24.dp),
                                border  = BorderStroke(1.dp, onColor.copy(0.12f)),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Rounded.Description, null, Modifier.size(20.dp), tint = onColor)
                                    @Suppress("DEPRECATION")
                                    Text("VIEW PDF", style = MaterialTheme.typography.labelLarge, color = onColor, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                note.attachedImageUri?.let { uri ->
                    AsyncImage(
                        model    = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                }

                note.attachedPdfUri?.let { uri ->
                    PdfPreview(
                        uri      = uri,
                        modifier = Modifier.height(160.dp).padding(bottom = 20.dp),
                    )
                }

                // Note content
                val segments = remember(note.content) { parseMarkdownToSegments(note.content) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    segments.forEach { seg ->
                        MarkdownSegment(
                            seg = seg,
                            baseFontSize = note.fontSize.sp,
                            textColor = onColor.copy(0.88f),
                            onLinkClick = { /* Handle link click if needed */ }
                        )
                    }
                }
            }
        }
    }

    // ── AI Summary sheet ─────────────────────────────────────────────────
    if (showSummary) {
        AiSummarySheet(
            summary      = aiSummary,
            isLoading    = isSummarizing,
            accentColor  = onColor,
            bgColor      = noteColor,
            onDismiss    = { showSummary = false; viewModel.clearAiSummary() },
        )
    }
}


// ─────────────────────────────────────────────────────────────
//  AI Summary bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSummarySheet(
    summary    : String?,
    isLoading  : Boolean,
    accentColor: Color,
    bgColor    : Color,
    onDismiss  : () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = bgColor.copy(alpha = 0.97f),
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp, 3.dp).background(accentColor.copy(0.2f), CircleShape))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier              = Modifier.padding(bottom = 16.dp),
            ) {
                // Animated AI spark icon
                val inf = rememberInfiniteTransition(label = "ai_spin")
                val spinRaw by inf.animateFloat(
                    0f, 360f,
                    infiniteRepeatable(tween(3000, easing = LinearEasing)),
                    label = "spin",
                )
                val spin = if (isLoading) spinRaw else 0f
                Icon(
                    Icons.Rounded.AutoAwesome, null,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = spin },
                    tint     = accentColor,
                )
                Text(
                    "AI SUMMARY",
                    style         = MaterialTheme.typography.labelMedium,
                    fontWeight    = FontWeight.Black,
                    color         = accentColor,
                    letterSpacing = 1.5.sp,
                )
            }

            if (isLoading) {
                // Three-dot loading indicator
                val loadInf = rememberInfiniteTransition(label = "load")
                val d1 by loadInf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 0),   RepeatMode.Reverse), "d1")
                val d2 by loadInf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 160), RepeatMode.Reverse), "d2")
                val d3 by loadInf.animateFloat(0.2f, 1f, infiniteRepeatable(tween(500, delayMillis = 320), RepeatMode.Reverse), "d3")

                Row(
                    Modifier.padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    listOf(d1, d2, d3).forEach { alpha ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .alpha(alpha)
                                .background(accentColor, CircleShape)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Analyzing note…",
                        style  = MaterialTheme.typography.bodyMedium,
                        color  = accentColor.copy(0.55f),
                    )
                }
            } else if (summary != null) {
                // Blinking cursor while "typing" effect — just show the full text
                Surface(
                    color  = accentColor.copy(0.08f),
                    shape  = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, accentColor.copy(0.12f)),
                ) {
                    Text(
                        summary,
                        modifier  = Modifier.padding(16.dp),
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = accentColor.copy(0.88f),
                        lineHeight = 22.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Generated by Groq · llama-3.3-70b-versatile",
                    style  = MaterialTheme.typography.labelSmall,
                    color  = accentColor.copy(0.3f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Note editor — full-screen dialog
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorDialog(
    note      : Note?,
    viewModel : NotepadViewModel,
    onDismiss : () -> Unit,
    onSave    : (String, String, Int, String, Float, Boolean, Boolean, String?, String?, String?, String?) -> Unit,
) {
    val vibration = LocalVibrationManager.current

    var title             by remember { mutableStateOf(note?.title    ?: "") }
    var content           by remember { mutableStateOf(note?.content  ?: "") }
    val isFocusMode by viewModel.isFocusMode.collectAsState()
    var selectedColor     by remember { mutableIntStateOf(note?.color ?: 0xFFFFF9C4.toInt()) }
    var fontStyle         by remember { mutableStateOf(note?.fontStyle ?: "DEFAULT") }
    var fontSize          by remember { mutableFloatStateOf(note?.fontSize ?: 17f) }
    var isBold            by remember { mutableStateOf(note?.isBold   ?: false) }
    var isItalic          by remember { mutableStateOf(note?.isItalic ?: false) }
    var attachedPdfUri    by remember { mutableStateOf(note?.attachedPdfUri) }
    var attachedAudioUri  by remember { mutableStateOf(note?.attachedAudioUri) }
    var attachedAudioName by remember { mutableStateOf(note?.attachedAudioName) }
    var attachedImageUri  by remember { mutableStateOf(note?.attachedImageUri) }

    val availableTracks by viewModel.availableTracks.collectAsStateWithLifecycle()
    val availablePdfs   by viewModel.availablePdfs.collectAsStateWithLifecycle()
    val aiStyle         by viewModel.aiStyle.collectAsState()
    val isAiStyling     by viewModel.isAiStyling.collectAsState()

    var showAttachMenu      by remember { mutableStateOf(false) }
    var showTrackPicker     by remember { mutableStateOf(false) }
    var showPdfPicker       by remember { mutableStateOf(false) }
    var showCustomColor     by remember { mutableStateOf(false) }
    var showAiStylePreview  by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { attachedImageUri = it.toString() }
    }

    val bgColor   = Color(selectedColor)
    val onBgColor = noteContentColor(bgColor)

    val noteColors = listOf(
        0xFFFFF9C4, 0xFFFFCCBC, 0xFFC8E6C9, 0xFFB3E5FC, 0xFFE1BEE7,
        0xFFF5F5F5, 0xFFD7CCC8, 0xFFCFD8DC, 0xFFFFE0B2, 0xFF263238,
    ).map { it.toInt() }

    // When AI style arrives, show preview
    LaunchedEffect(aiStyle) {
        if (aiStyle != null) showAiStylePreview = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = bgColor) {
            Column(Modifier.statusBarsPadding()) {
                // ── Top bar ──────────────────────────────────────────────
                AnimatedVisibility(
                    visible = !isFocusMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                if (note == null) "NEW NOTE" else "EDIT NOTE",
                                color         = onBgColor,
                                fontWeight    = FontWeight.Black,
                                style         = MaterialTheme.typography.labelMedium,
                                letterSpacing = 2.sp,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { vibration?.vibrateClick(); onDismiss() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = onBgColor)
                            }
                        },
                        actions = {
                            // Focus Mode Toggle
                            Surface(
                                onClick = { vibration?.vibrateClick(); viewModel.toggleFocusMode() },
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = onBgColor.copy(0.08f),
                                border = BorderStroke(1.dp, onBgColor.copy(0.05f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.FilterCenterFocus, "Focus Mode", Modifier.size(20.dp), tint = onBgColor)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            // AI "Choose the Look" button
                            Surface(
                                onClick  = {
                                    vibration?.vibrateClick()
                                    if (content.isNotBlank() || title.isNotBlank()) {
                                        val tempNote = (note ?: Note(title = title, content = content, color = selectedColor)).copy(title = title, content = content)
                                        viewModel.suggestStyleForNote(tempNote)
                                    }
                                },
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = onBgColor.copy(0.08f),
                                border = BorderStroke(1.dp, onBgColor.copy(0.05f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isAiStyling) {
                                        CircularProgressIndicator(
                                            modifier     = Modifier.size(18.dp),
                                            strokeWidth  = 2.5.dp,
                                            color        = onBgColor,
                                        )
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, "AI Look", Modifier.size(20.dp), tint = onBgColor)
                                    }
                                }
                            }
                            // Save
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                onClick  = {
                                    vibration?.vibrateClick()
                                    onSave(title, content, selectedColor, fontStyle, fontSize, isBold, isItalic, attachedPdfUri, attachedAudioUri, attachedAudioName, attachedImageUri)
                                },
                                modifier = Modifier.padding(end = 16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = onBgColor,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    "SAVE",
                                    modifier      = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                                    fontWeight    = FontWeight.Black,
                                    color         = bgColor,
                                    style         = MaterialTheme.typography.labelLarge,
                                    letterSpacing = 1.sp,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                ) {
                    // ── AI style preview banner ──────────────────────────────
                    AnimatedVisibility(
                        visible = showAiStylePreview && aiStyle != null,
                        enter   = expandVertically() + fadeIn(),
                        exit    = shrinkVertically() + fadeOut(),
                    ) {
                        aiStyle?.let { style ->
                            AiStyleBanner(
                                style    = style,
                                onColor  = onBgColor,
                                onAccept = {
                                    vibration?.vibrateClick()
                                    try {
                                        selectedColor = android.graphics.Color.parseColor(style.colorHex)
                                    } catch (_: Exception) {}
                                    fontSize = style.fontSize
                                    isBold   = style.isBold
                                    isItalic = style.isItalic
                                    showAiStylePreview = false
                                    viewModel.clearAiStyle()
                                },
                                onDismiss = {
                                    showAiStylePreview = false
                                    viewModel.clearAiStyle()
                                },
                            )
                        }
                    }

                    // ── Title field ─────────────────────────────────────────
                    AnimatedVisibility(visible = !isFocusMode) {
                        TextField(
                            value         = title,
                            onValueChange = { title = it },
                            placeholder   = {
                                Text(
                                    "Note Title",
                                    style      = MaterialTheme.typography.headlineLarge,
                                    color      = onBgColor.copy(0.2f),
                                    fontWeight = FontWeight.Black,
                                )
                            },
                            modifier  = Modifier.fillMaxWidth().padding(top = 16.dp),
                            textStyle = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color      = onBgColor,
                            ),
                            colors    = transparentTextFieldColors(onBgColor),
                            singleLine = true,
                        )
                    }

                    // ── Attachment row ────────────────────────────────────
                    AnimatedVisibility(visible = !isFocusMode && (attachedAudioUri != null || attachedPdfUri != null || attachedImageUri != null)) {
                        Row(
                            Modifier
                                .padding(vertical = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            attachedAudioUri?.let {
                                AttachmentChip(
                                    label     = attachedAudioName ?: "Audio",
                                    icon      = Icons.Rounded.MusicNote,
                                    color     = onBgColor,
                                    onDelete  = { vibration?.vibrateTick(); attachedAudioUri = null; attachedAudioName = null },
                                )
                            }
                            attachedPdfUri?.let {
                                AttachmentChip(
                                    label    = "PDF",
                                    icon     = Icons.Rounded.Description,
                                    color    = onBgColor,
                                    onDelete = { vibration?.vibrateTick(); attachedPdfUri = null },
                                )
                            }
                            attachedImageUri?.let {
                                AttachmentChip(
                                    label    = "Image",
                                    icon     = Icons.Rounded.Image,
                                    color    = onBgColor,
                                    onDelete = { vibration?.vibrateTick(); attachedImageUri = null },
                                )
                            }
                        }
                    }

                    // ── Formatting toolbar — modern floating feel ────────────────────
                    Surface(
                        modifier = Modifier.padding(vertical = 12.dp),
                        shape    = RoundedCornerShape(24.dp),
                        color    = onBgColor.copy(0.08f),
                        border   = BorderStroke(1.dp, onBgColor.copy(0.06f)),
                    ) {
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FormatButton(Icons.Rounded.FormatBold, isBold, onBgColor) {
                                vibration?.vibrateTick(); isBold = !isBold
                            }
                            FormatButton(Icons.Rounded.FormatItalic, isItalic, onBgColor) {
                                vibration?.vibrateTick(); isItalic = !isItalic
                            }
                            
                            ToolbarDivider(onBgColor)
                            
                            // Quick Attach Actions
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QuickAttachButton(Icons.Rounded.MusicNote, onBgColor, "Audio") {
                                    vibration?.vibrateClick(); showTrackPicker = true
                                }
                                QuickAttachButton(Icons.Rounded.Description, onBgColor, "PDF") {
                                    vibration?.vibrateClick(); showPdfPicker = true
                                }
                                QuickAttachButton(Icons.Rounded.Image, onBgColor, "Image") {
                                    vibration?.vibrateClick(); imagePicker.launch("image/*")
                                }
                            }
                            
                            ToolbarDivider(onBgColor)
                            
                            FontStyleSelector(fontStyle, onBgColor, vibration) { fontStyle = it }
                            
                            ToolbarDivider(onBgColor)
                            
                            FontSizeControl(fontSize, onBgColor) { fontSize = it }

                            if (isFocusMode) {
                                ToolbarDivider(onBgColor)
                                IconButton(onClick = { vibration?.vibrateClick(); viewModel.toggleFocusMode() }) {
                                    Icon(Icons.Rounded.Close, null, tint = onBgColor)
                                }
                            }
                        }
                    }


                    // ── Colour picker ────────────────────────────────────────
                    AnimatedVisibility(visible = !isFocusMode) {
                        Row(
                            Modifier
                                .padding(vertical = 12.dp)
                                .fadingEdge(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent)), 24.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Surface(
                                onClick  = { vibration?.vibrateClick(); showCustomColor = true },
                                modifier = Modifier.size(42.dp),
                                shape    = RoundedCornerShape(14.dp),
                                color    = onBgColor.copy(0.1f),
                                border   = BorderStroke(1.dp, onBgColor.copy(0.08f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Palette, null, Modifier.size(20.dp), tint = onBgColor)
                                }
                            }
                            noteColors.forEach { argb ->
                                val c   = Color(argb)
                                val sel = selectedColor == argb
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(c)
                                        .border(
                                            if (sel) 2.dp else 1.dp,
                                            if (sel) onBgColor else onBgColor.copy(0.15f),
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable { vibration?.vibrateTick(); selectedColor = argb },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = noteContentColor(c))
                                }
                            }
                        }
                    }

                    // ── Content field ────────────────────────────────────────
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        TextField(
                            value         = content,
                            onValueChange = { content = it },
                            placeholder   = {
                                Text(
                                    "Start typing your masterpiece…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = onBgColor.copy(0.2f),
                                )
                            },
                            modifier  = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = noteFontFamily(fontStyle),
                                fontSize   = fontSize.sp,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle  = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                color      = onBgColor,
                                lineHeight = (fontSize * 1.65f).sp,
                            ),
                            colors = transparentTextFieldColors(onBgColor),
                        )

                        // ── Disable Focus Button ────────────────────────────────
                        if (isFocusMode) {
                            Surface(
                                onClick = { vibration?.vibrateClick(); viewModel.toggleFocusMode() },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = onBgColor.copy(0.15f),
                                border = BorderStroke(1.dp, onBgColor.copy(0.1f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp), tint = onBgColor)
                                    Text("DISABLE FOCUS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = onBgColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // ── Bottom sheets & dialogs ────────────────────────────────────────

        if (showAttachMenu) {
            AttachmentMenuSheet(
                onDismiss     = { showAttachMenu = false },
                onPickAudio   = { showTrackPicker = true; showAttachMenu = false },
                onPickPdf     = { showPdfPicker   = true; showAttachMenu = false },
                onPickImage   = { imagePicker.launch("image/*"); showAttachMenu = false },
            )
        }

        if (showTrackPicker) {
            AttachmentPickerDialog(
                title    = "SELECT AUDIO",
                items    = availableTracks.map { it.title to it.uri },
                onDismiss = { showTrackPicker = false },
                onSelect  = { name, uri -> vibration?.vibrateClick(); attachedAudioUri = uri; attachedAudioName = name; showTrackPicker = false },
            )
        }

        if (showPdfPicker) {
            AttachmentPickerDialog(
                title    = "SELECT PDF",
                items    = availablePdfs.map { it.name to it.uri.toString() },
                onDismiss = { showPdfPicker = false },
                onSelect  = { _, uri -> vibration?.vibrateClick(); attachedPdfUri = uri; showPdfPicker = false },
            )
        }

        if (showCustomColor) {
            CustomColorDialog(
                initialColor   = selectedColor,
                onDismiss      = { showCustomColor = false },
                onColorSelected = { selectedColor = it; showCustomColor = false },
            )
        }
        }





// ─────────────────────────────────────────────────────────────
//  AI style preview banner
// ─────────────────────────────────────────────────────────────

@Composable
private fun AiStyleBanner(
    style    : AiNoteStyle,
    onColor  : Color,
    onAccept : () -> Unit,
    onDismiss: () -> Unit,
) {
    val previewColor = remember(style.colorHex) {
        try { Color(android.graphics.Color.parseColor(style.colorHex)) }
        catch (_: Exception) { Color(0xFFFFF9C4) }
    }

    Surface(
        color    = onColor.copy(0.1f),
        shape    = RoundedCornerShape(18.dp),
        border   = BorderStroke(1.dp, onColor.copy(0.12f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(15.dp), tint = onColor)
                Text(
                    "AI STYLE SUGGESTION",
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Black,
                    color         = onColor,
                    letterSpacing = 1.sp,
                    modifier      = Modifier.weight(1f),
                )
                // Colour preview swatch
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(1.dp, onColor.copy(0.2f), CircleShape)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                style.reasoning,
                style  = MaterialTheme.typography.bodySmall,
                color  = onColor.copy(0.7f),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onAccept,
                    color   = onColor.copy(0.18f),
                    shape   = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        "APPLY",
                        modifier      = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style         = MaterialTheme.typography.labelSmall,
                        fontWeight    = FontWeight.Black,
                        color         = onColor,
                        letterSpacing = 1.sp,
                    )
                }
                Surface(
                    onClick = onDismiss,
                    color   = Color.Transparent,
                    shape   = RoundedCornerShape(10.dp),
                    border  = BorderStroke(1.dp, onColor.copy(0.15f)),
                ) {
                    Text(
                        "DISMISS",
                        modifier   = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = onColor.copy(0.6f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Formatting toolbar helpers
// ─────────────────────────────────────────────────────────────

@Composable
private fun FormatButton(icon: ImageVector, active: Boolean, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = if (active) tint.copy(0.15f) else Color.Transparent,
        shape   = CircleShape,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, null,
                Modifier.size(18.dp),
                tint = if (active) tint else tint.copy(0.5f),
            )
        }
    }
}

@Composable
private fun ToolbarDivider(color: Color) {
    Box(Modifier.height(20.dp).width(1.dp).background(color.copy(0.15f)))
}

@Composable
private fun FontStyleSelector(
    current  : String,
    tint     : Color,
    vibration: com.frerox.toolz.util.VibrationManager?,
    onSelect : (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { vibration?.vibrateTick(); expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, color = tint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(17.dp), tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(18.dp)) {
            listOf("DEFAULT", "SERIF", "MONOSPACE", "CASUAL").forEach { f ->
                DropdownMenuItem(
                    text    = { Text(f, fontWeight = FontWeight.Bold) },
                    onClick = { vibration?.vibrateTick(); onSelect(f); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun FontSizeControl(size: Float, tint: Color, onChange: (Float) -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.width(180.dp),
    ) {
        Icon(Icons.Rounded.TextFields, null, Modifier.size(16.dp), tint = tint)
        SquigglySlider(
            value         = size,
            onValueChange = onChange,
            valueRange    = 12f..28f,
            modifier      = Modifier.weight(1f),
            activeColor   = tint,
            inactiveColor = tint.copy(0.2f),
        )
        Text("${size.toInt()}", color = tint, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.width(20.dp))
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
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 40.dp).navigationBarsPadding()) {
            @Suppress("DEPRECATION")
            Text("ATTACH", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 16.dp))
            AttachmentTypeItem("Music / Audio", "Attach a track or voice memo", Icons.Rounded.MusicNote,     Color(0xFFFF4081)) { onPickAudio() }
            Spacer(Modifier.height(12.dp))
            AttachmentTypeItem("PDF Document",  "Attach a document reference", Icons.Rounded.Description, Color(0xFF2196F3)) { onPickPdf()   }
            Spacer(Modifier.height(12.dp))
            AttachmentTypeItem("Image",         "Attach a photo or graphic", Icons.Rounded.Image,       Color(0xFF4CAF50)) { onPickImage() }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  MusicPill — composable violation FIXED
// ─────────────────────────────────────────────────────────────

@Composable
fun MusicPill(
    title          : String,
    isPlaying      : Boolean,
    thumbnail      : String?,
    containerColor : Color,
    contentColor   : Color,
    onClick        : () -> Unit,
    modifier       : Modifier = Modifier,
    compact        : Boolean = false,
    musicViewModel : MusicPlayerViewModel = hiltViewModel(),
) {
    val performanceMode = LocalPerformanceMode.current
    val vibration       = LocalVibrationManager.current

    // ── ALWAYS declare animations unconditionally ──────────────────────────
    val inf = rememberInfiniteTransition(label = "pill")

    val rotationRaw by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(
            tween(if (isPlaying) 8_000 else 20_000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "thumbRot",
    )
    // Use a fixed value in performance mode — animation still runs but result is discarded
    val rotation = if (performanceMode) 0f else rotationRaw

    // Bar heights — always declared, but only rendered when needed
    val bar1Raw by inf.animateFloat(4f, 16f, infiniteRepeatable(tween(400),              RepeatMode.Reverse), "b1")
    val bar2Raw by inf.animateFloat(4f, 16f, infiniteRepeatable(tween(600),              RepeatMode.Reverse), "b2")
    val bar3Raw by inf.animateFloat(4f, 16f, infiniteRepeatable(tween(800, delayMillis = 100), RepeatMode.Reverse), "b3")
    val showBars = isPlaying && !performanceMode && compact
    val barHeights = if (showBars) listOf(bar1Raw, bar2Raw, bar3Raw) else listOf(8f, 8f, 8f)

    Surface(
        modifier = modifier.bouncyClick { vibration?.vibrateClick(); onClick() },
        color    = containerColor,
        shape    = RoundedCornerShape(if (compact) 22.dp else 28.dp),
        border   = if (isPlaying)
            BorderStroke(1.5.dp, contentColor.copy(0.35f))
        else BorderStroke(1.dp,  contentColor.copy(0.12f)),
    ) {
        Row(
            Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical   = if (compact) 8.dp  else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Vinyl disc
            Box(
                modifier         = Modifier
                    .size(if (compact) 32.dp else 46.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(0.1f))
                    .rotate(rotation),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    AsyncImage(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.MusicNote, null, Modifier.size(if (compact) 16.dp else 22.dp), tint = contentColor)
                }
                // Centre hole
                Surface(
                    modifier = Modifier.size(if (compact) 7.dp else 10.dp),
                    shape    = CircleShape,
                    color    = containerColor,
                    border   = BorderStroke(1.dp, contentColor.copy(0.15f)),
                ) {}
            }

            Spacer(Modifier.width(if (compact) 10.dp else 14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style      = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    color      = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Text(
                    if (isPlaying) "Playing now" else "Audio attached",
                    style  = MaterialTheme.typography.labelSmall,
                    color  = contentColor.copy(0.5f),
                )
            }

            // Play/pause button or animated bars
            if (compact) {
                IconButton(
                    onClick = { vibration?.vibrateClick(); musicViewModel.togglePlayPause() },
                    modifier = Modifier.size(32.dp).background(contentColor.copy(0.1f), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        Modifier.size(16.dp),
                        tint = contentColor
                    )
                }
                if (isPlaying) {
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier.height(14.dp).padding(end = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                        verticalAlignment     = Alignment.Bottom,
                    ) {
                        barHeights.forEach { h ->
                            Box(
                                Modifier
                                    .width(2.5.dp)
                                    .height(h.dp)
                                    .background(contentColor, RoundedCornerShape(1.5.dp))
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = { vibration?.vibrateClick(); musicViewModel.togglePlayPause() },
                    modifier = Modifier.size(38.dp).background(contentColor.copy(0.12f), CircleShape),
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null, Modifier.size(20.dp), tint = contentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAttachButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(0.06f),
        border = BorderStroke(1.dp, color.copy(0.08f)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = color.copy(0.8f))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  PDF preview
// ─────────────────────────────────────────────────────────────

@Composable
fun PdfPreview(uri: String, modifier: Modifier = Modifier) {
    val context      = LocalContext.current
    var bitmap       by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r") ?: return@LaunchedEffect
            pfd.use {
                val renderer = PdfRenderer(it)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bmp  = Bitmap.createBitmap(page.width / 2, page.height / 2, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = bmp
                    page.close()
                }
                renderer.close()
            }
        } catch (_: Exception) {}
    }

    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        color    = Color.White,
    ) {
        if (bitmap != null) {
            Box {
                Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    color    = Color.White.copy(0.85f),
                    shape    = RoundedCornerShape(8.dp),
                ) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(12.dp), tint = Color(0xFFD32F2F))
                        Text("PDF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(90.dp).background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Description, null, Modifier.size(32.dp), tint = Color.Gray.copy(0.4f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Shared small components
// ─────────────────────────────────────────────────────────────

@Composable
fun AttachmentChip(label: String, icon: ImageVector, color: Color, onDelete: () -> Unit) {
    Surface(
        color  = color.copy(0.14f),
        shape  = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(0.2f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Text(label.take(18), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete, Modifier.size(20.dp)) {
                Icon(Icons.Rounded.Close, null, Modifier.size(12.dp), tint = color.copy(0.55f))
            }
        }
    }
}

@Composable
fun AttachmentTypeItem(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape    = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.2f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).background(color.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = color)
            }
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.55f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerDialog(
    title     : String,
    items     : List<Pair<String, String>>,
    onDismiss : () -> Unit,
    onSelect  : (String, String) -> Unit,
    musicViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val musicState by musicViewModel.uiState.collectAsState()
    val vibration  = LocalVibrationManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            shape    = RoundedCornerShape(28.dp),
            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(24.dp)) {
                @Suppress("DEPRECATION")
                Text(
                    title,
                    style         = MaterialTheme.typography.labelSmall,
                    fontWeight    = FontWeight.Black,
                    color         = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                    modifier      = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier            = Modifier.weight(1f).fadingEdges(top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items) { (name, uri) ->
                        val isMusic = title.contains("AUDIO", ignoreCase = true)
                        val track = if (isMusic) musicState.tracks.find { it.uri == uri } else null
                        val isPlaying = isMusic && musicState.isPlaying && musicState.currentTrack?.uri == uri

                        val inf = rememberInfiniteTransition(label = "rot")
                        val rotationRaw by inf.animateFloat(
                            0f, 360f,
                            infiniteRepeatable(tween(10000, easing = LinearEasing)),
                            label = "pickerRot"
                        )
                        val rotation = if (isPlaying) rotationRaw else 0f

                        Surface(
                            onClick = { vibration?.vibrateClick(); onSelect(name, uri) },
                            shape   = RoundedCornerShape(20.dp),
                            color   = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border  = BorderStroke(1.dp, if (isPlaying) MaterialTheme.colorScheme.primary.copy(0.3f) else Color.Transparent)
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Thumbnail / Icon
                                Surface(
                                    modifier = Modifier.size(46.dp).graphicsLayer { rotationZ = rotation },
                                    shape    = CircleShape,
                                    color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    if (track?.thumbnailUri != null) {
                                        AsyncImage(
                                            model = track.thumbnailUri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                if (isMusic) Icons.Rounded.MusicNote else Icons.Rounded.Description,
                                                null,
                                                Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(0.6f)
                                            )
                                        }
                                    }
                                }

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track?.title ?: name,
                                        style      = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Black,
                                        maxLines   = 1,
                                        overflow   = TextOverflow.Ellipsis
                                    )
                                    if (isMusic) {
                                        Text(
                                            track?.artist ?: "Unknown Artist",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (isPlaying) {
                                    Icon(
                                        Icons.Rounded.VolumeUp,
                                        null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick  = { vibration?.vibrateClick(); onDismiss() },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    @Suppress("DEPRECATION")
                    Text("CANCEL", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun CustomColorDialog(
    initialColor   : Int,
    onDismiss      : () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    var hex       by remember { mutableStateOf("%06X".format(0xFFFFFF and initialColor)) }
    val vibration = LocalVibrationManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(28.dp),
        title = { Text("CUSTOM COLOUR", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelMedium) },
        text  = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                val preview = remember(hex) {
                    try { Color(android.graphics.Color.parseColor("#$hex")) } catch (_: Exception) { Color.Gray }
                }
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape    = RoundedCornerShape(22.dp),
                    color    = preview,
                    border   = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {}
                OutlinedTextField(
                    value         = hex,
                    onValueChange = { if (it.length <= 6) hex = it.uppercase().filter { c -> c in "0123456789ABCDEF" } },
                    label         = { Text("HEX CODE") },
                    prefix        = { Text("#") },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(16.dp),
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        try { onColorSelected(android.graphics.Color.parseColor("#$hex")) } catch (_: Exception) {}
                    }),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { vibration?.vibrateClick(); try { onColorSelected(android.graphics.Color.parseColor("#$hex")) } catch (_: Exception) {} },
                shape   = RoundedCornerShape(14.dp),
            ) { Text("APPLY", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = { vibration?.vibrateClick(); onDismiss() }) { Text("CANCEL") }
        },
    )
}

// ─────────────────────────────────────────────────────────────
//  Utilities
// ─────────────────────────────────────────────────────────────

private fun noteFontFamily(style: String): FontFamily = when (style) {
    "SERIF"     -> FontFamily.Serif
    "MONOSPACE" -> FontFamily.Monospace
    "CASUAL"    -> FontFamily.Cursive
    else        -> FontFamily.Default
}

@Composable
private fun transparentTextFieldColors(cursorColor: Color): TextFieldColors {
    return TextFieldDefaults.colors(
        focusedContainerColor   = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor   = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor             = cursorColor,
    )
}

// ─────────────────────────────────────────────────────────────
//  Premium helper components
// ─────────────────────────────────────────────────────────────

@Composable
fun AttachmentIndicator(icon: ImageVector, color: Color) {
    Surface(
        color = color.copy(0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = color)
        }
    }
}

@Composable
fun ViewerActionButton(icon: ImageVector, tint: Color, bgAlpha: Float, bgColor: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color   = bgColor.copy(bgAlpha),
        shape   = CircleShape,
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = tint)
        }
    }
}

@Composable
fun NoteStatChip(text: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = color.copy(0.5f))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color.copy(0.7f),
            letterSpacing = 0.5.sp
        )
    }
}

// Reference to VibrationManager for FontStyleSelector parameter type
private typealias VibrationManager = com.frerox.toolz.util.VibrationManager