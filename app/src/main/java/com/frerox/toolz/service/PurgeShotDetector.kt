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
    const val SETTLE_DELAY_MS = 550L

    /** A candidate must be newer than "now - this" and not further than 2s in the future (clock skew).
     *  90s tolerates JobScheduler batching, Doze, OEM killers and MediaStore indexing delay
     *  when Toolz isn't in foreground; previously 20s was too tight and missed delayed dispatches
     *  on low-RAM / battery-saver devices where the Job can be deferred 10-30s.
     *  BEST fix: keep real-time window at 90s, but allow poll fallback to scan up to 16min (covers
     *  WorkManager 15min interval + Doze buffer) so missed events are never lost. */
    const val MAX_AGE_MS = 90_000L
    const val MAX_AGE_POLL_MS = 16 * 60_000L
    const val MAX_FUTURE_SKEW_MS = -2_000L

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

    // BEST fix: DATE_ADDED is the only reliably set column at insert time on all OEMs.
    // DATE_TAKEN is often 0 for screenshots (e.g., Samsung, Xiaomi leave it empty), so sorting
    // by DATE_TAKEN first pushes those screenshots to the bottom of the result and they fall
    // outside the top-8 window when the user has many recent photos — the core "doesn't work at all"
    // bug. Sort by DATE_ADDED primary (always set by MediaStore at insertion) and use DATE_TAKEN
    // only as secondary for devices where it is valid.
    private const val SORT = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media.DATE_TAKEN} DESC"

    /** Queries MediaStore for the most recently inserted images (up to limit). Used to scan for screenshot among recent inserts. */
    fun queryRecent(resolver: ContentResolver, limit: Int = 8): List<PurgeShotService.ScreenshotCandidate> {
        val results = mutableListOf<PurgeShotService.ScreenshotCandidate>()
        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                SORT
            )?.use { c ->
                var count = 0
                if (c.moveToFirst()) {
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val addedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val modIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val takenIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val relIdx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    val bucketIdx = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                    do {
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
                        results.add(PurgeShotService.ScreenshotCandidate(uri, name, dateAddedMs, rel, path, buck))
                        count++
                    } while (count < limit && c.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryRecent failed", e)
        }
        // Fallback: also check MediaStore.Files for images that were inserted via Files first (OEMs)
        if (results.size < limit) {
            try {
                val filesUri = MediaStore.Files.getContentUri("external")
                val filesProj = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.MEDIA_TYPE
                )
                val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
                val sort = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                resolver.query(filesUri, filesProj, sel, null, sort)?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val addedIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    val modIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val relIdx = c.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    val dataIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    var count = 0
                    if (c.moveToFirst()) {
                        do {
                            val id = c.getLong(idIdx)
                            val name = c.getString(nameIdx) ?: continue
                            // Only consider screenshot-like file names to avoid noise
                            if (!looksLikeScreenshotName(name, c.getString(relIdx), c.getString(dataIdx))) continue
                            val addedSec = c.getLong(addedIdx)
                            val modSec = c.getLong(modIdx)
                            val ts = when {
                                addedSec > 0 -> addedSec * 1000
                                modSec > 0 -> modSec * 1000
                                else -> System.currentTimeMillis()
                            }
                            // Deduplicate against Images results by displayName+ts proximity or exact filePath
                            if (results.any { it.displayName == name && kotlin.math.abs(it.dateAddedMs - ts) < 5000 }) continue
                            val rel = if (relIdx != -1) c.getString(relIdx) else null
                            val path = if (dataIdx != -1) c.getString(dataIdx) else null
                            if (path != null && results.any { it.filePath == path }) continue
                            val uri = ContentUris.withAppendedId(filesUri, id)
                            // Try to map to Images URI if possible for deletion path; keep Files URI as fallback
                            results.add(PurgeShotService.ScreenshotCandidate(uri, name, ts, rel, path, null))
                            count++
                        } while (count < 3 && c.moveToNext())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "query Files fallback failed", e)
            }
        }
        // BEST fix — File system fallback when MediaStore is empty or permission denied (SecurityException).
        // If hasMediaPermission is false, queryRecent will have returned empty (MediaStore query threw).
        // Fall back to direct File listing which works with MANAGE_EXTERNAL_STORAGE or when MediaStore
        // indexing is delayed by OEM. This guarantees detection even when MediaStore is not yet indexed.
        if (results.isEmpty()) {
            try {
                val fileCandidates = queryViaFileFallback()
                // Deduplicate and add
                for (fc in fileCandidates) {
                    if (results.none { it.filePath == fc.filePath }) results.add(fc)
                }
                if (fileCandidates.isNotEmpty()) Log.d(TAG, "File fallback found ${fileCandidates.size} candidates")
            } catch (e: Exception) {
                Log.w(TAG, "File fallback failed", e)
            }
        }
        return results.sortedByDescending { it.dateAddedMs }
    }

    /** File system fallback: directly list Screenshots directories via File API.
     *  Works even when READ_MEDIA_IMAGES is denied but MANAGE_EXTERNAL_STORAGE is granted,
     *  or when MediaStore hasn't indexed the new screenshot yet (OEM delays). */
    private fun queryViaFileFallback(): List<PurgeShotService.ScreenshotCandidate> {
        val out = mutableListOf<PurgeShotService.ScreenshotCandidate>()
        try {
            val candidates = mutableListOf<java.io.File>()
            val dcimScreens = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM), "Screenshots")
            val picturesScreens = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Screenshots")
            val dcimRoot = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
            val picturesRoot = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            for (dir in listOf(dcimScreens, picturesScreens, dcimRoot, picturesRoot)) {
                if (dir.exists() && dir.isDirectory && dir.canRead()) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile && looksLikeScreenshotName(f.name, f.parent, f.absolutePath)) {
                            candidates.add(f)
                        }
                    }
                }
            }
            // Also check generic /sdcard/Screenshots if exists
            val generic = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Screenshots")
            if (generic.exists() && generic.isDirectory) {
                generic.listFiles()?.forEach { f ->
                    if (f.isFile && looksLikeScreenshotName(f.name, f.parent, f.absolutePath)) candidates.add(f)
                }
            }
            candidates.sortByDescending { it.lastModified() }
            for (file in candidates.take(3)) {
                val ts = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                val uri = android.net.Uri.fromFile(file)
                val rel = when {
                    file.absolutePath.contains("DCIM/Screenshots") -> "DCIM/Screenshots"
                    file.absolutePath.contains("Pictures/Screenshots") -> "Pictures/Screenshots"
                    else -> file.parent
                }
                out.add(
                    PurgeShotService.ScreenshotCandidate(
                        uri = uri,
                        displayName = file.name,
                        dateAddedMs = ts,
                        relativePath = rel,
                        filePath = file.absolutePath,
                        bucketName = "Screenshots"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryViaFileFallback exception", e)
        }
        return out
    }

    private fun looksLikeScreenshotName(name: String?, rel: String?, path: String?): Boolean {
        return (name?.contains("screenshot", true) == true) ||
            (rel?.contains("screenshot", true) == true) ||
            (path?.contains("screenshot", true) == true) ||
            (name?.contains("screencap", true) == true) ||
            (name?.contains("screen_capture", true) == true)
    }

    /** Queries MediaStore for the most recently inserted image. Null if none or on query failure. */
    fun queryLatest(resolver: ContentResolver): PurgeShotService.ScreenshotCandidate? {
        return queryRecent(resolver, 1).firstOrNull()
    }

    /** Broad OEM-covering heuristic: Samsung, Pixel, Xiaomi, OnePlus, and anything storing under a "Screenshots" bucket.
     *  Covers: "Screenshot_*", "Screenshots" folder, "ScreenCapture", "Screen Cap", "screencap", "capture" in path. */
    fun looksLikeScreenshot(candidate: PurgeShotService.ScreenshotCandidate): Boolean {
        val rel = candidate.relativePath.orEmpty()
        val buck = candidate.bucketName.orEmpty()
        val path = candidate.filePath.orEmpty()
        val name = candidate.displayName
        val combined = "$rel/$buck/$path/$name"
        return name.contains("screenshot", ignoreCase = true) ||
            name.contains("screencap", ignoreCase = true) ||
            name.contains("screen_capture", ignoreCase = true) ||
            name.contains("screen shot", ignoreCase = true) ||
            rel.contains("screenshot", ignoreCase = true) ||
            buck.contains("screenshot", ignoreCase = true) ||
            path.contains("screenshot", ignoreCase = true) ||
            combined.contains("Screenshots", ignoreCase = true) ||
            combined.contains("ScreenCapture", ignoreCase = true) ||
            buck.equals("Screenshots", ignoreCase = true) ||
            rel.equals("DCIM/Screenshots", ignoreCase = true) ||
            rel.equals("Pictures/Screenshots", ignoreCase = true)
    }

    /** True if the candidate's timestamp is within the acceptable freshness window.
     *  @param isPoll true for WorkManager poll (allows 16min window to catch missed events) */
    fun isFreshEnough(candidate: PurgeShotService.ScreenshotCandidate, isPoll: Boolean = false): Boolean {
        val age = System.currentTimeMillis() - candidate.dateAddedMs
        val max = if (isPoll) MAX_AGE_POLL_MS else MAX_AGE_MS
        return age in MAX_FUTURE_SKEW_MS..max
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
        awaitSettle: Boolean = true,
        isPoll: Boolean = false
    ): Boolean {
        // CRITICAL FIX: default to true on DataStore read failure (fail open).
        // When JobScheduler/Accessibility cold-starts the process, DataStore may
        // throw before initialization completes. Defaulting to false silently dropped
        // every outside-Toolz screenshot. Fail open — the user explicitly enabled PurgeShot.
        val enabled = try { settingsRepository.purgeShotEnabled.first() } catch (_: Exception) { true }
        if (!enabled) {
            Log.d(TAG, "disabled, skip")
            return false
        }
        val hasPerm = hasMediaPermission(context)
        if (!hasPerm) {
            Log.w(TAG, "missing media permission — will attempt query anyway")
        }
        if (awaitSettle) delay(SETTLE_DELAY_MS)

        val recent = try {
            queryRecent(context.contentResolver, 8)
        } catch (e: SecurityException) {
            Log.w(TAG, "queryRecent SecurityException — missing permission", e)
            return false
        } catch (e: Exception) {
            Log.w(TAG, "queryRecent failed", e)
            return false
        }
        if (recent.isEmpty()) {
            Log.d(TAG, "no candidate")
            return false
        }
        val freshScreenshots = mutableListOf<PurgeShotService.ScreenshotCandidate>()
        val seenInThisBatch = mutableSetOf<String>()
        var staleReason: String? = null
        for (c in recent) {
            if (!isFreshEnough(c, isPoll)) {
                if (staleReason == null) staleReason = "age=${System.currentTimeMillis() - c.dateAddedMs}ms uri=${c.uri} isPoll=$isPoll"
                continue
            }
            if (!looksLikeScreenshot(c)) continue
            val uriStr = c.uri.toString()
            val path = c.filePath
            // Deduplicate within this detection batch by uri or path
            val dedupKey = path ?: uriStr
            if (dedupKey in seenInThisBatch) continue
            if (PurgeShotHandler.isAnyHandled(uriStr, path)) continue
            if (repository.hasEntry(uriStr)) {
                PurgeShotHandler.markUriHandled(uriStr)
                if (path != null) PurgeShotHandler.markUriHandled(path)
                continue
            }
            if (path != null && repository.hasEntryForPath(path)) {
                PurgeShotHandler.markUriHandled(uriStr)
                PurgeShotHandler.markUriHandled(path)
                continue
            }
            if (path != null && seenInThisBatch.contains(path)) continue
            if (seenInThisBatch.contains(uriStr)) continue
            freshScreenshots.add(c)
            seenInThisBatch.add(dedupKey)
            if (path != null) seenInThisBatch.add(path)
            seenInThisBatch.add(uriStr)
        }
        if (freshScreenshots.isEmpty()) {
            val top = recent.firstOrNull()
            if (top != null && !isFreshEnough(top, isPoll) && staleReason != null) {
                Log.d(TAG, "candidate stale, $staleReason")
            } else if (top != null) {
                Log.d(TAG, "no screenshot among recent ${recent.size}: ${recent.joinToString { it.displayName }}")
            }
            return false
        }

        // Hand off in chronological order (oldest to newest)
        for (candidate in freshScreenshots.reversed()) {
            PurgeShotHandler.handleNewScreenshot(context, repository, settingsRepository, candidate)
            Log.i(TAG, "handed off ${candidate.displayName} uri=${candidate.uri}")
        }
        return true
    }
}