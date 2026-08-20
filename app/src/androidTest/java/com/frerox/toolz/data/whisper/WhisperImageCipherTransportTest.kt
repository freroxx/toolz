package com.frerox.toolz.data.whisper

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

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
}
