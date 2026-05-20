package com.frerox.toolz.ui.screens.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.frerox.toolz.data.crypto.CryptoDao
import com.frerox.toolz.data.crypto.CryptoHistoryEntry
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.CryptoManager
import com.frerox.toolz.util.CryptoManager.CryptoAlgorithm
import com.frerox.toolz.util.CryptoManager.CryptoFormat
import com.frerox.toolz.util.CryptoManager.CryptoOperation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class EncrypterUiState(
    val inputText: String = "",
    val password: String = "",
    val passwordStrength: Float = 0f,
    val selectedAlgorithm: CryptoAlgorithm = CryptoAlgorithm.AES,
    val detectedFormat: CryptoFormat = CryptoFormat.PLAINTEXT,
    val suggestedOperation: CryptoOperation = CryptoOperation.ENCRYPT,
    val resultText: String = "",
    val error: String? = null,
    val isSecureMode: Boolean = false,
    val isLoading: Boolean = false,
    val isLiveEnabled: Boolean = true,
    val isSmartAutoEnabled: Boolean = true,
    val autoClearSeconds: Int = 0,
    val isAutoClearEnabled: Boolean = false,
    val qrCode: Bitmap? = null,
    val qrForeColor: Int = AndroidColor.BLACK,
    val qrBackColor: Int = AndroidColor.WHITE,
    val qrStyle: String = "SQUARE",
    val isAlgorithmSectionExpanded: Boolean = true,
    val qrNoteText: String = "",
    val qrNoteSize: Float = 16f,
    val qrNotePosition: String = "BOTTOM",
    val isQrNoteEnabled: Boolean = false,
    val isManualSelectionActive: Boolean = false,
    val isQrLoading: Boolean = false
)

