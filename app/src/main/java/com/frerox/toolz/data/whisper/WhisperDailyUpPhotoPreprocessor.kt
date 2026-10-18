/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

private const val DAILY_UP_LONG_EDGE_PX = 1920
private const val DAILY_UP_JPEG_QUALITY = 82
private const val DAILY_UP_MAX_INPUT_BYTES = 12 * 1024 * 1024
private const val DAILY_UP_MAX_DECODE_PIXELS = 8_000_000
private const val DAILY_UP_LOW_RAM_MAX_DECODE_PIXELS = 4_000_000
private const val AES_GCM_TAG_BYTES = 16
private const val DAILY_UP_MAX_PLAIN_BYTES = WhisperImageCipherTransport.MAX_CIPHER_BYTES - AES_GCM_TAG_BYTES

private val DAILY_UP_SUPPORTED_MIMES = setOf("image/jpeg", "image/png", "image/webp")
private val DAILY_UP_ALPHA_MIMES = setOf("image/png", "image/webp")
private val DAILY_UP_EXIF_TAGS = listOf(
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP,
)

/** The only output accepted by the Daily Ups publishing pipeline. */
data class DailyUpPhoto(val bytes: ByteArray, val mimeType: String)

/** A rejected source has no uploadable bytes; callers must never fall back to input. */
enum class DailyUpPhotoRejection {
    UNSUPPORTED,
    INPUT_TOO_LARGE,
    DECODE_FAILED,
    OUTPUT_TOO_LARGE,
    METADATA_REMAINS,
}

sealed interface DailyUpPhotoPreparation {
    data class Ready(val photo: DailyUpPhoto) : DailyUpPhotoPreparation
    data class Rejected(val reason: DailyUpPhotoRejection) : DailyUpPhotoPreparation
}

/**
 * Strict, privacy-preserving photo preprocessing for Daily Ups.
 *
 * The source is bounded before allocation, oriented before scaling, re-encoded at
 * JPEG quality 82 (or lossless PNG when the decoded image needs alpha), and checked
 * for EXIF/GPS remnants. A failure is represented by [DailyUpPhotoPreparation.Rejected]
 * so the caller never uploads the source bytes.
 */
suspend fun preprocessDailyUpPhoto(
    context: Context,
    source: ByteArray,
    mimeType: String,
): DailyUpPhotoPreparation {
    if (mimeType !in DAILY_UP_SUPPORTED_MIMES) {
        return DailyUpPhotoPreparation.Rejected(DailyUpPhotoRejection.UNSUPPORTED)
    }
    if (source.isEmpty() || source.size > DAILY_UP_MAX_INPUT_BYTES) {
        return DailyUpPhotoPreparation.Rejected(DailyUpPhotoRejection.INPUT_TOO_LARGE)
    }

    val decodePixelLimit = context.dailyUpDecodePixelLimit()
    val decoded = decodeDailyUpBitmap(source, decodePixelLimit)
        ?: return DailyUpPhotoPreparation.Rejected(DailyUpPhotoRejection.DECODE_FAILED)
    var working = decoded

    return try {
        applyExifOrientation(source, working)?.also { oriented ->
            if (oriented !== working) {
                working.recycle()
                working = oriented
            }
        }

        scaleLongEdge(working, DAILY_UP_LONG_EDGE_PX).also { scaled ->
            if (scaled !== working) {
                working.recycle()
                working = scaled
            }
        }

        val outputMime = if (mimeType in DAILY_UP_ALPHA_MIMES && working.hasAlpha()) {
            "image/png"
        } else {
            "image/jpeg"
        }
        val output = ByteArrayOutputStream().use { stream ->
            val format = if (outputMime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            check(working.compress(format, DAILY_UP_JPEG_QUALITY, stream)) { "Bitmap re-encode failed." }
            stream.toByteArray()
        }

        when {
            output.isEmpty() || output.size > DAILY_UP_MAX_PLAIN_BYTES ->
                DailyUpPhotoPreparation.Rejected(DailyUpPhotoRejection.OUTPUT_TOO_LARGE)
            outputContainsExifOrGps(output) ->
                DailyUpPhotoPreparation.Rejected(DailyUpPhotoRejection.METADATA_REMAINS)
            else -> DailyUpPhotoPreparation.Ready(DailyUpPhoto(output, outputMime))
        }
    } catch (_: OutOfMemoryError) {
        DailyUpPhotoPreparation.Rejected(DailyUpPhotoRejection.DECODE_FAILED)
    } catch (_: Exception) {
        DailyUpPhotoPreparation.Rejected(DailyUpPhotoRejection.DECODE_FAILED)
    } finally {
        if (!working.isRecycled) working.recycle()
    }
}

private fun Context.dailyUpDecodePixelLimit(): Int = try {
    val manager = getSystemService(ActivityManager::class.java)
    if (manager?.isLowRamDevice == true) DAILY_UP_LOW_RAM_MAX_DECODE_PIXELS else DAILY_UP_MAX_DECODE_PIXELS
} catch (_: Exception) {
    DAILY_UP_MAX_DECODE_PIXELS
}

private fun decodeDailyUpBitmap(source: ByteArray, pixelLimit: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (sampledPixelCount(bounds.outWidth, bounds.outHeight, sampleSize) > pixelLimit) {
        if (sampleSize > Int.MAX_VALUE / 2) return null
        sampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(
        source,
        0,
        source.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: return null
    if (bitmap.width.toLong() * bitmap.height > pixelLimit) {
        bitmap.recycle()
        return null
    }
    return bitmap
}

private fun sampledPixelCount(width: Int, height: Int, sampleSize: Int): Long {
    val sampledWidth = (width.toLong() + sampleSize - 1) / sampleSize
    val sampledHeight = (height.toLong() + sampleSize - 1) / sampleSize
    return sampledWidth * sampledHeight
}

private fun applyExifOrientation(source: ByteArray, bitmap: Bitmap): Bitmap? {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    }
    return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun scaleLongEdge(bitmap: Bitmap, maxLongEdge: Int): Bitmap {
    val longEdge = maxOf(bitmap.width, bitmap.height)
    if (longEdge <= maxLongEdge) return bitmap
    val scale = maxLongEdge.toFloat() / longEdge
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}

private fun outputContainsExifOrGps(bytes: ByteArray): Boolean = runCatching {
    val exif = ExifInterface(ByteArrayInputStream(bytes))
    DAILY_UP_EXIF_TAGS.any { tag -> exif.getAttribute(tag) != null }
}.getOrDefault(true)
