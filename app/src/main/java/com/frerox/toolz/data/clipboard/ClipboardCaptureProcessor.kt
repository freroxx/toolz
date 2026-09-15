/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.clipboard

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared insert/dedup logic used by ClipboardService AND ClipboardViewModel
 * so foreground-paste, tile, Shizuku and accessibility paths behave identically.
 */
@Singleton
class ClipboardCaptureProcessor @Inject constructor(
    private val clipboardDao: ClipboardDao,
    private val classifier: ClipboardClassifier,
) {
    enum class Outcome {
        INSERTED,
        /** Same content re-copied: timestamp bumped so it moves to top. */
        BUMPED,
        IGNORED_BLANK,
        IGNORED_SENSITIVE,
    }

    suspend fun processText(
        raw: String?,
        source: String,
        excludeSensitive: Boolean,
        maxChars: Int = 50_000,
    ): Outcome {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return Outcome.IGNORED_BLANK
        if (excludeSensitive && classifier.isSensitive(text)) {
            return Outcome.IGNORED_SENSITIVE
        }
        val content = if (text.length > maxChars) text.take(maxChars) else text
        val hash = content.hashCode()

        val latest = clipboardDao.getLatestEntry()
        if (latest != null && (latest.content == content || latest.contentHash == hash)) {
            clipboardDao.updateTimestamp(latest.id, System.currentTimeMillis())
            return Outcome.BUMPED
        }
        // Non-consecutive duplicate: bump that row too instead of inserting a twin.
        val existing = clipboardDao.getByContentHash(hash)
        if (existing != null && existing.content == content) {
            clipboardDao.updateTimestamp(existing.id, System.currentTimeMillis())
            return Outcome.BUMPED
        }

        clipboardDao.insert(
            ClipboardEntry(
                content = content,
                timestamp = System.currentTimeMillis(),
                type = classifier.classify(content),
                contentHash = hash,
                sourceApp = source,
                source = source,
                isAiProcessed = false,
            ),
        )
        return Outcome.INSERTED
    }
}