@HiltViewModel
class SmartEncrypterViewModel @Inject constructor(
    private val cryptoDao: CryptoDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EncrypterUiState())
    val uiState = _uiState.asStateFlow()

    val history: StateFlow<List<CryptoHistoryEntry>> = cryptoDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var autoClearJob: Job? = null
    private var liveProcessJob: Job? = null
    private var qrGenerationJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.lastCryptoAlgorithm.first()?.let { savedAlgo ->
                try {
                    val algo = CryptoAlgorithm.valueOf(savedAlgo)
                    _uiState.value = _uiState.value.copy(selectedAlgorithm = algo)
                } catch (e: Exception) {}
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update {
            it.copy(
                inputText = text,
                detectedFormat = CryptoManager.detectFormat(text),
                suggestedOperation = CryptoManager.suggestOperation(text, it.selectedAlgorithm),
                error = null,
                isManualSelectionActive = false
            )
        }
        if (_uiState.value.isLiveEnabled) {
            triggerLiveProcess()
        }
        if (_uiState.value.resultText.isNotBlank()) {
            generateQr(debounce = true)
        }
    }

    private fun triggerLiveProcess() {
        liveProcessJob?.cancel()
        liveProcessJob = viewModelScope.launch {
            delay(500) // Debounce
            if (_uiState.value.isSmartAutoEnabled) {
                if (_uiState.value.suggestedOperation == CryptoOperation.DECRYPT || 
                    _uiState.value.suggestedOperation == CryptoOperation.DECODE) {
                    decrypt()
                } else {
                    encrypt()
                }
            } else {
                // If not smart auto, we don't know if encrypt or decrypt is intended
                // maybe just don't do anything or default to encrypt
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordStrength = CryptoManager.calculatePasswordStrength(password),
                error = null
            )
        }
        if (_uiState.value.isLiveEnabled) triggerLiveProcess()
    }

    fun onAlgorithmSelected(algorithm: CryptoAlgorithm) {
        _uiState.update {
            it.copy(
                selectedAlgorithm = algorithm,
                error = null,
                suggestedOperation = CryptoManager.suggestOperation(it.inputText, algorithm),
                isManualSelectionActive = true
            )
        }
        viewModelScope.launch {
            settingsRepository.setLastCryptoAlgorithm(algorithm.name)
        }
        if (_uiState.value.isLiveEnabled) triggerLiveProcess()
    }

    fun toggleSecureMode() {
        _uiState.update { it.copy(isSecureMode = !it.isSecureMode) }
    }

    fun toggleLiveMode() {
        _uiState.update { it.copy(isLiveEnabled = !it.isLiveEnabled) }
    }

    fun toggleSmartAuto() {
        _uiState.update { it.copy(isSmartAutoEnabled = !it.isSmartAutoEnabled) }
    }

    fun updateQrCustomization(
        foreColor: Int,
        backColor: Int,
        style: String,
        noteText: String = _uiState.value.qrNoteText,
        noteSize: Float = _uiState.value.qrNoteSize,
        notePosition: String = _uiState.value.qrNotePosition,
        isNoteEnabled: Boolean = _uiState.value.isQrNoteEnabled
    ) {
        _uiState.update {
            it.copy(
                qrForeColor = foreColor,
                qrBackColor = backColor,
                qrStyle = style,
                qrNoteText = noteText,
                qrNoteSize = noteSize,
                qrNotePosition = notePosition,
                isQrNoteEnabled = isNoteEnabled
            )
        }
        generateQr(debounce = true)
    }

    fun toggleAlgorithmSection() {
        _uiState.update { it.copy(isAlgorithmSectionExpanded = !it.isAlgorithmSectionExpanded) }
    }

    fun toggleAutoClear() {
        _uiState.update {
            val newState = !it.isAutoClearEnabled
            if (!newState) {
                autoClearJob?.cancel()
                it.copy(isAutoClearEnabled = false, autoClearSeconds = 0)
            } else {
                it.copy(isAutoClearEnabled = true)
            }
        }
    }

    fun clearAll() {
        _uiState.update { current ->
            EncrypterUiState(
                isSecureMode = current.isSecureMode,
                isAutoClearEnabled = current.isAutoClearEnabled,
                isLiveEnabled = current.isLiveEnabled,
                isSmartAutoEnabled = current.isSmartAutoEnabled,
                selectedAlgorithm = current.selectedAlgorithm
            )
        }
        autoClearJob?.cancel()
        liveProcessJob?.cancel()
        qrGenerationJob?.cancel()
    }

    fun clearResult() {
        _uiState.update { it.copy(resultText = "", qrCode = null) }
        qrGenerationJob?.cancel()
    }

    fun generateQr(debounce: Boolean = false) {
        val result = _uiState.value.resultText
        if (result.isBlank()) return

        qrGenerationJob?.cancel()
        qrGenerationJob = viewModelScope.launch(Dispatchers.Default) {
            if (debounce) {
                delay(300)
            }
            
            _uiState.update { it.copy(isQrLoading = true) }
            
            val state = _uiState.value
            val qr = CryptoManager.generateQrCode(
                result,
                1024,
                state.qrForeColor,
                state.qrBackColor,
                state.qrStyle,
                state.qrNoteText,
                state.qrNoteSize,
                state.qrNotePosition
            )
            
            _uiState.update { 
                it.copy(
                    qrCode = qr,
                    isQrLoading = false
                )
            }
        }
    }

    fun deleteHistoryEntry(entry: CryptoHistoryEntry) {
        viewModelScope.launch {
            cryptoDao.deleteEntry(entry)
        }
    }

    fun restoreHistoryEntry(entry: CryptoHistoryEntry) {
        _uiState.update {
            it.copy(
                inputText = entry.input,
                resultText = entry.result,
                selectedAlgorithm = CryptoAlgorithm.valueOf(entry.algorithm),
                detectedFormat = CryptoManager.detectFormat(entry.input),
                suggestedOperation = CryptoManager.suggestOperation(entry.input, CryptoAlgorithm.valueOf(entry.algorithm)),
                error = null
            )
        }
        generateQr()
    }

    fun clearHistory() {
        viewModelScope.launch {
            cryptoDao.clearHistory()
        }
    }

    fun encrypt() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return
        if (listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20).contains(state.selectedAlgorithm) && state.password.isBlank()) {
            if (!state.isLiveEnabled) {
                _uiState.update { it.copy(error = "Password required for selected algorithm") }
            }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            if (!state.isLiveEnabled) _uiState.update { it.copy(isLoading = true, error = null) }
            val resultPair: Pair<String, Boolean> = when (state.selectedAlgorithm) {
                CryptoAlgorithm.AES -> CryptoManager.encryptAes(state.inputText, state.password.toCharArray())
                CryptoAlgorithm.CHACHA20 -> CryptoManager.encryptChaCha20(state.inputText, state.password.toCharArray())
                CryptoAlgorithm.BASE64 -> Pair(CryptoManager.encodeBase64(state.inputText), true)
                CryptoAlgorithm.HEX -> Pair(CryptoManager.encodeHex(state.inputText), true)
                CryptoAlgorithm.BINARY -> Pair(CryptoManager.encodeBinary(state.inputText), true)
                CryptoAlgorithm.ROT13 -> Pair(CryptoManager.applyRot13(state.inputText), true)
                CryptoAlgorithm.MD5 -> Pair(CryptoManager.hashMd5(state.inputText), true)
                CryptoAlgorithm.SHA1 -> Pair(CryptoManager.hashSha1(state.inputText), true)
                CryptoAlgorithm.SHA256 -> Pair(CryptoManager.hashSha256(state.inputText), true)
                CryptoAlgorithm.SHA512 -> Pair(CryptoManager.hashSha512(state.inputText), true)
                CryptoAlgorithm.URL -> Pair(CryptoManager.encodeUrl(state.inputText), true)
                CryptoAlgorithm.MORSE -> Pair(CryptoManager.encodeMorse(state.inputText), true)
                CryptoAlgorithm.BASE32 -> Pair(CryptoManager.encodeBase32(state.inputText), true)
            }
            val result = resultPair.first
            val success = resultPair.second

            if (success) {
                val operationType = when (state.selectedAlgorithm) {
                    CryptoAlgorithm.MD5, CryptoAlgorithm.SHA1, CryptoAlgorithm.SHA256, CryptoAlgorithm.SHA512 -> "HASH"
                    CryptoAlgorithm.BASE64, CryptoAlgorithm.HEX, CryptoAlgorithm.BINARY, CryptoAlgorithm.URL, CryptoAlgorithm.MORSE, CryptoAlgorithm.BASE32 -> "ENCODE"
                    else -> "ENCRYPT"
                }
                
                cryptoDao.insertEntry(CryptoHistoryEntry(
                    input = state.inputText,
                    result = result,
                    algorithm = state.selectedAlgorithm.name,
                    type = operationType,
                    isSuccess = true
                ))
                _uiState.update {
                    it.copy(
                        resultText = result,
                        isLoading = false,
                        error = null
                    )
                }
                if (!state.isLiveEnabled) startAutoClearTimer()
                generateQr()
            } else {
                _uiState.update {
                    it.copy(
                        error = if (!state.isLiveEnabled) result else null,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun decrypt() {
        val state = _uiState.value
        if (state.inputText.isBlank()) return
        if (listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20).contains(state.selectedAlgorithm) && state.password.isBlank()) {
            if (!state.isLiveEnabled) {
                _uiState.update { it.copy(error = "Password required for selected algorithm") }
            }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            if (!state.isLiveEnabled) _uiState.update { it.copy(isLoading = true, error = null) }
            val resultPair: Pair<String, Boolean> = when (state.selectedAlgorithm) {
                CryptoAlgorithm.AES -> CryptoManager.decryptAes(state.inputText, state.password.toCharArray())
                CryptoAlgorithm.CHACHA20 -> CryptoManager.decryptChaCha20(state.inputText, state.password.toCharArray())
                CryptoAlgorithm.BASE64 -> CryptoManager.decodeBase64(state.inputText)
                CryptoAlgorithm.HEX -> CryptoManager.decodeHex(state.inputText)
                CryptoAlgorithm.BINARY -> CryptoManager.decodeBinary(state.inputText)
                CryptoAlgorithm.ROT13 -> Pair(CryptoManager.applyRot13(state.inputText), true)
                CryptoAlgorithm.MD5, CryptoAlgorithm.SHA1, CryptoAlgorithm.SHA256, CryptoAlgorithm.SHA512 ->
                    Pair("Hash functions are one-way and cannot be decrypted", false)
                CryptoAlgorithm.URL -> CryptoManager.decodeUrl(state.inputText)
                CryptoAlgorithm.MORSE -> CryptoManager.decodeMorse(state.inputText)
                CryptoAlgorithm.BASE32 -> CryptoManager.decodeBase32(state.inputText)
            }
            val result = resultPair.first
            val success = resultPair.second

            if (success) {
                val operationType = when (state.selectedAlgorithm) {
                    CryptoAlgorithm.ROT13 -> "ENCODE"
                    CryptoAlgorithm.BASE64, CryptoAlgorithm.HEX, CryptoAlgorithm.BINARY, CryptoAlgorithm.URL, CryptoAlgorithm.MORSE, CryptoAlgorithm.BASE32 -> "DECODE"
                    else -> "DECRYPT"
                }

                cryptoDao.insertEntry(CryptoHistoryEntry(
                    input = state.inputText,
                    result = result,
                    algorithm = state.selectedAlgorithm.name,
                    type = operationType,
                    isSuccess = true
                ))
                _uiState.update {
                    it.copy(
                        resultText = result,
                        isLoading = false,
                        error = null
                    )
                }
                if (!state.isLiveEnabled) startAutoClearTimer()
                generateQr()
            } else {
                _uiState.update {
                    it.copy(
                        error = if (!state.isLiveEnabled) result else null,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun startAutoClearTimer() {
        if (!_uiState.value.isAutoClearEnabled) return
        autoClearJob?.cancel()
        autoClearJob = viewModelScope.launch {
            for (i in 30 downTo 1) {
                _uiState.value = _uiState.value.copy(autoClearSeconds = i)
                delay(1000)
            }
            _uiState.value = _uiState.value.copy(resultText = "", autoClearSeconds = 0)
        }
    }
}
