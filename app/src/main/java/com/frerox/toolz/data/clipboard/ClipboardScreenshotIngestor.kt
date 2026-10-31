/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.clipboard

import android.content.Context
import android.util.Log
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.PurgeShotDetector
import com.frerox.toolz.service.PurgeShotService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies new screenshots into the encrypted clipboard history before
 * PurgeShot deletes the originals.
 *
 * Detection stays in [PurgeShotDetector] so both features agree on what a
 * screenshot is. Clip ingestion goes through [ClipboardCaptureProcessor], so a
 * screenshot becomes a normal IMAGE clip with content-addressed attachments,
 * quotas, sensitivity masking and retention applied exactly like any other
 * captured URI.
 *
 * Dedupe is layered:
 *  - an in-process bounded LRU stops repeated observer/poll scans;
 *  - [SettingsRepository.clipboardIngestedScreenshotUris] survives process
 *    death so a service restart does not re-bump older scan results;
 *  - the capture processor's SHA-256 dedupe bumps an existing clip instead of
 *    inserting a twin.
 */
@Singleton
class ClipboardScreenshotIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val captureProcessor: ClipboardCaptureProcessor,
    private val settingsRepository: SettingsRepository,
) {
    private val captureMutex = Mutex()

    private val recent = object : LinkedHashMap<String, Unit>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > MAX_REMEMBERED
    }

    /**
     * Ingests one detected screenshot. Returns null when the candidate was
     * rejected before capture (disabled, stale, not a screenshot, duplicate).
     */
    suspend fun ingest(
        candidate: PurgeShotService.ScreenshotCandidate,
        isPoll: Boolean = false,
    ): ClipboardCaptureProcessor.CaptureResult? {
        if (!isEnabled()) return null
        if (!PurgeShotDetector.looksLikeScreenshot(candidate)) return null
        if (!PurgeShotDetector.isFreshEnough(candidate, isPoll)) return null

        val uriString = candidate.uri.toString()
        // MediaStore candidates almost always have a filePath, so this check
        // must not be conditional: otherwise every scan reprocesses the whole
        // recent-image window and re-bumps old clips.
        if (!remember(uriString)) return null

        return captureMutex.withLock {
            val alreadyIngested = try {
                uriString in settingsRepository.clipboardIngestedScreenshotUris.first()
            } catch (_: Exception) {
                false
            }
            if (alreadyIngested) return@withLock null
            val excludeSensitive = try {
                settingsRepository.clipboardExcludeSensitive.first()
            } catch (_: Exception) {
                false
            }
            val mime = try {
                context.contentResolver.getType(candidate.uri)
            } catch (_: Exception) {
                null
            } ?: "image/*"
            val result = captureProcessor.process(
                ClipboardPayload(
                    items = listOf(
                        ClipboardPayloadItem(
                            uri = candidate.uri,
                            mimeType = mime,
                            fileName = candidate.displayName,
                        ),
                    ),
                    label = candidate.displayName,
                    source = ClipboardSource.SCREENSHOT,
                    timestamp = candidate.dateAddedMs,
                ),
                excludeSensitive = excludeSensitive,
            )
            when (result.outcome) {
                ClipboardCaptureProcessor.Outcome.INSERTED,
                ClipboardCaptureProcessor.Outcome.BUMPED,
                ClipboardCaptureProcessor.Outcome.IGNORED_SENSITIVE,
                -> {
                    try {
                        settingsRepository.addClipboardIngestedScreenshotUri(uriString)
                    } catch (_: Exception) {
                    }
                }
                else -> {
                    // Allow a future scan to retry after the user raises a
                    // quota or the file becomes readable again.
                    forget(uriString)
                }
            }
            Log.d(TAG, "Screenshot ${candidate.displayName}: ${result.outcome}")
            result
        }
    }

    /**
     * Scans the newest MediaStore images for fresh screenshots and ingests all
     * of them in chronological order. Used when the observer may have missed
     * an event (screen resume, service restart, OEM MediaStore delay).
     */
    suspend fun ingestRecentScreenshots(
        isPoll: Boolean = false,
        limit: Int = 20,
    ): Int {
        if (!isEnabled()) return 0
        val candidates = try {
            PurgeShotDetector.queryRecent(context.contentResolver, limit)
                .filter { candidate ->
                    PurgeShotDetector.looksLikeScreenshot(candidate) &&
                        PurgeShotDetector.isFreshEnough(candidate, isPoll)
                }
                .distinctBy { it.filePath ?: it.uri.toString() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not scan screenshots", e)
            return 0
        }
        var ingested = 0
        for (candidate in candidates.sortedBy { it.dateAddedMs }) {
            if (ingest(candidate, isPoll) != null) ingested++
        }
        return ingested
    }

    private suspend fun isEnabled(): Boolean = try {
        // Pausing clipboard monitoring must pause screenshot capture too.
        settingsRepository.clipboardMonitoringEnabled.first() &&
            settingsRepository.clipboardCaptureScreenshots.first()
    } catch (_: Exception) {
        false
    }

    private fun remember(key: String): Boolean = synchronized(recent) {
        if (recent.containsKey(key)) return false
        recent[key] = Unit
        true
    }

    private fun forget(key: String) {
        synchronized(recent) { recent.remove(key) }
    }

    private companion object {
        const val TAG = "ClipboardScreenshots"
        const val MAX_REMEMBERED = 32
    }
}
