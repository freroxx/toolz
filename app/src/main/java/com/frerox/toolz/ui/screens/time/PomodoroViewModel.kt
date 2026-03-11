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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PomodoroMode(val minutes: Int) {
    WORK(25), SHORT_BREAK(5), LONG_BREAK(15)
}

data class PomodoroState(
    val remainingTime: Long = PomodoroMode.WORK.minutes * 60 * 1000L,
    val mode: PomodoroMode = PomodoroMode.WORK,
    val isRunning: Boolean = false,
    val sessionsCompleted: Int = 0,
    val isFinished: Boolean = false
)

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PomodoroState())
    val uiState: StateFlow<PomodoroState> = _uiState.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false
    private var mediaPlayer: MediaPlayer? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            
            viewModelScope.launch {
                toolService?.pomodoroRemaining?.collect { remaining ->
                    _uiState.update { it.copy(remainingTime = remaining) }
                    if (remaining == 0L && _uiState.value.isRunning) {
                        onSessionComplete()
                    }
                }
            }

            viewModelScope.launch {
                toolService?.isPomodoroRunning?.collect { running ->
                    _uiState.update { it.copy(isRunning = running) }
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

    fun toggleStartStop() {
        val currentlyRunning = _uiState.value.isRunning
        if (currentlyRunning) {
            toolService?.pausePomodoro()
        } else {
            toolService?.startPomodoro(_uiState.value.remainingTime, _uiState.value.mode.name)
        }
    }

    private fun onSessionComplete() {
        _uiState.update {
            val newSessions = if (it.mode == PomodoroMode.WORK) it.sessionsCompleted + 1 else it.sessionsCompleted
            val nextMode = when {
                it.mode == PomodoroMode.WORK && newSessions % 4 == 0 -> PomodoroMode.LONG_BREAK
                it.mode == PomodoroMode.WORK -> PomodoroMode.SHORT_BREAK
                else -> PomodoroMode.WORK
            }
            it.copy(
                mode = nextMode,
                remainingTime = nextMode.minutes * 60 * 1000L,
                isRunning = false,
                sessionsCompleted = newSessions,
                isFinished = true
            )
        }
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
        toolService?.resetPomodoro()
        stopRingtone()
        _uiState.update { 
            it.copy(
                remainingTime = it.mode.minutes * 60 * 1000L, 
                isRunning = false,
                isFinished = false
            ) 
        }
    }

    fun skip() {
        toolService?.pausePomodoro()
        stopRingtone()
        onSessionComplete()
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
        }
        mediaPlayer?.release()
    }
}
