/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Reborn engine: ONE filesystem walk feeds every analyzer via FileIndex.
 * Findings carry clearability: anything the device cannot delete is labeled,
 * never counted as cleanable, never reported as "failed".
 */

package com.frerox.toolz.data.cleaner.engine

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.SystemClock
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanFix
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.CleanResult
import com.frerox.toolz.data.cleaner.FailedItem
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.data.cleaner.analyzer.*
import com.frerox.toolz.data.cleaner.access.AccessGate
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import com.frerox.toolz.data.cleaner.trash.CleanerTrashDao
import com.frerox.toolz.data.cleaner.trash.CleanerTrashEntity
import com.frerox.toolz.data.cleaner.trash.TrashManager
import com.frerox.toolz.data.cleaner.util.FileUtils
import com.frerox.toolz.data.cleaner.util.StorageInfoProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

/** Max target so `pm trim-caches` releases everything the system can (a small target stops early). */
internal const val TRIM_CACHES_TARGET_BYTES = 999999999999999L

// Regex forbids quotes/slashes so interpolation is safe; confined to the two well-known cache dirs;
// deliberately NOT routed through FileUtils.isSafeToDelete (that gate is for shared storage,
// /data paths are handled here by strict construction).
internal fun appCacheRmCommand(pkg: String): String? {
    if (!pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) return null
    return "rm -rf '/data/data/$pkg/cache' '/data/data/$pkg/code_cache'"
}

