package com.frerox.toolz.util

import android.content.Context
import android.os.Build
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
    settingsRepository: SettingsRepository
) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val hapticEnabled: StateFlow<Boolean> = settingsRepository.hapticFeedback
        .stateIn(scope, SharingStarted.Eagerly, true)

    private val hapticIntensity: StateFlow<Float> = settingsRepository.hapticIntensity
        .stateIn(scope, SharingStarted.Eagerly, 0.5f)

    fun vibrate(duration: Long = 20L) {
        if (!hapticEnabled.value) return
        
        val intensity = hapticIntensity.value
        val amplitude = (intensity * 255).toInt().coerceIn(1, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } catch (e: Exception) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
    
    fun vibrateClick() = vibrate(15L)
    fun vibrateLongClick() = vibrate(40L)
    fun vibrateSuccess() = vibrate(60L)
    fun vibrateError() = vibrate(100L)
}
