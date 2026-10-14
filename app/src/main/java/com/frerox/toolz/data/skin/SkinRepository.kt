/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.skin

import android.content.Context
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Community skins repository.
 * Merges bundled legacy presets (always available offline) with server feed.
 * Fail-open: network errors keep cache + legacy.
 */
@Singleton
class SkinRepository @Inject constructor(
    private val skinApi: SkinApi,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    companion object {
        const val SYNC_STALE_MS = 6 * 60 * 60 * 1000L
    }

    private val mutex = Mutex()
    private val _skins = MutableStateFlow<List<SkinDto>>(LegacySkins.ALL)
    val skins: StateFlow<List<SkinDto>> = _skins.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _mySubmissions = MutableStateFlow<List<MySubmission>>(emptyList())
    val mySubmissions: StateFlow<List<MySubmission>> = _mySubmissions.asStateFlow()

    /** Server status per my submission id: published | archived | removed. Absent = pending review. */
    private val _submissionStatuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val submissionStatuses: StateFlow<Map<String, String>> = _submissionStatuses.asStateFlow()

    suspend fun refreshMySubmissions() {
        try {
            val raw = settingsRepository.mySkinSubmissionsJson.first()
            _mySubmissions.value = if (raw.isNullOrBlank()) emptyList()
            else json.decodeFromString(ListSerializer(MySubmission.serializer()), raw).take(50)
        } catch (_: Exception) { }
    }

    private suspend fun persistMySubmissions(list: List<MySubmission>) {
        _mySubmissions.value = list.take(50)
        try {
            settingsRepository.setMySkinSubmissionsJson(
                json.encodeToString(ListSerializer(MySubmission.serializer()), list.take(50))
            )
        } catch (_: Exception) { }
    }

    /** Ask the server for the review status of my submissions. Fail-open (keeps old). */
    suspend fun refreshSubmissionStatuses() {
        val ids = _mySubmissions.value.map { it.id }.filter { it.isNotBlank() }.take(50)
        if (ids.isEmpty()) return
        try {
            val feed = skinApi.getSkinStatuses(ids.joinToString(","))
            val map = _submissionStatuses.value.toMutableMap()
            feed.statuses.forEach { map[it.id] = it.status }
            feed.removed.forEach { map[it] = "removed" }
            _submissionStatuses.value = map
        } catch (_: Exception) { }
    }

    fun currentVersion(): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
    } catch (_: Exception) { "0.0.0" }

    suspend fun syncIfStale(force: Boolean = false): Boolean {
        return try {
            mutex.withLock {
                val last = try { settingsRepository.skinsLastSync.first() } catch (_: Exception) { 0L }
                if (!force && System.currentTimeMillis() - last < SYNC_STALE_MS && _skins.value.size > LegacySkins.ALL.size) {
                    return@withLock true
                }
                _syncing.value = true
                try {
                    val feed = skinApi.getSkins(currentVersion())
                    if (feed.degraded) {
                        loadCacheIntoState()
                        return@withLock false
                    }
                    val remote = feed.skins.filter { it.status == "published" && it.validate() == null }
                    val removed = feed.removedIds.toSet()
                    val merged = (LegacySkins.ALL + remote)
                        .filterNot { removed.contains(it.id) }
                        .distinctBy { it.id }
                    _skins.value = merged
                    persistCache(remote)
                    try {
                        settingsRepository.setSkinsLastSync(System.currentTimeMillis())
                        if (feed.feedVersion >= 0) settingsRepository.setSkinsFeedVersion(feed.feedVersion)
                    } catch (_: Exception) { }
                    true
                } finally {
                    _syncing.value = false
                }
            }
        } catch (_: Exception) {
            try { loadCacheIntoState() } catch (_: Exception) { }
            _syncing.value = false
            false
        }
    }

    private suspend fun persistCache(remote: List<SkinDto>) {
        try {
            val raw = json.encodeToString(ListSerializer(SkinDto.serializer()), remote.take(200))
            settingsRepository.setSkinsCacheJson(raw)
        } catch (_: Exception) { }
    }

    private suspend fun loadCacheIntoState() {
        try {
            val raw = settingsRepository.skinsCacheJson.first()
            if (raw.isNullOrBlank()) {
                if (_skins.value.isEmpty()) _skins.value = LegacySkins.ALL
                return
            }
            val cached = json.decodeFromString(ListSerializer(SkinDto.serializer()), raw)
                .filter { it.validate() == null }
                .take(200)
            _skins.value = (LegacySkins.ALL + cached).distinctBy { it.id }
        } catch (_: Exception) {
            if (_skins.value.isEmpty()) _skins.value = LegacySkins.ALL
        }
    }

    suspend fun submit(name: String, author: String, skin: SkinDto): Result<String> {
        val clean = name.trim().take(32)
        if (clean.length < 3) return Result.failure(IllegalArgumentException("Name too short"))
        val cleanAuthor = author.trim().take(32).ifBlank { null }
        val payload = skin.copy(id = "", name = clean, author = cleanAuthor, status = "draft")
        payload.validate()?.let { return Result.failure(IllegalArgumentException(it)) }
        return try {
            val res = skinApi.submitSkin(SkinSubmitRequest(name = clean, skin = payload))
            if (res.ok && !res.id.isNullOrBlank()) {
                val id = res.id
                persistMySubmissions((listOf(MySubmission(id = id, name = clean, ts = System.currentTimeMillis())) + _mySubmissions.value).distinctBy { it.id })
                Result.success(id)
            } else Result.failure(IllegalStateException(res.error ?: "Submit failed"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(serverMessage(e), e))
        }
    }

    /** Prefer the server's JSON error over a bare "HTTP 400". */
    private fun serverMessage(e: Exception): String {
        val http = e as? retrofit2.HttpException
            ?: return e.message ?: "Something went wrong. Check your connection and try again."
        return try {
            val raw = http.response()?.errorBody()?.string()
            val msg = org.json.JSONObject(raw ?: "").optString("error").ifBlank { null }
                ?: org.json.JSONObject(raw ?: "").optString("message").ifBlank { null }
            (msg ?: "Server error (${http.code()})").take(200)
        } catch (_: Exception) {
            if (http.code() == 404) "Server route not found (404). Update Toolz and try again."
            else "Server error (${http.code()})"
        }
    }

    /** Build a SkinDto snapshot from current local appearance settings. */
    suspend fun snapshotCurrent(name: String, author: String): SkinDto {
        val rawTheme = try { settingsRepository.themeMode.first() } catch (_: Exception) { "SYSTEM" }
        // SYSTEM is a first-class themeMode: uppercased + whitelisted so share never
        // fails validation just because the theme follows the system.
        val theme = rawTheme.uppercase().trim().take(16).let { if (it in setOf("SYSTEM", "LIGHT", "DARK")) it else "SYSTEM" }
        val dyn = try { settingsRepository.dynamicColor.first() } catch (_: Exception) { true }
        val pri = try { settingsRepository.customPrimaryColor.first()?.toLong()?.and(0xFFFFFFFFL) } catch (_: Exception) { null }
        val sec = try { settingsRepository.customSecondaryColor.first()?.toLong()?.and(0xFFFFFFFFL) } catch (_: Exception) { null }
        val grad = try { settingsRepository.backgroundGradientEnabled.first() } catch (_: Exception) { true }
        val rawIcon = try { settingsRepository.appIconPresetId.first() } catch (_: Exception) { IconCatalog.DEFAULT_ID }
        val iconId = IconCatalog.find(rawIcon).id
        val adapt = try { settingsRepository.appIconAdaptTheme.first() } catch (_: Exception) { true }
        val follow = try { settingsRepository.appIconFollowDynamic.first() } catch (_: Exception) { true }
        val rawShape = try { settingsRepository.appIconShape.first() } catch (_: Exception) { "HEXAGON" }
        // Canonical OCTAGON | CIRCLE; legacy HEXAGON/PILL parse in (PILL was removed).
        val shape = IconShape.parse(rawShape)?.name ?: "OCTAGON"
        return SkinDto(
            id = "",
            name = name.trim().take(32),
            author = author.trim().take(32).ifBlank { null },
            themeMode = theme,
            dynamicColor = dyn,
            primary = pri,
            secondary = sec,
            gradient = grad,
            iconPresetId = iconId,
            iconAdaptTheme = adapt,
            iconFollowDynamic = follow,
            iconShape = shape,
        )
    }

    /** Apply a skin atomically to SettingsRepository. */
    suspend fun apply(skin: SkinDto) {
        // Normalize wire values so legacy server skins (HEXAGON/PILL) land canonical.
        val shape = IconShape.parse(skin.iconShape)?.name ?: "OCTAGON"
        val theme = skin.themeMode.uppercase().take(16).let { if (it in setOf("SYSTEM", "LIGHT", "DARK")) it else "SYSTEM" }
        settingsRepository.applySkin(
            themeMode = theme,
            dynamicColor = skin.dynamicColor,
            primary = skin.primary?.toInt(),
            secondary = skin.secondary?.toInt(),
            gradient = skin.gradient,
            iconPresetId = IconCatalog.find(skin.iconPresetId).id,
            iconAdaptTheme = skin.iconAdaptTheme,
            iconFollowDynamic = skin.iconFollowDynamic,
            iconShape = shape,
            activeSkinId = skin.id,
        )
        try { settingsRepository.pushRecentSkinId(skin.id) } catch (_: Exception) { }
    }
}
