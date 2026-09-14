/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.catalog.innertube

import com.frerox.toolz.data.catalog.CatalogTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Open-source: key comes from BuildConfig (local.properties). No hardcoded value in repo.
    // This is a public YouTube InnerTube web key (same as NewPipe/etc), but externalized so it
    // can be rotated via local.properties or remote config without a code push.
    // If blank, all InnerTube calls gracefully no-op and CatalogRepository falls back to NewPipe.
    private val apiKey: String = com.frerox.toolz.BuildConfig.INNER_TUBE_API_KEY
    // Music base serves search + audio-only (ANDROID_MUSIC). Video/DASH player
    // requests MUST use the www base with an ANDROID client — music.youtube.com
    // returns LOGIN_REQUIRED / no streamingData for many non-music videos, which
    // used to empty the adaptive list and cap downloads at 360p muxed.
    private val musicBaseUrl = "https://music.youtube.com/youtubei/v1"
    private val webBaseUrl = "https://www.youtube.com/youtubei/v1"

    // Fresh player clients (aligned with NewPipeExtractor 0.26.5, Jan 2026).
    // The old ANDROID 19.29.37 is rejected/cipher-only by YouTube now, which
    // collapsed every HD request to the 360p progressive survivor. We rotate
    // ANDROID -> IOS -> WEB and take the first OK player with usable streams.
    private val androidClientVersion = "21.03.36"
    private val iosClientVersion = "21.03.2"
    private val webClientVersion = "2.20260120.01.00"
    private val androidUa =
        "com.google.android.youtube/21.03.36 (Linux; U; Android 15; en_US) gzip"
    private val iosUa =
        "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; en_US)"
    private val webUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

    private fun isConfigured(): Boolean {
        if (apiKey.isBlank()) {
            android.util.Log.w("InnerTubeClient", "INNER_TUBE_API_KEY is blank — InnerTube disabled, using NewPipe fallback. Set INNER_TUBE_API_KEY in local.properties (see local.properties.example)")
            return false
        }
        return true
    }

    suspend fun search(query: String, continuation: String? = null): Pair<List<CatalogTrack>, String?> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList<CatalogTrack>() to null
        val endpoint = "$musicBaseUrl/search?key=$apiKey"
        
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_MUSIC")
                    put("clientVersion", "7.19.52")
                    put("androidSdkVersion", 34)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("query", query)
                // Filter for "Songs" using standard params
                put("params", "Eg-KAQwIARAAGAAgACgA") 
            }
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("User-Agent", "com.google.android.apps.youtube.music/7.19.52 (Linux; U; Android 14; en_US) gzip")
            .header("X-Goog-Api-Format-Version", "2")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList<CatalogTrack>() to null
            val searchResponse = json.decodeFromString<InnerTubeSearchResponse>(body)
            
            val shelf = searchResponse.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull { it.musicShelfRenderer != null }
                ?.musicShelfRenderer

            val tracks = shelf?.contents?.mapNotNull { it.musicResponsiveListItemRenderer?.toCatalogTrack() } ?: emptyList()
            val nextContinuation = shelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation

            tracks to nextContinuation
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Search failed: ${e.message}")
            emptyList<CatalogTrack>() to null
        }
    }

    suspend fun resolveStream(videoId: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        val endpoint = "$musicBaseUrl/player?key=$apiKey"
        
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_MUSIC")
                    put("clientVersion", "7.19.52")
                    put("androidSdkVersion", 34)
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            put("playbackContext", buildJsonObject {
                putJsonObject("contentPlaybackContext") {
                    put("signatureTimestamp", 20000) // Generic timestamp
                }
            })
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("User-Agent", "com.google.android.apps.youtube.music/7.19.52 (Linux; U; Android 14; en_US) gzip")
            .header("X-Goog-Api-Format-Version", "2")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val playerResponse = json.decodeFromString<InnerTubePlayerResponse>(body)

            if (playerResponse.playabilityStatus?.status != "OK") {
                android.util.Log.w("InnerTubeClient", "Stream not playable: ${playerResponse.playabilityStatus?.reason}")
                return@withContext null
            }

            // Find best audio stream (M4A or Opus)
            val audioStreams = playerResponse.streamingData?.adaptiveFormats?.filter { 
                it.mimeType?.contains("audio/") == true
            } ?: emptyList()

            // Sort by bitrate descending
            val bestStream = audioStreams
                .filter { it.url != null } // We only handle direct URLs here
                .maxByOrNull { it.bitrate ?: 0L }

            bestStream?.url
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Stream resolution failed: ${e.message}")
            null
        }
    }

    /**
     * Muxed (progressive) result WITH its real height, so callers can detect a
     * silent downgrade (e.g. 1080p requested but only 360p muxed exists — YouTube
     * removed 720p progressive for most videos).
     */
    data class MuxedResult(val url: String, val height: Int)

    suspend fun resolveVideoStream(videoId: String, maxHeight: Int = 720): MuxedResult? = withContext(Dispatchers.IO) {
        // Prefer an exact muxed match first (fast path, no merge needed).
        resolveMuxedStream(videoId, maxHeight)
    }

    /**
     * HD pair for on-device merge: best DASH video (mp4/h264 preferred) at or below
     * [maxHeight] plus the best audio stream. Returns null when no adaptive pair exists
     * (caller falls back to legacy muxed-only behaviour).
     */
    data class AdaptivePair(val videoUrl: String, val audioUrl: String, val height: Int)

    suspend fun resolveAdaptivePair(videoId: String, maxHeight: Int = 720): AdaptivePair? =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext null
            // Scope: 1080p H264 max — higher VP9/AV1 would need re-encode.
            val ceiling = minOf(maxHeight, 1080)
            try {
                val player = fetchPlayerWithFallback(videoId) ?: return@withContext null
                if (player.playabilityStatus?.status != "OK") {
                    android.util.Log.w("InnerTubeClient", "Adaptive pair not playable for $videoId: ${player.playabilityStatus?.status} reason=${player.playabilityStatus?.reason?.take(120)}")
                    return@withContext null
                }
                val adaptive = player.streamingData?.adaptiveFormats ?: return@withContext null
                logCipherStats(videoId, player.streamingData?.formats.orEmpty(), adaptive)

                val video = adaptive
                    .filter { it.url != null && (it.mimeType?.contains("video/") == true) && (it.height ?: 0) > 0 }
                    .filter { (it.height ?: 0) <= ceiling }
                    // Prefer mp4/avc1 (plays everywhere, muxes cleanly), then any video, highest first.
                    .sortedWith(
                        compareByDescending<AdaptiveFormat> {
                            when {
                                it.mimeType?.contains("video/mp4") == true -> 2
                                it.mimeType?.contains("avc1") == true -> 1
                                else -> 0
                            }
                        }.thenByDescending { it.height ?: 0 }
                         .thenByDescending { it.bitrate ?: 0L }
                    )
                    .firstOrNull()
                if (video == null) {
                    val maxAvail = adaptive.filter { it.mimeType?.contains("video/") == true }.maxOfOrNull { it.height ?: 0 } ?: 0
                    android.util.Log.w("InnerTubeClient", "No playable DASH <= ${ceiling}p for $videoId (maxAvail=${maxAvail}p, totalAdaptive=${adaptive.size})")
                    return@withContext null
                }

                val audio = adaptive
                    .filter { it.url != null && (it.mimeType?.contains("audio/") == true) }
                    .maxByOrNull { it.bitrate ?: 0L }
                if (audio?.url == null) {
                    android.util.Log.w("InnerTubeClient", "No playable audio for DASH pair $videoId")
                    return@withContext null
                }

                AdaptivePair(videoUrl = video.url!!, audioUrl = audio.url!!, height = video.height ?: 0)
            } catch (e: Exception) {
                android.util.Log.e("InnerTubeClient", "Adaptive pair resolution failed: ${e.message}")
                null
            }
        }

    /** All playable video heights (muxed + adaptive with direct urls). Never faked. */
    suspend fun listMuxedHeights(videoId: String): List<Int> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList()
        try {
            val player = fetchPlayerWithFallback(videoId) ?: return@withContext emptyList()
            if (player.playabilityStatus?.status != "OK") return@withContext emptyList()
            logCipherStats(videoId, player.streamingData?.formats.orEmpty(), player.streamingData?.adaptiveFormats.orEmpty())
            (player.streamingData?.formats.orEmpty() + player.streamingData?.adaptiveFormats.orEmpty())
                .filter { it.url != null && it.mimeType?.contains("video/") == true }
                .mapNotNull { it.height?.takeIf { h -> h > 0 } }
                .distinct().sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun logCipherStats(videoId: String, muxed: List<AdaptiveFormat>, adaptive: List<AdaptiveFormat>) {
        try {
            val muxedPlayable = muxed.count { it.url != null }
            val adaptPlayable = adaptive.count { it.url != null && it.mimeType?.contains("video/") == true }
            val adaptCiphered = adaptive.count { it.url == null && !it.signatureCipher.isNullOrBlank() && it.mimeType?.contains("video/") == true }
            val adaptNoUrl = adaptive.count { it.url == null && it.mimeType?.contains("video/") == true } - adaptCiphered
            android.util.Log.d("InnerTubeClient", "formats for $videoId: muxedPlayable=$muxedPlayable/${muxed.size} dashPlayable=$adaptPlayable ciphered=$adaptCiphered noUrlOther=$adaptNoUrl total=${adaptive.size}")
        } catch (_: Exception) {}
    }

    private suspend fun resolveMuxedStream(videoId: String, maxHeight: Int): MuxedResult? =
        withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            val player = fetchPlayerWithFallback(videoId)
                ?: return@withContext null

            if (player.playabilityStatus?.status != "OK") {
                android.util.Log.w("InnerTubeClient", "Video stream not playable: ${player.playabilityStatus?.reason}")
                return@withContext null
            }

            // formats are muxed video + audio
            val formats = player.streamingData?.formats ?: emptyList()
            val playable = formats.filter { it.url != null && (it.mimeType?.contains("video/mp4") == true || it.mimeType?.contains("video/") == true) }
            if (playable.isEmpty()) {
                android.util.Log.w("InnerTubeClient", "No playable muxed for $videoId (formats=${formats.size} ciphered=${formats.count { !it.signatureCipher.isNullOrBlank() }})")
                return@withContext null
            }
            val matched = playable
                .filter { (it.height ?: 0) <= maxHeight }
                .maxByOrNull { it.height ?: 0 }
            if (matched?.url != null) {
                MuxedResult(url = matched.url, height = matched.height ?: 0)
            } else {
                // Nothing at/below ceiling — do NOT silently return a higher stream;
                // report the lowest higher stream is unavailable by returning null so
                // the caller can try DASH merge / yt-dlp instead of mislabeling.
                // Exception: if caller asked below everything (e.g. 240p on 360p-only
                // video), return the smallest playable so SD still works.
                val minPlayable = playable.minByOrNull { it.height ?: Int.MAX_VALUE }
                val minH = minPlayable?.height ?: 0
                if (minH > maxHeight) {
                    android.util.Log.w("InnerTubeClient", "Muxed floor for $videoId is ${minH}p > ceiling ${maxHeight}p — returning null for DASH/yt-dlp fallback")
                    null
                } else {
                    // Ceiling below smallest? unreachable, but keep safe fallback.
                    minPlayable?.url?.let { MuxedResult(url = it, height = minH) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InnerTubeClient", "Video stream resolution failed: ${e.message}")
            null
        }
        }

    /**
     * Tries fresh player clients in order and returns the first usable player.
     * ANDROID (fresh) -> IOS -> WEB. A player counts as usable when
     * playability is OK and it carries at least one direct-url video stream
     * (muxed or adaptive). Cipher-only players are skipped, not returned.
     */
    private suspend fun fetchPlayerWithFallback(videoId: String): InnerTubePlayerResponse? =
        withContext(Dispatchers.IO) {
            val attempts = listOf(
                Triple("ANDROID", androidClientVersion, true),
                Triple("IOS", iosClientVersion, true),
                Triple("WEB", webClientVersion, true),
            )
            var last: InnerTubePlayerResponse? = null
            for ((name, version, useWeb) in attempts) {
                val player = try {
                    fetchPlayer(videoId, clientName = name, clientVersion = version, useWebBase = useWeb)
                } catch (_: Exception) { null }
                if (player == null) continue
                last = player
                if (player.playabilityStatus?.status != "OK") {
                    android.util.Log.w(
                        "InnerTubeClient",
                        "Player $name/$version not playable for $videoId: ${player.playabilityStatus?.status} reason=${player.playabilityStatus?.reason?.take(120)}",
                    )
                    continue
                }
                val directVideo = (player.streamingData?.formats.orEmpty() + player.streamingData?.adaptiveFormats.orEmpty())
                    .count { it.url != null && it.mimeType?.contains("video/") == true }
                if (directVideo > 0) return@withContext player
                android.util.Log.w(
                    "InnerTubeClient",
                    "Player $name/$version cipher-only for $videoId (directVideo=0) — trying next client",
                )
            }
            last
        }

    private suspend fun fetchPlayer(
        videoId: String,
        clientName: String,
        clientVersion: String,
        useWebBase: Boolean = false,
    ): InnerTubePlayerResponse? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        val base = if (useWebBase) webBaseUrl else musicBaseUrl
        val endpoint = "$base/player?key=$apiKey"
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", clientName)
                    put("clientVersion", clientVersion)
                    when (clientName) {
                        "ANDROID" -> {
                            put("androidSdkVersion", 35)
                            put("osName", "Android")
                            put("osVersion", "15")
                            put("hl", "en")
                            put("gl", "US")
                        }
                        "IOS" -> {
                            put("deviceMake", "Apple")
                            put("deviceModel", "iPhone16,2")
                            put("osName", "iOS")
                            put("osVersion", "18.7.2.22H124")
                            put("hl", "en")
                            put("gl", "US")
                        }
                        else -> {
                            put("hl", "en")
                            put("gl", "US")
                            put("utcOffsetMinutes", 0)
                        }
                    }
                }
            }
            put("videoId", videoId)
            put("playbackContext", buildJsonObject {
                putJsonObject("contentPlaybackContext") {
                    put("signatureTimestamp", 20000)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }
            })
        }.toString().toRequestBody("application/json".toMediaType())
        val ua = when (clientName) {
            "ANDROID_MUSIC" -> "com.google.android.apps.youtube.music/7.19.52 (Linux; U; Android 14; en_US) gzip"
            "ANDROID" -> androidUa
            "IOS" -> iosUa
            else -> webUa
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("User-Agent", ua)
            .header("X-Goog-Api-Format-Version", "2")
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/")
            .build()
        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            json.decodeFromString<InnerTubePlayerResponse>(body)
        } catch (e: Exception) {
            android.util.Log.w("InnerTubeClient", "fetchPlayer failed: ${e.message}")
            null
        }
    }

    suspend fun getRelatedArtists(videoId: String): List<String> = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext emptyList<String>()
        val endpoint = "$musicBaseUrl/next?key=$apiKey"
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID_MUSIC")
                    put("clientVersion", "7.19.52")
                }
            }
            put("videoId", videoId)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("User-Agent", "com.google.android.apps.youtube.music/7.19.52 (Linux; U; Android 14; en_US) gzip")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList<String>()
            
            // Simplified parsing to find artist names in "Related" section
            val artistRegex = Regex("\"text\":\"([^\"]+)\",\"navigationEndpoint\":\\{\"clickTrackingParams\":\"[^\"]+\",\"browseEndpoint\":\\{\"browseId\":\"UC[^\"]+\"")
            artistRegex.findAll(body).map { it.groupValues[1] }.distinct().take(5).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun MusicResponsiveListItemRenderer.toCatalogTrack(): CatalogTrack? {
        val videoId = playlistItemData?.videoId ?: return null
        val title = flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.text ?: "Unknown"
        
        // Flex column 1 contains runs for Artist, Album, Duration etc.
        val runs = flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
        val artist = runs?.firstOrNull()?.text ?: "Unknown Artist"
        
        // Try to find duration (usually looks like "3:45")
        val durationText = runs?.lastOrNull { it.text?.contains(":") == true }?.text
        val durationMillis = durationText?.let { parseDuration(it) } ?: 0L

        val thumbUrl = thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
            ?: "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"

        return CatalogTrack(
            id = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbUrl,
            duration = durationMillis,
            sourceUrl = "https://www.youtube.com/watch?v=$videoId"
        )
    }

    private fun parseDuration(text: String): Long {
        return try {
            val parts = text.split(":").map { it.trim().toLong() }
            when (parts.size) {
                1 -> parts[0] * 1000L
                2 -> (parts[0] * 60 + parts[1]) * 1000L
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
                else -> 0L
            }
        } catch (_: Exception) {
            0L
        }
    }
}
