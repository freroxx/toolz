/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Custom photo icon: picks an image from the device, center-crops to a
 * square, downscales to 512px (EXIF orientation honored, location stripped
 * by re-encoding), and stores it as PNG in app files. Used by ToolzMark,
 * widgets and the pinned Home-screen shortcut. The manifest launcher tile
 * cannot use it (activity-alias entries are fixed at build time), so
 * LauncherIconManager keeps the nearest built-in alias enabled instead.
 */

package com.frerox.toolz.data.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class CustomIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val FILE_NAME = "custom_icon.png"
        const val SIZE_PX = 512

        fun fileFor(context: Context): File =
            File(File(context.filesDir, "custom_icons"), FILE_NAME)

        fun exists(context: Context): Boolean {
            val f = fileFor(context)
            return try { f.exists() && f.length() > 0 } catch (_: Exception) { false }
        }
    }

    fun file(): File = fileFor(context)

    fun exists(): Boolean = exists(context)

    fun loadBitmap(): Bitmap? {
        val f = file()
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Exception) { null }
    }

    /**
     * Copies [uri] into the cropped 512px PNG. Returns the absolute path on
     * success, null on failure. Safe to call from the UI thread (IO inside).
     */
    suspend fun saveFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val sampled = decodeSampled(uri, 1024) ?: return@withContext null
            val oriented = applyExifOrientation(uri, sampled)
            val side = min(oriented.width, oriented.height)
            val cropped = Bitmap.createBitmap(
                oriented,
                (oriented.width - side) / 2,
                (oriented.height - side) / 2,
                side,
                side,
            )
            val scaled = Bitmap.createScaledBitmap(cropped, SIZE_PX, SIZE_PX, true)
            if (cropped !== oriented) {
                try { oriented.recycle() } catch (_: Exception) { }
            }
            if (sampled !== oriented && sampled !== cropped) {
                try { sampled.recycle() } catch (_: Exception) { }
            }
            val dir = File(context.filesDir, "custom_icons")
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, FILE_NAME)).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            try { scaled.recycle() } catch (_: Exception) { }
            try { cropped.recycle() } catch (_: Exception) { }
            File(dir, FILE_NAME).absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        try { file().delete() } catch (_: Exception) { }
    }

    private fun decodeSampled(uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) { null }
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) { bitmap }
    }
}
