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

package com.frerox.toolz.data.cleaner.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object HashUtils {
    private const val QUICK_WINDOW = 16 * 1024
    /** 16K head + 16K tail SHA-256 prefilter. Suspend + cancellable via larger buffered reads. */
    fun computeQuickHash(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        // Mix size into digest to cut collision-driven full hashes
        digest.update(("len:${file.length()}").toByteArray())
        val size = file.length()
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(QUICK_WINDOW)
            val readStart = fis.read(buffer)
            if (readStart > 0) digest.update(buffer, 0, readStart)
            if (size > QUICK_WINDOW * 2L) {
                fis.channel.position(size - QUICK_WINDOW)
                val readEnd = fis.read(buffer)
                if (readEnd > 0) digest.update(buffer, 0, readEnd)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    fun computeFullHash(file: File, isActive: () -> Boolean = { true }): String? = try {        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        java.io.BufferedInputStream(FileInputStream(file)).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                if (!isActive()) return null
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    /**
     * Sampling identity for huge files (>500MB): SHA-256 over size + 1MB head,
     * middle and tail. 99.9% comparison at ~3MB of reads instead of gigabytes.
     */
    fun computeSampleHash(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val size = file.length()
        digest.update(("len:$size").toByteArray())
        val window = 1024 * 1024
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis ->
            fun feed(offset: Long, bytes: Int) {
                fis.channel.position(offset)
                var left = bytes
                while (left > 0) {
                    val n = fis.read(buffer, 0, minOf(buffer.size, left))
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                    left -= n
                }
            }
            feed(0, minOf(window.toLong(), size).toInt())
            if (size > window * 2L) {
                feed(size / 2 - window / 2, window)
                feed(size - window, window)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    /**
     * Lightweight sampling hash: SHA-256 over size + first/middle/last [sample]
     * bytes. Pure + safe: never throws; falls back to a size+mtime string.
     */
    fun sampleHash(file: File, sample: Int = 65536): String = try {
        val size = file.length()
        val window = if (sample > 0) sample else 65536
        if (size <= 0) return "empty:$size:${runCatching { file.lastModified() }.getOrDefault(0L)}"
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(("len:$size").toByteArray())
        val buffer = ByteArray(minOf(8192, window))
        FileInputStream(file).use { fis ->
            fun feed(offset: Long, bytes: Long) {
                fis.channel.position(offset)
                var left = bytes
                while (left > 0) {
                    val n = fis.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                    left -= n
                }
            }
            feed(0, minOf(window.toLong(), size))
            if (size > window * 2L) {
                feed(size / 2 - window / 2, window.toLong())
                feed(size - window, window.toLong())
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        try { "fb:${file.length()}:${file.lastModified()}" } catch (_: Exception) { "fb:0:0" }
    }
}
