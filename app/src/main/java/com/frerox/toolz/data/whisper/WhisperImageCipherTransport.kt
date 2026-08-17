package com.frerox.toolz.data.whisper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Encodes arbitrary ciphertext into a lossless PNG pixel stream for image-only hosts.
 * The host sees a valid opaque PNG; only Whisper can recover the original AEAD ciphertext.
 */
object WhisperImageCipherTransport {
    private const val HEADER_BYTES = 4
    const val MAX_CIPHER_BYTES = 22 * 1024 * 1024

    fun encode(cipherBytes: ByteArray): ByteArray {
        require(cipherBytes.isNotEmpty() && cipherBytes.size <= MAX_CIPHER_BYTES) { "Encrypted image is too large." }
        val dataBytes = HEADER_BYTES + cipherBytes.size
        val pixelCount = (dataBytes + 3) / 4
        val width = kotlin.math.ceil(kotlin.math.sqrt(pixelCount.toDouble())).toInt().coerceAtLeast(1)
        val height = ((pixelCount + width - 1) / width).coerceAtLeast(1)
        val raw = ByteArray(width * height * 4)
        ByteBuffer.wrap(raw).putInt(cipherBytes.size).put(cipherBytes)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(raw))
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not prepare encrypted image." }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun decode(pngBytes: ByteArray): ByteArray? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size) ?: return null
        try {
            if (bitmap.config != Bitmap.Config.ARGB_8888) return null
            val raw = ByteArray(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(raw))
            val buffer = ByteBuffer.wrap(raw)
            val size = buffer.int
            if (size !in 1..MAX_CIPHER_BYTES || size > buffer.remaining()) return null
            val cipherBytes = ByteArray(size)
            buffer.get(cipherBytes)
            cipherBytes
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()
}
