package com.frerox.toolz.ui.screens.time

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerState(
    val remainingTime: Long = 0L,
    val initialTime: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerState())
    val uiState: StateFlow<TimerState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    fun setTimer(minutes: Int, seconds: Int) {
        val totalMillis = (minutes * 60 + seconds) * 1000L
        _uiState.update { it.copy(remainingTime = totalMillis, initialTime = totalMillis, isFinished = false) }
    }

    fun toggleStartStop() {
        if (_uiState.value.isRunning) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        if (_uiState.value.remainingTime <= 0) return
        
        _uiState.update { it.copy(isRunning = true, isFinished = false) }
        timerJob = viewModelScope.launch {
            while (_uiState.value.remainingTime > 0) {
                delay(100)
                _uiState.update { it.copy(remainingTime = (it.remainingTime - 100).coerceAtLeast(0)) }
            }
            onTimerFinished()
        }
    }

    private fun onTimerFinished() {
        _uiState.update { it.copy(isRunning = false, isFinished = true) }
        playRingtone()
    }

    private fun playRingtone() {
        viewModelScope.launch {
            val ringtoneUriStr = settingsRepository.ringtoneUri.first()
            val uri = if (ringtoneUriStr != null) Uri.parse(ringtoneUriStr) else null
            
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    if (uri != null) {
                        setDataSource(context, uri)
                    } else {
                        // Fallback to a system default or a resource
                        // val assetFileDescriptor = context.resources.openRawResourceFd(R.raw.timer_end)
                        // setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                        // For now, let's use a standard notification sound if possible, or just skip if no file
                        return@launch
                    }
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRingtone() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun pause() {
        _uiState.update { it.copy(isRunning = false) }
        timerJob?.cancel()
    }

    fun reset() {
        pause()
        stopRingtone()
        _uiState.update { it.copy(remainingTime = it.initialTime, isFinished = false) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        mediaPlayer?.release()
    }
}
