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

package com.frerox.toolz.ui.screens.whisper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.whisper.WhisperAuthManager
import com.frerox.toolz.data.whisper.WhisperAnonToken
import com.frerox.toolz.data.whisper.WhisperAuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhisperAuthViewModel @Inject constructor(
    private val authManager: WhisperAuthManager,
) : ViewModel() {

    private val _authState = MutableStateFlow<WhisperAuthState>(WhisperAuthState.Idle)
    val authState: StateFlow<WhisperAuthState> = _authState.asStateFlow()

    // The generated anonymous token — held in memory for display/copy
    private val _generatedToken = MutableStateFlow<WhisperAnonToken?>(null)
    val generatedToken: StateFlow<WhisperAnonToken?> = _generatedToken.asStateFlow()

    init {
        if (authManager.currentUserId != null) {
            _authState.value = WhisperAuthState.Authenticated
        }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = WhisperAuthState.Loading
            authManager.loginWithEmail(email, password)
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
                .onFailure { _authState.value = WhisperAuthState.Error(it.message ?: "Login failed") }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = WhisperAuthState.Loading
            authManager.registerWithEmail(email, password)
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
                .onFailure { _authState.value = WhisperAuthState.Error(it.message ?: "Registration failed") }
        }
    }

    fun generateToken() {
        _generatedToken.value = authManager.generateAnonToken()
    }

    fun registerWithGeneratedToken() {
        val token = _generatedToken.value ?: return
        viewModelScope.launch {
            _authState.value = WhisperAuthState.Loading
            authManager.registerWithToken(token)
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
                .onFailure { _authState.value = WhisperAuthState.Error(it.message ?: "Registration failed") }
        }
    }

    fun loginWithToken(rawToken: String) {
        viewModelScope.launch {
            _authState.value = WhisperAuthState.Loading
            authManager.loginWithToken(rawToken)
                .onSuccess { _authState.value = WhisperAuthState.Authenticated }
                .onFailure { _authState.value = WhisperAuthState.Error(it.message ?: "Invalid token") }
        }
    }

    fun clearError() {
        _authState.value = WhisperAuthState.Idle
    }
}
