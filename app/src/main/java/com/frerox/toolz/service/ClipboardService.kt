/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import com.frerox.toolz.ToolzApplication
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.util.shizuku.ShizukuHelper
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
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var shizukuExecutor: com.frerox.toolz.util.shizuku.ShizukuShellExecutor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clipboardManager: ClipboardManager? = null
    private var isAiMonitoringEnabled = true

    /**
     * Clipboard change listener — fires for ALL system-wide copy events, even in background.
     *
     * Strategy:
     *  1. If Shizuku is authorized → read via ADB shell (works in background, no focus check needed).
     *  2. Otherwise → only attempt standard ClipboardManager API if app is actually in focus.
     *     Trying to read primaryClip without focus causes Android to deny access and log an error.
     *
     * The AccessibilityService (FocusFlowAccessibilityService) is the fallback for case 2:
     * it triggers a clipboard check the moment Toolz gains focus, capturing any missed copies.
     */
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        serviceScope.launch {
            if (ShizukuHelper.isAuthorized()) {
                // Shizuku path — safe to call from background, no focus requirement
                readClipboardViaShizuku()
            } else if (ToolzApplication.isFocused.value) {
                // Standard path — only safe when app is in focus
                readClipboardViaStandardApi()
            }
            // If neither: AccessibilityService will trigger a check when Toolz regains focus
        }
    }

    private suspend fun readClipboardViaShizuku() {
        try {
            val text = shizukuExecutor.getClipboardText()
            if (text != null) {
                processClipboardText(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku clipboard read failed", e)
        }
    }

    private fun readClipboardViaStandardApi() {
        try {
            val clip = clipboardManager?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val item = clip.getItemAt(0)
            val text = item?.coerceToText(this@ClipboardService)?.toString() ?: return
            serviceScope.launch { processClipboardText(text) }
        } catch (e: Exception) {
            // Silently swallow — this can still race during focus transitions
            Log.w(TAG, "Standard clipboard read denied or failed: ${e.message}")
        }
    }

    /**
     * Called by AccessibilityService or onStartCommand when app gains focus.
     * Tries Shizuku first, then standard API (since we know we're in focus here).
     */
    fun checkClipboard() {
        serviceScope.launch {
            if (ShizukuHelper.isAuthorized()) {
                readClipboardViaShizuku()
            } else {
                readClipboardViaStandardApi()
            }
        }
    }

    private fun processClipboardText(text: String) {
        if (text.isBlank()) return
        serviceScope.launch {
            try {
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
            if (!settingsRepository.aiClipboardMonitoringEnabled.first()) return@launch
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
        
        serviceScope.launch {
            settingsRepository.aiClipboardMonitoringEnabled.collect { enabled ->
                isAiMonitoringEnabled = enabled
                updateNotification()
            }
        }

        startPeriodicCheck()
    }
    
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NotificationHelper.ID_CLIPBOARD, createNotification())
    }
    
    private fun startPeriodicCheck() {
        serviceScope.launch {
            while (isActive) {
                val shizukuActive = ShizukuHelper.isAuthorized()

                // Shizuku backup read — the listener handles most cases, but poll as a safety net
                // in case a copy happened while the listener binder was briefly disconnected.
                if (shizukuActive) {
                    readClipboardViaShizuku()
                }

                // AI reprocessing for any entries that missed AI classification
                try {
                    val unprocessed = clipboardDao.getUnprocessedEntries()
                    unprocessed.take(2).forEach { entry ->
                        processWithAi(entry.id, entry.content, entry.type)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic AI reprocessing failed", e)
                }

                delay(if (shizukuActive) 5000L else 30000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK_CLIPBOARD) {
            val externalText = intent.getStringExtra(EXTRA_CLIPBOARD_TEXT)
            if (externalText != null) {
                processClipboardText(externalText)
            } else {
                checkClipboard()
            }
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
            .setContentText(if (isAiMonitoringEnabled) "AI Background Processing Active" else "AI Background Processing Disabled")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_CHECK_CLIPBOARD = "com.frerox.toolz.action.CHECK_CLIPBOARD"
        const val EXTRA_CLIPBOARD_TEXT = "extra_clipboard_text"
        const val MAX_ENTRIES = 150
    }
}
