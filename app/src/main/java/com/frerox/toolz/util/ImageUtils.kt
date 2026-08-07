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
package com.frerox.toolz.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import java.io.FileDescriptor

object ImageUtils {

    /**
     * Loads a bitmap from a URI at max available quality, respecting EXIF
     * rotation and capping resolution to prevent OOM.
     */
    fun loadOptimizedBitmap(context: Context, uri: Uri, maxDimension: Int = 4096): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val fd: FileDescriptor = pfd?.fileDescriptor ?: return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            // 1. Get dimensions
            BitmapFactory.decodeFileDescriptor(fd, null, options)

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            // 2. Calculate sample size
            options.inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // 3. Decode scaled bitmap
            var bitmap = BitmapFactory.decodeFileDescriptor(fd, null, options) ?: return null

            // 4. Handle EXIF rotation
            val rotation = getRotationDegrees(fd)
            if (rotation != 0) {
                bitmap = rotateBitmap(bitmap, rotation)
            }

            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val larger = maxOf(width, height)
        if (larger > maxDim) {
            while (larger / (inSampleSize * 2) >= maxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun getRotationDegrees(fd: FileDescriptor): Int {
        return try {
            val exif = ExifInterface(fd)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
