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
import androidx.datastore.preferences.core.stringPreferencesKey
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
        private val HISTORY_KEY = stringPreferencesKey("media_downloader_link_history")
        private val FORMAT_PREFS_KEY = stringPreferencesKey("media_downloader_format_prefs")
        private const val HISTORY_MAX = 20
        /** Converted-audio ladder containers; mp3 is always on. */
        val DEFAULT_LADDER_EXTS = setOf("mp3", "m4a", "wav", "ogg", "flac")
        private val DEFAULT_PLATFORMS = setOf(
            MediaDownloaderRepository.Platform.YOUTUBE.name,
            MediaDownloaderRepository.Platform.TIKTOK.name,
            MediaDownloaderRepository.Platform.INSTAGRAM.name,
        )
    }

    /** What to fetch: video streams, audio streams, or both (default). */
    enum class DownloadMode { VIDEO, BOTH, AUDIO }

    /** Per-platform format memory, edited in the customization screen. */
    data class PlatformFormatPrefs(
        val favorites: List<String> = emptyList(),
        val hidden: Set<String> = emptySet(),
        val autoSelect: String? = null,
        val defaultMode: DownloadMode? = null,
    )

    data class DownloaderPrefs(
        val perPlatform: Map<String, PlatformFormatPrefs> = emptyMap(),
        val ladderExts: Set<String> = DEFAULT_LADDER_EXTS,
    )

    data class QualityOption(
        val id: String,
        val label: String,
        val detail: String,
        val isAudio: Boolean = false,
        val isHd: Boolean = false,
        /** Lowercase container (mp4, mp3, …) used for favorites/hide/auto-select. */
        val ext: String = "",
        /** Target container for converter-backed options (null = direct download). */
        val targetExt: String? = null,
        /** File-converter backend type used after the base audio download finishes. */
        val conversion: ConversionEngine.ConversionType? = null,
    )

    /** A previously processed link: re-opening it re-extracts fresh options. */
    data class HistoryEntry(
        val url: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val platform: MediaDownloaderRepository.Platform? = null,
        val timestampMs: Long = System.currentTimeMillis(),
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
        val mode: DownloadMode = DownloadMode.BOTH,
        val apiConfigured: Boolean = true,
        val downloadEnqueued: String? = null,
        val conversionStarted: String? = null,
    )

    private val _ui = MutableStateFlow(UiState(apiConfigured = repository.isApiConfigured()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _downloads = MutableStateFlow<List<WorkInfo>>(emptyList())
    val downloads: StateFlow<List<WorkInfo>> = _downloads.asStateFlow()

    private val _labels = MutableStateFlow<Map<String, DownloadLabel>>(emptyMap())
    val labels: StateFlow<Map<String, DownloadLabel>> = _labels.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _formatPrefs = MutableStateFlow(DownloaderPrefs())
    val formatPrefs: StateFlow<DownloaderPrefs> = _formatPrefs.asStateFlow()

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
            dataStore.data.map { prefs -> decodeHistory(prefs[HISTORY_KEY]) }
                .collect { _history.value = it }
        }
        viewModelScope.launch {
            dataStore.data.map { prefs -> decodePrefs(prefs[FORMAT_PREFS_KEY]) }
                .collect { _formatPrefs.value = it }
        }
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

    fun setMode(mode: DownloadMode) {
        val platform = _ui.value.url.trim().takeIf { it.isNotBlank() }?.let {
            repository.detectPlatform(it)
        }
        val hasOverride = platform?.let { _formatPrefs.value.perPlatform[it.name]?.defaultMode } != null
        if (_ui.value.mode == mode && !hasOverride) return
        _ui.value = _ui.value.copy(mode = mode)
        // What you tap is what applies: a per-platform override would otherwise
        // silently win and make the switch look broken.
        if (platform != null && hasOverride) setPlatformMode(platform, null)
        // Never re-extract without a link: toggling the mode after clearing the
        // field used to wipe the current result into a "Paste a link first" error.
        // Otherwise always re-extract so the option list matches the mode.
        if (_ui.value.url.trim().isBlank()) return
        extract()
    }

    fun selectOption(id: String) {
        _ui.value = _ui.value.copy(selectedId = id, downloadEnqueued = null)
    }

    fun prefill(url: String?) {
        if (url.isNullOrBlank()) return
        onUrlChange(url)
        extract()
    }

    /** Re-runs a history entry with fresh options. */
    fun reopenHistory(entry: HistoryEntry) {
        onUrlChange(entry.url)
        extract()
    }

    fun clearHistory() {
        _history.value = emptyList()
        viewModelScope.launch {
            try {
                dataStore.edit { prefs -> prefs.remove(HISTORY_KEY) }
            } catch (_: Exception) {
            }
        }
    }

    private fun recordHistory(entry: HistoryEntry) {
        val next = (_history.value.filterNot { it.url == entry.url } + entry).takeLast(HISTORY_MAX)
        _history.value = next
        viewModelScope.launch {
            try {
                dataStore.edit { prefs -> prefs[HISTORY_KEY] = encodeHistory(next) }
            } catch (_: Exception) {
            }
        }
    }

    private fun encodeHistory(entries: List<HistoryEntry>): String {
        val arr = org.json.JSONArray()
        entries.forEach { e ->
            arr.put(
                org.json.JSONObject().apply {
                    put("url", e.url)
                    put("title", e.title)
                    put("thumbnailUrl", e.thumbnailUrl)
                    put("platform", e.platform?.name)
                    put("timestampMs", e.timestampMs)
                },
            )
        }
        return arr.toString()
    }

    private fun decodeHistory(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val url = obj.optString("url").ifBlank { return@mapNotNull null }
                HistoryEntry(
                    url = url,
                    title = obj.optString("title").ifBlank { url },
                    thumbnailUrl = obj.optString("thumbnailUrl").ifBlank { null },
                    platform = obj.optString("platform").ifBlank { null }?.let {
                        runCatching { MediaDownloaderRepository.Platform.valueOf(it) }.getOrNull()
                    },
                    timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Format preferences (customization screen, backup-compatible) ──────────
    // Everything lives in the shared settings DataStore file, so the backup &
    // restore tool picks it up with BackupItem.SETTINGS and no extra wiring.

    private fun prefsFor(platform: MediaDownloaderRepository.Platform): PlatformFormatPrefs =
        _formatPrefs.value.perPlatform[platform.name] ?: PlatformFormatPrefs()

    private fun updatePrefs(transform: (DownloaderPrefs) -> DownloaderPrefs) {
        val next = transform(_formatPrefs.value).let { prefs ->
            // mp3 is always available in the converted ladder.
            prefs.copy(ladderExts = prefs.ladderExts + "mp3")
        }
        _formatPrefs.value = next
        viewModelScope.launch {
            try {
                dataStore.edit { it[FORMAT_PREFS_KEY] = encodePrefs(next) }
            } catch (_: Exception) {
            }
        }
    }

    fun toggleFavorite(platform: MediaDownloaderRepository.Platform, ext: String) {
        val key = platform.name
        updatePrefs { prefs ->
            val cur = prefs.perPlatform[key] ?: PlatformFormatPrefs()
            val next = if (ext in cur.favorites) cur.favorites - ext else cur.favorites + ext
            prefs.copy(perPlatform = prefs.perPlatform + (key to cur.copy(favorites = next)))
        }
    }

    fun toggleHidden(platform: MediaDownloaderRepository.Platform, ext: String) {
        if (ext == "mp3" || ext == "mp4") return
        val key = platform.name
        updatePrefs { prefs ->
            val cur = prefs.perPlatform[key] ?: PlatformFormatPrefs()
            val next = if (ext in cur.hidden) cur.hidden - ext else cur.hidden + ext
            prefs.copy(
                perPlatform = prefs.perPlatform + (
                    key to cur.copy(hidden = next, favorites = cur.favorites - ext)
                    ),
            )
        }
    }

    fun setAutoSelect(platform: MediaDownloaderRepository.Platform, ext: String?) {
        val key = platform.name
        updatePrefs { prefs ->
            val cur = prefs.perPlatform[key] ?: PlatformFormatPrefs()
            prefs.copy(perPlatform = prefs.perPlatform + (key to cur.copy(autoSelect = ext)))
        }
    }

    fun setPlatformMode(platform: MediaDownloaderRepository.Platform, mode: DownloadMode?) {
        val key = platform.name
        updatePrefs { prefs ->
            val cur = prefs.perPlatform[key] ?: PlatformFormatPrefs()
            prefs.copy(perPlatform = prefs.perPlatform + (key to cur.copy(defaultMode = mode)))
        }
    }

    fun setLadderExt(ext: String, enabled: Boolean) {
        if (ext == "mp3") return
        updatePrefs { prefs ->
            val next = if (enabled) prefs.ladderExts + ext else prefs.ladderExts - ext
            prefs.copy(ladderExts = next)
        }
    }

    fun resetFormatPrefs() {
        _formatPrefs.value = DownloaderPrefs()
        viewModelScope.launch {
            try {
                dataStore.edit { it.remove(FORMAT_PREFS_KEY) }
            } catch (_: Exception) {
            }
        }
    }

    private fun encodePrefs(prefs: DownloaderPrefs): String {
        val root = org.json.JSONObject()
        val perPlatform = org.json.JSONObject()
        prefs.perPlatform.forEach { (key, p) ->
            perPlatform.put(
                key,
                org.json.JSONObject().apply {
                    put("favorites", org.json.JSONArray(p.favorites))
                    put("hidden", org.json.JSONArray(p.hidden.toList()))
                    put("autoSelect", p.autoSelect)
                    put("defaultMode", p.defaultMode?.name)
                },
            )
        }
        root.put("perPlatform", perPlatform)
        root.put("ladderExts", org.json.JSONArray(prefs.ladderExts.toList()))
        return root.toString()
    }

    private fun decodePrefs(raw: String?): DownloaderPrefs {
        if (raw.isNullOrBlank()) return DownloaderPrefs()
        return try {
            val root = org.json.JSONObject(raw)
            val perPlatform = mutableMapOf<String, PlatformFormatPrefs>()
            val pp = root.optJSONObject("perPlatform")
            if (pp != null) {
                val keys = pp.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    // Ignore platforms from newer app versions.
                    if (runCatching { MediaDownloaderRepository.Platform.valueOf(key) }.getOrNull() == null) continue
                    val obj = pp.optJSONObject(key) ?: continue
                    perPlatform[key] = PlatformFormatPrefs(
                        favorites = obj.optJSONArray("favorites")?.let { arr ->
                            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                        } ?: emptyList(),
                        hidden = obj.optJSONArray("hidden")?.let { arr ->
                            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
                        } ?: emptySet(),
                        autoSelect = obj.optString("autoSelect").ifBlank { null },
                        defaultMode = obj.optString("defaultMode").ifBlank { null }?.let {
                            runCatching { DownloadMode.valueOf(it) }.getOrNull()
                        },
                    )
                }
            }
            val ladder = root.optJSONArray("ladderExts")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
            } ?: DEFAULT_LADDER_EXTS
            DownloaderPrefs(perPlatform = perPlatform, ladderExts = ladder + "mp3")
        } catch (_: Exception) {
            DownloaderPrefs()
        }
    }

    /** Per-platform default mode override, falling back to the global switch. */
    private fun effectiveMode(platform: MediaDownloaderRepository.Platform?): DownloadMode =
        platform?.let { _formatPrefs.value.perPlatform[it.name]?.defaultMode } ?: _ui.value.mode

    /** Effective mode for UI display (global switch + platform override). */
    fun effectiveModeForUi(platform: MediaDownloaderRepository.Platform): DownloadMode =
        effectiveMode(platform)

    /**
     * Applies hide + favorite-sort prefs. Video (mp4) rows are never hidden —
     * every mp4 row would match and the list would lose all video. Unknown
     * extensions are never hidden, and if hiding would empty the list the
     * filter is ignored (fail-open).
     */
    private fun applyFormatPrefs(
        platform: MediaDownloaderRepository.Platform,
        options: List<QualityOption>,
    ): List<QualityOption> {
        val prefs = prefsFor(platform)
        if (prefs.hidden.isEmpty() && prefs.favorites.isEmpty()) return options
        var list = if (prefs.hidden.isEmpty()) {
            options
        } else {
            options.filter { it.ext.isBlank() || it.ext == "mp4" || it.ext !in prefs.hidden }
        }
        if (list.isEmpty()) list = options
        if (prefs.favorites.isNotEmpty()) {
            val rank = prefs.favorites.withIndex().associate { it.value to it.index }
            list = list.sortedWith(
                compareBy({ rank[it.ext] ?: Int.MAX_VALUE }, { options.indexOf(it) }),
            )
        }
        return list
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
            when (val res = repository.extract(raw, effectiveMode(detected) == DownloadMode.AUDIO)) {
                is MediaDownloaderRepository.ExtractResult.Remote -> {
                    val mode = effectiveMode(detected)
                    val opts = buildRemoteOptions(res.response, mode, detected)
                    val defaultId = defaultSelection(detected, mode, opts)
                    _ui.value = _ui.value.copy(
                        extracting = false,
                        remote = res.response,
                        options = opts,
                        selectedId = defaultId,
                    )
                    recordHistory(
                        HistoryEntry(
                            url = raw,
                            title = res.response.title ?: raw,
                            thumbnailUrl = res.response.thumbnail,
                            platform = repository.detectPlatform(raw),
                        ),
                    )
                }
                is MediaDownloaderRepository.ExtractResult.Blocked -> {
                    _ui.value = _ui.value.copy(
                        extracting = false,
                        remote = res.response,
                        blockedMessage = res.message,
                        options = res.response?.let {
                            buildRemoteOptions(it, effectiveMode(detected), detected)
                        } ?: emptyList(),
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
                    val mode = effectiveMode(detected)
                    val opts = buildLocalOptions(
                        heights,
                        mode,
                        MediaDownloaderRepository.Platform.YOUTUBE,
                    )
                    _ui.value = _ui.value.copy(
                        probingLocal = false,
                        localHeights = heights,
                        options = opts,
                        selectedId = defaultSelection(
                            MediaDownloaderRepository.Platform.YOUTUBE,
                            mode,
                            opts,
                            preferredId = "720p",
                        ),
                    )
                    recordHistory(
                        HistoryEntry(
                            url = raw,
                            title = res.title,
                            thumbnailUrl = res.thumbnailUrl,
                            platform = MediaDownloaderRepository.Platform.YOUTUBE,
                        ),
                    )
                }
                is MediaDownloaderRepository.ExtractResult.Error -> {
                    _ui.value = _ui.value.copy(extracting = false, error = res.message)
                }
            }
        }
    }

    private fun buildRemoteOptions(
        res: DownloadzExtractResponse,
        mode: DownloadMode,
        platform: MediaDownloaderRepository.Platform?,
    ): List<QualityOption> {
        val directAudio = res.formats.audio
            .filter { !it.url.isNullOrBlank() && it.format_id != null }
            .take(3)
            .map { f ->
                QualityOption(
                    id = f.format_id!!,
                    label = f.abr?.let { "${it.toInt()}k audio" } ?: f.resolution?.takeIf { it != "unknown" } ?: "Audio",
                    detail = f.filesize?.let { formatBytes(it) } ?: (f.ext ?: "mp3"),
                    isAudio = true,
                    ext = (f.ext ?: "mp3").lowercase(),
                )
            }
        if (mode == DownloadMode.AUDIO) {
            val list = (directAudio + converterAudioLadder(includeDirectMp3 = false)).distinctBy { it.id }
            val withPrefs = platform?.let { applyFormatPrefs(it, list) } ?: list
            return withPrefs.take(10)
        }
        val out = mutableListOf<QualityOption>()
        if (!res.download_url.isNullOrBlank()) {
            out += QualityOption("best", "Best quality", "recommended", ext = res.ext?.lowercase() ?: "mp4")
        }
        // Server-side cobalt ladder first (converted on demand), then direct formats.
        res.quality_options.forEach { q ->
            if (q.f.isNotBlank()) {
                val isAudio = q.f.endsWith("mp3")
                if (mode == DownloadMode.VIDEO && isAudio) return@forEach
                out += QualityOption(q.f, q.label, "server", isAudio = isAudio, isHd = false, ext = if (isAudio) "mp3" else "mp4")
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
                    ext = (f.ext ?: "mp4").lowercase(),
                )
            }
        out += directAudio
        if (mode != DownloadMode.VIDEO) {
            // MP3 is always offered too (converter-backed so it works on every
            // platform, even when the server exposes no direct MP3 stream).
            val convertedMp3 = converterAudioLadder(includeDirectMp3 = false).first()
            if (out.none { it.id == convertedMp3.id }) out += convertedMp3
        }
        val withPrefs = platform?.let { applyFormatPrefs(it, out) } ?: out
        val filtered = if (mode == DownloadMode.VIDEO) withPrefs.filter { !it.isAudio } else withPrefs
        return filtered.distinctBy { it.id }.take(14)
    }

    /**
     * Converter-backed audio ladder. The base audio is downloaded first, then
     * transcoded on-device with the file-converter backend ([ConversionEngine]).
     *
     * @param includeDirectMp3 true on the local YouTube path, where the native
     * MP3 engine downloads directly. Remote platforms use the converted MP3
     * instead, since servers don't reliably expose a direct MP3 stream.
     */
    private fun converterAudioLadder(includeDirectMp3: Boolean = true): List<QualityOption> = buildList {
        if (includeDirectMp3) {
            add(QualityOption("MP3", "MP3", "Direct • 320k", isAudio = true, ext = "mp3"))
        } else {
            add(QualityOption("AUDIO_MP3", "MP3", "Converted on device • 320k", isAudio = true, ext = "mp3", targetExt = "mp3", conversion = ConversionEngine.ConversionType.AUDIO_TO_MP3))
        }
        add(QualityOption("AUDIO_M4A", "M4A", "Converted on device • AAC", isAudio = true, ext = "m4a", targetExt = "m4a", conversion = ConversionEngine.ConversionType.AUDIO_TO_M4A))
        add(QualityOption("AUDIO_WAV", "WAV", "Converted on device • lossless", isAudio = true, ext = "wav", targetExt = "wav", conversion = ConversionEngine.ConversionType.AUDIO_TO_WAV))
        add(QualityOption("AUDIO_OGG", "OGG", "Converted on device • Vorbis", isAudio = true, ext = "ogg", targetExt = "ogg", conversion = ConversionEngine.ConversionType.AUDIO_TO_OGG))
        add(QualityOption("AUDIO_FLAC", "FLAC", "Converted on device • lossless", isAudio = true, ext = "flac", targetExt = "flac", conversion = ConversionEngine.ConversionType.AUDIO_TO_FLAC))
    }.filter { it.targetExt == null || it.targetExt in _formatPrefs.value.ladderExts }

    private fun buildLocalOptions(
        heights: List<Int>,
        mode: DownloadMode,
        platform: MediaDownloaderRepository.Platform,
    ): List<QualityOption> {
        // Audio-only mode offers the converter-backed audio ladder instead of video.
        if (mode == DownloadMode.AUDIO) {
            val list = converterAudioLadder().distinctBy { it.id }
            return (applyFormatPrefs(platform, list)).take(10)
        }
        val includeMp3 = mode == DownloadMode.BOTH
        // heights is now HONEST (only real playable heights, empty = probe failed).
        // Empty → unknown: offer the full ladder with "if available" notes (old UX),
        // default selection stays 720p. Non-empty → cap the ladder at the real max so
        // we never promise 1080p on a 360p-only video (the old 640x360 bug).
        if (heights.isEmpty()) {
            val ladder = buildList {
                add(QualityOption("1080p", "1080p", "Full HD • merged if available", isHd = true))
                add(QualityOption("720p", "720p", "HD if available"))
                add(QualityOption("480p", "480p", "SD if available"))
                add(QualityOption("360p", "360p", "Low"))
                add(QualityOption("240p", "240p", "Data saver"))
                if (includeMp3) {
                    add(QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true))
                }
            }
            return applyFormatPrefs(platform, ladder.withVideoExt())
        }
        val maxH = heights.maxOrNull() ?: 0
        val ladder = buildList {
            if (maxH >= 2160 || heights.any { it >= 2160 }) add(QualityOption("2160p", "2160p", "Ultra HD • merged", isHd = true))
            if (maxH >= 1440 || heights.any { it >= 1440 }) add(QualityOption("1440p", "1440p", "Quad HD • merged", isHd = true))
            if (maxH >= 1080 || heights.any { it in 721..1200 }) add(QualityOption("1080p", "1080p", "Full HD • merged", isHd = true))
            if (maxH >= 720) add(QualityOption("720p", "720p", "HD"))
            if (maxH >= 480) add(QualityOption("480p", "480p", "SD"))
            // Always keep a playable low fallback + audio.
            add(QualityOption("360p", "360p", if (maxH < 480) "Best for this video" else "Low"))
            add(QualityOption("240p", "240p", "Data saver"))
            if (includeMp3) {
                add(QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true))
            }
        }
        return applyFormatPrefs(platform, ladder.withVideoExt())
    }

    /** Local rows carry no ext from the probe: video is always mp4, audio is mp3. */
    private fun List<QualityOption>.withVideoExt(): List<QualityOption> =
        map { opt ->
            when {
                opt.ext.isNotBlank() -> opt
                opt.isAudio -> opt.copy(ext = "mp3")
                else -> opt.copy(ext = "mp4")
            }
        }

    /** Default selection: remembered auto format, then an explicit preference,
     * then the mode default, then the first row. */
    private fun defaultSelection(
        platform: MediaDownloaderRepository.Platform?,
        mode: DownloadMode,
        opts: List<QualityOption>,
        preferredId: String? = null,
    ): String {
        platform?.let { p ->
            prefsFor(p).autoSelect?.let { auto ->
                opts.firstOrNull { it.ext == auto }?.id?.let { return it }
            }
        }
        preferredId?.let { preferred ->
            opts.firstOrNull { it.id == preferred }?.id?.let { return it }
        }
        return when (mode) {
            DownloadMode.AUDIO -> opts.firstOrNull { it.isAudio }?.id
            DownloadMode.VIDEO -> opts.firstOrNull { !it.isAudio }?.id
            DownloadMode.BOTH -> null
        } ?: opts.firstOrNull()?.id ?: "best"
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
        val byId = infos.associateBy { it.id.toString() }
        for (info in infos) {
            handlePendingWork(info, pendingConversions[info.id.toString()] ?: continue)
        }
        // The list above is capped to 10 recent downloads — a pending base download
        // can fall outside that window, so query those IDs directly instead of
        // silently dropping the conversion.
        val missing = pendingConversions.keys - byId.keys
        if (missing.isNotEmpty()) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val wm = WorkManager.getInstance(appContext)
                val found = mutableListOf<WorkInfo>()
                for (key in missing) {
                    val uuid = runCatching { java.util.UUID.fromString(key) }.getOrNull()
                    if (uuid == null) {
                        continue
                    }
                    try {
                        wm.getWorkInfoById(uuid).get()?.let { found += it }
                    } catch (_: Exception) {
                        // WorkManager query failed — keep pending for the next emission.
                    }
                }
                if (found.isNotEmpty()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        for (info in found) {
                            handlePendingWork(info, pendingConversions[info.id.toString()] ?: continue)
                        }
                    }
                }
            }
        }
    }

    private fun handlePendingWork(info: WorkInfo, conversion: ConversionEngine.ConversionType) {
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
            val msg = try {
                appContext.getString(
                    com.frerox.toolz.R.string.st_MediaDownloader_ConvertingTo,
                    conversion.extension.uppercase(),
                )
            } catch (_: Exception) {
                "Converting to ${conversion.extension.uppercase()}… find it in File Converter"
            }
            _ui.value = _ui.value.copy(conversionStarted = msg)
        } catch (_: Exception) {
            try {
                toast(appContext, appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_ConvertFailed))
            } catch (_: Exception) {
                toast(appContext, "Couldn't start the audio conversion.")
            }
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

    fun consumeConversionStarted() {
        _ui.value = _ui.value.copy(conversionStarted = null)
    }

    private fun formatBytes(n: Long): String = when {
        n >= 1 shl 30 -> "%.2f GB".format(n / (1 shl 30).toDouble())
        n >= 1 shl 20 -> "%.1f MB".format(n / (1 shl 20).toDouble())
        n >= 1 shl 10 -> "%d KB".format(n / (1 shl 10))
        else -> "$n B"
    }
}
