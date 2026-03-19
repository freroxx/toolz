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
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val TAG = "ClipboardService"

@AndroidEntryPoint
class ClipboardService : Service() {

    @Inject lateinit var clipboardDao: ClipboardDao
    @Inject lateinit var classifier: ClipboardClassifier
    @Inject lateinit var aiRepository: ChatRepository

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
                val initialType = classifier.classify(text)
                
                val entry = ClipboardEntry(
                    content   = text,
                    timestamp = System.currentTimeMillis(),
                    type      = initialType
                )
                val id = clipboardDao.insert(entry).toInt()
                
                // Background AI processing for better category and optional summary if it's long
                processWithAi(id, text, initialType)
                
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

    private fun processWithAi(id: Int, text: String, currentType: String) {
        serviceScope.launch {
            try {
                // If it's already a very specific type like COLOR or OTP, maybe skip AI categorization to save tokens
                if (currentType == "COLOR" || currentType == "OTP") return@launch

                val prompt = """
                    Classify this clipboard content into one of these categories: 
                    TEXT, URL, PHONE, EMAIL, MATHS, PERSONAL, CODE, ADDRESS, CRYPTO, TODO.
                    If the text is long (over 100 words), also provide a 1-sentence summary.
                    
                    Content: ${text.take(1000)}
                    
                    Respond in JSON format: {"category": "CATEGORY", "summary": "optional summary or null"}
                """.trimIndent()

                aiRepository.getChatResponse(prompt, emptyList(), null).collect { result ->
                    result.onSuccess { response ->
                        // Simple JSON parsing from response
                        val category = Regex("\"category\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1) ?: currentType
                        val summary = Regex("\"summary\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1)
                        
                        if (summary != null && summary != "null") {
                            clipboardDao.updateAiDetails(id, summary, category)
                        } else {
                            // Just update category if no summary
                            val entry = clipboardDao.getEntryById(id)
                            entry?.let {
                                clipboardDao.update(it.copy(type = category))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI Background processing failed", e)
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
