/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.downloader

import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.catalog.CatalogRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified extraction repository for the Media Downloader tool.
 *
 * Strategy: all supported platforms use the v1 API first. It returns opaque,
 * short-lived asset IDs instead of upstream URLs. YouTube can fall back to the
 * device engine when the service is unavailable.
 *
 * The repository never throws raw Retrofit errors; it maps them to [ExtractResult].
 */
@Singleton
class MediaDownloaderRepository @Inject constructor(
    private val service: DownloadzService,
    @DownloadzClient private val okHttpClient: OkHttpClient,
    private val catalogRepository: CatalogRepository,
    @ApplicationContext private val context: Context,
) {

    sealed interface ExtractResult {
        data class Remote(val response: DownloadzExtractResponse) : ExtractResult
        data class Blocked(val platform: String?, val message: String, val response: DownloadzExtractResponse?) : ExtractResult
        data class LocalYouTube(val title: String, val thumbnailUrl: String, val sourceUrl: String) : ExtractResult
        data class Error(val message: String, val httpCode: Int? = null) : ExtractResult
    }

    enum class Platform { YOUTUBE, TIKTOK, INSTAGRAM }

    fun isApiConfigured(): Boolean = BuildConfig.DOWNLOADZ_API_URL.isNotBlank()

    fun detectPlatform(rawUrl: String): Platform? {
        val cleaned = cleanUrlForDetect(rawUrl) ?: return null
        val parsed = runCatching { java.net.URI(cleaned) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase()?.removeSuffix(".") ?: return null
        val path = parsed.path?.lowercase().orEmpty()
        fun isHost(domain: String) = host == domain || host.endsWith(".$domain")
        if (isHost("youtube.com") || isHost("youtu.be") || isHost("youtube-nocookie.com")) return Platform.YOUTUBE
        if (isHost("tiktok.com")) return Platform.TIKTOK
        if (isHost("instagram.com") && (path.startsWith("/reel") || path.startsWith("/reels") || path.startsWith("/p/") || path.startsWith("/tv/") || path.startsWith("/share"))) return Platform.INSTAGRAM
        return null
    }

    /** Trims pasted text, extracts first URL, strips trailing punctuation for detection. */
    fun cleanUrlForDetect(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // If user pasted surrounding text, pull the first URL out.
        val candidate = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(trimmed)?.value ?: trimmed
        return candidate.trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"').ifBlank { null }
    }

    /** Normalizes http→https and strips trailing punctuation from pasted links. */
    fun normalizePastedUrl(raw: String): String {
        var url = cleanUrlForDetect(raw) ?: return raw.trim()
        if (url.startsWith("http://", ignoreCase = true)) {
            url = "https://" + url.substringAfter("://")
        }
        return url
    }

    /** Rejects non-http(s) and private/internal hosts, mirroring api clean_url(). */
    fun sanitizeUrl(raw: String): String? {
        // Accept http pastes by upgrading to https; strip trailing punctuation first.
        val normalized = normalizePastedUrl(raw)
        val url = normalized.trim()
        if (!url.startsWith("https://")) return null
        if (url.length > 2048) return null
        return try {
            val host = java.net.URI(url).host?.lowercase()?.removeSuffix(".") ?: return null
            if (host in setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")) return null
            if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) return null
            if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return null
            if (isPrivate172(host)) return null
            if (host.startsWith("192.0.0.") || host.startsWith("198.18.") || host.startsWith("198.19.")) return null
            url
        } catch (_: Exception) {
            null
        }
    }

    private fun isPrivate172(host: String): Boolean {
        if (!host.startsWith("172.")) return false
        val second = host.removePrefix("172.").substringBefore(".").toIntOrNull() ?: return false
        return second in 16..31
    }

    suspend fun extract(rawUrl: String, audioOnly: Boolean = false): ExtractResult =
        withContext(Dispatchers.IO) {
            val url = sanitizeUrl(rawUrl)
                ?: return@withContext ExtractResult.Error("Enter a valid http(s) link.")
            val platform = detectPlatform(url)
                ?: return@withContext ExtractResult.Error("Unsupported link. Only YouTube, TikTok and Instagram Reels are supported.")

            // Every platform is server-first. The v1 API returns opaque asset IDs,
            // never raw media URLs or a project-wide embedded secret.
            if (isApiConfigured()) {
                try {
                    val session = service.createGuestSession(DownloadzGuestSessionRequest(installationId()))
                    if (session.access_token.isBlank()) throw IllegalStateException("Empty guest session")
                    val res = service.extractV1(
                        authorization = "Bearer ${session.access_token}",
                        request = DownloadzV1ExtractRequest(url, audioOnly),
                    )
                    return@withContext ExtractResult.Remote(res)
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    // YouTube → on-device fallback on any HTTP error; TikTok/IG surface the error.
                    if (platform != Platform.YOUTUBE) {
                        return@withContext ExtractResult.Error(
                            message = when (code) {
                                400 -> "That link looks invalid. Check it and try again."
                                401 -> "Your download session expired. Please retry."
                                402 -> "Download limit reached. Try again later."
                                403 -> "This media is private or blocked."
                                404 -> "Media not found. The post may have been deleted."
                                410 -> "This link expired. Extract it again."
                                422 -> "This link can't be downloaded. Try another."
                                429 -> "Too many requests. Wait a minute and retry."
                                in 500..599 -> "Server error ($code). Try again in a bit."
                                else -> "Server error ($code). Try again."
                            },
                            httpCode = code,
                        )
                    }
                    // else fall through to local YouTube
                } catch (_: Exception) {
                    if (platform != Platform.YOUTUBE) {
                        return@withContext ExtractResult.Error("No connection to the download server. Check your network and retry.")
                    }
                    // else fall through to local YouTube
                }
            } else if (platform != Platform.YOUTUBE) {
                return@withContext ExtractResult.Error(
                    "TikTok / Instagram need a configured download server."
                )
            }

            // Local YouTube fallback (on-device engine, works offline of the download server).
            try {
                val videoId = extractYouTubeId(url)
                // hqdefault always exists; maxres 404s on non-HD videos. UI tries maxres first.
                val thumb = if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""
                // Lightweight title probe via oEmbed-style fallback is skipped on-device;
                // UI shows the URL host + video id until streams resolve at download time.
                ExtractResult.LocalYouTube(
                    title = if (videoId != null) "YouTube video $videoId" else "YouTube video",
                    thumbnailUrl = thumb,
                    sourceUrl = url,
                )
            } catch (e: Exception) {
                ExtractResult.Error("YouTube lookup failed: ${e.message?.take(120) ?: "unknown error"}")
            }
        }

    /** A stable, non-secret app installation identifier for anonymous API sessions. */
    fun installationId(): String {
        val prefs = context.getSharedPreferences("downloadz_guest", Context.MODE_PRIVATE)
        return prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }
    }

    fun extractYouTubeId(url: String): String? {
        val cleaned = cleanUrlForDetect(url) ?: return null
        val strict = Regex("(?:[?&]v=|youtu\\.be/|shorts/|embed/|live/|/v/|watch\\?.*v=)([a-zA-Z0-9_-]{11})")
        strict.find(cleaned)?.let { return it.groupValues[1] }
        // youtu.be short link without query: https://youtu.be/VIDEOID
        runCatching { java.net.URI(cleaned) }.getOrNull()?.let { uri ->
            if (uri.host?.lowercase()?.endsWith("youtu.be") == true) {
                val seg = uri.path?.trim('/')?.substringBefore('/')?.substringBefore('?')
                if (seg?.matches(Regex("[a-zA-Z0-9_-]{11}")) == true) return seg
            }
        }
        return null
    }

    /** Thumbnail candidates in order: maxres (HD) then hqdefault (always exists). */
    fun youTubeThumbs(videoId: String?): List<String> {
        if (videoId == null) return emptyList()
        return listOf(
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        )
    }

    /** Best single-tap download candidate from a remote response. */
    fun bestCandidates(res: DownloadzExtractResponse): Triple<String?, String, String> {
        val asset = res.assets.firstOrNull { it.kind == "video" } ?: res.assets.firstOrNull()
        if (asset != null) return Triple(asset.id, asset.ext ?: "mp4", asset.resolution ?: asset.kind)
        return Triple(null, "mp4", "No stream")
    }

    fun downloadFileName(title: String?, ext: String): String {
        val safe = (title ?: "media")
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim().take(100).ifBlank { "media" }
        val cleanExt = ext.lowercase().takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "mp4"
        return "$safe.$cleanExt"
    }
}
