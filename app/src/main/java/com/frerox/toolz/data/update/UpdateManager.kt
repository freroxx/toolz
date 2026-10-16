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

package com.frerox.toolz.data.update

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Orchestrates check → download → install as a single [state] machine.
 *
 * Persistence uses only existing [SettingsRepository] APIs
 * ([SettingsRepository.setAvailableUpdate],
 * [SettingsRepository.setDownloadedApkPath],
 * [SettingsRepository.setLastUpdateCheck]); skip-version and
 * notify-dedup live in private SharedPreferences so no DataStore
 * migration is needed.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller,
    private val updateService: UpdateService,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val NOTIF_PREFS = "toolz_update_notifs"
        private const val KEY_NOTIFIED_VERSION = "notified_version"
        private const val SKIP_PREFS = "toolz_update_skip"
        private const val KEY_SKIPPED_VERSION = "skipped_version"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateManagerState>(UpdateManagerState.Idle)
    val state: StateFlow<UpdateManagerState> = _state.asStateFlow()

    val pending: StateFlow<PendingUpdate?> = _state
        .map { s ->
            when (s) {
                is UpdateManagerState.Available -> s.update
                is UpdateManagerState.Downloading -> s.update
                is UpdateManagerState.ReadyToInstall -> s.update
                is UpdateManagerState.Installing -> s.update
                else -> null
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val cancelFlag = AtomicBoolean(false)
    private var downloadJob: Job? = null
    private var lastProgressAt = 0L
    private var lastProgressPct = -1

    fun currentPendingOrNull(): PendingUpdate? = when (val s = _state.value) {
        is UpdateManagerState.Available -> s.update
        is UpdateManagerState.Downloading -> s.update
        is UpdateManagerState.ReadyToInstall -> s.update
        is UpdateManagerState.Installing -> s.update
        else -> null
    }

    /**
     * Check for updates. Returns the pending update, or null when
     * up-to-date / skipped / error. Concurrent check/download calls
     * return the in-flight pending update instead of double-fetching.
     */
    suspend fun check(showNotification: Boolean = false, force: Boolean = false): PendingUpdate? {
        val current = _state.value
        if (current is UpdateManagerState.Checking || current is UpdateManagerState.Downloading) {
            return currentPendingOrNull()
        }
        _state.value = UpdateManagerState.Checking
        return try {
            val result = updateRepository.checkForUpdates(showNotification = false)
            settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
            when (result) {
                is UpdateCheckResult.NewUpdate -> {
                    val update = enrichPending(result)
                    val skipped = skippedVersion()
                    if (!force && !update.isCritical && skipped == update.version) {
                        _state.value = UpdateManagerState.UpToDate
                        return null
                    }
                    settingsRepository.setAvailableUpdate(
                        update.version, update.changelog, update.downloadUrl
                    )
                    _state.value = UpdateManagerState.Available(update)
                    if (showNotification && shouldNotify(update.version)) {
                        UpdateNotifications.showAvailable(context, update)
                        markNotified(update.version)
                    }
                    update
                }
                is UpdateCheckResult.UpToDate -> {
                    _state.value = UpdateManagerState.UpToDate
                    null
                }
                is UpdateCheckResult.Error -> {
                    _state.value = UpdateManagerState.Error(result.message, retryable = true)
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "check failed: ${e.message}")
            _state.value = UpdateManagerState.Error(e.message ?: "Update check failed", retryable = true)
            null
        }
    }

    /**
     * Best-effort enrichment: when the signed manifest carries this
     * exact version, take versionCode/size/criticality/abi from it
     * (source="manifest"); otherwise fall back to the GitHub result
     * with versionCode = installed + 1 (source="github").
     */
    private suspend fun enrichPending(result: UpdateCheckResult.NewUpdate): PendingUpdate {
        val preferredAbi = try {
            settingsRepository.preferredAbi.first()
        } catch (_: Exception) {
            "AUTO"
        }
        val deviceAbi = preferredAbi.takeIf { it != "AUTO" }
            ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        try {
            val res = updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
            val manifest = if (res.isSuccessful) res.body() else null
            if (manifest != null && manifest.versionName == result.version) {
                val best = manifest.releases?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
                return PendingUpdate(
                    version = result.version,
                    versionCode = manifest.versionCode,
                    changelog = manifest.changelog ?: result.changelog,
                    downloadUrl = best?.downloadUrl ?: result.downloadUrl,
                    sizeBytes = best?.effectiveSizeBytes,
                    sha256 = best?.sha256,
                    isCritical = manifest.isCritical ?: result.isCritical,
                    abi = best?.abi ?: deviceAbi,
                    source = "manifest"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "manifest enrich failed, github fallback: ${e.message}")
        }
        val nextCode = try {
            (updateRepository.getCurrentVersionCode() + 1).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            1
        }
        return PendingUpdate(
            version = result.version,
            versionCode = nextCode,
            changelog = result.changelog,
            downloadUrl = result.downloadUrl,
            sizeBytes = null,
            sha256 = null,
            isCritical = result.isCritical,
            abi = deviceAbi,
            source = "github"
        )
    }

    /**
     * Start (or restart) the download. Non-suspending: progress flows
     * through [state]. On success the APK is staged as ReadyToInstall
     * and, when [autoInstall] and the unknown-sources gate allows it,
     * [installReady] is chained automatically.
     */
    fun download(update: PendingUpdate? = null, autoInstall: Boolean = true) {
        val target = update ?: currentPendingOrNull() ?: return
        downloadJob?.cancel()
        cancelFlag.set(false)
        lastProgressAt = 0L
        lastProgressPct = -1
        val dest = UpdateDownloader.defaultDestFile(context, target.version)
        _state.value = UpdateManagerState.Downloading(
            update = target,
            bytesDownloaded = 0L,
            totalBytes = target.sizeBytes ?: 0L,
            speedBps = 0L,
            progress = 0f
        )
        downloadJob = scope.launch {
            val result = downloader.download(
                url = target.downloadUrl,
                destFile = dest,
                expectedSize = target.sizeBytes,
                expectedSha256 = target.sha256,
                onProgress = { downloaded, total, speedBps ->
                    if (!cancelFlag.get()) {
                        val pct = if (total > 0) (downloaded * 100f / total).coerceIn(0f, 100f) else 0f
                        _state.value = UpdateManagerState.Downloading(
                            target, downloaded, total, speedBps, pct
                        )
                        val pctInt = pct.toInt()
                        val now = System.currentTimeMillis()
                        if (NotificationHelper.shouldPublishProgress(
                                lastProgressAt, lastProgressPct, now, pctInt
                            )
                        ) {
                            lastProgressAt = now
                            lastProgressPct = pctInt
                            UpdateNotifications.showProgress(
                                context, target, pctInt, downloaded, total, speedBps
                            )
                        }
                    }
                },
                isCancelled = { cancelFlag.get() }
            )
            when (result) {
                is UpdateDownloader.DownloadResult.Success -> {
                    if (!result.file.exists()) {
                        _state.value = UpdateManagerState.Error("Downloaded file missing", retryable = true)
                        UpdateNotifications.showError(context, "Downloaded file missing")
                        return@launch
                    }
                    settingsRepository.setDownloadedApkPath(result.file.absolutePath)
                    _state.value = UpdateManagerState.ReadyToInstall(target, result.file)
                    UpdateNotifications.showReady(context, target, result.file)
                    if (autoInstall && installer.canInstall()) {
                        installReady()
                    }
                }
                is UpdateDownloader.DownloadResult.Error -> {
                    if (result.message == "Cancelled" || cancelFlag.get()) {
                        _state.value = UpdateManagerState.Available(target)
                    } else {
                        _state.value = UpdateManagerState.Error(result.message, retryable = true)
                        UpdateNotifications.showError(context, result.message)
                    }
                }
            }
        }
    }

    /** Signal the in-flight download to stop; state returns to Available. */
    fun cancelDownload() {
        cancelFlag.set(true)
        currentPendingOrNull()?.let { target ->
            val s = _state.value
            if (s is UpdateManagerState.Downloading && s.update == target) {
                _state.value = UpdateManagerState.Available(target)
            }
        }
    }

    /**
     * Launch the installer for the staged APK. Session-based installs
     * report back through UpdateReceiver → [onInstallSuccess]/[onInstallFailure].
     */
    fun installReady() {
        val cur = _state.value
        if (cur !is UpdateManagerState.ReadyToInstall) return
        if (!cur.apkFile.exists()) {
            _state.value = UpdateManagerState.Error("APK file no longer exists", retryable = true)
            return
        }
        _state.value = UpdateManagerState.Installing(cur.update)
        when (installer.install(cur.apkFile)) {
            is InstallLaunch.SessionStarted,
            is InstallLaunch.ViewIntentStarted -> {
                // Final outcome arrives via the status broadcast / system UI.
            }
            is InstallLaunch.NeedsPermission -> {
                _state.value = UpdateManagerState.ReadyToInstall(cur.update, cur.apkFile)
            }
        }
    }

    fun needsInstallPermission(): Boolean = !installer.canInstall()

    fun openInstallSettings(activityContext: Context) {
        installer.openInstallSettings(activityContext)
    }

    /** Called by UpdateReceiver on STATUS_SUCCESS: clear state, notify, prune. */
    fun onInstallSuccess(version: String) {
        scope.launch {
            try {
                settingsRepository.setAvailableUpdate(null, null, null)
                settingsRepository.setDownloadedApkPath(null)
            } catch (e: Exception) {
                Log.w(TAG, "clear update prefs failed: ${e.message}")
            }
            _state.value = UpdateManagerState.Installed
            UpdateNotifications.cancelAll(context)
            UpdateNotifications.showInstalled(context, version)
            try {
                UpdateDownloader.pruneCache(context, keep = null)
            } catch (e: Exception) {
                Log.w(TAG, "prune failed: ${e.message}")
            }
        }
    }

    fun onInstallFailure(msg: String) {
        _state.value = UpdateManagerState.Error(msg, retryable = true)
        UpdateNotifications.showError(context, msg)
    }

    /** "Skip this version": persist, go Idle, clear the available row. */
    fun skipVersion(version: String) {
        scope.launch {
            try {
                context.getSharedPreferences(SKIP_PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_SKIPPED_VERSION, version).apply()
            } catch (e: Exception) {
                Log.w(TAG, "skip persist failed: ${e.message}")
            }
            _state.value = UpdateManagerState.Idle
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NotificationHelper.ID_APP_UPDATE)
            } catch (e: Exception) {
                Log.w(TAG, "skip cancel notif failed: ${e.message}")
            }
        }
    }

    /** Dismiss the banner: clear the stored available update, go Idle. */
    fun dismiss() {
        scope.launch {
            try {
                settingsRepository.setAvailableUpdate(null, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "dismiss clear failed: ${e.message}")
            }
            _state.value = UpdateManagerState.Idle
        }
    }

    // ── Private prefs helpers ──────────────────────────────────────

    private fun skippedVersion(): String? = try {
        context.getSharedPreferences(SKIP_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SKIPPED_VERSION, null)
    } catch (_: Exception) {
        null
    }

    private fun shouldNotify(version: String): Boolean = try {
        context.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NOTIFIED_VERSION, null) != version
    } catch (_: Exception) {
        true
    }

    private fun markNotified(version: String) {
        try {
            context.getSharedPreferences(NOTIF_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_NOTIFIED_VERSION, version).apply()
        } catch (e: Exception) {
            Log.w(TAG, "markNotified failed: ${e.message}")
        }
    }

    /** Newest legacy DownloadManager APK, for UI "install downloaded file" hints. */
    fun legacyApkFile(): File? = UpdateDownloader.legacyDownloadManagerFile(context)

    /**
     * Called when a legacy DownloadManager APK finished outside [download()].
     * Stages it as ReadyToInstall (persisting the path) and auto-installs
     * when allowed, so notification taps never dead-end.
     */
    fun onLegacyDownloadReady(update: PendingUpdate, apkFile: File) {
        scope.launch {
            try { settingsRepository.setDownloadedApkPath(apkFile.absolutePath) } catch (_: Exception) {}
            _state.value = UpdateManagerState.ReadyToInstall(update, apkFile)
            UpdateNotifications.showReady(context, update, apkFile)
            if (installer.canInstall()) installReady()
        }
    }
}
