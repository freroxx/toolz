/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.frerox.toolz.ui.screens.whisper.decodeBoundedBitmap
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared image preprocess for uploads (chat images use the original in
 * WhisperChatViewModel; group pictures use this copy — deliberately
 * duplicated, not extracted, so the 1:1 path is byte-untouched).
 *
 * EXIF orientation applied before scaling, 1920px bound, JPEG q82 (PNG kept
 * for alpha-capable sources), GPS/EXIF dropped by the re-encode.
 */
private val GROUP_ALPHA_CAPABLE_MIMES = setOf("image/png", "image/webp")

suspend fun compressImageForGroupUpload(bytes: ByteArray, mimeType: String): Pair<ByteArray, String> =
    withContext(Dispatchers.Default) {
        runCatching {
            var bitmap = decodeBoundedBitmap(bytes, 1920, 1920) ?: return@withContext bytes to mimeType
            try {
                val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
                val orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
                val matrix = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                    else -> {}
                }
                if (!matrix.isIdentity) {
                    val rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated != bitmap) { bitmap.recycle(); bitmap = rotated }
                }
            } catch (_: Exception) {}
            val maxDimension = 1920
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
                val b = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true)
                if (b != bitmap) bitmap.recycle()
                b
            } else {
                bitmap
            }
            // Square crop for group pictures (avatar-style).
            val side = min(scaledBitmap.width, scaledBitmap.height)
            val cropped = Bitmap.createBitmap(
                scaledBitmap,
                (scaledBitmap.width - side) / 2,
                (scaledBitmap.height - side) / 2,
                side, side,
            )
            if (cropped != scaledBitmap) scaledBitmap.recycle()
            val keepAlpha = mimeType in GROUP_ALPHA_CAPABLE_MIMES && cropped.hasAlpha()
            val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val outMime = if (keepAlpha) "image/png" else "image/jpeg"
            val out = ByteArrayOutputStream()
            cropped.compress(format, 82, out)
            cropped.recycle()
            val result = out.toByteArray()
            if (result.isNotEmpty() && result.size < bytes.size) result to outMime else bytes to mimeType
        }.getOrDefault(bytes to mimeType)
    }

/** Fresh random key + AES-256-GCM seal. Returns (sealedBytes, keyBytes). */
fun newGroupPictureSeal(plain: ByteArray): Pair<ByteArray, ByteArray> {
    val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
    return sealGroupPictureBytes(plain, key).first to key
}

fun sealGroupPictureBytes(plain: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
    val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
    }
    return (nonce + cipher.doFinal(plain)) to key
}

fun openGroupPictureBytes(sealed: ByteArray, key: ByteArray): ByteArray? = runCatching {
    require(sealed.size > 12 && key.size == 32) { "bad picture seal" }
    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
    }
    cipher.doFinal(sealed.copyOfRange(12, sealed.size))
}.getOrNull()
