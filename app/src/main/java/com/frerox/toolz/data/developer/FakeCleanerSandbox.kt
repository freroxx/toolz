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

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.CleanResult
import com.frerox.toolz.data.cleaner.MediaType
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.data.cleaner.trash.CleanerTrashEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DEV-SPOOF: RAM-only file cleaner fixture. Mirrors the real engine's data
 * shapes so the cleaner screen is fully explorable while the sandbox is active — scan, pick,
 * clean, trash, restore, empty — while the real filesystem, the trash Room
 * table and the accessibility service are never touched.
 */
object FakeCleanerSandbox {

    private val categories = MutableStateFlow<List<CleanCategory>>(emptyList())
    val fixtureCategories: StateFlow<List<CleanCategory>> = categories.asStateFlow()

    private val trash = MutableStateFlow<List<CleanerTrashEntity>>(emptyList())
    val fixtureTrash: StateFlow<List<CleanerTrashEntity>> = trash.asStateFlow()

    /** DEV-SPOOF: trashed fixture entries + their source, for restore/undo. */
    private data class Trashed(val entity: CleanerTrashEntity, val categoryId: String?, val item: CleanItem)
    private val trashed = mutableListOf<Trashed>()
    private var nextTrashId = 1L

    fun storageInfo(fullPercent: Int = 68): StorageInfo {
        val res = snapshotResults()
        val totalBytes = 128L * 1024 * 1024 * 1024
        val usedBytes = totalBytes * fullPercent.coerceIn(0, 100) / 100
        val freeBytes = totalBytes - usedBytes
        return StorageInfo(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes,
            cleanableBytes = res.totalCleanableBytes.coerceAtMost(freeBytes),
        )
    }

    private fun mb(v: Long) = v * 1024 * 1024L
    private fun kb(v: Long) = v * 1024L

