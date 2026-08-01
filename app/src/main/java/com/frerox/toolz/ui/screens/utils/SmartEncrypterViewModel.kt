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

package com.frerox.toolz.ui.screens.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.frerox.toolz.data.crypto.CryptoDao
import com.frerox.toolz.data.crypto.CryptoHistoryEntry
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.CryptoManager
import com.frerox.toolz.util.CryptoManager.CryptoAlgorithm
import com.frerox.toolz.util.CryptoManager.CryptoFormat
import com.frerox.toolz.util.CryptoManager.CryptoOperation
import java.io.File
import java.io.FileOutputStream
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
    val isQrLoading: Boolean = false,
    val isFileMode: Boolean = false,
    val isFilePermissionGranted: Boolean = false,
    val isProcessingFile: Boolean = false,
    val fileProcessingProgress: Float = 0f,
    val fileProcessingStatus: String = "",
    val lastTextAlgorithm: CryptoAlgorithm? = null,
    val fileOperationIntent: CryptoOperation? = null,
    val isRenamerEnabled: Boolean = false,
    val customFileName: String = "",
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val selectedFileSize: Long = 0,
    val processedFile: File? = null,
    val foundEncFiles: List<File> = emptyList(),
    val isSearchingEncFiles: Boolean = false
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
    private var fileProcessJob: Job? = null

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
        if (_uiState.value.isFileMode) return // Don't process text in file mode
        
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

    fun toggleFileMode() {
        // Cancel everything
        qrGenerationJob?.cancel()
        liveProcessJob?.cancel()
        fileProcessJob?.cancel()
        autoClearJob?.cancel()

        val currentIsFileMode = _uiState.value.isFileMode
        
        _uiState.update { 
            it.copy(
                isFileMode = !currentIsFileMode,
                inputText = "",
                password = "",
                resultText = "",
                error = null,
                fileProcessingStatus = "",
                fileProcessingProgress = 0f,
                isManualSelectionActive = false,
                qrCode = null,
                fileOperationIntent = null,
                isRenamerEnabled = false,
                customFileName = "",
                selectedFileUri = null,
                selectedFileName = null,
                selectedFileSize = 0,
                processedFile = null,
                lastTextAlgorithm = if (!currentIsFileMode) it.selectedAlgorithm else it.lastTextAlgorithm
            )
        }
        
        // Logical Switch
        if (_uiState.value.isFileMode) {
            // Switching to File Mode: Force stream-safe algorithm
            if (!listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20).contains(_uiState.value.selectedAlgorithm)) {
                onAlgorithmSelected(CryptoAlgorithm.AES)
            }
        } else {
            // Switching back to Text Mode: Restore previous algorithm
            _uiState.value.lastTextAlgorithm?.let { onAlgorithmSelected(it) }
        }
    }

    fun setFileOperationIntent(operation: CryptoOperation?) {
        _uiState.update { it.copy(fileOperationIntent = operation) }
    }

    fun toggleRenamer() {
        _uiState.update { it.copy(isRenamerEnabled = !it.isRenamerEnabled) }
    }

    fun onCustomFileNameChanged(name: String) {
        _uiState.update { it.copy(customFileName = name) }
    }

    fun handleExternalUri(context: Context, uri: Uri) {
        val fileName = getFileName(context, uri) ?: "unknown_file.enc"
        val isEncFile = fileName.lowercase().endsWith(".enc")
        
        // Force file mode and decrypt intent for external .enc files
        _uiState.update { 
            it.copy(
                isFileMode = true,
                fileOperationIntent = if (isEncFile) CryptoOperation.DECRYPT else CryptoOperation.ENCRYPT,
                selectedFileUri = uri,
                selectedFileName = fileName,
                selectedFileSize = getFileSize(context, uri),
                processedFile = null,
                error = null
            )
        }
        // Force stream-safe algorithm if needed
        if (!listOf(CryptoAlgorithm.AES, CryptoAlgorithm.CHACHA20).contains(_uiState.value.selectedAlgorithm)) {
            onAlgorithmSelected(CryptoAlgorithm.AES)
        }
    }

    fun onFileSelected(context: Context, uri: Uri) {
        val fileName = getFileName(context, uri)
        val suggestedOp = if (fileName != null) suggestFileOperation(fileName) else null
        
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = fileName,
                selectedFileSize = getFileSize(context, uri),
                fileProcessingStatus = "",
                processedFile = null,
                error = if (it.fileOperationIntent == CryptoOperation.DECRYPT && suggestedOp == CryptoOperation.ENCRYPT) {
                    "Warning: This file doesn't look like an .enc file"
                } else null
            )
        }
    }

    fun scanForEncFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearchingEncFiles = true, foundEncFiles = emptyList()) }
            
            val root = Environment.getExternalStorageDirectory()
            val found = mutableListOf<File>()
            
            // Skip "Android" folder and hidden system folders to improve performance and privacy
            scanDirectory(root, 0, 5, found, skipFolders = setOf("Android", "LOST.DIR"))
            
            val sorted = found.sortedByDescending { it.lastModified() }
            
            _uiState.update { 
                it.copy(
                    isSearchingEncFiles = false,
                    foundEncFiles = sorted
                )
            }
        }
    }

    private fun scanDirectory(dir: File, currentDepth: Int, maxDepth: Int, found: MutableList<File>, skipFolders: Set<String> = emptySet()) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory) return
        
        // Skip hidden folders and specific blacklisted system folders
        if (dir.name.startsWith(".") || skipFolders.contains(dir.name)) return

        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirectory(file, currentDepth + 1, maxDepth, found, skipFolders)
            } else if (file.name.lowercase().endsWith(".enc")) {
                found.add(file)
            }
        }
    }

    fun onEncFileSelected(file: File, context: Context) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
            onFileSelected(context, uri)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to access file: ${e.message}") }
        }
    }

    fun clearFileSelection() {
        _uiState.update {
            it.copy(
                selectedFileUri = null,
                selectedFileName = null,
                selectedFileSize = 0,
                fileProcessingStatus = "",
                fileProcessingProgress = 0f,
                processedFile = null,
                error = null
            )
        }
    }

    fun updateFilePermissionStatus(granted: Boolean) {
        _uiState.update { it.copy(isFilePermissionGranted = granted) }
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

    fun cancelFileProcess() {
        fileProcessJob?.cancel()
        _uiState.update { 
            it.copy(
                isProcessingFile = false,
                fileProcessingStatus = "Operation cancelled"
            )
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
        val state = _uiState.value
        if (state.resultText.isBlank() || state.isFileMode) return

        qrGenerationJob?.cancel()
        qrGenerationJob = viewModelScope.launch(Dispatchers.Default) {
            if (debounce) {
                delay(300)
            }
            
            _uiState.update { it.copy(isQrLoading = true) }
            
            val state = _uiState.value
            val qr = CryptoManager.generateQrCode(
                state.resultText,
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

    fun processFile(context: Context) {
        val state = _uiState.value
        val uri = state.selectedFileUri ?: return
        val operation = state.fileOperationIntent ?: return

        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "Password required for file processing") }
            return
        }

        if (state.isRenamerEnabled && state.customFileName.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a custom output name") }
            return
        }

        fileProcessJob?.cancel()
        fileProcessJob = viewModelScope.launch(Dispatchers.IO) {
            var outputFile: File? = null
            try {
                _uiState.update { 
                    it.copy(
                        isProcessingFile = true, 
                        fileProcessingProgress = 0f,
                        fileProcessingStatus = "Preparing (Deriving Key)..."
                    )
                }

                val contentResolver = context.contentResolver
                val totalSize = state.selectedFileSize
                
                if (totalSize <= 0) throw Exception("File is empty")
                if (totalSize > 2L * 1024 * 1024 * 1024) throw Exception("File exceeds 2GB limit")

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val toolzDir = File(downloadsDir, "Toolz").apply { if (!exists()) mkdirs() }
                
                if (!hasEnoughSpace(toolzDir, (totalSize * 1.1).toLong())) {
                    throw Exception("Not enough storage space")
                }

                val originalFileName = state.selectedFileName ?: "file_${System.currentTimeMillis()}"
                
                val fileName = if (state.isRenamerEnabled && state.customFileName.isNotBlank()) {
                    val input = state.customFileName
                    if (operation == CryptoOperation.DECRYPT) {
                        // For decryption, if user didn't provide extension, try to recover from original
                        if (!input.contains(".")) {
                            val originalWithoutEnc = originalFileName.removeSuffix(".enc").removeSuffix(".ENC")
                            val originalExt = originalWithoutEnc.substringAfterLast('.', "")
                            if (originalExt.isNotEmpty()) "$input.$originalExt" else input
                        } else {
                            input
                        }
                    } else {
                        input
                    }
                } else {
                    originalFileName
                }

                val baseOutputName = if (operation == CryptoOperation.ENCRYPT) {
                    if (fileName.lowercase().endsWith(".enc")) fileName else "$fileName.enc"
                } else {
                    // Remove .enc safely, ensuring we don't just leave a blank name or a weird one
                    if (fileName.lowercase().endsWith(".enc")) {
                        fileName.substring(0, fileName.length - 4)
                    } else {
                        // If it doesn't end in .enc but we're decrypting, 
                        // it might be a malformed intent or manual rename.
                        // Append .dec as safety if we can't determine extension
                        if (!fileName.contains(".")) "$fileName.dec" else fileName
                    }
                }

                outputFile = getUniqueOutputFile(toolzDir, baseOutputName)
                
                val success = contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        _uiState.update { it.copy(fileProcessingStatus = if (operation == CryptoOperation.ENCRYPT) "Encrypting..." else "Decrypting...") }
                        when (state.selectedAlgorithm) {
                            CryptoAlgorithm.AES -> {
                                if (operation == CryptoOperation.ENCRYPT) {
                                    CryptoManager.encryptStreamAes(inputStream, outputStream, state.password.toCharArray(), totalSize, { progress ->
                                        _uiState.update { it.copy(fileProcessingProgress = progress) }
                                    })
                                } else {
                                    CryptoManager.decryptStreamAes(inputStream, outputStream, state.password.toCharArray(), totalSize, { progress ->
                                        _uiState.update { it.copy(fileProcessingProgress = progress) }
                                    })
                                }
                            }
                            CryptoAlgorithm.CHACHA20 -> {
                                if (operation == CryptoOperation.ENCRYPT) {
                                    CryptoManager.encryptStreamChaCha20(inputStream, outputStream, state.password.toCharArray(), totalSize, { progress ->
                                        _uiState.update { it.copy(fileProcessingProgress = progress) }
                                    })
                                } else {
                                    CryptoManager.decryptStreamChaCha20(inputStream, outputStream, state.password.toCharArray(), totalSize, { progress ->
                                        _uiState.update { it.copy(fileProcessingProgress = progress) }
                                    })
                                }
                            }
                            else -> throw Exception("Algorithm ${state.selectedAlgorithm} doesn't support streaming")
                        }
                    }
                } ?: throw Exception("Failed to open file streams")

                if (success) {
                    _uiState.update { 
                        it.copy(
                            isProcessingFile = false,
                            fileProcessingStatus = "Successfully processed: ${outputFile!!.name}",
                            processedFile = outputFile,
                            error = null
                        )
                    }
                } else {
                    outputFile?.delete() // Cleanup blank/partial file
                    throw Exception("Decryption failed. Ensure your password is correct.")
                }
            } catch (e: Exception) {
                // If it was cancelled, delete partial file
                if (fileProcessJob?.isCancelled == true) {
                    outputFile?.delete()
                    _uiState.update { it.copy(isProcessingFile = false, fileProcessingStatus = "Operation cancelled") }
                } else {
                    outputFile?.delete()
                    val errorMessage = when (e.message) {
                        "AUTH_FAILURE" -> "Invalid password or corrupted file"
                        else -> "File processing failed: ${e.message}"
                    }
                    _uiState.update { 
                        it.copy(
                            isProcessingFile = false,
                            error = errorMessage
                        )
                    }
                }
            }
        }
    }

    private fun getUniqueOutputFile(dir: File, baseName: String): File {
        var file = File(dir, baseName)
        if (!file.exists()) return file

        val nameWithoutExt = baseName.substringBeforeLast('.')
        val ext = baseName.substringAfterLast('.', "")
        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
        
        var counter = 1
        while (file.exists()) {
            file = File(dir, "$nameWithoutExt($counter)$suffix")
            counter++
        }
        return file
    }

    private fun hasEnoughSpace(dir: File, requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(dir.path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            available > requiredBytes
        } catch (e: Exception) {
            true // Fallback to try anyway if StatFs fails
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun suggestFileOperation(fileName: String): CryptoOperation {
        return if (fileName.endsWith(".enc", ignoreCase = true)) {
            CryptoOperation.DECRYPT
        } else {
            CryptoOperation.ENCRYPT
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index != -1) size = cursor.getLong(index)
            cursor.close()
        }
        return size
    }
}
