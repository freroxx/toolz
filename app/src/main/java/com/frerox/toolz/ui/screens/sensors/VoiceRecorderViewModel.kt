package com.frerox.toolz.ui.screens.sensors

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.service.VoiceRecorderService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMillis: Long = 0L,
    val maxAmplitude: Int = 0,
    val recordings: List<File> = emptyList(),
    val playingFile: File? = null,
    val isPlaying: Boolean = false,
    val playbackPosition: Int = 0,
    val playbackDuration: Int = 0,
    val gainLevel: Float = 1.0f,
    val isBackgroundEnabled: Boolean = true,
    val availableDevices: List<String> = emptyList(),
    val selectedDevice: String = "Default"
)

@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingState())
    val uiState: StateFlow<RecordingState> = _uiState.asStateFlow()

    private var recorderService: VoiceRecorderService? = null
    private var isBound = false
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VoiceRecorderService.LocalBinder
            recorderService = binder.getService()
            isBound = true
            
            viewModelScope.launch {
                recorderService?.isRecording?.collect { recording ->
                    _uiState.update { it.copy(isRecording = recording) }
                    if (!recording) loadRecordings()
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
        loadRecordings()
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
        val folder = context.getExternalFilesDir("recordings")
        val files = folder?.listFiles()?.toList() ?: emptyList()
        _uiState.update { it.copy(recordings = files.filter { f -> f.extension == "m4a" || f.extension == "3gp" }.sortedByDescending { f -> f.lastModified() }) }
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
