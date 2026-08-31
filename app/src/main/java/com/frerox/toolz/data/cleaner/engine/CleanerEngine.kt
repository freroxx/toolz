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

package com.frerox.toolz.data.cleaner.engine

import android.content.Context
import android.os.Environment
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.CleanResult
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.data.cleaner.analyzer.*
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import com.frerox.toolz.data.cleaner.trash.CleanerTrashDao
import com.frerox.toolz.data.cleaner.trash.CleanerTrashEntity
import com.frerox.toolz.data.cleaner.util.FileUtils
import com.frerox.toolz.data.cleaner.util.StorageInfoProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class CleanerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageInfoProvider: StorageInfoProvider,
    private val systemJunkAnalyzer: SystemJunkAnalyzer,
    private val corpseAnalyzer: CorpseAnalyzer,
    private val emptyDirAnalyzer: EmptyDirAnalyzer,
    private val thumbAnalyzer: ThumbnailCacheAnalyzer,
    private val appCacheAnalyzer: AppCacheAnalyzer,
    private val duplicateAnalyzer: DuplicateAnalyzer,
    private val largeAnalyzer: LargeFilesAnalyzer,
    private val apkAnalyzer: ApkAnalyzer,
    private val mediaAnalyzer: MediaClutterAnalyzer,
    private val databaseJunkAnalyzer: DatabaseJunkAnalyzer,
    private val trashDao: CleanerTrashDao,
    private val shizukuExecutor: ShizukuShellExecutor,
    private val exclusionStore: CleanerExclusionStore
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
    fun refreshStorageInfo(cleanable: Long? = null) {
        val info = storageInfoProvider.getStorageInfo(cleanable ?: _storageInfo.value.cleanableBytes)
        _storageInfo.value = info
    }
    fun startScan(scope: CoroutineScope) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            try {
                _scanState.value = ScanState.Scanning(currentCategory = "Preparing…", progress = 0.02f, filesScanned = 0)
                val root = Environment.getExternalStorageDirectory()
                val pm = context.packageManager
                val installed = try { pm.getInstalledPackages(0).map { it.packageName }.toSet() } catch (_: Exception) { emptySet() }
                val exclusions = try { exclusionStore.exclusionsFlow.first() } catch (_: Exception) { emptySet() }
                val analyzers: List<CleanerAnalyzer> = listOf(systemJunkAnalyzer, corpseAnalyzer, emptyDirAnalyzer, thumbAnalyzer, appCacheAnalyzer, duplicateAnalyzer, largeAnalyzer, apkAnalyzer, mediaAnalyzer, databaseJunkAnalyzer)
                val categories = mutableListOf<CleanCategory>()
                var foundSize = 0L
                for ((idx, analyzer) in analyzers.withIndex()) {
                    ensureActive()
                    val progBase = 0.05f + (idx.toFloat() / analyzers.size) * 0.9f
                    _scanState.value = ScanState.Scanning(currentCategory = analyzer.categoryName, filesScanned = categories.sumOf { it.items.size }, foundSize = foundSize, progress = progBase)
                    try {
                        val cat = analyzer.analyze(root = root, installedPackages = installed, progress = { msg -> _scanState.value = ScanState.Scanning(msg, categories.sumOf { it.items.size }, foundSize, progBase) }, exclusions = exclusions, isActive = { isActive }, config = scanConfig)
                        if (cat.items.isNotEmpty()) {
                            val withFlag = when (analyzer.categoryId) {
                                "app_cache", "db_junk" -> cat.copy(requiresShizuku = true)
                                else -> cat
                            }
                            categories.add(withFlag); foundSize += withFlag.totalSize
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w("CleanerEngine", "Analyzer ${analyzer.categoryId} failed: ${e.message}") }
                }
                val totalCleanable = categories.sumOf { it.totalSize }
                val selected = categories.sumOf { it.selectedSize }
                val sorted = categories.sortedByDescending { it.totalSize }
                refreshStorageInfo(totalCleanable)
                _storageInfo.update { it.copy(cleanableBytes = totalCleanable) }
                _scanState.value = ScanState.Results(categories = sorted, totalCleanableBytes = totalCleanable, selectedBytes = selected, filesScanned = categories.sumOf { it.items.size })
            } catch (e: CancellationException) { _scanState.value = ScanState.Idle } catch (e: Exception) { _scanState.value = ScanState.Error(e.localizedMessage ?: "Scan failed") }
        }
    }
    fun cancelScan() { scanJob?.cancel(); _scanState.value = ScanState.Idle }
    suspend fun deleteSelected() {
        val state = _scanState.value as? ScanState.Results ?: return
        withContext(Dispatchers.IO) {
            val toDeleteFiles = mutableListOf<Pair<String, Long>>()
            val toDeleteDirs = mutableListOf<Pair<String, Long>>()
            var appCacheCount = 0
            state.categories.flatMap { it.items }.forEach { item ->
                when (item) {
                    is CleanItem.Duplicate -> item.group.files.filter { it.isSelected }.forEach { toDeleteFiles.add(it.path to item.group.sizeBytes) }
                    is CleanItem.Corpse -> if (item.entry.isSelected) toDeleteDirs.add(item.entry.path to item.entry.sizeBytes)
                    is CleanItem.GenericFile -> if (item.file.isSelected) toDeleteFiles.add(item.file.path to item.file.sizeBytes)
                    is CleanItem.EmptyDir -> if (item.entry.isSelected) toDeleteDirs.add(item.entry.path to 0L)
                    is CleanItem.MediaFile -> if (item.entry.isSelected) toDeleteFiles.add(item.entry.path to item.entry.sizeBytes)
                    is CleanItem.ApkFile -> if (item.entry.isSelected) toDeleteFiles.add(item.entry.path to item.entry.sizeBytes)
                    is CleanItem.AppCache -> if (item.entry.isSelected) appCacheCount++
                    is CleanItem.UnusedApp -> {}
                }
            }
            val totalOps = toDeleteFiles.size + toDeleteDirs.size + (if (appCacheCount>0) 1 else 0)
            if (totalOps == 0 && appCacheCount==0) { _scanState.value = ScanState.Done(CleanResult(0,0,0)); return@withContext }
            var deletedCount = 0; var failedCount = 0; var freed = 0L
            toDeleteFiles.forEachIndexed { idx, (path, size) ->
                ensureActive()
                _scanState.value = ScanState.Cleaning(idx.toFloat() / totalOps.coerceAtLeast(1), path)
                try {
                    if (!FileUtils.isSafeToDelete(path)) { failedCount++; return@forEachIndexed }
                    val file = File(path)
                    val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (ok) { deletedCount++; freed += size; try { trashDao.insert(CleanerTrashEntity(originalPath = path, sizeBytes = size, type = "file")) } catch(_:Exception){} } else { failedCount++; Log.w("CleanerEngine","Failed delete $path") }
                } catch(_:Exception){ failedCount++ }
            }
            toDeleteDirs.forEachIndexed { idx, (path, size) ->
                ensureActive()
                _scanState.value = ScanState.Cleaning((toDeleteFiles.size+idx).toFloat() / totalOps.coerceAtLeast(1), path)
                try {
                    if (!FileUtils.isSafeToDelete(path)) { failedCount++; return@forEachIndexed }
                    val f = File(path)
                    val ok = f.deleteRecursively()
                    if (ok) { deletedCount++; freed += size; try { trashDao.insert(CleanerTrashEntity(originalPath = path, sizeBytes = size, type = "dir")) } catch(_:Exception){} } else failedCount++
                } catch(_:Exception){ failedCount++ }
            }
            if (appCacheCount>0) {
                _scanState.value = ScanState.Cleaning(0.95f, "Clearing app caches…")
                val shizukuGranted = ShizukuHelper.isAuthorized()
                for (item in state.categories.flatMap { it.items }.filterIsInstance<CleanItem.AppCache>().filter { it.entry.isSelected }) {
                    var cleared = false
                    var size = item.entry.cacheBytes
                    if (shizukuGranted) {
                        try {
                            val res1 = shizukuExecutor.executeForResult("rm -rf /data/data/${item.entry.packageName}/cache/*")
                            val res2 = shizukuExecutor.executeForResult("rm -rf /data/data/${item.entry.packageName}/code_cache/*")
                            if (res1.isSuccess || res2.isSuccess) { cleared = true }
                            val extCache = File(Environment.getExternalStorageDirectory(), "Android/data/${item.entry.packageName}/cache")
                            if (extCache.exists()) { val sz = FileUtils.calculateDirSize(extCache); if (extCache.deleteRecursively()) { cleared = true; size = maxOf(size, sz) } }
                        } catch(_:Exception){}
                    } else {
                        try {
                            val extCache = File(Environment.getExternalStorageDirectory(), "Android/data/${item.entry.packageName}/cache")
                            if (extCache.exists()) { val sz = FileUtils.calculateDirSize(extCache); if (extCache.deleteRecursively()) { cleared = true; size = sz } }
                        } catch(_:Exception){}
                    }
                    if (cleared) { deletedCount++; freed += size; try { trashDao.insert(CleanerTrashEntity(originalPath = item.entry.packageName, sizeBytes = size, type = "appcache")) } catch(_:Exception){} } else { failedCount++ }
                }
            }
            _scanState.value = ScanState.Cleaning(1f, "Finishing…")
            delay(400)
            refreshStorageInfo()
            _scanState.value = ScanState.Done(CleanResult(freed, deletedCount, failedCount))
        }
    }
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
    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updated = state.categories.map { cat ->
            if (cat.id == categoryId) {
                val newItems = cat.items.map { item ->
                    if (item is CleanItem.Duplicate && item.group.hash == groupHash) {
                        val newFiles = item.group.files.map { f -> if (f.path == path) f.copy(isSelected = !f.isSelected) else f }
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
