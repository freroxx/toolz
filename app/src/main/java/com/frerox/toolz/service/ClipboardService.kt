/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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
import com.frerox.toolz.ToolzForegroundTracker
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.clipboard.ClipboardCaptureProcessor
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.util.shizuku.ShizukuHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val TAG = "ClipboardService"

@AndroidEntryPoint
class ClipboardService : Service() {

    @Inject lateinit var clipboardDao: ClipboardDao
    @Inject lateinit var classifier: ClipboardClassifier
    @Inject lateinit var captureProcessor: ClipboardCaptureProcessor
    @Inject lateinit var aiRepository: ChatRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var shizukuExecutor: com.frerox.toolz.util.shizuku.ShizukuShellExecutor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clipboardManager: ClipboardManager? = null
    private var isAiMonitoringEnabled = false
    private var monitoringEnabled = true
    private var excludeSensitive = false
    private var gateStatus: ClipboardGate.Status = ClipboardGate.Status.SETUP_REQUIRED

    /**
     * Clipboard change listener — fires for system-wide copy events.
     *
     * Android 10+ only delivers readable clips when:
     *  - Shizuku path (ADB shell, no focus requirement), or
     *  - app is in foreground (tracked via ToolzForegroundTracker).
     * Otherwise the event is honestly ignored; the accessibility service
     * re-triggers a check when Toolz regains focus.
     */
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        serviceScope.launch {
            if (!monitoringEnabled) return@launch
            if (gateStatus == ClipboardGate.Status.SETUP_REQUIRED ||
                gateStatus == ClipboardGate.Status.PAUSED
            ) return@launch
            if (ShizukuHelper.isAuthorized()) {
                readClipboardViaShizuku("listener")
            } else if (ToolzForegroundTracker.isForeground.value ||
                ToolzApplication.isFocused.value
            ) {
                readClipboardViaStandardApi()
            }
        }
    }

    private suspend fun readClipboardViaShizuku(source: String) {
        try {
            val text = shizukuExecutor.getClipboardText() ?: return
            processClipboardText(text, "shizuku-$source")
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku clipboard read failed", e)
        }
    }

    private fun readClipboardViaStandardApi(source: String = "foreground") {
        try {
            val clip = clipboardManager?.primaryClip ?: return
            if (clip.itemCount == 0) return
            // Skip non-text clips gracefully (images, intents, html) — V1 stores text only.
            val item = clip.getItemAt(0) ?: return
            if (item.uri != null && item.text == null && item.htmlText == null) {
                Log.d(TAG, "Skipping non-text clip (uri)")
                return
            }
            val text = item.coerceToText(this@ClipboardService)?.toString() ?: return
            serviceScope.launch { processClipboardText(text, source) }
        } catch (e: Exception) {
            Log.w(TAG, "Standard clipboard read denied or failed: ${e.message}")
        }
    }

    /**
     * Called by AccessibilityService or onStartCommand when app gains focus.
     */
    fun checkClipboard() {
        serviceScope.launch {
            if (!monitoringEnabled) return@launch
            if (ShizukuHelper.isAuthorized()) {
                readClipboardViaShizuku("check")
            } else {
                readClipboardViaStandardApi("check")
            }
        }
    }

    private fun processClipboardText(text: String, source: String) {
        if (text.isBlank()) return
        serviceScope.launch {
            try {
                val outcome = captureProcessor.processText(text, source, excludeSensitive)
                if (outcome == ClipboardCaptureProcessor.Outcome.IGNORED_SENSITIVE) {
                    Log.d(TAG, "Sensitive clip excluded by preference")
                    return@launch
                }
                if (outcome == ClipboardCaptureProcessor.Outcome.IGNORED_BLANK) return@launch
                Log.d(TAG, "Clipboard captured ($outcome) via $source")
                enforceRetention()

                // Queue AI enrichment only when BOTH toggles allow it.
                val latest = clipboardDao.getLatestEntry() ?: return@launch
                maybeProcessWithAi(latest.id, latest.content, latest.type)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing clipboard change", e)
            }
        }
    }

    private suspend fun enforceRetention() {
        try {
            val retention = settingsRepository.clipboardRetentionDays.first()
            if (retention > 0) {
                val expiry = System.currentTimeMillis() - retention * 24L * 60 * 60 * 1000
                clipboardDao.deleteOlderThan(expiry)
            }
            val count = clipboardDao.getEntryCount()
            if (count > MAX_ENTRIES) {
                clipboardDao.deleteOldestUnpinned(count - MAX_ENTRIES)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Retention enforcement failed", e)
        }
    }

    private suspend fun canAutoAi(): Boolean {
        if (!isAiMonitoringEnabled) return false
        return try {
            if (!settingsRepository.clipboardAiEnabled.first()) return false
            if (settingsRepository.offlineModeEnabled.first()) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun maybeProcessWithAi(id: Int, text: String, currentType: String) {
        serviceScope.launch {
            if (!canAutoAi()) return@launch
            processWithAi(id, text, currentType)
        }
    }

    private suspend fun processWithAi(id: Int, text: String, currentType: String) {
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

            aiRepository.getChatResponse(prompt, emptyList(), null, "openai/gpt-oss-120b").collect { result ->
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service creating")
        NotificationHelper.createAllChannels(this)

        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationHelper.ID_CLIPBOARD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NotificationHelper.ID_CLIPBOARD, notification)
            }
        } catch (e: Exception) {
            // Android 12+ ForegroundServiceStartNotAllowedException when started from
            // background without exemption — stop honestly instead of crash-looping.
            Log.w(TAG, "Could not start foreground: ${e.message}")
            stopSelf()
            return
        }

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)

        serviceScope.launch {
            combine(
                settingsRepository.clipboardMonitoringEnabled,
                settingsRepository.aiClipboardMonitoringEnabled,
                settingsRepository.clipboardExcludeSensitive,
            ) { monitoring, aiAuto, exclude ->
                Triple(monitoring, aiAuto, exclude)
            }.collect { (monitoring, aiAuto, exclude) ->
                monitoringEnabled = monitoring
                isAiMonitoringEnabled = aiAuto
                excludeSensitive = exclude
                gateStatus = ClipboardGate.evaluate(this@ClipboardService, monitoring)
                updateNotification()
                if (!monitoring || gateStatus == ClipboardGate.Status.SETUP_REQUIRED) {
                    Log.d(TAG, "Monitoring gated: enabled=$monitoring status=$gateStatus — listener idle")
                }
                if (!monitoring) {
                    // Honest stop: no requirement met, release the foreground service.
                    stopSelf()
                }
            }
        }

        startBackfillLoop()
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NotificationHelper.ID_CLIPBOARD, createNotification())
        } catch (_: Exception) {
        }
    }

    /**
     * Light backfill (15 min) for Shizuku mode only — the listener handles the
     * common case; polling exists as a safety net for binder disconnects.
     * No polling without Shizuku (would just fail behind the background ban).
     * AI reprocessing runs only when both AI toggles allow it.
     */
    private fun startBackfillLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (monitoringEnabled && ShizukuHelper.isAuthorized()) {
                        readClipboardViaShizuku("backfill")
                    }
                    if (canAutoAi()) {
                        val unprocessed = clipboardDao.getUnprocessedEntries()
                        unprocessed.take(1).forEach { entry ->
                            processWithAi(entry.id, entry.content, entry.type)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Backfill failed", e)
                }
                delay(15 * 60 * 1000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                serviceScope.launch {
                    settingsRepository.setClipboardMonitoringEnabled(false)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CHECK_CLIPBOARD -> {
                val externalText = intent.getStringExtra(EXTRA_CLIPBOARD_TEXT)
                if (externalText != null) {
                    processClipboardText(externalText, "accessibility")
                } else {
                    checkClipboard()
                }
                return START_STICKY
            }
        }
        // Default start: verify gate, otherwise stop (don't linger as zombie FGS).
        serviceScope.launch {
            val enabled = settingsRepository.clipboardMonitoringEnabled.first()
            val status = ClipboardGate.evaluate(this@ClipboardService, enabled)
            if (!enabled || status == ClipboardGate.Status.SETUP_REQUIRED) {
                Log.d(TAG, "Start requested but gate=$status enabled=$enabled — stopping")
                stopSelf()
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
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "clipboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 7001, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseIntent = Intent(this, ClipboardService::class.java).apply { action = ACTION_PAUSE }
        val pausePi = PendingIntent.getService(
            this, 7002, pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when {
            !monitoringEnabled -> "Clipboard paused" to "Tap to reopen history"
            gateStatus == ClipboardGate.Status.ACTIVE_SHIZUKU -> "Clipboard watching" to "Via Shizuku • tap to open"
            gateStatus == ClipboardGate.Status.ACTIVE_ACCESSIBILITY -> "Clipboard watching" to "Via accessibility • tap to open"
            else -> "Clipboard setup needed" to "Requires Shizuku or accessibility"
        }
        return NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_CLIPBOARD)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_toolz)
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_clipboard, "Open", openPi)
            .addAction(R.drawable.ic_clipboard, "Pause", pausePi)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_CHECK_CLIPBOARD = "com.frerox.toolz.action.CHECK_CLIPBOARD"
        const val ACTION_PAUSE = "com.frerox.toolz.action.CLIPBOARD_PAUSE"
        const val EXTRA_CLIPBOARD_TEXT = "extra_clipboard_text"
        const val MAX_ENTRIES = 500

        fun startIfNeeded(context: Context) {
            try {
                val intent = Intent(context, ClipboardService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startIfNeeded failed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ClipboardService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
