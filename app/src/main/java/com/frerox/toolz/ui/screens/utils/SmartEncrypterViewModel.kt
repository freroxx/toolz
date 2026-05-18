package com.frerox.toolz.ui.screens.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.util.CryptoAlgorithm
import com.frerox.toolz.util.CryptoFormat
import com.frerox.toolz.util.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EncrypterUiState(
    val inputText: String = "",
    val password: String = "",
    val selectedAlgorithm: CryptoAlgorithm = CryptoAlgorithm.AES,
    val detectedFormat: CryptoFormat = CryptoFormat.PLAINTEXT,
    val resultText: String = "",
    val error: String? = null,
    val isSecureMode: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class SmartEncrypterViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(EncrypterUiState())
    val uiState = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(
            inputText = text,
            detectedFormat = CryptoManager.detectFormat(text),
            error = null
        )
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun onAlgorithmSelected(algorithm: CryptoAlgorithm) {
        _uiState.value = _uiState.value.copy(selectedAlgorithm = algorithm, error = null)
    }

    fun toggleSecureMode() {
        _uiState.value = _uiState.value.copy(isSecureMode = !_uiState.value.isSecureMode)
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(resultText = "", error = null)
    }

    fun encrypt() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return
        if (state.selectedAlgorithm == CryptoAlgorithm.AES && state.password.isBlank()) {
            _uiState.value = state.copy(error = "Password required for AES")
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = when (state.selectedAlgorithm) {
                CryptoAlgorithm.AES -> CryptoManager.encryptAes(state.inputText, state.password.toCharArray()).getOrElse { 
                    it.message ?: "Encryption failed" 
                }
                CryptoAlgorithm.BASE64 -> CryptoManager.encodeBase64(state.inputText)
                CryptoAlgorithm.HEX -> CryptoManager.encodeHex(state.inputText)
                CryptoAlgorithm.BINARY -> CryptoManager.encodeBinary(state.inputText)
                CryptoAlgorithm.ROT13 -> CryptoManager.applyRot13(state.inputText)
            }
            _uiState.value = _uiState.value.copy(resultText = result, isLoading = false)
        }
    }

    fun decrypt() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return
        if (state.selectedAlgorithm == CryptoAlgorithm.AES && state.password.isBlank()) {
            _uiState.value = state.copy(error = "Password required for AES")
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = when (state.selectedAlgorithm) {
                CryptoAlgorithm.AES -> CryptoManager.decryptAes(state.inputText, state.password.toCharArray())
                CryptoAlgorithm.BASE64 -> CryptoManager.decodeBase64(state.inputText)
                CryptoAlgorithm.HEX -> CryptoManager.decodeHex(state.inputText)
                CryptoAlgorithm.BINARY -> CryptoManager.decodeBinary(state.inputText)
                CryptoAlgorithm.ROT13 -> Result.success(CryptoManager.applyRot13(state.inputText))
            }

            result.onSuccess {
                _uiState.value = _uiState.value.copy(resultText = it, isLoading = false)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    error = "Decoding failed: Check algorithm and input",
                    isLoading = false
                )
            }
        }
    }
}
