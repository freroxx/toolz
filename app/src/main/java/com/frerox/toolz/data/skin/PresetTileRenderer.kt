/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rasterises a preset's launcher tile for a pinned Home-screen shortcut.
 *
 * The manifest already ships the exact art: `mipmap-anydpi-v26/ic_icon_<san>`
 * composes `@color/ic_bg_<san>` with `@drawable/ic_fg_<san>`, and every
 * foreground vector is authored as a full 108dp adaptive layer (1620 viewport,
 * content translated into the 72dp safe zone). Drawing the fg at full bounds
 * over the bg colour therefore reproduces the adaptive icon without applying a
 * mask, which is exactly what [android.graphics.drawable.Icon.createWithAdaptiveBitmap]
 * expects — the launcher adds its own mask.
 */
object PresetTileRenderer {
    /** 108dp at 4x, the adaptive icon authoring size. */
    const val SIZE_PX = 432

    fun fgName(presetId: String, shape: String?): String {
        val san = IconCatalog.find(presetId).id.replace('-', '_')
        return "ic_fg_$san" + if (LauncherIconManager.isCircleShape(shape)) "_circle" else ""
    }

    /** Backgrounds are one colour per preset; shape only changes the foreground. */
    fun bgName(presetId: String): String =
        "ic_bg_" + IconCatalog.find(presetId).id.replace('-', '_')

    suspend fun render(context: Context, presetId: String, shape: String?): Bitmap? =
        withContext(Dispatchers.Default) {
            try {
                val res = context.resources
                val pkg = context.packageName
                val fgId = res.getIdentifier(fgName(presetId, shape), "drawable", pkg)
                val bgId = res.getIdentifier(bgName(presetId), "color", pkg)
                if (fgId == 0 || bgId == 0) return@withContext null
                val fg = context.getDrawable(fgId) ?: return@withContext null
                val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(context.getColor(bgId))
                fg.setBounds(0, 0, SIZE_PX, SIZE_PX)
                fg.draw(canvas)
                out
            } catch (_: Exception) {
                null
            }
        }
}
