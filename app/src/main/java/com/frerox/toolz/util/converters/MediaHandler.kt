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
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FFmpeg-based handler for Video → Video, Video → Audio, Audio → Audio, and Image → Image.
 *
 * Key audio reliability fixes:
 *  - Strips video/embedded cover art streams (`-vn -map 0:a:0?`) so audio containers (M4A, MP3, WAV, etc.)
 *    never crash on embedded album art (e.g., MP3 with ID3 cover art).
 *  - Audio bitrate uses standard `-b:a` with sensible fallbacks.
 *  - FLAC/AIFF/PCM: no bitrate parameter — codec-specific flags only.
 *  - AAC / M4A: uses `-c:a aac` with `-movflags +faststart` for clean streaming/seeking.
 *  - Opus: forces `-c:a libopus -ar 48000` (standard Opus sample rate).
 *  - OGG: uses `-c:a libvorbis`.
 *  - AMR: forces `-c:a amr_nb -ar 8000 -ac 1` (AMR-NB strictly requires 8 kHz, 1 channel).
 *  - Captures full FFmpeg output logs so error details are always surfaced clearly.
 */
@Singleton
class MediaHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConversionHandler {

    override fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean,
    ): Flow<ConversionEngine.ConversionStatus> = callbackFlow {
        if (inputUris.isEmpty()) {
            trySend(ConversionEngine.ConversionStatus.Error("No input files provided"))
            close()
            return@callbackFlow
        }

        val inputUri = inputUris.first()
        val inputPath = if (inputUri.scheme == "file") {
            inputUri.path ?: run {
                trySend(ConversionEngine.ConversionStatus.Error("Invalid input path"))
                close()
                return@callbackFlow
            }
        } else {
            trySend(ConversionEngine.ConversionStatus.Error("Expected file:// URI in MediaHandler"))
            close()
            return@callbackFlow
        }

        val isImageConversion = type.category == "Images" || type.category == "Animations"
        val totalDurationMs = if (!isImageConversion) getMediaDuration(inputPath) else 0L

        trySend(ConversionEngine.ConversionStatus.Progress(0))

        val command = buildFFmpegCommand(inputPath, outputPath, type, highQuality)
        val logBuffer = StringBuilder()

        val session = FFmpegKit.executeAsync(
            command,
            { session ->
                when {
                    ReturnCode.isSuccess(session.returnCode) -> {
                        trySend(ConversionEngine.ConversionStatus.Success(outputPath))
                    }
                    ReturnCode.isCancel(session.returnCode) -> {
                        trySend(ConversionEngine.ConversionStatus.Error("Conversion cancelled"))
                    }
                    else -> {
                        val fullLog = logBuffer.toString().trim()
                        val errorLines = fullLog.lines().filter { line ->
                            line.contains("Error", ignoreCase = true) ||
                            line.contains("Invalid", ignoreCase = true) ||
                            line.contains("Could not", ignoreCase = true) ||
                            line.contains("failed", ignoreCase = true) ||
                            line.contains("unsupported", ignoreCase = true)
                        }
                        val errMsg = errorLines.lastOrNull()?.trim()
                            ?: session.failStackTrace
                            ?: if (fullLog.isNotBlank()) fullLog.lines().takeLast(3).joinToString("\n")
                            else "FFmpeg failed with exit code ${session.returnCode?.value ?: -1}"
                        trySend(ConversionEngine.ConversionStatus.Error(errMsg))
                    }
                }
                close()
            },
            { log ->
                if (log != null && log.message != null) {
                    logBuffer.appendLine(log.message)
                }
            },
            { stats: Statistics ->
                if (isImageConversion) {
                    trySend(ConversionEngine.ConversionStatus.Progress(50))
                } else if (totalDurationMs > 0) {
                    val pct = (stats.time.toDouble() / totalDurationMs * 100).toInt().coerceIn(1, 99)
                    trySend(ConversionEngine.ConversionStatus.Progress(pct))
                }
            }
        )

        awaitClose { FFmpegKit.cancel(session.sessionId) }
    }

    // ── Command builder ───────────────────────────────────────────────────────

    private fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        type: ConversionEngine.ConversionType,
        hq: Boolean,
    ): String {
        val i = "\"$inputPath\""
        val o = "\"$outputPath\""

        return when (type) {

            // ── Video → GIF ──────────────────────────────────────────────────
            ConversionEngine.ConversionType.VIDEO_TO_GIF -> {
                val filter = if (hq)
                    "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                else
                    "fps=10,scale=320:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                "-i $i -vf \"$filter\" -loop 0 -y $o"
            }

            // ── Video → Animated WebP ────────────────────────────────────────
            ConversionEngine.ConversionType.VIDEO_TO_WEBP -> {
                val q = if (hq) "80" else "55"
                "-i $i -vcodec libwebp -lossless 0 -compression_level 6 -q:v $q -loop 0 -an -y $o"
            }

            // ── Video → Audio ────────────────────────────────────────────────
            ConversionEngine.ConversionType.VIDEO_TO_MP3 ->
                "-i $i -vn -map 0:a:0? -c:a libmp3lame ${audioBitrate("320k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_WAV ->
                "-i $i -vn -map 0:a:0? -c:a pcm_s16le -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_AAC ->
                "-i $i -vn -map 0:a:0? -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_FLAC ->
                "-i $i -vn -map 0:a:0? -c:a flac -compression_level ${if (hq) "8" else "5"} -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_M4A ->
                "-i $i -vn -map 0:a:0? -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -movflags +faststart -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_OGG ->
                "-i $i -vn -map 0:a:0? -c:a libvorbis ${audioBitrate("192k", "96k", hq)} -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_AIFF ->
                "-i $i -vn -map 0:a:0? -c:a pcm_s16be -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_OPUS ->
                "-i $i -vn -map 0:a:0? -c:a libopus ${audioBitrate("128k", "64k", hq)} -ar 48000 -y $o"

            // ── Audio → Audio ────────────────────────────────────────────────
            // Crucial: -vn -map 0:a:0? strips embedded cover art video streams
            // that cause FFmpeg to fail when muxing into audio-only containers.
            ConversionEngine.ConversionType.AUDIO_TO_MP3 ->
                "-i $i -vn -map 0:a:0? -c:a libmp3lame ${audioBitrate("320k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_WAV ->
                "-i $i -vn -map 0:a:0? -c:a pcm_s16le -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AAC ->
                "-i $i -vn -map 0:a:0? -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_M4A ->
                "-i $i -vn -map 0:a:0? -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -movflags +faststart -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_FLAC ->
                "-i $i -vn -map 0:a:0? -c:a flac -compression_level ${if (hq) "8" else "5"} -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_OGG ->
                "-i $i -vn -map 0:a:0? -c:a libvorbis ${audioBitrate("192k", "96k", hq)} -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_OPUS ->
                "-i $i -vn -map 0:a:0? -c:a libopus ${audioBitrate("128k", "64k", hq)} -ar 48000 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AMR ->
                "-i $i -vn -map 0:a:0? -c:a amr_nb -ar 8000 -ac 1 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AIFF ->
                "-i $i -vn -map 0:a:0? -c:a pcm_s16be -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_MKA ->
                "-i $i -vn -map 0:a:0? -c:a libopus ${audioBitrate("192k", "96k", hq)} -ar 48000 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AC3 ->
                "-i $i -vn -map 0:a:0? -c:a ac3 ${audioBitrate("448k", "192k", hq)} -ar 48000 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_MP2 ->
                "-i $i -vn -map 0:a:0? -c:a mp2 ${audioBitrate("192k", "128k", hq)} -ar 44100 -y $o"

            // ── Image → Image ────────────────────────────────────────────────
            ConversionEngine.ConversionType.IMAGE_TO_JPG -> {
                val q = if (hq) "2" else "5"
                "-i $i -q:v $q -y $o"
            }

            ConversionEngine.ConversionType.IMAGE_TO_PNG ->
                "-i $i -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_WEBP -> {
                val q = if (hq) "85" else "60"
                "-i $i -vcodec libwebp -lossless 0 -q:v $q -y $o"
            }

            ConversionEngine.ConversionType.IMAGE_TO_GIF ->
                "-i $i -vf \"scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse\" -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_BMP ->
                "-i $i -pix_fmt bgr24 -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_TIFF ->
                "-i $i -vf format=rgb24 -compression_level 6 -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_ICO ->
                "-i $i -vf scale=256:256:flags=lanczos -vcodec png -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_HEIF ->
                "-i $i -c:v libx265 -crf ${if (hq) "18" else "28"} -tag:v hvc1 -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_AVIF ->
                "-i $i -c:v libaom-av1 -crf ${if (hq) "23" else "35"} -b:v 0 -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_TGA ->
                "-i $i -f targa -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_PPM ->
                "-i $i -pix_fmt rgb24 -y $o"

            // ── Video → Video (default) ──────────────────────────────────────
            else -> when (type.category) {
                "Videos" -> {
                    val (codec, crf, preset) = if (type.extension == "webm") {
                        Triple("libvpx-vp9", if (hq) "22" else "35", "")
                    } else if (type.extension == "mkv") {
                        Triple("libx265", if (hq) "20" else "30", "-preset ${if (hq) "slow" else "fast"}")
                    } else {
                        Triple("libx264", if (hq) "18" else "28", "-preset ${if (hq) "slow" else "ultrafast"}")
                    }
                    val audioFlags = "-c:a aac ${audioBitrate("192k", "128k", hq)}"
                    "-i $i -c:v $codec -crf $crf $preset -pix_fmt yuv420p $audioFlags -y $o"
                }
                else -> "-i $i -y $o"
            }
        }
    }

    private fun audioBitrate(hqRate: String, lqRate: String, hq: Boolean) =
        "-b:a ${if (hq) hqRate else lqRate}"

    private fun getMediaDuration(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            ms?.toLong() ?: 0L
        } catch (_: Exception) { 0L }
    }
}
