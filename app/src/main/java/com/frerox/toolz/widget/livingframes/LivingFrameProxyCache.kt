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

package com.frerox.toolz.widget.livingframes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Widget-size copies of video frames.
 *
 * Stored frames are up to 1920–2560 px WebP. Decoding one costs the same
 * whether or not the widget needs that many pixels (libwebp scales after it
 * has decoded the full picture), so a 460 px widget was paying for a 1080p
 * decode on every frame. A proxy is the frame already cropped and scaled to
 * the exact widget render size and stored as JPEG, which decodes several
 * times faster and needs no scaling.
 *
 * Proxies hold only the opaque picture. Corners and opacity are applied after
 * loading, so changing them never invalidates the cache. Only FILL video is
 * proxied: FIT would need transparent letterboxing, which JPEG cannot hold.
 *
 * This lives in cacheDir. The system may delete it at any time; the service
 * simply regenerates from the stored frames.
 */
internal class LivingFrameProxyCache(private val context: Context) {

    private val root: File get() = File(context.cacheDir, "living_proxy")

    fun dirFor(frameId: String, width: Int, height: Int): File =
        File(root, "$frameId/${width}x$height")

    /** Avoid filling a nearly full device with a cache. */
    fun hasSpace(): Boolean = context.cacheDir.usableSpace > MIN_FREE_BYTES

    fun read(dir: File, index: Int, rgb565: Boolean): Bitmap? {
        val file = file(dir, index)
        if (!file.isFile) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        val bitmap = try {
            BitmapFactory.decodeFile(file.path, options)
        } catch (_: OutOfMemoryError) {
            return null
        } catch (_: Exception) {
            null
        }
        // A truncated or corrupt file is removed so it gets regenerated.
        if (bitmap == null) runCatching { file.delete() }
        return bitmap
    }

    fun write(dir: File, index: Int, bitmap: Bitmap) {
        runCatching {
            dir.mkdirs()
            val target = file(dir, index)
            val temp = File(dir, String.format(Locale.US, "frame_%04d.tmp", index))
            val ok = FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (!ok || !temp.renameTo(target)) temp.delete()
        }
    }

    /**
     * Drop proxies for sets that no longer exist and keep only the few most
     * recently used sizes of the current set (two widgets of one set can have
     * different sizes, so keeping just one would make them evict each other).
     */
    fun prune(currentId: String, keep: File, exists: (String) -> Boolean) {
        runCatching {
            keep.mkdirs()
            keep.setLastModified(System.currentTimeMillis())
            for (idDir in root.listFiles().orEmpty()) {
                if (!idDir.isDirectory) continue
                if (!exists(idDir.name)) {
                    idDir.deleteRecursively()
                    continue
                }
                if (idDir.name != currentId) continue
                idDir.listFiles().orEmpty()
                    .filter { it.isDirectory && it != keep }
                    .sortedByDescending { it.lastModified() }
                    .drop(MAX_EXTRA_SIZES)
                    .forEach { it.deleteRecursively() }
            }
        }
    }

    private fun file(dir: File, index: Int) =
        File(dir, String.format(Locale.US, "frame_%04d.jpg", index))

    private companion object {
        const val QUALITY = 92
        const val MAX_EXTRA_SIZES = 2
        const val MIN_FREE_BYTES = 256L * 1024 * 1024
    }
}
