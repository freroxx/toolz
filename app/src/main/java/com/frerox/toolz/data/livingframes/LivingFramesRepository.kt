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

package com.frerox.toolz.data.livingframes

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * File + JSON storage for Living Frames. No Room migration needed:
 * - frames/<id>/frame_XXXX.webp (downscaled, Binder-safe)
 * - frames.json (metadata)
 * - living_frames_bindings prefs (appWidgetId -> frameId)
 */
@Singleton
class LivingFramesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir: File get() = File(context.filesDir, "living_frames").apply { mkdirs() }
    private val metaFile: File get() = File(dir, "frames.json")
    private val bindings: SharedPreferences by lazy {
        context.getSharedPreferences("living_frames_bindings", Context.MODE_PRIVATE)
    }

    private val _frames = MutableStateFlow<List<LivingFrame>>(emptyList())
    val frames: Flow<List<LivingFrame>> = _frames.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            if (!metaFile.exists()) {
                _frames.value = emptyList()
                return@withContext
            }
            val list = json.decodeFromString(ListSerializer(LivingFrame.serializer()), metaFile.readText())
            // Validate: drop sets whose frame files are gone (partial deletes,
            // cleared storage) so neither widget nor preview can wedge on them.
            val valid = list.filter { frameFile(it.id, 0).exists() }
            val dropped = list.filterNot { frameFile(it.id, 0).exists() }
            dropped.forEach { try { frameDir(it.id).deleteRecursively() } catch (_: Exception) {} }
            val sorted = valid.sortedByDescending { it.createdAt }
            if (dropped.isNotEmpty()) {
                try {
                    metaFile.writeText(json.encodeToString(ListSerializer(LivingFrame.serializer()), sorted))
                } catch (_: Exception) {}
            }
            _frames.value = sorted
        } catch (_: Exception) {
            _frames.value = emptyList()
        }
    }

    fun snapshot(): List<LivingFrame> = _frames.value

    fun frameDir(id: String): File = File(dir, id).apply { mkdirs() }

    fun frameFile(id: String, index: Int): File =
        File(frameDir(id), "frame_%04d.webp".format(index))

    fun coverFile(id: String): File = File(frameDir(id), "cover.webp")

    suspend fun decodeFrame(id: String, index: Int, maxSide: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val f = frameFile(id, index)
                if (!f.exists()) return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                if (bounds.outWidth <= 0) return@withContext null
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(f.absolutePath, opts)
            } catch (_: Exception) { null }
        }

    suspend fun decodeCover(id: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val f = coverFile(id)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        } catch (_: Exception) { null }
    }

    /**
     * Widget-ready bitmap for pushing: per-type resolution tier, RGB_565 for
     * opaque photo/video stills (halves the Binder payload), ARGB_8888 for
     * GIFs (transparency), with per-widget opacity + rounded corners baked in
     * (RemoteViews cannot do either). See budget table in helper.
     */
    suspend fun decodeWidgetFrame(frame: LivingFrame, index: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val isGif = frame.type == LivingFrameType.GIF
                val isVideo = frame.type == LivingFrameType.VIDEO
                val maxSide = when {
                    isGif -> PUSH_SIDE_ANIMATED
                    isVideo -> PUSH_SIDE_VIDEO
                    else -> PUSH_SIDE_STATIC
                }
                var bmp = decodeFrame(frame.id, index, maxSide) ?: return@withContext null
                // Exact-fit: inSampleSize undershoots (powers of two), so scale
                // precisely to the tier for consistent sharpness.
                if (maxOf(bmp.width, bmp.height) > maxSide) {
                    try {
                        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height).toFloat()
                        val tw = (bmp.width * scale).roundToInt().coerceAtLeast(1)
                        val th = (bmp.height * scale).roundToInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bmp, tw, th, true)
                        try { bmp.recycle() } catch (_: Exception) {}
                        bmp = scaled
                    } catch (_: Exception) {}
                }
                val needsAlpha = isGif || frame.opacity < 0.999f || frame.cornerRadiusDp > 0
                if (!needsAlpha && frame.fit == LivingFrameFit.FILL) {
                    // Opaque fast path: halve Binder bytes with zero visible loss.
                    try {
                        val flat = bmp.copy(Bitmap.Config.RGB_565, false)
                        try { bmp.recycle() } catch (_: Exception) {}
                        if (flat != null) bmp = flat
                    } catch (_: Exception) {}
                    return@withContext bmp
                }
                // Effect path: ARGB canvas, rounded corners, scaled alpha.
                // Capped at 512px so the 4-byte format stays in Binder budget.
                try {
                    if (maxOf(bmp.width, bmp.height) > 512) {
                        val scale = 512f / maxOf(bmp.width, bmp.height).toFloat()
                        val tw = (bmp.width * scale).roundToInt().coerceAtLeast(1)
                        val th = (bmp.height * scale).roundToInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bmp, tw, th, true)
                        try { bmp.recycle() } catch (_: Exception) {}
                        bmp = scaled
                    }
                    val w = bmp.width
                    val h = bmp.height
                    if (w <= 0 || h <= 0) return@withContext bmp
                    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(out)
                    if (frame.cornerRadiusDp > 0) {
                        val fraction = (frame.cornerRadiusDp / 32f).coerceIn(0f, 1f)
                        val radius = fraction * minOf(w, h) * 0.22f
                        if (radius > 1f) {
                            val path = android.graphics.Path().apply {
                                addRoundRect(
                                    android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()),
                                    radius, radius,
                                    android.graphics.Path.Direction.CW
                                )
                            }
                            canvas.clipPath(path)
                        }
                    }
                    val paint = android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
                    ).apply { alpha = (frame.opacity.coerceIn(0f, 1f) * 255).toInt() }
                    canvas.drawBitmap(bmp, 0f, 0f, paint)
                    try { bmp.recycle() } catch (_: Exception) {}
                    out
                } catch (_: Exception) {
                    bmp
                }
            } catch (_: Exception) { null }
        }

    suspend fun upsert(frame: LivingFrame) = withContext(Dispatchers.IO) {
        val next = (_frames.value.filterNot { it.id == frame.id } + frame)
            .sortedByDescending { it.createdAt }
        persist(next)
    }

    suspend fun update(id: String, transform: (LivingFrame) -> LivingFrame) {
        val cur = _frames.value.firstOrNull { it.id == id } ?: return
        upsert(transform(cur))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        try { frameDir(id).deleteRecursively() } catch (_: Exception) {}
        // Unbind widgets pointing at this frame.
        try {
            val ed = bindings.edit()
            bindings.all.forEach { (k, v) -> if (v == id) ed.remove(k) }
            ed.apply()
        } catch (_: Exception) {}
        persist(_frames.value.filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    // -- widget bindings ---------------------------------------------------

    fun bindWidget(appWidgetId: Int, frameId: String) {
        bindings.edit().putString("w_$appWidgetId", frameId).apply()
    }

    fun unbindWidget(appWidgetId: Int) {
        bindings.edit().remove("w_$appWidgetId").apply()
    }

    fun boundFrame(appWidgetId: Int): String? = bindings.getString("w_$appWidgetId", null)

    fun boundWidgetIds(frameId: String): List<Int> =
        bindings.all.mapNotNull { (k, v) ->
            if (v == frameId && k.startsWith("w_")) k.removePrefix("w_").toIntOrNull() else null
        }

    fun allBoundWidgetIds(): List<Int> =
        bindings.all.mapNotNull { (k, _) ->
            if (k.startsWith("w_")) k.removePrefix("w_").toIntOrNull() else null
        }

    fun get(id: String): LivingFrame? = _frames.value.firstOrNull { it.id == id }

    private suspend fun persist(list: List<LivingFrame>) = withContext(Dispatchers.IO) {
        try {
            metaFile.writeText(json.encodeToString(ListSerializer(LivingFrame.serializer()), list))
        } catch (_: Exception) {}
        _frames.update { list }
    }

    companion object {
        /** Binder-safe ceiling: frames must stay small or updates throw. */
        const val MAX_FRAME_SIDE = 320
        const val MAX_FRAMES = 90
        /** Quality tiers — see push-budget table in LivingFramesWidgetHelper. */
        const val STORE_SIDE_ANIMATED = 480
        const val STORE_SIDE_STATIC = 960
        const val STORE_SIDE_VIDEO = 640
        const val PUSH_SIDE_ANIMATED = 448
        const val PUSH_SIDE_STATIC = 720
        const val PUSH_SIDE_VIDEO = 560
    }
}
