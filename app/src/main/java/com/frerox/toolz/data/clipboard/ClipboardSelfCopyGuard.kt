/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.clipboard

import android.content.ClipData

/**
 * Process-wide guard against capturing the clipboard writes Toolz makes
 * itself.
 *
 * Every listener (the foreground service and the screen-level one) reacts to
 * `onPrimaryClipChanged`, including changes caused by "copy" actions inside
 * the clipboard history. Those clips already exist — copying one should only
 * bump its recency, not run the capture pipeline again, and bulk copy should
 * not insert the synthesized aggregate as a brand new clip.
 *
 * The signature is built from the same fields both a [ClipData] and a
 * normalized [ClipboardPayload] expose, so the service (which reads the
 * clipboard once) and the ViewModel (which writes it) agree without sharing
 * objects. Entries expire quickly, so a genuine external copy of identical
 * content is captured normally.
 */
object ClipboardSelfCopyGuard {
    private const val EXPIRY_MS = 5_000L
    private const val MAX_PENDING = 16

    private data class Pending(val signature: String, val expiresAt: Long)

    private val pending = ArrayDeque<Pending>()

    fun signatureOf(clip: ClipData?): String {
        if (clip == null) return ""
        return buildString {
            for (index in 0 until clip.itemCount) {
                val item = clip.getItemAt(index) ?: continue
                append(item.text?.toString().orEmpty())
                append('\u0000')
                append(item.htmlText.orEmpty())
                append('\u0000')
                append(item.uri?.toString().orEmpty())
                append('\u0000')
                append(clip.description?.getMimeType(index).orEmpty())
                append('\u0001')
            }
        }
    }

    fun signatureOf(payload: ClipboardPayload): String = buildString {
        payload.items.forEach { item ->
            append(item.text.orEmpty())
            append('\u0000')
            append(item.html.orEmpty())
            append('\u0000')
            append(item.uri?.toString().orEmpty())
            append('\u0000')
            append(item.mimeType.orEmpty())
            append('\u0001')
        }
    }

    /** Records a clipboard write performed by Toolz itself. */
    fun noteWrite(clip: ClipData?) {
        val signature = signatureOf(clip)
        if (signature.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(pending) {
            pruneLocked(now)
            pending.addLast(Pending(signature, now + EXPIRY_MS))
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
    }

    /** Records a write whose signature was computed elsewhere. */
    fun noteWrite(signature: String) {
        if (signature.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(pending) {
            pruneLocked(now)
            pending.addLast(Pending(signature, now + EXPIRY_MS))
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
    }

    /**
     * Returns true when this payload is a Toolz-initiated write and must be
     * skipped by the capture listener. Each recorded write can be consumed
     * only once.
     */
    fun consume(payload: ClipboardPayload): Boolean {
        val signature = signatureOf(payload)
        if (signature.isBlank()) return false
        val now = System.currentTimeMillis()
        synchronized(pending) {
            pruneLocked(now)
            val index = pending.indexOfFirst { it.signature == signature }
            if (index < 0) return false
            pending.removeAt(index)
            return true
        }
    }

    fun consume(clip: ClipData?): Boolean {
        val signature = signatureOf(clip)
        if (signature.isBlank()) return false
        val now = System.currentTimeMillis()
        synchronized(pending) {
            pruneLocked(now)
            val index = pending.indexOfFirst { it.signature == signature }
            if (index < 0) return false
            pending.removeAt(index)
            return true
        }
    }

    fun clear() {
        synchronized(pending) { pending.clear() }
    }

    private fun pruneLocked(now: Long) {
        while (pending.isNotEmpty() && pending.first().expiresAt < now) {
            pending.removeFirst()
        }
    }
}
