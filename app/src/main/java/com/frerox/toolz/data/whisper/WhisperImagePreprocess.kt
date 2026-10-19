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
 *
 * FAIL-CLOSED: undecodable input is an ERROR — it is never shipped raw. The
 * old `re-encode kept only when smaller than the original` fallback sent the
 * ORIGINAL bytes upstream instead: EXIF/GPS still embedded, possibly past
 * transport size bounds, possibly in a format recipients cannot decode. The
 * re-encode is always the shipped artifact; for tiny already-compressed
 * sources being a few bytes larger is the price of sanitization.
 */
private val GROUP_ALPHA_CAPABLE_MIMES = setOf("image/png", "image/webp")

suspend fun compressImageForGroupUpload(bytes: ByteArray, mimeType: String): Pair<ByteArray, String> =
    withContext(Dispatchers.Default) {
        val decoded = decodeBoundedBitmap(bytes, 1920, 1920)
            ?: error("Could not read that image.")
        val oriented = applyWhisperImageExifOrientation(bytes, decoded)
        if (oriented !== decoded) decoded.recycle()
        val bitmap = oriented
        val maxDimension = 1920
        val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            val b = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true)
            if (b !== bitmap) bitmap.recycle()
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
        if (cropped !== scaledBitmap) scaledBitmap.recycle()
        val keepAlpha = mimeType in GROUP_ALPHA_CAPABLE_MIMES && cropped.hasAlpha()
        val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outMime = if (keepAlpha) "image/png" else "image/jpeg"
        val out = ByteArrayOutputStream()
        cropped.compress(format, 82, out)
        cropped.recycle()
        val result = out.toByteArray()
        if (result.isEmpty()) error("Could not process this image.")
        result to outMime
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

/** Cap for gallery picks bound for group sends (OOM guard at the read site). */
const val GROUP_MAX_PICK_BYTES = 12 * 1024 * 1024

/** Bounded stream read for group image picks; throws past the cap. */
fun readBoundedGroupImageBytes(input: java.io.InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= GROUP_MAX_PICK_BYTES) { "That image is too large to send." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

/**
 * In-chat image preprocess: same EXIF-strip + 1920 + JPEG82 pipeline as the
 * group picture path but WITHOUT the square avatar crop (chat photos keep
 * their aspect ratio). FAIL-CLOSED like [compressImageForGroupUpload]:
 * undecodable/unsanitizable input is an error, never a raw passthrough.
 */
suspend fun compressGroupChatImage(bytes: ByteArray, mimeType: String): Pair<ByteArray, String> =
    withContext(Dispatchers.Default) {
        val decoded = decodeBoundedBitmap(bytes, 1920, 1920)
            ?: error("Could not read that image.")
        val oriented = applyWhisperImageExifOrientation(bytes, decoded)
        if (oriented !== decoded) decoded.recycle()
        val bitmap = oriented
        val maxDimension = 1920
        val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            val b = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1), (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true)
            if (b !== bitmap) bitmap.recycle()
            b
        } else {
            bitmap
        }
        val keepAlpha = mimeType in GROUP_ALPHA_CAPABLE_MIMES && scaledBitmap.hasAlpha()
        val format = if (keepAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outMime = if (keepAlpha) "image/png" else "image/jpeg"
        val out = ByteArrayOutputStream()
        scaledBitmap.compress(format, 82, out)
        scaledBitmap.recycle()
        val result = out.toByteArray()
        if (result.isEmpty()) error("Could not process this image.")
        result to outMime
    }
