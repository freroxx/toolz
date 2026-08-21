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
}

@HiltViewModel
class WhisperAuthViewModel @Inject constructor(
    application: Application,
    private val authManager: WhisperAuthManager,
    private val repository: WhisperRepository,
    private val passwordDao: PasswordDao,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {

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

    private val _usernameAvailability = MutableStateFlow<UsernameAvailability>(UsernameAvailability.Idle)
    val usernameAvailability: StateFlow<UsernameAvailability> = _usernameAvailability.asStateFlow()

    // Clipboard expiry is owned here (viewModelScope survives rotation and navigation,
    // unlike a rememberCoroutineScope), with a SharedPreferences fallback so a killed
    // process can still clean up the clipboard the next time this screen opens.
    private val clipboardRestorePrefs =
        application.getSharedPreferences("whisper_clipboard_restore", android.content.Context.MODE_PRIVATE)

    init {
        restorePendingClipboardExpiry()
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
                when (status) {
                    is SessionStatus.Initializing -> _authState.value = WhisperAuthState.Loading
                    is SessionStatus.Authenticated -> _authState.value = WhisperAuthState.Authenticated
                    else -> _authState.value = WhisperAuthState.Idle
                }
            }
        }
    }

    /**
     * Copies a token to the clipboard for [TOKEN_CLIPBOARD_TTL_MS], then swaps it back
     * to [restoreTo]. The timer runs on the ViewModel, so it survives screen navigation
     * and rotation; the persisted entry covers process death.
     * NOTE: VM-scoped expiry is best-effort and dies if VM is cleared/navigation away;
     * a full fix requires WorkManager to guarantee expiry across process death.
     */
    fun scheduleTokenClipboardExpiry(token: String, restoreTo: String?, clipboard: ClipboardManager) {
        val deadline = SystemClock.elapsedRealtime() + TOKEN_CLIPBOARD_TTL_MS
        clipboardRestorePrefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_RESTORE_TO, restoreTo)
            .putLong(KEY_DEADLINE, deadline)
            .apply()
        armClipboardExpiry(token, restoreTo, deadline, clipboard)
    }

    private fun restorePendingClipboardExpiry() {
        val token = clipboardRestorePrefs.getString(KEY_TOKEN, null) ?: return
        val restoreTo = clipboardRestorePrefs.getString(KEY_RESTORE_TO, null)
        val deadline = clipboardRestorePrefs.getLong(KEY_DEADLINE, 0L)
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java) ?: return
        val remaining = deadline - SystemClock.elapsedRealtime()
        if (remaining <= 0) {
            // The deadline passed while the app was gone: swap the clipboard back now if
            // our token is still the top entry.
            if (clipboard.primaryClip?.getItemAt(0)?.coerceToText(getApplication<Application>())?.toString() == token) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, restoreTo ?: ""))
            }
            clipboardRestorePrefs.edit().clear().apply()
        } else {
            // Still inside the window: re-arm the timer with the remaining time.
            armClipboardExpiry(token, restoreTo, deadline, clipboard)
        }
    }

    private fun armClipboardExpiry(token: String, restoreTo: String?, deadline: Long, clipboard: ClipboardManager) {
        val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        viewModelScope.launch {
            delay(remaining)
            // Only touch the clipboard if our token is still the top entry.
            // Uses coerceToText for AnnotatedString compatibility and elapsedRealtime for monotonic deadline.
            // NOTE: Best-effort VM-scoped; full fix requires WorkManager to survive VM recreation.
            if (clipboard.primaryClip?.getItemAt(0)?.coerceToText(getApplication<Application>())?.toString() == token) {
                clipboard.setPrimaryClip(ClipData.newPlainText(null, restoreTo ?: ""))
            }
            clipboardRestorePrefs.edit().clear().apply()
        }
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
                .onFailure { _usernameAvailability.value = UsernameAvailability.Idle }
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
            authManager.loginWithUsername(username, password)
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
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
                saveToVault("Whisper: $cleanUser", cleanUser, password)
            }
            .onFailure { _authState.value = WhisperAuthState.Error(formatError(it)) }
    }

    private fun saveToVault(name: String, user: String, pass: String) {
        viewModelScope.launch {
            passwordDao.insertPassword(
                PasswordEntity(
                    name = name,
                    url = "whisper.toolz.app",
                    username = user,
                    password = pass,
                    strength = PasswordGenerator.calculateStrength(pass)
                )
            )
        }
    }

    fun generateToken() {
        _generatedToken.value = authManager.generateAnonToken()
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
                    saveToVault("Whisper Anon: $cleanUsername", cleanUsername, token.token)
                }
                .onFailure {
                    _authState.value = WhisperAuthState.Error(formatError(it))
                    // A failed registration must not leave a usable token floating around:
                    // roll a fresh one so the old credential is burned on both sides.
                    _generatedToken.value = authManager.generateAnonToken()
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
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
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

    private companion object {
        const val TOKEN_CLIPBOARD_TTL_MS = 60_000L
        const val KEY_TOKEN = "token"
        const val KEY_RESTORE_TO = "restoreTo"
        const val KEY_DEADLINE = "deadlineEpochMs"
    }
}
