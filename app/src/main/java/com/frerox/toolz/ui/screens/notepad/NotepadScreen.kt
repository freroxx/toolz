package com.frerox.toolz.ui.screens.notepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    CenterAlignedTopAppBar(
                        title = { 
                            Text(
                                "NOTEPAD", 
                                fontWeight = FontWeight.Black, 
                                style = MaterialTheme.typography.headlineSmall, 
                                letterSpacing = 3.sp,
                                color = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .padding(start = 16.dp)
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
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
                            placeholder = { Text("Search your thoughts...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp)),
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(16.dp))
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
                    .padding(bottom = 16.dp, end = 8.dp)
                    .bouncyClick(onClick = { 
                        noteToEdit = null
                        showEditor = true 
                    }),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
                icon = { Icon(Icons.Rounded.Add, "Add Note", Modifier.size(28.dp)) },
                text = { Text("NEW NOTE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = RoundedCornerShape(40.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                if (searchQuery.isEmpty()) Icons.Rounded.EditNote else Icons.Rounded.SearchOff, 
                                contentDescription = null, 
                                modifier = Modifier.padding(32.dp), 
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            if (searchQuery.isEmpty()) "Your canvas is empty" else "No matching notes found", 
                            style = MaterialTheme.typography.titleMedium, 
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().fadingEdge(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
                        length = 24.dp
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        ImprovedNoteItem(
                            note = note, 
                            isDark = isDark,
                            isPlaying = musicState.isPlaying && musicState.currentTrack?.uri == note.attachedAudioUri,
                            currentTrackThumbnail = musicState.currentTrack?.thumbnailUri,
                            onClick = { 
                                noteToEdit = note
                                showEditor = false // We now open viewer first through noteToEdit being non-null
                            },
                            onDelete = { 
                                viewModel.deleteNote(note)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Note moved to trash",
                                        actionLabel = "UNDO",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDelete()
                                    }
                                }
                            },
                            onTogglePin = { viewModel.togglePin(note) },
                            onPlayAudio = { note.attachedAudioUri?.let { onPlayAudio(it) } },
                            onViewPdf = { note.attachedPdfUri?.let { onViewPdf(it) } }
                        )
                    }
                }
            }
        }

        if (showEditor && noteToEdit == null) {
            // Creating a new note
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
            // Viewing an existing note
            NoteViewerDialog(
                note = noteToEdit!!,
                viewModel = viewModel,
                isAmoled = isDark,
                isPlaying = musicState.isPlaying && musicState.currentTrack?.uri == noteToEdit!!.attachedAudioUri,
                currentTrackThumbnail = musicState.currentTrack?.thumbnailUri,
                onDismiss = { noteToEdit = null },
                onEdit = {
                    showEditor = true // This will trigger the NoteEditorDialog overlay within the viewer's awareness, or we can just swap
                },
                onPlayAudio = { onPlayAudio(it) },
                onViewPdf = { onViewPdf(it) }
            )

            // If user clicked Edit from inside NoteViewerDialog
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
                        noteToEdit = updated // Update viewer state
                        showEditor = false
                    }
                )
            }
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
        containerColor = noteColor.copy(alpha = 0.9f),
        contentColor = onNoteColor,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = onNoteColor.copy(alpha = 0.4f)) },
        tonalElevation = 12.dp,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(note.timestamp)),
                    style = MaterialTheme.typography.labelMedium,
                    color = onNoteColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.togglePin(note) },
                        modifier = Modifier.size(36.dp).background(onNoteColor.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) onNoteColor else onNoteColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp).background(onNoteColor.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Edit, "Edit", tint = onNoteColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            viewModel.deleteNote(note)
                            onDismiss()
                        },
                        modifier = Modifier.size(36.dp).background(onNoteColor.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Delete, "Delete", tint = onNoteColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = note.title.ifEmpty { "Untitled" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = onNoteColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (note.attachedAudioUri != null || note.attachedPdfUri != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 24.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (note.attachedAudioUri != null) {
                            MusicPill(
                                title = note.attachedAudioName ?: "Play Audio",
                                isPlaying = isPlaying,
                                thumbnail = currentTrackThumbnail,
                                containerColor = onNoteColor.copy(alpha = 0.15f),
                                contentColor = onNoteColor,
                                onClick = { onPlayAudio(note.attachedAudioUri) }
                            )
                        }
                        if (note.attachedPdfUri != null) {
                            Surface(
                                onClick = { onViewPdf(note.attachedPdfUri) },
                                color = onNoteColor.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Description, 
                                        null, 
                                        modifier = Modifier.size(24.dp), 
                                        tint = onNoteColor
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "View PDF",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = onNoteColor,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }

                if (note.attachedPdfUri != null) {
                    PdfPreview(
                        uri = note.attachedPdfUri, 
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                            .height(180.dp)
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
                        lineHeight = 1.5.times(note.fontSize).sp
                    ),
                    color = onNoteColor.copy(alpha = 0.85f)
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(if (visible) 1f else 0.9f, spring(Spring.DampingRatioMediumBouncy), label = "")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "")

    val noteColor = Color(note.color)
    val onNoteColor = if (isDark(noteColor)) Color.White else Color.Black
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thumbRotation"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = noteColor.copy(alpha = 0.95f),
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, onNoteColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    val pinScale by animateFloatAsState(if (note.isPinned) 1.2f else 1f, spring(Spring.DampingRatioHighBouncy), label = "pinScale")
                    Icon(
                        Icons.Rounded.PushPin, 
                        contentDescription = "Pinned", 
                        modifier = Modifier
                            .size(16.dp)
                            .padding(start = 4.dp)
                            .graphicsLayer { 
                                scaleX = pinScale
                                scaleY = pinScale
                                rotationZ = -15f
                            }, 
                        tint = onNoteColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // PDF Preview
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
                    fontSize = (note.fontSize * 0.8f).sp,
                    fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 1.3.times(note.fontSize * 0.8f).sp
                ),
                color = onNoteColor.copy(alpha = 0.7f),
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
            
            if (note.attachedAudioUri != null || note.attachedPdfUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (note.attachedAudioUri != null) {
                        MusicPill(
                            title = note.attachedAudioName ?: "Audio",
                            isPlaying = isPlaying,
                            thumbnail = currentTrackThumbnail,
                            containerColor = if (isPlaying) onNoteColor.copy(alpha = 0.2f) else onNoteColor.copy(alpha = 0.1f),
                            contentColor = onNoteColor,
                            onClick = onPlayAudio,
                            modifier = Modifier.weight(1f),
                            compact = true
                        )
                    }
                    if (note.attachedPdfUri != null) {
                        Surface(
                            onClick = onViewPdf,
                            color = onNoteColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Description, 
                                    null, 
                                    modifier = Modifier.size(18.dp), 
                                    tint = onNoteColor
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "PDF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onNoteColor,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(note.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onNoteColor.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) onNoteColor else onNoteColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Delete, 
                            contentDescription = "Delete", 
                            tint = onNoteColor.copy(alpha = 0.2f), 
                            modifier = Modifier.size(14.dp)
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
                    val b = Bitmap.createBitmap(page.width / 4, page.height / 4, Bitmap.Config.ARGB_8888)
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
            .height(140.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.3f))
            .border(1.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
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
                // Overlay to make it feel like a document
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f))
                            )
                        )
                )
            }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.PictureAsPdf, 
                        null, 
                        tint = Color(0xFFE53935).copy(alpha = 0.8f), 
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "DOCUMENT PREVIEW", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black, 
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                }
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
    compact: Boolean = false
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

    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.02f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        color = containerColor,
        shape = RoundedCornerShape(if (compact) 16.dp else 24.dp),
        border = if (isPlaying) BorderStroke(1.dp, contentColor.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 24.dp else 32.dp)
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
                        modifier = Modifier.size(if (compact) 14.dp else 18.dp),
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                }
                
                // Overlay play/pause icon in the center of the disc
                Surface(
                    modifier = Modifier.size(if (compact) 12.dp else 16.dp),
                    color = contentColor.copy(alpha = 0.8f),
                    shape = CircleShape
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        modifier = Modifier.padding(2.dp),
                        tint = containerColor
                    )
                }
            }
            
            Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
            
            Text(
                text = if (compact) title.take(10) else title,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (!compact && isPlaying) {
                Spacer(Modifier.width(10.dp))
                // Simple visualizer effect (3 bars)
                Row(
                    modifier = Modifier.height(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(3) { index ->
                        val barHeight by infiniteTransition.animateFloat(
                            initialValue = 4f,
                            targetValue = 12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(300 + (index * 100), easing = FastOutLinearInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bar$index"
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(barHeight.dp)
                                .background(contentColor, RoundedCornerShape(1.dp))
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
                    title = { Text(if (note == null) "NEW NOTE" else "EDIT NOTE", color = onBgColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp) },
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
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Surface(
                                color = onBgColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("DONE", fontWeight = FontWeight.Black, color = onBgColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
                
                Column(modifier = Modifier.padding(horizontal = 24.dp).weight(1f)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Headline", style = MaterialTheme.typography.headlineSmall, color = onBgColor.copy(alpha = 0.3f), fontWeight = FontWeight.Black) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, color = onBgColor),
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
                            modifier = Modifier.padding(vertical = 12.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attachedAudioUri?.let {
                                AttachmentChip(
                                    label = attachedAudioName ?: "Audio",
                                    icon = Icons.Rounded.MusicNote,
                                    onDelete = { attachedAudioUri = null; attachedAudioName = null },
                                    color = onBgColor
                                )
                            }
                            attachedPdfUri?.let {
                                AttachmentChip(
                                    label = "PDF Attached",
                                    icon = Icons.Rounded.Description,
                                    onDelete = { attachedPdfUri = null },
                                    color = onBgColor
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.padding(vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = onBgColor.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isBold = !isBold }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.FormatBold, contentDescription = "Bold", tint = if (isBold) MaterialTheme.colorScheme.primary else onBgColor, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { isItalic = !isItalic }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.FormatItalic, contentDescription = "Italic", tint = if (isItalic) MaterialTheme.colorScheme.primary else onBgColor, modifier = Modifier.size(20.dp))
                            }
                            
                            VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = onBgColor.copy(alpha = 0.2f))

                            Box {
                                var showFontMenu by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier.clickable { showFontMenu = true }.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(fontStyle, color = onBgColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    Icon(Icons.Rounded.ArrowDropDown, null, tint = onBgColor, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                                    val fonts = listOf("DEFAULT", "SERIF", "MONOSPACE", "CASUAL")
                                    fonts.forEach { font ->
                                        DropdownMenuItem(
                                            text = { Text(font, fontWeight = FontWeight.Bold) },
                                            onClick = { fontStyle = font; showFontMenu = false }
                                        )
                                    }
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(20.dp).width(1.dp), color = onBgColor.copy(alpha = 0.2f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp).width(120.dp)
                            ) {
                                Icon(Icons.Rounded.TextFields, null, tint = onBgColor, modifier = Modifier.size(16.dp))
                                com.frerox.toolz.ui.components.SquigglySlider(
                                    value = fontSize,
                                    onValueChange = { fontSize = it },
                                    valueRange = 12f..48f,
                                    modifier = Modifier.weight(1f),
                                    activeColor = onBgColor,
                                    isPlaying = true
                                )
                                Text(
                                    "${fontSize.toInt()}", 
                                    color = onBgColor, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(20.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        colors.forEach { color ->
                            val argb = color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = argb }
                                    .border(
                                        width = if (selectedColor == argb) 3.dp else 1.dp,
                                        color = if (selectedColor == argb) onBgColor else onBgColor.copy(alpha = 0.1f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == argb) {
                                    Icon(Icons.Rounded.Check, null, tint = onBgColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Write your masterpiece...", style = MaterialTheme.typography.bodyLarge, color = onBgColor.copy(alpha = 0.3f)) },
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
                            lineHeight = 1.5.times(fontSize).sp
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
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
                    Text("ATTACHMENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                    Spacer(Modifier.height(20.dp))
                    AttachmentTypeItem(
                        title = "Audio Track",
                        desc = "Link a song or recording",
                        icon = Icons.Rounded.MusicNote,
                        color = Color(0xFFFF4081),
                        onClick = { 
                            showTrackPicker = true
                            showAttachmentMenu = false
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    AttachmentTypeItem(
                        title = "PDF Document",
                        desc = "Link a relevant PDF file",
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
                title = "SELECT AUDIO",
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
                title = "SELECT DOCUMENT",
                items = availablePdfs.map { it.name to it.uri.toString() },
                onDismiss = { showPdfPicker = false },
                onSelect = { _, uri ->
                    attachedPdfUri = uri
                    showPdfPicker = false
                }
            )
        }
    }
}

@Composable
fun AttachmentTypeItem(title: String, desc: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun AttachmentChip(label: String, icon: ImageVector, onDelete: () -> Unit, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(label.take(12), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(12.dp), tint = color.copy(alpha = 0.5f))
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
            modifier = Modifier.padding(32.dp).widthIn(max = 400.dp).heightIn(max = 500.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items) { (name, uri) ->
                        Surface(
                            onClick = { onSelect(name, uri) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (title.contains("AUDIO")) Icons.Rounded.MusicNote else Icons.Rounded.Description,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { 
                    Text("CANCEL", fontWeight = FontWeight.Black) 
                }
            }
        }
    }
}

private fun isDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}
