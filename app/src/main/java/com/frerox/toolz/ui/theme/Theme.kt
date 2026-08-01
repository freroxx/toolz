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

package com.frerox.toolz.ui.theme

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.frerox.toolz.util.VibrationManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val LocalPerformanceMode = staticCompositionLocalOf { false }
val LocalHapticEnabled = staticCompositionLocalOf { true }
val LocalHapticIntensity = staticCompositionLocalOf { 0.5f }
val LocalBackgroundGradientEnabled = staticCompositionLocalOf { true }
val LocalIsDarkTheme = staticCompositionLocalOf { false }
val LocalVibrationManager = staticCompositionLocalOf<VibrationManager?> { null }

private val ToolzExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(48.dp),
)

val BouncyShape = RoundedCornerShape(32.dp)
val SquircleShape = RoundedCornerShape(28.dp)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = PrimaryDark.copy(alpha = 0.5f),
    outlineVariant = SecondaryDark.copy(alpha = 0.3f),
    surfaceTint = PrimaryDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = PrimaryLight.copy(alpha = 0.5f),
    outlineVariant = SecondaryLight.copy(alpha = 0.3f),
    surfaceTint = PrimaryLight,
)

/**
 * Enhanced background brush utility.
 */
fun toolzAppBackgroundBrush(
    darkTheme: Boolean,
    performanceMode: Boolean,
    gradientEnabled: Boolean = true
): Brush {
    val primary = if (darkTheme) PrimaryDark else PrimaryLight
    val secondary = if (darkTheme) SecondaryDark else SecondaryLight
    val background = if (darkTheme) BackgroundDark else BackgroundLight
    
    if (!gradientEnabled) return SolidColor(background)

    return if (darkTheme) {
        val colors = listOf(
            background,
            primary.copy(alpha = if (performanceMode) 0.08f else 0.15f),
            secondary.copy(alpha = if (performanceMode) 0.04f else 0.1f),
            background
        )
        Brush.verticalGradient(colors)
    } else {
        Brush.linearGradient(
            colors = listOf(
                primary.copy(alpha = if (performanceMode) 0.08f else 0.15f),
                background,
                secondary.copy(alpha = if (performanceMode) 0.05f else 0.10f),
                background
            ),
            start = Offset(0f, 0f),
            end = Offset(2500f, 1500f)
        )
    }
}

/**
 * Animated background modifier with expressive depth.
 */
@Composable
fun Modifier.toolzBackground(): Modifier = composed {
    val performanceMode = LocalPerformanceMode.current
    val gradientEnabled = LocalBackgroundGradientEnabled.current
    val colorScheme = MaterialTheme.colorScheme
    val isDark = LocalIsDarkTheme.current
    
    if (!gradientEnabled) return@composed this.background(colorScheme.background)

    if (performanceMode) {
        return@composed this.background(
            toolzAppBackgroundBrush(isDark, performanceMode = true)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "expressive_bg")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(35000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )

    val brush = remember(angle, colorScheme.primary, colorScheme.secondary, isDark) {
        val offset = Offset(x = (sin(angle) + 1f) / 2f, y = (cos(angle) + 1f) / 2f)
        Brush.radialGradient(
            colors = if (isDark) {
                listOf(
                    colorScheme.primary.copy(alpha = 0.15f), 
                    colorScheme.secondary.copy(alpha = 0.08f), 
                    colorScheme.background
                )
            } else {
                listOf(
                    colorScheme.primary.copy(alpha = 0.20f),
                    colorScheme.secondary.copy(alpha = 0.12f),
                    colorScheme.background
                )
            },
            center = Offset(offset.x * 2000f, offset.y * 2000f),
            radius = 3000f
        )
    }

    val overlayAlpha = if (isDark) 0.25f else 0.12f
    this.background(brush).background(colorScheme.background.copy(alpha = overlayAlpha))
}

@Composable
fun ToolzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customPrimary: Color? = null,
    customSecondary: Color? = null,
    backgroundGradientEnabled: Boolean = true,
    performanceMode: Boolean = false,
    hapticEnabled: Boolean = true,
    hapticIntensity: Float = 0.5f,
    vibrationManager: VibrationManager? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        (!dynamicColor) && (customPrimary != null || customSecondary != null) -> {
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            val primary = customPrimary ?: base.primary
            val secondary = customSecondary ?: base.secondary
            base.copy(
                primary = primary,
                primaryContainer = primary.copy(alpha = if (darkTheme) 0.25f else 0.12f),
                onPrimaryContainer = if (darkTheme) Color.White else primary,
                secondary = secondary,
                secondaryContainer = secondary.copy(alpha = if (darkTheme) 0.25f else 0.12f),
                onSecondaryContainer = if (darkTheme) Color.White else secondary,
                surfaceVariant = secondary.copy(alpha = if (darkTheme) 0.08f else 0.04f),
                onSurface = if (darkTheme) Color(0xFFFBF8FF) else Color(0xFF1B1B1F),
                background = if (darkTheme) BackgroundDark else BackgroundLight,
                surface = if (darkTheme) SurfaceDark else SurfaceLight,
                onBackground = if (darkTheme) Color(0xFFFBF8FF) else Color(0xFF1B1B1F)
            )
        }
        dynamicColor -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.copy(
                onSurface = if (darkTheme) Color(0xFFFBF8FF) else dynamic.onSurface,
                background = if (darkTheme) BackgroundDark else BackgroundLight,
                surface = if (darkTheme) SurfaceDark else SurfaceLight,
                onBackground = if (darkTheme) Color(0xFFFBF8FF) else dynamic.onBackground
            )
        }
        darkTheme -> DarkColorScheme.copy(onSurface = Color(0xFFFBF8FF), onBackground = Color(0xFFFBF8FF))
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    CompositionLocalProvider(
        LocalPerformanceMode provides performanceMode,
        LocalHapticEnabled provides hapticEnabled,
        LocalHapticIntensity provides hapticIntensity,
        LocalBackgroundGradientEnabled provides backgroundGradientEnabled,
        LocalIsDarkTheme provides darkTheme,
        LocalVibrationManager provides vibrationManager
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ToolzExpressiveShapes,
            typography = Typography,
            content = content,
        )
    }
}
