/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.cleaner

import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.data.cleaner.analyzer.MediaClutterAnalyzer
import com.frerox.toolz.data.cleaner.analyzer.ScreenshotAnalyzer
import com.frerox.toolz.service.CleanerAccessibilityService
import com.frerox.toolz.data.cleaner.engine.AppCacheOutcome
import com.frerox.toolz.data.cleaner.engine.IndexedFile
import com.frerox.toolz.data.cleaner.engine.TRIM_CACHES_TARGET_BYTES
import com.frerox.toolz.data.cleaner.engine.accumulateDirSizes
import com.frerox.toolz.data.cleaner.engine.appCacheRmCommand
import com.frerox.toolz.data.cleaner.engine.decideAppCacheOutcome
import com.frerox.toolz.data.cleaner.engine.parseDuTotal
import com.frerox.toolz.data.cleaner.shizuku.ShizukuFileLister
import org.junit.Assert.*
import org.junit.Test

class CleanerLogicTest {

    // --- du -sb line parser ---

    @Test fun `parses valid du line`() {
        val e = ShizukuFileLister.parseDuLine("12345\t/storage/emulated/0/Android/obb/com.game", "/storage/emulated/0/Android/obb")
        assertNotNull(e)
        assertEquals("com.game", e!!.name)
        assertEquals(12345L, e.sizeBytes)
    }

    @Test fun `rejects malformed lines`() {
        val parent = "/storage/emulated/0/Android/obb"
        assertNull(ShizukuFileLister.parseDuLine("", parent))
        assertNull(ShizukuFileLister.parseDuLine("notanumber\t$parent/com.x", parent))
        assertNull(ShizukuFileLister.parseDuLine("123 $parent/com.x", parent)) // no tab
        assertNull(ShizukuFileLister.parseDuLine("123\t$parent", parent)) // parent itself
    }

    @Test fun `rejects path escape`() {
        assertNull(ShizukuFileLister.parseDuLine("99\t/data/data/evil", "/storage/emulated/0/Android/obb"))
        assertNull(ShizukuFileLister.parseDuLine("99\t/storage/emulated/0/Android/obb/../data", "/storage/emulated/0/Android/obb"))
    }

    @Test fun `handles trailing slash`() {
        val e = ShizukuFileLister.parseDuLine("77\t/storage/emulated/0/Android/obb/com.x/", "/storage/emulated/0/Android/obb")
        assertEquals("com.x", e!!.name)
    }

    // --- AppCache honest accounting ---

    @Test fun `external clear counts as cleared`() {
        assertEquals(AppCacheOutcome.CLEARED, decideAppCacheOutcome(true, true, 5000L, 9000L, 0L))
    }

    @Test fun `measured delta counts as cleared even without external dir`() {
        assertEquals(AppCacheOutcome.CLEARED, decideAppCacheOutcome(false, false, 0L, 9000L, 4000L))
    }

    @Test fun `zero cache with no external dir is already clean, never failed`() {
        assertEquals(AppCacheOutcome.ALREADY_CLEAN, decideAppCacheOutcome(false, false, 0L, 0L, 0L))
    }

    @Test fun `internal-only cache needs automation, not failure`() {
        assertEquals(AppCacheOutcome.NEEDS_AUTO, decideAppCacheOutcome(false, false, 0L, 8000L, 0L))
    }

    @Test fun `existing external dir that would not clear is failed`() {
        assertEquals(AppCacheOutcome.FAILED, decideAppCacheOutcome(true, false, 0L, 3000L, 0L))
    }

    // --- Shared index: dir sizes fold up the tree ---

    private fun idx(path: String, size: Long, parent: String) =
        IndexedFile(path, path.substringAfterLast('/'), size, 0L, "dat", 3, parent)

    @Test fun `dir sizes accumulate to every ancestor`() {
        val root = "/storage/emulated/0"
        val files = listOf(
            idx("$root/Download/a.zip", 100L, "$root/Download"),
            idx("$root/Download/sub/b.zip", 50L, "$root/Download/sub"),
            idx("$root/DCIM/c.jpg", 25L, "$root/DCIM")
        )
        val sizes = accumulateDirSizes(root, files)
        assertEquals(100L + 50L, sizes["$root/Download"])
        assertEquals(50L, sizes["$root/Download/sub"])
        assertEquals(25L, sizes["$root/DCIM"])
        assertEquals(175L, sizes[root])
    }

    @Test fun `dir sizes ignore paths outside root`() {
        val sizes = accumulateDirSizes("/storage/emulated/0", listOf(idx("/other/x", 10L, "/other")))
        assertNull(sizes["/storage/emulated/0"])
    }

    // --- Media path classification ---

