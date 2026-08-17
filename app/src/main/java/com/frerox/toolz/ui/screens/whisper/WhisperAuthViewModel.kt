/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0
 */
package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.password.PasswordDao
import com.frerox.toolz.data.password.PasswordEntity
import com.frerox.toolz.data.whisper.*
import com.frerox.toolz.util.password.PasswordGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val authManager: WhisperAuthManager,
    private val repository: WhisperRepository,
    private val passwordDao: PasswordDao,
) : ViewModel() {

    private val _authState = MutableStateFlow<WhisperAuthState>(WhisperAuthState.Loading)
    val authState: StateFlow<WhisperAuthState> = _authState.asStateFlow()
    private val _generatedToken = MutableStateFlow<WhisperAnonToken?>(null)
    val generatedToken: StateFlow<WhisperAnonToken?> = _generatedToken.asStateFlow()

    private val _usernameAvailability = MutableStateFlow<UsernameAvailability>(UsernameAvailability.Idle)
    val usernameAvailability: StateFlow<UsernameAvailability> = _usernameAvailability.asStateFlow()

    init {
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
            _authState.value = WhisperAuthState.Loading
            authManager.loginWithUsername(username, password)
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
                .onFailure { _authState.value = WhisperAuthState.Error(formatError(it)) }
        }
    }

    fun registerWithUsername(username: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = WhisperAuthState.Loading
            val cleanUser = username.trim().lowercase()
            authManager.registerWithUsername(cleanUser, password, displayName)
                .onSuccess {
                    _authState.value = WhisperAuthState.Authenticated
                    saveToVault("Whisper: $cleanUser", cleanUser, password)
                }
                .onFailure { _authState.value = WhisperAuthState.Error(formatError(it)) }
        }
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
            "anon_" + token.token.take(6)
        }

        viewModelScope.launch {
            _authState.value = WhisperAuthState.Loading
            authManager.registerWithToken(token, username = cleanUsername, displayName = cleanName)
                .onSuccess { 
                    _authState.value = WhisperAuthState.Authenticated
                    saveToVault("Whisper Anon: $cleanUsername", cleanUsername, token.token)
                }
                .onFailure { _authState.value = WhisperAuthState.Error(formatError(it)) }
        }
    }

    fun loginWithToken(rawToken: String) {
        val cleanToken = authManager.normalizeToken(rawToken)
        if (!authManager.isValidToken(cleanToken)) {
            _authState.value = WhisperAuthState.Error(UiText.StringResource(R.string.st_Whisper_Error_InvalidToken))
            return
        }
        viewModelScope.launch {
            _authState.value = WhisperAuthState.Loading
            authManager.loginWithToken(rawToken)
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
                .onFailure {
                    val message = if (authManager.isInvalidCredentials(it)) {
                        UiText.StringResource(R.string.st_Whisper_Error_TokenNotRecognized)
                    } else formatError(it)
                    _authState.value = WhisperAuthState.Error(message)
                }
        }
    }

    fun normalizeToken(raw: String): String = authManager.normalizeToken(raw)
    fun clearError() { _authState.value = WhisperAuthState.Idle }

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
