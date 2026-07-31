package com.frerox.toolz.ui.screens.media

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
data class DynamicColors(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color
)

val DefaultDynamicColors = DynamicColors(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White
)

@Composable
fun rememberDynamicColors(artworkUri: String?, isDark: Boolean = true): DynamicColors {
    val context = LocalContext.current
    var dynamicColors by remember { mutableStateOf(DefaultDynamicColors) }

    LaunchedEffect(artworkUri, isDark) {
        if (artworkUri == null) {
            dynamicColors = DefaultDynamicColors
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.image.toBitmap()
                val palette = Palette.from(bitmap).generate()
                
                val swatch = if (isDark) {
                    palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
                } else {
                    palette.lightVibrantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
                }

                swatch?.let {
                    withContext(Dispatchers.Main) {
                        dynamicColors = DynamicColors(
                            primary = Color(it.rgb),
                            secondary = Color(palette.getLightVibrantColor(it.rgb)),
                            background = if (isDark) {
                                val hsl = FloatArray(3)
                                ColorUtils.colorToHSL(it.rgb, hsl)
                                hsl[2] = 0.05f
                                Color(ColorUtils.HSLToColor(hsl))
                            } else {
                                val hsl = FloatArray(3)
                                ColorUtils.colorToHSL(it.rgb, hsl)
                                hsl[2] = 0.95f
                                Color(ColorUtils.HSLToColor(hsl))
                            },
                            surface = Color(it.rgb).copy(alpha = 0.12f),
                            onSurface = Color(it.titleTextColor)
                        )
                    }
                }
            }
        }
    }

    return dynamicColors
}

/**
 * Revamped Now Playing Background Composable
 *
 * DARK THEME: Pitch dark / deep black at the bottom, gradually changing color using dynamic song colors
 * as it goes upwards to the top of the screen while preserving a polished dark gradient feel.
 *
 * LIGHT THEME: Crisp white / light grey at the bottom, gradually changing color using dynamic song colors
 * as it goes upwards to the top of the screen while preserving a polished light gradient feel.
 */
@Composable
fun RevampedNowPlayingBackground(
    modifier: Modifier = Modifier,
    artworkUri: String?,
    performanceMode: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val dynamicColors = rememberDynamicColors(artworkUri = artworkUri, isDark = isDark)

    val animPrimary by animateColorAsState(
        targetValue = dynamicColors.primary,
        animationSpec = tween(durationMillis = 800),
        label = "bgPrimaryAnim"
    )

    val animSecondary by animateColorAsState(
        targetValue = dynamicColors.secondary,
        animationSpec = tween(durationMillis = 800),
        label = "bgSecondaryAnim"
    )

    val bottomBaseColor = if (isDark) Color(0xFF09090C) else Color(0xFFFAFAFD)
    val midSecondaryColor = if (isDark) lerp(bottomBaseColor, animSecondary, 0.35f) else lerp(bottomBaseColor, animSecondary, 0.18f)
    val upperPrimaryColor = if (isDark) lerp(bottomBaseColor, animPrimary, 0.55f) else lerp(bottomBaseColor, animPrimary, 0.32f)
    val topAmbianceColor  = if (isDark) lerp(bottomBaseColor, animPrimary, 0.42f) else lerp(bottomBaseColor, animPrimary, 0.22f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bottomBaseColor)
    ) {
        // Upward Gradient: Dark/Light at the bottom, dynamic song colors expanding upwards
        val upwardGradient = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to topAmbianceColor,
                0.30f to upperPrimaryColor,
                0.65f to midSecondaryColor,
                0.88f to lerp(bottomBaseColor, midSecondaryColor, 0.30f),
                1.00f to bottomBaseColor
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(upwardGradient)
        )

        // Soft blurred artwork layer for extra dynamic richness (disabled in performance mode)
        if (!performanceMode && !artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (isDark) 0.18f else 0.12f,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = AndroidRenderEffect
                            .createBlurEffect(110f, 110f, AndroidShader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
            )
        }

        // Vignette polish: Ensures bottom controls sit on clear dark/light ground & top bar protection
        val polishOverlay = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to bottomBaseColor.copy(alpha = if (isDark) 0.40f else 0.25f),
                0.18f to Color.Transparent,
                0.70f to Color.Transparent,
                1.00f to bottomBaseColor.copy(alpha = 0.95f)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(polishOverlay)
        )

        content()
    }
}
