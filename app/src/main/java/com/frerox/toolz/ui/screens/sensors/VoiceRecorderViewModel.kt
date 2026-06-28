package com.frerox.toolz.ui.screens.sensors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.VoiceRecorderService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class RecordingItem(
    val file: File,
    val marks: List<Long> = emptyList()
)

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMillis: Long = 0L,
    val maxAmplitude: Int = 0,
    val recordings: List<RecordingItem> = emptyList(),
    val playingFile: File? = null,
    val isPlaying: Boolean = false,
    val playbackPosition: Int = 0,
    val playbackDuration: Int = 0,
    val gainLevel: Float = 1.0f,
    val isBackgroundEnabled: Boolean = true,
    val availableDevices: List<String> = emptyList(),
    val selectedDevice: String = "Default",
    val marks: List<Long> = emptyList(),
    val customOutputPath: String? = null
)

@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingState())
    val uiState: StateFlow<RecordingState> = _uiState.asStateFlow()

    private var recorderService: VoiceRecorderService? = null
    private var isBound = false
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    
    // In-memory cache for marks of the session's files, keyed by absolute path
    private val sessionMarks = mutableMapOf<String, List<Long>>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceRecorderService.LocalBinder
            recorderService = binder.getService()
            isBound = true
            
            viewModelScope.launch {
                recorderService?.isRecording?.collect { recording ->
                    if (!recording && _uiState.value.isRecording) {
                        // Just stopped recording
                        val marks = _uiState.value.marks
                        val path = recorderService?.currentPath?.value
                        if (path != null) {
                            sessionMarks[path] = marks
                        }
                        _uiState.update { it.copy(isRecording = false, marks = emptyList()) }
                        loadRecordings()
                    } else if (recording && !_uiState.value.isRecording) {
                        _uiState.update { it.copy(isRecording = true, marks = emptyList()) }
                    } else {
                        _uiState.update { it.copy(isRecording = recording) }
                    }
                }
            }
            viewModelScope.launch {
                recorderService?.isPaused?.collect { paused ->
                    _uiState.update { it.copy(isPaused = paused) }
                }
            }
            viewModelScope.launch {
                recorderService?.durationMillis?.collect { duration ->
                    _uiState.update { it.copy(durationMillis = duration) }
                }
            }
            viewModelScope.launch {
                recorderService?.maxAmplitude?.collect { amplitude ->
                    _uiState.update { it.copy(maxAmplitude = (amplitude * _uiState.value.gainLevel).toInt()) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recorderService = null
            isBound = false
        }
    }

    init {
        Intent(context, VoiceRecorderService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        
        viewModelScope.launch {
            settingsRepository.converterCustomOutputPath.collect { path ->
                _uiState.update { it.copy(customOutputPath = path) }
                loadRecordings()
            }
        }
        discoverDevices()
    }

    private fun discoverDevices() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.productName.toString() }
        } else {
            listOf("Default Microphone")
        }
        _uiState.update { it.copy(availableDevices = devices.distinct()) }
    }

    fun setGainLevel(level: Float) {
        _uiState.update { it.copy(gainLevel = level) }
        recorderService?.setGainLevel(level)
    }

    fun setBackgroundEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isBackgroundEnabled = enabled) }
    }

    fun setSelectedDevice(device: String) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    private fun loadRecordings() {
        val files = mutableListOf<File>()
        
        // Load from default internal recordings folder
        val internalFolder = context.getExternalFilesDir("recordings")
        internalFolder?.listFiles()?.let { files.addAll(it) }
        
        // Load from custom output folder if configured
        _uiState.value.customOutputPath?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                documentFile?.listFiles()?.forEach { doc ->
                    if (doc.isFile) {
                        // Attempt to get a File reference, though SAF uris might not always map easily
                        // For display purposes, we might need a more robust model than java.io.File
                        // but let's try to filter for audio extensions at least
                        val name = doc.name ?: ""
                        if (isAudioFile(name)) {
                            // This is a placeholder since SAF files aren't always java.io.Files
                            // However, we'll keep the File list for now and see if we can adapt
                        }
                    }
                }
                
                // Alternative: if it's a file path
                val folder = File(uriString)
                if (folder.exists() && folder.isDirectory) {
                    folder.listFiles()?.let { files.addAll(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val audioExtensions = setOf("m4a", "3gp", "mp3", "wav", "ogg", "opus", "aac", "flac")
        val filtered = files.filter { it.extension.lowercase() in audioExtensions }
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.lastModified() }
            .map { RecordingItem(it, sessionMarks[it.absolutePath] ?: emptyList()) }
            
        _uiState.update { it.copy(recordings = filtered) }
    }

    private fun isAudioFile(name: String): Boolean {
        val audioExtensions = setOf("m4a", "3gp", "mp3", "wav", "ogg", "opus", "aac", "flac")
        return audioExtensions.any { name.lowercase().endsWith(".$it") }
    }

    fun startRecording() {
        stopPlayback()
        recorderService?.startRecording()
    }

    fun pauseRecording() {
        recorderService?.pauseRecording()
    }

    fun resumeRecording() {
        recorderService?.resumeRecording()
    }

    fun stopRecording(save: Boolean = true) {
        recorderService?.stopRecording(save)
        // Note: Marks are handled in the collector to ensure they are associated with the file path
    }

    fun addMark() {
        if (_uiState.value.isRecording) {
            val currentMark = _uiState.value.durationMillis
            _uiState.update { it.copy(marks = it.marks + currentMark) }
        }
    }

    fun togglePlayback(file: File) {
        if (_uiState.value.playingFile == file && _uiState.value.isPlaying) {
            pausePlayback()
        } else if (_uiState.value.playingFile == file && !_uiState.value.isPlaying) {
            resumePlayback()
        } else {
            startPlayback(file)
        }
    }

    private fun startPlayback(file: File) {
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
                _uiState.update { 
                    it.copy(
                        playingFile = file, 
                        isPlaying = true, 
                        playbackDuration = duration,
                        playbackPosition = 0
                    ) 
                }
                startPlaybackTimer()
                setOnCompletionListener {
                    stopPlayback()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        _uiState.update { it.copy(isPlaying = false) }
        playbackJob?.cancel()
    }

    private fun resumePlayback() {
        mediaPlayer?.start()
        _uiState.update { it.copy(isPlaying = true) }
        startPlaybackTimer()
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false, playingFile = null, playbackPosition = 0) }
    }

    private fun startPlaybackTimer() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (_uiState.value.isPlaying) {
                _uiState.update { it.copy(playbackPosition = mediaPlayer?.currentPosition ?: 0) }
                delay(100)
            }
        }
    }

    fun deleteRecording(file: File) {
        if (_uiState.value.playingFile == file) {
            stopPlayback()
        }
        if (file.exists()) {
            file.delete()
            loadRecordings()
        }
    }

    fun renameRecording(file: File, newName: String) {
        if (newName.isBlank()) return
        val extension = file.extension
        val folder = file.parentFile ?: return
        val newFile = File(folder, "$newName.$extension")
        
        if (file.renameTo(newFile)) {
            if (_uiState.value.playingFile == file) {
                _uiState.update { it.copy(playingFile = newFile) }
            }
            loadRecordings()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
        }
        stopPlayback()
    }
}
