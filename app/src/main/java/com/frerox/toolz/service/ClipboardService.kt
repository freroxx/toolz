/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.clipboard.ClipboardAiProcessor
import com.frerox.toolz.data.clipboard.ClipboardCaptureProcessor
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.clipboard.ClipboardPayload
import com.frerox.toolz.data.clipboard.ClipboardPayloadItem
import com.frerox.toolz.data.clipboard.ClipboardRetentionManager
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private const val TAG = "ClipboardService"

/**
 * Foreground clipboard watcher.
 *
 * Android 10+ blocks background clipboard reads for ordinary apps. Toolz only
 * runs this service when a privileged read path is actually available:
 *
 *  - Shizuku authorized → `cmd clipboard get` through the shell user service.
 *  - `READ_CLIPBOARD` app-op granted → standard [ClipboardManager] reads.
 *
 * Otherwise the service stops and the UI falls back to manual capture, the QS
 * tile, the popup, share targets and `ACTION_PROCESS_TEXT`. No fake promises.
 */
@AndroidEntryPoint
class ClipboardService : Service() {

    @Inject lateinit var captureProcessor: ClipboardCaptureProcessor
    @Inject lateinit var aiProcessor: ClipboardAiProcessor
    @Inject lateinit var retentionManager: ClipboardRetentionManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var shizukuExecutor: ShizukuShellExecutor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var clipboardManager: ClipboardManager? = null
    @Volatile private var monitoringEnabled = true
    @Volatile private var excludeSensitive = false
    @Volatile private var onboardingSeen = false
    @Volatile private var gateStatus: ClipboardGate.Status = ClipboardGate.Status.SETUP_REQUIRED
    @Volatile private var lastFingerprint: String? = null
    private val capturing = AtomicBoolean(false)
    @Volatile private var foregroundStarted = false
    @Volatile private var manualCaptureRequested = false
    @Volatile private var settingsCollectorStarted = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        serviceScope.launch {
            if (!monitoringEnabled) return@launch
            if (!backgroundCapable()) return@launch
            captureCurrentClipboard(source = "LISTENER")
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createAllChannels(this)
        try {
            startForegroundCompat(createNotification())
            foregroundStarted = true
        } catch (e: Exception) {
            // Android 12+ can reject a background FGS start. Stop honestly
            // instead of crash-looping; the UI will show the fallback banner.
            Log.w(TAG, "Could not start foreground service: ${e.message}")
            stopSelf()
            return
        }

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)

