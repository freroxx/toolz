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

package com.frerox.toolz.ui.screens.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.service.FileConversionService
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileConverterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FileConverterUiState())
    val uiState: StateFlow<FileConverterUiState> = _uiState.asStateFlow()

    // ── File selection ────────────────────────────────────────────────────────

    fun onFilesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val infos = uris.map { uri ->
                val name = resolveFileName(uri)
                val size = resolveFileSize(uri)
                val mime = context.contentResolver.getType(uri) ?: ""
                FileInfo(uri = uri, name = name, size = size, mimeType = mime)
            }
            _uiState.update { it.copy(selectedFiles = infos, error = null) }
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(selectedFiles = emptyList(), conversionType = null, error = null)
        }
    }

    // ── Conversion ────────────────────────────────────────────────────────────

    fun startConversion(
        uris: List<Uri>,
        type: ConversionEngine.ConversionType,
        highQuality: Boolean,
    ) {
        _uiState.update {
            it.copy(
                conversionType    = type,
                isConverting      = true,
                conversionSuccess = false,
                progress          = 0,
                queuePos          = 1,
                queueTotal        = uris.size,
                outputFiles       = emptyList(),
                error             = null,
            )
        }

        val intent = Intent(context, FileConversionService::class.java).apply {
            putParcelableArrayListExtra("input_uris", ArrayList(uris))
            putExtra("conversion_type", type.name)
            putExtra("high_quality", highQuality)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancelConversion() {
        context.stopService(Intent(context, FileConversionService::class.java))
        _uiState.update {
            it.copy(isConverting = false, progress = 0, error = "Conversion cancelled")
        }
    }

    // ── Broadcast callbacks (called from Screen's BroadcastReceiver) ──────────

    fun onConversionProgress(progress: Int, queuePos: Int, queueTotal: Int) {
        _uiState.update {
            it.copy(progress = progress, queuePos = queuePos, queueTotal = queueTotal)
        }
    }

    fun onConversionSuccess(outputPath: String, queuePos: Int, queueTotal: Int) {
        val updatedOutputs = _uiState.value.outputFiles + outputPath
        val isComplete = queuePos >= queueTotal

        // Add to recent conversions
        val type = _uiState.value.conversionType
        val recent = if (type != null) {
            val entry = RecentConversion(
                outputPath = outputPath,
                label      = type.label,
                extension  = type.extension,
                category   = type.category,
            )
            (_uiState.value.recentConversions + entry).takeLast(5)
        } else {
            _uiState.value.recentConversions
        }

        _uiState.update {
            it.copy(
                isConverting      = !isComplete,
                conversionSuccess = isComplete,
                progress          = if (isComplete) 100 else it.progress,
                queuePos          = queuePos,
                queueTotal        = queueTotal,
                outputFiles       = updatedOutputs,
                recentConversions = recent,
                error             = null,
            )
        }
    }

    fun onConversionError(error: String) {
        _uiState.update {
            it.copy(isConverting = false, conversionSuccess = false, error = error, progress = 0)
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        _uiState.update { current ->
            FileConverterUiState(recentConversions = current.recentConversions)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path!!).name
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (idx >= 0) cursor.getString(idx) else uri.lastPathSegment ?: "file"
            } ?: uri.lastPathSegment ?: "file"
        } catch (_: Exception) { uri.lastPathSegment ?: "file" }
    }

    private fun resolveFileSize(uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path!!).length()
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                cursor.moveToFirst()
                if (idx >= 0) cursor.getLong(idx) else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }
}

// ── State models ──────────────────────────────────────────────────────────────

data class FileConverterUiState(
    val selectedFiles: List<FileInfo>           = emptyList(),
    val conversionType: ConversionEngine.ConversionType? = null,
    val isConverting: Boolean                   = false,
    val conversionSuccess: Boolean              = false,
    val progress: Int                           = 0,
    val queuePos: Int                           = 1,
    val queueTotal: Int                         = 1,
    val outputFiles: List<String>               = emptyList(),
    val error: String?                          = null,
    val recentConversions: List<RecentConversion> = emptyList(),
)

data class FileInfo(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
)

data class RecentConversion(
    val outputPath: String,
    val label: String,
    val extension: String,
    val category: String,
    val timestampMs: Long = System.currentTimeMillis(),
)
