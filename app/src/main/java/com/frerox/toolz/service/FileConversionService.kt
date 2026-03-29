package com.frerox.toolz.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@AndroidEntryPoint
class FileConversionService : Service() {

    @Inject
    lateinit var conversionEngine: ConversionEngine

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val NOTIFICATION_ID = 8888
    private val CHANNEL_ID = "file_conversion_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputUri = intent?.getParcelableExtra<Uri>("input_uri")
        val typeString = intent?.getStringExtra("conversion_type")
        val highQuality = intent?.getBooleanExtra("high_quality", true) ?: true
        val type = try {
            ConversionEngine.ConversionType.valueOf(typeString ?: "")
        } catch (e: Exception) {
            null
        }

        if (inputUri != null && type != null) {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("Preparing conversion...", 0))
            performConversion(inputUri, type, highQuality)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun performConversion(inputUri: Uri, type: ConversionEngine.ConversionType, highQuality: Boolean) {
        conversionEngine.convertFile(inputUri, type, highQuality)
            .onEach { status ->
                when (status) {
                    is ConversionEngine.ConversionStatus.Progress -> {
                        val progressText = if (status.percentage >= 0) {
                            "Converting to ${type.extension.uppercase()}... ${status.percentage}%"
                        } else {
                            "Processing file..."
                        }
                        updateNotification(progressText, status.percentage)
                        
                        sendBroadcast(Intent("COM_FREROX_TOOLZ_CONVERSION_PROGRESS").apply {
                            putExtra("progress", status.percentage)
                            setPackage(packageName)
                        })
                    }
                    is ConversionEngine.ConversionStatus.Success -> {
                        updateNotification("Conversion complete!", 100, isFinished = true)
                        sendBroadcast(Intent("COM_FREROX_TOOLZ_CONVERSION_SUCCESS").apply {
                            putExtra("output_path", status.outputPath)
                            setPackage(packageName)
                        })
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                    is ConversionEngine.ConversionStatus.Error -> {
                        updateNotification("Error: ${status.message}", 0, isFinished = true)
                        sendBroadcast(Intent("COM_FREROX_TOOLZ_CONVERSION_ERROR").apply {
                            putExtra("error_message", status.message)
                            setPackage(packageName)
                        })
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                }
            }
            .launchIn(serviceScope)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Conversion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of file conversions"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String, progress: Int, isFinished: Boolean = false): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Toolz File Converter")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!isFinished)
            .setContentIntent(pendingIntent)
            .setAutoCancel(isFinished)

        if (!isFinished && progress >= 0) {
            builder.setProgress(100, progress, false)
        } else if (!isFinished && progress < 0) {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(content: String, progress: Int, isFinished: Boolean = false) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content, progress, isFinished))
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
