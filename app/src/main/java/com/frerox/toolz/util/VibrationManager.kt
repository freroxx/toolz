package com.frerox.toolz.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VibrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
            ?: @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    val hapticEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedback
        .stateIn(scope, SharingStarted.Eagerly, true)

    val hapticIntensity: StateFlow<Float> = settingsRepository.hapticIntensity
        .stateIn(scope, SharingStarted.Eagerly, 0.5f)

    private val amplitude: Int
        get() {
            val intensity = hapticIntensity.value.coerceIn(0.1f, 1f)
            return (Math.sqrt(intensity.toDouble()) * 255).toInt().coerceIn(1, 255)
        }

    private val isFullStrength: Boolean
        get() = hapticIntensity.value >= 0.90f

    private val canVibrate: Boolean
        get() = hapticEnabled.value && vibrator.hasVibrator()

    fun vibrateClick() {
        if (!canVibrate) return
        if (isFullStrength) {
            if (vibratePredefined(VibrationEffect.EFFECT_CLICK)) return
        }
        vibrateOneShot(durationMs = 25L, amp = amplitude)
    }

    fun vibrateTick() {
        if (!canVibrate) return
        if (isFullStrength) {
            if (vibratePredefined(VibrationEffect.EFFECT_TICK)) return
        }
        vibrateOneShot(durationMs = 12L, amp = (amplitude * 0.70f).toInt().coerceIn(1, 255))
    }

    fun vibrateLongClick() {
        if (!canVibrate) return
        if (isFullStrength) {
            if (vibratePredefined(VibrationEffect.EFFECT_HEAVY_CLICK)) return
        }
        vibrateOneShot(durationMs = 50L, amp = (amplitude * 1.1f).toInt().coerceIn(1, 255))
    }

    fun vibrateSuccess() {
        if (!canVibrate) return
        val amp = amplitude
        vibrateWaveform(
            timings    = longArrayOf(0L, 20L, 80L, 40L),
            amplitudes = intArrayOf(0,  amp, 0,   (amp * 1.15f).toInt().coerceIn(1, 255)),
        )
    }

    fun vibrateError() {
        if (!canVibrate) return
        val amp = amplitude
        vibrateWaveform(
            timings    = longArrayOf(0L, 40L, 60L, 40L),
            amplitudes = intArrayOf(0,  amp, 0,  amp),
        )
    }

    fun vibrate(durationMs: Long = 20L) {
        if (!canVibrate) return
        vibrateOneShot(durationMs, amplitude)
    }

    private fun vibratePredefined(effectId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val effect = VibrationEffect.createPredefined(effectId)
            dispatchEffect(effect)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun vibrateOneShot(durationMs: Long, amp: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dispatchEffect(VibrationEffect.createOneShot(durationMs, amp))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            } catch (_: Exception) {}
        }
    }

    private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dispatchEffect(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                val totalOn = timings.filterIndexed { i, _ -> i % 2 == 1 }.sum()
                @Suppress("DEPRECATION")
                vibrator.vibrate(totalOn)
            }
        } catch (e: Exception) {
            vibrateOneShot(durationMs = 25L, amp = amplitude)
        }
    }

    private fun dispatchEffect(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
            vibrator.vibrate(effect, attrs)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect)
        }
    }
}
