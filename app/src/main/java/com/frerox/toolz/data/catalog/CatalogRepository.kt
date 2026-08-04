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

package com.frerox.toolz.data.catalog

import com.frerox.toolz.data.catalog.innertube.InnerTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The extraction engine powering the Music Catalog tab.
 * Uses NewPipeExtractor to search YouTube, resolve audio-only streams,
 * and fetch timed captions for LRC sync.
 *
 * STRICT CONSTRAINT: Never initializes video decoders. All stream resolution
 * is filtered to audio-only codecs (M4A/Opus).
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    val innerTubeClient: InnerTubeClient
) {
    private var isInitialized = false

    private val youtubeService: YoutubeService
        get() {
            initNewPipe()
            return ServiceList.YouTube as YoutubeService
        }

    private fun initNewPipe() {
        if (isInitialized) return
        try {
            // Localization can affect redirects. Standardizing on US/English.
            NewPipe.init(OkHttpDownloader(okHttpClient), Localization.DEFAULT, ContentCountry.DEFAULT)
            isInitialized = true
        } catch (_: Exception) { 
            isInitialized = true
        }
    }

    init {
        initNewPipe()
    }

    /**
     * Search YouTube for tracks matching the query.
     * Returns a pair of (results, nextPage token for pagination).
     */
    suspend fun search(
        query: String,
        page: Page? = null
    ): Pair<List<CatalogTrack>, Page?> = withContext(Dispatchers.IO) {
        try {
            // High-speed InnerTube search first
            val continuation = if (page is InnerTubePage) page.continuation else null
            val (tracks, nextToken) = innerTubeClient.search(query, continuation)
            
            if (tracks.isNotEmpty()) {
                val nextP = nextToken?.let { InnerTubePage(it) }
                return@withContext tracks to nextP
            }

            // Fallback to NewPipe if InnerTube is empty (rare)
            performInternalSearch(query, page, useMusicFilter = true)
        } catch (e: Exception) {
            val isParsingError = e.message?.contains("HTML") == true || 
                                e.message?.contains("JSON") == true ||
                                e.message?.contains("regex", ignoreCase = true) == true ||
                                e.message?.contains("group", ignoreCase = true) == true

            if (isParsingError) {
                android.util.Log.w("CatalogRepo", "Filtered search failed, falling back to broad search: $query")
                try {
                    // Fallback to broad search (which uses main YouTube instead of YouTube Music)
                    performInternalSearch(query, null, useMusicFilter = false)
                } catch (e2: Exception) {
                    android.util.Log.e("CatalogRepo", "Search failed completely for query \"$query\": ${e2.message}")
                    throw e2
                }
            } else {
                android.util.Log.e("CatalogRepo", "Network error during search for \"$query\": ${e.message}")
                throw e
            }
        }
    }

    private class InnerTubePage(val continuation: String) : Page(continuation)

    private fun performInternalSearch(
        query: String,
        page: Page?,
        useMusicFilter: Boolean
    ): Pair<List<CatalogTrack>, Page?> {
        val contentFilters = if (useMusicFilter) listOf("music_songs") else emptyList()
        val sortFilter = ""

        return if (page == null) {
            val searchInfo = SearchInfo.getInfo(
                youtubeService,
                youtubeService.searchQHFactory.fromQuery(
                    query,
                    contentFilters,
                    sortFilter
                )
            )
            val tracks = searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toCatalogTrack() }
            
            Pair(tracks, searchInfo.nextPage)
        } else {
            val moreItems = SearchInfo.getMoreItems(
                youtubeService,
                youtubeService.searchQHFactory.fromQuery(
                    query,
                    contentFilters,
                    sortFilter
                ),
                page
            )
            val tracks = moreItems.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toCatalogTrack() }
            Pair(tracks, moreItems.nextPage)
        }
    }

    /**
     * Fetch trending/kiosk content as a fallback discovery mechanism.
     * Modified to use search with "music_songs" filter to ensure only songs are shown.
     */
    suspend fun getTrending(
        page: Page? = null
    ): Pair<List<CatalogTrack>, Page?> = withContext(Dispatchers.IO) {
        // We use search instead of kiosk to strictly enforce the "music_songs" filter
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        search("trending music $year", page)
    }

    /**
     * Resolve the audio-only stream URL for a given YouTube video URL.
     * STRICT: Only audio streams (M4A / Opus) are considered.
     * Never accesses video streams to save bandwidth and battery.
     *
     * @param quality "AUTO" (default, highest), "HIGH", "MEDIUM", or "LOW"
     */
    suspend fun resolveAudioStream(sourceUrl: String, quality: String = "AUTO"): String? = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val streamInfo = StreamInfo.getInfo(youtubeService, sourceUrl)
            val audioStreams = streamInfo.audioStreams

            android.util.Log.d("CatalogRepo", "Found ${audioStreams.size} audio streams for $sourceUrl")

            // Prefer M4A (AAC), then Opus, then any audio — filter to audio-only formats
            val filtered = audioStreams
                .filter { stream ->
                    val format = stream.getFormat()
                    if (format == null) return@filter true // If unknown format, allow it as fallback
                    val name = format.name.lowercase()
                    name.contains("m4a") ||
                    name.contains("mp4a") ||
                    name.contains("webma") ||
                    name.contains("opus") ||
                    name.contains("webm")
                }
                .sortedByDescending { it.averageBitrate }
                .ifEmpty { audioStreams.sortedByDescending { it.averageBitrate } }

            val preferred = when (quality.uppercase()) {
                "LOW" -> filtered.lastOrNull() ?: filtered.firstOrNull()
                "MEDIUM" -> {
                    if (filtered.size <= 2) filtered.firstOrNull()
                    else filtered[filtered.size / 2]
                }
                else -> filtered.firstOrNull() // AUTO or HIGH → highest bitrate
            }

            var streamUrl = preferred?.content

            if (streamUrl == null) {
                // Fallback 1: Try any audio stream regardless of format
                streamUrl = audioStreams.sortedByDescending { it.averageBitrate }.firstOrNull()?.content
            }

            if (streamUrl == null) {
                // Fallback 2: Try multiplexed video stream (lowest resolution to save bandwidth)
                val videoStreams = streamInfo.videoStreams
                if (!videoStreams.isNullOrEmpty()) {
                    android.util.Log.w("CatalogRepo", "No audio streams found! Falling back to lowest resolution multiplexed video stream.")
                    streamUrl = videoStreams.sortedBy { it.resolution.replace("p", "").toIntOrNull() ?: Int.MAX_VALUE }.firstOrNull()?.content
                }
            }

            android.util.Log.d("CatalogRepo", "Resolved stream in ${System.currentTimeMillis() - startTime}ms: ${streamUrl != null}")
            streamUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("CatalogRepo", "resolveAudioStream failed: ${e.message}")
            null
        }
    }

    /**
     * Fetch timed captions (subtitles) from a YouTube video and convert to LRC format.
     * Returns null if no captions are available.
     */
    suspend fun fetchCaptions(sourceUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = StreamInfo.getInfo(youtubeService, sourceUrl)
            val subtitles = streamInfo.subtitles

            // Prefer auto-generated English captions, then any available
            val subtitle = subtitles.firstOrNull { 
                it.languageTag?.startsWith("en") == true 
            } ?: subtitles.firstOrNull()

            if (subtitle != null) {
                val subtitleUrl = subtitle.content
                // Download the VTT content
                val request = Request.Builder().url(subtitleUrl).build()
                val response = okHttpClient.newCall(request).execute()
                val vttContent = response.body?.string()
                response.close()

                if (vttContent != null) {
                    CaptionConverter.convertToLrc(vttContent)
                } else null
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Download the audio stream to a specified file and report progress.
     */
    suspend fun downloadAudioStream(
        streamUrl: String,
        outputFile: java.io.File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                response.close()
                return@withContext false
            }
            
            val body = response.body
            if (body == null) {
                response.close()
                return@withContext false
            }
            
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = java.io.FileOutputStream(outputFile)
            
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var totalBytesRead = 0L
            
            var lastProgressTime = 0L
            var syntheticProgress = 0.02f
            withContext(Dispatchers.Main) {
                onProgress(syntheticProgress)
            }
            
            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressTime > 200 || totalBytesRead == contentLength) {
                            lastProgressTime = currentTime
                            val progress = if (contentLength > 0) {
                                totalBytesRead.toFloat() / contentLength.toFloat()
                            } else {
                                syntheticProgress = (syntheticProgress + 0.015f).coerceAtMost(0.9f)
                                syntheticProgress
                            }
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            response.close()
            withContext(Dispatchers.Main) {
                onProgress(1f)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            false
        }
    }

    /**
     * Map a NewPipe StreamInfoItem to our lightweight CatalogTrack model.
     */
    private fun StreamInfoItem.toCatalogTrack(): CatalogTrack {
        val videoId = url.substringAfter("v=", "")
            .substringBefore("&")
            .ifBlank { url.substringAfterLast("/", "") }
            .ifBlank { url.hashCode().toString() }

        val bestThumb = thumbnails.maxByOrNull { it.width * it.height }
        var thumbUrl = bestThumb?.url ?: ""
        
        // Try to get higher quality YouTube thumbnail if possible
        if (thumbUrl.contains("hqdefault.jpg")) {
            thumbUrl = thumbUrl.replace("hqdefault.jpg", "maxresdefault.jpg")
        } else if (thumbUrl.contains("vi/")) {
            thumbUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
        }

        return CatalogTrack(
            id = videoId,
            title = name ?: "Unknown",
            artist = uploaderName ?: "Unknown Artist",
            thumbnailUrl = thumbUrl,
            streamUrl = null, // Resolved lazily
            duration = duration * 1000L, // NewPipe returns seconds, we use millis
            sourceUrl = url ?: ""
        )
    }

    /**
     * Lightweight OkHttp-based Downloader implementation for NewPipe.
     */
    private class OkHttpDownloader(
        private val client: OkHttpClient
    ) : Downloader() {

        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val url = request.url()
            val builder = Request.Builder()
                .url(url)
                .method(
                    request.httpMethod(),
                    if (request.dataToSend() != null) {
                        okhttp3.RequestBody.create(null, request.dataToSend()!!)
                    } else null
                )

            // Use a standard browser User-Agent for better compatibility with extractor 0.26.4
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
            builder.header("Accept-Language", "en-US,en;q=0.9")
            builder.header("Origin", "https://www.youtube.com")
            builder.header("Referer", "https://www.youtube.com/")
            
            // Minimal essential cookies
            builder.header("Cookie", "SOCS=CAI+ish")

            // Add headers from NewPipe, but avoid duplicates with our safety headers
            request.headers().forEach { (key, values) ->
                values.forEach { value ->
                    val k = key.lowercase()
                    if (k == "user-agent" || k == "cookie" || k == "origin" || k == "referer") return@forEach
                    builder.addHeader(key, value)
                }
            }

            val response = client.newCall(builder.build()).execute()
            val bodyString = response.body?.string()

            // Convert OkHttp headers to Map<String, List<String>>
            val responseHeaders = mutableMapOf<String, MutableList<String>>()
            for (name in response.headers.names()) {
                responseHeaders[name] = response.headers.values(name).toMutableList()
            }

            val finalUrl = response.request.url.toString()
            val code = response.code
            val message = response.message
            
            response.close()

            return Response(
                code,
                message,
                responseHeaders,
                bodyString,
                finalUrl
            )
        }
    }
}
