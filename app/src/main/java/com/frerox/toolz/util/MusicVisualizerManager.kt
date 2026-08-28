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

/**
 * Enhanced MusicVisualizerManager using a zero-latency circular buffer.
 * Processes audio data in real-time to provide high-sensitivity spectrum analysis.
 */
@Singleton
class MusicVisualizerManager @Inject constructor() {

    private val fftSize = 1024
    // Circular buffer to hold PCM data. 16384 samples (~370ms at 44.1kHz)
    private val circularBufferSize = 16384
    private val circularBuffer = ShortArray(circularBufferSize)
    private var writePos = 0
    
    // FFT buffers
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val spectrum = FloatArray(64)
    private val prevSpectrum = FloatArray(64)
    
    private val lock = Any()
    private var totalSamplesReceived = 0L
    @Volatile var currentSampleRate: Int = 44100
    @Volatile private var autoSensitivityEnabled: Boolean = false

    /**
     * Called by the AudioProcessor to feed PCM data into the manager.
     */
    fun onPcmData(buffer: ByteBuffer) {
        synchronized(lock) {
            val shorts = buffer.remaining() / 2
            repeat(shorts) {
                circularBuffer[writePos] = buffer.short
                writePos = (writePos + 1) % circularBufferSize
            }
            totalSamplesReceived += shorts
        }
    }

    fun onPcmFloatData(buffer: ByteBuffer) {
        synchronized(lock) {
            val floats = buffer.remaining() / 4
            repeat(floats) {
                val f = buffer.float
                // float -1..1 -> short -32768..32767
                val s = (f.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                circularBuffer[writePos] = s
                writePos = (writePos + 1) % circularBufferSize
            }
            totalSamplesReceived += floats
        }
    }

    fun setAutoSensitivity(enabled: Boolean) { autoSensitivityEnabled = enabled }

    /**
     * Extracts a spectrum analysis from the most recent audio data.
     * Guaranteed to return a valid spectrum as long as the first 1024 samples have been received.
     * @param sampleRate Optional override; if <=0 uses [currentSampleRate].
     */
    fun getSpectrum(sampleRate: Int = currentSampleRate): FloatArray? {
        synchronized(lock) {
            // Wait for the first window to fill before starting
            if (totalSamplesReceived < fftSize) return null
            
            // Always read the ABSOLUTE LATEST window of data. 
            // We sample the tail of the circular buffer.
            var readPos = (writePos - fftSize + circularBufferSize) % circularBufferSize
            for (i in 0 until fftSize) {
                // Apply Hamming window for better frequency isolation
                val window = 0.54f - 0.46f * cos(2f * PI.toFloat() * i / (fftSize - 1))
                real[i] = circularBuffer[readPos].toFloat() * window
                imag[i] = 0f
                readPos = (readPos + 1) % circularBufferSize
            }
        }

        // Perform FFT
        fft(real, imag)

        // Compute magnitude and group into 64 logarithmic bins
        val n = fftSize / 2
        for (i in 0 until 64) {
            // Logarithmic mapping: 20Hz to 20kHz
            val freqStart = 20.0 * (20000.0 / 20.0).pow(i / 64.0)
            val freqEnd = 20.0 * (20000.0 / 20.0).pow((i + 1) / 64.0)
            
            // P2-11 fix: use real sampleRate, not hardcoded 44100
            val effectiveRate = if (sampleRate > 0) sampleRate else currentSampleRate
            val binWidth = effectiveRate.toDouble() / fftSize
            val startBin = (freqStart / binWidth).toInt().coerceIn(0, n - 1)
            val endBin = (freqEnd / binWidth).toInt().coerceIn(startBin + 1, n)
            
            var maxMag = 0f
            for (j in startBin until endBin) {
                val mag = sqrt(real[j] * real[j] + imag[j] * imag[j])
                if (mag > maxMag) maxMag = mag
            }
            
            // Extreme sensitivity weighting:
            // High boost for mid/high ranges where energy is usually lower.
            val weighting = when {
                i < 8  -> 1.2f  // Bass
                i < 24 -> 2.2f  // Low Mids
                i < 48 -> 3.5f  // High Mids
                else   -> 5.0f  // Treble
            }
            // Sensitivity coefficient — auto scales with RMS when auto mode ON
            var coeff = 0.010f
            if (autoSensitivityEnabled) {
                // estimate energy-dependent scaling: low-energy content gets higher coeff
                val avg = prevSpectrum.average().toFloat().coerceIn(1f, 60f)
                coeff = (0.010f * (60f / avg).coerceIn(0.85f, 2.2f))
            }
            var value = (maxMag * weighting * coeff).coerceIn(0f, 100f)
            
            // Temporal smoothing (Less smoothing for faster reactions)
            value = prevSpectrum[i] * 0.15f + value * 0.85f
            spectrum[i] = value
            prevSpectrum[i] = value
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

    private var inputEncoding: Int = C.ENCODING_PCM_16BIT

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        inputEncoding = inputAudioFormat.encoding
        manager.currentSampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: manager.currentSampleRate
        // P2-04 fix: support float PCM (common on high-res devices) by converting to short; bypass others gracefully.
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Extract PCM data for visualization without consuming the main buffer
        try {
            val duplicate = inputBuffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
            if (inputEncoding == C.ENCODING_PCM_FLOAT) {
                manager.onPcmFloatData(duplicate)
            } else {
                manager.onPcmData(duplicate)
            }
        } catch (_: Exception) {
            // visualizer failure must never break audio pipeline
        }

        // Pass-through to output. We use duplicate() to avoid the "source buffer is this buffer"
        // exception if Media3's buffer management ever reuses the same object for input and output.
        val output = replaceOutputBuffer(remaining)
        if (output !== inputBuffer) {
            output.put(inputBuffer)
        } else {
            // If they are the same instance, replaceOutputBuffer just called buffer.clear().
            // The data is technically already there, but clear() wiped it. 
            // We advance position to signal consumption.
            inputBuffer.position(inputBuffer.limit())
        }
        output.flip()
    }
}
