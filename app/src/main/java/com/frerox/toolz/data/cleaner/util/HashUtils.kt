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
    fun computeQuickHash(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val size = file.length()
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(4096)
            val readStart = fis.read(buffer)
            if (readStart > 0) digest.update(buffer, 0, readStart)
            if (size > 8192) {
                fis.channel.position(size - 4096)
                val readEnd = fis.read(buffer)
                if (readEnd > 0) digest.update(buffer, 0, readEnd)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }

    fun computeFullHash(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        FileInputStream(file).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { null }
}
