package com.frerox.toolz.ui.screens.time

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerState(
    val remainingTime: Long = 0L,
    val initialTime: Long = 0L,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val isPaused: Boolean = false
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerState())
    val uiState: StateFlow<TimerState> = _uiState.asStateFlow()

    val hapticEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var toolService: ToolService? = null
    private var isBound = false
    private var mediaPlayer: MediaPlayer? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            
            viewModelScope.launch {
                toolService?.timerRemaining?.collect { remaining ->
                    _uiState.update { it.copy(remainingTime = remaining) }
                    if (remaining == 0L && _uiState.value.isRunning) {
                        onTimerFinished()
                    }
                }
            }
            
            viewModelScope.launch {
                toolService?.isTimerRunning?.collect { running ->
                    _uiState.update { it.copy(isRunning = running, isPaused = !running && it.remainingTime > 0) }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            toolService = null
            isBound = false
        }
    }

    init {
        Intent(context, ToolService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun setTimer(minutes: Int, seconds: Int) {
        val totalMillis = (minutes * 60 + seconds) * 1000L
        _uiState.update { it.copy(remainingTime = totalMillis, initialTime = totalMillis, isFinished = false, isPaused = false) }
    }

    fun addTime(millis: Long) {
        val current = _uiState.value.remainingTime
        val newTotal = current + millis
        _uiState.update { it.copy(remainingTime = newTotal, initialTime = if (it.isRunning) it.initialTime + millis else newTotal) }
        if (_uiState.value.isRunning) {
            toolService?.startTimer(newTotal)
        }
    }

    fun toggleStartStop() {
        val currentlyRunning = _uiState.value.isRunning
        if (currentlyRunning) {
            toolService?.pauseTimer()
        } else {
            if (_uiState.value.remainingTime > 0) {
                toolService?.startTimer(_uiState.value.remainingTime)
                _uiState.update { it.copy(isFinished = false) }
            }
        }
    }

    fun toggleHaptic() {
        viewModelScope.launch {
            settingsRepository.setHapticFeedback(!hapticEnabled.value)
        }
    }

    private fun onTimerFinished() {
        _uiState.update { it.copy(isRunning = false, isFinished = true, isPaused = false) }
        playRingtone()
    }

    private fun playRingtone() {
        viewModelScope.launch {
            val ringtoneUriStr = settingsRepository.ringtoneUri.first()
            val uri = if (!ringtoneUriStr.isNullOrEmpty()) {
                Uri.parse(ringtoneUriStr)
            } else {
                Settings.System.DEFAULT_NOTIFICATION_URI
            }
            
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
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

    fun reset() {
        toolService?.resetTimer()
        stopRingtone()
        _uiState.update { it.copy(remainingTime = 0, initialTime = 0, isRunning = false, isFinished = false, isPaused = false) }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
        }
        mediaPlayer?.release()
    }
}
