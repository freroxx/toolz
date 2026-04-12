package com.frerox.toolz.ui.screens.media

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
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

    LaunchedEffect(artworkUri) {
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
                                // Derive a very dark version of the dominant color for background
                                val hsl = FloatArray(3)
                                ColorUtils.colorToHSL(it.rgb, hsl)
                                hsl[2] = 0.05f // Very low lightness
                                Color(ColorUtils.HSLToColor(hsl))
                            } else {
                                // Derive a very light version of the dominant color for background
                                val hsl = FloatArray(3)
                                ColorUtils.colorToHSL(it.rgb, hsl)
                                hsl[2] = 0.95f // Very high lightness
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
