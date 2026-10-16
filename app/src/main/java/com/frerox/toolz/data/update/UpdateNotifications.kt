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

package com.frerox.toolz.data.update

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.worker.UpdateReceiver
import java.io.File

/**
 * All update-surface notifications. Every entry point is wrapped in
 * try/catch and must never crash the caller (workers, receivers).
 */
object UpdateNotifications {
    const val ID_UPDATE_DOWNLOAD = 8003

    const val ACTION_DOWNLOAD_UPDATE = "com.frerox.toolz.DOWNLOAD_UPDATE"
    const val ACTION_INSTALL_UPDATE = "com.frerox.toolz.INSTALL_UPDATE"
    const val ACTION_CANCEL_UPDATE = "com.frerox.toolz.CANCEL_UPDATE"

    private const val TAG = "UpdateNotifications"
    private const val REQ_CONTENT = 8101
    private const val REQ_DOWNLOAD = 8102
    private const val REQ_DETAILS = 8103
    private const val REQ_INSTALL = 8104
    private const val REQ_CANCEL = 8105
    private const val REQ_ERROR = 8106

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun updateScreenIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "update_settings")
            putExtra("show_update_dialog", true)
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags())
    }

    private fun receiverIntent(
        context: Context,
        action: String,
        requestCode: Int,
        extras: Intent.() -> Unit = {}
    ): PendingIntent {
        val intent = Intent(context, UpdateReceiver::class.java).apply {
            this.action = action
            extras()
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags())
    }

    /** First 3 changelog lines, capped at 220 chars, for BigText rows. */
    private fun changelogSnippet(changelog: String): String {
        val lines = changelog.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        val joined = lines.joinToString("\n")
        return if (joined.length <= 220) joined else joined.take(217) + "..."
    }

    /** "New Toolz vX ready" row with Download / Details actions. */
    fun showAvailable(context: Context, update: PendingUpdate) {
        try {
            NotificationHelper.createAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val title = "New Toolz v${update.version} ready" +
                if (update.isCritical) " • Critical" else ""
            val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_APP_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_toolz)
                .setContentTitle(title)
                .setContentText("Tap to see what's new in this release.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(changelogSnippet(update.changelog)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SYSTEM)
                .setContentIntent(updateScreenIntent(context, REQ_CONTENT))
                .setAutoCancel(true)
                .addAction(
                    R.drawable.ic_stat_toolz,
                    "Download",
                    receiverIntent(context, ACTION_DOWNLOAD_UPDATE, REQ_DOWNLOAD) {
                        putExtra("url", update.downloadUrl)
                        putExtra("version", update.version)
                    }
                )
                .addAction(
                    R.drawable.ic_stat_toolz,
                    "Details",
                    updateScreenIntent(context, REQ_DETAILS)
                )
                .build()
            manager.notify(NotificationHelper.ID_APP_UPDATE, notification)
        } catch (e: Exception) {
            Log.w(TAG, "showAvailable failed: ${e.message}")
        }
    }

    /** Silent ongoing progress row; caller throttles via shouldPublishProgress. */
    fun showProgress(
        context: Context,
        update: PendingUpdate,
        progress: Int,
        bytesDownloaded: Long,
        total: Long,
        speedBps: Long = 0L
    ) {
        try {
            NotificationHelper.createAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val pct = progress.coerceIn(0, 100)
            val sizeText = if (total > 0) {
                "${formatBytes(bytesDownloaded)} / ${formatBytes(total)}"
            } else {
                formatBytes(bytesDownloaded)
            }
            val speedText = if (speedBps > 0) " • ${formatSpeed(speedBps)}" else ""
            val notification = NotificationHelper.progressBuilder(
                context,
                NotificationHelper.CHANNEL_APP_UPDATES,
                "Downloading v${update.version} $pct%",
                "$sizeText$speedText",
                pct
            )
                .setContentIntent(updateScreenIntent(context, REQ_CONTENT))
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    receiverIntent(context, ACTION_CANCEL_UPDATE, REQ_CANCEL)
                )
                .build()
            manager.notify(ID_UPDATE_DOWNLOAD, notification)
        } catch (e: Exception) {
            Log.w(TAG, "showProgress failed: ${e.message}")
        }
    }

    /** "Tap to install" row; supersedes the available/progress rows. */
    fun showReady(context: Context, update: PendingUpdate, apkFile: File) {
        try {
            NotificationHelper.createAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val install = receiverIntent(context, ACTION_INSTALL_UPDATE, REQ_INSTALL) {
                putExtra("apk_path", apkFile.absolutePath)
            }
            val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_APP_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_toolz)
                .setContentTitle("Update ready — tap to install")
                .setContentText("Toolz v${update.version} downloaded. Tap to complete the update.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Toolz v${update.version} is downloaded and verified. " +
                            "Tap Install now (you will confirm on the system installer screen)."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SYSTEM)
                .setContentIntent(install)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_stat_toolz, "Install now", install)
                .build()
            manager.notify(NotificationHelper.ID_UPDATE_READY, notification)
            try { manager.cancel(NotificationHelper.ID_APP_UPDATE) } catch (_: Exception) {}
            try { manager.cancel(ID_UPDATE_DOWNLOAD) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "showReady failed: ${e.message}")
        }
    }

    fun showInstalled(context: Context, version: String) {
        try {
            NotificationHelper.createAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationHelper.terminalBuilder(
                context,
                NotificationHelper.CHANNEL_APP_UPDATES,
                "Toolz updated to v$version",
                "You're on the latest release. Enjoy the new improvements.",
                highPriority = false
            )
                .setContentIntent(updateScreenIntent(context, REQ_CONTENT))
                .build()
            manager.notify(NotificationHelper.ID_UPDATE_READY, notification)
        } catch (e: Exception) {
            Log.w(TAG, "showInstalled failed: ${e.message}")
        }
    }

    fun showError(context: Context, message: String) {
        try {
            NotificationHelper.createAllChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = "$message. Tap to retry."
            val notification = NotificationHelper.terminalBuilder(
                context,
                NotificationHelper.CHANNEL_APP_UPDATES,
                "Update failed",
                text,
                highPriority = true
            )
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(updateScreenIntent(context, REQ_ERROR))
                .build()
            manager.notify(NotificationHelper.ID_APP_UPDATE, notification)
        } catch (e: Exception) {
            Log.w(TAG, "showError failed: ${e.message}")
        }
    }

    fun cancelAll(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try { manager.cancel(NotificationHelper.ID_APP_UPDATE) } catch (_: Exception) {}
            try { manager.cancel(NotificationHelper.ID_UPDATE_READY) } catch (_: Exception) {}
            try { manager.cancel(ID_UPDATE_DOWNLOAD) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "cancelAll failed: ${e.message}")
        }
    }
}
