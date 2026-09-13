/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.media

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.nio.FloatBuffer

/**
 * ONNX Runtime inference backend for quality-tier segmentation models.
 *
 * Design:
 * - One shared [OrtEnvironment] per process (ORT requirement), one [OrtSession] per model.
 * - Sessions are created by [createSession] on whatever thread the caller dispatches to
 *   (must be non-main). This class owns the env; sessions are caller-owned.
 * - Thread caps: [cpuThreads] is deliberately conservative — the ORT internal pool must
 *   not saturate all cores or the UI thread pool starves (ANR). For Ultra (Swin) we cap
 *   at 4 intra + 2 inter to avoid thermal spikes on mid-range SoCs.
 * - NNAPI: attempted for all models except Ultra (its Swin graph compiles to a 8-second
 *   NNAPI graph on most vendors vs. 2 seconds CPU-BASIC_OPT — and then falls back to CPU
 *   op-by-op anyway, making NNAPI a net loss).
 * - CPU graph optimisation: Ultra uses BASIC_OPT (graph already optimised at export;
 *   ALL_OPT adds 6+ extra seconds for negligible runtime gain on Swin).
 *   All other models keep ALL_OPT.
 */
class OnnxInferenceEngine : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(ORT_ENV_NAME)

    /**
     * Creates a session for a verified model file.
     *
     * Call this only from a non-main thread (e.g. [kotlinx.coroutines.Dispatchers.Default]).
     * Session graph compilation for large models (Ultra/Pro) can take 2–8 seconds.
     *
     * @param modelId    Used to pick model-specific optimisation flags.
     * @param tryNnapi   Attempt the NNAPI execution provider before CPU. Failures fall back
     *                   transparently. Automatically overridden to false for Ultra.
     */
    fun createSession(modelFile: File, modelId: String = "", tryNnapi: Boolean = true): OrtSession {
        val isUltra = modelId == "ultra_birefnet" || modelFile.name.contains("birefnet", ignoreCase = true)
        // Ultra: always CPU-only — NNAPI driver compilation hangs or crashes on the Swin graph.
        val actualTryNnapi = tryNnapi && !isUltra

        if (actualTryNnapi) {
            try {
                val opts = buildSessionOptions(modelId, useBasicOpt = false)
                opts.addNnapi()
                return env.createSession(modelFile.absolutePath, opts).also {
                    Log.d(TAG, "NNAPI EP session created for ${modelFile.name}")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "NNAPI failed for ${modelFile.name}, falling back to CPU: ${e.message}")
            }
        }

        // Ultra uses BASIC_OPT: the Swin graph is already optimised at export time.
        // ALL_OPT adds 6+ seconds of recompilation for near-zero runtime improvement
        // on Snapdragon class hardware — and causes an ANR-class pause on the first load.
        val useBasicOpt = isUltra
        return try {
            val opts = buildSessionOptions(modelId, useBasicOpt = useBasicOpt)
            env.createSession(modelFile.absolutePath, opts).also {
                Log.d(TAG, "CPU ${if (useBasicOpt) "BASIC_OPT" else "ALL_OPT"} session created for ${modelFile.name}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "CPU session failed for ${modelFile.name}: ${e.message}, retrying with BASIC_OPT")
            val opts = buildSessionOptions(modelId, useBasicOpt = true)
            env.createSession(modelFile.absolutePath, opts)
        }
    }

    /**
     * Builds session options with model-appropriate thread counts.
     *
     * Thread strategy:
     * - Ultra (Swin): intra=4, inter=2. Swin's attention heads benefit from a modest
     *   inter-op count, but exceeding 4 intra causes thermal throttle on mid-range SoCs.
     * - All others: intra=min(cpuCount, 4), inter=1. Single-path graphs don't benefit
     *   from inter parallelism and the extra thread just wastes context-switch budget.
     * - In all cases we leave at least 1 core free for the UI and coroutine dispatchers.
     */
    private fun buildSessionOptions(modelId: String, useBasicOpt: Boolean): OrtSession.SessionOptions {
        val isUltra = modelId == "ultra_birefnet"
        val cpuCount = Runtime.getRuntime().availableProcessors()
        // Leave at least 1 core free for the UI thread, coerced into [2, 4].
        val intraThreads = (cpuCount - 1).coerceIn(2, 4)
        val interThreads = if (isUltra) 2 else 1

        return OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(intraThreads)
            setInterOpNumThreads(interThreads)
            setOptimizationLevel(
                if (useBasicOpt) OrtSession.SessionOptions.OptLevel.BASIC_OPT
                else OrtSession.SessionOptions.OptLevel.ALL_OPT,
            )
        }
    }

    // ── Inference ──────────────────────────────────────────────────────────────

    /**
     * Single-input / single-output segmentation (U²-Net, ISNet, MODNet-style).
     * First graph input, first graph output; output[0] is the foreground mask
     * with shape [1,1,H,W] (sigmoid already applied by these graphs).
     *
     * @return [InferenceResult] with mask in 0..1 and computed [MaskConfidence].
     */
    fun runSingleMask(
        session: OrtSession,
        chw: FloatArray,
        channels: Int,
        height: Int,
        width: Int,
    ): InferenceResult {
        // NOTE: intentionally NOT using session.inputInfo here. ORT's native
        // getInputInfo calls back into the NodeInfo Java constructor, which R8
        // can strip (ORT 1.29 consumer rules don't keep it) causing a fatal
        // abort in release builds. getInputNames is a pure-Java path and safe.
        val inputName = session.inputNames.firstOrNull()
            ?: throw OrtException("ONNX graph has no inputs")
        OnnxTensor.createTensor(
            env, FloatBuffer.wrap(chw),
            longArrayOf(1, channels.toLong(), height.toLong(), width.toLong()),
        ).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val raw = result.get(0).value
                val singleMask = extractFirstChannel(raw, "single")
                val confidence = MaskQualityAnalyzer.analyze(singleMask.data)
                return InferenceResult(singleMask, confidence)
            }
        }
    }

    /**
     * Robust Video Matting (single still image = one pass, zero recurrent states).
     * Contract verified against upstream docs: inputs [src, r1i..r4i, downsample_ratio],
     * zero [1,1,1,1] initial states; outputs [fgr, pha, r1o..r4o].
     *
     * @param downsampleRatio keep the internal working resolution in the 256–512px
     * band (upstream guidance). Computed by the caller from the fed resolution.
     */
    fun runRvm(
        session: OrtSession,
        chw: FloatArray,
        height: Int,
        width: Int,
        downsampleRatio: Float,
    ): InferenceResult {
        val names = session.inputNames
        fun need(sub: String): String = names.firstOrNull { it.contains(sub, ignoreCase = true) }
            ?: names.firstOrNull()
            ?: throw OrtException("ONNX graph has no inputs")

        val tensors = mutableListOf<OnnxTensor>()
        try {
            val src = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(chw), longArrayOf(1, 3, height.toLong(), width.toLong()),
            )
            tensors += src
            // Exact recurrent names (verified: r1i..r4i). Never invent names the
            // graph doesn't declare — ORT rejects unknown inputs outright.
            val recNames = (1..4).mapNotNull { k ->
                names.firstOrNull { it.equals("r${k}i", ignoreCase = true) }
            }
            if (recNames.size != 4) {
                throw OrtException("RVM graph missing recurrent inputs (found $recNames)")
            }
            val inputs = mutableMapOf<String, OnnxTensor>(need("src") to src)
            for (key in recNames) {
                val zero = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(0f)), longArrayOf(1, 1, 1, 1))
                tensors += zero
                inputs[key] = zero
            }
            val dsr = OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(downsampleRatio)), longArrayOf(1))
            tensors += dsr
            inputs[names.firstOrNull { it.contains("downsample", ignoreCase = true) } ?: "downsample_ratio"] = dsr

            session.run(inputs).use { result ->
                // 'pha' output if named, else index 1 (fgr=0, pha=1 per upstream spec).
                val phaValue: Any? = result.get("pha").map { it.value }.orElse(null)
                    ?: result.get(1).value
                val singleMask = extractFirstChannel(phaValue, "rvm")
                val confidence = MaskQualityAnalyzer.analyze(singleMask.data)
                return InferenceResult(singleMask, confidence)
            }
        } finally {
            tensors.forEach { runCatching { it.close() } }
        }
    }

    override fun close() {
        runCatching { env.close() }
    }

    companion object {
        private const val TAG = "OnnxEngine"
        private const val ORT_ENV_NAME = "toolz-bg-remover"
    }
}

