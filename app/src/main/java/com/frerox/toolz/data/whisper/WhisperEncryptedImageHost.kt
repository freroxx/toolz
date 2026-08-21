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
    /** Returns Pair(url, attachmentId) */
    suspend fun upload(cipherBytes: ByteArray, name: String, expirationSeconds: Long?): Result<Pair<String, String?>> = runCatching {
        require(cipherBytes.isNotEmpty() && cipherBytes.size <= MAX_CIPHER_BYTES) { "Image is too large to send securely." }
        val token = supabase.auth.currentSessionOrNull()?.accessToken ?: error("Sign in before uploading an image.")
        
        // 1. Try uploading to Edge Function (ImgBB) first
        val edgeFunctionResult = runCatching {
            // Peak allocation: encode() holds width*height*4 raw + PNG bytes, then Base64 adds ~4/3 expansion.
            // At MAX_CIPHER_BYTES this can transiently peak at >10 MB; callers must enforce size caps before this point.
            val pngBytes = WhisperImageCipherTransport.encode(cipherBytes)
            val body = buildJsonObject {
                put("image", Base64.encodeToString(pngBytes, Base64.NO_WRAP))
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
                    
                    val json = Json.parseToJsonElement(response).jsonObject
                    val url = json["url"]?.jsonPrimitive?.content ?: error("Image host returned an invalid response.")
                    val id = json["id"]?.jsonPrimitive?.content
                    url to id
                } finally {
                    connection.disconnect()
                }
            }
        }

        if (edgeFunctionResult.isFailure) {
            val edgeError = edgeFunctionResult.exceptionOrNull()?.message.orEmpty()
            android.util.Log.w("WhisperEncryptedImageHost", "Edge function upload failed: $edgeError")
        }
        
        if (edgeFunctionResult.isSuccess) {
            return@runCatching edgeFunctionResult.getOrThrow()
        }

        error("Encrypted image upload failed: ${edgeFunctionResult.exceptionOrNull()?.message.orEmpty().ifBlank { "unknown error" }}")
    }

    suspend fun delete(url: String, attachmentId: String?): Result<Unit> = runCatching {
        if (attachmentId == null) return@runCatching // Cannot delete without ID

        if (url.contains("supabase.co/storage/v1/object/public/whisper-avatars/")) {
            // It's a Supabase Storage file (the ID we store is the path). Avatar objects live
            // at "<userId>/avatar.ext" (see WhisperRepository.uploadAvatar), so only blobs
            // inside the current user's own folder may be deleted — otherwise a crafted URL
            // would let any signed-in user delete another user's avatar.
            val currentUserId = supabase.auth.currentSessionOrNull()?.user?.id
            if (currentUserId == null ||
                !attachmentId.startsWith("$currentUserId/") ||
                !attachmentId.contains("/") ||
                attachmentId.contains("..")
            ) return@runCatching
            supabase.storage.from("whisper-avatars").delete(attachmentId)
        } else {
            // It's an ImgBB file. We need an Edge Function to delete it because the API key is secret.
            val token = supabase.auth.currentSessionOrNull()?.accessToken ?: return@runCatching
            
            withContext(Dispatchers.IO) {
                val connection = (URL("${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/whisper-image-delete").openConnection() as HttpURLConnection)
                try {
                    val body = buildJsonObject { put("id", attachmentId) }.toString()
                    val bodyBytes = body.toByteArray(Charsets.UTF_8)
                    connection.requestMethod = "POST"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(bodyBytes) }
                    
                    if (connection.responseCode !in 200..299) {
                        android.util.Log.w("WhisperEncryptedImageHost", "Remote deletion failed: HTTP ${connection.responseCode}")
                        // Surface the failure to the caller: a silent success here would make
                        // the app think the image was removed when the blob is still hosted.
                        error("Remote deletion failed: HTTP ${connection.responseCode}")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }


    suspend fun download(url: String): Result<ByteArray> = runCatching {
        require(url.startsWith("https://")) { "Invalid image URL." }
        // SSRF allowlist: only the image hosts this app actually uses may be fetched.
        // Anything else (metadata endpoints, internal addresses, redirect targets) is
        // rejected before a connection is even opened.
        val host = runCatching { URL(url).host }.getOrNull() ?: error("Invalid image URL.")
        val supabaseHost = runCatching { URL(BuildConfig.SUPABASE_URL).host }.getOrNull()
        val allowedSupabase = supabaseHost != null && (host == supabaseHost || host.endsWith(".$supabaseHost"))
        if (host != "i.ibb.co" && host != "ibb.co" && !allowedSupabase) {
            error("Invalid image host.")
        }
        withContext(Dispatchers.IO) {
              val connection = (URL(url).openConnection() as HttpURLConnection)
            connection.instanceFollowRedirects = false
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                if (connection.responseCode in 300..399) {
                    val location = connection.getHeaderField("Location") ?: error("Invalid redirect")
                    val redirectHost = runCatching { URL(location).host }.getOrNull() ?: error("Invalid redirect")
                    val redirectAllowed = supabaseHost != null && (redirectHost == supabaseHost || redirectHost.endsWith(".$supabaseHost"))
                    if (redirectHost != "i.ibb.co" && redirectHost != "ibb.co" && !redirectAllowed) error("Invalid image host.")
                    error("Redirects not supported for images.")
                }
                if (connection.responseCode !in 200..299) error("Image is no longer available.")
                if (connection.contentLength > DOWNLOAD_MAX_BYTES) error("Image is too large.")
                connection.inputStream.use { input ->
                    // Capped read: never buffer more than the download ceiling into memory,
                    // even if the server omits Content-Length or lies about it.
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(64 * 1024)
                    while (buffer.size() <= DOWNLOAD_MAX_BYTES) {
                        val n = input.read(chunk)
                        if (n == -1) break
                        buffer.write(chunk, 0, n)
                    }
                    if (buffer.size() > DOWNLOAD_MAX_BYTES) error("Image is too large.")
                    buffer.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        // Base64 expands payloads by 4/3; stay below ImgBB's 32 MB input limit.
        const val MAX_CIPHER_BYTES = WhisperImageCipherTransport.MAX_CIPHER_BYTES
        // Downloads carry PNG-encoded ciphertext: 5 MiB of ciphertext becomes up to ~6.7 MiB
        // of PNG (4/3 RGBA expansion), so the download ceiling is separate from and larger
        // than the upload cap. 7 MiB bounds memory use while never rejecting legit images.
        const val DOWNLOAD_MAX_BYTES = 7 * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
    }
}
