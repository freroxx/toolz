package com.frerox.toolz.ui.screens.notepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.launch
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(
    viewModel: NotepadViewModel,
    onBack: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onViewPdf: (String) -> Unit,
    initialNoteId: Int? = null,
    musicViewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val musicState by musicViewModel.uiState.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val isDark = isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialNoteId, notes) {
        if (initialNoteId != null && notes.isNotEmpty()) {
            val note = notes.find { it.id == initialNoteId }
            if (note != null) {
                noteToEdit = note
                showEditor = true
            }
        }
    }
    
    val filteredNotes = notes.filter { 
        it.title.contains(searchQuery, ignoreCase = true) || 
        it.content.contains(searchQuery, ignoreCase = true) 
    }.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                CenterAlignedTopAppBar(
                    title = { 
                        Text(
                            "THOUGHT ENGINE", 
                            fontWeight = FontWeight.Black, 
                            style = MaterialTheme.typography.labelMedium, 
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        ) 
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
                
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search archived thoughts...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(32.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    noteToEdit = null
                    showEditor = true 
                },
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 8.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(28.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 12.dp),
                icon = { Icon(Icons.Rounded.Add, "Add Note", Modifier.size(28.dp)) },
                text = { Text("NEW ENTRY", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .fadingEdge(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), 20.dp)
        ) {
            if (filteredNotes.isEmpty()) {
                val isSearching = searchQuery.isNotEmpty()
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, 
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = RoundedCornerShape(44.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                if (isSearching) Icons.Rounded.SearchOff else Icons.Rounded.AutoAwesome, 
                                contentDescription = null, 
                                modifier = Modifier.padding(32.dp), 
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(32.dp))
                        Text(
                            text = if (isSearching) "NO MATCHES" else "VOID DETECTED", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (isSearching) "Adjust your parameters to find hidden insights." else "Your intellectual reservoir is empty. Deploy a new note to start indexing.", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp
                ) {
                    itemsIndexed(filteredNotes, key = { _, note -> note.id }) { _, note ->
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                        ImprovedNoteItem(
                            note = note, 
                            isDark = isDark,
                            isPlaying = musicState.isPlaying && musicState.currentTrack?.uri == note.attachedAudioUri,
                            currentTrackThumbnail = musicState.currentTrack?.thumbnailUri,
                            onClick = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                noteToEdit = note
                                showEditor = false
                            },
                            onDelete = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                viewModel.deleteNote(note)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Memory entry de-indexed",
                                        actionLabel = "RESTORE",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete()
                                    }
                                }
                            },
                            onTogglePin = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                viewModel.togglePin(note) 
                            },
                            onPlayAudio = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                note.attachedAudioUri?.let { onPlayAudio(it) } 
                            },
                            onViewPdf = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                note.attachedPdfUri?.let { onViewPdf(it) } 
                            }
                        )
                    }
                }
            }
        }
    }

        if (showEditor && noteToEdit == null) {
            NoteEditorDialog(
                note = null,
                viewModel = viewModel,
                isAmoled = isDark,
                onDismiss = { showEditor = false },
                onSave = { title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName ->
                    viewModel.addNote(title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName)
                    showEditor = false
                }
            )
        } else if (noteToEdit != null) {
            NoteViewerDialog(
                note = noteToEdit!!,
                viewModel = viewModel,
                isAmoled = isDark,
                isPlaying = musicState.isPlaying && musicState.currentTrack?.uri == noteToEdit!!.attachedAudioUri,
                currentTrackThumbnail = musicState.currentTrack?.thumbnailUri,
                onDismiss = { noteToEdit = null },
                onEdit = {
                    showEditor = true
                },
                onPlayAudio = { onPlayAudio(it) },
                onViewPdf = { onViewPdf(it) }
            )

            if (showEditor) {
                NoteEditorDialog(
                    note = noteToEdit,
                    viewModel = viewModel,
                    isAmoled = isDark,
                    onDismiss = { showEditor = false },
                    onSave = { title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName ->
                        val updated = noteToEdit!!.copy(
                            title = title,
                            content = content,
                            color = color,
                            fontStyle = fontStyle,
                            fontSize = fontSize,
                            isBold = bold,
                            isItalic = italic,
                            attachedPdfUri = pdfUri,
                            attachedAudioUri = audioUri,
                            attachedAudioName = audioName,
                            timestamp = System.currentTimeMillis()
                        )
                        viewModel.updateNote(updated)
                        noteToEdit = updated
                        showEditor = false
                    }
                )
            }
        }
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteViewerDialog(
    note: Note,
    viewModel: NotepadViewModel,
    isAmoled: Boolean,
    isPlaying: Boolean,
    currentTrackThumbnail: String?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onViewPdf: (String) -> Unit
) {
    val noteColor = Color(note.color)
    val onNoteColor = if (isDark(noteColor)) Color.White else Color.Black

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = noteColor.copy(alpha = 0.95f),
        contentColor = onNoteColor,
        shape = RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = onNoteColor.copy(alpha = 0.3f)) },
        tonalElevation = 12.dp,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(note.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onNoteColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.togglePin(note) },
                        modifier = Modifier.size(44.dp).background(onNoteColor.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) onNoteColor else onNoteColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(44.dp).background(onNoteColor.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Edit, "Edit", tint = onNoteColor, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteNote(note)
                            onDismiss()
                        },
                        modifier = Modifier.size(44.dp).background(onNoteColor.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Delete, "Delete", tint = onNoteColor, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = note.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = onNoteColor,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                if (note.attachedAudioUri != null || note.attachedPdfUri != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 28.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (note.attachedAudioUri != null) {
                            MusicPill(
                                title = note.attachedAudioName ?: "Play Audio",
                                isPlaying = isPlaying,
                                thumbnail = currentTrackThumbnail,
                                containerColor = onNoteColor.copy(alpha = 0.15f),
                                contentColor = onNoteColor,
                                onClick = { onPlayAudio(note.attachedAudioUri) },
                                modifier = Modifier.widthIn(max = 280.dp)
                            )
                        }
                        if (note.attachedPdfUri != null) {
                            Surface(
                                onClick = { onViewPdf(note.attachedPdfUri) },
                                color = onNoteColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(28.dp),
                                border = BorderStroke(1.dp, onNoteColor.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Description, null, modifier = Modifier.size(24.dp), tint = onNoteColor)
                                    Spacer(Modifier.width(12.dp))
                                    Text("VIEW PDF", style = MaterialTheme.typography.labelLarge, color = onNoteColor, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                if (note.attachedPdfUri != null) {
                    PdfPreview(
                        uri = note.attachedPdfUri, 
                        modifier = Modifier
                            .padding(bottom = 28.dp)
                            .height(200.dp)
                            .clickable { onViewPdf(note.attachedPdfUri) }
                    )
                }

                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = when(note.fontStyle) {
                            "SERIF" -> FontFamily.Serif
                            "MONOSPACE" -> FontFamily.Monospace
                            "CASUAL" -> FontFamily.Cursive
                            else -> FontFamily.Default
                        },
                        fontSize = note.fontSize.sp,
                        fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                        lineHeight = 1.6.times(note.fontSize).sp
                    ),
                    color = onNoteColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun ImprovedNoteItem(
    note: Note, 
    isDark: Boolean,
    isPlaying: Boolean,
    currentTrackThumbnail: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onPlayAudio: () -> Unit,
    onViewPdf: () -> Unit
) {
    val noteColor = Color(note.color)
    val onNoteColor = if (isDark(noteColor)) Color.White else Color.Black

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(40.dp),
        color = noteColor.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.5.dp, onNoteColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = note.title.ifEmpty { "Untitled" }, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Black,
                    color = onNoteColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.isPinned) {
                    Icon(
                        Icons.Rounded.PushPin, 
                        contentDescription = "Pinned", 
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = -15f }, 
                        tint = onNoteColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (note.attachedPdfUri != null) {
                PdfPreview(uri = note.attachedPdfUri, modifier = Modifier.padding(bottom = 12.dp))
            }

            Text(
                text = note.content, 
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = when(note.fontStyle) {
                        "SERIF" -> FontFamily.Serif
                        "MONOSPACE" -> FontFamily.Monospace
                        "CASUAL" -> FontFamily.Cursive
                        else -> FontFamily.Default
                    },
                    fontSize = (note.fontSize * 0.85f).sp,
                    fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 1.4.times(note.fontSize * 0.85f).sp
                ),
                color = onNoteColor.copy(alpha = 0.75f),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            
            if (note.attachedAudioUri != null || note.attachedPdfUri != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (note.attachedAudioUri != null) {
                        MusicPill(
                            title = note.attachedAudioName ?: "Audio",
                            isPlaying = isPlaying,
                            thumbnail = currentTrackThumbnail,
                            containerColor = if (isPlaying) onNoteColor.copy(alpha = 0.25f) else onNoteColor.copy(alpha = 0.15f),
                            contentColor = onNoteColor,
                            onClick = onPlayAudio,
                            compact = true
                        )
                    }
                    if (note.attachedPdfUri != null) {
                        Surface(
                            onClick = onViewPdf,
                            color = onNoteColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, onNoteColor.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Description, null, modifier = Modifier.size(18.dp), tint = onNoteColor)
                                Spacer(Modifier.width(8.dp))
                                Text("PDF", style = MaterialTheme.typography.labelSmall, color = onNoteColor, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(note.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onNoteColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) onNoteColor else onNoteColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Delete, 
                            contentDescription = "Delete", 
                            tint = onNoteColor.copy(alpha = 0.25f), 
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPreview(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        try {
            val contentUri = Uri.parse(uri)
            context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val b = Bitmap.createBitmap(page.width / 2, page.height / 2, Bitmap.Config.ARGB_8888)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = b
                    page.close()
                }
                renderer.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        color = Color.White
    ) {
        if (bitmap != null) {
            Box {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.1f))))
                )

                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0))) {
                Icon(Icons.Rounded.Description, null, tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
fun MusicPill(
    title: String,
    isPlaying: Boolean,
    thumbnail: String?,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    musicViewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 8000 else 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thumbRotation"
    )

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(if (compact) 28.dp else 36.dp),
        border = if (isPlaying) BorderStroke(2.dp, contentColor.copy(alpha = 0.4f)) else BorderStroke(1.dp, contentColor.copy(alpha = 0.15f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 36.dp else 52.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.1f))
                    .rotate(rotation),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        modifier = Modifier.size(if (compact) 18.dp else 24.dp),
                        tint = contentColor
                    )
                }
                
                Surface(
                    modifier = Modifier.size(if (compact) 8.dp else 12.dp),
                    color = containerColor,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
                ) {}
            }
            
            Spacer(Modifier.width(if (compact) 12.dp else 20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isPlaying) "Active Payload" else "Audio Interface",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }

            if (!compact) {
                IconButton(
                    onClick = { musicViewModel.togglePlayPause() },
                    modifier = Modifier.size(44.dp).background(contentColor.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor
                    )
                }
            } else if (isPlaying) {
                Row(
                    modifier = Modifier.height(16.dp).padding(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(3) { index ->
                        val barHeight by infiniteTransition.animateFloat(
                            initialValue = 4f,
                            targetValue = 16f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(400 + (index * 200), easing = FastOutLinearInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bar$index"
                        )
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight.dp)
                                .background(contentColor, RoundedCornerShape(1.5.dp))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorDialog(
    note: Note?,
    viewModel: NotepadViewModel,
    isAmoled: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, Float, Boolean, Boolean, String?, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var selectedColor by remember { mutableStateOf(note?.color ?: Color(0xFFFFF9C4).toArgb()) }
    var fontStyle by remember { mutableStateOf(note?.fontStyle ?: "DEFAULT") }
    var fontSize by remember { mutableFloatStateOf(note?.fontSize ?: 18f) }
    var isBold by remember { mutableStateOf(note?.isBold ?: false) }
    var isItalic by remember { mutableStateOf(note?.isItalic ?: false) }
    var attachedPdfUri by remember { mutableStateOf(note?.attachedPdfUri) }
    var attachedAudioUri by remember { mutableStateOf(note?.attachedAudioUri) }
    var attachedAudioName by remember { mutableStateOf(note?.attachedAudioName) }
    
    val availableTracks by viewModel.availableTracks.collectAsStateWithLifecycle()
    val availablePdfs by viewModel.availablePdfs.collectAsStateWithLifecycle()

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showTrackPicker by remember { mutableStateOf(false) }
    var showPdfPicker by remember { mutableStateOf(false) }
    var showCustomColorDialog by remember { mutableStateOf(false) }

    val colors = listOf(
        Color(0xFFFFF9C4), Color(0xFFFFCCBC), Color(0xFFC8E6C9), 
        Color(0xFFB3E5FC), Color(0xFFE1BEE7), Color(0xFFF5F5F5),
        Color(0xFFD7CCC8), Color(0xFFCFD8DC), Color(0xFFFFE0B2),
        Color(0xFF263238)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(selectedColor)
        ) {
            val onBgColor = if (isDark(Color(selectedColor))) Color.White else Color.Black
            
            Column(modifier = Modifier.statusBarsPadding()) {
                CenterAlignedTopAppBar(
                    title = { Text(if (note == null) "NEW ENTRY" else "MODIFY ENTRY", color = onBgColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = onBgColor)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAttachmentMenu = true }) {
                            Icon(Icons.Rounded.AttachFile, null, tint = onBgColor)
                        }
                        
                        TextButton(
                            onClick = { onSave(title, content, selectedColor, fontStyle, fontSize, isBold, isItalic, attachedPdfUri, attachedAudioUri, attachedAudioName) },
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Surface(
                                color = onBgColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, onBgColor.copy(alpha = 0.1f))
                            ) {
                                Text("DEPLOY", fontWeight = FontWeight.Black, color = onBgColor, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), letterSpacing = 1.sp)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
                
                Column(modifier = Modifier.padding(horizontal = 28.dp).weight(1f)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Entry Designation", style = MaterialTheme.typography.headlineMedium, color = onBgColor.copy(alpha = 0.3f), fontWeight = FontWeight.Black) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = onBgColor),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = onBgColor
                        )
                    )
                    
                    AnimatedVisibility(visible = attachedAudioUri != null || attachedPdfUri != null) {
                        Row(
                            modifier = Modifier.padding(vertical = 16.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            attachedAudioUri?.let {
                                AttachmentChip(
                                    label = attachedAudioName ?: "Audio Payload",
                                    icon = Icons.Rounded.MusicNote,
                                    onDelete = { attachedAudioUri = null; attachedAudioName = null },
                                    color = onBgColor
                                )
                            }
                            attachedPdfUri?.let {
                                AttachmentChip(
                                    label = "PDF Document",
                                    icon = Icons.Rounded.Description,
                                    onDelete = { attachedPdfUri = null },
                                    color = onBgColor
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.padding(vertical = 12.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = onBgColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, onBgColor.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isBold = !isBold }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.FormatBold, contentDescription = "Bold", tint = if (isBold) MaterialTheme.colorScheme.primary else onBgColor, modifier = Modifier.size(24.dp))
                            }
                            IconButton(onClick = { isItalic = !isItalic }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.FormatItalic, contentDescription = "Italic", tint = if (isItalic) MaterialTheme.colorScheme.primary else onBgColor, modifier = Modifier.size(24.dp))
                            }
                            
                            VerticalDivider(modifier = Modifier.height(24.dp).width(1.5.dp), color = onBgColor.copy(alpha = 0.2f))

                            Box {
                                var showFontMenu by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showFontMenu = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(fontStyle, color = onBgColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    Icon(Icons.Rounded.ArrowDropDown, null, tint = onBgColor, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }, shape = RoundedCornerShape(24.dp)) {
                                    val fonts = listOf("DEFAULT", "SERIF", "MONOSPACE", "CASUAL")
                                    fonts.forEach { font ->
                                        DropdownMenuItem(
                                            text = { Text(font, fontWeight = FontWeight.Bold) },
                                            onClick = { fontStyle = font; showFontMenu = false }
                                        )
                                    }
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).width(1.5.dp), color = onBgColor.copy(alpha = 0.2f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp).width(220.dp)
                            ) {
                                Icon(Icons.Rounded.TextFields, null, tint = onBgColor, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                com.frerox.toolz.ui.components.SquigglySlider(
                                    value = fontSize,
                                    onValueChange = { fontSize = it },
                                    valueRange = 12f..48f,
                                    modifier = Modifier.weight(1f),
                                    activeColor = onBgColor,
                                    inactiveColor = onBgColor.copy(alpha = 0.25f)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${fontSize.toInt()}", 
                                    color = onBgColor, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(24.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showCustomColorDialog = true },
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(onBgColor.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Rounded.Palette, null, tint = onBgColor, modifier = Modifier.size(22.dp))
                        }
                        
                        colors.forEach { color ->
                            val argb = color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = argb }
                                    .border(
                                        width = if (selectedColor == argb) 3.5.dp else 1.dp,
                                        color = if (selectedColor == argb) onBgColor else onBgColor.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == argb) {
                                    Icon(Icons.Rounded.Check, null, tint = onBgColor, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Deploy your sequence of thoughts...", style = MaterialTheme.typography.bodyLarge, color = onBgColor.copy(alpha = 0.35f)) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = when(fontStyle) {
                                "SERIF" -> FontFamily.Serif
                                "MONOSPACE" -> FontFamily.Monospace
                                "CASUAL" -> FontFamily.Cursive
                                else -> FontFamily.Default
                            },
                            fontSize = fontSize.sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            color = onBgColor,
                            lineHeight = 1.6.times(fontSize).sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = onBgColor
                        )
                    )
                }
            }
        }

        if (showAttachmentMenu) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentMenu = false },
                shape = RoundedCornerShape(topStart = 64.dp, topEnd = 64.dp)
            ) {
                Column(modifier = Modifier.padding(28.dp).padding(bottom = 40.dp)) {
                    Text("ATTACHMENT ENGINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                    Spacer(Modifier.height(24.dp))
                    AttachmentTypeItem(
                        title = "Audio Matrix",
                        desc = "Index a track or voice memo",
                        icon = Icons.Rounded.MusicNote,
                        color = Color(0xFFFF4081),
                        onClick = { 
                            showTrackPicker = true
                            showAttachmentMenu = false
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    AttachmentTypeItem(
                        title = "PDF Structure",
                        desc = "Deploy a document reference",
                        icon = Icons.Rounded.Description,
                        color = Color(0xFF2196F3),
                        onClick = { 
                            showPdfPicker = true
                            showAttachmentMenu = false
                        }
                    )
                }
            }
        }

        if (showTrackPicker) {
            AttachmentPickerDialog(
                title = "SELECT AUDIO SOURCE",
                items = availableTracks.map { it.title to it.uri },
                onDismiss = { showTrackPicker = false },
                onSelect = { name, uri ->
                    attachedAudioUri = uri
                    attachedAudioName = name
                    showTrackPicker = false
                }
            )
        }

        if (showPdfPicker) {
            AttachmentPickerDialog(
                title = "SELECT DOCUMENT SOURCE",
                items = availablePdfs.map { it.name to it.uri.toString() },
                onDismiss = { showPdfPicker = false },
                onSelect = { _, uri ->
                    attachedPdfUri = uri
                    showPdfPicker = false
                }
            )
        }
        
        if (showCustomColorDialog) {
            CustomColorDialog(
                initialColor = selectedColor,
                onDismiss = { showCustomColorDialog = false },
                onColorSelected = { 
                    selectedColor = it
                    showCustomColorDialog = false
                }
            )
        }
    }
}

@Composable
fun CustomColorDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    var hexInput by remember { mutableStateOf(String.format("%06X", (0xFFFFFF and initialColor))) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CHROMA ENGINE", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, style = MaterialTheme.typography.labelMedium) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(100.dp),
                    color = try { Color(android.graphics.Color.parseColor("#$hexInput")) } catch(e: Exception) { Color.Gray },
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {}
                
                Spacer(Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { if (it.length <= 6) hexInput = it.uppercase().filter { c -> c in "0123456789ABCDEF" } },
                    label = { Text("HEX CODE") },
                    prefix = { Text("#") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { 
                        try { onColorSelected(android.graphics.Color.parseColor("#$hexInput")) } catch(e: Exception) {} 
                    })
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    try { onColorSelected(android.graphics.Color.parseColor("#$hexInput")) } catch(e: Exception) {}
                },
                shape = RoundedCornerShape(16.dp)
            ) { Text("CALIBRATE", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", fontWeight = FontWeight.Bold) }
        },
        shape = RoundedCornerShape(48.dp)
    )
}

@Composable
fun AttachmentTypeItem(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun AttachmentChip(label: String, icon: ImageVector, onDelete: () -> Unit, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(10.dp))
            Text(label.take(15), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp), tint = color.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun AttachmentPickerDialog(
    title: String,
    items: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(32.dp).widthIn(max = 450.dp).heightIn(max = 550.dp),
            shape = RoundedCornerShape(56.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Spacer(Modifier.height(24.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items) { (name, uri) ->
                        Surface(
                            onClick = { onSelect(name, uri) },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (title.contains("AUDIO")) Icons.Rounded.MusicNote else Icons.Rounded.Description,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

private fun isDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}
