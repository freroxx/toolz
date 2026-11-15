/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Uses backdrop library © Kyant, Apache License 2.0
 * https://github.com/Kyant0/AndroidLiquidGlass
 */

package com.frerox.toolz.ui.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

/**
 * Customization parameters for Liquid Glass mode (controlled by user sliders).
 */
data class LiquidGlassConfig(
    val blurRadiusDp: Float = 8f,
    val tintAlpha: Float = 0.25f,
    val refractionDp: Float = 24f,
    val performanceMode: Boolean = false,
)

val LocalLiquidGlassConfig = compositionLocalOf { LiquidGlassConfig() }

/**
 * Whether Liquid Glass mode is active.
 * Read by every Expressive component to decide between glass and normal rendering.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/**
 * The shared [Backdrop] provided by [LiquidGlassSceneRoot].
 * Glass components read this to access the blurred content behind them.
 * Null outside a scene root (safe — components check [LocalLiquidGlassEnabled] first).
 */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Root composable that activates the Liquid Glass scene.
 *
 * Place this around any screen/surface that has a rendered background
 * (wallpaper, gradient, etc.). It provides the [Backdrop] context that
 * makes blur/refraction/lens effects work for every child component.
 *
 * When [enabled] is false the children render with their normal styles.
 */
@Composable
fun LiquidGlassSceneRoot(
    enabled: Boolean,
    backdrop: Backdrop,
    config: LiquidGlassConfig = LocalLiquidGlassConfig.current,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides enabled,
        LocalLiquidGlassBackdrop provides if (enabled) backdrop else null,
        LocalLiquidGlassConfig provides config,
        content = content,
    )
}
