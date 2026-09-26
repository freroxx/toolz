/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.media.downloader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
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
 * - Remote v1 assets → [SocialDownloadWorker] (opaque extraction/asset handles).
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
        private val PENDING_CONVERSIONS_KEY = stringPreferencesKey("media_downloader_pending_conversions")
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
        /** v1 asset kind: video, audio, or image. */
        val kind: String = "video",
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
        /** video, audio, or image; retained for resilient post-download UI. */
        val mediaKind: String = if (isAudio) "audio" else "video",
        /** Opaque worker input retained only for an in-session retry. */
        val retryData: Data? = null,
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
            try {
                dataStore.data.map { prefs -> decodeHistory(prefs[HISTORY_KEY]) }
                    .collect { _history.value = it }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                dataStore.data.map { prefs -> decodePrefs(prefs[FORMAT_PREFS_KEY]) }
                    .collect { _formatPrefs.value = it }
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
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
            } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                dataStore.data.map { prefs -> decodePendingConversions(prefs[PENDING_CONVERSIONS_KEY]) }
                    .collect { restored ->
                        var changed = false
                        for ((id, conv) in restored) {
                            if (id !in pendingConversions) {
                                pendingConversions[id] = conv
                                changed = true
                            }
                        }
                        if (changed) {
                            // Trigger a re-check once downloads flow emits.
                        }
                    }
            } catch (_: Exception) { }
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

    private fun decodePendingConversions(raw: String?): Map<String, ConversionEngine.ConversionType> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = org.json.JSONObject(raw)
            val out = mutableMapOf<String, ConversionEngine.ConversionType>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                runCatching { ConversionEngine.ConversionType.valueOf(v) }.getOrNull()?.let { out[k] = it }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistPendingConversion(id: String, conversion: ConversionEngine.ConversionType) {
        viewModelScope.launch {
            try {
                dataStore.edit { prefs ->
                    val cur = decodePendingConversions(prefs[PENDING_CONVERSIONS_KEY]).toMutableMap()
                    cur[id] = conversion
                    // Bound the map so it can't grow forever.
                    while (cur.size > 20) cur.remove(cur.keys.first())
                    val obj = org.json.JSONObject()
                    cur.forEach { (k, v) -> obj.put(k, v.name) }
                    prefs[PENDING_CONVERSIONS_KEY] = obj.toString()
                }
            } catch (_: Exception) { }
        }
    }

    private fun clearPersistedConversion(id: String) {
        viewModelScope.launch {
            try {
                dataStore.edit { prefs ->
                    val cur = decodePendingConversions(prefs[PENDING_CONVERSIONS_KEY]).toMutableMap()
                    if (cur.remove(id) != null) {
                        val obj = org.json.JSONObject()
                        cur.forEach { (k, v) -> obj.put(k, v.name) }
                        prefs[PENDING_CONVERSIONS_KEY] = obj.toString()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun togglePlatform(platform: MediaDownloaderRepository.Platform) {
        val current = _ui.value.enabledPlatforms
        val next = if (platform in current) {
            if (current.size <= 1) {
                // Never leave zero platforms: tell the user why the tap did nothing.
                _ui.value = _ui.value.copy(downloadEnqueued = "Keep at least one platform enabled")
                return
            }
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

    fun deleteHistoryEntry(url: String) {
        val next = _history.value.filterNot { it.url == url }
        if (next.size == _history.value.size) return
        _history.value = next
        viewModelScope.launch {
            try {
                dataStore.edit { prefs -> prefs[HISTORY_KEY] = encodeHistory(next) }
            } catch (_: Exception) {
            }
        }
    }

    /** One-shot snackbar info without touching result/error state. */
    fun showInfo(message: String) {
        _ui.value = _ui.value.copy(downloadEnqueued = message)
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
        // MP4 covers every video row — favoriting it is a dead toggle, ignore.
        if (ext == "mp4") return
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

    /** Clears the current result but keeps the link for editing. */
    fun clearResult() {
        extractJob?.cancel()
        _ui.value = _ui.value.copy(
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

    fun clearAll() {
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
        try {
            WorkManager.getInstance(appContext).cancelWorkById(id)
        } catch (_: Exception) { }
        _ui.value = _ui.value.copy(downloadEnqueued = "Cancelling download…")
    }

    /** Re-enqueues a terminal social-media download with its original opaque asset handle. */
    fun retryDownload(info: WorkInfo) {
        if (info.state !in setOf(WorkInfo.State.FAILED, WorkInfo.State.CANCELLED)) return
        val label = _labels.value[info.id.toString()]
        val retryData = label?.retryData ?: retryDataFrom(info)
        if (SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD !in info.tags || retryData == null) {
            _ui.value = _ui.value.copy(error = "Reopen the original link to retry this download.")
            return
        }
        val req = OneTimeWorkRequestBuilder<SocialDownloadWorker>()
            .setInputData(retryData)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "social_retry_${info.id}_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            req,
        )
        val retryLabel = label ?: DownloadLabel(
            title = retryData.getString(SocialDownloadWorker.KEY_TITLE) ?: "Media",
            qualityLabel = retryData.getString(SocialDownloadWorker.KEY_FILE_NAME) ?: "Download",
            isAudio = retryData.getBoolean(SocialDownloadWorker.KEY_IS_AUDIO, false),
            thumbnailUrl = retryData.getString(SocialDownloadWorker.KEY_THUMBNAIL_URL),
            mediaKind = when {
                retryData.getString(SocialDownloadWorker.KEY_MIME_TYPE_INPUT)?.startsWith("image/") == true -> "image"
                retryData.getBoolean(SocialDownloadWorker.KEY_IS_AUDIO, false) -> "audio"
                else -> "video"
            },
        )
        rememberLabel(req.id, retryLabel.copy(retryData = retryData))
        _ui.value = _ui.value.copy(downloadEnqueued = "Retry started")
    }

    fun canRetryDownload(info: WorkInfo): Boolean =
        info.state in setOf(WorkInfo.State.FAILED, WorkInfo.State.CANCELLED) &&
            SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD in info.tags &&
            (_labels.value[info.id.toString()]?.retryData != null || retryDataFrom(info) != null)

    private fun retryDataFrom(info: WorkInfo): Data? {
        val output = info.outputData
        val extractionId = output.getString(SocialDownloadWorker.KEY_EXTRACTION_ID) ?: return null
        val assetId = output.getString(SocialDownloadWorker.KEY_ASSET_ID) ?: return null
        return Data.Builder()
            .putString(SocialDownloadWorker.KEY_EXTRACTION_ID, extractionId)
            .putString(SocialDownloadWorker.KEY_ASSET_ID, assetId)
            .putString(SocialDownloadWorker.KEY_FILE_NAME, output.getString(SocialDownloadWorker.KEY_FILE_NAME))
            .putString(SocialDownloadWorker.KEY_TITLE, output.getString(SocialDownloadWorker.KEY_TITLE))
            .putString(SocialDownloadWorker.KEY_MIME_TYPE_INPUT, output.getString(SocialDownloadWorker.KEY_MIME_TYPE_INPUT))
            .putBoolean(SocialDownloadWorker.KEY_IS_AUDIO, output.getBoolean(SocialDownloadWorker.KEY_IS_AUDIO, false))
            .build()
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
                ?: when (label?.mediaKind) {
                    "image" -> "image/*"
                    "audio" -> "audio/*"
                    else -> "video/*"
                }
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
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
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
            // Rebuild the likely file name with the same sanitizer used at enqueue time,
            // preserving unicode (titles like Arabic/Hindi must still resolve).
            val exts = when (label.mediaKind) {
                "image" -> listOf("jpg", "jpeg", "png", "webp", "gif")
                "audio" -> listOf("mp3", "m4a", "wav", "ogg", "flac", "opus", "aac")
                else -> listOf("mp4", "webm", "mkv")
            }
            for (ext in exts) {
                val guess = repository.downloadFileName(label.title, ext)
                uri = findInMediaStore(guess)
                if (uri != null) break
            }
        }
        return uri
    }

    fun extract() {
        val rawInput = _ui.value.url.trim()
        if (rawInput.isBlank()) {
            _ui.value = _ui.value.copy(error = "Paste a link first.")
            return
        }
        // Normalize http→https + trailing punctuation so pasted links just work.
        val normalized = repository.normalizePastedUrl(rawInput)
        val raw = normalized
        if (normalized != rawInput) {
            _ui.value = _ui.value.copy(url = normalized, detectedPlatform = repository.detectPlatform(normalized))
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
                localTitle = null, localThumbnail = null, localSourceUrl = null,
                localHeights = emptyList(), probingLocal = false, options = emptyList(),
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
        // Galleries contain independently downloadable photos, never an audio
        // track. Keep them usable even if the user prefers audio for this app.
        if (res.media_kind == "gallery") {
            return res.assets
                .filter { it.kind == "image" && it.id.isNotBlank() }
                .mapIndexed { index, f ->
                    QualityOption(
                        id = f.id,
                        label = "Photo ${index + 1}",
                        detail = f.filesize?.let { formatBytes(it) } ?: (f.ext ?: "image").uppercase(),
                        ext = (f.ext ?: "jpg").lowercase(),
                        kind = "image",
                    )
                }
                .take(20)
        }
        val directAudio = res.assets.filter { it.kind == "audio" && it.id.isNotBlank() }
            .take(3)
            .map { f ->
                QualityOption(
                    id = f.id,
                    label = f.acodec?.takeIf { it != "none" } ?: f.resolution?.takeIf { it != "unknown" } ?: "Audio",
                    detail = f.filesize?.let { formatBytes(it) } ?: (f.ext ?: "mp3"),
                    isAudio = true,
                    ext = (f.ext ?: "mp3").lowercase(),
                    kind = "audio",
                )
            }
        if (mode == DownloadMode.AUDIO) {
            val direct = directAudio.distinctBy { it.id }
            // Direct server audio + on-device conversions (m4a/wav/ogg/flac) so the
            // AudioFormats settings actually do something on remote platforms.
            val converted = converterAudioLadder(includeDirectMp3 = false)
                .filter { it.targetExt != null && it.targetExt != "mp3" }
            val list = (direct + converted).distinctBy { it.id }
            val withPrefs = platform?.let { applyFormatPrefs(it, list) } ?: list
            return withPrefs.take(10)
        }
        val out = mutableListOf<QualityOption>()
        res.assets.filter { it.kind == "video" && it.id.isNotBlank() }
            .take(8)
            .forEach { f ->
                val hasAudio = f.has_audio == true || (f.has_audio == null && f.acodec != null && f.acodec != "none")
                val h = f.height
                out += QualityOption(
                    id = f.id,
                    label = f.resolution?.takeIf { it != "unknown" } ?: "${h?.let { "${it}p" } ?: (f.ext?.uppercase() ?: "Video")}",
                    detail = listOfNotNull(
                        if (!hasAudio) "no audio" else null,
                        f.filesize?.let { formatBytes(it) },
                        if (h != null && h >= 1080) "HD" else null,
                    ).joinToString(" · ").ifBlank { f.ext ?: "mp4" },
                    isHd = (h ?: 0) >= 1080,
                    ext = (f.ext ?: "mp4").lowercase(),
                    kind = "video",
                )
            }
        res.assets.filter { it.kind == "image" && it.id.isNotBlank() }
            .forEachIndexed { index, f ->
                out += QualityOption(
                    id = f.id, label = "Photo ${index + 1}",
                    detail = f.filesize?.let { formatBytes(it) } ?: (f.ext ?: "image").uppercase(),
                    ext = (f.ext ?: "jpg").lowercase(), kind = "image",
                )
            }
        out += directAudio
        // BOTH must also offer converted audio (m4a/wav/ogg/flac) so AudioFormats
        // settings are visible outside Audio-only mode. Direct stays first.
        if (mode == DownloadMode.BOTH) {
            val converted = converterAudioLadder(includeDirectMp3 = false)
                .filter { it.targetExt != null && it.targetExt != "mp3" }
            out += converted
        }
        val withPrefs = platform?.let { applyFormatPrefs(it, out) } ?: out
        val filtered = if (mode == DownloadMode.VIDEO) withPrefs.filter { !it.isAudio && it.kind != "audio" } else withPrefs
        return filtered.distinctBy { it.id }.take(18)
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
            add(QualityOption("MP3", "MP3", "Direct • 320k", isAudio = true, ext = "mp3", kind = "audio"))
        } else {
            add(QualityOption("AUDIO_MP3", "MP3", "Converted on device • 320k", isAudio = true, ext = "mp3", kind = "audio", targetExt = "mp3", conversion = ConversionEngine.ConversionType.AUDIO_TO_MP3))
        }
        add(QualityOption("AUDIO_M4A", "M4A", "Converted on device • AAC", isAudio = true, ext = "m4a", kind = "audio", targetExt = "m4a", conversion = ConversionEngine.ConversionType.AUDIO_TO_M4A))
        add(QualityOption("AUDIO_WAV", "WAV", "Converted on device • lossless", isAudio = true, ext = "wav", kind = "audio", targetExt = "wav", conversion = ConversionEngine.ConversionType.AUDIO_TO_WAV))
        add(QualityOption("AUDIO_OGG", "OGG", "Converted on device • Vorbis", isAudio = true, ext = "ogg", kind = "audio", targetExt = "ogg", conversion = ConversionEngine.ConversionType.AUDIO_TO_OGG))
        add(QualityOption("AUDIO_FLAC", "FLAC", "Converted on device • lossless", isAudio = true, ext = "flac", kind = "audio", targetExt = "flac", conversion = ConversionEngine.ConversionType.AUDIO_TO_FLAC))
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
        val isBoth = mode == DownloadMode.BOTH
        // heights is now HONEST (only real playable heights, empty = probe failed).
        // Empty → unknown: offer the full ladder with "if available" notes (old UX),
        // default selection stays 720p. Non-empty → cap the ladder at the real max so
        // we never promise 1080p on a 360p-only video (the old 640x360 bug).
        if (heights.isEmpty()) {
            val ladder = buildList {
                add(QualityOption("1080p", "1080p", "Full HD • merged if available", isHd = true, ext = "mp4", kind = "video"))
                add(QualityOption("720p", "720p", "HD if available", ext = "mp4", kind = "video"))
                add(QualityOption("480p", "480p", "SD if available", ext = "mp4", kind = "video"))
                add(QualityOption("360p", "360p", "Low", ext = "mp4", kind = "video"))
                add(QualityOption("240p", "240p", "Data saver", ext = "mp4", kind = "video"))
                if (isBoth) {
                    add(QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true, ext = "mp3", kind = "audio"))
                    addAll(converterAudioLadder(includeDirectMp3 = false).filter { it.targetExt != null && it.targetExt != "mp3" })
                }
            }
            return applyFormatPrefs(platform, ladder)
        }
        val maxH = heights.maxOrNull() ?: 0
        val ladder = buildList {
            if (maxH >= 2160 || heights.any { it >= 2160 }) add(QualityOption("2160p", "2160p", "Ultra HD • merged", isHd = true, ext = "mp4", kind = "video"))
            if (maxH >= 1440 || heights.any { it >= 1440 }) add(QualityOption("1440p", "1440p", "Quad HD • merged", isHd = true, ext = "mp4", kind = "video"))
            if (maxH >= 1080 || heights.any { it in 721..1200 }) add(QualityOption("1080p", "1080p", "Full HD • merged", isHd = true, ext = "mp4", kind = "video"))
            if (maxH >= 720) add(QualityOption("720p", "720p", "HD", ext = "mp4", kind = "video"))
            if (maxH >= 480) add(QualityOption("480p", "480p", "SD", ext = "mp4", kind = "video"))
            // Always keep a playable low fallback + audio.
            add(QualityOption("360p", "360p", if (maxH < 480) "Best for this video" else "Low", ext = "mp4", kind = "video"))
            add(QualityOption("240p", "240p", "Data saver", ext = "mp4", kind = "video"))
            if (isBoth) {
                add(QualityOption("MP3", "MP3", "Audio only • 320k", isAudio = true, ext = "mp3", kind = "audio"))
                addAll(converterAudioLadder(includeDirectMp3 = false).filter { it.targetExt != null && it.targetExt != "mp3" })
            }
        }
        return applyFormatPrefs(platform, ladder)
    }

    /** Local rows carry no ext from the probe: video is always mp4, audio is mp3. */
    private fun List<QualityOption>.withVideoExt(): List<QualityOption> =
        map { opt ->
            when {
                opt.ext.isNotBlank() -> opt
                opt.isAudio || opt.kind == "audio" -> opt.copy(ext = opt.ext.ifBlank { "mp3" }, kind = "audio", isAudio = true)
                else -> opt.copy(ext = "mp4", kind = "video")
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
        // Blocked results must never fall through to the YouTube engine.
        val remote = s.remote
        if (remote != null && remote.blocked) {
            _ui.value = s.copy(error = remote.blocked_message ?: "This media is blocked and can't be downloaded.")
            return
        }
        // Remote path (TikTok / IG / remote YouTube)
        if (remote != null) {
            val extractionId = remote.id
            if (extractionId.isNullOrBlank()) {
                _ui.value = s.copy(error = "This result expired. Extract the link again.")
                return
            }
            val asset = remote.assets.firstOrNull { it.id == opt.id }
            if (asset == null) {
                _ui.value = s.copy(error = "That format is no longer available. Extract the link again.")
                return
            }
            val isAudio = asset.kind == "audio"
            val ext = asset.ext ?: if (isAudio) "mp3" else "mp4"
            val baseName = if (asset.kind == "image") "${remote.title ?: "photo"}-${asset.id}" else remote.title
            val fileName = repository.downloadFileName(baseName, ext)
            val inputData = workDataOf(
                SocialDownloadWorker.KEY_EXTRACTION_ID to extractionId,
                SocialDownloadWorker.KEY_ASSET_ID to asset.id,
                SocialDownloadWorker.KEY_FILE_NAME to fileName,
                SocialDownloadWorker.KEY_TITLE to (remote.title ?: "media"),
                SocialDownloadWorker.KEY_THUMBNAIL_URL to remote.thumbnail,
                SocialDownloadWorker.KEY_IS_AUDIO to isAudio,
                SocialDownloadWorker.KEY_MIME_TYPE_INPUT to asset.mime_type,
            )
            val req = OneTimeWorkRequestBuilder<SocialDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "social_download_${extractionId}_${asset.id}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                req,
            )
            rememberLabel(
                req.id,
                DownloadLabel(remote.title ?: "media", opt.label, isAudio, remote.thumbnail, asset.kind, inputData),
            )
            _ui.value = s.copy(downloadEnqueued = "Download started — ${opt.label}")
            return
        }
        // Local YouTube path (shared HD engine)
        val sourceUrl = (s.localSourceUrl ?: s.url.trim()).trim()
        if (sourceUrl.isBlank()) {
            _ui.value = s.copy(error = "Nothing to download yet — extract a link first.")
            return
        }
        val youTubeId = repository.extractYouTubeId(sourceUrl)
        if (youTubeId == null && repository.detectPlatform(sourceUrl) != MediaDownloaderRepository.Platform.YOUTUBE) {
            _ui.value = s.copy(error = "That format is no longer available. Extract the link again.")
            return
        }
        val title = s.localTitle ?: remote?.title ?: "YouTube video"
        val thumb = s.localThumbnail ?: remote?.thumbnail
        val ok = if (opt.id.equals("MP3", ignoreCase = true) || opt.isAudio) {
            enqueueMp3(sourceUrl, title, thumb) != null
        } else {
            enqueueVideo(sourceUrl, title, thumb, opt.id)
        }
        if (ok) {
            _ui.value = s.copy(downloadEnqueued = "Download started — ${opt.label}")
        } else {
            _ui.value = s.copy(error = "Could not start the download. Try again.")
        }
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
            val extractionId = remote.id ?: run {
                _ui.value = s.copy(error = "This result expired. Extract the link again.")
                return
            }
            val baseAsset = remote.assets.firstOrNull { it.kind == "audio" } ?: run {
                _ui.value = s.copy(error = "No downloadable audio format is available.")
                return
            }
            val title = remote.title ?: "media"
            val fileName = repository.downloadFileName(title, "mp3")
            val inputData = workDataOf(
                SocialDownloadWorker.KEY_EXTRACTION_ID to extractionId,
                SocialDownloadWorker.KEY_ASSET_ID to baseAsset.id,
                SocialDownloadWorker.KEY_FILE_NAME to fileName,
                SocialDownloadWorker.KEY_TITLE to title,
                SocialDownloadWorker.KEY_THUMBNAIL_URL to remote.thumbnail,
                SocialDownloadWorker.KEY_IS_AUDIO to true,
                SocialDownloadWorker.KEY_MIME_TYPE_INPUT to baseAsset.mime_type,
            )
            val req = OneTimeWorkRequestBuilder<SocialDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(SocialDownloadWorker.TAG_SOCIAL_DOWNLOAD)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "social_download_${extractionId}_${baseAsset.id}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                req,
            )
            pendingConversions[req.id.toString()] = conversion
            persistPendingConversion(req.id.toString(), conversion)
            rememberLabel(req.id, DownloadLabel(title, "${opt.label} • .$ext", true, remote.thumbnail, "audio", inputData))
            _ui.value = s.copy(downloadEnqueued = "Download started — converting to ${opt.label} next")
            return
        }
        if (remote != null && remote.blocked) {
            _ui.value = s.copy(error = remote.blocked_message ?: "This media is blocked and can't be downloaded.")
            return
        }
        val sourceUrl = (s.localSourceUrl ?: s.url.trim()).trim()
        if (sourceUrl.isBlank()) {
            _ui.value = s.copy(error = "Nothing to download yet — extract a link first.")
            return
        }
        val title = s.localTitle ?: remote?.title ?: "YouTube video"
        val thumb = s.localThumbnail ?: remote?.thumbnail
        val baseId = enqueueMp3(sourceUrl, title, thumb)
        if (baseId != null) {
            pendingConversions[baseId.toString()] = conversion
            persistPendingConversion(baseId.toString(), conversion)
            rememberLabel(baseId, DownloadLabel(title, "${opt.label} • .$ext", true, thumb, "audio"))
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
                clearPersistedConversion(info.id.toString())
                val label = _labels.value[info.id.toString()]
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val uri = resolveDownloadUri(info, label)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (uri != null) {
                            startConversion(conversion, uri)
                        } else {
                            _ui.value = _ui.value.copy(downloadEnqueued = appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_FileGone))
                        }
                    }
                }
            }
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                pendingConversions.remove(info.id.toString())
                clearPersistedConversion(info.id.toString())
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
            val msg = try {
                appContext.getString(com.frerox.toolz.R.string.st_MediaDownloader_ConvertFailed)
            } catch (_: Exception) {
                "Couldn't start the audio conversion."
            }
            _ui.value = _ui.value.copy(downloadEnqueued = msg)
        }
    }

    private fun enqueueVideo(sourceUrl: String, title: String, thumb: String?, quality: String): Boolean {
        return try {
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
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(VideoDownloadWorker.TAG_VIDEO_DOWNLOAD)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                "video_download_${title.hashCode()}_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                req,
            )
            rememberLabel(req.id, DownloadLabel(title, quality, isAudio = quality.equals("MP3", ignoreCase = true), thumbnailUrl = thumb))
            true
        } catch (e: Exception) {
            android.util.Log.e("MediaDownloaderVM", "Video enqueue failed", e)
            false
        }
    }

    private fun enqueueMp3(sourceUrl: String, title: String, thumb: String?): java.util.UUID? {
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
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
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
            if (enqueueVideo(sourceUrl, title, thumb, "MP3")) null else null
        }
    }

    private fun toast(context: Context, msg: String) {
        // Single feedback surface is the snackbar (downloadEnqueued/conversionStarted).
        // Keep Toast only as a last resort for contexts without a snackbar observer.
        try {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    fun downloadProgress(info: WorkInfo): Float =
        info.progress.getFloat(SocialDownloadWorker.KEY_PROGRESS, Float.NaN).let { social ->
            if (!social.isNaN()) return social.coerceIn(0f, 1f)
            info.progress.getFloat(VideoDownloadWorker.KEY_PROGRESS, Float.NaN).let { video ->
                if (!video.isNaN()) return video.coerceIn(0f, 1f)
                val mp3 = info.progress.getFloat(YouTubeMp3DownloadWorker.KEY_PROGRESS, Float.NaN)
                if (!mp3.isNaN()) return mp3.coerceIn(0f, 1f)
                Float.NaN
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
