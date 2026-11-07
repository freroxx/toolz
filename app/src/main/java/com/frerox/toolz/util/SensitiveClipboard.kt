/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Clipboard writes for Whisper credentials, message text and identity material.
 *
 * Compose's `LocalClipboardManager` exposes no `ClipDescription`, so every copy
 * used to land as an ordinary clip: Android 13+ showed the credential in the
 * system preview and added it to clipboard history. This helper routes through
 * the platform [ClipboardManager] and flags the clip with
 * [ClipDescription.EXTRA_IS_SENSITIVE] so the OS masks it.
 *
 * The flag is best-effort by platform design: Android 12L and older ignore the
 * extra. Whisper's 60-second WorkManager clear still bounds exposure there.
 */
object SensitiveClipboard {

    /** Copies [text] as a sensitive clip. Returns false when the platform refuses. */
    fun copy(context: Context, text: String, label: String = "Whisper"): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        return runCatching {
            clipboard.setPrimaryClip(clip)
            true
        }.getOrDefault(false)
    }

    /** Clears the clipboard; used on account deletion so no copied token survives. */
    fun clear(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { clipboard.clearPrimaryClip() }
    }

    /** Current clipboard text, or null when empty/unreadable. */
    fun readText(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        return runCatching {
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
