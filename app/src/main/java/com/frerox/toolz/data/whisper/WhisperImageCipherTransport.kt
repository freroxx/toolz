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
    // PNG is RGBA (up to ~4x cipher bytes) and base64 adds 4/3. Keep the worst case under
    // ImgBB's 32 MB request ceiling instead of relying on PNG compression for random ciphertext.
    const val MAX_CIPHER_BYTES = 5 * 1024 * 1024
    // Decoded pixel ceiling for decode(): a hostile PNG can declare huge dimensions, and
    // each decoded pixel costs up to ~11 bytes at peak (4 for ARGB_8888 + 4 raw buffer +
    // 3 extracted), so a few KB of header can demand gigabytes of heap. The send pipeline
    // compresses real images to ~5 MP, so 8 MP leaves ample headroom for legit payloads.
    // P2-5: cap 4M on low-RAM devices.
    private const val MAX_PIXELS = 8_000_000
    private const val MAX_PIXELS_LOW_RAM = 4_000_000
    fun maxPixelsForDevice(context: android.content.Context): Int {
        return try {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            if (am?.isLowRamDevice == true) MAX_PIXELS_LOW_RAM else MAX_PIXELS
        } catch (_: Exception) { MAX_PIXELS }
    }

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

    fun decode(pngBytes: ByteArray): ByteArray? {
        // Peak allocation: bitmap (w*h*4) + raw buffer (w*h*4) + extracted (w*h*3) can transiently peak at ~11 bytes/pixel;
        // MAX_PIXELS bounds heap via bounds pre-pass, but OOM is still caught below.
        return try {
            // Fast-check for PNG header signature
            if (pngBytes.size < 8 || pngBytes[0] != 0x89.toByte() || pngBytes[1] != 0x50.toByte() || pngBytes[2] != 0x4E.toByte() || pngBytes[3] != 0x47.toByte()) {
                return null
            }
            // Bounds pre-pass: reject dimension bombs before any pixels are allocated. A PNG
            // header can claim arbitrary width/height; decoding then materializes
            // width*height*4 bytes (plus the extraction buffers), i.e. ~11 bytes/pixel at
            // peak. Legit images from the send pipeline are compressed to ~5 MP, so an 8 MP
            // ceiling cannot reject them. Long math avoids Int overflow that could otherwise
            // wrap the product negative and slip past the cap.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            if (bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) return null
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
        } catch (e: Throwable) {
            null
        }
    }
}
