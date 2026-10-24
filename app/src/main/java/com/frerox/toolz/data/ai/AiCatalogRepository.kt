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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiCatalogRepository"

/**
 * Emitted once when a server fetch installs a genuinely newer catalog than the
 * one currently active, so the UI can show a one-shot "models updated" notice.
 *
 * @param version newly installed catalog version.
 * @param newModelCount how many model ids appeared compared with the previous
 *   catalog (0 when the lineup changed in other ways, e.g. retirements only).
 */
data class CatalogUpdateNotice(
    val version: Int,
    val newModelCount: Int,
)

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

        /**
         * Startup/path coalescing window for live refreshes. The Application
         * warmup and the Loading screen both run seconds apart on cold start;
         * this prevents them from issuing two identical network requests.
         */
        private const val LIVE_REFRESH_COALESCE_MS = 5_000L
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
     * One-shot "a newer catalog was installed" event. Kept as state (not a
     * SharedFlow) so it survives the Loading screen finishing before the user
     * opens the AI Assistant, which is where the notice is displayed.
     */
    private val _pendingUpdate = MutableStateFlow<CatalogUpdateNotice?>(null)
    val pendingUpdate: StateFlow<CatalogUpdateNotice?> = _pendingUpdate.asStateFlow()

    private val refreshMutex = Mutex()

    private var installedCatalog: AiCatalogResponse? = null
    private var lastSuccessfulFetchAt = 0L

    /** True when the user allows automatic catalog refreshes. */
    fun isLiveUpdatesEnabled(): Boolean = settingsManager.isLiveCatalogUpdatesEnabled()

    /**
     * Refresh only when the user has "Live model catalog updates" enabled.
     * Used by the Loading screen and AI Assistant open paths.
     *
     * [force] fetches even inside the 24h TTL but coalesces bursts from the
     * startup path so app warmup + loading don't double-fetch.
     */
    suspend fun refreshLive(force: Boolean = true): Boolean {
        if (!isLiveUpdatesEnabled()) return _installedVersion.value != null
        return refresh(force = force, minIntervalMs = if (force) LIVE_REFRESH_COALESCE_MS else 0L)
    }

    /** Clear the notice after the UI has shown it (version-scoped, race-safe). */
    fun acknowledgeUpdate(notice: CatalogUpdateNotice) {
        _pendingUpdate.compareAndSet(notice, null)
    }

    /**
     * App-start entry point: install cache synchronously, refresh when stale.
     * Never throws. Network fetch is bounded to 10s so startup never hangs.
     */
    suspend fun warmup() {
        installCached()
        if (isCacheStale()) {
            withTimeoutOrNull(10_000L) { refresh() }
        }
    }

    /**
     * Fetch the catalog unless the cache is fresh (or [force] is true).
     * @param minIntervalMs skip a forced fetch when a successful one happened
     *   more recently than this (burst coalescing for launch paths).
     * @return true if a catalog (fresh or cached) is now active.
     */
    suspend fun refresh(force: Boolean = false, minIntervalMs: Long = 0L): Boolean = refreshMutex.withLock {
        if (minIntervalMs > 0L &&
            System.currentTimeMillis() - lastSuccessfulFetchAt < minIntervalMs
        ) {
            return _installedVersion.value != null
        }
        if (!force && !isCacheStaleLocked() && _installedVersion.value != null) return true
        if (_isRefreshing.value) return _installedVersion.value != null
        _isRefreshing.value = true
        try {
            val remote = try {
                withTimeoutOrNull(10_000L) { api.getModelCatalog() } ?: run {
                    Log.w(TAG, "Catalog fetch timed out; keeping previous")
                    return _installedVersion.value != null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Catalog fetch failed; keeping previous", e)
                return _installedVersion.value != null
            }
            if (remote.providers.isEmpty()) {
                Log.w(TAG, "Catalog payload has no providers; ignoring")
                return _installedVersion.value != null
            }
            if (!isTrustedCatalog(remote)) {
                Log.w(TAG, "Catalog rejected: untrusted chatCompletionUrl")
                return _installedVersion.value != null
            }
            val previous = installedCatalog
            val current = _installedVersion.value ?: 0
            if (remote.version < current) {
                Log.w(TAG, "Ignoring catalog v${remote.version} (installed v$current — server rolled back?)")
                return true
            }
            val isNewer = _installedVersion.value != null && remote.version > current
            val json = try {
                adapter.toJson(remote)
            } catch (e: Exception) {
                Log.w(TAG, "Catalog serialize failed", e)
                null
            }
            if (json != null) settingsManager.saveCachedCatalog(json, remote.version)
            install(remote)
            lastSuccessfulFetchAt = System.currentTimeMillis()
            if (isNewer) {
                _pendingUpdate.value = CatalogUpdateNotice(
                    version = remote.version,
                    newModelCount = countNewModels(previous, remote),
                )
            }
            return true
        } finally {
            _isRefreshing.value = false
        }
    }

    /** Count model ids that exist in [new] but not in the previously active catalog. */
    private fun countNewModels(old: AiCatalogResponse?, new: AiCatalogResponse): Int {
        if (old == null) return 0
        val oldIds = old.providers
            .flatMap { it.models + it.freeModels }
            .toHashSet()
        return new.providers
            .flatMap { it.models + it.freeModels }
            .distinct()
            .count { it !in oldIds }
    }

    private fun isCacheStale(): Boolean {
        if (_installedVersion.value == null) return true
        val age = System.currentTimeMillis() - settingsManager.getCachedCatalogTimestamp()
        // Clock skew (negative age) must not force constant refresh.
        return age.coerceAtLeast(0L) >= CACHE_TTL_MS
    }

    private fun isCacheStaleLocked(): Boolean = isCacheStale()

    /**
     * Catalog poisoning guard: chatCompletionUrl is where Bearer keys are sent.
     * Reject non-https or non-allowlisted hosts so a compromised endpoint
     * can't exfiltrate keys.
     */
    private fun isTrustedCatalog(catalog: AiCatalogResponse): Boolean {
        val allowedHosts = setOf(
            "api.openai.com", "api.groq.com", "api.deepseek.com", "openrouter.ai",
            "opencode.ai", "api.anthropic.com", "generativelanguage.googleapis.com"
        )
        return catalog.providers.all { p ->
            val raw = p.chatCompletionUrl?.takeIf { it.isNotBlank() } ?: return@all true
            try {
                val uri = java.net.URI(raw.trim())
                val schemeOk = uri.scheme?.lowercase(java.util.Locale.ROOT) == "https"
                val host = uri.host?.lowercase(java.util.Locale.ROOT).orEmpty()
                schemeOk && (allowedHosts.any { host == it || host.endsWith(".$it") })
            } catch (_: Exception) { false }
        }
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
        installedCatalog = catalog
        _installedVersion.value = catalog.version
        _catalogUpdatedAt.value = catalog.updatedAt.takeIf { it.isNotBlank() }
    }
}
