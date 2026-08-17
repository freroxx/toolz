package com.frerox.toolz.data.whisper

import android.util.Base64
import com.frerox.toolz.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Calls the authenticated Edge Function; the ImgBB secret never enters the APK. */
@Singleton
class WhisperEncryptedImageHost @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun upload(cipherBytes: ByteArray, name: String, expirationSeconds: Long?): Result<String> = runCatching {
        require(cipherBytes.isNotEmpty() && cipherBytes.size <= MAX_CIPHER_BYTES) { "Image is too large to send securely." }
        val token = supabase.auth.currentSessionOrNull()?.accessToken ?: error("Sign in before uploading an image.")
        
        val body = buildJsonObject {
            put("image", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
            put("name", name.take(80))
            expirationSeconds?.let { put("expiration", it) }
        }.toString()

        withContext(Dispatchers.IO) {
            val connection = (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/whisper-image-upload").openConnection() as HttpURLConnection)
            try {
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bodyBytes.size)
                
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                
                connection.outputStream.use { it.write(bodyBytes) }
                
                val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                
                if (connection.responseCode !in 200..299) {
                    error("Image upload failed (${connection.responseCode}): $response")
                }
                
                Json.parseToJsonElement(response).jsonObject["url"]?.jsonPrimitive?.content
                    ?: error("Image host returned an invalid response.")
            } finally {
                connection.disconnect()
            }
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
        // Base64 expands payloads by 4/3; stay below ImgBB's 32 MB input limit.
        const val MAX_CIPHER_BYTES = 23 * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
    }
}