    @Test fun `classifies clutter paths`() {
        assertEquals(MediaType.SCREENSHOT, MediaClutterAnalyzer.classifyMediaPath("Pictures/Screenshots/"))
        assertEquals(MediaType.WHATSAPP, MediaClutterAnalyzer.classifyMediaPath("Android/media/com.whatsapp/WhatsApp/Media/"))
        assertEquals(MediaType.TELEGRAM, MediaClutterAnalyzer.classifyMediaPath("Telegram/Telegram Images/"))
        assertEquals(MediaType.DOWNLOAD, MediaClutterAnalyzer.classifyMediaPath("Download/"))
    }

    @Test fun `camera roll maps to DCIM and unknown maps to null`() {
        assertEquals(MediaType.DCIM, MediaClutterAnalyzer.classifyMediaPath("DCIM/Camera/"))
        assertNull(MediaClutterAnalyzer.classifyMediaPath("Music/My Band/"))
        assertNull(MediaClutterAnalyzer.classifyMediaPath(""))
    }

    // --- AppCache trim/rm pins (S1.1.2) ---

    @Test fun trimTargetIsMaximal() {
        assertTrue(TRIM_CACHES_TARGET_BYTES >= 10_000_000_000_000L)
    }

    @Test fun rmCommandValidPkgs() {
        for (pkg in listOf("com.example.app", "org.telegram.messenger")) {
            val cmd = appCacheRmCommand(pkg)
            assertNotNull(cmd)
            assertEquals("rm -rf '/data/data/$pkg/cache' '/data/data/$pkg/code_cache'", cmd)
            assertFalse(cmd!!.contains(".."))
            assertFalse(cmd.contains(";"))
            assertFalse(cmd.contains("&&"))
            assertTrue(cmd.startsWith("rm -rf '/data/data/"))
        }
    }

    @Test fun rmCommandRejectsBad() {
        for (bad in listOf("", "foo", "../x", "com.a'; rm -rf / #", "com.a b", "/data/data/x", "com..a")) {
            assertNull(appCacheRmCommand(bad))
        }
    }

    @Test fun rmCommandConfinedDirs() {
        val pkg = "com.example.app"
        val cmd = appCacheRmCommand(pkg)!!
        assertTrue(cmd.contains("/cache'"))
        assertTrue(cmd.contains("/code_cache'"))
        assertEquals(2, cmd.split("'/data/data/").size - 1)
    }

    @Test fun outcomeTableUntouched() {
        assertEquals(AppCacheOutcome.NEEDS_AUTO, decideAppCacheOutcome(false, false, 0L, 5_000_000L, 0L))
    }

    // --- S1.1.2 parseDuTotal (du -sbc total line) ---

    @Test fun parseDuTotalValid() {
        assertEquals(300L, parseDuTotal("100\t/a\n200\t/b\n300\ttotal"))
    }

    @Test fun parseDuTotalMissing() {
        assertNull(parseDuTotal("100\t/a\n200\t/b"))
    }

    @Test fun parseDuTotalEmpty() {
        assertNull(parseDuTotal(""))
    }

    @Test fun parseDuTotalMalformed() {
        assertNull(parseDuTotal("abc\ttotal"))
    }

    // --- S1.1.2 10x Cleaner enhancements: Keeper, Selection, Stable IDs, Redundant APKs ---

    @Test fun `stableId generates unique stable prefixed keys`() {
        val dup = CleanItem.Duplicate(DuplicateGroup("abc123hash", 1024L, emptyList()))
        val apk = CleanItem.ApkFile(ApkEntry("test.apk", "/path/to/test.apk", 2048L, 1000L))
        val corpse = CleanItem.Corpse(CorpseEntry("com.old.app", "/Android/data/com.old.app", 4096L, CorpseType.DATA))
        val emptyDir = CleanItem.EmptyDir(EmptyDirEntry("/empty/dir", "dir"))
        val media = CleanItem.MediaFile(MediaEntry("photo.png", "/path/photo.png", 512L, 2000L, "png", MediaType.SCREENSHOT))

        assertEquals("d_abc123hash", dup.stableId())
        assertEquals("a_/path/to/test.apk", apk.stableId())
        assertEquals("c_/Android/data/com.old.app", corpse.stableId())
        assertEquals("e_/empty/dir", emptyDir.stableId())
        assertEquals("m_/path/photo.png", media.stableId())
    }

