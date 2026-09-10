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
 * Design notes:
 * - One shared [OrtEnvironment] per process (ORT requirement), one [OrtSession] per model.
 * - Sessions are created lazily by the caller (ViewModel) and must be closed on model
 *   switch / ViewModel clear. This class owns the env; sessions are caller-owned.
 * - CPU EP first, NNAPI attempted as an accelerator with silent CPU fallback — NNAPI
 *   driver quality varies wildly across vendors and several segmentation graphs fall
 *   back to CPU per-operator anyway.
 * - All preprocessing is explicit per-model (see [OnnxPreprocess]) so a swapped model
 *   file can never silently get the wrong normalization.
 */
class OnnxInferenceEngine : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(ORT_ENV_NAME)

    /**
     * Creates a session for a verified model file.
     *
     * @param tryNnapi attempt NNAPI execution provider before CPU. Failures (missing
     * driver, unsupported ops) fall back to CPU automatically.
     */
    fun createSession(modelFile: File, tryNnapi: Boolean = true): OrtSession {
        val opts = OrtSession.SessionOptions().apply {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            setIntraOpNumThreads(threads)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        if (tryNnapi) {
            try {
                opts.addNnapi()
                Log.d(TAG, "NNAPI EP registered for ${modelFile.name}")
            } catch (e: OrtException) {
                Log.w(TAG, "NNAPI unavailable, CPU only for ${modelFile.name}: ${e.message}")
            }
        }
        return env.createSession(modelFile.absolutePath, opts)
    }

    /**
     * Single-input / single-output segmentation (U²-Net, ISNet, MODNet-style):
     * first graph input, first graph output, output[0] is the foreground mask
     * with shape [1,1,H,W] (sigmoid already applied by these graphs).
     *
     * @return mask in 0..1 with dimensions [maskW × maskH] as reported by the graph.
     */
    fun runSingleMask(
        session: OrtSession,
        chw: FloatArray,
        channels: Int,
        height: Int,
        width: Int,
    ): SingleMask {
        // Prefer the 4-D image input explicitly — never assume graph input order.
        val inputName = try {
            session.inputInfo.entries
                .firstOrNull { (_, info) ->
                    (info.info as? ai.onnxruntime.TensorInfo)?.getShape()?.size == 4
                }?.key
        } catch (_: Exception) {
            null
        } ?: session.inputNames.firstOrNull()
        ?: throw OrtException("ONNX graph has no inputs")
        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, channels.toLong(), height.toLong(), width.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val raw = result.get(0).value
                return extractFirstChannel(raw, "single")
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
    ): SingleMask {
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
                return extractFirstChannel(phaValue, "rvm")
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

/** Foreground mask in 0..1, row-major [maskW × maskH]. */
data class SingleMask(val data: FloatArray, val maskW: Int, val maskH: Int)

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

/** RVM takes 0..1 RGB directly, any aspect (long edge capped by caller). */
val RVM_PREPROCESS = OnnxPreprocess(
    inputSize = 512,
    mean = floatArrayOf(0f, 0f, 0f),
    std = floatArrayOf(1f, 1f, 1f),
)

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
