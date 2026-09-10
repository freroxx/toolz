/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media

import ai.onnxruntime.OrtSession
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
import com.frerox.toolz.R
import com.frerox.toolz.data.media.BackgroundModel
import com.frerox.toolz.data.media.IMAGENET_PREPROCESS_1024
import com.frerox.toolz.data.media.IMAGENET_PREPROCESS_320
import com.frerox.toolz.data.media.InferenceRuntime
import com.frerox.toolz.data.media.MaskDecoder
import com.frerox.toolz.data.media.ModelDownloadManager
import com.frerox.toolz.data.media.OnnxInferenceEngine
import com.frerox.toolz.data.media.RVM_PREPROCESS
import com.frerox.toolz.data.media.SingleMask
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
import kotlin.math.max
import kotlin.math.min

/**
 * ViewModel for Background Remover — 2026 revamp.
 *
 * Dual backend: LiteRT (instant fallback) + ONNX Runtime (quality tiers).
 * The UI reads [BgStage] — it never guesses what "processing" means anymore.
 */
@HiltViewModel
class BackgroundRemoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val downloadManager = ModelDownloadManager(context, okHttpClient)
    private val onnxEngine = OnnxInferenceEngine()

    private val _uiState = MutableStateFlow(BackgroundRemoverUiState())
    val uiState: StateFlow<BackgroundRemoverUiState> = _uiState.asStateFlow()

    private var tfliteInterpreter: Interpreter? = null
    private var onnxSession: OrtSession? = null
    private var onnxSessionModelId: String? = null
    private val initMutex = Mutex()
    private var activeJob: Job? = null
    private val prefs = context.getSharedPreferences("bg_remover_prefs", Context.MODE_PRIVATE)

    init {
        reclaimLegacyFilesOnce()
        val verified = downloadManager.allDownloadedIds()
        val savedId = prefs.getString("selected_model_id", null)
        val savedModel = savedId?.let { BackgroundModel.fromId(it) }
            ?: savedId?.let { BackgroundModel.migrateLegacyId(it) }
        val chosen = when {
            savedModel != null && verified.contains(savedModel.id) -> savedModel
            savedModel != null && verified.isEmpty() -> savedModel // keep choice even if not yet downloaded
            verified.isEmpty() -> savedModel ?: BackgroundModel.default()
            savedModel != null && downloadManager.isVerified(savedModel) -> savedModel
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
            viewModelScope.launch(Dispatchers.IO) { ensureBackendReady() }
        }
    }

    // ── Model hub ──

    fun selectModel(model: BackgroundModel) {
        prefs.edit().putString("selected_model_id", model.id).apply()
        val isDownloaded = downloadManager.isVerified(model)
        closeBackends()

        _uiState.update {
            it.copy(
                selectedModel = model,
                isModelDownloaded = isDownloaded,
                failure = null,
                error = null,
                downloadProgress = if (isDownloaded) 1f else 0f,
            )
        }
        if (isDownloaded) {
            viewModelScope.launch(Dispatchers.IO) { ensureBackendReady() }
        }
    }

    fun deleteModel(model: BackgroundModel) {
        downloadManager.delete(model)
        if (_uiState.value.selectedModel == model) {
            closeBackends()
            recyclePhotoLocked()
            val remaining = downloadManager.allDownloadedIds()
            _uiState.update {
                it.copy(
                    isModelDownloaded = false,
                    resultBitmap = null,
                    downloadedIds = remaining,
                    downloadProgress = 0f,
                    stage = BgStage.IDLE,
                )
            }
        } else {
            _uiState.update { it.copy(downloadedIds = downloadManager.allDownloadedIds()) }
        }
    }

    /** True when a big-model download would burn mobile data — UI shows the Wi-Fi sheet. */
    fun downloadNeedsMeteredConsent(model: BackgroundModel): Boolean =
        model.gatedOnWifi && downloadManager.isMeteredConnection()

    fun downloadModel(model: BackgroundModel, allowMetered: Boolean = false) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update {
                    it.copy(
                        stage = BgStage.DOWNLOADING,
                        isProcessing = true,
                        downloadingId = model.id,
                        downloadProgress = 0f,
                        downloadedBytes = 0L,
                        totalBytes = -1L,
                        downloadSpeedBps = 0L,
                        failure = null,
                        error = null,
                    )
                }
                val result = downloadManager.download(model, allowMetered) { bytes, total ->
                    _uiState.update {
                        it.copy(
                            downloadedBytes = bytes,
                            totalBytes = total,
                            downloadProgress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f,
                            downloadSpeedBps = downloadManager.lastSpeedBps(model),
                        )
                    }
                }
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message
                        ?: context.getString(R.string.st_BackgroundRemover_DownloadFailed)
                    val failure = BgFailure(msg, RetryAction.RETRY_DOWNLOAD)
                    _uiState.update {
                        it.copy(
                            stage = BgStage.FAILED,
                            isProcessing = false,
                            downloadingId = null,
                            failure = failure,
                        )
                    }
                    return@launch
                }
                prefs.edit().putString("selected_model_id", model.id).apply()
                val verified = downloadManager.allDownloadedIds()
                _uiState.update {
                    it.copy(
                        stage = BgStage.IDLE,
                        isProcessing = false,
                        downloadingId = null,
                        selectedModel = model,
                        isModelDownloaded = true,
                        downloadedIds = verified,
                        downloadProgress = 1f,
                    )
                }
                ensureBackendReady()
                // Auto-run if an image is already loaded
                _uiState.value.originalBitmap?.let { bmp -> processImage(bmp) }
            } catch (ce: CancellationException) {
                _uiState.update { it.copy(stage = BgStage.IDLE, isProcessing = false, downloadingId = null) }
            } catch (e: Exception) {
                Log.e("BgRemoverVM", "downloadModel failed", e)
                val msg = e.message ?: context.getString(R.string.st_BackgroundRemover_ProcessingFailed)
                _uiState.update {
                    it.copy(
                        stage = BgStage.FAILED,
                        isProcessing = false,
                        downloadingId = null,
                        failure = BgFailure(msg, RetryAction.RETRY_DOWNLOAD),
                    )
                }            }
        }
    }

    fun cancelActive() {
        activeJob?.cancel()
        _uiState.update { it.copy(stage = BgStage.IDLE, isProcessing = false, downloadingId = null) }
    }

    // ── Backends ──

    private fun closeBackends() {
        runCatching { tfliteInterpreter?.close() }
        tfliteInterpreter = null
        runCatching { onnxSession?.close() }
        onnxSession = null
        onnxSessionModelId = null
    }

    private suspend fun ensureBackendReady(): Boolean = initMutex.withLock {
        val currentModel = _uiState.value.selectedModel ?: return@withLock false
        if (!downloadManager.isVerified(currentModel)) return@withLock false
        return@withLock when (currentModel.runtime) {
            InferenceRuntime.LITERT -> ensureTfliteReady(currentModel)
            InferenceRuntime.ONNX -> ensureOnnxReady(currentModel)
        }
    }

    private fun ensureTfliteReady(model: BackgroundModel): Boolean {
        if (tfliteInterpreter != null) return true
        val modelFile = downloadManager.modelFile(model)
        if (!modelFile.exists()) return false
        return try {
            val modelBuffer = loadModelFile(modelFile)
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            }
            tfliteInterpreter = Interpreter(modelBuffer, options)
            Log.d("BgRemoverVM", "LiteRT ready for ${model.id}")
            true
        } catch (e: Throwable) {
            Log.e("BgRemoverVM", "Interpreter init failed for ${model.id}", e)
            fail(context.getString(R.string.st_BackgroundRemover_EngineStartFail), RetryAction.OPEN_HUB)
            false
        }
    }

    private fun ensureOnnxReady(model: BackgroundModel): Boolean {
        if (onnxSession != null && onnxSessionModelId == model.id) return true
        val modelFile = downloadManager.modelFile(model)
        if (!modelFile.exists()) return false
        return try {
            runCatching { onnxSession?.close() }
            onnxSession = onnxEngine.createSession(modelFile, tryNnapi = true)
            onnxSessionModelId = model.id
            Log.d("BgRemoverVM", "ONNX ready for ${model.id}")
            true
        } catch (e: Throwable) {
            Log.e("BgRemoverVM", "ONNX session failed for ${model.id}", e)
            fail(context.getString(R.string.st_BackgroundRemover_EngineStartFail), RetryAction.OPEN_HUB)
            false
        }
    }

    private fun loadModelFile(file: File): ByteBuffer {
        FileInputStream(file).use { inputStream ->
            val fileChannel = inputStream.channel
            val length = fileChannel.size()
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, length)
        }
    }

    private fun fail(message: String, retry: RetryAction?) {
        _uiState.update {
            it.copy(
                stage = BgStage.FAILED,
                isProcessing = false,
                failure = BgFailure(message, retry),
            )
        }
    }

    /** Raw metered-connection state for the hub's Wi-Fi hints. */
    fun isMeteredNow(): Boolean = downloadManager.isMeteredConnection()

    // ── Image intake ──

    /**
     * The current original ONLY when we decoded it ourselves (loadBitmapRobust).
     * Caller-owned bitmaps (camera/share via onBitmapSelected) are never recycled —
     * recycling someone else's bitmap is a use-after-free crash.
     */
    private var internalOriginal: Bitmap? = null

    private fun recycleBitmap(bmp: Bitmap?) {
        try {
            if (bmp != null && !bmp.isRecycled) bmp.recycle()
        } catch (_: Exception) {
        }
    }

    /** Drop the current photo + result, freeing their native/heap pixels now (not at GC). */
    private fun recyclePhotoLocked() {
        recycleBitmap(internalOriginal)
        internalOriginal = null
        recycleBitmap(_uiState.value.resultBitmap)
    }

    fun onImageSelected(uri: Uri) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            recyclePhotoLocked()
            _uiState.update {
                it.copy(
                    stage = BgStage.SEGMENTING,
                    isProcessing = true,
                    originalBitmap = null,
                    resultBitmap = null,
                    failure = null,
                    error = null,
                )
            }
            try {
                val bitmap = loadBitmapRobust(uri)
                if (bitmap == null) {
                    fail(
                        context.getString(R.string.st_BackgroundRemover_UnreadablePhoto),
                        RetryAction.PICK_IMAGE,
                    )
                    return@launch
                }
                internalOriginal = bitmap
                _uiState.update { it.copy(originalBitmap = bitmap) }
                if (_uiState.value.isModelDownloaded) {
                    processImage(bitmap)
                } else {
                    fail(context.getString(R.string.st_BackgroundRemover_NeedModel), RetryAction.OPEN_HUB)
                }
            } catch (e: SecurityException) {
                Log.w("BgRemoverVM", "photo access revoked", e)
                fail(context.getString(R.string.st_BackgroundRemover_AccessRevoked), RetryAction.PICK_IMAGE)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("BgRemoverVM", "onImageSelected", e)
                    fail(context.getString(R.string.st_BackgroundRemover_LoadFailed), RetryAction.PICK_IMAGE)
                }
            }
        }
    }

    /**
     * Stream-first decoder: works with every ContentProvider (photo picker, share sheet,
     * cloud-backed gallery apps) — unlike decodeFileDescriptor which fails on several
     * providers/HEIC encoders. Falls back to the descriptor path, then fixes EXIF rotation.
     * maxDim is capped for the 256 MB heap: 3072px ≈ 36 MB per ARGB buffer worst case.
     */
    private suspend fun loadBitmapRobust(uri: Uri, maxDim: Int = 3072): Bitmap? = withContext(Dispatchers.IO) {
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
            recyclePhotoLocked()
            // Not ours — never recycle (see internalOriginal).
            internalOriginal = null
            _uiState.update {
                it.copy(
                    stage = BgStage.SEGMENTING,
                    isProcessing = true,
                    originalBitmap = bitmap,
                    resultBitmap = null,
                    failure = null,
                    error = null,
                )
            }
            if (_uiState.value.isModelDownloaded) processImage(bitmap)
            else fail(context.getString(R.string.st_BackgroundRemover_NeedModel), RetryAction.OPEN_HUB)
        }
    }

    // ── Inference ──

    private suspend fun processImage(bitmap: Bitmap) {
        if (!ensureBackendReady()) {
            if (_uiState.value.stage != BgStage.FAILED) {
                fail(context.getString(R.string.st_BackgroundRemover_EngineNotReady), RetryAction.OPEN_HUB)
            }
            return
        }
        val model = _uiState.value.selectedModel ?: return

        try {
            _uiState.update { it.copy(stage = BgStage.SEGMENTING) }
            val (mask, maskW, maskH) = withContext(Dispatchers.Default) {
                when (model.runtime) {
                    InferenceRuntime.LITERT -> runTfliteMask(bitmap, model)
                    InferenceRuntime.ONNX -> runOnnxMask(bitmap, model)
                }
            }
            _uiState.update { it.copy(stage = BgStage.MATTING) }
            val resultBitmap = runMatting(bitmap, mask, maskW, maskH)
            val superseded = _uiState.value.resultBitmap
            _uiState.update {
                it.copy(stage = BgStage.DONE, isProcessing = false, resultBitmap = resultBitmap)
            }
            // Free the previous cutout now — a stale 40 MB bitmap must not linger.
            if (superseded !== resultBitmap) recycleBitmap(superseded)
        } catch (e: OutOfMemoryError) {
            Log.e("BgRemoverVM", "processImage OOM", e)
            fail(context.getString(R.string.st_BackgroundRemover_TooLarge), RetryAction.PICK_IMAGE)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e("BgRemoverVM", "processImage failed", e)
                fail(context.getString(R.string.st_BackgroundRemover_ProcessingFailed), RetryAction.RETRY_PROCESS)
            }
        }
    }

    private data class RawMask(val data: FloatArray, val w: Int, val h: Int)

    private fun runTfliteMask(bitmap: Bitmap, model: BackgroundModel): RawMask {
        val interpreter = tfliteInterpreter
            ?: throw IllegalStateException("AI Engine uninitialized.")
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val modelH = if (inputShape.size >= 3) inputShape[1] else model.inputSize
        val modelW = if (inputShape.size >= 3) inputShape[2] else model.inputSize

        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()

        Log.d("BgRemoverVM", "LiteRT ${model.id} input=${inputShape.contentToString()} output=${outputShape.contentToString()}")

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
        return RawMask(combinedMask, modelW, modelH)
    }

    private fun runOnnxMask(bitmap: Bitmap, model: BackgroundModel): RawMask {
        val session = onnxSession?.takeIf { onnxSessionModelId == model.id }
            ?: throw IllegalStateException("ONNX session not ready.")
        val single: SingleMask = when (model.id) {
            "portrait_rvm" -> {
                // Aspect-preserving fit, long edge capped; internal working band per upstream.
                val scale = RVM_PREPROCESS.inputSize.toFloat() / max(bitmap.width, bitmap.height).toFloat()
                val s = min(1f, scale)
                val w = max(8, (bitmap.width * s).toInt())
                val h = max(8, (bitmap.height * s).toInt())
                val chw = RVM_PREPROCESS.toChw(bitmap, w, h)
                val dsr = (256f / max(w, h)).coerceIn(0.25f, 1f)
                onnxEngine.runRvm(session, chw, h, w, dsr)
            }
            "pro_detail" -> {
                val chw = IMAGENET_PREPROCESS_1024.toChw(bitmap)
                onnxEngine.runSingleMask(session, chw, 3, 1024, 1024)
            }
            else -> { // fast_general + future single-input nets
                val chw = IMAGENET_PREPROCESS_320.toChw(bitmap)
                onnxEngine.runSingleMask(session, chw, 3, 320, 320)
            }
        }
        Log.d("BgRemoverVM", "ONNX ${model.id} mask=${single.maskW}x${single.maskH}")
        return RawMask(single.data, single.maskW, single.maskH)
    }

    /**
     * Runs the matting engine with an OOM safety net: on memory pressure the photo is
     * downscaled once (≤2048px) and matting is retried before giving up.
     */
    private suspend fun runMatting(
        source: Bitmap,
        mask: FloatArray,
        maskW: Int,
        maskH: Int,
    ): Bitmap {
        return try {
            BackgroundRemoverEngine.removeBackground(source, mask, maskW, maskH)
        } catch (oom: OutOfMemoryError) {
            Log.w("BgRemoverVM", "OOM at ${source.width}×${source.height} — retrying at ≤2048px", oom)
            val scale = 2048f / maxOf(source.width, source.height)
            val scaled = Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            try {
                BackgroundRemoverEngine.removeBackground(scaled, mask, maskW, maskH)
            } finally {
                if (scaled != source) scaled.recycle()
            }
        }
    }

    // ── Export (honest: every preview mode composites for real) ──

    fun saveResult(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val success = withContext(Dispatchers.IO) { saveImageToGallery(bitmap, withBackground = false) }
            _uiState.update { it.copy(isProcessing = false, saveSuccess = success, error = if (!success) context.getString(R.string.st_BackgroundRemover_SaveFailed) else null) }
            if (success) {
                kotlinx.coroutines.delay(2500)
                _uiState.update { it.copy(saveSuccess = false) }
            }
        }
    }

    /** Save with the selected preview background composited — WYSIWYG, no silent fallbacks. */
    fun saveResultWithBackground(bitmap: Bitmap, background: PreviewBackground) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val success = withContext(Dispatchers.IO) {
                // Snapshot + recycled-guard: the user may pick a new photo mid-save,
                // which recycles the old original on another thread.
                val original = _uiState.value.originalBitmap?.takeUnless { it.isRecycled }
                val toSave = when (background) {
                    is PreviewBackground.White -> compositeOnColor(bitmap, 0xFFFFFFFF.toInt())
                    is PreviewBackground.Color -> compositeOnColor(bitmap, background.color)
                    is PreviewBackground.Transparent -> bitmap
                    is PreviewBackground.Blur ->
                        if (original != null) compositeOnBlur(bitmap, original) else bitmap
                    is PreviewBackground.CustomImage ->
                        compositeOnImage(bitmap, background.bitmap)
                }
                val wroteCustom = toSave !== bitmap
                try {
                    saveImageToGallery(toSave, withBackground = background !is PreviewBackground.Transparent)
                } finally {
                    if (wroteCustom) toSave.recycle()
                }
            }
            _uiState.update { it.copy(isProcessing = false, saveSuccess = success, error = if (!success) context.getString(R.string.st_BackgroundRemover_SaveFailed) else null) }
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

    /** Blurred original as backdrop (cover-fit), cutout on top — matches the preview. */
    private fun compositeOnBlur(fg: Bitmap, original: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(fg.width, fg.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val blurred = blurForBackdrop(original)
        val bg = coverFit(blurred, fg.width, fg.height)
        c.drawBitmap(bg, 0f, 0f, null)
        if (bg !== blurred) recycleBitmap(bg)
        if (blurred !== original) recycleBitmap(blurred)
        c.drawBitmap(fg, 0f, 0f, null)
        return out
    }

    private fun compositeOnImage(fg: Bitmap, backdrop: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(fg.width, fg.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val bg = coverFit(backdrop, fg.width, fg.height)
        c.drawBitmap(bg, 0f, 0f, null)
        if (bg !== backdrop) recycleBitmap(bg)
        c.drawBitmap(fg, 0f, 0f, null)
        return out
    }

    /** Center-crop cover fit without mutating the source. */
    private fun coverFit(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h) return src
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (src.width * scale).toInt().coerceAtLeast(1)
        val sh = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val x = ((sw - w) / 2).coerceAtLeast(0)
        val y = ((sh - h) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(scaled, x, y, min(w, sw), min(h, sh))
        if (scaled != cropped) scaled.recycle()
        return cropped
    }

    /** Cheap strong blur for backdrops: downscale hard, upscale smooth. */
    private fun blurForBackdrop(src: Bitmap): Bitmap {
        return try {
            val w = (src.width * 0.05f).toInt().coerceIn(1, 64)
            val h = (src.height * 0.05f).toInt().coerceIn(1, 64)
            val small = Bitmap.createScaledBitmap(src, w, h, true)
            Bitmap.createScaledBitmap(small, src.width, src.height, true).also {
                if (small != it) small.recycle()
            }
        } catch (_: Throwable) {
            src
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap, withBackground: Boolean): Boolean {
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

    // ── Misc UI intents ──

    fun setPreviewBackground(bg: PreviewBackground) {
        _uiState.update { it.copy(previewBackground = bg) }
    }

    fun retryFromFailure() {
        val retry = _uiState.value.failure?.retry ?: return
        when (retry) {
            RetryAction.RETRY_DOWNLOAD -> _uiState.value.selectedModel?.let { downloadModel(it) }
            RetryAction.RETRY_PROCESS -> _uiState.value.originalBitmap?.let {
                activeJob?.cancel()
                activeJob = viewModelScope.launch { processImage(it) }
            }
            RetryAction.OPEN_HUB, RetryAction.PICK_IMAGE -> {
                // Handled by the screen (opens hub / picker); just clear the inline state.
                _uiState.update { it.copy(stage = BgStage.IDLE, failure = null, error = null) }
            }
        }
    }

    fun clearResult() {
        activeJob?.cancel()
        recyclePhotoLocked()
        _uiState.update {
            BackgroundRemoverUiState(
                selectedModel = it.selectedModel,
                isModelDownloaded = it.isModelDownloaded,
                downloadedIds = it.downloadedIds,
            )
        }
    }

    fun dismissError() { _uiState.update { it.copy(error = null, failure = null) } }

    fun dismissSaveSuccess() { _uiState.update { it.copy(saveSuccess = false) } }

    private fun reclaimLegacyFilesOnce() {
        if (prefs.getBoolean("legacy_reclaimed_v2", false)) return
        prefs.edit().putBoolean("legacy_reclaimed_v2", true).apply()
        try {
            val bytes = downloadManager.deleteLegacyFiles(BackgroundModel.LEGACY_FILE_NAMES)
            if (bytes > 0) Log.i("BgRemoverVM", "Reclaimed ${bytes / 1024 / 1024} MB of retired models")
        } catch (e: Exception) {
            Log.w("BgRemoverVM", "legacy reclaim failed", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        closeBackends()
        recyclePhotoLocked()
        runCatching { onnxEngine.close() }
    }
}

/** Explicit pipeline state — the UI renders this, never infers it. */
enum class BgStage { IDLE, DOWNLOADING, SEGMENTING, MATTING, DONE, FAILED }

enum class RetryAction { RETRY_DOWNLOAD, RETRY_PROCESS, OPEN_HUB, PICK_IMAGE }

data class BgFailure(val message: String, val retry: RetryAction?)

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
    /** Byte-accurate download progress. totalBytes = -1 while the server hides it. */
    val downloadingId: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val downloadProgress: Float = 0f,
    val downloadSpeedBps: Long = 0L,
    val originalBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    /** Legacy: true while downloading OR inferring. Prefer [stage]. */
    val isProcessing: Boolean = false,
    val stage: BgStage = BgStage.IDLE,
    val failure: BgFailure? = null,
    val saveSuccess: Boolean = false,
    /** Legacy error plumbing (snackbar path). Prefer [failure]. */
    val error: String? = null,
    val previewBackground: PreviewBackground = PreviewBackground.Transparent,
)
