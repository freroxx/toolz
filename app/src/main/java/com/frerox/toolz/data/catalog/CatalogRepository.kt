package com.frerox.toolz.data.catalog

import kotlinx.coroutines.Dispatchers
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
    private val okHttpClient: OkHttpClient
) {
    private val youtubeService: YoutubeService
        get() = ServiceList.YouTube as YoutubeService

    init {
        try {
            NewPipe.init(OkHttpDownloader(okHttpClient))
        } catch (_: Exception) {
            // Already initialized — safe to ignore
        }
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
            val contentFilters = listOf("music_songs")
            val sortFilter = ""

            if (page == null) {
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
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), null)
        }
    }

    /**
     * Fetch trending/kiosk content as a fallback discovery mechanism.
     */
    suspend fun getTrending(
        page: Page? = null
    ): Pair<List<CatalogTrack>, Page?> = withContext(Dispatchers.IO) {
        try {
            val kiosks = youtubeService.kioskList
            val defaultKioskId = kiosks.defaultKioskId
            val kioskExtractor = kiosks.getExtractorById(defaultKioskId, page)

            if (page == null) {
                val kioskInfo = KioskInfo.getInfo(
                    youtubeService,
                    kioskExtractor.url
                )
                val tracks = kioskInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toCatalogTrack() }
                Pair(tracks, kioskInfo.nextPage)
            } else {
                val moreItems = KioskInfo.getMoreItems(
                    youtubeService,
                    kioskExtractor.url,
                    page
                )
                val tracks = moreItems.items
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toCatalogTrack() }
                Pair(tracks, moreItems.nextPage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to search-based trending
            search("trending music ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}", page)
        }
    }

    /**
     * Resolve the audio-only stream URL for a given YouTube video URL.
     * STRICT: Only audio streams (M4A / Opus) are considered.
     * Never accesses video streams to save bandwidth and battery.
     */
    suspend fun resolveAudioStream(sourceUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamInfo = StreamInfo.getInfo(youtubeService, sourceUrl)
            val audioStreams = streamInfo.audioStreams

            // Prefer M4A (AAC), then Opus, then any audio
            val preferred = audioStreams
                .filter { stream ->
                    val format = stream.getFormat()
                    format != null && (
                        format.name.contains("m4a", ignoreCase = true) ||
                        format.name.contains("mp4a", ignoreCase = true) ||
                        format.name.contains("webma", ignoreCase = true) ||
                        format.name.contains("opus", ignoreCase = true)
                    )
                }
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()
                ?: audioStreams.maxByOrNull { it.averageBitrate }

            preferred?.content
        } catch (e: Exception) {
            e.printStackTrace()
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
        } catch (e: Exception) {
            e.printStackTrace()
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
            val request = Request.Builder().url(streamUrl).build()
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
                                0f
                            }
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            response.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (outputFile.exists()) outputFile.delete()
            false
        }
    }

    /**
     * Map a NewPipe StreamInfoItem to our lightweight CatalogTrack model.
     */
    private fun StreamInfoItem.toCatalogTrack(): CatalogTrack {
        val bestThumb = thumbnails.maxByOrNull { it.width * it.height }
        var thumbUrl = bestThumb?.url ?: ""
        
        // Try to get higher quality YouTube thumbnail if possible
        if (thumbUrl.contains("hqdefault.jpg")) {
            thumbUrl = thumbUrl.replace("hqdefault.jpg", "maxresdefault.jpg")
        } else if (thumbUrl.contains("vi/")) {
            // If it's a standard YT thumb URL, ensure we try maxres
            val videoId = url.substringAfter("watch?v=", "").substringBefore("&")
            if (videoId.isNotEmpty()) {
                thumbUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"
            }
        }

        return CatalogTrack(
            id = url.substringAfter("watch?v=", "").substringBefore("&").ifEmpty { url.hashCode().toString() },
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
            val builder = Request.Builder()
                .url(request.url())
                .method(
                    request.httpMethod(),
                    if (request.dataToSend() != null) {
                        okhttp3.RequestBody.create(null, request.dataToSend()!!)
                    } else null
                )

            // Add headers
            request.headers().forEach { (key, values) ->
                values.forEach { value ->
                    builder.addHeader(key, value)
                }
            }

            val response = client.newCall(builder.build()).execute()
            val body = response.body?.string()

            // Convert OkHttp headers to Map<String, List<String>>
            val responseHeaders = mutableMapOf<String, MutableList<String>>()
            for (name in response.headers.names()) {
                responseHeaders[name] = response.headers.values(name).toMutableList()
            }

            return Response(
                response.code,
                response.message,
                responseHeaders,
                body,
                response.request.url.toString()
            )
        }
    }
}