    /** DEV-SPOOF: rebuilds a believable full RAM scan snapshot. */
    fun armScan() {
        trashed.clear()
        trash.value = emptyList()
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        val junk = listOf(
            CleanItem.GenericFile(com.frerox.toolz.data.cleaner.FileEntry(
                name = "exported_video_0420.mp4", path = "/storage/emulated/0/Download/exported_video_0420.mp4",
                sizeBytes = mb(214), lastModified = now - 26 * day, extension = "mp4")),
            CleanItem.GenericFile(com.frerox.toolz.data.cleaner.FileEntry(
                name = "CallRecord-20260129_1130.amr", path = "/storage/emulated/0/Recordings/CallRecord-20260129_1130.amr",
                sizeBytes = mb(6), lastModified = now - 41 * day, extension = "amr")),
            CleanItem.GenericFile(com.frerox.toolz.data.cleaner.FileEntry(
                name = "tmp_render_chunk_88.bin", path = "/storage/emulated/0/cache/tmp_render_chunk_88.bin",
                sizeBytes = mb(83), lastModified = now - 3 * day, extension = "bin")),
            CleanItem.GenericFile(com.frerox.toolz.data.cleaner.FileEntry(
                name = "backup_contacts_old.vcf", path = "/storage/emulated/0/Download/backup_contacts_old.vcf",
                sizeBytes = kb(12), lastModified = now - 59 * day, extension = "vcf")),
        )

        val appCache = listOf(
            CleanItem.AppCache(com.frerox.toolz.data.cleaner.AppCacheEntry(
                packageName = "com.example.clips", appName = "ClipStudio", sizeBytes = mb(318), cacheBytes = mb(318))),
            CleanItem.AppCache(com.frerox.toolz.data.cleaner.AppCacheEntry(
                packageName = "dev.example.player", appName = "Nimbus Player", sizeBytes = mb(152), cacheBytes = mb(152))),
            CleanItem.AppCache(com.frerox.toolz.data.cleaner.AppCacheEntry(
                packageName = "com.example.maps", appName = "Atlas Maps", sizeBytes = mb(96), cacheBytes = mb(96))),
            CleanItem.AppCache(com.frerox.toolz.data.cleaner.AppCacheEntry(
                packageName = "com.example.social", appName = "Pulse Social", sizeBytes = mb(57), cacheBytes = mb(57))),
        )

        val emptyDirs = listOf(
            CleanItem.EmptyDir(com.frerox.toolz.data.cleaner.EmptyDirEntry(path = "/storage/emulated/0/DCIM/.thumbnails/x", name = "x")),
            CleanItem.EmptyDir(com.frerox.toolz.data.cleaner.EmptyDirEntry(path = "/storage/emulated/0/Android/data/old/cache/j", name = "j")),
            CleanItem.EmptyDir(com.frerox.toolz.data.cleaner.EmptyDirEntry(path = "/storage/emulated/0/Download/unzipped", name = "unzipped")),
        )

        val dupes = listOf(
            CleanItem.Duplicate(com.frerox.toolz.data.cleaner.DuplicateGroup(
                hash = "fixture-dup-1", sizeBytes = mb(9),
                files = listOf(
                    com.frerox.toolz.data.cleaner.DuplicateFile("/storage/emulated/0/Download/photo_trip.jpg", now - 12 * day),
                    com.frerox.toolz.data.cleaner.DuplicateFile("/storage/emulated/0/DCIM/photo_trip.jpg", now - 12 * day),
                ))),
            CleanItem.Duplicate(com.frerox.toolz.data.cleaner.DuplicateGroup(
                hash = "fixture-dup-2", sizeBytes = mb(24),
                files = listOf(
                    com.frerox.toolz.data.cleaner.DuplicateFile("/storage/emulated/0/Download/invoice_q1.pdf", now - 31 * day),
                    com.frerox.toolz.data.cleaner.DuplicateFile("/storage/emulated/0/Documents/invoice_q1.pdf", now - 31 * day),
                ))),
        )

        val media = listOf(
            CleanItem.MediaFile(com.frerox.toolz.data.cleaner.MediaEntry(
                name = "IMG_20260112_090233.jpg", path = "/storage/emulated/0/DCIM/Camera/IMG_20260112_090233.jpg",
                sizeBytes = mb(4), lastModified = now - 17 * day, extension = "jpg", type = MediaType.DCIM)),
            CleanItem.MediaFile(com.frerox.toolz.data.cleaner.MediaEntry(
                name = "Screenshot_0211-084412.png", path = "/storage/emulated/0/Pictures/Screenshots/Screenshot_0211-084412.png",
                sizeBytes = kb(980), lastModified = now - 9 * day, extension = "png", type = MediaType.SCREENSHOT)),
            CleanItem.MediaFile(com.frerox.toolz.data.cleaner.MediaEntry(
                name = "IMG-20260130-WA0091.jpg", path = "/storage/emulated/0/WhatsApp/Media/IMG-20260130-WA0091.jpg",
                sizeBytes = mb(2), lastModified = now - 5 * day, extension = "jpg", type = MediaType.WHATSAPP)),
        )

        val apks = listOf(
            CleanItem.ApkFile(com.frerox.toolz.data.cleaner.ApkEntry(
                name = "camera_pro_v3.2.1.apk", path = "/storage/emulated/0/Download/camera_pro_v3.2.1.apk",
                sizeBytes = mb(48), lastModified = now - 33 * day, packageName = "cam.pro", versionName = "3.2.1")),
        )

        val corpses = listOf(
            CleanItem.Corpse(com.frerox.toolz.data.cleaner.CorpseEntry(
                packageName = "com.gone.editor", path = "/storage/emulated/0/Android/data/com.gone.editor",
                sizeBytes = mb(72), type = com.frerox.toolz.data.cleaner.CorpseType.DATA)),
            CleanItem.Corpse(com.frerox.toolz.data.cleaner.CorpseEntry(
                packageName = "com.gone.game", path = "/storage/emulated/0/Android/obb/com.gone.game",
                sizeBytes = mb(430), type = com.frerox.toolz.data.cleaner.CorpseType.OBB)),
        )

        fun cat(
            id: String, name: String, icon: String, items: List<CleanItem>,
            safe: Boolean, shizuku: Boolean = false,
            description: String? = null,
        ): CleanCategory {
            // Fixture defaults mirror the engine: caches preselected, files not.
            val seeded = items.map { i ->
                if (i is CleanItem.AppCache || i is CleanItem.Corpse || i is CleanItem.EmptyDir) setSelected(i, true)
                else if (i is CleanItem.Duplicate) {
                    // one copy per group preselected (the older file)
                    CleanItem.Duplicate(i.group.copy(
                        files = i.group.files.mapIndexed { idx, f -> f.copy(isSelected = idx > 0) },
                    ))
                } else i
            }
            return CleanCategory(
                id = id, name = name, icon = icon, items = seeded,
                totalSize = seeded.sumOf { sizeOf(it) },
                selectedSize = seeded.filter { selected(it) }.sumOf { sizeOf(it) },
                isSafeToClean = safe, description = description, requiresShizuku = shizuku,
            )
        }

        categories.value = listOf(
            cat("junk", "Junk files", "delete", junk, safe = true, description = "Stale downloads and temp renders."),
            cat("app_cache", "App caches", "cached", appCache, safe = true, description = "Clear per app (fixture: RAM only)."),
            cat("empty_dirs", "Empty folders", "folder_off", emptyDirs, safe = true),
            cat("dupes", "Duplicates", "difference", dupes, safe = true, description = "One copy per group stays."),
            cat("media", "Large media", "photo", media, safe = false, description = "Review before removing."),
            cat("apks", "Installers", "get_app", apks, safe = false),
            cat("corpses", "Leftovers of uninstalled apps", "psychology", corpses, safe = true, shizuku = true, description = "Orphan data + OBB folders."),
        )
    }

