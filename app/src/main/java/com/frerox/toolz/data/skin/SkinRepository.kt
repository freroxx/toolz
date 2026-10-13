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

    suspend fun submit(name: String, skin: SkinDto): Result<String> {
        val clean = name.trim().take(32)
        if (clean.length < 3) return Result.failure(IllegalArgumentException("Name too short"))
        val payload = skin.copy(id = "", name = clean, status = "draft")
        payload.validate()?.let { return Result.failure(IllegalArgumentException(it)) }
        return try {
            val res = skinApi.submitSkin(SkinSubmitRequest(name = clean, skin = payload))
            if (res.ok && !res.id.isNullOrBlank()) Result.success(res.id)
            else Result.failure(IllegalStateException(res.error ?: "Submit failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Build a SkinDto snapshot from current local appearance settings. */
    suspend fun snapshotCurrent(name: String): SkinDto {
        val theme = try { settingsRepository.themeMode.first() } catch (_: Exception) { "SYSTEM" }
        val dyn = try { settingsRepository.dynamicColor.first() } catch (_: Exception) { true }
        val pri = try { settingsRepository.customPrimaryColor.first()?.toLong()?.and(0xFFFFFFFFL) } catch (_: Exception) { null }
        val sec = try { settingsRepository.customSecondaryColor.first()?.toLong()?.and(0xFFFFFFFFL) } catch (_: Exception) { null }
        val grad = try { settingsRepository.backgroundGradientEnabled.first() } catch (_: Exception) { true }
        val iconId = try { settingsRepository.appIconPresetId.first() } catch (_: Exception) { IconCatalog.DEFAULT_ID }
        val adapt = try { settingsRepository.appIconAdaptTheme.first() } catch (_: Exception) { true }
        val follow = try { settingsRepository.appIconFollowDynamic.first() } catch (_: Exception) { true }
        val shape = try { settingsRepository.appIconShape.first() } catch (_: Exception) { "HEXAGON" }
        return SkinDto(
            id = "",
            name = name.trim().take(32),
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
        settingsRepository.applySkin(
            themeMode = skin.themeMode,
            dynamicColor = skin.dynamicColor,
            primary = skin.primary?.toInt(),
            secondary = skin.secondary?.toLong()?.toInt() ?: skin.secondary?.toInt(),
            gradient = skin.gradient,
            iconPresetId = skin.iconPresetId,
            iconAdaptTheme = skin.iconAdaptTheme,
            iconFollowDynamic = skin.iconFollowDynamic,
            iconShape = skin.iconShape,
            activeSkinId = skin.id,
        )
    }
}
