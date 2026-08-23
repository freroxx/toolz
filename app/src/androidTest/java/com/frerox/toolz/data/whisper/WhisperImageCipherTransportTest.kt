package com.frerox.toolz.data.whisper

import android.graphics.Bitmap
import android.graphics.ColorSpace
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Random
import java.util.zip.CRC32

@RunWith(AndroidJUnit4::class)
class WhisperImageCipherTransportTest {

    @Test
    fun testEncodeDecodeIsPixelPerfect() {
        val random = Random(42)
        val testSizes = listOf(16, 1024, 100_000, 1_000_000)
        
        for (size in testSizes) {
            val originalBytes = ByteArray(size)
            random.nextBytes(originalBytes)
            
            val encodedPng = WhisperImageCipherTransport.encode(originalBytes)
            assertNotNull("Encoded PNG should not be null", encodedPng)
            
            val decodedBytes = WhisperImageCipherTransport.decode(encodedPng)
            assertNotNull("Decoded bytes should not be null for size $size", decodedBytes)
            assertArrayEquals("Decoded bytes must match original for size $size", originalBytes, decodedBytes)
        }
    }

    @Test
    fun testSmallPayload() {
        val originalBytes = "Hello Whisper!".toByteArray(Charsets.UTF_8)
        val encodedPng = WhisperImageCipherTransport.encode(originalBytes)
        val decodedBytes = WhisperImageCipherTransport.decode(encodedPng)
        assertArrayEquals(originalBytes, decodedBytes)
    }

    // ── V2-FIX H-?: v2 container (magic + version + CRC32) validation tests ──

    /** Builds a raw extracted-stream payload exactly like the encoder embeds it. */
    private fun buildV2Stream(magic: ByteArray, payload: ByteArray, corruptCrc: Boolean = false): ByteArray {
        val crc = CRC32().apply { update(payload) }
        val crcValue = if (corruptCrc) crc.value.toInt() xor 0x55AA55AA else crc.value.toInt()
        val buffer = ByteBuffer.allocate(magic.size + 1 + 4 + 4 + payload.size)
        buffer.put(magic)
        buffer.put(1) // version
        buffer.putInt(crcValue)
        buffer.putInt(payload.size)
        buffer.put(payload)
        return buffer.array()
    }

    @Test
    fun testCorruptedMagicIsRejected() {
        val random = Random(7)
        val payload = ByteArray(512).also { random.nextBytes(it) }
        // Valid v2 stream still parses...
        assertArrayEquals(
            payload,
            WhisperImageCipherTransport.extractCipher(buildV2Stream("WZ1".toByteArray(Charsets.US_ASCII), payload)),
        )
        // ...but a single wrong magic byte must be rejected outright.
        assertNull(
            "Corrupted magic must be rejected",
            WhisperImageCipherTransport.extractCipher(buildV2Stream("WZ0".toByteArray(Charsets.US_ASCII), payload)),
        )
    }

    @Test
    fun testCorruptedCrcIsRejected() {
        val random = Random(8)
        val payload = ByteArray(777).also { random.nextBytes(it) }
        assertNull(
            "CRC mismatch must be rejected",
            WhisperImageCipherTransport.extractCipher(buildV2Stream("WZ1".toByteArray(Charsets.US_ASCII), payload, corruptCrc = true)),
        )
    }

    @Test
    fun testLegacyLengthOnlyHeaderStillDecodes() {
        val random = Random(9)
        val payload = ByteArray(300).also { random.nextBytes(it) }
        // Pre-v2 layout: bare [Int length] header, no magic/CRC.
        val legacy = ByteBuffer.allocate(4 + payload.size)
        legacy.putInt(payload.size)
        legacy.put(payload)
        assertArrayEquals("Legacy stream must decode via fallback", payload, WhisperImageCipherTransport.extractCipher(legacy.array()))
        // And end-to-end through a real PNG rasterized the pre-v2 way.
        assertArrayEquals("Legacy PNG must still decode", payload, decodePngOfLegacyStream(legacy.array()))
    }

    /** Mirror of the pre-v2 encoder: 3 data bytes per RGBA pixel, alpha forced opaque. */
    private fun decodePngOfLegacyStream(data: ByteArray): ByteArray? {
        val pixelCount = (data.size + 2) / 3
        val width = kotlin.math.ceil(kotlin.math.sqrt(pixelCount.toDouble())).toInt().coerceAtLeast(1)
        val height = ((pixelCount + width - 1) / width).coerceAtLeast(1)
        val raw = ByteArray(width * height * 4)
        var srcIdx = 0
        var dstIdx = 0
        while (dstIdx < raw.size) {
            raw[dstIdx] = if (srcIdx < data.size) data[srcIdx++] else 0
            raw[dstIdx + 1] = if (srcIdx < data.size) data[srcIdx++] else 0
            raw[dstIdx + 2] = if (srcIdx < data.size) data[srcIdx++] else 0
            raw[dstIdx + 3] = 0xFF.toByte()
            dstIdx += 4
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, false, ColorSpace.get(ColorSpace.Named.SRGB))
        return try {
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(raw))
            val png = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
            WhisperImageCipherTransport.decode(png)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun testDecodeRespectsTightenedPixelBudget() {
        val random = Random(10)
        val payload = ByteArray(64).also { random.nextBytes(it) }
        val encodedPng = WhisperImageCipherTransport.encode(payload)
        // A budget below the image's actual pixel count must reject it instead of decoding.
        assertNull(
            "Decode must honor a tightened maxPixels budget",
            WhisperImageCipherTransport.decode(encodedPng, maxPixels = 1),
        )
    }
}