    private fun selected(i: CleanItem): Boolean = when (i) {
        is CleanItem.Corpse -> i.entry.isSelected
        is CleanItem.GenericFile -> i.file.isSelected
        is CleanItem.EmptyDir -> i.entry.isSelected
        is CleanItem.AppCache -> i.entry.isSelected
        is CleanItem.MediaFile -> i.entry.isSelected
        is CleanItem.ApkFile -> i.entry.isSelected
        is CleanItem.UnusedApp -> i.entry.isSelected
        is CleanItem.Duplicate -> i.group.files.any { it.isSelected }
    }

    private fun sizeOf(i: CleanItem): Long = when (i) {
        is CleanItem.Corpse -> i.entry.sizeBytes
        is CleanItem.GenericFile -> i.file.sizeBytes
        is CleanItem.EmptyDir -> 0L
        is CleanItem.AppCache -> i.entry.sizeBytes
        is CleanItem.MediaFile -> i.entry.sizeBytes
        is CleanItem.ApkFile -> i.entry.sizeBytes
        is CleanItem.UnusedApp -> i.entry.sizeBytes
        is CleanItem.Duplicate -> i.group.sizeBytes
    }

    private fun idOf(i: CleanItem): String? = when (i) {
        is CleanItem.Corpse -> i.entry.path
        is CleanItem.GenericFile -> i.file.path
        is CleanItem.EmptyDir -> i.entry.path
        is CleanItem.AppCache -> "appcache:${i.entry.packageName}"
        is CleanItem.MediaFile -> i.entry.path
        is CleanItem.ApkFile -> i.entry.path
        is CleanItem.UnusedApp -> "unused:${i.entry.packageName}"
        is CleanItem.Duplicate -> "dup:${i.group.hash}"
    }

    fun snapshotResults(filesScanned: Int = 8124, truncated: Boolean = false): ScanState.Results {
        val cats = categories.value
        return ScanState.Results(
            categories = cats,
            totalCleanableBytes = cats.sumOf { it.totalSize },
            selectedBytes = cats.sumOf { it.selectedSize },
            filesScanned = filesScanned, truncated = truncated,
        )
    }

    fun toggleItem(categoryId: String, itemId: String) {
        mutateCategory(categoryId) { items ->
            items.map { i ->
                when {
                    i is CleanItem.Corpse && i.entry.path == itemId -> CleanItem.Corpse(i.entry.copy(isSelected = !i.entry.isSelected))
                    i is CleanItem.GenericFile && i.file.path == itemId -> CleanItem.GenericFile(i.file.copy(isSelected = !i.file.isSelected))
                    i is CleanItem.EmptyDir && i.entry.path == itemId -> CleanItem.EmptyDir(i.entry.copy(isSelected = !i.entry.isSelected))
                    i is CleanItem.MediaFile && i.entry.path == itemId -> CleanItem.MediaFile(i.entry.copy(isSelected = !i.entry.isSelected))
                    i is CleanItem.ApkFile && i.entry.path == itemId -> CleanItem.ApkFile(i.entry.copy(isSelected = !i.entry.isSelected))
                    i is CleanItem.AppCache && idOf(i) == itemId -> CleanItem.AppCache(i.entry.copy(isSelected = !i.entry.isSelected))
                    else -> i
                }
            }
        }
    }

