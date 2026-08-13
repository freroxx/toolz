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
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.util.BackgroundRemoverEngine
import com.frerox.toolz.util.ImageUtils
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import org.tensorflow.lite.gpu.GpuDelegateFactory
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject

/**
 * ViewModel for Background Remover with Model Hub support using direct TensorFlow Lite inference.
 */
@HiltViewModel
class BackgroundRemoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundRemoverUiState())
    val uiState: StateFlow<BackgroundRemoverUiState> = _uiState.asStateFlow()

    private var tfliteInterpreter: InterpreterApi? = null
    private val initMutex = Mutex()
    private var activeJob: Job? = null

    init {
        // Automatically select the first model if downloaded
        val firstModel = BackgroundModel.SELFIE_PORTRAIT
        val modelFile = File(context.filesDir, "models/${firstModel.fileName}")
        if (modelFile.exists() && modelFile.length() > 1024) {
            selectModel(firstModel)
        }
    }

    fun selectModel(model: BackgroundModel) {
        val modelFile = File(context.filesDir, "models/${model.fileName}")
        val isDownloaded = modelFile.exists() && modelFile.length() > 1024
        
        tfliteInterpreter?.close()
        tfliteInterpreter = null

        _uiState.update { 
            it.copy(
                selectedModel = model,
                isModelDownloaded = isDownloaded,
                error = null
            )
        }
        
        if (isDownloaded) {
            viewModelScope.launch(Dispatchers.IO) {
                ensureInterpreterReady()
            }
        }
    }

    fun deleteModel(model: BackgroundModel) {
        val modelFile = File(context.filesDir, "models/${model.fileName}")
        if (modelFile.exists()) {
            modelFile.delete()
        }
        
        if (_uiState.value.selectedModel == model) {
            tfliteInterpreter?.close()
            tfliteInterpreter = null
            
            _uiState.update { 
                it.copy(
                    isModelDownloaded = false,
                    resultBitmap = null
                )
            }
        }
    }

    fun downloadModel(model: BackgroundModel) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isProcessing = true, downloadProgress = 0.01f) }
                
                val modelDir = File(context.filesDir, "models")
                if (!modelDir.exists()) modelDir.mkdirs()
                
                val modelFile = File(modelDir, model.fileName)
                val request = Request.Builder().url(model.downloadUrl).build()
                
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorDetail = when(response.code) {
                            404 -> "Model not found on server (404)."
                            503 -> "Server unavailable. Try again later."
                            else -> "Network Error: ${response.code}"
                        }
                        throw Exception(errorDetail)
                    }
                    val body = response.body
                    val totalSize = body.contentLength()
                    
                    FileOutputStream(modelFile).use { output ->
                        val input = body.byteStream()
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        var totalRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalSize > 0) {
                                val progress = totalRead.toFloat() / totalSize
                                _uiState.update { it.copy(downloadProgress = progress) }
                            }
                        }
                    }
                }
                
                _uiState.update { it.copy(isProcessing = false, selectedModel = model, isModelDownloaded = true, downloadProgress = 1f) }
                ensureInterpreterReady()

                // If an image was already loaded, run background removal automatically after model download
                _uiState.value.originalBitmap?.let { bitmap ->
                    processImage(bitmap)
                }
            } catch (ce: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isProcessing = false, error = "Download failed: ${e.localizedMessage}") }
            }
        }
    }

    private suspend fun ensureInterpreterReady(): Boolean = initMutex.withLock {
        if (tfliteInterpreter != null) return@withLock true
        
        val currentModel = _uiState.value.selectedModel ?: return@withLock false
        val modelFile = File(context.filesDir, "models/${currentModel.fileName}")
        if (!modelFile.exists()) return@withLock false

        return@withLock withContext(Dispatchers.IO) {
            try {
                // Initialize TfLite in Play Services with GPU support
                val initOptions = TfLiteInitializationOptions.builder()
                    .setEnableGpuDelegateSupport(true)
                    .build()
                TfLite.initialize(context, initOptions).await()

                val modelBuffer = loadModelFile(modelFile)
                val options = InterpreterApi.Options().apply {
                    setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                    addDelegateFactory(GpuDelegateFactory())
                }
                tfliteInterpreter = InterpreterApi.create(modelBuffer, options)
                return@withContext true
            } catch (e: Throwable) {
                try {
                    val modelBuffer = loadModelFile(modelFile)
                    val cpuOptions = InterpreterApi.Options().apply {
                        setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                        setNumThreads(4)
                    }
                    tfliteInterpreter = InterpreterApi.create(modelBuffer, cpuOptions)
                    return@withContext true
                } catch (e2: Throwable) {
                    e2.printStackTrace()
                    _uiState.update { it.copy(isProcessing = false, error = "AI Engine Init Failed: ${e2.localizedMessage}") }
                    false
                }
            }
        }
    }

    private fun loadModelFile(file: File): ByteBuffer {
        FileInputStream(file).use { inputStream ->
            val fileChannel = inputStream.channel
            val length = fileChannel.size()
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, length)
        }
    }

    fun onImageSelected(uri: Uri) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, resultBitmap = null, error = null) }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageUtils.loadOptimizedBitmap(context, uri)
                } ?: run {
                    _uiState.update { it.copy(isProcessing = false, error = "Could not load image.") }
                    return@launch
                }

                _uiState.update { it.copy(originalBitmap = bitmap) }
                
                if (_uiState.value.isModelDownloaded) {
                    processImage(bitmap)
                } else {
                    _uiState.update { it.copy(isProcessing = false) }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _uiState.update { it.copy(isProcessing = false, error = "Selection error: ${e.localizedMessage}") }
                }
            }
        }
    }

    private suspend fun processImage(bitmap: Bitmap) {
        if (!ensureInterpreterReady()) return
        val interpreter = tfliteInterpreter ?: run {
            _uiState.update { it.copy(isProcessing = false, error = "AI Engine uninitialized.") }
            return
        }
        val model = _uiState.value.selectedModel ?: return

        try {
            withContext(Dispatchers.Default) {
                val inputTensor = interpreter.getInputTensor(0)
                val inputShape = inputTensor.shape()
                val modelH = if (inputShape.size >= 3) inputShape[1] else 256
                val modelW = if (inputShape.size >= 3) inputShape[2] else 256

                val outputTensor = interpreter.getOutputTensor(0)
                val outputShape = outputTensor.shape()

                // Preprocess bitmap to model input buffer
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, modelW, modelH, true)
                val inputPixels = IntArray(modelW * modelH)
                scaledBitmap.getPixels(inputPixels, 0, modelW, 0, 0, modelW, modelH)
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }

                val isFloatInput = inputTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32
                val inputBuffer = ByteBuffer.allocateDirect(1 * modelH * modelW * 3 * (if (isFloatInput) 4 else 1))
                inputBuffer.order(ByteOrder.nativeOrder())
                
                for (pixel in inputPixels) {
                    val r = ((pixel shr 16) and 0xFF)
                    val g = ((pixel shr 8) and 0xFF)
                    val b = (pixel and 0xFF)
                    
                    if (isFloatInput) {
                        inputBuffer.putFloat(r / 255.0f)
                        inputBuffer.putFloat(g / 255.0f)
                        inputBuffer.putFloat(b / 255.0f)
                    } else {
                        inputBuffer.put(r.toByte())
                        inputBuffer.put(g.toByte())
                        inputBuffer.put(b.toByte())
                    }
                }
                inputBuffer.rewind()

                // Prepare output buffer
                val totalOutputElements = outputShape.reduce { acc, i -> acc * i }
                val isFloatOutput = outputTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32
                val outputBuffer = ByteBuffer.allocateDirect(totalOutputElements * (if (isFloatOutput) 4 else 1))
                outputBuffer.order(ByteOrder.nativeOrder())

                // Run inference
                interpreter.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()

                // Parse confidence mask
                val combinedMask = FloatArray(modelW * modelH)
                var numChannels = 1
                var isNHWC = true

                if (outputShape.size == 4) {
                    if (outputShape[3] in 1..32) {
                        numChannels = outputShape[3]
                        isNHWC = true
                    } else {
                        numChannels = outputShape[1]
                        isNHWC = false
                    }
                }

                if (isFloatOutput) {
                    val fb = outputBuffer.asFloatBuffer()
                    
                    if (numChannels == 1) {
                        for (i in 0 until (modelW * modelH)) {
                            val v = fb.get(i)
                            // If value is already in 0..1 range (MediaPipe models), use directly; otherwise apply sigmoid for raw logits
                            combinedMask[i] = if (v in 0f..1f) v else (1.0f / (1.0f + kotlin.math.exp(-v.toDouble()))).toFloat()
                        }
                    } else if (model.id == "selfie_multiclass") {
                        // 6-channel anatomical selfie matte: channels 1..5 are subject components (hair, skin, clothes, accessories)
                        for (i in 0 until (modelW * modelH)) {
                            var fgProb = 0f
                            var sumExp = 0f
                            val channelVals = FloatArray(numChannels)
                            for (c in 0 until numChannels) {
                                val offset = if (isNHWC) i * numChannels + c else c * (modelW * modelH) + i
                                channelVals[c] = fb.get(offset)
                            }
                            val maxLogit = channelVals.maxOrNull() ?: 0f
                            for (c in 0 until numChannels) {
                                val exp = kotlin.math.exp((channelVals[c] - maxLogit).toDouble()).toFloat()
                                sumExp += exp
                                if (c > 0) fgProb += exp
                            }
                            combinedMask[i] = (fgProb / (sumExp + 1e-6f)).coerceIn(0f, 1f)
                        }
                    } else if (model.id == "deeplabv3_objects") {
                        // 21-channel DeepLabV3: channel 0 is background, channels 1..20 are objects
                        for (i in 0 until (modelW * modelH)) {
                            var bgExp = 0f
                            var sumExp = 0f
                            val channelVals = FloatArray(numChannels)
                            for (c in 0 until numChannels) {
                                val offset = if (isNHWC) i * numChannels + c else c * (modelW * modelH) + i
                                channelVals[c] = fb.get(offset)
                            }
                            val maxLogit = channelVals.maxOrNull() ?: 0f
                            for (c in 0 until numChannels) {
                                val exp = kotlin.math.exp((channelVals[c] - maxLogit).toDouble()).toFloat()
                                sumExp += exp
                                if (c == 0) bgExp = exp
                            }
                            val bgProb = bgExp / (sumExp + 1e-6f)
                            combinedMask[i] = (1f - bgProb).coerceIn(0f, 1f)
                        }
                    } else {
                        // Standard multi-channel softmax / 2-channel foreground
                        for (i in 0 until (modelW * modelH)) {
                            val fgIdx = numChannels - 1
                            val offset = if (isNHWC) i * numChannels + fgIdx else fgIdx * (modelW * modelH) + i
                            val v = fb.get(offset)
                            combinedMask[i] = if (v in 0f..1f) v else (1.0f / (1.0f + kotlin.math.exp(-v.toDouble()))).toFloat()
                        }
                    }
                } else {
                    // UINT8 Quantized
                    for (i in 0 until (modelW * modelH)) {
                        val offset = if (numChannels > 1) {
                            if (isNHWC) i * numChannels + (numChannels - 1) else (numChannels - 1) * (modelW * modelH) + i
                        } else i
                        combinedMask[i] = (outputBuffer.get(offset).toInt() and 0xFF) / 255.0f
                    }
                }

                // Post-process with sub-pixel refinement
                val resultBitmap = BackgroundRemoverEngine.removeBackground(
                    source = bitmap,
                    maskArray = combinedMask,
                    maskW = modelW,
                    maskH = modelH
                )

                _uiState.update { it.copy(isProcessing = false, resultBitmap = resultBitmap) }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                e.printStackTrace()
                _uiState.update { it.copy(isProcessing = false, error = "Processing Error: ${e.localizedMessage}") }
            }
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
        _uiState.update { BackgroundRemoverUiState(selectedModel = it.selectedModel, isModelDownloaded = it.isModelDownloaded) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        tfliteInterpreter?.close()
        tfliteInterpreter = null
    }
}

data class BackgroundRemoverUiState(
    val selectedModel: BackgroundModel? = null,
    val isModelDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val originalBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
)
