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
    class StreamResolutionException(message: String, val causeType: String? = null) : Exception(message)
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
     * STICKY: Tries InnerTube first, then falls back to NewPipeExtractor.
     *
     * @param quality "AUTO" (default, highest), "HIGH", "MEDIUM", or "LOW"
     */
    suspend fun resolveAudioStream(sourceUrl: String, quality: String = "AUTO"): String = withContext(Dispatchers.IO) {
        val videoId = sourceUrl.substringAfter("v=", "")
            .substringBefore("&")
            .ifBlank { sourceUrl.substringAfterLast("/", "") }

        try {
            // Step 1: Try InnerTube resolution (fastest)
            val innerTubeUrl = innerTubeClient.resolveStream(videoId)
            if (innerTubeUrl != null) {
                android.util.Log.i("CatalogRepo", "Resolved stream via InnerTube for $videoId")
                return@withContext innerTubeUrl
            }
            
            android.util.Log.d("CatalogRepo", "InnerTube resolution failed for $videoId, falling back to extractor")

            // Step 2: Fallback to NewPipeExtractor
            val startTime = System.currentTimeMillis()
            val streamInfo = StreamInfo.getInfo(youtubeService, sourceUrl)
            val audioStreams = streamInfo.audioStreams

            android.util.Log.d("CatalogRepo", "Extractor found ${audioStreams.size} audio streams for $sourceUrl")

            // Prefer M4A (AAC), then Opus, then any audio
            val filtered = audioStreams
                .filter { stream ->
                    val format = stream.getFormat()
                    if (format == null) return@filter true
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
                else -> filtered.firstOrNull() // AUTO or HIGH
            }

            var streamUrl = preferred?.content

            if (streamUrl == null) {
                streamUrl = audioStreams.sortedByDescending { it.averageBitrate }.firstOrNull()?.content
            }

            if (streamUrl == null) {
                val videoStreams = streamInfo.videoStreams
                if (!videoStreams.isNullOrEmpty()) {
                    streamUrl = videoStreams.sortedBy { it.resolution.replace("p", "").toIntOrNull() ?: Int.MAX_VALUE }.firstOrNull()?.content
                }
            }

            if (streamUrl == null) {
                throw StreamResolutionException("No playable streams found for this content", "UNPLAYABLE")
            }

            android.util.Log.d("CatalogRepo", "Resolved stream via extractor in ${System.currentTimeMillis() - startTime}ms")
            streamUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: StreamResolutionException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Unknown resolution error"
            android.util.Log.e("CatalogRepo", "resolveAudioStream failed: $msg")
            
            val errorType = when {
                msg.contains("403") -> "FORBIDDEN"
                msg.contains("429") -> "TOO_MANY_REQUESTS"
                msg.contains("Age restricted") -> "AGE_RESTRICTED"
                msg.contains("Region") -> "REGION_RESTRICTED"
                else -> "NETWORK_ERROR"
            }
            throw StreamResolutionException(msg, errorType)
        }
    }

    /**
     * HD video+audio pair for on-device merge (true 1080p+ with audio).
     * InnerTube adaptive first, then NewPipe videoOnly+audio fallback.
     */
    data class HdVideoPair(val videoUrl: String, val audioUrl: String, val height: Int)

    suspend fun resolveHdVideoPair(sourceUrl: String, maxHeight: Int = 1080): HdVideoPair? =
        withContext(Dispatchers.IO) {
            val videoId = sourceUrl.substringAfter("v=", "")
                .substringBefore("&")
                .ifBlank { sourceUrl.substringAfterLast("/", "").substringBefore("?") }

            // 1) InnerTube adaptive (fastest, no page scrape)
            try {
                val adaptive = innerTubeClient.resolveAdaptivePair(videoId, maxHeight)
                if (adaptive != null) {
                    android.util.Log.i("CatalogRepo", "HD pair via InnerTube ${adaptive.height}p for $videoId")
                    return@withContext HdVideoPair(adaptive.videoUrl, adaptive.audioUrl, adaptive.height)
                }
            } catch (e: Exception) {
                android.util.Log.w("CatalogRepo", "InnerTube HD pair failed: ${e.message}")
            }

            // 2) NewPipe videoOnly + audio fallback
            try {
                val cleanUrl = if (sourceUrl.startsWith("http")) sourceUrl else "https://www.youtube.com/watch?v=$videoId"
                val streamInfo = StreamInfo.getInfo(youtubeService, cleanUrl)
                fun npHeight(res: String?): Int {
                    if (res == null) return 0
                    val wxh = Regex("(\\d+)x(\\d+)").find(res)
                    if (wxh != null) return wxh.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                    return Regex("(\\d{3,4})").find(res)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                }
                val candidates = streamInfo.videoOnlyStreams
                    ?.filter { npHeight(it.resolution) in 1..maxHeight }
                    ?.sortedWith(
                        compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> {
                            // Prefer mp4/avc for clean FFmpeg mux, then height, then bitrate.
                            val fmt = it.format?.name?.lowercase() ?: ""
                            when {
                                fmt.contains("m4v") || fmt.contains("mp4") || fmt.contains("avc") -> 2
                                fmt.contains("webm") || fmt.contains("vp9") || fmt.contains("av01") -> 1
                                else -> 0
                            }
                        }.thenByDescending { npHeight(it.resolution) }
                         .thenByDescending { it.bitrate }
                    )
                val videoOnly = candidates?.firstOrNull()
                val audio = streamInfo.audioStreams?.sortedByDescending { it.averageBitrate }?.firstOrNull()
                if (videoOnly?.content != null && audio?.content != null) {
                    val h = npHeight(videoOnly.resolution)
                    android.util.Log.i("CatalogRepo", "HD pair via NewPipe ${h}p (${videoOnly.resolution} ${videoOnly.format?.name}) for $videoId (candidates=${candidates?.size})")
                    return@withContext HdVideoPair(videoOnly.content, audio.content, h)
                } else {
                    android.util.Log.w("CatalogRepo", "NewPipe HD pair empty for $videoId (videoOnly=${streamInfo.videoOnlyStreams?.size} audio=${streamInfo.audioStreams?.size})")
                }
            } catch (e: Exception) {
                android.util.Log.w("CatalogRepo", "NewPipe HD pair failed: ${e.message}")
            }
            null
        }

    /**
     * Available video heights for quality sheets — ONLY real, currently playable
     * heights (muxed + adaptive with direct urls). Returns empty when the probe
     * fails (offline / InnerTube disabled / page scrape failed) so callers can
     * show an "unknown, offer full ladder if-available" state instead of lying.
     * Previously this injected a fake classic ladder, which made has1080 always
     * true and let 1080p/720p presets silently save 360p files.
     */
    suspend fun availableVideoHeights(sourceUrl: String): List<Int> = withContext(Dispatchers.IO) {
        val videoId = sourceUrl.substringAfter("v=", "")
            .substringBefore("&")
            .ifBlank { sourceUrl.substringAfterLast("/", "").substringBefore("?") }
        val heights = mutableSetOf<Int>()
        try {
            heights += innerTubeClient.listMuxedHeights(videoId)
        } catch (_: Exception) {}
        if (heights.isEmpty()) {
            try {
                val cleanUrl = if (sourceUrl.startsWith("http")) sourceUrl else "https://www.youtube.com/watch?v=$videoId"
                val streamInfo = StreamInfo.getInfo(youtubeService, cleanUrl)
                streamInfo.videoStreams?.mapNotNullTo(heights) { parseStreamHeight(it.resolution) }
                streamInfo.videoOnlyStreams?.mapNotNullTo(heights) { parseStreamHeight(it.resolution) }
            } catch (_: Exception) {}
        }
        heights.distinct().sorted()
    }

    companion object {
        /** Parses "720p", "720p60", "1280x720" → 720. Returns null when unparseable. */
        fun parseStreamHeight(resolution: String?): Int? {
            if (resolution == null) return null
            val wxh = Regex("(\\d+)x(\\d+)").find(resolution)
            if (wxh != null) return wxh.groupValues.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }
            return Regex("(\\d{3,4})").find(resolution)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        }
    }

    /**
     * Resolves a direct playable and downloadable video stream URL (MP4) for a YouTube video.
     * Uses InnerTube first, then falls back to NewPipeExtractor muxed/video streams.
     *
     * Returns the url WITH its real height so callers can reject a silent
     * downgrade (e.g. 1080p requested but only 360p muxed exists).
     */
    data class VideoStream(val url: String, val height: Int)

    suspend fun resolveVideoStream(sourceUrl: String, maxHeight: Int = 720): VideoStream? = withContext(Dispatchers.IO) {
        val videoId = sourceUrl.substringAfter("v=", "")
            .substringBefore("&")
            .ifBlank { sourceUrl.substringAfterLast("/", "") }

        try {
            // Step 1: Try InnerTube resolution first (fastest)
            val innerTubeMuxed = innerTubeClient.resolveVideoStream(videoId, maxHeight)
            if (innerTubeMuxed != null) {
                android.util.Log.i("CatalogRepo", "Resolved video stream via InnerTube ${innerTubeMuxed.height}p (ceiling ${maxHeight}p) for $videoId")
                return@withContext VideoStream(innerTubeMuxed.url, innerTubeMuxed.height)
            }

            // Step 2: Fallback to NewPipeExtractor
            val cleanUrl = if (sourceUrl.startsWith("http")) sourceUrl else "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(youtubeService, cleanUrl)

            // Prefer muxed streams (video + audio in one stream, e.g. 720p or 360p MP4).
            // NOTE: YouTube progressive rarely exceeds 360p now — callers must check
            // .height against the request and prefer DASH merge / yt-dlp for HD.
            val muxed = streamInfo.videoStreams
            if (!muxed.isNullOrEmpty()) {
                val matched = muxed
                    .filter { stream ->
                        val res = parseStreamHeight(stream.resolution) ?: 0
                        res in 1..maxHeight
                    }
                    .maxByOrNull { parseStreamHeight(it.resolution) ?: 0 }
                    // Do NOT fall back to a *higher* stream than requested (would waste
                    // bandwidth and mislabel); only fall back to the smallest playable
                    // when everything exceeds the ceiling for very low requests.
                    ?: muxed.minByOrNull { parseStreamHeight(it.resolution) ?: Int.MAX_VALUE }
                        ?.takeIf { (parseStreamHeight(it.resolution) ?: Int.MAX_VALUE) > maxHeight && maxHeight < 360 }

                if (matched?.content != null) {
                    val h = parseStreamHeight(matched.resolution) ?: 0
                    android.util.Log.i("CatalogRepo", "Resolved muxed video stream via NewPipe (${matched.resolution}) for $videoId")
                    return@withContext VideoStream(matched.content, h)
                }
            }

            // Fallback to videoOnlyStreams (silent — no audio; worker should merge,
            // only used for inline playback where ExoPlayer handles DASH separately).
            val videoOnly = streamInfo.videoOnlyStreams
            if (!videoOnly.isNullOrEmpty()) {
                val matched = videoOnly
                    .filter { (parseStreamHeight(it.resolution) ?: 0) in 1..maxHeight }
                    .maxByOrNull { parseStreamHeight(it.resolution) ?: 0 }
                    ?: videoOnly.firstOrNull()
                if (matched?.content != null) {
                    val h = parseStreamHeight(matched.resolution) ?: 0
                    android.util.Log.w("CatalogRepo", "Falling back to SILENT video-only ${h}p for $videoId (no audio)")
                    return@withContext VideoStream(matched.content, h)
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("CatalogRepo", "resolveVideoStream failed for $videoId: ${e.message}")
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
        onProgress: suspend (Float) -> Unit
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
