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

package com.frerox.toolz.data.ai

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiCatalogRepository"

/**
 * Server-driven AI model catalog.
 *
 * Source of truth: `GET https://toolz-app.vercel.app/api/models`
 * (hand-maintained in `toolz-website/api/models.ts`).
 *
 * Flow:
 *  1. [warmup] (app start): install last cached payload instantly (if any),
 *     then [refresh] in the same call when the cache is stale.
 *  2. [refresh]: fetch → version-guard → persist → install into
 *     [AiSettingsHelper]. Any failure keeps the previous catalog; with no
 *     cache at all the helper's bundled tables apply (identical behavior
 *     to before this feature existed).
 *
 * Version guard: a payload is installed/persisted only when
 * `remote.version >= installed.version`, so a rolled-back server deploy
 * can never downgrade clients.
 */
@Singleton
class AiCatalogRepository @Inject constructor(
    private val api: AiCatalogApi,
    private val settingsManager: AiSettingsManager,
    moshi: Moshi,
) {
    companion object {
        /** Catalog TTL — models churn fast, so 24h (not the 15d specs cadence). */
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }

    private val adapter = moshi.adapter(AiCatalogResponse::class.java)

    private val _installedVersion = MutableStateFlow<Int?>(null)
    /** Currently active catalog version, or null when on bundled tables. */
    val installedVersion: StateFlow<Int?> = _installedVersion.asStateFlow()

    private val _catalogUpdatedAt = MutableStateFlow<String?>(null)
    /** Server `updatedAt` date string for the Settings "updated …" row. */
    val catalogUpdatedAt: StateFlow<String?> = _catalogUpdatedAt.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * App-start entry point: install cache synchronously, refresh when stale.
     * Never throws.
     */
    suspend fun warmup() {
        installCached()
        if (isCacheStale()) refresh()
    }

    /**
     * Fetch the catalog unless the cache is fresh (or [force] is true).
     * @return true if a catalog (fresh or cached) is now active.
     */
    suspend fun refresh(force: Boolean = false): Boolean {
        if (!force && !isCacheStale() && _installedVersion.value != null) return true
        if (_isRefreshing.value) return _installedVersion.value != null
        _isRefreshing.value = true
        try {
            val remote = try {
                api.getModelCatalog()
            } catch (e: Exception) {
                Log.w(TAG, "Catalog fetch failed; keeping previous", e)
                return _installedVersion.value != null
            }
            if (remote.providers.isEmpty()) {
                Log.w(TAG, "Catalog payload has no providers; ignoring")
                return _installedVersion.value != null
            }
            val current = _installedVersion.value ?: 0
            if (remote.version < current) {
                Log.w(TAG, "Ignoring catalog v${remote.version} (installed v$current — server rolled back?)")
                return true
            }
            val json = try {
                adapter.toJson(remote)
            } catch (e: Exception) {
                Log.w(TAG, "Catalog serialize failed", e)
                null
            }
            if (json != null) settingsManager.saveCachedCatalog(json, remote.version)
            install(remote)
            return true
        } finally {
            _isRefreshing.value = false
        }
    }

    private fun isCacheStale(): Boolean {
        if (_installedVersion.value == null) return true
        val age = System.currentTimeMillis() - settingsManager.getCachedCatalogTimestamp()
        return age >= CACHE_TTL_MS
    }

    private fun installCached() {
        val json = settingsManager.getCachedCatalogJson() ?: return
        val catalog = try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            Log.w(TAG, "Cached catalog corrupt; clearing", e)
            settingsManager.clearCachedCatalog()
            return
        } ?: return
        if (catalog.providers.isEmpty()) {
            settingsManager.clearCachedCatalog()
            return
        }
        install(catalog)
    }

    private fun install(catalog: AiCatalogResponse) {
        AiSettingsHelper.installCatalog(catalog)
        _installedVersion.value = catalog.version
        _catalogUpdatedAt.value = catalog.updatedAt.takeIf { it.isNotBlank() }
    }
}
