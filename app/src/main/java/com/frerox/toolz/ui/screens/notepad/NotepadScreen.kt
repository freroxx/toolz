package com.frerox.toolz.ui.screens.notepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Toolz Notepad", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { 
                    noteToEdit = null
                    showEditor = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Note", modifier = Modifier.size(32.dp))
            }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text("No notes found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {
                items(notes) { note ->
                    StickyNoteItem(
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
fun StickyNoteItem(
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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = noteColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = onNoteColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                if (note.isPinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, modifier = Modifier.size(16.dp), tint = onNoteColor)
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
                    fontStyle = if (note.isItalic) FontStyle.Italic else FontStyle.Normal
                ),
                color = onNoteColor.copy(alpha = 0.8f),
                maxLines = 8
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onTogglePin, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pin",
                        tint = if (note.isPinned) onNoteColor else onNoteColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = onNoteColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
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
    var selectedColor by remember { mutableStateOf(note?.color ?: Color(0xFFFFF9C4).toArgb()) } // Default light yellow
    var fontStyle by remember { mutableStateOf(note?.fontStyle ?: "SANS_SERIF") }
    var isBold by remember { mutableStateOf(note?.isBold ?: false) }
    var isItalic by remember { mutableStateOf(note?.isItalic ?: false) }

    val colors = listOf(
        Color(0xFFFFF9C4), Color(0xFFFFCCBC), Color(0xFFC8E6C9), 
        Color(0xFFB3E5FC), Color(0xFFE1BEE7), Color(0xFFF5F5F5)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                TopAppBar(
                    title = { Text(if (note == null) "New Note" else "Edit Note") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Button(
                            onClick = { onSave(title, content, selectedColor, fontStyle, isBold, isItalic) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Save")
                        }
                    }
                )
                
                Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    
                    // Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isBold = !isBold }) {
                            Icon(Icons.Rounded.FormatBold, contentDescription = "Bold", tint = if (isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { isItalic = !isItalic }) {
                            Icon(Icons.Rounded.FormatItalic, contentDescription = "Italic", tint = if (isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        
                        // Font Style Selector
                        TextButton(onClick = { 
                            fontStyle = when(fontStyle) {
                                "SANS_SERIF" -> "SERIF"
                                "SERIF" -> "MONOSPACE"
                                else -> "SANS_SERIF"
                            }
                        }) {
                            Text(fontStyle.replace("_", " "), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Color Picker
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                        width = if (selectedColor == argb) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Start typing...") },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = when(fontStyle) {
                                "SERIF" -> FontFamily.Serif
                                "MONOSPACE" -> FontFamily.Monospace
                                else -> FontFamily.Default
                            },
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
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
