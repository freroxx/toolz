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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

// ─────────────────────────────────────────────────────────────────────────────
// Glass Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        backdrop = LocalLiquidGlassBackdrop.current,
        modifier = modifier,
        shape = shape,
        tint = tint,
        content = content,
    )
}

/**
 * Authentic Liquid Glass card surface using Kyant's backdrop engine.
 *
 * Provides physical lens refraction, chromatic dispersion, specular highlights,
 * ambient shadow, and inner glass edge highlights dynamically tuned by [LocalLiquidGlassConfig].
 */
@Composable
fun GlassCard(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cornerShape: CornerBasedShape = (shape as? CornerBasedShape)
        ?: RoundedCornerShape(28.dp)
    val config = LocalLiquidGlassConfig.current
    val performanceMode = LocalPerformanceMode.current || config.performanceMode
    val uiSensor = com.kyant.backdrop.catalog.utils.rememberUISensor()

    if (backdrop == null) {
        Surface(
            modifier = modifier,
            shape = cornerShape,
            color = if (tint.isSpecified) tint else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            Column(content = content)
        }
        return
    }

    val effBlur = if (performanceMode) (config.blurRadiusDp * 0.4f).coerceIn(2f, 6f) else config.blurRadiusDp.coerceIn(3f, 16f)
    val effRefractH = if (performanceMode) (config.refractionDp * 0.25f) else (config.refractionDp * 0.65f)
    val effRefractAmt = if (performanceMode) (config.refractionDp * 0.35f) else config.refractionDp

    Column(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cornerShape },
                effects = {
                    if (!performanceMode) {
                        vibrancy()
                    }
                    blur(effBlur.dp.toPx())
                    lens(
                        refractionHeight = effRefractH.dp.toPx(),
                        refractionAmount = effRefractAmt.dp.toPx(),
                        depthEffect = !performanceMode,
                        chromaticAberration = !performanceMode,
                    )
                },
                highlight = {
                    Highlight(
                        style = HighlightStyle.Default(
                            color = Color.White.copy(alpha = if (performanceMode) 0.35f else 0.55f),
                            angle = if (performanceMode) 135f else uiSensor.gravityAngle,
                            falloff = if (performanceMode) 1.8f else 1.3f,
                        )
                    )
                },
                shadow = { Shadow(radius = if (performanceMode) 6.dp else 16.dp, color = Color.Black.copy(alpha = if (performanceMode) 0.12f else 0.20f)) },
                innerShadow = {
                    if (!performanceMode) {
                        InnerShadow(
                            radius = 4.dp,
                            color = Color.White.copy(alpha = 0.16f),
                        )
                    } else null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint)
                    } else {
                        drawRect(Color.White.copy(alpha = config.tintAlpha))
                    }
                },
            ),
        content = content,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Glass Settings Row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps any settings row content with an authentic glass surface.
 */
