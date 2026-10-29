/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.frerox.toolz.R
import com.frerox.toolz.data.clipboard.ClipboardCaptureProcessor
import com.frerox.toolz.data.clipboard.ClipboardPayload
import com.frerox.toolz.data.clipboard.ClipboardPayloadItem
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Invisible capture target for:
 *  - Share sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`)
 *  - Text selection menu (`ACTION_PROCESS_TEXT`)
 *
 * Saves the shared text/HTML/URIs through the same pipeline as the watcher,
 * then shows a short confirmation. It never opens the app UI.
 */
@AndroidEntryPoint
class ClipboardShareActivity : AppCompatActivity() {

    @Inject lateinit var captureProcessor: ClipboardCaptureProcessor
    @Inject lateinit var settingsRepository: SettingsRepository

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A second share can arrive while the first toast is still visible.
        // Process it instead of leaving an invisible activity open.
        handled = false
        handleIntent(intent)
    }

    private fun handleIntent(source: Intent) {
        if (handled) return
        handled = true
        val payload = try {
            buildPayload(source)
        } catch (e: Exception) {
            android.util.Log.w("ClipboardShare", "Malformed share intent", e)
            null
        }
        if (payload == null) {
            Toast.makeText(this, R.string.st_ClipboardShare_nothing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                val exclude = runCatching {
                    settingsRepository.clipboardExcludeSensitive.first()
                }.getOrDefault(false)
                val result = try {
                    captureProcessor.process(payload, exclude)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("ClipboardShare", "Share capture failed", e)
                    null
                }
                val message = when (result?.outcome) {
                    ClipboardCaptureProcessor.Outcome.INSERTED ->
                        R.string.st_ClipboardShare_saved
                    ClipboardCaptureProcessor.Outcome.BUMPED ->
                        R.string.st_ClipboardShare_bumped
                    ClipboardCaptureProcessor.Outcome.IGNORED_SENSITIVE ->
                        R.string.st_ClipboardShare_sensitive
                    ClipboardCaptureProcessor.Outcome.IGNORED_TOO_LARGE,
                    ClipboardCaptureProcessor.Outcome.IGNORED_QUOTA,
                    -> R.string.st_ClipboardShare_too_large
                    ClipboardCaptureProcessor.Outcome.IGNORED_BLANK ->
                        R.string.st_ClipboardShare_nothing
                    ClipboardCaptureProcessor.Outcome.FAILED,
                    null,
                    -> R.string.st_ClipboardShare_failed
                }
                Toast.makeText(this@ClipboardShareActivity, message, Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    private fun buildPayload(source: Intent): ClipboardPayload? {
        val action = source.action ?: return null
        val isProcessText = action == Intent.ACTION_PROCESS_TEXT
        val fallbackMime = source.type
        val textMime = fallbackMime?.takeIf { it.startsWith("text/") } ?: "text/plain"

        // Share intents routinely put the same content in EXTRA_TEXT,
        // ClipData and EXTRA_STREAM. Merge by URI (or normalized text) so a
        // single share never becomes a duplicated multi-item clip.
        val items = LinkedHashMap<String, ClipboardPayloadItem>()
        fun add(item: ClipboardPayloadItem) {
            if (item.text.isNullOrBlank() && item.uri == null) return
            val key = item.uri?.toString()?.let { "uri:$it" }
                ?: "text:${item.text.orEmpty().trim()}"
            val existing = items[key]
            items[key] = if (existing == null) {
                item
            } else {
                existing.copy(
                    text = existing.text ?: item.text,
                    html = existing.html ?: item.html,
                    mimeType = existing.mimeType?.takeUnless { it == "*/*" }
                        ?: item.mimeType?.takeUnless { it == "*/*" },
                    fileName = existing.fileName ?: item.fileName,
                )
            }
        }

        if (isProcessText) {
            val text = source.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (text.isNullOrBlank()) return null
            add(ClipboardPayloadItem(text = text, mimeType = "text/plain"))
        } else {
            source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    add(
                        ClipboardPayloadItem(
                            text = it,
                            html = source.getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)?.toString(),
                            mimeType = textMime,
                        ),
                    )
                }
            source.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    val item = clip.getItemAt(index)
                    val text = item.text?.toString()
                    val uri = item.uri
                    add(
                        ClipboardPayloadItem(
                            text = text,
                            html = item.htmlText,
                            uri = uri,
                            mimeType = resolveMime(uri, clip.description?.getMimeType(index)),
                        ),
                    )
                }
            }
            when (action) {
                Intent.ACTION_SEND -> {
                    IntentCompat.getParcelableExtra(source, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.let { add(ClipboardPayloadItem(uri = it, mimeType = resolveMime(it, fallbackMime))) }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    IntentCompat.getParcelableArrayListExtra(
                        source,
                        Intent.EXTRA_STREAM,
                        Uri::class.java,
                    )?.forEach {
                        add(ClipboardPayloadItem(uri = it, mimeType = resolveMime(it, fallbackMime)))
                    }
                }
            }
        }

        if (items.isEmpty()) return null
        return ClipboardPayload(
            items = items.values.toList(),
            label = source.clipData?.description?.label?.toString(),
            source = if (isProcessText) "PROCESS_TEXT" else "SHARE",
            isSensitiveExtra = source.getBooleanExtra(EXTRA_IS_SENSITIVE, false),
        )
    }

    private fun resolveMime(uri: Uri?, declared: String?): String? {
        val clean = declared?.takeUnless { it == "*/*" }
        if (clean != null) return clean
        if (uri == null) return null
        return runCatching { contentResolver.getType(uri) }.getOrNull()
    }

    private companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
