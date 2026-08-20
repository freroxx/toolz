package com.frerox.toolz.data.whisper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Encodes arbitrary ciphertext into a lossless PNG pixel stream for image-only hosts.
 * Sets Alpha=255 for all pixels to completely prevent Skia color pre-multiplication corruption.
 * Forces sRGB color space to prevent Android color management from altering pixel values.
 */
object WhisperImageCipherTransport {
    private const val HEADER_BYTES = 4
    const val MAX_CIPHER_BYTES = 22 * 1024 * 1024

    fun encode(cipherBytes: ByteArray): ByteArray {
        require(cipherBytes.isNotEmpty() && cipherBytes.size <= MAX_CIPHER_BYTES) { "Encrypted image is too large." }
        val dataWithHeader = ByteArray(HEADER_BYTES + cipherBytes.size)
        ByteBuffer.wrap(dataWithHeader).putInt(cipherBytes.size).put(cipherBytes)
        
        // 3 payload bytes per pixel (R, G, B), with Alpha fixed at 255 to prevent Skia pre-multiplication corruption
        val pixelCount = (dataWithHeader.size + 2) / 3
        val width = kotlin.math.ceil(kotlin.math.sqrt(pixelCount.toDouble())).toInt().coerceAtLeast(1)
        val height = ((pixelCount + width - 1) / width).coerceAtLeast(1)
        
        val raw = ByteArray(width * height * 4)
        var srcIdx = 0
        var dstIdx = 0
        while (dstIdx < raw.size) {
            raw[dstIdx] = if (srcIdx < dataWithHeader.size) dataWithHeader[srcIdx++] else 0
            raw[dstIdx + 1] = if (srcIdx < dataWithHeader.size) dataWithHeader[srcIdx++] else 0
            raw[dstIdx + 2] = if (srcIdx < dataWithHeader.size) dataWithHeader[srcIdx++] else 0
            raw[dstIdx + 3] = 0xFF.toByte() // Opaque Alpha: Prevents color pre-multiplication corruption
            dstIdx += 4
        }

        // Force sRGB to prevent any color space transformations during compression
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, false, ColorSpace.get(ColorSpace.Named.SRGB))
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
        // Fast-check for PNG header signature
        if (pngBytes.size < 8 || pngBytes[0] != 0x89.toByte() || pngBytes[1] != 0x50.toByte() || pngBytes[2] != 0x4E.toByte() || pngBytes[3] != 0x47.toByte()) {
            return null
        }
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inPremultiplied = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            // Ensure we decode as sRGB to match the encoding
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size, options) ?: return null
        try {
            val raw = ByteArray(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(ByteBuffer.wrap(raw))
            
            // Extract 3 data bytes per pixel (skipping every 4th alpha byte)
            val extracted = ByteArray(bitmap.width * bitmap.height * 3)
            var srcIdx = 0
            var dstIdx = 0
            while (srcIdx < raw.size && dstIdx < extracted.size) {
                extracted[dstIdx++] = raw[srcIdx++]
                extracted[dstIdx++] = raw[srcIdx++]
                extracted[dstIdx++] = raw[srcIdx++]
                srcIdx++ // Skip alpha byte
            }

            val buffer = ByteBuffer.wrap(extracted)
            if (buffer.remaining() < HEADER_BYTES) return null
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

