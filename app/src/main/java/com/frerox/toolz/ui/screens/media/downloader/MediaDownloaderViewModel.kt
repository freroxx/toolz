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
import com.frerox.toolz.service.FileConversionService
import com.frerox.toolz.util.ConversionEngine
import com.frerox.toolz.worker.SocialDownloadWorker
import com.frerox.toolz.worker.VideoDownloadWorker
import com.frerox.toolz.worker.YouTubeMp3DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey

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
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    companion object {
        private val PLATFORMS_KEY = stringSetPreferencesKey("media_downloader_platforms")
        private val DEFAULT_PLATFORMS = setOf(
            MediaDownloaderRepository.Platform.YOUTUBE.name,
            MediaDownloaderRepository.Platform.TIKTOK.name,
            MediaDownloaderRepository.Platform.INSTAGRAM.name,
        )
    }

    data class QualityOption(
        val id: String,
        val label: String,
        val detail: String,
        val isAudio: Boolean = false,
        val isHd: Boolean = false,
        /** Target container for converter-backed options (null = direct download). */
        val targetExt: String? = null,
        /** File-converter backend type used after the base audio download finishes. */
        val conversion: ConversionEngine.ConversionType? = null,
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
        val enabledPlatforms: Set<MediaDownloaderRepository.Platform> = setOf(
            MediaDownloaderRepository.Platform.YOUTUBE,
            MediaDownloaderRepository.Platform.TIKTOK,
            MediaDownloaderRepository.Platform.INSTAGRAM,
        ),
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

    /**
     * Base-download work IDs awaiting post-download conversion via the
     * file-converter backend. Main-thread confined (written in
     * [downloadSelected], consumed in the downloads collector).
     */
    private val pendingConversions = mutableMapOf<String, ConversionEngine.ConversionType>()

    init {
        viewModelScope.launch {
            dataStore.data
                .map { prefs -> prefs[PLATFORMS_KEY] ?: DEFAULT_PLATFORMS }
                .collect { names ->
                    val parsed = names.mapNotNull { runCatching { MediaDownloaderRepository.Platform.valueOf(it) }.getOrNull() }.toSet()
                    val effective = parsed.ifEmpty {
                        setOf(
                            MediaDownloaderRepository.Platform.YOUTUBE,
                            MediaDownloaderRepository.Platform.TIKTOK,
                            MediaDownloaderRepository.Platform.INSTAGRAM,
                        )
                    }
                    if (_ui.value.enabledPlatforms != effective) {
                        _ui.value = _ui.value.copy(enabledPlatforms = effective)
                    }
                }
        }
        viewModelScope.launch {
            combine(
                WorkManager.getInstance(appContext).getWorkInfosByTagFlow(SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD),
                WorkManager.getInstance(appContext).getWorkInfosByTagFlow(VideoDownloadWorker.TAG_VIDEO_DOWNLOAD),
                WorkManager.getInstance(appContext).getWorkInfosByTagFlow(YouTubeMp3DownloadWorker.TAG_MP3_DOWNLOAD),
            ) { social, video, mp3 -> (social + video + mp3).sortedByDescending { it.id.toString() }.take(10) }
                .collect {
                    _downloads.value = it
                    checkPendingConversions(it)
                }
        }
    }

    fun togglePlatform(platform: MediaDownloaderRepository.Platform) {
        val current = _ui.value.enabledPlatforms
        val next = if (platform in current) {
            if (current.size <= 1) return
            current - platform
        } else {
            current + platform
        }
        _ui.value = _ui.value.copy(enabledPlatforms = next, error = null)
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PLATFORMS_KEY] = next.map { it.name }.toSet()
            }
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
        // Never re-extract without a link: toggling the switch after clearing the
        // field used to wipe the current result into a "Paste a link first" error.
        if (_ui.value.url.trim().isBlank()) return
        // Fast path: options already contain audio rows — just reselect, no network.
        if (audioOnly && _ui.value.options.any { it.isAudio }) {
            val firstAudio = _ui.value.options.first { it.isAudio }
            _ui.value = _ui.value.copy(selectedId = firstAudio.id, downloadEnqueued = null)
            return
        }
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
            val uri = resolveDownloadUri(info, label)
            if (uri == null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    toast(context, appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_FileGone))
                }
                return@launch
            }
            val resolvedMime = info.outputData.getString("mime_type")
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

    /** Shared URI resolution for finished downloads (IO context). */
    private fun resolveDownloadUri(info: WorkInfo, label: DownloadLabel?): android.net.Uri? {
        val out = info.outputData
        val displayName = out.getString("display_name")
        var uri = out.getString("file_uri")?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
        if (uri != null && !uriResolves(uri)) uri = null
        if (uri == null && displayName != null) uri = findInMediaStore(displayName)
        if (uri == null && label != null) {
            val safe = label.title.replace(Regex("[^a-zA-Z0-9 \\-.]"), "_").take(80)
            val exts = if (label.isAudio) {
                listOf(".mp3", ".m4a", ".wav", ".ogg", ".flac", ".opus", ".aac")
            } else {
                listOf(".mp4", ".mp3")
            }
            for (ext in exts) {
                uri = findInMediaStore(safe + ext)
                if (uri != null) break
            }
        }
        return uri
    }

    fun extract() {
        val raw = _ui.value.url.trim()
        if (raw.isBlank()) {
            _ui.value = _ui.value.copy(error = "Paste a link first.")
            return
        }
        val detected = repository.detectPlatform(raw)
        if (detected != null && detected !in _ui.value.enabledPlatforms) {
            val msg = try {
                appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_PlatformDisabled)
            } catch (_: Exception) {
                "This link is from a disabled platform. Enable it above to continue."
            }
            _ui.value = _ui.value.copy(error = msg, detectedPlatform = detected)
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
                    val defaultId = if (_ui.value.audioOnly) {
                        opts.firstOrNull { it.isAudio }?.id ?: opts.firstOrNull()?.id ?: "best"
                    } else {
                        opts.firstOrNull()?.id ?: "best"
                    }
                    _ui.value = _ui.value.copy(
                        extracting = false,
                        remote = res.response,
                        options = opts,
                        selectedId = defaultId,
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
        val audioOnly = _ui.value.audioOnly
        val directAudio = res.formats.audio
            .filter { !it.url.isNullOrBlank() && it.format_id != null }
            .take(3)
            .map { f ->
                QualityOption(
                    id = f.format_id!!,
                    label = f.abr?.let { "${it.toInt()}k audio" } ?: f.resolution?.takeIf { it != "unknown" } ?: "Audio",
                    detail = f.filesize?.let { formatBytes(it) } ?: (f.ext ?: "mp3"),
                    isAudio = true,
                )
            }
        if (audioOnly) {
            return (directAudio + converterAudioLadder()).distinctBy { it.id }.take(10)
        }
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
        out += directAudio
        return out.distinctBy { it.id }.take(14)
    }

    /**
     * Converter-backed audio ladder. The base audio is downloaded first, then
     * transcoded on-device with the file-converter backend ([ConversionEngine]).
     */
    private fun converterAudioLadder(): List<QualityOption> = listOf(
        QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true),
        QualityOption("AUDIO_M4A", "M4A", "Converted on device • AAC", isAudio = true, targetExt = "m4a", conversion = ConversionEngine.ConversionType.AUDIO_TO_M4A),
        QualityOption("AUDIO_WAV", "WAV", "Converted on device • lossless", isAudio = true, targetExt = "wav", conversion = ConversionEngine.ConversionType.AUDIO_TO_WAV),
        QualityOption("AUDIO_OGG", "OGG", "Converted on device • Vorbis", isAudio = true, targetExt = "ogg", conversion = ConversionEngine.ConversionType.AUDIO_TO_OGG),
        QualityOption("AUDIO_FLAC", "FLAC", "Converted on device • lossless", isAudio = true, targetExt = "flac", conversion = ConversionEngine.ConversionType.AUDIO_TO_FLAC),
    )

    private fun buildLocalOptions(heights: List<Int>): List<QualityOption> {
        // Audio-only mode offers the converter-backed audio ladder instead of video.
        if (_ui.value.audioOnly) return converterAudioLadder()
        // heights is now HONEST (only real playable heights, empty = probe failed).
        // Empty → unknown: offer the full ladder with "if available" notes (old UX),
        // default selection stays 720p. Non-empty → cap the ladder at the real max so
        // we never promise 1080p on a 360p-only video (the old 640x360 bug).
        if (heights.isEmpty()) {
            return buildList {
                add(QualityOption("1080p", "1080p", "Full HD • merged if available", isHd = true))
                add(QualityOption("720p", "720p", "HD if available"))
                add(QualityOption("480p", "480p", "SD if available"))
                add(QualityOption("360p", "360p", "Low"))
                add(QualityOption("240p", "240p", "Data saver"))
                add(QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true))
            }
        }
        val maxH = heights.maxOrNull() ?: 0
        return buildList {
            if (maxH >= 2160 || heights.any { it >= 2160 }) add(QualityOption("2160p", "2160p", "Ultra HD • merged", isHd = true))
            if (maxH >= 1440 || heights.any { it >= 1440 }) add(QualityOption("1440p", "1440p", "Quad HD • merged", isHd = true))
            if (maxH >= 1080 || heights.any { it in 721..1200 }) add(QualityOption("1080p", "1080p", "Full HD • merged", isHd = true))
            if (maxH >= 720) add(QualityOption("720p", "720p", "HD"))
            if (maxH >= 480) add(QualityOption("480p", "480p", "SD"))
            // Always keep a playable low fallback + audio.
            add(QualityOption("360p", "360p", if (maxH < 480) "Best for this video" else "Low"))
            add(QualityOption("240p", "240p", "Data saver"))
            add(QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true))
        }
    }

    /** Pure helper for unit tests: max playable height, 0 when unknown. */
    internal fun maxAvailableHeight(heights: List<Int>): Int = heights.maxOrNull() ?: 0

    fun downloadSelected(context: Context) {
        val s = _ui.value
        val opt = s.options.firstOrNull { it.id == s.selectedId } ?: s.options.firstOrNull()
        if (opt == null) {
            _ui.value = s.copy(error = "Nothing to download yet — extract a link first.")
            return
        }
        // Converter-backed audio: download base audio first, transcode on completion.
        if (opt.conversion != null) {
            downloadThenConvert(context, s, opt)
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

    /**
     * Downloads base audio (MP3 / best audio stream), then transcodes it on-device
     * with the file-converter backend once the download succeeds.
     */
    private fun downloadThenConvert(context: Context, s: UiState, opt: QualityOption) {
        val conversion = opt.conversion ?: return
        val ext = opt.targetExt ?: "m4a"
        val remote = s.remote
        if (remote != null && !remote.blocked) {
            val pageUrl = remote.original_url?.ifBlank { s.url.trim() } ?: s.url.trim()
            val baseFormatId = remote.formats.audio.firstOrNull { !it.url.isNullOrBlank() && it.format_id != null }?.format_id ?: "best"
            val title = remote.title ?: "media"
            val fileName = repository.downloadFileName(title, "mp3")
            val req = OneTimeWorkRequestBuilder<SocialDownloadWorker>()
                .setInputData(
                    workDataOf(
                        SocialDownloadWorker.KEY_PAGE_URL to pageUrl,
                        SocialDownloadWorker.KEY_FORMAT_ID to baseFormatId,
                        SocialDownloadWorker.KEY_FILE_NAME to fileName,
                        SocialDownloadWorker.KEY_TITLE to title,
                        SocialDownloadWorker.KEY_THUMBNAIL_URL to remote.thumbnail,
                        SocialDownloadWorker.KEY_IS_AUDIO to true,
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
            pendingConversions[req.id.toString()] = conversion
            rememberLabel(req.id, DownloadLabel(title, "${opt.label} • .$ext", true, remote.thumbnail))
            _ui.value = s.copy(downloadEnqueued = "Download started — converting to ${opt.label} next")
            toast(context, "Downloading audio, then converting to ${opt.label}…")
            return
        }
        val sourceUrl = s.localSourceUrl ?: s.url.trim()
        val title = s.localTitle ?: remote?.title ?: "YouTube video"
        val thumb = s.localThumbnail ?: remote?.thumbnail
        val baseId = enqueueMp3(sourceUrl, title, thumb, context)
        if (baseId != null) {
            pendingConversions[baseId.toString()] = conversion
            rememberLabel(baseId, DownloadLabel(title, "${opt.label} • .$ext", true, thumb))
            _ui.value = s.copy(downloadEnqueued = "Download started — converting to ${opt.label} next")
        } else {
            _ui.value = s.copy(error = "Could not start the audio download. Try again.")
        }
    }

    /** Fires pending file-converter jobs for base downloads that just succeeded. */
    private fun checkPendingConversions(infos: List<WorkInfo>) {
        if (pendingConversions.isEmpty()) return
        for (info in infos) {
            val conversion = pendingConversions[info.id.toString()] ?: continue
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    pendingConversions.remove(info.id.toString())
                    val label = _labels.value[info.id.toString()]
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val uri = resolveDownloadUri(info, label)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (uri != null) {
                                startConversion(conversion, uri)
                            } else {
                                toast(appContext, appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_FileGone))
                            }
                        }
                    }
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    pendingConversions.remove(info.id.toString())
                }
                else -> Unit
            }
        }
    }

    private fun startConversion(conversion: ConversionEngine.ConversionType, uri: android.net.Uri) {
        try {
            val intent = android.content.Intent(appContext, FileConversionService::class.java).apply {
                putParcelableArrayListExtra("input_uris", ArrayList(listOf(uri)))
                putExtra("conversion_type", conversion.name)
                putExtra("high_quality", true)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            toast(appContext, "Converting to ${conversion.extension.uppercase()}…")
        } catch (_: Exception) {
            toast(appContext, appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_FileGone))
        }
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

    private fun enqueueMp3(sourceUrl: String, title: String, thumb: String?, context: Context): java.util.UUID? {
        try {
            android.widget.Toast.makeText(context, "Downloading MP3…", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        return try {
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
            req.id
        } catch (_: Exception) {
            enqueueVideo(sourceUrl, title, thumb, "MP3", context)
            null
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
