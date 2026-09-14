/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.media

import kotlin.math.exp

/**
 * Mask post-processing for the ONNX lineup — sigmoid for raw-logit exports
 * (BiRefNet family) and rembg-parity min-max stretch.
 */
object MaskDecoder {

    private fun sigmoid(v: Float): Float = (1f / (1f + exp(-v.toDouble()))).toFloat()

    /**
     * Element-wise sigmoid, for ONNX exports that emit raw logits
     * (e.g. BiRefNet family — rembg applies sigmoid explicitly there too).
     */
    fun sigmoidArray(data: FloatArray): FloatArray {
        val out = FloatArray(data.size)
        for (i in data.indices) out[i] = sigmoid(data[i])
        return out
    }

    /**
     * rembg-parity min-max stretch: (m - min) / (max - min).
     * Guarantees the full 0..1 range so a weak-but-correct response never
     * vanishes below the matting thresholds ("empty output").
     * Degenerate (flat) input returns zeros instead of exploding — strictly
     * more honest than rembg, which divides unguarded.
     */
    fun minMaxNormalize(data: FloatArray): FloatArray {
        if (data.isEmpty()) return data
        var mn = Float.POSITIVE_INFINITY
        var mx = Float.NEGATIVE_INFINITY
        for (v in data) {
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        if (!(mx - mn > 1e-6f)) return FloatArray(data.size)
        val range = mx - mn
        val out = FloatArray(data.size)
        for (i in data.indices) out[i] = ((data[i] - mn) / range).coerceIn(0f, 1f)
        return out
    }
}
