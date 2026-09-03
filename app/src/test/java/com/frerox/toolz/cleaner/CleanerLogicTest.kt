/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.cleaner

import com.frerox.toolz.data.cleaner.MediaType
import com.frerox.toolz.data.cleaner.analyzer.MediaClutterAnalyzer
import com.frerox.toolz.data.cleaner.engine.AppCacheOutcome
import com.frerox.toolz.data.cleaner.engine.IndexedFile
import com.frerox.toolz.data.cleaner.engine.accumulateDirSizes
import com.frerox.toolz.data.cleaner.engine.decideAppCacheOutcome
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
}
