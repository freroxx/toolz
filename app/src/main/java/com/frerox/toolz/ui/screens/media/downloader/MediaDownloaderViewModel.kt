/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.media.downloader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.downloader.DownloadzExtractResponse
import com.frerox.toolz.data.downloader.MediaDownloaderRepository
import com.frerox.toolz.worker.SocialDownloadWorker
import com.frerox.toolz.worker.VideoDownloadWorker
import com.frerox.toolz.worker.YouTubeMp3DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * ViewModel for the unified Media Downloader tool (YouTube / TikTok / Instagram Reels).
 *
 * Routing:
 * - Remote (API) results → [SocialDownloadWorker] (same-instance /api/download streaming).
 * - Local YouTube fallback → [VideoDownloadWorker] / [YouTubeMp3DownloadWorker] (shared HD engine).
 */
@HiltViewModel
class MediaDownloaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: MediaDownloaderRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    data class QualityOption(
        val id: String,
        val label: String,
        val detail: String,
        val isAudio: Boolean = false,
        val isHd: Boolean = false,
    )

    /** Human-readable info for a download row, captured at enqueue time. */
    data class DownloadLabel(
        val title: String,
        val qualityLabel: String,
        val isAudio: Boolean,
        val thumbnailUrl: String? = null,
    )

    data class UiState(
        val url: String = "",
        val detectedPlatform: MediaDownloaderRepository.Platform? = null,
        val extracting: Boolean = false,
        val error: String? = null,
        val remote: DownloadzExtractResponse? = null,
        val blockedMessage: String? = null,
        val localTitle: String? = null,
        val localThumbnail: String? = null,
        val localSourceUrl: String? = null,
        val localHeights: List<Int> = emptyList(),
        val probingLocal: Boolean = false,
        val options: List<QualityOption> = emptyList(),
        val selectedId: String = "best",
        val audioOnly: Boolean = false,
        val apiConfigured: Boolean = true,
        val downloadEnqueued: String? = null,
    )

    private val _ui = MutableStateFlow(UiState(apiConfigured = repository.isApiConfigured()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _downloads = MutableStateFlow<List<WorkInfo>>(emptyList())
    val downloads: StateFlow<List<WorkInfo>> = _downloads.asStateFlow()

    private val _labels = MutableStateFlow<Map<String, DownloadLabel>>(emptyMap())
    val labels: StateFlow<Map<String, DownloadLabel>> = _labels.asStateFlow()

    private fun rememberLabel(id: java.util.UUID, label: DownloadLabel) {
        val next = _labels.value.toMutableMap()
        next[id.toString()] = label
        // Keep the map bounded to recent downloads.
        while (next.size > 30) next.remove(next.keys.first())
        _labels.value = next
    }

    private var extractJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                WorkManager.getInstance(appContext).getWorkInfosByTagFlow(SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD),
                WorkManager.getInstance(appContext).getWorkInfosByTagFlow(VideoDownloadWorker.TAG_VIDEO_DOWNLOAD),
                WorkManager.getInstance(appContext).getWorkInfosByTagFlow(YouTubeMp3DownloadWorker.TAG_MP3_DOWNLOAD),
            ) { social, video, mp3 -> (social + video + mp3).sortedByDescending { it.id.toString() }.take(10) }
                .collect { _downloads.value = it }
        }
    }

    fun onUrlChange(value: String) {
        _ui.value = _ui.value.copy(
            url = value,
            detectedPlatform = repository.detectPlatform(value),
            error = null,
            downloadEnqueued = null,
        )
    }

    fun setAudioOnly(audioOnly: Boolean) {
        if (_ui.value.audioOnly == audioOnly) return
        _ui.value = _ui.value.copy(audioOnly = audioOnly)
        if (_ui.value.remote != null || _ui.value.localSourceUrl != null) extract()
    }

    fun selectOption(id: String) {
        _ui.value = _ui.value.copy(selectedId = id, downloadEnqueued = null)
    }

    fun prefill(url: String?) {
        if (url.isNullOrBlank()) return
        onUrlChange(url)
        extract()
    }

    /** Clears the current result so the user can start a fresh lookup. */
    fun clearResult() {
        extractJob?.cancel()
        _ui.value = _ui.value.copy(
            url = "",
            detectedPlatform = null,
            extracting = false,
            error = null,
            remote = null,
            blockedMessage = null,
            localTitle = null,
            localThumbnail = null,
            localSourceUrl = null,
            localHeights = emptyList(),
            probingLocal = false,
            options = emptyList(),
            selectedId = "best",
            downloadEnqueued = null,
        )
    }

    fun cancelDownload(id: java.util.UUID) {
        WorkManager.getInstance(appContext).cancelWorkById(id)
    }

    /** Opens a finished download in an external viewer/player. */
    fun openDownload(context: Context, info: WorkInfo, label: DownloadLabel?) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val out = info.outputData
            val mime = out.getString("mime_type")
            val displayName = out.getString("display_name")
            var uri = out.getString("file_uri")?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
            if (uri != null && !uriResolves(uri)) uri = null
            if (uri == null && displayName != null) uri = findInMediaStore(displayName)
            if (uri == null && label != null) {
                val safe = label.title.replace(Regex("[^a-zA-Z0-9 \\-.]"), "_").take(80)
                val exts = if (label.isAudio) listOf(".mp3") else listOf(".mp4", ".mp3")
                for (ext in exts) {
                    uri = findInMediaStore(safe + ext)
                    if (uri != null) break
                }
            }
            if (uri == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    toast(context, appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_FileGone))
                }
                return@launch
            }
            val resolvedMime = mime
                ?: appContext.contentResolver.getType(uri)
                ?: if (label?.isAudio == true) "audio/*" else "video/*"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    context.startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    toast(context, appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_NoApp))
                } catch (_: Exception) {
                    toast(context, appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_FileGone))
                }
            }
        }
    }

    private fun uriResolves(uri: android.net.Uri): Boolean = try {
        if (uri.scheme == "file") {
            java.io.File(uri.path ?: "").exists()
        } else {
            appContext.contentResolver.openInputStream(uri)?.use { true } ?: false
        }
    } catch (_: Exception) {
        false
    }

    private fun findInMediaStore(displayName: String): android.net.Uri? {
        val collections = listOf(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        )
        for (collection in collections) {
            try {
                appContext.contentResolver.query(
                    collection,
                    arrayOf(android.provider.MediaStore.MediaColumns._ID),
                    "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(displayName),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        return android.content.ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (_: Exception) {
                // Try the next collection.
            }
        }
        return null
    }

    fun extract() {
        val raw = _ui.value.url.trim()
        if (raw.isBlank()) {
            _ui.value = _ui.value.copy(error = "Paste a link first.")
            return
        }
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(
                extracting = true, error = null, remote = null, blockedMessage = null,
                localTitle = null, localSourceUrl = null, options = emptyList(),
                selectedId = "best", downloadEnqueued = null,
            )
            when (val res = repository.extract(raw, _ui.value.audioOnly)) {
                is MediaDownloaderRepository.ExtractResult.Remote -> {
                    val opts = buildRemoteOptions(res.response)
                    _ui.value = _ui.value.copy(
                        extracting = false,
                        remote = res.response,
                        options = opts,
                        selectedId = opts.firstOrNull()?.id ?: "best",
                    )
                }
                is MediaDownloaderRepository.ExtractResult.Blocked -> {
                    _ui.value = _ui.value.copy(
                        extracting = false,
                        remote = res.response,
                        blockedMessage = res.message,
                        options = res.response?.let { buildRemoteOptions(it) } ?: emptyList(),
                    )
                }
                is MediaDownloaderRepository.ExtractResult.LocalYouTube -> {
                    _ui.value = _ui.value.copy(
                        extracting = false,
                        localTitle = res.title,
                        localThumbnail = res.thumbnailUrl,
                        localSourceUrl = res.sourceUrl,
                        probingLocal = true,
                    )
                    // Probe available heights for honest HD rows (fast, cached by extractor).
                    val heights = try {
                        catalogRepository.availableVideoHeights(res.sourceUrl)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val opts = buildLocalOptions(heights)
                    _ui.value = _ui.value.copy(
                        probingLocal = false,
                        localHeights = heights,
                        options = opts,
                        selectedId = opts.firstOrNull { it.id == "720p" }?.id ?: opts.firstOrNull()?.id ?: "best",
                    )
                }
                is MediaDownloaderRepository.ExtractResult.Error -> {
                    _ui.value = _ui.value.copy(extracting = false, error = res.message)
                }
            }
        }
    }

    private fun buildRemoteOptions(res: DownloadzExtractResponse): List<QualityOption> {
        val out = mutableListOf<QualityOption>()
        if (!res.download_url.isNullOrBlank()) {
            out += QualityOption("best", "Best quality", "recommended")
        }
        // Server-side cobalt ladder first (converted on demand), then direct formats.
        res.quality_options.forEach { q ->
            if (q.f.isNotBlank()) {
                out += QualityOption(q.f, q.label, "server", isAudio = q.f.endsWith("mp3"), isHd = false)
            }
        }
        res.formats.video
            .filter { !it.url.isNullOrBlank() && it.format_id != null }
            .take(8)
            .forEach { f ->
                val hasAudio = f.acodec != null && f.acodec != "none"
                val h = f.height
                out += QualityOption(
                    id = f.format_id!!,
                    label = f.resolution?.takeIf { it != "unknown" } ?: "${h?.let { "${it}p" } ?: (f.ext?.uppercase() ?: "Video")}",
                    detail = listOfNotNull(
                        if (!hasAudio) "no audio" else null,
                        f.filesize?.let { formatBytes(it) },
                        if (h != null && h >= 1080) "HD" else null,
                    ).joinToString(" · ").ifBlank { f.ext ?: "mp4" },
                    isHd = (h ?: 0) >= 1080,
                )
            }
        res.formats.audio
            .filter { !it.url.isNullOrBlank() && it.format_id != null }
            .take(4)
            .forEach { f ->
                out += QualityOption(
                    id = f.format_id!!,
                    label = f.abr?.let { "${it.toInt()}k audio" } ?: f.resolution?.takeIf { it != "unknown" } ?: "Audio",
                    detail = f.filesize?.let { formatBytes(it) } ?: (f.ext ?: "mp3"),
                    isAudio = true,
                )
            }
        return out.distinctBy { it.id }.take(14)
    }

    private fun buildLocalOptions(heights: List<Int>): List<QualityOption> {
        val has1080 = 1080 in heights || heights.any { it > 720 }
        val has1440 = heights.any { it >= 1440 }
        val has2160 = heights.any { it >= 2160 }
        return buildList {
            if (has2160) add(QualityOption("2160p", "2160p", "Ultra HD • merged", isHd = true))
            if (has1440) add(QualityOption("1440p", "1440p", "Quad HD • merged", isHd = true))
            // 1080p always offered — worker merges DASH pair, falls back to 720p muxed.
            add(QualityOption("1080p", "1080p", if (has1080) "Full HD • merged" else "Full HD • merged if available", isHd = true))
            add(QualityOption("720p", "720p", "HD"))
            add(QualityOption("480p", "480p", "SD"))
            add(QualityOption("360p", "360p", "Low"))
            add(QualityOption("240p", "240p", "Data saver"))
            add(QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true))
        }
    }

    fun downloadSelected(context: Context) {
        val s = _ui.value
        val opt = s.options.firstOrNull { it.id == s.selectedId } ?: s.options.firstOrNull()
        if (opt == null) {
            _ui.value = s.copy(error = "Nothing to download yet — extract a link first.")
            return
        }
        // Remote path (TikTok / IG / remote YouTube)
        val remote = s.remote
        if (remote != null && !remote.blocked) {
            val pageUrl = remote.original_url?.ifBlank { s.url.trim() } ?: s.url.trim()
            val isAudio = opt.isAudio || opt.id.equals("MP3", ignoreCase = true)
            val ext = if (isAudio) "mp3" else "mp4"
            val fileName = repository.downloadFileName(remote.title, ext)
            val req = OneTimeWorkRequestBuilder<SocialDownloadWorker>()
                .setInputData(
                    workDataOf(
                        SocialDownloadWorker.KEY_PAGE_URL to pageUrl,
                        SocialDownloadWorker.KEY_FORMAT_ID to opt.id,
                        SocialDownloadWorker.KEY_FILE_NAME to fileName,
                        SocialDownloadWorker.KEY_TITLE to (remote.title ?: "media"),
                        SocialDownloadWorker.KEY_THUMBNAIL_URL to remote.thumbnail,
                        SocialDownloadWorker.KEY_IS_AUDIO to isAudio,
                    )
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "social_download_${pageUrl.hashCode()}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                req,
            )
            rememberLabel(req.id, DownloadLabel(remote.title ?: "media", opt.label, isAudio, remote.thumbnail))
            _ui.value = s.copy(downloadEnqueued = "Download started — ${opt.label}")
            toast(context, "Downloading ${opt.label}…")
            return
        }
        // Local YouTube path (shared HD engine)
        val sourceUrl = s.localSourceUrl ?: s.url.trim()
        val title = s.localTitle ?: remote?.title ?: "YouTube video"
        val thumb = s.localThumbnail ?: remote?.thumbnail
        if (opt.id.equals("MP3", ignoreCase = true) || opt.isAudio) {
            enqueueMp3(sourceUrl, title, thumb, context)
        } else {
            enqueueVideo(sourceUrl, title, thumb, opt.id, context)
        }
        _ui.value = s.copy(downloadEnqueued = "Download started — ${opt.label}")
    }

    private fun enqueueVideo(sourceUrl: String, title: String, thumb: String?, quality: String, context: Context) {
        try {
            android.widget.Toast.makeText(context, "Downloading video ($quality)…", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        try {
            val req = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                .setInputData(
                    workDataOf(
                        VideoDownloadWorker.KEY_SOURCE_URL to sourceUrl,
                        VideoDownloadWorker.KEY_TITLE to title,
                        VideoDownloadWorker.KEY_THUMBNAIL_URL to thumb,
                        VideoDownloadWorker.KEY_QUALITY to quality,
                    )
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(VideoDownloadWorker.TAG_VIDEO_DOWNLOAD)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "video_download_${title.hashCode()}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                req,
            )
            rememberLabel(req.id, DownloadLabel(title, quality, isAudio = quality.equals("MP3", ignoreCase = true), thumbnailUrl = thumb))
        } catch (e: Exception) {
            android.util.Log.e("MediaDownloaderVM", "Video enqueue failed", e)
        }
    }

    private fun enqueueMp3(sourceUrl: String, title: String, thumb: String?, context: Context) {
        try {
            android.widget.Toast.makeText(context, "Downloading MP3…", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        try {
            val req = OneTimeWorkRequestBuilder<YouTubeMp3DownloadWorker>()
                .setInputData(
                    workDataOf(
                        YouTubeMp3DownloadWorker.KEY_SOURCE_URL to sourceUrl,
                        YouTubeMp3DownloadWorker.KEY_TITLE to title,
                        YouTubeMp3DownloadWorker.KEY_THUMBNAIL_URL to thumb,
                    )
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(YouTubeMp3DownloadWorker.TAG_MP3_DOWNLOAD)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "mp3_download_${title.hashCode()}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                req,
            )
            rememberLabel(req.id, DownloadLabel(title, "MP3", isAudio = true, thumbnailUrl = thumb))
        } catch (_: Exception) {
            enqueueVideo(sourceUrl, title, thumb, "MP3", context)
        }
    }

    private fun toast(context: Context, msg: String) {
        try {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    fun downloadProgress(info: WorkInfo): Float =
        info.progress.getFloat(SocialDownloadWorker.KEY_PROGRESS, Float.NaN).let { social ->
            if (!social.isNaN()) return social
            info.progress.getFloat(VideoDownloadWorker.KEY_PROGRESS, Float.NaN).let { video ->
                if (!video.isNaN()) return video
                info.progress.getFloat(YouTubeMp3DownloadWorker.KEY_PROGRESS, 0f)
            }
        }

    fun dismissMessage() {
        _ui.value = _ui.value.copy(error = null, downloadEnqueued = null, blockedMessage = null)
    }

    fun consumeEnqueued() {
        _ui.value = _ui.value.copy(downloadEnqueued = null)
    }

    private fun formatBytes(n: Long): String = when {
        n >= 1 shl 30 -> "%.2f GB".format(n / (1 shl 30).toDouble())
        n >= 1 shl 20 -> "%.1f MB".format(n / (1 shl 20).toDouble())
        n >= 1 shl 10 -> "%d KB".format(n / (1 shl 10))
        else -> "$n B"
    }
}
