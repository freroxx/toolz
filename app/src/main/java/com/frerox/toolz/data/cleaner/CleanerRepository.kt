package com.frerox.toolz.data.cleaner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
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

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private var scanJob: Job? = null

    // ── Storage info ─────────────────────────────────────────────────────────
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
        } catch (_: Exception) { }
    }

    // ── Scan ─────────────────────────────────────────────────────────────────
    fun startScan(scope: CoroutineScope) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            try {
                _scanState.value = ScanState.Scanning()

                val root = Environment.getExternalStorageDirectory()
                val sizeMap = HashMap<Long, MutableList<File>>(4096)
                var scanned = 0

                // ── Phase 1: Walk filesystem, group by size ──────────────
                root.walkTopDown()
                    .onEnter { dir ->
                        // Skip system/hidden dirs that are inaccessible
                        !dir.name.startsWith(".") || dir == root
                    }
                    .filter { it.isFile && it.length() > 1024 } // skip tiny files (<1KB)
                    .forEach { file ->
                        ensureActive()
                        scanned++
                        if (scanned % 50 == 0) {
                            _currentPath.value = file.absolutePath
                            _scanState.value = ScanState.Scanning(
                                currentPath = file.absolutePath,
                                filesScanned = scanned,
                                duplicatesFound = 0,
                                corpsesFound = 0
                            )
                        }
                        sizeMap.getOrPut(file.length()) { mutableListOf() }.add(file)
                    }

                // ── Phase 2: Hash duplicates (SHA-256 of first 2MB) ──────
                val duplicateGroups = mutableListOf<DuplicateGroup>()
                var dupeCount = 0

                sizeMap.filter { it.value.size >= 2 }.forEach { (size, files) ->
                    ensureActive()
                    val hashGroups = HashMap<String, MutableList<File>>()

                    for (file in files) {
                        ensureActive()
                        val hash = computePartialHash(file) ?: continue
                        hashGroups.getOrPut(hash) { mutableListOf() }.add(file)
                    }

                    hashGroups.filter { it.value.size >= 2 }.forEach { (hash, dupes) ->
                        // Sort by last modified — oldest first
                        val sorted = dupes.sortedBy { it.lastModified() }
                        val duplicateFiles = sorted.mapIndexed { index, f ->
                            DuplicateFile(
                                path = f.absolutePath,
                                lastModified = f.lastModified(),
                                isSelected = index > 0 // auto-select all except the oldest
                            )
                        }
                        duplicateGroups.add(
                            DuplicateGroup(
                                hash = hash,
                                sizeBytes = size,
                                files = duplicateFiles
                            )
                        )
                        dupeCount += dupes.size - 1
                    }

                    _scanState.value = ScanState.Scanning(
                        currentPath = "Hashing duplicates...",
                        filesScanned = scanned,
                        duplicatesFound = dupeCount,
                        corpsesFound = 0
                    )
                }

                // ── Phase 3: Corpse detection ────────────────────────────
                val corpseEntries = mutableListOf<CorpseEntry>()
                val installedPackages = try {
                    context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
                } catch (_: Exception) {
                    emptySet()
                }

                fun scanCorpseDir(baseDir: File, type: CorpseType) {
                    if (!baseDir.exists() || !baseDir.isDirectory) return
                    baseDir.listFiles()?.forEach { dir ->
                        if (dir.isDirectory && !installedPackages.contains(dir.name)) {
                            val size = calculateDirSize(dir)
                            if (size > 0) {
                                corpseEntries.add(
                                    CorpseEntry(
                                        packageName = dir.name,
                                        path = dir.absolutePath,
                                        sizeBytes = size,
                                        type = type,
                                        isSelected = true
                                    )
                                )
                            }
                        }
                    }
                }

                _scanState.value = ScanState.Scanning(
                    currentPath = "Detecting orphaned app data...",
                    filesScanned = scanned,
                    duplicatesFound = dupeCount,
                    corpsesFound = 0
                )

                scanCorpseDir(File(root, "Android/data"), CorpseType.DATA)
                scanCorpseDir(File(root, "Android/obb"), CorpseType.OBB)

                // ── Calculate total cleanable bytes ──────────────────────
                val cleanableFromDupes = duplicateGroups.sumOf { group ->
                    group.files.filter { it.isSelected }.sumOf { group.sizeBytes }
                }
                val cleanableFromCorpses = corpseEntries.filter { it.isSelected }.sumOf { it.sizeBytes }
                val totalCleanable = cleanableFromDupes + cleanableFromCorpses

                // Update storage info with cleanable
                refreshStorageInfo()
                _storageInfo.update { it.copy(cleanableBytes = totalCleanable) }

                _scanState.value = ScanState.Results(
                    duplicateGroups = duplicateGroups.sortedByDescending { it.sizeBytes * it.files.size },
                    corpseEntries = corpseEntries.sortedByDescending { it.sizeBytes },
                    totalCleanableBytes = totalCleanable,
                    filesScanned = scanned
                )
            } catch (e: CancellationException) {
                _scanState.value = ScanState.Idle
            } catch (e: Exception) {
                _scanState.value = ScanState.Error(e.localizedMessage ?: "Scan failed")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanState.value = ScanState.Idle
    }

    // ── Delete selected items ────────────────────────────────────────────────
    suspend fun deleteSelected() {
        val state = _scanState.value
        if (state !is ScanState.Results) return

        withContext(Dispatchers.IO) {
            val filesToDelete = mutableListOf<String>()

            // Collect duplicate files to delete
            state.duplicateGroups.forEach { group ->
                group.files.filter { it.isSelected }.forEach { filesToDelete.add(it.path) }
            }

            // Collect corpse dirs to delete
            val corpsePaths = state.corpseEntries.filter { it.isSelected }.map { it.path }

            val totalItems = filesToDelete.size + corpsePaths.size
            var deleted = 0
            var failed = 0
            var freedBytes = 0L

            // Delete duplicate files
            filesToDelete.forEachIndexed { index, path ->
                ensureActive()
                _scanState.value = ScanState.Cleaning(
                    progress = (index.toFloat()) / totalItems,
                    currentFile = path
                )
                try {
                    val file = File(path)
                    val size = file.length()
                    if (file.delete()) {
                        freedBytes += size
                        deleted++
                    } else {
                        failed++
                    }
                } catch (_: Exception) {
                    failed++
                }
            }

            // Delete corpse directories
            corpsePaths.forEachIndexed { index, path ->
                ensureActive()
                _scanState.value = ScanState.Cleaning(
                    progress = ((filesToDelete.size + index).toFloat()) / totalItems,
                    currentFile = path
                )
                try {
                    val dir = File(path)
                    val size = calculateDirSize(dir)
                    if (dir.deleteRecursively()) {
                        freedBytes += size
                        deleted++
                    } else {
                        failed++
                    }
                } catch (_: Exception) {
                    failed++
                }
            }

            refreshStorageInfo()
            _scanState.value = ScanState.Done(
                CleanResult(
                    freedBytes = freedBytes,
                    deletedCount = deleted,
                    failedCount = failed
                )
            )
        }
    }

    fun resetState() {
        _scanState.value = ScanState.Idle
        _currentPath.value = ""
    }

    // ── Toggle selection helpers ─────────────────────────────────────────────
    fun toggleDuplicateFile(groupHash: String, filePath: String) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updatedGroups = state.duplicateGroups.map { group ->
            if (group.hash == groupHash) {
                group.copy(
                    files = group.files.map { file ->
                        if (file.path == filePath) file.copy(isSelected = !file.isSelected)
                        else file
                    }
                )
            } else group
        }
        _scanState.value = state.copy(
            duplicateGroups = updatedGroups,
            totalCleanableBytes = calculateCleanable(updatedGroups, state.corpseEntries)
        )
        _storageInfo.update { it.copy(cleanableBytes = (_scanState.value as? ScanState.Results)?.totalCleanableBytes ?: 0L) }
    }

    fun toggleCorpse(path: String) {
        val state = _scanState.value as? ScanState.Results ?: return
        val updatedCorpses = state.corpseEntries.map { entry ->
            if (entry.path == path) entry.copy(isSelected = !entry.isSelected)
            else entry
        }
        _scanState.value = state.copy(
            corpseEntries = updatedCorpses,
            totalCleanableBytes = calculateCleanable(state.duplicateGroups, updatedCorpses)
        )
        _storageInfo.update { it.copy(cleanableBytes = (_scanState.value as? ScanState.Results)?.totalCleanableBytes ?: 0L) }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private fun calculateCleanable(
        groups: List<DuplicateGroup>,
        corpses: List<CorpseEntry>
    ): Long {
        val dupeBytes = groups.sumOf { g -> g.files.filter { it.isSelected }.sumOf { g.sizeBytes } }
        val corpseBytes = corpses.filter { it.isSelected }.sumOf { it.sizeBytes }
        return dupeBytes + corpseBytes
    }

    /**
     * Compute SHA-256 of the first 2MB of a file.
     */
    private fun computePartialHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var totalRead = 0L
            val maxBytes = 2L * 1024 * 1024 // 2MB

            FileInputStream(file).use { fis ->
                while (totalRead < maxBytes) {
                    val toRead = minOf(buffer.size.toLong(), maxBytes - totalRead).toInt()
                    val read = fis.read(buffer, 0, toRead)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        try {
            dir.walkTopDown().filter { it.isFile }.forEach { size += it.length() }
        } catch (_: Exception) { }
        return size
    }
}
