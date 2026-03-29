package com.frerox.toolz.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    enum class ConversionType(
        val extension: String,
        val category: String,
        val isPopular: Boolean = false
    ) {
        // Video
        VIDEO_TO_MP4("mp4", "Videos", true),
        VIDEO_TO_MKV("mkv", "Videos", true),
        VIDEO_TO_MOV("mov", "Videos", true),
        VIDEO_TO_AVI("avi", "Videos", true),
        VIDEO_TO_WEBM("webm", "Videos", true),
        VIDEO_TO_GIF("gif", "Animations", true),
        VIDEO_TO_WEBP("webp", "Animations", true),
        VIDEO_TO_FLV("flv", "Videos"),
        VIDEO_TO_WMV("wmv", "Videos", true),
        VIDEO_TO_3GP("3gp", "Videos"),
        VIDEO_TO_MPEG("mpeg", "Videos"),
        VIDEO_TO_VOB("vob", "Videos"),
        VIDEO_TO_OGV("ogv", "Videos"),
        VIDEO_TO_M4V("m4v", "Videos"),
        VIDEO_TO_TS("ts", "Videos"),
        VIDEO_TO_MXF("mxf", "Videos"),
        VIDEO_TO_SWF("swf", "Videos"),
        VIDEO_TO_M2TS("m2ts", "Videos"),
        VIDEO_TO_DV("dv", "Videos"),
        VIDEO_TO_F4V("f4v", "Videos"),
        
        // Video to Audio
        VIDEO_TO_MP3("mp3", "Audio", true),
        VIDEO_TO_WAV("wav", "Audio", true),
        VIDEO_TO_AAC("aac", "Audio", true),
        VIDEO_TO_FLAC("flac", "Audio", true),
        VIDEO_TO_M4A("m4a", "Audio", true),
        VIDEO_TO_OGG("ogg", "Audio"),
        VIDEO_TO_AIFF("aiff", "Audio"),
        VIDEO_TO_OPUS("opus", "Audio"),
        VIDEO_TO_WMA("wma", "Audio"),

        // Audio
        AUDIO_TO_MP3("mp3", "Audio", true),
        AUDIO_TO_WAV("wav", "Audio", true),
        AUDIO_TO_AAC("aac", "Audio", true),
        AUDIO_TO_M4A("m4a", "Audio", true),
        AUDIO_TO_FLAC("flac", "Audio", true),
        AUDIO_TO_OGG("ogg", "Audio", true),
        AUDIO_TO_OPUS("opus", "Audio"),
        AUDIO_TO_AMR("amr", "Audio"),
        AUDIO_TO_WMA("wma", "Audio"),
        AUDIO_TO_AIFF("aiff", "Audio"),
        AUDIO_TO_MKA("mka", "Audio"),
        AUDIO_TO_AC3("ac3", "Audio"),
        AUDIO_TO_MP2("mp2", "Audio"),
        AUDIO_TO_AU("au", "Audio"),
        AUDIO_TO_CAF("caf", "Audio"),
        AUDIO_TO_VOC("voc", "Audio"),

        // Image
        IMAGE_TO_JPG("jpg", "Images", true),
        IMAGE_TO_PNG("png", "Images", true),
        IMAGE_TO_WEBP("webp", "Images", true),
        IMAGE_TO_GIF("gif", "Animations", true),
        IMAGE_TO_BMP("bmp", "Images", true),
        IMAGE_TO_TIFF("tiff", "Images"),
        IMAGE_TO_ICO("ico", "Images", true),
        IMAGE_TO_HEIF("heif", "Images", true),
        IMAGE_TO_AVIF("avif", "Images", true),
        IMAGE_TO_TGA("tga", "Images"),
        IMAGE_TO_PPM("ppm", "Images"),
        IMAGE_TO_PGM("pgm", "Images"),
        IMAGE_TO_PCX("pcx", "Images"),
        IMAGE_TO_PSD("psd", "Images"),
        
        // PDF / Documents
        IMAGE_TO_PDF("pdf", "Documents", true),
        VIDEO_TO_PDF("pdf", "Documents"),
        PDF_TO_PNG("png", "Images", true),
        PDF_TO_JPG("jpg", "Images", true)
    }

    sealed class ConversionStatus {
        data class Progress(val percentage: Int) : ConversionStatus()
        data class Success(val outputPath: String) : ConversionStatus()
        data class Error(val message: String) : ConversionStatus()
    }

    fun convertFile(
        inputUri: Uri,
        type: ConversionType,
        highQuality: Boolean = true
    ): Flow<ConversionStatus> = callbackFlow {
        val inputPath = getFilePathFromUri(inputUri) ?: run {
            trySend(ConversionStatus.Error("Invalid input file"))
            close()
            return@callbackFlow
        }

        val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val toolzMainDir = File(baseDir, "Toolz")
        val categoryDir = File(toolzMainDir, type.category).apply { mkdirs() }
        
        val outputFile = File(categoryDir, "TOOLZ_${System.currentTimeMillis()}.${type.extension}")
        val outputPath = outputFile.absolutePath

        // Native PDF to Image conversion to avoid FFmpeg libswresample/ghostscript issues
        if (type == ConversionType.PDF_TO_PNG || type == ConversionType.PDF_TO_JPG) {
            try {
                val file = File(inputPath)
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val pageCount = renderer.pageCount
                
                val baseName = outputFile.nameWithoutExtension
                
                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val scale = if (highQuality) 3f else 1.5f
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val pageFile = if (pageCount == 1) {
                        outputFile
                    } else {
                        File(categoryDir, "${baseName}_page_${i + 1}.${type.extension}")
                    }
                    
                    val format = if (type.extension == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val quality = if (highQuality) 100 else 80
                    
                    pageFile.outputStream().use { out ->
                        bitmap.compress(format, quality, out)
                    }
                    
                    page.close()
                    bitmap.recycle()
                    
                    trySend(ConversionStatus.Progress(((i + 1).toFloat() / pageCount * 100).toInt()))
                }
                
                renderer.close()
                fd.close()
                trySend(ConversionStatus.Success(outputPath))
                close()
            } catch (e: Exception) {
                trySend(ConversionStatus.Error("PDF conversion failed: ${e.localizedMessage}"))
                close()
            }
            return@callbackFlow
        }

        var totalDurationMs = 0L
        if (type.category != "Images" && type.extension != "pdf") {
            totalDurationMs = getVideoDuration(inputPath)
        }

        val command = when (type) {
            ConversionType.VIDEO_TO_GIF -> {
                val filter = if (highQuality) {
                    "fps=15,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse"
                } else {
                    "fps=10,scale=320:-1:flags=lanczos"
                }
                "-i \"$inputPath\" -vf \"$filter\" -y \"$outputPath\""
            }
            ConversionType.VIDEO_TO_WEBP -> {
                val quality = if (highQuality) "75" else "50"
                "-i \"$inputPath\" -vcodec libwebp -lossless 0 -compression_level 6 -q:v $quality -loop 0 -an -y \"$outputPath\""
            }
            ConversionType.VIDEO_TO_MP3, ConversionType.VIDEO_TO_WAV, 
            ConversionType.VIDEO_TO_AAC, ConversionType.VIDEO_TO_FLAC,
            ConversionType.VIDEO_TO_M4A, ConversionType.VIDEO_TO_OGG,
            ConversionType.VIDEO_TO_AIFF, ConversionType.VIDEO_TO_OPUS -> {
                val bitrate = if (highQuality) "320k" else "128k"
                "-i \"$inputPath\" -vn -ab $bitrate -ar 44100 -y \"$outputPath\""
            }
            ConversionType.IMAGE_TO_WEBP -> {
                val quality = if (highQuality) "85" else "60"
                "-i \"$inputPath\" -vcodec libwebp -lossless 0 -q:v $quality -y \"$outputPath\""
            }
            ConversionType.IMAGE_TO_JPG -> {
                val quality = if (highQuality) "2" else "5"
                "-i \"$inputPath\" -q:v $quality -y \"$outputPath\""
            }
            ConversionType.IMAGE_TO_PDF -> {
                "-i \"$inputPath\" \"$outputPath\""
            }
            ConversionType.VIDEO_TO_PDF -> {
                "-i \"$inputPath\" -frames:v 1 \"$outputPath\""
            }
            ConversionType.AUDIO_TO_MP3, ConversionType.AUDIO_TO_WAV, 
            ConversionType.AUDIO_TO_AAC, ConversionType.AUDIO_TO_OGG, 
            ConversionType.AUDIO_TO_FLAC, ConversionType.AUDIO_TO_M4A,
            ConversionType.AUDIO_TO_OPUS, ConversionType.AUDIO_TO_AMR,
            ConversionType.AUDIO_TO_WMA, ConversionType.AUDIO_TO_AIFF,
            ConversionType.AUDIO_TO_MKA, ConversionType.AUDIO_TO_AC3,
            ConversionType.AUDIO_TO_MP2, ConversionType.AUDIO_TO_AU,
            ConversionType.AUDIO_TO_CAF, ConversionType.AUDIO_TO_VOC -> {
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
                } else if (type.category == "Images") {
                    "-i \"$inputPath\" -y \"$outputPath\""
                } else {
                    "-i \"$inputPath\" -y \"$outputPath\""
                }
            }
        }

        val session = FFmpegKit.executeAsync(command, { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                trySend(ConversionStatus.Success(outputPath))
            } else if (ReturnCode.isCancel(returnCode)) {
                trySend(ConversionStatus.Error("Conversion cancelled"))
            } else {
                val logs = session.logs
                val errorLogs = logs.filter { it.level.name == "ERROR" || it.level.name == "FATAL" }
                val lastError = errorLogs.lastOrNull()?.message 
                    ?: logs.lastOrNull { !it.message.contains("libswresample") && !it.message.contains("ffmpeg version") }?.message
                    ?: session.failStackTrace 
                    ?: "Unknown FFmpeg error"
                trySend(ConversionStatus.Error("Conversion failed: $lastError"))
            }
            close()
        }, { _ ->
        }) { statistics: Statistics ->
            if (totalDurationMs > 0) {
                val progress = (statistics.time.toDouble() / totalDurationMs.toDouble() * 100).toInt()
                trySend(ConversionStatus.Progress(progress.coerceIn(0, 100)))
            } else {
                trySend(ConversionStatus.Progress(-1))
            }
        }

        awaitClose {
            FFmpegKit.cancel(session.sessionId)
            if (inputPath.contains("input_temp_")) {
                File(inputPath).delete()
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
