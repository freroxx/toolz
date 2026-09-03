/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.engine

import com.frerox.toolz.data.cleaner.util.FileUtils
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class CrawlProgress(val scanned: Int, val dirs: Int, val currentDir: String)

@Singleton
class CrawlEngine @Inject constructor() {
    suspend fun crawl(
        root: File,
        exclusions: Set<String>,
        includeHidden: Boolean,
        maxFiles: Int = 200_000,
        onProgress: (CrawlProgress) -> Unit = {}
    ): FileIndex {
        val rootPath = try { root.canonicalPath } catch (_: Exception) { root.absolutePath }
        if (!root.exists() || !root.isDirectory) {
            return FileIndex(
                root = rootPath,
                files = emptyList(),
                allDirs = emptyList(),
                emptyDirs = emptyList(),
                dirSizes = emptyMap(),
                scannedFiles = 0, scannedDirs = 0, skipped = 0, truncated = false
            )
        }
        val files = ArrayList<IndexedFile>(16_384)
        val allDirs = ArrayList<String>(8_192)
        val childCounts = HashMap<String, Int>(8_192)
        val sentinelDirs = HashSet<String>()
        var scanned = 0; var dirs = 0; var skipped = 0
        var truncated = false

        fun isExcludedDir(dir: File): Boolean {
            if (!includeHidden && dir.name.startsWith(".") && dir.absolutePath != rootPath) return true
            if (FileUtils.isExcluded(dir.absolutePath, exclusions)) return true
            return false
        }

        try {
            val stack = ArrayDeque<Pair<File, Int>>()
            stack.add(root to 0)
            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val (dir, depth) = stack.removeLast()
                if (depth > 32) { skipped++; continue }
                if (!dir.exists() || !dir.isDirectory) continue
                val dirPath = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
                if (dirPath != rootPath && isExcludedDir(dir)) { skipped++; continue }
                dirs++
                allDirs.add(dirPath)
                val children = try { dir.listFiles() } catch (_: Exception) { null } ?: continue
                for (i in children.indices.reversed()) {
                    val c = children[i]
                    try {
                        if (c.isDirectory) {
                            childCounts[dirPath] = (childCounts[dirPath] ?: 0) + 1
                            stack.add(c to depth + 1)
                        } else if (c.isFile) {
                            if (FileUtils.isExcluded(c.absolutePath, exclusions)) { skipped++; continue }
                            val name = c.name
                            // Sentinels are never indexed as content, but mark the dir non-empty.
                            if (name == ".nomedia" || name == ".gitkeep") {
                                childCounts[dirPath] = (childCounts[dirPath] ?: 0) + 1
                                sentinelDirs.add(dirPath)
                                continue
                            }
                            if (!includeHidden && name.startsWith(".")) continue
                            childCounts[dirPath] = (childCounts[dirPath] ?: 0) + 1
                            files.add(
                                IndexedFile(
                                    path = c.absolutePath, name = name,
                                    size = try { c.length() } catch (_: Exception) { 0L },
                                    lastModified = try { c.lastModified() } catch (_: Exception) { 0L },
                                    ext = c.extension.lowercase(), depth = depth + 1,
                                    parentDir = dirPath
                                )
                            )
                            scanned++
                            if (scanned >= maxFiles) { truncated = true; break }
                            if (scanned % 5000 == 0) onProgress(CrawlProgress(scanned, dirs, dir.name))
                        }
                    } catch (_: Exception) { skipped++ }
                }
                if (truncated) break
                if (dirs % 400 == 0) onProgress(CrawlProgress(scanned, dirs, dir.name))
            }
        } catch (_: Exception) {}
        onProgress(CrawlProgress(scanned, dirs, ""))
        // Truly empty = visited, zero children, no sentinels. Sentinel-only dirs are
        // deliberately NOT flagged (deleting them unhides media).
        val empty = allDirs.asSequence()
            .filter { it != rootPath && (childCounts[it] ?: 0) == 0 && !sentinelDirs.contains(it) }
            .take(2000)
            .toList()
        return FileIndex(
            root = rootPath,
            files = files,
            allDirs = allDirs,
            emptyDirs = empty,
            dirSizes = accumulateDirSizes(rootPath, files),
            scannedFiles = scanned, scannedDirs = dirs, skipped = skipped, truncated = truncated
        )
    }
}
