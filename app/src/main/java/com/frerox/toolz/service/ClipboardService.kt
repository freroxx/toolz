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
import com.frerox.toolz.R
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
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
        checkClipboard()
    }

    private fun checkClipboard() {
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
                    type      = initialType,
                    isAiProcessed = false
                )
                val id = clipboardDao.insert(entry).toInt()
                
                // Background AI processing
                processWithAi(id, text, initialType)
                
                cleanupOldEntries()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing clipboard change", e)
            }
        }
    }

    private suspend fun cleanupOldEntries() {
        val count = clipboardDao.getEntryCount()
        if (count > MAX_ENTRIES) {
            clipboardDao.deleteOldestUnpinned(count - MAX_ENTRIES)
        }
        val expiry = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        clipboardDao.deleteOlderThan(expiry)
    }

    private fun processWithAi(id: Int, text: String, currentType: String) {
        serviceScope.launch {
            try {
                val prompt = """
                    Classify this clipboard content. Be smart and specific. 
                    You can use standard categories (TEXT, URL, PHONE, EMAIL, MATHS, CODE, ADDRESS, CRYPTO, TODO) 
                    or CREATE A NEW ONE if it fits better (e.g., RECIPE, FLIGHT, PACKAGE, EVENT, QUOTE, etc.).
                    Keep category names uppercase and single-word if possible.
                    
                    Current guess: $currentType
                    
                    If the text is over 30 words or contains complex info, provide a punchy 1-sentence summary (max 15 words).
                    If it's short, summary should be null.
                    
                    Content: ${text.take(2000)}
                    
                    Respond ONLY in JSON format: {"category": "CATEGORY_NAME", "summary": "optional summary string or null"}
                """.trimIndent()

                aiRepository.getChatResponse(prompt, emptyList(), null, "llama-3.3-70b-versatile").collect { result ->
                    result.onSuccess { responseChunk ->
                        try {
                            val response = responseChunk.text
                            val category = Regex("\"category\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1) ?: currentType
                            val rawSummary = Regex("\"summary\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1)
                            val summary = if (rawSummary == "null" || rawSummary.isNullOrBlank()) null else rawSummary
                            
                            clipboardDao.updateAiDetails(id, summary, category)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing AI response", e)
                            val entry = clipboardDao.getEntryById(id)
                            entry?.let { clipboardDao.update(it.copy(isAiProcessed = true)) }
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
        NotificationHelper.createAllChannels(this)
        
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.ID_CLIPBOARD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NotificationHelper.ID_CLIPBOARD, notification)
        }
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
        
        startPeriodicCheck()
    }
    
    private fun startPeriodicCheck() {
        serviceScope.launch {
            while (isActive) {
                checkClipboard() 
                try {
                    val unprocessed = clipboardDao.getUnprocessedEntries()
                    unprocessed.take(2).forEach { entry ->
                        processWithAi(entry.id, entry.content, entry.type)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic check failed", e)
                }
                delay(30000) // 30 seconds poll
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK_CLIPBOARD) {
            checkClipboard()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        return NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_CLIPBOARD)
            .setContentTitle("Clipboard Monitoring")
            .setContentText("AI Background Processing Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_CHECK_CLIPBOARD = "com.frerox.toolz.action.CHECK_CLIPBOARD"
        const val MAX_ENTRIES = 150
    }
}
