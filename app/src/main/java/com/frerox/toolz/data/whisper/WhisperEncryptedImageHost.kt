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

/**
 * V2-FIX L-?: thrown by [WhisperEncryptedImageHost.download] when the image HOST itself
 * reports the blob as gone — HTTP 403 (expired/revoked, e.g. a disappearing image) or
 * 404 (deleted). Distinct from generic transport/HTTP failures so the UI can label the
 * attachment "Expired" instead of "Failed to load". Callers currently catch the generic
 * Result failure; UI wiring is deferred.
 */
class DownloadExpiredException(message: String) : Exception(message)

/** Calls the authenticated Edge Function with automatic Supabase Storage fallback. */
@Singleton
class WhisperEncryptedImageHost @Inject constructor(
    private val supabase: SupabaseClient,
    // P2: shared hardened transport for upload/delete edge calls.
    private val edgeFunctions: EdgeFunctionClient,
) {
    private suspend fun getValidAccessToken(forceRefresh: Boolean = false): String {
        if (forceRefresh) {
            runCatching { supabase.auth.refreshCurrentSession() }
        }
        return supabase.auth.currentSessionOrNull()?.accessToken
            ?: error("Sign in before uploading an image.")
    }

    /** Returns Pair(url, attachmentId) */
    suspend fun upload(cipherBytes: ByteArray, name: String, expirationSeconds: Long?): Result<Pair<String, String?>> = runCatching {
        require(cipherBytes.isNotEmpty() && cipherBytes.size <= MAX_CIPHER_BYTES) { "Image is too large to send securely." }
        
        val pngBytes = WhisperImageCipherTransport.encode(cipherBytes)
        val body = buildJsonObject {
            put("image", Base64.encodeToString(pngBytes, Base64.NO_WRAP))
            put("name", name.take(80))
            expirationSeconds?.let { put("expiration", it) }
        }.toString()

        // P2: helper now delegates to the shared edge-function client. Same headers,
        // same status handling, same error strings; timeouts preserved (15s/45s).
        suspend fun executeUpload(token: String): Pair<Int, String> {
            val response = edgeFunctions.execute(
                EdgeFunctionClient.Request(
                    function = "whisper-image-upload",
                    jsonBody = body,
                    authMode = EdgeFunctionClient.AuthMode.TOKEN,
                    bearerToken = token,
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                ),
            )
            return response.code to response.body
        }

        // Try with current token, if 401 refresh token and retry once
        var token = getValidAccessToken(forceRefresh = false)
        var (code, response) = executeUpload(token)
        
        if (code == 401) {
            android.util.Log.w("WhisperEncryptedImageHost", "Edge upload returned 401 Unauthorized, refreshing session and retrying...")
            token = getValidAccessToken(forceRefresh = true)
            val retryResult = executeUpload(token)
            code = retryResult.first
            response = retryResult.second
        }

        if (code !in 200..299) {
            val errorMsg = runCatching {
                Json.parseToJsonElement(response).jsonObject["error"]?.jsonPrimitive?.content
            }.getOrNull() ?: response.take(200).ifBlank { "HTTP $code" }
            error("Encrypted image upload failed: $errorMsg")
        }

        val json = Json.parseToJsonElement(response).jsonObject
        val url = json["url"]?.jsonPrimitive?.content ?: error("Image host returned an invalid response.")
        val id = json["id"]?.jsonPrimitive?.content
        url to id
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

            // P2: transport moved to the shared edge-function client; failure surfacing
            // preserved (a silent success here would leave the blob hosted).
            val response = edgeFunctions.execute(
                EdgeFunctionClient.Request(
                    function = "whisper-image-delete",
                    jsonBody = buildJsonObject { put("id", attachmentId) }.toString(),
                    authMode = EdgeFunctionClient.AuthMode.USER,
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                ),
            )
            if (!response.is2xx) {
                android.util.Log.w("WhisperEncryptedImageHost", "Remote deletion failed: HTTP ${response.code}")
                error("Remote deletion failed: HTTP ${response.code}")
            }
        }
    }


    suspend fun download(url: String): Result<ByteArray> = runCatching {
        require(url.startsWith("https://")) { "Invalid image URL." }
        // V6-R7c FIX (not-applied): bust query (?t=) from whisperAvatarModel and
        // fragment (#att=) from stored avatar_url must not affect the fetch —
        // ImgBB may 404 on unknown query and fragments are never sent on the wire.
        // Strip them before allowlist checks and before opening the connection.
        val fetchUrl = url.substringBefore("#").substringBefore("?")
        // SSRF allowlist: only the image hosts this app actually uses may be fetched.
        // Anything else (metadata endpoints, internal addresses, redirect targets) is
        // rejected before a connection is even opened.
        // P1-14 FIX: Strict path validation for Supabase — only whisper-avatars objects.
        val host = runCatching { URL(fetchUrl).host }.getOrNull() ?: error("Invalid image URL.")
        val supabaseHost = runCatching { URL(BuildConfig.SUPABASE_URL).host }.getOrNull()
        val urlPath = runCatching { URL(fetchUrl).path }.getOrNull() ?: ""
        val allowedSupabase = supabaseHost != null && (host == supabaseHost || host.endsWith(".$supabaseHost")) &&
            urlPath.startsWith("/storage/v1/object/public/whisper-avatars/")
        if (host != "i.ibb.co" && host != "ibb.co" && !allowedSupabase) {
            error("Invalid image host.")
        }
        withContext(Dispatchers.IO) {
              val connection = (URL(fetchUrl).openConnection() as HttpURLConnection)
            connection.instanceFollowRedirects = false
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location") ?: error("Invalid redirect")
                    val redirectHost = runCatching { URL(location).host }.getOrNull() ?: error("Invalid redirect")
                    val redirectAllowed = supabaseHost != null && (redirectHost == supabaseHost || redirectHost.endsWith(".$supabaseHost"))
                    if (redirectHost != "i.ibb.co" && redirectHost != "ibb.co" && !redirectAllowed) error("Invalid image host.")
                    // V6-R7c FIX (not-applied): ImgBB occasionally 302s to its CDN edge.
                    // Follow a single allowed redirect instead of hard-failing — the
                    // previous `error("Redirects not supported")` made freshly uploaded
                    // avatars appear as "not applied" (download → null → initials).
                    val redirectClean = location.substringBefore("#").substringBefore("?")
                    val redirConn = (URL(redirectClean).openConnection() as HttpURLConnection)
                    try {
                        redirConn.instanceFollowRedirects = false
                        redirConn.connectTimeout = CONNECT_TIMEOUT_MS
                        redirConn.readTimeout = READ_TIMEOUT_MS
                        val redirCode = redirConn.responseCode
                        if (redirCode == 403 || redirCode == 404) {
                            throw DownloadExpiredException("Image is no longer available (HTTP $redirCode).")
                        }
                        if (redirCode !in 200..299) error("Image is no longer available.")
                        if (redirConn.contentLength > DOWNLOAD_MAX_BYTES) error("Image is too large.")
                        return@withContext redirConn.inputStream.use { input ->
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
                        redirConn.disconnect()
                    }
                }
                // V2-FIX L-?: 403/404 from the host means the blob expired or was deleted —
                // surface a STRUCTURED signal (not a generic failure) so the UI can label it.
                if (responseCode == 403 || responseCode == 404) {
                    throw DownloadExpiredException("Image is no longer available (HTTP $responseCode).")
                }
                if (responseCode !in 200..299) error("Image is no longer available.")
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
