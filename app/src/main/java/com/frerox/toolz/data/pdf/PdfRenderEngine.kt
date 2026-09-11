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

package com.frerox.toolz.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable rendering layer over [PdfRenderer].
 *
 * V2 rules (remake):
 * - One short-lived [PdfRenderer] per call — never held across composition.
 *   Fast scroll therefore can't leak FDs; the FD is closed in `use {}`.
 * - Small target widths for thumbs + OOM pixel cap; everything ARGB_8888
 *   (PdfRenderer rejects all other configs).
 * - In-memory LRU keyed "$uri#$page#$bucket" so pager neighbours hit cache.
 */
@Singleton
class PdfRenderEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class PageSize(val width: Int, val height: Int)

    private val thumbCacheKB = 8 * 1024
    private val pageCacheKB: Int =
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceIn(12 * 1024, 48 * 1024)

    private val thumbCache = object : LruCache<String, Bitmap>(thumbCacheKB) {
        override fun sizeOf(key: String, v: Bitmap) = v.byteCount / 1024
    }
    private val pageCache = object : LruCache<String, Bitmap>(pageCacheKB) {
        override fun sizeOf(key: String, v: Bitmap) = v.byteCount / 1024
    }

    /** Striped per-URI locks: same-file opens stay serialized (the framework
     *  FD path isn't thread-safe per uri) while different files render in
     *  parallel — a single global mutex turned thumbnail warmup into a
     *  10s+ convoy on large libraries. Bounded; no leak. */
    private val openStripes = Array(16) { Mutex() }
    private fun mutexFor(uri: Uri) =
        openStripes[(uri.toString().hashCode() and Int.MAX_VALUE) % openStripes.size]

    suspend fun getPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        mutexFor(uri).withLock {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                } ?: 0
            } catch (_: SecurityException) {
                -1 // revoked / no persistable permission
            } catch (_: Exception) {
                0
            }
        }
    }

    suspend fun getPageSize(uri: Uri, pageIndex: Int): PageSize? = withContext(Dispatchers.IO) {
        mutexFor(uri).withLock {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex >= renderer.pageCount) return@withContext null
                        renderer.openPage(pageIndex).use { PageSize(it.width, it.height) }
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Render [pageIndex] so its width ≈ [targetWidthPx]. Height follows aspect.
     * Returns cached bitmap when bucket matches; caller must NOT recycle.
     */
    suspend fun renderPage(
        uri: Uri,
        pageIndex: Int,
        targetWidthPx: Int,
        highQuality: Boolean = false
    ): Bitmap? {
        val bucket = when {
            targetWidthPx <= 360 -> "thumb"
            targetWidthPx <= 900 -> "mid"
            else -> "full"
        }
        val key = "$uri#$pageIndex#$bucket"
        synchronized(pageCache) { pageCache.get(key)?.let { return it } }
        if (bucket == "thumb") synchronized(thumbCache) { thumbCache.get(key)?.let { return it } }

        return withContext(Dispatchers.IO) {
            mutexFor(uri).withLock {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            if (pageIndex >= renderer.pageCount) return@withContext null
                            renderer.openPage(pageIndex).use { page ->
                                val aspect = if (page.width == 0) 1.41f
                                else page.height.toFloat() / page.width.toFloat()
                                var w = targetWidthPx.coerceIn(120, 2200)
                                var h = (w * aspect).toInt().coerceIn(120, 3200)
                                // OOM guard: cap decoded pixels (~4MP thumb-mid, ~8MP full)
                                val maxPx = if (highQuality) 8_000_000 else 4_200_000
                                val px = w.toLong() * h.toLong()
                                if (px > maxPx) {
                                    val f = kotlin.math.sqrt(maxPx.toDouble() / px)
                                    w = (w * f).toInt().coerceAtLeast(120)
                                    h = (h * f).toInt().coerceAtLeast(120)
                                }
                                // NB: PdfRenderer.Page.render only supports ARGB_8888 —
                                // RGB_565 throws IllegalArgumentException ("Unsupported
                                // pixel format"), so thumbs use it too and stay small
                                // instead (see width cap + thumbCache budget).
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(
                                    bmp, null, null,
                                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )
                                synchronized(pageCache) { pageCache.put(key, bmp) }
                                if (bucket == "thumb") synchronized(thumbCache) {
                                    thumbCache.put(key, bmp)
                                }
                                bmp
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    android.util.Log.w("PdfRender", "denied: $uri ${e.message}")
                    null
                } catch (e: Exception) {
                    android.util.Log.w("PdfRender", "render failed: $uri p$pageIndex ${e.javaClass.simpleName} ${e.message}")
                    null
                }
            }
        }
    }

    suspend fun renderThumbnail(uri: Uri, widthPx: Int = 320): Bitmap? =
        renderPage(uri, 0, widthPx.coerceIn(160, 480))

    fun clearMemory() {
        synchronized(pageCache) { pageCache.evictAll() }
        synchronized(thumbCache) { thumbCache.evictAll() }
    }

    /** Long-lived session for back-to-back reads (TOC pass, text-index pass). */
    inner class Session(fd: ParcelFileDescriptor) : AutoCloseable {
        private val pfd: ParcelFileDescriptor = fd
        private val renderer = PdfRenderer(pfd)
        val pageCount: Int get() = renderer.pageCount
        fun pageSize(i: Int): PageSize =
            renderer.openPage(i).use { PageSize(it.width, it.height) }

        override fun close() {
            try { renderer.close() } catch (_: Exception) { }
            try { pfd.close() } catch (_: Exception) { }
        }
    }

    fun openSession(uri: Uri): Session? = try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        Session(pfd)
    } catch (_: Exception) {
        null
    }
}
