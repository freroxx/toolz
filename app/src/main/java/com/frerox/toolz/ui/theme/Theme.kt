package com.frerox.toolz.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    outline = PrimaryDark.copy(alpha = 0.5f),
    outlineVariant = SecondaryDark.copy(alpha = 0.3f)
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
    outline = PrimaryLight.copy(alpha = 0.5f),
    outlineVariant = SecondaryLight.copy(alpha = 0.3f)
)

@Composable
fun ToolzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    customPrimary: Color? = null,
    customSecondary: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        !dynamicColor && (customPrimary != null || customSecondary != null) -> {
            val base = if (darkTheme) DarkColorScheme else LightColorScheme
            val primary = customPrimary ?: base.primary
            val secondary = customSecondary ?: base.secondary
            base.copy(
                primary = primary,
                primaryContainer = primary.copy(alpha = if (darkTheme) 0.3f else 0.1f),
                onPrimaryContainer = if (darkTheme) Color.White else primary,
                secondary = secondary,
                secondaryContainer = secondary.copy(alpha = if (darkTheme) 0.3f else 0.1f),
                onSecondaryContainer = if (darkTheme) Color.White else secondary,
                outline = primary.copy(alpha = 0.5f),
                outlineVariant = secondary.copy(alpha = 0.3f),
                surfaceVariant = secondary.copy(alpha = if (darkTheme) 0.1f else 0.05f),
                onSurface = if (darkTheme) Color(0xFFE6E1E5) else Color.Black,
                onBackground = if (darkTheme) Color(0xFFE6E1E5) else Color.Black,
                onSurfaceVariant = if (darkTheme) Color(0xFFCAC4D0) else Color(0xFF49454F)
            )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.copy(
                onSurface = if (darkTheme) Color(0xFFE6E1E5) else dynamic.onSurface,
                onBackground = if (darkTheme) Color(0xFFE6E1E5) else dynamic.onBackground,
                onSurfaceVariant = if (darkTheme) Color(0xFFCAC4D0) else dynamic.onSurfaceVariant
            )
        }
        darkTheme -> DarkColorScheme.copy(
            onSurface = Color(0xFFE6E1E5), 
            onBackground = Color(0xFFE6E1E5),
            onSurfaceVariant = Color(0xFFCAC4D0)
        )
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
