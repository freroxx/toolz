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
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            // 1. Get dimensions via InputStream (independent descriptor so FD offset is not consumed)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            // 2. Calculate sample size
            options.inSampleSize = computeInSampleSize(originalWidth, originalHeight, maxDimension)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            // 3. Decode scaled bitmap via fresh FD (separate from bounds pass)
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val fd2 = pfd?.fileDescriptor ?: return null
            var bitmap = BitmapFactory.decodeFileDescriptor(fd2, null, options) ?: return null

            // 4. Handle EXIF rotation via fresh InputStream (avoid reusing consumed FD)
            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                try {
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) {
                    0
                }
            } ?: 0
            if (rotation != 0) {
                bitmap = rotateBitmap(bitmap, rotation)
            }

            bitmap
        } catch (e: Throwable) {
            null
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        while (maxOf(width, height) / inSampleSize > maxDim) {
            inSampleSize *= 2
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

    private fun getRotationDegreesFromBytes(imageBytes: ByteArray): Int {
        return try {
            val exif = ExifInterface(java.io.ByteArrayInputStream(imageBytes))
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

    /**
     * Downscales an image to fit within maxDimension and compresses it.
     * Automatically fixes EXIF rotation from camera captures.
     */
    fun downscaleAndCompress(imageBytes: ByteArray, maxDimension: Int = 1024, quality: Int = 80): ByteArray {
        // Two-pass sampling: read the bounds first, then decode with an inSampleSize so the
        // full-resolution bitmap is never materialized. A raw decode of a large capture can
        // otherwise allocate several hundred MB of heap before downscaling begins.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // Undecodable input: return an empty payload instead of the original bytes so
            // callers' downstream size caps (5 MB / 10 MB) reject it gracefully rather than
            // letting a malformed image slip through uncompressed.
            return ByteArray(0)
        }

        val options = BitmapFactory.Options().apply {
            inMutable = true
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return ByteArray(0)
        
        // Correct EXIF rotation (e.g. camera orientation)
        val rotation = getRotationDegreesFromBytes(imageBytes)
        if (rotation != 0) {
            bitmap = rotateBitmap(bitmap, rotation)
        }

        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val result = outputStream.toByteArray()
            bitmap.recycle()
            return result
        }
        
        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / aspectRatio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * aspectRatio).toInt()
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        if (scaledBitmap != bitmap) bitmap.recycle()
        val result = outputStream.toByteArray()
        scaledBitmap.recycle()
        return result
    }
}
