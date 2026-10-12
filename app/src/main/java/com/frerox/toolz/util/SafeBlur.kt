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

package com.frerox.toolz.util

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * MIUI-safe wrapper around framework blur.
 *
 * Some firmware (notably MIUI 14 on Poco F5 / Android 13) throws
 * `IllegalArgumentException: nativePtr is null` from
 * `RenderEffect.createBlurEffect()` when the native blur node cannot be
 * allocated. That exception happened inside composition in MainActivity and,
 * because Offline Mode is persisted, re-crashed on every cold start
 * (permanent crash loop requiring clear-data).
 *
 * This helper never throws: it clamps the radius to a safe range and returns
 * null when the platform call fails so callers can fall back to no blur.
 */
object SafeBlur {
    const val MAX_RADIUS = 20f
    const val MIN_RADIUS = 0.5f

    @RequiresApi(Build.VERSION_CODES.S)
    fun createBlurOrNull(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
        val clamped = radius.coerceIn(MIN_RADIUS, MAX_RADIUS)
        return try {
            RenderEffect
                .createBlurEffect(clamped, clamped, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
