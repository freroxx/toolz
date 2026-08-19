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
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import javax.inject.Inject
import javax.inject.Singleton

/** Calls the authenticated Edge Function with automatic Supabase Storage fallback. */
@Singleton
class WhisperEncryptedImageHost @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun upload(cipherBytes: ByteArray, name: String, expirationSeconds: Long?): Result<String> = runCatching {
        require(cipherBytes.isNotEmpty() && cipherBytes.size <= MAX_CIPHER_BYTES) { "Image is too large to send securely." }
        val token = supabase.auth.currentSessionOrNull()?.accessToken ?: error("Sign in before uploading an image.")
        val myUserId = supabase.auth.currentUserOrNull()?.id ?: "anonymous"
        
        // 1. Try uploading to Edge Function (ImgBB) first
        val edgeFunctionResult = runCatching {
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
                        val errorMsg = runCatching {
                            Json.parseToJsonElement(response).jsonObject["error"]?.jsonPrimitive?.content
                        }.getOrNull() ?: response.take(200).ifBlank { "HTTP ${connection.responseCode}" }
                        error(errorMsg)
                    }
                    
                    Json.parseToJsonElement(response).jsonObject["url"]?.jsonPrimitive?.content
                        ?: error("Image host returned an invalid response.")
                } finally {
                    connection.disconnect()
                }
            }
        }

        if (edgeFunctionResult.isSuccess) {
            return@runCatching edgeFunctionResult.getOrThrow()
        }

        // 2. Direct Supabase Storage fallback if Edge Function is unconfigured (503 / missing ImgBB key)
        val edgeError = edgeFunctionResult.exceptionOrNull()?.message.orEmpty()
        val storageResult = runCatching {
            val path = "$myUserId/attachments/${java.util.UUID.randomUUID()}.png"
            supabase.storage.from("whisper-avatars").upload(path, cipherBytes) { upsert = true }
            supabase.storage.from("whisper-avatars").publicUrl(path)
        }

        if (storageResult.isSuccess) {
            return@runCatching storageResult.getOrThrow()
        }

        error("Image upload failed: $edgeError")
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
