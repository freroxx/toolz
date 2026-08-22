package com.frerox.toolz.ui.screens.whisper

import kotlinx.coroutines.delay
import java.security.MessageDigest

private const val BYPASS_HASH = "fcfa7be6a471d30b5162f1bf31b6eb7cbeb6b90b126669f68c643cbee4f41150"

suspend fun isWhisperBypassPassword(input: String): Boolean {
    return try {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(trimmed.toByteArray(Charsets.UTF_8))
        val expected = BYPASS_HASH.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val match = MessageDigest.isEqual(hashBytes, expected)
        if (!match) {
            // Artificial delay to mitigate rapid automated brute-force attempts without blocking the main thread
            delay(300)
        }
        match
    } catch (_: Exception) {
        false
    }
}
