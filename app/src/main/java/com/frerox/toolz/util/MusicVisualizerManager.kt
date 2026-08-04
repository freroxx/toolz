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

package com.frerox.toolz.util

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class MusicVisualizerManager @Inject constructor() {

    private val fftSize = 1024
    private val pcmBuffer = ShortArray(fftSize)
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val spectrum = FloatArray(64)
    
    private val lock = Any()
    private var hasNewData = false

    fun onPcmData(buffer: ByteBuffer) {
        synchronized(lock) {
            val remaining = buffer.remaining() / 2 // bytes to shorts
            val toCopy = min(remaining, fftSize)
            for (i in 0 until toCopy) {
                pcmBuffer[i] = buffer.short
            }
            hasNewData = true
        }
    }

    fun getSpectrum(): FloatArray? {
        synchronized(lock) {
            if (!hasNewData) return null
            hasNewData = false
            
            // Prepare for FFT
            for (i in 0 until fftSize) {
                // Apply Hanning window
                val window = 0.5f * (1f - cos(2f * PI.toFloat() * i / (fftSize - 1)))
                real[i] = pcmBuffer[i].toFloat() * window
                imag[i] = 0f
            }
        }

        // Perform FFT
        fft(real, imag)

        // Compute magnitude and group into 64 bins
        val n = fftSize / 2
        for (i in 0 until 64) {
            val start = (2.0.pow(i / 10.6) - 1).toInt().coerceIn(0, n - 1)
            val end = (2.0.pow((i + 1) / 10.6) - 1).toInt().coerceIn(start + 1, n)
            
            var sumSq = 0f
            for (j in start until end) {
                val mag = sqrt(real[j] * real[j] + imag[j] * imag[j])
                sumSq += mag * mag
            }
            val rms = if (end > start) sqrt(sumSq / (end - start)) else 0f
            
            // Perceptual weighting and scaling
            val shelf = 1f + sqrt(i / 63f) * 1.6f
            spectrum[i] = (rms * shelf * 0.005f).coerceIn(0f, 100f)
        }
        
        return spectrum.copyOf()
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var m = n / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        var m = 2
        while (m <= n) {
            val theta = -2f * PI.toFloat() / m
            val wR = cos(theta)
            val wI = sin(theta)
            var i = 0
            while (i < n) {
                var w_mR = 1f
                var w_mI = 0f
                for (k in 0 until m / 2) {
                    val tR = w_mR * real[i + k + m / 2] - w_mI * imag[i + k + m / 2]
                    val tI = w_mR * imag[i + k + m / 2] + w_mI * real[i + k + m / 2]
                    real[i + k + m / 2] = real[i + k] - tR
                    imag[i + k + m / 2] = imag[i + k] - tI
                    real[i + k] += tR
                    imag[i + k] += tI
                    val next_w_mR = w_mR * wR - w_mI * wI
                    w_mI = w_mR * wI + w_mI * wR
                    w_mR = next_w_mR
                }
                i += m
            }
            m *= 2
        }
    }
}

@androidx.media3.common.util.UnstableApi
class VisualizerAudioProcessor(private val manager: MusicVisualizerManager) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputBuffer.hasRemaining()) {
            val duplicate = inputBuffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
            manager.onPcmData(duplicate)
        }
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }
}
