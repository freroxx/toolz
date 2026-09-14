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
import com.frerox.toolz.data.media.FastBlur
import com.frerox.toolz.data.media.IMAGENET_PREPROCESS_1024
import com.frerox.toolz.data.media.ISNET_PREPROCESS_1024
import com.frerox.toolz.data.media.IMAGENET_PREPROCESS_320
import com.frerox.toolz.data.media.InferenceResult
import com.frerox.toolz.data.media.MaskConfidence
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
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * ViewModel for Background Remover — 2026 revamp.
 *
 * ONNX Runtime backend for all quality tiers (Pro default, Ultra tiled).
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
                downloadingId = null,
                downloadedBytes = 0L,
                totalBytes = -1L,
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
                    downloadingId = null,
                    downloadedBytes = 0L,
                    totalBytes = -1L,
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
        runCatching { onnxSession?.close() }
        onnxSession = null
        onnxSessionModelId = null
    }

    private suspend fun ensureBackendReady(): Boolean = initMutex.withLock {
        val currentModel = _uiState.value.selectedModel ?: return@withLock false
        if (!downloadManager.isVerified(currentModel)) return@withLock false
        // ONNX-only lineup (LiteRT/Instant removed): every model runs on ONNX Runtime.
        return@withLock ensureOnnxReady(currentModel)
    }

    private suspend fun ensureOnnxReady(model: BackgroundModel): Boolean {
        if (onnxSession != null && onnxSessionModelId == model.id) return true
        val modelFile = downloadManager.modelFile(model)
        if (!modelFile.exists()) return false
        return try {
            runCatching { onnxSession?.close() }
            onnxSession = null
            onnxSessionModelId = null
            // Emit WARMING_UP so the user sees feedback during graph compilation.
            // For Ultra (Swin-Tiny) BASIC_OPT takes ~2 s; Pro (ISNet) ALL_OPT ~1 s.
            // Without this the screen freezes silently, which is worse than a spinner.
            _uiState.update { it.copy(stage = BgStage.WARMING_UP) }
            // createSession blocks on this thread (Dispatchers.Default/IO, never main).
            // The modelId arg picks BASIC_OPT + tighter thread caps for Ultra.
            onnxSession = onnxEngine.createSession(modelFile, modelId = model.id)
            onnxSessionModelId = model.id
            Log.d("BgRemoverVM", "ONNX ready for ${model.id}")
            true
        } catch (e: Throwable) {
            Log.e("BgRemoverVM", "ONNX session failed for ${model.id}", e)
            fail(context.getString(R.string.st_BackgroundRemover_EngineStartFail), RetryAction.OPEN_HUB)
            false
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

    /** Timeout per model tier: Ultra/Pro can be genuinely slow; others are fast. */
    private fun inferenceTimeoutMs(model: BackgroundModel): Long = when (model.id) {
        "ultra_birefnet", "pro_detail" -> 120_000L
        else -> 60_000L
    }

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

            val inferenceResult = withContext(Dispatchers.Default) {
                withTimeout(inferenceTimeoutMs(model)) {
                    runOnnxInference(bitmap, model)
                }
            }

            val confidence = inferenceResult.confidence
            Log.d(
                "BgRemoverVM",
                "${model.id} confidence: edgeRatio=${confidence.edgeRatio} " +
                    "entropy=${confidence.maskEntropy} fgCoverage=${confidence.fgCoverage} " +
                    "likelyEmpty=${confidence.likelyEmpty} likelyFull=${confidence.likelyFull}",
            )

            // Fail fast on a blank mask — surface a helpful message instead of a
            // transparent result that looks like a bug. Give the user a path forward.
            if (confidence.likelyEmpty) {
                val suggestion = when (model.id) {
                    "ultra_birefnet" -> context.getString(R.string.st_BackgroundRemover_EmptyMaskUltra)
                    else             -> context.getString(R.string.st_BackgroundRemover_EmptyMask)
                }
                fail(suggestion, RetryAction.SWITCH_MODEL)
                return
            }

            // ── Ultra memory guard ──────────────────────────────────────────────────────
            // BiRefNet at 1024×1024 leaves ~4 GB RSS in native ORT activation buffers after
            // session.run() returns. Arena-disable flags stop pre-allocation but cannot
            // reclaim post-run intermediate tensors — those only free when the session closes.
            // Closing here (before matting starts) lets the kernel reclaim those pages while
            // we do CPU-only work, preventing lmkd from killing the app when the user later
            // navigates to another screen. Next Ultra run re-warms the session (WARMING_UP
            // shows again) — acceptable: quality > memory smoothness.
            if (model.id == "ultra_birefnet") {
                initMutex.withLock {
                    Log.d("BgRemoverVM", "Ultra: closing ONNX session post-inference to free ~4 GB RSS")
                    runCatching { onnxSession?.close() }
                    onnxSession = null
                    onnxSessionModelId = null
                }
            }

            _uiState.update { it.copy(stage = BgStage.MATTING) }
            val resultBitmap = runMatting(
                bitmap,
                inferenceResult.mask.data,
                inferenceResult.mask.maskW,
                inferenceResult.mask.maskH,
                confidence,
                model.id,
            )
            val superseded = _uiState.value.resultBitmap
            _uiState.update {
                it.copy(stage = BgStage.DONE, isProcessing = false, resultBitmap = resultBitmap)
            }
            // Free the previous cutout now — a stale 40 MB bitmap must not linger.
            if (superseded !== resultBitmap) recycleBitmap(superseded)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e("BgRemoverVM", "processImage timed out for ${model.id}", e)
            fail(context.getString(R.string.st_BackgroundRemover_Timeout, model.shortName), RetryAction.SWITCH_MODEL)
        } catch (e: OutOfMemoryError) {
            Log.e("BgRemoverVM", "processImage OOM", e)
            fail(context.getString(R.string.st_BackgroundRemover_TooLarge), RetryAction.PICK_IMAGE)
        } catch (e: UltraTooHeavyException) {
            Log.w("BgRemoverVM", "ultra refused: no headroom", e)
            fail(context.getString(R.string.st_BackgroundRemover_TooHeavyUltra), RetryAction.SWITCH_MODEL)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                // A catchable ORT failure on Ultra means this device can't hold the
                // 1024 graph — say so and point at Pro instead of a dead-end retry.
                if (model.id == "ultra_birefnet" && e is ai.onnxruntime.OrtException) {
                    Log.e("BgRemoverVM", "ultra ORT failed", e)
                    fail(context.getString(R.string.st_BackgroundRemover_TooHeavyUltra), RetryAction.SWITCH_MODEL)
                } else {
                    Log.e("BgRemoverVM", "processImage failed", e)
                    fail(context.getString(R.string.st_BackgroundRemover_ProcessingFailed), RetryAction.RETRY_PROCESS)
                }
            }
        }
    }

    private suspend fun runOnnxInference(bitmap: Bitmap, model: BackgroundModel): InferenceResult {
        val session = onnxSession?.takeIf { onnxSessionModelId == model.id }
            ?: throw IllegalStateException("ONNX session not ready.")

        val rawResult: InferenceResult = when (model.id) {
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
                // DIS recipe (rembg DisSession + upstream training): (x - 0.5) / 1.0.
                // ImageNet stats shift every activation and collapse the mask.
                val sz = model.inferenceInputSize
                val chw = ISNET_PREPROCESS_1024.toChw(bitmap, sz, sz)
                onnxEngine.runSingleMask(session, chw, 3, sz, sz)
            }
            "ultra_birefnet" -> {
                // BiRefNet recipe (rembg BiRefNetSessionGeneral): ImageNet norm at the
                // full 1024; raw logits → sigmoid + min-max handled in the post block
                // below. The export has FIXED [1,3,1024,1024] dims (verified from the
                // file) — any other feed size is rejected by ORT — so small phones are
                // gated out up-front (see hasUltraHeadroom) instead of crashing.
                if (!hasUltraHeadroom()) throw UltraTooHeavyException()
                val sz = model.inferenceInputSize
                val chw = IMAGENET_PREPROCESS_1024.toChw(bitmap, sz, sz)
                onnxEngine.runSingleMask(session, chw, 3, sz, sz)
            }
            else -> {
                // fast_general — ImageNet 320; post block below handles sigmoid/min-max.
                val sz = model.inferenceInputSize
                val chw = IMAGENET_PREPROCESS_320.toChw(bitmap, sz, sz)
                onnxEngine.runSingleMask(session, chw, 3, sz, sz)
            }
        }

        // Post: raw-logit exports (BiRefNet family) need sigmoid first — rembg does
        // the same explicitly. Then rembg-parity min-max stretch so a weak-but-
        // correct response never vanishes below the matting thresholds. RVM
        // returns calibrated alpha and is exempt from both.
        return if (model.id == "portrait_rvm") {
            rawResult
        } else {
            val activated = if (model.onnxPostSigmoid) {
                MaskDecoder.sigmoidArray(rawResult.mask.data)
            } else {
                rawResult.mask.data
            }
            val normalised = MaskDecoder.minMaxNormalize(activated)
            // Re-analyse the post-processed mask — the sigmoid + min-max step changes
            // the confidence profile (e.g. a noisy logit space becomes cleaner after
            // normalisation). This gives the matting engine the right quality signal.
            val cookedMask = rawResult.mask.copy(data = normalised)
            val cookedConfidence = com.frerox.toolz.data.media.MaskQualityAnalyzer.analyze(normalised)
            Log.d("BgRemoverVM", "ONNX ${model.id} mask=${cookedMask.maskW}x${cookedMask.maskH}")
            InferenceResult(cookedMask, cookedConfidence)
        }
    }

    /**
     * Whether this device can plausibly survive Ultra's ~1 GB native spike.
     *
     * The BiRefNet export fixes its input at 1024×1024, so there is no smaller
     * feed to fall back to — attempting inference without headroom ends in a
     * native abort / lmkd kill (uncatchable), not an error card. Refuse early
     * with an honest redirect to Pro instead.
     */
    private fun hasUltraHeadroom(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                ?: return false
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem >= ULTRA_MIN_TOTAL_RAM_BYTES && info.availMem >= ULTRA_MIN_AVAIL_RAM_BYTES
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Runs the matting engine inside a memory budget computed from the live heap.
     *
     * Matting holds ~12 bytes/px beyond the source (pixels IntArray + alpha
     * FloatArray + result Bitmap). Instead of attempting full-res and dying with
     * "image too big", we downscale to fit BEFORE allocating, then retry at half
     * size on residual OOM. The cutout may come back smaller than the photo on
     * tight heaps — honest and useful beats a crash.
     */
    private suspend fun runMatting(
        source: Bitmap,
        mask: FloatArray,
        maskW: Int,
        maskH: Int,
        confidence: MaskConfidence = MaskConfidence.UNKNOWN,
        modelId: String = "",
    ): Bitmap {
        val budgeted = fitToMemoryBudget(source)
        val downsized = budgeted !== source
        if (downsized) {
            Log.i(
                "BgRemoverVM",
                "Memory budget: matting at ${budgeted.width}×${budgeted.height} " +
                    "instead of ${source.width}×${source.height}",
            )
        }
        try {
            return try {
                BackgroundRemoverEngine.removeBackground(budgeted, mask, maskW, maskH, confidence, modelId)
            } catch (oom: OutOfMemoryError) {
                // Budget was optimistic (heap is shared and moving) — halve once more.
                Log.w("BgRemoverVM", "OOM at ${budgeted.width}×${budgeted.height} — halving", oom)
                val half = budgeted.halvedForMemory() ?: throw oom
                try {
                    BackgroundRemoverEngine.removeBackground(half, mask, maskW, maskH, confidence, modelId)
                } finally {
                    if (half !== budgeted) half.recycle()
                }
            }
        } finally {
            if (downsized) budgeted.recycle()
        }
    }

    /**
     * Largest bitmap whose matting footprint (~12 B/px transient + result) fits in
     * half the CURRENT free heap. Uses live headroom, not maxMemory fantasies, so a
     * busy device budgets tighter than a fresh one.
     */
    private fun fitToMemoryBudget(source: Bitmap): Bitmap {
        val px = source.width.toLong() * source.height
        if (px <= 0) return source
        val rt = Runtime.getRuntime()
        val headroom = (rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())).coerceAtLeast(0)
        val affordablePx = headroom / 2 / MATTING_BYTES_PER_PX
        if (affordablePx <= 0) {
            // Heap is already exhausted — force a small attempt, let the OOM path speak.
            return source.scaledToLongEdge(768) ?: source
        }
        if (px <= affordablePx) return source
        val s = kotlin.math.sqrt(affordablePx.toDouble() / px)
        val w = (source.width * s).toInt().coerceAtLeast(1)
        val h = (source.height * s).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(source, w, h, true)
        } catch (_: OutOfMemoryError) {
            source.scaledToLongEdge(768) ?: source
        }
    }

    private fun Bitmap.halvedForMemory(): Bitmap? {
        val w = (width / 2).coerceAtLeast(256)
        val h = (height / 2).coerceAtLeast(256)
        if (w >= width && h >= height) return null
        return try {
            Bitmap.createScaledBitmap(this, w, h, true)
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun Bitmap.scaledToLongEdge(edge: Int): Bitmap? {
        val s = edge.toFloat() / maxOf(width, height).toFloat()
        if (s >= 1f) return null
        return try {
            Bitmap.createScaledBitmap(
                this,
                (width * s).toInt().coerceAtLeast(1),
                (height * s).toInt().coerceAtLeast(1),
                true,
            )
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    companion object {
        /** Matting transient + result footprint per source pixel (conservative). */
        private const val MATTING_BYTES_PER_PX = 12L
        /** Ultra pre-flight: refuse below 6 GB total RAM — the 1024 spike would kill us. */
        private const val ULTRA_MIN_TOTAL_RAM_BYTES = 6L * 1024 * 1024 * 1024
        /** …and require 1.5 GB actually free right now (spike + matting headroom). */
        private const val ULTRA_MIN_AVAIL_RAM_BYTES = 1536L * 1024 * 1024
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
                        compositeOnImage(bitmap, background)
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

    private fun compositeOnImage(fg: Bitmap, custom: PreviewBackground.CustomImage): Bitmap {
        val out = Bitmap.createBitmap(fg.width, fg.height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val backdrop = custom.bitmap
        val blurred = if (custom.blurRadius > 0f) {
            val r = (custom.blurRadius * 25).toInt().coerceIn(1, 25)
            FastBlur.blurredBackdrop(backdrop, radius = r) ?: backdrop
        } else backdrop
        val bg = if (custom.scaleMode == BgScaleMode.COVER) {
            coverFit(blurred, fg.width, fg.height)
        } else {
            fitInside(blurred, fg.width, fg.height)
        }
        c.drawBitmap(bg, 0f, 0f, null)
        if (custom.dimAmount > 0f) {
            val dimPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                alpha = (custom.dimAmount * 255).toInt().coerceIn(0, 255)
            }
            c.drawRect(0f, 0f, fg.width.toFloat(), fg.height.toFloat(), dimPaint)
        }
        c.drawBitmap(fg, 0f, 0f, null)
        if (bg !== blurred && bg !== backdrop) recycleBitmap(bg)
        if (blurred !== backdrop) recycleBitmap(blurred)
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

    private fun fitInside(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (src.width * scale).toInt().coerceAtLeast(1)
        val sh = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val x = (w - sw) / 2f
        val y = (h - sh) / 2f
        c.drawBitmap(scaled, x, y, null)
        if (scaled !== src) scaled.recycle()
        return out
    }

    /** Export backdrop: small stack-blur upscaled smooth. Falls back to src, never crashes save. */
    private fun blurForBackdrop(src: Bitmap): Bitmap {
        return try {
            com.frerox.toolz.data.media.FastBlur.blurredBackdrop(src) ?: src
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
            RetryAction.OPEN_HUB, RetryAction.SWITCH_MODEL, RetryAction.PICK_IMAGE -> {
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
        // v3: adds selfie_segmenter.tflite (LiteRT/Instant removal) to the reclaim list.
        if (prefs.getBoolean("legacy_reclaimed_v3", false)) return
        prefs.edit().putBoolean("legacy_reclaimed_v3", true).apply()
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
enum class BgStage { IDLE, DOWNLOADING, WARMING_UP, SEGMENTING, MATTING, DONE, FAILED }

/** Thrown when the device has no headroom for Ultra's fixed 1024 graph. */
private class UltraTooHeavyException : Exception()

enum class RetryAction { RETRY_DOWNLOAD, RETRY_PROCESS, OPEN_HUB, SWITCH_MODEL, PICK_IMAGE }

data class BgFailure(val message: String, val retry: RetryAction?)

enum class BgScaleMode { COVER, FIT }

sealed interface PreviewBackground {
    data object Transparent : PreviewBackground
    data object White : PreviewBackground
    data class Color(val color: Int) : PreviewBackground
    data object Blur : PreviewBackground
    data class CustomImage(
        val bitmap: Bitmap,
        val scaleMode: BgScaleMode = BgScaleMode.COVER,
        val blurRadius: Float = 0f,
        val dimAmount: Float = 0f,
    ) : PreviewBackground
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
