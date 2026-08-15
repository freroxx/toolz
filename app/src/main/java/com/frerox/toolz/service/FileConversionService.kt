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
import com.frerox.toolz.R
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that runs file conversions off the main thread.
 *
 * Accepts a batch of URIs via the intent extra "input_uris" (ArrayList<Uri>).
 * Processes them sequentially and broadcasts per-file and overall progress.
 *
 * Key improvement: a single file error no longer aborts the whole queue.
 * The error is broadcast for that file and processing continues.
 *
 * Broadcast actions:
 *  - COM_FREROX_TOOLZ_CONVERSION_PROGRESS  (extra: "progress" Int, "queue_pos" Int, "queue_total" Int)
 *  - COM_FREROX_TOOLZ_CONVERSION_SUCCESS   (extra: "output_path" String, "queue_pos" Int, "queue_total" Int)
 *  - COM_FREROX_TOOLZ_CONVERSION_ERROR     (extra: "error_message" String, "queue_pos" Int, "queue_total" Int)
 */
@AndroidEntryPoint
class FileConversionService : Service() {

    @Inject
    lateinit var conversionEngine: ConversionEngine

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val NOTIFICATION_ID = 8888
    private val CHANNEL_ID = "file_conversion_channel"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("UNCHECKED_CAST")
        val inputUris = intent?.getParcelableArrayListExtra<Uri>("input_uris")
            ?: intent?.getParcelableExtra<Uri>("input_uri")?.let { arrayListOf(it) }
            ?: run { stopSelf(); return START_NOT_STICKY }

        val typeString = intent?.getStringExtra("conversion_type") ?: run { stopSelf(); return START_NOT_STICKY }
        val highQuality = intent?.getBooleanExtra("high_quality", true) ?: true
        val type = try {
            ConversionEngine.ConversionType.valueOf(typeString)
        } catch (_: Exception) {
            stopSelf(); return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting conversion…", 0, 1, 1))
        processQueue(inputUris, type, highQuality)
        return START_NOT_STICKY
    }

    private fun processQueue(
        uris: List<Uri>,
        type: ConversionEngine.ConversionType,
        highQuality: Boolean,
    ) {
        serviceScope.launch {
            val total = uris.size
            var successCount = 0
            var errorCount = 0

            for ((idx, uri) in uris.withIndex()) {
                val pos = idx + 1
                val fileName = resolveFileName(uri)
                updateNotification(
                    "[$pos/$total] Converting: $fileName → .${type.extension.uppercase()}",
                    0, pos, total,
                )

                conversionEngine.routeConversion(uri, type, highQuality, batchIndex = if (total > 1) idx else -1)
                    .onEach { status ->
                        when (status) {
                            is ConversionEngine.ConversionStatus.Progress -> {
                                val pct = status.percentage
                                updateNotification(
                                    "[$pos/$total] $fileName → .${type.extension.uppercase()}${if (pct > 0) " ($pct%)" else ""}",
                                    pct, pos, total,
                                )
                                broadcast("COM_FREROX_TOOLZ_CONVERSION_PROGRESS") {
                                    putExtra("progress", pct)
                                    putExtra("queue_pos", pos)
                                    putExtra("queue_total", total)
                                }
                            }
                            is ConversionEngine.ConversionStatus.Success -> {
                                successCount++
                                val isLast = pos == total
                                updateNotification(
                                    if (isLast && errorCount == 0) "✓ All $total file(s) converted!"
                                    else if (isLast) "Done! $successCount ok, $errorCount failed"
                                    else "[$pos/$total] Done, continuing…",
                                    100, pos, total, finished = isLast,
                                )
                                broadcast("COM_FREROX_TOOLZ_CONVERSION_SUCCESS") {
                                    putExtra("output_path", status.outputPath)
                                    putExtra("queue_pos", pos)
                                    putExtra("queue_total", total)
                                }
                            }
                            is ConversionEngine.ConversionStatus.Error -> {
                                errorCount++
                                // Do NOT stop queue — broadcast error and continue
                                val isLast = pos == total
                                updateNotification(
                                    "[$pos/$total] Error: ${status.message}",
                                    0, pos, total, finished = isLast,
                                )
                                broadcast("COM_FREROX_TOOLZ_CONVERSION_ERROR") {
                                    putExtra("error_message", status.message)
                                    putExtra("queue_pos", pos)
                                    putExtra("queue_total", total)
                                }
                            }
                        }
                    }
                    .launchIn(serviceScope)
                    .join()
            }

            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Conversion",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows progress of file conversions" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        content: String,
        progress: Int,
        queuePos: Int,
        queueTotal: Int,
        finished: Boolean = false,
    ): Notification {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (queueTotal > 1) "Toolz · $queuePos of $queueTotal files" else "Toolz File Converter")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!finished)
            .setContentIntent(pending)
            .setAutoCancel(finished)

        when {
            !finished && progress > 0 -> builder.setProgress(100, progress, false)
            !finished             -> builder.setProgress(100, 0, true)
        }
        return builder.build()
    }

    private fun updateNotification(
        content: String,
        progress: Int,
        queuePos: Int,
        queueTotal: Int,
        finished: Boolean = false,
    ) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, buildNotification(content, progress, queuePos, queueTotal, finished))
    }

    private fun broadcast(action: String, block: Intent.() -> Unit = {}) {
        sendBroadcast(Intent(action).apply {
            setPackage(packageName)
            block()
        })
    }

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "file") return java.io.File(uri.path!!).name
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (idx >= 0) cursor.getString(idx) else uri.lastPathSegment ?: "file"
            } ?: uri.lastPathSegment ?: "file"
        } catch (_: Exception) { uri.lastPathSegment ?: "file" }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
