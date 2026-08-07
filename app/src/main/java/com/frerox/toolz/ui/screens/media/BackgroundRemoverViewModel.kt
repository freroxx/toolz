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

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.util.BackgroundRemoverEngine
import com.frerox.toolz.util.ImageUtils
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter.ImageSegmenterOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Pro ViewModel for Background Remover using MediaPipe Selfie Multiclass.
 */
@HiltViewModel
class BackgroundRemoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundRemoverUiState())
    val uiState: StateFlow<BackgroundRemoverUiState> = _uiState.asStateFlow()

    private var imageSegmenter: ImageSegmenter? = null
    private var activeJob: Job? = null

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("selfie_multiclass.tflite")
                    .setDelegate(Delegate.CPU)
                    .build()
                val options = ImageSegmenterOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setOutputConfidenceMasks(true)
                    .build()
                imageSegmenter = ImageSegmenter.createFromOptions(context, options)
            } catch (e: Throwable) {
                e.printStackTrace()
                val msg = e.localizedMessage ?: e.message ?: e::class.java.simpleName
                val cause = e.cause?.let { " [Cause: ${it.localizedMessage ?: it::class.java.simpleName}]" } ?: ""
                _uiState.update { it.copy(error = "MediaPipe init failed ($msg$cause). Ensure 'selfie_multiclass.tflite' is in your app/src/main/assets/ folder.") }
            }
        }
    }

    fun onImageSelected(uri: Uri) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { BackgroundRemoverUiState(isProcessing = true) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.loadOptimizedBitmap(context, uri)
                } ?: run {
                    _uiState.update { it.copy(isProcessing = false, error = "Invalid image file.") }
                    return@launch
                }

                _uiState.update { it.copy(originalBitmap = bitmap) }
                processImage(bitmap)
            } catch (ce: CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessing = false, error = "Selection error: ${e.localizedMessage}") }
            }
        }
    }

    private suspend fun processImage(bitmap: Bitmap) {
        if (imageSegmenter == null) {
            withContext(Dispatchers.Default) {
                try {
                    val baseOptions = BaseOptions.builder()
                        .setModelAssetPath("selfie_multiclass.tflite")
                        .setDelegate(Delegate.CPU)
                        .build()
                    val options = ImageSegmenterOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.IMAGE)
                        .setOutputConfidenceMasks(true)
                        .build()
                    imageSegmenter = ImageSegmenter.createFromOptions(context, options)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    val msg = e.localizedMessage ?: e.message ?: e::class.java.simpleName
                    val cause = e.cause?.let { " [Cause: ${it.localizedMessage ?: it::class.java.simpleName}]" } ?: ""
                    _uiState.update { it.copy(isProcessing = false, error = "MediaPipe init failed ($msg$cause).") }
                    return@withContext
                }
            }
        }

        val segmenter = imageSegmenter ?: return

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = withContext(Dispatchers.Default) {
                segmenter.segment(mpImage)
            }
            
            val masks = result.confidenceMasks().get()
            if (masks.isEmpty() || masks.size < 6) {
                _uiState.update { it.copy(isProcessing = false, error = "No subject detected.") }
                return
            }

            // High Fidelity Foreground Extraction: Sum categories 1-5 (Background is 0)
            val maskW = masks[0].width
            val maskH = masks[0].height
            val combinedMask = FloatArray(maskW * maskH)
            
            // Extract Background (Index 0) and invert to get the person
            val buf = ByteBufferExtractor.extract(masks[0])
            buf.order(ByteOrder.nativeOrder())
            val fb = buf.asFloatBuffer()
            fb.rewind()
            
            var maxPersonConf = 0f
            var personPixels = 0
            
            for (i in combinedMask.indices) {
                val conf = (1.0f - fb.get()).coerceIn(0f, 1f)
                combinedMask[i] = conf
                if (conf > 0.45f) personPixels++
                if (conf > maxPersonConf) maxPersonConf = conf
            }

            // PRO Compatibility Check
            val totalPixels = maskW * maskH
            val areaRatio = personPixels.toFloat() / totalPixels
            
            // Requires clear person (>45% confidence) and reasonable size (>0.2% frame)
            if (maxPersonConf < 0.45f || areaRatio < 0.002f) {
                _uiState.update { it.copy(isProcessing = false, error = "No person detected. Try a better portrait or selfie.") }
                return
            }

            val resultBitmap = BackgroundRemoverEngine.removeBackground(
                source = bitmap,
                maskArray = combinedMask,
                maskW = maskW,
                maskH = maskH
            )
            
            _uiState.update { it.copy(isProcessing = false, resultBitmap = resultBitmap) }
        } catch (ce: CancellationException) {
            // Ignore
        } catch (e: Exception) {
            _uiState.update { it.copy(isProcessing = false, error = "Cutout error: ${e.localizedMessage}") }
        }
    }

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val success = withContext(Dispatchers.IO) { saveImageToGallery(bitmap) }
            _uiState.update { it.copy(isProcessing = false, saveSuccess = success, error = if (!success) "Save failed." else null) }
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap): Boolean {
        val filename = "TOOLZ_BG_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Toolz")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    fun clearResult() {
        activeJob?.cancel()
        _uiState.update { BackgroundRemoverUiState() }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        imageSegmenter?.close()
    }
}

data class BackgroundRemoverUiState(
    val originalBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
)
