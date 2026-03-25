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
                val tempFiles = mutableListOf<FileEntry>()
                val largeFiles = mutableListOf<FileEntry>()
                val emptyDirs = mutableListOf<FileEntry>()
                val mediaFiles = mutableListOf<FileEntry>()
                val documentFiles = mutableListOf<FileEntry>()
                val sizeMap = HashMap<Long, MutableList<File>>(16384)
                var scannedCount = 0

                val junkExtensions = setOf(
                    "log", "tmp", "temp", "cache", "chk", "error", "dmp", "crash", "part", "crdownload"
                )
                
                val cachePatterns = listOf("cache", "cached", "temp", "tmp", "logs")
                
                val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf")
                val mediaExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "mp3", "wav", "m4a", "ogg", "flac")
                val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
                val largeFileSizeThreshold = 100 * 1024 * 1024L 

                _scanState.value = ScanState.Scanning(currentCategory = "Scanning Storage...", progress = 0.1f)
                
                root.walkTopDown()
                    .onEnter { dir -> 
                        val isHidden = dir.name.startsWith(".") && dir != root
                        !isHidden || dir.name == ".cache"
                    }
                    .forEach { file ->
                        ensureActive()
                        scannedCount++
                        
                        if (file.isFile) {
                            val size = file.length()
                            val ext = file.extension.lowercase()
                            
                            if (size > 50 * 1024) { 
                                sizeMap.getOrPut(size) { mutableListOf() }.add(file)
                            }

                            val isJunkPath = cachePatterns.any { file.absolutePath.contains(it, ignoreCase = true) }
                            val isMedia = imageExtensions.contains(ext) || mediaExtensions.contains(ext)
                            val isDocument = documentExtensions.contains(ext)
                            
                            if ((junkExtensions.contains(ext) || isJunkPath) && !isMedia && !isDocument) {
                                tempFiles.add(file.toEntry(isSelected = true))
                            }
                            if (isDocument) {
                                documentFiles.add(file.toEntry(isSelected = false))
                            }

                            if (size > largeFileSizeThreshold) {
                                largeFiles.add(file.toEntry(isSelected = false))
                            }
                        } else if (file.isDirectory) {
                            val children = file.list()
                            if (children != null && children.isEmpty()) {
                                emptyDirs.add(file.toEntry(isSelected = true))
                            }
                        }

                        if (scannedCount % 500 == 0) {
                            _scanState.value = ScanState.Scanning(
                                currentCategory = "Crawling: ${file.name.take(20)}...",
                                filesScanned = scannedCount,
                                foundSize = calculateFoundSize(tempFiles, largeFiles, mediaFiles, documentFiles, emptyDirs),
                                progress = 0.1f + (scannedCount.toFloat() / 30000f).coerceAtMost(0.4f)
                            )
                        }
                    }

                _scanState.value = ScanState.Scanning(currentCategory = "Analyzing Duplicates...", progress = 0.55f)
                val duplicateGroups = mutableListOf<DuplicateGroup>()
                val sizeMapFiltered = sizeMap.filter { it.value.size >= 2 }
                var processedSizes = 0
                
                sizeMapFiltered.forEach { (size, files) ->
                    ensureActive()
                    val hashGroups = HashMap<String, MutableList<File>>()
                    for (file in files) {
                        val hash = when {
                            size > 100 * 1024 * 1024 -> computeSuperFastHash(file)
                            size > 5 * 1024 * 1024 -> computeMediumHash(file)
                            else -> computeFullHash(file)
                        }
                        if (hash != null) {
                            hashGroups.getOrPut(hash) { mutableListOf() }.add(file)
                        }
                    }

                    hashGroups.filter { it.value.size >= 2 }.forEach { (hash, dupes) ->
                        val sorted = dupes.sortedBy { it.lastModified() }
                        duplicateGroups.add(DuplicateGroup(
                            hash = hash,
                            sizeBytes = size,
                            files = sorted.mapIndexed { index, f ->
                                DuplicateFile(f.absolutePath, f.lastModified(), index > 0)
                            }
                        ))
                    }
                    processedSizes++
                    if (processedSizes % 20 == 0) {
                        _scanState.value = ScanState.Scanning(
                            currentCategory = "Hashing: ${processedSizes}/${sizeMapFiltered.size}",
                            filesScanned = scannedCount,
                            progress = 0.55f + (processedSizes.toFloat() / sizeMapFiltered.size.coerceAtLeast(1) * 0.15f)
                        )
                    }
                }

                _scanState.value = ScanState.Scanning(currentCategory = "Analyzing App Usage...", progress = 0.75f)
                val unusedApps = getUnusedApps()

                _scanState.value = ScanState.Scanning(currentCategory = "Scanning Leftovers...", progress = 0.85f)
                val corpseEntries = mutableListOf<CorpseEntry>()
                val pm = context.packageManager
                val installedPackages = try {
                    pm.getInstalledPackages(0).map { it.packageName }.toSet()
                } catch (_: Exception) { emptySet() }

                listOf("Android/data", "Android/obb", "Android/media").forEach { path ->
                    val baseDir = File(root, path)
                    baseDir.listFiles()?.filter { it.isDirectory && !installedPackages.contains(it.name) }?.forEach { dir ->
                        if (path == "Android/media" && (dir.name.startsWith("com.android") || dir.name.startsWith("com.google"))) return@forEach
                        
                        val size = calculateDirSize(dir)
                        if (size > 0) {
                            corpseEntries.add(CorpseEntry(dir.name, dir.absolutePath, size, if (path.contains("obb")) CorpseType.OBB else CorpseType.DATA))
                        }
                    }
                }

                val categories = mutableListOf<CleanCategory>()
                
                if (tempFiles.isNotEmpty()) {
                    categories.add(CleanCategory(
                        id = "temp",
                        name = "System Junk",
                        icon = "DeleteSweep",
                        items = tempFiles.map { CleanItem.GenericFile(it) },
                        totalSize = tempFiles.sumOf { it.sizeBytes },
                        isSafeToClean = true
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

                if (duplicateGroups.isNotEmpty()) {
                    categories.add(CleanCategory(
                        id = "dupes",
                        name = "Duplicate Files",
                        icon = "FileCopy",
                        items = duplicateGroups.sortedByDescending { it.sizeBytes * it.files.size }.map { CleanItem.Duplicate(it) },
                        totalSize = duplicateGroups.sumOf { g -> g.files.filter { it.isSelected }.sumOf { g.sizeBytes } },
                        isSafeToClean = false
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

    private fun calculateFoundSize(temp: List<FileEntry>, large: List<FileEntry>, media: List<FileEntry>, documents: List<FileEntry>, emptyDirs: List<FileEntry>): Long {
        return temp.sumOf { it.sizeBytes } + 
               large.filter { it.isSelected }.sumOf { it.sizeBytes } +
               media.filter { it.isSelected }.sumOf { it.sizeBytes } +
               documents.filter { it.isSelected }.sumOf { it.sizeBytes } +
               emptyDirs.filter { it.isSelected }.sumOf { it.sizeBytes } 
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
            
            _scanState.value = ScanState.Cleaning(1f, "Finishing...")
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
        val thumbnailUri: String? = when (ext) {
            "pdf" -> getMediaStoreUri(this, MediaStore.Files.getContentUri("external"))
            "mp3", "wav", "m4a", "ogg", "flac" -> getMediaStoreUri(this, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> getMediaStoreUri(this, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            "mp4", "mkv", "avi", "mov", "webm" -> getMediaStoreUri(this, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            else -> null
        }
        return FileEntry(
            name = name,
            path = absolutePath,
            sizeBytes = length(),
            lastModified = lastModified(),
            extension = ext,
            isSelected = isSelected,
            thumbnailUri = thumbnailUri ?: absolutePath
        )
    }

    private fun getMediaStoreUri(file: File, collection: android.net.Uri): String? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = MediaStore.MediaColumns.DATA + "=?"
        val selectionArgs = arrayOf(file.absolutePath)
        return try {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0)).toString()
                } else null
            }
        } catch (e: Exception) {
            Log.e("CleanerRepository", "Error getting MediaStore URI: ${e.message}")
            null
        }
    }

    private fun computeFullHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }

    private fun computeMediumHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16384)
            val size = file.length()
            FileInputStream(file).use { fis ->
                val firstMb = 1024 * 1024L
                var totalRead = 0L
                while (totalRead < firstMb) {
                    val toRead = minOf(buffer.size.toLong(), firstMb - totalRead).toInt()
                    val read = fis.read(buffer, 0, toRead)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
                
                if (size > 2 * 1024 * 1024) {
                    fis.channel.position(size / 2)
                    totalRead = 0
                    while (totalRead < 512 * 1024) {
                        val read = fis.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        totalRead += read
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { null }
    }

    private fun computeSuperFastHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val size = file.length()
            FileInputStream(file).use { fis ->
                val chunk = ByteArray(128 * 1024)
                val readStart = fis.read(chunk)
                if (readStart > 0) digest.update(chunk, 0, readStart)
                
                if (size > 512 * 1024) {
                    fis.channel.position(size / 2)
                    val readMid = fis.read(chunk)
                    if (readMid > 0) digest.update(chunk, 0, readMid)
                }
                
                if (size > 256 * 1024) {
                    fis.channel.position(size - chunk.size)
                    val readEnd = fis.read(chunk)
                    if (readEnd > 0) digest.update(chunk, 0, readEnd)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { Log.e("CleanerRepository", "Error computing super fast hash for ${file.absolutePath}: ${e.message}"); null }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        try { dir.walkTopDown().filter { it.isFile }.forEach { size += it.length() } } catch (_: Exception) { }
        return size
    }
}
