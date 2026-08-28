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

package com.frerox.toolz.data.ai

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File

object LyricsExtractor {
    private const val TAG = "LyricsExtractor"

    /**
     * Extracts embedded lyrics from an audio file path or Uri using MediaMetadataRetriever.
     */
    fun extractEmbeddedLyrics(context: Context, uriString: String?, path: String?): String? {
        val retriever = MediaMetadataRetriever()
        try {
            if (path != null && File(path).exists()) {
                retriever.setDataSource(path)
            } else if (uriString != null && (uriString.startsWith("content://") || uriString.startsWith("file://"))) {
                retriever.setDataSource(context, Uri.parse(uriString))
            } else {
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Key 1000 is MediaMetadataRetriever.METADATA_KEY_LYRICS
                val lyrics = retriever.extractMetadata(1000)
                if (!lyrics.isNullOrBlank() && lyrics.trim() != "null") {
                    Log.d(TAG, "Successfully extracted embedded lyrics (API 31+ METADATA_KEY_LYRICS)")
                    return lyrics
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting embedded lyrics: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }
        return null
    }

    /**
     * Checks adjacent local sidecar files (.lrc, .txt) in the same directory as the track path.
     */
    fun findLocalSidecarLyrics(path: String?): Pair<String, Boolean>? {
        if (path.isNullOrBlank()) return null
        val audioFile = File(path)
        if (!audioFile.exists()) return null

        val parentDir = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        // P3-14 extend sidecar to srt/vtt (convert via CaptionConverter)
        val candidates = listOf(
            File(parentDir, "$baseName.lrc"),
            File(parentDir, "$baseName.LRC"),
            File(parentDir, "$baseName.txt"),
            File(parentDir, "$baseName.TXT"),
            File(parentDir, "$baseName.srt"),
            File(parentDir, "$baseName.SRT"),
            File(parentDir, "$baseName.vtt"),
            File(parentDir, "$baseName.VTT")
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                runCatching {
                    var content = candidate.readText().trim()
                    if (content.isNotBlank()) {
                        // convert srt/vtt to lrc when needed
                        if (candidate.extension.equals("srt", true) || candidate.extension.equals("vtt", true)) {
                            content = com.frerox.toolz.data.catalog.CaptionConverter.convertToLrc(content) ?: content
                        }
                        val isSynced = content.contains("[0") || content.contains("[1") || content.contains("[2")
                        Log.d(TAG, "Found local sidecar lyrics: ${candidate.absolutePath} (synced=$isSynced)")
                        return Pair(content, isSynced)
                    }
                }
            }
        }
        return null
    }
}
