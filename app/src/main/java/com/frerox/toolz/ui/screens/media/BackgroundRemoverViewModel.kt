/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.data.media.MaskDecoder
import com.frerox.toolz.data.media.ModelDownloadManager
import com.frerox.toolz.util.BackgroundRemoverEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject

/**
 * ViewModel for Background Remover — Revamp 2026.
 *
 * - Delegates downloads to [ModelDownloadManager] (atomic, verified)
 * - Uses [MaskDecoder] for clean mask parsing
 * - Hardened interpreter init (GPU → CPU fallback with logs)
 * - Exposes verified model set for Hub UI
 */
@HiltViewModel
class BackgroundRemoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val downloadManager = ModelDownloadManager(context, okHttpClient)

    private val _uiState = MutableStateFlow(BackgroundRemoverUiState())
    val uiState: StateFlow<BackgroundRemoverUiState> = _uiState.asStateFlow()

    private var tfliteInterpreter: Interpreter? = null
    private val initMutex = Mutex()
    private var activeJob: Job? = null

    init {
        // Prefer any already-verified model, else default; select & warm interpreter
        val verified = downloadManager.allDownloadedIds()
        val chosen = when {
            verified.isEmpty() -> BackgroundModel.default()
            verified.contains(BackgroundModel.SELFIE_PORTRAIT.id) -> BackgroundModel.SELFIE_PORTRAIT
            else -> BackgroundModel.fromId(verified.first()) ?: BackgroundModel.default()
        }
        val isDl = downloadManager.isVerified(chosen)
        _uiState.update {
            it.copy(
                selectedModel = chosen,
                isModelDownloaded = isDl,
                downloadedIds = verified,
            )
        }
        if (isDl) {
            viewModelScope.launch(Dispatchers.IO) { ensureInterpreterReady() }
        }
    }

    fun selectModel(model: BackgroundModel) {
        val isDownloaded = downloadManager.isVerified(model)
        tfliteInterpreter?.close()
        tfliteInterpreter = null

        _uiState.update {
            it.copy(
                selectedModel = model,
                isModelDownloaded = isDownloaded,
                error = null,
                downloadProgress = if (isDownloaded) 1f else 0f,
            )
        }
        if (isDownloaded) {
            viewModelScope.launch(Dispatchers.IO) { ensureInterpreterReady() }
        }
    }

    fun deleteModel(model: BackgroundModel) {
        downloadManager.delete(model)
        if (_uiState.value.selectedModel == model) {
            tfliteInterpreter?.close()
            tfliteInterpreter = null
            val remaining = downloadManager.allDownloadedIds()
            _uiState.update {
                it.copy(
                    isModelDownloaded = false,
                    resultBitmap = null,
                    downloadedIds = remaining,
                    downloadProgress = 0f,
                )
            }
        } else {
            _uiState.update { it.copy(downloadedIds = downloadManager.allDownloadedIds()) }
        }
    }

    fun downloadModel(model: BackgroundModel) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isProcessing = true, downloadProgress = 0.01f, error = null) }
                val result = downloadManager.download(model) { p ->
                    _uiState.update { it.copy(downloadProgress = p) }
                }
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Download failed"
                    _uiState.update { it.copy(isProcessing = false, error = msg) }
                    return@launch
                }
                val verified = downloadManager.allDownloadedIds()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        selectedModel = model,
                        isModelDownloaded = true,
                        downloadedIds = verified,
                        downloadProgress = 1f,
                    )
                }
                ensureInterpreterReady()
                // Auto-run if an image is already loaded
                _uiState.value.originalBitmap?.let { bmp -> processImage(bmp) }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(isProcessing = false) }
            } catch (e: Exception) {
                Log.e("BgRemoverVM", "downloadModel failed", e)
                _uiState.update { it.copy(isProcessing = false, error = "Download failed: ${e.localizedMessage}") }
            }
        }
    }

    fun cancelDownload() {
        activeJob?.cancel()
        _uiState.update { it.copy(isProcessing = false) }
    }

    private suspend fun ensureInterpreterReady(): Boolean = initMutex.withLock {
        if (tfliteInterpreter != null) return@withLock true
        val currentModel = _uiState.value.selectedModel ?: return@withLock false
        if (!downloadManager.isVerified(currentModel)) return@withLock false
        val modelFile = downloadManager.modelFile(currentModel)
        if (!modelFile.exists()) return@withLock false

        return@withLock withContext(Dispatchers.IO) {
            try {
                // Bundled runtime (org.tensorflow:tensorflow-lite) — works on every device,
                // no Google Play services module required. XNNPACK is on by default.
                val modelBuffer = loadModelFile(modelFile)
                val options = Interpreter.Options().apply {
                    setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                }
                tfliteInterpreter = Interpreter(modelBuffer, options)
                Log.d("BgRemoverVM", "Interpreter ready (bundled CPU) for ${currentModel.id}")
                return@withContext true
            } catch (e: Throwable) {
                Log.e("BgRemoverVM", "Interpreter init failed for ${currentModel.id}", e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Couldn't start the AI engine. Open AI models and re-download it.",
                    )
                }
                false
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
                val bitmap = loadBitmapRobust(uri)
                if (bitmap == null) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = "Couldn't read that photo. If it was shared from another app, try picking it from the gallery.",
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(originalBitmap = bitmap) }
                if (_uiState.value.isModelDownloaded) {
                    processImage(bitmap)
                } else {
                    _uiState.update { it.copy(isProcessing = false, error = "Download a model from the hub to get started") }
                }
            } catch (e: SecurityException) {
                Log.w("BgRemoverVM", "photo access revoked", e)
                _uiState.update { it.copy(isProcessing = false, error = "Photo access was revoked — pick the image again.") }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("BgRemoverVM", "onImageSelected", e)
                    _uiState.update { it.copy(isProcessing = false, error = "Couldn't load image: ${e.localizedMessage}") }
                }
            }
        }
    }

    /**
     * Stream-first decoder: works with every ContentProvider (photo picker, share sheet,
     * cloud-backed gallery apps) — unlike decodeFileDescriptor which fails on several
     * providers/HEIC encoders. Falls back to the descriptor path, then fixes EXIF rotation.
     */
    private suspend fun loadBitmapRobust(uri: Uri, maxDim: Int = 4096): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Pass 1 — bounds
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            // Pass 2 — decode via stream, fall back to descriptor for odd providers
            var bmp: Bitmap? = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: run {
                try {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
                    }
                } catch (_: Exception) { null }
            }
            bmp ?: return@withContext null

            // EXIF rotation (camera captures)
            val deg = try {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    when (androidx.exifinterface.media.ExifInterface(ins).getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                    )) {
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (_: Exception) { 0 }

            if (deg != 0) {
                val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rotated != bmp) bmp.recycle()
                bmp = rotated
            }
            bmp
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            Log.e("BgRemoverVM", "loadBitmapRobust failed for $uri", e)
            null
        }
    }

    /** For camera captures or share intents where we already have a bitmap */
    fun onBitmapSelected(bitmap: Bitmap) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, originalBitmap = bitmap, resultBitmap = null, error = null) }
            if (_uiState.value.isModelDownloaded) processImage(bitmap) else _uiState.update { it.copy(isProcessing = false) }
        }
    }

    private suspend fun processImage(bitmap: Bitmap) {
        if (!ensureInterpreterReady()) {
            _uiState.update { it.copy(isProcessing = false, error = "AI engine not ready — try re-downloading the model") }
            return
        }
        val interpreter = tfliteInterpreter ?: run {
            _uiState.update { it.copy(isProcessing = false, error = "AI Engine uninitialized.") }
            return
        }
        val model = _uiState.value.selectedModel ?: return

        try {
            withContext(Dispatchers.Default) {
                val inputTensor = interpreter.getInputTensor(0)
                val inputShape = inputTensor.shape()
                val modelH = if (inputShape.size >= 3) inputShape[1] else model.resolution
                val modelW = if (inputShape.size >= 3) inputShape[2] else model.resolution

                val outputTensor = interpreter.getOutputTensor(0)
                val outputShape = outputTensor.shape()

                Log.d("BgRemoverVM", "Inference ${model.id} input=${inputShape.contentToString()} output=${outputShape.contentToString()} dtype=${inputTensor.dataType()}")

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, modelW, modelH, true)
                val inputPixels = IntArray(modelW * modelH)
                scaledBitmap.getPixels(inputPixels, 0, modelW, 0, 0, modelW, modelH)
                if (scaledBitmap != bitmap) scaledBitmap.recycle()

                val isFloatInput = inputTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32
                val inputBuffer = ByteBuffer.allocateDirect(1 * modelH * modelW * 3 * (if (isFloatInput) 4 else 1))
                inputBuffer.order(ByteOrder.nativeOrder())
                for (pixel in inputPixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (isFloatInput) {
                        inputBuffer.putFloat(r / 255.0f)
                        inputBuffer.putFloat(g / 255.0f)
                        inputBuffer.putFloat(b / 255.0f)
                    } else {
                        inputBuffer.put(r.toByte()); inputBuffer.put(g.toByte()); inputBuffer.put(b.toByte())
                    }
                }
                inputBuffer.rewind()

                val totalOutputElements = outputShape.fold(1) { acc, i -> acc * i }
                val isFloatOutput = outputTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32
                val outputBuffer = ByteBuffer.allocateDirect(totalOutputElements * (if (isFloatOutput) 4 else 1))
                outputBuffer.order(ByteOrder.nativeOrder())

                interpreter.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()

                val combinedMask = MaskDecoder.decode(
                    outputBuffer = outputBuffer,
                    isFloatOutput = isFloatOutput,
                    outputShape = outputShape,
                    modelW = modelW,
                    modelH = modelH,
                    modelId = model.id,
                )

                val resultBitmap = BackgroundRemoverEngine.removeBackground(
                    source = bitmap,
                    maskArray = combinedMask,
                    maskW = modelW,
                    maskH = modelH,
                )
                _uiState.update { it.copy(isProcessing = false, resultBitmap = resultBitmap) }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e("BgRemoverVM", "processImage failed", e)
                _uiState.update { it.copy(isProcessing = false, error = "Processing error: ${e.localizedMessage}") }
            }
        }
    }

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val success = withContext(Dispatchers.IO) { saveImageToGallery(bitmap, compressWhiteBg = false) }
            _uiState.update { it.copy(isProcessing = false, saveSuccess = success, error = if (!success) "Save failed." else null) }
            if (success) {
                // auto-reset saveSuccess after 2.5s so UI can show snackbar again
                kotlinx.coroutines.delay(2500)
                _uiState.update { it.copy(saveSuccess = false) }
            }
        }
    }

    /** Save with optional white background composite */
    fun saveResultWithBackground(bitmap: Bitmap, background: PreviewBackground) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val success = withContext(Dispatchers.IO) {
                val toSave = when (background) {
                    is PreviewBackground.White -> compositeOnColor(bitmap, 0xFFFFFFFF.toInt())
                    is PreviewBackground.Color -> compositeOnColor(bitmap, background.color)
                    is PreviewBackground.Transparent -> bitmap
                    is PreviewBackground.Blur -> bitmap // blur preview only — save transparent
                    is PreviewBackground.CustomImage -> bitmap
                }
                saveImageToGallery(toSave, compressWhiteBg = background !is PreviewBackground.Transparent)
            }
            _uiState.update { it.copy(isProcessing = false, saveSuccess = success, error = if (!success) "Save failed." else null) }
            if (success) { kotlinx.coroutines.delay(2500); _uiState.update { it.copy(saveSuccess = false) } }
        }
    }

    private fun compositeOnColor(fg: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(fg.width, fg.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(color)
        c.drawBitmap(fg, 0f, 0f, null)
        return out
    }

    private fun saveImageToGallery(bitmap: Bitmap, compressWhiteBg: Boolean): Boolean {
        val filename = "TOOLZ_BG_${System.currentTimeMillis()}.png"
        val mime = "image/png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
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
            Log.e("BgRemoverVM", "save failed", e)
            resolver.delete(uri, null, null)
            false
        }
    }

    fun getShareIntent(bitmap: Bitmap): Intent? {
        return try {
            val cache = File(context.cacheDir, "share")
            cache.mkdirs()
            val f = File(cache, "toolz_bg_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", f)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e("BgRemoverVM", "share intent failed", e)
            null
        }
    }

    fun setPreviewBackground(bg: PreviewBackground) {
        _uiState.update { it.copy(previewBackground = bg) }
    }

    fun clearResult() {
        activeJob?.cancel()
        _uiState.update {
            BackgroundRemoverUiState(
                selectedModel = it.selectedModel,
                isModelDownloaded = it.isModelDownloaded,
                downloadedIds = it.downloadedIds,
            )
        }
    }

    fun dismissError() { _uiState.update { it.copy(error = null) } }

    fun dismissSaveSuccess() { _uiState.update { it.copy(saveSuccess = false) } }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        tfliteInterpreter?.close()
        tfliteInterpreter = null
    }
}

sealed interface PreviewBackground {
    data object Transparent : PreviewBackground
    data object White : PreviewBackground
    data class Color(val color: Int) : PreviewBackground
    data object Blur : PreviewBackground
    data class CustomImage(val bitmap: Bitmap) : PreviewBackground
}

data class BackgroundRemoverUiState(
    val selectedModel: BackgroundModel? = null,
    val isModelDownloaded: Boolean = false,
    val downloadedIds: Set<String> = emptySet(),
    val downloadProgress: Float = 0f,
    val originalBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val previewBackground: PreviewBackground = PreviewBackground.Transparent,
)