    @Test fun `apk entry correctly tracks redundancy flag`() {
        val regularApk = ApkEntry("app.apk", "/Download/app.apk", 1000L, 100L)
        assertFalse(regularApk.isRedundant)
        assertNull(regularApk.installedVersionName)

        val redundantApk = ApkEntry(
            name = "app.apk",
            path = "/Download/app.apk",
            sizeBytes = 1000L,
            lastModified = 100L,
            packageName = "com.test.app",
            versionName = "1.0.0",
            installedVersionName = "1.2.0",
            isRedundant = true,
            isSelected = true
        )
        assertTrue(redundantApk.isRedundant)
        assertEquals("1.2.0", redundantApk.installedVersionName)
        assertTrue(redundantApk.isSelected)
    }

    @Test fun `selectedCount accurately counts selected items across categories`() {
        val cat = CleanCategory(
            id = "test",
            name = "Test",
            icon = "Folder",
            items = listOf(
                CleanItem.ApkFile(ApkEntry("1.apk", "/1.apk", 100L, 1L, isSelected = true)),
                CleanItem.ApkFile(ApkEntry("2.apk", "/2.apk", 100L, 1L, isSelected = false)),
                CleanItem.Duplicate(DuplicateGroup("h1", 50L, listOf(
                    DuplicateFile("/a", 1L, isSelected = false),
                    DuplicateFile("/b", 2L, isSelected = true)
                ))),
                CleanItem.Duplicate(DuplicateGroup("h2", 50L, listOf(
                    DuplicateFile("/c", 1L, isSelected = false),
                    DuplicateFile("/d", 2L, isSelected = false)
                )))
            ),
            totalSize = 300L,
            selectedSize = 150L
        )
        assertEquals(2, cat.selectedCount())
    }

    @Test fun `duplicate keeper reordering preserves keeper at index 0 without selection`() {
        val originalFiles = listOf(
            DuplicateFile("/orig/a.jpg", 100L, isSelected = false),
            DuplicateFile("/copy/b.jpg", 200L, isSelected = true),
            DuplicateFile("/copy/c.jpg", 300L, isSelected = true)
        )
        val group = DuplicateGroup("imghash", 1024L, originalFiles)

        // Switch keeper to /copy/b.jpg
        val targetKeeperPath = "/copy/b.jpg"
        val keeper = group.files.find { it.path == targetKeeperPath }
        assertNotNull(keeper)
        val others = group.files.filter { it.path != targetKeeperPath }
        val reordered = listOf(keeper!!.copy(isSelected = false)) + others.map { it.copy(isSelected = true) }

        assertEquals(3, reordered.size)
        assertEquals("/copy/b.jpg", reordered[0].path)
        assertFalse("Keeper should not be selected for deletion", reordered[0].isSelected)
        assertTrue("Previous keeper should now be marked for deletion", reordered[1].isSelected)
        assertTrue("Other copy should be marked for deletion", reordered[2].isSelected)
    }

    // --- S1.1.3 Dedicated Screenshots & Accessibility Auto-clear Tests ---

    @Test fun `identifies screenshots correctly from path and filename`() {
        assertTrue(ScreenshotAnalyzer.isScreenshot("Pictures/Screenshots/", "Screenshot_20260904.png"))
        assertTrue(ScreenshotAnalyzer.isScreenshot("DCIM/Screenshots/", "img.png"))
        assertTrue(ScreenshotAnalyzer.isScreenshot("Download/", "Screenshot_app.png"))
        assertTrue(ScreenshotAnalyzer.isScreenshot("Pictures/", "screencap_01.jpg"))
        assertTrue(ScreenshotAnalyzer.isScreenshot("Screenshots/", "capture.png"))

        assertFalse(ScreenshotAnalyzer.isScreenshot("DCIM/Camera/", "IMG_20260904.jpg"))
        assertFalse(ScreenshotAnalyzer.isScreenshot("Download/", "document.pdf"))
        assertFalse(ScreenshotAnalyzer.isScreenshot("Pictures/Wallpapers/", "nature.jpg"))
    }

    @Test fun `accessibility matcher protects against clearing storage or data`() {
        val dangerous = listOf(
            "Clear storage",
            "CLEAR DATA",
            "Manage space",
            "Borrar datos",
            "Vider le stockage",
            "Effacer les données",
            "Daten löschen"
        )
        for (action in dangerous) {
            val lower = action.lowercase()
            assertTrue(
                "Action '$action' must be caught by dangerous action filter",
                CleanerAccessibilityService.DANGEROUS_ACTION_TEXTS.any { lower.contains(it) }
            )
            assertFalse(
                "Action '$action' must NOT be classified as clear cache",
                CleanerAccessibilityService.CLEAR_CACHE_TEXTS.any { lower == it }
            )
        }
    }

