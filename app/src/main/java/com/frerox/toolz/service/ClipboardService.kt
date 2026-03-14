package com.frerox.toolz.service

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

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
                val text = clip.getItemAt(0)?.coerceToText(this@ClipboardService)?.toString() ?: return@launch
                if (text.isBlank()) return@launch
                
                // Avoid duplicate of the last entry
                val latest = clipboardDao.getLatestEntry()
                if (latest?.content == text) return@launch
                
                val type = classifier.classify(text)
                val entry = ClipboardEntry(
                    content = text,
                    timestamp = System.currentTimeMillis(),
                    type = type
                )
                clipboardDao.insert(entry)
                
                // Enforce limits: max 100 items
                val count = clipboardDao.getEntryCount()
                if (count > MAX_ENTRIES) {
                    clipboardDao.deleteOldestUnpinned(count - MAX_ENTRIES)
                }
                
                // Enforce limits: 7-day expiry
                val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                clipboardDao.deleteOlderThan(sevenDaysAgo)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Manager",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Monitors clipboard for smart history tracking"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "clipboard")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Manager")
            .setContentText("Monitoring clipboard activity")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "clipboard_service_channel"
        const val NOTIFICATION_ID = 3001
        const val MAX_ENTRIES = 100
    }
}
