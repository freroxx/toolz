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
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject

/**
 * Pro ViewModel for Background Remover using direct TensorFlow Lite inference.
 *
 * Replaces MediaPipe framework to eliminate native crashes & cut APK size drastically.
 * Runs `selfie_multiclass.tflite` directly with GPU acceleration & CPU fallback.
 */
@HiltViewModel
class BackgroundRemoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackgroundRemoverUiState())
    val uiState: StateFlow<BackgroundRemoverUiState> = _uiState.asStateFlow()

    private var tfliteInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val initMutex = Mutex()
    private var activeJob: Job? = null

    // Channel weights for multiclass segmentation:
    // 0: Background (ignored/subtracted)
    // 1: Hair (weight 1.0 - critical fine detail)
    // 2: Body / Skin (weight 1.0)
    // 3: Face (weight 1.0)
    // 4: Clothes (weight 0.95)
    // 5: Accessories (weight 0.90)
    private val channelWeights = floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 0.95f, 0.90f)

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            ensureInterpreterReady()
        }
    }

    private suspend fun ensureInterpreterReady(): Boolean = initMutex.withLock {
        if (tfliteInterpreter != null) return@withLock true

        return@withLock withContext(Dispatchers.IO) {
            try {
                val modelBuffer = loadModelFile("selfie_multiclass.tflite")
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    val compatList = CompatibilityList()
                    if (compatList.isDelegateSupportedOnThisDevice) {
                        try {
                            val delegate = GpuDelegate()
                            addDelegate(delegate)
                            gpuDelegate = delegate
                        } catch (_: Throwable) {
                            // Fallback to CPU if GPU delegate init fails
                        }
                    }
                }
                tfliteInterpreter = Interpreter(modelBuffer, options)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback attempt without GPU delegate
                try {
                    val modelBuffer = loadModelFile("selfie_multiclass.tflite")
                    val options = Interpreter.Options().apply { setNumThreads(4) }
                    tfliteInterpreter = Interpreter(modelBuffer, options)
                    true
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = "TFLite Init Failed: ${ex.localizedMessage ?: "Model selfie_multiclass.tflite not readable"}"
                        )
                    }
                    false
                }
            }
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
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
            } catch (_: CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessing = false, error = "Selection error: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun processImage(bitmap: Bitmap) {
        if (!ensureInterpreterReady()) return
        val interpreter = tfliteInterpreter ?: return

        try {
            withContext(Dispatchers.Default) {
                // 1. Inspect model input/output tensor dimensions dynamically
                val inputTensor = interpreter.getInputTensor(0)
                val inputShape = inputTensor.shape() // Typically [1, 256, 256, 3]
                val modelH = if (inputShape.size >= 3) inputShape[1] else 256
                val modelW = if (inputShape.size >= 3) inputShape[2] else 256

                val outputTensor = interpreter.getOutputTensor(0)
                val outputShape = outputTensor.shape() // Typically [1, 256, 256, 6] or [1, 6, 256, 256]

                // 2. Preprocess bitmap to model input float buffer
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, modelW, modelH, true)
                val inputPixels = IntArray(modelW * modelH)
                scaledBitmap.getPixels(inputPixels, 0, modelW, 0, 0, modelW, modelH)
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }

                val inputBuffer = ByteBuffer.allocateDirect(1 * modelH * modelW * 3 * 4)
                inputBuffer.order(ByteOrder.nativeOrder())
                for (pixel in inputPixels) {
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                }
                inputBuffer.rewind()

                // 3. Prepare output buffer
                val totalOutputElements = outputShape.reduce { acc, i -> acc * i }
                val outputBuffer = ByteBuffer.allocateDirect(totalOutputElements * 4)
                outputBuffer.order(ByteOrder.nativeOrder())

                // 4. Run inference
                interpreter.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()

                // 5. Parse confidence masks from output tensor
                val combinedMask = FloatArray(modelW * modelH)
                var numChannels = 6
                var isNHWC = true

                if (outputShape.size == 4) {
                    if (outputShape[3] == 6 || outputShape[3] < outputShape[1]) {
                        // NHWC: [1, H, W, Channels]
                        numChannels = outputShape[3]
                        isNHWC = true
                    } else {
                        // NCHW: [1, Channels, H, W]
                        numChannels = outputShape[1]
                        isNHWC = false
                    }
                }

                val outputFloatBuffer = outputBuffer.asFloatBuffer()
                val activeChannels = numChannels.coerceAtMost(channelWeights.size)
                val channelExps = FloatArray(numChannels)

                if (isNHWC) {
                    for (y in 0 until modelH) {
                        for (x in 0 until modelW) {
                            val pixelIdx = y * modelW + x
                            val offset = pixelIdx * numChannels

                            // Compute Softmax across all channels for this pixel
                            var maxLogit = Float.NEGATIVE_INFINITY
                            for (c in 0 until numChannels) {
                                val logit = outputFloatBuffer.get(offset + c)
                                if (logit > maxLogit) maxLogit = logit
                            }

                            var sumExp = 0.0f
                            for (c in 0 until numChannels) {
                                val logit = outputFloatBuffer.get(offset + c)
                                val expVal = kotlin.math.exp(logit - maxLogit)
                                channelExps[c] = expVal
                                sumExp += expVal
                            }

                            if (sumExp > 0.00001f) {
                                var fgProb = 0.0f
                                for (c in 1 until activeChannels) {
                                    val prob = channelExps[c] / sumExp
                                    fgProb += prob * channelWeights[c]
                                }
                                combinedMask[pixelIdx] = fgProb.coerceIn(0f, 1f)
                            } else {
                                val bgProb = if (sumExp > 0f) channelExps[0] / sumExp else 1f
                                combinedMask[pixelIdx] = (1f - bgProb).coerceIn(0f, 1f)
                            }
                        }
                    }
                } else {
                    // NCHW
                    val channelSize = modelW * modelH
                    val tempLogits = FloatArray(numChannels)
                    for (i in 0 until channelSize) {
                        var maxLogit = Float.NEGATIVE_INFINITY
                        for (c in 0 until numChannels) {
                            val logit = outputFloatBuffer.get(c * channelSize + i)
                            tempLogits[c] = logit
                            if (logit > maxLogit) maxLogit = logit
                        }
                        var sumExp = 0.0f
                        for (c in 0 until numChannels) {
                            val expVal = kotlin.math.exp(tempLogits[c] - maxLogit)
                            channelExps[c] = expVal
                            sumExp += expVal
                        }
                        if (sumExp > 0.00001f) {
                            var fgProb = 0.0f
                            for (c in 1 until activeChannels) {
                                val prob = channelExps[c] / sumExp
                                fgProb += prob * channelWeights[c]
                            }
                            combinedMask[i] = fgProb.coerceIn(0f, 1f)
                        } else {
                            val bgProb = if (sumExp > 0f) channelExps[0] / sumExp else 1f
                            combinedMask[i] = (1f - bgProb).coerceIn(0f, 1f)
                        }
                    }
                }

                // 6. Verify mask quality with forgiving threshold
                var maxConf = 0f
                for (v in combinedMask) {
                    if (v > maxConf) maxConf = v
                }

                if (maxConf < 0.10f) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = "No clear subject detected. Try a photo with a visible person or object."
                        )
                    }
                    return@withContext
                }

                // 7. Post-process using Studio-Grade Engine
                val resultBitmap = BackgroundRemoverEngine.removeBackground(
                    source = bitmap,
                    maskArray = combinedMask,
                    maskW = modelW,
                    maskH = modelH
                )

                _uiState.update { it.copy(isProcessing = false, resultBitmap = resultBitmap) }
            }
        } catch (_: CancellationException) {
            // Ignore
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update {
                it.copy(isProcessing = false, error = "Processing Error: ${e.localizedMessage}")
            }
        }
    }

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val success = withContext(Dispatchers.IO) { saveImageToGallery(bitmap) }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    saveSuccess = success,
                    error = if (!success) "Save failed." else null
                )
            }
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
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false
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
        tfliteInterpreter?.close()
        tfliteInterpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}

data class BackgroundRemoverUiState(
    val originalBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
)