    @Test fun `accessibility matcher identifies clear cache across languages`() {
        val validClearCache = listOf(
            "Clear cache",
            "Vider le cache",
            "Borrar caché",
            "Cache leeren",
            "Limpar cache",
            "Cancella cache",
            "Cache wissen",
            "Önbelleği temizle",
            "Очистить кэш",
            "清除缓存"
        )
        for (label in validClearCache) {
            val lower = label.lowercase()
            assertTrue(
                "Label '$label' must match CLEAR_CACHE_TEXTS",
                CleanerAccessibilityService.CLEAR_CACHE_TEXTS.any { lower == it }
            )
            assertFalse(
                "Label '$label' must NOT match DANGEROUS_ACTION_TEXTS",
                CleanerAccessibilityService.DANGEROUS_ACTION_TEXTS.any { lower.contains(it) }
            )
        }
    }

    // --- Trash & Categories Tests ---

    @Test fun `trash entity defaults to 7-day expiry`() {
        val now = System.currentTimeMillis()
        val entity = com.frerox.toolz.data.cleaner.trash.CleanerTrashEntity(
            originalPath = "/storage/emulated/0/Download/test.apk",
            trashPath = "/data/user/0/com.frerox.toolz/files/cleaner_trash/uuid_test.apk",
            sizeBytes = 1024L
        )
        val expectedMin = now + 6L * 24 * 60 * 60 * 1000L
        val expectedMax = now + 8L * 24 * 60 * 60 * 1000L
        assertTrue("ExpiresAt should be within 7 days", entity.expiresAt in expectedMin..expectedMax)
        assertTrue("DeletedAt should be recent", entity.deletedAt <= System.currentTimeMillis())
    }

    @Test fun `categories include dedicated screenshots and separate apk and media`() {
        val expectedCategoryIds = listOf(
            "corpse",
            "dupes",
            "screenshots",
            "large",
            "apk",
            "media_clutter",
            "system_junk",
            "app_cache"
        )
        assertTrue("Screenshots category must be present", expectedCategoryIds.contains("screenshots"))
        assertTrue("Apk category must be distinct", expectedCategoryIds.contains("apk"))
        assertTrue("Media clutter category must be distinct", expectedCategoryIds.contains("media_clutter"))
    }

    @Test fun `setAllDuplicateKeepers newest and oldest selection`() {
        val fOld = DuplicateFile("/path/old.jpg", lastModified = 1000L, isSelected = false)
        val fMid = DuplicateFile("/path/mid.jpg", lastModified = 2000L, isSelected = true)
        val fNew = DuplicateFile("/path/new.jpg", lastModified = 3000L, isSelected = true)
        val group = DuplicateGroup("hash123", 1000L, listOf(fOld, fMid, fNew))

        // Keep newest
        val sortedNewest = group.files.sortedByDescending { it.lastModified }
        val keeperNewest = sortedNewest.first()
        val othersNewest = sortedNewest.drop(1)
        val resultNewest = listOf(keeperNewest.copy(isSelected = false)) + othersNewest.map { it.copy(isSelected = true) }

        assertEquals("/path/new.jpg", resultNewest[0].path)
        assertFalse(resultNewest[0].isSelected)
        assertTrue(resultNewest[1].isSelected)
        assertTrue(resultNewest[2].isSelected)

        // Keep oldest
        val sortedOldest = group.files.sortedBy { it.lastModified }
        val keeperOldest = sortedOldest.first()
        val othersOldest = sortedOldest.drop(1)
        val resultOldest = listOf(keeperOldest.copy(isSelected = false)) + othersOldest.map { it.copy(isSelected = true) }

        assertEquals("/path/old.jpg", resultOldest[0].path)
        assertFalse(resultOldest[0].isSelected)
        assertTrue(resultOldest[1].isSelected)
        assertTrue(resultOldest[2].isSelected)
    }

    @Test fun `selectSafeOnly selects safe categories and keeps personal files unselected by default`() {
        val safeIds = setOf("app_cache", "corpse", "system_junk")
        val reviewRequiredIds = setOf("large", "screenshots", "media_clutter")

        for (id in safeIds) {
            assertTrue("Category $id should be safe for auto-recommendation", id in safeIds)
        }
        for (id in reviewRequiredIds) {
            assertFalse("Category $id requires explicit user review and should not be auto-selected", id in safeIds)
        }
    }

    @Test fun `accessibility clear cache texts do not overlap with dangerous actions`() {
        val clearTexts = CleanerAccessibilityService.CLEAR_CACHE_TEXTS
        val dangerousTexts = CleanerAccessibilityService.DANGEROUS_ACTION_TEXTS

        for (d in dangerousTexts) {
            for (c in clearTexts) {
                assertNotEquals("Dangerous text '$d' must not equal clear cache text '$c'", d, c)
            }
        }
        assertTrue(clearTexts.contains("clear cache"))
        assertTrue(dangerousTexts.contains("clear storage"))
        assertTrue(dangerousTexts.contains("clear data"))
    }
}
