package com.frerox.toolz.ui.screens.notepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.pdf.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotepadViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val musicRepository: MusicRepository,
    private val pdfRepository: PdfRepository
) : ViewModel() {

    val notes: StateFlow<List<Note>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTracks = musicRepository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _availablePdfs = MutableStateFlow<List<com.frerox.toolz.data.pdf.PdfFile>>(emptyList())
    val availablePdfs = _availablePdfs.asStateFlow()

    init {
        loadPdfs()
    }

    private fun loadPdfs() {
        viewModelScope.launch {
            _availablePdfs.value = pdfRepository.getPdfFiles()
        }
    }

    fun addNote(
        title: String,
        content: String,
        color: Int,
        fontStyle: String = "SANS_SERIF",
        fontSize: Float = 16f,
        isBold: Boolean = false,
        isItalic: Boolean = false,
        attachedPdfUri: String? = null,
        attachedAudioUri: String? = null,
        attachedAudioName: String? = null
    ) {
        viewModelScope.launch {
            noteDao.insertNote(
                Note(
                    title = title,
                    content = content,
                    color = color,
                    fontStyle = fontStyle,
                    fontSize = fontSize,
                    isBold = isBold,
                    isItalic = isItalic,
                    attachedPdfUri = attachedPdfUri,
                    attachedAudioUri = attachedAudioUri,
                    attachedAudioName = attachedAudioName
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
