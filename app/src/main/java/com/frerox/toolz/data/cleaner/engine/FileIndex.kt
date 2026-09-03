/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.engine

import java.io.File

/** Single-crawl shared file index — the ONLY filesystem walk per scan. */
data class IndexedFile(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val ext: String,
    val depth: Int,
    val parentDir: String
)

/** Shared index produced by a single [CrawlEngine.crawl] filesystem walk. */
data class FileIndex(
    val root: String,
    val files: List<IndexedFile>,
    /** Every visited directory (absolute paths). */
    val allDirs: List<String>,
    /** Truly empty dirs (no files, no subdirs, no sentinels) — safe to auto-remove. */
    val emptyDirs: List<String>,
    /** Recursive byte totals per directory, accumulated during the crawl. */
    val dirSizes: Map<String, Long>,
    val scannedFiles: Int,
    val scannedDirs: Int,
    val skipped: Int,
    val truncated: Boolean
) {
    /**
     * Top [limit] directories by recursive bytes, largest first.
     * Pure view over [dirSizes]; never mutates the index.
     */
    fun topDirs(limit: Int = 10): List<Pair<String, Long>> =
        dirSizes.entries
            .sortedByDescending { it.value }
            .take(limit.coerceAtLeast(0))
            .map { it.key to it.value }
}

/** Ownership: one path → one category. Priority order wins. */
class OwnershipRegistry {
    private val claimed = HashSet<String>(8192)
    /** @return true if caller now owns path */
    @Synchronized
    fun claim(path: String): Boolean = claimed.add(path)
    @Synchronized
    fun isClaimed(path: String): Boolean = claimed.contains(path)
    @Synchronized
    fun reset() = claimed.clear()
    /**
     * Claims every path in [paths].
     * @return count of newly claimed paths (already-claimed ones are skipped).
     */
    @Synchronized
    fun claimAll(paths: List<String>): Int {
        var newly = 0
        for (p in paths) if (claimed.add(p)) newly++
        return newly
    }
}

/**
 * Pure helper — unit tested. Folds per-file sizes up the tree.
 * No I/O; capped at 64 ancestors per file to bound pathological depths.
 */
internal fun accumulateDirSizes(root: String, files: List<IndexedFile>): Map<String, Long> {
    val sizes = HashMap<String, Long>(files.size.coerceAtMost(65536).coerceAtLeast(1024))
    for (f in files) {
        var dir = f.parentDir
        var guard = 0
        while (guard++ < 64) {
            sizes[dir] = (sizes[dir] ?: 0L) + f.size
            if (dir == root || !dir.startsWith(root)) break
            val parent = dir.substringBeforeLast('/', "")
            if (parent.isEmpty() || parent == dir) break
            dir = parent
        }
    }
    return sizes
}
