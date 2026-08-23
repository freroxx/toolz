/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */
package com.frerox.toolz.ui.screens.whisper

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.util.password.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UsernameAvailability {
    object Idle : UsernameAvailability()
    object Checking : UsernameAvailability()
    object Available : UsernameAvailability()
    object Taken : UsernameAvailability()
    data class Invalid(val reason: UiText) : UsernameAvailability()

    // V2-FIX A-H2: the availability check failed (offline / server error). This used to
    // collapse to Idle, silently dead-ending registration because the CTA only enabled
    // on `Available`. The server still final-validates, so the UI treats this as
    // "unverified, may proceed".
    object Unavailable : UsernameAvailability()
}

sealed class AubupRecoveryState {
    object Idle : AubupRecoveryState()
    object Scanning : AubupRecoveryState()
    data class ScanResult(
        val vaultAccounts: List<PasswordEntity>,
        val accessFiles: List<java.io.File>,
    ) : AubupRecoveryState()
    data class Restored(
        val username: String,
        val authType: String,
        val credential: String,
    ) : AubupRecoveryState()

    // V2-FIX A-M3: carries UiText instead of a raw English String so recovery errors
    // are localizable at the consumption site (screen resolves via asString()).
    data class Error(val message: UiText) : AubupRecoveryState()
}