    fun setItemsSelected(categoryId: String, itemIds: Set<String>, selected: Boolean) {
        mutateCategory(categoryId) { items ->
            items.map { i ->
                val hit = idOf(i) in itemIds
                if (hit) setSelected(i, selected) else i
            }
        }
    }

    fun setCategorySelected(categoryId: String, selected: Boolean) {
        mutateCategory(categoryId) { items -> items.map { if (it is CleanItem.Duplicate) dupSelected(it, selected) else setSelected(it, selected) } }
    }

    fun setAllSelected(selected: Boolean) {
        categories.value = categories.value.map { c -> recompute(c.copy(items = c.items.map { if (it is CleanItem.Duplicate) dupSelected(it, selected) else setSelected(it, selected) })) }
    }

    fun selectSafeOnly() {
        categories.value = categories.value.map { c ->
            recompute(c.copy(items = c.items.map { if (it is CleanItem.Duplicate) dupSelected(it, c.isSafeToClean) else setSelected(it, c.isSafeToClean) }))
        }
    }

    private fun dupSelected(i: CleanItem.Duplicate, s: Boolean): CleanItem =
        CleanItem.Duplicate(i.group.copy(files = i.group.files.map { f -> f.copy(isSelected = s) }))

    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) {
        mutateCategory(categoryId) { items ->
            items.map { i ->
                if (i is CleanItem.Duplicate && i.group.hash == groupHash) {
                    CleanItem.Duplicate(i.group.copy(
                        files = i.group.files.map { f -> if (f.path == path) f.copy(isSelected = !f.isSelected) else f },
                    ))
                } else i
            }
        }
    }

    fun setDuplicateKeeper(categoryId: String, groupHash: String, keeperPath: String) {
        mutateCategory(categoryId) { items ->
            items.map { i ->
                if (i is CleanItem.Duplicate && i.group.hash == groupHash) {
                    // Exactly one survivor: the keeper stays unselected.
                    CleanItem.Duplicate(i.group.copy(
                        files = i.group.files.map { f -> f.copy(isSelected = f.path != keeperPath) },
                    ))
                } else i
            }
        }
    }

    /** DEV-SPOOF: keeper per group by age — keeper unselected, others kept. */
    fun setAllDuplicateKeepers(newest: Boolean) {
        categories.value = categories.value.map { c ->
            if (c.id != "dupes") c else recompute(c.copy(items = c.items.map { i ->
                if (i is CleanItem.Duplicate) {
                    val keeper = if (newest) i.group.files.maxByOrNull { it.lastModified }
                    else i.group.files.minByOrNull { it.lastModified }
                    CleanItem.Duplicate(i.group.copy(
                        files = i.group.files.map { f -> f.copy(isSelected = keeper != null && f.path != keeper.path) },
                    ))
                } else i
            }))
        }
    }

    private fun setSelected(i: CleanItem, s: Boolean): CleanItem = when (i) {
        is CleanItem.Corpse -> CleanItem.Corpse(i.entry.copy(isSelected = s))
        is CleanItem.GenericFile -> CleanItem.GenericFile(i.file.copy(isSelected = s))
        is CleanItem.EmptyDir -> CleanItem.EmptyDir(i.entry.copy(isSelected = s))
        is CleanItem.AppCache -> CleanItem.AppCache(i.entry.copy(isSelected = s))
        is CleanItem.MediaFile -> CleanItem.MediaFile(i.entry.copy(isSelected = s))
        is CleanItem.ApkFile -> CleanItem.ApkFile(i.entry.copy(isSelected = s))
        is CleanItem.UnusedApp -> CleanItem.UnusedApp(i.entry.copy(isSelected = s))
        is CleanItem.Duplicate -> i
    }

    private fun mutateCategory(categoryId: String, block: (List<CleanItem>) -> List<CleanItem>) {
        categories.value = categories.value.map { c ->
            if (c.id == categoryId) recompute(c.copy(items = block(c.items))) else c
        }
    }

    private fun recompute(c: CleanCategory): CleanCategory = c.copy(
        totalSize = c.items.sumOf { sizeOf(it) },
        selectedSize = c.items.filter { selected(it) }.sumOf { sizeOf(it) },
    )

    fun isCategoryFullySelected(c: CleanCategory): Boolean =
        categories.value.find { it.id == c.id }?.let { cat -> cat.items.all { selected(it) } } ?: false

    // ── Clean + trash (RAM only) ────────────────────────────────────────────

    /**
     * DEV-SPOOF: removes the selected fixture items, files removable kinds
     * into the RAM trash and returns the accounting result the screen shows.
     */
    fun deleteSelected(): CleanResult {
        var freed = 0L
        var deleted = 0
        var cleared = 0
        val now = System.currentTimeMillis()

        val newCats = categories.value.map { c ->
            val kept = mutableListOf<CleanItem>()
            c.items.forEach { item ->
                if (!selected(item)) { kept += item; return@forEach }
                when (item) {
                    // Cache "clears" vanish without a trash row (like reality).
                    is CleanItem.AppCache -> { cleared++; freed += item.entry.sizeBytes }
                    is CleanItem.Corpse, is CleanItem.EmptyDir, is CleanItem.Duplicate -> {
                        freed += sizeOf(item); deleted++
                    }
                    else -> {
                        freed += sizeOf(item); deleted++
                        val catId = c.id
                        val entity = CleanerTrashEntity(
                            id = nextTrashId++,
                            originalPath = pathOf(item) ?: c.id,
                            trashPath = null, sizeBytes = sizeOf(item),
                            deletedAt = now,
                            expiresAt = now + 7 * 24 * 60 * 60 * 1000L,
                            type = when (item) {
                                is CleanItem.MediaFile -> "media"
                                is CleanItem.ApkFile -> "apk"
                                else -> "file"
                            },
                        )
                        trashed += Trashed(entity, catId, item)
                        trash.value = trash.value + entity
                    }
                }
            }
            recompute(c.copy(items = kept))
        }
        categories.value = newCats

        return CleanResult(
            freedBytes = freed, deletedCount = deleted, failedCount = 0,
            trashedCount = trash.value.size, clearedCount = cleared,
            alreadyCleanCount = 0,
        )
    }

    private fun pathOf(i: CleanItem): String? = when (i) {
        is CleanItem.GenericFile -> i.file.path
        is CleanItem.MediaFile -> i.entry.path
        is CleanItem.ApkFile -> i.entry.path
        else -> null
    }

    val trashRows: List<CleanerTrashEntity> get() = trash.value

    /** DEV-SPOOF: RAM restore — row leaves the fake trash, item reappears. */
    fun restoreItem(id: Long): Boolean {
        val hit = trash.value.find { it.id == id } ?: return false
        val record = trashed.firstOrNull { it.entity.id == id } ?: return false
        trash.value = trash.value.filterNot { it.id == id }
        trashed.remove(record)
        reinsert(record)
        return true
    }

    fun restoreItems(ids: Set<Long>): Int {
        var n = 0
        ids.forEach { if (restoreItem(it)) n++ }
        return n
    }

    fun restoreAll(): Int {
        val n = trash.value.size
        val rows = trash.value
        trash.value = emptyList()
        val moved = trashed.toList()
        trashed.clear()
        rows.forEach { row -> moved.firstOrNull { it.entity.id == row.id }?.let { reinsert(it) } }
        return n
    }

    fun deletePermanently(ids: Set<Long>): Int {
        val removedCount = trash.value.count { it.id in ids }
        trash.value = trash.value.filterNot { it.id in ids }
        trashed.removeAll { it.entity.id in ids }
        return removedCount
    }

    fun emptyTrash(): Int {
        val n = trash.value.size
        trash.value = emptyList()
        trashed.clear()
        return n
    }

    /** DEV-SPOOF: files reappear under their fixture category; caches don't. */
    private fun reinsert(t: Trashed) {
        val catId = t.categoryId ?: return
        if (t.item is CleanItem.AppCache) return // cleared cache has no file back
        categories.value = categories.value.map { c ->
            if (c.id == catId) recompute(c.copy(items = c.items + t.item)) else c
        }
    }

    fun reset() {
        categories.value = emptyList()
        trash.value = emptyList()
        trashed.clear()
        nextTrashId = 1L
    }
}
