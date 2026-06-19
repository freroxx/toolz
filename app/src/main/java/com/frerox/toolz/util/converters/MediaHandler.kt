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
 * Key fixes vs. original:
 *  - Uses `-b:a` (not `-ab`) for audio bitrate — correct across all codecs
 *  - FLAC/AIFF/PCM: no bitrate parameter — use codec-specific flags only
 *  - Opus: force libopus encoder
 *  - OGG: force libvorbis encoder
 *  - AMR: force 8 kHz mono + amr_nb encoder
 *  - Removed WMA/CAF (not supported in standard ffmpeg-kit build)
 *  - H.265/HEVC for MKV, WEBM uses VP9
 *  - Image formats: progress emitted as 0→50→100 (no time-based stat)
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
        // Accept file:// URIs directly (already copied by ConversionEngine)
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
        val totalDurationMs = if (!isImageConversion) getVideoDuration(inputPath) else 0L

        trySend(ConversionEngine.ConversionStatus.Progress(0))

        val command = buildFFmpegCommand(inputPath, outputPath, type, highQuality)

        val session = FFmpegKit.executeAsync(command, { session ->
            when {
                ReturnCode.isSuccess(session.returnCode) -> {
                    trySend(ConversionEngine.ConversionStatus.Success(outputPath))
                }
                ReturnCode.isCancel(session.returnCode) -> {
                    trySend(ConversionEngine.ConversionStatus.Error("Conversion cancelled"))
                }
                else -> {
                    val logs = session.logs
                    val errMsg = logs
                        .filter { it.level.name == "ERROR" || it.level.name == "FATAL" }
                        .lastOrNull()?.message?.trim()
                        ?: session.failStackTrace
                        ?: "Unknown FFmpeg error"
                    trySend(ConversionEngine.ConversionStatus.Error(errMsg))
                }
            }
            close()
        }, { _ -> /* log callback — intentionally empty */ }) { stats: Statistics ->
            if (isImageConversion) {
                // Image conversions don't have time-based stats; emit a half-way progress
                trySend(ConversionEngine.ConversionStatus.Progress(50))
            } else if (totalDurationMs > 0) {
                val pct = (stats.time.toDouble() / totalDurationMs * 100).toInt().coerceIn(1, 99)
                trySend(ConversionEngine.ConversionStatus.Progress(pct))
            }
        }

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
                "-i $i -vn -c:a libmp3lame ${audioBitrate("320k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_WAV ->
                "-i $i -vn -c:a pcm_s16le -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_AAC ->
                "-i $i -vn -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_FLAC ->
                "-i $i -vn -c:a flac -compression_level ${if (hq) "8" else "5"} -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_M4A ->
                "-i $i -vn -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -movflags +faststart -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_OGG ->
                "-i $i -vn -c:a libvorbis ${audioBitrate("192k", "96k", hq)} -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_AIFF ->
                "-i $i -vn -c:a pcm_s16be -ar 44100 -y $o"

            ConversionEngine.ConversionType.VIDEO_TO_OPUS ->
                "-i $i -vn -c:a libopus ${audioBitrate("128k", "64k", hq)} -ar 48000 -y $o"

            // ── Audio → Audio ────────────────────────────────────────────────
            ConversionEngine.ConversionType.AUDIO_TO_MP3 ->
                "-i $i -c:a libmp3lame ${audioBitrate("320k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_WAV ->
                "-i $i -c:a pcm_s16le -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AAC ->
                "-i $i -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_M4A ->
                "-i $i -c:a aac ${audioBitrate("256k", "128k", hq)} -ar 44100 -movflags +faststart -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_FLAC ->
                "-i $i -c:a flac -compression_level ${if (hq) "8" else "5"} -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_OGG ->
                "-i $i -c:a libvorbis ${audioBitrate("192k", "96k", hq)} -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_OPUS ->
                "-i $i -c:a libopus ${audioBitrate("128k", "64k", hq)} -ar 48000 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AMR ->
                "-i $i -c:a amr_nb -ar 8000 -ac 1 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AIFF ->
                "-i $i -c:a pcm_s16be -ar 44100 -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_MKA ->
                "-i $i -c:a libopus ${audioBitrate("192k", "96k", hq)} -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_AC3 ->
                "-i $i -c:a ac3 ${audioBitrate("448k", "192k", hq)} -y $o"

            ConversionEngine.ConversionType.AUDIO_TO_MP2 ->
                "-i $i -c:a mp2 ${audioBitrate("192k", "128k", hq)} -y $o"

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
                "-i $i -compression_algo deflate -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_ICO ->
                "-i $i -vf scale=256:256 -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_HEIF ->
                "-i $i -c:v libx265 -crf ${if (hq) "18" else "28"} -tag:v hvc1 -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_AVIF ->
                "-i $i -c:v libaom-av1 -crf ${if (hq) "23" else "35"} -b:v 0 -y $o"

            ConversionEngine.ConversionType.IMAGE_TO_TGA ->
                "-i $i -y $o"

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

    private fun getVideoDuration(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            ms?.toLong() ?: 0L
        } catch (_: Exception) { 0L }
    }
}
