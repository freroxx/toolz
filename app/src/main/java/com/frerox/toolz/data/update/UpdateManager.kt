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

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates check → download → verify → install as one [state] machine.
 *
 * The session is persisted in [UpdateStore], so a download survives process
 * death, notification taps work after a restart, and the install result is
 * confirmed on the next launch. A monotonically increasing generation token
 * makes cancellation and restart races deterministic.
 */
@Singleton
class UpdateManager @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
    private val downloader: UpdateDownloader,
    private val installer: UpdateInstaller,
    private val store: UpdateStore,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val CHECK_TIMEOUT_MS = 25_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateManagerState>(UpdateManagerState.Idle)
    val state: StateFlow<UpdateManagerState> = _state.asStateFlow()

    val pending: StateFlow<PendingUpdate?> = _state
        .map { it.pendingUpdate() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val checkMutex = Mutex()
    private val restoreMutex = Mutex()

    @Volatile
    private var restored = false

    private val downloadGeneration = AtomicLong(0L)
    private var downloadJob: Job? = null
    private var lastProgressAt = 0L
    private var lastProgressPct = -1

    /** Warm the state machine. Safe to call repeatedly. */
    fun restore() {
        scope.launch { ensureRestored() }
    }

    suspend fun ensureRestored() {
        if (restored) return
        restoreMutex.withLock {
            if (!restored) {
                try {
                    restoreInternal()
                } catch (e: Exception) {
                    Log.w(TAG, "restore failed: ${e.message}")
                }
                restored = true
            }
        }
    }

    // ── Check ───────────────────────────────────────────────────────────

    /**
     * Check for updates. Returns the candidate, or null when up to date,
     * skipped, already downloading or failed. Concurrent calls are deduped.
     */
    suspend fun check(showNotification: Boolean = false, force: Boolean = false): PendingUpdate? {
        ensureRestored()
        return checkMutex.withLock {
            when (val current = _state.value) {
                is UpdateManagerState.Checking -> return@withLock null
                is UpdateManagerState.Downloading,
                is UpdateManagerState.Verifying,
                is UpdateManagerState.ReadyToInstall,
                is UpdateManagerState.Installing -> return@withLock current.pendingUpdate()
                is UpdateManagerState.Available -> if (!force) return@withLock current.update
                else -> Unit
            }

            val previous = _state.value
            _state.value = UpdateManagerState.Checking

            val result = try {
                withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                    updateRepository.checkForUpdates()
                } ?: UpdateCheckResult.Error(UpdateError.Timeout)
            } catch (e: CancellationException) {
                _state.value = previous
                throw e
            } catch (e: Exception) {
                UpdateCheckResult.Error(UpdateError.fromThrowable(e))
            }

            runCatching { settingsRepository.setLastUpdateCheck(System.currentTimeMillis()) }

            when (result) {
                is UpdateCheckResult.NewUpdate -> {
                    val update = result.update
                    val skipped = store.skippedVersion()
                    if (!force && !update.isCritical && skipped == update.version) {
                        clearOffer()
                        _state.value = UpdateManagerState.UpToDate
                        return@withLock null
                    }
                    // A skip only applies to the version that was skipped.
                    if (skipped != null && UpdateVersion.isNewer(skipped, update.version)) {
                        store.setSkippedVersion(null)
                    }
                    persistOffer(update, apkPath = null)
                    _state.value = UpdateManagerState.Available(update)
                    if (
                        showNotification &&
                        notificationsEnabled() &&
                        store.markNotifiedIfNew(update.version)
                    ) {
                        UpdateNotifications.showAvailable(context, update)
                    }
                    update
                }

                is UpdateCheckResult.UpToDate -> {
                    clearOffer()
                    _state.value = UpdateManagerState.UpToDate
                    null
                }

                is UpdateCheckResult.Error -> {
                    // Keep the persisted offer: the next launch can restore it.
                    _state.value = UpdateManagerState.Error(result.error)
                    null
                }
            }
        }
    }

    /**
     * Like [check], but maps the resulting state to [UpdateCheckResult] for
     * callers (onboarding) that render the three-way outcome directly.
     */
    suspend fun checkForResult(
        showNotification: Boolean = false,
        force: Boolean = true,
    ): UpdateCheckResult {
        check(showNotification = showNotification, force = force)
        return when (val current = _state.value) {
            is UpdateManagerState.Available -> UpdateCheckResult.NewUpdate(current.update)
            is UpdateManagerState.Downloading -> UpdateCheckResult.NewUpdate(current.update)
            is UpdateManagerState.Verifying -> UpdateCheckResult.NewUpdate(current.update)
            is UpdateManagerState.ReadyToInstall -> UpdateCheckResult.NewUpdate(current.update)
            is UpdateManagerState.Installing -> UpdateCheckResult.NewUpdate(current.update)
            is UpdateManagerState.Error -> UpdateCheckResult.Error(current.error)
            else -> UpdateCheckResult.UpToDate
        }
    }

    // ── Download ────────────────────────────────────────────────────────

    /**
     * Start (or restart) the download. Non-suspending: progress and the
     * eventual result flow through [state].
     */
    fun download(update: PendingUpdate? = null, autoInstall: Boolean = true) {
        val target = update
            ?: currentPendingOrNull()
            ?: store.pending()?.toPending()
            ?: return
        if (target.downloadUrl.isBlank()) {
            _state.value = UpdateManagerState.Error(
                UpdateError.Unknown(
                    overrideMessageRes = R.string.st_Update_ErrorNoDownloadLink,
                ),
                retryDownload = false,
            )
            return
        }

        val generation = downloadGeneration.incrementAndGet()
        downloadJob?.cancel()
        downloadJob = null
        lastProgressAt = 0L
        lastProgressPct = -1

        val dest = UpdateDownloader.defaultDestFile(context, target.version)
        _state.value = UpdateManagerState.Downloading(
            update = target,
            bytesDownloaded = if (dest.isFile) dest.length() else 0L,
            totalBytes = target.sizeBytes ?: 0L,
            speedBps = 0L,
            progress = 0f,
        )

        downloadJob = scope.launch {
            persistOffer(target, apkPath = dest.takeIf { it.isFile }?.absolutePath)
            val drawNotifications = notificationsEnabled()
            val result = downloader.download(
                url = target.downloadUrl,
                destFile = dest,
                expectedSize = target.sizeBytes,
                expectedSha256 = target.sha256,
                onProgress = { downloaded, total, speedBps ->
                    if (downloadGeneration.get() == generation) {
                        val progress = if (total > 0L) {
                            (downloaded * 100f / total).coerceIn(0f, 100f)
                        } else {
                            0f
                        }
                        _state.value = UpdateManagerState.Downloading(
                            target, downloaded, total, speedBps, progress
                        )
                        val pct = progress.toInt()
                        val now = System.currentTimeMillis()
                        if (
                            drawNotifications &&
                            NotificationHelper.shouldPublishProgress(
                                lastProgressAt, lastProgressPct, now, pct
                            )
                        ) {
                            lastProgressAt = now
                            lastProgressPct = pct
                            UpdateNotifications.showProgress(
                                context, target, pct, downloaded, total, speedBps
                            )
                        }
                    }
                },
                isCancelled = { downloadGeneration.get() != generation },
            )

            if (downloadGeneration.get() != generation) return@launch

            when (result) {
                is UpdateDownloader.DownloadResult.Success -> {
                    _state.value = UpdateManagerState.Verifying(target, result.file)
                    val validation = try {
                        installer.validate(result.file)
                    } catch (e: Exception) {
                        Log.w(TAG, "verification failed: ${e.message}")
                        ApkValidation.Invalid(ApkIdentity.Reason.NotAnApk)
                    }
                    if (downloadGeneration.get() != generation) return@launch

                    when (validation) {
                        is ApkValidation.Valid -> {
                            persistOffer(target, apkPath = result.file.absolutePath)
                            _state.value = UpdateManagerState.ReadyToInstall(
                                update = target,
                                apkFile = result.file,
                                needsPermission = !installer.canInstall(),
                            )
                            // A background finish can't launch the installer UI,
                            // so only auto-install while visible; otherwise
                            // notify and let the user finish from the app.
                            val willAutoInstall =
                                autoInstall && installer.canInstall() && isAppInForeground()
                            if (drawNotifications && !willAutoInstall) {
                                UpdateNotifications.showReady(context, target, result.file)
                            }
                            if (willAutoInstall) {
                                installReady()
                            }
                        }

                        is ApkValidation.Invalid -> {
                            result.file.delete()
                            UpdateDownloader.deleteSidecar(result.file)
                            fail(
                                UpdateError.Verification(
                                    reason = validation.reason,
                                ),
                                retryDownload = false,
                            )
                        }
                    }
                }

                is UpdateDownloader.DownloadResult.Failure -> {
                    when (result.kind) {
                        UpdateDownloader.FailureKind.Cancelled ->
                            _state.value = UpdateManagerState.Available(target)
                        else -> fail(result.toUpdateError(), retryDownload = true)
                    }
                }
            }
        }
    }

    /** Stop the in-flight download; the partial file is kept for next time. */
    fun cancelDownload() {
        val current = _state.value as? UpdateManagerState.Downloading ?: return
        downloadGeneration.incrementAndGet()
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateManagerState.Available(current.update)
        UpdateNotifications.cancelDownload(context)
    }

    // ── Install ─────────────────────────────────────────────────────────

    fun installReady() {
        val current = _state.value as? UpdateManagerState.ReadyToInstall ?: return
        val apkFile = current.apkFile
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            _state.value = UpdateManagerState.Error(UpdateError.Storage, retryDownload = true)
            return
        }
        val generation = downloadGeneration.incrementAndGet()
        scope.launch {
            val validation = try {
                installer.validate(apkFile)
            } catch (e: Exception) {
                ApkValidation.Invalid(ApkIdentity.Reason.NotAnApk)
            }
            if (downloadGeneration.get() != generation) return@launch
            when (validation) {
                is ApkValidation.Invalid -> {
                    apkFile.delete()
                    UpdateDownloader.deleteSidecar(apkFile)
                    fail(
                        UpdateError.Verification(reason = validation.reason),
                        retryDownload = false,
                    )
                }

                is ApkValidation.Valid -> {
                    _state.value = UpdateManagerState.Installing(current.update, apkFile)
                    when (installer.install(apkFile)) {
                        is InstallLaunch.NeedsPermission -> {
                            _state.value = UpdateManagerState.ReadyToInstall(
                                update = current.update,
                                apkFile = apkFile,
                                needsPermission = true,
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * Re-check the unknown-sources permission when the screen resumes and
     * continue the install automatically if the user granted it.
     */
    fun refreshInstallPermission(autoInstall: Boolean = true) {
        val current = _state.value as? UpdateManagerState.ReadyToInstall ?: return
        val canInstall = installer.canInstall()
        if (current.needsPermission && canInstall) {
            _state.value = current.copy(needsPermission = false)
            if (autoInstall) installReady()
        } else if (!current.needsPermission && !canInstall) {
            _state.value = current.copy(needsPermission = true)
        }
    }

    /** Install an APK handed over by a notification tap or legacy download. */
    fun installDownloaded(apkFile: File, update: PendingUpdate? = null) {
        scope.launch {
            ensureRestored()
            if (!apkFile.isFile || apkFile.length() <= 0L) {
                fail(UpdateError.Storage, retryDownload = true)
                return@launch
            }
            val target = update ?: currentPendingOrNull() ?: store.pending()?.toPending()
            if (target == null) {
                fail(
                    UpdateError.Unknown(
                        overrideMessageRes = R.string.st_Update_ErrorDetailsExpired,
                    ),
                    retryDownload = false,
                )
                return@launch
            }
            val validation = try {
                installer.validate(apkFile)
            } catch (e: Exception) {
                ApkValidation.Invalid(ApkIdentity.Reason.NotAnApk)
            }
            when (validation) {
                is ApkValidation.Invalid -> {
                    apkFile.delete()
                    UpdateDownloader.deleteSidecar(apkFile)
                    fail(
                        UpdateError.Verification(reason = validation.reason),
                        retryDownload = false,
                    )
                }

                is ApkValidation.Valid -> {
                    persistOffer(target, apkFile.absolutePath)
                    _state.value = UpdateManagerState.ReadyToInstall(
                        update = target,
                        apkFile = apkFile,
                        needsPermission = !installer.canInstall(),
                    )
                    installReady()
                }
            }
        }
    }

    /**
     * Called when a DownloadManager download from an older app version
     * finishes. The manager only adopts the file — it never enqueues new
     * system downloads.
     */
    fun onLegacyDownloadReady(update: PendingUpdate, apkFile: File) {
        scope.launch {
            ensureRestored()
            if (!apkFile.isFile || apkFile.length() <= 0L) return@launch
            val validation = try {
                installer.validate(apkFile)
            } catch (e: Exception) {
                ApkValidation.Invalid(ApkIdentity.Reason.NotAnApk)
            }
            when (validation) {
                is ApkValidation.Invalid -> {
                    apkFile.delete()
                    UpdateDownloader.deleteSidecar(apkFile)
                    fail(
                        UpdateError.Verification(reason = validation.reason),
                        retryDownload = false,
                    )
                }

                is ApkValidation.Valid -> {
                    persistOffer(update, apkFile.absolutePath)
                    _state.value = UpdateManagerState.ReadyToInstall(
                        update = update,
                        apkFile = apkFile,
                        needsPermission = !installer.canInstall(),
                    )
                    if (notificationsEnabled()) {
                        UpdateNotifications.showReady(context, update, apkFile)
                    }
                    if (installer.canInstall() && isAppInForeground()) installReady()
                }
            }
        }
    }

    /** Called by UpdateReceiver after STATUS_SUCCESS. */
    fun onInstallSuccess(version: String) {
        scope.launch {
            val resolved = version.ifBlank {
                pendingVersionOrNull().orEmpty()
            }
            // commit() inside: the process may be killed right after this.
            store.setInstallConfirmation(resolved)
            clearOffer()
            store.setSkippedVersion(null)
            UpdateDownloader.pruneCache(context, keep = null)
            UpdateNotifications.cancelAll(context)
            _state.value = UpdateManagerState.Installed(resolved)
            if (notificationsEnabled()) {
                UpdateNotifications.showInstalled(context, resolved)
            }
        }
    }

    /** User dismissed the system installer: return to Ready, no error. */
    fun onInstallAborted() {
        val current = _state.value as? UpdateManagerState.Installing ?: return
        _state.value = UpdateManagerState.ReadyToInstall(
            update = current.update,
            apkFile = current.apkFile,
            needsPermission = !installer.canInstall(),
        )
    }

    fun onInstallFailure(message: String) {
        onInstallFailure(UpdateError.InstallFailed(message))
    }

    fun onInstallFailure(error: UpdateError) {
        _state.value = UpdateManagerState.Error(error)
        scope.launch {
            if (notificationsEnabled() && !isAppInForeground()) {
                UpdateNotifications.showError(context, context.getString(error.messageRes))
            }
        }
    }

    // ── Offer lifecycle ─────────────────────────────────────────────────

    /** "Later": hide the prompt but keep the update reachable in the updater. */
    fun dismissOffer() {
        scope.launch {
            runCatching { settingsRepository.setAvailableUpdate(null, null, null) }
        }
    }

    /** "Skip this version": clear everything until a newer version appears. */
    fun skipVersion(version: String) {
        scope.launch {
            store.setSkippedVersion(version)
            clearOffer()
            UpdateDownloader.pruneCache(context, keep = null)
            UpdateNotifications.cancelAll(context)
            _state.value = UpdateManagerState.Idle
        }
    }

    /** Acknowledge the post-install confirmation card. */
    fun dismissInstalled() {
        if (_state.value is UpdateManagerState.Installed) {
            _state.value = UpdateManagerState.UpToDate
        }
    }

    /** Download requested from an "update available" notification. */
    fun downloadFromNotification(url: String, version: String) {
        scope.launch {
            ensureRestored()
            val target = currentPendingOrNull()
                ?.takeIf { it.downloadUrl == url || it.version == version }
                ?: store.pending()?.toPending()
                    ?.takeIf { it.downloadUrl == url || it.version == version }
                ?: PendingUpdate(
                    version = version,
                    versionCode = 0,
                    changelog = "",
                    downloadUrl = url,
                    sizeBytes = null,
                    sha256 = null,
                    isCritical = false,
                    abi = "",
                    source = "notification",
                )
            if (target.downloadUrl.isBlank()) return@launch
            persistOffer(target, apkPath = null)
            download(target, autoInstall = true)
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────

    fun currentPendingOrNull(): PendingUpdate? = _state.value.pendingUpdate()

    fun pendingFromStore(): PendingUpdate? = store.pending()?.toPending()

    fun pendingVersionOrNull(): String? =
        currentPendingOrNull()?.version ?: store.pending()?.version

    fun legacyApkFile(): File? = UpdateDownloader.legacyDownloadManagerFile(context)

    fun openInstallSettings(activityContext: Context) {
        installer.openInstallSettings(activityContext)
    }

    /**
     * Automatic (background) downloads wait for an unmetered network;
     * critical updates cut through and may use mobile data. Manual
     * downloads never call this.
     */
    fun canAutoDownloadNow(update: PendingUpdate? = null): Boolean {
        val target = update
            ?: currentPendingOrNull()
            ?: store.pending()?.toPending()
            ?: return false
        if (target.isCritical) return true
        return !isActiveNetworkMetered()
    }

    private fun isActiveNetworkMetered(): Boolean = try {
        context.getSystemService(ConnectivityManager::class.java)
            ?.isActiveNetworkMetered ?: true
    } catch (e: Exception) {
        true
    }

    /**
     * True while at least one activity is visible. Android blocks activity
     * starts from the background, so the package-installer UI may only be
     * launched while the app is in the foreground: background finishes
     * stop at "ready to install" plus a notification instead.
     */
    private fun isAppInForeground(): Boolean = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    } catch (e: Exception) {
        false
    }

    // ── Persistence / restore ───────────────────────────────────────────

    private suspend fun restoreInternal() {
        // 1. Post-install confirmation (the process usually died during the
        //    install, so this is the first chance to report success).
        val confirmed = store.installConfirmation()
        if (confirmed != null) {
            store.setInstallConfirmation(null)
            // A blank version still means the installer reported success; the
            // confirmation card falls back to a generic localized title.
            if (confirmed.isBlank() ||
                UpdateVersion.compare(confirmed, updateRepository.getCurrentVersionName()) == 0
            ) {
                store.setPending(null)
                store.setSkippedVersion(null)
                clearLegacyOffer()
                UpdateDownloader.pruneCache(context, keep = null)
                _state.value = UpdateManagerState.Installed(confirmed)
                return
            }
        }

        // A live session (download/install in progress) owns the state.
        if (_state.value !is UpdateManagerState.Idle) return

        migrateLegacyOffer()

        val record = store.pending()
        if (record == null) {
            UpdateDownloader.pruneCache(context, keep = null)
            return
        }

        val installedVersion = updateRepository.getCurrentVersionName()
        if (!UpdateVersion.isNewer(installedVersion, record.version)) {
            // Already installed or older than the running build.
            store.setPending(null)
            clearLegacyOffer()
            UpdateDownloader.pruneCache(context, keep = null)
            return
        }

        val target = record.toPending()
        val candidates = listOfNotNull(
            record.apkPath?.let { File(it) },
            UpdateDownloader.defaultDestFile(context, record.version),
        ).distinctBy { it.absolutePath }

        val readyFile = candidates.firstOrNull { file ->
            file.isFile && file.length() > 0L &&
                (record.sizeBytes == null || file.length() == record.sizeBytes) &&
                installer.validate(file) is ApkValidation.Valid
        }
        val keep = readyFile
            ?: candidates.firstOrNull { it.isFile && it.length() > 0L }
        UpdateDownloader.pruneCache(context, keep = keep)

        // Note: the DataStore offer is intentionally NOT rewritten here.
        // "Later" only clears DataStore (the persisted record survives), so
        // restoring the record must not resurrect the prompt on every launch.

        if (readyFile != null) {
            runCatching { settingsRepository.setDownloadedApkPath(readyFile.absolutePath) }
            _state.value = UpdateManagerState.ReadyToInstall(
                update = target,
                apkFile = readyFile,
                needsPermission = !installer.canInstall(),
            )
        } else {
            runCatching { settingsRepository.setDownloadedApkPath(null) }
            _state.value = UpdateManagerState.Available(target)
        }
    }

    /**
     * Before the store existed, the available offer lived only in DataStore.
     * Import it once so updates found by an older build stay actionable.
     */
    private suspend fun migrateLegacyOffer() {
        if (store.pending() != null) return
        val version = runCatching { settingsRepository.updateAvailableVersion.first() }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val url = runCatching { settingsRepository.updateApkUrl.first() }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val changelog = runCatching { settingsRepository.updateChangelog.first() }
            .getOrNull().orEmpty()
        val apkPath = runCatching { settingsRepository.downloadedApkPath.first() }
            .getOrNull()
        store.setPending(
            StoredUpdate(
                version = version,
                versionCode = 0,
                changelog = changelog,
                downloadUrl = url,
                sizeBytes = null,
                sha256 = null,
                isCritical = false,
                abi = "",
                source = "legacy",
                apkPath = apkPath,
            )
        )
    }

    private suspend fun persistOffer(update: PendingUpdate, apkPath: String?) {
        store.setPending(StoredUpdate.from(update, apkPath))
        runCatching {
            settingsRepository.setAvailableUpdate(
                update.version, update.changelog, update.downloadUrl
            )
        }
        runCatching { settingsRepository.setDownloadedApkPath(apkPath) }
    }

    private suspend fun clearOffer() {
        store.setPending(null)
        clearLegacyOffer()
    }

    private suspend fun clearLegacyOffer() {
        runCatching { settingsRepository.setAvailableUpdate(null, null, null) }
        runCatching { settingsRepository.setDownloadedApkPath(null) }
    }

    private suspend fun notificationsEnabled(): Boolean = try {
        settingsRepository.appUpdateNotifications.first()
    } catch (e: Exception) {
        true
    }

    private suspend fun fail(error: UpdateError, retryDownload: Boolean) {
        _state.value = UpdateManagerState.Error(error, retryDownload)
        // Only surface failures the user cannot already see on the updater
        // screen; foreground failures would be redundant noise.
        if (notificationsEnabled() && !isAppInForeground()) {
            UpdateNotifications.showError(context, context.getString(error.messageRes))
        }
    }

    private fun UpdateDownloader.DownloadResult.Failure.toUpdateError(): UpdateError =
        when (kind) {
            UpdateDownloader.FailureKind.Cancelled -> UpdateError.Unknown()
            UpdateDownloader.FailureKind.Network -> UpdateError.Offline
            UpdateDownloader.FailureKind.Timeout -> UpdateError.Timeout
            UpdateDownloader.FailureKind.Http ->
                UpdateError.fromHttp(httpCode ?: -1)
            UpdateDownloader.FailureKind.SizeMismatch,
            UpdateDownloader.FailureKind.Integrity ->
                UpdateError.Integrity(detail)
            UpdateDownloader.FailureKind.Storage -> UpdateError.Storage
        }
}
