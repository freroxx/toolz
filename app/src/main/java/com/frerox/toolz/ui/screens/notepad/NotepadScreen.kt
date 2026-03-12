package com.frerox.toolz.ui.screens.notepad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(
    viewModel: NotepadViewModel,
    onBack: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onViewPdf: (String) -> Unit,
    initialNoteId: Int? = null
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

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
        containerColor = if (isDark) Color.Black else MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = if (isDark) Color.Black else MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.shadow(if (isDark) 0.dp else 8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    TopAppBar(
                        title = { Text("NOTEPAD", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, letterSpacing = 1.sp) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search your notes...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = if (isDark) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = if (isDark) Color(0xFF222222) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    noteToEdit = null
                    showEditor = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp),
                icon = { Icon(Icons.Rounded.Add, "Add Note") },
                text = { Text("NEW NOTE", fontWeight = FontWeight.Black) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            if (searchQuery.isEmpty()) Icons.Rounded.EditNote else Icons.Rounded.SearchOff, 
                            contentDescription = null, 
                            modifier = Modifier.size(120.dp), 
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            if (searchQuery.isEmpty()) "Your digital journal is empty" else "No matching notes found", 
                            style = MaterialTheme.typography.titleMedium, 
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 24.dp, 16.dp, 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        ImprovedNoteItem(
                            note = note, 
                            isDark = isDark,
                            onClick = { 
                                noteToEdit = note
                                showEditor = true
                            },
                            onDelete = { viewModel.deleteNote(note) },
                            onTogglePin = { viewModel.togglePin(note) },
                            onPlayAudio = { note.attachedAudioUri?.let { onPlayAudio(it) } },
                            onViewPdf = { note.attachedPdfUri?.let { onViewPdf(it) } }
                        )
                    }
                }
            }
        }

        if (showEditor) {
            NoteEditorDialog(
                note = noteToEdit,
                viewModel = viewModel,
                isAmoled = isDark,
                onDismiss = { showEditor = false },
                onSave = { title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName ->
                    if (noteToEdit == null) {
                        viewModel.addNote(title, content, color, fontStyle, fontSize, bold, italic, pdfUri, audioUri, audioName)
                    } else {
                        viewModel.updateNote(noteToEdit!!.copy(
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
                        ))
                    }
                    showEditor = false
                }
            )
        }
    }
}