@HiltViewModel
class WhisperAuthViewModel @Inject constructor(
    application: Application,
    private val authManager: WhisperAuthManager,
    private val repository: WhisperRepository,
    private val passwordDao: PasswordDao,
    private val settingsRepository: SettingsRepository,
    private val aubupManager: WhisperAubupManager,
) : AndroidViewModel(application) {

    companion object {
        /** V2-FIX AU-M5: hard cap for picked access-file imports (encrypted payload is tiny). */
        const val MAX_ACCESS_FILE_BYTES: Int = 1 * 1024 * 1024 // 1 MB
    }

    val screenshotBypassEnabled: StateFlow<Boolean> = settingsRepository.whisperScreenshotBypass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setScreenshotBypass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWhisperScreenshotBypass(enabled)
        }
    }

    private val _authState = MutableStateFlow<WhisperAuthState>(WhisperAuthState.Loading)
    val authState: StateFlow<WhisperAuthState> = _authState.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _generatedToken = MutableStateFlow<WhisperAnonToken?>(null)
    val generatedToken: StateFlow<WhisperAnonToken?> = _generatedToken.asStateFlow()

    // V2-FIX A-M4: increments every time the anon token is (re)generated. The screen
    // observes this to reset its "copied/saved" affordances — a rolled token burns the
    // old credential, so stale UI claiming the OLD token was copied must not survive.
    private val _generatedTokenVersion = MutableStateFlow(0)
    val generatedTokenVersion: StateFlow<Int> = _generatedTokenVersion.asStateFlow()

    private val _usernameAvailability = MutableStateFlow<UsernameAvailability>(UsernameAvailability.Idle)
    val usernameAvailability: StateFlow<UsernameAvailability> = _usernameAvailability.asStateFlow()

    private val _aubupState = MutableStateFlow<AubupRecoveryState>(AubupRecoveryState.Idle)
    val aubupState: StateFlow<AubupRecoveryState> = _aubupState.asStateFlow()

    init {
        // The session check must never leave the user stuck on the splash: after a hard
        // cap, fall back to the auth form even if Supabase never resolved the session.
        viewModelScope.launch {
            delay(15_000)
            if (_authState.value is WhisperAuthState.Loading) {
                _authState.value = WhisperAuthState.Idle
            }
        }
        viewModelScope.launch {
            authManager.sessionStatus.collectLatest { status ->
                // V2-FIX A-H1: the old `else -> Idle` catch-all mapped EVERY unknown or
                // transient status to Idle — e.g. a SessionStatus.RefreshFailure (token
                // refresh retrying on flaky network) downgraded an Authenticated user
                // back to the sign-in form mid-session. Enumerate all statuses from the
                // supabase-kt 3.x API used here explicitly; only a real "no session"
                // state may sign the UI out, everything unknown/transient keeps the
                // previous state.
                when (status) {
                    is SessionStatus.Initializing -> _authState.value = WhisperAuthState.Loading
                    is SessionStatus.Authenticated -> _authState.value = WhisperAuthState.Authenticated
                    // Both NotAuthenticated flavors mean there is genuinely no session:
                    // isSignOut=true is an explicit sign-out; isSignOut=false is emitted
                    // once after init when no stored session exists (fresh install /
                    // previously signed out). Not mapping the latter to Idle would leave
                    // logged-out users stranded behind the 15 s splash fallback below.
                    is SessionStatus.NotAuthenticated -> _authState.value = WhisperAuthState.Idle
                    // Transient: refresh failed but will retry — keep previous state and
                    // never downgrade an Authenticated session to Idle.
                    is SessionStatus.RefreshFailure -> Unit
                    // Unknown future statuses must never silently sign the user out.
                    else -> Unit
                }
            }
        }
    }

    /**
     * Schedules durable clipboard clearing via WorkManager (H-3 fix).
     * Survives process recreation and app restarts cleanly.
     * P0-5 FIX: Token is encrypted before persisting to WorkManager's SQLite.
     * If Keystore encryption fails, scheduling is SKIPPED — a reversible copy of
     * the token must never land in WorkData.
     */
    fun scheduleTokenClipboardExpiry(token: String, restoreTo: String?, clipboard: ClipboardManager) {
        val encryptedToken = try {
            com.frerox.toolz.worker.WhisperClipboardClearWorker.encryptForStorage(token, getApplication())
        } catch (_: Exception) { null }
        // Encryption unavailable (or empty): skip the durable clear rather than store
        // anything reversible. The 60 s clipboard exposure still applies, but no secret
        // is persisted.
        if (encryptedToken.isNullOrEmpty()) return
        val encryptedRestore = if (restoreTo != null) {
            try {
                com.frerox.toolz.worker.WhisperClipboardClearWorker.encryptForStorage(restoreTo, getApplication())
            } catch (_: Exception) { null }
        } else ""
        if (restoreTo != null && encryptedRestore.isNullOrEmpty()) return
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.frerox.toolz.worker.WhisperClipboardClearWorker>()
            .setInitialDelay(60, java.util.concurrent.TimeUnit.SECONDS)
            .setInputData(
                androidx.work.workDataOf(
                    com.frerox.toolz.worker.WhisperClipboardClearWorker.KEY_TOKEN to encryptedToken,
                    com.frerox.toolz.worker.WhisperClipboardClearWorker.KEY_RESTORE_TO to (encryptedRestore ?: ""),
                )
            )
            .build()

        androidx.work.WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(
                com.frerox.toolz.worker.WhisperClipboardClearWorker.UNIQUE_WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    fun startAubupScan() {
        viewModelScope.launch {
            _aubupState.value = AubupRecoveryState.Scanning
            delay(400) // Visual feedback for smooth scanning transition
            val vaultAccounts = aubupManager.scanVaultForWhisperAccounts()
            val accessFiles = aubupManager.scanToolzFolderForAccessFiles()
            _aubupState.value = AubupRecoveryState.ScanResult(
                vaultAccounts = vaultAccounts,
                accessFiles = accessFiles,
            )
        }
    }

    fun resetAubupState() {
        _aubupState.value = AubupRecoveryState.Idle
    }

    /**
     * V2-FIX AU-M5: reads the user-picked .enc access file OFF the main thread with a
     * hard size cap — the old picker callback did `readBytes()` directly in
     * composition-triggered UI code, freezing the main thread on large/crooked files.
     * Fails the returned Result on IO errors or when the file exceeds 1 MB.
     */
    fun importAccessBytes(uri: android.net.Uri, onResult: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(64 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_ACCESS_FILE_BYTES) { "Access file exceeds the ${MAX_ACCESS_FILE_BYTES / (1024 * 1024)} MB limit" }
                            buffer.write(chunk, 0, read)
                        }
                        buffer.toByteArray()
                    }
                    // require's contract smart-casts bytes to non-null ByteArray below.
                    require(bytes != null && bytes.isNotEmpty()) { "Could not read the selected file" }
                    bytes
                }
            }
            onResult(result)
        }
    }

    // V2-FIX A-M2: callers that KNOW the account kind (same contract as
    // createAccessFileForUser's isToken param, e.g. the live session's
    // authManager.isAnonymousTokenUser) pass an explicit hint; only when none is
    // provided do we fall back to the vault-name substring heuristic. A dedicated
    // stored flag would need a PasswordEntity column — deferred (see B5 note).
    fun restoreFromVault(account: PasswordEntity, isTokenHint: Boolean? = null) {
        viewModelScope.launch {
            _submitting.value = true
            val isToken = isTokenHint ?: account.name.contains("Anon", ignoreCase = true)

            val result = if (isToken) {
                authManager.loginWithToken(account.password)
            } else {
                authManager.loginWithUsername(account.username, account.password)
            }

            result.onSuccess {
                // BUGFIX (Review #P0-RestorePopup): Set Restored BEFORE Authenticated so
                // WhisperAuthScreen LaunchedEffect can see the restore state and suppress
                // the automatic navigate. Without this ordering the authState navigate
                // fires first and the Restored dialog flashes for one frame then disappears
                // because the composable is popped.
                _aubupState.value = AubupRecoveryState.Restored(
                    username = account.username,
                    authType = if (isToken) "TOKEN" else "PASSWORD",
                    credential = account.password,
                )
                _authState.value = WhisperAuthState.Authenticated
            }.onFailure { err ->
                // V2-FIX A-M3: wrap in UiText; reuse the existing generic error string
                // when the throwable carries no message.
                _aubupState.value = AubupRecoveryState.Error(
                    err.message?.let(UiText::DynamicString)
                        ?: UiText.StringResource(R.string.st_Whisper_Error_Generic)
                )
            }
            _submitting.value = false
        }
    }

    fun restoreFromAccessFile(file: java.io.File, whisperCode: String) {
        viewModelScope.launch {
            _submitting.value = true
            aubupManager.decryptAccessFile(file, whisperCode)
                .onSuccess { payload ->
                    loginWithPayload(payload)
                }
                .onFailure { err ->
                    // V2-FIX A-M3: UiText payload (see restoreFromVault).
                    _aubupState.value = AubupRecoveryState.Error(
                        err.message?.let(UiText::DynamicString)
                            ?: UiText.StringResource(R.string.st_Whisper_Error_Generic)
                    )
                    _submitting.value = false
                }
        }
    }

    fun restoreFromAccessBytes(bytes: ByteArray, whisperCode: String) {
        viewModelScope.launch {
            _submitting.value = true
            aubupManager.decryptAccessBytes(bytes, whisperCode)
                .onSuccess { payload ->
                    loginWithPayload(payload)
                }
                .onFailure { err ->
                    // V2-FIX A-M3: UiText payload (see restoreFromVault).
                    _aubupState.value = AubupRecoveryState.Error(
                        err.message?.let(UiText::DynamicString)
                            ?: UiText.StringResource(R.string.st_Whisper_Error_Generic)
                    )
                    _submitting.value = false
                }
        }
    }

    private suspend fun loginWithPayload(payload: WhisperAccessPayload) {
        val result = if (payload.authType == "TOKEN") {
            authManager.loginWithToken(payload.credential)
        } else {
            authManager.loginWithUsername(payload.username, payload.credential)
        }

        result.onSuccess {
            // BUGFIX (P0-RestorePopup): Order matters — see restoreFromVault.
            _aubupState.value = AubupRecoveryState.Restored(
                username = payload.username,
                authType = payload.authType,
                credential = payload.credential,
            )
            _authState.value = WhisperAuthState.Authenticated
            // Re-save to vault on successful recovery
            aubupManager.upsertWhisperVaultEntry(
                name = if (payload.authType == "TOKEN") "Whisper Anon: ${payload.username}" else "Whisper: ${payload.username}",
                username = payload.username,
                credential = payload.credential,
                isToken = payload.authType == "TOKEN"
            )
        }.onFailure { err ->
            // V2-FIX A-M3: UiText payload (see restoreFromVault).
            _aubupState.value = AubupRecoveryState.Error(
                err.message?.let(UiText::DynamicString)
                    ?: UiText.StringResource(R.string.st_Whisper_Error_Generic)
            )
        }
        _submitting.value = false
    }

    private var usernameCheckJob: Job? = null
    fun checkUsernameAvailable(username: String) {
        usernameCheckJob?.cancel()
        val clean = username.trim().lowercase()
        if (clean.isEmpty()) { _usernameAvailability.value = UsernameAvailability.Idle; return }
        val validationError = validateUsernameFormat(clean)
        if (validationError != null) { _usernameAvailability.value = UsernameAvailability.Invalid(validationError); return }
        usernameCheckJob = viewModelScope.launch {
            _usernameAvailability.value = UsernameAvailability.Checking
            delay(500)
            repository.checkUsernameAvailable(clean)
                .onSuccess { available ->
                    _usernameAvailability.value = if (available) UsernameAvailability.Available else UsernameAvailability.Taken
                }
                // V2-FIX A-H2: a failed check (offline, server error) must NOT collapse
                // to Idle — that silently dead-ended registration because the CTA gated
                // on `Available`. Surface an explicit Unavailable state; the server
                // final-validates on submit.
                .onFailure { _usernameAvailability.value = UsernameAvailability.Unavailable }
        }
    }

    private fun validateUsernameFormat(username: String): UiText? {
        if (username.length !in 3..20) return UiText.StringResource(R.string.st_Whisper_Error_UsernameLength)
        if (!username.matches(Regex("[a-z0-9_]+"))) return UiText.StringResource(R.string.st_Whisper_Error_UsernameInvalidChars)
        if (username.startsWith("_") || username.endsWith("_")) return UiText.StringResource(R.string.st_Whisper_Error_UsernameUnderscore)
        return null
    }

    fun loginWithUsername(username: String, password: String) {
        viewModelScope.launch {
            // Double-submit guard: only one sign-in may be in flight at a time.
            if (_submitting.value) return@launch
            _submitting.value = true
            val cleanUser = username.trim().lowercase()
            authManager.loginWithUsername(cleanUser, password)
                .onSuccess {
                    _authState.value = WhisperAuthState.Authenticated
                    aubupManager.upsertWhisperVaultEntry("Whisper: $cleanUser", cleanUser, password, isToken = false)
                }
                .onFailure { _authState.value = WhisperAuthState.Error(formatError(it)) }
            _submitting.value = false
        }
    }

    fun registerWithUsername(username: String, password: String, displayName: String) {
        viewModelScope.launch {
            // Double-submit guard: only one registration may be in flight at a time.
            if (_submitting.value) return@launch
            val cleanUser = username.trim().lowercase()
            // Fail fast with a clean error instead of sending an invalid username to the
            // server (the format check used to only gate the availability pre-check).
            validateUsernameFormat(cleanUser)?.let { invalid ->
                _authState.value = WhisperAuthState.Error(invalid)
                return@launch
            }
            _submitting.value = true
            repository.checkUsernameAvailable(cleanUser)
                .onSuccess { available ->
                    if (!available) {
                        _authState.value = WhisperAuthState.Error(UiText.StringResource(R.string.st_Whisper_Error_UsernameExists))
                    } else {
                        registerWithUsernameInternal(cleanUser, password, displayName)
                    }
                }
                .onFailure {
                    // Availability check failed (offline etc.) — let the real
                    // registration attempt surface the actual result.
                    registerWithUsernameInternal(cleanUser, password, displayName)
                }
            _submitting.value = false
        }
    }

    private suspend fun registerWithUsernameInternal(cleanUser: String, password: String, displayName: String) {
        authManager.registerWithUsername(cleanUser, password, displayName)
            .onSuccess {
                _authState.value = WhisperAuthState.Authenticated
                aubupManager.upsertWhisperVaultEntry("Whisper: $cleanUser", cleanUser, password, isToken = false)
            }
            .onFailure { _authState.value = WhisperAuthState.Error(formatError(it)) }
    }

    fun generateToken() {
        rollGeneratedToken()
    }

    /** V2-FIX A-M4: single funnel for (re)generation so the version counter never drifts. */
    private fun rollGeneratedToken() {
        _generatedToken.value = authManager.generateAnonToken()
        _generatedTokenVersion.value++
    }

    fun registerWithGeneratedToken(displayName: String, customUsername: String? = null) {
        val token = _generatedToken.value ?: return
        val cleanName = displayName.trim()
        if (cleanName.isEmpty()) {
            _authState.value = WhisperAuthState.Error(UiText.StringResource(R.string.st_Whisper_Error_DisplayNameRequired))
            return
        }
        val cleanUsername = if (!customUsername.isNullOrBlank()) {
            val valid = validateUsernameFormat(customUsername.trim().lowercase())
            if (valid != null) {
                _authState.value = WhisperAuthState.Error(valid)
                return
            }
            customUsername.trim().lowercase()
        } else {
            // Random suffix instead of a leaky 6-char token prefix: usernames must not
            // reveal partial credential material, and anon users stay unpredictable.
            "anon_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)
        }

        viewModelScope.launch {
            // Double-submit guard: only one registration may be in flight at a time.
            if (_submitting.value) return@launch
            _submitting.value = true
            authManager.registerWithToken(token, username = cleanUsername, displayName = cleanName)
                .onSuccess {
                    _authState.value = WhisperAuthState.Authenticated
                    aubupManager.upsertWhisperVaultEntry("Whisper Anon: $cleanUsername", cleanUsername, token.token, isToken = true)
                }
                .onFailure {
                    _authState.value = WhisperAuthState.Error(formatError(it))
                    // A failed registration must not leave a usable token floating around:
                    // roll a fresh one so the old credential is burned on both sides.
                    rollGeneratedToken()
                }
            _submitting.value = false
        }
    }

    fun loginWithToken(rawToken: String) {
        val cleanToken = authManager.normalizeToken(rawToken)
        if (!authManager.isValidToken(cleanToken)) {
            _authState.value = WhisperAuthState.Error(UiText.StringResource(R.string.st_Whisper_Error_InvalidToken))
            return
        }
        viewModelScope.launch {
            // Double-submit guard: only one login may be in flight at a time.
            if (_submitting.value) return@launch
            _submitting.value = true
            authManager.loginWithToken(cleanToken)
                .onSuccess {
                    _authState.value = WhisperAuthState.Authenticated
                    val myProfile = repository.getMyProfile(forceRefresh = true).getOrNull()
                    // V2-FIX A-M1: the old fallback label embedded a 6-char prefix of the
                    // raw token in the vault entry — partial credential material leaking
                    // into storage/UI. Use a random UUID suffix like registration instead.
                    val userHandle = myProfile?.effectiveUsername
                        ?: ("anon_" + java.util.UUID.randomUUID().toString().replace("-", "").take(8))
                    aubupManager.upsertWhisperVaultEntry("Whisper Anon: $userHandle", userHandle, cleanToken, isToken = true)
                }
                .onFailure {
                    val message = if (authManager.isInvalidCredentials(it)) {
                        UiText.StringResource(R.string.st_Whisper_Error_TokenNotRecognized)
                    } else formatError(it)
                    _authState.value = WhisperAuthState.Error(message)
                }
            _submitting.value = false
        }
    }

    fun normalizeToken(raw: String): String = authManager.normalizeToken(raw)

    fun clearError() {
        // Only a real error is cleared; a Notice (or the form itself) is never stomped.
        if (_authState.value is WhisperAuthState.Error) _authState.value = WhisperAuthState.Idle
    }

    fun dismissNotice() {
        if (_authState.value is WhisperAuthState.Notice) _authState.value = WhisperAuthState.Idle
    }

    private fun formatError(throwable: Throwable): UiText {
        val msg = throwable.message ?: return UiText.StringResource(R.string.st_Whisper_Error_Generic)
        return when {
            msg.contains("Token must be", ignoreCase = true) || msg.contains("doesn't look right", ignoreCase = true) ->
                UiText.StringResource(R.string.st_Whisper_Error_InvalidToken)
            authManager.isInvalidCredentials(throwable) -> UiText.StringResource(R.string.st_Whisper_Error_InvalidCredentials)
            msg.contains("User already registered", ignoreCase = true) -> UiText.StringResource(R.string.st_Whisper_Error_UsernameExists)
            msg.contains("network", ignoreCase = true) || msg.contains("connect", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) -> 
                UiText.StringResource(R.string.st_Whisper_Error_Network)
            else -> UiText.DynamicString(msg)
        }
    }
}
