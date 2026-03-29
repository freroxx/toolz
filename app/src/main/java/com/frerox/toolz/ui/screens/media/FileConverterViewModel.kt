package com.frerox.toolz.ui.screens.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import com.frerox.toolz.service.FileConversionService
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class FileConverterViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileConverterUiState())
    val uiState: StateFlow<FileConverterUiState> = _uiState.asStateFlow()

    fun selectFile(uri: Uri, type: ConversionEngine.ConversionType, highQuality: Boolean) {
        _uiState.value = _uiState.value.copy(
            selectedFileUri = uri,
            conversionType = type,
            isConverting = true,
            conversionSuccess = false,
            progress = 0,
            error = null
        )
        
        val intent = Intent(context, FileConversionService::class.java).apply {
            putExtra("input_uri", uri)
            putExtra("conversion_type", type.name)
            putExtra("high_quality", highQuality)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun onConversionProgress(progress: Int) {
        _uiState.value = _uiState.value.copy(progress = progress)
    }

    fun onConversionFinished(success: Boolean, outputPath: String?, error: String?) {
        _uiState.value = _uiState.value.copy(
            isConverting = false,
            conversionSuccess = success,
            outputPath = outputPath,
            progress = if (success) 100 else 0,
            error = error
        )
    }

    fun reset() {
        _uiState.value = FileConverterUiState()
    }
}

data class FileConverterUiState(
    val selectedFileUri: Uri? = null,
    val conversionType: ConversionEngine.ConversionType? = null,
    val isConverting: Boolean = false,
    val conversionSuccess: Boolean = false,
    val progress: Int = 0,
    val outputPath: String? = null,
    val error: String? = null
)