// ── Result types ───────────────────────────────────────────────────────────────

/**
 * The structured output of one inference pass.
 *
 * Carries both the raw mask pixels and a quality profile computed from them,
 * so downstream stages (matting engine, ViewModel) can adapt without re-scanning.
 */
data class InferenceResult(
    val mask: SingleMask,
    val confidence: MaskConfidence,
)

/** Foreground mask in 0..1, row-major [maskW × maskH]. */
data class SingleMask(val data: FloatArray, val maskW: Int, val maskH: Int)

// ── Preprocessing contracts ────────────────────────────────────────────────────

/**
 * Explicit preprocessing contract per ONNX model. Normalization bugs are silent
 * quality killers — this makes them impossible to mix up.
 */
data class OnnxPreprocess(
    /** Square model input (RVM: long-edge cap, aspect preserved). */
    val inputSize: Int,
    /** Per-channel mean (0..1 space). Use 0 for scale-only models (RVM). */
    val mean: FloatArray,
    /** Per-channel std (0..1 space). Use 1 for scale-only models (RVM). */
    val std: FloatArray,
) {
    /** Bitmap pixels → CHW float tensor. */
    fun toChw(bitmap: Bitmap, targetW: Int = inputSize, targetH: Int = inputSize): FloatArray {
        val scaled = if (bitmap.width == targetW && bitmap.height == targetH) bitmap
        else Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        try {
            val px = IntArray(targetW * targetH)
            scaled.getPixels(px, 0, targetW, 0, 0, targetW, targetH)
            val out = FloatArray(3 * targetW * targetH)
            val plane = targetW * targetH
            for (i in px.indices) {
                val c = px[i]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                out[i] = (r - mean[0]) / std[0]
                out[plane + i] = (g - mean[1]) / std[1]
                out[2 * plane + i] = (b - mean[2]) / std[2]
            }
            return out
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }
}

/** ImageNet normalization shared by U²-Net / ISNet families (rembg-compatible). */
val IMAGENET_PREPROCESS_320 = OnnxPreprocess(
    inputSize = 320,
    mean = floatArrayOf(0.485f, 0.456f, 0.406f),
    std = floatArrayOf(0.229f, 0.224f, 0.225f),
)

val IMAGENET_PREPROCESS_1024 = OnnxPreprocess(
    inputSize = 1024,
    mean = floatArrayOf(0.485f, 0.456f, 0.406f),
    std = floatArrayOf(0.229f, 0.224f, 0.225f),
)

/**
 * DIS/ISNet normalization, exactly as rembg's DisSession AND the upstream DIS
 * training recipe: (x - 0.5) / 1.0, i.e. input range [-0.5, +0.5].
 * ImageNet stats here shift every activation and collapse the mask (verified:
 * mask/u2netp IoU 0.53 → 0.94 after fixing).
 */
val ISNET_PREPROCESS_1024 = OnnxPreprocess(
    inputSize = 1024,
    mean = floatArrayOf(0.5f, 0.5f, 0.5f),
    std = floatArrayOf(1f, 1f, 1f),
)

/** RVM takes 0..1 RGB directly, any aspect (long edge capped by caller). */
val RVM_PREPROCESS = OnnxPreprocess(
    inputSize = 512,
    mean = floatArrayOf(0f, 0f, 0f),
    std = floatArrayOf(1f, 1f, 1f),
)

// ── Internal mask extraction ───────────────────────────────────────────────────

@Suppress("UNCHECKED_CAST")
private fun extractFirstChannel(raw: Any?, tag: String): SingleMask {
    fun fail(what: String): Nothing = throw OrtException("Unexpected $tag mask $what")
    // Expected [1][1][H][W] float nest (ORT Java boxes primitives as Array<FloatArray> or Array<Array<*>>).
    val batch = (raw as? Array<*>)?.getOrNull(0) as? Array<*> ?: fail("layout: ${raw?.javaClass}")
    val ch0 = batch[0] as? Array<*> ?: fail("channels")
    val h = ch0.size
    if (h == 0) fail("empty")
    return when (val row0 = ch0[0]) {
        is FloatArray -> {
            val w = row0.size
            val out = FloatArray(w * h)
            row0.copyInto(out, 0, 0, w)
            for (y in 1 until h) {
                val row = ch0[y] as? FloatArray ?: fail("row type")
                row.copyInto(out, y * w, 0, w)
            }
            SingleMask(out, w, h)
        }
        is Array<*> -> {
            val w = row0.size
            val out = FloatArray(w * h)
            for (y in 0 until h) {
                val row = ch0[y] as? Array<*> ?: fail("row type")
                for (x in 0 until w) {
                    out[y * w + x] = (row[x] as? Number ?: fail("cell type")).toFloat().coerceIn(0f, 1f)
                }
            }
            SingleMask(out, w, h)
        }
        else -> fail("rows")
    }
}
