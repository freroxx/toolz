package com.frerox.toolz.data.cleaner

import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.Log
import android.provider.MediaStore
import android.text.format.Formatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CleanerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private var scanJob: Job? = null

    fun refreshStorageInfo() {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            _storageInfo.value = StorageInfo(
                totalBytes = total,
                usedBytes = total - free,
                freeBytes = free
            )
        } catch (e: Exception) { Log.e("CleanerRepository", "Error refreshing storage info: ${e.message}") }
    }

    fun startScan(scope: CoroutineScope) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            try {
                _scanState.value = ScanState.Scanning(currentCategory = "Initializing Engine...", progress = 0.05f)

                val root = Environment.getExternalStorageDirectory()
                val systemJunk = mutableListOf<FileEntry>()
                val largeFiles = mutableListOf<FileEntry>()
                val documentFiles = mutableListOf<FileEntry>()
                val sizeMap = HashMap<Long, MutableList<File>>(65536)
                var scannedCount = 0

                val junkExtensions = setOf(
                    "log", "tmp", "temp", "cache", "chk", "error", "dmp", "crash", "part", "crdownload", "old", "bak", "thumb", "db-journal",
                    "tombstone", "apk.analytics", "obbtemp", "tmp_video", "exo", "fb_temp", "apk.part"
                )
                
                val junkPatterns = listOf(
                    "cache", "cached", "temp", "tmp", "logs", ".thumbnails", "lost+found", 
                    "BugReport", "sent", "diagnostics", "crash_reports", "UnityAdsCache", 
                    "GmsCoreConfigCache", "fb_temp", ".exo", "vungle.cache", "fresco_cache",
                    "video_cache", "image_cache", ".Fabric", "leakcanary", "bugly", "crashlytics"
                )
                
                val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "json", "xml", "html")
                val mediaExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "mp3", "wav", "m4a", "ogg", "flac")
                val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
                val largeFileSizeThreshold = 100 * 1024 * 1024L 

                _scanState.value = ScanState.Scanning(currentCategory = "Crawling Storage...", progress = 0.1f)
                
                root.walkTopDown()
                    .onEnter { dir -> 
                        val name = dir.name
                        if (name == "Android" && dir.parentFile == root) return@onEnter true
                        // Skip sensitive directories
                        if (name.startsWith(".") && name != ".thumbnails") return@onEnter false
                        true 
                    }
                    .forEach { file ->
                        ensureActive()
                        scannedCount++
                        
                        if (file.isFile) {
                            val size = file.length()
                            val ext = file.extension.lowercase()
                            
                            if (size > 1024) { 
                                sizeMap.getOrPut(size) { mutableListOf() }.add(file)
                            }

                            val isJunkPath = junkPatterns.any { file.absolutePath.contains(it, ignoreCase = true) }
                            val isMedia = imageExtensions.contains(ext) || mediaExtensions.contains(ext)
                            val isDocument = documentExtensions.contains(ext)
                            
                            if ((junkExtensions.contains(ext) || isJunkPath) && !isMedia && !isDocument) {
                                systemJunk.add(file.toEntry(isSelected = true))
                            } else if (isDocument) {
                                documentFiles.add(file.toEntry(isSelected = false))
                            }

                            if (size > largeFileSizeThreshold) {
                                largeFiles.add(file.toEntry(isSelected = false))
                            }
                        } else if (file.isDirectory) {
                            val children = file.list()
                            if (children != null && children.isEmpty() && file != root) {
                                systemJunk.add(file.toEntry(isSelected = true))
                            }
                        }

                        if (scannedCount % 2000 == 0) {
                            _scanState.value = ScanState.Scanning(
                                currentCategory = "Found: ${Formatter.formatFileSize(context, calculateFoundSize(systemJunk, largeFiles, documentFiles))}",
                                filesScanned = scannedCount,
                                foundSize = calculateFoundSize(systemJunk, largeFiles, documentFiles),
                                progress = 0.1f + (scannedCount.toFloat() / 150000f).coerceAtMost(0.4f)
                            )
                        }
                    }

                _scanState.value = ScanState.Scanning(currentCategory = "Analyzing Duplicates...", progress = 0.55f)
                val duplicateGroups = mutableListOf<DuplicateGroup>()
                val sizeMapFiltered = sizeMap.filter { it.value.size >= 2 }
                var processedSizes = 0
                
                sizeMapFiltered.forEach { (size, files) ->
                    ensureActive()
                    val partialHashGroups = HashMap<String, MutableList<File>>()
                    for (file in files) {
                        val pHash = computeQuickHash(file)
                        if (pHash != null) {
                            partialHashGroups.getOrPut(pHash) { mutableListOf() }.add(file)
                        }
                    }

                    partialHashGroups.filter { it.value.size >= 2 }.forEach { (_, potentialDupes) ->
                        val finalHashGroups = HashMap<String, MutableList<File>>()
                        for (file in potentialDupes) {
                            val fHash = computeFullHash(file)
                            if (fHash != null) {
                                finalHashGroups.getOrPut(fHash) { mutableListOf() }.add(file)
                            }
                        }

                        finalHashGroups.filter { it.value.size >= 2 }.forEach { (hash, dupes) ->
                            val sorted = dupes.sortedBy { it.lastModified() }
                            duplicateGroups.add(DuplicateGroup(
                                hash = hash,
                                sizeBytes = size,
                                files = sorted.mapIndexed { index, f ->
                                    DuplicateFile(f.absolutePath, f.lastModified(), index > 0)
                                }
                            ))
                        }
                    }
                    
                    processedSizes++
                    if (processedSizes % 100 == 0 || processedSizes == sizeMapFiltered.size) {
                        _scanState.value = ScanState.Scanning(
                            currentCategory = "Fingerprinting Duplicates...",
                            filesScanned = scannedCount,
                            progress = 0.55f + (processedSizes.toFloat() / sizeMapFiltered.size.coerceAtLeast(1) * 0.2f)
                        )
                    }
                }

                _scanState.value = ScanState.Scanning(currentCategory = "Scanning App Leftovers...", progress = 0.8f)
                val corpseEntries = mutableListOf<CorpseEntry>()
                val pm = context.packageManager
                val installedPackages = try {
                    pm.getInstalledPackages(0).map { it.packageName }.toSet()
                } catch (_: Exception) { emptySet() }

                listOf("Android/data", "Android/obb", "Android/media", "Android/obj").forEach { path ->
                    val baseDir = File(root, path)
                    if (!baseDir.exists()) return@forEach
                    
                    baseDir.listFiles()?.filter { it.isDirectory && !installedPackages.contains(it.name) }?.forEach { dir ->
                        if (dir.name.startsWith("com.android.") || dir.name.startsWith("com.google.android.")) return@forEach
                        
                        val size = calculateDirSize(dir)
                        if (size > 0) {
                            corpseEntries.add(CorpseEntry(
                                packageName = dir.name,
                                path = dir.absolutePath,
                                sizeBytes = size,
                                type = if (path.contains("obb")) CorpseType.OBB else CorpseType.DATA,
                                isSelected = true
                            ))
                        }
                    }
                }

                _scanState.value = ScanState.Scanning(currentCategory = "Analyzing App Usage...", progress = 0.9f)
                val unusedApps = getUnusedApps()

                val categories = mutableListOf<CleanCategory>()
                
                if (systemJunk.isNotEmpty()) {
                    categories.add(CleanCategory(
                        id = "temp",
                        name = "System Junk",
                        icon = "DeleteSweep",
                        items = systemJunk.sortedByDescending { it.sizeBytes }.map { CleanItem.GenericFile(it) },
                        totalSize = systemJunk.sumOf { it.sizeBytes },
                        isSafeToClean = true
                    ))
                }

                if (corpseEntries.isNotEmpty()) {
                    categories.add(CleanCategory(
                        id = "corpse",
                        name = "App Leftovers",
                        icon = "AutoDelete",
                        items = corpseEntries.sortedByDescending { it.sizeBytes }.map { CleanItem.Corpse(it) },
                        totalSize = corpseEntries.sumOf { it.sizeBytes },
                        isSafeToClean = true
                    ))
                }

                if (duplicateGroups.isNotEmpty()) {
                    val totalDupesSize = duplicateGroups.sumOf { g -> g.files.filter { it.isSelected }.sumOf { g.sizeBytes } }
                    categories.add(CleanCategory(
                        id = "dupes",
                        name = "Duplicate Files",
                        icon = "FileCopy",
                        items = duplicateGroups.sortedByDescending { it.sizeBytes * it.files.size }.map { CleanItem.Duplicate(it) },
                        totalSize = totalDupesSize,
                        isSafeToClean = false
                    ))
                }

                if (largeFiles.isNotEmpty()) {
                    categories.add(CleanCategory(
                        id = "large",
                        name = "Large Files",
                        icon = "Straighten",
                        items = largeFiles.sortedByDescending { it.sizeBytes }.map { CleanItem.GenericFile(it) },
                        totalSize = largeFiles.filter { it.isSelected }.sumOf { it.sizeBytes },
                        isSafeToClean = false
                    ))
                }

                if (unusedApps.isNotEmpty()) {
                    categories.add(CleanCategory(
                        id = "unused_apps",
                        name = "Unused Apps",
                        icon = "AppSettingsAlt",
                        items = unusedApps.map { CleanItem.UnusedApp(it) },
                        totalSize = unusedApps.filter { it.isSelected }.sumOf { it.sizeBytes },
                        isSafeToClean = false
                    ))
                }

                if (documentFiles.isNotEmpty()) {
                    categories.add(CleanCategory(
                        id = "documents",
                        name = "Documents",
                        icon = "Description",
                        items = documentFiles.sortedByDescending { it.sizeBytes }.map { CleanItem.GenericFile(it) },
                        totalSize = documentFiles.filter { it.isSelected }.sumOf { it.sizeBytes },
                        isSafeToClean = false
                    ))
                }

                val totalCleanable = categories.sumOf { it.totalSize }
                refreshStorageInfo()
                _storageInfo.update { it.copy(cleanableBytes = totalCleanable) }

                _scanState.value = ScanState.Results(
                    categories = categories.sortedByDescending { it.totalSize },
                    totalCleanableBytes = totalCleanable,
                    filesScanned = scannedCount
                )

            } catch (e: CancellationException) {
                _scanState.value = ScanState.Idle
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.localizedMessage ?: "Scan failed")
            }
        }
    }

    private fun getUnusedApps(): List<UnusedAppEntry> {
        val result = mutableListOf<UnusedAppEntry>()
        val pm = context.packageManager
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (30L * 24 * 60 * 60 * 1000)
        
        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val storageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else null

        for (app in installedApps) {
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
            if (app.packageName == context.packageName) continue
            
            val lastUsed = stats[app.packageName]?.lastTimeUsed ?: 0L
            val isUnused = lastUsed == 0L || lastUsed < startTime
            
            if (isUnused) {
                var size = 0L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && storageStatsManager != null) {
                    try {
                        val uuid = if (app.storageUuid == StorageManager.UUID_DEFAULT) StorageManager.UUID_DEFAULT else app.storageUuid
                        val appStats = storageStatsManager.queryStatsForPackage(uuid, app.packageName, android.os.Process.myUserHandle())
                        size = appStats.appBytes + appStats.dataBytes + appStats.cacheBytes
                    } catch (_: Exception) {}
                }
                
                result.add(UnusedAppEntry(
                    packageName = app.packageName,
                    appName = pm.getApplicationLabel(app).toString(),
                    sizeBytes = size,
                    lastUsed = lastUsed,
                    icon = pm.getApplicationIcon(app),
                    isSelected = false
                ))
            }
        }
        return result.sortedBy { it.lastUsed }
    }

    private fun calculateFoundSize(systemJunk: List<FileEntry>, large: List<FileEntry>, documents: List<FileEntry>): Long {
        return systemJunk.sumOf { it.sizeBytes } + 
               large.filter { it.isSelected }.sumOf { it.sizeBytes } +
               documents.filter { it.isSelected }.sumOf { it.sizeBytes }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = ScanState.Idle
    }

    suspend fun deleteSelected() {
        val state = _scanState.value as? ScanState.Results ?: return
        withContext(Dispatchers.IO) {
            val allItems = state.categories.flatMap { it.items }
            val toDeleteFiles = mutableListOf<Pair<String, Long>>()
            val toUninstallApps = mutableListOf<String>()

            allItems.forEach { item ->
                when (item) {
                    is CleanItem.Duplicate -> {
                        item.group.files.filter { it.isSelected }.forEach { toDeleteFiles.add(it.path to item.group.sizeBytes) }
                    }
                    is CleanItem.Corpse -> {
                        if (item.entry.isSelected) toDeleteFiles.add(item.entry.path to item.entry.sizeBytes)
                    }
                    is CleanItem.GenericFile -> {
                        if (item.file.isSelected) toDeleteFiles.add(item.file.path to item.file.sizeBytes)
                    }
                    is CleanItem.UnusedApp -> {
                        if (item.entry.isSelected) toUninstallApps.add(item.entry.packageName)
                    }
                }
            }

            if (toDeleteFiles.isEmpty() && toUninstallApps.isEmpty()) {
                _scanState.value = ScanState.Done(CleanResult(0, 0, 0))
                return@withContext
            }

            var deletedCount = 0
            var failedCount = 0
            var freedBytes = 0L

            toDeleteFiles.forEachIndexed { index, (path, size) ->
                ensureActive()
                _scanState.value = ScanState.Cleaning(index.toFloat() / (toDeleteFiles.size + toUninstallApps.size).coerceAtLeast(1), path)
                
                try {
                    val file = File(path)
                    val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (success) {
                        deletedCount++
                        freedBytes += size
                    } else { Log.w("CleanerRepository", "Failed to delete file: $path"); failedCount++ }
                } catch (_: Exception) { failedCount++ }
            }
            
            _scanState.value = ScanState.Cleaning(1f, "Finishing up...")
            delay(500)

            refreshStorageInfo()
            _scanState.value = ScanState.Done(CleanResult(freedBytes, deletedCount, failedCount))
        }
    }

    fun toggleSelection(categoryId: String, itemId: String) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updatedCategories = state.categories.map { cat ->
            if (cat.id == categoryId) {
                val updatedItems = cat.items.map { item ->
                    when (item) {
                        is CleanItem.Corpse -> {
                            if (item.entry.path == itemId) CleanItem.Corpse(item.entry.copy(isSelected = !item.entry.isSelected))
                            else item
                        }
                        is CleanItem.GenericFile -> {
                            if (item.file.path == itemId) CleanItem.GenericFile(item.file.copy(isSelected = !item.file.isSelected))
                            else item
                        }
                        is CleanItem.UnusedApp -> {
                            if (item.entry.packageName == itemId) CleanItem.UnusedApp(item.entry.copy(isSelected = !item.entry.isSelected))
                            else item
                        }
                        else -> item
                    }
                }
                cat.copy(items = updatedItems, totalSize = calculateCategorySize(updatedItems))
            } else cat
        }
        updateStateWithNewCategories(state, updatedCategories)
    }

    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updatedCategories = state.categories.map { cat ->
            if (cat.id == categoryId) {
                val updatedItems = cat.items.map { item ->
                    if (item is CleanItem.Duplicate && item.group.hash == groupHash) {
                        val updatedFiles = item.group.files.map { f ->
                            if (f.path == path) f.copy(isSelected = !f.isSelected) else f
                        }
                        CleanItem.Duplicate(item.group.copy(files = updatedFiles))
                    } else item
                }
                cat.copy(items = updatedItems, totalSize = calculateCategorySize(updatedItems))
            } else cat
        }
        updateStateWithNewCategories(state, updatedCategories)
    }

    private fun updateStateWithNewCategories(oldState: ScanState.Results, newCategories: List<CleanCategory>) {
        val total = newCategories.sumOf { it.totalSize }
        _scanState.value = oldState.copy(categories = newCategories, totalCleanableBytes = total)
        _storageInfo.update { it.copy(cleanableBytes = total) }
    }

    private fun calculateCategorySize(items: List<CleanItem>): Long {
        return items.sumOf { item ->
            when (item) {
                is CleanItem.Duplicate -> item.group.files.filter { it.isSelected }.sumOf { item.group.sizeBytes }
                is CleanItem.Corpse -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
                is CleanItem.GenericFile -> if (item.file.isSelected) item.file.sizeBytes else 0L
                is CleanItem.UnusedApp -> if (item.entry.isSelected) item.entry.sizeBytes else 0L
            }
        }
    }

    fun resetState() {
        _scanState.value = ScanState.Idle
    }

    private fun File.toEntry(isSelected: Boolean): FileEntry {
        val ext = extension.lowercase()
        return FileEntry(
            name = name,
            path = absolutePath,
            sizeBytes = length(),
            lastModified = lastModified(),
            extension = ext,
            isSelected = isSelected,
            thumbnailUri = getMediaStoreUri(context, absolutePath, ext) ?: absolutePath
        )
    }

    private fun getMediaStoreUri(context: Context, path: String, ext: String): String? {
        val collection = when (ext) {
            "mp3", "wav", "m4a", "ogg", "flac" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            "pdf" -> MediaStore.Files.getContentUri("external")
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "mp4", "mkv", "avi", "mov", "webm" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }
        
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = MediaStore.MediaColumns.DATA + "=?"
        val selectionArgs = arrayOf(path)
        
        return try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()
                } else null
            }
        } catch (e: Exception) { null }
    }

    private fun computeFullHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }

    private fun computeQuickHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val size = file.length()
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(65536)
                val readStart = fis.read(buffer)
                if (readStart > 0) digest.update(buffer, 0, readStart)
                
                if (size > 65536 * 2) {
                    fis.channel.position(size - 65536)
                    val readEnd = fis.read(buffer)
                    if (readEnd > 0) digest.update(buffer, 0, readEnd)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        try { dir.walkTopDown().filter { it.isFile }.forEach { size += it.length() } } catch (_: Exception) { }
        return size
    }
}
