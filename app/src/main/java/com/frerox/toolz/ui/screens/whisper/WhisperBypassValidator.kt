package com.frerox.toolz.ui.screens.whisper

import java.security.MessageDigest

private const val BYPASS_HASH = "fcfa7be6a471d30b5162f1bf31b6eb7cbeb6b90b126669f68c643cbee4f41150"

fun isWhisperBypassPassword(input: String): Boolean {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        hash == BYPASS_HASH
    } catch (_: Exception) {
        false
    }
}