@Composable
fun GlassSettingsRow(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    val config = LocalLiquidGlassConfig.current
    val performanceMode = LocalPerformanceMode.current || config.performanceMode
    val uiSensor = com.kyant.backdrop.catalog.utils.rememberUISensor()

    if (backdrop == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            content()
        }
        return
    }

    val effBlur = if (performanceMode) (config.blurRadiusDp * 0.4f).coerceIn(2f, 6f) else config.blurRadiusDp
    val effRefractH = if (performanceMode) (config.refractionDp * 0.20f) else (config.refractionDp * 0.5f)
    val effRefractAmt = if (performanceMode) (config.refractionDp * 0.30f) else (config.refractionDp * 0.75f)

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(effBlur.dp.toPx())
                    lens(
                        refractionHeight = effRefractH.dp.toPx(),
                        refractionAmount = effRefractAmt.dp.toPx(),
                        depthEffect = !performanceMode,
                        chromaticAberration = !performanceMode,
                    )
                },
                highlight = {
                    Highlight(
                        style = HighlightStyle.Default(
                            color = Color.White.copy(alpha = if (performanceMode) 0.30f else 0.45f),
                            angle = if (performanceMode) 135f else uiSensor.gravityAngle,
                            falloff = 1.5f,
                        )
                    )
                },
                shadow = { Shadow(radius = if (performanceMode) 4.dp else 10.dp, color = Color.Black.copy(alpha = 0.12f)) },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = config.tintAlpha))
                },
            ),
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Liquid Glass Dialog Container (1-to-1 matching AndroidLiquidGlass Dialog)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiquidGlassDialogContainer(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(36.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalLiquidGlassConfig.current
    val performanceMode = LocalPerformanceMode.current || config.performanceMode
    val isLight = !isSystemInDarkTheme()
    val containerColor = if (isLight) Color(0xFFFAFAFA).copy(alpha = config.tintAlpha)
    else Color(0xFF121212).copy(alpha = config.tintAlpha)

    if (backdrop == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
        ) {
            Column(content = content)
        }
        return
    }

    val effBlur = if (performanceMode) (config.blurRadiusDp * 0.5f).coerceIn(3f, 8f) else config.blurRadiusDp
    val effRefractH = if (performanceMode) (config.refractionDp * 0.4f) else config.refractionDp
    val effRefractAmt = if (performanceMode) (config.refractionDp * 0.6f) else (config.refractionDp * 1.5f)

    Column(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(effBlur.dp.toPx())
                    lens(
                        refractionHeight = effRefractH.dp.toPx(),
                        refractionAmount = effRefractAmt.dp.toPx(),
                        depthEffect = !performanceMode,
                        chromaticAberration = !performanceMode,
                    )
                },
                highlight = { Highlight.Plain },
                shadow = { Shadow(radius = if (performanceMode) 10.dp else 24.dp, color = Color.Black.copy(alpha = 0.25f)) },
                innerShadow = {
                    if (!performanceMode) {
                        InnerShadow(radius = 6.dp, color = Color.White.copy(alpha = 0.25f))
                    } else null
                },
                onDrawSurface = { drawRect(containerColor) },
            ),
        content = content,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Liquid Glass Chip / Pill
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiquidGlassChip(
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val config = LocalLiquidGlassConfig.current
    val performanceMode = LocalPerformanceMode.current || config.performanceMode
    val uiSensor = com.kyant.backdrop.catalog.utils.rememberUISensor()
    val isLight = !isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary

    if (backdrop == null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = Capsule(),
            color = if (selected) accent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
        return
    }

    val effBlur = if (performanceMode) (config.blurRadiusDp * 0.3f).coerceIn(2f, 4f) else (config.blurRadiusDp * 0.5f)

    Row(
        modifier = modifier
            .bouncyClick(onClick = onClick)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(effBlur.dp.toPx())
                    lens(
                        refractionHeight = (if (performanceMode) 4f else 8f).dp.toPx(),
                        refractionAmount = (if (performanceMode) 8f else 14f).dp.toPx(),
                        depthEffect = !performanceMode,
                        chromaticAberration = selected && !performanceMode,
                    )
                },
                highlight = {
                    Highlight(
                        style = HighlightStyle.Default(
                            color = if (selected) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f),
                            angle = if (performanceMode) 135f else uiSensor.gravityAngle,
                            falloff = 1.5f,
                        )
                    )
                },
                shadow = { Shadow(radius = if (performanceMode) 3.dp else 6.dp, color = Color.Black.copy(alpha = 0.10f)) },
                onDrawSurface = {
                    if (selected) {
                        drawRect(accent.copy(alpha = 0.22f))
                    } else {
                        drawRect(if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.08f))
                    }
                },
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Liquid Glass Modifier Extension
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Universal modifier extension to apply authentic liquid glass backdrop effects.
 *
 * Reads [LocalLiquidGlassConfig] dynamically and tracks device orientation with [rememberUISensor].
 */
