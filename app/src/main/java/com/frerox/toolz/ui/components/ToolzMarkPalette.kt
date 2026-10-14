/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.frerox.toolz.data.skin.IconSpec
import kotlin.math.pow

/**
 * The four colours that fully describe a mark.
 *
 * [carve] is the capsule's flat fill. In the source art it equals the gradient's start
 * colour, which is the default.
 */
@Immutable
data class MarkPalette(
    val rim: Color,
    val bodyStart: Color,
    val bodyEnd: Color = bodyStart,
    val disc: Color,
    val carve: Color = bodyStart,
) {
    /** Body fill for the octagon frame, built once per palette. */
    internal val octagonBody: Brush by lazy(LazyThreadSafetyMode.NONE) {
        MarkGradient.brush(bodyStart, bodyEnd, MarkGeometry.gradientStart, MarkGeometry.gradientEnd)
    }

    internal fun dimmed(factor: Float) = copy(
        bodyStart = bodyStart.scaled(factor),
        bodyEnd = bodyEnd.scaled(factor),
        carve = carve.scaled(factor),
    )
}

/**
 * Body gradient profile measured from the reference icons: the start colour holds for the
 * first ~9% of the axis, then eases in (u^1.42) and reaches the end colour at ~97%.
 * Interpolation is plain sRGB; Compose's own lerp() works in Oklab and would drift.
 */
internal object MarkGradient {
    private const val HOLD = 0.093f
    private const val DONE = 0.974f
    private const val POWER = 1.42f
    private const val STEPS = 8

    fun brush(start: Color, end: Color, from: Offset, to: Offset): Brush {
        if (start == end) return SolidColor(start)
        val stops = ArrayList<Pair<Float, Color>>(STEPS + 3)
        stops += 0f to start
        for (k in 0..STEPS) {
            val u = k / STEPS.toFloat()
            stops += (HOLD + (DONE - HOLD) * u) to mixSrgb(start, end, u.pow(POWER))
        }
        stops += 1f to end
        return Brush.linearGradient(colorStops = stops.toTypedArray(), start = from, end = to)
    }
}

object MarkPalettes {

    fun of(spec: IconSpec) = MarkPalette(
        rim = spec.rim.toColor(),
        bodyStart = spec.bodyStart.toColor(),
        bodyEnd = (spec.bodyEnd ?: spec.bodyStart).toColor(),
        disc = spec.disc.toColor(),
        carve = (spec.carve ?: spec.bodyStart).toColor(),
    )

    /**
     * Material-You palette in the same structure as the shipped presets:
     * light = saturated body fading to a pale rim colour, dark = deep body with a muted rim.
     */
    fun fromScheme(scheme: ColorScheme, isDark: Boolean): MarkPalette =
        if (isDark) {
            MarkPalette(
                rim = mixSrgb(scheme.onPrimaryContainer, scheme.primaryContainer, 0.45f),
                bodyStart = scheme.primaryContainer,
                bodyEnd = mixSrgb(scheme.primaryContainer, Color.Black, 0.65f),
                disc = scheme.onPrimaryContainer,
            )
        } else {
            MarkPalette(
                rim = scheme.primaryContainer,
                bodyStart = scheme.primary,
                bodyEnd = mixSrgb(scheme.primary, scheme.primaryContainer, 0.85f),
                disc = scheme.onPrimary,
            )
        }

    /**
     * Precedence: per-field overrides > scheme-driven palette > theme dimming > preset as drawn.
     *
     * Presets are never recoloured unless the spec opts in ([IconSpec.followsScheme] /
     * [IconSpec.adaptToTheme]), so a preset always renders exactly as designed.
     */
    fun resolve(
        spec: IconSpec,
        scheme: ColorScheme,
        isDark: Boolean,
        dynamicColor: Boolean,
        followDynamic: Boolean,
        adaptToTheme: Boolean,
        bgOverride: Int?,
        fgOverride: Int?,
        strokeOverride: Int?,
        iconGradient: Boolean = true,
    ): MarkPalette {
        var p = when {
            spec.followsScheme && followDynamic && dynamicColor -> fromScheme(scheme, isDark)
            spec.adaptToTheme && adaptToTheme && isDark -> of(spec).dimmed(0.82f)
            else -> of(spec)
        }
        if (!iconGradient) p = p.copy(bodyEnd = p.bodyStart)
        bgOverride?.let { val c = Color(it); p = p.copy(bodyStart = c, bodyEnd = c, carve = c) }
        fgOverride?.let { p = p.copy(disc = Color(it)) }
        strokeOverride?.let { p = p.copy(rim = Color(it)) }
        return p
    }
}

private fun Long.toColor() = Color(toInt())

private fun Color.scaled(f: Float) = Color(red * f, green * f, blue * f, alpha)

internal fun mixSrgb(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
