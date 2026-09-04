/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.analyzer

import android.util.Log
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.CorpseEntry
import com.frerox.toolz.data.cleaner.CorpseType
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.shizuku.ShizukuFileLister
import com.frerox.toolz.data.cleaner.util.FileUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorpseAnalyzer @Inject constructor(
    private val accessGate: com.frerox.toolz.data.cleaner.access.AccessGate,
    private val shizukuLister: ShizukuFileLister
) : CleanerAnalyzer {
    override val categoryId = "corpse"
    override val categoryName = "Leftover app folders"
    override val categoryIcon = "AutoDelete"
    override val description = "Folders left behind by apps you uninstalled"
    override val isSafeToClean = true

    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        val entries = mutableListOf<CorpseEntry>()
        val seen = HashSet<String>()
        val allInstalled = ctx.installed
        var accessDenied = 0
        var usedShell = false
        var obbBlind = false
        val shizukuMissing = !ctx.shizukuUsable
        val bases = listOf("Android/data" to CorpseType.DATA, "Android/obb" to CorpseType.OBB, "Android/media" to CorpseType.MEDIA)
        // Immediate children come from the shared index — no walks.
        val childrenByBase = HashMap<String, MutableList<String>>()
        for (dir in index.allDirs) {
            if (!ctx.isActive()) break
            if (!dir.startsWith(index.root)) continue
            val rel = dir.removePrefix(index.root).trim('/')
            // Immediate child of a base looks like "Android/data/<pkg>" (exactly 2 slashes).
            if (rel.count { it == '/' } != 2) continue
            val base = rel.substringBeforeLast('/')
            if (bases.any { it.first == base }) {
                childrenByBase.getOrPut(base) { mutableListOf() }.add(dir)
            }
        }
        for ((base, type) in bases) {
            if (!ctx.isActive()) break
            ctx.progress("Leftovers — $base")
            var childDirs = childrenByBase[base]
            var shellSizes: Map<String, Long>? = null
            if (childDirs == null) {
                // Base itself wasn't listable (Android/obb on 12+). Shell fallback, else honest miss.
                val baseFile = java.io.File(index.root, base)
                if (baseFile.exists()) {
                    val shell = try { shizukuLister.listDirs(baseFile) } catch (e: Exception) { Log.w("Corpse", "shell $base", e); null }
                    if (shell != null) {
                        usedShell = true
                        childDirs = shell.map { it.path }.toMutableList()
                        shellSizes = shell.associate { it.path to it.sizeBytes }
                    } else {
                        accessDenied++
                        if (base == "Android/obb") obbBlind = true
                    }
                } else if (base == "Android/obb") {
                    // Restricted on 12+: the base itself is invisible without privilege.
                    obbBlind = true
                }
            }
            for (dirPath in childDirs ?: emptyList()) {
                if (!ctx.isActive()) break
                try {
                    if (!seen.add(dirPath)) continue
                    val name = dirPath.substringAfterLast('/')
                    if (name.startsWith("com.android.") || name.startsWith("com.google.android.")) continue
                    if (name.startsWith("com.samsung.") || name.startsWith("com.sec.")) continue
                    if (FileUtils.isExcluded(dirPath, ctx.exclusions)) continue
                    if (!allInstalled.contains(name)) {
                        // Zero-size leftovers still count — a leftover that holds nothing is still a corpse.
                        val size = shellSizes?.get(dirPath) ?: index.dirSizes[dirPath] ?: 0L
                        entries.add(CorpseEntry(name, dirPath, size, type, isSelected = true))
                    }
                } catch (e: Exception) { Log.w("Corpse", "skip $dirPath", e); accessDenied++ }
            }
        }
        // Modern chat-app roots + legacy top-level roots whose owner app is gone. Index-only.
        try {
            for (dir in index.allDirs) {
                if (!ctx.isActive()) break
                if (!dir.startsWith(index.root)) continue
                val rel = dir.removePrefix(index.root).trim('/')
                val segs = rel.split('/')
                if (segs.size == 3 && segs[0] == "Android" && segs[1] == "media") {
                    val pkg = segs[2]
                    val pl = pkg.lowercase()
                    if (("whatsapp" in pl || "telegram" in pl || "challegram" in pl) && !allInstalled.contains(pkg)) {
                        if (!seen.add(dir) || FileUtils.isExcluded(dir, ctx.exclusions)) continue
                        entries.add(CorpseEntry(pkg, dir, index.dirSizes[dir] ?: 0L, CorpseType.MEDIA, isSelected = true))
                    }
                }
            }
            for ((top, owner) in mapOf("WhatsApp" to "com.whatsapp", "Telegram" to "org.telegram.messenger")) {
                if (!ctx.isActive()) break
                val lp = index.root.trimEnd('/') + "/" + top
                if (index.allDirs.any { it.trimEnd('/') == lp } && !allInstalled.contains(owner)) {
                    if (!seen.add(lp) || FileUtils.isExcluded(lp, ctx.exclusions)) continue
                    entries.add(CorpseEntry(owner, lp, index.dirSizes[lp] ?: 0L, CorpseType.MEDIA, isSelected = true))
                }
            }
        } catch (e: Exception) { Log.w("Corpse", "chat roots", e); accessDenied++ }
        // Bottom-up: deepest paths first so children are removed before parents.
        val sorted = entries.sortedWith(compareByDescending<CorpseEntry> { it.path.count { c -> c == '/' } }.thenByDescending { it.sizeBytes })
        val items = sorted.map { CleanItem.Corpse(it) }
        val total = sorted.sumOf { it.sizeBytes }
        val selected = items.sumOf { (it as CleanItem.Corpse).let { c -> if (c.entry.isSelected) c.entry.sizeBytes else 0L } }
        val blocked = when {
            obbBlind && shizukuMissing -> "Android/obb can't be inspected without Shizuku"
            entries.isEmpty() && accessDenied > 0 && !ctx.allFilesGranted ->
                "Some app folders are hidden — grant All-files access"
            else -> null
        }
        val blockedFix: String? = when {
            blocked == null -> null
            obbBlind && shizukuMissing -> "Set up Shizuku"
            else -> "Grant"
        }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, selected, isSafeToClean,
            description = description, blockedReason = blocked,
            blockedFixLabel = blockedFix,
            skippedCount = accessDenied,
            emptyHint = if (entries.isEmpty() && blocked == null) "No leftovers — every app owns its folders" else null)
    }
}
