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
        val oriented = applyWhisperImageExifOrientation(source, working)
        if (oriented !== working) {
            working.recycle()
            working = oriented
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

/**
 * Orients [bitmap] per [ExifInterface.TAG_ORIENTATION] in [source]. Returns
 * [bitmap] UNCHANGED when nothing applies — callers receiving a DIFFERENT
 * instance own recycling the original.
 *
 * Shared by the Daily Up path (publish + compose preview) and the group
 * upload preprocess: one EXIF implementation, pixel-verified.
 *
 * Every transform is an explicitly normalized affine whose four mapped corners
 * stay inside the (possibly swapped) output bounds:
 *  2 flip H    (x,y) → (W−x, y)
 *  3 rot 180   (x,y) → (W−x, H−y)
 *  4 flip V    (x,y) → (x, H−y)
 *  5 transpose (x,y) → (y, x)        output H×W  (was MISSING — mirrored cameras)
 *  6 rot 90 CW (x,y) → (H−y, x)      output H×W
 *  7 transverse(x,y) → (H−y, W−x)    output H×W  (was MISSING)
 *  8 rot90 CCW (x,y) → (y, W−x)      output H×W
 */
fun applyWhisperImageExifOrientation(source: ByteArray, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val matrix = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> exifAffine(-1f, 0f, w, 0f, 1f, 0f)
        ExifInterface.ORIENTATION_ROTATE_180 -> exifAffine(-1f, 0f, w, 0f, -1f, h)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> exifAffine(1f, 0f, 0f, 0f, -1f, h)
        ExifInterface.ORIENTATION_TRANSPOSE -> exifAffine(0f, 1f, 0f, 1f, 0f, 0f)
        ExifInterface.ORIENTATION_ROTATE_90 -> exifAffine(0f, -1f, h, 1f, 0f, 0f)
        ExifInterface.ORIENTATION_TRANSVERSE -> exifAffine(0f, -1f, h, -1f, 0f, w)
        ExifInterface.ORIENTATION_ROTATE_270 -> exifAffine(0f, 1f, 0f, -1f, 0f, w)
        else -> return bitmap
    }
    // Never fail the pipeline over a Bitmap allocation: wrong orientation on
    // screen beats publishing nothing.
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}

/** Row-major affine matrix setter: (x,y) → (a·x + b·y + c, d·x + e·y + f). */
private fun exifAffine(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): Matrix = Matrix().apply {
    setValues(floatArrayOf(a, b, c, d, e, f, 0f, 0f, 1f))
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
