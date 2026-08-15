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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/** Which mode the converter is currently in. */
enum class ConversionMode { SINGLE, BATCH }

@HiltViewModel
class FileConverterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("file_converter_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        FileConverterUiState(recentConversions = loadHistory())
    )
    val uiState: StateFlow<FileConverterUiState> = _uiState.asStateFlow()

    // ── Mode switching ────────────────────────────────────────────────────────

    fun switchMode(mode: ConversionMode) {
        _uiState.update {
            it.copy(
                conversionMode = mode,
                selectedFiles = emptyList(),
                batchStagedFiles = emptyList(),
                conversionType = null,
                error = null,
            )
        }
    }

    // ── File selection ────────────────────────────────────────────────────────

    fun onFilesSelected(uris: List<Uri>) {
        viewModelScope.launch {
            val infos = uris.map { uri ->
                val name = resolveFileName(uri)
                val size = resolveFileSize(uri)
                val mime = context.contentResolver.getType(uri) ?: ""
                FileInfo(uri = uri, name = name, size = size, mimeType = mime)
            }
            val mode = _uiState.value.conversionMode
            if (mode == ConversionMode.BATCH) {
                // In BATCH mode: append to staged list
                val current = _uiState.value.batchStagedFiles
                _uiState.update { it.copy(batchStagedFiles = current + infos, error = null) }
            } else {
                // In SINGLE mode: replace selection
                _uiState.update { it.copy(selectedFiles = infos, error = null) }
            }
        }
    }

    fun removeFromBatch(file: FileInfo) {
        _uiState.update {
            it.copy(batchStagedFiles = it.batchStagedFiles - file)
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedFiles = emptyList(),
                batchStagedFiles = emptyList(),
                conversionType = null,
                error = null,
            )
        }
    }

    fun clearFormatSelection() {
        _uiState.update {
            it.copy(
                selectedFiles = if (it.conversionMode == ConversionMode.BATCH) emptyList() else emptyList(),
                conversionType = null,
                error = null,
            )
        }
    }

    fun prepareBatchForConversion() {
        _uiState.update {
            it.copy(
                selectedFiles = it.batchStagedFiles
            )
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
                selectedFiles     = if (it.conversionMode == ConversionMode.SINGLE) it.selectedFiles
                                    else it.batchStagedFiles,
                conversionType    = type,
                isConverting      = true,
                conversionSuccess = false,
                progress          = 0,
                queuePos          = 1,
                queueTotal        = uris.size,
                outputFiles       = emptyList(),
                error             = null,
                lastErrorMessage  = null,
                filesErrored      = 0,
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

    /** Start batch conversion using all staged files. */
    fun startBatchConversion(
        type: ConversionEngine.ConversionType,
        highQuality: Boolean,
    ) {
        val uris = _uiState.value.batchStagedFiles.map { it.uri }
        if (uris.isEmpty()) return
        startConversion(uris, type, highQuality)
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
        val isComplete = queuePos >= queueTotal && _uiState.value.filesErrored + updatedOutputs.size >= queueTotal

        val type = _uiState.value.conversionType
        val recent = if (type != null) {
            val entry = RecentConversion(
                outputPath = outputPath,
                label      = type.label,
                extension  = type.extension,
                category   = type.category,
            )
            (_uiState.value.recentConversions + entry).takeLast(10)
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

        if (isComplete) saveHistory(recent)
    }

    fun onConversionError(error: String, queuePos: Int, queueTotal: Int) {
        val newErrored = _uiState.value.filesErrored + 1
        val totalDone = _uiState.value.outputFiles.size + newErrored
        val isComplete = totalDone >= queueTotal

        _uiState.update {
            it.copy(
                isConverting      = !isComplete,
                conversionSuccess = isComplete && _uiState.value.outputFiles.isNotEmpty(),
                filesErrored      = newErrored,
                lastErrorMessage  = error,
                error             = if (isComplete && _uiState.value.outputFiles.isEmpty()) error else null,
                queuePos          = queuePos,
                queueTotal        = queueTotal,
            )
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        _uiState.update { current ->
            FileConverterUiState(
                conversionMode    = current.conversionMode,
                recentConversions = current.recentConversions,
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearHistory() {
        prefs.edit().remove("recent_conversions").apply()
        _uiState.update { it.copy(recentConversions = emptyList()) }
    }

    fun removeHistoryItem(recent: RecentConversion) {
        val updated = _uiState.value.recentConversions - recent
        _uiState.update { it.copy(recentConversions = updated) }
        saveHistory(updated)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun saveHistory(history: List<RecentConversion>) {
        val json = JSONArray()
        history.forEach { item ->
            json.put(JSONObject().apply {
                put("outputPath", item.outputPath)
                put("label", item.label)
                put("extension", item.extension)
                put("category", item.category)
                put("timestampMs", item.timestampMs)
            })
        }
        prefs.edit().putString("recent_conversions", json.toString()).apply()
    }

    private fun loadHistory(): List<RecentConversion> {
        return try {
            val json = prefs.getString("recent_conversions", null) ?: return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                RecentConversion(
                    outputPath  = obj.getString("outputPath"),
                    label       = obj.getString("label"),
                    extension   = obj.getString("extension"),
                    category    = obj.getString("category"),
                    timestampMs = obj.getLong("timestampMs"),
                )
            }
        } catch (_: Exception) { emptyList() }
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
    val conversionMode: ConversionMode              = ConversionMode.SINGLE,
    val selectedFiles: List<FileInfo>               = emptyList(),
    /** Files staged for batch conversion (populated in BATCH mode). */
    val batchStagedFiles: List<FileInfo>            = emptyList(),
    val conversionType: ConversionEngine.ConversionType? = null,
    val isConverting: Boolean                       = false,
    val conversionSuccess: Boolean                  = false,
    val progress: Int                               = 0,
    val queuePos: Int                               = 1,
    val queueTotal: Int                             = 1,
    val outputFiles: List<String>                   = emptyList(),
    val error: String?                              = null,
    /** The last error message (used in success+partial-error state). */
    val lastErrorMessage: String?                   = null,
    /** How many files in a batch errored. */
    val filesErrored: Int                           = 0,
    val recentConversions: List<RecentConversion>   = emptyList(),
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
