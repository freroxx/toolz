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

    private val apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI"
    private val baseUrl = "https://music.youtube.com/youtubei/v1"

    suspend fun search(query: String, continuation: String? = null): Pair<List<CatalogTrack>, String?> = withContext(Dispatchers.IO) {
        val endpoint = "$baseUrl/search?key=$apiKey"
        
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
        val endpoint = "$baseUrl/player?key=$apiKey"
        
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

    suspend fun getRelatedArtists(videoId: String): List<String> = withContext(Dispatchers.IO) {
        val endpoint = "$baseUrl/next?key=$apiKey"
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
