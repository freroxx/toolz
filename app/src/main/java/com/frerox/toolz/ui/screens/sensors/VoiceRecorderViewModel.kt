package com.frerox.toolz.ui.screens.sensors

import android.content.Context
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class RecordingState(
    val isRecording: Boolean = false,
    val durationMillis: Long = 0L,
    val recordings: List<File> = emptyList()
)

@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingState())
    val uiState: StateFlow<RecordingState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var currentFile: File? = null

    init {
        loadRecordings()
    }

    private fun loadRecordings() {
        val files = context.getExternalFilesDir("recordings")?.listFiles()?.toList() ?: emptyList()
        _uiState.update { it.copy(recordings = files.sortedByDescending { f -> f.lastModified() }) }
    }

    fun startRecording() {
        val folder = context.getExternalFilesDir("recordings")
        if (folder?.exists() == false) folder.mkdirs()
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentFile = File(folder, "REC_$timeStamp.m4a")

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentFile?.absolutePath)
            prepare()
            start()
        }

        _uiState.update { it.copy(isRecording = true, durationMillis = 0L) }
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (_uiState.value.isRecording) {
                delay(100)
                _uiState.update { it.copy(durationMillis = System.currentTimeMillis() - start) }
            }
        }
    }

    fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        timerJob?.cancel()
        _uiState.update { it.copy(isRecording = false) }
        loadRecordings()
    }

    fun deleteRecording(file: File) {
        if (file.exists()) {
            file.delete()
            loadRecordings()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRecording()
    }
}
