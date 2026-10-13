/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.IconSpec

/**
 * Dynamic Toolz mark renderer.
 * Layers: toolz_base (hexagon) -> toolz_second_layer (coat) ->
 *         toolz_circle (foreground) -> toolz_pill (carve for swoosh).
 * Resolution: overrides > followDynamic+dynamicColor > adaptToTheme > preset.
 */
object ToolzMarkColors {
    data class Resolved(
        val background: Color,
        val stroke: Color,
        val foreground: Color,
        val carve: Color,
    )

    fun resolve(
        spec: IconSpec,
        isDark: Boolean,
        dynamicColor: Boolean,
        followDynamic: Boolean,
        adaptToTheme: Boolean,
        schemePrimary: Color,
        schemeSecondary: Color,
        bgOverride: Int?,
        fgOverride: Int?,
        strokeOverride: Int?,
    ): Resolved {
        bgOverride?.let {
            val bg = Color(it)
            return Resolved(
                background = bg,
                stroke = strokeOverride?.let { Color(it) } ?: Color(spec.stroke.toInt()),
                foreground = fgOverride?.let { Color(it) } ?: Color(spec.foreground.toInt()),
                carve = bg,
            )
        }
        if (followDynamic && dynamicColor) {
            val container = if (isDark) Color(0xFF1B1B1F) else Color.White
            return Resolved(
                background = container,
                stroke = schemePrimary,
                foreground = schemePrimary,
                carve = container,
            )
        }
        val bgBase = Color(spec.background.toInt())
        val bg = if (adaptToTheme && isDark) bgBase.darker(0.82f) else bgBase
        val carveBase = Color(spec.carveResolved.toInt())
        val carve = if (adaptToTheme && isDark) bg.darker(1f) else carveBase
        return Resolved(
            background = if (adaptToTheme && isDark) bg else bgBase,
            stroke = strokeOverride?.let { Color(it) } ?: Color(spec.stroke.toInt()),
            foreground = fgOverride?.let { Color(it) } ?: Color(spec.foreground.toInt()),
            carve = strokeOverride?.let { Color(it) }?.let { bg } ?: carve.let { bg },
        )
    }

    private fun Color.darker(f: Float): Color = Color(red * f, green * f, blue * f, alpha)
}

@Composable
fun ToolzMark(
    presetId: String,
    modifier: Modifier = Modifier,
    shape: String = "HEXAGON",
    adaptToTheme: Boolean = true,
    followDynamic: Boolean = true,
    bgOverride: Int? = null,
    fgOverride: Int? = null,
    strokeOverride: Int? = null,
    showBackground: Boolean = true,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()
    // Dynamic flag unknown here; assume on so followDynamic previews live.
    ToolzMarkThemed(
        presetId = presetId,
        isDark = isDark,
        dynamicColor = true,
        schemePrimary = scheme.primary,
        schemeSecondary = scheme.secondary,
        modifier = modifier,
        shape = shape,
        adaptToTheme = adaptToTheme,
        followDynamic = followDynamic,
        bgOverride = bgOverride,
        fgOverride = fgOverride,
        strokeOverride = strokeOverride,
        showBackground = showBackground,
        contentDescription = contentDescription,
    )
}

@Composable
fun ToolzMarkThemed(
    presetId: String,
    isDark: Boolean,
    dynamicColor: Boolean,
    schemePrimary: Color,
    schemeSecondary: Color,
    modifier: Modifier = Modifier,
    shape: String = "HEXAGON",
    adaptToTheme: Boolean = true,
    followDynamic: Boolean = true,
    bgOverride: Int? = null,
    fgOverride: Int? = null,
    strokeOverride: Int? = null,
    showBackground: Boolean = true,
    corner: Dp = 28.dp,
    contentDescription: String? = null,
) {
    val spec = IconCatalog.find(presetId)
    val resolved = ToolzMarkColors.resolve(
        spec = spec,
        isDark = isDark,
        dynamicColor = dynamicColor,
        followDynamic = followDynamic,
        adaptToTheme = adaptToTheme,
        schemePrimary = schemePrimary,
        schemeSecondary = schemeSecondary,
        bgOverride = bgOverride,
        fgOverride = fgOverride,
        strokeOverride = strokeOverride,
    )
    val clipShape = when (shape.uppercase()) {
        "CIRCLE" -> CircleShape
        "PILL" -> RoundedCornerShape(50.dp)
        else -> RoundedCornerShape(corner)
    }
    Box(
        modifier = modifier.clip(clipShape).background(resolved.background),
        contentAlignment = Alignment.Center,
    ) {
        if (showBackground) {
            MarkLayer(resId = R.drawable.toolz_base, tint = resolved.background)
        }
        MarkLayer(resId = R.drawable.toolz_second_layer, tint = resolved.stroke)
        MarkLayer(resId = R.drawable.toolz_circle, tint = resolved.foreground)
        MarkLayer(resId = R.drawable.toolz_pill, tint = resolved.carve)
    }
}

@Composable
private fun MarkLayer(resId: Int, tint: Color) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
    )
}
