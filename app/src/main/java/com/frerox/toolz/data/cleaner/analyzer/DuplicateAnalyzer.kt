/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.analyzer

import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.DuplicateFile
import com.frerox.toolz.data.cleaner.DuplicateGroup
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.IndexedFile
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import android.util.Log
import com.frerox.toolz.data.cleaner.util.HashUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateAnalyzer @Inject constructor() : CleanerAnalyzer {
    override val categoryId = "dupes"
    override val categoryName = "Duplicate files"
    override val categoryIcon = "FileCopy"
    override val description = "Exact copies — the oldest is always kept"
    override val isSafeToClean = false

    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        val config = ctx.config
        // Group by size from the shared index — no walk.
        val sizeMap = HashMap<Long, MutableList<IndexedFile>>(4096)
        for (f in index.files) {
            if (!ctx.isActive()) break
            if (f.size < config.duplicateMinSize) continue
            sizeMap.getOrPut(f.size) { mutableListOf() }.add(f)
        }
        var filtered = sizeMap.filter { it.value.size >= 2 }
        // Bound hashing work: largest buckets first, and SAY SO.
        var truncated = 0
        val totalCandidates = filtered.values.sumOf { it.size }
        if (totalCandidates > 5000) {
            var acc = 0
            val limited = LinkedHashMap<Long, MutableList<IndexedFile>>()
            for ((sz, lst) in filtered.entries.sortedByDescending { it.key }) {
                if (acc >= 5000) { truncated += lst.size; continue }
                limited[sz] = lst
                acc += lst.size
            }
            filtered = limited
        }
        ctx.progress("Checking duplicates…")
        val groups = mutableListOf<DuplicateGroup>()
        var processed = 0
        for ((size, files) in filtered) {
            if (!ctx.isActive()) break
            val quickGroups = HashMap<String, MutableList<IndexedFile>>()
            for (f in files) {
                val q = if (size > 500 * 1024 * 1024L) HashUtils.computeSampleHash(File(f.path))
                        else HashUtils.computeQuickHash(File(f.path)) ?: continue
                if (q == null) continue
                quickGroups.getOrPut(q) { mutableListOf() }.add(f)
            }
            for ((quickHash, pot) in quickGroups.filter { it.value.size >= 2 }) {
                val fullGroups = HashMap<String, MutableList<IndexedFile>>()
                for (f in pot) {
                    if (!ctx.isActive()) break
                    val fh = if (size > 500 * 1024 * 1024L) "sample:$quickHash"
                             else HashUtils.computeFullHash(File(f.path)) { ctx.isActive() } ?: continue
                    fullGroups.getOrPut(fh) { mutableListOf() }.add(f)
                }
                for ((hash, dupes) in fullGroups.filter { it.value.size >= 2 }) {
                    val sorted = dupes.sortedBy { it.lastModified }
                    val dupFiles = sorted.mapIndexed { idx, file ->
                        DuplicateFile(file.path, file.lastModified, isSelected = idx > 0)
                    }
                    groups.add(DuplicateGroup(hash, size, dupFiles))
                    if (groups.size >= config.maxDuplicatesGroups) break
                }
                if (groups.size >= config.maxDuplicatesGroups) break
            }
            processed++
            if (processed % 50 == 0) ctx.progress("Checking duplicates…")
            if (groups.size >= config.maxDuplicatesGroups) break
        }
        val shown = groups.sortedByDescending { it.sizeBytes }.take(config.maxDuplicatesGroups)
        val items = shown.map { CleanItem.Duplicate(it) }
        val total = shown.sumOf { (it.files.size - 1).coerceAtLeast(0) * it.sizeBytes }
        val sel = shown.sumOf { g -> g.files.count { it.isSelected } * g.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, sel, isSafeToClean,
            description = description, truncatedCount = truncated + (groups.size - shown.size).coerceAtLeast(0))
    }
}
