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

package com.frerox.toolz.util.converters

import android.content.Context
import android.net.Uri
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArchiveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConversionHandler {
    override fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean,
    ): Flow<ConversionEngine.ConversionStatus> = flow {
        emit(ConversionEngine.ConversionStatus.Progress(0))
        try {
            if (type == ConversionEngine.ConversionType.XAPK_TO_APK) {
                var largestApkEntry: ZipEntry? = null
                var largestApkSize: Long = -1

                // First pass: find the largest APK entry
                for (uri in inputUris) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        ZipInputStream(stream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                                    // Some ZIP entries might not have size known until read, but usually they do.
                                    // If size is unknown (-1), we could extract all to temp to check sizes,
                                    // but let's assume size is known or we just pick the first one.
                                    val size = entry.size
                                    if (size > largestApkSize) {
                                        largestApkSize = size
                                        largestApkEntry = entry
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    }
                }

                if (largestApkEntry != null) {
                    // Second pass: extract the found entry
                    var extracted = false
                    for (uri in inputUris) {
                        if (extracted) break
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            ZipInputStream(stream).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    if (entry.name == largestApkEntry!!.name && entry.size == largestApkEntry!!.size) {
                                        FileOutputStream(File(outputPath)).use { out ->
                                            zis.copyTo(out)
                                        }
                                        extracted = true
                                        break
                                    }
                                    zis.closeEntry()
                                    entry = zis.nextEntry
                                }
                            }
                        }
                    }
                } else {
                    throw Exception("No APK files found inside the archive.")
                }
            } else if (type.name.startsWith("ZIP_")) {
                // Implement logic if converting ZIP to something else
            }
            emit(ConversionEngine.ConversionStatus.Progress(100))
            emit(ConversionEngine.ConversionStatus.Success(outputPath))
        } catch (e: Exception) {
            emit(ConversionEngine.ConversionStatus.Error("Archive conversion failed: ${e.localizedMessage}"))
        }
    }
}