@Singleton
class CleanerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageInfoProvider: StorageInfoProvider,
    private val systemJunkAnalyzer: SystemJunkAnalyzer,
    private val corpseAnalyzer: CorpseAnalyzer,
    private val appCacheAnalyzer: AppCacheAnalyzer,
    private val duplicateAnalyzer: DuplicateAnalyzer,
    private val largeAnalyzer: LargeFilesAnalyzer,
    private val apkAnalyzer: ApkAnalyzer,
    private val mediaAnalyzer: MediaClutterAnalyzer,
    private val trashDao: CleanerTrashDao,
    private val trashManager: TrashManager,
    private val shizukuExecutor: ShizukuShellExecutor,
    private val crawlEngine: CrawlEngine,
    private val exclusionStore: CleanerExclusionStore,
    private val accessGate: AccessGate
) {
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()
    private val _isShizukuGranted = MutableStateFlow(ShizukuHelper.isAuthorized())
    val isShizukuGranted: StateFlow<Boolean> = _isShizukuGranted.asStateFlow()
    fun refreshShizuku() { _isShizukuGranted.value = ShizukuHelper.isAuthorized() }
    private var scanJob: Job? = null
    private var scanConfig = CleanScanConfig()
    fun updateConfig(config: CleanScanConfig) { scanConfig = config }

    fun refreshStorageInfo(cleanable: Long? = null) {
        val info = storageInfoProvider.getStorageInfo(cleanable ?: _storageInfo.value.cleanableBytes)
        _storageInfo.value = info
    }

    fun analyzerCount(): Int = 6

    /** Last crawl, kept so cleaning can auto-tidy truly-empty folders. */
    private var lastIndex: FileIndex? = null
    /** Last scan telemetry, carried into [CleanResult] (scanMs/scannedFiles). */
    private var lastScanMs: Long = 0L
    private var lastScannedFiles: Int = 0

    fun startScan(scope: CoroutineScope) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            val scanStart = SystemClock.elapsedRealtime()
            val ownership = OwnershipRegistry()
            try {
                _scanState.value = ScanState.Scanning(currentCategory = "Preparing…", progress = 0.02f, filesScanned = 0)
                launch { try { trashManager.purgeExpired() } catch (_: Exception) {} }

                @Suppress("DEPRECATION")
                val root = try {
                    context.getExternalFilesDirs(null).firstOrNull()?.let {
                        var f: File? = it
                        repeat(4) { f = f?.parentFile }
                        f?.takeIf { it.exists() }
                    } ?: Environment.getExternalStorageDirectory()
                } catch (_: Exception) {
                    @Suppress("DEPRECATION") Environment.getExternalStorageDirectory()
                }
                val pm = context.packageManager
                val installed = try {
                    @Suppress("DEPRECATION") pm.getInstalledPackages(0).map { it.packageName }.toSet()
                } catch (_: Exception) { emptySet() }
                val exclusions = try { exclusionStore.exclusionsFlow.first() } catch (_: Exception) { emptySet() }

                // Phase 1: the single filesystem walk (0 → 0.45).
                _scanState.value = ScanState.Scanning("Looking through files…", 0, 0L, 0.03f)
                val index = crawlEngine.crawl(root, exclusions, scanConfig.includeHidden) { p ->
                    _scanState.value = ScanState.Scanning(
                        "Looking through files…", p.scanned, 0L,
                        (0.03f + (p.scanned.coerceAtMost(150_000) / 150_000f) * 0.42f).coerceIn(0f, 0.45f)
                    )
                }
                ensureActive()
                lastIndex = index

                // Phase 2: analyzers read the index (0.45 → 0.9). Only duplicates
                // touches disk again (hashing); everything else is in-memory.
                // Gate snapshot at scan start so analyzers banner only still-missing capabilities.
                val allFiles = try { accessGate.allFilesGranted() } catch (_: Exception) { true }
                val shizukuUsable = try { ShizukuHelper.isAuthorized() } catch (_: Exception) { false }
                val defs: List<Pair<CleanerAnalyzer, Float>> = listOf(
                    corpseAnalyzer to 0.50f, appCacheAnalyzer to 0.58f, mediaAnalyzer to 0.66f,
                    systemJunkAnalyzer to 0.72f, largeAnalyzer to 0.78f, apkAnalyzer to 0.82f,
                    duplicateAnalyzer to 0.90f
                )
                val results = supervisorScope {
                    defs.map { (analyzer, weight) ->
                        async(Dispatchers.IO) {
                            try {
                                ensureActive()
                                val ctx = ScanCtx(
                                    root = root, installed = installed, exclusions = exclusions,
                                    config = scanConfig, isActive = { isActive },
                                    progress = { msg ->
                                        _scanState.value = ScanState.Scanning(msg, index.scannedFiles, 0L, weight)
                                    },
                                    allFilesGranted = allFiles, shizukuUsable = shizukuUsable
                                )
                                analyzer.categoryId to Result.success(analyzer.analyze(index, ctx))
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                Log.w("CleanerEngine", "Analyzer ${analyzer.categoryId} failed: ${e.message}")
                                analyzer.categoryId to Result.failure<CleanCategory>(e)
                            }
                        }
                    }.awaitAll()
                }
                ensureActive()
                val byId = results.mapNotNull { (_, r) -> r.getOrNull() }.associateBy { it.id }
                // Installers + media review share one card.
                val merged = mergeInstallMedia(byId["apk"], byId["media_clutter"])
                val ordered = listOfNotNull(
                    byId["corpse"], byId["dupes"], byId["large"], merged,
                    byId["system_junk"], byId["app_cache"]
                )
                // Phase 3: ownership dedup — one path, one card, honest totals.
                var foundSize = 0L
                val deduped = mutableListOf<CleanCategory>()
                for (cat in ordered) {
                    if (cat.items.isEmpty()) {
                        // Keep blocked-empty cards only when actionable: a banner with no fix
                        // action is unactionable noise; analyzers needing user action set a fix label.
                        if (cat.blockedReason != null && cat.blockedFixLabel != null) deduped.add(cat)
                        continue
                    }
                    val kept = cat.items.filter { item ->
                        val paths = itemPaths(item)
                        if (paths.isEmpty()) return@filter true
                        var owned = true
                        for (p in paths) if (!ownership.claim(p)) { owned = false; break }
                        owned
                    }
                    if (kept.isEmpty()) {
                        // Same honesty filter: drop unactionable blocked cards (no fix label).
                        if (cat.blockedReason != null && cat.blockedFixLabel != null) deduped.add(cat)
                        continue
                    }
                    val total = kept.sumOf { itemSize(it) }
                    val sel = kept.sumOf { itemSelectedSize(it) }
                    deduped.add(cat.copy(items = kept, totalSize = total, selectedSize = sel))
                    foundSize += total
                }
                val totalCleanable = deduped.sumOf { it.totalSize }
                val selected = deduped.sumOf { it.selectedSize }
                val sorted = deduped.sortedByDescending { it.totalSize }
                lastScanMs = SystemClock.elapsedRealtime() - scanStart
                lastScannedFiles = index.scannedFiles
                refreshStorageInfo(totalCleanable)
                _scanState.value = ScanState.Results(
                    categories = sorted, totalCleanableBytes = totalCleanable,
                    selectedBytes = selected, filesScanned = index.scannedFiles,
                    truncated = index.truncated
                )
            } catch (e: CancellationException) { _scanState.value = ScanState.Idle }
            catch (e: Exception) { _scanState.value = ScanState.Error(e.localizedMessage ?: "Scan failed") }
        }
    }

    fun cancelScan() { scanJob?.cancel(); _scanState.value = ScanState.Idle }

    private fun mergeInstallMedia(apk: CleanCategory?, media: CleanCategory?): CleanCategory? {
        val items = (apk?.items.orEmpty() + media?.items.orEmpty())
            .sortedByDescending { itemSize(it) }
        if (items.isEmpty()) {
            val blocked = apk?.blockedReason ?: media?.blockedReason ?: return null
            return CleanCategory("install_media", "Installers & media", "Collections",
                emptyList(), 0L, 0L, false, description = "Old installers plus photos & videos to review",
                blockedReason = blocked, blockedFixLabel = "Fix")
        }
        return CleanCategory(
            id = "install_media", name = "Installers & media", icon = "Collections",
            items = items, totalSize = items.sumOf { itemSize(it) },
            selectedSize = items.sumOf { itemSelectedSize(it) }, isSafeToClean = false,
            description = "Old installers plus photos & videos to review",
            truncatedCount = (apk?.truncatedCount ?: 0) + (media?.truncatedCount ?: 0),
            blockedReason = apk?.blockedReason ?: media?.blockedReason,
            blockedFixLabel = (apk?.blockedFixLabel ?: media?.blockedFixLabel),
            skippedCount = 0
        )
    }

    private fun itemPaths(item: CleanItem): List<String> = when (item) {
        // Only SELECTED copies are claimed: the kept original stays visible to Large files.
        is CleanItem.Duplicate -> item.group.files.filter { it.isSelected }.map { it.path }
        is CleanItem.Corpse -> listOf(item.entry.path)
        is CleanItem.GenericFile -> listOf(item.file.path)
        is CleanItem.EmptyDir -> listOf(item.entry.path)
        is CleanItem.MediaFile -> listOf(item.entry.path)
        is CleanItem.ApkFile -> listOf(item.entry.path)
        is CleanItem.AppCache -> emptyList()
        is CleanItem.UnusedApp -> emptyList()
    }
    private fun itemSize(item: CleanItem): Long = when (item) {
        // Reclaimable math matches delete accounting (copies beyond kept oldest).
        is CleanItem.Duplicate -> item.group.files.count { it.isSelected } * item.group.sizeBytes
        is CleanItem.Corpse -> item.entry.sizeBytes
        is CleanItem.GenericFile -> item.file.sizeBytes
        is CleanItem.EmptyDir -> 0L
        is CleanItem.MediaFile -> item.entry.sizeBytes
        is CleanItem.ApkFile -> item.entry.sizeBytes
        is CleanItem.AppCache -> item.entry.cacheBytes
        is CleanItem.UnusedApp -> item.entry.sizeBytes
    }
    private fun itemSelectedSize(item: CleanItem): Long = when (item) {
        is CleanItem.Duplicate -> item.group.files.count { it.isSelected } * item.group.sizeBytes
        is CleanItem.Corpse -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
        is CleanItem.GenericFile -> if (item.file.isSelected) item.file.sizeBytes else 0L
        is CleanItem.EmptyDir -> 0L
        is CleanItem.MediaFile -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
        is CleanItem.ApkFile -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
        is CleanItem.AppCache -> if (item.entry.isSelected) item.entry.cacheBytes else 0L
        is CleanItem.UnusedApp -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
    }

    /** Paths the system won't let us delete even with All-files access. */
    private fun needsPrivilegedDelete(path: String): Boolean {
        val p = try { File(path).canonicalPath } catch (_: Exception) { path }
        return p.contains("/Android/obb/")
    }

    suspend fun deleteSelected(): CleanResult {
        val state = _scanState.value as? ScanState.Results
            ?: return CleanResult(scannedFiles = lastScannedFiles, scanMs = lastScanMs)
        return withContext(Dispatchers.IO) {
            val toDeleteFiles = mutableListOf<Triple<String, String, Long>>()
            val toDeleteDirs = mutableListOf<Triple<String, String, Long>>()
            var appCacheCount = 0
            state.categories.flatMap { it.items }.forEach { item ->
                when (item) {
                    is CleanItem.Duplicate -> item.group.files.filter { it.isSelected }
                        .forEach { toDeleteFiles.add(Triple(it.path, File(it.path).name, item.group.sizeBytes)) }
                    is CleanItem.Corpse -> if (item.entry.isSelected) toDeleteDirs.add(Triple(item.entry.path, item.entry.packageName, item.entry.sizeBytes))
                    is CleanItem.GenericFile -> if (item.file.isSelected) toDeleteFiles.add(Triple(item.file.path, item.file.name, item.file.sizeBytes))
                    is CleanItem.EmptyDir -> if (item.entry.isSelected) toDeleteDirs.add(Triple(item.entry.path, item.entry.name, 0L))
                    is CleanItem.MediaFile -> if (item.entry.isSelected) toDeleteFiles.add(Triple(item.entry.path, item.entry.name, item.entry.sizeBytes))
                    is CleanItem.ApkFile -> if (item.entry.isSelected) toDeleteFiles.add(Triple(item.entry.path, item.entry.name, item.entry.sizeBytes))
                    is CleanItem.AppCache -> if (item.entry.isSelected) appCacheCount++
                    is CleanItem.UnusedApp -> {}
                }
            }
            // Phase 0: auto-tidy truly-empty folders (0 bytes, instant, silent on failure).
            var emptiesRemoved = 0
            try {
                val empties = (lastIndex?.emptyDirs.orEmpty()).take(scanConfig.maxEmptyDirs)
                    .sortedByDescending { it.length }
                for (dirPath in empties) {
                    ensureActive()
                    try {
                        val dir = File(dirPath)
                        if (!dir.isDirectory) continue
                        if (!FileUtils.isSafeToDelete(dirPath)) continue
                        val kids = try { dir.list() } catch (_: Exception) { null } ?: continue
                        // Skip sentinel-only dirs even here — deleting .nomedia unhides media.
                        if (kids.isNotEmpty() && !kids.all { it == ".nomedia" || it == ".gitkeep" }) continue
                        if (kids.any { it == ".nomedia" || it == ".gitkeep" }) continue
                        if (dir.delete()) {
                            emptiesRemoved++
                            // Collapse chains: re-check the parent now that the child is gone.
                            val parent = dir.parentFile
                            if (parent != null && FileUtils.isSafeToDelete(parent.absolutePath)) {
                                val pl = try { parent.list() } catch (_: Exception) { null }
                                if (pl != null && pl.isEmpty() && parent.delete()) emptiesRemoved++
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            val totalOps = toDeleteFiles.size + toDeleteDirs.size + (if (appCacheCount > 0) 1 else 0)
            if (totalOps == 0) {
                refreshStorageInfo()
                val r = CleanResult(
                    emptyDirsRemoved = emptiesRemoved,
                    scannedFiles = lastScannedFiles, scanMs = lastScanMs
                )
                _scanState.value = ScanState.Done(r)
                return@withContext r
            }
            var deletedCount = 0; var clearedCount = 0; var alreadyCleanCount = 0; var freed = 0L; var trashed = 0
            val failedItems = mutableListOf<FailedItem>()
            suspend fun deleteOne(path: String, label: String, size: Long, type: String) {
                if (!FileUtils.isSafeToDelete(path)) {
                    failedItems.add(FailedItem(path, label, "Unsafe location — skipped for safety")); return
                }
                if (needsPrivilegedDelete(path)) {
                    failedItems.add(FailedItem(path, label,
                        "System protects this folder — needs Shizuku",
                        CleanFix.OPEN_SHIZUKU_SETUP)); return
                }
                val trashPath = try { trashManager.moveToTrash(path, type, size) } catch (_: Exception) { null }
                if (trashPath != null) {
                    deletedCount++; clearedCount++; freed += size; trashed++
                    notifyMediaChanged(path)
                    return
                }
                try {
                    val file = File(path)
                    if (!file.exists()) { alreadyCleanCount++; return }
                    val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (ok) {
                        deletedCount++; clearedCount++; freed += size
                        // No trash row: direct deletes are unrestorable by design;
                        // only moveToTrash() records restorable entries.
                        notifyMediaChanged(path)
                    } else {
                        failedItems.add(FailedItem(path, label, "Couldn't delete — it may be in use or protected"))
                        Log.w("CleanerEngine", "Failed delete $path")
                    }
                } catch (_: Exception) {
                    failedItems.add(FailedItem(path, label, "Couldn't delete — it may be in use or protected"))
                }
            }
            toDeleteFiles.forEachIndexed { idx, (path, label, size) ->
                ensureActive()
                _scanState.value = ScanState.Cleaning(idx.toFloat() / totalOps.coerceAtLeast(1), label)
                deleteOne(path, label, size, "file")
            }
            toDeleteDirs.forEachIndexed { idx, (path, label, size) ->
                ensureActive()
                _scanState.value = ScanState.Cleaning((toDeleteFiles.size + idx).toFloat() / totalOps.coerceAtLeast(1), label)
                deleteOne(path, label, size, "dir")
            }
            if (appCacheCount > 0) {
                var trimRan = false
                _scanState.value = ScanState.Cleaning(0.95f, "Trimming system caches…")
                if (ShizukuHelper.isAuthorized()) {
                    try {
                        val result = shizukuExecutor.executeForResult("pm trim-caches $TRIM_CACHES_TARGET_BYTES", timeoutMs = 90_000)
                        trimRan = true
                        Log.d("CleanerEngine", "trim-caches exit=${result.exitCode} out=${result.combinedOutput}")
                    } catch (_: Exception) {}
                }
                for (item in state.categories.flatMap { it.items }.filterIsInstance<CleanItem.AppCache>().filter { it.entry.isSelected }) {
                    val pkg = item.entry.packageName
                    val appLabel = item.entry.appName
                    val before = item.entry.cacheBytes
                    if (!pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
                        failedItems.add(FailedItem(pkg, appLabel, "Unrecognized package name — skipped for safety")); continue
                    }
                    _scanState.value = ScanState.Cleaning(0.95f, "Clearing $appLabel…")
                    var extCleared = false
                    var extSize = 0L
                    var extExisted = false
                    try {
                        @Suppress("DEPRECATION")
                        val extCache = File(Environment.getExternalStorageDirectory(), "Android/data/$pkg/cache")
                        if (extCache.exists()) {
                            extExisted = true
                            val sz = FileUtils.calculateDirSize(extCache)
                            val tp = trashManager.moveToTrash(extCache.absolutePath, "appcache", sz)
                            if (tp != null) { extCleared = true; extSize = sz }
                            else if (extCache.deleteRecursively()) { extCleared = true; extSize = sz }
                        }
                    } catch (_: Exception) {}
                    appCacheRmCommand(pkg)?.let { cmd ->
                        try { shizukuExecutor.executeForResult(cmd, timeoutMs = 15_000) } catch (_: Exception) {}
                    }
                    val after = queryCacheBytes(pkg)
                    val delta = if (after != null) (before - after).coerceAtLeast(0L) else 0L
                    when (decideAppCacheOutcome(extExisted, extCleared, extSize, before, delta)) {
                        AppCacheOutcome.CLEARED -> {
                            val freedApp = maxOf(if (extCleared) extSize else 0L, delta)
                            deletedCount++; clearedCount++; freed += freedApp
                            // Note: no trash row here — moveToTrash already recorded one
                            // when it moved the external cache; ghost rows are unrestorable.
                        }
                        AppCacheOutcome.ALREADY_CLEAN -> alreadyCleanCount++
                        AppCacheOutcome.NEEDS_AUTO -> failedItems.add(FailedItem(
                            pkg, appLabel,
                            if (trimRan) "System trim couldn't free this app's internal cache — try Auto-clear"
                            else "Internal cache only — needs Auto-clear or system Clear cache",
                            CleanFix.ENABLE_AUTO_CLEAR, pkg))
                        AppCacheOutcome.FAILED -> failedItems.add(FailedItem(
                            pkg, appLabel,
                            "Couldn't clear external cache — it may be in use", null, pkg))
                    }
                }
            }
            _scanState.value = ScanState.Cleaning(1f, "Finishing…")
            delay(300)
            refreshStorageInfo()
            val result = CleanResult(
                freed, clearedCount, failedItems.size, trashed, clearedCount,
                alreadyCleanCount, failedItems.toList(), emptiesRemoved,
                scannedFiles = lastScannedFiles, scanMs = lastScanMs
            )
            _scanState.value = ScanState.Done(result)
            result
        }
    }

    suspend fun undoLastClean(): Int = trashManager.restoreAll()

    /** MediaStore cleanup keyed by RELATIVE_PATH + DISPLAY_NAME with count==1 enforcement. */
    private fun notifyMediaChanged(path: String) {
        try { MediaScannerConnection.scanFile(context, arrayOf(path), null, null) } catch (_: Exception) {}
        try {
            val ext = path.substringAfterLast('.', "").lowercase()
            val collection = when (ext) {
                in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif") -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                in setOf("mp4", "mkv", "avi", "mov", "webm") -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                in setOf("mp3", "wav", "m4a", "ogg", "flac", "aac") -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> null
            } ?: return
            val file = File(path)
            val name = file.name
            val parentName = try { file.parentFile?.name } catch (_: Exception) { null } ?: return
            val id = context.contentResolver.query(
                collection, arrayOf(android.provider.MediaStore.MediaColumns._ID),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME}=? AND " +
                    "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "%$parentName%"), null
            )?.use { c ->
                if (c.count != 1 || !c.moveToFirst()) null else c.getLong(0)
            } ?: return
            val uri = android.content.ContentUris.withAppendedId(collection, id)
            context.contentResolver.delete(uri, null, null)
            // Confirm the row is actually gone.
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns._ID),
                null, null, null)?.use { c -> if (c.count > 0) Log.w("CleanerEngine", "MediaStore row survived for $path") }
        } catch (_: Exception) {}
    }

    /** Re-read an app's cache size after clearing; null when stats are unavailable. */
    private fun queryCacheBytes(pkg: String): Long? = try {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return null
        val pm = context.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        val mgr = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? android.app.usage.StorageStatsManager
            ?: return null
        mgr.queryStatsForPackage(ai.storageUuid, pkg, android.os.Process.myUserHandle()).cacheBytes
    } catch (_: Exception) { null }

    fun toggleSelection(categoryId: String, itemId: String) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updated = state.categories.map { cat ->
            if (cat.id == categoryId) {
                val newItems = cat.items.map { item ->
                    when (item) {
                        is CleanItem.Corpse -> if (item.entry.path == itemId) CleanItem.Corpse(item.entry.copy(isSelected = !item.entry.isSelected)) else item
                        is CleanItem.GenericFile -> if (item.file.path == itemId) CleanItem.GenericFile(item.file.copy(isSelected = !item.file.isSelected)) else item
                        is CleanItem.EmptyDir -> if (item.entry.path == itemId) CleanItem.EmptyDir(item.entry.copy(isSelected = !item.entry.isSelected)) else item
                        is CleanItem.MediaFile -> if (item.entry.path == itemId) CleanItem.MediaFile(item.entry.copy(isSelected = !item.entry.isSelected)) else item
                        is CleanItem.ApkFile -> if (item.entry.path == itemId) CleanItem.ApkFile(item.entry.copy(isSelected = !item.entry.isSelected)) else item
                        is CleanItem.AppCache -> if (item.entry.packageName == itemId) CleanItem.AppCache(item.entry.copy(isSelected = !item.entry.isSelected)) else item
                        is CleanItem.UnusedApp -> if (item.entry.packageName == itemId) CleanItem.UnusedApp(item.entry.copy(isSelected = !item.entry.isSelected)) else item
                        else -> item
                    }
                }
                cat.copy(items = newItems, selectedSize = calcSelected(newItems))
            } else cat
        }
        updateState(state, updated)
    }

    /** Single-emission batch select. */
    fun setCategorySelected(categoryId: String, selected: Boolean) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updated = state.categories.map { cat ->
            if (cat.id != categoryId) return@map cat
            cat.copy(items = applySelection(cat.items, selected), selectedSize = 0L).let {
                it.copy(selectedSize = calcSelected(it.items))
            }
        }
        updateState(state, updated)
    }

    fun setAllSelected(selected: Boolean) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updated = state.categories.map { cat ->
            val newItems = applySelection(cat.items, selected)
            cat.copy(items = newItems, selectedSize = calcSelected(newItems))
        }
        updateState(state, updated)
    }

    private fun applySelection(items: List<CleanItem>, selected: Boolean): List<CleanItem> =
        items.map { item ->
            when (item) {
                is CleanItem.Corpse -> CleanItem.Corpse(item.entry.copy(isSelected = selected))
                is CleanItem.GenericFile -> CleanItem.GenericFile(item.file.copy(isSelected = selected))
                is CleanItem.EmptyDir -> CleanItem.EmptyDir(item.entry.copy(isSelected = selected))
                is CleanItem.MediaFile -> CleanItem.MediaFile(item.entry.copy(isSelected = selected))
                is CleanItem.ApkFile -> CleanItem.ApkFile(item.entry.copy(isSelected = selected))
                is CleanItem.AppCache -> CleanItem.AppCache(item.entry.copy(isSelected = selected))
                is CleanItem.UnusedApp -> CleanItem.UnusedApp(item.entry.copy(isSelected = selected))
                is CleanItem.Duplicate -> {
                    // Keeper (oldest, index 0) is NEVER selected — wiping all copies is impossible.
                    val files = item.group.files.mapIndexed { idx, f -> f.copy(isSelected = if (selected) idx > 0 else false) }
                    CleanItem.Duplicate(item.group.copy(files = files))
                }
            }
        }

    fun isCategoryFullySelected(cat: CleanCategory): Boolean = cat.items.all { item ->
        when (item) {
            is CleanItem.Corpse -> item.entry.isSelected
            is CleanItem.GenericFile -> item.file.isSelected
            is CleanItem.EmptyDir -> item.entry.isSelected
            is CleanItem.MediaFile -> item.entry.isSelected
            is CleanItem.ApkFile -> item.entry.isSelected
            is CleanItem.AppCache -> item.entry.isSelected
            is CleanItem.UnusedApp -> item.entry.isSelected
            is CleanItem.Duplicate -> item.group.files.filterIndexed { idx, _ -> idx > 0 }.all { it.isSelected }
        }
    }

    fun areAllSelected(cats: List<CleanCategory>): Boolean =
        cats.isNotEmpty() && cats.all { isCategoryFullySelected(it) }

    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updated = state.categories.map { cat ->
            if (cat.id == categoryId) {
                val newItems = cat.items.map { item ->
                    if (item is CleanItem.Duplicate && item.group.hash == groupHash) {
                        val keeper = item.group.files.firstOrNull()?.path
                        val newFiles = item.group.files.map { f ->
                            if (f.path != path) f
                            // The kept original can only ever be deselected, never selected.
                            else if (f.path == keeper && !f.isSelected) f
                            else f.copy(isSelected = !f.isSelected)
                        }
                        CleanItem.Duplicate(item.group.copy(files = newFiles))
                    } else item
                }
                cat.copy(items = newItems, selectedSize = calcSelected(newItems))
            } else cat
        }
        updateState(state, updated)
    }
    private fun calcSelected(items: List<CleanItem>): Long = items.sumOf { item ->
        when (item) {
            is CleanItem.Duplicate -> item.group.files.filter { it.isSelected }.sumOf { item.group.sizeBytes }
            is CleanItem.Corpse -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
            is CleanItem.GenericFile -> if (item.file.isSelected) item.file.sizeBytes else 0L
            is CleanItem.EmptyDir -> 0L
            is CleanItem.MediaFile -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
            is CleanItem.ApkFile -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
            is CleanItem.AppCache -> if (item.entry.isSelected) item.entry.cacheBytes else 0L
            is CleanItem.UnusedApp -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
        }
    }
    private fun updateState(old: ScanState.Results, newCats: List<CleanCategory>) {
        val sel = newCats.sumOf { it.selectedSize }
        _scanState.value = old.copy(categories = newCats, selectedBytes = sel)
    }
    fun resetState() { _scanState.value = ScanState.Idle }
}

/** V4 honest AppCache accounting — pure decision table, unit tested. */
internal enum class AppCacheOutcome { CLEARED, ALREADY_CLEAN, NEEDS_AUTO, FAILED }

internal fun decideAppCacheOutcome(
    extExisted: Boolean,
    extCleared: Boolean,
    extSize: Long,
    reportedBefore: Long,
    measuredDelta: Long
): AppCacheOutcome {
    val freed = maxOf(if (extCleared) extSize else 0L, measuredDelta)
    return when {
        extCleared || freed > 0 -> AppCacheOutcome.CLEARED
        !extExisted && reportedBefore <= 0L -> AppCacheOutcome.ALREADY_CLEAN
        !extExisted -> AppCacheOutcome.NEEDS_AUTO
        else -> AppCacheOutcome.FAILED
    }
}
