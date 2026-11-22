/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional "save Daily Ups to my device" archive.
 *
 * The posted photo is re-encoded into the user's own gallery with a small
 * Whisper badge burned in. This is deliberately a device-local convenience:
 * nothing about it touches the story plane, and a failure here must never turn
 * a successful publish into an error.
 *
 * MediaStore insert on API 29+ needs no storage permission; minSdk is 31.
 */
internal suspend fun saveDailyUpToGallery(
    context: Context,
    sourceBytes: ByteArray,
    mimeType: String,
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val decoded = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: error("photo decode failed")
        val oriented = applyWhisperImageExifOrientation(sourceBytes, decoded)
        val watermark = addWhisperWatermark(oriented)
        if (watermark !== oriented) oriented.recycle()

        val resolver = context.contentResolver
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Whisper_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}${File.separator}Whisper",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("gallery insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                check(watermark.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    "gallery write failed"
                }
            } ?: error("gallery stream unavailable")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        } finally {
            watermark.recycle()
        }
    }
}

/**
 * Bottom-left "Whisper" badge. Sized relative to the photo so a 720px preview
 * and a 1920px posted image look the same, with a translucent scrim for
 * legibility on bright photos.
 */
private fun addWhisperWatermark(source: Bitmap): Bitmap {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
        ?: return source
    val width = out.width.toFloat()
    val height = out.height.toFloat()
    val pad = width * 0.028f
    val badgeHeight = (height * 0.052f).coerceAtLeast(34f)
    val badgeWidth = badgeHeight * 4.6f
    val left = pad
    val top = height - pad - badgeHeight

    val canvas = Canvas(out)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3000000.toInt()
    }
    val radius = badgeHeight * 0.34f
    canvas.drawRoundRect(RectF(left, top, left + badgeWidth, top + badgeHeight), radius, radius, bg)

    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = badgeHeight * 0.46f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val baseline = top + badgeHeight / 2f - (text.descent() + text.ascent()) / 2f
    canvas.drawText("Whisper", left + badgeWidth / 2f, baseline, text)
    return out
}
