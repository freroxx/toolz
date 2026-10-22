/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * External links and the share sheet used by Developer Mode. Everything
 * here is initiated by an explicit user tap; no text leaves the device
 * automatically and no diagnostic is uploaded.
 */

package com.frerox.toolz.data.developer

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

object DevUiOverrider {

    /** Same invite link the Settings screen's Discord button uses. */
    const val TOOLZ_DISCORD_URL = "https://discord.gg/aAswRUerwh"

    fun openSystemDeveloperOptions(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    fun openAppNotificationSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun openToolzDiscord(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, TOOLZ_DISCORD_URL.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /**
     * System share sheet — the only channel a diagnostic report leaves the
     * device. The user chooses the destination.
     */
    fun shareText(context: Context, chooserTitle: String, text: String): Boolean = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}
