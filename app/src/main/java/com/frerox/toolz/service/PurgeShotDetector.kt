/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.frerox.toolz.data.purgeshot.PurgeShotHandler
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Single source of truth for "what is the latest screenshot in MediaStore, and does it look
 * like a screenshot worth acting on".
 *
 * Both [PurgeShotService] (live ContentObserver, runs while the process is alive) and
 * [PurgeShotObserverJobService] (JobScheduler content-trigger, runs even when the process is
 * dead / outside Toolz) delegate here so they can never disagree on sort order, the screenshot
 * heuristic, or the age-gate threshold — previously these were hand-duplicated in both files
 * and had drifted (different sort columns, different age gates), so the two paths could pick
 * two different "latest" images.
 *
 * Uses [PurgeShotService.ScreenshotCandidate] as the candidate type rather than defining a
 * second one, because [PurgeShotHandler.handleNewScreenshot] — the shared next step after
 * detection — is already typed against it; introducing a parallel type here would just move
 * the mismatch one layer down instead of fixing it.
 *
 * Dedup (comparing against `purgeShotLastScreenshotUri` and writing the new one) is left to
 * [PurgeShotHandler], which already owns that check — duplicating it here would mean two
 * call sites racing to read-then-write the same DataStore key.
 */
object PurgeShotDetector {
    private const val TAG = "PurgeShotDetector"

    /** How long to wait for MediaStore to settle (IS_PENDING -> 0) after a change notification. */
    const val SETTLE_DELAY_MS = 700L

    /** A candidate must be newer than "now - this" and not further than 2s in the future (clock skew). */
    private const val MAX_AGE_MS = 20_000L
    private const val MAX_FUTURE_SKEW_MS = -2_000L

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.RELATIVE_PATH,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATA
    )

    // DATE_TAKEN is set at capture time and is the most accurate signal for "just now";
    // DATE_ADDED is the fallback for OEMs that leave DATE_TAKEN at 0 for screenshots.
    private const val SORT = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

    /** Queries MediaStore for the most recently inserted image. Null if none or on query failure. */
    fun queryLatest(resolver: ContentResolver): PurgeShotService.ScreenshotCandidate? {
        return try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                SORT
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val addedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val modIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val takenIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val relIdx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                val bucketIdx = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)

                val id = c.getLong(idIdx)
                val name = c.getString(nameIdx) ?: "screenshot.jpg"
                val addedSec = c.getLong(addedIdx)
                val modSec = c.getLong(modIdx)
                val takenMs = if (takenIdx != -1) c.getLong(takenIdx) else 0L
                val dateAddedMs = when {
                    takenMs > 0 -> takenMs
                    addedSec > 0 -> addedSec * 1000
                    modSec > 0 -> modSec * 1000
                    else -> System.currentTimeMillis()
                }
                val rel = if (relIdx != -1) c.getString(relIdx) else null
                val buck = if (bucketIdx != -1) c.getString(bucketIdx) else null
                val path = if (dataIdx != -1) c.getString(dataIdx) else null
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                PurgeShotService.ScreenshotCandidate(uri, name, dateAddedMs, rel, path, buck)
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryLatest failed", e)
            null
        }
    }

    /** Broad OEM-covering heuristic: Samsung, Pixel, Xiaomi, OnePlus, and anything storing under a "Screenshots" bucket. */
    fun looksLikeScreenshot(candidate: PurgeShotService.ScreenshotCandidate): Boolean {
        val rel = candidate.relativePath.orEmpty()
        val buck = candidate.bucketName.orEmpty()
        val path = candidate.filePath.orEmpty()
        val name = candidate.displayName
        return name.contains("screenshot", ignoreCase = true) ||
                rel.contains("screenshot", ignoreCase = true) ||
                buck.contains("screenshot", ignoreCase = true) ||
                path.contains("screenshot", ignoreCase = true)
    }

    /** True if the candidate's timestamp is within the acceptable freshness window. */
    fun isFreshEnough(candidate: PurgeShotService.ScreenshotCandidate): Boolean {
        val age = System.currentTimeMillis() - candidate.dateAddedMs
        return age in MAX_FUTURE_SKEW_MS..MAX_AGE_MS
    }

    fun hasMediaPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Full pipeline: settle -> query -> heuristic -> freshness -> hand off to
     * [PurgeShotHandler.handleNewScreenshot], which owns dedup and Smart Auto / popup /
     * notification routing from there. Returns true if a screenshot candidate was found and
     * handed off (not necessarily acted on — PurgeShotHandler may still dedup it), false if
     * nothing passed the pre-checks here.
     *
     * Both callers share this so a fix to the pipeline (a new heuristic, a new age gate)
     * only has to happen once.
     */
    suspend fun detectAndHandle(
        context: Context,
        repository: PurgeShotRepository,
        settingsRepository: SettingsRepository,
        awaitSettle: Boolean = true
    ): Boolean {
        if (!settingsRepository.purgeShotEnabled.first()) {
            Log.d(TAG, "disabled, skip")
            return false
        }
        if (!hasMediaPermission(context)) {
            Log.w(TAG, "missing media permission")
            return false
        }
        if (awaitSettle) delay(SETTLE_DELAY_MS)

        val candidate = queryLatest(context.contentResolver) ?: run {
            Log.d(TAG, "no candidate")
            return false
        }
        if (!isFreshEnough(candidate)) {
            Log.d(TAG, "candidate stale, age=${System.currentTimeMillis() - candidate.dateAddedMs}ms uri=${candidate.uri}")
            return false
        }
        if (!looksLikeScreenshot(candidate)) {
            Log.d(TAG, "not a screenshot: ${candidate.displayName}")
            return false
        }

        PurgeShotHandler.handleNewScreenshot(context, repository, settingsRepository, candidate)
        Log.i(TAG, "handed off ${candidate.displayName}")
        return true
    }
}