@Composable
fun ImprovedNoteItem(
    note: Note, 
    isDark: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onPlayAudio: () -> Unit,
    onViewPdf: () -> Unit
) {
    val noteColor = Color(note.color)
    val onNoteColor = if (isDark(noteColor)) Color.White else Color.Black

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = noteColor.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, onNoteColor.copy(alpha = 0.1f))
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
                    Icon(
                        Icons.Rounded.PushPin, 
                        contentDescription = "Pinned", 
                        modifier = Modifier.size(16.dp).padding(start = 4.dp), 
                        tint = onNoteColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = note.content, 
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = when(note.fontStyle) {
                        "SERIF" -> FontFamily.Serif
                        "MONOSPACE" -> FontFamily.Monospace
                        "ROBOTO" -> FontFamily.SansSerif
                        "CASUAL" -> FontFamily.Cursive
                        "CURSIVE" -> FontFamily.Cursive
                        else -> FontFamily.Default
                    },
                    fontSize = (note.fontSize * 0.85f).sp,
                    fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 1.2.times(note.fontSize * 0.85f).sp
                ),
                color = onNoteColor.copy(alpha = 0.8f),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            
            if (note.attachedAudioUri != null || note.attachedPdfUri != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (note.attachedAudioUri != null) {
                        Surface(
                            onClick = onPlayAudio,
                            color = onNoteColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(44.dp).weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val infiniteTransition = rememberInfiniteTransition()
                                val rotation by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(4000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )
                                Icon(
                                    Icons.Rounded.MusicNote, 
                                    null, 
                                    modifier = Modifier.size(20.dp).rotate(rotation), 
                                    tint = onNoteColor
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    note.attachedAudioName?.take(8) ?: "Audio", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onNoteColor,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (note.attachedPdfUri != null) {
                        Surface(
                            onClick = onViewPdf,
                            color = onNoteColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(44.dp).weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(20.dp), tint = onNoteColor)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "PDF", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onNoteColor,
                                    fontWeight = FontWeight.Bold
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
                    text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(note.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onNoteColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
                
                Row {
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
                            Icons.Rounded.DeleteOutline, 
                            contentDescription = "Delete", 
                            tint = onNoteColor.copy(alpha = 0.3f), 
                            modifier = Modifier.size(16.dp)
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
    var fontStyle by remember { mutableStateOf(note?.fontStyle ?: "SANS_SERIF") }
    var fontSize by remember { mutableFloatStateOf(note?.fontSize ?: 18f) }
    var isBold by remember { mutableStateOf(note?.isBold ?: false) }
    var isItalic by remember { mutableStateOf(note?.isItalic ?: false) }
    var attachedPdfUri by remember { mutableStateOf(note?.attachedPdfUri) }
    var attachedAudioUri by remember { mutableStateOf(note?.attachedAudioUri) }
    var attachedAudioName by remember { mutableStateOf(note?.attachedAudioName) }
    
    val context = LocalContext.current
    val availableTracks by viewModel.availableTracks.collectAsStateWithLifecycle()
    val availablePdfs by viewModel.availablePdfs.collectAsStateWithLifecycle()

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showTrackPicker by remember { mutableStateOf(false) }
    var showPdfPicker by remember { mutableStateOf(false) }

    val colors = listOf(
        Color(0xFFFFF9C4), Color(0xFFFFCCBC), Color(0xFFC8E6C9), 
        Color(0xFFB3E5FC), Color(0xFFE1BEE7), Color(0xFFF5F5F5),
        Color(0xFFD7CCC8), Color(0xFFCFD8DC), Color(0xFFFFE0B2),
        Color(0xFF212121)
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
                TopAppBar(
                    title = { Text(if (note == null) "NEW NOTE" else "EDIT NOTE", color = onBgColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) },
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
                                Text("SAVE", fontWeight = FontWeight.Black, color = onBgColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                
                Column(modifier = Modifier.padding(horizontal = 24.dp).weight(1f)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium, color = onBgColor.copy(alpha = 0.3f), fontWeight = FontWeight.Black) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = onBgColor),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = onBgColor,
                            unfocusedTextColor = onBgColor,
                            focusedTextColor = onBgColor
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
                                    icon = Icons.Rounded.PictureAsPdf,
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isBold = !isBold }) {
                                Icon(Icons.Rounded.FormatBold, contentDescription = "Bold", tint = if (isBold) MaterialTheme.colorScheme.primary else onBgColor)
                            }
                            IconButton(onClick = { isItalic = !isItalic }) {
                                Icon(Icons.Rounded.FormatItalic, contentDescription = "Italic", tint = if (isItalic) MaterialTheme.colorScheme.primary else onBgColor)
                            }
                            
                            VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = onBgColor.copy(alpha = 0.2f))

                            Box {
                                var showFontMenu by remember { mutableStateOf(false) }
                                TextButton(onClick = { showFontMenu = true }) {
                                    Text(fontStyle.replace("_", " "), color = onBgColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Icon(Icons.Rounded.ArrowDropDown, null, tint = onBgColor, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                                    val fonts = listOf("SANS_SERIF", "SERIF", "MONOSPACE", "ROBOTO", "CASUAL", "CURSIVE")
                                    fonts.forEach { font ->
                                        DropdownMenuItem(
                                            text = { Text(font.lowercase().replaceFirstChar { it.uppercase() }) },
                                            onClick = { fontStyle = font; showFontMenu = false }
                                        )
                                    }
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = onBgColor.copy(alpha = 0.2f))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if (fontSize > 10) fontSize -= 2 }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Remove, null, tint = onBgColor, modifier = Modifier.size(16.dp))
                                }
                                Text("${fontSize.toInt()}", color = onBgColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                IconButton(onClick = { if (fontSize < 60) fontSize += 2 }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Add, null, tint = onBgColor, modifier = Modifier.size(16.dp))
                                }
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
                                    .size(36.dp)
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
                                    Icon(Icons.Rounded.Check, null, tint = onBgColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Start typing your masterpiece...", style = MaterialTheme.typography.bodyLarge, color = onBgColor.copy(alpha = 0.3f)) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = when(fontStyle) {
                                "SERIF" -> FontFamily.Serif
                                "MONOSPACE" -> FontFamily.Monospace
                                "ROBOTO" -> FontFamily.SansSerif
                                "CASUAL" -> FontFamily.Cursive
                                "CURSIVE" -> FontFamily.Cursive
                                else -> FontFamily.Default
                            },
                            fontSize = fontSize.sp,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            color = onBgColor,
                            lineHeight = 1.4.times(fontSize).sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = onBgColor,
                            unfocusedTextColor = onBgColor,
                            focusedTextColor = onBgColor
                        )
                    )
                }
            }
        }

        if (showAttachmentMenu) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentMenu = false },
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Attach File", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (isAmoled) Color.White else Color.Unspecified)
                    Spacer(Modifier.height(24.dp))
                    AttachmentTypeItem(
                        title = "Audio Track",
                        desc = "Select from your music library",
                        icon = Icons.Rounded.MusicNote,
                        color = Color(0xFFFF4081),
                        isAmoled = isAmoled,
                        onClick = { 
                            showTrackPicker = true
                            showAttachmentMenu = false
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    AttachmentTypeItem(
                        title = "PDF Document",
                        desc = "Select from your documents",
                        icon = Icons.Rounded.PictureAsPdf,
                        color = Color(0xFF2196F3),
                        isAmoled = isAmoled,
                        onClick = { 
                            showPdfPicker = true
                            showAttachmentMenu = false
                        }
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        if (showTrackPicker) {
            AttachmentPickerDialog(
                title = "Select Audio",
                items = availableTracks.map { it.title to it.uri },
                isAmoled = isAmoled,
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
                title = "Select PDF",
                items = availablePdfs.map { it.name to it.uri.toString() },
                isAmoled = isAmoled,
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
fun AttachmentTypeItem(title: String, desc: String, icon: ImageVector, color: Color, isAmoled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = if (isAmoled) Color.White else Color.Unspecified)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun AttachmentChip(label: String, icon: ImageVector, onDelete: () -> Unit, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(8.dp))
            Text(label.take(15), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Rounded.Close, 
                    null, 
                    modifier = Modifier.size(14.dp),
                    tint = color.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun AttachmentPickerDialog(
    title: String,
    items: List<Pair<String, String>>,
    isAmoled: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp).widthIn(max = 450.dp).heightIn(max = 500.dp),
            shape = RoundedCornerShape(32.dp),
            color = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = if (isAmoled) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (isAmoled) Color.White else Color.Unspecified)
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items) { (name, uri) ->
                        Surface(
                            onClick = { onSelect(name, uri) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (title.contains("Audio")) Icons.Rounded.MusicNote else Icons.Rounded.PictureAsPdf,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (isAmoled) Color.White else Color.Unspecified)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text("CANCEL", fontWeight = FontWeight.Bold, color = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

private fun isDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}

@Composable
fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}
