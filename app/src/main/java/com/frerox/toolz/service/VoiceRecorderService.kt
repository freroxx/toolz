package com.frerox.toolz.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.ui.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class VoiceRecorderService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _durationMillis = MutableStateFlow(0L)
    val durationMillis: StateFlow<Long> = _durationMillis

    private val _maxAmplitude = MutableStateFlow(0)
    val maxAmplitude: StateFlow<Int> = _maxAmplitude

    private var gainLevel: Float = 1.0f

    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var currentFile: File? = null
    private var startTime: Long = 0L
    private var pausedTime: Long = 0L

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): VoiceRecorderService = this@VoiceRecorderService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun startRecording() {
        if (_isRecording.value && !_isPaused.value) return
        
        if (_isPaused.value) {
            resumeRecording()
            return
        }

        val folder = getExternalFilesDir("recordings")
        if (folder?.exists() == false) folder.mkdirs()
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentFile = File(folder, "REC_$timeStamp.m4a")

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentFile?.absolutePath)
                prepare()
                start()
            }

            _isRecording.value = true
            _isPaused.value = false
            _durationMillis.value = 0L
            startTime = SystemClock.elapsedRealtime()
            startTimer()
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                _isPaused.value = true
                pausedTime = SystemClock.elapsedRealtime() - startTime
                timerJob?.cancel()
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                _isPaused.value = false
                startTime = SystemClock.elapsedRealtime() - pausedTime
                startTimer()
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_isRecording.value && !_isPaused.value) {
                _durationMillis.value = SystemClock.elapsedRealtime() - startTime
                _maxAmplitude.value = (mediaRecorder?.maxAmplitude ?: 0)
                delay(100)
            }
        }
    }

    fun stopRecording(save: Boolean = true) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        timerJob?.cancel()
        
        if (!save) {
            currentFile?.delete()
        }
        
        _isRecording.value = false
        _isPaused.value = false
        _durationMillis.value = 0L
        _maxAmplitude.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun setGainLevel(level: Float) {
        this.gainLevel = level
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.VoiceRecorder.route)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val toggleAction = if (_isPaused.value) ACTION_RESUME else ACTION_PAUSE
        val toggleIntent = Intent(this, VoiceRecorderService::class.java).apply { action = toggleAction }
        val togglePI = PendingIntent.getService(this, 2, toggleIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, VoiceRecorderService::class.java).apply { action = ACTION_STOP }
        val stopPI = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (_isPaused.value) "Recording Paused" else "Recording Audio...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setColor(0xFFE91E63.toInt())

        if (!_isPaused.value) {
            builder.setUsesChronometer(true)
            builder.setWhen(System.currentTimeMillis() - (_durationMillis.value))
        } else {
            builder.setContentText("Duration: ${formatDuration(_durationMillis.value)}")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.addAction(
                if (_isPaused.value) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (_isPaused.value) "Resume" else "Pause",
                togglePI
            )
        }
        builder.addAction(android.R.drawable.ic_menu_save, "Save", stopPI)

        return builder.build()
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording(true)
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "voice_recorder_channel"
        private const val NOTIFICATION_ID = 3001
        const val ACTION_STOP = "com.frerox.toolz.action.STOP_RECORDING"
        const val ACTION_PAUSE = "com.frerox.toolz.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.frerox.toolz.action.RESUME_RECORDING"
    }
}
