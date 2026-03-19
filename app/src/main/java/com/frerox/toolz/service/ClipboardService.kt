package com.frerox.toolz.service

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val TAG = "ClipboardService"

@AndroidEntryPoint
class ClipboardService : Service() {

    @Inject lateinit var clipboardDao: ClipboardDao
    @Inject lateinit var classifier: ClipboardClassifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clipboardManager: ClipboardManager? = null
    
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        serviceScope.launch {
            try {
                val clip = clipboardManager?.primaryClip ?: return@launch
                if (clip.itemCount == 0) return@launch
                val item = clip.getItemAt(0)
                val text = item?.coerceToText(this@ClipboardService)?.toString() ?: return@launch
                if (text.isBlank()) return@launch
                
                // Avoid duplicate of the last entry
                val latest = clipboardDao.getLatestEntry()
                if (latest?.content == text) return@launch
                
                Log.d(TAG, "New clipboard content detected")
                val type = classifier.classify(text)
                val entry = ClipboardEntry(
                    content   = text,
                    timestamp = System.currentTimeMillis(),
                    type      = type
                )
                clipboardDao.insert(entry)
                
                // Maintenance
                val count = clipboardDao.getEntryCount()
                if (count > MAX_ENTRIES) {
                    clipboardDao.deleteOldestUnpinned(count - MAX_ENTRIES)
                }
                
                val expiry = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                clipboardDao.deleteOlderThan(expiry)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing clipboard change", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service creating")
        createNotificationChannel()
        
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroying")
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Manager",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors clipboard for history tracking"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "clipboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Active")
            .setContentText("Smart history tracking is running")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "clipboard_service_channel"
        const val NOTIFICATION_ID = 3001
        const val MAX_ENTRIES = 150
    }
}
