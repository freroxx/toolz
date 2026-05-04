package com.frerox.toolz.util.converters

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ConversionHandler {

    override fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean
    ): Flow<ConversionEngine.ConversionStatus> = callbackFlow {
        if (inputUris.isEmpty()) {
            trySend(ConversionEngine.ConversionStatus.Error("No input files provided"))
            close()
            return@callbackFlow
        }

        // FFmpeg handler mostly deals with single input for basic conversions.
        // Merging and complex document tasks will be handled by specialized handlers.
        val inputUri = inputUris.first()
        val inputPath = getFilePathFromUri(inputUri) ?: run {
            trySend(ConversionEngine.ConversionStatus.Error("Invalid input file"))
            close()
            return@callbackFlow
        }

        var totalDurationMs = 0L
        if (type.category != "Images" && type.extension != "pdf") {
            totalDurationMs = getVideoDuration(inputPath)
        }

        val command = buildFFmpegCommand(inputPath, outputPath, type, highQuality)

        val session = FFmpegKit.executeAsync(command, { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                trySend(ConversionEngine.ConversionStatus.Success(outputPath))
            } else if (ReturnCode.isCancel(returnCode)) {
                trySend(ConversionEngine.ConversionStatus.Error("Conversion cancelled"))
            } else {
                val logs = session.logs
                val errorLogs = logs.filter { it.level.name == "ERROR" || it.level.name == "FATAL" }
                val lastError = errorLogs.lastOrNull()?.message 
                    ?: logs.lastOrNull { !it.message.contains("libswresample") && !it.message.contains("ffmpeg version") }?.message
                    ?: session.failStackTrace 
                    ?: "Unknown FFmpeg error"
                trySend(ConversionEngine.ConversionStatus.Error("Conversion failed: $lastError"))
            }
            close()
        }, { _ ->
        }) { statistics: Statistics ->
            if (totalDurationMs > 0) {
                val progress = (statistics.time.toDouble() / totalDurationMs.toDouble() * 100).toInt()
                trySend(ConversionEngine.ConversionStatus.Progress(progress.coerceIn(0, 100)))
            } else {
                // For images or single frame extraction, we don't have time-based progress
                trySend(ConversionEngine.ConversionStatus.Progress(-1))
            }
        }

        awaitClose {
            FFmpegKit.cancel(session.sessionId)
            if (inputPath.contains("input_temp_")) {
                File(inputPath).delete()
            }
        }
    }

    private fun buildFFmpegCommand(
        inputPath: String,
        outputPath: String,
        type: ConversionEngine.ConversionType,
        highQuality: Boolean
    ): String {
        return when (type) {
            ConversionEngine.ConversionType.VIDEO_TO_GIF -> {
                val filter = if (highQuality) {
                    "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                } else {
                    "fps=10,scale=320:-1:flags=lanczos"
                }
                "-i \"$inputPath\" -vf \"$filter\" -y \"$outputPath\""
            }
            ConversionEngine.ConversionType.VIDEO_TO_WEBP -> {
                val quality = if (highQuality) "75" else "50"
                "-i \"$inputPath\" -vcodec libwebp -lossless 0 -compression_level 6 -q:v $quality -loop 0 -an -y \"$outputPath\""
            }
            ConversionEngine.ConversionType.VIDEO_TO_MP3, ConversionEngine.ConversionType.VIDEO_TO_WAV, 
            ConversionEngine.ConversionType.VIDEO_TO_AAC, ConversionEngine.ConversionType.VIDEO_TO_FLAC,
            ConversionEngine.ConversionType.VIDEO_TO_M4A, ConversionEngine.ConversionType.VIDEO_TO_OGG,
            ConversionEngine.ConversionType.VIDEO_TO_AIFF, ConversionEngine.ConversionType.VIDEO_TO_OPUS -> {
                val bitrate = if (highQuality) "320k" else "128k"
                "-i \"$inputPath\" -vn -ab $bitrate -ar 44100 -y \"$outputPath\""
            }
            ConversionEngine.ConversionType.IMAGE_TO_WEBP -> {
                val quality = if (highQuality) "85" else "60"
                "-i \"$inputPath\" -vcodec libwebp -lossless 0 -q:v $quality -y \"$outputPath\""
            }
            ConversionEngine.ConversionType.IMAGE_TO_JPG -> {
                val quality = if (highQuality) "2" else "5"
                "-i \"$inputPath\" -q:v $quality -y \"$outputPath\""
            }
            ConversionEngine.ConversionType.IMAGE_TO_PDF -> {
                "-i \"$inputPath\" \"$outputPath\""
            }
            ConversionEngine.ConversionType.VIDEO_TO_PDF -> {
                "-i \"$inputPath\" -frames:v 1 \"$outputPath\""
            }
            ConversionEngine.ConversionType.AUDIO_TO_MP3, ConversionEngine.ConversionType.AUDIO_TO_WAV, 
            ConversionEngine.ConversionType.AUDIO_TO_AAC, ConversionEngine.ConversionType.AUDIO_TO_OGG, 
            ConversionEngine.ConversionType.AUDIO_TO_FLAC, ConversionEngine.ConversionType.AUDIO_TO_M4A,
            ConversionEngine.ConversionType.AUDIO_TO_OPUS, ConversionEngine.ConversionType.AUDIO_TO_AMR,
            ConversionEngine.ConversionType.AUDIO_TO_WMA, ConversionEngine.ConversionType.AUDIO_TO_AIFF,
            ConversionEngine.ConversionType.AUDIO_TO_MKA, ConversionEngine.ConversionType.AUDIO_TO_AC3,
            ConversionEngine.ConversionType.AUDIO_TO_MP2, ConversionEngine.ConversionType.AUDIO_TO_AU,
            ConversionEngine.ConversionType.AUDIO_TO_CAF, ConversionEngine.ConversionType.AUDIO_TO_VOC -> {
                val bitrate = if (highQuality) "320k" else "128k"
                "-i \"$inputPath\" -ab $bitrate -y \"$outputPath\""
            }
            else -> {
                if (type.category == "Videos") {
                    if (highQuality) {
                        "-i \"$inputPath\" -c:v libx264 -crf 18 -preset slow -pix_fmt yuv420p -c:a aac -b:a 192k -y \"$outputPath\""
                    } else {
                        "-i \"$inputPath\" -c:v libx264 -crf 28 -preset ultrafast -pix_fmt yuv420p -c:a aac -b:a 128k -y \"$outputPath\""
                    }
                } else {
                    "-i \"$inputPath\" -y \"$outputPath\""
                }
            }
        }
    }

    private fun getVideoDuration(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        
        return try {
            val file = File(context.cacheDir, "input_temp_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
