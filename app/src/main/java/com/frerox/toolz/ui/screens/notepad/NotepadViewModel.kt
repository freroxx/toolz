package com.frerox.toolz.ui.screens.notepad

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotepadViewModel @Inject constructor(
    private val noteDao: NoteDao
) : ViewModel() {

    val notes: StateFlow<List<Note>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addNote(
        title: String,
        content: String,
        color: Int,
        fontStyle: String = "SANS_SERIF",
        isBold: Boolean = false,
        isItalic: Boolean = false
    ) {
        viewModelScope.launch {
            noteDao.insertNote(
                Note(
                    title = title,
                    content = content,
                    color = color,
                    fontStyle = fontStyle,
                    isBold = isBold,
                    isItalic = isItalic
                )
            )
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            noteDao.insertNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            noteDao.updatePinned(note.id, !note.isPinned)
        }
    }
}
