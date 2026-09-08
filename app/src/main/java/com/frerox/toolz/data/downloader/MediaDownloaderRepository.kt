/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.downloader

import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.catalog.CatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified extraction repository for the Media Downloader tool.
 *
 * Strategy (per platform, mirroring toolz-downloadz-api/api/index.py detect_platform):
 * - TikTok / Instagram / YouTube-remote → toolz-downloadz-api /api/extract (requires API key).
 * - YouTube on-device fallback → CatalogRepository (InnerTube + NewPipe) when the API
 *   is unconfigured, unreachable, or returns blocked:true.
 *
 * The repository never throws raw Retrofit errors; it maps them to [ExtractResult].
 */
@Singleton
class MediaDownloaderRepository @Inject constructor(
    private val service: DownloadzService,
    @DownloadzClient private val okHttpClient: OkHttpClient,
    private val catalogRepository: CatalogRepository,
) {

    sealed interface ExtractResult {
        data class Remote(val response: DownloadzExtractResponse) : ExtractResult
        data class Blocked(val platform: String?, val message: String, val response: DownloadzExtractResponse?) : ExtractResult
        data class LocalYouTube(val title: String, val thumbnailUrl: String, val sourceUrl: String) : ExtractResult
        data class Error(val message: String, val httpCode: Int? = null) : ExtractResult
    }

    enum class Platform { YOUTUBE, TIKTOK, INSTAGRAM }

    fun isApiConfigured(): Boolean =
        BuildConfig.DOWNLOADZ_API_URL.isNotBlank() && BuildConfig.DOWNLOADZ_API_KEY.isNotBlank()

    fun detectPlatform(rawUrl: String): Platform? {
        val u = rawUrl.trim().lowercase()
        if ("youtube.com" in u || "youtu.be" in u) return Platform.YOUTUBE
        if ("tiktok.com" in u) return Platform.TIKTOK
        if ("instagram.com" in u && ("/reel" in u || "/reels" in u || "/p/" in u)) return Platform.INSTAGRAM
        return null
    }

    /** Rejects non-http(s) and private/internal hosts, mirroring api clean_url(). */
    fun sanitizeUrl(raw: String): String? {
        val url = raw.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        if (url.length > 2048) return null
        return try {
            val host = java.net.URI(url).host?.lowercase() ?: return null
            if (host in setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")) return null
            if (host.endsWith(".local") || host.endsWith(".internal")) return null
            if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return null
            if (host.startsWith("172.16.") || host.startsWith("172.17.") || host.startsWith("172.18.") ||
                host.startsWith("172.19.") || host.startsWith("172.2") || host.startsWith("172.30.") ||
                host.startsWith("172.31.")
            ) return null
            url
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extract(rawUrl: String, audioOnly: Boolean = false): ExtractResult =
        withContext(Dispatchers.IO) {
            val url = sanitizeUrl(rawUrl)
                ?: return@withContext ExtractResult.Error("Enter a valid http(s) link.")
            val platform = detectPlatform(url)
                ?: return@withContext ExtractResult.Error("Unsupported link. Only YouTube, TikTok and Instagram Reels are supported.")

            // Remote first when configured (TikTok/IG have no on-device engine).
            if (isApiConfigured()) {
                try {
                    val res = service.extract(url, audioOnly)
                    if (res.blocked) {
                        // YouTube blocked → fall through to on-device engine below.
                        if (platform != Platform.YOUTUBE) {
                            return@withContext ExtractResult.Blocked(
                                platform = res.platform,
                                message = res.blocked_message ?: "Extraction blocked on the server. Try again later.",
                                response = res,
                            )
                        }
                    } else {
                        return@withContext ExtractResult.Remote(res)
                    }
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    // YouTube → on-device fallback on any HTTP error; TikTok/IG surface the error.
                    if (platform != Platform.YOUTUBE) {
                        return@withContext ExtractResult.Error(
                            message = when (code) {
                                401 -> "Invalid API key. Check DOWNLOADZ_API_KEY in local.properties."
                                429 -> "Too many requests. Wait a minute and retry."
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
                    "TikTok / Instagram need the download server. Set DOWNLOADZ_API_KEY in local.properties and rebuild."
                )
            }

            // Local YouTube fallback (on-device engine, works offline of the download server).
            try {
                val videoId = extractYouTubeId(url)
                val thumb = if (videoId != null) "https://img.youtube.com/vi/$videoId/maxresdefault.jpg" else ""
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

    fun extractYouTubeId(url: String): String? {
        val idRegex = Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([a-zA-Z0-9_-]{11})")
        idRegex.find(url)?.let { return it.groupValues[1] }
        return Regex("[a-zA-Z0-9_-]{11}").find(url)?.value
    }

    /** Best single-tap download candidate from a remote response. */
    fun bestCandidates(res: DownloadzExtractResponse): Triple<String?, String, String> {
        // Triple(downloadUrlOrFormatId, ext, label)
        val fmt = res.formats.video.firstOrNull { !it.url.isNullOrBlank() }
        if (!res.download_url.isNullOrBlank()) {
            return Triple("best", res.ext ?: "mp4", "Best quality")
        }
        if (fmt != null) {
            return Triple(fmt.format_id ?: "best", fmt.ext ?: "mp4", fmt.resolution ?: "Video")
        }
        val audio = res.formats.audio.firstOrNull { !it.url.isNullOrBlank() }
        if (audio != null) {
            return Triple(audio.format_id ?: "best", audio.ext ?: "mp3", "Audio")
        }
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
