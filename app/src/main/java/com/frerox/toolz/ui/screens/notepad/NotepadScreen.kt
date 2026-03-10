package com.frerox.toolz.ui.screens.notepad

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadScreen(
    viewModel: NotepadViewModel,
    onBack: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<Note?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredNotes = notes.filter { 
        it.title.contains(searchQuery, ignoreCase = true) || 
        it.content.contains(searchQuery, ignoreCase = true) 
    }.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.id })

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    TopAppBar(
                        title = { Text("Notepad", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (notes.isNotEmpty()) {
                                IconButton(onClick = { /* Sort logic */ }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                                }
                            }
                        }
                    )
                    
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search your notes...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { 
                    noteToEdit = null
                    showEditor = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Note", modifier = Modifier.size(36.dp))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (searchQuery.isEmpty()) Icons.Rounded.Description else Icons.Rounded.SearchOff, 
                            contentDescription = null, 
                            modifier = Modifier.size(120.dp), 
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            if (searchQuery.isEmpty()) "Your thoughts belong here" else "No matching notes", 
                            style = MaterialTheme.typography.titleMedium, 
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdge(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.05f to Color.Black,
                                0.95f to Color.Black,
                                1f to Color.Transparent
                            ),
                            length = 24.dp
                        ),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        ImprovedNoteItem(
                            note = note, 
                            onClick = { 
                                noteToEdit = note
                                showEditor = true
                            },
                            onDelete = { viewModel.deleteNote(note) },
                            onTogglePin = { viewModel.togglePin(note) }
                        )
                    }
                }
            }
        }

        if (showEditor) {
            NoteEditorDialog(
                note = noteToEdit,
                onDismiss = { showEditor = false },
                onSave = { title, content, color, fontStyle, bold, italic ->
                    if (noteToEdit == null) {
                        viewModel.addNote(title, content, color, fontStyle, bold, italic)
                    } else {
                        viewModel.updateNote(noteToEdit!!.copy(
                            title = title, 
                            content = content, 
                            color = color,
                            fontStyle = fontStyle,
                            isBold = bold,
                            isItalic = italic
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
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    val noteColor = Color(note.color)
    val onNoteColor = if (isDark(noteColor)) Color.White else Color.Black

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = noteColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        modifier = Modifier.size(16.dp).padding(start = 4.dp), 
                        tint = onNoteColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = note.content, 
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = when(note.fontStyle) {
                        "SERIF" -> FontFamily.Serif
                        "MONOSPACE" -> FontFamily.Monospace
                        else -> FontFamily.Default
                    },
                    fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 22.sp
                ),
                color = onNoteColor.copy(alpha = 0.7f),
                maxLines = 12,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onTogglePin,
                    color = onNoteColor.copy(alpha = 0.05f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Rounded.PushPin else Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) onNoteColor else onNoteColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(Modifier.width(8.dp))
                
                Surface(
                    onClick = onDelete,
                    color = onNoteColor.copy(alpha = 0.05f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.DeleteOutline, 
                            contentDescription = "Delete", 
                            tint = onNoteColor.copy(alpha = 0.4f), 
                            modifier = Modifier.size(18.dp)
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
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, Boolean, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var selectedColor by remember { mutableStateOf(note?.color ?: Color(0xFFFFF9C4).toArgb()) }
    var fontStyle by remember { mutableStateOf(note?.fontStyle ?: "SANS_SERIF") }
    var isBold by remember { mutableStateOf(note?.isBold ?: false) }
    var isItalic by remember { mutableStateOf(note?.isItalic ?: false) }

    val colors = listOf(
        Color(0xFFFFF9C4), Color(0xFFFFCCBC), Color(0xFFC8E6C9), 
        Color(0xFFB3E5FC), Color(0xFFE1BEE7), Color(0xFFF5F5F5),
        Color(0xFFD7CCC8), Color(0xFFCFD8DC), Color(0xFFFFE0B2)
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
            
            Column {
                TopAppBar(
                    title = { Text(if (note == null) "New Note" else "Edit Note", color = onBgColor, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = onBgColor)
                        }
                    },
                    actions = {
                        Button(
                            onClick = { onSave(title, content, selectedColor, fontStyle, isBold, isItalic) },
                            modifier = Modifier.padding(end = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = onBgColor,
                                contentColor = Color(selectedColor)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                
                Column(modifier = Modifier.padding(24.dp).weight(1f)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium, color = onBgColor.copy(alpha = 0.3f)) },
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
                    
                    // Toolbar
                    Surface(
                        modifier = Modifier.padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = onBgColor.copy(alpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { isBold = !isBold }) {
                                Icon(Icons.Rounded.FormatBold, contentDescription = "Bold", tint = if (isBold) MaterialTheme.colorScheme.primary else onBgColor)
                            }
                            IconButton(onClick = { isItalic = !isItalic }) {
                                Icon(Icons.Rounded.FormatItalic, contentDescription = "Italic", tint = if (isItalic) MaterialTheme.colorScheme.primary else onBgColor)
                            }
                            
                            Box {
                                var showFontMenu by remember { mutableStateOf(false) }
                                TextButton(onClick = { showFontMenu = true }) {
                                    Icon(Icons.Rounded.FontDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = onBgColor)
                                    Spacer(Modifier.width(8.dp))
                                    Text(fontStyle.replace("_", " "), color = onBgColor, fontWeight = FontWeight.Bold)
                                }
                                DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                                    DropdownMenuItem(text = { Text("Sans Serif") }, onClick = { fontStyle = "SANS_SERIF"; showFontMenu = false })
                                    DropdownMenuItem(text = { Text("Serif") }, onClick = { fontStyle = "SERIF"; showFontMenu = false })
                                    DropdownMenuItem(text = { Text("Monospace") }, onClick = { fontStyle = "MONOSPACE"; showFontMenu = false })
                                }
                            }
                        }
                    }

                    // Color Picker
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
                                    .size(44.dp)
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
                                    Icon(Icons.Rounded.Check, null, tint = onBgColor, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Start typing your thoughts...", style = MaterialTheme.typography.bodyLarge, color = onBgColor.copy(alpha = 0.3f)) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = when(fontStyle) {
                                "SERIF" -> FontFamily.Serif
                                "MONOSPACE" -> FontFamily.Monospace
                                else -> FontFamily.Default
                            },
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                            color = onBgColor,
                            lineHeight = 28.sp
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
    }
}

private fun isDark(color: Color): Boolean {
    val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return darkness >= 0.5
}
