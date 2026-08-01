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

package com.frerox.toolz.ui.screens.utils

import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import com.frerox.toolz.util.VibrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ColorPickerViewModel @Inject constructor(
    private val vibrationManager: VibrationManager
) : ViewModel() {

    private val _pickedColor = mutableStateOf(Color.White)
    val pickedColor: State<Color> = _pickedColor

    private val _hexCode = mutableStateOf("#FFFFFF")
    val hexCode: State<String> = _hexCode

    private val _zoomRatio = mutableStateOf(1f)
    val zoomRatio: State<Float> = _zoomRatio

    private val _samplingBitmap = mutableStateOf<Bitmap?>(null)
    val samplingBitmap: State<Bitmap?> = _samplingBitmap

    val colorHistory = mutableStateListOf<Color>()

    private var camera: Camera? = null

    fun setCamera(camera: Camera) {
        this.camera = camera
        // Reset zoom when camera is set
        camera.cameraControl.setZoomRatio(_zoomRatio.value)
    }

    fun setZoom(ratio: Float) {
        _zoomRatio.value = ratio
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun onImageAnalyzed(bitmap: Bitmap) {
        _samplingBitmap.value = bitmap
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        
        // Sample a small area for better stability (7x7)
        val sampleSize = 3 // 3 pixels in each direction from center
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0
        
        for (x in (centerX - sampleSize)..(centerX + sampleSize)) {
            for (y in (centerY - sampleSize)..(centerY + sampleSize)) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    redSum += android.graphics.Color.red(pixel)
                    greenSum += android.graphics.Color.green(pixel)
                    blueSum += android.graphics.Color.blue(pixel)
                    count++
                }
            }
        }
        
        if (count > 0) {
            val avgRed = (redSum / count).toInt()
            val avgGreen = (greenSum / count).toInt()
            val avgBlue = (blueSum / count).toInt()
            
            val newColor = Color(avgRed, avgGreen, avgBlue)
            _pickedColor.value = newColor
            _hexCode.value = String.format(Locale.US, "#%06X", (0xFFFFFF and android.graphics.Color.rgb(avgRed, avgGreen, avgBlue)))
        }
    }

    fun captureColor() {
        val color = _pickedColor.value
        if (!colorHistory.contains(color)) {
            colorHistory.add(0, color)
            if (colorHistory.size > 15) {
                colorHistory.removeAt(colorHistory.lastIndex)
            }
            vibrationManager.vibrateSuccess()
        }
    }

    fun selectFromHistory(color: Color) {
        _pickedColor.value = color
        _hexCode.value = String.format(Locale.US, "#%06X", (0xFFFFFF and color.toArgb()))
        vibrationManager.vibrateTick()
    }
}
