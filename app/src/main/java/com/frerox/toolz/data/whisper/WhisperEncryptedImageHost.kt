package com.frerox.toolz.data.whisper

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encrypted image hosting via Supabase Storage.
 * Ciphertext is losslessly wrapped in a PNG container before upload.
 */
@Singleton
class WhisperEncryptedImageHost @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun upload(cipherBytes: ByteArray, name: String, expirationSeconds: Long?): Result<String> = runCatching {
        require(cipherBytes.isNotEmpty() && cipherBytes.size <= MAX_CIPHER_BYTES) { "Image is too large to send securely." }
        
        val fileName = "$name.png"
        val bucket = supabase.storage.from("whisper-images")
        
        withContext(Dispatchers.IO) {
            // Upload directly to Supabase Storage bucket.
            // Note: expirationSeconds is ignored here; disappearing images require a Postgres cron job
            // or trigger on the storage.objects table to handle deletion if Edge Functions are not used.
            bucket.upload(fileName, cipherBytes) {
                upsert = true
            }
            bucket.publicUrl(fileName)
        }
    }

    suspend fun download(url: String): Result<ByteArray> = runCatching {
        require(url.startsWith("https://")) { "Invalid image URL." }
        withContext(Dispatchers.IO) {
             val connection = (URL(url).openConnection() as HttpURLConnection)
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                if (connection.responseCode !in 200..299) error("Image is no longer available.")
                connection.inputStream.use { input ->
                    input.readBytes().also { require(it.size <= MAX_CIPHER_BYTES) { "Image is too large." } }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val MAX_CIPHER_BYTES = 23 * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
    }
}