@Composable
fun Modifier.liquidGlassEffect(
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    shape: CornerBasedShape = RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    refractionFactor: Float = 1.0f,
): Modifier {
    val enabled = LocalLiquidGlassEnabled.current
    if (!enabled) return this
    val isDark = isSystemInDarkTheme()
    val fallbackColor = if (isDark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
    val effectiveBackdrop = backdrop ?: com.kyant.backdrop.backdrops.rememberCanvasBackdrop {
        drawRect(fallbackColor)
    }
    val config = LocalLiquidGlassConfig.current
    val performanceMode = LocalPerformanceMode.current || config.performanceMode
    val uiSensor = com.kyant.backdrop.catalog.utils.rememberUISensor()

    val effBlur = if (performanceMode) (config.blurRadiusDp * 0.4f).coerceIn(2f, 6f) else config.blurRadiusDp.coerceIn(3f, 16f)
    val effRefractH = if (performanceMode) (config.refractionDp * 0.20f * refractionFactor) else (config.refractionDp * 0.5f * refractionFactor)
    val effRefractAmt = if (performanceMode) (config.refractionDp * 0.35f * refractionFactor) else (config.refractionDp * 0.8f * refractionFactor)

    return this.drawBackdrop(
        backdrop = effectiveBackdrop,
        shape = { shape },
        effects = {
            if (!performanceMode) {
                vibrancy()
            }
            blur(effBlur.dp.toPx())
            lens(
                refractionHeight = effRefractH.dp.toPx(),
                refractionAmount = effRefractAmt.dp.toPx(),
                depthEffect = !performanceMode,
                chromaticAberration = !performanceMode,
            )
        },
        highlight = {
            Highlight(
                style = HighlightStyle.Default(
                    color = Color.White.copy(alpha = if (performanceMode) 0.35f else 0.5f),
                    angle = if (performanceMode) 135f else uiSensor.gravityAngle,
                    falloff = 1.3f,
                )
            )
        },
        shadow = { Shadow(radius = if (performanceMode) 4.dp else 12.dp, color = Color.Black.copy(alpha = 0.18f)) },
        innerShadow = {
            if (!performanceMode) {
                InnerShadow(radius = 3.dp, color = Color.White.copy(alpha = 0.18f))
            } else null
        },
        onDrawSurface = {
            if (tint.isSpecified) {
                drawRect(tint)
            } else {
                drawRect(Color.White.copy(alpha = config.tintAlpha * 0.65f))
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Liquid Glass Icon Button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop? = LocalLiquidGlassBackdrop.current,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val config = LocalLiquidGlassConfig.current
    val performanceMode = LocalPerformanceMode.current || config.performanceMode
    val uiSensor = com.kyant.backdrop.catalog.utils.rememberUISensor()
    val isLight = !isSystemInDarkTheme()
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    if (backdrop == null) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed && enabled && !performanceMode) 0.92f else 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            label = "LiquidIconButtonScale"
        )
        Surface(
            onClick = { haptic.click(); currentOnClick() },
            enabled = enabled,
            modifier = modifier
                .defaultMinSize(minWidth = size, minHeight = size)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            shape = shape,
            interactionSource = interactionSource,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
        return
    }

    val effBlur = if (performanceMode) (config.blurRadiusDp * 0.3f).coerceIn(2f, 4f) else (config.blurRadiusDp * 0.5f)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (!performanceMode) vibrancy()
                    blur(effBlur.dp.toPx())
                    lens(
                        refractionHeight = (if (performanceMode) 5f else 10f).dp.toPx(),
                        refractionAmount = (if (performanceMode) 8f else 16f).dp.toPx(),
                        depthEffect = !performanceMode,
                        chromaticAberration = !performanceMode,
                    )
                },
                highlight = {
                    Highlight(
                        style = HighlightStyle.Default(
                            color = Color.White.copy(alpha = if (performanceMode) 0.4f else 0.6f),
                            angle = if (performanceMode) 135f else uiSensor.gravityAngle,
                            falloff = 1.2f,
                        )
                    )
                },
                shadow = { Shadow(radius = if (performanceMode) 3.dp else 8.dp, color = Color.Black.copy(alpha = 0.15f)) },
                onDrawSurface = {
                    drawRect(if (isLight) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = config.tintAlpha))
                },
            )
            .clip(shape)
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                onClick = { haptic.click(); currentOnClick() }
            )
            .then(if (enabled && !performanceMode) Modifier.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
