/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.media

import java.nio.ByteBuffer
import kotlin.math.exp

/**
 * Clean, testable mask decoding — extracted from ViewModel.
 * Handles all TFLite output variants observed across our hub models.
 */
object MaskDecoder {

    /**
     * Decodes raw TFLite output ByteBuffer into a FloatArray mask [W*H] in 0..1.
     *
     * @param outputBuffer rewound buffer after interpreter.run
     * @param isFloatOutput true if DataType.FLOAT32 else quantized UINT8
     * @param outputShape as reported by interpreter.getOutputTensor(0).shape()
     * @param modelW model input width
     * @param modelH model input height
     * @param modelId e.g. "selfie_multiclass" to pick softmax strategy
     */
    fun decode(
        outputBuffer: ByteBuffer,
        isFloatOutput: Boolean,
        outputShape: IntArray,
        modelW: Int,
        modelH: Int,
        modelId: String,
    ): FloatArray {
        val total = modelW * modelH
        val out = FloatArray(total)

        // Infer channel count + layout
        var numChannels = 1
        var isNHWC = true
        if (outputShape.size == 4) {
            // shapes like [1, H, W, C] or [1, C, H, W]
            if (outputShape[3] in 1..64) {
                numChannels = outputShape[3]
                isNHWC = true
            } else if (outputShape[1] in 1..64) {
                numChannels = outputShape[1]
                isNHWC = false
            }
        } else if (outputShape.size == 3) {
            // [H, W, C] rare
            if (outputShape[2] in 1..64) {
                numChannels = outputShape[2]
                isNHWC = true
            }
        } else if (outputShape.size == 2) {
            // [H*W, C] degenerate
            numChannels = outputShape[1]
            isNHWC = true
        }

        if (isFloatOutput) {
            val fb = outputBuffer.asFloatBuffer()
            when {
                numChannels == 1 -> {
                    for (i in 0 until total) {
                        val v = fb.get(i)
                        out[i] = if (v in 0f..1f) v else sigmoid(v)
                    }
                }
                modelId == "selfie_multiclass" -> {
                    // 6 channels, background = 0, foreground = 1..5 sum
                    for (i in 0 until total) {
                        val chans = FloatArray(numChannels) { c ->
                            val off = if (isNHWC) i * numChannels + c else c * total + i
                            fb.get(off)
                        }
                        val max = chans.maxOrNull() ?: 0f
                        var sumExp = 0f
                        var fgExp = 0f
                        for (c in chans.indices) {
                            val e = exp((chans[c] - max).toDouble()).toFloat()
                            sumExp += e
                            if (c > 0) fgExp += e
                        }
                        out[i] = (fgExp / (sumExp + 1e-6f)).coerceIn(0f, 1f)
                    }
                }
                modelId == "deeplabv3_objects" -> {
                    // 21 channels: 0 = background
                    for (i in 0 until total) {
                        val chans = FloatArray(numChannels) { c ->
                            val off = if (isNHWC) i * numChannels + c else c * total + i
                            fb.get(off)
                        }
                        val max = chans.maxOrNull() ?: 0f
                        var sumExp = 0f
                        var bgExp = 0f
                        for (c in chans.indices) {
                            val e = exp((chans[c] - max).toDouble()).toFloat()
                            sumExp += e
                            if (c == 0) bgExp = e
                        }
                        val bgProb = bgExp / (sumExp + 1e-6f)
                        out[i] = (1f - bgProb).coerceIn(0f, 1f)
                    }
                }
                else -> {
                    // generic multi-channel: last channel as foreground (e.g., 2-ch selfie)
                    val fgIdx = numChannels - 1
                    for (i in 0 until total) {
                        val off = if (isNHWC) i * numChannels + fgIdx else fgIdx * total + i
                        val v = fb.get(off)
                        out[i] = if (v in 0f..1f) v else sigmoid(v)
                    }
                }
            }
        } else {
            // Quantized UINT8 0..255
            outputBuffer.rewind()
            for (i in 0 until total) {
                val off = if (numChannels > 1) {
                    if (isNHWC) i * numChannels + (numChannels - 1) else (numChannels - 1) * total + i
                } else i
                out[i] = (outputBuffer.get(off).toInt() and 0xFF) / 255f
            }
        }
        return out
    }

    private fun sigmoid(v: Float): Float = (1f / (1f + exp(-v.toDouble()))).toFloat()
}