        serviceScope.launch { watchLoop() }
    }

    private fun startSettingsCollector() {
        if (settingsCollectorStarted) return
        settingsCollectorStarted = true
        serviceScope.launch {
            combine(
                settingsRepository.clipboardMonitoringEnabled,
                settingsRepository.clipboardExcludeSensitive,
                settingsRepository.clipboardOnboardingSeen,
            ) { monitoring, exclude, seen -> Triple(monitoring, exclude, seen) }
                .collect { (monitoring, exclude, seen) ->
                    monitoringEnabled = monitoring
                    excludeSensitive = exclude
                    onboardingSeen = seen
                    gateStatus = ClipboardGate.evaluate(
                        context = this@ClipboardService,
                        monitoringEnabled = monitoring,
                        onboardingSeen = seen,
                    )
                    updateNotification()
                    if (!monitoring || !backgroundCapable()) {
                        if (manualCaptureRequested) return@collect
                        Log.d(TAG, "Clipboard gate not background-capable: $gateStatus")
                        stopSelf()
                    }
                }
        }
    }

    private fun backgroundCapable(): Boolean =
        gateStatus == ClipboardGate.Status.ACTIVE_SHIZUKU ||
            gateStatus == ClipboardGate.Status.ACTIVE_APP_OP

    /**
     * Primary path: system listener. Safety net: a light poll that runs every
     * 5s while interactive and every 45s with the screen off. Polling is
     * skipped when the listener already captured the exact same payload.
     */
    private suspend fun watchLoop() {
        while (serviceScope.isActive) {
            try {
                if (monitoringEnabled && backgroundCapable()) {
                    captureCurrentClipboard(source = "POLL")
                    if (aiProcessor.canAutoProcess()) aiProcessor.processQueued(1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard poll failed: ${e.message}")
            }
            delay(if (isInteractive()) POLL_INTERVAL_MS else SCREEN_OFF_INTERVAL_MS)
        }
    }

    private fun isInteractive(): Boolean = try {
        (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
    } catch (_: Exception) {
        true
    }

    private suspend fun captureCurrentClipboard(source: String) {
        if (!capturing.compareAndSet(false, true)) return
        try {
            val shizukuText = if (ShizukuHelper.isAuthorized()) {
                try {
                    shizukuExecutor.getClipboardText()
                } catch (e: Exception) {
                    // Shizuku can die between authorization check and call, or
                    // the ROM may reject `cmd clipboard get`. Never let that
                    // skip the standard-read fallback.
                    Log.w(TAG, "Shizuku clipboard read failed: ${e.message}")
                    null
                }
            } else {
                null
            }
            if (!shizukuText.isNullOrBlank()) {
                val fingerprint = fingerprint(shizukuText)
                if (shouldProcess(fingerprint)) {
                    val outcome = captureProcessor.processText(
                        raw = shizukuText,
                        source = source,
                        excludeSensitive = excludeSensitive,
                    )
                    handleOutcome(outcome, fingerprint)
                }
                return
            }
            // Shizuku unavailable or `cmd clipboard get` unsupported on this
            // ROM: use the standard read. A granted app-op makes this work in
            // the background; otherwise it still works while Toolz is focused.
            val payload = readSystemClipboard(source) ?: return
            val fingerprint = fingerprint(payload)
            if (!shouldProcess(fingerprint)) return
            val result = captureProcessor.process(payload, excludeSensitive)
            handleOutcome(result.outcome, fingerprint)
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard capture failed: ${e.message}")
        } finally {
            capturing.set(false)
        }
    }

    private fun shouldProcess(fingerprint: String): Boolean =
        fingerprint != lastFingerprint

    private suspend fun handleOutcome(
        outcome: ClipboardCaptureProcessor.Outcome,
        fingerprint: String,
    ) {
        if (outcome == ClipboardCaptureProcessor.Outcome.FAILED) return
        lastFingerprint = fingerprint
        if (outcome == ClipboardCaptureProcessor.Outcome.INSERTED ||
            outcome == ClipboardCaptureProcessor.Outcome.BUMPED
        ) {
            retentionManager.enforceAfterCapture()
            if (aiProcessor.canAutoProcess()) aiProcessor.processQueued(1)
        }
    }

    private fun readSystemClipboard(source: String): ClipboardPayload? {
        return try {
            val manager = clipboardManager ?: return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val description = clip.description
            val items = buildList {
                for (index in 0 until clip.itemCount) {
                    val item = clip.getItemAt(index) ?: continue
                    add(
                        ClipboardPayloadItem(
                            text = item.text?.toString(),
                            html = item.htmlText,
                            uri = item.uri,
                            mimeType = description?.getMimeType(index),
                        ),
                    )
                }
            }
            if (items.all { it.text.isNullOrBlank() && it.uri == null }) return null
            ClipboardPayload(
                items = items,
                label = description?.label?.toString(),
                source = source,
                isSensitiveExtra = description?.extras
                    ?.getBoolean("android.content.extra.IS_SENSITIVE", false) ?: false,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Standard clipboard read denied: ${e.message}")
            null
        }
    }

    private fun fingerprint(text: String): String = sha256("text:$text")

    private fun fingerprint(payload: ClipboardPayload): String = sha256(
        buildString {
            payload.items.forEach { item ->
                append(item.text.orEmpty())
                append('\u0000')
                append(item.html.orEmpty())
                append('\u0000')
                append(item.uri?.toString().orEmpty())
                append('\u0001')
            }
        },
    )

    private fun sha256(value: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        value.hashCode().toString()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK_CLIPBOARD) manualCaptureRequested = true
        startSettingsCollector()
        when (intent?.action) {
            ACTION_PAUSE -> {
                serviceScope.launch {
                    settingsRepository.setClipboardMonitoringEnabled(false)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                serviceScope.launch {
                    settingsRepository.setClipboardMonitoringEnabled(true)
                    refreshGate()
                }
                return START_STICKY
            }
            ACTION_CHECK_CLIPBOARD -> {
                val externalText = intent.getStringExtra(EXTRA_CLIPBOARD_TEXT)
                serviceScope.launch {
                    try {
                        if (externalText != null) {
                            val outcome = captureProcessor.processText(
                                raw = externalText,
                                source = intent.getStringExtra(EXTRA_SOURCE) ?: "EXTERNAL",
                                excludeSensitive = excludeSensitive,
                            )
                            handleOutcome(outcome, fingerprint(externalText))
                        } else {
                            captureCurrentClipboard(source = "MANUAL")
                        }
                    } finally {
                        manualCaptureRequested = false
                        refreshGate()
                    }
                }
                return START_STICKY
            }
        }
        serviceScope.launch { refreshGate() }
        return START_STICKY
    }

    private suspend fun refreshGate() {
        val monitoring = settingsRepository.clipboardMonitoringEnabled.first()
        val seen = settingsRepository.clipboardOnboardingSeen.first()
        excludeSensitive = settingsRepository.clipboardExcludeSensitive.first()
        monitoringEnabled = monitoring
        onboardingSeen = seen
        gateStatus = ClipboardGate.evaluate(this, monitoring, seen)
        updateNotification()
        if (!manualCaptureRequested && (!monitoring || !backgroundCapable())) stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        // `specialUse` only exists on Android 14+. On Android 12–13 the
        // two-argument call is the safe path and the manifest type is ignored.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.ID_CLIPBOARD,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.ID_CLIPBOARD, notification)
        }
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NotificationHelper.ID_CLIPBOARD, createNotification())
        } catch (_: Exception) {
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "clipboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            7001,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggleIntent = Intent(this, ClipboardService::class.java).apply {
            action = if (monitoringEnabled) ACTION_PAUSE else ACTION_RESUME
        }
        val togglePi = PendingIntent.getService(
            this,
            7002,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = when {
            !monitoringEnabled -> getString(R.string.st_ClipboardService_paused_title)
            gateStatus == ClipboardGate.Status.ACTIVE_SHIZUKU ->
                getString(R.string.st_ClipboardService_watching_title)
            gateStatus == ClipboardGate.Status.ACTIVE_APP_OP ->
                getString(R.string.st_ClipboardService_watching_title)
            else -> getString(R.string.st_ClipboardService_setup_title)
        }
        val text = when {
            !monitoringEnabled -> getString(R.string.st_ClipboardService_paused_body)
            gateStatus == ClipboardGate.Status.ACTIVE_SHIZUKU ->
                getString(R.string.st_ClipboardService_watching_shizuku)
            gateStatus == ClipboardGate.Status.ACTIVE_APP_OP ->
                getString(R.string.st_ClipboardService_watching_appop)
            else -> getString(R.string.st_ClipboardService_setup_body)
        }
        val toggleLabel = if (monitoringEnabled) {
            getString(R.string.st_ClipboardService_action_pause)
        } else {
            getString(R.string.st_ClipboardService_action_resume)
        }

        return NotificationHelper.baseBuilder(
            this,
            NotificationHelper.CHANNEL_CLIPBOARD,
            showLargeIcon = false,
        )
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_toolz)
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_clipboard, getString(R.string.st_ClipboardService_action_open), openPi)
            .addAction(R.drawable.ic_clipboard, toggleLabel, togglePi)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_CHECK_CLIPBOARD = "com.frerox.toolz.action.CHECK_CLIPBOARD"
        const val ACTION_PAUSE = "com.frerox.toolz.action.CLIPBOARD_PAUSE"
        const val ACTION_RESUME = "com.frerox.toolz.action.CLIPBOARD_RESUME"
        const val EXTRA_CLIPBOARD_TEXT = "extra_clipboard_text"
        const val EXTRA_SOURCE = "extra_source"

        private const val POLL_INTERVAL_MS = 5_000L
        private const val SCREEN_OFF_INTERVAL_MS = 45_000L

        fun startIfNeeded(context: Context) {
            try {
                context.startForegroundService(
                    Intent(context, ClipboardService::class.java),
                )
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

        fun requestCapture(context: Context, source: String = "TILE") {
            try {
                val intent = Intent(context, ClipboardService::class.java).apply {
                    action = ACTION_CHECK_CLIPBOARD
                    putExtra(EXTRA_SOURCE, source)
                }
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "requestCapture failed: ${e.message}")
            }
        }
    }
}
