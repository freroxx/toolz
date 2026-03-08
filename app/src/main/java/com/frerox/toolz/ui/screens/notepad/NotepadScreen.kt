package com.frerox.toolz.ui.screens.notepad

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
            Column {
                TopAppBar(
                    title = { Text("Notepad", fontWeight = FontWeight.ExtraBold) },
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true
                )
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
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Note", modifier = Modifier.size(36.dp))
            }
        }
    ) { padding ->
        if (filteredNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (searchQuery.isEmpty()) Icons.Rounded.Description else Icons.Rounded.SearchOff, 
                        contentDescription = null, 
                        modifier = Modifier.size(100.dp), 
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isEmpty()) "Your thoughts belong here" else "No matching notes", 
                        style = MaterialTheme.typography.bodyLarge, 
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = noteColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = note.title.ifEmpty { "Untitled" }, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = onNoteColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.isPinned) {
                    Icon(
                        Icons.Rounded.PushPin, 
                        contentDescription = "Pinned", 
                        modifier = Modifier.size(14.dp).padding(start = 4.dp), 
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
                        else -> FontFamily.Default
                    },
                    fontWeight = if (note.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 20.sp
                ),
                color = onNoteColor.copy(alpha = 0.8f),
                maxLines = 10,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onTogglePin,
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pin",
                            tint = if (note.isPinned) onNoteColor else onNoteColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Spacer(Modifier.width(4.dp))
                
                Surface(
                    onClick = onDelete,
                    color = Color.Transparent,
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp)
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
                    title = { Text(if (note == null) "New Note" else "Edit Note", color = onBgColor) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = onBgColor)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { onSave(title, content, selectedColor, fontStyle, isBold, isItalic) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, color = onBgColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                
                Column(modifier = Modifier.padding(24.dp).weight(1f)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium, color = onBgColor.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = onBgColor),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = onBgColor
                        )
                    )
                    
                    // Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                                Text(fontStyle.replace("_", " "), color = onBgColor)
                            }
                            DropdownMenu(expanded = showFontMenu, onDismissRequest = { showFontMenu = false }) {
                                DropdownMenuItem(text = { Text("Sans Serif") }, onClick = { fontStyle = "SANS_SERIF"; showFontMenu = false })
                                DropdownMenuItem(text = { Text("Serif") }, onClick = { fontStyle = "SERIF"; showFontMenu = false })
                                DropdownMenuItem(text = { Text("Monospace") }, onClick = { fontStyle = "MONOSPACE"; showFontMenu = false })
                            }
                        }
                    }

                    // Color Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Write something...", style = MaterialTheme.typography.bodyLarge, color = onBgColor.copy(alpha = 0.5f)) },
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
